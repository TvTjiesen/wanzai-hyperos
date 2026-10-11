package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CAS 登录链的**端到端离线验证**。
 *
 * 这是本檔案存在的主要理由：把整条链（取登录页 → 解析 fm1 → AES 加密提交 →
 * 跟跳转 → 跑探针 → 落 token 与 cookie）指向一台本机假服务器，
 * 于是**不需要真实账号、不碰学校服务器**就能验证它是对的。
 *
 * 真机联调只需要换掉 [HzcuCasService.create] 的 `pageUriBuilder`，
 * 链上其余部分已经被这里覆盖过。
 */
class HzcuCasServiceTest {

    private lateinit var server: TestHttpServer

    private val account = "2021001"
    private val password = "Passw0rd!"
    private val token = "0123456789abcdef0123456789abcdef"

    private val serviceBase: String get() = server.baseUrl + "/portal"

    @Before
    fun setUp() {
        server = TestHttpServer()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    // ---------------------------------------------------------------------------
    // 夹具
    // ---------------------------------------------------------------------------

    /** 移动模板：**没有 validTime、多一个 randomStr** —— App 真机拿到的就是这一套。 */
    private fun casPage(action: String, extraField: String = """<input name="randomStr" value="">""") =
        """
        <html><body>
        <form id="fm1" method="post" action="$action">
            <input name="username" value="">
            <input name="password" value="">
            <input name="authType" value="0">
            <input name="lt" value="">
            <input name="execution" value="e1s1">
            <input name="_eventId" value="submit">
            $extraField
        </form>
        </body></html>
        """.trimIndent()

    /** CAS 认证失败时回的是 200 + 同一个登录页 + 一个错误框（**不是** 302）。 */
    private fun casPageWithError(action: String, sentence: String) =
        """
        <html><body>
        <div class="div_errorMsgBox">$sentence</div>
        <form id="fm1" method="post" action="$action">
            <input name="username" value="">
            <input name="password" value="">
            <input name="authType" value="0">
            <input name="lt" value="">
            <input name="execution" value="e1s1">
            <input name="_eventId" value="submit">
            <input name="randomStr" value="">
        </form>
        </body></html>
        """.trimIndent()

    private fun service(session: PortalSession): HzcuCasService {
        val client = HzcuClient.create(
            credentials = session,
            portalBaseUrl = server.baseUrl,
            rateLimiter = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0),
            maxAttempts = 3,
            backoffBaseMs = 0,
        )
        return HzcuCasService.create(
            client = client,
            session = session,
            pageUriBuilder = { target ->
                server.baseUrl + "/cas/login?service=" + URLEncoder.encode(target, "UTF-8")
            },
        )
    }

    /** 把 `a=1&b=2` 形态的提交体解回 map（OkHttp 会对 Base64 里的 +/= 做百分号编码）。 */
    private fun decodeForm(body: String): Map<String, String> =
        body.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val equals = pair.indexOf('=')
            val key = if (equals < 0) pair else pair.substring(0, equals)
            val value = if (equals < 0) "" else pair.substring(equals + 1)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    // ---------------------------------------------------------------------------
    // 成功链
    // ---------------------------------------------------------------------------

    @Test
    fun `完整登录链：取表单 加密提交 跟跳 命中令牌 落盘`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        val action = "/cas/login;jsessionid=FAKE123?service=x"
        server.enqueue(200, casPage(action), contentType = "text/html;charset=UTF-8")
        server.enqueue(
            302,
            "",
            contentType = "text/plain",
            headers = mapOf(
                "Location" to "${server.baseUrl}/portal?token=$token",
                "Set-Cookie" to "SESSION=s-1; Path=/",
            ),
        )

        val result = service(session).login(account, password, service = serviceBase)

        assertEquals(token, result.accessToken)
        assertEquals(account, result.userCode)
        // 命中的是 query 探针 —— 真机第一次跑通就能知道答案，不用再猜。
        assertEquals("probeTokenInQuery", result.probeName)
        // 一次 GET 登录页 + 一次 POST，**没有多余请求**（QPS 红线）。
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `成功后 token 与会话 cookie 一起进加密存储`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        server.enqueue(200, casPage("/cas/login;jsessionid=FAKE123?service=x"), contentType = "text/html")
        server.enqueue(
            302,
            "",
            contentType = "text/plain",
            headers = mapOf(
                "Location" to "${server.baseUrl}/portal?token=$token",
                // ★ 门户的 token 与会话 cookie 是绑定的：只落 token 会全部 401。
                "Set-Cookie" to "SESSION=s-1; Path=/",
            ),
        )

