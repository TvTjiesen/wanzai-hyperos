package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 一門課的**詳情**（「我的課 → 點進去」那一頁）。
 *
 * ### 契約（活體探測 2026-10-12）
 *
 * ```
 * GET https://courses.hzcu.edu.cn/api/courses/{id}
 * → {
 *     id, name, display_name, course_code, subject_code, credit,
 *     department: { name }, instructors: [{ id, name }],
 *     semester: { name, real_name },
 *     start_date, end_date, classroom_schedule, compulsory, public_scope,
 *     course_outline: {
 *       status,                       // 實測 "empty"（老師沒填大綱）
 *       common_fields: [ { key, title, description } ],   // 11 個固定欄位
 *       custom_fields: []
 *     }
 *   }
 * ```
 *
 * ### ★ `course_outline` 的實情：欄位在、內容空
 *
 * 實測 11 個 `common_fields` 的 `title` 都在（教學大綱／教學進度表／課程評分方式／
 * 基本教材／教學目標…），但 `description` **全部是空字串**，`status` 是 `"empty"`。
 * 所以界面上要能把「老師還沒填」與「取數失敗」分開講
 *（[outlineFilled] 就是為了這個）。
 */
data class TronCourseDetail(
    val id: Int,
    /** 課名（`display_name` 優先，退回 `name`）。 */
    val name: String,
    val courseCode: String,
    /** 學分（`credit`）。 */
    val credit: String,
    /** 開課單位。 */
    val department: String,
    /** 教師名（「、」連接）。 */
    val teachers: String,
    /** 學期名。 */
    val semesterName: String,
    /** 開始日期（`start_date`，`yyyy-MM-dd`）。 */
    val startDate: String,
    /** 大綱欄位（含沒填的 —— 標題本身是資訊）。 */
    val outline: List<TronOutlineField>,
) {
    /**
     * 大綱**有沒有真的填**。
     *
     * 判據是「至少一個欄位有非空描述」，而不是看 `status` ——
     * `status` 是服務端的內部狀態字串，哪天它改了名不該讓界面說謊。
     */
    val outlineFilled: Boolean get() = outline.any { it.description.isNotBlank() }

    /** 有填寫內容的大綱欄位。 */
    val filledOutline: List<TronOutlineField> get() = outline.filter { it.description.isNotBlank() }
}

/** 大綱的一個欄位（標題固定，描述可能為空）。 */
data class TronOutlineField(
    /** 欄位標題（`title`，如「教學目標」）。 */
    val title: String,
    /** 內容（`description`）。未填時為空字串。 */
    val description: String,
)

object TronCourseDetailParser {

    fun parse(root: JsonObject?): TronCourseDetail? {
        root ?: return null
        val id = root["id"].asIntOrNull() ?: return null
        val name = root["display_name"].asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: root["name"].asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        return TronCourseDetail(
            id = id,
            name = name,
            courseCode = root["course_code"].asStringOrNull().orEmpty(),
            credit = root["credit"].asStringOrNull().orEmpty(),
            department = root["department"].asObjectOrNull()?.get("name").asStringOrNull().orEmpty(),
            teachers = root["instructors"].asArrayOrNull()
                ?.mapNotNull { it.asObjectOrNull()?.get("name").asStringOrNull() }
                ?.filter { it.isNotBlank() }
                ?.joinToString("、")
                .orEmpty(),
            semesterName = root["semester"].asObjectOrNull()?.get("name").asStringOrNull().orEmpty(),
            startDate = root["start_date"].asStringOrNull().orEmpty(),
            outline = parseOutline(root["course_outline"].asObjectOrNull()),
        )
    }

    /**
     * 大綱欄位：`common_fields` + `custom_fields`（老師自訂的欄位）。
     * 標題為空的條目丟掉（沒有標題沒法顯示）。
     */
    private fun parseOutline(outline: JsonObject?): List<TronOutlineField> {
        outline ?: return emptyList()
        val fields = (outline["common_fields"].asArrayOrNull().orEmpty() +
            outline["custom_fields"].asArrayOrNull().orEmpty())
        return fields.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val title = obj["title"].asStringOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            TronOutlineField(
                title = title,
                description = obj["description"].asStringOrNull().orEmpty().trim(),
            )
        }
    }
}
