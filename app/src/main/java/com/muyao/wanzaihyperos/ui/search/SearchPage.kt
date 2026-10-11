package com.muyao.wanzaihyperos.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.portal.toUiApp
import com.muyao.wanzaihyperos.net.shortErrorText
import com.muyao.wanzaihyperos.ui.common.AppRow
import com.muyao.wanzaihyperos.ui.common.DemoBanner
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder

/** 搜索的显式状态：还没输入 / 搜索中 / 有结果 / **取数失败**。 */
private sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val items: List<PortalApp>) : SearchState

    /**
     * ★ 取数失败**必须**与「没有匹配」分开。
     * 合成一个空列表就会出现「网络断了却告诉用户『没有匹配的应用』」——
     * 那正是业主提过的「替服务端撒谎」。
     */
    data class Failed(val message: String) : SearchState
}

/**
 * 搜索页：应用名/副标题匹配。
 *
 * 四种状态分开表示（方案要求「搜索空／非空」都要验）：
 * 未输入 / 搜索中 / 有结果 / 无结果 —— **不用一个空列表同时表示后两者**。
 */
@Composable
fun SearchPage(
    graph: AppGraph,
    onBack: () -> Unit,
    onOpenApp: (PortalApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var retryTick by remember { mutableIntStateOf(0) }

    // ---- 真实搜索（门户 appinfolist，JSON 包体，读顶层 rows）------------------
    val searchState by graph.search.state.collectAsState()
    val results = remember(searchState) {
        ((searchState as? com.muyao.wanzaihyperos.data.ListState.Content)?.items ?: emptyList())
            .map { it.toUiApp() }
    }

    // ★ 状态**由仓库推导**，不再手工维护一份可能与真实结果脱节的局部状态。
    val state: SearchState = when {
        query.isBlank() -> SearchState.Idle
        searchState is com.muyao.wanzaihyperos.data.ListState.FirstLoad -> SearchState.Searching
        searchState is com.muyao.wanzaihyperos.data.ListState.Failed ->
            SearchState.Failed(
                (searchState as com.muyao.wanzaihyperos.data.ListState.Failed).error.shortErrorText(),
            )
        searchState is com.muyao.wanzaihyperos.data.ListState.SessionExpired ->
            SearchState.Failed("登录状态已失效，请到「我的」重新登录后再试")
        else -> SearchState.Results(results)
    }

    // 进页面即聚焦输入框（搜索页的常规预期）。
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "搜索应用",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            tint = colors.onBackground,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { q -> query = q },
                expanded = true,
                onExpandedChange = { },
                label = "输入应用名，例如「课表」",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )

            DemoBanner(
                text = "数据来自学校门户搜索接口。注意：服务端的 appName 是**未转义的 LIKE 匹配**，" +
                    "搜「%」会命中全部 —— 这不是模糊搜索语义。",
            )

            // 防抖：用户还在打字时不要发请求（QPS 红线）。
            // 仓库内部用 StaleGuard 丢弃过时响应，所以快速改词不会出现旧结果覆盖新结果。
            LaunchedEffect(query, graph, retryTick) {
                if (query.isBlank()) {
                    graph.search.clear()
                    return@LaunchedEffect
                }
                delay(350)
                runCatching { graph.search.search(query) }
            }

            when (val s = state) {
                SearchState.Idle -> {
                    Column {
                        SmallTitle(text = "试试这些")
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(listOf("课表", "一卡通", "图书馆", "请假", "心理", "讲座")) { hint ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 3.dp),
                                    onClick = { query = hint },
                                ) {
                                    Text(
                                        text = hint,
                                        style = MiuixTheme.textStyles.body1,
                                        color = colors.onSurface,
                                        modifier = Modifier.padding(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                SearchState.Searching -> {
                    StatePlaceholder(PageState.Loading)
                }

                is SearchState.Failed -> {
                    StatePlaceholder(
                        state = PageState.Error,
                        detail = s.message,
                        onRetry = { retryTick++ },
                    )
                }

                is SearchState.Results -> {
                    if (s.items.isEmpty()) {
                        StatePlaceholder(
                            state = PageState.Empty,
                            detail = "没有匹配「$query」的应用（是「没有匹配」，不是加载失败）",
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            item {
                                Text(
                                    text = "找到 ${s.items.size} 个应用",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colors.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                                )
                            }
                            items(s.items, key = { it.id }) { app ->
                                AppRow(
                                    app = app,
                                    onClick = { onOpenApp(app) },
                                    onLongClick = { onOpenApp(app) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
