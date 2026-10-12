package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asDoubleOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 學在城院的一門課（首頁「最近訪問的課程」用）。
 *
 * ### 契約（活體探測 2026-10-12）
 *
 * ```
 * GET https://courses.hzcu.edu.cn/api/user/recently-visited-courses
 * 頭 X-SESSION-ID
 * → { visited_courses: [{
 *       id, name, course_code, course_type,
 *       course_attributes: { teaching_class_name },
 *       department: { name },
 *       credit_state: { credit_remaining, status },
 *       cover, url, ...
 *   }] }   // 實測 6 門
 * ```
 *
 * ### ⚠️ `url` 欄位**不使用**
 *
 * 它是課程頁地址（52 字元），很可能帶一次性 SSO 憑據 ——
 * 與「我的事務」那批 `url` 同一性質。**不顯示、不點擊**：
 * 要在 App 裡看課程詳情，走「改用網頁版」那條正規入口。
 */
data class TronCourse(
    /** 課程 id。 */
    val id: Int,
    /** 課程名（`name`）。 */
    val name: String,
    /** 課程代碼（`course_code`）。 */
    val courseCode: String,
    /** 教學班名（`course_attributes.teaching_class_name`）。可能為空。 */
    val teachingClass: String,
    /** 開課單位（`department.name`）。可能為空。 */
    val department: String,
)

object TronCourseParser {
    /** 從 `visited_courses` 解析。缺 id 或課名的條目丟掉（沒有它無法顯示也無法去重）。 */
    fun parse(root: JsonObject?): List<TronCourse> {
        val list = root?.get("visited_courses").asArrayOrNull() ?: return emptyList()
        return list.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val id = obj["id"].asIntOrNull() ?: return@mapNotNull null
            val name = obj["name"].asStringOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            TronCourse(
                id = id,
                name = name,
                courseCode = obj["course_code"].asStringOrNull().orEmpty(),
                teachingClass = obj["course_attributes"].asObjectOrNull()
                    ?.get("teaching_class_name").asStringOrNull().orEmpty(),
                department = obj["department"].asObjectOrNull()
                    ?.get("name").asStringOrNull().orEmpty(),
            )
        }
    }
}

/** 只用來讀 `credit_state` 的輔助（目前不顯示，留著說明契約裡有這個欄位）。 */
internal fun JsonObject.creditRemaining(): Double? =
    this["credit_state"].asObjectOrNull()?.get("credit_remaining").asDoubleOrNull()
