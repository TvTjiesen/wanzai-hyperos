package com.muyao.wanzaihyperos.session

import com.muyao.wanzaihyperos.net.PortalCredentials

/**
 * 门户（数字校园 / 移动校园）的会话。**全项目唯一碰门户凭据的地方。**
 *
 * 移植自 Flutter 蓝本 `lib/http/hzcu/hzcu_session.dart` 的 `HzcuSession`，
 * 键名逐字保留（`hzcu_access_token` 等），这样两个平台的排障笔记能互相对照。
 *
 * ### 存的四样东西，以及为什么 cookie 必须跟 token 一起存
 *
 * ★ 2026-10-09 真机抓包抓出来的 bug：门户的 `ACCESS_TOKEN` 是
 * **与 CAS 会话 Cookie 绑定**的。只发 token 不带 Cookie，服务端一律回
 * `{"code":401,"message":"token 不能为空"}` —— 它对「没带」和「带了但无效」
 * 不做区分（同一句话）。真机证据：
 * ```
 * POST /api/app/exclude/listappinfogroup/1 HTTP/1.1
 * content-length: 38
 * token=<32位>              ← 格式完全正确
 *                           ← ★ 但一个 Cookie 头都没有
 * → 401 {"code":401,"message":"token 不能为空"}
 * ```
 * 原先 cookie 罐是 CAS 登录方法的**局部变量**，登录结束即丢，
 * 所以后续所有门户请求都是裸请求。假服务器测试抓不到：
 * 当时让 token 独立生效，没模拟「token 必须配 cookie」这个真实约束。
 *
 * 存储形态：cookie 用 JSON（host → {name: value}），**同样是凭据**，
 * 所以与 token 同库（[EncryptedSecureStore.PREFS_FILE_NAME]，AES256-GCM），绝不进日志。
 */
class PortalSession(store: SecureStore) : StoredSession(store), PortalCredentials {

    companion object {
        const val KEY_ACCESS_TOKEN = "hzcu_access_token"
        const val KEY_USER_CODE = "hzcu_user_code"
        const val KEY_SAVED_AT = "hzcu_token_saved_at"
        const val KEY_PORTAL_COOKIES = "hzcu_portal_cookies"

        /** token 长度（实测 32）。凭据本身永不外泄，但长度可以进断言。 */
        const val ACCESS_TOKEN_LENGTH = 32
    }

    @Volatile
    private var token: String? = null

    @Volatile
    private var userCodeValue: String? = null

    @Volatile
    private var savedAtMillisValue: Long? = null

    @Volatile
    private var cookiesJson: String? = null

    // ---------------------------------------------------------------------------
    // 读
    // ---------------------------------------------------------------------------

    override fun accessToken(): String? {
        ensureLoaded()
        return token
    }

    override fun portalCookiesJson(): String? {
        ensureLoaded()
        return cookiesJson
    }

    /** 学号 / 工号。没有则 null。 */
    override fun userCode(): String? {
        ensureLoaded()
        return userCodeValue
    }

    /** token 落盘时间（毫秒）。用于算「登录多久了」。 */
    fun savedAtMillis(): Long? {
        ensureLoaded()
        return savedAtMillisValue
    }

    override fun hasCredential(): Boolean = token != null

    override fun loadFrom(store: SecureStore) {
        token = store.read(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }
        userCodeValue = store.read(KEY_USER_CODE)
        savedAtMillisValue = store.read(KEY_SAVED_AT)?.toLongOrNull()
        cookiesJson = store.read(KEY_PORTAL_COOKIES)
    }

    override fun clearMemory() {
        token = null
        userCodeValue = null
        savedAtMillisValue = null
        cookiesJson = null
    }

    // ---------------------------------------------------------------------------
    // 写
    // ---------------------------------------------------------------------------

    /**
     * 登录成功后同批落盘 token、用户、时间与 Cookie。null Cookie 保留已有值；
     * 登录出口应传入此次 CAS Cookie，不要拆成两次存储调用。空 token 直接拒绝。
     */
    fun save(accessToken: String, userCode: String? = null, portalCookiesJson: String? = null,
        extraChanges: Map<String, String?> = emptyMap(), notify: Boolean = true) {
        val clean = accessToken.trim()
        require(clean.isNotEmpty()) { "ACCESS_TOKEN 为空，拒绝保存" }
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val cookies = portalCookiesJson ?: if (loaded) cookiesJson else store.read(KEY_PORTAL_COOKIES)
            persist(mapOf(
                KEY_ACCESS_TOKEN to clean,
                KEY_USER_CODE to userCode,
                KEY_SAVED_AT to now.toString(),
                KEY_PORTAL_COOKIES to cookies,
            ) + extraChanges)
            token = clean
            userCodeValue = userCode
            savedAtMillisValue = now
            cookiesJson = cookies
            markLoaded()
        }
        if (notify) notifyListeners()
    }

    /**
     * 落盘 Cookie（与 token 同一把锁、同一个加密库）。
     *
     * 仅用于单独续期 Cookie；CAS 登录出口必须使用 [save] 同批保存 token 与 Cookie。
     */
    override fun savePortalCookies(json: String) {
        if (json.isBlank()) return
        synchronized(lock) {
            ensureLoaded()
            persist(mapOf(KEY_PORTAL_COOKIES to json))
            cookiesJson = json
        }
    }

    /**
     * 供网络层（[com.muyao.wanzaihyperos.net.PortalCredentials]）调用。
     *
     * 与公开的 [save] 是同一实现 —— 名字不同只因为 Kotlin **不允许覆写函数带默认参数**，
     * 而 [save] 的 `userCode` 有默认值（登录页确实常常拿不到学号）。
     */
    override fun saveAccessToken(accessToken: String, userCode: String?) = save(accessToken, userCode)

    override fun savePortalSession(accessToken: String, userCode: String?, portalCookiesJson: String) =
        save(accessToken, userCode, portalCookiesJson)

    /** 退出登录 / token 失效：内存与存储一起清。 */
    override fun clear() {
        synchronized(lock) {
            persist(mapOf(
                KEY_ACCESS_TOKEN to null,
                KEY_USER_CODE to null,
                KEY_SAVED_AT to null,
                KEY_PORTAL_COOKIES to null,
            ))
            clearMemory()
            markLoaded()
        }
        notifyListeners()
    }

    /** 只输出长度，绝不输出值。 */
    override fun toString(): String =
        "PortalSession(state=$state, token=${describe(token)}, userCode=${describe(userCodeValue)})"
}
