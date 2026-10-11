package com.muyao.wanzaihyperos.session

import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话层。这里盯的是两条最容易做错的判断：
 *
 * 1. **「读不出来」不等于「没登录」** —— 判错的后果是把还登录着的用户踢到登录页；
 * 2. **门户与学在城院各自独立** —— 合成一个的后果是「门户过期把学在城院也登出」。
 */
class SessionStoreTest {

    private val validToken = "0123456789abcdef0123456789abcdef"

    @Test
    fun `没读过存储之前是未知，不是已登出`() {
        val session = PortalSession(InMemorySecureStore())
        // ★ 还没 ensureLoaded 就说「你登出了」是撒谎。
        assertEquals(SessionState.Unknown, session.state)
    }

    @Test
    fun `读过之后确实没有凭据才是已登出`() {
        val session = PortalSession(InMemorySecureStore())
        session.ensureLoaded()
        assertEquals(SessionState.LoggedOut, session.state)
    }

    @Test
    fun `存储读取失败必须降级成未知而不是已登出`() {
        val session = PortalSession(FailingSecureStore())
        session.ensureLoaded()

        // ★ 这一条是本文件最重要的断言。判成 LoggedOut 会把用户踢到登录页。
        assertEquals(SessionState.Unknown, session.state)
        assertTrue(session.lastStorageError is HzcuException.Storage)
        assertNull(session.accessToken())
    }

    @Test
    fun `存储失败只带操作与错误码`() {
        val session = PortalSession(FailingSecureStore("MyErrorCode"))
        session.ensureLoaded()

        val error = session.lastStorageError!!
        assertEquals("read", error.op)
        assertEquals("MyErrorCode", error.code)
        // 消息里不得出现任何存储内容（键名或值）。
        assertFalse(error.message!!.contains("hzcu_access_token"))
    }

    @Test
    fun `保存后可读且状态变为已登录`() {
        val store = InMemorySecureStore()
        val session = PortalSession(store)

        session.save(validToken, "2021001")

        assertEquals(SessionState.LoggedIn, session.state)
        assertEquals(validToken, session.accessToken())
        assertEquals("2021001", session.userCode())
        assertTrue(store.keys().contains(PortalSession.KEY_ACCESS_TOKEN))
        assertTrue(session.savedAtMillis() != null)
    }

    @Test
    fun `空 token 被拒绝保存`() {
        val session = PortalSession(InMemorySecureStore())
        assertThrows(IllegalArgumentException::class.java) { session.save("   ") }
        session.ensureLoaded()
        assertEquals(SessionState.LoggedOut, session.state)
    }

    @Test
    fun `登出会清空存储而不只是内存`() {
        val store = InMemorySecureStore()
        val session = PortalSession(store)
        session.save(validToken, "2021001")

        session.clear()

        assertEquals(SessionState.LoggedOut, session.state)
        assertNull(session.accessToken())
        assertFalse(store.keys().contains(PortalSession.KEY_ACCESS_TOKEN))
    }

    @Test
    fun `重启后能从存储里读回凭据`() {
        val store = InMemorySecureStore()
        PortalSession(store).save(validToken, "2021001")
        PortalSession(store).savePortalCookies("""{"ca.hzcu.edu.cn":{"CASTGC":"v"}}""")

        // 新实例模拟「App 重启」。
        val reopened = PortalSession(store)
        // 界面拿 state 之前必须先加载过：不加载就是 Unknown（那是有意的，见 SessionState）。
        reopened.ensureLoaded()
        assertEquals(SessionState.LoggedIn, reopened.state)
        assertEquals(validToken, reopened.accessToken())
        assertEquals("2021001", reopened.userCode())
        assertEquals("""{"ca.hzcu.edu.cn":{"CASTGC":"v"}}""", reopened.portalCookiesJson())
    }

