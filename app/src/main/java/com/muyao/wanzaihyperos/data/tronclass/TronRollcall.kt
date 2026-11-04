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
     * 這一場點名的**生命週期**（`rollcall_status`：`in_progress` / `finished` / `waiting`）。
     *
     * ⚠️ 不要拿它跟考勤狀態比。同一個 JSON 裡的頂層 `status` 是**我**的考勤狀態
     * （`absent` / `on_call`），不是這一場點名的狀態 —— 兩者長得像、意思完全不同，
     * 2026-11-04 就是因為把 `status` 當成生命週期，讓正在進行的點名全被判成「已結束」。
     * 分類規則集中在 [TronAttendanceStates]。
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
    val isOpen: Boolean get() = TronAttendanceStates.isOpenLifecycle(status) && !expired

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
        get() = TronAttendanceStates.label(myStatus) ?: myStatus.ifBlank { "—" }

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

    /**
     * 我還沒簽（進行中且狀態不是「已到」；**請假/缺勤都算還能簽**，由服務端裁定）。
     *
     * ⚠️ 不能只比 `"on_call"`：真實資料裡「到了」有 `on_call_fine` / `on_call_arrive_late`
     * 等五種寫法（見 [TronAttendanceStates.SIGNED]）。只認一個字面量會讓
     * 「簽到了但顯示還要你再簽一次」。
     */
    val unanswered: Boolean get() = isOpen && !TronAttendanceStates.isSigned(myStatus)

    /**
     * 不能點時給使用者的理由。能點時 null。
     *
     * 「按鈕是灰的」必須有一句話跟著 —— 否使用者只能猜是自己沒登入、
     * 還是 App 壞了（這個專案栽過兩次）。
     */
    val actionBlockReason: String?
        get() = when {
            !isOpen && expired -> "这次签到已经截止"
            !isOpen -> "这次签到没有在进行中，服务端说现在是“${status.ifBlank { "未知" }}”"
            TronAttendanceStates.isSigned(myStatus) -> "你已经签过了"
            answerKind == null -> when {
                source.contains("radar", true) -> "雷达签到靠定位/信号自动判定，没有可以点的按钮"
                source.contains("manual", true) -> "老师手动点名，不需要你这边操作"
                source.contains("auto", true) -> "系统自动签到，不需要你这边操作"
                else -> "这种签到类型（${source.ifBlank { "未标明" }}）不支持在 App 里点"
            }
            else -> null
        }
}

/**
 * 簽到裡那兩個**同名字段**到底各是什麼。
 *
 * ★ 這一段是 2026-11-04 真機現場抓出來的（`files/tron_raw_rollcalls.txt` 逐字）：
 *
 * ```json
 * {"rollcall_id":252446,"rollcall_status":"in_progress","status":"absent",
 *  "student_rollcall_id":0,"source":"qr"}
 * ```
 *
 * 同一個 JSON 裡：
 * - `rollcall_status` = **這一場點名**的狀態（`in_progress` / `finished` …）
 * - `status`           = **我**在這場點名的考勤狀態（`absent` / `on_call` …）
 *
 * 舊解析器把 `status` 當成「這一場點名的狀態」，於是：
 * 進行中的點名 → `status` 是 `absent` → `isOpen` 判成 **false** →
 * 界面把它歸到「已結束」，`unanswered` 也是 false →
 * 「簽到」頁那顆掃碼按鈕拿不到任何候選、清單裡那條看起來已經關門。
 * 這是比「判據讀錯層級」更早一步的錯：**連門都進不去**。
 *
 * 所以這裡按**值的形狀**分類，而不是按欄位名猜 —— 兩個欄位名都可能出現在
 * 不同端點的不同位置上（`/api/radar/rollcalls` 與 `answer_qr_rollcall`
 * 的擺法就不完全一樣）。
 */
object TronAttendanceStates {

    /**
     * 這一場點名**還開著**的生命週期值。
     *
     * ⚠️ `waiting` **不在這裡**：它是「還沒開始」，那時候按鈕也不該給
     *（既有用例 `截止的与没开始的都不算开放` 守著這條）。
     */
    private val OPEN_LIFECYCLE = setOf("in_progress", "open")

    /** 生命週期裡所有認得的值（用來判斷某個 `status` 是不是在說「這場點名」）。 */
    private val LIFECYCLE = OPEN_LIFECYCLE + setOf("finished", "closed", "ended", "expired")

