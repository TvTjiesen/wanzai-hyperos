package com.muyao.wanzaihyperos.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    Scaffold(
        modifier = modifier,
        bottomBar = {
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
