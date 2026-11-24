package com.muyao.wanzaihyperos.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.containerBackground
import com.muyao.wanzaihyperos.ui.home.colorAt
import com.muyao.wanzaihyperos.ui.design.Motion
import com.muyao.wanzaihyperos.ui.design.glass.GlassTheme
import com.muyao.wanzaihyperos.ui.design.glass.LocalBarMaterial
import com.muyao.wanzaihyperos.ui.design.liquid.GlassNavBar
import com.muyao.wanzaihyperos.ui.design.liquid.GlassNavItem
import com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning
import com.muyao.wanzaihyperos.ui.design.liquid.IosTopBar
import com.muyao.wanzaihyperos.ui.design.liquid.LocalLargeTitle
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageChrome
import com.muyao.wanzaihyperos.ui.design.liquid.PageChrome
import com.muyao.wanzaihyperos.ui.main.pages.AppearancePage
import com.muyao.wanzaihyperos.ui.main.pages.AppsSectionPage
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.main.pages.HomeSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.NotifySectionPage
import com.muyao.wanzaihyperos.ui.main.pages.SettingsSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.TimetableSectionPage
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCanvasBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import com.muyao.wanzaihyperos.ui.design.liquid.GlasmioSearchButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * 标准 HorizontalPager 统一拖动、底栏与外部选页，顶栏/底栏是固定的单实例兄弟节点。
 * 每个 page key 自有 Glasmio 录制层；只有 currentPage 发布采样源，dispose 按身份清理。
 * 页面业务只在 settled 且无遮挡时 active，邻页预组合不代表进入页面。
 */
