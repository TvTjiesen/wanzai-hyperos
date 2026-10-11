package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 課表模型 —— **一門課可以有多個上課時段**。
 *
 * ### 為什麼必須是「一課多段」
 *
 * 這是 PLAN §B1 特別點名的風險（「模型支援**多周次、多時段、多教室**」）。
 * 真機取證確認：介面回的是**扁平的課程列表**，每門課自己帶一個
 * `classTimeInfoList[]`。同一門《大學物理(II)》可能：
 *
 * - 週一 1-2 節 @ 教六310（1-8 週），
 * - 週三 3-4 節 @ 理4-205（9-16 週）。
 *
 * 如果模型做成「一課一時段」，上面這門課就會**丟掉一半**，
 * 而丟掉的那一半不會報錯 —— 它只是靜默消失。
 * 所以這裡的結構**從一開始就是 `List<CourseSlot>`**。
 *
 * ### 欄位型別的三個坑（都來自實測，不是猜的）
 *
 * 1. **`startWeek` / `endWeek` 是字串不是數字**，且 `endWeek` 實測出現過 2 位數
 *    （契約文檔 §4.3）。所以**不要**用 `toInt()` 硬轉，要用寬鬆讀取。
 * 2. **課程層級的 `startTime` / `endTime` 實測是 `null`** ——
 *    真正的時間在 `classTimeInfoList[].start/end`。**別接錯層級**。
 * 3. `classTimeInfoList` 可能**缺失或為空**（課程已排但未排時段）。
 *    這種課要保留（學生要看到「有這門課」），只是沒有可展示的時段。
 */
data class Course(
    /** 課程 id（`cid`）。同一門課只有一個。 */
    val id: Int,
    /** 課程名（`className`）。 */
    val name: String,
    /** 班號（`classNo`），如 `(2026-2027-1)-A0101-4-J05018-5`。 */
    val classNo: String,
    /** 開課學院（`collegeName`）。 */
    val collegeName: String,
    /** 主講教師（`teaName`）。可能為空。 */
    val teacherName: String,
    /** 課程代碼（`courseCode`）。 */
    val courseCode: String,
    /** 課程性質（`natureContent`），如「必修」。 */
    val nature: String,
    /** **所有上課時段**。見類註釋：這是清單不是單值。 */
    val slots: List<CourseSlot>,
) {
    /** 這門課涉及的所有週次（給「本週有什麼課」用）。 */
    val weekRange: IntRange?
        get() {
            val all = slots.mapNotNull { it.weekRange }
            if (all.isEmpty()) return null
            return all.minOf { it.first }..all.maxOf { it.last }
        }
}

/**
 * 一個上課時段。**這是課表的真正單位** —— 排課表是按它排，不是按 [Course]。
 */
data class CourseSlot(
    /** 星期。實測是中文（「星期一」），也可能是數字 —— 用 [weekdayIndex] 歸一。 */
    val day: String,
    /** 開始時間 `HH:mm:ss`。 */
    val start: String,
    /** 結束時間 `HH:mm:ss`。 */
    val end: String,
    /** 起始週（字串原文，如 `"1"`）。 */
    val startWeek: String,
    /** 結束週（字串原文，如 `"16"`）。 */
    val endWeek: String,
    /** 上課地點（`location`），如 `教六310`。 */
    val location: String,
    /** 教室名（`classroomName`），與 [location] 可能重複或互補。 */
    val classroomName: String,
    /** 校區（`campusName`）。 */
    val campusName: String,
    /** 教室 sid，用於去重/關聯。 */
    val classroomSid: Int?,
) {
    /** 週次區間；兩個欄位都讀不到時 null（**不是 0..0**）。 */
    val weekRange: IntRange?
        get() {
            val s = startWeek.toIntOrNull() ?: return null
            val e = endWeek.toIntOrNull() ?: s
            return if (e >= s) s..e else s..s
        }

    /**
     * 星期幾（1=週一 … 7=週日）；認不出來時 null。
     *
     * 相容中文（「星期一」/「周一」/「週一」）與數字（「1」）。
     * **不用 `contains` 猜**：`"星期一"` 與 `"星期天"` 都要能落到正確值，
     * 而 `"星期日"`/`"星期天"` 都歸 7。
     */
    val weekdayIndex: Int?
        get() {
            val t = day.trim()
            if (t.isEmpty()) return null
            t.toIntOrNull()?.let { if (it in 1..7) return it }
            // 先處理「日/天」→ 7，否則「星期一…六」的比對不會誤中
            if (t.contains('日') || t.contains('天')) return 7
            for (i in 1..6) {
                if (t.contains(WEEKDAY_CHARS[i - 1])) return i
            }
            return null
        }

    /** 展示用的地點（`location` 優先，退回 `classroomName`）。 */
    val displayLocation: String get() = location.ifBlank { classroomName }

    /** 展示用的時間段。 */
    val displayTime: String get() = "$start~$end"

    private companion object {
        /** 一…六 的中文序數（用於星期解析）。 */
        val WEEKDAY_CHARS = listOf("一", "二", "三", "四", "五", "六")
    }
}

/**
 * 把 CJH52 的 `get_class_info_by_uid` 回應映射成 [Course] 清單。
 *
 * **映射策略：寬進嚴出。**
 * - 讀不到的欄位給空字串/0，**不拋異常** —— 學校的介面欄位會變，
 *   一個欄位缺失不該讓整張課表空白。
 * - 但**沒有 `cid` 或 `className` 的條目直接丟掉** —— 那種條目無法展示也無法去重，
 *   留著只會變成課表上一格什麼都沒有的空白。
 */
object CourseParser {

    fun parse(data: List<JsonObject>): List<Course> = data.mapNotNull { parseCourse(it) }

    private fun parseCourse(obj: JsonObject): Course? {
        val id = obj["cid"].asIntOrNull() ?: return null
        val name = obj["className"].asStringOrNull() ?: return null

        val slots = obj["classTimeInfoList"]
            .asArrayOrNull()
            .orEmpty()
            .mapNotNull { it.asObjectOrNull() }
            .map { parseSlot(it) }

        return Course(
            id = id,
            name = name,
            classNo = obj["classNo"].asStringOrNull().orEmpty(),
            collegeName = obj["collegeName"].asStringOrNull().orEmpty(),
            teacherName = obj["teaName"].asStringOrNull().orEmpty(),
            courseCode = obj["courseCode"].asStringOrNull().orEmpty(),
            nature = obj["natureContent"].asStringOrNull().orEmpty(),
            slots = slots,
        )
    }

    private fun parseSlot(obj: JsonObject): CourseSlot = CourseSlot(
        day = obj["day"].asStringOrNull().orEmpty(),
        start = obj["start"].asStringOrNull().orEmpty(),
        end = obj["end"].asStringOrNull().orEmpty(),
        startWeek = obj["startWeek"].asStringOrNull().orEmpty(),
        endWeek = obj["endWeek"].asStringOrNull().orEmpty(),
        location = obj["location"].asStringOrNull().orEmpty(),
        classroomName = obj["classroomName"].asStringOrNull().orEmpty(),
        campusName = obj["campusName"].asStringOrNull().orEmpty(),
        classroomSid = obj["classroomSid"].asIntOrNull(),
    )
}
