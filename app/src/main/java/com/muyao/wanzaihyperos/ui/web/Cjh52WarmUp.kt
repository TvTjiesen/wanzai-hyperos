package com.muyao.wanzaihyperos.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import java.net.URI
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive

/** Let the official SPA activate a fresh portal code without accepting an older storage value. */
@SuppressLint("SetJavaScriptEnabled")
internal suspend fun activateCjh52InWebView(
    context: Context,
    url: String,
    expected: String,
    lease: WebDisplayLease,
): Boolean = withContext(Dispatchers.Main) {
    if (!isCjh52Origin(url) || expected.isBlank() || !lease.isCurrent()) return@withContext false
    val view = WebView(context)
    var registration: AutoCloseable? = null
    try {
        registration = WebViewCleanup.register(view) { WebViewCleanup.stop(view) }
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        if (!awaitCjh52Document(view, url, lease)) return@withContext false
        val literal = JsonPrimitive(expected).toString()
        val script = """
            (function() {
                if (location.origin !== '${HzcuEndpoints.CJH52_BASE_URL}') return false;
                try {
                    var expected = $literal;
                    return localStorage.getItem('token') === expected || sessionStorage.getItem('token') === expected;
                } catch(e) { return false; }
            })()
        """.trimIndent()
        withTimeoutOrNull(5_000) {
            while (lease.isCurrent() && !WebViewCleanup.isStopped(view) && isCjh52Origin(view.url)) {
                val matches = withTimeoutOrNull(1_500) {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        view.evaluateJavascript(script) { value ->
                            if (continuation.isActive) continuation.resume(value == "true")
                        }
                    }
                } ?: return@withTimeoutOrNull false
                if (matches && lease.isCurrent() && isCjh52Origin(view.url)) return@withTimeoutOrNull true
                delay(250)
            }
            false
        } ?: false
    } finally {
        try { WebViewCleanup.stop(view) } finally {
            registration?.close()
            view.destroy()
        }
    }
}

private suspend fun awaitCjh52Document(view: WebView, url: String, lease: WebDisplayLease): Boolean =
    withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine { continuation ->
            fun finish(success: Boolean) {
                if (continuation.isActive) continuation.resume(success)
            }
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val allowed = lease.isCurrent() && isCjh52Origin(request.url.toString())
                    if (!allowed) finish(false)
                    return !allowed
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (view.url == url) finish(lease.isCurrent() && isCjh52Origin(url))
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) finish(false)
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, error: WebResourceResponse) {
                    if (request.isForMainFrame) finish(false)
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    if (error.url == view.url || error.url == url) finish(false)
                }
            }
            if (!lease.publish { view.loadUrl(url) }) finish(false)
        }
    } ?: false

internal fun isCjh52Origin(url: String?): Boolean {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    return uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null &&
        uri.host.equals(HzcuEndpoints.CJH52_HOST, ignoreCase = true) && (uri.port == -1 || uri.port == 443)
}
