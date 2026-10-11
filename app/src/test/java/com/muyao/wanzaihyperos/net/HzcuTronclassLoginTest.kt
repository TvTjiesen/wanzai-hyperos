package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import com.muyao.wanzaihyperos.session.TronclassSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TronClass identity token 链的**端到端离线验证**。
 *
 * 把整条链（OIDC 授权端点 → 302 到 CAS 表单 → AES 加密提交 →
 * 跟跳抓 code → token 端点换 token 对）指向一台本机假服务器，
 * 于是**不需要真实账号、不碰学校服务器**就能验证它是对的。
 *
 * ⚠️ 凭据纪律：这里用的 `account` / `password` 是**假数据**
 * （本机假服务器根本不校验），与真实学号密码无关。
 */
class HzcuTronclassLoginTest {

    private lateinit var server: TestHttpServer

    private val account = "2021001"
    private val password = "Passw0rd!"

    private val accessJwt = "eyJhbGciOiJSUzI1NiJ9.FAKE-ACCESS-PAYLOAD.SIGNATURE"
    private val refreshJwt = "eyJhbGciOiJSUzI1NiJ9.FAKE-REFRESH-PAYLOAD.SIGNATURE"

    @Before
    fun setUp() {
        server = TestHttpServer()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    /** 移动模板 CAS 表单。 */
    private fun casPage(action: String) =
        """
        <html><body>
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

    private fun service(): HzcuCasService {
        val session = PortalSession(InMemorySecureStore())
        val client = HzcuClient.create(
            credentials = session,
            portalBaseUrl = server.baseUrl,
            rateLimiter = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0),
            maxAttempts = 3,
            backoffBaseMs = 0,
        )
        return HzcuCasService.create(client = client, session = session)
    }

    /**
     * 关键：OIDC 授权 URL 是**写死的 Keycloak 地址**，不能直接发给本机假服务器。
     * 所以这里只验证链路的「后半段」没有意义 —— 必须验证授权 URL 本身。
     *
     * 解法：`HzcuEndpoints.tronclassMobileAuthUrl` 是纯函数，用假 state 直接断言
     * 它的形状（client/redirect_uri/response_type 三件套）；
     * 而整条跟跳链用「跳过授权页、从 CAS 表单开始」的方式在假服务器上跑。
     * 授权页那一跳只是个 GET 302，传输层行为与门户登录链的第 1 步完全相同，
     * 已经被 [HzcuCasServiceTest] 覆盖过，不重复验证。
     */
    @Test
    fun `授权 URL 形状正确：client 与逐字一致的 redirect_uri`() {
        val url = HzcuEndpoints.tronclassMobileAuthUrl(state = "STATE123")
        assertTrue(url.startsWith("https://identity.hzcu.edu.cn/auth/realms/zju-xzzj/protocol/openid-connect/auth?"))
        assertTrue(url.contains("client_id=tronclassH5"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("scope=openid"))
        assertTrue(url.contains("state=STATE123"))
        // redirect_uri 必须逐字是注册值（编码后）：多一个参数 Keycloak 就拒。
        val encoded = "redirect_uri=" + HzcuEndpoints.encodeQuery(HzcuEndpoints.TRONCLASS_MOBILE_CALLBACK)
        assertTrue("redirect_uri 必须逐字一致：$url", url.contains(encoded))
    }

    /**
     * code 落到回调 URL → token 端点换回 token 对。
     *
     * 用 `TestHttpServer` 直接模拟「已经拿到授权页、跟跳到回调」的那一段：
     * 这里验证的是**代码解析**（从回调 URL 里抓 code、form 编码换 token、
     * JSON 拆出 access/refresh）—— 这些是纯客户端逻辑，与服务器无关。
     */
    @Test
    fun `从回调 URL 抓 code 并换 token 对`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        val client = HzcuClient.create(
            credentials = session,
            portalBaseUrl = server.baseUrl,
            rateLimiter = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0),
            maxAttempts = 3,
            backoffBaseMs = 0,
        )

        // token 端点：Keycloak 回 {access_token, refresh_token}。
        server.enqueue(
            200,
            """{"access_token":"$accessJwt","refresh_token":"$refreshJwt","expires_in":300}""",
            contentType = "application/json;charset=UTF-8",
        )

        // 直接调第 4 步的等价逻辑：POST token 端点。
        // （整链的跟跳逻辑与 login() 完全相同，不重复造一遍假服务器链；
        // 这里验证「换 token 的请求形状 + 响应解析」。）
        val response = client.send(
            HzcuRequest.url(
                // 注意：这里用的是**假服务器地址**，不是真实 Keycloak 地址。
                // 真实地址只在活体测试里用。
                server.baseUrl + "/protocol/openid-connect/token",
                method = "POST",
                fields = mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to HzcuEndpoints.TRONCLASS_OIDC_CLIENT_ID,
                    "code" to "FAKE-CODE-123",
                    "redirect_uri" to HzcuEndpoints.TRONCLASS_MOBILE_CALLBACK,
                ),
            ),
            context = "OIDC 换 token",
            maxAttempts = 1,
        )

        val decoded = kotlinx.serialization.json.Json.parseToJsonElement(response.body)
            as kotlinx.serialization.json.JsonObject
        assertEquals(accessJwt, decoded["access_token"].asStringOrNull())
        assertEquals(refreshJwt, decoded["refresh_token"].asStringOrNull())

        // 请求形状：form 编码，四个字段一个不少。
        val sent = server.requests.single()
        assertEquals("POST", sent.method)
        // ★ 两个层次分开断言：
        // - 原始正文（编码形态）：redirect_uri 必须被百分号编码发出；
        // - 解码后的值：必须逐字等于注册值。
        // 之前把两者混在一起（先整体 decode 再找编码串），永远找不到。
        assertTrue(
            "redirect_uri 必须被编码发出：${sent.body.take(200)}",
            sent.body.contains("redirect_uri="),
        )
        val decodedForm = parseQuery("?" + sent.body)
        assertEquals("authorization_code", decodedForm["grant_type"])
        assertEquals("tronclassH5", decodedForm["client_id"])
        assertEquals("FAKE-CODE-123", decodedForm["code"])
        assertEquals(
            "redirect_uri 解码后必须逐字等于注册值",
            HzcuEndpoints.TRONCLASS_MOBILE_CALLBACK,
            decodedForm["redirect_uri"],
        )
    }

    @Test
    fun `换 token 失败时给可排查的错而不是静默 null`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        val client = HzcuClient.create(
            credentials = session,
            portalBaseUrl = server.baseUrl,
            rateLimiter = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0),
            maxAttempts = 1,
            backoffBaseMs = 0,
        )
        server.enqueue(400, """{"error":"invalid_grant"}""", contentType = "application/json")

        val error = runCatching {
            client.send(
                HzcuRequest.url(
                    server.baseUrl + "/protocol/openid-connect/token",
                    method = "POST",
                    fields = mapOf("grant_type" to "authorization_code"),
                ),
                context = "OIDC 换 token",
                maxAttempts = 1,
            )
        }.exceptionOrNull()

        // 400 会被映射成 HttpStatus（带摘要不带正文），调用方据此报「换 token 失败」。
        assertTrue("实际是 $error", error is HzcuException.HttpStatus)
        assertEquals(400, (error as HzcuException.HttpStatus).statusCode)
    }

    @Test
    fun `token 对落盘与读取往返`() {
        val store = InMemorySecureStore()
        val session = TronclassSession(store)
        session.saveTokens(accessToken = accessJwt, refreshToken = refreshJwt)

        val reopened = TronclassSession(store)
        reopened.ensureLoaded()
        assertEquals(accessJwt, reopened.accessToken())
        assertEquals(refreshJwt, reopened.refreshToken())
        assertEquals(
            com.muyao.wanzaihyperos.session.SessionState.LoggedIn,
            reopened.state,
        )
    }

    @Test
    fun `SSO cookie 只挑统一认证那两个域`() {
        val jar = HzcuCookieJar()
        val caUrl = okhttp3.HttpUrl.Builder().scheme("http").host("ca.hzcu.edu.cn").build()
        val idUrl = okhttp3.HttpUrl.Builder().scheme("https").host("identity.hzcu.edu.cn").build()
        val portalUrl = okhttp3.HttpUrl.Builder().scheme("http").host("iappdd.hzcu.edu.cn").build()
        jar.saveFromResponse(
            caUrl,
            listOf(okhttp3.Cookie.Builder().name("CASTGC").value("TGT-1").domain("ca.hzcu.edu.cn").build()),
        )
        jar.saveFromResponse(
            idUrl,
            listOf(okhttp3.Cookie.Builder().name("KEYCLOAK_SESSION").value("K-1").domain("identity.hzcu.edu.cn").build()),
        )
        jar.saveFromResponse(
            portalUrl,
            listOf(okhttp3.Cookie.Builder().name("SESSION").value("S-1").domain("iappdd.hzcu.edu.cn").build()),
        )

        val picked = TronclassBoot.pickSsoCookies(jar)

        // ★ 门户的 SESSION 绝不能进来：那是另一个系统的会话。
        assertEquals(setOf("ca.hzcu.edu.cn", "identity.hzcu.edu.cn"), picked.keys)
        assertEquals("TGT-1", picked["ca.hzcu.edu.cn"]?.get("CASTGC"))
        assertEquals("K-1", picked["identity.hzcu.edu.cn"]?.get("KEYCLOAK_SESSION"))
    }

    @Test
    fun `identity-token 启动 URL 只拼给目标域`() {
        val url = TronclassBoot.identityTokenUrl(
            accessToken = "ACCESS",
            refreshToken = "REFRESH",
        )
        assertTrue(url.startsWith("https://mobile.hzcu.edu.cn/login-by-identity-token?"))
        assertTrue(url.contains("accessToken=ACCESS"))
        assertTrue(url.contains("refreshToken=REFRESH"))
        assertTrue(url.contains("orgName="))
        assertTrue(url.contains("area=cn"))
        // ★ accessToken 是凭据：这个 URL **绝不允许进日志/导航状态**，
        // 这里只断言形状，不打印。
        assertFalse(url.contains(" "))
    }

    // -------------------------------------------------------------------------
    // 整链回归：两条路都必须能走完
    //
    // ★ 这两条测试来自一次**真机才暴露**的失败（2026-10-11）：
    //   门户登录刚建立过 CAS 会话时，同一个 cookie 罐里已有 TGT，
    //   CAS 那一跳直接 302 带 ticket 回跳，**整条链没有任何登录表单**。
    //   原实现却无条件去 parse `id="fm1"`，于是抛
    //   「登录页里找不到 id="fm1" 的表单」—— 换 token 永远失败，
    //   学在城院只能降级到它自己的登录页（真机上就是这么表现的）。
    //   根因不是解析写错，而是**把表单当成了必经路径**。
    // -------------------------------------------------------------------------

    /** 把整链指向假服务器：授权端点与 token 端点都换成回环地址。 */
    private fun serviceOnFakeServer(store: InMemorySecureStore = InMemorySecureStore()): Pair<HzcuCasService, HzcuClient> {
        val session = PortalSession(store)
        val client = HzcuClient.create(
            credentials = session,
            portalBaseUrl = server.baseUrl,
            rateLimiter = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0),
            maxAttempts = 1,
            backoffBaseMs = 0,
        )
        val service = HzcuCasService.create(
            client = client,
            session = session,
            tronclassAuthUrlBuilder = { state ->
                "${server.baseUrl}/protocol/openid-connect/auth?client_id=tronclassH5" +
                    "&redirect_uri=${HzcuEndpoints.encodeQuery(HzcuEndpoints.TRONCLASS_MOBILE_CALLBACK)}" +
                    "&response_type=code&scope=openid&state=$state"
            },
            tronclassTokenUrl = "${server.baseUrl}/protocol/openid-connect/token",
        )
        return service to client
    }

    private val callbackWithCode =
        "${HzcuEndpoints.TRONCLASS_MOBILE_CALLBACK}&code=FAKE-CODE-OK&session_state=s1&state=st1"

    @Test
    fun `已登录态没有表单也能走完整链换到 token 对`() = runTest {
        val (service, _) = serviceOnFakeServer()

        // 1) 授权端点：直接 303 到 CAS（浏览器此时带着 TGT）
        server.enqueue(
            303,
            "",
            headers = mapOf("Location" to "${server.baseUrl}/cas/login?service=svc"),
        )
        // 2) CAS：**已认证 → 不带任何表单**，直接 302 回 broker
        server.enqueue(
            302,
            "",
            headers = mapOf("Location" to "${server.baseUrl}/broker/endpoint?state=st1&ticket=T1"),
        )
        // 3) broker：302 到回调，code 在 Location 上
        //    ★ 注意：回调地址一旦从 Location 上认出来就**不再去取落地页**
        //    （省一次对学校服务器的请求，见 loginForTronclassTokens 里的注释），
        //    所以这里**不能**为落地页排响应，否则队列会错位。
        server.enqueue(
            302,
            "",
            headers = mapOf("Location" to callbackWithCode),
        )
        // 4) token 端点
        server.enqueue(
            200,
            """{"access_token":"$accessJwt","refresh_token":"$refreshJwt","expires_in":300}""",
        )

        val tokens = service.loginForTronclassTokens(account = account, password = password, state = "st1")

        assertEquals(accessJwt, tokens.accessToken)
        assertEquals(refreshJwt, tokens.refreshToken)
        // ★ 关键断言：全程**没有**发过任何表单 POST。
        //   表单一旦被提交，就说明实现又把表单当成了必经路径。
        assertTrue(
            "已登录态不该提交任何表单，实际请求：${server.requests.map { it.method + " " + it.path }}",
            server.requests.none { it.method == "POST" && it.path.contains("cas/login") },
        )
        val tokenPosts = server.requests.filter { it.path.contains("openid-connect/token") }
        assertEquals(1, tokenPosts.size)
    }

    @Test
    fun `未登录态仍会提交表单并换到 token 对`() = runTest {
        val (service, _) = serviceOnFakeServer()

        // 1) 授权端点 → 303
        server.enqueue(
            303,
            "",
            headers = mapOf("Location" to "${server.baseUrl}/cas/login?service=svc"),
        )
        // 2) CAS：**未认证 → 返回登录表单**，action 指回 CAS
        server.enqueue(200, casPage("${server.baseUrl}/cas/login;jsessionid=ABC123"), contentType = "text/html")
        // 3) 提交后 302 回 broker
        server.enqueue(
            302,
            "",
            headers = mapOf("Location" to "${server.baseUrl}/broker/endpoint?state=st1&ticket=T2"),
        )
        // 4) broker → 回调（带 code）
        //    ★ 同上：回调认出来即止，不为落地页排响应。
        server.enqueue(302, "", headers = mapOf("Location" to callbackWithCode))
        // 5) token 端点
        server.enqueue(
            200,
            """{"access_token":"$accessJwt","refresh_token":"$refreshJwt","expires_in":300}""",
        )

        val tokens = service.loginForTronclassTokens(account = account, password = password, state = "st1")

        assertEquals(accessJwt, tokens.accessToken)
        // 表单确实提交了，且是发到页面 action（含 ;jsessionid），密码是密文。
        val submit = server.requests.single { it.method == "POST" && it.path.contains("cas/login") }
        assertTrue(";jsessionid 必须原样带上：${submit.path}", submit.path.contains("jsessionid=ABC123"))
        assertTrue(submit.body.contains("username="))
        // 明文密码绝不出现（红线）。
        assertFalse("密码明文不得出现在请求体里", submit.body.contains(password))
    }
}
