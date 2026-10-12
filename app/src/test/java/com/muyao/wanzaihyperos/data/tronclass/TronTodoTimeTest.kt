package com.muyao.wanzaihyperos.data.tronclass

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * 學在城院待辦的解析 —— 重點盯**時區**。
 *
 * ### 為什麼專門測時區
 *
 * 真機取證：`end_time` 是帶 `Z` 的 UTC，值形如 `2026-10-14T15:30:00Z`，
 * 而同一份作業在網頁上顯示「**截止日期 2026.10.14 23:30**」——
 * 差 8 小時，正好是東八區的偏移。
 *
 * 如果照原樣顯示原始字串，使用者看到的是 **15:30**，
 * 比真實截止時間**早 8 小時** —— 那是會讓人**錯過交作業**的錯誤，
 * 不是顯示美觀問題。所以這條有專門的測試。
 */
class TronTodoTimeTest {

    private lateinit var original: TimeZone

    @Before
    fun fixTimezone() {
        // 固定成東八區：否則測試結果取決於跑測試那台機器的時區，
        // 在 CI 上會隨機紅。
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun restoreTimezone() {
        TimeZone.setDefault(original)
    }

    @Test
    fun `带 Z 的截止时间按 UTC 解析`() {
        // 2026-10-14T15:30:00Z == 北京时间 2026-10-14 23:30
        // （期望值用 python 算的，不是手算 —— 手算第一版算錯了一位，
        //   反而是解析器对、测试错。用工具算比用脑算可靠。）
        val millis = TronTodoParser.parseIsoMillis("2026-10-14T15:30:00Z")
        assertEquals(1791991800000L, millis)
    }

    @Test
    fun `解析出的时间转本地后与网页显示一致`() {
        val millis = TronTodoParser.parseIsoMillis("2026-10-14T15:30:00Z")!!
        val local = java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        // 網頁實測顯示「2026.10.14 23:30」—— 必須對得上。
        assertEquals("2026-10-14 23:30", local)
    }

    @Test
    fun `不带时区的时间按本地解析`() {
        // 服務端哪天改成不帶 Z，就按本地時間理解（而不是當 UTC 再+8，那會又錯一次）。
        val millis = TronTodoParser.parseIsoMillis("2026-10-14T23:30:00")
        val local = java.time.Instant.ofEpochMilli(millis!!)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        assertEquals("23:30", local)
    }

    @Test
    fun `end_time 为 null 时不给时间`() {
        // 早期取證時 19 條全是 null —— 那時不能猜一個時間出來。
        assertNull(TronTodoParser.parseIsoMillis(""))
        assertNull(TronTodoParser.parseIsoMillis("not-a-time"))
    }
}
