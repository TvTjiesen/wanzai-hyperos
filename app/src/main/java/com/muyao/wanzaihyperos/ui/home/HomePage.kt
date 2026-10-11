package com.muyao.wanzaihyperos.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.portal.toUiApp
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.common.AppTile
import com.muyao.wanzaihyperos.ui.common.DemoBanner
import com.muyao.wanzaihyperos.ui.common.FreshnessNote
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder

/**
 * 首页：常用应用宫格 + 待办三计数 + 新闻分类与列表 + 搜索/消息入口。
 *
 * 结构纪律：整页以**一个** `LazyVerticalGrid` 作为唯一滚动容器，
 * 宫格与列表区都是它的 item（标题/卡片用 `GridItemSpan(maxLineSpan)` 占整行）。
 * 这样不会出现「宫格套在列表里」的同向嵌套滚动，也不会因为给宫格写死高度
 * 而把最后一行瓷贴切一半（首版就踩了这个坑，真机截图里第三行是断的）。
 *
 * 状态覆盖（方案要求）：加载中 / 空 / 错误 / 离线 / 数据过期 / 登录失效。
 * A1 没有真实网络，用右上角按钮循环切换这些分支，让走查能**逐个看到**每种状态。
 */
@Composable
fun HomePage(
    graph: com.muyao.wanzaihyperos.app.AppGraph,
    onOpenApp: (PortalApp) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMessages: () -> Unit,
    contentPadding: PaddingValues,
    /**
     * 點「待辦 / 申請 / 已辦」三格時進來（B3）。
     *
     * 傳的是**清單枚舉名**（`"TODO"`/`"APPLIED"`/`"FINISHED"`），
     * 而不是枚舉本身：`ui/home` 這一層不該依賴 `data.portal` 的類型細節，
     * 而且導航參數本來就是字串（見 `Route.Busyness`）。
     */
    onOpenBusyness: ((String) -> Unit)? = null,
    /**
     * 打開「全部待辦」看板（B6）。
     *
     * 可空：與 [onOpenBusyness] 同一個理由 —— 走查/預覽時沒有導航容器，
     * 給 null 就**不顯示這個入口**，而不是顯示一個點了沒反應的東西。
     */
    onOpenTodoBoard: (() -> Unit)? = null,
    /** 真实未读数。**null = 不知道**（显示 «—»，绝不显示 0）。 */
    unreadCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()

    var newsGroup by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    // ---- 真实数据（A3 起首页不再用示例数据）------------------------------------
    val frequentState by graph.frequent.state.collectAsState()
    val todoCounts by graph.todo.counts.collectAsState()
    val newsGroupsState by graph.news.groups.collectAsState()
    val newsState by graph.news.news.collectAsState()

    var retryTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(graph, retryTick) {
        runCatching { graph.frequent.refresh() }
        // force = false：待办与未读同一套 15 分钟下限，自动路径不许绕过。
        runCatching { graph.todo.refresh(force = false) }
        runCatching { graph.news.refreshGroups() }
    }

    val frequentApps = remember(frequentState) {
        (frequentState as? com.muyao.wanzaihyperos.data.ListState.Content)?.items
            ?.map { it.toUiApp() }
            ?: emptyList()
    }
    val newsGroups = remember(newsGroupsState) {
        (newsGroupsState as? com.muyao.wanzaihyperos.data.ListState.Content)?.items ?: emptyList()
    }
    val news = remember(newsState) {
        (newsState as? com.muyao.wanzaihyperos.data.ListState.Content)?.items ?: emptyList()
    }
    val selectedNewsGroupName = newsGroups.getOrNull(newsGroup)?.name ?: "新闻"

    // 分组列表或选中的 tab 一变就去取那一页（仓库内部用 StaleGuard 丢弃过时响应）。
    LaunchedEffect(newsGroups, newsGroup) {
        newsGroups.getOrNull(newsGroup)?.let { runCatching { graph.news.selectGroup(it) } }
    }

    // ---- 走查用的状态覆盖（默认 null = 用真实状态）----
    val demoStates = remember {
        listOf(
            null, PageState.Loading, PageState.Empty,
            PageState.Error, PageState.Offline, PageState.Stale, PageState.SessionExpired,
        )
    }
    var demoIndex by remember { mutableIntStateOf(0) }
    val state = demoStates[demoIndex] ?: frequentState.toPageState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ---- 顶部动作行：标题 + 状态切换 + 搜索 + 消息（带未读角标）------------------
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "玩在城院",
                    style = MiuixTheme.textStyles.title4,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        demoIndex = (demoIndex + 1) % demoStates.size
                        if (demoIndex == 0) retryTick++   // 回到真实状态时顺手重新取材
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = "切换状态（走查用）",
                        tint = colors.onBackgroundVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = "搜索",
                        tint = colors.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onOpenMessages) {
                    BadgedBox(
                        badge = {
                            if (unreadCount != null && unreadCount > 0) {
                                Badge {
                                    Text(
                                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                        style = MiuixTheme.textStyles.footnote2,
                                    )
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Messages,
                            contentDescription = "消息",
                            tint = colors.onBackground,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            // ⚠️ 这里**不能**再挂「示例数据」横幅了：A3 起首页的常用应用、
            //    待办三计数、新闻都来自学校门户，写「示例」就是反向撒谎。
            //    图标仍是本地按名字推断（不下载 app_img，避免几十次请求踩限流红线），
            //    这一条要说清楚，否则用户会以为图标也是门户下发的。
            DemoBanner(
                text = "数据来自学校门户（实时）。图标按应用名本地推断 —— " +
                    "不下载门户的 app_img，避免几十次请求违反限流红线。",
            )
        }

        when (state) {
            PageState.Content, PageState.Stale, PageState.Offline -> {
                // ---- 常用应用宫格 ----------------------------------------------------
                item(span = { GridItemSpan(maxLineSpan) }) { SmallTitle(text = "常用应用") }

                items(frequentApps, key = { "app-${it.id}" }) { app ->
                    AppTile(
                        app = app,
                        onClick = { onOpenApp(app) },
                        onLongClick = { onOpenApp(app) },
                    )
                }

                if (state == PageState.Offline) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FreshnessNote("离线：显示的是上次加载的目录，可能已过期。下拉或点右上角刷新重试。")
                    }
                }
                if (state == PageState.Stale) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FreshnessNote("数据过期：本次刷新没成功，显示的是上次取到的内容。下拉刷新可重试。")
                    }
                }

                // ---- 待办三计数 -------------------------------------------------------
                item(span = { GridItemSpan(maxLineSpan) }) {
                    // 標題行右側帶一個「全部待办」入口（B6）。
                    // 放這裡而不是另開一格：這一組本來講的就是「要辦的事」，
                    // 多一個區塊會把首頁拉得更長，而語義是同一件事。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        SmallTitle(
                            text = "待办事项",
                            modifier = Modifier.weight(1f),
                        )
                        if (onOpenTodoBoard != null) {
                            Text(
                                text = "全部待办 ›",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .clickable { onOpenTodoBoard() },
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            // ★ 取不到就是 null → 「—」，**不是 0**。
                            // 三格都可點：進「我的事務」對應的清單（B3）。
                            // 之前只有數字、點不動，要看具體內容得回門戶一層層翻。
                            TodoCountCell("我的待办", todoCounts?.pending) { onOpenBusyness?.invoke("TODO") }
                            TodoCountCell("我的申请", todoCounts?.applied) { onOpenBusyness?.invoke("APPLIED") }
                            TodoCountCell("我的已办", todoCounts?.finished) { onOpenBusyness?.invoke("FINISHED") }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FreshnessNote(
                        "三个计数来自门户的待办接口；自动刷新至少间隔 15 分钟（全 App 无轮询定时器）。" +
                            "取不到时显示「—」而不是 0 —— 我们还不知道，不等于没有。",
                    )
                }

                // ---- 新闻动态 ---------------------------------------------------------
                item(span = { GridItemSpan(maxLineSpan) }) { SmallTitle(text = "新闻动态") }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TabRow(
                        tabs = newsGroups.map { it.name },
                        selectedTabIndex = newsGroup,
                        onTabSelected = { newsGroup = it },
                        // 分组名是服务端下发的 4 字中文，默认 76~98dp 的 tab 会把它们截成
                        // 「教育…」。把 min/max 一起抬到 96dp，让 TabRow 内部横向滚动，
                        // 从而**完整显示**服务端分组名（而不是把名字改短来迁就布局）。
                        minWidth = 96.dp,
                        maxWidth = 96.dp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }

                if (news.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StatePlaceholder(
                            state = PageState.Empty,
                            detail = "「$selectedNewsGroupName」分组下没有内容（不是加载失败）",
                        )
                    }
                } else {
                    items(news, key = { "news-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            onClick = { },
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = item.title,
                                    style = MiuixTheme.textStyles.body1,
                                    color = colors.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$selectedNewsGroupName · ${item.createdDate}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colors.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = if (refreshing) "正在刷新…" else "刷新首页",
                            onClick = {
                                if (refreshing) return@TextButton
                                refreshing = true
                                scope.launch {
                                    delay(700)
                                    refreshing = false
                                }
                            },
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(16.dp)) }
            }

            PageState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                StatePlaceholder(PageState.Loading)
            }

            PageState.Empty -> item(span = { GridItemSpan(maxLineSpan) }) {
                StatePlaceholder(
                    state = PageState.Empty,
                    detail = "服务端明确返回了空结果（不是加载失败）",
                    onRetry = { demoIndex = 0; retryTick++ },
                )
            }

            PageState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                StatePlaceholder(
                    state = PageState.Error,
                    onRetry = { demoIndex = 0; retryTick++ },
                )
            }

            PageState.SessionExpired -> item(span = { GridItemSpan(maxLineSpan) }) {
                StatePlaceholder(
                    state = PageState.SessionExpired,
                    onAction = { demoIndex = 0; retryTick++ },
                    actionLabel = "我已重新登录，重试",
                )
            }
        }
    }
}

@Composable
private fun TodoCountCell(label: String, count: Int?, onClick: (() -> Unit)? = null) {
    val colors = MiuixTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            // 可點：整格都是熱區（只有文字可點會很難按）。
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
        // ★ count 可空是刻意的：取不到时显示「—」，**绝不显示 0**。
        //   显示 0 等于告诉用户「你没有待办」，而我们其实不知道。
        //   这是业主在 Flutter 版明确提过的原始问题。
        val unknown = count == null
        Text(
            text = count?.toString() ?: "—",
            style = MiuixTheme.textStyles.title3,
            color = if (unknown) colors.onSurfaceVariantSummary else colors.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceVariantSummary,
        )
    }
}
