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
        // ★ 這是**真機實樣**（業主 2026-11-04 15:16 那次真實提交，
        //   機器上 `files/tron_raw_answer_qr_resp.txt`，31 B，逐字）：
        //     {"id":null,"status":"on_call"}
        //   狀態就在**正文頂層**，沒有 `data` 這一層。
        //   官方 H5 也是這樣判的（`o` 是 axios 響應對象，`o.data` 即正文）：
        //     "on_call"===(null===(t=o.data)||void 0===t?void 0:t.status)
        //   前兩版測試用的 `{"data":{"status":…}}` 形狀**服務端從來沒回過** ——
        //   所以它們一直綠，而真機上永遠「沒簽上」。
        server.enqueue(status = 200, body = """{"id":null,"status":"on_call"}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertTrue("真机实样必须判 ok=true（消息=${r.message}）", r.ok)

        val w = writes()
        assertEquals("写请求必须**恰好一条**（GET 刷新也在同一台 server 上，所以只数写方法）", 1, w.size)
        assertEquals("PUT", w[0].method)
        assertEquals(
            "端点必须是带签到 id 的那条（官方 H5：answerQRRollcall）",
            "/api/rollcall/4242/answer_qr_rollcall",
            w[0].path,
        )
        assertTrue("包体应带扫到的原文", "SCAN-PAYLOAD" in w[0].body)
        assertTrue("包体字段必须是 data（官方 1154.js：{ data: r.data }）", "\"data\":" in w[0].body)
        assertTrue("包体必须带 deviceId（官方 1154.js：{ deviceId: D.Z.deviceId }）", "\"deviceId\":" in w[0].body)
        assertTrue("旧的错字段 code_str 不许再出现", "code_str" !in w[0].body)
    }

    @Test
    fun `顶层 status 是细粒度值时也认`() = runTest {
        // 同一支端点在别的時機會回細粒度那套（`on_call_fine` / `on_call_arrive_late`），
        // 它們都算「到了」。頂層判據不許只認 `on_call` 一個字面量。
        server.enqueue(status = 200, body = """{"id":123,"status":"on_call_fine"}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertTrue("顶层 status=on_call_fine 必须判成功：${r.message}", r.ok)
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
    @Test
    fun `HTTP 200 但服务端没记成已到 必须判失败`() = runTest {
        // 只看 HTTP 200 会把「服务端收到了但没算签到」说成成功 —— 那是替学校撒谎。
        // `absent` 在粗细两套枚举里都不属于「到了」。
        server.enqueue(status = 200, body = """{"data":{"status":"absent"}}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertFalse("缺勤不能说签到成功：${r.message}", r.ok)
        assertEquals("仍然只发一条", 1, writes().size)
        assertTrue("要告诉他服务端记成了什么，实际是：${r.message}", "缺勤" in r.message)
    }

    @Test
    fun `只回 student_status 的响应也认`() = runTest {
        // 不知道服务端回的是细粒度的 `status` 还是粗粒度的 `student_status`，
        // 两支都得认 —— 只认一支就是这次「明明签上了却说没签上」的同族 bug。
        server.enqueue(status = 200, body = """{"data":{"student_status":"on_call"}}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertTrue("student_status=on_call 必须判成功：${r.message}", r.ok)
    }

    @Test
    fun `请假的响应不算签到成功`() = runTest {
        // 请假是「没到但被批准」。判成成功等于告诉他「你签上了」，而他根本没出席。
        server.enqueue(status = 200, body = """{"data":{"status":"on_sick_leave"}}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertFalse("病假不能算签到成功：${r.message}", r.ok)
        assertTrue("要说出是病假，实际是：${r.message}", "病假" in r.message)
    }

    @Test
    fun `服务端的 error_code 也要翻成人话`() = runTest {
        // 服务端错误码键是带下划线的 `error_code`（axios 拦截器里逐字读过），
        // 只认 message/msg/error 会让「二维码过期」变成一个空兜底。
        server.enqueue(status = 400, body = """{"error_code":"qr_code_expired"}""")
        val r = repository.answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertFalse("过期必须判失败", r.ok)
        assertTrue("error_code 要翻成人话，实际是：${r.message}", "过期" in r.message)
    }

    @Test
    fun `deviceId 那边抛异常也必须把请求发出去`() = runTest {
        // 真机现场（业主 2026-11-03）：`files/` 里**连 answer 的 dump 都没有**，
        // 说明那次根本没发请求。唯一在发请求之前、又能抛的，就是 deviceId 提供方
        // （加密存储读写）。这里把它钉死：提供方炸了也要用兜底 uuid 发出去，
        // 因为「发出去拿到服务端一句真话」永远比「本地炸掉、什么都不留」有用。
        val throwing = com.muyao.wanzaihyperos.net.tronHttpClient().newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val local = req.url.newBuilder()
                    .scheme("http").host("127.0.0.1")
                    .port(java.net.URI(server.baseUrl).port).build()
                chain.proceed(req.newBuilder().url(local).build())
            }
            .build()
        val c = TronclassClient(
            http = throwing,
            accountProvider = { "2021001" },
            accessTokenProvider = { "FAKE-ACCESS-TOKEN" },
            cachedSessionId = { "FAKE-SESSION-ID" },
            limiter = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0),
            deviceIdProvider = { error("加密存储读不出来") },
        )
        server.enqueue(status = 200, body = """{"data":{"status":"on_call_fine"}}""")
        val r = LiveRollcallRepository(c).answer(4242, RollcallAnswerKind.QR, "SCAN-PAYLOAD")
        assertEquals("请求必须真的发出去", 1, writes().size)
        assertTrue("包体里仍要有 deviceId（兜底值也算）", "\"deviceId\":" in writes()[0].body)
        assertTrue("服务端记成 on_call_fine 就是成功，实际消息：${r.message}", r.ok)
    }

    // ---------------- 提交后按考勤记录核对（只读，绝不重发写请求） ----------------

    @Test
    fun `核对：进行中清单里已经是到了 就返回那个状态`() = runTest {
        // ★ 真机现场（2026-11-04 15:16 那次提交，机器上 `files/http_cache/03c28bac…json`）：
        //   `/api/radar/rollcalls` 的每条记录里，`rollcall_status` 是**整场点名**的状态，
        //   顶层 `status` 才是**我**的状态（未签时是 `absent`，签上后变 `on_call`）。
        //   这条路径**不需要**先拿学号：顶层 status 就是我的。
        server.enqueue(
            status = 200,
            // 真實外層是 `{"rollcalls":[...]}`（`requestJson` 只接對象，接不了裸數組）。
            // 欄位形狀照抄 2026-11-04 那次真機原文，只把 `status` 從 absent 改成簽上之後的值。
            body = """{"rollcalls":[{"id":252446,"course_id":62045,"rollcall_status":"in_progress","status":"on_call","source":"qr","is_expired":false}]}""",
        )
        val got = repository.verifyAttendance(252446, 62045)
        assertEquals("on_call", got)
        assertEquals("一条 /api/radar/rollcalls 就够，不许再去问 profile", 1, server.requestCount)
        assertEquals("核对是只读的：一条写请求都不许有", 0, writes().size)
    }

    @Test
    fun `核对：清单里查不到才回落到课程考勤记录`() = runTest {
        // 老师刚提交完就结束点名的情形：清单里已经没有了，但考勤记录里会有。
        // `/api/profile` 的 `id` 在**顶层**（`profileUserId()` 读 `root["id"]`）。
        server.enqueue(status = 200, body = """{"rollcalls":[]}""")
        server.enqueue(status = 200, body = """{"id":36519}""")
        server.enqueue(
            status = 200,
            body = """{"rollcalls":[{"rollcall_id":4242,"student_status":"on_call","source":"qr"}]}""",
        )
        val got = repository.verifyAttendance(4242, 67436)
        assertEquals("on_call", got)
        assertEquals("核对是只读的：一条写请求都不许有", 0, writes().size)
    }

    @Test
    fun `核对：两处都查不到就返回 null 而不是猜`() = runTest {
        server.enqueue(status = 200, body = """{"rollcalls":[]}""")
        server.enqueue(status = 200, body = """{"id":36519}""")
        server.enqueue(status = 200, body = """{"rollcalls":[]}""")
        assertEquals("查不到就是「核对不了」，不是「没签上」", null, repository.verifyAttendance(4242, 67436))
    }

    @Test
    fun `核对：取数失败也返回 null 不许改口说没签上`() = runTest {
        server.enqueue(status = 500, body = """{"message":"服务器开小差了"}""")
        server.enqueue(status = 500, body = """{"message":"服务器开小差了"}""")
        assertEquals("核对失败只能是 null", null, repository.verifyAttendance(4242, 67436))
        assertEquals("核对失败也不许重发写请求", 0, writes().size)
    }

    @Test
    fun `核对：课程 id 缺失时最多查一下清单 不回去翻考勤记录`() = runTest {
        // `courseId == 0` 是扫描器合成的占位记录常用的值：没有课程就无处可翻考勤，
        // 但清单那条路**仍然有意义**（它不需要 courseId）。
        server.enqueue(status = 200, body = """{"rollcalls":[]}""")
        assertEquals(null, repository.verifyAttendance(4242, 0))
        assertEquals("没有 courseId 就不该再发第二个请求", 1, server.requestCount)
    }
}
