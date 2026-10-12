package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 學在城院的一項**作業**（含要求內容）。
 *
 * ### 契約（活體探測，逐字對照源碼的 `getHomeworkList`）
 *
 * ```
 * GET /api/courses/{courseId}/homework-activities?page=1&page_size=20
 * 頭 X-SESSION-ID
 * → {
 *     homework_activities: [{
 *       id, deadline, end_time, is_closed, is_in_progress,
 *       created_at, completion_criterion,
 *       data: { homework_type, description, submit_times, mode,
 *               score_percentage, allow_retract, ... },
 *       assign_student_ids, group_set_id, group_set_name, ...
 *     }],
 *     page, page_size, pages, total      ← 這個站的 total 可信（與門戶不同）
 *   }
 * ```
 *
 * ### ★ 為什麼這一頁只「看」不「交」
 *
 * 作業的「完整操作」是**上傳檔案並提交** —— 那是寫操作，而且後果是成績。
 * 本專案一貫不做（§B4 充值、§B5 續借、§B9 待辦同一原則）。
 * 所以原生頁做到「看清楚要求」為止，提交仍走網頁版。
 *
 * 這仍然很有價值：待辦清單只有標題（實測 `title` 只有 6 個字），
 * 使用者必須知道**要求是什麼、什麼時候交、能交幾次**才能動手。
 */
data class TronHomework(
    val id: Int,
    /** 作業標題（在外層 `data` 之外，由 `title` 欄位提供；這裡從 `data.description` 補充）。 */
    val title: String,
    /** 截止時間（`deadline`，ISO 字串）。 */
    val deadline: String,
    /** 結束時間（`end_time`）。 */
    val endTime: String,
    /** 是否已截止（`is_closed`）。 */
    val closed: Boolean,
    /** 是否進行中（`is_in_progress`）。 */
    val inProgress: Boolean,
    /** 作業類型（`data.homework_type`）。 */
    val homeworkType: String,
    /** 要求／說明（`data.description`）。**這是使用者最需要的**。 */
    val description: String,
    /** 可提交次數（`data.submit_times`）。null = 不限或未知。 */
    val submitTimes: Int?,
    /** 評分佔比（`data.score_percentage`）。 */
    val scorePercentage: String,
) {
    /** 截止時間轉本地顯示（服務端給 ISO，可能帶時區）。 */
    val deadlineLocal: String
        get() = formatTronLocalTime(TronTodoParser.parseIsoMillis(deadline)) ?: deadline.ifBlank { "—" }
}

object TronHomeworkParser {

    fun parseList(root: JsonObject?): List<TronHomework> {
        val list = root?.get("homework_activities").asArrayOrNull() ?: return emptyList()
        return list.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val id = obj["id"].asIntOrNull() ?: return@mapNotNull null
            val data = obj["data"].asObjectOrNull()
            TronHomework(
                id = id,
                title = obj["title"].asStringOrNull().orEmpty(),
                deadline = obj["deadline"].asStringOrNull().orEmpty(),
                endTime = obj["end_time"].asStringOrNull().orEmpty(),
                closed = obj["is_closed"].asBoolOrNull() ?: false,
                inProgress = obj["is_in_progress"].asBoolOrNull() ?: false,
                homeworkType = data?.get("homework_type").asStringOrNull().orEmpty(),
                description = data?.get("description").asStringOrNull().orEmpty(),
                submitTimes = data?.get("submit_times").asIntOrNull(),
                scorePercentage = data?.get("score_percentage").asStringOrNull().orEmpty(),
            )
        }
    }
}
