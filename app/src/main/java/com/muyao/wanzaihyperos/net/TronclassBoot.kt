package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.session.TronclassSession

/**
 * 学在城院手机版的**启动**逻辑（纯函数 + 薄封装，可单测）。
 *
 * ## 它怎么登录（实测自钉钉自己的缓存 + 逐字读它的 bundle）
 *
 * 手机版是 TronClass 官方 H5，登录态**不是 cookie**，而是
 * `localStorage["global"].{isLogin, sessionID, identityRefreshToken}`。
 * SPA 提供了一个**现成的 identity-token 登录路由**（chunk `489`
 * `login-by-identity-token`），读 URL 参数直接登录：
 *
 * ```text
 * /login-by-identity-token?accessToken=<JWT>&refreshToken=<JWT>&orgName=学在城院&area=cn
 *   → SPA 调 loginByAccessToken → POST /api/login?login=access_token
 *   → X-SESSION-ID 回填 localStorage.global → 完整登录态
 * ```
 *
 * 真机实测：落地 `/home` —— 扫码/签到 + 真实课程列表，**与钉钉完全一致**。
 *
 * ## **不做**的三件事
 *
 * 1. 不伪造钉钉 authCode、不手写 SPA 的 localStorage 结构 ——
 *    少一处漂移，就少一类「学校升级后打不开」。
 * 2. code 之后的兑换全部交给 SPA 自己做。我们既不碰 Keycloak token 的刷新流程，
 *    也不解析 JWT 的 payload（读 payload 等于把用户信息引进内存，多一处泄漏面）。
 * 3. 这个 URL **绝不进日志、不进导航状态、不进崩溃上报的参数** ——
 *    它完整包含 accessToken，打印一次就是一次泄漏。
 */
object TronclassBoot {

    /**
     * 拼 identity-token 启动 URL。
     *
     * ⚠️ **返回值就是凭据**：调用方只允许把它直接交给 WebView `loadUrl`，
     * 禁止打印、禁止拼进异常/日志、禁止存进导航状态。
     *
     * JWT 里只含 `A-Za-z0-9-_=.`（base64url + 点分隔），`encodeComponent` 后不变；
     * 但这里仍然显式编码 —— 哪天 Keycloak 换了 token 格式也不会静默拼出坏 URL。
     */
    fun identityTokenUrl(accessToken: String, refreshToken: String?): String {
        require(accessToken.isNotBlank()) { "accessToken 为空，拒绝拼启动 URL" }
        val refresh = refreshToken.orEmpty()
        return buildString {
            append("https://").append(HzcuEndpoints.TRONCLASS_MOBILE_HOST)
            append(HzcuEndpoints.TRONCLASS_MOBILE_IDENTITY_TOKEN_PATH)
            append("?accessToken=").append(encode(accessToken))
            append("&refreshToken=").append(encode(refresh))
            append("&orgName=").append(encode("学在城院"))
            append("&area=cn")
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    /**
     * 从 cookie 罐里**只挑 SSO 域**的 cookie（免登备用通道）。
     *
     * ★ 为什么只挑 `identity.hzcu.edu.cn` 与 `ca.hzcu.edu.cn`：
     * token 失效时走 SSO 免登链重新拿码，只需要统一认证那两个域的会话。
     * **CAS 的 `CASTGC`、Keycloak 的 `KEYCLOAK_*` 当时不进 WebView**（蓝本实测结论）——
     * WebView 里的 JS 可以读到非 HttpOnly cookie，多给一个域就多一处泄漏面。
     *
     * @return host → {name: value}，只含 [TronclassSession.SSO_HOSTS] 的。**这是凭据。**
     */
    fun pickSsoCookies(jar: HzcuCookieJar): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, Map<String, String>>()
        for (host in TronclassSession.SSO_HOSTS) {
            val names = jar.namesFor(host)
            if (names.isEmpty()) continue
            val bucket = LinkedHashMap<String, String>()
            for (name in names) {
                val value = jar.valueOf(host, name)
                if (!value.isNullOrEmpty()) bucket[name] = value
            }
            if (bucket.isNotEmpty()) out[host] = bucket
        }
        return out
    }

    /**
     * SSO cookie JSON 序列化（host → {name: value}）。
     * 与 [HzcuCookieJar.toJson] 同形，但**只含 SSO 域** —— 落盘的是过滤后的。
     */
    fun ssoCookiesToJson(picked: Map<String, Map<String, String>>): String {
        if (picked.isEmpty()) return "{}"
        return buildString {
            append("{")
            var firstHost = true
            for ((host, bucket) in picked) {
                if (!firstHost) append(",")
                firstHost = false
                append(jsonString(host)).append(":{")
                var first = true
                for ((name, value) in bucket) {
                    if (!first) append(",")
                    first = false
                    append(jsonString(name)).append(":").append(jsonString(value))
                }
                append("}")
            }
            append("}")
        }
    }

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
