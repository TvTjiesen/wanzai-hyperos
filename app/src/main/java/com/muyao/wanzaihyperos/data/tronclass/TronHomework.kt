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
 * ### 這一頁現在能「交」了（业主 2026-10-19：「我要在待办里查看作业详情并提交作业」）
 *
 * 先前的判断（「原生只做到看清楚要求，提交走网页版」）**已被推翻**，
 * 推翻的依据是从學在城院 H5 包里逐字挖出来的提交契约（见 [TronSubmission] 的類註釋）：
 *
 * ```
 * POST /api/course/activities/{作业 id}/submissions
 *   body = {"comment":"正文","is_draft":false,"slides":[],"uploads":[]}
 *   201 = 成功
 * ```
 *
 * ⚠️ 仍然**不猜**的部分：附件上传那条链是「`POST /api/uploads` 拿 upload_url →
 * 直传七牛（`GET /api/uptoken?key=`）→ `PUT /api/uploads/{id}/uploaded`」，
 * 三跳且中间那一跳出站到我们管不到的存储域名。
 * **没验证过就不要拿它碰成绩**，所以文件提交仍给网页版出口 —— 见 [TronSubmission] 的说明。
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
    /**
     * 「不限提交次數」（`data.non_submit_times`）。
     *
     * ★ 為什麼要單獨存這個旗標：`submit_times` 在**不限次數**時是 `null`，
     * 而 `null` 也可以是「沒給」。只看 `submit_times` 就無法區分
     * 「不限」與「不知道」—— 前者該讓按鈕亮著，後者不該斷言。
     * 源碼的模型是 `nonSubmitTimes=true, submitTimes=null`（默認即不限），
     * 所以**以這個旗標為準**，`submit_times` 只在旗標為 false 時才當上限。
     */
    val unlimitedSubmits: Boolean,
    /** 是否**小組作業**（`data.submit_by_group`）。 */
    val submitByGroup: Boolean,
    /** 是否允許撤回（`data.allow_retract`）。只用於把話說實：不允許時不提「撤回」。 */
    val allowRetract: Boolean,
) {
    /** 截止時間轉本地顯示（服務端給 ISO，可能帶時區）。 */
    val deadlineLocal: String
        get() = formatTronLocalTime(TronTodoParser.parseIsoMillis(deadline)) ?: deadline.ifBlank { "—" }

    /**
     * **可讀的要求內容**：把 [description] 的 HTML 處理掉。
     *
     * ### ★ 這是真機上抓到的第二個同類 bug
     *
     * B14 修好了**課程大綱**的 HTML（那是在真機上看到的），
     * 但**作業說明是同一種東西**，當時沒一起查。真機一開作業清單就看到：
     * ```
     * <p class="MsoNormal" style="font-family: DengXian; font-size: 10.5pt;
     *    line-height: 24px; text-align: center; color: rgb(0, 0, 0);">…
     * ```
     * 學生的作業要求常常是老師從 Word 貼進去的，所以**這邊的 HTML 只會更常見**
     *（`MsoNormal` 就是 Word 的樣式名）。
     *
     * 走同一條 [htmlToPlainText]（與公告、大綱共用）。
     * 教訓：**同一個來源的富文本欄位要一次全部找出來修**，
     * 一處一處按「看到才修」會漏 —— 這一段就是漏掉的那一處。
     */
    val plainDescription: String get() = htmlToPlainText(description)

    /**
     * 提交上限的顯示文字。
     *
     * 三種說法不能混：「不限次数」是**學校說的**，「次数未知」是**我們沒取到**，
     * 「可交 N 次」是規則。把「不知道」寫成「不限」會讓人多交，
     * 寫成「1 次」會讓人不敢交。
     */
    val submitLimitLabel: String
        get() = when {
            unlimitedSubmits -> "不限次数"
            submitTimes != null -> "可交 $submitTimes 次"
            else -> "次数未知"
        }

    /**
     * 以「已正式提交幾次」為輸入，判斷**還讓不讓再交**。
     *
     * ⚠️ 這裡只做**規則**，不做校驗 —— 真正的判定在服務端
     *（截止時間、鎖定、小組、次數都由它裁）。函式存在的意義是
     * **把按鈕的亮/滅講得出理由**：滅的時候界面上要能顯示「為什麼」。
     *
     * 上限不知道時回 true（**不替學校斷言「不能交」**），
     * 交完服務端若拒絕，原話會顯示出來。
     */
    fun acceptsAnotherSubmission(submittedCount: Int): Boolean =
        unlimitedSubmits || submitTimes == null || submittedCount < submitTimes
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
                // ★ 缺欄位時**按源碼默認值**收（源碼模型就是 `nonSubmitTimes=true`）。
                //   把「學校沒給」讀成「限交 0 次」會直接把提交按鈕打死，
                //   而真實情況多數是這一項根本沒返回那個鍵。
                unlimitedSubmits = data?.get("non_submit_times").asBoolOrNull() ?: true,
                submitByGroup = data?.get("submit_by_group").asBoolOrNull() ?: false,
                allowRetract = data?.get("allow_retract").asBoolOrNull() ?: false,
            )
        }
    }
}
