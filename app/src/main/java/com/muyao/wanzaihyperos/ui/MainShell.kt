package com.muyao.wanzaihyperos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.muyao.wanzaihyperos.ui.design.SmearMath
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import com.muyao.wanzaihyperos.data.PortalApp

/** 底部四个 tab。索引与 [MainShell] 的 `selectedTab` 一一对应。 */
private enum class Tab(val label: String) {
    Home("首页"),
    Apps("应用"),
    Messages("消息"),
    Mine("我的"),
}

/**
 * 主壳：`Scaffold` + 底部 `NavigationBar`（四个 tab）。
 *
 * **Insets 与主题的所有权（方案要求「有明确 owner」）**：
 * - `MiuixTheme` 由 [WanzaiTheme] 在根节点**唯一**提供；
 * - 这里只放**一个** `Scaffold`，它负责把顶栏/底栏高度算进 `PaddingValues`，
 *   页面**必须**消费这个 padding（各页已把 `contentPadding` 接到 `LazyColumn/LazyGrid`）；
 * - 主壳**不再给页面套额外 padding**，避免「双重留白」；
 * - 底部栏自己处理导航条 inset（`defaultWindowInsetsPadding = true` 默认行为），
 *   页面内容因此不会被系统手势条压住。
 *
 * tab 选中态只有**一个**来源（[AppUiState.selectedTab]），
 * 不从路由推导 —— 否则会出现「chrome 高亮在 A、内容是 B」的双份真相。
 */
