package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asDoubleOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 我在某門課的**成績**（只讀）。
 *
 * ### 契約（活體探測 2026-10-12）
 *
 * ```
 * GET /api/course/{id}/student-self-score
 *  → { self_score: { exceptional_case, instructor_score_time, raw_score, total_score } }
 * GET /api/course/{id}/score-percentages
 *  → { score_percentage_left, score_percentage_total, unpublished_percentage }
 * ```
 *
 * 實測值（一門還沒出成績的課）：`raw_score 0.0`、`total_score 0.0`、
 * `instructor_score_time null`、`score_percentage_total 100`、`left 100`、`unpublished 0.0`。
 *
 * ### ★ 為什麼要 [published] 這個判斷
 *
 * `total_score` 在「還沒出成績」時也是 **0.0** —— 直接顯示「總分 0.0」
 * 等於告訴學生他考了 0 分，而事實是**老師還沒發布**。
 *
 * 判據用 `instructor_score_time`（老師給分的時間戳）：
 * 它是 null 就表示還沒發布，界面顯示「尚未公布」而不是 0。
 * 這與全專案「未知不得當成 0」是同一條紀律。
 */
data class TronScore(
    /** 總分（`total_score`，字串形態以保留服務端的小數位）。 */
    val totalScore: String,
    /** 原始分（`raw_score`）。 */
    val rawScore: String,
    /** 老師是否已發布成績（`instructor_score_time != null`）。 */
    val published: Boolean,
    /** 課程成績佔比的總量（`score_percentage_total`，通常是 100）。 */
    val percentageTotal: Int?,
    /** 尚未計入的佔比（`score_percentage_left`）。 */
    val percentageLeft: Int?,
    /** 未發布的佔比（`unpublished_percentage`）。 */
    val unpublishedPercentage: String,
) {
    /**
     * 界面用的一句話。
     *
     * 未發布 → 「尚未公布」；已發布 → 「總分 X（滿分 100）」這種就交給界面拼，
     * 這裡只回最能表達事實的那一種講法。
     */
    val summary: String
        get() = when {
            !published -> "尚未公布"
            totalScore.isBlank() -> "—"
            else -> totalScore
        }

    /** 已計分的佔比 = 總量 − 尚未計入。有任一項取不到就回 null（顯示「—」）。 */
    val gradedPercentage: Int?
        get() {
            val total = percentageTotal ?: return null
            val left = percentageLeft ?: return null
            return (total - left).coerceAtLeast(0)
        }
}

object TronScoreParser {

    /**
     * @param selfRoot `/api/course/{id}/student-self-score` 的回應
     * @param percentRoot `/api/course/{id}/score-percentages` 的回應（可為 null）
     */
    fun parse(selfRoot: JsonObject?, percentRoot: JsonObject?): TronScore? {
        val self = selfRoot?.get("self_score").asObjectOrNull()
            // 有些部署直接回 `{raw_score: ...}`（沒有 self_score 外層）。
            ?: selfRoot?.takeIf { it.containsKey("total_score") }
            ?: return null
        return TronScore(
            totalScore = self["total_score"].asStringOrNull().orEmpty(),
            rawScore = self["raw_score"].asStringOrNull().orEmpty(),
            published = !self["instructor_score_time"].asStringOrNull().isNullOrBlank(),
            percentageTotal = percentRoot?.get("score_percentage_total").asIntOrNull(),
            percentageLeft = percentRoot?.get("score_percentage_left").asIntOrNull(),
            // ⚠️ 這個欄位是**數字**（實測 `0.0`），不是字串 ——
            //   用 asStringOrNull 讀會永遠得到空字串。模型收成 String
            //   是為了保住服務端的小數形態。
            unpublishedPercentage = percentRoot?.get("unpublished_percentage")
                .asDoubleOrNull()?.toString().orEmpty(),
        )
    }
}

/**
 * 某門課的**出勤彙總**（`/api/course/{id}/rollcall-score`）。
 *
 * ```
 * → { last_rollcall_time, rollcall_count, rollcall_times, score,
 *     score_percentage, scored, public }
 * ```
 *
 * 實測：`rollcall_count 5`、`rollcall_times 5`（點名 5 次、我都在）、
 * `score "0"`、`scored true`。
 *
 * ### ★ 為什麼**不顯示** `score`
 *
 * 實測那門課我 5 次全部 `on_call_fine`（全到），但 `score` 仍是 `"0"` ——
 * 說明這個分數不是「出勤得分」，可能是尚未計算的原始值或另一套計分。
 * 把 0 顯示成「出勤得分 0」是**對學生說謊**。所以只顯示
 * 「點名 N 次」（`rollcall_times`）與最後一次時間，分數留著不用。
 */
data class TronRollcallScore(
    /** 老師發起過的點名次數（`rollcall_times`）。 */
    val times: Int?,
    /** 我參與過的次數（`rollcall_count`）。 */
    val count: Int?,
    /** 最後一次點名時間（UTC 原字串）。 */
    val lastRollcallTime: String,
    /** 最後一次點名時間（本地顯示）。 */
    val lastRollcallLocal: String,
) {
    /** 「已點名 5 次 / 共 5 次」這種講法；取不到的部分顯示「—」。 */
    val summary: String
        get() {
            val t = times?.toString() ?: "—"
            val c = count?.toString() ?: "—"
            return "$c / $t 次"
        }
}

object TronRollcallScoreParser {
    fun parse(root: JsonObject?): TronRollcallScore? {
        root ?: return null
        // 只有當它真的長得像出勤彙總時才認（避免把錯誤包體當資料）。
        if (!root.containsKey("rollcall_times") && !root.containsKey("rollcall_count")) return null
        val raw = root["last_rollcall_time"].asStringOrNull().orEmpty()
        return TronRollcallScore(
            times = root["rollcall_times"].asIntOrNull(),
            count = root["rollcall_count"].asIntOrNull(),
            lastRollcallTime = raw,
            lastRollcallLocal = TronTodoParser.parseIsoMillis(raw)
                ?.let { formatTronLocalTime(it) }
                .orEmpty(),
        )
    }
}
