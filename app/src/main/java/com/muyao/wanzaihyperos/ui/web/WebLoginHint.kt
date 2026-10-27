package com.muyao.wanzaihyperos.ui.web

/**
 * B4-05 的「一句实话」到底什么时候说、说什么。
 *
 * 背景（真机 2026-10-24 实测）：点「一卡通」后 WebView 的真实落地地址是
 * `ca.hzcu.edu.cn`（学校 CAS 登录表单），而旧版 [isSchoolCasHost] 只认
 * `etcard.hzcu.edu.cn` 与 `libres.hzcu.edu.cn` 两个域名 —— 于是
 * `onPageFinished` 在 CAS 域上走 else 分支，那句实话**一次都没显示过**。
 * 图书馆更糟：第一跳确实是 libres 才闪了一下，随后跳到 CAS 又被关掉。
 *
 * 所以判定必须看**真实落地的那一页**：
 *  - 落在 CAS 登录页  -> 学校正在要账密，说实话（按有没有过 CAS 票据区分"没登过"和"过期了"）
 *  - 落在站点自己的页 -> 只要本站 cookie 还在，就说明会话是通的，不说
 *  - 别的域          -> 不说（通知/公告/办事不需要登录，提示只会变噪音）
 *
 * 这里只影响"要不要提示"，**不影响登录**：本 App 绝不向 WebView 注入任何会话
 * cookie（B5 真机对照：注入后图书馆立刻 500，见 WebShellPage 档头注释）。
 */
internal object WebLoginHint {

    /** 业主点名要登录的两处学校站点（它们背后是学校 CAS）。 */
    private val SITE_HOSTS = setOf("etcard.hzcu.edu.cn", "libres.hzcu.edu.cn")

    /** 学校统一身份认证（CAS）登录表单所在域。 */
    private val CAS_HOSTS = setOf("ca.hzcu.edu.cn")

    /** CAS 的登录票据 cookie 名；它在不在，是"这台手机登过没有"的唯一本机证据。 */
    private const val CAS_TICKET_COOKIE = "CASTGC"

    /** 还没登过：原句，业主 2026-10-23 定的口径。 */
    const val TEXT_NEVER =
        "这台手机还没登录过这个网站：在网页里登录一次，之后免登录。（App 不替你输入账号密码）"

    /** 登过但学校那边过期了：不能沿用原句，否则是假话。 */
    const val TEXT_AGAIN =
        "学校那边的登录过期了：在网页里重新登录一次就好。（App 不替你输入账号密码）"

    fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")

    /** 学校站点自己的域（一卡通 / 图书馆）。 */
    fun isSchoolCasHost(url: String): Boolean = hostOf(url) in SITE_HOSTS

    /** 学校 CAS 的登录页所在域。 */
    fun isCasLoginPage(url: String): Boolean = hostOf(url) in CAS_HOSTS

    /**
     * 应当显示的提示文本；不该提示时返回 null。
     *
     * @param url 当前**真实**地址（页内跳转后的，不是入参那个）
     * @param siteCookies `CookieManager.getCookie(url)` 的原样返回
     * @param casCookies  `CookieManager.getCookie(casProbeUrl)` 的原样返回
     */
    fun decide(url: String, siteCookies: String?, casCookies: String?): String? {
        val hasTicket = !casCookies.isNullOrBlank() && casCookies.contains(CAS_TICKET_COOKIE + "=")
        return when {
            isCasLoginPage(url) -> if (hasTicket) TEXT_AGAIN else TEXT_NEVER
            isSchoolCasHost(url) -> if (siteCookies.isNullOrBlank()) {
                if (hasTicket) TEXT_AGAIN else TEXT_NEVER
            } else {
                null
            }
            else -> null
        }
    }

    /** 判定 CAS 票据该去哪个地址探。 */
    const val CAS_PROBE_URL = "https://ca.hzcu.edu.cn/cas/login"
}
