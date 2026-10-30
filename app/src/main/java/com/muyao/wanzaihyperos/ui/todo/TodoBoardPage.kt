package com.muyao.wanzaihyperos.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import com.muyao.wanzaihyperos.data.todo.TodoBoard
import com.muyao.wanzaihyperos.data.todo.TodoGroup
import com.muyao.wanzaihyperos.data.todo.TodoItem
import com.muyao.wanzaihyperos.data.todo.TodoNext
import com.muyao.wanzaihyperos.data.todo.TodoRepository
import com.muyao.wanzaihyperos.data.todo.UrgentTodo
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.Type
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 多來源待辦（B6）。
 *
 * ### 這一頁回答的問題是「我今天總共要辦什麼」
 *
 * 學校的待辦散在幾個互不相通的系統裡。以前要一個一個點進去看的，
 * 這一頁把它們**按來源分組**放在一处。
 *
 * ### 為什麼分組而不是混排
 *
 * 兩個來源的「辦」法完全不同（作業要去交、審批要去批），
 * 混在一起按時間排會讓人不知道该點哪個。
 * 而且分組之後，**某個來源掛了一眼看得出來** ——
 * 混排時「某來源沒取到」會表現成「那些事就是沒有」，那是最壞的誤導。
 *
 * ### 三條計劃書硬要求，對應的實現
 *
 * | 要求 | 實現 |
 * |---|---|
 * | 按來源分組 | 每個 [TodoGroup] 一個區塊 |
 * | 來源內去重 | [com.muyao.wanzaihyperos.data.todo.TodoBoardBuilder.dedupeAndSort] |
 * | 未知總數不當 0 | [TodoGroup.countText] 顯示「—」 |
 */
@Composable
fun TodoBoardPage(
    repository: TodoRepository,
    onExit: () -> Unit,
    /**
     * 點一條**作業**待辦 → 進那一項作業的詳情（可提交）。
     *
     * ⚠️ 只有 `item.actionable` 為真的才會有回調（學在城院裡 `type == homework` 的）。
     * 上報的審批、通知、報名那些**不該可點** —— 點它們會拿錯 id 去發寫請求，
     * 或落到一個根本不是作業的詳情頁。判據在 [com.muyao.wanzaihyperos.data.todo.TodoItem.actionable]。
     */
    onOpenHomework: (TodoItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val board = (state as? ListState.Content)?.items?.firstOrNull()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "全部待办",
color = Color.Transparent,
                subtitle = "按来源分组",
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { runCatching { repository.refresh() } } }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "重新加载",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (board == null) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = board.knownTotal?.let { "共 $it 条待办" }
                            ?: "部分来源未取到，总数未知",
                        style = MiuixTheme.textStyles.body1.copy(fontFeatureSettings = Type.tabular),
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                // 「最近要交的那一件」摆最上面。
                // 分组是必要的（两边的「办」法不同），但分组之后就看不出「今天最该先动哪件」——
                // 真机上 19 条作业散在两组里，要自己滚动比对日期。
                // 没截止时间的一条都不参与（學在城院 end_time 实测常为 null，
                // 門戶那条给的是创建时间）—— 那些只计数，编一个时间会让人照着错的顺序做事。
                val urgent = TodoNext.pick(board.groups, java.time.LocalDateTime.now())
                if (urgent != null) {
                    item(key = "urgent") { UrgentCard(urgent, onOpenHomework) }
                }
                items(board.groups, key = { it.source.name }) { group ->
                    SourceSection(group, onOpenHomework)
                }
                item {
                    Text(
                        text = "分组展示是因为两边的“办”法不同（作业要交、审批要批）。" +
                            "某个来源没取到时显示“—”而不是 0 —— 我们还不知道，不等于没有。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * 「最近要交」一张卡。
 *
 * 过期的**不藏起来**，而是摆到最前面并如实写「已过期 N 天」——
 * 迟交的作业仍然在待办里，跳过它去显示下周那份，等于帮人把麻烦藏了。
 */
@Composable
private fun UrgentCard(urgent: UrgentTodo, onOpen: (TodoItem) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val openable = urgent.item.actionable && urgent.item.courseId > 0
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .then(
                    if (openable) {
                        Modifier.clickable(onClickLabel = "打开这项作业") { onOpen(urgent.item) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                text = "最近要交",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = urgent.item.title.ifBlank { "（无标题）" },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = TodoNext.dueText(urgent.daysAway),
                    style = MiuixTheme.textStyles.footnote2,
                    // 过期的用 error 色，但**不只靠颜色**：文字本身就写着「已过期 N 天」。
                    color = if (urgent.overdue) colors.error else colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val meta = buildList {
                if (urgent.item.subtitle.isNotBlank()) add(urgent.item.subtitle)
                if (urgent.item.timeText.isNotBlank()) add(urgent.item.timeText)
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString("　"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun SourceSection(group: TodoGroup, onOpenHomework: (TodoItem) -> Unit) {
    val colors = MiuixTheme.colorScheme
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.source.label,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // ★ 未知不當 0：失敗或拿不到總數時是「—」。
                Text(
                    text = group.countText,
                    style = MiuixTheme.textStyles.title4,
                    color = if (group.failed) colors.onSurfaceVariantSummary else colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            when {
                group.failed -> {
                    Spacer(Modifier.height(6.dp))
                    // 失敗時把原因寫出來，而不是顯示一片空白 ——
                    // 空白會被讀成「這個來源沒有待辦」。
                    Text(
                        text = group.error.orEmpty(),
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary,
                    )
                }

                group.items.isEmpty() -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "这个来源没有待办",
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary,
                    )
                }

                else -> {
                    Spacer(Modifier.height(8.dp))
                    group.items.forEach { item -> TodoRow(item, onOpenHomework) }
                }
            }
        }
    }
}

/**
 * 一條待辦。
 *
 * **能不能點**只由 [TodoItem.actionable] 決定（轉換處設，見 `TronTodo.toTodoItem`）：
 * 只有學在城院裡 `type == homework` 的那幾條會亮。
 * 可點的那一條會多一個「去交 ›」的尾巴 —— 不靠顏色單獨表達可用性
 * （色盲和强光下都看不出「这行能点」）。
 */
@Composable
private fun TodoRow(item: TodoItem, onOpen: (TodoItem) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val openable = item.actionable && item.courseId > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(
                if (openable) {
                    Modifier.clickable(onClickLabel = "打开这项作业") { onOpen(item) }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.title.ifBlank { "（无标题）" },
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (openable) {
                Text(
                    text = "去交 ›",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.primary,
                )
            }
        }
        val meta = buildList {
            if (item.subtitle.isNotBlank()) add(item.subtitle)
            if (item.timeText.isNotBlank()) add(item.timeText)
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString("　"),
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}
