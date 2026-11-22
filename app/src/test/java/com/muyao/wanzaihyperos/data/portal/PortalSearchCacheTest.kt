package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同一个关键词再搜 = **后台刷新**，不是重新开始。
 *
 * 旧实现每次 search 都把状态写成 [ListState.FirstLoad]，所以点开某个应用再返回、
 * 或者下拉刷新时，屏幕上已经搜到的结果会先整片消失、再重新出现。
 * 换关键词才应该显示加载态（结果本来就不属于新词）。
 */
class PortalSearchCacheTest {

    private fun rows(keyword: String, count: Int): String =
        """{"code":200,"rows":[${(1..count).joinToString(",") {
            """{"id":$it,"app_name":"$keyword-$it","app_url":"https://synthetic/$keyword/$it"}"""
        }}]}"""

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

    private inner class Rig : AutoCloseable {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }
        val calls = CopyOnWriteArrayList<String>()

        @Volatile
        var reply: (String) -> String = { keyword -> rows(keyword, 1) }

        private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            val keyword = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                .getValue("appName").jsonPrimitive.content
            calls += keyword
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(reply(keyword).toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        private val session = PortalSession(InMemorySecureStore()).apply {
            save("synthetic-portal", "synthetic-A", "{}")
        }

        val repo = LivePortalSearchRepository(
            api = PortalApi(
                HzcuClient.create(
                    session,
                    portalBaseUrl = "https://synthetic.invalid",
                    epoch = epoch,
                    rateLimiter = HzcuRateLimiter.forTesting(0, 0),
                    maxAttempts = 1,
                    httpClient = http,
                ),
            ),
            clock = { 123L },
            epoch = epoch,
        )

        fun content() = repo.state.value as ListState.Content
        fun ids() = content().items.map { it.id }
        fun meta() = requireNotNull(repo.state.value.metaOrNull)

        override fun close() {
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
        }
    }

    @Test fun `same keyword refreshes in the background and keeps existing rows visible`() = runBlocking {
        withTimeout(8000) {
            Rig().use { rig ->
                rig.repo.search("aa")
                assertEquals(listOf(1), rig.ids())

                val blocked = Blocked()
                rig.reply = { keyword -> blocked.reply(rows(keyword, 2)) }
                val job = launch { rig.repo.search("aa") }
                blocked.entered.await()

                // 刷新途中：还是同一批结果，只是标了 refreshing —— 不是空屏。
                assertEquals(listOf(1), rig.ids())
                assertTrue(rig.meta().refreshing)

                blocked.release()
                job.join()

                assertEquals(listOf(1, 2), rig.ids())
                assertFalse(rig.meta().refreshing)
                assertEquals(listOf("aa", "aa"), rig.calls.toList())
            }
        }
    }

    @Test fun `different keyword still starts from a loading state`() = runBlocking {
        withTimeout(8000) {
            Rig().use { rig ->
                rig.repo.search("aa")
                assertEquals(listOf(1), rig.ids())

                val blocked = Blocked()
                rig.reply = { keyword -> blocked.reply(rows(keyword, 3)) }
                val job = launch { rig.repo.search("bb") }
                blocked.entered.await()

                assertTrue("换新词应先进入加载态", rig.repo.state.value is ListState.FirstLoad)

                blocked.release()
                job.join()
                assertEquals(listOf(1, 2, 3), rig.ids())
                assertEquals("搜索结果", rig.content().items.first().groupName)
            }
        }
    }

    @Test fun `failed same keyword refresh keeps rows and records the real error`() = runBlocking {
        withTimeout(8000) {
            Rig().use { rig ->
                rig.repo.search("aa")
                assertEquals(listOf(1), rig.ids())

                rig.reply = { throw IOException("synthetic") }
                rig.repo.search("aa")

                val kept = rig.content()
                assertEquals(listOf(1), kept.items.map { it.id })
                assertTrue(kept.meta.stale)
                assertNotNull(kept.meta.lastRefreshError)
                assertFalse(kept.meta.refreshing)
            }
        }
    }
}