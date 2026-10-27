package com.muyao.wanzaihyperos.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.portal.toUiGroup
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.ui.common.AppTile
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState

/**
 * 应用页：分组目录（宫格）。**数据来自学校门户**（A3 起）。
 *
 * 交互：**点击**直接打开，**长按**查看信息。
 *
 * 整页以 `LazyVerticalGrid` 作为**唯一**滚动容器（分组标题用整行 span），
 * 这样不会出现「宫格套在列表里」的同向嵌套滚动问题。
 *
 * ### 走查开关
 * 右上角那个「切换状态」是 A1 留下的**演示开关**：数据层的真实状态已经能驱动
 * 加载/空/错误/登录失效，但「离线」「过期」这两种在真机上不好复现，
 * 保留它就能一次走查全部六种状态。**默认值 = 真实状态**，不按它就不影响观察真实数据。
 */
@Composable
fun AppsPage(
    graph: AppGraph,
    onOpenApp: (PortalApp) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme

    var infoApp by remember { mutableStateOf<PortalApp?>(null) }
    var collapsed by remember { mutableStateOf(setOf<Int>()) }

    // ---- 真实数据 ----
    val catalogState by graph.catalog.state.collectAsState()
    // retryTick 让「重试」能真的再拉一次（LaunchedEffect 的 key 变了才会重跑）。
    var retryTick by remember { mutableStateOf(0) }
    LaunchedEffect(graph, retryTick) { runCatching { graph.catalog.refresh() } }

    val uiGroups = remember(catalogState) {
        (catalogState as? com.muyao.wanzaihyperos.data.ListState.Content)?.items
            ?.map { it.toUiGroup() }
            ?: emptyList()
    }
    // ★ 必须**按 id 去重**再计数：门户会把同一条应用放进多个分组，
    //   按分组累加会把重复的算两遍（实测：累加 87，去重后 78）。
    val appCount = remember(uiGroups) {
        uiGroups.flatMap { it.apps }.distinctBy { it.id }.size
    }
    val entryCount = remember(uiGroups) { uiGroups.sumOf { it.apps.size } }

    // ---- 走查用的状态覆盖（默认 null = 用真实状态）----
    val demoStates = remember {
        listOf(
            null, PageState.Loading, PageState.Empty,
            PageState.Error, PageState.Offline, PageState.Stale, PageState.SessionExpired,
        )
    }
    var demoIndex by remember { mutableStateOf(0) }
    val state = demoStates[demoIndex] ?: catalogState.toPageState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // 条数**不写死**：门户实际给多少就显示多少。
                    // 历史记录是 87，2026-10-11 实测是 78 —— 写死就等于替服务端撒谎。
                    text = "共 $appCount 个应用 · ${uiGroups.size} 个分组" +
                        (if (entryCount != appCount) "（含重复入口 $entryCount 条）" else "") +
                        if (demoIndex != 0) "（演示状态）" else "",
                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = Type.tabular),
                    color = colors.onBackgroundVariant,
                )
                TextButton(
                    text = "切换状态（走查）",
                    onClick = { demoIndex = (demoIndex + 1) % demoStates.size },
                )
            }
        }

        when (state) {
            PageState.Content, PageState.Stale, PageState.Offline -> {
                uiGroups.forEachIndexed { index, group ->
                    item(key = "header-$index", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SmallTitle(text = group.title, modifier = Modifier.weight(1f))
                            TextButton(
                                text = if (index in collapsed) "展开" else "收起",
                                onClick = {
                                    collapsed =
                                        if (index in collapsed) collapsed - index else collapsed + index
                                },
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }

                    if (index !in collapsed) {
                        items(
                            items = group.apps,
                            key = { "app-$index-${it.id}" },
                        ) { app ->
                            AppTile(
                                app = app,
                                onClick = { onOpenApp(app) },
                                onLongClick = { infoApp = app },
                            )
                        }
                    }
                }

                if (state == PageState.Offline) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StatePlaceholder(PageState.Offline, detail = "显示的是上次加载的目录")
                    }
                }
                if (state == PageState.Stale) {
                    item(span = { GridItemSpan(maxLineSpan) }) { StatePlaceholder(PageState.Stale) }
                }

                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(16.dp)) }
            }

            PageState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                StatePlaceholder(PageState.Loading)
            }

            PageState.Empty -> item(span = { GridItemSpan(maxLineSpan) }) {
                StatePlaceholder(
                    state = PageState.Empty,
                    detail = "服务端明确返回了空目录（不是加载失败）",
                    onRetry = { demoIndex = 0 },
                )
            }

            PageState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                StatePlaceholder(
                    state = PageState.Error,
                    // 重试 = 回到真实状态并重新拉一次目录。
                    onRetry = {
                        demoIndex = 0
                        retryTick++
                    },
                )
            }

            PageState.SessionExpired -> item(span = { GridItemSpan(maxLineSpan) }) {
                StatePlaceholder(
                    state = PageState.SessionExpired,
                    onAction = { demoIndex = 0 },
                    actionLabel = "我已重新登录，重试",
                )
            }
        }
    }

    // 长按信息弹窗：只展示已核实的字段，不编造评分等未取到的数据。
    val current = infoApp
    OverlayDialog(
        show = current != null,
        onDismissRequest = { infoApp = null },
        title = current?.name,
        summary = current?.subtitle,
    ) {
        if (current != null) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                InfoRow("应用 id", current.id.toString())
                InfoRow("归属子系统", current.system.label)
                InfoRow("仅校园网可开", if (current.needsCampusNetwork) "是" else "否")
                InfoRow("地址", current.url)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "以上信息来自学校门户实时下发（应用名、地址、标的色）。" +
                        "图标是本地按名字推断的 —— 不下载门户的 app_img，" +
                        "因为 ${uiGroups.size} 组共 $appCount 个图标就是 $appCount 次请求，会违反限流红线。",
                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = Type.tabular),
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = colors.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
