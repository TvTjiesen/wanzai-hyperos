package com.muyao.wanzaihyperos.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 脱敏是**红线**，所以它必须有测试，不能只靠代码审查。
 *
 * 这些用例全部来自真实会发生的场景（CAS 的 URL 重写、门户的 token 字段、
 * 网关回的 HTML 登录页），不是编出来的字符串。
 */
class RedactionTest {

    @Test
    fun `token 字段的值被抹掉`() {
        val out = Redaction.redact("token=0123456789abcdef0123456789abcdef")
        assertEquals("token=${Redaction.HIDDEN}", out)
        assertFalse(out.contains("0123456789abcdef"))
    }

    @Test
    fun `密码字段的值被抹掉`() {
        val out = Redaction.redact("password=MySecret123&username=2021001")
        assertFalse(out.contains("MySecret123"))
        assertTrue(out.contains("username=2021001")) // 用户名不是凭据，要保留以便定位
    }

    @Test
    fun `cookie 请求头整行被抹掉`() {
        val out = Redaction.redact("Cookie: CASTGC=TGT-1234-abcd; JSESSIONID=xyz")
        assertFalse(out.contains("TGT-1234-abcd"))
        assertFalse(out.contains("xyz"))
        assertTrue(out.startsWith("Cookie="))
    }

    @Test
    fun `Set-Cookie 响应头整行被抹掉`() {
        val out = Redaction.redact("Set-Cookie: route=abc123; Path=/")
        assertFalse(out.contains("abc123"))
    }

    @Test
    fun `URL 的 query 整个丢掉`() {
        val out = Redaction.sanitizedRequestUri(
            "http://iappdd.hzcu.edu.cn/api/x?token=secret&ticket=ST-1",
        )
        assertEquals("http://iappdd.hzcu.edu.cn/api/x", out)
    }

    /**
     * ★ 这条是相对 Flutter 蓝本的**有意修复**。
     *
     * CAS 用 URL 重写传会话 ID，形如 `/cas/login;jsessionid=ABC`。
     * 蓝本的 `sanitizedRequestUri` 原样保留 path，而 `HzcuRequest.logShape`
     * 是直接调它的（没过 `redact`），于是每次 CAS 请求都会在日志里留下一个
     * 可复用的会话 ID。这里必须连名带值一起去掉。
     */
    @Test
    fun `URL path 里的 jsessionid 被剥掉`() {
        val out = Redaction.sanitizedRequestUri(
            "http://ca.hzcu.edu.cn/cas/login;jsessionid=ABC123DEF?service=x",
        )
        assertEquals("http://ca.hzcu.edu.cn/cas/login", out)
        assertFalse(out.contains("ABC123DEF"))
    }

    @Test
    fun `端口保留 host 保留`() {
        assertEquals(
            "https://courses.hzcu.edu.cn:8443/api/profile",
            Redaction.sanitizedRequestUri("https://courses.hzcu.edu.cn:8443/api/profile?a=1"),
        )
    }

    @Test
    fun `无法解析的 URI 不泄露原文`() {
        val out = Redaction.sanitizedRequestUri("这不是一个网址 token=abc")
        assertEquals(Redaction.HIDDEN_URL, out)
    }

    @Test
    fun `null URI 给出未知标记`() {
        assertEquals(Redaction.UNKNOWN_URI, Redaction.sanitizedRequestUri(null))
    }

    @Test
    fun `文本里的 URL 整体被脱敏`() {
        val out = Redaction.redact("请求 http://ca.hzcu.edu.cn/cas/login?ticket=ST-9 失败")
        assertFalse(out.contains("ST-9"))
        assertTrue(out.contains("http://ca.hzcu.edu.cn/cas/login"))
    }

    // ---------------------------------------------------------------------------
    // 响应摘要：只给结构，不给内容
    // ---------------------------------------------------------------------------

    @Test
    fun `响应摘要不复制正文`() {
        val summary = Redaction.responseSummary(
            """{"code":200,"success":true,"data":{"name":"李同学","sid":"2021001"}}""",
        )
        assertFalse(summary.contains("李同学"))
        assertFalse(summary.contains("2021001"))
        // 顶层是 code / success / data 三个键。
        assertTrue(summary.contains("字段数=3"))
    }

    @Test
    fun `数组摘要只给条目数`() {
        val summary = Redaction.responseSummary("""[1,2,3]""")
        assertEquals("<JSON数组；条目数=3>", summary)
    }

    @Test
    fun `HTML 响应被识别`() {
        assertEquals("<HTML响应；长度=6>", Redaction.responseSummary("<html>"))
    }

    @Test
    fun `空响应被识别`() {
        assertEquals("<空响应>", Redaction.responseSummary("   "))
    }

    // ---------------------------------------------------------------------------
    // 「其实是没登录」的识别
    // ---------------------------------------------------------------------------

    @Test
    fun `门户的 token 不能为空算认证失败`() {
        assertTrue(
            Redaction.bodyIndicatesAuthenticationFailure(
                """{"code":401,"message":"token 不能为空","success":false}""",
            ),
        )
    }

    @Test
    fun `kickout 算认证失败`() {
        assertTrue(Redaction.bodyIndicatesAuthenticationFailure("""{"kickout":1}"""))
        assertTrue(Redaction.bodyIndicatesAuthenticationFailure("kickout=1&x=2"))
    }

    @Test
    fun `普通业务失败不算认证失败`() {
        // ★ 这条很关键：认错了会把还登录着的用户踢到登录页。
        assertFalse(
            Redaction.bodyIndicatesAuthenticationFailure(
                """{"code":500,"message":"该生无此权限","success":false}""",
            ),
        )
    }

    @Test
    fun `跳转到认证页被识别`() {
        assertTrue(
            Redaction.locationIndicatesAuthenticationFailure(
                "http://ca.hzcu.edu.cn/cas/login?service=x",
            ),
        )
        assertTrue(
            Redaction.locationIndicatesAuthenticationFailure(
                "https://identity.hzcu.edu.cn/auth/realms/zju-xzzj/protocol/cas/login",
            ),
        )
        assertFalse(Redaction.locationIndicatesAuthenticationFailure("/caimsmvc/hzcc.html"))
        assertFalse(Redaction.locationIndicatesAuthenticationFailure(null))
    }
}
