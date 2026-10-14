package com.muyao.wanzaihyperos.data.zy

/**
 * 智雲課堂的時間格式化。
 *
 * ## ★ 為什麼放在資料層而不是 UI 層
 *
 * 因為它是**純函式且需要單測**的邏輯：服務端給的時間格式有坑
 *（見下），而 UI 層的 Composable 很難單測。
 * 放在這裡，[ZyParserTest] 能直接盯著它。
 *
 * ## 坑：服務端給的是 **Unix 秒的字串**
 *
 * 實測 `start_at` 是 `"1791782957"`，而**不是**常見的
 * `"2026-10-12 13:29:17"`。用 `SimpleDateFormat` 硬解會得到 1970 年
 *（或直接丟例外），界面上就是一個錯的時間 —— 比不顯示更糟。
 *
 * 這個站兩種格式都可能出現（不同介面來源），所以**兩種都試**。
 */

/**
 * 兩個時間字串 → `13:29-16:55`。
 *
 * 任一解析不出來就回 **null**（界面據此**不顯示時間**，
 * 而不是顯示一個編出來的或 1970 年的時間）。
 */
fun timeRange(startAt: String, endAt: String): String? {
    val s = parseTime(startAt) ?: return null
    val e = parseTime(endAt) ?: return null
    val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    return "${f.format(s)}-${f.format(e)}"
}

/**
 * Unix 秒字串（或 `yyyy-MM-dd HH:mm:ss`）→ [java.util.Date]。
 *
 * @return 解析不出來時 null。
 */
fun parseTime(raw: String): java.util.Date? {
    if (raw.isBlank()) return null
    raw.toLongOrNull()?.let { sec ->
        // 防禦性：> 10^11 視為毫秒（1791782957 秒 ≈ 2026 年；
        // 1791782957000 毫秒也是 2026 年，兩者都合理，靠量級區分）
        val ms = if (sec > 100_000_000_000L) sec else sec * 1000L
        return java.util.Date(ms)
    }
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(raw)
    }.getOrNull()
}
