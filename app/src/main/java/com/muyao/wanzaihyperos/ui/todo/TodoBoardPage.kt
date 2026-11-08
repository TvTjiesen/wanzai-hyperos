package com.muyao.wanzaihyperos.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.em
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.data.todo.TodoBoard
import com.muyao.wanzaihyperos.data.todo.TodoGroup
import com.muyao.wanzaihyperos.data.todo.TodoItem
import com.muyao.wanzaihyperos.data.todo.TodoNext
import com.muyao.wanzaihyperos.data.todo.TodoRepository
import com.muyao.wanzaihyperos.data.todo.UrgentTodo
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.Type
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
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
    /**
     * 開「門戶 · 我的事務」（審批那一頁）。
     *
     * ★ 為什麼這一頁需要它：門戶的審批條目在這一頁**刻意不可點**（見 [TodoItem.actionable]）——
     *   點它們會拿錯 id 去發寫請求。但「不可點」不能變成「沒地方去」：
     *   以前 `Route.Busyness` 在整個工程裡**沒有任何一處 `backStack.add`**，
     *   用戶根本到不了那一頁。這顆按鈕就是那個出口（接線在 `WanzaiRoot.kt`，Lead 負責）。
     */
    onOpenBills: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val board = (state as? ListState.Content)?.items?.firstOrNull()

    /** 來源篩子：null = 兩組都列（稿上的預設）；不是 null 就只看那一組。 */
    var filter by remember { mutableStateOf<com.muyao.wanzaihyperos.data.todo.TodoSource?>(null) }

    // 清單滾動狀態提到這裡：頂欄漸變紗靠它算進度。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val veilProgress by com.muyao.wanzaihyperos.ui.design.rememberScrollProgress(listState)
    val colors = MiuixTheme.colorScheme

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    // 頂欄走共享殼（SecondaryScaffold）：漸變、安全區、返回鍵、欄高只由設計層決定一次。
    com.muyao.wanzaihyperos.ui.design.SecondaryScaffold(
        title = "全部待办",
        subtitle = "按来源分组",
        onBack = onExit,
        modifier = modifier,
        scrollProgress = veilProgress,
        onRefresh = { scope.launch { runCatching { repository.refresh() } } },
    ) { innerPadding ->
        if (board == null) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // 安全區只讓一次：頂欄高度（含狀態列）由殼給。
                    .padding(top = innerPadding.calculateTopPadding()),
                state = listState,
                contentPadding = PaddingValues(
                    start = Dims.screenH,
                    end = Dims.screenH,
                    bottom = Dims.screenV,
                    top = Dims.screenV,
                ),
                verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
            ) {
                // ── 首屏英雄区（稿 v2.1 `todos.png` 顶部三行）──────────────────
                // 稿：「待办聚合 / 需要你处理 / 课程作业与门户事务 · 独立来源」。
                // 顶栏那颗小标题是「全部待办」，首屏大字是「需要你处理」——
                // 和消息页一样，顶栏与英雄区不是同一句话。
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth().padding(top = Dims.tight, bottom = 2.dp)) {
                        Text(
                            text = "待办聚合",
                            fontSize = Type.micro,
                            letterSpacing = 0.08.em,
                            fontWeight = Type.weightTitle,
                            color = colors.primary,
                        )
                        Text(
                            text = "需要你处理",
                            fontSize = Type.displayTitle,
                            fontWeight = Type.weightTitle,
                            letterSpacing = (-0.01).em,
                            color = colors.onBackground,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(
                            text = "课程作业与门户事务 · 独立来源",
                            fontSize = Type.micro,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                // ── 来源筛子（稿 `.subnav` 那两颗）────────────────────────────
                // 默认**都不选中 = 两组都列**（稿上默认就是两段都在）。
                // 点一下只看那一组，再点一下回到全部 —— 这是真筛子，不是装饰按钮。
                item(key = "source-filter") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TodoSourceChip(
                            text = "门户审批",
                            on = filter == com.muyao.wanzaihyperos.data.todo.TodoSource.PORTAL,
                            onClick = {
                                filter = if (filter == com.muyao.wanzaihyperos.data.todo.TodoSource.PORTAL) {
                                    null
                                } else {
                                    com.muyao.wanzaihyperos.data.todo.TodoSource.PORTAL
                                }
                            },
                        )
                        TodoSourceChip(
                            text = "课程作业",
                            on = filter == com.muyao.wanzaihyperos.data.todo.TodoSource.TRONCLASS,
                            onClick = {
                                filter = if (filter == com.muyao.wanzaihyperos.data.todo.TodoSource.TRONCLASS) {
                                    null
                                } else {
                                    com.muyao.wanzaihyperos.data.todo.TodoSource.TRONCLASS
                                }
                            },
                        )
                    }
                }

                item {
                    Text(
                        text = board.knownTotal?.let { "共 $it 条待办" }
                            ?: "部分来源未取到，总数未知",
                        style = MiuixTheme.textStyles.body1.copy(fontFeatureSettings = Type.tabular),
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = Dims.tight),
                    )
                }
                // ── 門戶審批的出口 ──────────────────────────────────────
                //
                // 這一頁的審批條目**刻意不可點**（見 [TodoItem.actionable]）——
                // 點它們會拿錯 id 去發寫請求，或落到一個根本不是作業的詳情頁。
                // 但「不可點」不能變成「沒地方去」：Route.Busyness（門戶我的事務）
                // 以前在整個工程裡沒有一處 backStack.add，用戶根本到不了。
                // 這顆按鈕就是那個出口；接線在 WanzaiRoot.kt（Lead 負責）。
                item(key = "portal") { PortalApprovalEntry(board, onOpenBills) }
                // 「最近要交的那一件」摆最上面。
                // 分组是必要的（两边的「办」法不同），但分组之后就看不出「今天最该先动哪件」——
                // 真机上 19 条作业散在两组里，要自己滚动比对日期。
                // 没截止时间的一条都不参与（學在城院 end_time 实测常为 null，
                // 門戶那条给的是创建时间）—— 那些只计数，编一个时间会让人照着错的顺序做事。
                val urgent = TodoNext.pick(board.groups, java.time.LocalDateTime.now())
                if (urgent != null) {
                    item(key = "urgent") { UrgentCard(urgent, onOpenHomework) }
                }
                items(board.groups.filter { filter == null || it.source == filter }, key = { it.source.name }) { group ->
                    SourceSection(group, onOpenHomework)
                }
                item(key = "reload") {
                    // 稿 v2.1 頁尾：「重新讀取各來源」。
                    // 這裡是**重取**，不是「刷新快取」——兩個來源各自一個請求，
                    // 所以文案寫「各來源」而不是「刷新這頁」。
                    val reloadColors = MiuixTheme.colorScheme
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, reloadColors.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "重新读取各来源") { scope.launch { runCatching { repository.refresh() } } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "重新读取各来源", fontSize = Type.secondary, color = reloadColors.primary)
                    }
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
 * 門戶審批的入口（一顆真的能按的按鈕）。
 *
 * ### 為什麼它必須存在
 *
 * 這一頁的審批條目**刻意不可點**：`TodoItem.actionable` 只對學在城院的 `type == homework`
 * 為真（見 `TronTodo.toTodoItem`）。點一條審批會拿 `billid` 去當作業 id 發請求。
 *
 * 但「條目不可點」不等於「用戶不該有去處」——以前 `Route.Busyness` 在整個工程裡
 * 沒有一處 `backStack.add`，門戶那 12 條審批在 App 裡**完全到不了**。
 * 所以出口放在這裏，且**如實寫明哪一部分要進門戶**：
 * 作業那幾條在這一頁能點開，審批那幾條得過門戶（那裏才有批的權限與確認）。
 *
 * 門戶沒有審批條目時**不顯示這張卡**（不給一顆通向空頁的按鈕）。
 */
