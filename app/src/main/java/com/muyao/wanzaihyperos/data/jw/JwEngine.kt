package com.muyao.wanzaihyperos.data.jw

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 教務系統的**引擎**：用一個（對使用者不可見的）WebView 維持會話，並在裡面取資料。
 *
 * ## ★ 為什麼一定要有 WebView，不能純 HTTP
 *
 * 這是真機實測的結論（`docs/B18-JW-PROBE.txt`），不是偏好：
 *
 * | 做法 | 結果 |
 * |------|------|
 * | 純 HTTP POST 登入（RSA 加密正確） | **失敗** —— 回應只有 `route` cookie，**沒有 `JSESSIONID`** |
 * | 用 WebView 載入登入頁、填欄位、觸發它自己的登入按鈕 | **成功** —— 產生 `JSESSIONID`，功能頁正常 |
 * | 之後在同一個 WebView 裡 `fetch` 資料介面 | **成功**，回 JSON |
 *
 * 也就是說：**它的登入處理器會做純 HTTP 做不到的事**（產生那個 session）。
 * 所以我們不重寫登入，而是**讓它自己的網頁做登入**，我們只負責觸發與讀結果。
 *
 * ## ★ 兩個登入上下文（少一個就只有首頁能看）
 *
 * ```
 * /xtgl/login_slogin.html          ← 入口首頁
 * /jwglxt/xtgl/login_slogin.html   ← 功能頁所在（**必須也登入**）
 * ```
 *
 * 只登入前者時，首頁正常（姓名／選單／通知都在），
 * 但**所有功能頁一律 302 回登入頁** —— 這是我卡了一整輪的原因。
 *
 * ## 執行緒
 *
 * `evaluateJavascript` 必須在**有 Looper 的執行緒**（主執行緒）呼叫，
 * 所以所有 WebView 操作都 `withContext(Dispatchers.Main)`。
 */
@SuppressLint("SetJavaScriptEnabled")
class JwEngine(private val baseUrl: String = BASE) {

    private var webView: WebView? = null

    /** 每次 fetch 一個唯一 key（見 [fetchText] 的兩步做法）。 */
    private var nextFetchId = 0

    /** 由 Compose 的 `AndroidView` 建立後交進來。 */
    fun attach(view: WebView) {
        webView = view
    }

    fun detach() {
        webView = null
    }

    val isAttached: Boolean get() = webView != null

    /**
     * 等到 WebView 被交進來為止。
     *
     * ### ★ 為什麼需要它（真機踩到的競態）
     *
     * `AndroidView` 的 `factory`（也就是 [attach] 被呼叫的地方）是在
     * **layout 階段**執行的，而 Compose 的 `LaunchedEffect` 在**組合完成後**就跑。
     * 兩者的順序**不保證** —— 實測 `LaunchedEffect` 先跑，
     * 於是 `engine.login()` 看到 `webView == null`，**靜靜地回 false**。
     *
     * 症狀是「一進頁面就顯示登入失效」，而且**連日誌都沒有**（因為根本沒進去），
     * 非常難查。等一下就解決。
     *
     * @return 等到回 true；逾時回 false（呼叫方據此報錯，而不是靜默失敗）。
     */
    suspend fun awaitAttached(timeoutMs: Long = ATTACH_TIMEOUT_MS): Boolean {
        if (webView != null) return true
        var waited = 0L
        while (waited < timeoutMs) {
            withContext(Dispatchers.Main) { kotlinx.coroutines.delay(ATTACH_POLL_MS) }
            waited += ATTACH_POLL_MS
            if (webView != null) return true
        }
        android.util.Log.w(TAG, "等不到 WebView（${timeoutMs}ms）")
        return false
    }

    /**
     * 載入一個網址並等它載完。
     *
     * ⚠️ 只等 `onPageFinished`。這個站的頁面會在載完後再發 XHR，
     * 所以呼叫方要自己留時間（見 [fetchText] 的說明）。
     */
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

    /** 目前頁面網址（用於判斷登入狀態）。 */
    suspend fun currentUrl(): String = evalJsDecoded("location.href").orEmpty()

