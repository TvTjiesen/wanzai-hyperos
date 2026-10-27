package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.TestHttpServer
import com.muyao.wanzaihyperos.net.TronclassClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **签到是写操作 ⇒ 点一次只发一次、失败绝不自动重试、失败只显服务端原话。**
 *
 * ## 为什么这条必须存在
 *
 * 671 条测试里**没有任何一条**守过它：`grep -rlniE "retry|maxAttempts" app/src/test` 只命中门户/CAS 域。
 * 一旦有人在网络层加一个统一重试拦截器（或者把这里改走 `HzcuClient` 默认的 3 次），
 * **签到会被静默重复提交**，而全量测试照样全绿 —— 这是本项目最认的失分形状。
 *
 * ## 测试替身
 *
 * 用仓库里现成的 [TestHttpServer]（JDK 自带 `com.sun.net.httpserver`），**不引 MockWebServer**。
 * `TronclassClient` 的基址是常量，所以这里用一条 OkHttp 拦截器**把主机改写到本机假服务器**，
 * **生产代码一行没改**。
 */
class RollcallWriteOnceTest {

    private lateinit var server: TestHttpServer
    private lateinit var repository: LiveRollcallRepository

    private val writeMethods = setOf("PUT", "POST", "PATCH", "DELETE")

    @Before
    fun setUp() {
        server = TestHttpServer()
        // ★ 用**生产那一支** factory（`retryOnConnectionFailure(false)` 在里面），
        //   只在其上再加一条改主机的拦截器 —— 生产改坏了这里就会跟着红。
        val rewrite = com.muyao.wanzaihyperos.net.tronHttpClient().newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val local = req.url.newBuilder()
                    .scheme("http")
                    .host("127.0.0.1")
                    .port(java.net.URI(server.baseUrl).port)
                    .build()
                chain.proceed(req.newBuilder().url(local).build())
            }
            .build()
        val client = TronclassClient(
            http = rewrite,
            accountProvider = { "2021001" },
            accessTokenProvider = { "FAKE-ACCESS-TOKEN" },
            // 已缓存 sessionID ⇒ 不会去换 token（换 token 也是写操作之外的事，这里不测）。
            cachedSessionId = { "FAKE-SESSION-ID" },
            limiter = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0),
        )
        repository = LiveRollcallRepository(client)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun writes() = server.requests.filter { it.method in writeMethods }

    @Test
    fun `成功路径只发一条PUT且打在正确的端点上`() = runTest {
        server.enqueue(status = 200, body = """{"status":true}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertTrue("成功响应应判 ok=true（消息=${r.message}）", r.ok)

        val w = writes()
        assertEquals("写请求必须**恰好一条**（GET 刷新也在同一台 server 上，所以只数写方法）", 1, w.size)
        assertEquals("PUT", w[0].method)
        assertEquals(
            "端点必须是带签到 id 的那条（官方 H5：answerQRRollcall）",
            "/api/rollcall/4242/answer_qr_rollcall",
            w[0].path,
        )
        assertTrue("包体应带扫到的原文", "SCAN-PAYLOAD" in w[0].body)
    }

    @Test
    fun `失败不重试 4xx 只发一条`() = runTest {
        server.enqueue(status = 400, body = """{"message":"该签到已截止"}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertFalse("4xx 必须判失败", r.ok)
        assertEquals("**红线**：失败不许重试，写请求仍然只发 1 条", 1, writes().size)
    }

    @Test
    fun `失败不重试 5xx 只发一条`() = runTest {
        server.enqueue(status = 500, body = """{"message":"服务器开小差了"}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertFalse("5xx 必须判失败", r.ok)
        assertEquals("**红线**：5xx 同样不许重试", 1, writes().size)
    }

    @Test
    fun `失败只显服务端原话 不许换成App自造的文案`() = runTest {
        server.enqueue(status = 400, body = """{"message":"该签到已截止"}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertTrue(
            "界面文案必须带服务端原话，实际是：${r.message}",
            "该签到已截止" in r.message,
        )
        listOf("请重试", "网络异常", "稍后再试").forEach { invented ->
            assertFalse("不许用 App 自造文案 `$invented` 顶替服务端原话：${r.message}", invented in r.message)
        }
    }
}
