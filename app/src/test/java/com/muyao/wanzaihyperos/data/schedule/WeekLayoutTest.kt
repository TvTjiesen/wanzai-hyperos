package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 課表版面的規則（业主 2026-10-19：「把五天全部顯示」「雙層改單層」「只顯示本週」）。
 *
 * 真機樣本逐字取自 `docs/B13-TRONCLASS-SCHEDULE.txt`（他自己 30 門課的 25 種寫法），
 * 不是編的 —— 版面的對錯最終取決於**這些寫法有沒有被讀對**。
 */
class WeekLayoutTest {

    private fun slot(
        day: Int,
        start: Int,
        end: Int = start,
        course: String = "课",
        id: Int = 1,
        weekStart: Int = 1,
        weekEnd: Int = 18,
    ) = TronScheduleSlot(
        courseId = id,
        courseName = course,
        dayOfWeek = day,
        periodStart = start,
        periodEnd = end,
        weekStart = weekStart,
        weekEnd = weekEnd,
        raw = "周${day}第${start}-${end}节第${weekStart},${weekEnd}周",
    )

    // ── 五天全顯示 ────────────────────────────────────────────────────────

    @Test
    fun `周一到周五永远在，哪怕整天没课`() {
        val cols = WeekLayout.columns(listOf(slot(1, 1, 2)))
        assertEquals(listOf(1, 2, 3, 4, 5), cols.map { it.dayOfWeek })
        // 空的一天是**資訊**（週三沒課），不能因為沒課就從版面上消失。
        assertTrue(cols.first { it.dayOfWeek == 3 }.isEmpty)
    }