    @Test
    fun `cookie 与 token 同时落盘`() {
        val store = InMemorySecureStore()
        val session = PortalSession(store)
        session.save(validToken)
        session.savePortalCookies("""{"iappdd.hzcu.edu.cn":{"SESSION":"s"}}""")

        // ★ 为什么必须一起存：门户的 token 与 CAS 会话 cookie 是绑定的，
        //   只发 token 会被回 401 token 不能为空。cookie 丢了等于没登录。
        assertTrue(store.keys().contains(PortalSession.KEY_PORTAL_COOKIES))
        assertEquals("""{"iappdd.hzcu.edu.cn":{"SESSION":"s"}}""", session.portalCookiesJson())
    }

    // ---------------------------------------------------------------------------
    // 门户与学在城院必须互相独立
    // ---------------------------------------------------------------------------

    @Test
    fun `门户过期不会把学在城院也登出`() {
        val store = InMemorySecureStore()
        val portal = PortalSession(store)
        val tronclass = TronclassSession(store)

        portal.save(validToken, "2021001")
        tronclass.saveTokens(accessToken = "tron-access-jwt", refreshToken = "tron-refresh-jwt")

        assertEquals(SessionState.LoggedIn, portal.state)
        assertEquals(SessionState.LoggedIn, tronclass.state)

        portal.clear()

        assertEquals(SessionState.LoggedOut, portal.state)
        assertEquals("门户过期不该连带登出学在城院", SessionState.LoggedIn, tronclass.state)
        assertEquals("tron-access-jwt", tronclass.accessToken())
        assertEquals("tron-refresh-jwt", tronclass.refreshToken())
    }

    @Test
    fun `学在城院过期不会把门户也登出`() {
        val store = InMemorySecureStore()
        val portal = PortalSession(store)
        val tronclass = TronclassSession(store)
        portal.save(validToken)
        tronclass.saveTokens(accessToken = "tron-access-jwt", refreshToken = null)

        tronclass.clear()

        assertEquals(SessionState.LoggedIn, portal.state)
        assertEquals(validToken, portal.accessToken())
    }

    @Test
    fun `两边的存储键完全不重叠`() {
        val store = InMemorySecureStore()
        PortalSession(store).save("a")
        TronclassSession(store).saveTokens(accessToken = "b", refreshToken = null)

        val keys = store.keys()
        assertTrue(keys.contains(PortalSession.KEY_ACCESS_TOKEN))
        assertTrue(keys.contains(TronclassSession.KEY_TOKENS))
        assertFalse(PortalSession.KEY_ACCESS_TOKEN == TronclassSession.KEY_TOKENS)
    }

    @Test
    fun `学在城院空 access token 被拒绝保存`() {
        val session = TronclassSession(InMemorySecureStore())
        assertThrows(IllegalArgumentException::class.java) {
            session.saveTokens(accessToken = "  ", refreshToken = null)
        }
    }

    @Test
    fun `学在城院的 SSO cookie 可以单独更新`() {
        val session = TronclassSession(InMemorySecureStore())
        session.saveTokens(accessToken = "tron-access-jwt", refreshToken = null)
        session.saveSsoCookies("""{"identity.hzcu.edu.cn":{"KEYCLOAK_SESSION":"x"}}""")
        assertEquals(
            """{"identity.hzcu.edu.cn":{"KEYCLOAK_SESSION":"x"}}""",
            session.ssoCookiesJson(),
        )
        assertEquals("单独更新 SSO cookie 不该动 token 对", "tron-access-jwt", session.accessToken())
    }

    // ---------------------------------------------------------------------------
    // 凭据永不外泄
    // ---------------------------------------------------------------------------

    @Test
    fun `toString 不泄露凭据`() {
        val session = PortalSession(InMemorySecureStore())
        session.save("SUPER-SECRET-TOKEN-VALUE-0123456789", "2021001")

        val text = session.toString()

        assertFalse("toString 泄漏了 token", text.contains("SUPER-SECRET-TOKEN"))
        assertTrue(text.contains("长度"))
    }

