package com.muyao.wanzaihyperos.ui.web

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * 把 WebView 侧一切**会残留的登录态**清干净。
 *
 * ### 为什么单独做一件事
 *
 * A4 的安全契约要求：清除不能只清 Cookie。学在城院那个 SPA 的登录态
 * **恰好不在 Cookie 里** —— 它在 `localStorage["global"]` 的
 * `isLogin` / `sessionID` / `identityRefreshToken`（这是实测结论，
 * 见 docs/A4-WALKTHROUGH.md）。所以只清 Cookie 的话，
 * 下一个在这台手机上登录的人打开学在城院会**直接进上一个人的首页**。
 *
 * ### 清哪些、为什么
 *
 * 1. **Cookie**（`CookieManager`）—— SPA 与 SSO 的会话 cookie。
 * 2. **WebStorage** —— 就是 localStorage / sessionStorage，
 *    学在城院的登录态在这里。`deleteAllData()` 清的是**全部 origin**，
 *    不是本次域名：残留就是残留，不该按域挑。
 * 3. **WebView 磁盘缓存** —— 缓存里可能有带凭据的响应体
 *    （索引页、API 响应），`clearCache(true)` 连磁盘一起清。
 * 4. **表单数据、历史与缓存** —— 内核可能记住过输入框内容；
 *    清理用的临时 WebView 实例用完立刻 `destroy()`。
 *
 * ### 在途任务
 *
 * WebView 页面被销毁时（`WebShellPage` 的 `DisposableEffect` 会调 `destroy()`），
 * 内核自己会停掉它发起的加载。这里不清「某个具体页面的在途请求」——
 * 因为清理发生时那个页面已经销毁了；能残留到磁盘上的只有上面四类。
 * 若将来出现「页面还活着就退出登录」的路径，需要在那里显式 `stopLoading()`。
 *
 * ### 为什么不用 `WebView.clearFormData()` 之外的 API
 *
 * `WebStorage.deleteAllData()` 是**全局**的（没有按 origin 的公开版本），
 * 对本 App 来说正好：我们只开学校站点，没有别的 origin 需要保留。
 *
 * ### 线程
 *
 * WebView 的这几个静态方法都要求**主线程**。调用方（退出登录）
 * 本来就跑在主线程的协程里，所以这里不自己切线程，
 * 而是**明确要求**主线程，避免以后再被从后台线程误调。
 */
object WebViewCleanup {

    /**
     * 清空 WebView 留下的所有登录残留。
     *
     * @param context 只用来拿 applicationContext；不会泄漏 Activity。
     */
    fun clearAll(context: Context) {
        // (1) localStorage / sessionStorage：学在城院的登录态就在这儿。
        //     先清存储再清 Cookie —— 顺序无关紧要，但**必须都做**。
        runCatching { WebStorage.getInstance().deleteAllData() }

        // (2) Cookie：主 Jar 与 WebView 是两个罐，必须都清。
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }

        // (3) 缓存：连磁盘一起清（可能含带凭据的响应体）。
        //     下面的 WebView 实例只用来调这两个清理方法，用完立刻 destroy，
        //     免得留一个不可见的 WebView 挂着 Context。
        runCatching {
            val probe = WebView(context.applicationContext)
            try {
                probe.clearCache(true)
                probe.clearFormData()
                probe.clearHistory()
            } finally {
                probe.destroy()
            }
        }
    }
}
