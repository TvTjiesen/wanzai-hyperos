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
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyBroadcastItems
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyFreshnessLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyHeroLabel
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.rememberSpringListEntrance
import com.muyao.wanzaihyperos.ui.design.springListEntrance
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.muyao.wanzaihyperos.ui.home.collectAsVisibleStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.R
import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.cjh52.Cjh52Notice
import com.muyao.wanzaihyperos.data.cjh52.Lecture
import com.muyao.wanzaihyperos.data.portal.PortalNewsItem
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import com.muyao.wanzaihyperos.ui.components.WanzaiTextButton as TextButton
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
 * - notices / lectures（CJH52 日常通知与爱讲座）
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
    /** 宿主兼容参数：本页已移除旧门户通知，不再触发已读写操作。 */
    onRead: (String) -> Unit = {},
    onReadAll: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenAccounts: () -> Unit = {},
    selectedFilter: Int? = null,
    onFilterChange: (Int) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    listPositions: MutableMap<String, Pair<Int, Int>> = remember { mutableMapOf() },
) {
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
    val broadcastOwner = com.muyao.wanzaihyperos.ui.main.LocalPageEntryOwner.current
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

    val newsGroupsState by graph.news.groups.collectAsVisibleStateWithLifecycle()
    val newsSelected by graph.news.selectedGroup.collectAsVisibleStateWithLifecycle()
    val pageActive = com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive.current
    val entryGate = com.muyao.wanzaihyperos.ui.main.LocalPageEntryGate.current
    val entryOwner = com.muyao.wanzaihyperos.ui.main.LocalPageEntryOwner.current
    val refreshGate = remember(entryOwner) { com.muyao.wanzaihyperos.data.portal.NoticeRefreshGate() }

    // ══════════ 通知来源的选择 ══════════
    //
    // 通知只保留仍在更新的 CJH52「日常通知」与「爱讲座」；旧门户通知不再作为本页来源。
    var noticeSource by androidx.compose.runtime.saveable.rememberSaveable(entryOwner) {
        mutableStateOf(NOTICE_SOURCE_DAILY)
    }
    val noticeState by graph.notices.state.collectAsVisibleStateWithLifecycle()
    val lectureState by graph.lectures.state.collectAsVisibleStateWithLifecycle()
    val lectureHasMore by graph.lectures.hasMore.collectAsVisibleStateWithLifecycle()
    LaunchedEffect(pageActive, entryOwner, entryGate, refreshGate) {
        if (!pageActive) return@LaunchedEffect
        if (entryGate.claim("notify", entryOwner)) {
            graph.fetch { graph.rollcall.refresh() }
            graph.fetch { graph.todoBoard.refresh() }
        }
        while (true) {
            val now = System.currentTimeMillis()
            if (refreshGate.claim("news-groups", graph.news.groups.value, now)) {
                graph.fetch { graph.news.refreshGroups() }
            }
            graph.news.selectedGroup.value?.let { group ->
                if (group.dataId != null && refreshGate.claim("news:${group.id}", graph.news.news.value, now)) {
                    graph.fetch { graph.news.refreshKeepingContent() }
                }
            }
            if (refreshGate.claim("system-announcements", graph.broadcasts.state.value, now)) {
                graph.fetch { graph.broadcasts.refresh() }
            }
            // 只刷**当前正在看**的那份来源：没在看的那份不必替它拉数据，
            // 既省一次请求，也避免学工平台的会话被无谓地消耗。
            if (noticeSource == NOTICE_SOURCE_DAILY &&
                refreshGate.claim("daily-notices", graph.notices.state.value, now)
            ) {
                graph.fetch { graph.notices.refresh() }
            }
            if (noticeSource == NOTICE_SOURCE_LECTURE &&
                refreshGate.claim("lectures", graph.lectures.state.value, now)
            ) {
                graph.fetch { graph.lectures.refresh() }
            }
            graph.fetch { graph.unread.refresh() }
            kotlinx.coroutines.delay(60_000)
        }
    }

    LaunchedEffect(noticeSource, pageActive, entryOwner) {
        if (!pageActive) return@LaunchedEffect
        // 切到某个来源时立刻拉一次，而不是等下一轮 60 秒轮询 ——
        // 用户刚点完胶囊却看到「正在取得…」转一整分钟是不能接受的。
        when (noticeSource) {
            NOTICE_SOURCE_DAILY -> graph.fetch { graph.notices.refresh() }
            NOTICE_SOURCE_LECTURE -> graph.fetch { graph.lectures.refresh() }
            else -> Unit
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
    val sourceLabel = when (filter) { 1 -> "待处理"; 3 -> "校园新闻"; 4 -> "学校公告"; else -> "通知" }
    val currentStates: List<ListState<*>> = when (filter) {
        1 -> listOf(rollState, todoState)
        3 -> listOf(newsGroupsState, newsState)
        4 -> listOf(broadcastState)
        else -> listOf(if (noticeSource == NOTICE_SOURCE_DAILY) noticeState else lectureState)
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
        else -> com.muyao.wanzaihyperos.ui.common.listStateDescription(
            if (noticeSource == NOTICE_SOURCE_DAILY) "日常通知" else "爱讲座",
            if (noticeSource == NOTICE_SOURCE_DAILY) noticeState else lectureState,
        )
    }
    fun refreshCurrent() {
        if (refreshing) return
        refreshing = true
        graph.fetch {
            try {
                when (filter) {
                    1 -> { graph.rollcall.refresh(); graph.todoBoard.refresh() }
                    2, 0 -> if (noticeSource == NOTICE_SOURCE_DAILY) graph.notices.refresh() else graph.lectures.refresh()
                    3 -> { graph.news.refreshGroups(); if (graph.news.selectedGroup.value != null) graph.news.refreshKeepingContent() }
                    4 -> graph.broadcasts.refresh()
                    else -> Unit
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
    val heroClock: String = (if (noticeSource == NOTICE_SOURCE_DAILY) noticeState else lectureState)
        .let { (it as? ListState.Content)?.meta?.updatedAtMillis }
        ?.let { millis ->
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

            // ══════════ 通知来源 ══════════
            //
            // 只在两份 CJH52 通知来源间切换，不再保留门户历史消息。
            item(key = "notice-sources") {
                NoticeSourceChips(
                    selected = noticeSource,
                    onSelect = { source ->
                        noticeSource = source
                    },
                )
            }

            if (noticeSource == NOTICE_SOURCE_DAILY) {
                // ══════════ 日常通知（CJH52）══════════
                //
                // 与「应用 → 日常通知」同源同仓库（graph.notices），
                // 所以在这里刷新之后进那一页也是新数据，不会两处打架。
                item(key = "daily-header") {
                    GroupHead(
                        name = "日常通知",
                        count = (noticeState as? ListState.Content)?.items?.size,
                        note = "学生服务 · 实时更新",
                    )
                }
                when (val s = noticeState) {
                    is ListState.FirstLoad -> item(key = "daily-loading") { StatusHint("正在取得日常通知…") }
                    is ListState.SessionExpired -> item(key = "daily-expired") {
                        StatusError("学工平台登录已失效", "请到应用页打开一次智慧学工以重新激活。", "管理账号", onOpenAccounts)
                    }
                    is ListState.Failed -> item(key = "daily-failed") {
                        StatusError("取不到日常通知", s.error.userText, "重试") {
                            graph.fetch { graph.notices.refresh() }
                        }
                    }
                    is ListState.Empty -> item(key = "daily-empty") {
                        Column {
                            ListFreshness(s.meta) { graph.fetch { graph.notices.refresh() } }
                            StatusHint("暂时没有日常通知。")
                        }
                    }
                    is ListState.Content -> {
                        item(key = "daily-freshness") {
                            ListFreshness(s.meta) { graph.fetch { graph.notices.refresh() } }
                        }
                        if (s.items.isEmpty()) {
                            item(key = "daily-none") { StatusHint("暂时没有日常通知。") }
                        } else {
                            s.items.forEachIndexed { index, notice ->
                                val itemKey = "daily-${notice.id}"
                                item(key = itemKey) {
                                    Column(Modifier.springListEntrance(entrance, itemKey)) {
                                        DailyNoticeRow(notice)
                                        if (index != s.items.lastIndex) ThinDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (noticeSource == NOTICE_SOURCE_LECTURE) {
                // ══════════ 爱讲座（CJH52）══════════
                // 报名/取消报名是写操作，这里只读；要报名走「去原站报名」。
                item(key = "lecture-header") {
                    GroupHead(
                        name = "爱讲座",
                        count = (lectureState as? ListState.Content)?.items?.size,
                        note = "CJH52 · 报名请去原站",
                    )
                }
                when (val s = lectureState) {
                    is ListState.FirstLoad -> item(key = "lecture-loading") { StatusHint("正在取得讲座…") }
                    is ListState.SessionExpired -> item(key = "lecture-expired") {
                        StatusError("学工平台登录已失效", "请到应用页打开一次智慧学工以重新激活。", "管理账号", onOpenAccounts)
                    }
                    is ListState.Failed -> item(key = "lecture-failed") {
                        StatusError("取不到讲座", s.error.userText, "重试") {
                            graph.fetch { graph.lectures.refresh() }
                        }
                    }
                    is ListState.Empty -> item(key = "lecture-empty") {
                        Column {
                            ListFreshness(s.meta) { graph.fetch { graph.lectures.refresh() } }
                            StatusHint("暂时没有讲座。")
                        }
                    }
                    is ListState.Content -> {
                        item(key = "lecture-freshness") {
                            ListFreshness(s.meta) { graph.fetch { graph.lectures.refresh() } }
                        }
                        if (s.items.isEmpty()) {
                            item(key = "lecture-none") { StatusHint("暂时没有讲座。") }
                        } else {
                            s.items.forEachIndexed { index, lecture ->
                                val itemKey = "lecture-${lecture.id}"
                                item(key = itemKey) {
                                    Column(Modifier.springListEntrance(entrance, itemKey)) {
                                        LectureNoticeRow(lecture)
                                        if (index != s.items.lastIndex) ThinDivider()
                                    }
                                }
                            }
                        }
                        if (lectureHasMore) {
                            item(key = "lecture-more") {
                                MoreRow(
                                    loading = s.meta.loadingMore || s.meta.refreshing,
                                    onClick = { graph.fetch { graph.lectures.loadMore() } },
                                )
                            }
                        }
                    }
                }
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


}

@Composable
private fun NotifyFilters(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val items = listOf(2 to "通知", 4 to "公告", 1 to "待处理", 3 to "新闻")
    ScheduleNoticeSelector(
        items = items,
        selected = if (selected == 0) 2 else selected,
        onSelected = onSelect,
        modifier = Modifier.fillMaxWidth(),
        itemContent = { id, _ ->
            NotifyFilterLabel(label = items.first { it.first == id }.second, count = "—")
        },
    )
}

@Composable
private fun NotifyFilterLabel(label: String, count: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = androidx.compose.material3.LocalContentColor.current
        Text(text = label, color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = count, color = color, modifier = Modifier.alpha(0.62f), fontSize = 11.sp, maxLines = 1)
    }
}

private const val NOTICE_SOURCE_DAILY = 0
private const val NOTICE_SOURCE_LECTURE = 1

@Composable
private fun NoticeSourceChips(selected: Int, onSelect: (Int) -> Unit) {
    ScheduleNoticeSelector(
        items = listOf(NOTICE_SOURCE_DAILY to "日常通知", NOTICE_SOURCE_LECTURE to "讲座通知"),
        selected = selected.coerceIn(NOTICE_SOURCE_DAILY, NOTICE_SOURCE_LECTURE),
        onSelected = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
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
            .scheduleNoticeClick(onClickLabel = action, onClick = onClick)
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
        ScheduleNoticeActionCue(text = action, prominent = action == "去签到")
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
 * 日常通知的一行（CJH52 `find_all_for_reader`）。
 *
 * 这里没有未读态（CJH52 不给），
 * 时间来自 `publishedAt`，发布人来自 `publisher`。
 */
@Composable
private fun DailyNoticeRow(notice: Cjh52Notice, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.dividerLine.copy(alpha = 0.6f), shape)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = notice.title.ifBlank { "（无标题）" },
            fontSize = Type.rowTitle,
            lineHeight = Type.lineRowTitle,
            fontWeight = Type.weightBody,
            color = colors.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            formatNoticeMillis(notice.publishedAt ?: notice.createdAt),
            notice.publisher.takeIf { it.isNotBlank() },
            notice.level.takeIf { it.isNotBlank() && it != "普通" },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, fontSize = Type.summary, color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 5.dp))
        }
        if (notice.detail.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = notice.detail,
                fontSize = Type.secondary,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 爱讲座的一行（CJH52 `find_lectures`）。
 *
 * 报名/取消报名是写操作，这里**不做** —— 只显示状态与名额，
 * 要报名去原站（入口在「应用 → 爱讲座」那一页）。
 */
@Composable
private fun LectureNoticeRow(lecture: Lecture, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.dividerLine.copy(alpha = 0.6f), shape)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = lecture.title.ifBlank { "（无标题）" },
                fontSize = Type.rowTitle,
                lineHeight = Type.lineRowTitle,
                fontWeight = Type.weightBody,
                color = colors.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = lecture.statusLabel,
                fontSize = Type.summary,
                color = if (lecture.canRegister) colors.primary else colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        val meta = listOfNotNull(
            lecture.startLocal.takeIf { it.isNotBlank() },
            lecture.address.takeIf { it.isNotBlank() },
            lecture.speaker.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, fontSize = Type.summary, color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 5.dp))
        }
        val extra = listOfNotNull(
            lecture.capacityLabel.takeIf { it.isNotBlank() },
            lecture.deadlineLocal.takeIf { it.isNotBlank() }?.let { "报名截止 $it" },
        ).joinToString(" · ")
        if (extra.isNotBlank()) {
            Text(extra, fontSize = Type.summary, color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/**
 * CJH52 的时间戳 → 与通知行一致的短日期。
 *
 * 复用 [boardShortDate] 的"同一年只写 月/日"口径，但输入是毫秒数，
 * 所以先按本地时区格式化成 `yyyy-MM-dd HH:mm` 再交给它。
 */
private fun formatNoticeMillis(millis: Long?): String? {
    if (millis == null || millis <= 0L) return null
    val formatted = runCatching {
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull() ?: return null
    return boardShortDate(formatted)
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
    /**
     * 已加载条数。**可以不知道**（`null`）：来源还没回来时写「—」而不是 0 ——
     * 把「不知道」印成「0 条」是这条流水线反复犯过的错（见 UnknownCountGuardTest）。
     */
    count: Int?,
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
            text = if (note.isNotBlank()) note else count?.let { "已加载 $it 条" } ?: "已加载 — 条",
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

@Composable
private fun NewsRow(n: PortalNewsItem, onOpenUrl: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val openUrl = n.openUrl?.takeIf { it.isNotBlank() }
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth()
        .clip(shape)
        .background(colors.surface.copy(alpha = 0.72f))
        .border(1.dp, colors.dividerLine.copy(alpha = 0.6f), shape)
        .then(if (openUrl == null) Modifier else Modifier.scheduleNoticeClick(onClickLabel = "打开新闻") { onOpenUrl(openUrl) })
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
