package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
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
 * 「愛講座」刷新必须**整段窗口**重取，不能退回第 1 页。
 *
 * 这一条是易错点：旧实现里 refresh 只 fetch(1)，然后把状态换成第一页，
 * 用户翻到第 2 页再下拉刷新，屏幕上会突然只剩 20 条（已经加载的一页凭空消失），
 * 而且 page 被重置成 1，下一次「加载更多」会把第 2 页再取一遍。
 */
class LectureWindowCacheTest {

    private fun items(page: Int, count: Int): String = (0 until count).joinToString(",") {
        """{"id":"p$page-$it","name":"Lecture $page-$it","speakerName":"Synthetic",
           "startTime":1792204200000,"endTime":1792211400000,
           "registrationNumber":30,"normalRegisteredCount":4}"""
    }

    private fun envelope(page: Int, count: Int, code: Int = 200, msg: String = "ok"): String =
        """{"code":$code,"msg":"$msg","data":{"total":100,"pageSize":20,"pageNum":$page,"data":[${items(page, count)}]}}"""

    private inner class Rig : AutoCloseable {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }
        val calls = CopyOnWriteArrayList<Int>()

        @Volatile
        var reply: (Int) -> String = { page -> envelope(page, if (page <= 2) 20 else 1) }

        private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            val page = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                .getValue("pageNum").jsonPrimitive.int
            calls += page
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(reply(page).toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        private val session = PortalSession(InMemorySecureStore()).apply {
            save("synthetic-portal", "synthetic-A", "{}")
        }

        val repo = LiveLectureRepository(
            cjh52 = Cjh52Client(
                transport = HzcuClient.create(
                    session,
                    portalBaseUrl = "https://synthetic.invalid",
                    epoch = epoch,
                    rateLimiter = HzcuRateLimiter.forTesting(0, 0),
                    maxAttempts = 1,
                    httpClient = http,
                ).apply { renewLog = {} },
                tokenProvider = { "synthetic-token" },
                epoch = epoch,
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

    @Test fun `refresh reloads the whole loaded window instead of collapsing to page one`() = runBlocking {
        withTimeout(8000) {
            Rig().use { rig ->
                rig.repo.refresh()
                rig.repo.loadMore()
                assertEquals(40, rig.ids().size)
                assertEquals(2, rig.meta().page)
                assertTrue(rig.meta().hasMore)

                rig.calls.clear()
                rig.repo.refresh()

                assertEquals(listOf(1, 2), rig.calls.toList())
                assertEquals(40, rig.ids().size)
                assertEquals(2, rig.meta().page)
                assertTrue(rig.meta().hasMore)
                assertFalse(rig.meta().refreshing)
                assertEquals(123L, rig.meta().updatedAtMillis)
                assertEquals("p1-0", rig.ids().first())
                assertEquals("p2-19", rig.ids().last())
            }
        }
    }

    @Test fun `failed window refresh keeps the old window and its page count`() = runBlocking {
        withTimeout(8000) {
            Rig().use { rig ->
                rig.repo.refresh()
                rig.repo.loadMore()
                assertEquals(40, rig.ids().size)

                rig.reply = { page ->
                    if (page == 2) envelope(2, 0, code = 500, msg = "synthetic")
                    else envelope(page, 20)
                }
                rig.repo.refresh()

                val kept = rig.content()
                assertEquals(40, kept.items.size)
                assertTrue(kept.meta.stale)
                assertNotNull(kept.meta.lastRefreshError)
                assertFalse(kept.meta.refreshing)

                // ★ page 没有被重置：接下来 loadMore 取的是第 3 页，而不是把第 2 页再取一遍。
                rig.calls.clear()
                rig.reply = { page -> envelope(page, if (page == 3) 1 else 20) }
                rig.repo.loadMore()
                assertEquals(listOf(3), rig.calls.toList())
                assertEquals(41, rig.ids().size)
                assertEquals(3, rig.meta().page)
                assertFalse(rig.meta().hasMore)
            }
        }
    }

    @Test fun `window refresh that shrinks keeps only what the server still returns`() = runBlocking {
        withTimeout(8000) {
            Rig().use { rig ->
                rig.repo.refresh()
                rig.repo.loadMore()
                assertEquals(40, rig.ids().size)

                rig.reply = { page -> envelope(page, if (page == 2) 5 else 20) }
                rig.repo.refresh()

                assertEquals(25, rig.ids().size)
                assertEquals(2, rig.meta().page)
                assertFalse(rig.meta().hasMore)
                assertFalse(rig.meta().stale)
            }
        }
    }
}