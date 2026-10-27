package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import com.muyao.wanzaihyperos.net.jsonQuote
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 學在城院的**一份作业提交**（我自己交上去的那一份）。
 *
 * ### 契約（逐字挖自 H5 包，不是推的）
 *
 * ```
 * GET  /api/activities/{作业 id}/students/{我的 user id}/submission_list
 *      → { list: [ {id, comment, created_at, updated_at, is_draft,
 *                  is_latest_version, score, instructor_score, final_score,
 *                  instructor_comment, marked_submitted, uploads[], …} ] }
 *      （源碼 `toClasses(n, Yo, "list")` —— 那個 `"list"` 就是外層的鍵名）
 *
 * POST /api/course/activities/{作业 id}/submissions      → 201 才算成功
 *      body = {"comment":正文,"is_draft":是否草稿,"slides":[],"uploads":[]}
 *      （源碼 `createHomeworkSubmission(e)` + `toPlain(t, hC)`，
 *        `hC` 那個模型只有四個欄位：comment / is_draft / slides / uploads）
 * ```
 *
 * ### ★ 為什麼 `uploads` 傳空陣列而不是支援傳檔
 *
 * 附件那條鏈是三跳：`POST /api/uploads`（拿 `upload_url` 與 `key`）
 * → 把位元組**直傳七牛**（`GET /api/uptoken?key=` 拿 token，再 POST 到存儲域名）
 * → `PUT /api/uploads/{id}/uploaded` 通知完成。
 * 中間那一跳出站到我们**無法核驗**的存儲域，而且第一跳的 `parent_type`
 * 在清單頁的包裡沒有出現（只出现在按模块懒加载的分包里，我沒拿到）。
 *
 * 這是一個**會記成績的寫操作**。在这种场合，「不确定就不要发」比
 * 「先发了试试」重要 —— 试错的成本是他的作业，不是一次失败的重跑。
 * 所以原生做**正文提交 + 草稿**（契約完整、可核驗），
 * 檔案提交留在网页版出口，並在界面上把這句話講明（不藏）。
 */
data class TronSubmission(
    val id: Int,
    /** 正文（`comment`，服務端存的是富文本 HTML）。 */
    val comment: String,
    /** 建立時間（`created_at`，ISO）。 */
    val createdAt: String,
    /** 是否草稿（`is_draft`）。草稿**不算交过**。 */
    val draft: Boolean,
    /** 是否最新版本（`is_latest_version`）。多次提交時只有一份是最新的。 */
    val latest: Boolean,
    /** 老師評分（`instructor_score`；未評分為 null —— **那不是 0 分**）。 */
    val instructorScore: String?,
    /** 老師評語（`instructor_comment`）。 */
    val instructorComment: String,
) {
    /** 正文的可讀形態（去 HTML；與作業要求走同一個 [htmlToPlainText]）。 */
    val plainComment: String get() = htmlToPlainText(comment)

    /** 建立時間的本地顯示；解析不出來就原樣顯示服務端給的字串。 */
    val createdAtLocal: String
        get() = formatTronLocalTime(TronTodoParser.parseIsoMillis(createdAt)) ?: createdAt

    /** 評分文字；沒評分時說「未评分」而不是空白（空白會被讀成壞了）。 */
    val scoreLabel: String get() = instructorScore?.takeIf { it.isNotBlank() } ?: "未评分"
}

object TronSubmissionParser {

    /**
     * 解析清單。外層三種都接：`{list:[…]}`、`{data:[…]}`、直接 `[…]`。
     *
     * 這個站的外層鍵不統一（公告是 `bulletins`、作業是 `homework_activities`、
     * 提交是 `list`），而且同類資料在不同版本裡會換鍵名。
     * 多接一種的代價是幾行，接錯的代價是**整頁空白** —— 而整頁空白會被
     * 使用者讀成「你还没交过」，那是會讓他重複提交的謊。
     */
    fun parseList(root: JsonElement?): List<TronSubmission> {
        val array = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is JsonObject ->
                root["list"].asArrayOrNull()
                    ?: root["submissions"].asArrayOrNull()
                    ?: root["data"].asArrayOrNull()
                    ?: root["data"].asObjectOrNull()?.get("list").asArrayOrNull()
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { it.asObjectOrNull() }.mapNotNull { parse(it) }
    }

    fun parse(obj: JsonObject): TronSubmission? {
        val id = obj["id"].asIntOrNull() ?: return null
        // 分數：可能是數字也可能是 null。**null 不等於 0 分**。
        val raw = obj["instructor_score"]
        val score = raw.asStringOrNull()
            ?: raw?.takeIf { it.toString() != "null" && it !is kotlinx.serialization.json.JsonNull }
                ?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        return TronSubmission(
            id = id,
            comment = obj["comment"].asStringOrNull().orEmpty(),
            createdAt = obj["created_at"].asStringOrNull().orEmpty(),
            draft = obj["is_draft"].asBoolOrNull() ?: false,
            latest = obj["is_latest_version"].asBoolOrNull() ?: false,
            instructorScore = score,
            instructorComment = obj["instructor_comment"].asStringOrNull().orEmpty(),
        )
    }
}

/**
 * 一次**作业提交**的結果。
 *
 * 與簽到同一個紀律：成敗都要把**服務端的原話**帶回去，
 * 而且**不重試**（重試一次提交 = 可能多一筆記錄，而多那筆會占用可交次數）。
 */
data class HomeworkSubmitResult(
    val ok: Boolean,
    val httpStatus: Int,
    val message: String,
)

/**
 * 提交包體（**純函式，可單測**）。
 *
 * 四個鍵就是 H5 那個模型的全部（`comment / is_draft / slides / uploads`）。
 * 多帶一個鍵都可能讓服務端按它的規則改寫記錄，所以**只做這四個**。
 *
 * @param comment 正文原文（這層不做 HTML 轉換 —— 網頁版也是把富文本原樣送上去，
 *   我們送純文本，學校那邊照樣顯示；自造 `<p>` 包裝反而會和它的過濾器打架）。
 * @param draft `is_draft`：true = 存草稿（**不算交过**）。
 */
fun homeworkSubmissionPayload(comment: String, draft: Boolean): String =
    """{"comment":${jsonQuote(comment)},"is_draft":$draft,"slides":[],"uploads":[]}"""
