package com.muyao.wanzaihyperos.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.glass.GlassTheme
import com.muyao.wanzaihyperos.ui.design.liquid.GlassNavBar
import com.muyao.wanzaihyperos.ui.design.liquid.GlassNavItem
import com.muyao.wanzaihyperos.ui.design.liquid.GlassSearchButton
import com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning
import com.muyao.wanzaihyperos.ui.design.liquid.IosTopBar
import com.muyao.wanzaihyperos.ui.design.liquid.LocalLargeTitle
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageChrome
import com.muyao.wanzaihyperos.ui.design.liquid.PageChrome
import com.muyao.wanzaihyperos.ui.design.liquid.rememberPageBackdrop
import com.muyao.wanzaihyperos.ui.design.liquid.backdropIsLive
import com.muyao.wanzaihyperos.ui.main.pages.AppsSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.HomeSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.NotifySectionPage
import com.muyao.wanzaihyperos.ui.main.pages.SettingsSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.TimetableSectionPage
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主殼：**结构照抄参考（变焦环 MainActivity）**。
 *
 * ```
 * Box
 *  ├ AnimatedContent(板块)          ← 只有页面参与切换动画
 *  │    └ 每页一份采样层 layerBackdrop → 写进 activeBackdrop
 *  └ AnimatedVisibility(底栏)        ← 底栏在 AnimatedContent **外面**
 *       └ GlassNavBar(backdrop = activeBackdrop.value)
 * ```
 *
 * ## 上一版为什么是「雷霆动效」
 *
 * 我把底栏放进了 `AnimatedContent` 的 lambda 里 ⇒ 切页时底栏自己也在淡入 + 缩放，
 * 而且过渡那 200~280ms 内**同时存在两份底栏**（进场页一份、离场页一份）。
 * 参考那边底栏是 AnimatedContent 的**兄弟节点**，只跟着 `activeBackdrop` 换采样源，
 * 自己不参与页面动画 —— 现在照它改回来了。
 *
 * ## 三条从参考学来的硬纪律（别再改回去）
 *
 * 1. **每页一份采样层**：Miuix 的 `LayerBackdropNode.onDetach()` 会把 `layerCoordinates`
 *    置成 null，共享一份时离场页一 detach 就把坐标清了 ⇒ 之后全部采到空层
 *    （表现是「只有首次打开正常」）。
 * 2. **只有在场那一页往 `activeBackdrop` 写**，离场页不再写 —— 否则切页那一瞬两边抢同一个值会闪。
 * 3. **取槽位/建层的键必须是「这一块内容自己是哪个板块」（lambda 参数 `s`），不能用 `section`**：
 *    切页那一瞬离场页也会用 current 去取，于是把自己那份状态写进下一页的槽位里。
 *
 * ## 底栏的显隐动画（参考原值）
 *
 * `slideInVertically(spring(0.9f, 600f)) + fadeIn(160)` /
 * `slideOutVertically(spring(1.0f, 900f)) + fadeOut(140)`。
 * 本工程二级页是**独立导航路由**（整壳会离开 composition），所以现在恒 `visible = true`；
 * 等以后壳内做栈，把这个布尔换成「栈空不空」就自动生效。
 *
 * ## 热调通道（参考自带）
 *
 * `adb shell settings put global hzr_glass_bar_blur 8` / `hzr_glass_lens 40` /
 * `hzr_glass_pill_blur 8` / `hzr_glass_off 1`（关采样层做对照）。
 * 必须经 `tuningVersion` 走一层，否则改了 Settings 界面不动、量出来的数是假的。
 */
