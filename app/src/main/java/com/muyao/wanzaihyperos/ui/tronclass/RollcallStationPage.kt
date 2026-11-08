package com.muyao.wanzaihyperos.ui.tronclass

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind
import com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerResult
import com.muyao.wanzaihyperos.data.tronclass.RollcallQrCode
import com.muyao.wanzaihyperos.data.tronclass.RollcallRepository
import com.muyao.wanzaihyperos.data.tronclass.TronAttendanceStates
import com.muyao.wanzaihyperos.data.tronclass.TronRollcall
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.infoItems
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 學在城院的**簽到**（业主 2026-10-19：「簽點功能給我重做，做成一個簽到的磁貼，
 * 點進去可以掃碼或者其他簽到方式」）。
 *
 * ## 这一页的设计决定
 *
 * 1. **顶部一颗「打开相机扫码」**。以前只能从某一行的课程点进去扫码 ——
 *    但上课时老师的码就在屏幕上，学生的第一反应是「拿手机扫」，
 *    而不是「先在列表里认出这是哪门课」。
 *    扫到之后**只选出**是哪一次，把「课名 · 时间」摆给他看 —— 提交那一键永远由他按。
 * 2. **扫码结果先过 [com.muyao.wanzaihyperos.data.tronclass.QrPayloadParser]**。
 *    码里嵌了一层 JSON，真正的凭据在 `scan_code.scan_result`；
 *    整串送上去会被服务端拒（源码逐字可见）。
 * 3. **签名之外的方式老实分类**。`radar`（定位/信号）、`manual`（老师点名）、
 *    `auto_attendance`（系统判定）**不给按钮**，只给一句为什么 ——
 *    给一颗点了必然失败的按钮，比没有按钮更糟（他会以为是自己的网络问题）。
 * 4. **写操作只发一次**。见 [RollcallRepository.answer]：不重试。
 *    重试一次签到＝可能送两笔记录。
 * 5. **失败原因显示服务端原话**（「位置不对」「二维码过期」「IP 不对」），
 *    那些话直接告诉他该做什么（走近教室 / 连校园网 / 让老师重发码）。
 */
@Composable
fun RollcallStationPage(
    repository: RollcallRepository,
    onExit: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    // 清單滾動狀態提到這裡：頂欄漸變紗靠它算進度。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val veilProgress by com.muyao.wanzaihyperos.ui.design.rememberScrollProgress(listState)

    // 頂欄走共享殼（[SecondaryScaffold]）：漸變、安全區、返回鍵、欄高只由設計層決定一次。
    com.muyao.wanzaihyperos.ui.design.SecondaryScaffold(
        title = "签到",
        subtitle = rollcallSubtitle(state),
        onBack = onExit,
        modifier = modifier,
        scrollProgress = veilProgress,
        onRefresh = { scope.launch { runCatching { repository.refresh() } } },
    ) { innerPadding ->
        RollcallBody(
            repository = repository,
            onOpenWeb = onOpenWeb,
            listState = listState,
            modifier = Modifier
                .fillMaxSize()
                // 安全區只讓一次：頂欄高度（含狀態列）由殼給。
                .padding(top = innerPadding.calculateTopPadding()),
        )
    }
}

/** 顶栏副标题：一句能概括整页状态的话（不写「共 N 条」那种没用数字）。 */
private fun rollcallSubtitle(state: ListState<TronRollcall>): String {
    val items = (state as? ListState.Content)?.items.orEmpty()
    val open = items.filter { it.isOpen }
    val mine = open.count { TronAttendanceStates.isSigned(it.myStatus) }
    return when {
        items.isEmpty() && state is ListState.FirstLoad -> "正在取进行中的签到…"
        // 「没有进行中的签到」这句以前是整页的头条，配上一颗禁用的扫码按钮 ——
        // 他照字面理解就是「App 坏了/没签到功能」。現在清單只是旁證，
        // 扫码永远可点，所以这句话要说的是「清单空」，不是「没得签」。
        open.isEmpty() -> "清单里暂时没有进行中的签到 · 扫码仍然可用"
        else -> "进行中 ${open.size} 项 · 已签 $mine"
    }
}

/**
 * 簽到的**内容**（不含顶欄）。
 *
 * 拆出來是為了**同一份實作服務兩個入口**：
 * 首頁那顆磁貼進的獨立頁（[RollcallStationPage]）與「學在城院」外殼裡的二級頁
 * （`TronSub.Rollcall`）。兩份實作的下場在本專案裡見過很多次：
 * 一份修了、另一份繼續錯。
 *
 * @param header 清單最前面的額外條目（外殼用來塞「返回」）。
 */
