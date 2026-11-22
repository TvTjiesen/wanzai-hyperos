package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
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
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class PortalNewsCacheTest {
    private val a = PortalNewsGroup("a", "A", 11)
    private val b = PortalNewsGroup("b", "B", 22)

    private fun rows(vararg ids: String): String =
        """{"code":200,"data":[${ids.joinToString(",") { """{"id":"$it","title":"News $it"}""" }}]}"""

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

    private inner class Rig {
        val owner = AccountEpoch().apply { activate("synthetic-owner-a") }
        val requests = CopyOnWriteArrayList<Pair<Int, Int>>()
        @Volatile var respond: (Int, Int) -> String = { category, page -> rows("$category-$page") }
        private val session = PortalSession(InMemorySecureStore()).apply {
            save("synthetic-access", "synthetic-user", "{}")
        }
        private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val buffer = Buffer()
            chain.request().body!!.writeTo(buffer)
            val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            val category = body.getValue("cataid").jsonPrimitive.int
            val start = body.getValue("start").jsonPrimitive.int
            val page = (start - 1) / 2 + 1
            requests += category to page
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(respond(category, page).toResponseBody()).build()
        }.build()
        val repo = LivePortalNewsRepository(PortalApi(HzcuClient.create(
            session, portalBaseUrl = "https://synthetic.invalid", httpClient = http,
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1,
        )), pageSize = 2, clock = { 1234L }, epoch = owner)
        fun ids(): List<String> = (repo.news.value as ListState.Content).items.map { it.id }
        fun meta() = requireNotNull(repo.news.value.metaOrNull)
        suspend fun twoPages() {
            respond = { category, page -> if (category == 11 && page == 1) rows("a1", "a2") else rows("a3") }
            repo.selectGroup(a)
            repo.loadMore()
            assertEquals(listOf("a1", "a2", "a3"), ids())
            assertEquals(2, meta().page)
        }
    }

    @Test fun returningToGroupRestoresImmediatelyAndRefreshesEntireLoadedWindow() = runBlocking {
        withTimeout(8000) {
            val rig = Rig()
            rig.twoPages()
            rig.repo.selectGroup(b)
            val blocked = Blocked()
            rig.requests.clear()
            rig.respond = { _, page -> if (page == 1) blocked.reply(rows("new1", "new2")) else rows("new3") }
            val refresh = launch { rig.repo.selectGroup(a) }
            try {
                blocked.entered.await()
                assertEquals(a, rig.repo.selectedGroup.value)
                assertEquals(listOf("a1", "a2", "a3"), rig.ids())
                assertTrue(rig.meta().refreshing)
                assertEquals(2, rig.meta().page)
            } finally { blocked.release() }
            refresh.join()
            assertEquals(listOf(11 to 1, 11 to 2), rig.requests.toList())
            assertEquals(listOf("new1", "new2", "new3"), rig.ids())
            assertFalse(rig.meta().refreshing)
            assertFalse(rig.meta().hasMore)
        }
    }

    @Test fun failedSecondRefreshPageKeepsWholePriorWindowAndCursor() = runBlocking {
        val rig = Rig()
        rig.twoPages()
        rig.respond = { _, page -> if (page == 1) rows("replacement1", "replacement2") else """{"code":500,"message":"synthetic failed"}""" }
        rig.repo.refreshKeepingContent()
        assertEquals(listOf("a1", "a2", "a3"), rig.ids())
        assertEquals(2, rig.meta().page)
        assertFalse(rig.meta().hasMore)
        assertTrue(rig.meta().stale)
        assertNotNull(rig.meta().lastRefreshError)
        assertFalse(rig.meta().refreshing)
        assertEquals(1234L, rig.meta().updatedAtMillis)
    }

    @Test fun cachedEmptyRestoresAndFailedRefreshKeepsEmpty() = runBlocking {
        withTimeout(8000) {
            val rig = Rig()
            rig.respond = { _, _ -> rows() }
            rig.repo.selectGroup(a)
            rig.repo.selectGroup(b)
            val blocked = Blocked()
            rig.respond = { _, _ -> blocked.reply("""{"code":500,"message":"synthetic failed"}""") }
            val refresh = launch { rig.repo.selectGroup(a) }
            try {
                blocked.entered.await()
                assertTrue(rig.repo.news.value is ListState.Empty)
                assertTrue(rig.meta().refreshing)
            } finally { blocked.release() }
            refresh.join()
            assertTrue(rig.repo.news.value is ListState.Empty)
            assertTrue(rig.meta().stale)
            assertFalse(rig.meta().hasMore)
        }
    }

    @Test fun successfulEmptyRefreshReplacesOldWindowAndStopsAfterFirstPage() = runBlocking {
        val rig = Rig()
        rig.twoPages()
        rig.requests.clear()
        rig.respond = { _, _ -> rows() }
        rig.repo.refreshKeepingContent()
        assertTrue(rig.repo.news.value is ListState.Empty)
        assertEquals(1, rig.meta().page)
        assertFalse(rig.meta().hasMore)
        assertFalse(rig.meta().stale)
        rig.repo.loadMore()
        assertEquals(listOf(11 to 1), rig.requests.toList())
    }

    @Test fun paginationDeduplicatesButUsesRawRowsForEndAndAdvancesCursor() = runBlocking {
        val rig = Rig()
        rig.respond = { _, page -> when (page) {
            1 -> rows("same", "same")
            2 -> rows("same", "next")
            else -> rows()
        } }
        rig.repo.selectGroup(a)
        assertEquals(listOf("same"), rig.ids())
        assertTrue(rig.meta().hasMore)
        rig.repo.loadMore()
        assertEquals(listOf("same", "next"), rig.ids())
        assertEquals(2, rig.meta().page)
        assertTrue(rig.meta().hasMore)
        rig.repo.loadMore()
        assertEquals(3, rig.meta().page)
        assertFalse(rig.meta().hasMore)
        rig.repo.loadMore()
        assertEquals(listOf(11 to 1, 11 to 2, 11 to 3), rig.requests.toList())
    }

    @Test fun shortRefreshedWindowDropsOldTailAndUpdatesCursor() = runBlocking {
        val rig = Rig()
        rig.twoPages()
        rig.requests.clear()
        rig.respond = { _, _ -> rows("only") }
        rig.repo.refreshKeepingContent()
        assertEquals(listOf("only"), rig.ids())
        assertEquals(1, rig.meta().page)
        assertFalse(rig.meta().hasMore)
        assertEquals(listOf(11 to 1), rig.requests.toList())
    }

    @Test fun failedLoadMoreRetriesSamePageWithoutLosingItems() = runBlocking {
        val rig = Rig()
        rig.respond = { _, page -> if (page == 1) rows("a1", "a2") else """{"code":500,"message":"synthetic failed"}""" }
        rig.repo.selectGroup(a)
        rig.repo.loadMore()
        assertEquals(1, rig.meta().page)
        assertEquals(listOf("a1", "a2"), rig.ids())
        assertTrue(rig.meta().hasMore)
        assertFalse(rig.meta().loadingMore)
        rig.respond = { _, _ -> rows("a3") }
        rig.repo.loadMore()
        assertEquals(listOf("a1", "a2", "a3"), rig.ids())
        assertEquals(listOf(11 to 1, 11 to 2, 11 to 2), rig.requests.toList())
        assertEquals(2, rig.meta().page)
        assertFalse(rig.meta().stale)
    }

    @Test fun lateOldGroupResponseCannotReplaceNewSelectionOrItsCache() = runBlocking {
        withTimeout(8000) {
            val rig = Rig()
            val blocked = Blocked()
            rig.respond = { category, _ -> if (category == 11) blocked.reply(rows("late-a")) else rows("b") }
            val old = launch { rig.repo.selectGroup(a) }
            try {
                blocked.entered.await()
                rig.repo.selectGroup(b)
            } finally { blocked.release() }
            old.join()
            assertEquals(b, rig.repo.selectedGroup.value)
            assertEquals(listOf("b"), rig.ids())
            rig.respond = { _, _ -> rows("fresh-a") }
            rig.repo.selectGroup(a)
            assertEquals(listOf("fresh-a"), rig.ids())
        }
    }

    @Test fun resetClearsAllGroupSnapshotsAndRejectsInflightResponse() = runBlocking {
        withTimeout(8000) {
            val rig = Rig()
            rig.twoPages()
            rig.repo.selectGroup(b)
            val blocked = Blocked()
            rig.respond = { _, _ -> blocked.reply(rows("old-owner")) }
            val old = launch { rig.repo.selectGroup(a) }
            try {
                blocked.entered.await()
                rig.owner.suspendOwner()
                rig.owner.activate("synthetic-owner-b")
                assertNull(rig.repo.selectedGroup.value)
                assertTrue(rig.repo.news.value is ListState.FirstLoad)
            } finally { blocked.release() }
            old.join()
            assertTrue(rig.repo.news.value is ListState.FirstLoad)
            val fresh = Blocked()
            rig.respond = { _, _ -> fresh.reply(rows("new-owner")) }
            val load = launch { rig.repo.selectGroup(a) }
            try {
                fresh.entered.await()
                assertTrue(rig.repo.news.value is ListState.FirstLoad)
            } finally { fresh.release() }
            load.join()
            assertEquals(listOf("new-owner"), rig.ids())
        }
    }

    @Test fun cancellationClearsBusyFlagsAndAllowsCachedGroupRetry() = runBlocking {
        withTimeout(8000) {
            val rig = Rig()
            rig.twoPages()
            val blocked = Blocked()
            rig.respond = { _, _ -> blocked.reply(rows("cancelled")) }
            val refresh = launch { rig.repo.refreshKeepingContent() }
            try {
                blocked.entered.await()
                refresh.cancelAndJoin()
            } finally { blocked.release() }
            assertEquals(listOf("a1", "a2", "a3"), rig.ids())
            assertFalse(rig.meta().refreshing)
            assertFalse(rig.meta().loadingMore)
            assertFalse(rig.meta().stale)
            rig.respond = { _, _ -> rows("retry") }
            rig.repo.selectGroup(a)
            assertEquals(listOf("retry"), rig.ids())
        }
    }

    @Test fun duplicateRefreshAndLoadMoreDoNotInvalidateActiveRefresh() = runBlocking {
        withTimeout(8000) {
            val rig = Rig()
            rig.twoPages()
            val blocked = Blocked()
            rig.requests.clear()
            rig.respond = { _, page -> if (page == 1) blocked.reply(rows("new1", "new2")) else rows("new3") }
            val refresh = launch { rig.repo.refreshKeepingContent() }
            try {
                blocked.entered.await()
                rig.repo.loadMore()
                rig.repo.refreshKeepingContent()
                rig.repo.selectGroup(a)
                assertEquals(1, rig.requests.size)
            } finally { blocked.release() }
            refresh.join()
            assertEquals(listOf("new1", "new2", "new3"), rig.ids())
            assertEquals(listOf(11 to 1, 11 to 2), rig.requests.toList())
        }
    }

    @Test fun sameGroupIdWithNewCategoryDoesNotRestoreDifferentCategoryData() = runBlocking {
        withTimeout(8000) {
            val rig = Rig()
            rig.repo.selectGroup(a)
            val blocked = Blocked()
            rig.respond = { _, _ -> blocked.reply(rows("new-category")) }
            val changed = a.copy(dataId = 33)
            val load = launch { rig.repo.selectGroup(changed) }
            try {
                blocked.entered.await()
                assertTrue(rig.repo.news.value is ListState.FirstLoad)
                assertEquals(changed, rig.repo.selectedGroup.value)
            } finally { blocked.release() }
            load.join()
            assertEquals(listOf("new-category"), rig.ids())
            assertEquals(33 to 1, rig.requests.last())
        }
    }

    @Test fun categoryWithoutDataIdIsEmptyAndDoesNotSendRequest() = runBlocking {
        val rig = Rig()
        rig.repo.selectGroup(a)
        val count = rig.requests.size
        rig.repo.selectGroup(PortalNewsGroup("missing", "Missing", null))
        rig.repo.refreshKeepingContent()
        rig.repo.loadMore()
        assertTrue(rig.repo.news.value is ListState.Empty)
        assertFalse(rig.meta().hasMore)
        assertEquals(count, rig.requests.size)
    }
}
