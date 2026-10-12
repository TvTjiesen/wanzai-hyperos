package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 學在城院的**公告／課程動態**。
 *
 * ### 契約（活體探測 + 逐字對照源碼）
 *
 * 有兩個層級，**都來自公告接口**：
 * ```
 * 全校／機構公告：GET /api/org-bulletin/bulletins?conditions={}
 * 課程公告：      GET /api/courses/{courseId}/bulletins
 * ```
 * 回應外層是 `{ bulletins: [...], total, page, ... }`（實測）。
 *
 * ### ⚠️ 為什麼「動態」與「公告」是同一個東西
 *
 * 學在城院首頁的「課程動態」在資料層**不是一個獨立接口** ——
 * 它就是各課程的活動（作業、公告、討論）的彙總視圖。
 * 我們已經有三塊來源（待辦／作業／公告），所以**不另外造一個「動態」接口**，
 * 而是把已有的資料按時間排成「最近發生了什麼」。
 *
 * 這樣做的好處：不猜一個可能不存在的接口；壞處：時間軸不如原站完整 ——
 * 但「完整」在這裡是次要的（原站隨時可開），**準確**才是首要的。
 */
data class TronBulletin(
    val id: Int,
    /** 標題。 */
    val title: String,
    /** 正文／摘要（`content` 或 `description`）。 */
    val content: String,
    /** 發佈者名字。 */
    val authorName: String,
    /** 發佈時間（ISO 字串）。 */
    val createdAt: String,
    /** 是否已讀（`is_read`）。null = 不知道。 */
    val isRead: Boolean?,
    /** 所屬課程名（課程公告才有）。 */
    val courseName: String,
) {
    /** 發佈時間轉本地顯示。 */
    val createdAtLocal: String
        get() = formatTronLocalTime(TronTodoParser.parseIsoMillis(createdAt)) ?: createdAt.ifBlank { "—" }
}

object TronBulletinParser {

    /**
     * 解析公告列表。
     *
     * 外層可能是 `{bulletins:[...]}`、`{data:[...]}` 或直接 `[...]` ——
     * **三種都接**（這個站的回應外層不統一，寧可多接一種，
     * 也不要因為外層變了就空白）。
     */
    fun parseList(root: kotlinx.serialization.json.JsonElement?): List<TronBulletin> {
        val array = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is JsonObject ->
                root["bulletins"].asArrayOrNull()
                    ?: root["data"].asArrayOrNull()
                    ?: root["data"].asObjectOrNull()?.get("bulletins").asArrayOrNull()
            else -> null
        } ?: return emptyList()

        return array.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val id = obj["id"].asIntOrNull() ?: return@mapNotNull null
            TronBulletin(
                id = id,
                title = obj["title"].asStringOrNull().orEmpty(),
                content = (obj["content"].asStringOrNull()
                    ?: obj["description"].asStringOrNull()).orEmpty(),
                authorName = (obj["author_name"].asStringOrNull()
                    ?: obj["created_by"].asObjectOrNull()?.get("name").asStringOrNull()).orEmpty(),
                createdAt = (obj["created_at"].asStringOrNull()
                    ?: obj["published_at"].asStringOrNull()).orEmpty(),
                isRead = obj["is_read"].asBoolOrNull(),
                courseName = (obj["course_name"].asStringOrNull()
                    ?: obj["course"].asObjectOrNull()?.get("name").asStringOrNull()).orEmpty(),
            )
        }
    }
}
