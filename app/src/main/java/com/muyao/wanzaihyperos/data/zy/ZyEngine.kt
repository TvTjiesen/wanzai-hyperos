package com.muyao.wanzaihyperos.data.zy

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume

/**
 * 智雲課堂的**引擎**：用一個（對使用者不可見的）WebView 維持會話並取資料。
 *
 * ## ★ 為什麼用 WebView 而不是 OkHttp（雖然它是標準 REST）
 *
 * 它的認證靠一批**域 cookie**（`.hzcu.edu.cn` 下）：`_token` / `JWTUser` /
 * `login_cmc_id` 等。而本專案的
 * [com.muyao.wanzaihyperos.net.HzcuCookieJar] 是**按 host 精確分桶**的
 *（那個設計是刻意的 —— 見它的類註釋：為了不讓 CAS 憑據跨系統漂移）。
 *
 * 結果就是：`livemobileapp` 的罐子裡**不會**有 `.hzcu.edu.cn` 的域 cookie，
 * 用 OkHttp 打這些介面會**未登入**。
 *
 * 兩條路：
 * 1. 另造一個「懂 domain 的」cookie 罐，從 WebView 的 CookieManager 灌進去；
 * 2. **讓 WebView 自己發請求**（域 cookie 由它自己管，天然正確）。
 *
 * 選 2。理由不只是省事：這條鏈路上**播放頁遲早要用 WebView**
 *（見 [ZyLiveInfo] 的註釋：觀看進度上報是學校認定學習進度的依據），
 * 既然那個會話已經在 WebView 裡，取數也用它最一致 —— 不必維護兩份登入態。
 *
 * ## 與 B18 教務系統引擎的關係
 *
 * 取數機制**照抄**（`evaluateJavascript` 不等 Promise，所以要「先寫 window、
 * 再輪詢取回」）。差別只在：這裡**不需要帳密**（沿用既有 CAS 會話）、
 * 也**不需要兩段登入**。所以沒有 `login()`。
 */
@SuppressLint("SetJavaScriptEnabled")
class ZyEngine(private val baseUrl: String = BASE) {

    private var webView: WebView? = null
    private var cleanupRegistration: AutoCloseable? = null
    private val work = com.muyao.wanzaihyperos.ui.web.CleanupWorkSet()

    private var nextFetchId = 0
    private var documentReady = false

    internal val reader: ZyReader = object : ZyReader {
        override suspend fun awaitAttached() = this@ZyEngine.awaitAttached()
        override suspend fun ensureSession(force: Boolean) = this@ZyEngine.ensureSession(force)
        override suspend fun fetchText(path: String) = this@ZyEngine.fetchText(path)
        override suspend fun invalidateSession() = withContext(Dispatchers.Main) { documentReady = false }
    }

    fun attach(view: WebView) {
        if (webView === view || com.muyao.wanzaihyperos.ui.web.WebViewCleanup.isStopped(view)) return
        if (webView != null) detach()
        webView = view
        view.settings.javaScriptEnabled = true
        view.settings.blockNetworkLoads = false
        cleanupRegistration = com.muyao.wanzaihyperos.ui.web.WebViewCleanup.register(this) { detach(permanent = true) }
    }

    fun detach(permanent: Boolean = false) {
        val view = webView
        webView = null
        documentReady = false
        cleanupRegistration?.close()
        cleanupRegistration = null
        work.cancelAll()
        view?.let { com.muyao.wanzaihyperos.ui.web.WebViewCleanup.stop(it, permanent) }
    }

    fun release(view: WebView) {
        try {
            if (webView === view) detach(permanent = true)
            else com.muyao.wanzaihyperos.ui.web.WebViewCleanup.stop(view)
        } finally { view.destroy() }
    }

    /**
     * 等到 WebView 被交進來為止。
     *
     * 與 B18 同一個坑：`AndroidView` 的 `factory`（呼叫 [attach] 的地方）
     * 在 **layout 階段**跑，而 `LaunchedEffect` 組合完就跑，順序**不保證**。
     * 少了這步，第一次進來時 `webView == null`，取數會失敗。
     */
    suspend fun awaitAttached(timeoutMs: Long = ATTACH_TIMEOUT_MS): Boolean = work.run { awaitAttachedActive(timeoutMs) }

