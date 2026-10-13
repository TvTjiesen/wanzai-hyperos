package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * **我的提交狀態**（每項作業交了沒）。
 *
 * ## 契約（活體探測 2026-10-13，只讀）
 *
 * ```
 * GET /api/course/{courseId}/homework/submission-status
 * 頭 X-SESSION-ID
 * → { course_id, homework_activities: [{
 *       id, homework_type, status, status_code, score, is_announce_score_time_passed
 *   }] }
 * ```
 *
 * 實測兩種（`docs/B15-SUBMIT-PROBE.txt`）：
 * ```
 * status "已交" / status_code "submitted"   ×3
 * status "未交" / status_code "absent"      ×24
 * ```
 *
 * ### ★ 為什麼要有這一支
 *
 * 作業清單本身（`/api/courses/{id}/homework-activities`）**不含**「我交了沒」，
 * 只有截止時間與要求。所以先前那一頁只能說「截止 10-14 23:30」，
 * 卻答不出學生最想問的那句：**「這份我交了沒？」**
 *
 * 這一支是**只讀**的（GET），補上那個缺口，且沒有任何寫入風險。
 *
 * ### 用 `status_code` 判斷，不用 `status`
 *
 * `status` 是**中文顯示字串**（「已交」「未交」）—— 拿它做邏輯判斷，
 * 學校哪天把文案改成「已完成」就整頁失準。`status_code` 是穩定的英文枚舉
 *（`submitted` / `absent`），所以判斷一律用 [statusCode]，
 * `status` 只在**認不出 `status_code` 時**當退路顯示。
 */
data class TronHomeworkStatus(
    val homeworkId: Int,
    /** 服務端給的顯示字串（如「已交」）。認不出 [statusCode] 時才用它。 */
    val status: String,
    /** 穩定枚舉：`submitted` / `absent`（實測）；其餘原樣保留。 */
    val statusCode: String,
    /** 我的分數（`score`）。未評分時為 null —— **那不是 0 分**。 */
    val score: String?,
) {
    /** 我交了沒。認不出的 `status_code` 回 null（＝不知道，不是「沒交」）。 */
    val submitted: Boolean?
        get() = when (statusCode) {
            "submitted" -> true
            "absent" -> false
            else -> null
        }

    /**
     * 界面顯示字。
     *
     * 認不出 `status_code` 就**原樣顯示服務端的說法** —— 不編一個我們自己猜的詞
     * （那會在學校新增狀態時說謊）。
     */
    val label: String
        get() = when (statusCode) {
            "submitted" -> "已交"
            "absent" -> "未交"
            else -> status.ifBlank { "—" }
        }
}

object TronHomeworkStatusParser {

    /** 解析成「作業 id → 狀態」的映射（界面用它與作業清單合併）。 */
    fun parse(root: JsonObject?): Map<Int, TronHomeworkStatus> {
        val list = root?.get("homework_activities").asArrayOrNull() ?: return emptyMap()
        return list.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val id = obj["id"].asIntOrNull() ?: return@mapNotNull null
            // 分數：服務端可能給數字或 null。**null 不等於 0 分**，所以留 null。
            val rawScore = obj["score"]
            val score = rawScore.asStringOrNull()
                ?: rawScore?.takeIf { it.toString() != "null" }?.toString()
            TronHomeworkStatus(
                homeworkId = id,
                status = obj["status"].asStringOrNull().orEmpty(),
                statusCode = obj["status_code"].asStringOrNull().orEmpty(),
                score = score?.takeIf { it.isNotBlank() },
            ) to id
        }.associate { (status, id) -> id to status }
    }
}