    /**
     * 算「到了」的考勤狀態。
     *
     * 請假（`on_*_leave`）與缺勤（`absent`）**不算到** —— 那些是「沒出席」的紀錄，
     * 把請假算成到課等於告訴他「你上課了」，而他一節課都沒去。
     */
    val SIGNED = setOf(
        "on_call",
        "on_call_fine",
        "on_call_arrive_late",
        "on_call_leave_early",
        "on_call_arrive_late_leave_early",
    )

    /** 所有認得的考勤狀態（不含生命週期值）。 */
    private val ATTENDANCE = SIGNED + setOf(
        "absent",
        "on_leave",
        "on_personal_leave",
        "on_sick_leave",
        "on_public_leave",
        "on_physiological_leave",
        "on_funeral_leave",
        "on_mental_health_leave",
        "other",
    )

    /** 這個值是在說「這一場點名」嗎？ */
    fun isLifecycle(value: String): Boolean = value in LIFECYCLE

    /** 這個值是在說「我到了沒有」嗎？ */
    fun isAttendance(value: String): Boolean = value in ATTENDANCE

    /** 算不算「到了」。 */
    fun isSigned(value: String): Boolean = value in SIGNED

    /** 這場點名還開著嗎？ */
    fun isOpenLifecycle(value: String): Boolean = value in OPEN_LIFECYCLE

    /** 考勤狀態翻成人話；認不出來回 null（**不編**）。 */
    fun label(value: String): String? = when (value) {
        "on_call", "on_call_fine" -> "已签到"
        "on_call_arrive_late" -> "迟到"
        "on_call_leave_early" -> "早退"
        "on_call_arrive_late_leave_early" -> "迟到且早退"
        "on_leave" -> "请假"
        "on_personal_leave" -> "事假"
        "on_sick_leave" -> "病假"
        "on_public_leave" -> "公假"
        "on_physiological_leave" -> "生理假"
        "on_funeral_leave" -> "丧假"
        "on_mental_health_leave" -> "心理健康假"
        "absent" -> "缺勤"
        "other" -> "其他"
        else -> null
    }
}

object TronRollcallParser {

    /**
     * 解析一條簽到。
     *
     * @param myStudentId 自己的 `student_id`（用來從 `student_rollcalls[]` 挑出自己那條）。
     *   為 null 時仍會嘗試用**頂層 `status`**（`/api/radar/rollcalls` 就是這樣回我的狀態的）。
     */
    fun parse(obj: JsonObject, myStudentId: Int?): TronRollcall? {
        // ★ 主鍵有兩個名字：`id` 與 `rollcall_id`。
        //   `/api/radar/rollcalls` 實測用的是 **`rollcall_id`**（2026-11-04 15:16 機器原文）。
        //   上一版只認 `id` ⇒ 每一條都 `return null` ⇒ **清單永遠是空的**，
        //   於是「簽到」頁一直說「清單裡現在沒有進行中的簽到」，
        //   掃碼那顆按鈕也永遠拿不到候選（因為老師正在點名，它卻說沒有）。
        val id = obj["id"].asIntOrNull() ?: obj["rollcall_id"].asIntOrNull() ?: return null
        val topStatus = obj["status"].asStringOrNull().orEmpty()
        val rollcallStatus = obj["rollcall_status"].asStringOrNull().orEmpty()

        // 生命週期：先看 `rollcall_status`（它專指這一場點名），
        // 再看 `status` —— 但**只在它的值長得像生命週期時**才認，
        // 否則會把「我缺勤」讀成「這場點名結束了」。
        val lifecycle = rollcallStatus.ifBlank {
            topStatus.takeIf { TronAttendanceStates.isLifecycle(it) }.orEmpty()
        }

        val mine = obj["student_rollcalls"].asArrayOrNull()
            ?.mapNotNull { it.asObjectOrNull() }
            ?.firstOrNull { myStudentId != null && it["student_id"].asIntOrNull() == myStudentId }

        val myStatus = mine?.get("status").asStringOrNull().orEmpty().ifBlank {
            // 頂層 `status` 是考勤值時就是我的狀態（`absent` / `on_call` …）。
            topStatus.takeIf { TronAttendanceStates.isAttendance(it) }.orEmpty()
        }

        return TronRollcall(
            id = id,
            courseId = obj["course_id"].asIntOrNull() ?: 0,
            title = obj["title"].asStringOrNull().orEmpty(),
            source = obj["source"].asStringOrNull().orEmpty(),
            status = lifecycle,
            expired = obj["is_expired"].asBoolOrNull() ?: false,
            rollcallTime = obj["rollcall_time"].asStringOrNull().orEmpty(),
            endTime = obj["end_time"].asStringOrNull().orEmpty(),
            numberCode = obj["number_code"].asStringOrNull().orEmpty(),
            myStatus = myStatus,
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
