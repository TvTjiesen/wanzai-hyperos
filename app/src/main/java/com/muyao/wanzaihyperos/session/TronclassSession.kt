package com.muyao.wanzaihyperos.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 学在城院（TronClass，含手机版）的会话。
 *
 * ### 存什么（T45 终版）
 *
 * **手机版的 identity token 对**（`accessToken` + `refreshToken`，Keycloak JWT）。
 * 它是 `mobile.hzcu.edu.cn/login-by-identity-token?accessToken=…` 的门票：
 * SPA 自己用它换 sessionID 并落 localStorage —— 真机实测落地 `/home`
 * （扫码/签到，钉钉同款）。
 *
 * T44 那版存的是 `courses` 会话 cookie —— 对**桌面版**有效，对手机版无效
 * （手机版不认 cookie）。所以 token 对才是硬通货。
 * 同时保留 SSO cookie（统一认证，token 失效时的免登备用通道）。
 *
 * ### 为什么与门户**分开存**
 * 两者是**同一套统一身份认证**（CAS）下的两个下游系统，但：
 * - 登录态的生命周期不同：门户是 `ACCESS_TOKEN`（32 位，可独立续期），
 *   学在城院是 Keycloak 签发的 JWT 对（有 refresh，可轮换）；
 * - 失效时机不同：其中一个被清掉时，另一个往往还有效；
 * - 键名不同：[PortalSession] 用 `hzcu_*`，这里用 `tronclass_*`。
 *
 * 合成一个会话的直接后果是「门户过期把学在城院也一起登出」，
 * 而用户完全没有理由接受这件事。所以**两边各自保存、各自更新、各自清除**。
 *
 * ### 凭据纪律
 *
 * JWT **就是凭据**（拿到 access_token 即可登录你的学在城院）：
 * - 只进加密存储，**绝不落普通文件、绝不进日志**；
 * - 退出登录时一并清除（见 [clear]）；
 * - [toString] 只报存在与否与长度，**不报值**。
 */
class TronclassSession(store: SecureStore) : StoredSession(store) {

