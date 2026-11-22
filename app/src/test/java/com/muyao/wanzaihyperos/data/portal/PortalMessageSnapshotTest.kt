package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class PortalMessageSnapshotTest {
    private val group = PortalMessageGroup("activity", 2)
    private val row = PortalMessageItem("synthetic-id", "Notice", "Body", false, group.name,
        "2025-11-06", "2026-11-16", "https://school.invalid/open?access_token=must-not-persist")
    private val content = ListState.Content(listOf(row), ListMeta(page = 2, hasMore = true, updatedAtMillis = 456L))
    private fun epoch(owner: String = "synthetic-owner") = AccountEpoch().apply { activate(owner) }
    private fun save(file: File, epoch: AccountEpoch) = PortalMessageSnapshot(file, epoch).write(
        epoch.token(), group.name, listOf(group), mapOf(group.name to content))

    @Test fun restartRestoresOwnerWindowAsStaleAndNeverPersistsDestinationCredentials() {
        val dir = Files.createTempDirectory("portal-snapshot").toFile()
        try {
            val file = File(dir, "messages.json")
            save(file, epoch())
            assertFalse(file.readText().contains("must-not-persist"))
            val next = epoch()
            val restored = PortalMessageSnapshot(file, next).read(next.token())!!
            val state = restored.states.getValue(group.name) as ListState.Content
            assertEquals(group.name, restored.selected)
            assertEquals(row.copy(openUrl = null), state.items.single())
            assertEquals(2, state.meta.page)
            assertEquals(456L, state.meta.updatedAtMillis)
            assertTrue(state.meta.stale)
            assertTrue(state.meta.hasMore)
            assertFalse(state.meta.refreshing)
        } finally { dir.deleteRecursively() }
    }

    @Test fun otherOwnerCorruptAndUnboundFilesAreNotSeeded() {
        val dir = Files.createTempDirectory("portal-owner").toFile()
        try {
            val file = File(dir, "messages.json")
            save(file, epoch())
            val other = epoch("other-owner")
            assertNull(PortalMessageSnapshot(file, other).read(other.token()))
            file.writeText("{broken")
            assertNull(PortalMessageSnapshot(file, other).read(other.token()))
            file.writeText("""{"selected":"activity","groups":[]}""")
            assertNull(PortalMessageSnapshot(file, other).read(other.token()))
        } finally { dir.deleteRecursively() }
    }

    @Test fun confirmedEmptyIsPreservedRatherThanRepeatedFirstLoad() {
        val dir = Files.createTempDirectory("portal-empty").toFile()
        try {
            val file = File(dir, "messages.json")
            val owner = epoch()
            val store = PortalMessageSnapshot(file, owner)
            store.write(owner.token(), group.name, listOf(group), mapOf(group.name to ListState.Empty(ListMeta(hasMore = false))))
            val restored = store.read(owner.token())!!.states.getValue(group.name) as ListState.Empty
            assertFalse(restored.meta.hasMore)
            assertTrue(restored.meta.stale)
        } finally { dir.deleteRecursively() }
    }

    @Test fun repositorySeedsBeforeOfflineGroupRequestAndAccountResetDeletesItsPrivateSnapshot() = runBlocking {
        val dir = Files.createTempDirectory("portal-restart").toFile()
        val http = OkHttpClient.Builder().addInterceptor { throw IOException("Synthetic offline") }.build()
        try {
            val file = File(dir, "messages.json")
            save(file, epoch())
            val owner = epoch()
            val credentials = object : PortalCredentials {
                override fun accessToken() = "synthetic-token"
                override fun userCode() = "synthetic-owner"
                override fun portalCookiesJson(): String? = null
                override fun savePortalCookies(json: String) = Unit
                override fun saveAccessToken(accessToken: String, userCode: String?) = Unit
                override fun savePortalSession(accessToken: String, userCode: String?, portalCookiesJson: String) = Unit
                override fun clear() = Unit
            }
            val repo = LivePortalMessageRepository(PortalApi(HzcuClient.create(credentials = credentials,
                portalBaseUrl = "https://portal.invalid", rateLimiter = HzcuRateLimiter.forTesting(0, 0),
                maxAttempts = 1, httpClient = http)), epoch = owner, snapshotFile = file)
            repo.refreshGroups()
            assertEquals(group.name, repo.selectedGroup.value?.name)
            assertEquals(row.id, (repo.messages.value as ListState.Content).items.single().id)
            assertEquals(2, (repo.messages.value as ListState.Content).meta.page)
            owner.activate("different-owner")
            assertFalse(file.exists())
            assertNull(repo.selectedGroup.value)
            assertEquals(ListState.FirstLoad, repo.messages.value)
        } finally {
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
            dir.deleteRecursively()
        }
    }
}
