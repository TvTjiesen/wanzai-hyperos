package com.muyao.wanzaihyperos.data.reminder

import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 上课提醒的**算题**（纯函数，不碰 Android）。
 *
 * 这几条盯的都是「宁可不说，也不编」：排不出来就是排不出来，
 * 排错了会让他按着通知去错的地方。
 */
class ClassReminderPlanTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 2026-11-02 是周一。 */
    private val monday: LocalDate = LocalDate.of(2026, 11, 2)

    private fun slot(
        day: Int,
        start: Int,
        end: Int = start,
        weekStart: Int = 1,
        weekEnd: Int = 16,
        name: String = "线性代数",
        room: String = "理4-224",
    ) = TronScheduleSlot(
        courseId = 1,
        courseName = name,
        dayOfWeek = day,
        periodStart = start,
        periodEnd = end,
        weekStart = weekStart,
        weekEnd = weekEnd,
        raw = "",
        teacher = "",
        room = room,
        slotLabel = "第" + start + "-" + end + "节",
    )

    private fun millisOf(date: LocalDate, clock: String): Long {
        val parts = clock.split(":")
        return date.atTime(parts[0].toInt(), parts[1].toInt()).atZone(zone).toInstant().toEpochMilli()
    }

    private val everyWeek: (LocalDate) -> Int? = { 7 }

    @Test
    fun `提前量是从上课时刻往前减的`() {
        val now = monday.atTime(6, 0)
        val out = ClassReminderPlan.plan(
            slots = listOf(slot(day = 1, start = 1)),
            times = emptyList(),
            now = now,
            leadMinutes = 10,
            weekOf = everyWeek,
        )
        // 第 1 节 08:00，提前 10 分钟 ⇒ 07:50
        assertEquals(1, out.size)
        assertEquals(millisOf(monday, "07:50"), out[0].atMillis)
        assertEquals("08:00", out[0].startLabel)
        assertEquals("理4-224", out[0].room)
    }

    @Test
    fun `取不到上课时刻的那一节不排`() {
        // 学校日历里第 2 节只有结束时间、没有开始时间。
        val out = ClassReminderPlan.plan(
            slots = listOf(slot(day = 1, start = 2)),
            times = emptyList(),
            now = monday.atTime(6, 0),
            leadMinutes = 10,
            weekOf = everyWeek,
        )
        assertTrue("第 2 节没有开始时间，不许拿结束时间去猜", out.isEmpty())
    }

    @Test
    fun `已经响过的不再排`() {
        // 08:00 的课、提前 10 分钟 ⇒ 07:50 响；now 已经在 09:00。
        val out = ClassReminderPlan.plan(
            slots = listOf(slot(day = 1, start = 1)),
            times = emptyList(),
            now = monday.atTime(9, 0),
            leadMinutes = 10,
            weekOf = everyWeek,
        )
        assertTrue("过去的时刻排进闹钟，打开 App 就会补弹一串旧提醒", out.isEmpty())
    }

    @Test
    fun `不知道第几周就整段不排`() {
        val out = ClassReminderPlan.plan(
            slots = listOf(slot(day = 1, start = 1)),
            times = emptyList(),
            now = monday.atTime(6, 0),
            leadMinutes = 10,
            weekOf = { null },
        )
        assertTrue("不知道周次却提醒「明天有课」，等于替他猜单双周", out.isEmpty())
    }

    @Test
    fun `不在上课周次里的课不排`() {
        val out = ClassReminderPlan.plan(
            slots = listOf(slot(day = 1, start = 1, weekStart = 8, weekEnd = 16)),
            times = emptyList(),
            now = monday.atTime(6, 0),
            leadMinutes = 10,
            weekOf = { 7 },
        )
        assertTrue("第 7 周上的不是这门课", out.isEmpty())
    }

    @Test
    fun `撞课的两门课都要排`() {
        val out = ClassReminderPlan.plan(
            slots = listOf(
                slot(day = 1, start = 1, name = "线性代数"),
                slot(day = 1, start = 1, name = "大学英语"),
            ),
            times = emptyList(),
            now = monday.atTime(6, 0),
            leadMinutes = 10,
            weekOf = everyWeek,
        )
        assertEquals(2, out.size)
        assertEquals(setOf("线性代数", "大学英语"), out.map { it.courseName }.toSet())
    }

    @Test
    fun `同一门课同一时刻只留一条`() {
        val out = ClassReminderPlan.plan(
            slots = listOf(
                slot(day = 1, start = 1, name = "线性代数"),
                slot(day = 1, start = 1, name = "线性代数"),
            ),
            times = emptyList(),
            now = monday.atTime(6, 0),
            leadMinutes = 10,
            weekOf = everyWeek,
        )
        assertEquals(1, out.size)
    }

    @Test
    fun `清单按时间从早到晚`() {
        val out = ClassReminderPlan.plan(
            slots = listOf(
                slot(day = 3, start = 1, name = "周三的课"),
                slot(day = 1, start = 1, name = "周一的课"),
            ),
            times = emptyList(),
            now = monday.atTime(6, 0),
            leadMinutes = 10,
            weekOf = everyWeek,
        )
        assertEquals(listOf("周一的课", "周三的课"), out.map { it.courseName })
    }

    @Test
    fun `只看今天往后这几天`() {
        val out = ClassReminderPlan.plan(
            slots = listOf(slot(day = 1, start = 1)),
            times = emptyList(),
            now = monday.atTime(6, 0),
            leadMinutes = 10,
            weekOf = everyWeek,
            days = 1,
        )
        assertEquals(1, out.size)
    }

    @Test
    fun `自己填的时刻优先于学校日历`() {
        val times = listOf("07:30" to "09:05")
        val out = ClassReminderPlan.plan(
            slots = listOf(slot(day = 1, start = 1)),
            times = times,
            now = monday.atTime(6, 0),
            leadMinutes = 5,
            weekOf = everyWeek,
        )
        assertEquals(1, out.size)
        assertEquals("07:30", out[0].startLabel)
        assertEquals(millisOf(monday, "07:25"), out[0].atMillis)
    }

    @Test
    fun `提前量比离上课还久就不排那一条`() {
        // 08:00 的课、提前 60 分钟 ⇒ 07:00 响；now 已经 07:30，这条不该排。
        val out = ClassReminderPlan.plan(
            slots = listOf(slot(day = 1, start = 1)),
            times = emptyList(),
            now = monday.atTime(7, 30),
            leadMinutes = 60,
            weekOf = everyWeek,
        )
        assertTrue(out.isEmpty())
    }
    // ── D4（业主 2026-11-08 20:11 拍板）───────────────────────────────
    // 总单说「提醒隐私现在默认 true，无依据的默认关」，但这里的 true 是「隐藏课名与教室」
    // 那一侧 —— 默认关 = 默认把课程名和教室推到锁屏上，泄露面更大。
    // 业主看过两个方向后选择保持现状。这条断言把决定钉住：
    // 以后谁想把它改成 false，必须同时改这条理由，而不是顺手改个默认值。
    @Test
    fun `D4 提醒隐私默认开，默认不把课名推到锁屏上`() {
        val prefs = java.io.File("src/main/java/com/muyao/wanzaihyperos/data/UiPrefs.kt")
        assertTrue("UiPrefs.kt 不见了", prefs.isFile)
        val text = prefs.readText(Charsets.UTF_8)
        assertTrue(
            "reminderPrivate 的默认值不再是 true ⇒ 通知会默认带课程名与教室",
            "var reminderPrivate by mutableStateOf(true)" in text,
        )
        // 读盘那一支也必须 true（否则首次读到缺键时会变成 false，与上面的默认不一致）
        assertTrue(
            "读盘缺键时的兜底值不是 true，与内存默认不一致",
            "p.getBoolean(KEY_REMINDER_PRIVATE, true)" in text,
        )
    }

}
