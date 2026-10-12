package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asLongOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 學在城院（TronClass）的一條待辦。
 *
 * ### 契約來源
 *
 * 真機取證（`build/tron3.json`）：在已登入的學在城院頁面上重新載入，
 * 記錄它自己發的請求。
 *
 * ```jsonc
 * GET https://mobile.hzcu.edu.cn/api/todos
 * → { todo_list: [
 *      { course_code, course_id, course_name, course_type,
 *        end_time, id, is_locked, is_student,
 *        prerequisites: [], title, type }
 *    ] }
 * ```
 * 實測 19 條。
 *
 * ### ⚠️ `end_time` 實測全是 null
 *
 * 19 條**全部**是 `null`。所以：
 * - 時間顯示必須能處理「沒有」；
 * - 排序**不能假設它有值**（[com.muyao.wanzaihyperos.data.todo.TodoItem.sortTimeMillis] 可空）。
 *
 * ### `id` 是數字
 *
 * 用 `Int` 收。**不轉成 String 存**：它是來源內唯一鍵，
 * 保留數字形態能讓「去重」的意圖在型別上就看得出來（數字 id 天生唯一且可比較）。
 */
data class TronTodo(
    val id: Int,
    /** 作業標題（`title`）。 */
    val title: String,
    /** 課程名（`course_name`）。 */
    val courseName: String,
    /** 課程代碼（`course_code`）。 */
    val courseCode: String,
    /** 類型（`type`，實測長度 4，如 `homework`）。 */
    val type: String,
    /**
     * 截止時間的**毫秒值**。
     *
     * ⚠️ 服務端給的 `end_time` 在本次取證裡全是 `null`，
     * 所以這個欄位多數時候是 null。若哪天它給了值，可能是 ISO 字串或毫秒數 ——
     * 兩種都試，試不出來就當「沒有」（**不猜**）。
     */
    val endTimeMillis: Long?,
    /** 時間的顯示文字（原始形態，便於「服務端改了格式」時還原樣顯示）。 */
    val timeText: String,
    /** 是否已鎖定（`is_locked`）。鎖定的作業通常不能提交。 */
    val locked: Boolean,
) {
    /**
     * 截止時間的**本地**顯示文字（`yyyy-MM-dd HH:mm`）。
     *
     * ### ★ 為什麼一定要走這裡，不能直接用 [timeText]
     *
     * [timeText] 是服務端原樣字串，帶 `Z` 表示 **UTC**（實測
     * `2026-10-14T15:30:00Z`，而網頁顯示「截止 2026.10.14 **23:30**」）。
     * 照原樣顯示會**早 8 小時** —— 那是會讓人**錯過交作業**的錯誤。
     *
     * 把它做成模型上的屬性（而不是讓每個頁面各自轉換）：
     * 一旦有第二個頁面顯示待辦時間，它不可能忘記轉 —— 因為**沒有別的欄位可用**。
     */
    val localTimeText: String
        get() = formatTronLocalTime(endTimeMillis) ?: timeText
}

/**
 * 毫秒 → 本地時間 `yyyy-MM-dd HH:mm`。解析不出來回 null（呼叫方退回原字串）。
 *
 * 用系統時區：截止時間的意義就是「你當地幾點」。
 */
internal fun formatTronLocalTime(millis: Long?): String? {
    if (millis == null || millis <= 0L) return null
    return runCatching {
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull()
}

object TronTodoParser {

    /**
     * 從 `/api/todos` 的回應解析。
     *
     * 寬進嚴出：缺 `id` 的條目丟掉（沒有 id 就無法去重，留著會出現重複項），
     * 其餘欄位缺了給預設值。
     */
    fun parse(root: JsonObject?): List<TronTodo> {
        val list = root?.get("todo_list").asArrayOrNull() ?: return emptyList()
        return list.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val id = obj["id"].asIntOrNull() ?: return@mapNotNull null
            val endRaw = obj["end_time"]
            val endMillis = endRaw.asLongOrNull()
                ?: endRaw.asStringOrNull()?.let { parseIsoMillis(it) }
            TronTodo(
                id = id,
                title = obj["title"].asStringOrNull().orEmpty(),
                courseName = obj["course_name"].asStringOrNull().orEmpty(),
                courseCode = obj["course_code"].asStringOrNull().orEmpty(),
                type = obj["type"].asStringOrNull().orEmpty(),
                endTimeMillis = endMillis,
                // 有值時把原始形態留下來顯示（免得我們解析錯了就完全看不到資訊）。
                timeText = endRaw.asStringOrNull().orEmpty(),
                locked = obj["is_locked"].asBoolOrNull() ?: false,
            )
        }
    }

    /** ISO-8601（可帶時區）→ 毫秒。解析不出來回 null。 */
    internal fun parseIsoMillis(raw: String): Long? {
        if (raw.isBlank()) return null
        val text = raw.trim()
        return runCatching {
            java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli()
        }.recoverCatching {
            java.time.LocalDateTime.parse(text.replace(' ', 'T'))
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
