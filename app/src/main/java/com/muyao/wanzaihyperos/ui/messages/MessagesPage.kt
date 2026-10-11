package com.muyao.wanzaihyperos.ui.messages

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.portal.toUiMessage
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.data.DemoMessage
import com.muyao.wanzaihyperos.ui.common.DemoBanner
import com.muyao.wanzaihyperos.ui.common.Dot
import com.muyao.wanzaihyperos.ui.common.FreshnessNote
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder

/**
 * 消息页：可滚动分组 + 未读角标 + 更新时间 + 刷新与翻页。
 *
 * 覆盖方案要求：
 * - **分组切换**（顶部可横向滚动的一排分组，带未读数）
 * - **未读角标**（数字）
 * - **更新时间**（下拉刷新与「多久前取的」都说明数据新鲜度）
 * - **刷新**（`PullToRefresh`，保留已有内容）
 * - **翻页**（首版每页 6 条，「加载更多」按返回条数判断还有没有下一页 ——
 *   真实接口的 `total` 字段**不可靠**，门户实测出现过 `total=9` 而实际返回 10 条）
 */
@Composable
fun MessagesPage(
    graph: com.muyao.wanzaihyperos.app.AppGraph,
    contentPadding: PaddingValues,
    /** 真实未读数。**null = 不知道 → 显示 «—»**（不是 0）。 */
    unreadCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()

    var selectedGroup by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    // ---- 真实数据（A3 起消息页不再用示例数据）----------------------------------
    val groupsState by graph.messages.groups.collectAsState()
    val messagesState by graph.messages.messages.collectAsState()
    LaunchedEffect(graph) { runCatching { graph.messages.refreshGroups() } }

    val groups = remember(groupsState) {
        (groupsState as? ListState.Content)?.items ?: emptyList()
    }
    val visible = remember(messagesState) {
        ((messagesState as? ListState.Content)?.items ?: emptyList()).map { it.toUiMessage() }
    }
    // 分页状态直接来自仓库：它按「返回条数是否满一页」判定，**不用服务端 total**。
    val meta = (messagesState as? ListState.Content)?.meta
    val hasMore = meta?.hasMore ?: false
    val loadingMore = meta?.loadingMore ?: false
    val groupName = groups.getOrNull(selectedGroup)?.name ?: "消息"

    // 分组列表或选中的 tab 一变就去取那一组（仓库内部用 StaleGuard 丢弃过时响应）。
    LaunchedEffect(groups, selectedGroup) {
        groups.getOrNull(selectedGroup)?.let { runCatching { graph.messages.selectGroup(it) } }
    }

    // ---- 走查用的状态覆盖（默认 null = 用真实状态）----
    val demoStates = remember {
        listOf(
            null, PageState.Loading, PageState.Empty,
            PageState.Error, PageState.Offline, PageState.Stale, PageState.SessionExpired,
        )
    }
    var demoIndex by remember { mutableIntStateOf(0) }
    val state = demoStates[demoIndex] ?: messagesState.toPageState()

    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                runCatching { graph.messages.refreshMessages() }
                refreshing = false
            }
        },
        modifier = modifier.fillMaxWidth(),
        // ⚠️ 这里只影响**下拉指示器**的位置，不会给内容加 padding
        // （见 Miuix PullToRefresh 源码：contentPadding 只用于 offset 指示器）。
        // 内容的 inset 必须由里面的 LazyColumn 自己消费，否则首行会钻到状态栏底下。
        contentPadding = contentPadding,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            // 内容的真实 inset 在这里消费：top = 状态栏高度，bottom = 底部导航栏高度。
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 12.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        // ★ 取不到就是「—」，**不是 0**：显示 0 等于替服务端说「你没有未读」。
                        text = "未读 " +
                            (unreadCount?.let { if (it > 99) "99+" else it.toString() } ?: "—") +
                            " 条",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onBackgroundVariant,
                    )
                    TextButton(
                        text = "切换状态（走查）",
                        onClick = {
                            demoIndex = (demoIndex + 1) % demoStates.size
                        },
                    )
                }
            }

            item { DemoBanner(text = "数据来自学校门户消息接口（真实分组与消息；顶栏未读数为真实值）") }

            when (state) {
                PageState.Content, PageState.Stale, PageState.Offline -> {
                    // ---- 分组切换器（横向可滚动，带未读角标）--------------------------
                    item {
                        LazyRowOfGroups(
                            groups = groups,
                            selected = selectedGroup,
                            onSelect = {
                                selectedGroup = it
                            },
                        )
                    }

                    item {
                        FreshnessNote(
                            when (state) {
                                PageState.Stale -> "数据过期：本次刷新没成功，显示的是上次取到的消息"
                                PageState.Offline -> "离线：显示上次加载的消息，可能已过期"
                                else -> "分组与消息来自学校门户；分页按返回条数判定（服务端 total 实测不可靠）"
                            },
                        )
                    }

                    if (visible.isEmpty()) {
                        item {
                            StatePlaceholder(
                                state = PageState.Empty,
                                detail = "「$groupName」分组下没有消息（不是加载失败）",
                            )
                        }
                    } else {
                        items(visible, key = { it.id }) { msg ->
                            MessageCard(msg)
                        }

                        // 翻页：只有确实还有更多才画按钮（按返回条数判断，不信 total）。
                        if (hasMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TextButton(
                                        text = if (loadingMore) "正在加载…" else "加载更多",
                                        onClick = {
                                            if (loadingMore) return@TextButton
                                            scope.launch { runCatching { graph.messages.loadMore() } }
                                        },
                                    )
                                }
                            }
                        } else {
                            item {
                                FreshnessNote("已没有更多（按返回条数判断，未使用服务端不可靠的 total）")
                            }
                        }
                    }

                    // 手动刷新入口（下拉之外再给一个明确按钮，方便走查）
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(
                                text = "刷新消息",
                                onClick = {
                                    refreshing = true
                                    scope.launch {
                                        runCatching { graph.messages.refreshMessages() }
                                        refreshing = false
                                    }
                                },
                            )
                        }
                    }
                }

                PageState.Loading -> item { StatePlaceholder(PageState.Loading) }

                PageState.Empty -> item {
                    StatePlaceholder(
                        state = PageState.Empty,
                        detail = "服务端明确返回了空结果（不是加载失败）",
                        onRetry = { demoIndex = 0 },
                    )
                }

                PageState.Error -> item {
                    StatePlaceholder(
                        state = PageState.Error,
                        onRetry = { demoIndex = 0 },
                    )
                }

                PageState.SessionExpired -> item {
                    StatePlaceholder(
                        state = PageState.SessionExpired,
                        onAction = { demoIndex = 0 },
                        actionLabel = "我已重新登录，重试",
                    )
                }
            }
        }
    }
}