@Composable
fun RollcallBody(
    repository: RollcallRepository,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    header: LazyListScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val items = (state as? ListState.Content)?.items.orEmpty()

    var notice by remember { mutableStateOf(Notice()) }
    /** 正在提交哪一条（null = 没有）。用它同时把按钮和顶部扫码都锁住。 */
    var busyId by remember { mutableStateOf<Int?>(null) }
    /** 相机现在的目标：某一行，或者顶部那颗「扫码签到」。 */
    var scanTarget by remember { mutableStateOf<ScanTarget?>(null) }
    /**
     * 上面那块输入面板现在在显示哪一种途径（稿上的五颗 chip）。
     *
     * ⚠️ 它**只切面板**，不触发任何动作：切到「4位数字」不会去找签到，
     *   切到「相機」也不会开相机。要开相机得按面板里那颗按钮 ——
     *   chip 是"选择"，按钮才是"做"。
     */
    var inputMode by remember { mutableStateOf(ScanMode.CAMERA) }
    /**
     * 扫到了、但**还没提交**：挂在这里，由选择卡让他自己按那一键。
     *
     * `targetId` = 他在某一行里点「打开相机扫码」时的那一条（他自己挑的）；
     * 顶部那颗 / 粘贴拿到的码则没有目标，由清单筛出候选。两种都只是"选中"，不写。
     */
    var pending by remember { mutableStateOf<ScanPending?>(null) }

    // ── 触觉：只挂在"有因果的那两刻"──────────────────────────────────────
    //
    // 业主批准的清单第 1 条（全工程原本 0 处触觉）。准则是 Apple 那三条里的两条：
    // **因果明确**（震动挂在真正造成变化的那一下）+ **同帧**（这里就在改状态的那一行同步发，
    // 不放进协程里延后一拍，否则画面和手感错帧，错觉就破了）。
    //
    // 点位刻意只有三个：
    //   commit  = 真的把签到请求发出去（不是"点开了页面"）
    //   success = 服务端认了
    //   failure = 服务端拒了 / 没发出去
    // 清单为空、刷新、展开说明这些**都不给** —— 过度反馈会训练用户忽略全部反馈。
    val haptics = com.muyao.wanzaihyperos.ui.design.rememberHaptics()
    val setBusy: (Int?) -> Unit = { id ->
        busyId = id
        if (id != null) haptics.commit()
    }
    val showNotice: (Notice) -> Unit = { n ->
        notice = n
        if (n.ok) haptics.success() else haptics.failure()
    }
    /** 某一行手输/粘贴的内容。按 id 存，免得两行共用一个字符串。 */
    var inputs by remember { mutableStateOf(mapOf<Int, String>()) }
    /**
     * 只给可调试构建：把他真正扫到的那一串原文留下来。
     *
     * 业主 2026-10-28 第⑤条：他用本 App 扫了真实签到码，要我自己去找那串值。
     * 事后取： run-as com.muyao.wanzaihyperos cat files/tron_raw_scanned.txt
     * 只记他真按下的那一次；release 构建恒为 null，不写任何东西。
     */
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val rawScanSink: ((String) -> Unit)? = remember(appCtx) {
        val ctx = appCtx
        if ((ctx.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        ) {
            { raw ->
                runCatching {
                    java.io.File(ctx.filesDir, "tron_raw_scanned.txt").appendText(
                        System.currentTimeMillis().toString() + " " + raw + "\n",
                    )
                }
                Unit
            }
        } else {
            null
        }
    }

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    // ── 掃到的原文走**同一條管道** ──────────────────────────────────
    //
    // 相機取景、相冊選圖、手動粘貼、全局扫一扫帶過來的碼 —— 四個入口，
    // 一条管道（以前只有相機那一支內聯在 QrScannerPage 的 onScanned 裡，
    // 所以相冊這條路根本沒地方接）。
    //
    // ★ 這條管道**只挑候選、永不寫**：寫請求只存在於 `submitTo`，
    //   而它的調用點全在按鈕的 onClick 裡（簽到是寫操作，業主紅線）。
    fun handleScanned(raw: String, target: ScanTarget?) {
        rawScanSink?.invoke(raw)
        val payload = com.muyao.wanzaihyperos.data.tronclass.QrPayloadParser.extract(raw)
        when {
            payload == null -> notice = Notice(
                false,
                "这不是签到二维码（内容里没有签到信息）",
            )

            target is ScanTarget.Row -> {
                // 行内扫码：目标是他自己点开的那一行。**但码照样不替他交出去** ——
                // 挂到同一张选择卡上，提交那一键由他按（审计红线：签到是写操作）。
                pending = ScanPending(payload, target.id, RollcallQrCode.parse(raw))
            }

            else -> {
                // 顶部那颗 / 相冊 / 粘贴：同一个入口 —— 先只读刷新、再按"需扫码 + 未签"挑，
                // 有候选就挂到选择卡上，**点击提交由他按**；没候选就照实说。
                // ⚠️ `/api/qr-code/scan` 已经**不使用**：它是 roomis 校园码的端点
                // （学校对签到码回 404 `{"message":"未找到资源"}`，记在 `docs/LOG.md:1381`），
                // 签到码真正的落地端点要在路径里带那条签到的 id（`answer_qr_rollcall`）。
                pickScanTarget(
                    scope = scope,
                    code = payload,
                    refreshCandidates = {
                        repository.refresh()
                        (repository.state.value as? ListState.Content)?.items.orEmpty()
                            .filter { it.needsCamera && it.unanswered }
                    },
                    onBusy = setBusy,
                    onNotice = showNotice,
                    onNeedPick = { pending = ScanPending(it, null, RollcallQrCode.parse(raw)) },
                )
            }
        }
    }

    // ── 全局扫一扫帶過來的碼 ──────────────────────────────────────
    //
    // 應用頁的掃一掃認出签到码後會 `ScanHandoff.put(raw)` 再跳回签到站；
    // `take()` 取走即清空。★ 它也走同一條 `handleScanned` —— 上一版這裡自己
    // 解析一遍、只在 `pending == null` 時才掛，與相機那條路的行為不一致。
    LaunchedEffect(Unit) {
        val handed = com.muyao.wanzaihyperos.data.tronclass.ScanHandoff.take()
            ?: return@LaunchedEffect
        handleScanned(handed, null)
    }

    // ── 相冊：截圖、老師發到群裡的碼、已經拍糊了的那張照片 ─────────
    // 業主合同裡的五條途徑：相機 / 相冊 / 粘貼 / 4 位數字 / 自主。
    // 相冊那條一直是缺的 —— 而它恰好是最常用的一條（很多人先把碼截圖再掃）。
    // 解碼用 [decodePickedImage]（與掃碼頁同一支，會自己縮圖防 OOM）。
    val pickCtx = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val albumPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            picking = true
            scope.launch {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { decodePickedImage(pickCtx, uri) }.getOrNull()
                }
                picking = false
                if (text != null) {
                    handleScanned(text, null)
                } else {
                    notice = Notice(false, "这张照片里没找到二维码。换一张把码拍正、拍清楚的。")
                }
            }
        }
    }

    // ── 相机（全屏覆盖，扫完立即收起）──────────────────────────────
    if (scanTarget != null) {
        QrScannerPage(
            onExit = { scanTarget = null },
            onScanned = { raw: String ->
                val target = scanTarget
                scanTarget = null
                handleScanned(raw, target)
            },
        )
        return
    }

    // ── 下拉刷新：清单是**随时会变**的东西（老师这一秒发起）──────────────
    //
    // 以前只有右上角那颗刷新钮，手指下拉没有任何反应 —— 而人对列表的第一反应就是往下拉。
    // 刷新走 `repository.refresh()`，它在 `ongoingRollcalls` 之前会 `bypassCacheOnce()`，
    // 所以拉一次一定打到服务器（不会把五分钟前的清单再播一遍）。
    //
    // ⚠️ 下拉里**只刷新、不清空**：仓库用 `startRefresh()` 保留现有内容，
    // 手指往下拉时不会闪成「正在取…」的空页。
    var refreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                try {
                    runCatching { repository.refresh() }
                } finally {
                    refreshing = false
                }
            }
        },
        pullToRefreshState = pullState,
        modifier = modifier.fillMaxSize(),
        // 默认四句气泡是英文（Pull down to refresh / …），中文界面里会冒出英文。
        // 顺序是 下拉 / 松开 / 正在刷 / 刷完了。
        refreshTexts = listOf("下拉刷新", "松开手就刷", "正在刷新…", "刷完了"),
    ) {
        val qrOpen = items.count { it.needsCamera && it.unanswered }
        val digitOpen = items.count {
            it.isOpen && it.unanswered && it.answerKind == RollcallAnswerKind.NUMBER
        }
        val selfOpen = items.count {
            it.isOpen && it.unanswered && it.answerKind == RollcallAnswerKind.SELF_REGISTRATION
        }

        // 「當前這一次」= 清單裡第一條還在進行中的。
        // 稿上那張匯總卡（課程/活動、簽到時間/截止、當前狀態）講的就是它：
        // 用戶進來第一件事是「現在能不能簽、截止了沒」，
        // 而原本這些字散在每一張卡裡，得自己拼出來。
        val current = items.firstOrNull { it.isOpen }

        AppList(modifier = Modifier.fillMaxSize(), state = listState) {
            header()

            // ── 頁面級三行（稿 rollcall.png 上的 eyebrow / hero / 副句）────
            // 「永遠不自動提交」是這一頁的核心承諾 —— 掃到就提交會出事，
            // 而原本這句話在介面上一個字都沒有。
            item(key = "rollcall-hero") {
                TronHero(
                    eyebrow = "CHECK-IN / 签到站",
                    title = "每一次，先确认",
                    sub = "识别只找候选，永远不自动提交。",
                    icon = MarkIcon.Scan,
                )
            }

            item {
                NoticeBanner(notice)
            }

            // ── 當前簽到匯總（稿上那張三條 kv 的卡）──────────────────
            // 沒有進行中的就整卡不顯示：一條「沒有進行中」的空卡只是噪音。
            current?.let { r ->
                item(key = "rollcall-summary") {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Dims.cardPadding)) {
                            KeyValueRow(
                                label = "课程 / 活动",
                                value = r.title.ifBlank { "签到" },
                                labelWidth = Dims.labelColumnWide,
                            )
                            KeyValueRow(
                                label = "签到时间 / 截止",
                                value = listOfNotNull(
                                    r.rollcallTime.takeIf { it.isNotBlank() },
                                    r.endTime.takeIf { it.isNotBlank() },
                                ).joinToString(" / ").ifBlank { "—" },
                                labelWidth = Dims.labelColumnWide,
                            )
                            KeyValueRow(
                                label = "当前状态",
                                value = "进行中 · ${r.myStatusLabel}",
                                labelWidth = Dims.labelColumnWide,
                            )
                        }
                    }
                }
            }

            // ── 五種簽到途徑（稿 rollcall.png 上那一行 chips）────────────────
            //
            // 稿上是五個平排 chip：相機 / 相冊 / 粘貼 / 4位數字 / 自主。
            // 前三個是「怎麼把碼弄進來」，後兩個是「碼不用掃」的兩種途徑 ——
            // 所以 chip 只切換**上面這塊輸入面板**，一行簽到底要按哪一顆按鈕，
            // 仍然只在下面那張卡片裡（寫操作永遠由他按，這裡一顆都不提交）。
            item { TronSection(text = "签到方式", icon = MarkIcon.Scan) }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Dims.cardPadding)) {
                        ScanModeChips(
                            selected = inputMode,
                            enabled = busyId == null,
                            onSelect = { inputMode = it },
                        )
                        Spacer(Modifier.height(Dims.loose))

                        when (inputMode) {
                            ScanMode.CAMERA -> {
                                Text(
                                    // ⚠️ 這裡的話改過兩次，現在以**真機實測**為準：
                                    //   2026-10-20 在他手机上把掃到的串送 `GET /api/qr-code/scan?codeStr=…`，
                                    //   學校回的是 **404 {"message":"未找到资源"}** ——
                                    //   也就是說這個部署**沒有「服務端認碼」那支接口**。
                                    //   所以「扫了服务端会认」是我說錯的話，不能留。
                                    //   相機仍然**永遠可點**：萬一清單同步慢了，他不該被我擋住。
                                    //   扫到的码**不直接提交**：先刷新清单、把候选摆出来，再由他按确认。
                                    text = if (qrOpen > 0) {
                                        "对着老师屏幕上的码扫一下（我这边看到 $qrOpen 项在进行）。" +
                                            "扫完我会先问一句是哪一次，提交由你按。"
                                    } else {
                                        "清单里现在没有进行中的签到，但扫码照样能用 ——" +
                                            "学校的签到码里自带签到编号，扫完不用等它出现在清单里。" +
                                            "提交仍然由你按。"
                                    },
                                    style = MiuixTheme.textStyles.footnote2.copy(
                                        fontFeatureSettings = Type.tabular,
                                    ),
                                    color = colors.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(Dims.sectionGap))
                                Button(
                                    onClick = { scanTarget = ScanTarget.Quick },
                                    enabled = busyId == null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (busyId != null) "提交中…" else "打开相机扫码")
                                }
                            }

                            ScanMode.ALBUM -> {
                                Text(
                                    // 相冊這條路**不需要相機權限**：拒了權限的人也還能認已經拍下來的碼。
                                    // 它與相機走同一個 `handleScanned` —— 認出來只是"掛上候選"，不寫。
                                    text = "已经拍下来的码也能认（拒了相机权限照样可用）。" +
                                        "认出来同样只挂候选，提交由你按。",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colors.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(Dims.sectionGap))
                                Button(
                                    onClick = {
                                        notice = Notice()
                                        albumPicker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    },
                                    enabled = busyId == null && !picking,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (picking) "正在认这张图…" else "从相册选一张")
                                }
                            }

                            ScanMode.PASTE -> {
                                // 相机读不出来时的出路（暗教室、屏幕反光、拍糊）。
                                PasteRow(
                                    value = inputs[PASTE_KEY].orEmpty(),
                                    onValueChange = { inputs = inputs + (PASTE_KEY to it) },
                                    enabled = busyId == null,
                                    onSubmit = { text ->
                                        if (text.isBlank()) {
                                            notice = Notice(false, "先粘贴扫码得到的内容")
                                        } else {
                                            // 和相机、相册**完全同一个入口**：只挑、不写。
                                            handleScanned(text, null)
                                        }
                                    },
                                )
                            }

                            // 下面這兩種**沒有頂部輸入框**：碼不用掃，但數字 / 自助鍵
                            // 必須落在**那一次簽到**上（提交是 `PUT /api/rollcall/{id}/…`，
                            // 沒有 id 就沒有東西可交）。所以 chip 只負責說清楚「去下面哪幾條按」。
                            ScanMode.DIGITS -> WaitingNotice(
                                text = if (digitOpen > 0) {
                                    "数字签到在下面「进行中」每一条里填 —— " +
                                        "数字要交给那一次签到，在这里先填没有意义。"
                                } else {
                                    "现在没有在等数字签到的签到。有的话会出现在下面「进行中」里。"
                                },
                                count = digitOpen,
                                unit = "条正在等数字",
                            )

                            ScanMode.SELF -> WaitingNotice(
                                text = "自主签到同样在下面「进行中」每一条里按 —— " +
                                    "进入这一页不产生任何签到。",
                                count = selfOpen,
                                unit = "条可自主签到",
                            )
                        }
                    }
                }
            }

            // ── 扫到了、要确认（或挑）是哪门课 ───────────────────────────
            pending?.let { p ->
                item {
                    val candidates = pendingCandidates(items, p.targetId, p.code)
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Dims.cardPadding)) {
                            Text(
                                // 一条时是"确认"，多条时是"挑"—— 两种都不能由我们替他决定
                                text = when {
                                    candidates.isEmpty() -> "这条签到已经不在了（清单刚刷新过）。"
                                    p.code?.rollcallId != null && p.targetId == null ->
                                        "扫到了。码里自带签到编号（第 ${p.code.rollcallId} 号），" +
                                            "不用等它出现在清单里；确认后由你按下面这一键。"
                                    candidates.size == 1 -> "扫到了。是这一次吗？确认后由你按下面这一键。"
                                    else -> "有几门课正在扫码签到，是哪一门？"
                                },
                                style = MiuixTheme.textStyles.body1,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(Dims.loose))
                            candidates.forEach { r ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = r.title.ifBlank { "签到" },
                                            style = MiuixTheme.textStyles.body2,
                                            color = colors.onSurface,
                                        )
                                        // 学校给的原文（课/节次/时间），不加工、不补
                                        val extra = listOf(r.rollcallTime, r.endTime)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                        if (extra.isNotBlank()) {
                                            Text(
                                                text = extra,
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = colors.onSurfaceVariantSummary,
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            // ★ 全页唯一会发签到写请求的两个入口之一，
                                            //   另一个是行内"签到/自助签到"按钮。都得他自己按。
                                            pending = null
                                            submitTo(
                                                scope, repository, r, RollcallAnswerKind.QR, p.payload,
                                                onBusy = setBusy, onNotice = showNotice,
                                            )
                                        },
                                        enabled = busyId == null,
                                    ) { Text(if (candidates.size == 1) "确认签到" else "就是这次") }
                                }
                                Spacer(Modifier.height(Dims.tight))
                            }
                            TextButton(
                                text = "取消",
                                onClick = { pending = null },
                            )
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    HintText(
                        // 上面那颗「打开相机扫码」在这一屏仍然可点 —— 這句只是說明清單空，
                        // 不是「簽到功能不可用」。
                        if (pageState == PageState.Empty)
                            "清单里暂时没有进行中的签到（老师发起后才会出现在这里）。" +
                                "手上已经有码的话，上面那颗扫码键照样能用 ——" +
                                "学校的签到码里自带签到编号，扫完直接就能签，不用等它出现在清单里。"
                        else "签到没有取到，请稍后重试。",
                    )
                }
                item { ActionRow(title = "改用网页版", action = "打开", onClick = onOpenWeb) }
                return@AppList
            }

            val open = items.filter { it.isOpen }
            val closed = items.filter { !it.isOpen }

            if (open.isNotEmpty()) {
                item { TronSection(text = "进行中（${open.size}）", icon = MarkIcon.Run) }
                infoItems(open, key = { it.id }) { r ->
                    RollcallCard(
                        rollcall = r,
                        submitting = busyId == r.id,
                        inputValue = inputs[r.id].orEmpty(),
                        onInput = { inputs = inputs + (r.id to it) },
                        onScan = { scanTarget = ScanTarget.Row(r.id) },
                        onSubmit = { kind, code ->
                            submitTo(
                                scope, repository, r, kind, code,
                                onBusy = setBusy, onNotice = showNotice,
                            )
                        },
                    )
                }
            }

            if (closed.isNotEmpty()) {
                item { TronSection(text = "已经结束（${closed.size}）", icon = MarkIcon.Clock) }
                infoItems(closed, key = { it.id }) { r ->
                    InfoItem(
                        title = r.title.ifBlank { "签到" },
                        subtitle = listOfNotNull(
                            r.sourceLabel,
                            r.rollcallTime.takeIf { it.isNotBlank() },
                        ).joinToString("　"),
                        trailing = r.myStatusLabel,
                        trailingColor = if (TronAttendanceStates.isSigned(r.myStatus)) colors.primary else colors.error,
                    )
                }
            }

            item {
                HintText(
                    "签到成不成由学校服务器判定（位置、IP、二维码时效都在那边校验），" +
                        "失败时会把服务器的原话显示出来。数字签到以老师屏幕上的数字为准。",
                )
            }
            // 稿上是「簽到原站更多」：那句話更具體 —— 原站才有的確實是
            // 「更多簽到」（補簽、歷史、申訴），不是一堆不相干的功能。
            item { ActionRow(title = "签到原站更多", action = "打开", onClick = onOpenWeb) }

            // 來源標註。稿上寫的是演示時間；這一頁讀真接口，寫「本地模擬樣例」是造假。
            // 要留的是「以老師屏幕為準」這層意思 —— 判錯是直接記曠課。
            item(key = "rollcall-source") {
                Text(
                    text = "来源：学在城院接口 · 以老师屏幕上的签到为准",
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 相機目標：某一行（帶著那次的 id），或頂部那顆（id 要掃完才知道）。 */
private sealed interface ScanTarget {
    data class Row(val id: Int) : ScanTarget
    data object Quick : ScanTarget
}

/**
 * 扫到的码 + 要确认的那一条（`targetId == null` = 还没定，选择卡按"需扫码 + 未签"筛）。
 *
 * 它只是**待确认的意向**，不含任何"已提交"的语义；唯一的写入口是卡片上那颗按钮。
 */
/**
 * 待确认卡上该列哪一条。
 *
 * 优先级（2026-10-30 定）：行内扫码（他点了那一行）> 码里自带的签到编号 >
 * 清单里"需扫码 + 未签"的那几条。
 *
 * 第二条是关键：学校的签到码 `/j?p=` 里第 4 段就是 rollcallId，
 * 所以扫到码**不必等它出现在「进行中」清单里**。清单里没有时合成一条占位，
 * 标题写明"扫到的这次签到（编号 N）"，提交仍然由他按。
 */
internal fun pendingCandidates(
    items: List<TronRollcall>,
    targetId: Int?,
    code: RollcallQrCode.Payload?,
): List<TronRollcall> {
    if (targetId != null) return items.filter { it.id == targetId }
    val fromCode = code?.rollcallId?.let { id ->
        items.firstOrNull { it.id == id } ?: TronRollcall(
            id = id,
            courseId = code.courseId ?: 0,
            title = "扫到的这次签到（编号 $id）",
            source = "qr",
            status = "in_progress",
            expired = false,
            rollcallTime = "",
            endTime = "",
            numberCode = "",
            myStatus = "unanswered",
        )
    }
    if (fromCode != null) return listOf(fromCode)
    return items.filter { it.needsCamera && it.unanswered }
}

private data class ScanPending(
    /** 要交出去的原文（客户端会再解一次，取出码里的 data）。 */
    val payload: String,
    /** 行内扫码时他选定的那一行；顶部/粘贴是 null。 */
    val targetId: Int?,
    /** 码解码出来的东西（rollcallId / courseId / data）。解不出是 null。 */
    val code: RollcallQrCode.Payload? = null,
)

/** 一次簽到後的提示（默認是「沒有提示」）。 */
private data class Notice(val ok: Boolean = false, val text: String? = null)

/** 「粘貼扫码结果」那一行用的 key（不是签到 id，所以不衝突）。 */
private const val PASTE_KEY = -1

/** `busyId` 的哨兵值：正在提交的是「扫码」這件事，不是列表裡某一條（真的 id 都是正數）。 */
private const val SCAN_BUSY_ID = -2

/**
 * 課程考勤記錄裡，哪些 `student_status` 算「我真的到了」。
 *
 * 只放**到課**的幾種：`on_call` / `on_call_fine`。請假（`on_leave`）與缺勤（`absent`）
 * **不算到課** —— 那是「沒到但被批准」，把它判成成功等於告訴他「你簽上了」。
 * 出勤記錄這支端點回的是粗粒度的 `student_status`，實測樣本
 * （`docs/B12-TRONCLASS-SHAPES.txt:26`）五條全部是 `on_call`。
 */
private val VERIFIED_ATTENDED = setOf("on_call", "on_call_fine")

@Composable
private fun NoticeBanner(notice: Notice) {
    val text = notice.text ?: return
    ResultBanner(message = text, ok = notice.ok)
}

/**
 * 把「提交 → 显示原话 → 成功了就刷新」串起来。
 *
 * ★ 三個細節都必須在所有入口上一致，所以抽成一個函式而不是每處抄一遍：
 * 1. **不重試**（写操作走 `net/TronHttp.kt` 那台 client：`retryOnConnectionFailure(false)`，
 *    且 `repository.answer` 只发一次；`RollcallWriteOnceTest` 有 4xx/5xx 各一条钉着）；
 * 2. 成功之後才刷新 —— 失敗時刷新會把那條錯誤橫幅下面的列表改寫掉，
 *    他來不及看是為什麼失敗的；
 * 3. 異常也要變成一句話而不是崩潰（`runCatching` + `httpStatus=0`）。
 */
private fun submitTo(
    scope: kotlinx.coroutines.CoroutineScope,
    repository: RollcallRepository,
    rollcall: TronRollcall,
    kind: RollcallAnswerKind,
    code: String?,
    onBusy: (Int?) -> Unit,
    onNotice: (Notice) -> Unit,
) {
    val id = rollcall.id
    onBusy(id)
    scope.launch {
        val result = runCatching { repository.answer(id, kind, code) }
            .getOrElse { e ->
                // ★ 旧版这里只写 `${e.javaClass.simpleName}`，真机上只能看到「提交失败：SocketTimeoutException」
                //   这类赤裸类名 —— 业主 2026-11-03 报「提示了什么没返回」就是这个形状：
                //   连是超时、是 401、还是解析炸了都分不出来。
                //   `HzcuException.userText` 就是为这一步准备的（只带类名+message+已有 details，
                //   不带请求正文/URL/凭据），所以直接把它的结果拿来显示。
                val text = (e as? com.muyao.wanzaihyperos.net.HzcuException)?.userText
                    ?: (e.message?.take(160)?.takeIf { it.isNotBlank() }
                        ?.let { "${e.javaClass.simpleName}：$it" }
                        ?: e.javaClass.simpleName)
                RollcallAnswerResult(
                    ok = false, httpStatus = 0,
                    message = "提交失败（$text）",
                )
            }
        onNotice(
            Notice(
                result.ok,
                when {
                    result.ok -> "签到成功（${rollcall.title.ifBlank { rollcall.sourceLabel }}）"
                    else -> result.message
                },
            ),
        )
        // ★ 提交后核对：HTTP 200 却判成「没签上」，是 2026-11-03 那次误判的形状。
        //   判据已经修过（认 on_call_fine），但它仍然是在猜响应字段 —— 这个站的响应外层
        //   从来不统一，`answer` 的返回体更是只有一份样本。
        //   所以「服务端接受了请求、但字面上看不出已到」时，回它自己的出勤记录里查一次：
        //   那条记在册上的 student_status 才是权威。只读，不重发写请求。
        //   核对不到就什么都不说（保持 answer 的原话），绝不因为查不到就改口「没签上」。
        var signed = result.ok
        if (!result.ok && result.httpStatus == 200) {
            val verified = runCatching { repository.verifyAttendance(id, rollcall.courseId) }
                .getOrNull()
            if (verified != null && verified in VERIFIED_ATTENDED) {
                signed = true
                onNotice(Notice(true, "签到成功（${rollcall.title.ifBlank { rollcall.sourceLabel }}）· 已核对课程记录"))
            }
        }
        if (signed) runCatching { repository.refresh() }
        onBusy(null)
    }
}

/**
 * 扫到码、**刷新过清单之后**该干什么 —— 纯函数，B4-07 的阶段 1 判据就钉在这里。
 *
 * 背景（官方 H5 产物，`docs/B9-TRONCLASS-qrbody-raw.txt:5`）：
 * 扫到的串先 `JSON.parse`，成功就取 `scan_code.scan_result`，失败就原样当字符串；
 * 而正式提交是 `PUT /api/rollcall/{id}/answer_qr_rollcall`（`answerQRRollcall: function(e){ return "/api/rollcall/".concat(e,"/answer_qr_rollcall") }`）
 * —— **路径里必须带那条签到自己的 id**，包里没有任何"码 → id"的解析接口。
 * ⇒ 扫到码但清单里没有对应条目时，正确动作是**不发写请求**，而不是去打校园码那条 404 的端点。
 */
internal sealed interface ScanPlan {
    /**
     * 唯一候选 ⇒ **只选中**这条 id，交给他按确认。
     * （名字刻意不叫 `Submit`：这个分支本身**不发任何写请求**，写请求只在按钮的 onClick 里。）
     */
    data class Single(val rollcallId: Int) : ScanPlan

    /** 清单里没有"需扫码且未签"的条目 ⇒ 不写，照实说。 */
    data object NoCandidate : ScanPlan

    /** 有多条扫码签到，分不清这个码属于哪一条 ⇒ 不写，让他去那条卡上点自己的「扫码」。 */
    data class Ambiguous(val count: Int) : ScanPlan
}

internal fun scanPlan(candidates: List<TronRollcall>): ScanPlan = when (candidates.size) {
    0 -> ScanPlan.NoCandidate
    1 -> ScanPlan.Single(candidates[0].id)
    else -> ScanPlan.Ambiguous(candidates.size)
}

/**
 * 扫到码以后**只做挑选、不做提交**。
 *
 * ★★ 第 1 轮阶段 1 审计的**红线**（业主原话：「签到是写操作，永不重试、**不许替他点提交**」）：
 *   上一版在"唯一候选"时直接 `answer(id, QR, code)` —— 那是把**我们猜的**"这个码对应哪次签到"
 *   当成他的选择，等于替他点了提交。现在改成：
 *     · 有候选（1 条或多条）⇒ 把码挂到 `pending`，由**选择卡**把候选（课名 · 时间）列出来，
 *       **提交那一键由他按**；
 *     · 一条候选都没有 ⇒ 不打任何写接口，照实说；
 *   写操作因此**只存在于 `submitTo` 一处**，而它的调用点全在按钮的 onClick 里（`ScanPlanTest` 有守卫）。
 *
 * ★ 参数里**故意没有 `RollcallRepository`**：只给"刷新 + 读候选"这一支，
 *   这样"在这里顺手提交一下"在**编译期**就写不出来（守卫测试只作第二道）。
 */
private fun pickScanTarget(
    scope: kotlinx.coroutines.CoroutineScope,
    code: String,
    refreshCandidates: suspend () -> List<TronRollcall>,
    onBusy: (Int?) -> Unit,
    onNotice: (Notice) -> Unit,
    onNeedPick: (String) -> Unit,
) {
    onBusy(SCAN_BUSY_ID)
    scope.launch {
        // 端点事实（阶段 1 已定案）：签到码落到 `PUT /api/rollcall/{id}/answer_qr_rollcall`
        // （官方 H5 包 `answerQRRollcall` 那一行），**必须带那条签到自己的 id**；
        // 包里没有"码 → id"的接口，所以先只读刷新一次再按"需扫码 + 未签"重挑。
        // ★ 2026-10-30 更正：那个 id **就写在码里**（`/j?p=` 的 4 号段，36 进制），
        //   所以扫到码根本不必等它出现在「进行中」清单里 —— 直接挂待确认，
        //   由他按确认键。清单匹配只是解不出编号时的兜底。
        if (com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.debugLog) {
            android.util.Log.i("wz-rollcall", "scan raw len=${code.length} head=${code.take(80)}")
        }
        if (RollcallQrCode.parse(code)?.rollcallId != null) {
            onNeedPick(code)
            onBusy(null)
            return@launch
        }
        val candidates = runCatching { refreshCandidates() }.getOrDefault(emptyList())
        when (val plan = scanPlan(candidates)) {
            is ScanPlan.NoCandidate -> onNotice(
                Notice(
                    false,
                    "这个码没能对上任何一次进行中的签到（我已刷新过一次清单）。\n" +
                        "可能是老师还没发起、或这次签到不按扫码。等老师发起后这里会自动出现。",
                ),
            )
            // 1 条和多条**走同一条路**：都只把码挂上去等他自己按
            is ScanPlan.Single, is ScanPlan.Ambiguous -> onNeedPick(code)
        }
        onBusy(null)
    }
}

/**
 * 一行簽到。
 *
 * ⚠️ 動作的有無**由 [TronRollcall.answerKind] 決定**（模型裡那一處規則），
 * 界面不自己判 `source` —— 頂那顆掃碼按鈕用的也是同一個規則，
 * 兩處各判一份會出現「頂部說是扫码、这里不给扫」。
 */
@Composable
private fun RollcallCard(
    rollcall: TronRollcall,
    submitting: Boolean,
    inputValue: String,
    onInput: (String) -> Unit,
    onScan: () -> Unit,
    onSubmit: (RollcallAnswerKind, String?) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            InfoItem(
                title = rollcall.title.ifBlank { "签到" },
                subtitle = listOfNotNull(
                    rollcall.sourceLabel,
                    rollcall.rollcallTime.takeIf { it.isNotBlank() },
                    rollcall.endTime.takeIf { it.isNotBlank() }?.let { "截止 $it" },
                ).joinToString("　"),
                trailing = rollcall.myStatusLabel,
                trailingColor = if (TronAttendanceStates.isSigned(rollcall.myStatus)) colors.primary else colors.error,
            )

            when {
                TronAttendanceStates.isSigned(rollcall.myStatus) -> Unit   // 已簽：什麼都不給（避免重複提交）

                else -> {
                    val blocked = rollcall.actionBlockReason
                    if (blocked != null) {
                        Spacer(Modifier.height(Dims.tight))
                        HintText(blocked)
                    } else {
                        Spacer(Modifier.height(Dims.sectionGap))
                        when (rollcall.answerKind) {
                            RollcallAnswerKind.NUMBER -> {
                                // 服务端在列表里带了一个 number_code。**不当输入框默认值**
                                //（万一它不是「老师屏幕上那个数字」，就等于替他填错），
                                // 只作为参考信息显示出来。
                                if (rollcall.numberCode.isNotBlank()) {
                                    HintText("接口里的 number_code：${rollcall.numberCode}（以老师屏幕上的数字为准）")
                                    Spacer(Modifier.height(Dims.tight))
                                }
                                TextField(
                                    value = inputValue,
                                    // ★ 只收數字、最多 4 位。官方是 4 個格子、每位一格
                                    //   （`7748.9d913ed8.js`：`new Array(4)`，收滿 4 位自動送出），
                                    //   我們是一個輸入框，所以由這裡保證「送出去的永遠是 4 位數字」——
                                    //   否則他少打一位按下去，服務端只會回 `wrong_number_code`，
                                    //   而界面說的會像「數字錯了」，他會以為是老師報的數字有問題。
                                    onValueChange = { raw -> onInput(raw.filter { it.isDigit() }.take(4)) },
                                    label = "老师报的数字（4 位）",
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(Dims.sectionGap))
                                Button(
                                    onClick = { onSubmit(RollcallAnswerKind.NUMBER, inputValue) },
                                    // 必須剛好 4 位才能按：這是官方那個「收滿 4 位才發」的等價物，
                                    // 而且仍然由他按下去 —— 寫操作的紅線不放寬到自動提交。
                                    enabled = inputValue.length == 4 && !submitting,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (submitting) "提交中…" else "签到") }
                                if (inputValue.isNotEmpty() && inputValue.length < 4) {
                                    Spacer(Modifier.height(Dims.tight))
                                    HintText("还差 ${4 - inputValue.length} 位 —— 数字是 4 位，打满才会亮起签到键。")
                                }
                            }

                            RollcallAnswerKind.SELF_REGISTRATION -> Button(
                                onClick = { onSubmit(RollcallAnswerKind.SELF_REGISTRATION, null) },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (submitting) "提交中…" else "自助签到") }

                            RollcallAnswerKind.QR -> Button(
                                onClick = onScan,
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (submitting) "提交中…" else "打开相机扫码") }

                            // `null`（雷达/点名/自动）上面 actionBlockReason 已经说过；
                            // `PLAIN` 是通用端点，**不主動给按钮** ——
                            // 服务端在什么条件下接受裸 `/answer` 我们没有证据，
                            // 猜一次就是拿他的出勤记录试。
                            null, RollcallAnswerKind.PLAIN -> Unit
                        }
                    }
                }
            }
        }
    }
}

