package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asLongOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 愛講座的一場講座。
 *
 * ## 契約（真機探測 2026-10-13，`docs/B16-CJH52-PROBE.txt`）
 *
 * ```
 * POST /dapi/v2/activity/lecture_service/find_lectures
 *      Authorization: Bearer <tempcode>      ← ★ 少了這個會回 code 401
 *      body {"pageNum":1,"pageSize":10}
 * → { code:200, data:{ total:2541, pageSize, pageNum,
 *       data: [{
 *         id, name,                      // 講題
 *         speakerName, speakerJob,       // 講者 / 職稱
 *         appliedUnit,                   // 主辦單位
 *         type,                          // 分類名（如「國土空間規劃學院學術講座」）
 *         address,                       // 地點
 *         startTime, endTime,            // **毫秒時間戳**
 *         registrationDeadline,          // 報名截止（毫秒）
 *         registrationNumber,            // 名額
 *         normalRegisteredCount,         // 已報名人數
 *         standbyRegisteredCount,        // 候補人數
 *         isRegistering,                 // 是否報名中
 *         state,                         // published 等
 *         signedCount, scored,           // 原始返回字段；其个人签到语义未由探测证实
 *       } ] } }
 * ```
 *
 * 實測 `total: 2541` —— **真的有大量資料**，不是空殼功能。
 *
 * ### ★ 兩個「不騙人」的地方
 *
 * 1. **時間是毫秒數，要轉成本地時間**。照原樣顯示會是一串 13 位數字
 *    （實測 `1792204200000`），完全看不懂。
 * 2. **名額與已報名分開講**。`registrationNumber` 是名額、
 *    `normalRegisteredCount` 是已報名（實測 30 / 4）。
 *    把兩個混成一個「已滿 / 未滿」會讓使用者不知道自己排第幾。
 */
data class Lecture(
    /** 講座 id（`id`，實測是 24 字元十六進位字串）。 */
    val id: String,
    /** 講題（`name`）。 */
    val title: String,
    /** 講者（`speakerName`）。 */
    val speaker: String,
    /** 講者職稱（`speakerJob`）。 */
    val speakerJob: String,
    /** 主辦單位（`appliedUnit`）。 */
    val unit: String,
    /** 分類名（`type`）。 */
    val category: String,
    /** 地點（`address`）。 */
    val address: String,
    /** 開始時間（毫秒）。 */
    val startMillis: Long?,
    /** 結束時間（毫秒）。 */
    val endMillis: Long?,
    /** 報名截止（毫秒）。 */
    val deadlineMillis: Long?,
    /** 名額（`registrationNumber`）。null ＝ 不限或未知。 */
    val capacity: Int?,
    /** 已報名人數（`normalRegisteredCount`）。 */
    val registered: Int?,
    /** 候補人數（`standbyRegisteredCount`）。 */
    val standby: Int?,
    /** 是否報名中（`isRegistering`）。 */
    val registering: Boolean,
    /**
     * 个人签到状态。列表接口的 `signedCount` 个人状态语义未经验证；null 表示未知，
     * 不得展示为已签到或未签到。
     */
    val signed: Boolean?,
) {
    /** 開始時間的本地顯示。 */
    val startLocal: String get() = formatCjh52Time(startMillis)

    /** 報名截止的本地顯示。 */
    val deadlineLocal: String get() = formatCjh52Time(deadlineMillis)

    /**
     * 名額狀況。
     *
     * ★ `capacity` 為 null 時說「不限」而不是「0」——
     * 實測有些講座沒有名額上限，顯示「0 個名額」會讓人以為不能報。
     */
    val capacityLabel: String
        get() {
            val cap = capacity
            val reg = registered
            return when {
                cap == null || cap <= 0 -> {
                    // 名額未知／不限：只講已報名人數（取不到就說不知道）。
                    reg?.let { "已报名 $it" } ?: "名额未知"
                }
                reg == null -> "$cap 个名额"
                else -> "$reg / $cap"
            }
        }

    /** 還能不能報名（報名中 + 名額未滿）。 */
    val canRegister: Boolean
        get() {
            if (!registering) return false
            // ⚠️ `capacity` 為 null **或 0** 都代表「不限名額」——
            //   實測有講座的名額欄位就是 0。若只判 null，
            //   `0` 會走進 `registered < capacity`（7 < 0 = false），
            //   於是「不限名額」被顯示成「已满」。測試抓到這個。
            val cap = capacity?.takeIf { it > 0 } ?: return true
            val reg = registered ?: return true
            return reg < cap
        }

    /** 狀態講法。 */
    val statusLabel: String
        get() = when {
            canRegister -> "报名中"
            registering -> "已满"
            else -> "已结束报名"
        }
}

/**
 * CJH52 的毫秒時間戳 → 本地顯示。
 *
 * ### ★ 為什麼一定要走這裡
 *
 * [Lecture.startMillis] 是**毫秒數**（實測 `1792204200000`）。
 * 直接把數字丟給界面會顯示一串 13 位數字 —— 使用者完全看不懂。
 *
 * 與 Tronclass 的處理共用同一種寫法（`formatTronLocalTime`），
 * 但**不能直接複用**：那支在 `data.tronclass` 套件裡且是 `internal`，
 * 跨模組引用會把「Tronclass 的實作細節」洩漏到 cjh52 這一層。
 */
internal fun formatCjh52Time(millis: Long?): String {
    if (millis == null || millis <= 0L) return ""
    return runCatching {
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull().orEmpty()
}

object LectureParser {

    /**
     * 解析講座清單。
     *
     * 外層是 `{code, data:{total, data:[...]}}` —— 這家的分頁**多包一層**，
     * 與 Tronclass 的 `{x:[...]}` 不同。缺 id 或講題的條目丟掉
     *（沒有它們無法顯示也無法去重）。
     */
    fun parse(root: JsonObject?): List<Lecture> {
        val page = root?.get("data").asObjectOrNull() ?: return emptyList()
        val list = page["data"].asArrayOrNull() ?: return emptyList()
        return list.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val id = obj["id"].asStringOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val name = obj["name"].asStringOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            Lecture(
                id = id,
                title = name,
                speaker = obj["speakerName"].asStringOrNull().orEmpty(),
                speakerJob = obj["speakerJob"].asStringOrNull().orEmpty(),
                unit = obj["appliedUnit"].asStringOrNull().orEmpty(),
                category = obj["type"].asStringOrNull().orEmpty(),
                address = obj["address"].asStringOrNull().orEmpty(),
                startMillis = obj["startTime"].asLongOrNull(),
                endMillis = obj["endTime"].asLongOrNull(),
                deadlineMillis = obj["registrationDeadline"].asLongOrNull(),
                // 名額 0 視為「不限」（實測有些講座是 0）—— 但**保留原始值**讓界面可判斷。
                capacity = obj["registrationNumber"].asIntOrNull(),
                registered = obj["normalRegisteredCount"].asIntOrNull(),
                standby = obj["standbyRegisteredCount"].asIntOrNull(),
                registering = obj["isRegistering"].asBoolOrNull() ?: false,
                // `find_lectures` 探测只证实字段存在、样例为 0，未证实其个人状态语义。
                // 未知个人签到状态不得冒充「已签到」或「未签到」；UI按其他已知报名条件展示状态。
                signed = null,
            )
        }
    }
}
