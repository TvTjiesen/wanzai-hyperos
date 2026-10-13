package com.muyao.wanzaihyperos.session

/**
 * **智慧公寓**（`zhgy.hzcu.edu.cn`）的會話。
 *
 * ## 為什麼這個會話與其他三個都不一樣
 *
 * | 系統 | 憑據 | 怎麼取得 |
 * |------|------|---------|
 * | 門戶 | `ACCESS_TOKEN` | 學號密碼登入 |
 * | 學在城院 | Keycloak JWT 對 | CAS OIDC |
 * | CJH52 | tempcode（UUID） | 門戶簽發 |
 * | **智慧公寓** | **URL 上的一段 `code`（JWT）** | **只能由使用者自己貼** |
 *
 * 前三者 App 都能自己走完登入；**智慧公寓不行** ——
 * 原因見 `docs/B17-WALKTHROUGH.md`：
 *
 * - 門戶給的入口是 `/mob/zuccding/auth`，那是**釘釘免登**，
 *   授權碼由釘釘容器（JSAPI）簽發，瀏覽器裡拿不到；
 * - 該站沒有其他可取碼的接口（路徑掃描全 404，JS 裡也沒有取碼邏輯）。
 *
 * 而它的真正入口 `/mob/zuccindex.html?code=<JWT>` 是**可以用的**
 *（真機實測開出完整功能頁），而且那個 code **可重複使用**
 *（清掉 cookie 再用同一個 code，照樣進得去）。
 *
 * 所以設計成：**使用者貼一次連結 → 存在加密存儲 → 之後直接用**。
 *
 * ## 只存「碼」，不存整個連結
 *
 * 連結裡除 `code` 之外還有別的查詢參數（實測只有 `code`，但不能假設永遠如此）。
 * 存整個連結會把那些參數一起留著 —— 它們可能有時效或追蹤用途。
 * **只抽 `code` 出來存**，要開頁面時自己拼一個乾淨的 URL。
 *
 * ## 憑據紀律
 *
 * `code` **就是憑據**（拿到它即可讀你的報修記錄、內務分、生活繳費）：
 * - 只進加密存儲，**絕不落普通文件、不進日誌**；
 * - [toString] 只報存在與否與長度，**不報值**；
 * - 退出登錄時一併清除。
 */
class ZhgySession(store: SecureStore) : StoredSession(store) {

    companion object {
        /** 會話鍵。**v1 後綴是格式版本**（與其他會話同一慣例）。 */
        const val KEY_CODE = "hzcu_zhgy_code_v1"

        /** 取得時間（毫秒）。用於界面顯示「什麼時候設的」，不是硬性到期。 */
        const val KEY_OBTAINED_AT = "hzcu_zhgy_obtained_at_v1"

        /**
         * 入口頁面路徑。
         *
         * 實測這是唯一能帶 `code` 進去的頁面（`/mob/zuccding/auth` 是釘釘那條）。
         */
        const val ENTRY_PATH = "/mob/zuccindex.html"

        /** 從使用者貼上的東西裡把 `code` 抽出來。抽不到回 null。 */
        fun extractCode(input: String?): String? {
            val text = input?.trim().orEmpty()
            if (text.isEmpty()) return null

            // ★ 先分「這是不是一條網址」，再決定怎麼抽。
            //   一開始把「有沒有 query」當判據，於是**完全沒有 query 的網址**
            //   （例如 `http://zhgy.hzcu.edu.cn/mob/zuccindex.html`）
            //   會掉到下面「當成純碼」那條，整條網址被存成憑據 ——
            //   症狀是「打開後是錯誤頁」而完全查不出原因。
            //   測試 `空的與沒有 code 的都回 null` 抓到這個。
            // ⚠️ 判據**刻意保守**：不用「有沒有 `/`」——
            //   base64（含一般 base64 的 `+` `/`）可能剛好含斜線，
            //   那會把一段碼誤判成網址。JWT 用的是 base64url（`-` `_`），
            //   但不值得賭；只認真正像網址的形狀。
            val looksLikeUrl = text.startsWith("http://") ||
                text.startsWith("https://") ||
                text.contains("://") ||
                text.contains("hzcu.edu.cn") ||
                text.contains("/mob/")

            if (looksLikeUrl) {
                // 是網址：**只認 query 裡的 `code=`**，認不出就是沒認出（絕不退回整條網址）。
                val query = runCatching { java.net.URI(text).query }.getOrNull()
                    ?: text.substringAfter('?', "").takeIf { it.isNotBlank() }
                    ?: return null
                return query.split('&').firstNotNullOfOrNull { pair ->
                    val i = pair.indexOf('=')
                    if (i > 0 && pair.substring(0, i) == "code") pair.substring(i + 1) else null
                }?.takeIf { it.isNotBlank() }
            }

            // 不是網址：使用者只貼了碼本身（或 `code=xxx` 的片段）。
            //   不去猜它像不像 JWT（那會把「格式判斷」變成第二個會出錯的地方），
            //   只做最保守的清理：去掉可能的 `code=` 前綴。
            //   含空白的一律不認 —— 碼本身不會有空白，那多半是貼到了別的東西。
            if (text.contains(' ') || text.contains('\n')) return null
            return text.removePrefix("code=").takeIf { it.isNotBlank() }
        }

        /** 用 `code` 拼出入口網址。 */
        fun buildEntryUrl(code: String): String =
            "http://zhgy.hzcu.edu.cn$ENTRY_PATH?code=$code"
    }

    @Volatile
    private var codeValue: String? = null

    @Volatile
    private var obtainedAtValue: Long? = null

    /** 當前的 `code`。**這是憑據**，只允許拼進要載入的網址，禁止打印/記錄。 */
    fun code(): String? {
        ensureLoaded()
        return codeValue
    }

    /** 有沒有碼（＝設過入口）。 */
    val hasCode: Boolean get() = code() != null

    fun obtainedAtMillis(): Long? {
        ensureLoaded()
        return obtainedAtValue
    }

    /** 要載入的入口網址；沒設過回 null（呼叫方據此顯示輸入提示）。 */
    fun entryUrl(): String? = code()?.takeIf { it.isNotBlank() }?.let { buildEntryUrl(it) }

    fun save(code: String, nowMillis: Long) {
        codeValue = code
        obtainedAtValue = nowMillis
        store.write(KEY_CODE, code)
        store.write(KEY_OBTAINED_AT, nowMillis.toString())
        markLoaded()
        notifyListeners()
    }

    fun clear() {
        codeValue = null
        obtainedAtValue = null
        store.delete(KEY_CODE)
        store.delete(KEY_OBTAINED_AT)
        markLoaded()
        notifyListeners()
    }

    override fun hasCredential(): Boolean = codeValue != null

    override fun loadFrom(store: SecureStore) {
        codeValue = store.read(KEY_CODE)?.takeIf { it.isNotBlank() }
        obtainedAtValue = store.read(KEY_OBTAINED_AT)?.toLongOrNull()
    }

    override fun clearMemory() {
        codeValue = null
        obtainedAtValue = null
    }

    /** 只報長度，**不報值**。與其他會話同一條紀律。 */
    override fun toString(): String = "ZhgySession(code=${codeValue?.length ?: 0}字元)"
}