    private suspend fun awaitAttachedActive(timeoutMs: Long): Boolean {
        if (webView != null) return true
        var waited = 0L
        while (waited < timeoutMs) {
            withContext(Dispatchers.Main) { kotlinx.coroutines.delay(ATTACH_POLL_MS) }
            waited += ATTACH_POLL_MS
            if (webView != null) return true
        }
        return false
    }

    private suspend fun ensureSession(force: Boolean): String = work.run {
        withContext(Dispatchers.Main) {
            val view = webView ?: throw ZyDomainFailure(ZyFailure.EngineNotReady)
            if (!force && documentReady && isZyOrigin(view.url, baseUrl)) {
                readUserId(view)?.let { return@withContext it }
            }
            documentReady = false
            navigate(view)
            // Storage provides a query ID only. The repository still verifies it with the read API.
            val id = withTimeoutOrNull(SESSION_TIMEOUT_MS) {
                var userId = readUserId(view)
                while (userId == null) {
                    delay(SESSION_POLL_MS)
                    userId = readUserId(view)
                }
                userId
            } ?: throw ZyDomainFailure(ZyFailure.IdentityUnavailable)
            documentReady = true
            id
        }
    }

    private suspend fun readUserId(view: WebView): String? {
        val raw = evalOnZy(view, "localStorage.getItem('yjUserInfo')") ?: return null
        val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        val data = obj?.get("data") as? JsonObject
        return (data?.get("id") as? JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() && it != "0" && it != "null" }
    }

