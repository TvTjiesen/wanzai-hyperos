package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SettingExpand
import com.muyao.wanzaihyperos.ui.design.SettingGroup
import com.muyao.wanzaihyperos.ui.design.SettingIcon
import com.muyao.wanzaihyperos.ui.design.SettingRow
import com.muyao.wanzaihyperos.ui.design.SettingSectionLabel
import com.muyao.wanzaihyperos.ui.design.PillSwitch
import com.muyao.wanzaihyperos.ui.design.PeriodTimeEditor
import androidx.compose.ui.platform.LocalContext
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 读安装包自己的 versionName。
 *
 * ⚠️ 讀不到就回「—」，**不寫死一個版本號**：寫死的數字遲早和真實包不一致，
 * 那時「關於」頁就是在骗人（也害他按錯的版本去找更新）。
 */
@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "—"
    }
}

/**
 * **設定板塊**（业主 2026-10-19：设置页照参照站的功能与 UI 做）。
 *
 * ## 版式
 *
 * 分组小标题 + 一个圆角容器里塞几行，每行「彩色圆角方形图标 + 标题/副标题 + 右侧
 * chevron 或开关」—— 图标全部 Canvas 自绘（`ui/design/SettingsChrome.kt`）。
 *
 * ## ★ 只放**真的会做事**的行
 *
 * 参照站有「检查更新 / 导出日志 / 申请适配学校」，那是因为它有 GitHub Releases 和一个
 * 适配申请服务端。我们没有：装的是自建 release，没有更新渠道，也没有日志导出。
 * **摆三行点了没反应的东西就是骗人**，所以这里一行都不放。
 *
 * 同理：旧版那两颗「重新登入／退出登入」按钮，两个都调 `onOpenSettings()`
 * （而它只是把 tab 切到设定自己）—— 等于两颗假按钮。现在改成一颗真的「去重新登录」，
 * 走 `onOpenLogin`；退出登录要清凭据，等真接上 `SecureStore` 的删除口再加。
 *
 * ## 落盘
 *
 * 主题、課表默認排版、節次時間表都经 `UiPrefs` 写进 SharedPreferences（明文，只放界面偏好）。
 * 凭据一律不碰这里 —— 那是 `session/SecureStore` 的事。
 */
@Composable
fun SettingsSectionPage(
    graph: AppGraph,
    uiState: AppUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLogin: () -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    var showPeriods by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            top = 56.dp,
            bottom = 132.dp,   // 玻璃底欄的高度：讓最後一行能滾到玻璃底下
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        item(key = "title") {
            Text(
                text = "設定",
                fontSize = Type.largeTitle,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                lineHeight = Type.largeTitle * Type.LINE_HEIGHT_TITLE,
            )
        }

        // ---- 帳號 ----------------------------------------------------------
        item(key = "account-label") { SettingSectionLabel("帳號") }
        item(key = "account") {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Account,
                    tint = Color(0xFF2F6BFA),
                    title = if (uiState.loggedIn) uiState.displayAccount else "未登入",
                    subtitle = if (uiState.loggedIn) "已通過學校統一身份認證"
                    else "登入後才能取用需要身份的功能",
                )
                SettingRow(
                    icon = SettingIcon.Logout,
                    tint = Color(0xFFE08A1E),
                    title = "去重新登入",
                    subtitle = "回到登入頁再走一次統一身份認證（不清已存憑據）",
                    onClick = onOpenLogin,
                )
            }
        }

        // ---- 外觀 ----------------------------------------------------------
        item(key = "appearance-label") { SettingSectionLabel("外觀") }
        item(key = "appearance") {
            SettingGroup {
                AppThemeMode.entries.forEach { mode ->
                    SettingRow(
                        icon = SettingIcon.Theme,
                        tint = if (uiState.themeMode == mode) colors.primary else Color(0xFF7C5CD6),
                        title = mode.label,
                        subtitle = when (mode) {
                            AppThemeMode.System -> "跟随系统深色模式"
                            AppThemeMode.Light -> "始终浅色"
                            AppThemeMode.Dark -> "始终深色"
                        },
                        onClick = {
                            uiState.themeMode = mode
                            UiPrefs.saveThemeMode(mode.name)
                        },
                        trailing = {
                            if (uiState.themeMode == mode) {
                                Text(text = "✓", fontSize = Type.body, color = colors.primary)
                            }
                        },
                    )
                }
            }
        }

        // ---- 課表 ----------------------------------------------------------
        item(key = "timetable-label") { SettingSectionLabel("課表") }
        item(key = "timetable-default-view") {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Timetable,
                    tint = Color(0xFF1FA37A),
                    title = "默認用三天窗口網格",
                    subtitle = "關掉則默認按天列表（課名最完整）",
                    trailing = {
                        PillSwitch(
                            checked = uiState.timetableGrid,
                            label = "默認網格",
                        ) {
                            uiState.timetableGrid = !uiState.timetableGrid
                            UiPrefs.saveTimetableGrid(uiState.timetableGrid)
                        }
                    },
                )
                SettingRow(
                    icon = SettingIcon.Clock,
                    tint = Color(0xFF3E7BFA),
                    title = "節次時間表",
                    subtitle = if (UiPrefs.periodTimes.isEmpty())
                        "未設定 · 課表只標「第 N 節」"
                    else "已填 ${UiPrefs.periodTimes.count { it.first.isNotBlank() }} 節 · 課表左列會顯示時間",
                    onClick = { showPeriods = !showPeriods },
                    trailing = { Text(text = if (showPeriods) "▾" else "›", fontSize = Type.title, color = colors.onSurfaceVariantSummary) },
                )
            }
        }
        item(key = "period-editor") {
            SettingExpand(visible = showPeriods) {
                SettingGroup {
                    PeriodTimeEditor(rows = UiPrefs.periodTimes) { rows ->
                        UiPrefs.savePeriodTimes(rows)
                    }
                }
            }
        }

        // ---- 首頁視窗 ------------------------------------------------------
        item(key = "home-label") { SettingSectionLabel("首頁") }
        item(key = "home-window") {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Glass,
                    tint = Color(0xFFB4552E),
                    title = "首頁視窗",
                    subtitle = "沒自定義時顯示隨時變化的內置場景（清晨到深夜的天空與日月）",
                )
            }
        }

        // ---- 關於 ----------------------------------------------------------
        item(key = "about-label") { SettingSectionLabel("關於") }
        item(key = "about") {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Info,
                    tint = Color(0xFF6B7280),
                    title = "玩在城院",
                    subtitle = "版本 ${rememberAppVersion()} · 非官方客戶端",
                )
                SettingRow(
                    icon = SettingIcon.School,
                    tint = Color(0xFF2F6BFA),
                    title = "資料來源",
                    subtitle = "浙大城市學院門戶與各業務系統，資料所有權歸學校",
                )
            }
        }
    }
}
