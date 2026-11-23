package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.largeTitleItem
import com.muyao.wanzaihyperos.ui.design.liquid.rememberPageChromePilot
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.ui.main.pages.redesign.NotifyLocalFullTextSheet
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyBroadcastItems
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyFreshnessLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyGroupCountLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyHeroLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyMessageDateLabel
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.rememberSpringListEntrance
import com.muyao.wanzaihyperos.ui.design.springListEntrance
import com.muyao.wanzaihyperos.ui.design.springPress
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.muyao.wanzaihyperos.ui.home.collectAsVisibleStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.R
import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.portal.PortalMessageGroup
import com.muyao.wanzaihyperos.data.portal.PortalMessageItem
import com.muyao.wanzaihyperos.data.portal.PortalNewsItem
import com.muyao.wanzaihyperos.data.portal.opensPortalTextFirst
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import com.muyao.wanzaihyperos.ui.components.WanzaiOverlayDialog as OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 消息板塊：消息與新聞分兩段。
 *
 * 為什麼要分開：
 * - 消息是「給你的」（審批結果、通知），常要動作，會過期；
 * - 新聞是「給所有人看的」（校園新聞），通常只看一眼，不會過期。
 *
 * 混在一條時間軸上，使用者分不清「哪條需要我做事」。
 *
 * 資料來源：
 * - messages.messages（門戶消息介面，第一個分組的消息）
 * - news.news（門戶新聞介面）
 *
 * 介面上不寫「數據來自某某介面」那種開發說明 ——
 * 那是開發者要知道的事，不是使用者要看的。
 */