@Composable
fun WanzaiMainShell(
    graph: AppGraph,
    uiState: AppUiState,
    onOpenApp: (com.muyao.wanzaihyperos.data.PortalApp) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSection: (MainSection) -> Unit,
    onOpenJw: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 「設定 → 去重新登入」：真的把使用者送回登入頁。 */
    onOpenLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val maxIdx = MainSection.entries.size - 1
    var section by remember { mutableIntStateOf(uiState.selectedTab.coerceIn(0, maxIdx)) }

    /** 切页方向：+1 右、-1 左、0 = 不滑（只淡入 + 微缩放）。参考的 transitionSpec 读它。 */
    var navDir by remember { mutableIntStateOf(0) }

    /** 水平拖动期间给内容的位移（像素）。松手立刻归零，换页动画由 AnimatedContent 接手。 */
    var swipePx by remember { mutableFloatStateOf(0f) }

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current

    // 外部导航（例如「我的→設定」）改 selectedTab 时，跟着切板块。
    LaunchedEffect(uiState.selectedTab) {
        val target = uiState.selectedTab.coerceIn(0, maxIdx)
        if (target != section) section = target
    }

    // ── 热调通道：进场读一次，之后靠 ContentObserver ───────────────
    var tuningVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        if (GlassTuning.refresh(context)) tuningVersion++
        val closeable = GlassTuning.startObserving(context) { tuningVersion++ }
        onDispose { closeable.close() }
    }
    val blurDp = remember(tuningVersion) { GlassTuning.barBlurDp }
    val lensDp = remember(tuningVersion) { GlassTuning.lensDp }
    val veilBlurDp = remember(tuningVersion) { GlassTuning.veilBlurDp }
    val recorderOff = remember(tuningVersion) { GlassTuning.recorderOff >= 0.5f }
    /** 悬浮胶囊要不要真的采页面内容（`hzr_glass_btn_live`）。默认关，理由见 GlassTuning 注释。 */
    val btnLive = remember(tuningVersion) { GlassTuning.btnLive >= 0.5f }

    // 顶栏折叠进度（页面往里写；目前页面还没接，先恒 0）
    val chrome = remember { PageChrome() }

    val barItems = remember { MainSection.entries.map { GlassNavItem(it.label, it.icon) } }

    GlassTheme {
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
                LocalPageChrome provides chrome,
                LocalLargeTitle provides MainSection.entries[section].label,
                LocalPageActive provides true,
                LocalNavBarVisible provides true,
            ) {
                AnimatedContent(
                    targetState = section,
                transitionSpec = {
                    val fade = tween<Float>(200, easing = FastOutSlowInEasing)
                    val slide = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                    if (navDir == 0) {
                        (fadeIn(fade) + scaleIn(fade, initialScale = 0.985f))
                            .togetherWith(fadeOut(fade) + scaleOut(fade, targetScale = 0.99f))
                    } else {
                        val d = navDir
                        (
                            fadeIn(fade) +
                                slideInHorizontally(slide) { it / 5 * d } +
                                scaleIn(fade, initialScale = 0.97f)
                            )
                            .togetherWith(
                                fadeOut(tween(160)) +
                                    slideOutHorizontally(slide) { -it / 9 * d } +
                                    scaleOut(fade, targetScale = 0.98f),
                            )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    // 拖动期间整块内容跟着手指走一点（松手归零，真正的换页交给下面
                    // 那条 280ms 的 transitionSpec）。**没有这层位移，滑动就是"拖了没反应"**。
                    .graphicsLayer { translationX = swipePx }
                    .swipeToSwitchSection(
                        enabled = section > 0 || section < maxIdx,
                        onDrag = { swipePx = it },
                        onSwipeEnd = { deltaPx, widthPx ->
                            swipePx = 0f
                            // 越界比例 + 手势方向一起判，**不靠速度阈值**：
                            // 这台机 120Hz，速度阈值会把"慢慢推过去"误判成不翻页。
                            if (abs(deltaPx) > widthPx * 0.28f) {
                                val dir = if (deltaPx < 0f) 1 else -1
                                val target = (section + dir).coerceIn(0, maxIdx)
                                if (target != section) {
                                    navDir = dir
                                    section = target
                                }
                            }
                        },
                    ),
            ) { s ->
                // ★ 键是「这一块内容自己是哪个板块」，不能用 section（见档头纪律 3）
                val pageBackdrop = rememberPageBackdrop(
                    recordColor = MiuixTheme.colorScheme.background,
                )
                LaunchedEffect(s, pageBackdrop) {
                    if (s == section) activeBackdrop.value = pageBackdrop
                }
                DisposableEffect(pageBackdrop) {
                    onDispose {
                        if (activeBackdrop.value === pageBackdrop) activeBackdrop.value = null
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.pageBrush(isDark))
                        .layerBackdrop(pageBackdrop),
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
                            )

                            MainSection.Timetable -> TimetableSectionPage(
                                graph = graph,
                                uiState = uiState,
                            )

                            MainSection.Notify -> NotifySectionPage(
                                graph = graph,
                                onOpenMessages = onOpenMessages,
                                onOpenNews = onOpenNews,
                            )

                            MainSection.Apps -> AppsSectionPage(
                                graph = graph,
                                onOpenApp = onOpenApp,
                            )

                            MainSection.Settings -> SettingsSectionPage(
                                graph = graph,
                                uiState = uiState,
                                onOpenSettings = onOpenSettings,
                                onOpenLogin = onOpenLogin,
                            )
                        }
                    }
                }
            }
            }   // CompositionLocalProvider(LocalPageChrome …)

            // ── 顶栏（AnimatedContent 之外：它不跟页面一起淡入缩放）────
            //
            // 搜索入口放在顶栏 `actions`（右上角）：上一版是浮在底栏上方的胶囊，
            // 业主反馈「放你这会误触」—— 它压在列表/课表内容上，滚动时手指正好划过。
            // 右上角那块本来就没有内容手势，折叠态与展开态也都在同一个位置。
            IosTopBar(
                title = MainSection.entries[section].label,
                chrome = chrome,
                canGoBack = false,
                onBack = {},
                backdrop = activeBackdrop.value,
                veilBlurDp = veilBlurDp,
                actions = {
                    GlassSearchButton(
                        onClick = onOpenSearch,
                        // 采样层只在「真的录到内容」时才交给它 —— Miuix 对空样本
                        // 不是没模糊，而是画实心黑（`liquid/GlassBar.kt` 那条闸门）。
                        backdrop = if (backdropIsLive(activeBackdrop.value)) activeBackdrop.value else null,
                        liveBlur = btnLive,
                    )
                },
            )

            // ── 底栏：AnimatedContent 的兄弟节点，自己不参与页面动画 ──
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
                    initialOffsetY = { it },
                ) + fadeIn(tween(160)),
                exit = slideOutVertically(
                    animationSpec = spring(dampingRatio = 1.0f, stiffness = 900f),
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
                        if (idx != section) {
                            navDir = if (idx > section) 1 else -1
                        }
                        section = idx
                        uiState.selectedTab = idx
                        onOpenSection(MainSection.entries[idx])
                    },
                    backdrop = activeBackdrop.value,
                    blurDp = blurDp,
                    lensDp = lensDp,
                )
            }
        }
    }
}