    @Test
    fun `周六周日只有真有课才上版面`() {
        val withSat = WeekLayout.columns(listOf(slot(6, 1, 2, "实验")))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), withSat.map { it.dayOfWeek })
        val withSun = WeekLayout.columns(listOf(slot(7, 1, 2, "讲座")))
        assertEquals(listOf(1, 2, 3, 4, 5, 7), withSun.map { it.dayOfWeek })
    }

    // ── 單層（不再橫向分道）──────────────────────────────────────────────

    @Test
    fun `撞课的两门在同一栏里，各占一格，并互相标记`() {
        // 真機確實有這種：週二第 1-2 節有體育與另一門（docs/B13 樣本 [18] 同段）。
        val a = slot(2, 1, 2, "体育", id = 11)
        val b = slot(2, 1, 2, "机动", id = 12)
        val cols = WeekLayout.columns(listOf(a, b))
        val tuesday = cols.first { it.dayOfWeek == 2 }

        // 單層 = 一天**一欄**：這裡沒有「欄中欄」的概念，兩門都在同一個 slots 裡。
        assertEquals(2, tuesday.slots.size)
        // 但界面必須告訴他「這不是一門課的兩行」，否則他會以為是連堂。
        assertEquals(listOf("机动"), tuesday.conflictsOf(a).map { it.courseName })
        assertEquals(listOf("体育"), tuesday.conflictsOf(b).map { it.courseName })
    }

    @Test
    fun `时间不重叠就不算撞课`() {
        val a = slot(3, 1, 2)
        val b = slot(3, 3, 4)
        val c = slot(3, 4, 5)   // 與 b 共用第 4 節 → 重疊
        val col = WeekLayout.columns(listOf(a, b, c)).first { it.dayOfWeek == 3 }
        assertTrue(col.conflictsOf(a).isEmpty())
        assertEquals(1, col.conflictsOf(b).size)
    }

    @Test
    fun `不同天的同一节次不算撞课`() {
        assertFalse(WeekLayout.overlaps(slot(1, 1, 2), slot(2, 1, 2)))
    }

    // ── 只顯示本週 ────────────────────────────────────────────────────────

    @Test
    fun `真机样本：第4周这一门课没有课（1-3 与 5-17 两段中间断开）`() {
        // B13 樣本 [0]：周2第3-4节第1,3周;周2第3-4节第5,17周
        val parsed = TronScheduleParser.parse(
            "周2第3-4节第1,3周;周2第3-4节第5,17周",
            courseId = 901,
            courseName = "某课",
        )
        assertEquals(2, parsed.slots.size)
        assertTrue(WeekLayout.forWeek(parsed.slots, 1).isNotEmpty())
        assertTrue(WeekLayout.forWeek(parsed.slots, 3).isNotEmpty())
        // ★ 第 4 週**真的沒課** —— 這就是「隔週課」的表達方式（學校拆成兩段寫）。
        assertTrue(WeekLayout.forWeek(parsed.slots, 4).isEmpty())
        assertTrue(WeekLayout.forWeek(parsed.slots, 5).isNotEmpty())
    }

    @Test
    fun `不知道第几周时不筛，让界面去说不知道`() {
        val all = listOf(slot(1, 1, 2), slot(2, 3, 4))
        assertEquals(2, WeekLayout.forWeek(all, null).size)
    }

    @Test
    fun `真机样本：四段同一门课只算一门`() {
        // B13 樣本 [2]：同一門課拆成 4 個區間（第 4 週沒課）
        val text = "周2第8-9节第10,16周;周2第8-9节第1,3周;周2第8-9节第5,9周;周2第8-9节第17,17周"
        val slots = TronScheduleParser.parse(text, courseId = 902, courseName = "同一门课").slots
        assertEquals(4, slots.size)
        // 直接 slots.size 會報「4 門課」——那是虛報，他只有 1 門。
        assertEquals(1, WeekLayout.courseCount(slots))
        assertEquals(1, WeekLayout.courseCount(WeekLayout.forWeek(slots, 8)))
        assertEquals(0, WeekLayout.courseCount(WeekLayout.forWeek(slots, 4)))
    }

    @Test
    fun `真机样本：跨周一到周日的多段能摊成六栏`() {
        // B13 樣本 [7]：同一門課在週一/週五/週六/週日都有
        val text = "周1第1-2节第18,18周;周5第1-2节第1,1周;周5第1-2节第4,6周;" +
            "周5第1-2节第8,15周;周5第1-2节第17,17周;周6第1-2节第4,4周;周7第1-2节第1,1周"
        val slots = TronScheduleParser.parse(text, courseId = 903, courseName = "分散课").slots
        val week1 = WeekLayout.forWeek(slots, 1)
        val cols = WeekLayout.columns(week1)
        // 第 1 週：週五、週日有課 ⇒ 週六不該出現（那一段是第 4 週）
        assertEquals(listOf(1, 2, 3, 4, 5, 7), cols.map { it.dayOfWeek })
        assertEquals(1, cols.first { it.dayOfWeek == 5 }.slots.size)
        assertFalse(cols.any { it.dayOfWeek == 6 })
    }

    // ── 日程視圖 ──────────────────────────────────────────────────────────

    // ── 節次行對齊（业主 2026-10-20：「侧边写个时间课程对齐时间」）────────────

    private fun LaneCells(lanes: List<WeekLayout.LaneCell>): String = lanes.joinToString(",") {
        when (it) {
            is WeekLayout.LaneCell.Free -> "空${it.rows}"
            is WeekLayout.LaneCell.Placed -> "${it.slot.courseName}×${it.rows}${if (it.misaligned) "↓" else ""}"
        }
    }

    private fun rowsOf(lanes: List<WeekLayout.LaneCell>): Int = lanes.sumOf {
        when (it) {
            is WeekLayout.LaneCell.Free -> it.rows
            is WeekLayout.LaneCell.Placed -> it.rows
        }
    }

    @Test
    fun `每一栏都恰好占满同样的行数，否则各栏对不齐`() {
        val only = WeekLayout.columns(listOf(slot(1, 6, 9)))
        val mon = WeekLayout.lane(only.first { it.dayOfWeek == 1 }, 11)
        val tue = WeekLayout.lane(only.first { it.dayOfWeek == 2 }, 11)
        assertEquals("空5,课×4,空2", LaneCells(mon))
        assertEquals(11, rowsOf(mon))
        // 空的一天也是一条 11 行的空格子 —— 塌掉的话后面几栏就会跟时间轴错位
        assertEquals(11, rowsOf(tue))
        assertEquals("空11", LaneCells(tue))
    }

    @Test
    fun `撞课的那一门不消失，往下挤并标成不在自己行上`() {
        val a = slot(2, 1, 2, "体育", id = 11)
        val b = slot(2, 1, 2, "机动", id = 12)
        val lane = WeekLayout.lane(WeekLayout.columns(listOf(a, b)).first { it.dayOfWeek == 2 }, 4)
        // 單層不切道 ⇒ 两门都画得下，但第二门只能落在第 3-4 行上
        assertEquals("体育×2,机动×2↓", LaneCells(lane))
        assertEquals(4, rowsOf(lane))
    }

    @Test
    fun `一天里几门课之间的空档按真实节次留出来`() {
        val slots = listOf(slot(3, 1, 2, "早课"), slot(3, 8, 9, "晚课"))
        val lane = WeekLayout.lane(WeekLayout.columns(slots).first { it.dayOfWeek == 3 }, 11)
        assertEquals("早课×2,空5,晚课×2,空2", LaneCells(lane))
        assertEquals(11, rowsOf(lane))
    }

    @Test
    fun `行数只到本周真的用到的那一节，并且封个顶`() {
        assertEquals(9, WeekLayout.rowsNeeded(WeekLayout.columns(listOf(slot(1, 6, 9), slot(2, 1, 2)))))
        assertEquals(0, WeekLayout.rowsNeeded(WeekLayout.columns(emptyList())))
        // 来源万一给个离谱的节次号（第 40 节），界面不能因此长成一屏空白
        assertEquals(14, WeekLayout.rowsNeeded(WeekLayout.columns(listOf(slot(1, 1, 40)))))
    }

    @Test
    fun `日程按天升序、天内按节次升序，且只留有课的天`() {
        val slots = listOf(slot(5, 3, 4), slot(1, 5, 6), slot(1, 1, 2), slot(3, 1, 2))
        val days = WeekLayout.agenda(slots)
        assertEquals(listOf(1, 3, 5), days.map { it.first })
        assertEquals(listOf(1, 5), days.first().second.map { it.periodStart })
    }

    // ── 節次 → 幾點（业主 2026-10-19：「你课表和时间对不上」）──────────────

    @Test
    fun `他自己填的时间优先，跨节的课读首尾两头`() {
        // 一行 = **一小节**自己的上下课（設定頁那排输入框就是这个语义：
        // 第 i 行 → 第 i 节），所以跨 3-4 节要读第 3 行的上课与第 4 行的下课。
        val times = listOf(
            "08:00" to "08:45",
            "08:55" to "09:40",
            "10:00" to "10:45",
            "10:55" to "11:40",
        )
        assertEquals("10:00-11:40", PeriodClock.label(times, 3, 4))
        assertEquals("08:00-11:40", PeriodClock.label(times, 1, 4))
        assertEquals("08:55-09:40", PeriodClock.label(times, 2, 2))
        // 他填过第 6 节的上课 ⇒ 盖掉学校日历里的 13:30（「我改了没生效」是最不能出现的）。
        // 注意行号：times 只有 4 行，要填到第 6 行得先把第 5 行占上。
        val withOverride = times + ("11:30" to "") + ("13:00" to "")
        assertEquals("13:00-15:05", PeriodClock.label(withOverride, 6, 7))
    }

    @Test
    fun `两头都查不到时不显示，绝不显示半截时间`() {
        // 第 11 节的上课、第 12 节的下课：他自己没填、学校日历里也没有 ⇒ null。
        assertNull(PeriodClock.label(emptyList(), 11, 12))
        // 单节：只知道第 1 节几点上课、不知道几点下课（日历里只出现过「第1-2节」这一对）
        // ⇒ 不显示「08:00」这种半截（会被读成「一节课 08:00 开始，那几点结束？」）。
        assertNull(PeriodClock.label(emptyList(), 1, 1))
        assertNull(PeriodClock.label(emptyList(), 99, 100))
        // 前后空格要吃掉，否则会显示成「 08:00-09:40」。
        assertEquals("08:00-09:40", PeriodClock.label(listOf(" 08:00 " to " 09:40 "), 1, 1))
    }

    @Test
    fun `没填时用的是学校日历那份，而不是编一个时间`() {
        // ★ 这一条钉住两层来源的分工：他没填 ⇒ 用他给的那个 .ics 推出来的表；
        //   表里没有的节次 ⇒ 还是 null（**不外推、不拿 45 分钟规律去凑**）。
        assertEquals("13:30-15:05", PeriodClock.label(emptyList(), 6, 7))
        assertEquals("15:20-16:55", PeriodClock.label(emptyList(), 8, 9))
        assertNull(PeriodClock.label(List(12) { "" to "" }, 12, 13))
    }

    @Test
    fun `学校日历自洽：文件里出现过的每个区间都能从两端拼回原样`() {
        // 这条不是测「函数返回某个字符串」，是测**这份表自己对不对**：
        // 任何一头被改错（比如把 14:15 写成 14:55），对应区间立刻对不上。
        // 期望值逐字来自 `日历-浙大城市学院.ics`（53 个事件、11 种区间，各只有一种时刻）。
        for ((start, end, expected) in SchoolPeriods.observedRanges) {
            assertEquals("第${start}-${end}节", expected, PeriodClock.label(emptyList(), start, end))
        }
        assertEquals(11, SchoolPeriods.observedRanges.size)
    }

    @Test
    fun `空格当没填，认得的节次按两头任一算`() {
        val times = listOf("08:00" to "09:40", " " to " ", "14:00" to "15:40")
        assertEquals(2, PeriodClock.filledCount(times))   // 第 2 行是空格 = 没填
        // 学校日历认得第 1~11 节（每一节至少有一头被观测到），第 12 节没有
        assertEquals(11, PeriodClock.knownCount(emptyList()))
        // 他多填一行（第 12 节）⇒ 认得的节次跟着涨，不会被学校那份的 11 行卡住
        assertEquals(12, PeriodClock.knownCount(List(12) { "" to "" } + ("19:00" to "20:30")))
    }
}
