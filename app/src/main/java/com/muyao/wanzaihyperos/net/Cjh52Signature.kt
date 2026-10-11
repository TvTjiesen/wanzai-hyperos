package com.muyao.wanzaihyperos.net

import java.security.MessageDigest
import java.util.concurrent.ThreadLocalRandom

/**
 * CJH52 的請求簽名（`X-BD` / `X-XW` / `X-QW`）。
 *
 * ### 算法從哪來
 *
 * 逐字讀自公開的 SPA bundle（`umi.9e29886a.js` 的 `60590` 模組）：
 *
 * ```js
 * K = function(path, body, appCode) {
 *   var Et = "$4holys**t";                       // 硬編碼鹽
 *   var Lr = Date.now();                          // 毫秒時間戳
 *   var Hr = body ? JSON.stringify(body) : "{}";
 *   var Vr = md5(Hr);                             // body 的 md5（hex）
 *   var dn = nanoid(16);                          // 16 位隨機（字母）
 *   var La = md5(Et + appCode + Vr + dn.substring(3,11) + Lr + path);
 *   return { "X-BD": dn, "X-XW": String(Lr), "X-QW": La };
 * }
 * ```
 *
 * ### 為什麼現在要實現它
 *
 * B0 曾得出「`X-QW` 的值不被校驗」的結論，但那個實驗用的是**已經被網頁用過一次**的碼。
 * 後續的跨環境對照顯示：
 *
 * ```
 * 網頁用過的碼 → JVM 用（不帶簽名）→ 200 ✅
 * 門戶新發的碼 → JVM 用（不帶簽名）→ 401 ❌
 * ```
 *
 * 所以更準確的假設是：**簽名只在「首次使用」時校驗**（順帶把碼註冊成會話）。
 * 這個類就是為了驗證該假設而寫 —— 若成立，原生直連只要首調帶簽名即可。
 *
 * ### 不做什麼
 *
 * 不把它當成「安全機制」：鹽是**公開**的（就在 bundle 裡），
 * 任何人都能算出同樣的簽名。它只是服務端的一個校驗步驟，不是加密。
 */
object Cjh52Signature {

    /** bundle 裡硬編碼的鹽。**公開值**，不是秘密。 */
    private const val SALT = "\$4holys**t"

    /**
     * nonce 的字符集：[A-Za-z]（52 個）。
     *
     * 逐字對應 bundle 的 `Ke` 函式默認字符集
     * `"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"`。
     */
    private const val NONCE_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    /** nonce 長度：bundle 裡調 `zs(16)`。 */
    private const val NONCE_LEN = 16

    /** nonce 用於簽名的切片：`dn.substring(3, 11)`。 */
    private const val NONCE_SLICE_FROM = 3
    private const val NONCE_SLICE_TO = 11

    data class Headers(val xBd: String, val xXw: String, val xQw: String) {
        fun asMap(): Map<String, String> = mapOf("X-BD" to xBd, "X-XW" to xXw, "X-QW" to xQw)
    }

    /**
     * 算出一次請求要帶的三個簽名頭。
     *
     * @param path 請求路徑（bundle 裡傳的就是它，如 `/dapi/v2/...`）
     * @param bodyJson 請求體的 **JSON 字串**（必須與實際發出的位元組一致，
     *   否則 md5 對不上）。null 或空 → 用 `{}`
     * @param appCode 租戶碼（學生端實測為 `"6"`）
     * @param timestampMillis 毫秒時間戳。由呼叫方傳入（本物件不讀時鐘，
     *   這樣單測可重現）。
     * @param nonce 16 位隨機字串。由呼叫方傳入（同樣為了可測）。
     */
    fun sign(
        path: String,
        bodyJson: String?,
        appCode: String,
        timestampMillis: Long,
        nonce: String,
    ): Headers {
        val body = bodyJson?.takeIf { it.isNotEmpty() } ?: "{}"
        val bodyMd5 = md5Hex(body)
        val slice = if (nonce.length >= NONCE_SLICE_TO) {
            nonce.substring(NONCE_SLICE_FROM, NONCE_SLICE_TO)
        } else {
            // nonce 太短時退化：取能取的部分（真實場景不會發生，但別拋）。
            nonce.drop(NONCE_SLICE_FROM)
        }
        val qw = md5Hex(SALT + appCode + bodyMd5 + slice + timestampMillis + path)
        return Headers(xBd = nonce, xXw = timestampMillis.toString(), xQw = qw)
    }

    /** 產生一個合乎 bundle 形態的 nonce。 */
    fun randomNonce(): String {
        val sb = StringBuilder(NONCE_LEN)
        val rnd = ThreadLocalRandom.current()
        repeat(NONCE_LEN) {
            sb.append(NONCE_ALPHABET[rnd.nextInt(NONCE_ALPHABET.length)])
        }
        return sb.toString()
    }

    /** md5 → 小寫 hex。 */
    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"
}
