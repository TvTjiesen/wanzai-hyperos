package com.muyao.wanzaihyperos.data.reminder

import com.muyao.wanzaihyperos.data.schedule.PeriodClock
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 一条上课提醒：到点要弹的那条通知，内容在这一刻就定死了。
 *
 * 为什么把文案一起存下来（而不是到点再查课表）：闹钟到点时进程可能是新起的、
 * 网可能不通、课表也可能刷失败。到点还说得出「哪门课、几点、哪间教室」，
 * 只能靠排闹钟那一刻就把它抄下来。
 */
data class ClassReminder(
    /** 提醒应该响的时刻（毫秒，UTC）。 */
    val atMillis: Long,
    val courseName: String,
    /** 空 = 这个来源没给教室（不是「没有教室」）。 */
    val room: String,
    /** 上课时刻，形如 08:00，直接来自 PeriodClock.startOf。 */
    val startLabel: String,
    /** 形如 第1-2节。 */
    val periodLabel: String,
)

/**
 * 把课表算成一串上课提醒。**纯函数**：不碰 Android、不碰磁盘，能单测。
 *
 * 三条不许编的规矩：
 *  1. 上课时刻只认 PeriodClock.startOf；学校时间表里第 2/4/11 节只有结束时间、
 *     没有开始时间，取不到就**这一节不排提醒**，绝不拿结束时间减一节课去猜。
 *  2. 第几周只认外面传进来的 weekOf；返回 null（没有锚点、锚点太旧、算不出正数）
 *     就整段不排 —— 不知道是第几周却提醒「明天有课」等于替他瞎猜单双周。
 *  3. 已经过去的时刻不排（响铃时刻必须晚于 now）；所以刚打开 App 不会补弹一串旧提醒。
 */
object ClassReminderPlan {
    /** 提前多久的选项（分钟）。三项够用：再短来不及走，再长容易被忘掉。 */
    val LEAD_CHOICES: List<Int> = listOf(5, 10, 15)

    /** 默认提前 10 分钟。 */
    const val DEFAULT_LEAD_MINUTES = 10

    /** 往后排几天。七天刚好覆盖一整周（含今天），也避开「排太远、课表变了不准」。 */
    const val LOOKAHEAD_DAYS = 7

    fun plan(
        slots: List<TronScheduleSlot>,
        times: List<Pair<String, String>>,
        now: LocalDateTime,
        leadMinutes: Int,
        weekOf: (LocalDate) -> Int?,
        days: Int = LOOKAHEAD_DAYS,
    ): List<ClassReminder> {
        if (slots.isEmpty() || days <= 0) return emptyList()
        val zone = ZoneId.systemDefault()
        val lead = leadMinutes.coerceAtLeast(0).toLong()
        val out = ArrayList<ClassReminder>()
        for (offset in 0 until days) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val week = weekOf(date) ?: continue
            val dow = date.dayOfWeek.value
            slots.forEach { slot ->
                if (slot.dayOfWeek == dow && slot.activeIn(week)) {
                    val start = PeriodClock.startOf(times, slot.periodStart)
                    val at = start?.let { clock -> atOn(date, clock) }
                    if (at != null) {
                        val fire = at.minusMinutes(lead)
                        if (fire.isAfter(now)) {
                            out.add(
                                ClassReminder(
                                    atMillis = fire.atZone(zone).toInstant().toEpochMilli(),
                                    courseName = slot.courseName,
                                    room = slot.room,
                                    startLabel = start,
                                    periodLabel = slot.periodLabel,
                                ),
                            )
                        }
                    }
                }
            }
        }
        // 同一门课同一时刻只留一条（撞课时两门课不同名，都得留）。
        return out
            .distinctBy { r -> r.atMillis.toString() + "|" + r.courseName }
            .sortedBy { it.atMillis }
    }

    /** 把 08:00 解析成那天的 08:00。解析不出返回 null（不猜）。 */
    private fun atOn(date: LocalDate, clock: String): LocalDateTime? {
        val parts = clock.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return date.atTime(hour, minute)
    }
}
