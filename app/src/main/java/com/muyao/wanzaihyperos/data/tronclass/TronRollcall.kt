package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 學在城院的**簽到**。
 *
 * ### 契約（逐字讀自 bundle 的 mobx 模型 + 接口表）
 *
 * ```
 * GET /api/radar/rollcalls               進行中的簽到（實測 200 可用）
 * GET /api/rollcall/{id}/lite            單條簽到信息
 * GET /api/courses/rollcall_status/{id}/result   簽到結果（含每個人的狀態）
 * ```
 *
 * 模型欄位（源碼 `this.rollcallId=0, this.courseId=0, ...`）：
 * ```
 * id, course_id, title, updated_at, status, scored,
 * is_radar, is_number, type, section, is_expired,
 * published_at, source, module_id, rollcall_time, end_time,
 * number_code, student_rollcalls[], latitude, longitude
 * ```
 *
 * ### 簽到「方式」有 7 種（源碼枚舉）
 *
 * `manual`（老師手動點）/ `number`（數字）/ `radar`（雷達）/ `qr`（掃碼）/
 * `selfRegistration`（自助）/ `autoAttendance`（自動）/ `merged`（合併）。
 *
 * 我們**不區分全部**：只把「使用者需不需要動手」講清楚 ——
 * 這才是他關心的。所以保存原始 source，界面上翻譯成人話。
 *
 * ### ★ 為什麼這一頁只做「看」
 *
 * 校驗全在服務端（源碼錯誤碼：位置不對、二維碼過期、IP 不對、雷達沒信号）。
 * 「看」的部分沒有風險；「簽」的部分是寫操作，見 docs/B9-TRONCLASS-CHECKIN.md。
 */
data class TronRollcall(
    val id: Int,
    val courseId: Int,
    /** 標題（通常是課程名或「第N次點名」）。 */
    val title: String,
    /** 來源方式（`source`，如 `number` / `radar` / `qr`）。 */
    val source: String,
    /**
     * 狀態（`status`，源碼枚舉 `in_progress` / `finished` / `waiting`）。
     */
    val status: String,
    /** 是否已結束（`is_expired`）。 */
    val expired: Boolean,
    /** 簽到時間（`rollcall_time`）。 */
    val rollcallTime: String,
    /** 截止時間（`end_time`）。 */
    val endTime: String,
    /** 數字簽到的數字（`number_code`）。**僅在進行中且是數字簽到時才有意義**。 */
    val numberCode: String,
    /** 我在這次簽到裡的狀態（從 `student_rollcalls[]` 挑出自己那條）。 */
    val myStatus: String,
) {
    /** 是否進行中（還能簽）。 */
    val isOpen: Boolean get() = status == "in_progress" && !expired

    /** 使用者看得懂的方式名。認不出來的**原樣顯示**（不編）。 */
    val sourceLabel: String
        get() = when (source) {
            "manual" -> "老师点名"
            "number" -> "数字签到"
            "radar" -> "雷达签到"
            "roomis" -> "扫码签到"
            "qr" -> "扫码签到"
            "qr_rollcall" -> "扫码签到"
            "self_registration" -> "自助签到"
            "auto_attendance" -> "自动签到"
            "merged_rollcall" -> "合并签到"
            "middle_db" -> "签到"
            else -> source.ifBlank { "签到" }
        }

    /** 我的狀態翻成人話；認不出來原樣顯示。 */
    val myStatusLabel: String
        get() = when (myStatus) {
            "on_call" -> "已签到"
            "on_leave" -> "请假"
            "absent" -> "缺勤"
            "" -> "—"
            else -> myStatus
        }

    /**
     * 這一條該用**哪個端點**去簽（null = 不是「學生點一下就能簽」的類型）。
     *
     * ★ 為什麼從 UI 搬到模型上（业主 2026-10-19：「簽點重做，做成磁貼點進去可以掃碼
     *   或其他簽到方式」）：重做後的頁面有**兩個入口**要知道這件事 ——
     *   頂那顆「掃碼簽到」和每一行自己的按鈕。兩處各寫一份 `source.contains(...)`，
     *   遲早出現「頂部認為這是掃碼簽到、行裡卻不給掃碼」的分歧。
     *   規則只放一處，界面只消費它。
     *
     * 判定順序與源碼的端點命名一致：`number` → `self_registration` → `qr`/`roomis`。
     * `radar`（靠定位/信号）與 `manual`（老師手動點名）**故意回 null**：
     * 給它們一顆能點的按鈕等於許一件我們做不到的事。
     */
    val answerKind: RollcallAnswerKind?
        get() = when {
            source.contains("number", true) -> RollcallAnswerKind.NUMBER
            source.contains("self", true) || source.contains("registration", true) ->
                RollcallAnswerKind.SELF_REGISTRATION
            source.contains("qr", true) || source.contains("roomis", true) ||
                source.contains("scan", true) -> RollcallAnswerKind.QR
            else -> null
        }

    /** 需不需要相機（只有掃碼那一類）。 */
    val needsCamera: Boolean get() = answerKind == RollcallAnswerKind.QR

    /** 我還沒簽（進行中且狀態不是「已簽」；**請假/缺勤都算還能簽**，由服務端裁定）。 */
    val unanswered: Boolean get() = isOpen && myStatus != "on_call"

    /**
     * 不能點時給使用者的理由。能點時 null。
     *
     * 「按鈕是灰的」必須有一句話跟著 —— 否使用者只能猜是自己沒登入、
     * 還是 App 壞了（這個專案栽過兩次）。
     */
    val actionBlockReason: String?
        get() = when {
            !isOpen && expired -> "这次签到已经截止"
            !isOpen -> "这次签到还没结束显示中的状态，服务端说现在是“${status.ifBlank { "未知" }}”"
            myStatus == "on_call" -> "你已经签过了"
            answerKind == null -> when {
                source.contains("radar", true) -> "雷达签到靠定位/信号自动判定，没有可以点的按钮"
                source.contains("manual", true) -> "老师手动点名，不需要你这边操作"
                source.contains("auto", true) -> "系统自动签到，不需要你这边操作"
                else -> "这种签到类型（${source.ifBlank { "未标明" }}）不支持在 App 里点"
            }
            else -> null
        }
}

