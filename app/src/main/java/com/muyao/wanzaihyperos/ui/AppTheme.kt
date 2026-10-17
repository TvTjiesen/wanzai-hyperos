package com.muyao.wanzaihyperos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme

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
    val controller = remember(themeMode) {
        ThemeController(
            colorSchemeMode = themeMode.mode,
            darkColors = darkColorScheme(
                surfaceContainer = Color(0xFF282828),
            ),
        )
    }
    MiuixTheme(controller = controller, content = content)
}
