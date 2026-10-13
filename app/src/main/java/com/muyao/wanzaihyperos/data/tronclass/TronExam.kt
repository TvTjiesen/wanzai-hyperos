package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 課程**考試**（`/api/courses/{id}/exams`）。
 *
 * ## ★ B12 的結論是錯的，這一輪更正
 *
 * B12 只抽查了 8 門課就寫下「考試全空」。B14 把**全部 37 門**掃過一遍：
 * **6 門課有考試**（61847 / 55935 / 56133 / 45300 / 46122 / 44304）。
 * 教訓很具體：**空結果要掃全部才算數**，8 門的樣本不足以宣稱「全空」。
 *
 * ### 契約（活體探測 2026-10-13，`docs/B14-TRONCLASS-SCAN.txt`）
 *
 * ```
 * GET /api/courses/{id}/exams
 * → { exams: [{
 *       id, title, description,
 *       start_time, end_time,            // ISO-8601 UTC
 *       is_started, is_closed,           // 已開始／已結束
 *       publish_time,                    // 老師發布時間（可能 null）
 *       score_percentage,                // 佔比（字串，如 "0.00"）
 *       exam_submissions: [ 63300 ],     // 我的作答 id（**空陣列＝還沒考**）
 *       announce_score_status, ...
 *   }] }
 * ```
 *
 * ### ★ 用 `exam_submissions` 判斷「考了沒」，不猜
 *
 * 實測有兩門課的考試同時滿足 `is_started=true` 且 `is_closed=true`（已結束），
 * 而 `exam_submissions` 是 `[63300]`（有作答）或 `[]`（沒作答）。
 * 界面要講的是「你考了沒」，那個答案**只在這個欄位裡** ——
 * 不能從 `is_closed` 推（結束了不等於我考了）。
 */
data class TronExam(
    val id: Int,
    val title: String,
    /** 說明（`description`）。多數為空字串。 */
    val description: String,
    /** 開始時間（本地顯示）。解析不出來為空字串。 */
    val startLocal: String,
    /** 結束時間（本地顯示）。 */
    val endLocal: String,
    /** 已開始。 */
    val started: Boolean,
    /** 已結束。 */
    val closed: Boolean,
    /** 我的作答 id 清單。**非空＝我考過**。 */
    val submissionIds: List<Int>,
    /** 佔比（`score_percentage`，原樣字串）。 */
    val scorePercentage: String,
) {
    /** 我考過了沒（`exam_submissions` 非空）。 */
    val submitted: Boolean get() = submissionIds.isNotEmpty()

    /**
     * 狀態講法。
     *
     * 順序有意義：**先看「我考了沒」，再看時間**——
     * 已經考完的試顯示「未開始」是荒謬的。
     */
    val statusLabel: String
        get() = when {
            submitted -> "已作答"
            !started -> "未开始"
            closed -> "已结束（未作答）"
            else -> "进行中"
        }

    /** 進行中（還沒結束、且我還沒交）。 */
    val actionable: Boolean get() = started && !closed && !submitted
}

object TronExamParser {

    fun parseList(root: kotlinx.serialization.json.JsonElement?): List<TronExam> {
        val array = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is JsonObject -> root["exams"].asArrayOrNull() ?: root["data"].asArrayOrNull()
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { it.asObjectOrNull() }.mapNotNull { parseOne(it) }
    }

    fun parseOne(obj: JsonObject): TronExam? {
        val id = obj["id"].asIntOrNull() ?: return null
        val start = obj["start_time"].asStringOrNull().orEmpty()
        val end = obj["end_time"].asStringOrNull().orEmpty()
        return TronExam(
            id = id,
            title = obj["title"].asStringOrNull().orEmpty().ifBlank { "（无标题考试）" },
            description = obj["description"].asStringOrNull().orEmpty(),
            startLocal = formatTronLocalTime(TronTodoParser.parseIsoMillis(start)).orEmpty(),
            endLocal = formatTronLocalTime(TronTodoParser.parseIsoMillis(end)).orEmpty(),
            started = obj["is_started"].asBoolOrNull() ?: false,
            closed = obj["is_closed"].asBoolOrNull() ?: false,
            submissionIds = obj["exam_submissions"].asArrayOrNull()
                ?.mapNotNull { it.toString().trim('"').toIntOrNull() }
                .orEmpty(),
            scorePercentage = obj["score_percentage"].asStringOrNull().orEmpty(),
        )
    }
}

/**
 * 課程**公告**（`/api/courses/{id}/bulletins`）。
 *
 * ## 契約（活體探測 2026-10-13）
 *
 * ```
 * GET /api/courses/{id}/bulletins?page=1&page_size=20
 * → { bulletins: [{
 *       id, title, content,            // content 是 **HTML 富文本**
 *       created_at, updated_at,
 *       publisher: { name },           // 可能不存在
 *       attachments: [...],            // 附件（本次樣本為空）
 *       is_read, top, ...
 *   }] }
 * ```
 *
 * ### ★ `content` 是 HTML —— 絕對不能直接丟給 `Text`
 *
 * 實測內容長這樣：
 * ```
 * <p><span style="font-size: 13.5pt;">各位同学好，本课程将采用线下为主，…</span></p>
 * ```
 * 直接顯示會看到一堆 `<p><span style=...>` 標籤。
 * 這一層只做**去掉標籤、還原常見實體**（見 [TronBulletin.plainText]），
 * 不引入 HTML 渲染器 —— 公告是**閱讀**用的，粗體斜體丟掉不影響理解，
 * 而引入 WebView 會帶回一整套它自己的問題（捲動、返回、深色模式）。
 *
 * ### 為什麼課程公告用這個類別、全校公告用 [TronBulletin]
 *
 * 兩者的**單條欄位形狀幾乎相同**（`id/title/content/created_at`），
 * 但外層不同（`bulletins` vs 也是 `bulletins`）且來源端點不同。
 * 這裡刻意**不加第二個模型** —— [TronBulletinParser] 已經兩種外層都接，
 * 所以課程公告直接複用它，只在使用處補上「哪門課的公告」。
 */
object TronCourseBulletinParser {
    /** 複用全校公告的解析器（回應形狀相同，見上面註釋）。 */
    fun parseList(root: kotlinx.serialization.json.JsonElement?): List<TronBulletin> =
        TronBulletinParser.parseList(root)
}