object TronRollcallParser {

    /**
     * 解析一條簽到。
     *
     * @param myStudentId 自己的 `student_id`（用來從 `student_rollcalls[]` 挑出自己那條）。
     *   為 null 時 `myStatus` 為空 —— 那是「不知道」，不是「沒簽」。
     */
    fun parse(obj: JsonObject, myStudentId: Int?): TronRollcall? {
        val id = obj["id"].asIntOrNull() ?: return null
        val mine = obj["student_rollcalls"].asArrayOrNull()
            ?.mapNotNull { it.asObjectOrNull() }
            ?.firstOrNull { myStudentId != null && it["student_id"].asIntOrNull() == myStudentId }
        return TronRollcall(
            id = id,
            courseId = obj["course_id"].asIntOrNull() ?: 0,
            title = obj["title"].asStringOrNull().orEmpty(),
            source = obj["source"].asStringOrNull().orEmpty(),
            status = obj["status"].asStringOrNull().orEmpty(),
            expired = obj["is_expired"].asBoolOrNull() ?: false,
            rollcallTime = obj["rollcall_time"].asStringOrNull().orEmpty(),
            endTime = obj["end_time"].asStringOrNull().orEmpty(),
            numberCode = obj["number_code"].asStringOrNull().orEmpty(),
            myStatus = mine?.get("status").asStringOrNull().orEmpty(),
        )
    }

    /**
     * 解析清單。回應可能是 `[...]`，也可能包在 `{rollcalls: [...]}` 或
     * `{data: [...]}` 裡 —— 三種都接（這個站的回應外層不統一，
     * 寧可多接一種，也不要因為外層變了就空白）。
     */
    fun parseList(root: kotlinx.serialization.json.JsonElement?, myStudentId: Int?): List<TronRollcall> {
        val array = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is JsonObject ->
                root["rollcalls"].asArrayOrNull()
                    ?: root["data"].asArrayOrNull()
                    ?: root["data"].asObjectOrNull()?.get("rollcalls").asArrayOrNull()
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { it.asObjectOrNull() }
            .mapNotNull { parse(it, myStudentId) }
    }
}