    @Test
    fun `学在城院 toString 不泄露 token 对`() {
        val session = TronclassSession(InMemorySecureStore())
        session.saveTokens(accessToken = "SUPER-SECRET-ACCESS-JWT", refreshToken = "SUPER-SECRET-REFRESH")

        val text = session.toString()
        assertFalse(text.contains("SUPER-SECRET-ACCESS"))
        assertFalse(text.contains("SUPER-SECRET-REFRESH"))
        assertTrue(text.contains("长度"))
    }

    @Test
    fun `监听者在登录与登出时都会被通知`() {
        val session = PortalSession(InMemorySecureStore())
        var notifications = 0
        session.addListener { notifications++ }

        session.save(validToken)
        assertEquals(1, notifications)

        session.clear()
        assertEquals(2, notifications)
    }

    @Test
    fun `凭据存储文件名是专用的`() {
        // ★ 不能与 Celechron 遗留的默认文件名相同：那些库里的迁移逻辑
        //   会搬走「别人的」键值，属于乱改业主的东西。
        assertEquals("WanzaiHzcuSecureStorage", EncryptedSecureStore.PREFS_FILE_NAME)
        assertEquals("wanzai_hzcu_master_key", EncryptedSecureStore.MASTER_KEY_ALIAS)
    }

    @Test
    fun `token 长度断言与实测一致`() {
        assertEquals(32, PortalSession.ACCESS_TOKEN_LENGTH)
        assertEquals(32, validToken.length)
    }

    // ---------------------------------------------------------------------------
    // token 对序列化（纯内存 round-trip，不碰存储）
    // ---------------------------------------------------------------------------

    @Test
    fun `token 对写入与读回一致`() {
        val raw = TronclassSession.writeTokenPair("ACCESS-123", "REFRESH-456")
        val pair = TronclassSession.readTokenPair(raw)
        assertEquals("ACCESS-123", pair?.first)
        assertEquals("REFRESH-456", pair?.second)
    }

    @Test
    fun `refresh 为空也能存取`() {
        val noRefresh = TronclassSession.writeTokenPair("ACCESS-123", null)
        assertEquals("ACCESS-123", TronclassSession.readTokenPair(noRefresh)?.first)
        assertEquals(null, TronclassSession.readTokenPair(noRefresh)?.second)

        val blankRefresh = TronclassSession.writeTokenPair("ACCESS-123", "  ")
        assertEquals("ACCESS-123", TronclassSession.readTokenPair(blankRefresh)?.first)
        assertEquals(null, TronclassSession.readTokenPair(blankRefresh)?.second)
    }

    @Test
    fun `坏数据读成没有而不是抛异常`() {
        assertEquals(null, TronclassSession.readTokenPair(null))
        assertEquals(null, TronclassSession.readTokenPair("  "))
        assertEquals(null, TronclassSession.readTokenPair("{不是 json"))
        assertEquals(null, TronclassSession.readTokenPair("""{"refreshToken":"只有refresh"}"""))
        assertEquals(null, TronclassSession.readTokenPair("""{"accessToken":"  "}"""))
    }

    @Test
    fun `键名与蓝本同形`() {
        // 与 Flutter 蓝本同形（accessToken/refreshToken），两边排障时能逐字对照。
        val raw = TronclassSession.writeTokenPair("A", "R")
        assertTrue(raw.contains("\"accessToken\""))
        assertTrue(raw.contains("\"refreshToken\""))
    }

    @Test
    fun `重启后能从存储读回 token 对`() {
        val store = InMemorySecureStore()
        PortalSession(store).save(validToken, "2021001")
        TronclassSession(store).saveTokens(
            accessToken = "PERSISTED-ACCESS",
            refreshToken = "PERSISTED-REFRESH",
        )

        // 新实例模拟「App 重启」。
        val reopened = TronclassSession(store)
        reopened.ensureLoaded()
        assertEquals(SessionState.LoggedIn, reopened.state)
        assertEquals("PERSISTED-ACCESS", reopened.accessToken())
        assertEquals("PERSISTED-REFRESH", reopened.refreshToken())
    }
}
