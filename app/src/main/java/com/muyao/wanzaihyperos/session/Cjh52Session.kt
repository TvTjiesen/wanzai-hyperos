package com.muyao.wanzaihyperos.session

/**
 * CJH52（浙大城市学院场景管理）的会话。
 *
 * ### 存什么
 *
 * **就是门户签发的那枚 tempcode**（UUID，36 字元）。
 *
 * 这是 B1 的真機取證結論（`docs/B0-CJH52-CONTRACT.md` §2.1）：
 * URL 上的 `tempcode`、`token` 參數與 CJH52 自己的 `localStorage["token"]`
 * **三者完全相同**，而且把它直接當 `Authorization: Bearer` 用，
 * 服務端就當作已認證（實測回 517「查無用戶」而非 401）。
 *
 * 所以**不需要任何「換碼」步驟**。
 *
 * ### 為什麼單獨一個會話
 *
 * 與門戶、學在城院是**三套獨立的登入態**：
 *
 * | 系統 | 憑據 | 壽命 |
 * |------|------|------|
 * | 門戶 | `ACCESS_TOKEN`（32 位） | 可獨立續期 |
 * | 學在城院 | Keycloak JWT 對 | 有 refresh，可輪換 |
 * | **CJH52** | **tempcode（UUID）** | **有壽命但長度未驗，隨時可能失效** |
 *
 * 合成一個會話的後果是「其中一個過期把另外兩個也登出」，用戶沒理由接受。
 *
 * ### 憑據紀律
 *
 * tempcode **就是憑據**（拿到它即可讀你的課表與考勤）：
 * - 只進加密存儲，**絕不落普通文件、不進日誌**；
 * - 退出登錄時一併清除；
 * - [toString] 只報存在與否與長度，**不報值**。
 */
class Cjh52Session(store: SecureStore) : StoredSession(store) {

    companion object {
        /**
         * 會話鍵。**v1 後綴是格式版本**：換結構時加新鍵，
         * 舊鍵裡的壞資料只會讀成「沒有」，不會毒掉新資料。
         */
        const val KEY_TOKEN = "hzcu_cjh52_token_v1"

        /** 取得時間（毫秒），用於「太舊就先重取」的保守判斷（不是硬性到期）。 */
        const val KEY_OBTAINED_AT = "hzcu_cjh52_obtained_at_v1"

        /**
         * **是否已「激活」**（被真實瀏覽器載入過）。
         *
         * ### 為什麼需要單獨一個標記
         *
         * 實測（`docs/B1-WALKTHROUGH.md`）：門戶簽發的 tempcode
         * **直接當 Bearer 用會被拒**（HTTP 401），
         * 只有被**真實 WebView 載入過一次**的碼才可用。
         *
         * 所以「會話裡有碼」與「這個碼能用」是**兩件不同的事**。
         * 少了這個標記，程式就無法區分：
         * - 從門戶取來、還沒走過瀏覽器的碼（**不能用**）；
         * - 從瀏覽器讀回來、已激活的碼（**能用**）。
         *
         * 混淆兩者的後果是「閘門看到有 token 就放行 → 原生請求必定 401」，
         * 這個 bug 實際發生過一次。
         */
        const val KEY_ACTIVATED = "hzcu_cjh52_activated_v1"
    }

    @Volatile
    private var tokenValue: String? = null

    @Volatile
    private var obtainedAtValue: Long? = null

    @Volatile
    private var activatedValue: Boolean = false

    /** 當前 tempcode。**這是憑據**，只允許放進 `Authorization` 頭，禁止打印/記錄。 */
    fun token(): String? {
        ensureLoaded()
        return tokenValue
    }

    /**
     * 會話是否**可用** —— 有碼**且**已激活。
     *
     * ⚠️ 呼叫方要判斷「能不能發請求」時**必須用這個**，不要用 [hasToken]：
     * 未激活的碼發出去一定 401（見 [KEY_ACTIVATED] 的說明）。
     */
    val isUsable: Boolean get() = token() != null && isActivated()

    /** 有沒有碼（**不代表能用** —— 見 [isUsable]）。 */
    val hasToken: Boolean get() = token() != null

    /** 這枚碼是否被真實瀏覽器載入過。 */
    fun isActivated(): Boolean {
        ensureLoaded()
        return activatedValue
    }

    /** 取得時間（毫秒）；沒有就 null。 */
    fun obtainedAtMillis(): Long? {
        ensureLoaded()
        return obtainedAtValue
    }

    /**
     * 存下從門戶取到的 tempcode（**尚未激活**）。
     *
     * 存下來只是為了讓「取碼」與「激活」兩步之間不必傳參數；
     * **它還不能用** —— 要等 [markActivated]。
     *
     * @param nowMillis 取得時間。由調用方傳入（本類不讀時鐘）——
     *   這樣它在單測裡是確定的，也不會在重算時悄悄變值。
     */
    fun save(token: String, nowMillis: Long) {
        synchronized(lock) {
            persist(mapOf(
                KEY_TOKEN to token,
                KEY_OBTAINED_AT to nowMillis.toString(),
                KEY_ACTIVATED to "false",
            ))
            tokenValue = token
            obtainedAtValue = nowMillis
            activatedValue = false
            markLoaded()
            markCredentialReplacement()
        }
        notifyListeners()
    }

    /**
     * 標記「這枚碼已被真實瀏覽器載入過，現在能用了」。
     *
     * 由預熱流程（[com.muyao.wanzaihyperos.ui.web.Cjh52WarmUpGate]）在
     * 從 WebView 讀回 token 之後呼叫。
     */
    fun markActivated() {
        synchronized(lock) {
            ensureLoaded()
            persist(mapOf(KEY_ACTIVATED to "true"))
            activatedValue = true
        }
        notifyListeners()
    }

    /** 清掉會話（token 失效或退出登錄時）。 */
    fun clear() {
        synchronized(lock) {
            persist(mapOf(
                KEY_TOKEN to null,
                KEY_OBTAINED_AT to null,
                KEY_ACTIVATED to null,
            ))
            clearMemory()
            markLoaded()
        }
        notifyListeners()
    }

    override fun hasCredential(): Boolean = tokenValue != null

    override fun loadFrom(store: SecureStore) {
        tokenValue = store.read(KEY_TOKEN)?.takeIf { it.isNotBlank() }
        obtainedAtValue = store.read(KEY_OBTAINED_AT)?.toLongOrNull()
        activatedValue = store.read(KEY_ACTIVATED) == "true"
    }

    override fun clearMemory() {
        tokenValue = null
        obtainedAtValue = null
        activatedValue = false
    }

    /**
     * 只報長度，**不報值**。
     * 與 [TronclassSession.toString] 同一條紀律。
     */
    override fun toString(): String = "Cjh52Session(token=${tokenValue?.length ?: 0}字元)"
}
