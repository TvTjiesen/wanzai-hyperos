package com.muyao.wanzaihyperos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontWeight
import com.muyao.wanzaihyperos.ui.design.MiSans
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 主题模式的三个选项。[mode] 直接对应 Miuix 的 [ColorSchemeMode]。 */
enum class AppThemeMode(val label: String, val mode: ColorSchemeMode) {
    System("跟随系统", ColorSchemeMode.System),
    Light("浅色", ColorSchemeMode.Light),
    Dark("深色", ColorSchemeMode.Dark),
}

/**
 * 应用级 UI 状态：主题模式 + 登录态 + 底部选中 tab。
 *
 * 用 `mutableStateOf` 而不是普通字段：切主题/登录/切 tab 都必须触发重组。
 * 状态从 ViewModel 层拿是 A2 的事；A1 只有 UI，所以用 `remember` 持有。
 */
@Stable
class AppUiState {
    var themeMode by mutableStateOf(AppThemeMode.System)

    /**
     * 登录态。A1 **没有真实登录**（不接学校接口），
     * 所以它只表示「用户是否已经过了登录页」，不表示任何凭据有效。
     */
    var loggedIn by mutableStateOf(false)

    /** 登录页上填的学号，仅用于「我的」页回显；**不是凭据存储**。 */
    var displayAccount by mutableStateOf("")

    private data class TabSelection(val index: Int, val requestRevision: Long)
    private var tabSelection by mutableStateOf(TabSelection(0, 0L))

    /** Every assignment is a navigation request, including a request for the same page. */
    var selectedTab: Int
        get() = tabSelection.index
        set(value) { tabSelection = TabSelection(value, tabSelection.requestRevision + 1L) }

    val tabRequestRevision: Long get() = tabSelection.requestRevision

    /** Keep the selected source when the main route is disposed for an article/detail route. */
    var notifyFilter by mutableStateOf(4)
    val notifyListState = androidx.compose.foundation.lazy.LazyListState()
    val notifyListPositions = mutableMapOf<String, Pair<Int, Int>>()

    /** Settling updates the visible page, but cannot overwrite a newer unconsumed request. */
    internal fun publishSettledTab(index: Int, expectedRevision: Long): Boolean {
        if (tabSelection.requestRevision != expectedRevision) return false
        tabSelection = TabSelection(index, expectedRevision)
        return true
    }

    /**
     * 課程表用哪種排版：`true` = 週網格（WakeUp 那一類），`false` = 按天列表。
     *
     * ⚠️ **和 [themeMode] 一样只在进程内**，没有落盘。这个工程的界面偏好目前一律如此；
     *   要落盘应该单开一张卡统一做（顺带把主题选择也落盘），不要在这一页先加第一个
     *   SharedPreferences，否则会出现「一半偏好记得、一半不记得」这种更难解释的状态。
     */
    var timetableGrid by mutableStateOf(false)
}

/**
 * 把盘里的「主题 / 课表默认排版」推回界面状态。
 *
 * ★ 返工总单 C3：冷启动（`WanzaiRoot`）与「从备份恢复」（设置页）必须走**同一条**口径。
 *   上一版导入备份只写了盘、没推界面 ⇒ 提示「已应用 N 项」但主题要冷启才变，
 *   用户会以为导入失败。
 *
 * 放在这里而不是设置页：`ThemeEntryGuardTest` 要求 `AppThemeMode.entries` 全工程只许在读盘
 * 那一处出现，避免长成第二个主题入口。
 */
fun syncUiStateFromPrefs(uiState: AppUiState) {
    // 不用 AppThemeMode.entries：ThemeEntryGuardTest 要求枚举全量遍历只出现在读盘那一处。
    uiState.themeMode = when (com.muyao.wanzaihyperos.data.UiPrefs.themeModeName) {
        "Light" -> AppThemeMode.Light
        "Dark" -> AppThemeMode.Dark
        else -> AppThemeMode.System
    }
    uiState.timetableGrid = com.muyao.wanzaihyperos.data.UiPrefs.timetableGrid
}

