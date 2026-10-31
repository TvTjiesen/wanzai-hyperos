package com.muyao.wanzaihyperos.session

/**
 * **数智体育**（`ty.hzcu.edu.cn`）的会话。
 *
 * ## ★ 为什么这里存的是 token + userId，而不是账号密码
 *
 * 这座系统**没有可用的账密登录**（实测 2026-10-31，真账号）：
 *
 * | 输入 | 服务端响应 |
 * |------|-----------|
 * | 真学号 + 真密码 | `-1 登录失败，使用统一身份认证账号密码登录！` |
 * | 真学号 + 乱写密码 | **一字不差**的同一条 |
 * | 假学号 + 假密码 | `-1 登录名不存在，请检查输入或者询问有关工作人员` |
 *
 * 对密码与错密码同响应 ⇒ 它把账密丢给学校统一身份认证，而那一跳现在一律拒。
 * 所以**不做账密代登**（做了也只是把一个必然失败的密码再交一次给学校）。
 *
 * 真正能用的是它自己发的那枚 `SPORT_API_…` token：
 * 用户在数智体育官方 App 里登录一次，token 就能在这边复用。
 *
 * ## token 之外还需要 userId
 *
 * 服务端**不按 token 认人，按请求体里的数字 userId 认人**（实测：
 * `{'userId':80000}` 成功，`{'userId':32500000}`（学号）失败，
 * `{'id':…}` / `{'stuNum':…}` / `{'bindId':…}` 全失败；
 * 而 token 里解不出 userId —— 它是 32 字节随机数，用 App 的 AES 常量解不开）。
 *
 * 所以这里存**两样**：token（凭据）+ userId（识别码）。
 *
 * ## 凭据纪律
 *
 * - 只进**加密存储**（[SecureStore]），绝不落普通文件、绝不进日志；
 * - [toString] 只报长度，**永不报值**；
 * - userId 不是凭据（它是学号之外的另一个数字编号），但仍与 token 放在一起。
 */
class SztySession(store: SecureStore) : StoredSession(store) {

    companion object {
        /** 数智体育发的 `SPORT_API_…`。**这是凭据**。 */
        const val KEY_TOKEN = "hzcu_szty_token_v1"

        /** 服务端认人用的数字 userId（本次账号是 80000）。 */
        const val KEY_USER_ID = "hzcu_szty_user_id_v1"

        /** 上次成功取数的时间（毫秒）。只用于界面上说「多久前取的」。 */
        const val KEY_LAST_SYNC = "hzcu_szty_last_sync_v1"

        /** 用户把 token 粘进来的时刻（毫秒）。账号页那一列要报的就是这个。 */
        const val KEY_SAVED_AT = "hzcu_szty_saved_at_v1"
    }

    @Volatile
    private var tokenValue: String? = null

    @Volatile
    private var userIdValue: Int? = null

    @Volatile
    private var lastSyncValue: Long? = null

    @Volatile
    private var savedAtValue: Long? = null

    fun token(): String? {
        ensureLoaded()
        return tokenValue
    }

    fun userId(): Int? {
        ensureLoaded()
        return userIdValue
    }

    fun lastSyncMillis(): Long? {
        ensureLoaded()
        return lastSyncValue
    }

    /**
     * 凭据是**什么时候存进来的**。
     *
     * ★ 与 [lastSyncMillis] 分开：那个每次取数成功都会变，账号页要报的是「他什么时候粘的」。
     */
    fun savedAtMillis(): Long? {
        ensureLoaded()
        return savedAtValue
    }

    /** 两样都有才算能查（token 是凭据，userId 是识别码，缺一个服务端都不认）。 */
    val hasCredentialPair: Boolean
        get() = !token().isNullOrBlank() && (userId() ?: 0) > 0

    fun save(token: String, userId: Int, nowMillis: Long) {
        tokenValue = token.trim()
        userIdValue = userId
        lastSyncValue = nowMillis
        savedAtValue = nowMillis
        store.write(KEY_TOKEN, tokenValue)
        store.write(KEY_USER_ID, userId.toString())
        store.write(KEY_LAST_SYNC, nowMillis.toString())
        store.write(KEY_SAVED_AT, nowMillis.toString())
        markLoaded()
        notifyListeners()
    }

    /** 只更新「上次成功取数」时间。 */
    fun markSync(nowMillis: Long) {
        lastSyncValue = nowMillis
        store.write(KEY_LAST_SYNC, nowMillis.toString())
        markLoaded()
        notifyListeners()
    }

    fun clear() {
        tokenValue = null
        userIdValue = null
        lastSyncValue = null
        savedAtValue = null
        store.delete(KEY_TOKEN)
        store.delete(KEY_USER_ID)
        store.delete(KEY_LAST_SYNC)
        store.delete(KEY_SAVED_AT)
        markLoaded()
        notifyListeners()
    }

    override fun hasCredential(): Boolean = hasCredentialPair

    override fun loadFrom(store: SecureStore) {
        tokenValue = store.read(KEY_TOKEN)?.takeIf { it.isNotBlank() }
        userIdValue = store.read(KEY_USER_ID)?.toIntOrNull()?.takeIf { it > 0 }
        lastSyncValue = store.read(KEY_LAST_SYNC)?.toLongOrNull()
        savedAtValue = store.read(KEY_SAVED_AT)?.toLongOrNull()
            ?: lastSyncValue
    }

    override fun clearMemory() {
        tokenValue = null
        userIdValue = null
        lastSyncValue = null
        savedAtValue = null
    }

    /** ⚠️ **只报长度**。token 是凭据，userId 是识别码，值一律不出现在字符串里。 */
    override fun toString(): String =
        "SztySession(token=${describe(tokenValue)}, userId=${if (userIdValue == null) "<无>" else "已存"}, lastSync=${lastSyncValue ?: 0})"
}
