package com.muyao.wanzaihyperos.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 本地缓存的三条边界（业主 2026-10-20：「帮我缓存到本地不行吗」）。
 *
 * 這裡盯的不是"能不能存取"，而是**什麼絕對不能存**：
 * 寫操作、會話類端點、被踢回登入頁時的 HTML。
 * 那三種一旦進緩存，症狀都是「界面看著正常，其實在播舊數據」——
 * 這個工程裡最貴的一類錯。
 */
class ResponseCacheTest {

    private val source = "tronclass"
    private val identity = RequestIdentity("synthetic-cache-owner", 1)
    private fun dir() = File(System.getProperty("java.io.tmpdir"), "wzcache-${System.nanoTime()}")
    /** 假时钟：緩存的過期判據不靠真實時鐘，否則這類測試會隨時鐘抖動。 */
    // 起點必須是**真實現在**：文件的最後修改時間用的是系統時鐘，
    // 假時鐘從 0 開始會被「age < 0 ⇒ 時鐘被改過，當沒有緩存」那條防呆全部吃掉
    // （這條防呆本身是對的：生產環境裡時鐘跳動時寧可重新取，也不要播一份日期對不上的舊數據）。
    private class FakeClock(var now: Long = System.currentTimeMillis()) {
        /**
         * 不回退到真實時鐘之前。
         *
         * ★ 這條不是偷懶：文件的 mtime 是**真實系統時鐘**寫的，而我這個假時鐘是凍結的，
         *   於 `at()` 會比 mtime 小 ⇒ `age < 0` ⇒ 命中生產代碼裡「時鐘被改過就当没缓存」
         *   那條防呆，三條 get 測試全紅。那條防呆本身是對的（寧可重取也不要播一份
         *   日期對不上的舊數據），錯的是測試假裝時間停住了。
         */
        fun at(): Long = maxOf(now, System.currentTimeMillis())
        fun advance(ms: Long) { now = at() + ms }
    }

    private fun clock() = FakeClock()

    @Test
    fun `只有 GET 能缓存，写操作一律不经过这里`() {
        val c = ResponseCache(dir())
        assertTrue(c.cacheable("/api/todos", "GET"))
        assertTrue(!c.cacheable("/api/rollcall/9/answer_qr_rollcall", "PUT"))
        assertTrue(!c.cacheable("/api/course/activities/9/submissions", "POST"))
        assertTrue(!c.cacheable("/api/radar/rollcalls", "DELETE"))
    }

    @Test
    fun `會話與掃碼端點不緩存`() {
        val c = ResponseCache(dir())
        // 掃碼那支是「把碼交給服務端認領」，可能記考勤 —— 緩存它等於重複簽到
        assertTrue(!c.cacheable("/api/qr-code/scan?codeStr=abc", "GET"))
        assertTrue(!c.cacheable("/api/auth-server-url", "GET"))
        assertTrue(!c.cacheable("/api/qrcode/login", "GET"))
        assertTrue(!c.cacheable("/api/user/logout", "GET"))
    }

    @Test
    fun `过期就当没有，绝不播旧数据`() {
        val k = clock()
        val c = ResponseCache(dir(), k::at)
        c.put(source, identity, "/api/todos", """{"todo_list":[]}""")
        assertNotNull(c.get(source, identity, "/api/todos", 5_000))
        k.advance(3_000)                                   // 過 3 秒
        assertNull(c.get(source, identity, "/api/todos", 2_000))              // TTL 2 秒 ⇒ 當沒有
        assertNotNull(c.get(source, identity, "/api/todos", 60_000))          // TTL 60 秒 ⇒ 還在
    }

    @Test
    fun `不同查询串是不同条目，不会把账单当成课表`() {
        val k = clock()
        val c = ResponseCache(dir(), k::at)
        c.put(source, identity, "/api/todos?page=1", "A")
        c.put(source, identity, "/api/todos?page=2", "B")
        assertEquals("A", c.get(source, identity, "/api/todos?page=1", 60_000))
        assertEquals("B", c.get(source, identity, "/api/todos?page=2", 60_000))
        assertNull(c.get(source, identity, "/api/todos?page=3", 60_000))
    }

    @Test
    fun `带中文与百分号的长地址也能存（文件名不能用原 URL）`() {
        val k = clock()
        val c = ResponseCache(dir(), k::at)
        val path = "/api/course/37/student/12/rollcalls?fields=中文&x=%7B%7D"
        c.put(source, identity, path, "OK")
        assertEquals("OK", c.get(source, identity, path, 60_000))
    }

    @Test
    fun `清除返回真的字节数，界面才敢说清了多少`() {
        val k = clock()
        val c = ResponseCache(dir(), k::at)
        c.put(source, identity, "/api/todos", "x".repeat(4096))
        c.put(source, identity, "/api/my-courses", "y".repeat(1024))
        assertEquals(2, c.entries())
        assertTrue(c.usedBytes() >= 5120)
        val freed = c.clearAll()
        assertTrue(freed >= 5120)
        assertEquals(0, c.entries())
        assertEquals(0L, c.usedBytes())
    }

    @Test
    fun `读不到的文件当没缓存，不抛给界面`() {
        val k = clock()
        val c = ResponseCache(dir(), k::at)
        assertNull(c.get(source, identity, "/api/nothing", 60_000))
        assertNull(c.get(source, identity, "/api/nothing/../weird", 60_000))
    }
}
