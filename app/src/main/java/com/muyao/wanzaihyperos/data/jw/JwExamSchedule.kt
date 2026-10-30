package com.muyao.wanzaihyperos.data.jw

import java.time.LocalDate

/**
 * 最近一场考试 —— 考试页原本只有一条条列表，11 月 14 日那场得自己数日子。
 *
 * ## 只认服务端真给了时间的那些
 *
 * 考试的 [JwExam.time] 可能是 `"未放开不可查"`（学校还没开放查询，实测大部分是这种）。
 * 那种**不进**排序，也不许当成"没安排" —— 它是「不知道」。
 * 所以这里的 [next] 返回 null 有两种意思，界面必须分清：
 * 全是未开放（[unknownCount] > 0），和真的没有未来的考试。
 *
 * 时间的真实形状（真机 2026-10-31 原文）：`"2026-11-14(08:10-10:10)"`。
 */
data class NextExam(
    val courseName: String,
    val examName: String,
    val date: LocalDate,
    /** 当天几点开考，取不到就 null（不编）。 */
    val startClock: String?,
    val endClock: String?,
    val room: String,
    /** 距今还有几天；0 = 就是今天。 */
    val daysAway: Long,
)

object JwExamSchedule {

    private val DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
    private val CLOCK = Regex("\\((\\d{2}:\\d{2})-(\\d{2}:\\d{2})\\)")

    /** 从 `"2026-11-14(08:10-10:10)"` 里取日期；取不到（含"未放开不可查"）返回 null。 */
    fun dateOf(time: String): LocalDate? {
        val m = DATE.find(time) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        val mo = m.groupValues[2].toIntOrNull() ?: return null
        val d = m.groupValues[3].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
    }

    private fun clockOf(time: String): Pair<String, String>? {
        val m = CLOCK.find(time) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    /**
     * 从今天算起最近的一场。
     *
     * @param today 由调用方传入，纯函数才好测（也在跨零点时口径明确）。
     * @return null = 没有一场能算出日期（可能全是未开放，也可能列表为空）
     */
    fun next(exams: List<JwExam>, today: LocalDate): NextExam? =
        exams.mapNotNull { e ->
            val date = dateOf(e.time) ?: return@mapNotNull null
            if (date.isBefore(today)) return@mapNotNull null
            val clock = clockOf(e.time)
            NextExam(
                courseName = e.courseName,
                examName = e.examName,
                date = date,
                startClock = clock?.first,
                endClock = clock?.second,
                room = e.room.takeIf { !it.contains("未放开") }.orEmpty(),
                daysAway = java.time.temporal.ChronoUnit.DAYS.between(today, date),
            )
        }.minByOrNull { it.date }

    /** 有几场是学校还没开放时间的 —— 界面要如实说，别让人以为没考试。 */
    fun unknownCount(exams: List<JwExam>): Int = exams.count { dateOf(it.time) == null }

    /** 「还有 14 天」/「就是今天」/「明天」。 */
    fun awayText(days: Long): String = when (days) {
        0L -> "就是今天"
        1L -> "明天"
        else -> "还有 " + days + " 天"
    }
}
