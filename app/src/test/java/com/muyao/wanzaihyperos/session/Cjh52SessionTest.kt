package com.muyao.wanzaihyperos.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CJH52 會話的離線驗證。
 *
 * 重點不在「能存能讀」（那是 [StoredSession] 的事），而在本類的三條**約定**：
 * 1. tempcode 是憑據 —— [Cjh52Session.toString] 不得吐出值；
 * 2. 壞資料讀成「沒有」而不是崩；
 * 3. 清除要真的從存儲裡刪掉（不是只清內存）。
 */
class Cjh52SessionTest {

    private val uuid = "6475ed90-4441-442a-826d-261a1a8371d4"

    @Test
    fun `存了就能讀 且記錄取得時間`() {
        val store = InMemorySecureStore()
        val s = Cjh52Session(store)

        s.save(token = uuid, nowMillis = 1_700_000_000_000L)

        assertEquals(uuid, s.token())
        assertEquals(1_700_000_000_000L, s.obtainedAtMillis())
        assertTrue(s.hasToken)
        assertEquals(SessionState.LoggedIn, s.state)
    }

    @Test
    fun `重新開一個實例仍讀得到（真的落盤了）`() {
        val store = InMemorySecureStore()
        Cjh52Session(store).save(token = uuid, nowMillis = 1L)

        val reopened = Cjh52Session(store)
        reopened.ensureLoaded()

        assertEquals(uuid, reopened.token())
    }

    @Test
    fun `clear 會真的從存儲刪掉 不只是清內存`() {
        val store = InMemorySecureStore()
        val s = Cjh52Session(store)
        s.save(token = uuid, nowMillis = 1L)

        s.clear()

        assertNull(s.token())
        assertFalse(s.hasToken)
        // ★ 關鍵：換一個實例（= 重新從存儲讀）也讀不到，才算真的刪了。
        val reopened = Cjh52Session(store)
        reopened.ensureLoaded()
        assertNull("clear 必須真的刪存儲，否則重啟後憑據還在", reopened.token())
        assertEquals(SessionState.LoggedOut, reopened.state)
    }

    @Test
    fun `toString 不吐出憑據`() {
        val store = InMemorySecureStore()
        val s = Cjh52Session(store)
        s.save(token = uuid, nowMillis = 1L)

        val text = s.toString()

        assertEquals(false, text.contains(uuid))
        assertTrue("應該只有長度：$text", text.contains("36"))
    }

    @Test
    fun `壞資料讀成沒有 而不是崩`() {
        val store = InMemorySecureStore()
        // 直接塞一個「像 UUID 但其實是垃圾」的值 → 仍可讀（本類不做格式校驗）
        store.write(Cjh52Session.KEY_TOKEN, "")
        val s = Cjh52Session(store)
        s.ensureLoaded()

        // 空字串按「沒有」處理（takeIf { isNotBlank }）
        assertNull(s.token())
        assertEquals(SessionState.LoggedOut, s.state)
    }
}