@Composable
fun NotifySectionPage(
    graph: AppGraph,
    onOpenRollcall: () -> Unit = {},
    onOpenTodos: () -> Unit = {},
    /** 點開一條消息：把它的 openurl 交給外部（真頁面接 Route.Web）。 */
    onOpenUrl: (String) -> Unit = {},
    /** 保留宿主回调兼容；打开详情不自动标已读。 */
    onRead: (String) -> Unit = {},
    /** 把当前分组整个标已读。失败要能从外面给一句实话，所以也提到页面外面。 */
    onReadAll: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenAccounts: () -> Unit = {},
    selectedFilter: Int? = null,
    onFilterChange: (Int) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    listPositions: MutableMap<String, Pair<Int, Int>> = remember { mutableMapOf() },
) {
    val msgState by graph.messages.messages.collectAsVisibleStateWithLifecycle()
    val newsState by graph.news.news.collectAsVisibleStateWithLifecycle()
    val broadcastState by graph.broadcasts.state.collectAsVisibleStateWithLifecycle()
    val broadcastDetail by graph.broadcasts.detail.collectAsVisibleStateWithLifecycle()
    val rollState by graph.rollcall.state.collectAsVisibleStateWithLifecycle()
    val todoState by graph.todoBoard.state.collectAsVisibleStateWithLifecycle()

    // The shell owns selection across detail routes; standalone previews retain a local fallback.
    var localFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(4) }
    val filter = selectedFilter ?: localFilter
    // 当前组刷新已加载页，成功后整体替换；请求期间保留原列表。
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 「全部已读」是批量写，按下就先问一句 —— 一百多条一次性变已读，没有撤销。
    var confirmReadAll by remember { mutableStateOf<PortalMessageGroup?>(null) }
    val broadcastOwner = com.muyao.wanzaihyperos.ui.main.LocalPageEntryOwner.current
    var localFullText by remember(graph, broadcastOwner) { mutableStateOf<PortalMessageItem?>(null) }
    var selectedBroadcast by remember(broadcastOwner) {
        mutableStateOf<com.muyao.wanzaihyperos.data.portal.PortalBroadcastItem?>(null)
    }
    var broadcastJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    androidx.compose.runtime.DisposableEffect(graph.broadcasts, broadcastOwner) {
        onDispose {
            broadcastJob?.cancel()
            graph.broadcasts.closeDetail()
        }
    }

    // 消息仓库在更新分组时同步选择；UI不再额外触发首组读取。
    val msgGroupsState by graph.messages.groups.collectAsVisibleStateWithLifecycle()
    val msgSelected by graph.messages.selectedGroup.collectAsVisibleStateWithLifecycle()
    val newsGroupsState by graph.news.groups.collectAsVisibleStateWithLifecycle()
    val newsSelected by graph.news.selectedGroup.collectAsVisibleStateWithLifecycle()
    // 门户的消息分组（服务端的 `listmessagegroup`，标识是名字）。
    // 没有它，页面只能停在「仓库自己选中的第一个分组」上。
    val msgGroups: List<PortalMessageGroup> =
        (msgGroupsState as? ListState.Content)?.items.orEmpty()

    val pageActive = com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive.current
    val entryGate = com.muyao.wanzaihyperos.ui.main.LocalPageEntryGate.current
    val entryOwner = com.muyao.wanzaihyperos.ui.main.LocalPageEntryOwner.current
    val refreshGate = remember(entryOwner) { com.muyao.wanzaihyperos.data.portal.NoticeRefreshGate() }
    LaunchedEffect(pageActive, entryOwner, entryGate, refreshGate) {
        if (!pageActive) return@LaunchedEffect
        if (entryGate.claim("notify", entryOwner)) {
            graph.fetch { graph.rollcall.refresh() }
            graph.fetch { graph.todoBoard.refresh() }
        }
        while (true) {
            val now = System.currentTimeMillis()
            if (refreshGate.claim("message-groups", graph.messages.groups.value, now)) {
                graph.fetch { graph.messages.refreshGroups() }
            }
            if (refreshGate.claim("news-groups", graph.news.groups.value, now)) {
                graph.fetch { graph.news.refreshGroups() }
            }
            graph.messages.selectedGroup.value?.let { group ->
                if (refreshGate.claim("messages:${group.name}", graph.messages.messages.value, now)) {
                    graph.fetch { graph.messages.refreshKeepingContent() }
                }
            }
            graph.news.selectedGroup.value?.let { group ->
                if (group.dataId != null && refreshGate.claim("news:${group.id}", graph.news.news.value, now)) {
                    graph.fetch { graph.news.refreshKeepingContent() }
                }
            }
            if (refreshGate.claim("system-announcements", graph.broadcasts.state.value, now)) {
                graph.fetch { graph.broadcasts.refresh() }
            }
            graph.fetch { graph.unread.refresh() }
            kotlinx.coroutines.delay(60_000)
        }
    }

    LaunchedEffect(newsGroupsState, newsSelected, pageActive, entryOwner) {
        if (!pageActive) return@LaunchedEffect
        val groups = (newsGroupsState as? ListState.Content)?.items.orEmpty()
        if (newsSelected == null) {
            groups.firstOrNull()?.let { g -> graph.fetch { graph.news.selectGroup(g) } }
        }
    }

    // 大標題交給頂欄（T53 的試點抽成共用實作）：標題是列表第 0 項，
    // 跟著內容一起滾，滾出去之後由頂欄接手小標題。
    // 原本這一頁自己畫了一個「消息/應用/設定」的大字 —— 那就是雙標題的來源：
    // 頂欄已經會顯示板塊名，頁內再畫一個，兩處的字體/位置遲早不一致。
    val pilot = rememberPageChromePilot(listState)
    val entrance = rememberSpringListEntrance(pilot.listState)
    val positionKey = when (filter) {
        2 -> "messages:${msgSelected?.name.orEmpty()}"
        3 -> "news:${newsSelected?.id.orEmpty()}:${newsSelected?.dataId}"
        else -> "section:$filter"
    }
    LaunchedEffect(positionKey, listState, listPositions) {
        val saved = listPositions[positionKey] ?: (0 to 0)
        androidx.compose.runtime.withFrameNanos { }
        listState.scrollToItem(saved.first, saved.second)
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { listPositions[positionKey] = it }
    }

    val openRollcalls = (rollState as? ListState.Content)
        ?.items?.filter { !it.expired }.orEmpty()
    val todoGroups = (todoState as? ListState.Content)?.items?.firstOrNull()?.groups.orEmpty()
    // 未知不得当成 0（data/ListState.kt 的红线）：只有拿到结论的两种状态才敢给数字。
    // 原来这里把非 Content 一律 `?: 0`，于是「没取到」在筛选条和分组头上都长成「0」。
    // 委托属性（by collectAsState）不能智能转型，所以先落成局部 val 再判。
    val rollNow = rollState
    val todoNow = todoState
    val msgNow = msgState
    val newsNow = newsState
    val rollCount: Int? = when (rollNow) {
        is ListState.Content -> openRollcalls.size
        is ListState.Empty -> 0
        else -> null
    }
    val todoCount: Int? = when (todoNow) {
        is ListState.Content -> todoNow.items.firstOrNull()?.knownTotal
        is ListState.Empty -> 0
        else -> null
    }
    // 分页元信息：hasMore 由「本次返回条数 >= 页大小」算出（ListMeta 的注释），
    // 不能拿服务端 total 算 —— 实测 total=20 而 allrecords=192。
    val msgMeta = (msgNow as? ListState.Content)?.meta
    val msgCount: Int? = when (msgNow) {
        is ListState.Content -> msgNow.items.size
        is ListState.Empty -> 0
        else -> null
    }
    val newsMeta = (newsNow as? ListState.Content)?.meta
    val newsCount: Int? = when (newsNow) {
        is ListState.Content -> newsNow.items.size
        is ListState.Empty -> 0
        else -> null
    }
    val broadcastCount: Int? = when (val current = broadcastState) {
        is ListState.Content -> current.items.size
        is ListState.Empty -> 0
        else -> null
    }
    val pendingCount: Int? =
        if (rollCount != null && todoCount != null) rollCount + todoCount else null
    val sourceLabel = when (filter) { 1 -> "待处理"; 3 -> "校园新闻"; 4 -> "学校公告"; else -> "门户通知" }
    val currentStates: List<ListState<*>> = when (filter) {
        1 -> listOf(rollState, todoState)
        3 -> listOf(newsGroupsState, newsState)
        4 -> listOf(broadcastState)
        else -> listOf(msgGroupsState, msgState)
    }
    val sourceLoading = refreshing || currentStates.any { it is ListState.FirstLoad || (it as? ListState.Content)?.meta?.refreshing == true }
    val sourceError = currentStates.any { it is ListState.Failed || it is ListState.SessionExpired ||
        (it as? ListState.Content)?.meta?.hasRefreshProblem == true || (it as? ListState.Empty)?.meta?.hasRefreshProblem == true }
    val ballState = when {
        sourceError -> com.muyao.wanzaihyperos.ui.design.ai.AiBallState.Error
        sourceLoading -> com.muyao.wanzaihyperos.ui.design.ai.AiBallState.Loading
        filter == 1 -> AiBallStates.ofCount(pendingCount)
        else -> com.muyao.wanzaihyperos.ui.design.ai.AiBallState.Idle
    }
    val sourceDescription = when (filter) {
        1 -> com.muyao.wanzaihyperos.ui.common.listStateDescription("签到", rollState) + "\n" +
            com.muyao.wanzaihyperos.ui.common.listStateDescription("待办", todoState)
        3 -> com.muyao.wanzaihyperos.ui.common.listStateDescription("新闻", newsState)
        4 -> com.muyao.wanzaihyperos.ui.common.listStateDescription("公告", broadcastState)
        else -> com.muyao.wanzaihyperos.ui.common.listStateDescription(msgSelected?.name ?: "通知", msgState)
    }
    fun refreshCurrent() {
        if (refreshing) return
        refreshing = true
        graph.fetch {
            try {
                when (filter) {
                    1 -> { graph.rollcall.refresh(); graph.todoBoard.refresh() }
                    3 -> { graph.news.refreshGroups(); if (graph.news.selectedGroup.value != null) graph.news.refreshKeepingContent() }
                    4 -> graph.broadcasts.refresh()
                    else -> { graph.messages.refreshGroups(); if (graph.messages.selectedGroup.value != null) graph.messages.refreshKeepingContent() }
                }
                graph.unread.refresh(force = true)
            } finally { refreshing = false }
        }
    }

    // ── 英雄区副行（设计稿 v2.1：「未读 8 · 09:40」）──────────────────────
    // 两个数都必须是**真数据**，任何一个取不到就把那一半省掉（`data/ListState.kt` 红线：
    // 未知不得当成 0，更不得编一个像样的数字）。两组都拿不到时整行不画 ——
    // 空字符串走 [LargeTitle] 的 `subtitle.isEmpty()` 分支，副行自然消失。
    val unreadState by graph.unread.state.collectAsVisibleStateWithLifecycle()
    val heroUnread = (unreadState.value as? CountValue.Known)?.value
    // 「这一份是什么时候的」：取**这一次成功取数**的时间（ListState.Content.updatedAtMillis），
    // 不是「现在」。取不到就只写未读，不编一个像样的钟点（同一条红线）。
    val heroClock: String = (msgState as? ListState.Content)?.meta?.updatedAtMillis?.let { millis ->
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }.orEmpty()
    val heroSubtitle = notifyHeroLabel(heroUnread, unreadState.stale, heroClock)

    // 下拉只更新当前分类，通知保留已加载窗口，内容原地不动。
    // ⚠️ contentPadding 只影响下拉指示器的位置，不给内容加 padding ——
    // 内容的 inset 仍由里面那个 LazyColumn 自己消费（见 Miuix PullToRefresh 源码）。
    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = ::refreshCurrent,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = statusBarTopPadding()),
        // 四句气泡文案的默认值是英文（Pull down to refresh / Release to refresh /
        // Refreshing... / Refreshed successfully）—— 真机上那行字就是「Refreshing...」，
        // 中文界面里冒出一句英文。顺序是 下拉 / 松开 / 正在刷 / 刷完了。
        refreshTexts = listOf("下拉刷新", "松开手就刷", "正在刷新…", "刷完了"),
    ) {
        LazyColumn(
            state = pilot.listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dims.screenH,
                end = Dims.screenH,
                // 大標題自己帶狀態欄 inset，這裡 top 必須歸零 ——
                // 留著原本那 56dp 會出現「標題下面憑空一大塊空白」。
                top = 0.dp,
                bottom = pageBottomInsetDp(LocalNavBarVisible.current),   // 留出玻璃底欄的高度（讓內容能滾到它下面，玻璃才折射得到東西）
            ),
            verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
        ) {
            largeTitleItem(
                pilot = pilot,
                leading = { AiBall(
                    state = ballState, diameter = AiBall.DIAMETER_PAGE, hitSlop = AiBall.INLINE_SLOP,
                    stateDescription = sourceLabel + "。" + sourceDescription,
                    presentation = com.muyao.wanzaihyperos.ui.design.ai.AiBallPresentation.Home,
                    work = com.muyao.wanzaihyperos.ui.design.ai.AiBallWork(
                        phase = when {
                            sourceError -> com.muyao.wanzaihyperos.ui.design.ai.AiBallWorkPhase.Error
                            sourceLoading -> com.muyao.wanzaihyperos.ui.design.ai.AiBallWorkPhase.Reading
                            else -> com.muyao.wanzaihyperos.ui.design.ai.AiBallWorkPhase.Ready
                        },
                    ),
                ) },
                // 2026-11-07 按设计稿 v2.1 复刻首屏英雄区：
                // 「校园消息 / 消息与动态 / 未读 8 · 09:40」三行 + 一条分隔线。
                // 副行是**服务端口径的未读数 + 这一份的时间**，取不到就整行不画（不编）。
                kicker = "通知 · 待办 · 动态",
                // 设计稿里顶栏小标题与首屏大字**不是同一句话**：顶栏「消息」、首屏「消息与动态」。
                // 折叠后顶栏接的仍是 `LocalLargeTitle`（板块名），这里只换首屏那颗大字。
                heroTitle = "校园消息",
                subtitle = heroSubtitle,
                divider = true,
            )

            item(key = "tabs") {
                NotifyFilters(
                    selected = filter,
                    onSelect = {
                        if (it != filter) {
                            localFilter = it
                            onFilterChange(it)
                        }
                    },
                )
            }

            if (filter == 1) {
                item(key = "todo-header") {
                    SectionHeader(text = "需要处理", trailing = pendingCount?.toString() ?: "—")
                }
                item(key = "roll-state") {
                    when (val s = rollState) {
                        is ListState.Content -> Column {
                            ListFreshness(s.meta) { graph.fetch { graph.rollcall.refresh() } }
                            if (openRollcalls.isEmpty()) StatusHint("当前没有可签到的课程。")
                            openRollcalls.forEach { r ->
                                PendingRow(
                                    title = r.title.ifBlank { "现在可以签到" },
                                    subtitle = if (r.endTime.isBlank()) r.sourceLabel else "截止 ${r.endTime} · ${r.sourceLabel}",
                                    action = "去签到",
                                    onClick = onOpenRollcall,
                                )
                                ThinDivider()
                            }
                        }
                        is ListState.Empty -> Column {
                            ListFreshness(s.meta) { graph.fetch { graph.rollcall.refresh() } }
                            StatusHint("当前没有可签到的课程。")
                        }
                        is ListState.SessionExpired -> StatusError("学在城院登录已失效", "签到暂时无法取得。", "管理账号", onOpenAccounts)
                        is ListState.Failed -> StatusError("签到还没取到", s.error.userText, "查看签到", onOpenRollcall)
                        is ListState.FirstLoad -> StatusHint("正在取得签到…")
                    }
                }
                item(key = "todo-card") {
                    when (val s = todoState) {
                        is ListState.Content -> Column(Modifier.fillMaxWidth()) {
                            ListFreshness(s.meta) { graph.fetch { graph.todoBoard.refresh() } }
                            todoGroups.forEach { group ->
                                GroupHead(
                                    name = group.source.label,
                                    count = group.items.size,
                                    note = "总数 " + (if (group.failed || group.total == null) "—" else group.countText) +
                                        " · 已加载 " + group.items.size + " 条",
                                )
                                if (group.failed) {
                                    StatusError("这一来源还没取到", group.error.orEmpty(), "查看待办", onOpenTodos)
                                } else if (group.items.isEmpty()) {
                                    StatusHint(if (group.total == 0) "这个来源没有待办。" else "这个来源当前未加载到待办，总数尚不确定。")
                                }
                                group.items.take(2).forEach { t ->
                                    PendingRow(
                                        title = t.title,
                                        subtitle = listOfNotNull(t.subtitle.takeIf { it.isNotBlank() }, t.timeText.takeIf { it.isNotBlank() }).joinToString(" · "),
                                        action = "查看",
                                        onClick = onOpenTodos,
                                    )
                                    ThinDivider()
                                }
                                if (group.items.size > 2 || group.hasMore) {
                                    TextButton("查看这一来源的更多待办", onClick = onOpenTodos, modifier = Modifier.heightIn(min = 48.dp))
                                }
                            }
                        }
                        is ListState.Empty -> Column {
                            ListFreshness(s.meta) { graph.fetch { graph.todoBoard.refresh() } }
                            StatusHint("没有待办。")
                        }
                        is ListState.SessionExpired -> StatusError("待办来源登录已失效", "请在账号管理检查门户与学在城院。", "管理账号", onOpenAccounts)
                        is ListState.Failed -> StatusError("待办还没取到", s.error.userText, "查看待办", onOpenTodos)
                        is ListState.FirstLoad -> StatusHint("正在取得双源待办…")
                    }
                }
            }

            if (filter == 2 || filter == 0) {

            // ══════════ 消息 ══════════
            item(key = "msg-header") {
                // 不叫「消息」：頁面大標題已經是「消息」，分組再來一個同名大字
                // 看起來像標題重複。入口在首頁就叫「门户通知」，這裡沿用同一個說法。
                //
                // ★ 不再放「全部 ›」：它以前只把 selectedTab 設成 2 —— 而這裡就是 2，
                //   點下去等於什麼都沒發生（审查 2026-10-21）。門戶詳情路由還沒接，
                //   所以整段直接展開、行不可點：沒有死入口，也不用拿假動作騙人。
                // 板 `.group-head`：小灰点 + 组名(8.9sp/700) + 条数(8sp) + 一条吃掉余宽的发丝线。
                // 原来这里用 13sp 粗体大字，于是这一屏出现三个同级别的大标题，
                // 板上的层级（一个大标题 + 若干小分组头）被压平了（复评 2026-10-21）。
                // 「全部已读」只对**当前选中的那一组**（门户没有跨组接口）。
                // 数字取 libmessagegroup 的 noread，而不是列表长度：列表只是一页（10 条）。
                val openGroup = msgGroups.firstOrNull { it.name == msgSelected?.name }
                GroupHead(
                    name = "门户通知",
                    count = msgCount ?: 0,
                    note = when {
                        msgCount == null -> notifyGroupCountLabel(null, openGroup?.unread, false)
                        else -> notifyGroupCountLabel(msgCount, openGroup?.unread, msgMeta?.hasMore == true)
                    },
                ) {
                    if (openGroup != null && openGroup.unread > 0) {
                        ReadAllAction(onClick = { confirmReadAll = openGroup })
                    }
                }
            }

            // 分组切换：`listmessage` 一次只返回**一个分组**，而首位分组会变
            // （2026-10-30 实测在「讲座通知」与「活动通知」之间来回）。
            // 没有这排胶囊，另一个分组对用户等于不存在。
            item(key = "msg-groups") {
                MsgGroupChips(
                    groups = msgGroups,
                    selected = msgSelected,
                    onSelect = { g -> graph.fetch { graph.messages.selectGroup(g) } },
                )
            }

            when (val s = msgGroupsState) {
                is ListState.SessionExpired -> item(key = "msg-groups-expired") {
                    StatusError("门户登录已失效", "请到账号管理重新连接门户。", "管理账号", onOpenAccounts)
                }
                is ListState.Failed -> item(key = "msg-groups-failed") {
                    StatusError("取不到消息分组", s.error.userText, "重试消息分组") {
                        graph.fetch { graph.messages.refreshGroups() }
                    }
                }
                is ListState.Empty -> item(key = "msg-groups-empty") { StatusHint("门户未返回消息分组。") }
                is ListState.Content -> if (s.meta.hasRefreshProblem) item(key = "msg-groups-freshness") {
                    ListFreshness(s.meta) { graph.fetch { graph.messages.refreshGroups() } }
                }
                else -> Unit
            }
            if (msgSelected != null || msgState is ListState.SessionExpired || msgState is ListState.Failed) when (val s = msgState) {
                is ListState.FirstLoad -> item(key = "msg-loading") { StatusHint("正在取得消息…") }
                is ListState.SessionExpired -> item(key = "msg-expired") {
                    StatusError("门户登录已失效", "请到账号管理重新连接门户。", "管理账号", onOpenAccounts)
                }
                is ListState.Failed -> item(key = "msg-failed") {
                    StatusError("取不到消息", s.error.userText, "重试通知") {
                        graph.fetch {
                            if (msgSelected == null) graph.messages.refreshGroups()
                            else graph.messages.refreshKeepingContent()
                        }
                    }
                }
                is ListState.Content -> {
                    item(key = "msg-freshness") {
                        Column {
                            ListFreshness(s.meta) { graph.fetch { graph.messages.refreshKeepingContent() } }
                            StatusHint(notifyMessageDateLabel(s.items, s.meta.hasMore))
                        }
                    }
                    val items = s.items
                    if (items.isEmpty()) {
                        item(key = "msg-empty") { StatusHint("暂无通知") }
                    } else {
                        items.forEachIndexed { index, message ->
                            val itemKey = "message-${msgSelected?.name}-$index-${message.id}"
                            item(key = itemKey) {
                                Column(Modifier.springListEntrance(entrance, itemKey)) {
                                    MessageRow(
                                        message, onOpenUrl,
                                        opensPortalTextFirst = message.opensPortalTextFirst(msgSelected?.name),
                                        onOpenLocal = { localFullText = it },
                                    )
                                    if (index != items.lastIndex) ThinDivider()
                                }
                            }
                        }
                    }
                    // 后页入口不依赖当前过滤是否命中。
                    if (msgMeta?.hasMore == true) {
                        item(key = "msg-more") {
                            MoreRow(
                                loading = msgMeta.loadingMore || msgMeta.refreshing,
                                onClick = { graph.fetch { graph.messages.loadMore() } },
                            )
                        }
                    }
                }
                is ListState.Empty -> item(key = "msg-none") {
                    Column {
                        ListFreshness(s.meta) { graph.fetch { graph.messages.refreshKeepingContent() } }
                        StatusHint("当前组没有消息。")
                    }
                }
            } else if (msgGroupsState is ListState.FirstLoad || msgGroupsState is ListState.Content) {
                item(key = "msg-await-group") { StatusHint("正在取得消息分组…") }
            }
            }

            if (filter == 4) {
                notifyBroadcastItems(
                    state = broadcastState,
                    onRefresh = { graph.fetch { graph.broadcasts.refresh() } },
                    onOpen = { announcement ->
                        broadcastJob?.cancel()
                        selectedBroadcast = announcement
                        broadcastJob = graph.fetch { graph.broadcasts.open(announcement) }
                    },
                    onAccounts = onOpenAccounts,
                )
            }

            if (filter == 3) {

            // ══════════ 新聞 ══════════
            item(key = "news-header") {
                // 同样不放「更多 ›」：它以前把新闻跳到 cjh52 的「日常通知」，
                // 那是另一个系统、另一份数据（审查 2026-10-21）。没有正确的目的地就不画入口。
                GroupHead(
                    name = "新闻",
                    count = newsCount ?: 0,
                    // 新闻一次取 8 条（门户 NEWS_PAGE_SIZE）。只报 8 会让人以为校园新闻只有 8 条。
                    note = when {
                        newsCount == null -> "已加载 — 条"
                        newsMeta?.hasMore == true -> "已加载 $newsCount 条 · 还有更多"
                        else -> "已加载 $newsCount 条"
                    },
                )
            }

            when (val s = newsGroupsState) {
                is ListState.SessionExpired -> item(key = "news-groups-expired") {
                    StatusError("门户登录已失效", "请到账号管理重新连接门户。", "管理账号", onOpenAccounts)
                }
                is ListState.Failed -> item(key = "news-groups-failed") {
                    StatusError("取不到新闻分组", s.error.userText, "重试新闻分组") {
                        graph.fetch { graph.news.refreshGroups() }
                    }
                }
                is ListState.Empty -> item(key = "news-groups-empty") { StatusHint("门户未返回新闻分组。") }
                is ListState.Content -> if (s.meta.hasRefreshProblem) item(key = "news-groups-freshness") {
                    ListFreshness(s.meta) { graph.fetch { graph.news.refreshGroups() } }
                }
                else -> Unit
            }
            if (newsSelected != null || newsState is ListState.SessionExpired || newsState is ListState.Failed) when (val s = newsState) {
                is ListState.FirstLoad -> item(key = "news-loading") { StatusHint("正在取得新闻…") }
                is ListState.SessionExpired -> item(key = "news-expired") {
                    StatusError("门户登录已失效", "请到账号管理重新连接门户。", "管理账号", onOpenAccounts)
                }
                is ListState.Failed -> item(key = "news-failed") {
                    StatusError("取不到新闻", s.error.userText, "重试新闻") {
                        graph.fetch {
                            if (newsSelected == null) graph.news.refreshGroups()
                            else graph.news.refreshKeepingContent()
                        }
                    }
                }
                is ListState.Content -> {
                    item(key = "news-freshness") {
                        ListFreshness(s.meta) { graph.fetch { graph.news.refreshKeepingContent() } }
                    }
                    val items = s.items
                    if (items.isEmpty()) {
                        item(key = "news-empty") { StatusHint("暂无新闻") }
                    } else {
                        items.forEachIndexed { index, news ->
                            val itemKey = "news-${newsSelected?.id}-$index-${news.id}"
                            item(key = itemKey) { Box(Modifier.springListEntrance(entrance, itemKey)) { NewsRow(news, onOpenUrl) } }
                        }
                    }
                    if (newsMeta?.hasMore == true) {
                        item(key = "news-more") {
                            MoreRow(
                                loading = newsMeta.loadingMore,
                                onClick = { graph.fetch { graph.news.loadMore() } },
                            )
                        }
                    }
                }
                is ListState.Empty -> item(key = "news-none") {
                    Column {
                        ListFreshness(s.meta) { graph.fetch { graph.news.refreshKeepingContent() } }
                        StatusHint("当前新闻分组暂时没有新闻。")
                    }
                }
            } else if (newsGroupsState is ListState.FirstLoad || newsGroupsState is ListState.Content) {
                item(key = "news-await-group") { StatusHint("正在取得新闻分组…") }
            }
            }
        }
    }

    localFullText?.let { message ->
        val detailUrl = com.muyao.wanzaihyperos.net.HzcuEndpoints.portalOpenUrl(message.openUrl)?.takeIf {
            com.muyao.wanzaihyperos.nav.WebTarget.fromUrl(it).kind != com.muyao.wanzaihyperos.nav.WebTarget.Kind.BLOCKED
        }
        NotifyLocalFullTextSheet(
            message = message,
            onDismiss = { localFullText = null },
            onOpenDetail = detailUrl?.let { url ->
                { localFullText = null; onOpenUrl(url) }
            },
        )
    }

    selectedBroadcast?.let { announcement ->
        com.muyao.wanzaihyperos.ui.main.pages.redesign.NotifyBroadcastSheet(
            selected = announcement,
            state = broadcastDetail,
            onRetry = {
                broadcastJob?.cancel()
                broadcastJob = graph.fetch { graph.broadcasts.open(announcement) }
            },
            onAccounts = onOpenAccounts,
            onDismiss = {
                selectedBroadcast = null
                broadcastJob?.cancel()
                broadcastJob = null
                graph.broadcasts.closeDetail()
            },
        )
    }

    val pendingGroup = confirmReadAll
    OverlayDialog(
        show = pendingGroup != null,
        onDismissRequest = { confirmReadAll = null },
        title = "全部标成已读",
        summary = "把「" + (pendingGroup?.name ?: "") + "」这一组的 " +
            (pendingGroup?.unread ?: 0) + " 条未读一次标成已读。" +
            "门户没有撤销接口，标完只能重新等新消息。",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { confirmReadAll = null },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            top.yukonga.miuix.kmp.basic.Button(
                onClick = {
                    val g = confirmReadAll
                    confirmReadAll = null
                    g?.let { onReadAll(it.name) }
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(text = "全部已读")
            }
        }
    }
}

