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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
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
import com.muyao.wanzaihyperos.ui.main.pages.AppearancePage
import com.muyao.wanzaihyperos.ui.main.pages.AppsSectionPage
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.main.pages.HomeSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.NotifySectionPage
import com.muyao.wanzaihyperos.ui.main.pages.SettingsSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.TimetableSectionPage
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch

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
    /** A6：设置首屏「名称与图标」→ 品牌与图标页。 */
    onOpenBrand: () -> Unit = {},
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
     * 「消息」那颗 tab 上的未读数。0 = 不画角标。
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
    var section by remember { mutableIntStateOf(uiState.selectedTab.coerceIn(0, maxIdx)) }

    /** 切页方向：+1 右、-1 左、0 = 不滑（只淡入 + 微缩放）。参考的 transitionSpec 读它。 */
    var navDir by remember { mutableIntStateOf(0) }

    /** 底栏落位的轻触反馈（点位只有这一个；滚动/悬停一律不给）。 */
    val navHaptics = com.muyao.wanzaihyperos.ui.design.rememberHaptics()

    /**
     * 水平拖动期间给内容的位移（像素）。
     *
     * ★ B4-08（2026-10-23，逐帧录屏实测）：以前松手那句是 `swipePx = 0f` ——
     *   内容在**一帧之内弹回原位**，之后才播换页的淡入淡出（59.3fps 的录屏里，
     *   "回到原位"发生在一帧里，前后各有一小段静止）⇒ 读起来就是"拖过去→啪一下弹回→再换页"
     *   的双段运动，正是他说的「不丝滑」。
     *   现在改成**动画收口**：要换页就顺着拖动方向再走一小段（与 `AnimatedContent` 同向），
     *   换完之后再归零；不换页就弹回 0（带一点临界阻尼，不是瞬移）。
     */
    val swipePx = remember { androidx.compose.animation.core.Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    // 每次新的拖动 +1：延迟收口的那支协程发现代数变了就放弃，
    // 否则"上一次松手的 280ms 后回收"会跟"这一次的拖动"抢同一个 Animatable。
    var swipeGen by remember { mutableIntStateOf(0) }

    /**
     * 「外觀」子頁：殼內全屏覆蓋層，**不是新路由**。
     *
     * 它只改這台手機怎麼顯示，不屬於任何板塊；蓋住底欄是刻意的
     * （否則他會以為還在板塊裡、卻點不動）。
     */
    var showAppearance by remember { mutableStateOf(false) }
    // 「动画与辅助显示」：与外观页同一种承载方式（壳内全屏覆盖），不新增 Route。
    var showAccessibility by remember { mutableStateOf(false) }
    // 返工总单 A4：节次时间表从设置项内联展开改成独立页（与外观/辅助显示同一套覆盖层）。
    var showPeriods by remember { mutableStateOf(false) }
    // 返工总单 A5：上课提醒独立页。
    var showReminders by remember { mutableStateOf(false) }
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()

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

    // 顶栏折叠进度（页面经 `rememberPageChromePilot()` 往里写）
    val chrome = remember { PageChrome() }

    // ★ 換板塊時把折疊度歸零。
    //
    // 為什麼必須有這一行：progress 是**頁面**寫的，而「首頁」這一塊刻意不接
    // 大標題（它的頂部是他要的「大日期 + 每日一句」，再壓一行「首頁」就是雙標題）。
    // 沒人寫，上一頁留下的值就會**跟過來** —— 從設定滾到底再點首頁，
    // 頂欄那條紗會一直掛著，看起來像首頁壞了。
    LaunchedEffect(section) { chrome.progress = 0f }

    // 未读角标只挂在「消息」那颗上（MainSection.Notify），其余恒 0。
    val barItems = remember(unreadCount) {
        MainSection.entries.map {
            GlassNavItem(
                label = it.label,
                iconRes = it.iconRes,
                badge = if (it == MainSection.Notify) unreadCount else 0,
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
                LocalPageChrome provides chrome,
                LocalLargeTitle provides MainSection.entries[section].label,
                LocalNavBarVisible provides true,
            ) {
                AnimatedContent(
                    targetState = section,
                transitionSpec = {
                    // v2（審計 §2#2）：切板塊是「幾十次/天」檔，砍到 170ms + 6dp，
                    // **去掉縮放層** —— 縮放沒有目的，只會增加存在感。
                    // `it / 60` 在 1080px 寬的機器上約 18px ≈ 6dp。
                    val enter = tween<Float>(170, easing = FastOutSlowInEasing)
                    val exit = tween<Float>(140, easing = FastOutSlowInEasing)
                    // 位移的 spec 是 IntOffset，不能复用上面那两条 Float 的。
                    val moveIn = tween<IntOffset>(170, easing = FastOutSlowInEasing)
                    val moveOut = tween<IntOffset>(140, easing = FastOutSlowInEasing)
                    if (navDir == 0) {
                        fadeIn(enter).togetherWith(fadeOut(exit))
                    } else {
                        val d = navDir
                        (
                            fadeIn(enter) +
                                slideInHorizontally(moveIn) { it / 60 * d }
                            )
                            .togetherWith(
                                fadeOut(exit) +
                                    slideOutHorizontally(moveOut) { -it / 90 * d },
                            )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    // 拖动期间整块内容跟着手指走一点（松手归零，真正的换页交给下面
                    // 那条 280ms 的 transitionSpec）。**没有这层位移，滑动就是"拖了没反应"**。
                    .graphicsLayer { translationX = swipePx.value }
                    .swipeToSwitchSection(
                        enabled = section > 0 || section < maxIdx,
                        onDrag = { v ->
                            swipeGen++
                            swipeScope.launch { swipePx.snapTo(v) }
                        },
                        onSwipeEnd = { deltaPx, velocityPxPerSec, widthPx ->
                            // 先把"手指停在哪"作为起点；下面两条分支都从这里动画收口。
                            val from = swipePx.value
                            // ★ B4-08（业主 2026-10-23「底栏之间的左右滑切换不丝滑」）：
                            //   以前只看**位移**（>0.28 宽）⇒ 轻轻一甩翻不了页（要拖够距离），
                            //   而慢慢推过去又会突然翻 ⇒ 手感"发黏/发硬"。
                            //   现在按标准走**落点投影**：`projected = 位移 + 速度 × 投影时长`。
                            //   ⚠️ 不是"速度阈值"（那条老注释的担心仍在）：慢推时 v≈0 ⇒ 投影≈位移，
                            //   行为与以前完全一致；只有真的甩起来才会多走那一段。
                            //   投影时长用与周条同一个热调键 `wz_fling_ms`（默认 110ms），不另造一个数。
                            // 判据抽到 `sectionSwipeStep`（生产与单测共用），这里只负责落位
                            val dir = com.muyao.wanzaihyperos.ui.main.sectionSwipeStep(
                                deltaPx = deltaPx,
                                velocityPxPerSec = velocityPxPerSec,
                                widthPx = widthPx,
                            )
                            if (dir != 0) {
                                val target = (section + dir).coerceIn(0, maxIdx)
                                if (target != section) {
                                    navDir = dir
                                    section = target
                                    // **不要在这里归零**：让内容停在手指松开的位置，等换页动画（280ms）
                                    // 把新页面铺好之后再收口 —— 归零发生在"内容刚换掉"的那一刻，
                                    // 眼睛看到的是新页面滑进位置，而不是旧页面砰地弹回去。
                                    val gen = ++swipeGen
                                    swipeScope.launch {
                                        kotlinx.coroutines.delay(280)
                                        if (gen != swipeGen) return@launch   // 他又开始拖了，别抢
                                        swipePx.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                                        )
                                    }
                                    return@swipeToSwitchSection
                                }
                            }
                            // 不换页：带阻尼地弹回 0（不是瞬移）
                            swipeScope.launch {
                                swipePx.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = com.muyao.wanzaihyperos.ui.design.Motion.OVERLAY_OUT_DAMPING,
                                        stiffness = com.muyao.wanzaihyperos.ui.design.Motion.OVERLAY_IN_STIFFNESS,
                                    ),
                                )
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

                // 「在场」按**这一份内容自己的板块**判：切换过渡期间，离场页的
                // pilot 必须停写 chrome.progress，否则两页会抢同一根顶栏。
                CompositionLocalProvider(LocalPageActive provides (s == section)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 容器背景两版：`Designed` 铺页内纸色 + 天光；`Bare` 让开，透出根上那层整页渐变。
                        .containerBackground(isDark)
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
                                onOpenRollcall = onOpenRollcall,
                                onOpenAppearance = { showAppearance = true },
                                onOpenSzty = onOpenSzty,
                            )

                            MainSection.Timetable -> TimetableSectionPage(
                                graph = graph,
                                uiState = uiState,
                                onOpenJw = onOpenJw,
                                onOpenTron = onOpenTron,
                            )

                            MainSection.Notify -> NotifySectionPage(
                                graph = graph,
                                onOpenRollcall = onOpenRollcall,
                                onOpenTodos = onOpenTodos,
                                onOpenUrl = onOpenUrl,
                                onRead = onReadMessage,
                                onReadAll = onReadAllMessage,
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
                                onOpenAccessibility = { showAccessibility = true },
                                onOpenPeriods = { showPeriods = true },
                                onOpenReminders = { showReminders = true },
                                onOpenJw = onOpenJw,
                                onOpenZhgy = onOpenZhgy,
                                onOpenSzty = onOpenSzty,
                                // CJH52 的临时码要「在应用里打开一次网页」才激活，
                                // 所以那一行的「去哪登」就是切到应用板块。
                                onOpenApps = { uiState.selectedTab = MainSection.Apps.ordinal },
                                onOpenAbout = onOpenAbout,
                                onOpenBrand = onOpenBrand,
                                onOpenAccounts = onOpenAccounts,
                                refreshKey = navDepth,
                            )
                        }
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
                veilBlurDp = veilBlurDp,
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
                    GlassSearchButton(
                        onClick = onOpenSearch,
                        // 采样层只在「真的录到内容」时才交给它 —— Miuix 对空样本
                        // 不是没模糊，而是画实心黑（`liquid/GlassBar.kt` 那条闸门）。
                        backdrop = if (backdropIsLive(activeBackdrop.value)) activeBackdrop.value else null,
                        liveBlur = btnLive,
                    )
                    }
                    // 「整理」不在顶栏：板 `.topbar` 只有标题与搜索钮，
                    // 而 `apps.png` 里那颗「整理」是**「常用」标题行右侧**的文字按钮。
                    // 上一版按注释（写的是 `.topbar .meta .tidybtn`）把它放在顶栏，
                    // 真机截图与板面一比就露馅：板面顶栏根本没有这颗。
                },
            )

            // ── 底栏：AnimatedContent 的兄弟节点，自己不参与页面动画 ──
            AnimatedVisibility(
                visible = true,
                enter = (if (com.muyao.wanzaihyperos.ui.design.rememberMotion().canMove)
                        slideInVertically(
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
                            initialOffsetY = { it },
                        ) else fadeIn()) + fadeIn(tween(160)),
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
                            // 轻：滑块**吸到位**那一下。这是"落位"，不是"成功"，
                            // 所以用 tick 而不是 commit（点位表在 `Haptics` 档头）。
                            navHaptics.tick()
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
                            com.muyao.wanzaihyperos.data.UiPrefs.saveThemeMode(it.name)
                        },
                        // 稿 `appearance` 最后一行「减少动态与高对比 ›」的出口。
                        // 外观页自己不开第二张表，只把这一跳交给壳 —— 两个覆盖层同一种承载方式。
                        onOpenAccessibility = {
                            // ★ 返工总单 C5：从外观进辅助显示时**不要先关掉外观**。
                            //   以前先 showAppearance = false，返回后直接掉回主壳，
                            //   外观的滚动位置也一起丢了 —— 二/三级返回链断在这里。
                            //   现在两层同时挂着：外观在下、辅助显示在上，关掉上层自然回下层。
                            showAccessibility = true
                        },
                        onOpenCrop = onOpenCrop,
                    )
                }
            }
            // ── 动画与辅助显示：同一种承载方式（壳内覆盖层，不新增 Route）──────
            //
            // 它与外观页是「展示偏好」的两半：一半决定**看起来怎样**，一半决定
            // **动起来怎样**。所以用同一套进出场参数，切过去不会像换了个应用。
            AnimatedVisibility(
                visible = showAccessibility,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .containerBackground(isDark),
                ) {
                    com.muyao.wanzaihyperos.ui.main.pages.AccessibilityPage(
                        onClose = { showAccessibility = false },
                    )
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .containerBackground(isDark),
                ) {
                    com.muyao.wanzaihyperos.ui.main.pages.PeriodsPage(
                        onClose = { showPeriods = false },
                    )
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .containerBackground(isDark),
                ) {
                    com.muyao.wanzaihyperos.ui.main.pages.RemindersPage(
                        onClose = { showReminders = false },
                    )
                }
            }
        }
    }
}
