package com.muyao.wanzaihyperos.data.zy

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    /** 每次 fetch 一個唯一 key（見 [fetchJson] 的兩步做法）。 */
    private var nextFetchId = 0

    fun attach(view: WebView) {
        webView = view
    }

    fun detach() {
        webView = null
    }

    /**
     * 等到 WebView 被交進來為止。
     *
     * 與 B18 同一個坑：`AndroidView` 的 `factory`（呼叫 [attach] 的地方）
     * 在 **layout 階段**跑，而 `LaunchedEffect` 組合完就跑，順序**不保證**。
     * 少了這步，第一次進來時 `webView == null`，取數會失敗。
     */
    suspend fun awaitAttached(timeoutMs: Long = ATTACH_TIMEOUT_MS): Boolean {
        if (webView != null) return true
        var waited = 0L
        while (waited < timeoutMs) {
            withContext(Dispatchers.Main) { kotlinx.coroutines.delay(ATTACH_POLL_MS) }
            waited += ATTACH_POLL_MS
            if (webView != null) return true
        }
        return false
    }

    /** 目前頁面網址。 */
    suspend fun currentUrl(): String = evalJsDecoded("location.href").orEmpty()

    /**
     * 載入 SPA 首頁以建立／沿用會話，並回報**登入狀態**。
     *
     * 判據用 `localStorage.yjUserInfo`（實測：登入後才有，含 `id`／`tenant_id`）。
     * 不用「頁面上有沒有『退出登录』」那種文字判斷 —— 那會隨版面改動而失效。
     *
     * @return 已登入時回 `user_id`（取數要用）；未登入回 null。
     */
    suspend fun ensureSession(): String? {
        if (currentUrl().startsWith(baseUrl)) {
            // 已經在這個站上，不必重新載入（省一次白屏）
            readUserId()?.let { return it }
        }
        val loaded = load("$baseUrl/#/pages/liveCourse/index?tenant_code=$TENANT")
        if (!loaded) return null
        // 等 SPA 起來（Vue 掛載 + 它自己那幾個請求）
        repeat(SESSION_POLLS) {
            withContext(Dispatchers.Main) { kotlinx.coroutines.delay(SESSION_POLL_MS) }
            readUserId()?.let { return it }
        }
        return null
    }

    private suspend fun readUserId(): String? {
        val raw = evalJsDecoded("localStorage.getItem('yjUserInfo')") ?: return null
        return runCatching {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(raw)
                as? kotlinx.serialization.json.JsonObject ?: return null
            val data = obj["data"] as? kotlinx.serialization.json.JsonObject ?: return null
            val id = data["id"]
            when (id) {
                is kotlinx.serialization.json.JsonPrimitive -> id.content
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() && it != "0" }
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
    suspend fun fetchText(path: String): String? {
        val key = "__zyFetch_${nextFetchId++}"
        val kickoff = """
            (function(){
              window.$key = { done: false, text: null };
              fetch(${jsString(path)}, { credentials: 'include' })
                .then(function(r){ return r.text(); })
                .then(function(t){ window.$key = { done: true, text: t }; })
                .catch(function(e){ window.$key = { done: true, text: '__ERR__' + String(e).slice(0,120) }; });
              return 'started';
            })()
        """.trimIndent()
        if (evalJsDecoded(kickoff) != "started") return null

        repeat(FETCH_POLLS) {
            withContext(Dispatchers.Main) { kotlinx.coroutines.delay(FETCH_POLL_MS) }
            val probe = """
                (function(){
                  var r = window.$key;
                  if (!r) return '__PENDING__';
                  if (!r.done) return '__PENDING__';
                  delete window.$key;
                  return r.text === null ? '__ERR__no-text' : r.text;
                })()
            """.trimIndent()
            val out = evalJsDecoded(probe)
            if (out != null && out != "__PENDING__") return out
        }
        return null
    }

    /** 載入一個網址並等它載完。 */
    suspend fun load(url: String): Boolean = withContext(Dispatchers.Main) {
        val view = webView ?: return@withContext false
        suspendCancellableCoroutine { cont ->
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, u: String?) {
                    if (cont.isActive) cont.resume(true)
                }
            }
            view.loadUrl(url)
        }
    }

    private suspend fun evalJsDecoded(script: String): String? =
        withContext(Dispatchers.Main) {
            val view = webView ?: return@withContext null
            suspendCancellableCoroutine { cont ->
                view.evaluateJavascript(script) { raw ->
                    if (!cont.isActive) return@evaluateJavascript
                    cont.resume(decodeJsResult(raw))
                }
            }
        }

    companion object {
        const val BASE = "https://livemobileapp.hzcu.edu.cn"

        /** 租戶 id（`/static/config.js` 的 `tenantId`，實測 21）。 */
        const val TENANT = "21"

        /** 等 WebView 交進來的上限。 */
        private const val ATTACH_TIMEOUT_MS = 5000L
        private const val ATTACH_POLL_MS = 100L

        /** 等 SPA 起來（40 × 500ms = 20 秒）。 */
        private const val SESSION_POLLS = 40
        private const val SESSION_POLL_MS = 500L

        /** 取數輪詢（40 × 500ms = 20 秒）。 */
        private const val FETCH_POLLS = 40
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
