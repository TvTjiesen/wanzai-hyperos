package com.muyao.wanzaihyperos.ui.web

import android.webkit.CookieManager
import com.muyao.wanzaihyperos.net.HzcuCookieJar
import com.muyao.wanzaihyperos.net.HzcuEndpoints

/**
 * 把**我们自己的会话 cookie** 交给 WebView，让学校那些「需要 CAS 登录」的系统免登。
 *
 * ### 为什么需要它
 *
 * 门戶登入（A3）建立的是 **CAS 统一身份认证** 会话。学校有很多子系统
 * （一卡通 `etcard.hzcu.edu.cn`、图书馆 `libres.hzcu.edu.cn` …）
 * **共用这一套 CAS** —— 它们在浏览器里本来就是靠 CAS cookie 免登的。
 *
 * 但我们的 `WebShellPage` 从 A1 起就写着「不注入任何 token / cookie」
 * （当时还没有登录态，那条注释是对的）。结果是：**我们手里明明有 CAS 会话，
 * 却没有交给 WebView**，一卡通点进去直接停在登录页要求重新输密码。
 *
 * 真机实测（2026-10-11）：`一卡通` 打开后落在
 * `ca.hzcu.edu.cn/cas/login?service=…etcard…` —— 正是「没带 CAS cookie」的表现。
 *
 * ### 只注入什么（**这是红线**）
 *
 * **只注入 CAS / 统一认证域**（`ca.hzcu.edu.cn`、`identity.hzcu.edu.cn`）的 cookie。
 * 理由与 A4 的 `pickSsoCookies` 完全一致：
 *
 * - 这两个域是**统一认证本身**，它们的 cookie 语义就是「我已在浏览器里通过认证」，
 *   交给 WebView 是**按设计用途使用**；
 * - **绝不**把门户的 `ACCESS_TOKEN`、cjh52 的 token、学在城院的 token 对带过去 ——
 *   那些是**应用级凭据**，不该出现在别的系统的请求里。
 *
 * ### 只在打开学校域时注入
 *
 * 目标是外部网站时不注入（`isSchoolHost` 判定，与 A3 的 tempcode 同一道闸）。
 * 宁可某个外部站点需要重新登录，也不把学校会话交给它。
 *
 * ### 线程
 *
 * `CookieManager.setCookie` 必须在主线程调用（`WebView` 系列的要求）。
 * 本物件不自己切线程，而是**明确要求**主线程，避免以后被从后台线程误调。
 */
object WebViewCookieBridge {

    /**
     * 把 CAS 会话 cookie 注入 WebView 的 cookie 罐。
     *
     * @param jar 我们自己的 cookie 罐（门户登录后里面就有 CAS 会话）
     * @param targetUrl 即将在 WebView 里打开的地址
     * @return 实际注入的 cookie 条数（**只回数量，不回内容**）
     */
    fun injectSsoCookiesIfSchoolHost(jar: HzcuCookieJar, targetUrl: String): Int {
        val host = HzcuEndpoints.hostOf(targetUrl)?.lowercase()
        if (host.isNullOrEmpty() || !isSchoolHost(host)) {
            // 不是学校域：**一条都不注入**。宁可它要重新登录。
            return 0
        }

        val manager = CookieManager.getInstance()
        var injected = 0
        for (ssoHost in SSO_HOSTS) {
            // 用罐子既有的 API（namesFor + valueOf）而不是新增一個「整包取出」的方法：
            // 「按名字逐條取」讓**取到什麼**在代碼上一目了然 ——
            // 而一個 `cookiesFor(host)` 會把該域的全部 cookie 交出去，
            // 以後有人在那些域上存了別的東西，就會被靜默帶進 WebView。
            val names = jar.namesFor(ssoHost)
            // ★ 只記「這個域有幾個 cookie、叫什麼名字」——
            //   名字本身不是憑據（`CASTGC` 這個名字是公開的），值才是。
            //   沒有這條日誌，「注入 0 條」時無從判斷是「罐子是空的」
            //   還是「域名對不上」。
            android.util.Log.i(
                "WanzaiWeb",
                "注入檢查 host=$ssoHost 罐中 cookie 數=${names.size} 名稱=${names.joinToString(",")}",
            )
            for (name in names) {
                val value = jar.valueOf(ssoHost, name) ?: continue
                // 同時給 https 與 http：學校的子系統兩種協議都有
                // （一卡通是 http://…:8088，圖書館是 https）。
                val okHttps = runCatching {
                    manager.setCookie("https://$ssoHost", "$name=$value; path=/; domain=$ssoHost")
                }.isSuccess
                val okHttp = runCatching {
                    manager.setCookie("http://$ssoHost", "$name=$value; path=/; domain=$ssoHost")
                }.isSuccess
                if (okHttps || okHttp) injected++
            }
        }
        // flush：WebView 的 cookie 是异步落盘的，不 flush 的话
        // 「注入完立刻 loadUrl」有时会赶在写入之前（真机上表现为时好时坏）。
        runCatching { manager.flush() }
        return injected
    }

    /**
     * 是不是学校自己的域。**与 A3 的 tempcode 闸门同一判据**：
     * 后缀匹配 `hzcu.edu.cn`，不用 `contains`（那会被 `hzcu.edu.cn.evil.com` 骗过）。
     */
    fun isSchoolHost(host: String): Boolean {
        val h = host.lowercase().trim()
        if (h.isEmpty()) return false
        return h == "hzcu.edu.cn" || h.endsWith(".hzcu.edu.cn")
    }

    /**
     * 允许注入的域：**只有统一认证那两个**。
     *
     * 与 `TronclassSession.SSO_HOSTS` 保持一致（那一条是 A4 为学在城院定的），
     * 但这里**刻意再写一份**而不是引用它：这两个域的语义是「统一认证」，
     * 不该跟着某个业务会话（学在城院）的定义走 —— 哪天学在城院改了域，
     * 这里也不该跟着改。
     */
    val SSO_HOSTS = listOf("ca.hzcu.edu.cn", "identity.hzcu.edu.cn")
}
