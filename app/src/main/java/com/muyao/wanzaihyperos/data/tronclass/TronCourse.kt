package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asDoubleOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 學在城院的一門課（「我的課」板塊用）。
 *
 * ### 契約（活體探測 2026-10-12，`app/build/tron-shapes.txt`）
 *
 * ```
 * GET https://courses.hzcu.edu.cn/api/my-courses?conditions=%7B%7D&page=1&page_size=30
 * 頭 X-SESSION-ID
 * → { courses: [{
 *       id, name, course_code, course_type, credit,
 *       course_attributes: { teaching_class_name, student_count, published },
 *       department: { name },
 *       instructors: [{ id, name }],
 *       semester: { name, real_name, code },
 *       academic_year: { name },
 *       credit_state: { credit_remaining, status },
 *       start_date, ...
 *   }] }   // 實測 20 門
 * ```
 *
 * ### ★ 為什麼從「最近訪問」改成 `/api/my-courses`
 *
 * 原本用 `/api/user/recently-visited-courses`（只回**最近點過**的幾門課，
 * 見同目錄的舊註釋）。那個清單有兩個毛病：
 *
 * 1. **會漏**：沒點過的課就不在清單裡，學生看不到自己選了什麼；
 * 2. **資訊少**：沒有 `credit`、`instructors`、`semester`。
 *
 * `/api/my-courses` 是**全量**（實測 20 門，含跨學期），且多帶三樣：
 * 教師、學分、學期 —— 「我的課」該顯示的就是這些。
 * 兩個端點的單條欄位形狀相同，所以解析器同時接 `visited_courses` 與 `courses`，
 * 舊的那個端點若哪天要用也不會壞。
 *
 * ### ⚠️ `url` 欄位**不使用**
 *
 * 它可能帶一次性 SSO 憑據 —— 與「我的事務」那批 `url` 同一性質。
 * **不顯示、不點擊**：課程詳情走本 App 原生的 `/api/courses/{id}`。
 */
data class TronCourse(
    /** 課程 id。 */
    val id: Int,
    /** 課程名（`name`）。 */
    val name: String,
    /** 課程代碼（`course_code`）。 */
    val courseCode: String,
    /** 教學班名／上課時間（`course_attributes.teaching_class_name`）。可能為空。 */
    val teachingClass: String,
    /** 開課單位（`department.name`）。可能為空。 */
    val department: String,
    /** 學分（`credit`，字串形態，如 `"3.0"`）。可能為空。 */
    val credit: String = "",
    /** 教師名（`instructors[].name` 用「、」連接）。可能為空。 */
    val teachers: String = "",
    /** 學期名（`semester.real_name` 優先，退回 `semester.name`）。可能為空。 */
    val semesterName: String = "",
) {
    /**
     * 副標：教師 · 學期 · 學分。
     *
     * 放在模型上而不是各頁自己拼 —— 同一門課在「我的課」與「課程動態」都要出現，
     * 兩處各拼一次就會出現「一處有學分一處沒有」。
     */
    val subtitle: String
        get() = listOfNotNull(
            teachers.takeIf { it.isNotBlank() },
            semesterName.takeIf { it.isNotBlank() },
            credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
        ).joinToString("　")
}

object TronCourseParser {
    /**
     * 解析課程清單。缺 id 或課名的條目丟掉（沒有它無法顯示也無法去重）。
     *
     * 外層同時接 `courses`（`/api/my-courses`）與 `visited_courses`
     *（`/api/user/recently-visited-courses`）—— 見類註釋。
     */
    fun parse(root: JsonObject?): List<TronCourse> {
        val list = root?.get("courses").asArrayOrNull()
            ?: root?.get("visited_courses").asArrayOrNull()
            ?: return emptyList()
        return list.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj -> parseOne(obj) }
    }

    /** 解析單條。缺 id／課名回 null（呼叫方據此丟掉）。 */
    fun parseOne(obj: JsonObject): TronCourse? {
        val id = obj["id"].asIntOrNull() ?: return null
        val name = obj["name"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val semester = obj["semester"].asObjectOrNull()
        return TronCourse(
            id = id,
            name = name,
            courseCode = obj["course_code"].asStringOrNull().orEmpty(),
            teachingClass = obj["course_attributes"].asObjectOrNull()
                ?.get("teaching_class_name").asStringOrNull().orEmpty(),
            department = obj["department"].asObjectOrNull()
                ?.get("name").asStringOrNull().orEmpty(),
            credit = obj["credit"].asStringOrNull().orEmpty(),
            teachers = obj["instructors"].asArrayOrNull()
                ?.mapNotNull { it.asObjectOrNull()?.get("name").asStringOrNull() }
                ?.filter { it.isNotBlank() }
                ?.joinToString("、")
                .orEmpty(),
            // `real_name` 是「第1學期」這種短名，`name` 是「2026-2027第1學期」。
            // 顯示用後者更清楚（跨學期的清單裡「第1學期」會有兩條同名）。
            semesterName = semester?.get("name").asStringOrNull().orEmpty()
                .ifBlank { semester?.get("real_name").asStringOrNull().orEmpty() },
        )
    }
}

/** 只用來讀 `credit_state` 的輔助（目前不顯示，留著說明契約裡有這個欄位）。 */
internal fun JsonObject.creditRemaining(): Double? =
    this["credit_state"].asObjectOrNull()?.get("credit_remaining").asDoubleOrNull()
