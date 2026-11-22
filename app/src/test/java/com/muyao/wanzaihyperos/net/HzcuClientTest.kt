package com.muyao.wanzaihyperos.net

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 网络底座的端到端行为。用一个真的本地 HTTP 服务器收发，
 * **只有限流间隔**用替身关掉（那部分由 [HzcuRateLimiterTest] 单独钉死）。
 *
 * 这些用例覆盖的都是「错了会在真机上很难查」的点：
 * 拆壳、JSON 与 form 的区别、401 不重试、5xx 要重试、
 * 以及**错误信息里绝不能出现凭据**。
 */
class HzcuClientTest {

    private lateinit var server: TestHttpServer

    private val token = "0123456789abcdef0123456789abcdef"

    private class FakeCredentials(
        private val token: String? = "0123456789abcdef0123456789abcdef",
        private val cookiesJson: String? = null,
    ) : PortalCredentials {
        var savedCookies: String? = null
        var savedToken: String? = null
        var cleared: Boolean = false
        override fun accessToken(): String? = token
        override fun userCode(): String? = "fake-user"
        override fun portalCookiesJson(): String? = cookiesJson
        override fun savePortalCookies(json: String) {
            savedCookies = json
        }

        override fun saveAccessToken(accessToken: String, userCode: String?) {
            savedToken = accessToken
        }

        override fun savePortalSession(accessToken: String, userCode: String?, portalCookiesJson: String) {
            savedToken = accessToken
            savedCookies = portalCookiesJson
        }

        override fun clear() {
            cleared = true
        }
    }

    @Before
    fun setUp() {
        server = TestHttpServer()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun client(
        credentials: PortalCredentials = FakeCredentials(),
        maxAttempts: Int = 3,
    ): HzcuClient = HzcuClient.create(
        credentials = credentials,
        portalBaseUrl = server.baseUrl,
        // 单测不真等 1 秒；间隔逻辑本身由 HzcuRateLimiterTest 负责。
        rateLimiter = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0),
        maxAttempts = maxAttempts,
        backoffBaseMs = 0,
    )

    // ---------------------------------------------------------------------------
    // 拆壳
    // ---------------------------------------------------------------------------

    @Test
    fun `成功响应被拆壳且 data 可读`() = runTest {
        server.enqueue(200, """{"code":200,"success":true,"message":"ok","data":{"a":1}}""")

        val response = client().portalPost("/api/x")

        assertEquals(200, response.code)
        assertTrue(response.success)
        assertEquals("ok", response.message)
        assertEquals(1, response.asMap?.get("a").asIntOrNull())
    }

    /**
     * ★ 消息族与搜索族把数组放在**信封顶层的 `rows`**，而 `data` 是 null。
     * 用 `data` 读会**静默**拿到 null（不报错，只表现为「没有消息」），
     * 所以这条必须钉死。
     */
    @Test
    fun `数组在顶层 rows 时 listPayload 能拿到`() = runTest {
        server.enqueue(
            200,
            """{"code":200,"success":true,"rows":[{"id":1},{"id":2}],"data":null}""",
        )

        val response = client().portalPost(
            "/api/app/ummessage/listmessage",
            jsonBody = buildJsonObject { put("pageindex", 1) },
        )

        assertNull("这一族的 data 本来就是 null", response.asList)
        assertEquals(2, response.listPayload?.size)
        assertEquals(2, response.rows?.size)
    }

    @Test
    fun `信封没写 success 时按 code 推断`() = runTest {
        server.enqueue(200, """{"code":200,"data":[]}""")
        assertTrue(client().portalPost("/api/x").success)
    }

    // ---------------------------------------------------------------------------
    // 认证失败：必须是 LoginExpired，而且**不重试**
    // ---------------------------------------------------------------------------

