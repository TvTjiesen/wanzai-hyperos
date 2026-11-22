package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.jw.OwnerSnapshotStore
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class PortalNewsSnapshotTest {
    private val a = PortalNewsGroup("a", "A", 11)
    private val b = PortalNewsGroup("b", "B", 22)
    private val row = PortalNewsItem("synthetic-id", "News", "2026-11-21",
        "https://school.invalid/open?tempcode=must-not-persist&access_token=also-secret")
    private val content = ListState.Content(listOf(row),
        ListMeta(page = 2, hasMore = true, updatedAtMillis = 456L))
    private fun epoch(name: String = "synthetic-owner") = AccountEpoch().apply { activate(name) }
    private fun rows(vararg ids: String) =
        """{"code":200,"data":[${ids.joinToString(",") { """{"id":"$it","title":"News $it","blink":"https://school.invalid/?token=never-store"}""" }}]}"""
    private fun save(file: File, owner: AccountEpoch = epoch()): Boolean = PortalNewsSnapshot(file, owner).write(
        owner.token(), a, listOf(PortalNewsSnapshot.Entry(a, content)))

    private class Blocked {
        val entered = CompletableDeferred<Unit>()
        private val released = CountDownLatch(1)
        fun reply(body: String): String {
            entered.complete(Unit)
            check(released.await(5, TimeUnit.SECONDS)) { "Synthetic response was not released" }
            return body
        }
        fun release() = released.countDown()
    }

    private inner class Fixture : AutoCloseable {
        val dir = Files.createTempDirectory("news-snapshot-test").toFile()
        val file = File(dir, "news.json")
        val owner = epoch()
        val requests = CopyOnWriteArrayList<Pair<Int, Int>>()
        @Volatile var respond: (Request) -> String = { throw IOException("Synthetic offline") }
        private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(respond(chain.request()).toResponseBody()).build()
        }.build()
        private val session = PortalSession(InMemorySecureStore()).apply {
            save("synthetic-access", "synthetic-user", "{}")
        }
        private val api = PortalApi(HzcuClient.create(session, portalBaseUrl = "https://synthetic.invalid",
            httpClient = http, rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1))
        fun repo() = LivePortalNewsRepository(api, pageSize = 2, clock = { 999L },
            epoch = owner, snapshotFile = file)
        fun replyPages(reply: (Int, Int) -> String) {
            respond = { request ->
                val buffer = Buffer()
                request.body!!.writeTo(buffer)
                val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                val category = body.getValue("cataid").jsonPrimitive.int
                val page = (body.getValue("start").jsonPrimitive.int - 1) / 2 + 1
                requests += category to page
                reply(category, page)
            }
        }
        override fun close() {
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
            dir.deleteRecursively()
        }
    }

    @Test fun restartRestoresOwnerCategoriesCursorTimeAndStripsAllDestinationFields() {
        Fixture().use { f ->
            val writer = PortalNewsSnapshot(f.file, f.owner)
            val alternate = a.copy(dataId = 33)
            assertTrue(writer.write(f.owner.token(), alternate, listOf(
                PortalNewsSnapshot.Entry(a, content),
                PortalNewsSnapshot.Entry(alternate, ListState.Empty(ListMeta(hasMore = false, updatedAtMillis = 400L))),
            )))
            val text = f.file.readText()
            assertFalse(text.contains("must-not-persist"))
            assertFalse(text.contains("also-secret"))
            assertFalse(text.contains("openUrl"))
            assertFalse(text.contains("https://"))
            val next = epoch()
            val restored = PortalNewsSnapshot(f.file, next).read(next.token())!!
            assertEquals(alternate, restored.selected)
            val state = restored.entries.first { it.group == a }.state as ListState.Content
            assertEquals(row.copy(openUrl = null), state.items.single())
            assertEquals(2, state.meta.page)
            assertEquals(456L, state.meta.updatedAtMillis)
            assertTrue(state.meta.hasMore)
            assertTrue(state.meta.stale)
            assertFalse(state.meta.refreshing)
            assertFalse(state.meta.loadingMore)
        }
    }

    @Test fun confirmedEmptyRestoresAsEmptyWithItsTimeAndEndState() {
        Fixture().use { f ->
            assertTrue(PortalNewsSnapshot(f.file, f.owner).write(f.owner.token(), b,
                listOf(PortalNewsSnapshot.Entry(b, ListState.Empty(ListMeta(
                    page = 3, hasMore = false, updatedAtMillis = 789L))))))
            val next = epoch()
            val restored = PortalNewsSnapshot(f.file, next).read(next.token())!!
            assertEquals(b, restored.selected)
            val state = restored.entries.single().state as ListState.Empty
            assertEquals(3, state.meta.page)
            assertEquals(789L, state.meta.updatedAtMillis)
            assertFalse(state.meta.hasMore)
            assertTrue(state.meta.stale)
        }
    }

    @Test fun wrongOwnerStaleEpochAndSuspendedOwnerCannotReadOrOverwrite() {
        Fixture().use { f ->
            assertTrue(save(f.file, f.owner))
            val good = f.file.readText()
            val other = epoch("different-owner")
            assertNull(PortalNewsSnapshot(f.file, other).read(other.token()))
            val old = f.owner.token()
            f.owner.suspendOwner()
            val store = PortalNewsSnapshot(f.file, f.owner)
            assertNull(store.read(old))
            assertFalse(store.write(old, a, listOf(PortalNewsSnapshot.Entry(a, content))))
            f.owner.activate("next-owner")
            assertFalse(store.write(old, a, listOf(PortalNewsSnapshot.Entry(a, content))))
            assertEquals(good, f.file.readText())
        }
    }

    @Test fun corruptLegacyAndWrongSchemaAreRejectedWithoutMutatingDisk() {
        Fixture().use { f ->
            val store = PortalNewsSnapshot(f.file, f.owner)
            listOf("{broken", """{"selected":"a","groups":[]}""").forEach { invalid ->
                f.file.writeText(invalid)
                assertNull(store.read(f.owner.token()))
                assertEquals(invalid, f.file.readText())
            }
            val payload = """{"version":2,"selected":null,"groups":[]}"""
            assertTrue(OwnerSnapshotStore(f.file, "portal-news-groups", f.owner).write(f.owner.token(), payload))
            val bad = f.file.readText()
            assertNull(store.read(f.owner.token()))
            assertEquals(bad, f.file.readText())
            assertTrue(save(f.file, f.owner))
            assertNotNull(store.read(f.owner.token()))
        }
    }

    @Test fun invalidOrUnconfirmedWritesPreserveLastGoodFile() {
        Fixture().use { f ->
            assertTrue(save(f.file, f.owner))
            val good = f.file.readText()
            val store = PortalNewsSnapshot(f.file, f.owner)
            val invalid = listOf<ListState<PortalNewsItem>>(
                ListState.FirstLoad,
                content.copy(meta = content.meta.copy(page = 0)),
                content.copy(meta = content.meta.copy(updatedAtMillis = null)),
                content.copy(meta = content.meta.copy(loadingMore = true)),
                content.copy(items = listOf(row.copy(title = ""))),
                content.copy(items = listOf(row, row)),
                content.copy(items = listOf(row.copy(title = "x".repeat(8193)))),
            )
            invalid.forEach { state ->
                assertFalse(store.write(f.owner.token(), a, listOf(PortalNewsSnapshot.Entry(a, state))))
                assertEquals(good, f.file.readText())
            }
            assertFalse(store.write(f.owner.token(), null, emptyList()))
            assertEquals(good, f.file.readText())
        }
    }

    @Test fun constructorSeedsBeforeAnyRequestAndOfflineRefreshPreservesFileAndWindow() = runBlocking {
        Fixture().use { f ->
            assertTrue(save(f.file))
            val good = f.file.readText()
            val repo = f.repo()
            assertEquals(a, repo.selectedGroup.value)
            val initial = repo.news.value as ListState.Content
            assertEquals(row.copy(openUrl = null), initial.items.single())
            assertTrue(initial.meta.stale)
            repo.refreshKeepingContent()
            val failed = repo.news.value as ListState.Content
            assertEquals(2, failed.meta.page)
            assertEquals(456L, failed.meta.updatedAtMillis)
            assertNotNull(failed.meta.lastRefreshError)
            assertFalse(failed.meta.refreshing)
            repo.refreshGroups()
            assertEquals(a, repo.selectedGroup.value)
            assertEquals(good, f.file.readText())
        }
    }

    @Test fun restartRefreshesWholeLoadedWindowThenPersistsNewWindow() = runBlocking {
        withTimeout(8000) {
            Fixture().use { f ->
                assertTrue(save(f.file))
                val repo = f.repo()
                val blocked = Blocked()
                f.replyPages { _, page -> if (page == 1) blocked.reply(rows("new1", "new2")) else rows("new3") }
                val refresh = launch { repo.refreshKeepingContent() }
                try {
                    blocked.entered.await()
                    val state = repo.news.value as ListState.Content
                    assertEquals(row.id, state.items.single().id)
                    assertTrue(state.meta.refreshing)
                } finally { blocked.release() }
                refresh.join()
                assertEquals(listOf(11 to 1, 11 to 2), f.requests.toList())
                val next = epoch()
                val state = PortalNewsSnapshot(f.file, next).read(next.token())!!.entries.single().state as ListState.Content
                assertEquals(listOf("new1", "new2", "new3"), state.items.map { it.id })
                assertTrue(state.items.all { it.openUrl == null })
                assertFalse(f.file.readText().contains("never-store"))
                assertEquals(2, state.meta.page)
                assertEquals(999L, state.meta.updatedAtMillis)
                assertFalse(state.meta.hasMore)
                assertTrue(state.meta.stale)
            }
        }
    }

    @Test fun failedSecondRefreshPageDoesNotReplaceLastGoodDiskWindow() = runBlocking {
        Fixture().use { f ->
            assertTrue(save(f.file))
            val good = f.file.readText()
            val repo = f.repo()
            f.replyPages { _, page -> if (page == 1) rows("replacement1", "replacement2")
                else """{"code":500,"message":"Synthetic failed"}""" }
            repo.refreshKeepingContent()
            assertEquals(good, f.file.readText())
            assertEquals(row.id, (repo.news.value as ListState.Content).items.single().id)
            assertEquals(2, repo.news.value.metaOrNull!!.page)
        }
    }

    @Test fun successfulEmptyRefreshReplacesOldDiskContent() = runBlocking {
        Fixture().use { f ->
            assertTrue(save(f.file))
            val repo = f.repo()
            f.replyPages { _, _ -> rows() }
            repo.refreshKeepingContent()
            val next = epoch()
            val state = PortalNewsSnapshot(f.file, next).read(next.token())!!.entries.single().state as ListState.Empty
            assertEquals(1, state.meta.page)
            assertEquals(999L, state.meta.updatedAtMillis)
            assertFalse(state.meta.hasMore)
            assertEquals(listOf(11 to 1), f.requests.toList())
        }
    }

    @Test fun pageSuccessAndMultipleGroupsSurviveRestartWithCursorAndSelectedGroup() = runBlocking {
        Fixture().use { f ->
            val repo = f.repo()
            f.replyPages { category, page -> if (category == 11 && page == 1) rows("a1", "a2") else rows("a3") }
            repo.selectGroup(a)
            repo.loadMore()
            f.replyPages { _, _ -> rows() }
            repo.selectGroup(b)
            val restarted = f.repo()
            assertEquals(b, restarted.selectedGroup.value)
            assertTrue(restarted.news.value is ListState.Empty)
            val blocked = Blocked()
            f.replyPages { _, page -> if (page == 1) blocked.reply(rows("next1", "next2")) else rows() }
            val load = launch { restarted.selectGroup(a) }
            try {
                blocked.entered.await()
                val state = restarted.news.value as ListState.Content
                assertEquals(listOf("a1", "a2", "a3"), state.items.map { it.id })
                assertEquals(2, state.meta.page)
            } finally { blocked.release() }
            load.join()
        }
    }

    @Test fun resetDeletesPrivateSnapshotAndLateResponseCannotRecreateIt() = runBlocking {
        withTimeout(8000) {
            Fixture().use { f ->
                assertTrue(save(f.file))
                val repo = f.repo()
                val blocked = Blocked()
                f.replyPages { _, _ -> blocked.reply(rows("old-owner")) }
                val load = launch { repo.refreshKeepingContent() }
                try {
                    blocked.entered.await()
                    f.owner.suspendOwner()
                    f.owner.activate("different-owner")
                    assertFalse(f.file.exists())
                    assertEquals(ListState.FirstLoad, repo.news.value)
                    assertNull(repo.selectedGroup.value)
                } finally { blocked.release() }
                load.join()
                assertFalse(f.file.exists())
                assertEquals(ListState.FirstLoad, repo.news.value)
            }
        }
    }

    @Test fun cancellationCannotReplaceLastGoodSnapshot() = runBlocking {
        withTimeout(8000) {
            Fixture().use { f ->
                assertTrue(save(f.file))
                val good = f.file.readText()
                val repo = f.repo()
                val blocked = Blocked()
                f.replyPages { _, _ -> blocked.reply(rows("cancelled")) }
                val load = launch { repo.refreshKeepingContent() }
                try {
                    blocked.entered.await()
                    load.cancelAndJoin()
                } finally { blocked.release() }
                assertEquals(good, f.file.readText())
                assertFalse(repo.news.value.metaOrNull!!.refreshing)
            }
        }
    }
}
