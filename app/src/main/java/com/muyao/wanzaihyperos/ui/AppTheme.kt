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

    /** 底部选中 tab 下标。由主壳与内容共用同一份状态（不给 NavDisplay 推导）。 */
    var selectedTab by mutableStateOf(0)

    /**
     * 課程表用哪種排版：`true` = 週網格（WakeUp 那一類），`false` = 按天列表。
     *
     * ⚠️ **和 [themeMode] 一样只在进程内**，没有落盘。这个工程的界面偏好目前一律如此；
     *   要落盘应该单开一张卡统一做（顺带把主题选择也落盘），不要在这一页先加第一个
     *   SharedPreferences，否则会出现「一半偏好记得、一半不记得」这种更难解释的状态。
     */
    var timetableGrid by mutableStateOf(false)
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
    val background = com.muyao.wanzaihyperos.ui.design.AppColors.background
    val controller = remember(themeMode, background) {
        ThemeController(
            colorSchemeMode = themeMode.mode,
            // 定稿板（wz-v2.html）的真实色值：暖米底 + 墨绿强调 + 暖灰字。
            // 不是「差不多」——色相从蓝改成绿、底从冷蓝紫改成 #F4F0E8，
            // 这两条一改，整页的观感才和板子站到同一边。
            lightColors = lightColorScheme(
                // 2026-10-29 业主「我不喜欢绿色」⇒ 主色 #2E6B64 暖墨绿换成 #3E4E9E 靛蓝。
                // 白字压它 7.53:1（旧的 4.42:1）；对纸面 6.95、对卡片 7.34。
                // 同一次还换了背景层：四枚底色改成跟着 BackgroundPreset 走。
                primary = Color(0xFF3E4E9E),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFE4E7F7),
                onPrimaryContainer = Color(0xFF1E2650),
                secondaryContainer = Color(0xFFE4E7F7),
                onSecondaryContainer = Color(0xFF1E2650),
                background = com.muyao.wanzaihyperos.ui.design.AppColors.pageBackgroundLight,
                onBackground = Color(0xFF1A1917),
                surface = com.muyao.wanzaihyperos.ui.design.AppColors.surface(isDark = false),
                onSurface = Color(0xFF1A1917),
                surfaceContainer = com.muyao.wanzaihyperos.ui.design.AppColors.surfaceContainerLight,
                onSurfaceContainer = Color(0xFF1A1917),
                onSurfaceVariantSummary = Color(0xFF666B71),  // 与 Tone.quiet 同一枚：冷白纸面后 #696E74 对最暗档只剩 4.49:1
                dividerLine = Color(0xFFE2E7EC),
                outline = Color(0xFFD3DAE1),
                error = Color(0xFFB65E3D),
            ),
            darkColors = darkColorScheme(
                // 深档主色 #83C9BC → #8E9CE8：对 #101213 7.22:1、对暗卡 5.93:1。
                primary = Color(0xFF8E9CE8),
                onPrimary = Color(0xFF101213),
                primaryContainer = Color(0xFF262E5C),
                onPrimaryContainer = Color(0xFFC9D0F5),
                secondaryContainer = Color(0xFF262E5C),
                onSecondaryContainer = Color(0xFFC9D0F5),
                background = com.muyao.wanzaihyperos.ui.design.AppColors.pageBackgroundDark,
                onBackground = Color(0xFFF2EFE7),
                surface = com.muyao.wanzaihyperos.ui.design.AppColors.surface(isDark = true),
                onSurface = Color(0xFFF2EFE7),
                surfaceContainer = com.muyao.wanzaihyperos.ui.design.AppColors.surfaceContainerDark,
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
                        com.muyao.wanzaihyperos.ui.design.glass.LocalBarMaterial provides
                            com.muyao.wanzaihyperos.ui.design.glass.barMaterialFor(
                                glassMode = com.muyao.wanzaihyperos.data.UiPrefs.glassMode,
                                translucent = motion.glassIsTranslucent,
                            ),
                        content = content,
                    )
                    top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost()
                }
            },
        )
    }
}