        service(session).login(account, password, service = serviceBase)

        assertEquals(token, session.accessToken())
        val cookies = session.portalCookiesJson()
        assertNotNull("cookie 没有被一起落盘，业务接口会全部 401", cookies)
        assertTrue(cookies!!.contains("SESSION"))
    }

    @Test
    fun `提交的是 AES 密文而不是明文`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        server.enqueue(200, casPage("/cas/login;jsessionid=FAKE123?service=x"), contentType = "text/html")
        server.enqueue(
            302,
            "",
            contentType = "text/plain",
            headers = mapOf("Location" to "${server.baseUrl}/portal?token=$token"),
        )

        service(session).login(account, password, service = serviceBase)

        val posted = server.requests[1]
        assertEquals("POST", posted.method)

        // 红线：明文密码绝不允许出现在提交体里（这是一条 HTTP 明文链路）。
        assertFalse("明文密码被发出去了", posted.body.contains(password))

        val form = decodeForm(posted.body)
        assertEquals(account, form["username"])
        // 逐字等于学校前端 crypto-js 的算法产物。
        assertEquals(HzcuCasCrypto.encryptPassword(password), form["password"])
        // 页面给的隐藏域全部原样带上（以后学校加 CSRF 字段也不用改代码）。
        assertEquals("e1s1", form["execution"])
        assertEquals("submit", form["_eventId"])
        assertEquals("0", form["authType"])
        assertTrue(form.containsKey("randomStr"))
    }

    @Test
    fun `提交地址按页面 action 走，含 jsessionid`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        val action = "/cas/login;jsessionid=AAAA1111.1425?service=x"
        server.enqueue(200, casPage(action), contentType = "text/html")
        server.enqueue(
            302,
            "",
            contentType = "text/plain",
            headers = mapOf("Location" to "${server.baseUrl}/portal?token=$token"),
        )

        service(session).login(account, password, service = serviceBase)

        val posted = server.requests[1]
        assertTrue(
            "丢了 ;jsessionid 就提交不回同一个会话：${posted.path}",
            posted.path.contains(";jsessionid=AAAA1111.1425"),
        )
    }

    @Test
    fun `桌面模板（带 validTime）也能登录`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        server.enqueue(
            200,
            casPage("/cas/login;jsessionid=D?service=x", extraField = """<input name="validTime" value="5">"""),
            contentType = "text/html",
        )
        server.enqueue(
            302,
            "",
            contentType = "text/plain",
            headers = mapOf("Location" to "${server.baseUrl}/portal?token=$token"),
        )

        val result = service(session).login(account, password, service = serviceBase)

        assertEquals(token, result.accessToken)
        assertEquals("5", decodeForm(server.requests[1].body)["validTime"])
    }

    /** token 也可能由门户的 JSON 信封给出（另一个探针），这条走的是那条路。 */
    @Test
    fun `令牌在 JSON 信封里时也能命中`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        server.enqueue(200, casPage("/cas/login;jsessionid=D?service=x"), contentType = "text/html")
        server.enqueue(
            302,
            "",
            contentType = "text/plain",
            // Location 不带 token，所以必须真去 GET 落地页。
            headers = mapOf("Location" to "${server.baseUrl}/portal"),
        )
        server.enqueue(200, """{"code":200,"success":true,"message":"","data":"$token"}""")

        val result = service(session).login(account, password, service = serviceBase)

        assertEquals(token, result.accessToken)
        assertEquals("probeTokenInJsonEnvelope", result.probeName)
        assertEquals("不该多打一次请求", 3, server.requestCount)
    }

    // ---------------------------------------------------------------------------
    // 失败链
    // ---------------------------------------------------------------------------

    /** ★ 必须与「token 来源未证实」分开，否则排查方向会被带偏。 */
    @Test
    fun `密码错误时给出中文原因且不落盘`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        val action = "/cas/login;jsessionid=D?service=x"
        server.enqueue(200, casPage(action), contentType = "text/html")
        // 移动模板给的是**英文**报错（实测原文）。
        server.enqueue(
            200,
            casPageWithError(action, "The credentials you provided cannot be determined to be authentic."),
            contentType = "text/html",
        )

        val error = runCatching {
            service(session).login(account, "wrong-password", service = serviceBase)
        }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.AuthenticationRejected)
        assertTrue(
            "要把英文原文翻成看得懂的中文：${error?.message}",
            error?.message!!.contains("学号或密码不正确"),
        )
        assertNull("认证失败绝不能落盘", session.accessToken())
        // 只发一次 GET + 一次 POST：失败不重试（重试会拿同一个错密码反复敲门）。
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `账号锁定被单独识别`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        val action = "/cas/login;jsessionid=D?service=x"
        server.enqueue(200, casPage(action), contentType = "text/html")
        server.enqueue(
            200,
            casPageWithError(action, "Account is locked out."),
            contentType = "text/html",
        )

        val error = runCatching {
            service(session).login(account, password, service = serviceBase)
        }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.AuthenticationRejected)
        assertTrue(error?.message!!.contains("锁定"))
    }

    /**
     * ★ 全部探针落空 ≠ 密码错。报「token 来源未证实」并交出**脱敏**链条。
     */
    @Test
    fun `探针全部落空时报 token 来源未证实且链条已脱敏`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        server.enqueue(200, casPage("/cas/login;jsessionid=SECRETSESSION?service=x"), contentType = "text/html")
        server.enqueue(
            302,
            "",
            contentType = "text/plain",
            headers = mapOf("Location" to "${server.baseUrl}/portal"),
        )
        server.enqueue(200, "<html><body>SPA 外壳</body></html>", contentType = "text/html")

        val error = runCatching {
            service(session).login(account, password, service = serviceBase)
        }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.TokenSourceUnverified)
        val details = (error as HzcuException).details!!

        // 链条必须能看懂（有每一步的状态码与 host）。
        assertTrue(details.contains("跳转链"))
        assertTrue(details.contains("127.0.0.1"))
        // ★ 但绝不能带会话 ID —— 这段文本是要展示给业主、写进日志的。
        assertFalse("链条泄漏了 jsessionid 的值", details.contains("SECRETSESSION"))
        assertTrue("应当保留 jsessionid 这个键名以便看出走了 URL 重写", details.contains(";jsessionid=…"))
        assertNull(session.accessToken())
    }

    /** ★ 带凭据的 POST 只发一次：响应丢了不等于请求没到。 */
    @Test
    fun `提交遇到 5xx 不会被自动重试`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        server.enqueue(200, casPage("/cas/login;jsessionid=D?service=x"), contentType = "text/html")
        server.enqueue(500, "boom", contentType = "text/plain")

        val error = runCatching {
            service(session).login(account, password, service = serviceBase)
        }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.HttpStatus)
        // 1 次取表单 + 1 次提交；若被重试会是 4 次，等于把密码再交三遍。
        assertEquals("凭据 POST 被重试了，账号可能因此被锁", 2, server.requestCount)
    }

    @Test
    fun `缺少必填输入时不发任何请求`() = runTest {
        val session = PortalSession(InMemorySecureStore())

        val blankAccount = runCatching { service(session).login("   ", password, serviceBase) }
        val blankPassword = runCatching { service(session).login(account, "", serviceBase) }

        assertTrue(blankAccount.exceptionOrNull() is HzcuException.AuthenticationRejected)
        assertTrue(blankPassword.exceptionOrNull() is HzcuException.AuthenticationRejected)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `登录页结构变了给出可排查的说明`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        // 页面被短信/验证码流程接管：没有 fm1。
        server.enqueue(200, "<html><body>请使用手机验证码登录</body></html>", contentType = "text/html")

        val error = runCatching {
            service(session).login(account, password, service = serviceBase)
        }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.Parse)
        assertTrue(error?.message!!.contains("读不到账号表单"))
        // details 里要带着链条，否则真机上不知道走到了哪一步。
        val details = (error as HzcuException).details!!
        assertTrue("缺少跳转链", details.contains("跳转链"))
        assertTrue("链条里要有这一跳的 host 与状态码", details.contains("127.0.0.1") && details.contains("200"))
    }
}
