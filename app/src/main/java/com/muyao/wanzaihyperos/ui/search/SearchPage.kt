package com.muyao.wanzaihyperos.ui.search

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.main.pages.portalSourceBadge
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import com.muyao.wanzaihyperos.ui.components.WanzaiIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.R
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.portal.toUiApp
import com.muyao.wanzaihyperos.net.shortErrorText
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import com.muyao.wanzaihyperos.ui.common.DemoBanner
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.design.ai.AiBallState
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
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
    requestInputFocus: Boolean = true,
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
            SearchState.Failed("登录状态已失效，请到“我的”重新登录后再试")
        else -> SearchState.Results(results)
    }

    // 「全部应用」= 门户全量目录。板子把这一页当**目录**用：输入框空着时下面就是整份清单
    // （`pages/search.txt` 的顺序：display-title → searchbox → chip-list →「全部应用」+「79 项」→ 清单）。
    val catalogState by graph.catalog.state.collectAsState()
    val allApps = remember(catalogState) {
        ((catalogState as? ListState.Content)?.items ?: emptyList())
            .flatMap { it.apps }
            .distinctBy { it.id }
            .map { it.toUiApp() }
    }

    // 空着进来时把目录取一次；已经有内容就不重取（切回这一页不该再打一次门户）。
    LaunchedEffect(Unit) {
        if (graph.catalog.state.value !is ListState.Content) {
            graph.fetch { graph.catalog.refresh() }
        }
    }

    // 进页面即聚焦输入框（搜索页的常规预期）。
    LaunchedEffect(requestInputFocus) {
        if (requestInputFocus) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
            SmallTopAppBar(
                title = "搜索应用",
color = Color.Transparent,
                navigationIcon = {
                    WanzaiIconButton(label = "返回", onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = null,
                            tint = colors.onBackground,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
            if (state !is SearchState.Searching) {
                com.muyao.wanzaihyperos.ui.design.InlineRefreshToast(Modifier.padding(horizontal = 24.dp))
            }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // S-1：板 `h2.display-title` 永远是这一句，输入框在它下面（`pages/search.txt` 首两行）。
            Text(
                text = "找一项校园服务",
                fontSize = Type.displayTitle,
                lineHeight = Type.lineDisplayTitle,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
            )
            InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { q -> query = q },
                expanded = true,
                onExpandedChange = { },
                label = "输入应用名，例如“课表”",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )

            DemoBanner(
                text = "数据来自学校门户搜索接口。注意：服务端的 appName 是未转义的 LIKE 匹配，" +
                    "搜“%”会命中全部 —— 这不是模糊搜索语义。",
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
                    // 模板里的快捷词是横排胶囊（`button.chip`「课 / 图书 / 报修 / 离校」），
                    // 不是六张占满整屏的白卡 —— 那六张卡把「全部应用」挤到了第二屏。
                    HintChips(onPick = { query = it })
                    CatalogList(apps = allApps, onOpenApp = onOpenApp)
                }

                SearchState.Searching -> {
                    // 返工总单 B4：搜索中要出 Searching 那张脸。
                    // 原来这里只画一个加载 spinner —— 于是 12 张脸里的 Searching
                    // 永远不出场。球 + 一句「正在翻找资料」比转圈更说明在找什么。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 34.dp, bottom = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        com.muyao.wanzaihyperos.ui.design.AiBallFeedbackRow(ball = { AiBall(
                            state = AiBallState.Searching,
                            diameter = AiBall.DIAMETER_PAGE,
                            hitSlop = AiBall.INLINE_SLOP,
                            stateDescription = "正在检索学校门户的应用入口。\n结果尚未返回，不代表没有匹配。\n这是学校检索，不是大模型对话。",
                        ) })
                        Text(
                            text = "正在翻找资料",
                            fontSize = Type.caption,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
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
                            detail = "没有匹配“$query”的应用（是“没有匹配”，不是加载失败）",
                        )
                    } else {
                        // 板 `R.section(q?'搜索结果':'全部应用', …)` —— 有查询时标题换成「搜索结果」。
                        CatalogList(apps = s.items, onOpenApp = onOpenApp, title = "搜索结果")
                    }
                }
            }
        }
    }
}

/** 快捷搜索词：横滑胶囊。板上它们是 `button.chip`，不是占满整屏的卡片。 */
@Composable
private fun HintChips(onPick: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 六个词一屏放不下：横滑，不折行（折行会把「全部应用」顶下去）。
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("课表", "一卡通", "图书馆", "请假", "心理", "讲座").forEach { hint ->
            Text(
                text = hint,
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                modifier = Modifier
                    .clip(shape)
                    .border(1.dp, colors.dividerLine, shape)
                    .clickable(onClickLabel = "搜「" + hint + "」") { onPick(hint) }
                    .padding(horizontal = 9.7.dp, vertical = 6.2.dp),
            )
        }
    }
}

/**
 * S-2：分组标题「全部应用 / 搜索结果」+ 右端「N 项」。
 *
 * 原来这里是一整句「找到 N 个应用」—— 板上没有那句话，数量只出现在 `.sec-head .meta`。
 */
@Composable
private fun SectionHead(title: String, count: Int) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = Type.body,
            fontWeight = Type.weightTitle,
            color = colors.onBackground,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count 项",
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Type.tabular),
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

/** 应用清单：一条标题 + 逐行[SearchRow]（板 `div.paper.flush > button.web-item`）。 */
@Composable
private fun CatalogList(
    apps: List<PortalApp>,
    onOpenApp: (PortalApp) -> Unit,
    title: String = "全部应用",
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "catalog-head") { SectionHead(title = title, count = apps.size) }
        items(apps, key = { it.id }) { app ->
            SearchRow(app = app, onClick = { onOpenApp(app) })
        }
    }
}

/**
 * S-3：一行 = 名称 + 下一行来源徽标 `S1 · 原站服务 · ID n` + 箭头。
 *
 * 不复用 [com.muyao.wanzaihyperos.ui.common.AppRow]：那个组件只有一行名字，
 * 而板上这一行是「名称 / `small` 徽标」两行结构；改 AppRow 会连带改到别人的页。
 */
@Composable
private fun SearchRow(app: PortalApp, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        onClick = onClick,
        onLongPress = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    fontSize = Type.body,
                    fontWeight = Type.weightBody,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = portalSourceBadge(app),
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_board_chev),
                contentDescription = null,
                tint = colors.onSurfaceVariantSummary.copy(alpha = 0.7f),
                modifier = Modifier.size(9.dp),
            )
        }
    }
}
