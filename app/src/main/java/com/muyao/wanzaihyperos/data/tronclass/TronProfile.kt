package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 學在城院「我的」要展示的個人資訊。
 *
 * ### 契約（活體探測 `GET /api/profile`）
 *
 * ```
 * { id, name, user_no, avatar_big_url, role, active,
 *   department: { name, short_name }, grade: { name },
 *   klass: { name, code }, org: { name, ... } }
 * ```
 *
 * ### ★ 只放「該顯示的」，不放「能拿到的」
 *
 * 這個接口回傳的欄位遠不止這些（還有 `email`、`mobile_phone`、`program`、
 * `storage_*` 等）。這裡**刻意只留展示需要的**：
 *
 * - **不取手機號、郵箱** —— 那是個資，App 沒有理由把它們載進記憶體只為了顯示；
 * - **不取 `id`** —— 那是識別碼，界面不該顯示（別的功能要用時另外取）。
 *
 * 這與本專案一貫的做法一致（\u00a7A3「不顯示 `url`」、\u00a7B4「卡號只留後四位」）。
 */
data class TronProfile(
    /** 姓名。**這是個資**：只顯示在「我的」頁，不進日誌、不進截圖（截圖時打碼）。 */
    val name: String,
    /** 學號（`user_no`）。同樣是個資，僅顯示。 */
    val userNo: String,
    /** 頭像地址。null = 沒有。 */
    val avatarUrl: String?,
    /** 身分（`role`，如 `student`）。 */
    val role: String,
    /** 院系／部門名。 */
    val department: String,
    /** 年級。 */
    val grade: String,
    /** 班級。 */
    val klass: String,
    /** 學校名。 */
    val orgName: String,
) {
    /**
     * 身分的中文（認不出來原樣顯示，不編）。
     *
     * ⚠️ 服務端回的是 **`Student`（首字母大寫）**，實測確認 ——
     * 所以比對前先轉小寫。這種「大小寫不一致」是最容易漏的一類：
     * 漏了不會報錯，只會讓每個人都顯示成 `Student` 而不是「学生」。
     */
    val roleLabel: String
        get() = when (role.lowercase()) {
            "student" -> "学生"
            "teacher" -> "教师"
            "assistant" -> "助教"
            else -> role.ifBlank { "—" }
        }

    /** 「我的」頁頂部的副標：學號 + 班級。 */
    val identityLine: String
        get() = listOf(userNo, klass).filter { it.isNotBlank() }.joinToString("　")
}

object TronProfileParser {
    fun parse(root: JsonObject?): TronProfile? {
        root ?: return null
        val name = root["name"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return TronProfile(
            name = name,
            userNo = root["user_no"].asStringOrNull().orEmpty(),
            // 頭像：只認 https 的（http 在校外網路會失敗，顯示破圖不如不顯示）。
            avatarUrl = (root["avatar_big_url"].asStringOrNull()
                ?: root["avatar_small_url"].asStringOrNull())
                ?.takeIf { it.startsWith("https://") },
            role = root["role"].asStringOrNull().orEmpty(),
            department = (root["department"].asObjectOrNull()?.get("name").asStringOrNull()
                ?: root["department"].asObjectOrNull()?.get("short_name").asStringOrNull()).orEmpty(),
            grade = root["grade"].asObjectOrNull()?.get("name").asStringOrNull().orEmpty(),
            klass = root["klass"].asObjectOrNull()?.get("name").asStringOrNull().orEmpty(),
            orgName = root["org"].asObjectOrNull()?.get("name").asStringOrNull().orEmpty(),
        )
    }
}

/**
 * 「我的」頁的**統計**（有幾個待辦、幾門課…）。
 *
 * 這些數字**全部來自已經取過的資料**，不另外打接口 ——
 * 多打一次只是為了顯示一個數字，不划算（而限流紅線要求我們省著用）。
 *
 * ⚠️ 每一項都是 `Int?`：**取不到時是 null 而不是 0**。
 * 「不知道有幾個」與「一個都沒有」是兩件事（本專案從 A2 起的紀律）。
 */
data class TronMyStats(
    val todoCount: Int?,
    val courseCount: Int?,
    /** 進行中的簽到數。 */
    val openRollcallCount: Int?,
)
