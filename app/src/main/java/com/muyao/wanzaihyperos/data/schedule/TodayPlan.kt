package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 首页那句「今天还要上什么课」的算法（**纯函数**，可单测）。
 *
 * ## 为什么不直接列今日课程（业主 2026-10-20：「首页的今日课程提示你今天还要上什么课，
 * 而不是列出今日课程」）
 *
 * 列出全天 4 门课，他还是要自己拿现在的时间去比"哪门还没上"。
 * 那一比就是这一屏该做的事：**已经上完的不该占地方，下一节该最显眼**。
 *
 * ## 时间从哪来
 *
 * 只有 [PeriodClock] 那一层（他填的 → 学校日历 [SchoolPeriods]）。
 * **认不出时间的课不猜**：那种情况下 [clockKnown] = false，
 * 界面退回按节次说「还要上 N 节」，不编一个"还有 45 分钟"。
 */
data class TodayPlan(
    /** 主句：「今天还要上 2 节课」/「正在上 B/S架构应用开发」/「今天的课上完了」/「今天没有课」。 */
    val headline: String,
    /** 第二句：下一节或正在上的那一节的细节（时间、地点、还有多久）。null = 没得说。 */
    val detail: String?,
    /** 还没上的那些（**已下课的一律不在里面**）。 */
    val remaining: List<TronScheduleSlot>,
    /** 今天全部课程数（含已上完的）—— 用来在 detail 里说「共 N 节，已上完 M 节」。 */
    val totalToday: Int,
    /** true = 这一天的课**都**认得出时间；false = 至少有一节认不出（界面要改口）。 */
    val clockKnown: Boolean,
) {
    companion object {

        fun of(
            slots: List<TronScheduleSlot>,
            week: Int?,
            now: LocalDateTime,
            times: List<Pair<String, String>>,
        ): TodayPlan {
            if (week == null) {
                return TodayPlan(
                    "还不知道目前是第几教学周", null, emptyList(), 0, clockKnown = false,
                )
            }
            val today: List<TronScheduleSlot> = slots
                .filter { it.dayOfWeek == now.dayOfWeek.value && it.activeIn(week) }
                .sortedBy { it.periodStart }

            if (today.isEmpty()) {
                return TodayPlan("今天没有课", nextDayHint(slots, week, now), emptyList(), 0, clockKnown = true)
            }

            val startOf = today.map { clockOf(PeriodClock.startOf(times, it.periodStart), now.toLocalDate()) }
            val endOf = today.map { clockOf(PeriodClock.endOf(times, it.periodEnd), now.toLocalDate()) }
            val known = startOf.zip(endOf).all { (s, e) -> s != null && e != null }

            val nowT = now.toLocalTime()
            val ongoingIdx = today.indices.firstOrNull { i ->
                val s = startOf[i]; val e = endOf[i]
                s != null && e != null && !nowT.isBefore(s) && nowT.isBefore(e)
            }
            // 「还要上」= **还没下课**的那些（正在上的那节仍然在里面 —— 他得在教室里）。
            // ⚠️ 认不出下课时间的课**一律算"还要上"**：反过来（判成上完了）
            // 就是让他以为某节课不用去，那是最贵的一种错。
            val remainingIdx = today.indices.filter { i ->
                val e = endOf[i]
                e == null || nowT.isBefore(e)
            }
            val remaining = remainingIdx.map { today[it] }

            val headline = when {
                ongoingIdx != null -> "正在上 ${today[ongoingIdx].courseName.ifBlank { "（未命名课程）" }}"
                remaining.isNotEmpty() -> "今天还要上 ${remaining.size} 节课"
                else -> "今天的课上完了"
            }

            val detail = when {
                ongoingIdx != null -> {
                    val s = today[ongoingIdx]
                    val left = java.time.Duration.between(nowT, endOf[ongoingIdx]).toMinutes().toInt()
                    "还剩 ${human(left)}（${startOf[ongoingIdx]}-${endOf[ongoingIdx]}）" +
                        (if (s.room.isNotBlank()) " · ${s.room}" else "")
                }

                remaining.isNotEmpty() -> {
                    val i = remainingIdx.first()
                    val s = today[i]
                    val start = startOf[i]
                    if (start == null) {
                        // 只知道"还有课"、不知道几点 ⇒ 说清楚是**学校没给时间**，
                        // 而不是我们算不出（他会去設定里填，这一屏下次就准了）。
                        "${s.periodLabel} 的上课时间学校没给，去设置里填一次"
                    } else {
                        val wait = java.time.Duration.between(nowT, start).toMinutes().toInt()
                        "下一节 $start ${s.courseName.ifBlank { "（未命名课程）" }}" +
                            (if (s.room.isNotBlank()) " · ${s.room}" else "") +
                            "（还有 ${human(wait)}）"
                    }
                }

                else -> "共 ${today.size} 节，都上完了 · 第 $week 周"
            }

            return TodayPlan(headline, detail, remaining, today.size, known)
        }

        /** 今天没课时，顺带说下一天有什么 —— 这才是他要知道的（「那我这周还剩什么」）。 */
        private fun nextDayHint(slots: List<TronScheduleSlot>, week: Int, now: LocalDateTime): String? {
            for (delta in 1..3) {
                val day = now.toLocalDate().plusDays(delta.toLong())
                val list = slots.filter { it.dayOfWeek == day.dayOfWeek.value && it.activeIn(week) }
                    .sortedBy { it.periodStart }
                if (list.isNotEmpty()) {
                    val first = list.first()
                    val label = if (delta == 1) "明天"
                    else "周" + "一二三四五六日".getOrElse(day.dayOfWeek.value - 1) { ' ' }
                    return "下一节：$label ${first.periodLabel} ${first.courseName.ifBlank { "（未命名）" }}"
                }
            }
            return null
        }

        private fun clockOf(v: String?, day: LocalDate): LocalTime? {
            val t = v?.trim().orEmpty()
            if (t.length < 4) return null
            return runCatching { LocalTime.parse(t) }.getOrNull()
        }

        private fun human(mins: Int): String {
            val m = if (mins < 0) 0 else mins
            return if (m < 60) "$m 分钟" else "${m / 60} 小时${if (m % 60 > 0) " ${m % 60} 分" else ""}"
        }
    }
}
