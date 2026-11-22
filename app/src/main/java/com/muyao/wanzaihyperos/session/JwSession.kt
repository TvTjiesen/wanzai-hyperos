package com.muyao.wanzaihyperos.session

/**
 * **教務系統**（`ijw.hzcu.edu.cn`，正方 ZFSoft）的會話。
 *
 * ## ★ 為什麼這裡要存**密碼**（全專案唯一一個）
 *
 * 其他三套系統都能「登入一次、長期有效」：
 *
 * | 系統 | 憑據 | 壽命 |
 * |------|------|------|
 * | 門戶 | `ACCESS_TOKEN` | 可續期 |
 * | 學在城院 | Keycloak JWT 對 | 有 refresh |
 * | CJH52 | tempcode | 較長 |
 * | 智慧公寓 | URL 上的 code | **可重複使用** |
 * | **教務系統** | **學號＋密碼** | **會話很短（`JSESSIONID`），且無 refresh** |
 *
 * 實測（`docs/B18-JW-PROBE.txt`）：它的登入**必須由真實 WebView 走它自己的
 * 處理器**才會產生 `JSESSIONID`（純 HTTP 打不出來）；
 * 而那個 session 過期後**沒有任何續期手段** —— 只能再用帳密登入一次。
 *
 * 所以「讓使用者每次過期都手動輸入一次密碼」在體驗上不可接受
 *（這是學生每天要看的考試與成績）。
 * 業主也已同意用其帳密調試。
 *
 * ## 憑據紀律（**比照最高標準**，因為這裡是明文密碼）
 *
 * - 只進**加密存儲**（[SecureStore]），**絕不落普通文件、絕不進日誌**；
 * - [toString] 只報「有沒有」與長度，**永不報值**；
 * - 退出登錄時**一併清除**；
 * - 界面不顯示它、不讓它進任何 URL 或例外消息。
 *
 * ## 與其他會話的關係
 *
 * 它是**第五套獨立登錄態**（門戶／學在城院／CJH52／智慧公寓／教務）。
 * 合成一個會話的後果是「其中一個過期把其他也登出」。
 */
class JwSession(store: SecureStore) : StoredSession(store) {

    companion object {
        /** 學號。**它單獨看不是憑據**（門戶也會用到），但仍只放加密存儲。 */
        const val KEY_ACCOUNT = "hzcu_jw_account_v1"

        /** 密碼。**這是憑據**，紀律最嚴。 */
        const val KEY_PASSWORD = "hzcu_jw_password_v1"

        /** 上次成功登入的時間（毫秒）。用於「太舊就先重登」，不是硬性到期。 */
        const val KEY_LAST_LOGIN = "hzcu_jw_last_login_v1"
    }

    @Volatile
    private var accountValue: String? = null

    @Volatile
    private var passwordValue: String? = null

    @Volatile
    private var lastLoginValue: Long? = null

    fun account(): String? {
        ensureLoaded()
        return accountValue
    }

    fun password(): String? {
        ensureLoaded()
        return passwordValue
    }

    fun lastLoginMillis(): Long? {
        ensureLoaded()
        return lastLoginValue
    }

    /** 有沒有可用的帳密（兩者都有才算）。 */
    val hasCredentialPair: Boolean get() = !account().isNullOrBlank() && !password().isNullOrBlank()

    fun save(account: String, password: String, nowMillis: Long) {
        synchronized(lock) {
            persist(mapOf(
                KEY_ACCOUNT to account,
                KEY_PASSWORD to password,
                KEY_LAST_LOGIN to nowMillis.toString(),
            ))
            accountValue = account
            passwordValue = password
            lastLoginValue = nowMillis
            markLoaded()
            markCredentialReplacement()
        }
        notifyListeners()
    }

    /** 只更新「上次成功登入」時間（登入流程成功後呼叫）。 */
    fun markLogin(nowMillis: Long) {
        synchronized(lock) {
            ensureLoaded()
            persist(mapOf(KEY_LAST_LOGIN to nowMillis.toString()))
            lastLoginValue = nowMillis
        }
        notifyListeners()
    }

    fun clear() {
        synchronized(lock) {
            persist(mapOf(
                KEY_ACCOUNT to null,
                KEY_PASSWORD to null,
                KEY_LAST_LOGIN to null,
            ))
            clearMemory()
            markLoaded()
        }
        notifyListeners()
    }

    override fun hasCredential(): Boolean = hasCredentialPair

    override fun loadFrom(store: SecureStore) {
        accountValue = store.read(KEY_ACCOUNT)?.takeIf { it.isNotBlank() }
        passwordValue = store.read(KEY_PASSWORD)?.takeIf { it.isNotBlank() }
        lastLoginValue = store.read(KEY_LAST_LOGIN)?.toLongOrNull()
    }

    override fun clearMemory() {
        invalidateCredential()
        accountValue = null
        passwordValue = null
        lastLoginValue = null
    }

    /** ⚠️ **只報長度**。這裡是明文密碼，洩漏的代價最高。 */
    override fun toString(): String =
        "JwSession(account=${accountValue?.length ?: 0}字元, password=${passwordValue?.length ?: 0}字元)"
}
