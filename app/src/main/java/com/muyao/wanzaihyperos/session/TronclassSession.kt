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

        /**
         * `X-SESSION-ID`（courses 域的會話標識）。
         *
         * **複用上面那個 T44 舊鍵**：T44 存的就是 courses 會話標識，
         * 與現在 [TronclassClient] 換到的是**同一個東西的同一種用途** ——
         * 換個鍵名只會讓「同一份憑據有兩份存放」，多一處要清的方。
         * 舊值讀出來若不可用，服務端會回 401，客戶端據此重換 —— 不會出錯。
         */
        const val KEY_X_SESSION_ID = KEY_SESSION_ID

        /**
         * 這台設備的穩定標識（簽到包體裡的 `deviceId`）。
         *
         * 官方 H5 送的是 Capacitor 的 `Device.getInfo().uuid`
         * （`mobile.hzcu.edu.cn/js/1154.*.js`：`n={data:r.data,deviceId:D.Z.deviceId}`）。
         * 它**不是憑據**（只是設備指紋），所以 [clear] 不清它 —— 換賬號不等於換手機。
         */
        const val KEY_DEVICE_ID = "tronclass_device_id"

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

    /** `X-SESSION-ID`（courses 域會話）。**這是憑據**。 */
    @Volatile
    private var xSessionIdValue: String? = null

    /** 這台設備的簽到 `deviceId`。不是憑據，清了會讓服務端把同一台手機當成新設備。 */
    @Volatile
    private var deviceIdValue: String? = null

    /**
     * 讀快取的 `X-SESSION-ID`。
     *
     * ⚠️ 它**可能已經過期**（服務端沒告訴我們壽命）。呼叫方（[TronclassClient]）
     * 的策略是「先用它試，401 就重換」—— 所以這兒不做任何有效性假設。
     */
    fun xSessionId(): String? {
        ensureLoaded()
        return xSessionIdValue
    }

    /** 存 `X-SESSION-ID`（換到新的時由客戶端回呼）。**這是憑據**，只進加密存儲。 */
    fun saveXSessionId(sid: String) {
        if (sid.isBlank()) return
        synchronized(lock) {
            ensureLoaded()
            persist(mapOf(KEY_X_SESSION_ID to sid))
            xSessionIdValue = sid
        }
    }

    /**
     * 這台設備的簽到 `deviceId`（第一次用時生成並落加密存儲，之後一直是同一個）。
     *
     * 為什麼要它：官方掃碼簽到的包體是 `{"data":…,"deviceId":…}`
     * （`mobile.hzcu.edu.cn/js/1154.*.js` 的 `re` 函數逐字）。缺了它服務端回
     * `no_request_data`。生成後**永久保留**，換賬號也不換（那是同一台手機）。
     *
     * ### 存儲壞了**不許拋**
     *
     * 這個值不是憑據（洩出去也登不進任何東西），而它落在**簽到提交的最前面**：
     * 加密存儲一旦讀寫失敗（Keystore 失效、換 ROM、root），舊版會在這裡直接拋
     * `HzcuException.Storage`，於是**請求根本發不出去、連 debugDump 都留不下** ——
     * 真機上表現就是「按了確認，什麼都沒發生」（業主 2026-11-03 報的那次，
     * `files/` 裡連 `tron_raw_answer_qr.txt` 都沒有，正是這個形狀）。
     * 所以這裡降級：讀寫失敗就用一個**本次進程內穩定**的 uuid 頂上，
     * 讓簽到照樣發得出去；能不能長期穩定，比「發不出去」次要得多。
     */
    fun deviceId(): String {
        deviceIdValue?.let { return it }
        synchronized(lock) {
            deviceIdValue?.let { return it }
            val stored = runCatching { store.read(KEY_DEVICE_ID) }
                .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val value = stored ?: java.util.UUID.randomUUID().toString().also { fresh ->
                // 寫不進去就算了 —— 這個值不是憑據，丟了只是下次換一個 uuid。
                runCatching { store.write(KEY_DEVICE_ID, fresh) }
            }
            deviceIdValue = value
            return value
        }
    }

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
        xSessionIdValue = store.read(KEY_X_SESSION_ID)?.takeIf { it.isNotBlank() }
    }

    override fun clearMemory() {
        invalidateCredential()
        accessTokenValue = null
        refreshTokenValue = null
        ssoCookiesJsonValue = null
        xSessionIdValue = null
        savedAtMillisValue = null
    }

    /**
     * 新登录同批清旧 SID 并标记替换；只有显式 renewal 才保留 SID 和替换版本。
     *
     * accessToken 为空视为脏数据，**直接拒绝** —— 存进去只会让 SPA 的
     * `loginByAccessToken` 失败，还不如让调用方立刻发现。
     */
    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        ssoCookiesJson: String? = null,
        renewal: Boolean = false,
    ) {
        val access = accessToken.trim()
        require(access.isNotEmpty()) { "TRONCLASS_ACCESS_TOKEN 为空，拒绝保存" }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val sso = ssoCookiesJson ?: if (loaded) ssoCookiesJsonValue else store.read(KEY_SSO)
            val sid = if (renewal) {
                if (loaded) xSessionIdValue else store.read(KEY_X_SESSION_ID)?.takeIf { it.isNotBlank() }
            } else null
            persist(mapOf(
                KEY_TOKENS to writeTokenPair(access, refreshToken),
                KEY_SAVED_AT to now.toString(),
                KEY_SSO to sso,
            ) + (if (renewal) emptyMap() else mapOf(KEY_SESSION_ID to null)))
            accessTokenValue = access
            refreshTokenValue = refreshToken?.takeIf { it.isNotBlank() }
            ssoCookiesJsonValue = sso
            xSessionIdValue = sid
            savedAtMillisValue = now
            markLoaded()
            if (!renewal) markCredentialReplacement()
        }
        notifyListeners()
    }

    /**
     * 只更新 SSO cookie（免登链跑完后把统一认证会话收下来）。
     * 与 token 对分开存：语义不同（一个是手机版门票、一个是备用通道）。
     */
    fun saveSsoCookies(json: String) {
        if (json.isBlank()) return
        synchronized(lock) {
            ensureLoaded()
            persist(mapOf(KEY_SSO to json))
            ssoCookiesJsonValue = json
        }
    }

    /**
     * 学在城院会话失效：**只清这一边**，不动门户的 token。
     *
     * 连 T44 的旧键一起清：不留残留，哪怕那些值现在已经没用了。
     */
    fun clear() {
        synchronized(lock) {
            persist(mapOf(
                KEY_TOKENS to null,
                KEY_SSO to null,
                KEY_SESSION_ID to null,
                KEY_COOKIES to null,
                KEY_SAVED_AT to null,
                KEY_DISPLAY_NAME to null,
            ))
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