/** 「粘貼扫码结果」那一行（相機讀不出來的出路）。 */
@Composable
private fun PasteRow(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = "或粘贴扫码结果",
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Dims.sectionGap))
    Button(
        onClick = { onSubmit(value) },
        enabled = enabled && value.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("用粘贴的内容签到") }
}

/**
 * 簽到的五種途徑（稿 `rollcall.png` 上那五颗 chip）。
 *
 * 前三個是「怎麼把碼弄進來」，後兩個是「碼不用掃」的兩種簽到方式。
 * 兩種性質不同，所以 chip 只切換上面那塊面板：
 * **數字與自主簽到必須落在某一次簽到上**（提交端點是 `PUT /api/rollcall/{id}/…`），
 * 所以它們的面板只能說清楚「去下面哪幾條按」，不自己開一個輸入框。
 */
private enum class ScanMode(val label: String) {
    CAMERA("相机"),
    ALBUM("相册"),
    PASTE("粘贴"),
    DIGITS("4位数字"),
    SELF("自主"),
}

/**
 * 五颗平排 chip（換行由 [FlowRow] 負責）。
 *
 * ### 為什麼用 FlowRow 而不是 `LazyRow`
 *
 * 稿上第一行四颗、第二行一顆（392px 寬時放不下五颗）。
 * `JwPage.kt:542 SecChipRow` 用可橫向滾動的 `LazyRow` —— 那對「一排篩選器」成立，
 * 但這裡是**固定五種途徑**，橫向滾動會把「自主」藏到螢幕外，
 * 而它恰恰是必須被看見的一種（自助簽到的人找不到入口就等於沒有）。
 * 折行的代價是兩行高度，換來五種全都一眼可見。
 *
 * ### 命中區
 *
 * 視覺高度照稿是 32dp 左右，但**可點區域**撐到 [Dims.minTouch]（44dp）——
 * 用 `heightIn(min=)`，pill 的底色仍然由內距決定，不是把 pill 拉高成一塊板磚。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScanModeChips(
    selected: ScanMode,
    enabled: Boolean,
    onSelect: (ScanMode) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dims.tight),
        verticalArrangement = Arrangement.spacedBy(Dims.tight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ScanMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .heightIn(min = Dims.minTouch)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) colors.primary else colors.surfaceContainer)
                    .clickable(enabled = enabled, onClickLabel = "选择${mode.label}") {
                        onSelect(mode)
                    }
                    .padding(horizontal = Dims.cardPadding, vertical = Dims.normal),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    fontSize = Type.bodyEmphasis,
                    fontWeight = if (active) FontWeight.SemiBold else Type.weightBody,
                    color = if (active) colors.onPrimary else colors.onSurface,
                )
            }
        }
    }
}

/** 數字／自主那兩種 chip 的面板：只說清楚「去下面哪幾條按」，這裡沒有輸入框也沒有按鈕。 */
@Composable
private fun WaitingNotice(text: String, count: Int, unit: String) {
    Column {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (count > 0) {
            Spacer(Modifier.height(Dims.tight))
            Text(
                text = "现在有 $count $unit",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
