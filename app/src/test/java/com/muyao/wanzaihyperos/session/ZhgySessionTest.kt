package com.muyao.wanzaihyperos.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B17：智慧公寓憑據（`code`）的抽取與存取。
 *
 * 這一段最需要測試 —— 使用者貼上來的東西**五花八門**：
 * 完整網址、只有 `code=xxx` 的片段、直接貼碼、貼到旁邊的空白、
 * 甚至貼到手機分享選單帶來的多餘文字。抽錯了就等於拿錯憑據，
 * 而症狀是「開出來是錯誤頁」，很難查出是抽錯。
 */
class ZhgySessionTest {

    private val sampleCode = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6IkFCQyJ9.SIGNATURE"

    @Test
    fun `從完整網址抽出 code`() {
        val url = "http://zhgy.hzcu.edu.cn/mob/zuccindex.html?code=$sampleCode"
        assertEquals(sampleCode, ZhgySession.extractCode(url))
    }

    @Test
    fun `網址有其他參數時仍只抽 code`() {
        val url = "http://zhgy.hzcu.edu.cn/mob/zuccindex.html?from=share&code=$sampleCode&trackid=123"
        assertEquals(sampleCode, ZhgySession.extractCode(url))
    }

    @Test
    fun `兩端有空白也認得`() {
        assertEquals(
            sampleCode,
            ZhgySession.extractCode("  http://zhgy.hzcu.edu.cn/mob/zuccindex.html?code=$sampleCode  \n"),
        )
    }

    @Test
    fun `只貼 code 片段也認得`() {
        assertEquals(sampleCode, ZhgySession.extractCode("code=$sampleCode"))
    }

    @Test
    fun `直接貼碼本身也認得`() {
        assertEquals(sampleCode, ZhgySession.extractCode(sampleCode))
    }

    @Test
    fun `空的與沒有 code 的都回 null`() {
        assertNull(ZhgySession.extractCode(null))
        assertNull(ZhgySession.extractCode(""))
        assertNull(ZhgySession.extractCode("   "))
        // 有網址但沒有 code 參數 → 抽不到（不能拿整條網址當碼）
        assertNull(ZhgySession.extractCode("http://zhgy.hzcu.edu.cn/mob/zuccindex.html"))
        // 相似名稱不算（`mycode` 不是 `code`）
        assertNull(ZhgySession.extractCode("http://x/y?mycode=abc"))
    }

    @Test
    fun `拼出來的入口網址形狀正確`() {
        val url = ZhgySession.buildEntryUrl(sampleCode)
        assertTrue(url.startsWith("http://zhgy.hzcu.edu.cn/mob/zuccindex.html?code="))
        assertTrue(url.endsWith(sampleCode))
    }

    @Test
    fun `沒設過時 entryUrl 是 null`() {
        val s = ZhgySession(InMemorySecureStore())
        s.ensureLoaded()
        assertFalse(s.hasCode)
        assertNull(s.entryUrl())
        assertNull(s.code())
    }

    @Test
    fun `存了之後 entryUrl 就出來、清了就沒了`() {
        val store = InMemorySecureStore()
        val s = ZhgySession(store)
        s.save(sampleCode, 1_700_000_000_000L)
        assertTrue(s.hasCode)
        assertEquals(sampleCode, s.code())
        assertEquals(1_700_000_000_000L, s.obtainedAtMillis())
        assertTrue(s.entryUrl()!!.contains(sampleCode))

        s.clear()
        assertFalse(s.hasCode)
        assertNull(s.entryUrl())
    }

    @Test
    fun `持久化到 store 之後新實例讀得到`() {
        val store = InMemorySecureStore()
        ZhgySession(store).save(sampleCode, 123L)

        val reopened = ZhgySession(store)
        reopened.ensureLoaded()
        assertTrue("新實例應該讀得到存下的碼", reopened.hasCode)
        assertEquals(sampleCode, reopened.code())
    }

    @Test
    fun `toString 只報長度不報值`() {
        val s = ZhgySession(InMemorySecureStore())
        s.save(sampleCode, 1L)
        val text = s.toString()
        assertFalse("toString 不可以洩漏憑據：$text", text.contains(sampleCode))
        assertTrue(text.contains("${sampleCode.length}"))
    }

    @Test
    fun `空白字串不算有效憑據`() {
        val store = InMemorySecureStore()
        store.write(ZhgySession.KEY_CODE, "   ")
        val s = ZhgySession(store)
        s.ensureLoaded()
        assertFalse("純空白不該被當成有憑據", s.hasCode)
    }
}
