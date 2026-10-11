package com.muyao.wanzaihyperos.ui.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder

/** 加载状态：进度 / 内容 / 失败。**不把失败显示成空白页**。 */
private sealed interface WebLoadState {
    data object Loading : WebLoadState
    data object Content : WebLoadState
    data class Failed(val message: String, val code: Int) : WebLoadState
}

/**
 * WebView 外壳：进度、页内返回、退出、失败重试。
 *
 * 方案要求覆盖的四种能力都在这一个页面里：
 * - **进度**：顶部 `LinearProgressIndicator`，`progress < 100` 时才显示；
 * - **页内返回**：系统返回键先交给 WebView（`canGoBack()`），退不到才退出本页；
 * - **退出**：顶栏左侧返回按钮 = 退出本页（不做危险操作，无需二次确认）；
 * - **失败与重试**：`onReceivedError` 只对**主框架**的请求报错，子资源失败不误报整页失败，
 *   失败页给「重试」按钮（用 `reload()` 而不是重新 load 原 URL，保留表单状态）。
 *
 * ⚠️ 凭据纪律：A1 **不注入任何 token / cookie**。
 * 将来 A3 要做 tempcode 换码时，必须按方案的「原始 URL 先验证，再做 webvpn 改写」顺序，
 * 绝不把凭据带到外部域名。
 *
 * ⚠️ 限流纪律：WebView 自己产生的请求**不受 OkHttp 限流器控制**（方案 §A2 明确要求
 * 不得宣称受控）。这里不做任何自动轮询。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebShellPage(
    title: String,
    url: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 我們自己的 cookie 罐。給了它，打開**學校域**時會把 CAS 統一認證 cookie
     * 注入 WebView，讓共用 CAS 的子系统（一卡通、圖書館…）免登。
     *
     * 為 null 表示不注入（走查/測試用）。
     * **只注入統一認證那兩個域**，詳見 [WebViewCookieBridge]。
     */
    cookieJar: com.muyao.wanzaihyperos.net.HzcuCookieJar? = null,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current

    var progress by remember { mutableFloatStateOf(0f) }
    var loadState by remember { mutableStateOf<WebLoadState>(WebLoadState.Loading) }
    var canGoBack by remember { mutableStateOf(false) }
    // 当前真实地址（页内跳转后用于「重新加载」与展示主机名）。
    var currentUrl by remember { mutableStateOf(url) }

    var webView by remember { mutableStateOf<WebView?>(null) }

    // 系统返回：优先让 WebView 退一步，退无可退才退出本页。
    BackHandler(enabled = canGoBack) {
        webView?.let { view ->
            if (view.canGoBack()) {
                view.goBack()
            } else {
                onExit()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 退出本页时释放 WebView，避免它继续在后台跑 JS / 持有会话。
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                destroy()
            }
            webView = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                SmallTopAppBar(
                    title = title,
                    subtitle = hostOf(currentUrl),
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(
                                imageVector = MiuixIcons.ChevronBackward,
                                contentDescription = "返回",
                                tint = colors.onBackground,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "重新加载",
                                tint = colors.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = onExit) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = "关闭",
                                tint = colors.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                if (progress < 100f) {
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        // ★ 打開學校域時，把 CAS 統一認證會話交給 WebView。
                        //
                        // 為什麼要這樣做：門戶登入建立的 CAS 會話我們**手裡就有**，
                        // 而學校有幾十個子系統共用它（一卡通、圖書館…）。
                        // 不注入的話，那些系統會停在 CAS 登入頁要求重新輸密碼
                        // —— 真機實測一卡通就是這樣。
                        //
                        // ⚠️ 只注入統一認證那兩個域的 cookie；**絕不**把門戶
                        // ACCESS_TOKEN / cjh52 token / 學在城院 token 對帶過去。
                        // 非學校域**一條都不注入**。詳見 [WebViewCookieBridge]。
                        cookieJar?.let { jar ->
                            val injected = runCatching {
                                WebViewCookieBridge.injectSsoCookiesIfSchoolHost(jar, url)
                            }.getOrDefault(0)
                            // 只記「注入了幾條」與是否學校域，**不記 cookie 名與值**
                            //（名與值都是憑據的一部分）。
                            android.util.Log.i(
                                "WanzaiWeb",
                                "cookie 注入：host=${com.muyao.wanzaihyperos.net.HzcuEndpoints.hostOf(url)} " +
                                    "條數=$injected",
                            )
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                loadState = WebLoadState.Loading
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() == true
                                if (url != null) currentUrl = url
                                progress = 100f
                                // 只有没被错误路径改写过才判定为内容，避免「先报错后完成」把失败页盖掉。
                                if (loadState !is WebLoadState.Failed) {
                                    loadState = WebLoadState.Content
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() == true
                                if (url != null) currentUrl = url
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                // 只把**主框架**失败当作整页失败；图片/CSS 等子资源失败不误报。
                                if (request?.isForMainFrame != true) return
                                val code = error?.errorCode ?: -1
                                loadState = WebLoadState.Failed(describeError(code), code)
                                progress = 100f
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                progress = newProgress.toFloat()
                            }
                        }
                        loadUrl(url)
                    }.also { webView = it }
                },
                // 不用在此处切 WebView 的 visibility：可见性交给下面那层"状态遮罩"处理
                // （见覆盖层注释）。切 visibility 在「重试」路径上会漏出 Chromium 自己的
                // 错误页（真机复现过：点重试后先闪一下 net::ERR_… 再回到我们的卡片）。
                update = { },
            )

            // ---- 状态遮罩 ---------------------------------------------------------
            // 只要不是 Content，就用一层**不透明**的全屏遮罩盖住 WebView。
            // 为什么必须不透明而不是「隐藏 WebView」：加载失败时 Chromium 会把它自己的
            // 错误页画在 WebView 上，隐藏/显示切换有时序窗口，用户会看到两块内容叠加
            // 或者闪一下系统错误页。用不透明遮罩从根上避免这种闪烁。
            if (loadState !is WebLoadState.Content) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background),
                ) {
                    when (val s = loadState) {
                        WebLoadState.Content -> Unit

                        WebLoadState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                                    color = colors.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "正在打开…",
                                    style = MiuixTheme.textStyles.body2,
                                    color = colors.onSurfaceVariantSummary,
                                )
                            }
                        }

                        is WebLoadState.Failed -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                StatePlaceholder(
                                    state = PageState.Error,
                                    detail = "打不开这个页面：${s.message}（错误码 ${s.code}）。\n" +
                                        "地址：${hostOf(currentUrl)}",
                                    onRetry = {
                                        // 先回到 Loading（不透明遮罩会盖住 WebView），
                                        // 再 reload；这样重试期间不会闪出 Chromium 自己的错误页。
                                        loadState = WebLoadState.Loading
                                        progress = 0f
                                        webView?.reload()
                                    },
                                )
                                // 若是「仅校园网可达」的入口，这里给出可操作的解释而不是空话。
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "如果这个入口需要校园网或 WebVPN，连上校园网后重试。" +
                                                "另有一些系统（如学在城院、CJH52）有自己的一套登录，" +
                                                "与本 App 的学校统一身份认证不互通，" +
                                                "即使网络可达也会要求先登录一次。",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = colors.onSurfaceVariantSummary,
                                        )
                                        TextButton(
                                            text = "退出并返回",
                                            onClick = onExit,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 从 URL 里取主机名用于顶栏副标题。取不到就显示原串（不假装解析成功）。 */
private fun hostOf(url: String): String = runCatching {
    java.net.URI(url).host ?: url
}.getOrDefault(url)

/** 把 WebView 的错误码翻成人话。 */
private fun describeError(code: Int): String = when (code) {
    WebViewClient.ERROR_HOST_LOOKUP -> "域名解析不了"
    WebViewClient.ERROR_CONNECT -> "连不上服务器"
    WebViewClient.ERROR_TIMEOUT -> "连接超时"
    WebViewClient.ERROR_UNKNOWN -> "未知错误"
    WebViewClient.ERROR_BAD_URL -> "地址格式不对"
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "SSL 握手失败"
    WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "不支持的地址协议"
    WebViewClient.ERROR_FILE_NOT_FOUND -> "页面不存在"
    else -> "加载失败"
}
