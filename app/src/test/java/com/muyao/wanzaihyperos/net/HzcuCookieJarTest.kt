package com.muyao.wanzaihyperos.net

import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cookie 分桶是**凭据不外泄**这条红线的实现方式，所以必须有测试。
 *
 * 真实事故背景：上一版把 cookie 存成一张全局「名→值」表，而罐子是调用方
 * 每个请求显式传进来的。于是同一个罐一旦被 CAS 流程和门户调用共用，
 * `CASTGC` 就会原样发给 `iappdd` —— 会话凭据跨系统漂移，正是 CAS 要防的事。
 */
class HzcuCookieJarTest {

    private val caUrl: HttpUrl = "http://ca.hzcu.edu.cn/cas/login".toHttpUrl()
    private val portalUrl: HttpUrl = "http://iappdd.hzcu.edu.cn/api/app/x".toHttpUrl()

    private fun cookie(name: String, value: String, domain: String) =
        Cookie.Builder().name(name).value(value).domain(domain).path("/").build()

    @Test
    fun `cookie 按 host 分桶，不跨域漂移`() {
        val jar = HzcuCookieJar()
        jar.saveFromResponse(caUrl, listOf(cookie("CASTGC", "TGT-1", "ca.hzcu.edu.cn")))
        jar.saveFromResponse(portalUrl, listOf(cookie("SESSION", "s-1", "iappdd.hzcu.edu.cn")))

        assertEquals(listOf("CASTGC"), jar.loadForRequest(caUrl).map { it.name })
        assertEquals(listOf("SESSION"), jar.loadForRequest(portalUrl).map { it.name })

        // ★ 这一条是重点：CAS 的会话票据绝不能出现在发往门户的请求里。
        assertTrue(jar.loadForRequest(portalUrl).none { it.name == "CASTGC" })
        assertTrue(jar.loadForRequest(caUrl).none { it.name == "SESSION" })
    }

    @Test
    fun `同 host 的多个 cookie 都会带上`() {
        val jar = HzcuCookieJar()
        jar.saveFromResponse(
            caUrl,
            listOf(
                cookie("CASTGC", "TGT-1", "ca.hzcu.edu.cn"),
                cookie("JSESSIONID", "j-1", "ca.hzcu.edu.cn"),
            ),
        )
        assertEquals(
            setOf("CASTGC", "JSESSIONID"),
            jar.loadForRequest(caUrl).map { it.name }.toSet(),
        )
    }

    @Test
    fun `空值表示服务端在删 cookie`() {
        val jar = HzcuCookieJar()
        jar.saveFromResponse(caUrl, listOf(cookie("CASTGC", "TGT-1", "ca.hzcu.edu.cn")))
        assertTrue(jar.hasCookie("ca.hzcu.edu.cn", "CASTGC"))

        jar.saveFromResponse(caUrl, listOf(cookie("SET", "", "ca.hzcu.edu.cn")))
        // 上面的 helper 造不出空值（OkHttp 的 Builder 允许），这里直接用空值覆盖。
        jar.saveFromResponse(caUrl, listOf(cookie("CASTGC", "", "ca.hzcu.edu.cn")))
        assertFalse(jar.hasCookie("ca.hzcu.edu.cn", "CASTGC"))
    }

    @Test
    fun `JSON 往返保留分桶`() {
        val jar = HzcuCookieJar()
        jar.saveFromResponse(caUrl, listOf(cookie("CASTGC", "TGT-1", "ca.hzcu.edu.cn")))
        jar.saveFromResponse(portalUrl, listOf(cookie("SESSION", "s-1", "iappdd.hzcu.edu.cn")))

        val restored = HzcuCookieJar.fromJson(jar.toJson())

        assertEquals("TGT-1", restored.valueOf("ca.hzcu.edu.cn", "CASTGC"))
        assertEquals("s-1", restored.valueOf("iappdd.hzcu.edu.cn", "SESSION"))
        assertNull(restored.valueOf("iappdd.hzcu.edu.cn", "CASTGC"))
    }

    @Test
    fun `loadFrom 就地替换，对象身份不变`() {
        val jar = HzcuCookieJar()
        jar.saveFromResponse(caUrl, listOf(cookie("OLD", "1", "ca.hzcu.edu.cn")))

        val source = HzcuCookieJar()
        source.saveFromResponse(portalUrl, listOf(cookie("NEW", "2", "iappdd.hzcu.edu.cn")))

        val identity = System.identityHashCode(jar)
        jar.loadFrom(source.toJson())

        // ★ OkHttpClient 持有的是这个罐子的引用，换对象不会生效，所以必须就地替换。
        assertEquals(identity, System.identityHashCode(jar))
        assertNull(jar.valueOf("ca.hzcu.edu.cn", "OLD"))
        assertEquals("2", jar.valueOf("iappdd.hzcu.edu.cn", "NEW"))
    }

    @Test
    fun `坏 JSON 静默返回空罐而不是抛异常`() {
        val jar = HzcuCookieJar.fromJson("{ 这不是合法 JSON")
        assertTrue(jar.isEmpty)
        assertTrue(jar.loadForRequest(caUrl).isEmpty())
    }

    @Test
    fun `null 与空串都得到空罐`() {
        assertTrue(HzcuCookieJar.fromJson(null).isEmpty)
        assertTrue(HzcuCookieJar.fromJson("   ").isEmpty)
    }

    @Test
    fun `toString 只输出 host 与名，绝不输出值`() {
        val jar = HzcuCookieJar()
        jar.saveFromResponse(caUrl, listOf(cookie("CASTGC", "SUPER-SECRET-VALUE", "ca.hzcu.edu.cn")))

        val text = jar.toString()
        assertFalse("toString 泄漏了 cookie 值", text.contains("SUPER-SECRET-VALUE"))
        assertTrue(text.contains("CASTGC"))
        assertTrue(text.contains("ca.hzcu.edu.cn"))
    }
}