@Composable
private fun PortalApprovalEntry(board: TodoBoard, onOpenBills: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val portal = board.groups.firstOrNull { it.source == com.muyao.wanzaihyperos.data.todo.TodoSource.PORTAL }
    // 取不到 / 一條都沒有 → 這顆按鈕沒有意義，不如不留。
    val hasAny = portal != null && !portal.failed && portal.items.isNotEmpty()
    if (!hasAny) return

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 命中區 ≥44dp：整張卡可點，而不是只有右邊那幾個字。
                .clickable(onClickLabel = "打开门户我的事务") { onOpenBills() }
                .padding(Dims.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BodyMark(icon = MarkIcon.Card, tint = colors.primary)
            Spacer(Modifier.size(Dims.tight))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "门户 · 审批",
                    style = MiuixTheme.textStyles.body1,
                    color = colors.onSurface,
                )
                Text(
                    // 如實：這一頁只列得出來，批要回門戶批。
                    text = "本页只列出审批条目标题，处理请回门户",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Text(
                text = "去门户 ›",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.primary,
            )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                BodyMark(
                    icon = MarkIcon.Clock,
                    tint = if (urgent.overdue) colors.error else colors.primary,
                )
                Spacer(Modifier.size(Dims.tight))
                Text(
                    text = "最近要交",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                )
            }
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
                BodyMark(
                    icon = when (group.source) {
                        com.muyao.wanzaihyperos.data.todo.TodoSource.TRONCLASS -> MarkIcon.Course
                        com.muyao.wanzaihyperos.data.todo.TodoSource.PORTAL -> MarkIcon.Card
                    },
                    tint = colors.primary,
                )
                Spacer(Modifier.size(Dims.tight))
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

/**
 * 待辦頁的來源篩子（稿 v2.1 `.subnav` 那兩顆）。
 *
 * 為什麼不做成 [top.yukonga.miuix.kmp.basic.FilterChip]：這裡只有兩顆、
 * 且預設**都不選**（未選 = 兩組都列）。FilterChip 的視覺假定「總有一個選中」，
 * 用它表達「不選 = 全部」會讓人以為有一顆是預設選中的。
 */
@Composable
private fun TodoSourceChip(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) colors.primary else colors.surface.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = if (on) colors.primary else colors.dividerLine,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClickLabel = text) { onClick() }
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = Type.caption,
            fontWeight = if (on) Type.weightTitle else Type.weightBody,
            color = if (on) colors.onPrimary else colors.onSurfaceVariantSummary,
        )
    }
}
