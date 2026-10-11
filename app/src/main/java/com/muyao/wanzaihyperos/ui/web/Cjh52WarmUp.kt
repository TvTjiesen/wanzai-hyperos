package com.muyao.wanzaihyperos.ui.web

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * CJH52 的「**預熱**」WebView —— B1 那個會話問題的解法。
 *
 * ### 為什麼需要它（實測結論，不是猜的）
 *
 * 門戶簽發的 tempcode，**直接當 Bearer 用會被拒**（HTTP 401）。
 * 但若先讓**真實的 WebView 載入一次**帶該碼的頁面，同一枚碼就**立刻可用**了。
 *
 * 跨環境對照（`tools/cdp-exporttoken.js` + `Cjh52WebTokenCrossTest`）：
 *
 * ```
 * 網頁載入過的碼 → 交給 JVM(OkHttp) 調 → HTTP 200 code=200   ✅
 * 門戶新發的碼   → 交給 JVM(OkHttp) 調 → HTTP 401             ❌
 * ```
 * 同一台機器、同一個 OkHttp、同一組請求頭。
 *
 * 且已**逐項排除**（每個都做過對照實驗）：
 * 簽名的值與存在性、`App-Code`、`platform`、`lang`、cookie、`Origin`、`Referer`、
 * `Accept`、`Accept-Language`、`User-Agent`、`Accept-Charset`、
 * **HTTP 版本**（h2 / http/1.1 都 401）、**取碼參數**（5 種取法都 401）、
 * **正確簽名**（`Cjh52Signature`）、
 * **用 OkHttp 帶碼 GET 一次頁面**（只拿到 482 位元組的 SPA 空殼，不執行 JS）。
 *
 * → 關鍵在「**真實瀏覽器載入並執行了頁面的 JS**」本身。
 * 不去猜服務端的具體規則，而是**照著做**：開一次真的 WebView。
 *
 * ### 設計
 *
 * - **尺寸為 0**：不佔版面（整個過程只顯示一句「正在準備」）。
 * - **只做一次**：拿到碼就回呼；之後原生頁直接用它。
 *   碼有壽命，失效時（原生收到 401）再預熱一次。
 * - **用完立刻銷毀**：不讓它留在記憶體裡。
 *
 * ### ⚠️ 已知代價（如實記錄）
 *
 * 這個 WebView 會讓 SPA 自己發一輪請求（它本來就會），
 * 而**那些請求不受我們的限流閘門控制** —— 與既有 WebView 路徑相同，
 * 專案裡已明確要求「不得宣稱 WebView 流量受控」（見 [WebShellPage] 的註解）。
 * 所以預熱只在**必須**時做一次，不隨每次刷新重做。
 *
 * ### 憑據紀律
 *
 * 讀到的是 CJH52 的會話 token（**憑據**）：
 * 只用來回呼、由呼叫方存進加密存儲；**本檔案不打印、不落盤**。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Cjh52WarmUp(
    /** 帶 tempcode 的目標 URL。 */
    url: String,
    /** 拿到 token 時回呼（**這是憑據**，呼叫方要立刻存進加密存儲）。 */
    onToken: (String) -> Unit,
    /** 逾時或失敗時回呼，讓上層能給使用者一個說法。 */
    onFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberUpdatedState：WebView 裡的閉包永遠看到最新的回呼，
    // 不必因為回呼換了新 instance 就重建 WebView。
    val latestOnToken by rememberUpdatedState(onToken)
    val latestOnFailure by rememberUpdatedState(onFailure)

    val holder = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                destroy()
            }
            holder[0] = null
        }
    }

    Box(modifier = modifier.size(0.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // ★ 直接在工廠閉包裡捕獲回呼，並把「輪詢」寫成局部函式。
                //   先前想用 View.tag / WeakHashMap 存回呼，都太繞且容易失效 ——
                //   閉包本來就能帶住它，不需要額外的容器。
                fun poll(view: WebView, attempt: Int) {
                    if (attempt > MAX_ATTEMPTS) {
                        latestOnFailure()
                        return
                    }
                    view.evaluateJavascript(READ_TOKEN_JS) { raw ->
                        val token = raw
                            ?.trim()
                            ?.removeSurrounding("\"")
                            ?.takeIf { it.isNotEmpty() && it != "null" }
                        if (token != null) {
                            latestOnToken(token)
                        } else {
                            view.postDelayed({ poll(view, attempt + 1) }, INTERVAL_MS)
                        }
                    }
                }

                WebView(ctx).apply {
                    holder[0] = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            super.onPageFinished(view, finishedUrl)
                            // 頁面的 JS 已經跑過一輪。但 SPA 是非同步的：
                            // onPageFinished 只代表「文件載完」，token 可能還沒寫進 storage，
                            // 所以**輪詢**幾次而不是只讀一次。
                            view?.let { poll(it, attempt = 0) }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            // 只對主框架報錯；子資源失敗不代表整頁失敗
                            // （與 WebShellPage 的判據保持一致）。
                            if (request?.isForMainFrame == true) latestOnFailure()
                        }
                    }
                    loadUrl(url)
                }
            },
        )
    }
}

private const val READ_TOKEN_JS =
    "(function(){try{return localStorage.getItem('token')||sessionStorage.getItem('token')||'';}catch(e){return '';}})()"

/** 最多輪詢次數與間隔：12 × 200ms ≈ 2.4 秒。**不無限等**。 */
private const val MAX_ATTEMPTS = 12
private const val INTERVAL_MS = 200L