@Composable
fun WanzaiMainShell(
    graph: AppGraph,
    uiState: AppUiState,
    onOpenApp: (com.muyao.wanzaihyperos.data.PortalApp) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSection: (MainSection) -> Unit,
    onOpenJw: () -> Unit,
    /** 課表頁頁尾「學在城院 · 本週 / 全部」（稿 v2.1 三顆出口之一）。 */
    onOpenTron: () -> Unit = {},
    onOpenTimetable: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 首頁「簽到」磁貼：直接進簽到頁（掃碼/數字/自助）。 */
    onOpenRollcall: () -> Unit = {},
    /** 應用頁的掃一掃：全局入口，掃到網址開網頁、掃到簽到碼帶去簽到頁。 */
    onOpenScan: () -> Unit = {},
    /** 「設定 → 去重新登入」：真的把使用者送回登入頁。 */
    onOpenLogin: () -> Unit = {},
    /** 设置页「智慧公寓」那一行：跳到它自己的闸门页（只能他自己粘贴入口链接）。 */
    onOpenZhgy: () -> Unit = {},
    /** 设置页「数智体育」那一行：跳到它自己的闸门页（粘 token + 数字 userId）。 */
    onOpenSzty: () -> Unit = {},
    /** 设置页「关于」那一行：关于 + 开源许可页（B4-10）。 */
    onOpenAbout: () -> Unit = {},
    onOpenPreferences: () -> Unit = {},
    /** A10：外观页「调整照片取景」→ 取景页（点应用才落盘）。 */
    onOpenCrop: () -> Unit = {},
    /** B4-06：设置页「账号」二级页。 */
    onOpenAccounts: () -> Unit = {},
    /** 消息裡點一條：把它的 openurl 交給根導航開 WebView（Route.Web）。 */
    onOpenUrl: (String) -> Unit = {},
    /**
     * 消息裡點開一條之後標已讀。
     *
     * 不寫在這一行裡，是因為「標已讀」要網路、要重取未讀數，
     * 那是根導航（WanzaiRoot）的職責；頁面只管把 id 報上來。
     */
    onReadMessage: (String) -> Unit = {},
    onReadAllMessage: (String) -> Unit = {},
    /**
     * 首页卡片原位展开到详情路由的运动状态；根导航与首页共用。
     */
    onHomeMorphState: com.muyao.wanzaihyperos.nav.HomeMorphState? = null,
    /** 「消息」那颗 tab 上的未读数。0 = 不画角标。
     *
     * 只收一个 Int 而不是 CountValue：底栏的职责就是画一个数，
     * 「未知」在根导航那一层已经折成 0 了（不画 0 也不画问号）。
     */
    unreadCount: Int = 0,
    /** 返回栈深度：设置页的账号聚合行要按它重算（见 `SettingsSectionPage.refreshKey`）。 */
    navDepth: Int = 0,
    modifier: Modifier = Modifier,
) {
    val maxIdx = MainSection.entries.size - 1
    val pagerState = rememberPagerState(initialPage = uiState.selectedTab.coerceIn(0, maxIdx)) { MainSection.entries.size }
    val section = pagerState.currentPage
    val selectionScope = rememberCoroutineScope()
    var selectionJob by remember { mutableStateOf<Job?>(null) }
    var selectionGeneration by remember { mutableIntStateOf(0) }
    var requestedPage by remember { mutableStateOf<Int?>(null) }
    var committedPage by remember { mutableIntStateOf(uiState.selectedTab.coerceIn(0, maxIdx)) }
    var consumedRequestRevision by remember { mutableStateOf(uiState.tabRequestRevision) }
    var notifiedPage by remember { mutableIntStateOf(uiState.selectedTab.coerceIn(0, maxIdx)) }
    val entryGate = remember { PageEntryGate() }
    val entryOwner by graph.accountRevision.collectAsState()
    val navHaptics = com.muyao.wanzaihyperos.ui.design.rememberHaptics()

    /**
     * 「外觀」子頁：殼內全屏覆蓋層，**不是新路由**。
     *
     * 它只改這台手機怎麼顯示，不屬於任何板塊；蓋住底欄是刻意的
     * （否則他會以為還在板塊裡、卻點不動）。
     */
    var showAppearance by remember { mutableStateOf(false) }
    // 返工总单 A4：节次时间表从设置项内联展开改成独立页（与外观/辅助显示同一套覆盖层）。
    var showPeriods by remember { mutableStateOf(false) }
    // 返工总单 A5：上课提醒独立页。
    var showReminders by remember { mutableStateOf(false) }
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val pageBrush = AppColors.pageBrush(isDark)
    val desktopWallpaper = UiPrefs.themeCustomization.useSystemWallpaper
    val pageBaseBackdrop = rememberCanvasBackdrop { if (!desktopWallpaper) drawRect(pageBrush) }

    fun selectPage(target: Int) {
        val page = target.coerceIn(0, maxIdx)
        val generation = ++selectionGeneration
        requestedPage = page
        selectionJob?.cancel()
        selectionJob = selectionScope.launch {
            try {
                if (motion.canMove) pagerState.animateScrollToPage(page)
                else pagerState.scrollToPage(page)
            } finally {
                if (generation == selectionGeneration) requestedPage = null
            }
        }
    }
    DisposableEffect(Unit) { onDispose { selectionJob?.cancel() } }
    LaunchedEffect(uiState, pagerState, motion.canMove) {
        snapshotFlow { uiState.tabRequestRevision to uiState.selectedTab.coerceIn(0, maxIdx) }.collect { (revision, target) ->
            if (revision == uiState.tabRequestRevision && revision != consumedRequestRevision) {
                consumedRequestRevision = revision
                committedPage = target
                selectPage(target)
            }
        }
    }
    LaunchedEffect(pagerState, uiState) {
        snapshotFlow {
            (Triple(pagerState.settledPage, pagerState.isScrollInProgress, requestedPage) to selectionGeneration) to uiState.tabRequestRevision
        }.collect { (transition, revision) ->
            val (snapshot, generation) = transition
            val (settled, scrolling, pending) = snapshot
            if (!scrolling && pending == null && generation == selectionGeneration &&
                requestedPage == null && !pagerState.isScrollInProgress && pagerState.settledPage == settled &&
                revision == uiState.tabRequestRevision && revision == consumedRequestRevision &&
                uiState.selectedTab.coerceIn(0, maxIdx) == committedPage &&
                uiState.publishSettledTab(settled, revision)
            ) {
                committedPage = settled
                if (notifiedPage != settled) {
                    notifiedPage = settled
                    onOpenSection(MainSection.entries[settled])
                }
            }
        }
    }

    // ── 热调通道：进场读一次，之后靠 ContentObserver ───────────────
    var tuningVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        if (GlassTuning.refresh(context)) tuningVersion++
        val closeable = GlassTuning.startObserving(context) { tuningVersion++ }
        onDispose { closeable.close() }
    }
    val veilBlurDp = remember(tuningVersion) { GlassTuning.veilBlurDp }
    val recorderOff = remember(tuningVersion) { GlassTuning.recorderOff >= 0.5f }

    val pageChromes = remember { MainSection.entries.map { PageChrome() } }
    val chrome = pageChromes[section]
    val overlaysVisible = showAppearance || showPeriods || showReminders

    // 未读角标只挂在「消息」那颗上（MainSection.Notify），其余恒 0。
    val barItems = remember(unreadCount, UiPrefs.themeCustomization.showNavigationBadges) {
        MainSection.entries.map {
            GlassNavItem(
                label = it.label,
                iconRes = it.iconRes,
                badge = if (it == MainSection.Notify && UiPrefs.themeCustomization.showNavigationBadges) unreadCount else 0,
            )
        }
    }

    // ★★★ 独立审计（v3 #12 第 2 轮 P0#1）：这层 `GlassTheme {}` **不传 material**，
    //   而它内部把 `LocalBarMaterial` 交出去时用的是 `material ?: BarMaterial.LIQUID`
    //   （`glass/GlassSurface.kt:255` 那一次 give）⇒ 内层胜出，把外壳以内（顶栏纱 / 四个板块 / 「此刻」卡 / 底栏）
    //   **全部覆写回常量 LIQUID** —— 我上一轮在 `AppTheme.kt` 接的那道线净效果 = 0。
    //   台账还纠正了我写在清单里的**错根因**：真实来路不是「`GlassTheme()` 全工程零调用」
    //   （`git show HEAD` 里它一直在调），而是「被 provide 成常量 LIQUID」。机理写错下一轮还会照错的修。
    //   现在显式把外层那份传进来（`AppTheme.kt` 里按 `UiPrefs.glassMode` 算好的那一份）。
    GlassTheme(material = LocalBarMaterial.current) {
        // 在场页面的采样层；底栏读它的 value（不是直接持有，避免两页抢一份）。
        val activeBackdrop = remember { mutableStateOf<LayerBackdrop?>(null) }

        Box(modifier = modifier.fillMaxSize()) {
            // ── 顶栏：参考那份 IosTopBar（大标题 + 渐变纱 + 存活判定）──
            //
            // 折叠进度由页面写进 `chrome.progress`。本工程的页面目前还没接
            // （它们用的是 Miuix SmallTopAppBar），所以 progress 恒为 0 ⇒
            // 顶栏先以「大标题 + 无纱」的静止态呈现，结构先就位。
            // 接线清单见 docs/LOG.md T51。
            CompositionLocalProvider(
                LocalPageEntryGate provides entryGate,
                LocalPageEntryOwner provides entryOwner,
                LocalNavBarVisible provides true,
            ) {
                val customization = UiPrefs.themeCustomization
                val swipeEnabled = !overlaysVisible && customization.swipePages
                val crossAxis = customization.pagerGestureMode == com.muyao.wanzaihyperos.data.PagerGestureMode.CrossAxis
                val iosLike = customization.pagerGestureMode == com.muyao.wanzaihyperos.data.PagerGestureMode.IosLike
                val onSwipeStep by androidx.compose.runtime.rememberUpdatedState<(Int) -> Unit> { step ->
                    if (requestedPage == null && !pagerState.isScrollInProgress) {
                        val target = (pagerState.settledPage + step).coerceIn(0, maxIdx)
                        if (target != pagerState.settledPage) uiState.selectedTab = target
                    }
                }
                val crossAxisStep = remember { { step: Int -> onSwipeStep(step) } }
                HorizontalPager(
                    state = pagerState,
                    key = { MainSection.entries[it] },
                    beyondViewportPageCount = 0,
                    userScrollEnabled = swipeEnabled && !crossAxis,
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = if (iosLike) .35f else .5f,
                        snapAnimationSpec = if (iosLike) androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = 550f,
                        ) else androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    ),
                    overscrollEffect = null,
                    modifier = Modifier.fillMaxSize().crossAxisPageSwipe(swipeEnabled && crossAxis, crossAxisStep),
                ) { s ->
                // 层与 page key 同寿命，不能让邻页共用录制对象。
                val pageBackdrop = rememberLayerBackdrop()
                val isCurrent = s == pagerState.currentPage
                val recordEnabled = !recorderOff && isCurrent && !overlaysVisible
                val sampleReady = remember(pageBackdrop, recordEnabled) { mutableStateOf(false) }
                LaunchedEffect(pageBackdrop, recordEnabled) {
                    if (recordEnabled) {
                        withFrameNanos { }
                        withFrameNanos { }
                        sampleReady.value = true
                    }
                }
                LaunchedEffect(s, pageBackdrop, isCurrent, recordEnabled, sampleReady.value) {
                    if (isCurrent && recordEnabled && sampleReady.value) activeBackdrop.value = pageBackdrop
                    else if (activeBackdrop.value === pageBackdrop) activeBackdrop.value = null
                }
                DisposableEffect(pageBackdrop) {
                    onDispose {
                        if (activeBackdrop.value === pageBackdrop) activeBackdrop.value = null
                    }
                }

                // 「在场」按**这一份内容自己的板块**判：切换过渡期间，离场页的
                // pilot 必须停写 chrome.progress，否则两页会抢同一根顶栏。
                CompositionLocalProvider(
                    LocalPageActive provides (s == pagerState.settledPage && !pagerState.isScrollInProgress && !overlaysVisible),
                    LocalPageVisible provides (s == pagerState.settledPage && !overlaysVisible),
                    LocalPageChrome provides pageChromes[s],
                    LocalLargeTitle provides MainSection.entries[s].label,
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (recordEnabled)
                            Modifier.layerBackdrop(pageBackdrop).trackBackdropPosition(pageBackdrop)
                        else Modifier)
                        // 背景必须在录制层内；Bare 保持让开，由消费端先铺根背景兜底。
                        .containerBackground(isDark),
                ) {
                    // 内容不留底部内距 —— 让列表一直滚到玻璃底下，否则玻璃背后永远是空白。
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (MainSection.entries[s]) {
                            MainSection.Home -> HomeSectionPage(
                                graph = graph,
                                onOpenJw = onOpenJw,
                                onOpenTimetable = onOpenTimetable,
                                onOpenTodos = onOpenTodos,
                                onOpenMessages = onOpenMessages,
                                onOpenRollcall = onOpenRollcall,
                                onOpenAppearance = { showAppearance = true },
                                onOpenSzty = onOpenSzty,
                                morphState = onHomeMorphState,
                            )

                            MainSection.Timetable -> TimetableSectionPage(
                                graph = graph,
                                uiState = uiState,
                                onOpenJw = onOpenJw,
                                onOpenTron = onOpenTron,
                            )

                            MainSection.Notify -> NotifySectionPage(
                                graph = graph,
                                selectedFilter = uiState.notifyFilter,
                                onFilterChange = { uiState.notifyFilter = it },
                                listState = uiState.notifyListState,
                                listPositions = uiState.notifyListPositions,
                                onOpenRollcall = onOpenRollcall,
                                onOpenTodos = onOpenTodos,
                                onOpenUrl = onOpenUrl,
                                onRead = onReadMessage,
                                onReadAll = onReadAllMessage,
                                onOpenAccounts = onOpenAccounts,
                            )

                            MainSection.Apps -> AppsSectionPage(
                                graph = graph,
                                onOpenApp = onOpenApp,
                                onOpenSearch = onOpenSearch,
                                onOpenScan = onOpenScan,
                            )

                            MainSection.Settings -> SettingsSectionPage(
                                graph = graph,
                                uiState = uiState,
                                onOpenSettings = onOpenSettings,
                                onOpenLogin = onOpenLogin,
                                onOpenAppearance = { showAppearance = true },

                                onOpenPeriods = { showPeriods = true },
                                onOpenReminders = { showReminders = true },
                                onOpenJw = onOpenJw,
                                onOpenZhgy = onOpenZhgy,
                                onOpenSzty = onOpenSzty,
                                // CJH52 的临时码要「在应用里打开一次网页」才激活，
                                // 所以那一行的「去哪登」就是切到应用板块。
                                onOpenApps = { uiState.selectedTab = MainSection.Apps.ordinal },
                                onOpenAbout = onOpenAbout,
                                onOpenPreferences = onOpenPreferences,
                                onOpenAccounts = onOpenAccounts,
                                refreshKey = navDepth,
                            )
                        }
                    }
                }
                }
            }
            }   // CompositionLocalProvider(LocalPageChrome …)

            // ── 固定顶栏：不参与分页位移 ──
            //
            // 搜索入口放在顶栏 `actions`（右上角）：上一版是浮在底栏上方的胶囊，
            // 业主反馈「放你这会误触」—— 它压在列表/课表内容上，滚动时手指正好划过。
            // 右上角那块本来就没有内容手势，折叠态与展开态也都在同一个位置。
            IosTopBar(
                title = if (section == 0) UiPrefs.themeCustomization.homeTitle.ifBlank { MainSection.entries[section].label } else MainSection.entries[section].label,
                chrome = chrome,
                canGoBack = false,
                onBack = {},
                backdrop = activeBackdrop.value.takeIf { !recorderOff }
                    ?.let { rememberCombinedBackdrop(pageBaseBackdrop, it) },
                // 这一屏顶栏后面真正铺的色：首页是天空最上一站（浅色主题 × 夜航时它很暗），
                // 其它页是页面渐变最上一站 —— 都**不是**主题令牌。
                backdropColor = if (section == 0) {
                    com.muyao.wanzaihyperos.ui.home.skyLookOf(
                        com.muyao.wanzaihyperos.ui.home.SkyPreset.of(UiPrefs.wallpaperPreset),
                        java.time.LocalTime.now().hour,
                    ).colorAt(0f)
                } else {
                    com.muyao.wanzaihyperos.ui.design.AppColors.pageTopColor(
                        MiuixTheme.colorScheme.background.luminance() < 0.5f,
                    )
                },
                veilBlurDp = UiPrefs.themeCustomization.topBlur.toFloat(),
                actions = {
                    // 小球不在顶栏：业主 2026-10-25「小球不要单独一个图层，要与其他内容同层、
                    // 并且可以变化位置」⇒ 五页各自把球放进第一个区块标题的左边（[AiBallRow]），
                    // 跟着列表一起滚。顶栏只留搜索钮。
                    // 业主 2026-10-21：课表与应用的右上角不要搜索钮（那两页有页内入口）。
                    // 首页、消息保留。
                    // 业主 2026-10-25：「在设置页面也可以加上」⇒ 设置页也放进这颗。
                    if (MainSection.entries[section] == MainSection.Home ||
                        MainSection.entries[section] == MainSection.Notify ||
                        MainSection.entries[section] == MainSection.Settings
                    ) {
                    GlasmioSearchButton(
                        onClick = onOpenSearch,
                        backdrop = activeBackdrop.value.takeIf { motion.glassIsTranslucent && !recorderOff }
                            ?.let { rememberCombinedBackdrop(pageBaseBackdrop, it) }
                            ?: LocalGlassBackdrop.current,
                    )
                    }
                    // 「整理」不在顶栏：板 `.topbar` 只有标题与搜索钮，
                    // 而 `apps.png` 里那颗「整理」是**「常用」标题行右侧**的文字按钮。
                    // 上一版按注释（写的是 `.topbar .meta .tidybtn`）把它放在顶栏，
                    // 真机截图与板面一比就露馅：板面顶栏根本没有这颗。
                },
            )

            // ── 固定底栏：Pager 的单实例兄弟节点 ──
            AnimatedVisibility(
                visible = true,
                enter = (if (com.muyao.wanzaihyperos.ui.design.rememberMotion().canMove)
                        slideInVertically(
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
                            initialOffsetY = { it },
                        ) else fadeIn()) + fadeIn(tween(160)),
                exit = slideOutVertically(
                    animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessHigh),
                    targetOffsetY = { it },
                ) + fadeOut(tween(140)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                GlassNavBar(
                    items = barItems,
                    selected = section,
                    onSelect = { idx ->
                        if (idx != pagerState.settledPage) navHaptics.tick()
                        committedPage = idx
                        uiState.selectedTab = idx
                        consumedRequestRevision = uiState.tabRequestRevision
                        selectPage(idx)
                    },
                    backdrop = activeBackdrop.value.takeIf { motion.glassIsTranslucent && !recorderOff }
                        ?.let { rememberCombinedBackdrop(pageBaseBackdrop, it) },
                )
            }

            // ── 外觀子頁：全屏覆蓋，**蓋住底欄與頂欄** ──────────────
            //
            // 它只改這台手機怎麼顯示，不屬於任何板塊。做成殼內覆蓋層而不是新路由：
            // 關掉就回到原來那一頁，不新增 Route、不動根導航。
            // 進場 240ms / 出場 140ms（出場必須比進場快）；減弱動效時只留淡入。
            AnimatedVisibility(
                visible = showAppearance,
                enter = (if (motion.canMove) {
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Motion.OVERLAY_IN_DAMPING,
                            stiffness = Motion.OVERLAY_IN_STIFFNESS,
                        ),
                        initialOffsetY = { it / 14 },
                    )
                } else {
                    fadeIn(tween(120))
                }) + fadeIn(tween(200)),
                exit = (if (motion.canMove) {
                    slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = Motion.OVERLAY_OUT_DAMPING,
                            stiffness = Motion.OVERLAY_OUT_STIFFNESS,
                        ),
                        targetOffsetY = { it / 20 },
                    )
                } else {
                    fadeOut(tween(120))
                }) + fadeOut(tween(140)),
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopStart),
            ) {
                CompositionLocalProvider(LocalPageActive provides (showAppearance && !showPeriods && !showReminders)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .containerBackground(isDark),
                ) {
                    AppearancePage(
                        onClose = { showAppearance = false },
                        graph = graph,
                        themeMode = uiState.themeMode,
                        // ★ B4-14（2026-10-23 工单第 13 轮）：这里**只改了运行期状态、没落盘** ⇒
                        //   杀进程重进就回退（设置页那一份是写盘的，两处行为不一致）。
                        //   现在与 `SettingsSectionPage` 对齐：改状态 + 写 `UiPrefs`。
                        onThemeMode = {
                            uiState.themeMode = it
                        },
                        onOpenCrop = onOpenCrop,
                    )
                }
                }
            }
            // 返工总单 A4：节次时间表独立页。进出场与外观/辅助显示完全一致 ——
            // 它们是同一族「设置里的二级页」，切过去不该像换了个应用。
            AnimatedVisibility(
                visible = showPeriods,
                enter = (if (motion.canMove) {
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Motion.OVERLAY_IN_DAMPING,
                            stiffness = Motion.OVERLAY_IN_STIFFNESS,
                        ),
                        initialOffsetY = { it / 14 },
                    )
                } else {
                    fadeIn(tween(120))
                }) + fadeIn(tween(200)),
                exit = (if (motion.canMove) {
                    slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = Motion.OVERLAY_OUT_DAMPING,
                            stiffness = Motion.OVERLAY_OUT_STIFFNESS,
                        ),
                        targetOffsetY = { it / 20 },
                    )
                } else {
                    fadeOut(tween(120))
                }) + fadeOut(tween(140)),
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopStart),
            ) {
                CompositionLocalProvider(LocalPageActive provides (showPeriods && !showReminders)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .containerBackground(isDark),
                ) {
                    com.muyao.wanzaihyperos.ui.main.pages.PeriodsPage(
                        onClose = { showPeriods = false },
                        onSearch = onOpenSearch,
                    )
                }
                }
            }
            // 返工总单 A5：上课提醒独立页（三级：首屏一行 → 本页 → 折叠诊断）。
            AnimatedVisibility(
                visible = showReminders,
                enter = (if (motion.canMove) {
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Motion.OVERLAY_IN_DAMPING,
                            stiffness = Motion.OVERLAY_IN_STIFFNESS,
                        ),
                        initialOffsetY = { it / 14 },
                    )
                } else {
                    fadeIn(tween(120))
                }) + fadeIn(tween(200)),
                exit = (if (motion.canMove) {
                    slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = Motion.OVERLAY_OUT_DAMPING,
                            stiffness = Motion.OVERLAY_OUT_STIFFNESS,
                        ),
                        targetOffsetY = { it / 20 },
                    )
                } else {
                    fadeOut(tween(120))
                }) + fadeOut(tween(140)),
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopStart),
            ) {
                CompositionLocalProvider(LocalPageActive provides showReminders) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .containerBackground(isDark),
                ) {
                    com.muyao.wanzaihyperos.ui.main.pages.RemindersPage(
                        onClose = { showReminders = false },
                        onSearch = onOpenSearch,
                    )
                }
                }
            }
        }
    }
}