    /**
     * 在頁面裡取一個 JSON 介面，回**原始文字**。
     *
     * ### ★★ 為什麼不能用「async IIFE + await」
     *
     * `WebView.evaluateJavascript` **不會等待 Promise** ——
     * 它求值完就把**當下的值**序列化回傳。所以
     * `(async () => { const r = await fetch(...); return await r.text(); })()`
     * 回來的是 **Promise 物件**，序列化成 `{}`。
     *
     * 症狀極具誤導性：JSON 解析「成功」但內容是空的
     * → 界面顯示「沒有資料」，而其實有 —— **那是替服務端撒謊**。
     *（這個坑在 B18 教務系統踩過一次，見 `docs/B18-WALKTHROUGH.md` §6.3。）
     *
     * 正確做法分兩步：
     * 1. 起 fetch，結果與完成旗標寫進 `window`；
     * 2. **輪詢**那個旗標，拿到才回。
     */
    private suspend fun fetchText(path: String): String = work.run {
        withContext(Dispatchers.Main) {
            val view = webView ?: throw ZyDomainFailure(ZyFailure.EngineNotReady)
            if (!documentReady || !path.startsWith("/") || path.startsWith("//")) {
                throw ZyDomainFailure(ZyFailure.EngineNotReady)
            }
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetchInDocument(view, path) }
                ?: throw ZyDomainFailure(ZyFailure.Timeout)
        }
    }

    private suspend fun fetchInDocument(view: WebView, path: String): String {
        val key = "__zyFetch_${nextFetchId++}"
        val kickoff = """
            (function(){
              var slot = window.$key = { result: null };
              var controller = new AbortController();
              var timer = setTimeout(function(){ controller.abort(); }, $FETCH_TIMEOUT_MS);
              fetch(${jsString(path)}, { credentials: 'same-origin', signal: controller.signal })
                .then(async function(r){
                  var text = r.ok && new URL(r.url).origin === location.origin ? await r.text() : null;
                  slot.result = { status: r.status, url: r.url, type: r.headers.get('Content-Type'), text: text };
                })
                .catch(function(e){ slot.result = { timeout: e.name === 'AbortError' }; })
                .finally(function(){
                  clearTimeout(timer);
                  setTimeout(function(){ delete window.$key; }, $FETCH_TIMEOUT_MS);
                });
              return 'started';
            })()
        """.trimIndent()
        if (evalOnZy(view, kickoff) != "started") throw ZyDomainFailure(ZyFailure.NoResponse)

        val probe = """
            (function(){
              var slot = window.$key;
              if (!slot || !slot.result) return null;
              delete window.$key;
              return JSON.stringify(slot.result);
            })()
        """.trimIndent()
        while (true) {
            delay(FETCH_POLL_MS)
            val raw = evalOnZy(view, probe) ?: continue
            return decodeZyFetch(raw, baseUrl)
        }
    }

    private suspend fun navigate(view: WebView) {
        val target = "$baseUrl/#/pages/liveCourse/index?tenant_code=$TENANT"
        var finished = false
        try {
            val failure = withTimeoutOrNull(NAVIGATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<NavigationResult> { cont ->
                    var startedUrl: String? = null
                    fun finish(failure: ZyFailure?) {
                        if (cont.isActive && webView === view) cont.resume(NavigationResult(failure))
                    }
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame || isZyNavigation(request.url.toString())) return false
                            finish(ZyFailure.NoResponse)
                            return true
                        }

                        override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                            startedUrl = url
                        }

                        override fun onPageFinished(v: WebView, url: String) {
                            if (startedUrl == null || view.url != url) return
                            when {
                                isZyLoginUrl(url, baseUrl) -> finish(ZyFailure.NotLoggedIn)
                                isZyOrigin(url, baseUrl) -> finish(null)
                                !isZyNavigation(url) -> finish(ZyFailure.NoResponse)
                            }
                        }

                        override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) finish(ZyFailure.NoResponse)
                        }

                        override fun onReceivedHttpError(v: WebView, request: WebResourceRequest, error: WebResourceResponse) {
                            if (request.isForMainFrame) finish(httpFailure(error.statusCode))
                        }

                        override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel()
                            if (error.url == startedUrl || error.url == target) finish(ZyFailure.NoResponse)
                        }
                    }
                    // Reload starts a real document even when the entry differs only by its hash.
                    if (view.url?.substringBefore('#') == target.substringBefore('#')) view.reload()
                    else view.loadUrl(target)
                }
            } ?: throw ZyDomainFailure(ZyFailure.Timeout)
            failure.failure?.let { throw ZyDomainFailure(it) }
            finished = true
        } finally {
            if (webView === view) {
                view.webViewClient = WebViewClient()
                if (!finished) view.stopLoading()
            }
        }
    }

    private data class NavigationResult(val failure: ZyFailure?)

    private fun isZyNavigation(url: String): Boolean = isZyOrigin(url, baseUrl) ||
        isZyOrigin(url, CAS_ORIGIN) || isZyOrigin(url, CALLBACK_ORIGIN)

    private fun checkZyDocument(view: WebView) {
        if (webView !== view) throw ZyDomainFailure(ZyFailure.EngineNotReady)
        if (isZyLoginUrl(view.url, baseUrl)) throw ZyDomainFailure(ZyFailure.NotLoggedIn)
        if (!isZyOrigin(view.url, baseUrl)) throw ZyDomainFailure(ZyFailure.NoResponse)
    }

    private suspend fun evalOnZy(view: WebView, script: String): String? {
        checkZyDocument(view)
        val result = withTimeoutOrNull(JS_TIMEOUT_MS) {
            suspendCancellableCoroutine<JsResult> { cont ->
                view.evaluateJavascript(script) { raw ->
                    if (cont.isActive) cont.resume(JsResult(decodeJsResult(raw)))
                }
            }
        } ?: throw ZyDomainFailure(ZyFailure.Timeout)
        checkZyDocument(view)
        return result.value
    }

    private data class JsResult(val value: String?)

    companion object {
        const val BASE = "https://livemobileapp.hzcu.edu.cn"

        /** 租戶 id（`/static/config.js` 的 `tenantId`，實測 21）。 */
        const val TENANT = "21"

        /** 等 WebView 交進來的上限。 */
        private const val ATTACH_TIMEOUT_MS = 5000L
        private const val ATTACH_POLL_MS = 100L

        private const val NAVIGATION_TIMEOUT_MS = 15_000L
        private const val JS_TIMEOUT_MS = 3_000L
        private const val SESSION_TIMEOUT_MS = 10_000L
        private const val SESSION_POLL_MS = 500L
        private const val FETCH_TIMEOUT_MS = 20_000L
        private const val FETCH_POLL_MS = 500L

        /**
         * 把 `evaluateJavascript` 的原始回傳解成字串。
         *
         * `null` / `undefined` 會被回成字串 `"null"`，統一轉成 null。
         * 解析不出來就**原樣回傳**（寧可讓上層看到奇怪內容，也不要靜默吞掉 ——
         * 那會變成「永遠沒有資料」這種最難查的症狀）。
         */
        internal fun decodeJsResult(raw: String?): String? {
            val text = raw ?: return null
            if (text == "null" || text.isEmpty()) return null
            return runCatching {
                when (val el = kotlinx.serialization.json.Json.parseToJsonElement(text)) {
                    is kotlinx.serialization.json.JsonPrimitive -> el.content
                    else -> el.toString()
                }
            }.getOrElse { text }
        }

        /** JS 字串字面值（含引號），轉義齊全。 */
        private fun jsString(v: String): String = buildString {
            append('"')
            for (ch in v) when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
            append('"')
        }
    }
}

