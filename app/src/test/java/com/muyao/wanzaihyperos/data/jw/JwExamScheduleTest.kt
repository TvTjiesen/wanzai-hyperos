package com.muyao.wanzaihyperos.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 最近一场考试。
 *
 * 值得釘的地方只有一个：**"未放开不可查" 不是日期**。
 * 把它当成"没有考试"，学生就会错过那场；把它当成"今天"，他会现在跑去考场。
 */
class JwExamScheduleTest {

    private fun exam(name: String, time: String, room: String = "", examName: String = "") =
        JwExam(
            courseName = name,
            examName = examName,
            time = time,
            room = room,
            seat = "",
            courseCode = "",
            credit = "",
            teacher = "",
            college = "",
            academicYear = "2026-2027",
            term = "1",
            batchNote = "",
        )

    @Test
    fun `日期从服务端原文里切出来`() {
        assertEquals(LocalDate.of(2026, 11, 14), JwExamSchedule.dateOf("2026-11-14(08:10-10:10)"))
        // 只有日期没有时间也要认
        assertEquals(LocalDate.of(2026, 11, 14), JwExamSchedule.dateOf("2026-11-14"))
        // 边界日期不许崩
        assertEquals(LocalDate.of(2026, 2, 28), JwExamSchedule.dateOf("2026-02-28"))
    }

    @Test
    fun `未放开不可查不是日期`() {
        assertNull(JwExamSchedule.dateOf("未放开不可查"))
        assertNull(JwExamSchedule.dateOf(""))
        // 2 月 30 日不存在，不许被 LocalDate 规整成 3 月 2 日
        assertNull(JwExamSchedule.dateOf("2026-02-30"))
    }

    @Test
    fun `最近一场取离今天最近的那场`() {
        val today = LocalDate.of(2026, 10, 31)
        val next = JwExamSchedule.next(
            listOf(
                exam("大学物理", "2026-11-14(08:10-10:10)", room = "教三401"),
                exam("微积分", "2026-11-07(08:00-10:00)", room = "教二101"),
            ),
            today,
        )!!
        assertEquals("微积分", next.courseName)
        assertEquals(7L, next.daysAway)
        assertEquals("08:00", next.startClock)
        assertEquals("10:00", next.endClock)
    }

    @Test
    fun `已经考过的不算下一场`() {
        val today = LocalDate.of(2026, 11, 20)
        val next = JwExamSchedule.next(
            listOf(exam("大学物理", "2026-11-14(08:10-10:10)")),
            today,
        )
        assertNull("过了日期的不该被当成还有几天", next)
    }

    @Test
    fun `就是今天要算 0 天`() {
        val today = LocalDate.of(2026, 11, 14)
        val next = JwExamSchedule.next(listOf(exam("大学物理", "2026-11-14(08:10-10:10)")), today)!!
        assertEquals(0L, next.daysAway)
        assertEquals("就是今天", JwExamSchedule.awayText(next.daysAway))
    }

    @Test
    fun `未开放的考试不参与排序但会被数出来`() {
        val today = LocalDate.of(2026, 10, 31)
        val exams = listOf(
            exam("数据挖掘技术与应用", "未放开不可查", room = "未放开不可查"),
            exam("大学物理", "2026-11-14(08:10-10:10)"),
        )
        val next = JwExamSchedule.next(exams, today)!!
        assertEquals("大学物理", next.courseName)
        assertEquals(1, JwExamSchedule.unknownCount(exams))
    }

    @Test
    fun `考场的未放开不许当成教室名显示`() {
        val next = JwExamSchedule.next(
            listOf(exam("某课", "2026-11-14(08:10-10:10)", room = "未放开不可查")),
            LocalDate.of(2026, 10, 31),
        )!!
        assertEquals("", next.room)
    }

    @Test
    fun `全都是未开放时下一场是 null`() {
        val exams = listOf(exam("甲", "未放开不可查"), exam("乙", "未放开不可查"))
        assertNull(JwExamSchedule.next(exams, LocalDate.of(2026, 10, 31)))
        assertEquals(2, JwExamSchedule.unknownCount(exams))
    }
}