@Composable
fun MainShell(
    uiState: AppUiState,
    graph: com.muyao.wanzaihyperos.app.AppGraph,
    onOpenApp: (PortalApp) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMessages: () -> Unit,
    onRelogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    /** 點首頁「待辦/申請/已辦」三格（B3）：參數是清單名。 */
    onOpenBusyness: ((String) -> Unit)? = null,
    /** 點首頁「全部待办」入口（B6）：打開多來源待辦看板。 */
    onOpenTodoBoard: (() -> Unit)? = null,
    /** B18：點首頁「教务系统」入口 —— 考試信息 / 學生成績（只讀）。 */
    onOpenJw: (() -> Unit)? = null,
    /**
     * 未读数。**`null` = 不知道**（还没取到、或取失败）。
     *
     * ★ 这里刻意用可空 `Int` 而不是 `Int`：角标画不出「—」，
     * 所以「不知道」时不画角标 —— 但那**不等于 0**，
     * 具体「未读 —」的信息由消息页承担（那里能显示文字）。
     * 用 `Int` 默认 0 就会变成「替服务端说没有未读」，是业主提过的原始问题。
     */
    unreadCount: Int? = null,
) {
    val tabs = remember { Tab.entries.toList() }
    // 背景漸層要跟隨深/淺色。
    //
    // ⚠️ `MiuixTheme.colorScheme`（`Colors`）**沒有** `isDark` ——
    // 那個屬性在 `ThemeController` 上，而主殼這層拿不到 controller。
    // 所以用**色板自身的亮度**判斷：`background` 是深色板時亮度低。
    // 這比「猜系統設定」可靠（使用者可以強制淺色而系統是深色）。
    val isDark = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background.luminance() < 0.5f
    // ★ 背景漸層 + Scaffold 透明底。
    //
    // 為什麼要在 Scaffold **外面**包一層 Box：
    // Miuix `Scaffold` 的 `containerColor` 型別是 `Color`（純色），**吃不下 Brush**。
    // 而規範 §2 明確說「沒有背景層次，玻璃就是灰塊」——
    // 漸層是玻璃質感的前提，不是裝飾。
    //
    // 所以：外層畫漸層 → Scaffold 底色設 `Color.Transparent` → 內容照常疊上來。
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.muyao.wanzaihyperos.ui.design.AppColors.pageBrush(isDark)),
    ) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            // ★ 底欄形變（規範 §6.3「要液體感就抄這個」）。
            //
            // ## 為什麼形變加在**容器**而不是滑塊
            //
            // 規範紅線 #7 明說：「滑塊只平移，形變全摘掉」——
            // 滑塊的追手是欠阻尼彈簧，疊上「速度驅動的縮放」就會來回過衝，
            // 那就是業主說的「奇怪的抖動」。
            //
            // 所以：**容器負責形變，滑塊只負責移動**。
            //
            // ## 為什麼這裡的 smear 只接「切頁領先量」
            //
            // 完整手感需要「手指拖拽量」，而 Miuix `NavigationBar` 的滑塊
            // 手勢在它內部，我們拿不到逐幀位移（`NavigationBarItem` 沒有
            // onDrag 回調）。硬去攔截會和它自己的手勢打架。
            //
            // 所以先接**第二項**（領先量）：純點擊切頁時整條欄朝新 tab 傾一下再落回。
            // 這是規範說「只有第二項也非零」的那一半，視覺上已經有液體感；
            // 逐幀拖拽要等 Miuix 開放滑塊拖拽回調，或自己重寫滑塊。
            // **記在這裡而不是假裝做全了。**
            val tabCount = tabs.size
            // ★ 形變量用「狀態 + 動畫」驅動，**不用 LaunchedEffect + delay**。
            //
            // ## 為什麼（真機踩到的，量了三次）
            //
            // 第一版寫成「LaunchedEffect 裡設 lead=1，delay 250ms 後歸零」，
            // 結果**形變完全沒發生** —— 連把 scaleX 臨時調到 1.5
            // 都量不到底欄寬度變化（1123px 一動不動）。
            //
            // 原因：`bottomBar` 是 Scaffold 的一個 **slot lambda**。
            // 把 `LaunchedEffect` 掛在 slot 內容裡時，它會在每次重組被取消/重啟，
            // 而重啟的第一件事就是讓 `delay` 從頭跑 —— 峰值那一幀很容易被跳過。
            //
            // 改成**宣告式**：`peak` 是 `uiState.selectedTab` 的純函數結果，
            // 再用 `animateFloatAsState` 讓它平滑回 0。
            // 這樣沒有任何時序假設，重組多少次都對。
            // ★ 峰值要按「权重」反算，不能直接给 1.0 或 0.8。
            //
            // ## 我量了三次才發現的算術（**不是**時序問題）
            //
            // 第一版峰值給 0.8，心想「接近滿形變」。實際算一遍：
            // ```
            // lead = 0.8
            // lead/(tabsCount-1) = 0.8/3 = 0.267
            // × LEAD_WEIGHT(0.6)  = 0.16       ← 這才是 smear
            // scaleX = 1 + 0.03×0.16 = 1.0048  ← 只有 0.48%
            // ```
            // 1123px 的欄 × 0.48% = **5px** —— 量不出來，看起來就像「沒生效」。
            //
            // 根因：規範那 3% 是「拖到滿形變（smear=1）」時的值，
            // 而我們**沒有拖拽項**（Miuix 的滑塊手勢在它內部，拿不到逐幀位移），
            // 只剩領先項，且領先項還要除以 (格數-1) 再乘 0.6 —— 被壓了 6 倍。
            //
            // 所以要拿到規範的 3% 觀感，峰值必須**反算**：
            // `peak = (tabsCount-1) / LEAD_WEIGHT` 才能讓 smear 到 1。
            // 取 0.95 倍留餘量（不頂滿，避免邊緣裁切）。
            //
            // ⚠️ 這也說明：抽屜裡的 `SmearMath` 公式本身是對的
            //（13 條單測全綠），錯的是**餵給它的輸入量級**。
            val leadPeakUnits =
                (tabCount - 1).coerceAtLeast(1) / SmearMath.LEAD_WEIGHT * 0.95f
            var peak by remember { mutableFloatStateOf(0f) }
            var lastTab by remember { mutableIntStateOf(uiState.selectedTab) }
            if (uiState.selectedTab != lastTab) {
                peak = (uiState.selectedTab - lastTab).coerceIn(-1, 1) * leadPeakUnits
                lastTab = uiState.selectedTab
            }
            // 用 `Animatable` 而不是 `animateFloatAsState`：後者的 target 是常量 0 時
            // 只有**首次**會跑動畫，後續 peak 變化不會重新觸發。
            val leadAnim = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(peak) {
                if (peak == 0f) {
                    leadAnim.snapTo(0f)
                } else {
                    leadAnim.snapTo(peak)
                    leadAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = com.muyao.wanzaihyperos.ui.design.Motion.NORMAL_MS,
                        ),
                    )
                }
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.graphicsLayer {
                    val smear = SmearMath.smear(
                        rawDragPx = 0f,
                        tabWidthPx = 1f,   // 無拖拽時這項不參與（drag=0）
                        leadUnits = leadAnim.value,
                        tabsCount = tabCount,
                    )
                    // 支點跟著受力方向換邊，於是「被拉長的是朝新 tab 的那條邊」。
                    //
                    // ⚠️ `TransformOrigin` 的參數名是 `pivotFractionX/Y`（0~1 的**比例**），
                    // 不是 `pivotX/pivotY`（那是像素）。
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = SmearMath.pivotX(smear),
                        pivotFractionY = 0.5f,
                    )
                    scaleX = SmearMath.scaleX(smear)
                    scaleY = SmearMath.scaleY(smear)
                },
            ) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = uiState.selectedTab == index,
                            onClick = { uiState.selectedTab = index },
                            icon = when (tab) {
                                Tab.Home -> MiuixIcons.Home
                                Tab.Apps -> MiuixIcons.GridView
                                Tab.Messages -> MiuixIcons.Messages
                                Tab.Mine -> MiuixIcons.Contacts
                            },
                            label = tab.label,
                            // 消息 tab 直接显示未读数；其它 tab 不硬塞角标。
                            // 未知（null）时不画 —— 见 unreadCount 的说明。
                            badge = if (tab == Tab.Messages) {
                                {
                                    val count = unreadCount
                                    if (count != null && count > 0) {
                                        top.yukonga.miuix.kmp.basic.Badge {
                                            Text(
                                                text = if (count > 99) "99+" else count.toString(),
                                                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote2,
                                            )
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding: PaddingValues ->
        when (tabs[uiState.selectedTab]) {
            Tab.Home -> com.muyao.wanzaihyperos.ui.home.HomePage(
                graph = graph,
                onOpenApp = onOpenApp,
                onOpenSearch = onOpenSearch,
                onOpenMessages = onOpenMessages,
                contentPadding = innerPadding,
                unreadCount = unreadCount,
                onOpenBusyness = onOpenBusyness,
                onOpenTodoBoard = onOpenTodoBoard,
                // B18：教務系統入口（考試／成績）。
                onOpenJw = onOpenJw,
            )

            Tab.Apps -> com.muyao.wanzaihyperos.ui.apps.AppsPage(
                graph = graph,
                onOpenApp = onOpenApp,
                contentPadding = innerPadding,
            )

            Tab.Messages -> com.muyao.wanzaihyperos.ui.messages.MessagesPage(
                graph = graph,
                contentPadding = innerPadding,
                unreadCount = unreadCount,
            )

            Tab.Mine -> com.muyao.wanzaihyperos.ui.mine.MinePage(
                loggedIn = uiState.loggedIn,
                account = uiState.displayAccount,
                themeMode = uiState.themeMode,
                onThemeModeChange = { uiState.themeMode = it },
                onRelogin = onRelogin,
                onLogout = onLogout,
                contentPadding = innerPadding,
            )
        }
    }
    }
}
