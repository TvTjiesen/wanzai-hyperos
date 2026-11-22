package com.muyao.wanzaihyperos.data.repo

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.cjh52.LiveGrowthRepository
import com.muyao.wanzaihyperos.data.cjh52.LiveLectureRepository
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.portal.LivePortalSearchRepository
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class CachedRefreshSafetyTest {
    @Test fun `same owner login failure preserves content and confirmed empty metadata`() {
        val error = HzcuException.LoginExpired("synthetic login expired")
        val meta = ListMeta(refreshing = true, loadingMore = true, page = 3, hasMore = false, updatedAtMillis = 123L)
        val content = retainCachedOnFailure(ListState.Content(listOf("synthetic"), meta), error) as ListState.Content
        assertEquals(listOf("synthetic"), content.items)
        assertEquals(meta.copy(refreshing = false, loadingMore = false, stale = true, lastRefreshError = error), content.meta)
        val empty = retainCachedOnFailure(ListState.Empty(meta), error) as ListState.Empty
        assertEquals(content.meta, empty.meta)
        assertSame(error, empty.meta.lastRefreshError)
        assertSame(ListState.SessionExpired, retainCachedOnFailure(ListState.FirstLoad, error))
        assertSame(ListState.SessionExpired, retainCachedOnFailure(ListState.SessionExpired, error))
        val cancelling = HzcuException.LoginExpired("synthetic cancelled", cause = kotlinx.coroutines.CancellationException())
        val cancelled = retainCachedOnFailure(ListState.Content(listOf("synthetic"), meta), cancelling) as ListState.Content
        assertEquals(meta.copy(refreshing = false, loadingMore = false), cancelled.meta)
    }

    private class Blocked {
        val entered = CompletableDeferred<Unit>()
        private val release = CountDownLatch(1)
        fun reply(text: String): String {
            entered.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
            return text
        }
        fun close() = release.countDown()
    }

    private class Rig : AutoCloseable {
        val epoch = AccountEpoch().apply { activate("synthetic-owner") }
        var now = 123L
        @Volatile var reply: (String) -> String = { "{\"code\":200,\"rows\":[]}" }
        private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            response(chain, reply(chain.request().url.encodedPath))
        }.build()
        private val session = PortalSession(InMemorySecureStore()).apply { save("synthetic-token", "synthetic-owner", "{}") }
        private val transport = HzcuClient.create(
            session, portalBaseUrl = "https://synthetic.invalid", epoch = epoch,
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1, httpClient = http,
        ).apply { renewLog = {} }
        val lectures = LiveLectureRepository(Cjh52Client(transport, { "synthetic-cjh" }, epoch = epoch), { now }, epoch)
        val search = LivePortalSearchRepository(PortalApi(transport), { now }, epoch)
        val growth = LiveGrowthRepository(Cjh52Client(transport, { "synthetic-cjh" }, epoch = epoch), { now }, epoch)
        override fun close() {
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
        }
        companion object {
            private fun response(chain: Interceptor.Chain, body: String): Response = Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun lecture(count: Int) = """{"code":200,"data":{"data":[${(1..count).joinToString(",") {
        """{"id":"synthetic-$it","name":"Synthetic lecture","speakerName":"Synthetic"}"""
    }}]}}"""
    private fun search(count: Int) = """{"code":200,"rows":[${(1..count).joinToString(",") {
        """{"id":$it,"app_name":"Synthetic","app_url":"https://synthetic.invalid/$it"}"""
    }}]}"""

    @Test(timeout = 8000) fun `lecture login expiry preserves both cached content and confirmed empty`() = runBlocking {
        for (count in listOf(0, 1)) Rig().use { rig ->
            rig.reply = { lecture(count) }
            rig.lectures.refresh()
            val before = rig.lectures.state.value
            rig.now = 999L
            rig.reply = { "{\"code\":401,\"msg\":\"synthetic expired\"}" }
            rig.lectures.refresh()
            val after = rig.lectures.state.value
            assertEquals(before::class, after::class)
            if (before is ListState.Content) assertEquals(before.items, (after as ListState.Content).items)
            val meta = requireNotNull(after.metaOrNull)
            assertEquals(123L, meta.updatedAtMillis)
            assertTrue(meta.stale)
            assertFalse(meta.refreshing)
            assertTrue(meta.lastRefreshError is HzcuException.LoginExpired)
        }
    }

    @Test(timeout = 8000) fun `lecture cancellation ends refreshing without changing cached success`() = runBlocking {
        Rig().use { rig ->
            rig.reply = { lecture(1) }
            rig.lectures.refresh()
            val before = rig.lectures.state.value
            val blocked = Blocked()
            rig.reply = { blocked.reply(lecture(2)) }
            try {
                val pending = launch { rig.lectures.refresh() }
                blocked.entered.await()
                assertTrue(requireNotNull(rig.lectures.state.value.metaOrNull).refreshing)
                pending.cancelAndJoin()
                assertEquals(before, rig.lectures.state.value)
            } finally { blocked.close() }
        }
    }

    @Test(timeout = 8000) fun `same keyword auth failure retains content and empty but changed query does not reuse either`() = runBlocking {
        for (count in listOf(0, 1)) Rig().use { rig ->
            rig.reply = { search(count) }
            rig.search.search("first")
            val before = rig.search.state.value
            rig.now = 999L
            rig.reply = { "{\"code\":401,\"msg\":\"synthetic expired\"}" }
            rig.search.search(" first ")
            val after = rig.search.state.value
            assertEquals(before::class, after::class)
            val meta = requireNotNull(after.metaOrNull)
            assertEquals(123L, meta.updatedAtMillis)
            assertTrue(meta.stale)
            assertTrue(meta.lastRefreshError is HzcuException.LoginExpired)
            rig.search.search("second")
            assertSame(ListState.SessionExpired, rig.search.state.value)
        }
    }

    @Test(timeout = 8000) fun `current search cancellation ends busy and stale request finally cannot end newer refresh`() = runBlocking {
        Rig().use { rig ->
            rig.reply = { search(1) }
            rig.search.search("first")
            val before = rig.search.state.value
            val old = Blocked()
            val next = Blocked()
            try {
                rig.reply = { old.reply(search(2)) }
                val first = launch { rig.search.search("first") }
                old.entered.await()
                rig.reply = { next.reply(search(3)) }
                val second = launch { rig.search.search("first") }
                next.entered.await()
                first.cancelAndJoin()
                assertTrue(requireNotNull(rig.search.state.value.metaOrNull).refreshing)
                second.cancelAndJoin()
                assertEquals(before, rig.search.state.value)
                rig.epoch.activate("synthetic-other-owner")
                assertTrue(rig.search.state.value is ListState.Empty)
                assertNull(rig.search.state.value.metaOrNull?.updatedAtMillis)
            } finally { old.close(); next.close() }
        }
    }

    @Test(timeout = 8000) fun `growth auth failure keeps complete successful content and its timestamp`() = runBlocking {
        Rig().use { rig ->
            rig.reply = { path -> when (path) {
                HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH -> "{\"code\":200,\"data\":{\"user\":{\"sid\":\"7\"}}}"
                HzcuEndpoints.CJH52_KQA_SCORE_PATH -> "{\"code\":200,\"data\":{\"Q\":7,\"GY\":2,\"K\":1}}"
                HzcuEndpoints.CJH52_PHYSIQUE_PATH -> "{\"code\":200,\"data\":[]}"
                HzcuEndpoints.CJH52_ACTIVITY_MONTHS_PATH -> "{\"code\":200,\"data\":{\"results\":[]}}"
                else -> "{\"code\":200,\"data\":{}}"
            } }
            rig.growth.refresh()
            val before = rig.growth.state.value as ListState.Content
            rig.now = 999L
            rig.reply = { "{\"code\":401,\"msg\":\"synthetic expired\"}" }
            rig.growth.refresh()
            val after = rig.growth.state.value as ListState.Content
            assertEquals(before.items, after.items)
            assertEquals(before.meta.updatedAtMillis, after.meta.updatedAtMillis)
            assertTrue(after.meta.stale)
            assertFalse(after.meta.refreshing)
            assertTrue(after.meta.lastRefreshError is HzcuException.LoginExpired)
        }
    }
}