/** 记住一个 [AppUiState]。 */
@Composable
fun rememberAppUiState(): AppUiState = remember { AppUiState() }

/**
 * 主题容器：**唯一**的 `MiuixTheme` 提供者。
 *
 * 纪律（来自 miuix-skill 的 setup-and-theme）：
 * - 只在这里创建 `ThemeController`，页面内部**不再嵌套** `MiuixTheme`；
 * - `ThemeController.colorSchemeMode` 是只读的（`by mutableStateOf` 私有 set），
 *   所以切主题必须把模式当 `remember` 的 key **重建控制器**，而不是改它的字段；
 * - 不传 `lightColors`/`darkColors` 时用 Miuix 默认的浅/深色板，
 *   `System` 即跟随系统深浅色。
 */
@Composable
fun WanzaiTheme(
    themeMode: AppThemeMode,
    appearance: com.muyao.wanzaihyperos.data.AppearanceSettings? = null,
    content: @Composable () -> Unit,
) {
    // ★ 只**覆蓋一個色**：`surfaceContainer`（卡片底），其餘吃 Miuix 預設。
    //
    // 為什麼要覆蓋（真機量到的數字，不是猜的）：
    // 深色下 Miuix 預設卡片是 (36,36,36)、而我們新加的漸層背景最暗處是
    // (15,26,52)；兩者**對比度只有 1.11:1** —— 幾乎看不出卡片邊界。
    // 正常 UI 要 ≥3:1 才分得清層級（規範 §2「四級深灰分層」講的就是這件事）。
    //
    // 為什麼只覆蓋一個、不做整套色板：
    // Miuix 有 50+ 個語義色，自己挑一套要保證兩兩搭配都對；
    // 而這裡的問題**只有卡片底和背景貼太近**，覆蓋一個就解決。
    // 規範 §2.1 也是這個建議（「只在兩個地方做局部覆蓋」）。
    //
    // 40 → 比預設 36 亮一檔，讓卡片在漸層上「浮起來」；
    // 對比度約 1.22:1，配合第 5 步要加的 1dp 描邊，層級才真正立得住。
    // ★ 背景预设也是 key（业主 2026-10-29 让用户自选底色）：主题令牌里的
    //   background / surface / surfaceContainer **都按预设现取**，所以换一套底
    //   必须重建控制器。少这个 key 的症状是「设置里选了米白，页面还是冷的」。
    val background = appearance?.let { com.muyao.wanzaihyperos.ui.design.BackgroundPreset.of(it.background) }
        ?: com.muyao.wanzaihyperos.ui.design.AppColors.background
    val customization = (appearance?.customization ?: com.muyao.wanzaihyperos.data.UiPrefs.themeCustomization).copy(useSystemWallpaper = false)
    val desktopWallpaper = com.muyao.wanzaihyperos.ui.home.rememberDesktopWallpaper(customization.useSystemWallpaper)
    if (appearance == null) com.muyao.wanzaihyperos.ui.home.DesktopWallpaperWindow(customization.useSystemWallpaper)
    val inheritedCustomization = com.muyao.wanzaihyperos.ui.design.LocalThemeCustomization.current
    val inheritedDensity = androidx.compose.ui.platform.LocalDensity.current
    val customizedDensity = androidx.compose.ui.unit.Density(inheritedDensity.density * customization.displayScale / inheritedCustomization.displayScale, inheritedDensity.fontScale * customization.textScale / inheritedCustomization.textScale)
    val context = androidx.compose.ui.platform.LocalContext.current
    val accentPair = when (customization.accent) {
        "blue" -> 0xFF0064D6L to 0xFF0A84FFL
        "teal" -> 0xFF007A72L to 0xFF63D2C4L
        "purple" -> 0xFF8944C8L to 0xFFBF8AF5L
        "rose" -> 0xFFBF2859L to 0xFFFF6482L
        "orange" -> 0xFFA35200L to 0xFFFFAB40L
        else -> 0xFF5056B8L to 0xFF9D9AFFL
    }
    val baseAccentLight = if (customization.dynamicColor && android.os.Build.VERSION.SDK_INT >= 31)
        Color(context.getColor(android.R.color.system_accent1_600)) else if (customization.accentHex.isNotEmpty()) com.muyao.wanzaihyperos.ui.design.readableThemeAccent(customization.accentHex, false) else Color(accentPair.first)
    val baseAccentDark = if (customization.dynamicColor && android.os.Build.VERSION.SDK_INT >= 31)
        Color(context.getColor(android.R.color.system_accent1_200)) else if (customization.accentHex.isNotEmpty()) com.muyao.wanzaihyperos.ui.design.readableThemeAccent(customization.accentHex, true) else Color(accentPair.second)
    val neutral = customization.colorStyle == "neutral"
    val accentLight = if (neutral) androidx.compose.ui.graphics.lerp(baseAccentLight, Color(0xFF414141), .85f) else baseAccentLight
    val accentDark = if (neutral) androidx.compose.ui.graphics.lerp(baseAccentDark, Color(0xFFCCCCCC), .85f) else baseAccentDark
    val vivid = customization.colorStyle == "vivid"
    val containerTint = if (vivid) .32f else if (neutral) .04f else .12f
    val controller = remember(themeMode, background, accentLight, accentDark, containerTint) {
        ThemeController(
            colorSchemeMode = themeMode.mode,
            // 定稿板（wz-v2.html）的真实色值：暖米底 + 墨绿强调 + 暖灰字。
            // 不是「差不多」——色相从蓝改成绿、底从冷蓝紫改成 #F4F0E8，
            // 这两条一改，整页的观感才和板子站到同一边。
            lightColors = lightColorScheme(
                // 2026-10-29 业主「我不喜欢绿色」⇒ 主色 #2E6B64 暖墨绿换成 #3E4E9E 靛蓝。
                // 白字压它 7.53:1（旧的 4.42:1）；对纸面 6.95、对卡片 7.34。
                // 同一次还换了背景层：四枚底色改成跟着 BackgroundPreset 走。
                primary = accentLight,
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = androidx.compose.ui.graphics.lerp(background.paper(false), accentLight, containerTint),
                onPrimaryContainer = if (vivid) Color(0xFF1A1917) else accentLight,
                secondaryContainer = androidx.compose.ui.graphics.lerp(background.paper(false), accentLight, containerTint * .6f),
                onSecondaryContainer = accentLight,
                background = background.paper(false),
                onBackground = Color(0xFF1A1917),
                surface = background.cardLight,
                onSurface = Color(0xFF1A1917),
                surfaceContainer = background.cardLight,
                onSurfaceContainer = Color(0xFF1A1917),
                onSurfaceVariantSummary = Color(0xFF666B71),  // 与 Tone.quiet 同一枚：冷白纸面后 #696E74 对最暗档只剩 4.49:1
                dividerLine = Color(0xFFE2E7EC),
                outline = Color(0xFFD3DAE1),
                error = Color(0xFFB65E3D),
            ),
            darkColors = darkColorScheme(
                // 深档主色 #83C9BC → #8E9CE8：对 #101213 7.22:1、对暗卡 5.93:1。
                primary = accentDark,
                onPrimary = Color(0xFF101213),
                primaryContainer = androidx.compose.ui.graphics.lerp(background.paper(true), accentDark, containerTint),
                onPrimaryContainer = if (vivid) Color(0xFFF2EFE7) else accentDark,
                secondaryContainer = androidx.compose.ui.graphics.lerp(background.paper(true), accentDark, containerTint * .6f),
                onSecondaryContainer = accentDark,
                background = background.paper(true),
                onBackground = Color(0xFFF2EFE7),
                surface = background.cardDark,
                onSurface = Color(0xFFF2EFE7),
                surfaceContainer = background.cardDark,
                onSurfaceContainer = Color(0xFFF2EFE7),
                onSurfaceVariantSummary = Color(0xFFA3A9AF),
                dividerLine = Color(0xFF2A2D2E),
                outline = Color(0xFF3A3D3E),
                error = Color(0xFFE09270),
            ),
        )
    }
    MiuixTheme(controller = controller) {
        // 全 App 统一 MiSans：只换字族，字号/字重仍由各页显式给。
        // 用同一套 colors 再包一层，只覆盖 TextStyles（Miuix 的 Text 只认主题里的样式）。
        val base = MiuixTheme.textStyles
        val styles = base.copy(
            main = base.main.copy(fontFamily = MiSans),
            paragraph = base.paragraph.copy(fontFamily = MiSans),
            body1 = base.body1.copy(fontFamily = MiSans),
            body2 = base.body2.copy(fontFamily = MiSans),
            button = base.button.copy(fontFamily = MiSans),
            footnote1 = base.footnote1.copy(fontFamily = MiSans),
            footnote2 = base.footnote2.copy(fontFamily = MiSans),
            headline1 = base.headline1.copy(fontFamily = MiSans),
            headline2 = base.headline2.copy(fontFamily = MiSans),
            subtitle = base.subtitle.copy(fontFamily = MiSans),
            title1 = base.title1.copy(fontFamily = MiSans),
            title2 = base.title2.copy(fontFamily = MiSans),
            title3 = base.title3.copy(fontFamily = MiSans),
            title4 = base.title4.copy(fontFamily = MiSans),
        )
        MiuixTheme(
            colors = MiuixTheme.colorScheme,
            textStyles = styles,
            content = {
                // ★ v3 #12：玻璃三档**必须在这里推到全 App**。
                // 此前 `LocalBarMaterial` 没有任何一处 provide（唯一会 provide 的
                // `GlassTheme` 零调用），于是每一面 `GlassSurface` 都吃默认值 LIQUID ——
                // 设置页显示"磨砂"、屏幕上却还是带折射的液态，正是业主点名不许有的那种假开关。
                // 读两条状态：`UiPrefs.glassMode`（mutableStateOf，读了就会重组）
                // 与 `motion.glassIsTranslucent`（减弱透明度/高对比 ⇒ 一律先退实底）。
                val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
                // ★ 弹层宿主必须在这里挂一次，否则全 App 的 OverlayDialog 都画不出来。
                //
                // miuix 0.9.4 的 OverlayDialog 自己不画任何东西：它只是往
                // `LocalDialogStates` 里塞一个 DialogState，真正把它渲染出来的是
                // `MiuixPopupUtils.MiuixPopupHost()`。而整个 miuix jar 里**只有它自己的
                // Scaffold** 调用过这个宿主（javap 查过：MiuixPopupHost 的调用点只有
                // ComposableSingletons$ScaffoldKt 与 MiuixPopupUtils 自身）。
                // 本工程主壳是自定义 Box + AnimatedContent，没有 miuix Scaffold ⇒
                // 设置页/课表页/消息页的 OverlayDialog 全部点了没反应
                // （2026-10-30 真机实测：「全部已读」clickable 走进了闭包、状态也写进去了，
                //  就差这一层宿主，画面一动不动）。
                //
                // 放在 Box 的最后一层：弹层要压在所有页面内容之上。
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.muyao.wanzaihyperos.ui.design.LocalThemeCustomization provides customization,
                        com.muyao.wanzaihyperos.ui.home.LocalDesktopWallpaper provides desktopWallpaper,
                        androidx.compose.ui.platform.LocalDensity provides customizedDensity,
                        com.muyao.wanzaihyperos.ui.design.glass.LocalBarMaterial provides
                            com.muyao.wanzaihyperos.ui.design.glass.barMaterialFor(
                                glassMode = appearance?.glass ?: com.muyao.wanzaihyperos.data.UiPrefs.glassMode,
                                translucent = motion.glassIsTranslucent,
                            ),
                    ) {
                        com.muyao.wanzaihyperos.ui.design.glasmio.WanzaiGlasmioHost(themeMode = themeMode, appearanceSettings = appearance) {
                            content()
                            top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost()
                        }
                    }
                }
            },
        )
    }
}
