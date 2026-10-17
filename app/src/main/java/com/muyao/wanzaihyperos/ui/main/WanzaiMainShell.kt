package com.muyao.wanzaihyperos.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.glass.GlassBackdropHost
import com.muyao.wanzaihyperos.ui.design.glass.GlassBarItem
import com.muyao.wanzaihyperos.ui.design.glass.GlassBottomBar
import com.muyao.wanzaihyperos.ui.design.glass.GlassTheme
import com.muyao.wanzaihyperos.ui.main.pages.AppsSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.HomeSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.NotifySectionPage
import com.muyao.wanzaihyperos.ui.main.pages.SettingsSectionPage
import com.muyao.wanzaihyperos.ui.main.pages.TimetableSectionPage
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主殼：五個板塊 + 液態玻璃底欄。
 *
 * 結構（順序要緊）：
 * ```
 * GlassBackdropHost
 *   ├ content  → 背景漸層 + 當前板塊內容（會被畫進 layer，供玻璃折射）
 *   └ overlay  → 玻璃底欄（在 layer 之外，折射上面那份內容）
 * ```
 *
 * 為什麼底欄是「浮」的而不是 Scaffold 的 bottomBar：
 * 液態玻璃的價值在於透出背後的內容。用 Scaffold 的 bottomBar 時，
 * 內容會在底欄上方被截斷 —— 底欄背後永遠是空白，
 * 玻璃就只是一塊半透明色，看不出材質。
 * 所以底欄疊在內容上，並給內容留出底部內距讓列表能滾到它下面。
 *
 * 為什麼要 `key(section)`：
 * 切板塊時必須重建 backdrop。不重建的話，玻璃會折射到上一頁的殘留內容
 * 或空白 —— 表現是「切過去的一瞬間玻璃是灰的」。
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
    modifier: Modifier = Modifier,
) {
    val maxIdx = MainSection.entries.size - 1
    var section by remember { mutableIntStateOf(uiState.selectedTab.coerceIn(0, maxIdx)) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f

    // 外部導航（例如「我的→設定」）改 selectedTab 時，跟著切板塊。
    LaunchedEffect(uiState.selectedTab) {
        val target = uiState.selectedTab.coerceIn(0, maxIdx)
        if (target != section) section = target
    }

    GlassTheme {
        key(section) {
            GlassBackdropHost(
                modifier = modifier.fillMaxSize(),
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppColors.pageBrush(isDark)),
                    ) {
                        // ★ 內容**不**留底部內距 —— 讓列表能一直滾到玻璃底下。
                        //
                        // 這是「玻璃看得出來」的前提：玻璃的價值是折射背後的內容，
                        // 若內容在玻璃上方就被截斷，玻璃背後永遠是空白漸層，
                        // 那就只是一塊半透明色，看不出材質。
                        // 所以底部內距改由**頁面自己**在列表末尾加 padding
                        // （見各 SectionPage 的 contentPadding bottom）。
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (MainSection.entries[section]) {
                                MainSection.Home -> HomeSectionPage(
                                    graph = graph,
                                    onOpenJw = onOpenJw,
                                    onOpenTimetable = onOpenTimetable,
                                    onOpenTodos = onOpenTodos,
                                    onOpenMessages = onOpenMessages,
                                )

                                MainSection.Timetable -> TimetableSectionPage(graph = graph)

                                MainSection.Notify -> NotifySectionPage(
                                    graph = graph,
                                    onOpenMessages = onOpenMessages,
                                    onOpenNews = onOpenNews,
                                )

                                MainSection.Apps -> AppsSectionPage(
                                    graph = graph,
                                    onOpenApp = onOpenApp,
                                    onOpenSearch = onOpenSearch,
                                )

                                MainSection.Settings -> SettingsSectionPage(
                                    graph = graph,
                                    uiState = uiState,
                                    onOpenSettings = onOpenSettings,
                                )
                            }
                        }
                    }
                },
                overlay = { backdrop ->
                    GlassBottomBar(
                        backdrop = backdrop,
                        items = MainSection.entries.map { GlassBarItem(it.label, it.icon) },
                        selectedIndex = section,
                        onSelect = { idx ->
                            section = idx
                            uiState.selectedTab = idx
                            onOpenSection(MainSection.entries[idx])
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                },
            )
        }
    }
}