internal interface ZyReader {
    suspend fun awaitAttached(): Boolean
    suspend fun ensureSession(force: Boolean): String
    suspend fun fetchText(path: String): String
    suspend fun invalidateSession()
}

private const val CAS_ORIGIN = "https://ca.hzcu.edu.cn"
private const val CALLBACK_ORIGIN = "https://yjapi.hzcu.edu.cn"

private fun parseUri(url: String?): URI? = url?.let { runCatching { URI(it) }.getOrNull() }

private fun isZyOrigin(url: String?, base: String): Boolean {
    val uri = parseUri(url) ?: return false
    val origin = parseUri(base) ?: return false
    if (uri.userInfo != null || !uri.scheme.equals("https", ignoreCase = true) || uri.host == null) return false
    return uri.host.equals(origin.host, ignoreCase = true) &&
        (if (uri.port == -1) 443 else uri.port) == (if (origin.port == -1) 443 else origin.port)
}

private fun isZyLoginUrl(url: String?, base: String): Boolean {
    val uri = parseUri(url) ?: return false
    return when {
        isZyOrigin(url, CAS_ORIGIN) -> uri.path.trimEnd('/') == "/cas/login"
        isZyOrigin(url, base) -> uri.path.trimEnd('/') == "/login" ||
            uri.fragment?.substringBefore('?')?.trimEnd('/') == "/pages/login/index"
        else -> false
    }
}

private fun httpFailure(status: Int): ZyFailure =
    if (status == 401) ZyFailure.NotLoggedIn else ZyFailure.Http(status)

internal fun decodeZyFetch(raw: String, base: String = ZyEngine.BASE): String {
    val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        ?: throw ZyDomainFailure(ZyFailure.NoResponse)
    if ((obj["timeout"] as? JsonPrimitive)?.content == "true") throw ZyDomainFailure(ZyFailure.Timeout)
    val status = (obj["status"] as? JsonPrimitive)?.content?.toIntOrNull()
        ?: throw ZyDomainFailure(ZyFailure.NoResponse)
    val url = (obj["url"] as? JsonPrimitive)?.content
    if (isZyLoginUrl(url, base)) throw ZyDomainFailure(ZyFailure.NotLoggedIn)
    if (!isZyOrigin(url, base)) throw ZyDomainFailure(ZyFailure.NoResponse)
    if (status !in 200..299) throw ZyDomainFailure(httpFailure(status))
    val type = (obj["type"] as? JsonPrimitive)?.content.orEmpty()
    val text = (obj["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw ZyDomainFailure(ZyFailure.NoResponse)
    if (type.startsWith("text/html", ignoreCase = true) || text.trimStart().startsWith('<')) {
        // An unrecognized HTML page could be a proxy or server error, not just login.
        throw ZyDomainFailure(ZyFailure.UnexpectedResponse)
    }
    return text
}