    @Test
    fun `HTTP 401 归类为登录失效`() = runTest {
        server.enqueue(401, """{"code":401,"message":"token 不能为空","success":false}""")

        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.LoginExpired)
        assertEquals("认证失败重试只会把账号打锁", 1, server.requestCount)
    }

    // ---------------------------------------------------------------------------
    // ★ 免密续期（业主 2026-10-19 选的「先 A」：只重放已存 CAS cookie，不落密码）
    // ---------------------------------------------------------------------------

    @Test
    fun `失效后免密续期成功则重试一次并拿到数据`() = runTest {
        server.enqueue(401, """{"code":401,"message":"token 不能为空","success":false}""")
        server.enqueue(200, """{"code":200,"success":true,"message":"ok","data":{"a":1}}""")
        val c = client()
        var renewCalls = 0
        c.renewContext = kotlinx.coroutines.Dispatchers.Unconfined
        c.renewLog = { }
        c.renewSession = { renewCalls++; true }

        val response = c.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH)

        assertTrue("续期后应该拿到数据", response.success)
        assertEquals("只续一次", 1, renewCalls)
        assertEquals("一次失效 + 一次重试", 2, server.requestCount)
    }

    @Test
    fun `cookie 也过期时照实抛出且不无限重试`() = runTest {
        server.enqueue(401, """{"code":401,"message":"token 不能为空","success":false}""")
        val c = client()
        var renewCalls = 0
        c.renewContext = kotlinx.coroutines.Dispatchers.Unconfined
        c.renewLog = { }
        c.renewSession = { renewCalls++; false }   // 重放落空 = cookie 死了

        val error = runCatching { c.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.LoginExpired)
        assertEquals(1, renewCalls)
        assertEquals("续不上就不该再打第二次，撞 QPS 红线", 1, server.requestCount)
    }

    @Test
    fun `没注入续期钩子时行为与改动前一致`() = runTest {
        server.enqueue(401, """{"code":401,"message":"token 不能为空","success":false}""")

        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()

        assertTrue(error is HzcuException.LoginExpired)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `续期只针对带 token 的调用，公开接口不触发`() = runTest {
        server.enqueue(401, """{"code":401,"message":"nope","success":false}""")
        val c = client()
        var renewCalls = 0
        c.renewContext = kotlinx.coroutines.Dispatchers.Unconfined
        c.renewLog = { }
        c.renewSession = { renewCalls++; true }

        runCatching { c.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH, attachToken = false) }

        assertEquals("不带凭据的调用失败与登录态无关，不该去重放会话", 0, renewCalls)
    }

    @Test
    fun `凭据失效时保留服务端原文`() = runTest {
        server.enqueue(401, """{"code":401,"message":"token 不能为空","success":false}""")

        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()

        assertEquals("token 不能为空", error?.message)
    }

    @Test
    fun `业务失败给出服务端中文原文`() = runTest {
        server.enqueue(200, """{"code":500,"success":false,"message":"该生无此权限"}""")

        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.Api)
        assertEquals("该生无此权限", error?.message)
        assertEquals(500, (error as HzcuException.Api).code)
    }

    @Test
    fun `HTML 登录页被识别为登录失效`() = runTest {
        server.enqueue(
            200,
            """<html><form action="/cas/login"><input type="password" name="password"/></form></html>""",
            contentType = "text/html;charset=UTF-8",
        )

        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()

        // ★ 认错的后果：把还登录着的用户显示成「服务端坏了」。
        assertTrue("实际是 $error", error is HzcuException.LoginExpired)
    }

    @Test
    fun `跳转到认证页被识别为登录失效`() = runTest {
        server.enqueue(
            302,
            "",
            contentType = "text/plain",
            headers = mapOf("Location" to "http://ca.hzcu.edu.cn/cas/login?service=x"),
        )

        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.LoginExpired)
        assertEquals("登录失效不重试", 1, server.requestCount)
    }

    // ---------------------------------------------------------------------------
    // 其它失败：分类要准，重试要克
    // ---------------------------------------------------------------------------

    @Test
    fun `只读接口 5xx 会重试到上限`() = runTest {
        repeat(3) { server.enqueue(500, """{"error":"boom"}""") }

        val error = runCatching { client().portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.HttpStatus)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `未列入只读白名单的写操作 5xx 不重试`() = runTest {
        repeat(3) { server.enqueue(500, """{"error":"boom"}""") }
        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()
        assertTrue(error is HzcuException.HttpStatus)
        assertEquals("写请求失败不得重放", 1, server.requestCount)
    }

    @Test
    fun `未列入只读白名单的写操作 401 不续期也不重放`() = runTest {
        server.enqueue(401, """{"code":401,"success":false}""")
        server.enqueue(200, """{"code":200,"success":true}""")
        val c = client()
        var renewCalls = 0
        c.renewContext = kotlinx.coroutines.Dispatchers.Unconfined
        c.renewLog = { }
        c.renewSession = { renewCalls++; true }
        val error = runCatching { c.portalPost("/api/x") }.exceptionOrNull()
        assertTrue(error is HzcuException.LoginExpired)
        assertEquals(0, renewCalls)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `maxAttempts 为 1 时只发一次`() = runTest {
        server.enqueue(500, "")

        val error = runCatching { client(maxAttempts = 1).portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.HttpStatus)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `非 JSON 且非登录页归类为解析失败且不重试`() = runTest {
        server.enqueue(200, "<html><body>502 Bad Gateway</body></html>", contentType = "text/html")

        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.Parse)
        assertEquals("重试同一个坏响应没有意义", 1, server.requestCount)
    }

    // ---------------------------------------------------------------------------
    // 凭据：怎么带、带去哪、以及绝不进错误信息
    // ---------------------------------------------------------------------------

    @Test
    fun `没有 token 时不发请求`() = runTest {
        val error = runCatching {
            client(FakeCredentials(token = null)).portalPost("/api/x")
        }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.LoginExpired)
        assertEquals("省一次无谓命中", 0, server.requestCount)
    }

    @Test
    fun `form 包体走 urlencoded 且带 token`() = runTest {
        server.enqueue(200, """{"code":200,"success":true,"data":null}""")

        client().portalPost("/api/app/listappinfoex", fields = mapOf("a" to "1"))

        val recorded = server.requests.single()
        assertTrue(
            "实际是 ${recorded.contentType}",
            recorded.contentType!!.startsWith("application/x-www-form-urlencoded"),
        )
        assertTrue(recorded.body.contains("a=1"))
        assertTrue("token 要写进 form", recorded.body.contains("token=$token"))
    }

    @Test
    fun `JSON 包体走 application json 且带 token`() = runTest {
        server.enqueue(200, """{"code":200,"success":true,"data":null}""")

        client().portalPost(
            "/api/newsapi/listnewslist",
            jsonBody = buildJsonObject { put("cataid", 1) },
        )

        val recorded = server.requests.single()
        assertTrue("实际是 ${recorded.contentType}", recorded.contentType!!.startsWith("application/json"))
        assertTrue(recorded.body.contains("\"cataid\""))
        assertTrue("token 要写进 JSON", recorded.body.contains(token))
    }

    @Test
    fun `token 同时放请求头`() = runTest {
        server.enqueue(200, """{"code":200,"success":true,"data":null}""")

        client().portalPost("/api/x")

        assertEquals(token, server.requests.single().tokenHeader)
    }

    /**
     * ★ 门户的 ACCESS_TOKEN 与 CAS 会话 Cookie 是**绑定**的：
     * 只发 token 不带 Cookie，服务端一律回 `token 不能为空`。
     * 这条测试保证「罐子里的 cookie 真的会出现在请求里」。
     *
     * 桶键用测试服务器的 host（127.0.0.1）：分桶是按 host 的，
     * 用 `iappdd.hzcu.edu.cn` 当键就不会发给本机服务器 —— 那正是分桶在起作用。
     */
    @Test
    fun `会话 cookie 会跟着请求一起发出去`() = runTest {
        val client = client(FakeCredentials(cookiesJson = """{"127.0.0.1":{"SESSION":"s-1"}}"""))
        client.restoreCookieJar()
        server.enqueue(200, """{"code":200,"success":true,"data":null}""")

        client.portalPost("/api/x")

        val cookie = server.requests.single().cookieHeader
        assertNotNull("请求里没有 Cookie 头，门户会回 401", cookie)
        assertTrue(cookie!!.contains("SESSION=s-1"))
    }

    @Test
    fun `persistCookieJar 把罐子交给会话层`() = runTest {
        val credentials = FakeCredentials()
        val client = client(credentials)
        server.enqueue(
            200,
            """{"code":200,"success":true,"data":null}""",
            headers = mapOf("Set-Cookie" to "SESSION=s-9; Path=/"),
        )

        client.portalPost("/api/x")
        client.persistCookieJar()

        val saved = credentials.savedCookies
        assertNotNull("Set-Cookie 没有被落盘", saved)
        assertTrue(saved!!.contains("SESSION"))
    }

    @Test
    fun `错误信息与摘要里不出现凭据或业务正文`() = runTest {
        repeat(3) { server.enqueue(500, """{"message":"某某某的密文","token":"$token"}""") }

        val error = runCatching { client().portalPost("/api/x") }.exceptionOrNull()!!

        val text = error.toString() + (error as HzcuException).details
        assertFalse("错误信息泄漏了 token", text.contains(token))
        assertFalse("响应摘要复制了业务正文", text.contains("某某某"))
    }
}