    /**
     * 走**它自己的登入流程**。
     *
     * 三步（缺一不可）：
     * 1. 載入登入頁（讓它自己的 JS 與公鑰就緒）
     * 2. **填欄位並觸發它自己的登入按鈕**（純 HTTP 打不出 `JSESSIONID`）
     * 3. 等它導到 `index_initMenu.html` 才算成功
     *
     * @return **null 表示成功**；否則回一個能顯示給使用者看的失敗原因。
     *
     * ### ★ 為什麼回原因而不是布林
     *
     * 真機踩到：App 的 `Log` 輸出在這台機器上**拿不到**（HyperOS 過濾掉），
     * 所以「WebView 還沒就緒」「帳密錯」「表單找不到」若都只回 false，
     * 界面上會長得一模一樣（都是「登入已失效」），**根本沒法排查**。
     * 把原因帶出來同時解決了排查與體驗。
     */
    suspend fun login(account: String, password: String, loginPath: String = "/xtgl/login_slogin.html"): JwFailure? {
        val loaded = load(baseUrl + loginPath)
        if (!loaded) return JwFailure.PageNotLoaded(loginPath)
        // 等它的 JS 就緒（公鑰是 ajax 取的非同步）
        withContext(Dispatchers.Main) { kotlinx.coroutines.delay(1500) }

        // ★★ 關鍵：**先看有沒有已經登入**。
        //
        // 真機踩到：WebView 裡若已有有效的 session，請求登入頁會被
        // **302 到 `index_initMenu.html`** —— 那頁沒有 `#yhm`/`#mm`/`#dl`，
        // 於是接下來「找不到欄位」被誤判成「網站改版了」。
        //
        // 這種情況的正確答案是「已經登入，不必再登」，而不是失敗。
        // （而且它會發生在每一次「登入過之後再進來」——正是最常見的路徑。）
        if (currentUrl().contains("index_initMenu")) return null

        // ★ 填值 + 觸發它自己的 handler。
        //   送出的必須是 `hidMm`（頁面有兩個 name="mm"，真正送出的是隱藏那個），
        //   這件事由它自己的 handler 處理 —— 所以一定要按它的按鈕，不要自己組表單。
        val js = """
            (function(){
              try {
                var $ = window.jQuery;
                if (!$) return 'no-jquery';
                var y = document.querySelector('#yhm');
                var m = document.querySelector('#mm');
                var btn = document.querySelector('#dl');
                if (!y || !m || !btn) return 'no-fields';
                y.value = ${jsString(account)};
                m.value = ${jsString(password)};
                btn.click();
                return 'clicked';
              } catch (e) { return 'err:' + String(e).slice(0, 80); }
            })()
        """.trimIndent()
        val clicked = evalJsDecoded(js)
        // ⚠️ 只回「哪一步不成」，**絕不帶帳密**。
        if (clicked != "clicked") return JwFailure.FormNotFound(clicked.orEmpty().take(60))

        // 等跳轉（這個站的登入會做授權檢查，實測約 3~6 秒）
        repeat(LOGIN_POLLS) { attempt ->
            withContext(Dispatchers.Main) { kotlinx.coroutines.delay(LOGIN_POLL_MS) }
            val url = currentUrl()
            if (url.contains("index_initMenu")) return null
            // 還停在登入頁 → 失敗（密碼錯／次數限制），提早結束
            if (url.contains("login_slogin") && attempt > 8) return JwFailure.BadCredential
        }
        return if (currentUrl().contains("index_initMenu")) null else JwFailure.BadCredential
    }

