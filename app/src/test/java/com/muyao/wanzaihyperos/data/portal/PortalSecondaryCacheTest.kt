package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.*
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
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

class PortalSecondaryCacheTest {
    private fun owner() = AccountEpoch().apply { activate("synthetic-owner") }
    private fun bills(vararg ids: String) = """{"code":200,"rows":[${ids.joinToString(",") {
        """{"billid":"$it","billname":"Bill $it"}"""
    }}]}"""
    private fun fullBills() = bills(*(1..10).map { "old$it" }.toTypedArray())
    private fun response(text: String): HzcuResponse {
        val raw = Json.parseToJsonElement(text).jsonObject
        return HzcuResponse(200, true, "", raw["data"], raw)
    }
    private fun body(text: String) = response("""{"data":{"content":"$text"}}""")
    private val item = PortalBroadcastItem("7", "A", "", "")

    private class Blocked {
        val entered = CompletableDeferred<Unit>()
        private val released = CountDownLatch(1)
        fun reply(text: String): String {
            entered.complete(Unit)
            check(released.await(5, TimeUnit.SECONDS)) { "Synthetic response was not released" }
            return text
        }
        fun release() = released.countDown()
    }

    private inner class BillRig : AutoCloseable {
        val epoch = owner()
        val calls = CopyOnWriteArrayList<Pair<PortalBillBucket, Int>>()
        @Volatile var reply: (PortalBillBucket, Int) -> String = { _, page -> if (page == 1) fullBills() else bills("tail") }
        private val session = PortalSession(InMemorySecureStore()).apply { save("synthetic", "owner", "{}") }
        private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val bucket = PortalBillBucket.values().single { it.path == request.url.encodedPath }
            val buffer = Buffer(); request.body!!.writeTo(buffer)
            val page = Json.parseToJsonElement(buffer.readUtf8()).jsonObject.getValue("page").jsonPrimitive.int
            calls += bucket to page
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply(bucket, page).toResponseBody()).build()
        }.build()
        val repo = LivePortalBillRepository(PortalApi(HzcuClient.create(session,
            portalBaseUrl = "https://synthetic.invalid", httpClient = http,
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1)), clock = { 123L }, epoch = epoch)
        fun ids() = (repo.state.value as ListState.Content).items.map { it.id }
        fun meta() = requireNotNull(repo.state.value.metaOrNull)
        suspend fun window() { repo.refresh(); repo.loadMore(); assertEquals(2, meta().page) }
        override fun close() { http.dispatcher.executorService.shutdownNow(); http.connectionPool.evictAll() }
    }

    @Test fun billBucketReturnRestoresWindowAndCoalescesSameBucketReads() = runBlocking {
        withTimeout(8000) {
            BillRig().use { r ->
                r.window()
                r.repo.selectBucket(PortalBillBucket.APPLIED)
                val blocked = Blocked(); r.calls.clear()
                r.reply = { _, page -> if (page == 1) blocked.reply(fullBills()) else bills("new-tail") }
                val load = launch { r.repo.selectBucket(PortalBillBucket.TODO) }
                try {
                    blocked.entered.await()
                    assertEquals("tail", r.ids().last())
                    assertEquals(2, r.meta().page)
                    assertTrue(r.meta().refreshing)
                    r.repo.refresh(); r.repo.selectBucket(PortalBillBucket.TODO); r.repo.loadMore()
                    assertEquals(1, r.calls.size)
                } finally { blocked.release() }
                load.join()
                assertEquals(listOf(PortalBillBucket.TODO to 1, PortalBillBucket.TODO to 2), r.calls.toList())
                assertEquals("new-tail", r.ids().last())
                assertFalse(r.meta().refreshing)
            }
        }
    }

    @Test fun billSecondWindowPageFailurePreservesOldWindowCursorAndTime() = runBlocking {
        BillRig().use { r ->
            r.window()
            val old = r.ids()
            r.reply = { _, page -> if (page == 1) fullBills() else throw IOException("Synthetic offline") }
            r.repo.refresh()
            assertEquals(old, r.ids())
            assertEquals(2, r.meta().page)
            assertEquals(123L, r.meta().updatedAtMillis)
            assertFalse(r.meta().hasMore)
            assertTrue(r.meta().stale)
            assertNotNull(r.meta().lastRefreshError)
            assertFalse(r.meta().refreshing)
        }
    }

    @Test fun billDedupUsesRawRowCountAndFailedPageRetriesSameCursor() = runBlocking {
        BillRig().use { r ->
            r.reply = { _, page -> if (page == 1) bills(*Array(10) { "same" }) else throw IOException("Synthetic offline") }
            r.repo.refresh()
            assertEquals(listOf("same"), r.ids())
            assertTrue(r.meta().hasMore)
            r.repo.loadMore()
            assertEquals(1, r.meta().page)
            assertFalse(r.meta().loadingMore)
            r.reply = { _, _ -> bills("same", "next") }
            r.repo.loadMore()
            assertEquals(listOf("same", "next"), r.ids())
            assertEquals(2, r.meta().page)
            assertFalse(r.meta().hasMore)
            assertEquals(listOf(1, 2, 2), r.calls.map { it.second })
        }
    }

    @Test fun billSuccessfulShortWindowDropsTailAndEmptyBucketIsCached() = runBlocking {
        withTimeout(8000) {
            BillRig().use { r ->
                r.window(); r.calls.clear()
                r.reply = { _, _ -> bills() }
                r.repo.refresh()
                assertTrue(r.repo.state.value is ListState.Empty)
                assertEquals(1, r.meta().page)
                assertFalse(r.meta().hasMore)
                assertEquals(listOf(1), r.calls.map { it.second })
                r.repo.selectBucket(PortalBillBucket.APPLIED)
                val blocked = Blocked()
                r.reply = { _, _ -> blocked.reply(bills()) }
                val load = launch { r.repo.selectBucket(PortalBillBucket.TODO) }
                try { blocked.entered.await(); assertTrue(r.repo.state.value is ListState.Empty) }
                finally { blocked.release() }
                load.join()
            }
        }
    }

    @Test fun billAccountResetRejectsLateResponseAndClearsAllBucketCaches() = runBlocking {
        withTimeout(8000) {
            BillRig().use { r ->
                r.window()
                val blocked = Blocked()
                r.reply = { _, _ -> blocked.reply(bills("old-owner")) }
                val load = launch { r.repo.refresh() }
                try { blocked.entered.await(); r.epoch.activate("other-owner") }
                finally { blocked.release() }
                load.join()
                assertEquals(ListState.FirstLoad, r.repo.state.value)
                r.reply = { _, _ -> throw IOException("Synthetic offline") }
                r.repo.selectBucket(PortalBillBucket.TODO)
                assertTrue(r.repo.state.value is ListState.Failed)
            }
        }
    }

    @Test fun billCancellationRetainsWindowClearsFlagsAndAllowsRetry() = runBlocking {
        withTimeout(8000) {
            BillRig().use { r ->
                r.window()
                val blocked = Blocked()
                r.reply = { _, _ -> blocked.reply(fullBills()) }
                val load = launch { r.repo.refresh() }
                try { blocked.entered.await(); load.cancelAndJoin() } finally { blocked.release() }
                assertEquals("tail", r.ids().last())
                assertFalse(r.meta().refreshing)
                assertNull(r.meta().lastRefreshError)
                r.reply = { _, _ -> bills("retried") }
                r.repo.refresh()
                assertEquals(listOf("retried"), r.ids())
            }
        }
    }

    @Test fun billLateOtherBucketCannotReplaceReturnedBucketWindow() = runBlocking {
        withTimeout(8000) {
            BillRig().use { r ->
                r.window()
                val blocked = Blocked()
                r.reply = { bucket, page ->
                    if (bucket == PortalBillBucket.APPLIED) blocked.reply(bills("late-applied"))
                    else if (page == 1) fullBills() else bills("new-tail")
                }
                val old = launch { r.repo.selectBucket(PortalBillBucket.APPLIED) }
                try {
                    blocked.entered.await()
                    r.repo.selectBucket(PortalBillBucket.TODO)
                } finally { blocked.release() }
                old.join()
                assertEquals(PortalBillBucket.TODO, r.repo.bucket.value)
                assertEquals("new-tail", r.ids().last())
                assertEquals(2, r.meta().page)
            }
        }
    }

    @Test fun billLoginExpiredPreservesCachedContentButUncachedShowsSessionExpired() = runBlocking {
        BillRig().use { r ->
            r.window()
            r.reply = { _, _ -> """{"code":401,"message":"Synthetic expired"}""" }
            r.repo.refresh()
            assertEquals("tail", r.ids().last())
            assertTrue(r.meta().lastRefreshError is HzcuException.LoginExpired)
            assertTrue(r.meta().stale)
            assertFalse(r.meta().refreshing)
            r.repo.selectBucket(PortalBillBucket.FINISHED)
            assertEquals(ListState.SessionExpired, r.repo.state.value)
        }
    }

    @Test fun broadcastCloseReopenRestoresAndRefreshesSameIdOnce() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        val repo = LivePortalBroadcastRepository({ response("""{"rows":[]}""") }, {
            calls++
            if (calls == 2) { entered.complete(Unit); release.await() }
            body(if (calls == 1) "old" else "new")
        }, clock = { 100L }, epoch = owner())
        repo.open(item); repo.closeDetail()
        assertEquals(ListState.FirstLoad, repo.detail.value)
        val updated = item.copy(title = "Renamed")
        val load = launch { repo.open(updated) }; entered.await()
        val restored = repo.detail.value as ListState.Content
        assertEquals("old", restored.items.single().content)
        assertEquals(updated, restored.items.single().item)
        assertTrue(restored.meta.refreshing)
        repo.open(updated); assertEquals(2, calls)
        release.complete(Unit); load.join()
        assertEquals("new", (repo.detail.value as ListState.Content).items.single().content)
    }

    @Test fun broadcastCachedDetailAuthFailureRetainsContentAndSuccessfulTime() = runBlocking {
        var expired = false
        val repo = LivePortalBroadcastRepository({ response("""{"rows":[]}""") }, {
            if (expired) throw HzcuException.LoginExpired("Synthetic expired")
            body("old")
        }, clock = { 200L }, epoch = owner())
        repo.open(item); expired = true; repo.closeDetail(); repo.open(item)
        val state = repo.detail.value as ListState.Content
        assertEquals("old", state.items.single().content)
        assertEquals(200L, state.meta.updatedAtMillis)
        assertTrue(state.meta.lastRefreshError is HzcuException.LoginExpired)
        assertFalse(state.meta.refreshing)
        repo.open(item.copy(id = "8"))
        assertEquals(ListState.SessionExpired, repo.detail.value)
        expired = false; repo.open(item)
        assertFalse(repo.detail.value.metaOrNull!!.stale)
    }

    @Test fun broadcastLateOtherIdCannotOverwriteReopenedCachedDetail() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repo = LivePortalBroadcastRepository({ response("""{"rows":[]}""") }, { id ->
            if (id == "8") { entered.complete(Unit); release.await() }
            body(id)
        }, epoch = owner())
        repo.open(item)
        val old = launch { repo.open(item.copy(id = "8")) }; entered.await()
        repo.closeDetail(); repo.open(item); release.complete(Unit); old.join()
        assertEquals(item, (repo.detail.value as ListState.Content).items.single().item)
        assertEquals("7", (repo.detail.value as ListState.Content).items.single().content)
    }

    @Test fun broadcastCancelledDetailRefreshKeepsCacheAndAllowsRetry() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var calls = 0
        val repo = LivePortalBroadcastRepository({ response("""{"rows":[]}""") }, {
            calls++; if (calls == 2) { entered.complete(Unit); awaitCancellation() }
            body("old")
        }, epoch = owner())
        repo.open(item)
        val load = launch { repo.open(item) }; entered.await(); load.cancelAndJoin()
        assertEquals("old", (repo.detail.value as ListState.Content).items.single().content)
        assertFalse(repo.detail.value.metaOrNull!!.refreshing)
        assertNull(repo.detail.value.metaOrNull!!.lastRefreshError)
        repo.open(item); assertEquals(3, calls)
    }

    @Test fun broadcastResetClearsDetailCacheAndRejectsInflightResponse() = runBlocking {
        val epoch = owner()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        var failNext = false
        val repo = LivePortalBroadcastRepository({ response("""{"rows":[]}""") }, {
            calls++; if (calls == 2) { entered.complete(Unit); release.await() }
            if (failNext) throw IOException("Synthetic offline")
            body("old-owner")
        }, epoch = epoch)
        repo.open(item)
        val load = launch { repo.open(item) }; entered.await(); epoch.activate("other-owner")
        release.complete(Unit); load.join()
        assertEquals(ListState.FirstLoad, repo.detail.value)
        repo.closeDetail()
        failNext = true
        repo.open(item)
        assertTrue(repo.detail.value is ListState.Failed)
    }

    @Test fun broadcastListAuthFailureRetainsListAndRetryClearsError() = runBlocking {
        var expired = false
        val repo = LivePortalBroadcastRepository({
            if (expired) throw HzcuException.LoginExpired("Synthetic expired")
            response("""{"rows":[{"id":"7","title":"A"}]}""")
        }, { body("body") }, epoch = owner())
        repo.refresh(); expired = true; repo.refresh()
        assertTrue(repo.state.value is ListState.Content)
        assertTrue(repo.state.value.metaOrNull!!.lastRefreshError is HzcuException.LoginExpired)
        expired = false; repo.refresh()
        assertNull(repo.state.value.metaOrNull!!.lastRefreshError)
        assertFalse(repo.state.value.metaOrNull!!.stale)
    }
}
