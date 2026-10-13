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

    /**
     * **可讀的正文**：把 `content` 的 HTML 標籤與實體處理掉。
     *
     * ### ★ 為什麼一定要走這裡，不能直接把 [content] 丟給 `Text`
     *
     * 課程公告的 `content` 是 **HTML 富文本**（實測）：
     * ```
     * <p><span style="font-size: 13.5pt;">各位同学好，本课程将采用线下为主，…</span></p>
     * ```
     * 直接顯示會看到一堆 `<p><span style=...>` 標籤。
     *
     * 這裡**不引入 HTML 渲染器**（不開 WebView）：公告是**閱讀**用的，
     * 粗體斜體丟掉不影響理解；而引入一套渲染會帶回它自己的問題
     * （捲動、返回鍵、深色模式、以及一個藏在列表裡的瀏覽器）。
     */
    val plainText: String get() = htmlToPlainText(content)
}

/**
 * HTML → 純文字（**保守**轉換，只用於顯示）。
 *
 * 只做四件事，其餘原樣保留：
 *
 * 1. **區塊級標籤換行** —— `<p>` `<br>` `<div>` `<li>` 變成 `\n`，
 *    否則整篇公告會擠成一行。
 * 2. **其餘標籤直接刪掉** —— `<span style=...>` 這種對閱讀沒有貢獻。
 * 3. **還原常見實體** —— `&nbsp; &amp; &lt; &gt; &quot; &#39;`。
 *    刻意**只做這幾個**：`&#x4e2d;` 這種數字實體在公告樣本裡沒出現過，
 *    為它寫一套解碼只是多一個會出錯的地方。
 * 4. **收掉多餘空行** —— 標籤被刪掉後常留下 `\n\n\n`。
 *
 * ⚠️ **不做**的事：不解析 `<script>` 內容（樣本裡沒有）、
 * 不處理表格（變成一行，可接受）、不還原 `<img>`（公告圖片會靜默消失，
 * 但那個代價比開一個 WebView 小）。
 */
internal fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""
    // 沒有標籤又不是實體的東西 → 直接回原文（省掉下面所有處理）。
    if (!html.contains('<') && !html.contains('&')) return html.trim()

    var text = html
    // ① 區塊級標籤 → 換行（先做，避免與行內標籤的處理順序打架）。
    text = Regex("""<\s*(br|/p|/div|/li|/tr|/h[1-6])\s*/?\s*>""", RegexOption.IGNORE_CASE)
        .replace(text, "\n")
    // ② 其餘標籤刪掉（連同屬性）。
    text = Regex("""<[^>]*>""").replace(text, "")
    // ③ 實體還原。`&nbsp;` 換成**普通空格**：在等寬中文字體裡它不換行，
    //    留著會讓一行變得超寬。
    text = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        // `&amp;` **必須最後做**：先做的話 `&amp;lt;` 會變成 `<`，
        // 那是把「使用者真的想顯示 &lt; 這個字」誤解成標籤。
        .replace("&amp;", "&")
    // ④ 收空行與行首尾空白。
    return text
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
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