    /**
     * 在頁面裡取一個資料介面，回**原始文字**（通常是 JSON）。
     *
     * ### ★★ 為什麼不能用「async IIFE + await」那套（真機踩到的）
     *
     * `WebView.evaluateJavascript` **不會等待 Promise**：
     * 它求值完就把**當下的值**序列化回傳。所以
     * `(async () => { const r = await fetch(...); return await r.text(); })()`
     * 回來的是一個 **Promise 物件**，序列化成 `{}`。
     *
     * 症狀極具誤導性：JSON 解析成功、`items` 是空的 →
     * 界面顯示「**沒有查到考試安排**」—— 而學校明明有 22 筆。
     * （第一版就是這樣；`docs/B18-WALKTHROUGH.md` 記了這個坑。）
     *
     * 正確做法分兩步（就是下面這樣）：
     * 1. 起一個 fetch，把結果與完成旗標寫進 `window`；
     * 2. 用 `evaluateJavascript` **輪詢**那個旗標，拿到才回。
     */
    suspend fun fetchText(path: String, formBody: String? = null): String? {
        val key = "__jwFetch_${nextFetchId++}"
        val fetchCall = if (formBody == null) {
            "fetch(${jsString(path)}, { credentials: 'include' }).then(r => r.text())"
        } else {
            """
            fetch(${jsString(path)}, {
              method: 'POST',
              headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                         'X-Requested-With': 'XMLHttpRequest' },
              credentials: 'include',
              body: ${jsString(formBody)}
            }).then(r => r.text())
            """.trimIndent()
        }
        val kickoff = """
            (function(){
              window.$key = { done: false, text: null };
              $fetchCall.then(function(t){ window.$key = { done: true, text: t }; })
                          .catch(function(e){ window.$key = { done: true, text: '__ERR__' + String(e).slice(0,120) }; });
              return 'started';
            })()
        """.trimIndent()
        if (evalJsDecoded(kickoff) != "started") return null

        // 輪詢取回（上限約 20 秒；這個站偶爾慢）
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

    /**
     * 求值一段 JS 並回**解碼後**的字串結果。
     *
     * ### ★ 為什麼一定要「解碼」
     *
     * `evaluateJavascript` 回傳的不是原始字串，而是**JSON 編碼後的字面值**：
     * 例如 JS 回 `{"code":1}`，Kotlin 收到的是 `"{\"code\":1}"`（外層帶引號、內層轉義）。
     *
     * 直接把那個字串丟給 `Json.parseToJsonElement` 會**解析失敗或解析成字串**。
     * 正確做法是用 JSON 解析器把它**還原成一個字串** ——
     * 這也是唯一能正確處理內層所有轉義（引號、換行、Unicode）的辦法；
     * 自己寫 replace 一定漏。
     *
     * （先前踩過：不回解碼就當 JSON 解析，結果 `items` 永遠是空的。）
     */
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
        const val BASE = "http://ijw.hzcu.edu.cn"

        /** 功能頁所在的登入上下文（**兩個都要登入**，見類註釋）。 */
        const val JWGLXT_LOGIN_PATH = "/jwglxt/xtgl/login_slogin.html"

        /** 日誌標籤。**只記流程與結果，永不記帳密**。 */
        private const val TAG = "WanzaiJw"

        /** 登入輪詢次數與間隔（20 × 700ms ≈ 14 秒上限）。 */
        private const val LOGIN_POLLS = 20
        private const val LOGIN_POLL_MS = 700L

        /** 等 WebView 交進來的上限（見 [awaitAttached]）。 */
        private const val ATTACH_TIMEOUT_MS = 5000L
        private const val ATTACH_POLL_MS = 100L

        /** 取數輪詢（40 × 500ms = 20 秒上限）。 */
        private const val FETCH_POLLS = 40
        private const val FETCH_POLL_MS = 500L

        /**
         * 把 `evaluateJavascript` 的原始回傳解成字串。
         *
         * `null` / `undefined` 會被回成字串 `"null"`，統一轉成 null。
         * 解析不出來就**原樣回傳**（寧可讓上層看到奇怪的內容，
         * 也不要靜默吞掉 —— 那會變成「永遠沒有資料」這種最難查的症狀）。
         */
        internal fun decodeJsResult(raw: String?): String? {
            val text = raw ?: return null
            if (text == "null" || text.isEmpty()) return null
            return runCatching {
                val el = kotlinx.serialization.json.Json.parseToJsonElement(text)
                when (el) {
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
