package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 掃碼結果的**解析**（純函式，可單測）。
 *
 * ### 為什麼要有它，而不是直接把掃到的字串送去提交
 *
 * 學在城院的二維碼**不是**一個裸字串，而是這樣一層 JSON
 *（逐字讀自源碼的 `window.wx.scanQRCode` 回呼）：
 *
 * ```js
 * var o = JSON.parse(e.resultStr),
 *     i = o?.["scan_code"]?.["scan_result"];
 * t("string" === typeof i ? i : null)     // 取到就用它
 * // 解析失敗 → 用原字串
 * ```
 *
 * 也就是：**碼裡嵌著一層 JSON**，真正的簽到憑據在
 * `scan_code.scan_result`。直接把整串 JSON 送去簽到會被服務端拒。
 *
 * ### 三種情形（照源碼，一種都不能漏）
 *
 * | 掃到的內容 | 結果 |
 * |---|---|
 * | `{"scan_code":{"scan_result":"abc"}}` | `abc` |
 * | 不合法 JSON 的裸字串 `abc` | `abc`（原樣用） |
 * | JSON 合法但取不到 `scan_result` | null（**不猜**） |
 *
 * 第三種很容易被忽略：一個合法但結構不符的 JSON（例如別的二維碼），
 * 送去簽到只會失敗；回 null 讓上層說「這不是簽到碼」更誠實。
 */
object QrPayloadParser {

    /**
     * @return 真正要提交的憑據；解析不出來回 null。
     */
    fun extract(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        // 先試 JSON（碼裡嵌著一層）。
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        if (parsed is JsonObject) {
            val inner = parsed["scan_code"] as? JsonObject
            val value = inner?.get("scan_result")
            if (value is JsonPrimitive && value.isString) {
                val s = value.content.trim()
                return s.takeIf { it.isNotEmpty() }
            }
            // ⚠️ 是合法 JSON，但沒有 scan_result：
            //   這可能是別的二維碼，**不要**把整串 JSON 當憑據送去（一定失敗）。
            return null
        }

        // 不是 JSON → 原樣用（源碼就是這個行為）。
        return text
    }

    /**
     * 這串內容看起來是不是「學在城院的簽到碼」。
     *
     * 用來給使用者一句明確的話（「這不是簽到碼」比「簽到失敗」有用）。
     */
    fun looksLikeRollcallCode(raw: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return false
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        // 裸字串也可能是（舊格式），所以只要不是「合法但結構不符的 JSON」就算可能。
        if (parsed is JsonObject) {
            return (parsed["scan_code"] as? JsonObject)?.get("scan_result") != null
        }
        return true
    }
}
