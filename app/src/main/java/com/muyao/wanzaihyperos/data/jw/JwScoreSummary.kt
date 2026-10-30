package com.muyao.wanzaihyperos.data.jw

/**
 * 成績的匯總：已修學分、加權平均分、加權績點。
 *
 * ## 為什麼要另外拎一個純函式出來
 *
 * 這三個數字**頁面上一次都沒出現過**（`JwPage` 只逐條列成績），
 * 但學生真正想知道的就是「我現在均分多少、績點多少」。
 * 拎成純函式才測得動：餵一批 [JwScore] 進去，出來的數字可以直接對。
 *
 * ## 算不出來就說算不出來
 *
 * 成績可能是等第（`优秀` / `良好` / `及格`）而不是數字，
 * 學分也可能是空的。這種課**不能當 0 分參與加權** —— 那會把均分拉低，
 * 而學生看不出是自己考砸了還是程序瞎算。所以：
 *
 * - 只有「學分是數字」的課才進學分合計；
 * - 只有「分數是數字且學分是數字」的課才進加權平均分；
 * - 被排除的條數原樣報出去（[excludedFromAverage]），界面照實說明。
 *
 * 一條都算不出來時字段是 null，不是 0。
 */
data class JwScoreSummary(
    /** 已修學分合計（只算學分是數字的課）。一條都算不出來時 null。 */
    val credits: Double?,
    /** 學分是數字的課有幾門。 */
    val creditCount: Int,
    /** 加權平均分（以學分為權重）。算不出來時 null。 */
    val weightedScore: Double?,
    /** 加權平均績點（以學分為權重）。算不出來時 null。 */
    val weightedGradePoint: Double?,
    /** 有分數但不是數字（等第制）、因而沒進加權平均分的課有幾門。 */
    val excludedFromAverage: Int,
) {
    /** 一條都沒有 —— 界面據此不畫匯總卡，而不是畫一排「—」。 */
    val isEmpty: Boolean get() = creditCount == 0 && weightedScore == null && weightedGradePoint == null
}

object JwScoreSummarizer {

    /** 把 `3` / `3.0` / `3.5` 這種讀成 Double；`优秀` / 空串 / `—` 一律 null。 */
    internal fun number(raw: String): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val v = t.toDoubleOrNull() ?: return null
        // 學分與績點的合理範圍；超出範圍的多半是解析到了別的文字。
        if (v < 0.0 || v > 100.0) return null
        return v
    }

    fun summarize(items: List<JwScore>): JwScoreSummary {
        var creditSum = 0.0
        var creditCount = 0
        var scoreTimesCredit = 0.0
        var scoreCreditSum = 0.0
        var gpTimesCredit = 0.0
        var gpCreditSum = 0.0
        var excluded = 0

        for (item in items) {
            val credit = number(item.credit)
            if (credit == null) {
                // 學分讀不出來：這一門不進任何加權（分母也不該含它）。
                excluded++
                continue
            }
            creditSum += credit
            creditCount++

            // 分數優先看百分制 `bfzcj`，沒有才看 `cj`（等第制時 cj 是文字）。
            val score = number(item.rawScore) ?: number(item.score)
            if (score == null) {
                excluded++
            } else {
                scoreTimesCredit += score * credit
                scoreCreditSum += credit
            }

            val gp = number(item.gradePoint)
            if (gp != null) {
                gpTimesCredit += gp * credit
                gpCreditSum += credit
            }
        }

        return JwScoreSummary(
            credits = if (creditCount > 0) creditSum else null,
            creditCount = creditCount,
            weightedScore = if (scoreCreditSum > 0.0) scoreTimesCredit / scoreCreditSum else null,
            weightedGradePoint = if (gpCreditSum > 0.0) gpTimesCredit / gpCreditSum else null,
            excludedFromAverage = excluded,
        )
    }
}