/**
 * 分组切换器。
 *
 * 用 `LazyRow`（横向）而不是 `TabRow`：分组有 9 个且名字较长，
 * 横向滚动比把 9 个挤进一屏更可读；选中态由外部 `selected` 单一来源控制。
 */
@Composable
private fun LazyRowOfGroups(
    groups: List<com.muyao.wanzaihyperos.data.portal.PortalMessageGroup>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(groups.size) { index ->
            val group = groups[index]
            val isSelected = index == selected
            Card(
                onClick = { onSelect(index) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = group.name,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (isSelected) colors.primary else colors.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    if (group.unread > 0) {
                        Box(
                            modifier = Modifier
                                .size(18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Dot(color = colors.error, size = 16)
                            Text(
                                text = group.unread.toString(),
                                style = MiuixTheme.textStyles.footnote2,
                                color = colors.onError,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一条消息。未读时左侧有未读点，并且标题加粗。 */
@Composable
private fun MessageCard(msg: DemoMessage) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = { },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 未读点：只表示「未读」，不表示重要性 —— 不给它上红色 error 语义。
            Box(modifier = Modifier.padding(top = 6.dp)) {
                Dot(
                    color = if (msg.unread) colors.primary else colors.dividerLine,
                    size = 8,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = msg.title,
                    style = MiuixTheme.textStyles.body1,
                    color = colors.onSurface,
                    fontWeight = if (msg.unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg.summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = msg.group,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceVariantActions,
                    )
                    Text(
                        text = msg.updatedAt,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