@Composable
private fun NotifyFilters(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val filters = listOf(2, 4, 1, 3)
    val labels = listOf("通知", "公告", "待处理", "新闻")
    com.muyao.wanzaihyperos.ui.design.SlidingChoiceRow(
        labels = labels,
        stableKeys = filters.map { it.toString() },
        selectedIndex = filters.indexOf(if (selected == 0) 2 else selected),
        onSelect = { onSelect(filters[it]) },
        modifier = Modifier.fillMaxWidth(),
        itemContent = { index, isSelected ->
            FilterPill(labels[index], "—", isSelected)
        },
    )
}

/**
 * 消息分组胶囊。
 *
 * 为什么必须能切：`listmessage` 一次只回**一个分组**的行，而服务端排在第一的分组
 * 会变（2026-10-30 实测在「讲座通知」与「活动通知」之间来回）。切不了的话，
 * 另一个分组对用户等于不存在 —— 一百多条未读就藏在看不见的那一组里。
 *
 * 分组标识是**名字**不是 id（data/portal/PortalModels.kt 的注释），所以选中态按名字比。
 * 胶囊样式直接复用 [FilterPill]：两排胶囊长成两种样子，用户会以为它们是两种东西。
 */
@Composable
private fun MsgGroupChips(
    groups: List<PortalMessageGroup>,
    selected: PortalMessageGroup?,
    onSelect: (PortalMessageGroup) -> Unit,
) {
    // 只有一个分组时这排胶囊不提供任何选择，白占一行高度。
    if (groups.size < 2) return
    com.muyao.wanzaihyperos.ui.design.SlidingChoiceRow(
        labels = groups.map { g -> "${g.name} ${g.unread}" },
        stableKeys = groups.map { it.name },
        selectedIndex = groups.indexOfFirst { g -> selected?.name == g.name },
        onSelect = { onSelect(groups[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FilterPill(label: String, count: String, selected: Boolean) {
    val colors = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = Type.bodyEmphasis, fontWeight = Type.weightBody,
            color = if (selected) colors.primary else colors.onSurface, maxLines = 1)
        if (count.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(text = count, modifier = Modifier.alpha(0.62f), fontSize = Type.caption,
                color = if (selected) colors.primary else colors.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun PendingRow(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = action) { onClick() }
            .heightIn(min = 48.dp)
            // 板 `.action-row{padding:9px 0}` ⇒ 8dp；
            // 原来 8dp 上下 + 行内间距把整行撑到 53 CSS（板 45.8）。
            .padding(vertical = 6.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                // 板 `.action-row .dot{width:7px;box-shadow:0 0 0 3px var(--accent-soft)}`
                // × 0.886 ⇒ 6.2dp 的点外面再套一圈 2.7dp 的 accent-soft 光环。
                // 原来只画一颗 6dp 实心点，板上那颗是「被高亮过的一颗」—— 这圈光晕
                // 就是「要你处理」和「一条普通消息」的区别。
                .size(12.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(6.2.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                // 板 `.action-row .t{font-size:12px;font-weight:640}` × 0.886 ⇒ 10.6sp
                fontSize = 16.sp,
                fontWeight = Type.weightBody,
                color = colors.onBackground,
                // 板 `.action-row .t{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}`
                // ⇒ **一行**。折成两行会把右边的按钮顶偏、行高逐行漂移（复评 2026-10-21）。
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        val filled = action == "去签到"
        Text(
            text = action,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            // 板 `.action-row .btn.ghost`：字色 --sub、描边 --line-strong。
            // 用强调绿画次级按钮，等于让每一行都有两颗主按钮。
            color = if (filled) colors.onPrimary else colors.onSurfaceVariantSummary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (filled) colors.primary else Color.Transparent)
                .then(
                    if (filled) Modifier
                    else Modifier.border(
                        1.dp,
                        colors.dividerLine,
                        RoundedCornerShape(50),
                    )
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SectionHeader(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 頂部 8 加上列表的 10，剛好是分區之間要的 18dp 呼吸；
            // 不用額外的占位 item，避免 key 與高度都要多管一份。
            .padding(top = Dims.sectionGap, start = 0.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BodyMark(
            icon = when (text) {
                "需要处理" -> MarkIcon.Warn
                "未读消息" -> MarkIcon.Message
                "校园新闻" -> MarkIcon.Web
                else -> MarkIcon.Info
            },
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 7.dp),
        )
        Text(
            text = text,
            fontSize = Type.body,
            fontWeight = Type.weightTitle,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = Type.caption,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 加載 / 空的共用說明。
 *
 * 以前這些狀態各包一張卡片，空頁面反而比有內容時更重；
 * 統一成一行低調文字，讓「沒有東西」安靜地待著。
 */
@Composable
private fun StatusHint(text: String) {
    Text(
        text = text,
        fontSize = Type.caption,
        lineHeight = Type.caption * 1.45f,
        color = if (rememberMotion().highContrast) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = Dims.cardPadding, vertical = Dims.tight),
    )
}

/**
 * 失敗說明。
 *
 * 錯誤不能藏：標題保留 error 色，下面照舊給 `userText` 的具體原因。
 */
@Composable
private fun StatusError(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.padding(horizontal = Dims.cardPadding, vertical = Dims.tight)) {
        Text(text = title, fontSize = Type.body, lineHeight = Type.body * 1.4f, color = if (rememberMotion().highContrast) colors.onBackground else colors.error)
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(text = detail, fontSize = Type.caption, lineHeight = Type.caption * 1.45f, color = if (rememberMotion().highContrast) colors.onBackground else colors.onSurfaceVariantSummary)
        }
        TextButton(action, onClick = onAction, modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
private fun ListFreshness(meta: ListMeta, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        com.muyao.wanzaihyperos.ui.design.RefreshStatusCapsule(meta, notifyFreshnessLabel(meta))
        meta.lastRefreshError?.let { error ->
            StatusError("本次更新失败", error.userText, "重试更新", onRetry)
        }
        if (meta.stale && meta.lastRefreshError == null && !meta.refreshing) {
            TextButton("更新这一来源", onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

/** 板 `.group-head{i,gname,gn,line}` × 0.886 ⇒ 点 5.3dp、名 9sp/700、数 8sp、线 0.3dp。 */
/**
 * 「加载更多」一行。
 *
 * 只在这一组还有下一页时出现（ListMeta.hasMore）；
 * 取的时候显示「正在加载…」，而不是把按钮留在原地让人连点两次。
 */
@Composable
private fun MoreRow(loading: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Text(
                text = "正在加载…",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            TextButton(
                text = "加载更多",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun GroupHead(
    name: String,
    count: Int,
    note: String = "",
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(5.3.dp).clip(CircleShape).background(colors.onSurfaceVariantSummary.copy(alpha = 0.55f)))
            Text(
                text = name,
                fontSize = Type.caption,
                fontWeight = Type.weightTitle,
                letterSpacing = 0.02.em,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (note.isNotBlank()) note else "已加载 $count 条",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            style = TextStyle(fontFeatureSettings = Type.tabular),
            modifier = Modifier.padding(top = 4.dp),
        )
        trailing?.let { action ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { action() }
        }
        ThinDivider()
    }
}

/**
 * 组头右边的「全部已读」。
 *
 * 故意**不用 [FilterPill]**：那是一排「选哪个分组」的开关，长得一样会让人以为
 * 点下去只是换个分组，而这里会一次改掉一百多条消息的状态。
 * 所以它是纯文字 + 强调色，落在发丝线右侧，像一行小注脚。
 */
@Composable
private fun ReadAllAction(onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Text(
        // 稿 v2.1 写「当前组已读」：门户只给「当前分组」批量接口，
        // 写成「全部已读」是整个门户的意思，点下去做不到（也做不到同时清 189 条）。
        text = "当前组已读",
        fontSize = Type.micro,
        fontWeight = Type.weightTitle,
        color = colors.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "把这一组全部标成已读") { onClick() }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
private fun MessageRow(
    m: PortalMessageItem,
    onOpenUrl: (String) -> Unit,
    opensPortalTextFirst: Boolean,
    onOpenLocal: (PortalMessageItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val openUrl = m.openUrl?.takeIf { it.isNotBlank() && !opensPortalTextFirst }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            // clip 在最外层：卡面与点击水波都按圆角裁切；clickable 在 padding 之前，整行仍可点。
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.dividerLine.copy(alpha = 0.6f), shape)
            // 消息条目本身是一张半透明玻璃卡：描边 + 半透明底 + 圆角，与搜索胶囊/chip/底栏同一套材质。
            .then(
                if (openUrl == null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "查看本地全文，只读不标已读") { onOpenLocal(m) }
                } else {
                    Modifier.clickable(role = Role.Button, onClickLabel = "打开这条通知") {
                        // Opening a link is not proof that its detail loaded successfully.
                        onOpenUrl(openUrl)
                    }
                }
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(Dims.loose),
        // 頂對齊是為了標題折成兩行時，日期仍停在第一行。
        verticalAlignment = Alignment.Top,
    ) {
        // 已讀也佔同寬的空位，標題左邊線才不會逐行跳。
        UnreadMark(unread = !m.isRead)
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dims.normal),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = m.title.ifBlank { "（无标题）" },
                    // 板 `.mrow .t{font-size:12px;font-weight:620}` × 0.886 ⇒ 10.6sp / 620
                    //    未读 = --ink，已读 = --sub（板上已读是**整条变淡**，不是同号同色）
                    fontSize = 16.sp,
                    fontWeight = if (m.isRead) FontWeight.Normal else Type.weightBody,
                    color = if (m.isRead) colors.onSurfaceVariantSummary else colors.onBackground,
                    maxLines = 3,
                    lineHeight = 23.sp,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (m.createdDate.isNotBlank()) Text(boardShortDate(m.createdDate), fontSize = 12.sp,
                color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 5.dp))
            if (m.message.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = m.message,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (openUrl == null) {
                Text("查看原文 ›", fontSize = 12.sp, color = colors.primary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (openUrl != null) {
            // 箭頭只是「這行能點」的信號，不是獨立按鈕（整行都可點）。
            // 光學對齊首行文字，所以用固定 padding 而不是垂直居中。
            Text(
                text = "›",
                fontSize = Type.title,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun UnreadMark(unread: Boolean) {
    Box(
        modifier = Modifier
            // 讓 6dp 的圓點落在首行文字的光學中線上；用固定 padding 而不是
            // 垂直居中，否則標題兩行時圓點會跑到兩行中間。
            .padding(top = 7.dp)
            .size(6.dp)
            .then(
                if (unread) {
                    Modifier.background(MiuixTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            ),
    )
}

@Composable
private fun NewsRow(n: PortalNewsItem, onOpenUrl: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val openUrl = n.openUrl?.takeIf { it.isNotBlank() }
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth()
        .clip(shape)
        .background(colors.surface.copy(alpha = 0.72f))
        .border(1.dp, colors.dividerLine.copy(alpha = 0.6f), shape)
        .then(if (openUrl == null) Modifier else Modifier.clickable(role = Role.Button) { onOpenUrl(openUrl) })
        .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(n.title.ifBlank { "（无标题）" }, fontSize = 16.sp, lineHeight = 23.sp,
                color = colors.onBackground, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (n.createdDate.isNotBlank()) Text(boardShortDate(n.createdDate), fontSize = 12.sp,
                color = colors.onSurfaceVariantSummary)
        }
        if (openUrl != null) Text("›", fontSize = 20.sp, color = colors.onSurfaceVariantSummary)
    }
}

/**
 * 板上的时间列只放**短式**：`.mrow .when` 是「10:05 / 昨天 / 10/20」，不是完整日期时间。
 * 门户给的是「2026-10-21 18:00:00」这种全串，96dp 里放不下（真机上会被截成
 * 「2026-10-21 18:…」）。**只裁格式，不编内容**：今年的写成 MM/DD，
 * 往年的保留年份，解析不出来就原样给回（宁可长一点也不瞎猜）。
 */
internal fun boardShortDate(raw: String): String {
    val date = raw.trim().substringBefore(' ')
    val parts = date.split('-', '.')
    if (parts.size != 3) return raw.trim()
    val year = parts[0].toIntOrNull() ?: return date
    val month = parts[1].toIntOrNull() ?: return date
    val day = parts[2].toIntOrNull() ?: return date
    return if (year == java.time.LocalDate.now().year) "$month/$day" else date
}

/** 分組卡內的分隔線：0.5dp 是髮絲線，1dp 會把列表畫成表格。 */
@Composable
private fun ThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}
