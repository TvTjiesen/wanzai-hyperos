package com.muyao.wanzaihyperos.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.ValueCallback
import android.graphics.Bitmap
import android.webkit.PermissionRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.muyao.wanzaihyperos.ui.components.WanzaiOverlayDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.Type
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import com.muyao.wanzaihyperos.ui.components.WanzaiTextButton as TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder

/** 加载状态：进度 / 内容 / 失败。**不把失败显示成空白页**。 */
private sealed interface WebLoadState {
    data object Loading : WebLoadState
    data object Content : WebLoadState
    data class Failed(val message: String, val code: Int) : WebLoadState
}

private data class PendingWebCapture(
    val request: PermissionRequest,
    val ticket: WebPermissionGate.Ticket,
)

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
    /**
     * 可選的「清除憑據」出口（B17）。
     *
     * 只有**需要使用者提供憑據**的頁面才傳（目前是智慧公寓閘門）。
     * 一般 WebView 不需要 —— 它們的登入態在網頁自己那邊。
     */
    onClearCredential: (() -> Unit)? = null,
    /**
     * 顶栏副标题。默认显示**当前地址的主机名** —— 这是本页的常态口径：
     * 让使用者随时看得见「我现在看的是哪个域的页面」。
     *
     * 只有**回放页**这种「地址背了 ID、但使用者更想知道是哪门课」的场景才覆盖它
     * （v2.1 稿：`设计史 · 官方播放出口`）。默认值仍是主机名，其它调用点不受影响。
     */
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    loadInitialUrl: (WebView, String) -> Boolean = { view, address -> view.loadUrl(address); true },
    publishWebAction: (() -> Unit) -> Boolean = { action -> action(); true },
    onReload: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var pageStarted by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var cleanupRegistration by remember { mutableStateOf<AutoCloseable?>(null) }
    var boundaryStopped by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(url) }
    val permissionGate = remember { WebPermissionGate() }
    var pendingWebPermission by remember { mutableStateOf<PendingWebCapture?>(null) }
    var permissionConfirmation by remember { mutableStateOf<PendingWebCapture?>(null) }
    // Keep the launched ticket until its result arrives, even after navigation cancels it.
    var launchedWebPermission by remember { mutableStateOf<PendingWebCapture?>(null) }

    fun denyPendingCapture() {
        val pending = pendingWebPermission
        pendingWebPermission = null
        permissionConfirmation = null
        permissionGate.invalidate()
        pending?.request?.deny()
    }

    fun completeCapture(launched: PendingWebCapture) {
        if (boundaryStopped) { denyPendingCapture(); launched.request.deny(); return }
        // Navigation may have cancelled this capture while the system dialog was open.
        // The old request must still be answered: denying it here is the only safe end.
        if (pendingWebPermission !== launched) { launched.request.deny(); return }
        val granted = launched.ticket.resources.filter { resource ->
            ContextCompat.checkSelfPermission(context, androidPermissionOf(resource)) ==
                PackageManager.PERMISSION_GRANTED
        }.toSet()
        val allowed = permissionGate.finish(
            launched.ticket.id, webView?.url.orEmpty(), pageStarted, granted,
        )
        pendingWebPermission = null
        permissionConfirmation = null
        if (allowed.isEmpty()) launched.request.deny()
        else launched.request.grant(allowed.map(::webResourceOf).toTypedArray())
    }

    val webPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val launched = launchedWebPermission
        launchedWebPermission = null
        if (launched != null) {
            if (permissionGate.endSystemRequest(launched.ticket.id)) completeCapture(launched)
            else launched.request.deny()
        }
    }

    fun confirmCapture(confirmation: PendingWebCapture) {
        if (boundaryStopped) { denyPendingCapture(); return }
        if (pendingWebPermission !== confirmation || launchedWebPermission != null) return
        permissionConfirmation = null
        if (!permissionGate.confirm(confirmation.ticket.id, webView?.url.orEmpty(), pageStarted)) {
            denyPendingCapture()
            return
        }
        val missing = confirmation.ticket.resources.map(::androidPermissionOf).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            completeCapture(confirmation)
        } else {
            if (!permissionGate.startSystemRequest(confirmation.ticket.id)) {
                denyPendingCapture()
                return
            }
            launchedWebPermission = confirmation
            runCatching { webPermissionLauncher.launch(missing.toTypedArray()) }.onFailure {
                launchedWebPermission = null
                permissionGate.endSystemRequest(confirmation.ticket.id)
                denyPendingCapture()
            }
        }
    }

    // 網頁要「選檔案上傳」（請假附件、報修照片、作業提交）時走的通道。
    //
    // 為什麼必須接：不覆寫 onShowFileChooser 時，頁面裡的 <input type="file"> 點了
    // **什麼都不會發生** —— 沒有報錯、沒有對話框，用戶只會以為按鈕壞了。
    // 校園網裡要傳附件的頁面很多（請假、報修、助學貸款回執），這條是硬需求。
    // 2026-10-30 巡察時 `grep -rn onShowFileChooser app/src` = 0 命中。
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        // parseResult 負責「單選 / 多選 / 取消 / 沒有 data」四種情況；取消時給 null。
        // **必須回調一次**：不調的話那個 file input 會永遠卡住，之後再點也沒有反應。
        callback?.onReceiveValue(if (boundaryStopped) null else
            android.webkit.WebChromeClient.FileChooserParams.parseResult(
                result.resultCode,
                result.data,
            ),
        )
    }

    var progress by remember { mutableFloatStateOf(0f) }
    var loadState by remember { mutableStateOf<WebLoadState>(WebLoadState.Loading) }
    var canGoBack by remember { mutableStateOf(false) }

    fun stopAtBoundary(view: WebView) {
        boundaryStopped = true
        pageStarted = false
        canGoBack = false
        loadState = WebLoadState.Failed("账号已切换，请关闭此页面", -1)
        val actions: List<() -> Unit> = listOf(
            { denyPendingCapture() },
            { permissionGate.dispose() },
            {
                val callback = pendingFileCallback
                pendingFileCallback = null
                callback?.onReceiveValue(null)
            },
            { WebViewCleanup.stop(view) },
        )
        val errors = actions.mapNotNull { action ->
            try { action(); null } catch (error: Throwable) { error }
        }
        errors.firstOrNull()?.let { first ->
            errors.drop(1).filter { it !== first }.forEach(first::addSuppressed)
            throw first
        }
    }

    /**
     * 「这个学校网页**还没在本机登录过**」—— B4-05（业主 2026-10-23 选的口径：**一句实话**）。
     *
     * 为什么不是"帮他自动登录"：设置页那一列的"已登录"说的是 **App 存的凭据**（六源，一卡通不在其中），
     * 而一卡通/图书馆这类是**学校 CAS 在 WebView 里的独立会话**，两套东西。
     * 把 App 的会话说注入进去试过（B4/B5 真机对照）：一卡通确实免登了，
     * 但**图书馆立刻变 500**（厂商 `existIp` 端点），而且清掉注入的 cookie 也回不来
     * —— WebView 自己拿到了 `.ca.hzcu.edu.cn` 的真会话，作用域不同删不掉。
     * 规律是「WebView 里只要有有效 CAS 会话，图书馆就坏」⇒ 不注入（详见档头注释与 `docs/B5-WALKTHROUGH.md`）。
     * 所以这里只做一件事：**探一下有没有 cookie，没有就照实说一句**，绝不重复索要账密。
     */
    var needsWebLogin by remember { mutableStateOf<String?>(null) }

    // 系统返回：优先让 WebView 退一步，退无可退才退出本页。
    BackHandler(enabled = canGoBack) {
        webView?.let { view ->
            if (view.canGoBack()) {
                denyPendingCapture()
                view.goBack()
            } else {
                onExit()
            }
        }
    }

    DisposableEffect(lifecycle, webView) {
        val view = webView
        fun syncLifecycle() {
            pageStarted = !boundaryStopped && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (pageStarted) view?.onResume() else {
                denyPendingCapture()
                view?.onPause()
            }
        }
        val observer = LifecycleEventObserver { _, _ -> syncLifecycle() }
        lifecycle.addObserver(observer)
        syncLifecycle()
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            cleanupRegistration?.close()
            cleanupRegistration = null
            val view = webView
            try {
                if (view != null) stopAtBoundary(view)
            } finally {
                webView = null
                view?.destroy()
            }
        }
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                SmallTopAppBar(
                    title = title,
color = Color.Transparent,
                    subtitle = subtitle ?: hostOf(currentUrl),
                    navigationIcon = {
                        IconButton(onClick = { denyPendingCapture(); onExit() }) {
                            Icon(
                                imageVector = MiuixIcons.ChevronBackward,
                                contentDescription = "返回",
                                tint = colors.onBackground,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            denyPendingCapture()
                            if (!boundaryStopped && !publishWebAction {
                                    if (onReload == null) webView?.reload() else onReload()
                                }) webView?.let { stopAtBoundary(it) }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "重新加载",
                                tint = colors.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        // ★ B17：可選的「清除憑據」出口。
                        //   智慧公寓那條路需要使用者提供憑據，而**給了就沒有正路可收回**——
                        //   想換帳號或不想再留著時只能重裝 App（實際踩過）。
                        //   所以把它做成頂欄上一個明確的按鈕，而不是藏起來。
                        if (onClearCredential != null) {
                            IconButton(onClick = { denyPendingCapture(); onClearCredential() }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "清除已保存的登录信息",
                                    tint = colors.onBackground,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(onClick = { denyPendingCapture(); onExit() }) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = "关闭",
                                tint = colors.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                // ★ 進度條的顯示條件是「**正在載入**」，不是 `progress < 100f`。
                //
                //   真機 bug（B17）：先前用 `progress < 100f` 判斷，
                //   但 `onProgressChanged` 在 `onPageFinished` **之後**仍會回呼
                //   小於 100 的值（Chromium 的行為），於是那條動畫進度條**永遠在跑** ——
                //   `LinearProgressIndicator` 是不斷重繪的動畫，它讓整頁每幀重組，
                //   GPU 佔用跑到 98%、畫面看起來一直在閃。
                //
                //   改用 `loadState`（它由 onPageStarted/onPageFinished 精確驅動），
                //   載入完成就不再顯示進度條，閃爍從根上消失。
                if (loadState is WebLoadState.Loading) {
                    LinearProgressIndicator(
                        progress = (progress / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // ★ B4-05：这句是**实话**，不是代登录。他点了「一卡通」看到登录页时，
                //   至少知道「App 里显示已登录」和「这个网页要学校登录」是两件事，
                //   而且登过一次之后这台手机就记住了（WebView cookie 跨会话持久化）。
                needsWebLogin?.let { hint ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceContainer.copy(alpha = 0.6f))
                            .padding(horizontal = Dims.screenH, vertical = 8.dp),
                    ) {
                        // 正文前图标：这条讲的是「这个网页要它自己的一套登录」，用 Account。
                        BodyMark(
                            icon = MarkIcon.Account,
                            tint = colors.onSurfaceVariantSummary,
                        )
                        Text(
                            text = hint,
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                        // ⚠️ 不向 WebView 注入任何我們自己的會話 cookie。
                        //
                        // B4 曾加過「打開學校域時注入 CAS 會話」，讓一卡通免登成功，
                        // 但 B5 真機對照實驗證明它同時**把圖書館弄壞了**：
                        //   注入前  圖書館 → 正常 CAS 登入表單（可用）
                        //   注入後  圖書館 → 廠商 existIp 端點 **500 錯誤頁**
                        //   清掉注入的 cookie 後 **仍然 500** —— 因為 WebView 在訪問
                        //   一卡通的過程中**自己**從服務端拿到了 `.ca.hzcu.edu.cn`
                        //   的真實會話（與注入的 cookie 作用域不同，刪不掉）。
                        // → 規律是「WebView 裡只要有任何有效 CAS 會話，圖書館就壞」，
                        //   與誰放進去無關。注入只是讓每個用戶**第一次打開就中招**。
                        //
                        // 而收益是邊際的：WebView 的 cookie 本来就跨會話持久化，
                        // 用戶手動登入一卡通一次之後就一直免登，不需要注入。
                        // 爆炸半徑又無法界定（幾十個 CAS 子系統只測過兩個，
                        // 已證實其中一個被弄壞）。所以撤銷。
                        // 完整記錄見 docs/B5-WALKTHROUGH.md。
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                if (boundaryStopped) return true
                                if (request?.isForMainFrame == true) denyPendingCapture()
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (boundaryStopped) { view?.stopLoading(); return }
                                super.onPageStarted(view, url, favicon)
                                denyPendingCapture()
                                if (url != null) currentUrl = url
                                loadState = WebLoadState.Loading
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (boundaryStopped) return
                                super.onPageFinished(view, url)
                                // B4-05：只在**学校 CAS 那几个域**上提示，别的网页（通知/公告/办事）
                                // 不需要登录，提示只会变噪音。判定看的是**真实落地**的那一页 ——
                                // 一卡通最终落在 CAS 的 ca.hzcu.edu.cn，只看 etcard/libres 会让
                                // 这句实话一次都不出现（真机 2026-10-24 实测，判据见 WebLoginHint。
                                val jar = android.webkit.CookieManager.getInstance()
                                needsWebLogin = WebLoginHint.decide(
                                    url = url.orEmpty(),
                                    siteCookies = if (url == null) null else jar.getCookie(url),
                                    casCookies = jar.getCookie(WebLoginHint.CAS_PROBE_URL),
                                )
                                canGoBack = view?.canGoBack() == true
                                if (url != null) currentUrl = url
                                progress = 100f
                                // 只有没被错误路径改写过才判定为内容，避免「先报错后完成」把失败页盖掉。
                                if (loadState !is WebLoadState.Failed) {
                                    loadState = WebLoadState.Content
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                if (boundaryStopped) return
                                super.doUpdateVisitedHistory(view, url, isReload)
                                if (isReload || (url != null && url != currentUrl)) denyPendingCapture()
                                canGoBack = view?.canGoBack() == true
                                if (url != null) currentUrl = url
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (boundaryStopped) return
                                super.onReceivedError(view, request, error)
                                // 只把**主框架**失败当作整页失败；图片/CSS 等子资源失败不误报。
                                if (request?.isForMainFrame != true) return
                                denyPendingCapture()
                                val code = error?.errorCode ?: -1
                                loadState = WebLoadState.Failed(describeError(code), code)
                                progress = 100f
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                if (request == null) return
                                if (boundaryStopped) { request.deny(); return }
                                // Never replace a live request or reuse an outstanding Android result.
                                if (pendingWebPermission != null || launchedWebPermission != null) {
                                    if (pendingWebPermission?.request !== request) request.deny()
                                    return
                                }
                                val wanted = request.resources.mapNotNull(::captureResourceOf).toSet()
                                val ticket = permissionGate.begin(
                                    request.origin.toString(), webView?.url.orEmpty(), wanted,
                                    pageStarted,
                                )
                                if (ticket == null) {
                                    request.deny()
                                    return
                                }
                                val pending = PendingWebCapture(request, ticket)
                                pendingWebPermission = pending
                                permissionConfirmation = pending
                            }

                            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                                if (pendingWebPermission?.request === request) denyPendingCapture()
                            }

                            override fun onShowFileChooser(
                                view: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: android.webkit.WebChromeClient.FileChooserParams?,
                            ): Boolean {
                                if (boundaryStopped) { filePathCallback?.onReceiveValue(null); return true }
                                if (filePathCallback == null || fileChooserParams == null) return false
                                // 上一次的選擇還沒回話就又點了一次：先把舊回調餵 null 收掉，
                                // 否則舊的那個 input 永遠等不到結果（Chromium 只認最後一次）。
                                pendingFileCallback?.onReceiveValue(null)
                                pendingFileCallback = filePathCallback
                                // 沒有檔案管理器（罕見）時不能把回調吞掉，否則 input 卡死。
                                return runCatching {
                                    fileChooserLauncher.launch(fileChooserParams.createIntent())
                                }.map { true }.getOrElse {
                                    pendingFileCallback = null
                                    filePathCallback.onReceiveValue(null)
                                    false
                                }
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (boundaryStopped) return
                                super.onProgressChanged(view, newProgress)
                                // ⚠️ 只在**值真的變了**才寫 state。
                                //   這個回呼在載入期間會高頻觸發，每次都寫 state 等於
                                //   每一幀都重組一次整棵樹（含下面的 AndroidView）。
                                val next = newProgress.toFloat()
                                if (progress != next) progress = next
                            }
                        }
                    }.also { view ->
                        webView = view
                        cleanupRegistration = WebViewCleanup.register(view) { stopAtBoundary(view) }
                        if (!boundaryStopped && !loadInitialUrl(view, url)) stopAtBoundary(view)
                    }
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    // 正文前图标：这行字说的是「网页正在打开」，用 Web。
                                    BodyMark(
                                        icon = MarkIcon.Web,
                                        tint = colors.onSurfaceVariantSummary,
                                    )
                                    Text(
                                        text = "正在打开…",
                                        style = MiuixTheme.textStyles.body2,
                                        color = colors.onSurfaceVariantSummary,
                                    )
                                }
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
                                        if (!boundaryStopped) {
                                            // 先回到 Loading（不透明遮罩会盖住 WebView），
                                            // 再 reload；这样重试期间不会闪出 Chromium 自己的错误页。
                                            loadState = WebLoadState.Loading
                                            progress = 0f
                                            denyPendingCapture()
                                            if (!publishWebAction {
                                                    if (onReload == null) webView?.reload() else onReload()
                                                }) webView?.let { stopAtBoundary(it) }
                                        }
                                    },
                                )
                                // 若是「仅校园网可达」的入口，这里给出可操作的解释而不是空话。
                                AppCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            // 正文前图标：这条讲的是「连不上」或「原站要另行登录」，用 Warn。
                                            BodyMark(
                                                icon = MarkIcon.Warn,
                                                tint = colors.onSurfaceVariantSummary,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                            Text(
                                                text = "如果这个入口需要校园网或 WebVPN，连上校园网后重试。" +
                                                    "另有一些系统（如学在城院、CJH52）有自己的一套登录，" +
                                                    "与本 App 的学校统一身份认证不互通，" +
                                                    "即使网络可达也会要求先登录一次。",
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = colors.onSurfaceVariantSummary,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
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

    permissionConfirmation?.let { confirmation ->
        val resources = confirmation.ticket.resources.joinToString("、") {
            when (it) {
                WebCaptureResource.Camera -> "相机"
                WebCaptureResource.Microphone -> "麦克风"
            }
        }
        WanzaiOverlayDialog(
            show = true,
            onDismissRequest = { if (pendingWebPermission === confirmation) denyPendingCapture() },
            title = "允许网页使用$resources？",
            summary = "实际请求网站：${confirmation.ticket.origin.display}\n" +
                "仅允许这一次请求。App 已有的权限不代表网页获得授权；" +
                "允许后仍需检查系统权限。取消、离开网页或切换地址即拒绝。",
        ) {
            TextButton(
                text = "取消",
                onClick = { if (pendingWebPermission === confirmation) denyPendingCapture() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
            TextButton(
                text = "允许这一次",
                onClick = { confirmCapture(confirmation) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
        }
    }
}

private fun captureResourceOf(resource: String): WebCaptureResource? = when (resource) {
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> WebCaptureResource.Camera
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> WebCaptureResource.Microphone
    else -> null
}

private fun webResourceOf(resource: WebCaptureResource): String = when (resource) {
    WebCaptureResource.Camera -> PermissionRequest.RESOURCE_VIDEO_CAPTURE
    WebCaptureResource.Microphone -> PermissionRequest.RESOURCE_AUDIO_CAPTURE
}

private fun androidPermissionOf(resource: WebCaptureResource): String = when (resource) {
    WebCaptureResource.Camera -> android.Manifest.permission.CAMERA
    WebCaptureResource.Microphone -> android.Manifest.permission.RECORD_AUDIO
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

