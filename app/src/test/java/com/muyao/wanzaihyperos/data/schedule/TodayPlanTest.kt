package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.tronclass.ScheduleSource
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 首页那句「今天还要上什么课」（业主 2026-10-20：「提示你今天还要上什么课，而不是列出今日课程」）。
 *
 * 时刻全部用**学校日历那张表**里的真值（`SchoolPeriods`，来自他给的 .ics），
 * 所以这些断言测的是"按他学校真实的作息，这句话对不对"，不是我自己造的数。
 */
class TodayPlanTest {

    private fun slot(day: Int, start: Int, end: Int, name: String, id: Int) = TronScheduleSlot(
        courseId = id,
        courseName = name,
        dayOfWeek = day,
        periodStart = start,
        periodEnd = end,
        weekStart = 1,
        weekEnd = 17,
        raw = "",
        room = "理4-205",
        source = ScheduleSource.JIAOWU,
    )

    /** 周三：第3-4节 数据结构（09:50-11:25）、第8-9节 总体国家安全观（15:20-16:55）。 */
    private val wed = listOf(
        slot(3, 3, 4, "数据结构与算法", 11),
        slot(3, 8, 9, "总体国家安全观", 12),
    )

    private fun at(date: String, time: String) =
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))

    @Test
    fun `早上说的是还要上几节，下一节几点、还有多久、在哪间`() {
        val p = TodayPlan.of(wed, 6, at("2026-10-21", "08:00"), emptyList())
        assertEquals("今天还要上 2 节课", p.headline)
        assertEquals("下一节 09:50 数据结构与算法 · 理4-205（还有 1 小时 50 分）", p.detail)
        assertEquals(2, p.remaining.size)
    }

    @Test
    fun `正在上课时说的是这一节还剩多久，不是列表`() {
        val p = TodayPlan.of(wed, 6, at("2026-10-21", "10:00"), emptyList())
        assertEquals("正在上 数据结构与算法", p.headline)
        assertEquals("还剩 1 小时 25 分（09:50-11:25） · 理4-205", p.detail)
        // 正在上的这节**仍然算没上完**（他得在教室里）
        assertEquals(listOf("数据结构与算法", "总体国家安全观"), p.remaining.map { it.courseName })
    }

    @Test
    fun `全部上完就说上完了，不把已下课的再列一遍`() {
        val p = TodayPlan.of(wed, 6, at("2026-10-21", "18:00"), emptyList())
        assertEquals("今天的课上完了", p.headline)
        assertEquals("共 2 节，都上完了 · 第 6 周", p.detail)
        assertTrue(p.remaining.isEmpty())
    }

    @Test
    fun `今天没课时顺带说下一天有什么`() {
        val p = TodayPlan.of(wed, 6, at("2026-10-19", "20:00"), emptyList())   // 周一
        assertEquals("今天没有课", p.headline)
        assertEquals("下一节：周三 第3-4节 数据结构与算法", p.detail)
    }

    @Test
    fun `★ 认不出时间的课绝不能被当成已经上完`() {
        // 第 12-13 节：他自己没填、学校日历里也没有。
        val withUnknown = wed + slot(3, 12, 13, "晚上补课", 13)
        val p = TodayPlan.of(withUnknown, 6, at("2026-10-21", "18:00"), emptyList())
        // 反过来（判成"上完了"）就是让他以为某节课不用去 —— 那是最贵的一种错。
        assertEquals(listOf("晚上补课"), p.remaining.map { it.courseName })
        assertEquals("今天还要上 1 节课", p.headline)
        assertEquals("第12-13节 的上课时间学校没给，去设置里填一次", p.detail)
        assertTrue(!p.clockKnown)
    }

    @Test
    fun `不知道第几周时不猜今天有没有课`() {
        val p = TodayPlan.of(wed, null, at("2026-10-21", "10:00"), emptyList())
        assertEquals("还不知道目前是第几教学周", p.headline)
        assertTrue(p.remaining.isEmpty())
    }

    @Test
    fun `他自己填的时间盖过学校那份`() {
        // 他把第 3 节的上课时间改成 09:00 ⇒ 首页那句"下一节"要跟着变。
        val times = List(11) { "" to "" }.toMutableList()
        times[2] = "09:00" to ""     // 第 3 节：只填上课
        val p = TodayPlan.of(wed, 6, at("2026-10-21", "08:00"), times)
        assertTrue(p.detail!!.startsWith("下一节 09:00"))
    }

    // ── 週切換那一行的文字（业主：「本周我哪知道是几周，改成第几周，第几学期」）──

    @Test
    fun `周切换写绝对的第几周，学期名用学校自己写的那段`() {
        assertEquals("第 6 周 · 本周 · 2026-2027学年第1学期", WeekLayout.switchLabel(6, 0, "2026-2027学年第1学期"))
        assertEquals("第 8 周 · 本周后 2 周 · 2026-2027学年第1学期", WeekLayout.switchLabel(6, 2, "2026-2027学年第1学期"))
        assertEquals("第 5 周 · 本周前 1 周", WeekLayout.switchLabel(6, -1, ""))
        // 还不知道第几周 ⇒ 不编数字，也不写「本周」（那句在这状态下没有意义）
        assertEquals("还没取到教学周", WeekLayout.switchLabel(null, 0, ""))
        assertEquals("还没取到教学周 · 2026-2027学年第1学期", WeekLayout.switchLabel(null, 0, "2026-2027学年第1学期"))
    }
}
