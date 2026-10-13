package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 課程**討論區**（`/api/courses/{id}/topic-categories`）。只讀。
 *
 * ## ★ B12 的結論是錯的，這一輪更正
 *
 * B12 抽查 8 門課就寫「討論區全空」。B14 掃全部 37 門：**4 門有討論區**
 *（60830 / 55935 / 51420 / 47265）。空結果**要掃全部才算數**。
 *
 * ### 契約（活體探測 2026-10-13）
 *
 * ```
 * GET /api/courses/{id}/topic-categories
 * → { topic_categories: [{
 *       id, title,                    // 討論主題名（如「你对凌老师的课程资料满意吗」）
 *       activity_id, created_at,
 *       activity: {                   // 這個討論所屬的「活動」（計分規則在這裡）
 *         id, title, type: "forum",
 *         start_time, end_time, is_started, is_closed, published,
 *         data: { description, score_percentage, enable, ... }
 *       },
 *       topics: [ { id, reply_count } ]   // 貼文（**這個端點只給 id 與回覆數**）
 *   }] }
 * ```
 *
 * ### ★ 這個端點**不給貼文內容**
 *
 * `topics[]` 裡只有 `{id, reply_count}` —— 沒有標題、沒有正文、沒有作者。
 * 要讀內容得再打 `/api/topics/{id}`（逐條）。
 *
 * 所以這一層只呈現**「這門課有哪些討論、各有多少回覆」**，
 * 而**不假裝能看貼文**：點進去的動作是「改用網頁版」。
 * 這是刻意的邊界 —— 37 門課裡只有 4 門有討論區，
 * 為它再寫一整套「分頁 + 逐條抓貼文」的鏈路，收益不成立。
 *
 * ### ⚠️ `51420` 那門課的 `activity` 是 `null`
 *
 * 實測有一條 `{"activity":null,"activity_id":0,...}` —— 解析器要能吃這個，
 * 否則整門課的討論區會因為**一條**壞資料而全部消失。
 */
data class TronTopicCategory(
    val id: Int,
    /** 討論主題名（`title`）。活動那邊也有 title，兩者取先有的。 */
    val title: String,
    /** 貼文數（`topics.size`）。 */
    val topicCount: Int,
    /** 回覆總數（把每條 `topics[].reply_count` 加起來）。 */
    val replyCount: Int,
    /** 說明（`activity.data.description`），可能為空。 */
    val description: String,
    /** 結束時間（本地顯示），可能為空。 */
    val endLocal: String,
    /** 是否已結束（`activity.is_closed`）。`activity` 為 null 時為 false。 */
    val closed: Boolean,
) {
    /**
     * 狀態講法。
     *
     * ⚠️ 不顯示「進行中／已結束」以外的推測 ——
     * 這個端點沒有「我發過帖沒有」的資訊（那在逐條 topic 裡），
     * 所以**不顯示「已參與」這種話**。
     */
    val statusLabel: String get() = if (closed) "已结束" else "进行中"

    /**
     * **可讀的說明**：把 [description] 的 HTML 處理掉。
     *
     * 與 [TronExam.plainDescription] 同一個處置：這一條也**沒有活體樣本**
     *（實測四門課的 `activity.data.description` 全是空字串），
     * 但 [htmlToPlainText] 對純文字是等價變換，所以先接上成本是零。
     */
    val plainDescription: String get() = htmlToPlainText(description)
}

object TronTopicCategoryParser {

    fun parseList(root: kotlinx.serialization.json.JsonElement?): List<TronTopicCategory> {
        val array = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is JsonObject -> root["topic_categories"].asArrayOrNull()
                ?: root["data"].asArrayOrNull()
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { it.asObjectOrNull() }.mapNotNull { parseOne(it) }
    }

    fun parseOne(obj: JsonObject): TronTopicCategory? {
        val id = obj["id"].asIntOrNull() ?: return null
        // `activity` 可能是 null（實測 51420 那門課），此時只有頂層欄位可用。
        val activity = obj["activity"].asObjectOrNull()
        val topics = obj["topics"].asArrayOrNull().orEmpty()
        return TronTopicCategory(
            id = id,
            title = obj["title"].asStringOrNull()?.takeIf { it.isNotBlank() }
                ?: activity?.get("title").asStringOrNull().orEmpty(),
            topicCount = topics.size,
            replyCount = topics.mapNotNull { it.asObjectOrNull() }
                .sumOf { it["reply_count"].asIntOrNull() ?: 0 },
            description = activity?.get("data").asObjectOrNull()
                ?.get("description").asStringOrNull().orEmpty(),
            endLocal = formatTronLocalTime(
                TronTodoParser.parseIsoMillis(activity?.get("end_time").asStringOrNull().orEmpty()),
            ).orEmpty(),
            closed = activity?.get("is_closed").asBoolOrNull() ?: false,
        )
    }
}