    companion object {
        /**
         * identity token 对。**v1 后缀是格式版本**：换结构时加新键，
         * 旧键的坏数据只会读成「没有」，不会毒掉新数据。
         *
         * 与 Flutter 蓝本的 `hzcu_tronclass_tokens_v1` **同名不同库**：
         * 那边是 flutter_secure_storage 的文件，这边是我们的加密存储，
         * 不会互相覆盖，但「同一个用途就叫同一个名字」让排查时不会认错库。
         */
        const val KEY_TOKENS = "hzcu_tronclass_tokens_v1"

        /**
         * SSO cookie（统一认证，token 失效时的免登备用通道）。
         * 只存 `identity.hzcu.edu.cn` 与 `ca.hzcu.edu.cn` 两个域的。
         */
        const val KEY_SSO = "hzcu_tronclass_sso_v1"

        // ---- 以下为 T44 桌面版时代的旧键，仅用于「读不到新键时回看一眼」 ----
        // 注意：**不迁移**。T44 存的是 courses 会话 cookie，与手机版无关，
        // 搬过来也没用；而且静默迁移旧凭据等于替用户做决定。
        // 旧键留着只是为了 [clear] 时顺手清掉，不留残留。
        const val KEY_SESSION_ID = "tronclass_session_id"
        const val KEY_COOKIES = "tronclass_cookies"
        const val KEY_SAVED_AT = "tronclass_saved_at"
        const val KEY_DISPLAY_NAME = "tronclass_display_name"

        /** token 对所在的域。**JWT 只允许拼到这个 host**（见 TronclassBoot）。 */
        const val TARGET_HOST = "mobile.hzcu.edu.cn"

        /** 统一认证 SSO 的两个域（token 失效时的免登备用通道）。 */
        val SSO_HOSTS = listOf("identity.hzcu.edu.cn", "ca.hzcu.edu.cn")

        /**
         * 读 token 对。格式不对（坏数据/旧格式）→ null，**不抛** ——
         * 宁可让用户重新登录一次，也不要因为一份坏数据把首页拖崩。
         */
        fun readTokenPair(raw: String?): Pair<String, String?>? {
            if (raw.isNullOrBlank()) return null
            return try {
                val root = kotlinx.serialization.json.Json.parseToJsonElement(raw)
                    as? JsonObject ?: return null
                val access = (root["accessToken"] as? JsonPrimitive)
                    ?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
                val refresh = (root["refreshToken"] as? JsonPrimitive)
                    ?.contentOrNull?.takeIf { it.isNotBlank() }
                Pair(access, refresh)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * 写 token 对。**键名必须与 [readTokenPair] 一致**（`accessToken`/`refreshToken`），
         * 与 Flutter 蓝本同形，方便两边对照。
         */
        fun writeTokenPair(accessToken: String, refreshToken: String?): String =
            buildString {
                append("{\"accessToken\":")
                append(jsonString(accessToken))
                append(",\"refreshToken\":")
                append(if (refreshToken.isNullOrBlank()) "null" else jsonString(refreshToken))
                append("}")
            }

        /** 最小 JSON 字符串转义（只处理 token 里可能出现的字符，不做完整实现）。 */
        private fun jsonString(value: String): String = buildString {
            append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }

    @Volatile
    private var accessTokenValue: String? = null

    @Volatile
    private var refreshTokenValue: String? = null

    @Volatile
    private var ssoCookiesJsonValue: String? = null

    @Volatile
    private var savedAtMillisValue: Long? = null

    /** access_token。**这是凭据**，调用方只允许把它拼进目标 URL，禁止打印/记录。 */
    fun accessToken(): String? {
        ensureLoaded()
        return accessTokenValue
    }

    /** refresh_token。**这是凭据**，同上。 */
    fun refreshToken(): String? {
        ensureLoaded()
        return refreshTokenValue
    }

    /**
     * SSO cookie 的 JSON（host → {name: value}），只含 SSO_HOSTS 的。
     * ⚠️ **这是凭据**，只允许交给 WebView 的 CookieManager 或加密存储。
     */
    fun ssoCookiesJson(): String? {
        ensureLoaded()
        return ssoCookiesJsonValue
    }

    fun savedAtMillis(): Long? {
        ensureLoaded()
        return savedAtMillisValue
    }

    override fun hasCredential(): Boolean = accessTokenValue != null

    override fun loadFrom(store: SecureStore) {
        val pair = readTokenPair(store.read(KEY_TOKENS))
        accessTokenValue = pair?.first
        refreshTokenValue = pair?.second
        ssoCookiesJsonValue = store.read(KEY_SSO)?.takeIf { it.isNotBlank() }
        savedAtMillisValue = store.read(KEY_SAVED_AT)?.toLongOrNull()
    }

    override fun clearMemory() {
        accessTokenValue = null
        refreshTokenValue = null
        ssoCookiesJsonValue = null
        savedAtMillisValue = null
    }

    /**
     * 登录成功后落盘（token 对 + 可选的 SSO cookie）。
     *
     * accessToken 为空视为脏数据，**直接拒绝** —— 存进去只会让 SPA 的
     * `loginByAccessToken` 失败，还不如让调用方立刻发现。
     */
    fun saveTokens(accessToken: String, refreshToken: String?, ssoCookiesJson: String? = null) {
        val access = accessToken.trim()
        require(access.isNotEmpty()) { "TRONCLASS_ACCESS_TOKEN 为空，拒绝保存" }
        val now = System.currentTimeMillis()
        store.write(KEY_TOKENS, writeTokenPair(access, refreshToken))
        store.write(KEY_SAVED_AT, now.toString())
        if (ssoCookiesJson != null) store.write(KEY_SSO, ssoCookiesJson)
        synchronized(lock) {
            accessTokenValue = access
            refreshTokenValue = refreshToken?.takeIf { it.isNotBlank() }
            if (ssoCookiesJson != null) ssoCookiesJsonValue = ssoCookiesJson
            savedAtMillisValue = now
            markLoaded()
        }
        notifyListeners()
    }

    /**
     * 只更新 SSO cookie（免登链跑完后把统一认证会话收下来）。
     * 与 token 对分开存：语义不同（一个是手机版门票、一个是备用通道）。
     */
    fun saveSsoCookies(json: String) {
        if (json.isBlank()) return
        store.write(KEY_SSO, json)
        synchronized(lock) {
            ssoCookiesJsonValue = json
        }
    }

    /**
     * 学在城院会话失效：**只清这一边**，不动门户的 token。
     *
     * 连 T44 的旧键一起清：不留残留，哪怕那些值现在已经没用了。
     */
    fun clear() {
        store.delete(KEY_TOKENS)
        store.delete(KEY_SSO)
        store.delete(KEY_SESSION_ID)
        store.delete(KEY_COOKIES)
        store.delete(KEY_SAVED_AT)
        store.delete(KEY_DISPLAY_NAME)
        synchronized(lock) {
            clearMemory()
            markLoaded()
        }
        notifyListeners()
    }

    /** 只输出有没有与长度，绝不输出值。 */
    override fun toString(): String =
        if (accessTokenValue == null) "TronclassSession(state=$state, tokens=<无>)"
        else "TronclassSession(state=$state, access=<长度 ${accessTokenValue!!.length}>" +
            if (refreshTokenValue == null) ", 无refresh)" else ", 有refresh)"
}
