package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.largeTitleItem
import com.muyao.wanzaihyperos.ui.design.liquid.rememberPageChromePilot
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.portal.PortalMessageGroup
import com.muyao.wanzaihyperos.data.portal.PortalMessageItem
import com.muyao.wanzaihyperos.data.portal.PortalNewsItem
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 消息板塊：消息與新聞分兩段。
 *
 * 為什麼要分開：
 * - 消息是「給你的」（審批結果、通知），常要動作，會過期；
 * - 新聞是「給所有人看的」（校園新聞），通常只看一眼，不會過期。
 *
 * 混在一條時間軸上，使用者分不清「哪條需要我做事」。
 *
 * 資料來源：
 * - messages.messages（門戶消息介面，第一個分組的消息）
 * - news.news（門戶新聞介面）
 *
 * 介面上不寫「數據來自某某介面」那種開發說明 ——
 * 那是開發者要知道的事，不是使用者要看的。
 */
@Composable
fun NotifySectionPage(
    graph: AppGraph,
    onOpenRollcall: () -> Unit = {},
    onOpenTodos: () -> Unit = {},
    /** 點開一條消息：把它的 openurl 交給外部（真頁面接 Route.Web）。 */
    onOpenUrl: (String) -> Unit = {},
    /** 点开一条之后标已读。写在页面里而不是行里，行就仍然只认一个「怎么显示」。 */
    onRead: (String) -> Unit = {},
    /** 把当前分组整个标已读。失败要能从外面给一句实话，所以也提到页面外面。 */
    onReadAll: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val msgState by graph.messages.messages.collectAsState()
    val newsState by graph.news.news.collectAsState()
    val rollState by graph.rollcall.state.collectAsState()
    val todoState by graph.todoBoard.state.collectAsState()

    // 板子顶部是四颗小胶囊：全部 / 待处理 / 门户 / 新闻。
    var filter by remember { mutableStateOf(0) }
    // 「全部已读」是批量写，按下就先问一句 —— 一百多条一次性变已读，没有撤销。
    var confirmReadAll by remember { mutableStateOf<PortalMessageGroup?>(null) }

    // ★ 這裡原本有個競態 bug（真機表現：兩個板塊永遠卡在「正在取得…」）。
    //
    // 錯在哪：`refreshGroups()` 回來後**立刻**讀 `groups.value`，
    // 但 StateFlow 的值可能還沒更新（或被自己的 collectAsState 搶先），
    // 於是 `groups` 是空的 → `selectGroup()` 不會被呼叫 →
    // `messages` 永遠停在 FirstLoad。
    //
    // 正解（照既有 MessagesPage 的寫法）：**響應式** ——
    // 訂閱 groups，值到了再選分組。這樣不依賴任何時序假設。
    val msgGroupsState by graph.messages.groups.collectAsState()
    val msgSelected by graph.messages.selectedGroup.collectAsState()
    val newsGroupsState by graph.news.groups.collectAsState()
    val newsSelected by graph.news.selectedGroup.collectAsState()
    // 门户的消息分组（服务端的 `listmessagegroup`，标识是名字）。
    // 没有它，页面只能停在「仓库自己选中的第一个分组」上。
    val msgGroups: List<PortalMessageGroup> =
        (msgGroupsState as? ListState.Content)?.items.orEmpty()

    LaunchedEffect(Unit) {
        // ★ 取数交给 `AppGraph.fetch`（应用级作用域）。原因见 `AppGraph.ioScope` 的注释：
        //   挂在组合生命周期上时，切走页面会取消请求，
        //   取消被仓库当成「取不到」永久写进仓库状态，回来就一直报错或一直转圈。
        graph.fetch { graph.messages.refreshGroups() }
        graph.fetch { graph.news.refreshGroups() }
        graph.fetch { graph.rollcall.refresh() }
        graph.fetch { graph.todoBoard.refresh() }
    }

    // groups 到了、且還沒選分組 → 選第一個
    LaunchedEffect(msgGroupsState, msgSelected) {
        val groups = (msgGroupsState as? ListState.Content)?.items.orEmpty()
        if (msgSelected == null) {
            groups.firstOrNull()?.let { g -> graph.fetch { graph.messages.selectGroup(g) } }
        }
    }
    LaunchedEffect(newsGroupsState, newsSelected) {
        val groups = (newsGroupsState as? ListState.Content)?.items.orEmpty()
        if (newsSelected == null) {
            groups.firstOrNull()?.let { g -> graph.fetch { graph.news.selectGroup(g) } }
        }
    }

    // 大標題交給頂欄（T53 的試點抽成共用實作）：標題是列表第 0 項，
    // 跟著內容一起滾，滾出去之後由頂欄接手小標題。
    // 原本這一頁自己畫了一個「消息/應用/設定」的大字 —— 那就是雙標題的來源：
    // 頂欄已經會顯示板塊名，頁內再畫一個，兩處的字體/位置遲早不一致。
    val pilot = rememberPageChromePilot()

    val openRollcalls = (rollState as? ListState.Content)
        ?.items?.filter { !it.expired }.orEmpty()
    val todos = (todoState as? ListState.Content)
        ?.items?.firstOrNull()?.groups?.flatMap { it.items }.orEmpty()
    // 未知不得当成 0（data/ListState.kt 的红线）：只有拿到结论的两种状态才敢给数字。
    // 原来这里把非 Content 一律 `?: 0`，于是「没取到」在筛选条和分组头上都长成「0」。
    // 委托属性（by collectAsState）不能智能转型，所以先落成局部 val 再判。
    val rollNow = rollState
    val todoNow = todoState
    val msgNow = msgState
    val newsNow = newsState
    val rollCount: Int? = when (rollNow) {
        is ListState.Content -> openRollcalls.size
        is ListState.Empty -> 0
        else -> null
    }
    val todoCount: Int? = when (todoNow) {
        is ListState.Content -> todoNow.items.firstOrNull()?.knownTotal
        is ListState.Empty -> 0
        else -> null
    }
    // 分页元信息：hasMore 由「本次返回条数 >= 页大小」算出（ListMeta 的注释），
    // 不能拿服务端 total 算 —— 实测 total=20 而 allrecords=192。
    val msgMeta = (msgNow as? ListState.Content)?.meta
    val msgCount: Int? = when (msgNow) {
        is ListState.Content -> msgNow.items.size
        is ListState.Empty -> 0
        else -> null
    }
    val newsCount: Int? = when (newsNow) {
        is ListState.Content -> newsNow.items.size
        is ListState.Empty -> 0
        else -> null
    }
    val pendingCount: Int? =
        if (rollCount != null && todoCount != null) rollCount + todoCount else null
    val totalCount: Int? =
        if (pendingCount != null && msgCount != null && newsCount != null) {
            pendingCount + msgCount + newsCount
        } else {
            null
        }

    // 小球跟着内容走（业主 2026-10-25「小球不要单独一个图层，要与其他内容同层」）：
    // 它挂在大标题左边，随列表一起滚出屏幕，不再是钉在顶栏上的徽章。
    // 脸色口径不变：「待处理还没取到」→ Loading（眼睛扫一次），**不是** Idle ——
    // 那会把「不知道」说成「没有」。
    val inFlight by graph.inFlight.collectAsState()
    val ballState = AiBallStates.ofCount(pendingCount, fetching = inFlight > 0)

    LazyColumn(
        state = pilot.listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            // 大標題自己帶狀態欄 inset，這裡 top 必須歸零 ——
            // 留著原本那 56dp 會出現「標題下面憑空一大塊空白」。
            top = 0.dp,
            bottom = pageBottomInsetDp(LocalNavBarVisible.current),   // 留出玻璃底欄的高度（讓內容能滾到它下面，玻璃才折射得到東西）
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        largeTitleItem(pilot, leading = { AiBall(state = ballState, hitSlop = AiBall.INLINE_SLOP) })

        item(key = "tabs") {
            NotifyFilters(
                selected = filter,
                total = totalCount,
                pending = pendingCount,
                portal = msgCount,
                news = newsCount,
                onSelect = { filter = it },
            )
        }

        if (filter == 0 || filter == 1) {
            item(key = "todo-header") {
                SectionHeader(text = "需要处理", trailing = pendingCount?.toString() ?: "—")
            }
            // 取不到 ≠ 没有：只有两个来源都拿到结论、且都为空时，才敢说这句话；
            // 任一来源还没结论时只能承认「还没取到」。
            if (rollCount == 0 && todoCount == 0) {
                item(key = "todo-empty") { StatusHint("没有需要处理的事。") }
            } else if (openRollcalls.isEmpty() && todos.isEmpty()) {
                item(key = "todo-unknown") {
                    StatusHint(
                        if (rollCount == null || todoCount == null) {
                            "待处理还没取到。"
                        } else {
                            "没有需要处理的事。"
                        },
                    )
                }
            } else {
                item(key = "todo-card") {
                    Column(Modifier.fillMaxWidth()) {
                            var first = true
                            openRollcalls.forEach { r ->
                                if (!first) ThinDivider()
                                first = false
                                PendingRow(
                                    title = r.title.ifBlank { "现在可以签到" },
                                    subtitle = if (r.endTime.isBlank()) r.sourceLabel
                                    else "截止 ${r.endTime} · ${r.sourceLabel}",
                                    action = "去签到",
                                    onClick = onOpenRollcall,
                                )
                            }
                            todos.take(2).forEach { t ->
                                if (!first) ThinDivider()
                                first = false
                                PendingRow(
                                    title = t.title,
                                    subtitle = listOfNotNull(
                                        t.subtitle.takeIf { it.isNotBlank() },
                                        t.timeText.takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                    action = "查看",
                                    onClick = onOpenTodos,
                                )
                            }
                    }
                }
            }
        }

        if (filter == 0 || filter == 2) {

        // ══════════ 消息 ══════════
        item(key = "msg-header") {
            // 不叫「消息」：頁面大標題已經是「消息」，分組再來一個同名大字
            // 看起來像標題重複。入口在首頁就叫「门户通知」，這裡沿用同一個說法。
            //
            // ★ 不再放「全部 ›」：它以前只把 selectedTab 設成 2 —— 而這裡就是 2，
            //   點下去等於什麼都沒發生（审查 2026-10-21）。門戶詳情路由還沒接，
            //   所以整段直接展開、行不可點：沒有死入口，也不用拿假動作騙人。
            // 板 `.group-head`：小灰点 + 组名(8.9sp/700) + 条数(8sp) + 一条吃掉余宽的发丝线。
            // 原来这里用 13sp 粗体大字，于是这一屏出现三个同级别的大标题，
            // 板上的层级（一个大标题 + 若干小分组头）被压平了（复评 2026-10-21）。
            // 「全部已读」只对**当前选中的那一组**（门户没有跨组接口）。
            // 数字取 libmessagegroup 的 noread，而不是列表长度：列表只是一页（10 条）。
            val openGroup = msgGroups.firstOrNull { it.name == msgSelected?.name }
            GroupHead(
                name = "门户通知",
                count = msgCount ?: 0,
                // 门户一个分组有 189 条未读，而列表一次只取 10 条（rows=10 是门户写死的）。
                // 只写「10 条」会让人以为这一组只有 10 条 —— 剩下 179 条等于不存在。
                note = when {
                    msgCount == null -> "—"
                    msgMeta?.hasMore == true -> "$msgCount 条 · 还有更多"
                    else -> ""
                },
            ) {
                if (openGroup != null && openGroup.unread > 0) {
                    ReadAllAction(onClick = { confirmReadAll = openGroup })
                }
            }
        }

        // 分组切换：`listmessage` 一次只返回**一个分组**，而首位分组会变
        // （2026-10-30 实测在「讲座通知」与「活动通知」之间来回）。
        // 没有这排胶囊，另一个分组对用户等于不存在。
        item(key = "msg-groups") {
            MsgGroupChips(
                groups = msgGroups,
                selected = msgSelected,
                onSelect = { g -> graph.fetch { graph.messages.selectGroup(g) } },
            )
        }

        when (val s = msgState) {
            is ListState.FirstLoad -> item(key = "msg-loading") { StatusHint("正在取得消息…") }

            is ListState.Failed -> item(key = "msg-failed") {
                StatusError(title = "取不到消息", detail = s.error.userText)
            }

            is ListState.Content -> {
                val items = s.items
                if (items.isEmpty()) {
                    item(key = "msg-empty") { StatusHint("没有消息。") }
                } else {
                    // 一條一張卡會讓同一個板塊看起來像十個獨立入口；
                    // 收成一張分組卡，掃讀時眼睛只需認一次邊界。
                    // 全部列出（不再 take(5)）：既然沒有「全部」頁，少畫的那幾條就真丢了。
                    item(key = "msg-card") { MessageGroup(items, onOpenUrl, onRead) }
                    // 门户一次只回 10 条（rows 写死），没有这个入口，第 11 条之后
                    // 在界面上根本走不到 —— 而那一组有一百多条未读。
                    if (msgMeta?.hasMore == true) {
                        item(key = "msg-more") {
                            MoreRow(
                                loading = msgMeta.loadingMore,
                                onClick = { graph.fetch { graph.messages.loadMore() } },
                            )
                        }
                    }
                }
            }

            else -> item(key = "msg-none") { StatusHint("没有消息。") }
        }
        }

        if (filter == 0 || filter == 3) {

        // ══════════ 新聞 ══════════
        item(key = "news-header") {
            // 同样不放「更多 ›」：它以前把新闻跳到 cjh52 的「日常通知」，
            // 那是另一个系统、另一份数据（审查 2026-10-21）。没有正确的目的地就不画入口。
            GroupHead(name = "新闻", count = newsCount ?: 0, note = if (newsCount == null) "—" else "")
        }

        when (val s = newsState) {
            is ListState.FirstLoad -> item(key = "news-loading") { StatusHint("正在取得新闻…") }

            is ListState.Failed -> item(key = "news-failed") {
                StatusError(title = "取不到新闻", detail = s.error.userText)
            }

            is ListState.Content -> {
                val items = s.items
                if (items.isEmpty()) {
                    item(key = "news-empty") { StatusHint("暂时没有新闻。") }
                } else {
                    item(key = "news-card") { NewsGroup(items) }
                }
            }

            else -> item(key = "news-none") { StatusHint("暂时没有新闻。") }
        }
        }
    }

    val pendingGroup = confirmReadAll
    OverlayDialog(
        show = pendingGroup != null,
        onDismissRequest = { confirmReadAll = null },
        title = "全部标成已读",
        summary = "把「" + (pendingGroup?.name ?: "") + "」这一组的 " +
            (pendingGroup?.unread ?: 0) + " 条未读一次标成已读。" +
            "门户没有撤销接口，标完只能重新等新消息。",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { confirmReadAll = null },
                modifier = Modifier.weight(1f),
            )
            top.yukonga.miuix.kmp.basic.Button(
                onClick = {
                    val g = confirmReadAll
                    confirmReadAll = null
                    g?.let { onReadAll(it.name) }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "全部已读")
            }
        }
    }
}

@Composable
private fun NotifyFilters(
    selected: Int,
    total: Int?,
    pending: Int?,
    portal: Int?,
    news: Int?,
    onSelect: (Int) -> Unit,
) {
    // 计数为 null = 那一栏还没取到。写「—」而不是 0（data/ListState.kt 的红线）。
    // 计数单独成一个 Text：板上 .chip .n 带 opacity:.62，数字比标签淡一档。
    fun pillCount(n: Int?): String = n?.toString() ?: "—"
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterPill("全部", pillCount(total), selected == 0) { onSelect(0) }
        FilterPill("待处理", pillCount(pending), selected == 1) { onSelect(1) }
        FilterPill("门户", pillCount(portal), selected == 2) { onSelect(2) }
        FilterPill("新闻", pillCount(news), selected == 3) { onSelect(3) }
    }
}

/**
 * 消息分组胶囊。
 *
 * 为什么必须能切：`listmessage` 一次只回**一个分组**的行，而服务端排在第一的分组
 * 会变（2026-10-30 实测在「讲座通知」与「活动通知」之间来回）。切不了的话，
 * 另一个分组对用户等于不存在 —— 一百多条未读就藏在看不见的那一组里。
 *
 * 分组标识是**名字**不是 id（data/portal/PortalModels.kt 的注释），所以选中态按名字比。
 * 胶囊样式直接复用 [FilterPill]：两排胶囊长成两种样子，用户会以为它们是两种东西。
 */
@Composable
private fun MsgGroupChips(
    groups: List<PortalMessageGroup>,
    selected: PortalMessageGroup?,
    onSelect: (PortalMessageGroup) -> Unit,
) {
    // 只有一个分组时这排胶囊不提供任何选择，白占一行高度。
    if (groups.size < 2) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 门户实测 9 个分组，一屏放不下；横滑而不是折行，免得把消息列表顶出屏幕。
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { g ->
            key(g.name) {
                FilterPill(
                    label = g.name,
                    count = g.unread.toString(),
                    selected = selected?.name == g.name,
                    onClick = { onSelect(g) },
                )
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(50)
    // 板 `.chip{font-size:10px;font-weight:600;padding:6px 11px;border:1px solid var(--line)}`
    // × 0.886 ⇒ 8.9sp、5.3×9.7dp。
    // **字重不随选中变**（硬规则 1：加粗让文字变宽，四条并排时整行会横抖一下；
    // 板上 `.chip.on` 也只换底色与字色）。选中态 = 板上那条 `background:var(--ink)`。
    val ink = if (selected) colors.background else colors.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.onBackground else Color.Transparent)
            .then(
                if (selected) Modifier
                else Modifier.border(
                    1.dp,
                    colors.dividerLine,
                    shape,
                )
            )
            .clickable(onClickLabel = "筛选" + label) { onClick() }
            .padding(horizontal = 9.7.dp, vertical = 6.2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
    ) {
        Text(
            text = label,
            fontSize = Type.caption,
            fontWeight = Type.weightBody,
            color = ink,
            maxLines = 1,
        )
        // 板 `.chip .n{opacity:.62;margin-left:4px}` —— 数字比标签淡一档是板上的信息层级，
        // 不是装饰：数字会变、标签不变。实测板选中胶囊里标签 238.7、数字 157.0（= 0.62）。
        Text(
            text = count,
            fontSize = Type.caption,
            fontWeight = Type.weightBody,
            color = ink.copy(alpha = 0.62f),
            maxLines = 1,
            style = TextStyle(fontFeatureSettings = Type.tabular),
        )
    }
}

/** 待处理的一行：标题 / 时间，右侧一个真入口（去签到、查看待办）。 */
@Composable
private fun PendingRow(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = action) { onClick() }
            // 板 `.action-row{padding:9px 0}` ⇒ 8dp；
            // 原来 8dp 上下 + 行内间距把整行撑到 53 CSS（板 45.8）。
            .padding(vertical = 6.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                // 板 `.action-row .dot{width:7px;box-shadow:0 0 0 3px var(--accent-soft)}`
                // × 0.886 ⇒ 6.2dp 的点外面再套一圈 2.7dp 的 accent-soft 光环。
                // 原来只画一颗 6dp 实心点，板上那颗是「被高亮过的一颗」—— 这圈光晕
                // 就是「要你处理」和「一条普通消息」的区别。
                .size(12.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(6.2.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                // 板 `.action-row .t{font-size:12px;font-weight:640}` × 0.886 ⇒ 10.6sp
                fontSize = Type.summary,
                fontWeight = Type.weightBody,
                color = colors.onBackground,
                // 板 `.action-row .t{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}`
                // ⇒ **一行**。折成两行会把右边的按钮顶偏、行高逐行漂移（复评 2026-10-21）。
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        val filled = action == "去签到"
        Text(
            text = action,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            // 板 `.action-row .btn.ghost`：字色 --sub、描边 --line-strong。
            // 用强调绿画次级按钮，等于让每一行都有两颗主按钮。
            color = if (filled) colors.onPrimary else colors.onSurfaceVariantSummary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (filled) colors.primary else Color.Transparent)
                .then(
                    if (filled) Modifier
                    else Modifier.border(
                        1.dp,
                        colors.dividerLine,
                        RoundedCornerShape(50),
                    )
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SectionHeader(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 頂部 8 加上列表的 10，剛好是分區之間要的 18dp 呼吸；
            // 不用額外的占位 item，避免 key 與高度都要多管一份。
            .padding(top = Dims.sectionGap, start = 0.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = Type.body,
            fontWeight = Type.weightTitle,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = Type.caption,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 加載 / 空的共用說明。
 *
 * 以前這些狀態各包一張卡片，空頁面反而比有內容時更重；
 * 統一成一行低調文字，讓「沒有東西」安靜地待著。
 */
@Composable
private fun StatusHint(text: String) {
    Text(
        text = text,
        fontSize = Type.caption,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = Dims.cardPadding, vertical = Dims.tight),
    )
}

/**
 * 失敗說明。
 *
 * 錯誤不能藏：標題保留 error 色，下面照舊給 `userText` 的具體原因。
 */
@Composable
private fun StatusError(title: String, detail: String) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.padding(horizontal = Dims.cardPadding, vertical = Dims.tight)) {
        Text(text = title, fontSize = Type.body, color = colors.error)
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(text = detail, fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
        }
    }
}

/**
 * 消息分組卡。
 *
 * 未讀只用圓點標（規範紅線：不用字重，避免字寬變化造成行內抖動）。
 */
@Composable
private fun MessageGroup(
    items: List<PortalMessageItem>,
    onOpenUrl: (String) -> Unit,
    onRead: (String) -> Unit,
) {
    // 板子：消息直接铺在纸上，只有行与行之间的发丝线，没有一整张白卡。
    //
    // **按来源分组**（板 `.group-head`）：门户每条消息自带 `group` 字段，
    // 那是学校给的原文分类，不是我编的。以前把十几条平铺成一根长列表，
    // 复评判「板块内部没有层级」；板上的层级就是这一行小灰点 + 组名 + 条数 + 发丝线。
    val groups = items.groupBy { it.group }.values
    Column(Modifier.fillMaxWidth()) {
        // 只有一个来源组时**不画内层组头**：外层已经写了「门户通知 N 条」，
        // 再顶一个小灰点组头就是两个头之间零行，中间空出一条带（复评 2026-10-21）。
        val showInnerHeads = groups.size > 1
        groups.forEachIndexed { gi, grp ->
            val name = grp.first().group.ifBlank { "其它通知" }
            key("grp-$gi-$name") {
                if (showInnerHeads) {
                    GroupHead(name = name, count = grp.size)
                }
                grp.forEachIndexed { idx, m ->
                    key("msg-$gi-$idx-${m.id}") {
                        MessageRow(m, onOpenUrl, onRead)
                        if (idx != grp.lastIndex) ThinDivider()
                    }
                }
            }
        }
    }
}

/** 板 `.group-head{i,gname,gn,line}` × 0.886 ⇒ 点 5.3dp、名 9sp/700、数 8sp、线 0.3dp。 */
/**
 * 「加载更多」一行。
 *
 * 只在这一组还有下一页时出现（ListMeta.hasMore）；
 * 取的时候显示「正在加载…」，而不是把按钮留在原地让人连点两次。
 */
@Composable
private fun MoreRow(loading: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Text(
                text = "正在加载…",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            TextButton(
                text = "加载更多",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GroupHead(
    name: String,
    count: Int,
    note: String = "",
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.3.dp)
                .clip(CircleShape)
                .background(colors.onSurfaceVariantSummary.copy(alpha = 0.55f)),
        )
        Text(
            text = name,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            letterSpacing = 0.02.em,
            color = colors.onSurfaceVariantSummary,
        )
        Text(
            text = if (note.isNotBlank()) note else count.toString() + " 条",
            fontSize = Type.micro,
            color = colors.onSurfaceVariantSummary.copy(alpha = 0.75f),
            style = TextStyle(fontFeatureSettings = Type.tabular),
        )
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .weight(1f)
                .height(0.7.dp)
                .background(colors.dividerLine),
        )
        trailing?.invoke()
    }
}

/**
 * 组头右边的「全部已读」。
 *
 * 故意**不用 [FilterPill]**：那是一排「选哪个分组」的开关，长得一样会让人以为
 * 点下去只是换个分组，而这里会一次改掉一百多条消息的状态。
 * 所以它是纯文字 + 强调色，落在发丝线右侧，像一行小注脚。
 */
@Composable
private fun ReadAllAction(onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = "全部已读",
        fontSize = Type.micro,
        fontWeight = Type.weightTitle,
        color = colors.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = "把这一组全部标成已读") { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun MessageRow(
    m: PortalMessageItem,
    onOpenUrl: (String) -> Unit,
    onRead: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // 門戶自己對 openurl 為空的行不給點也不畫箭頭，只 toast 一句
    // 「未獲取到消息通知地址」。我們照同一條界線來（docs/API-PORTAL.md §1.3）。
    // 以前整行可點但點下去只是把「消息」tab 再選一次 —— 那是死入口（審查 2026-10-21）。
    val openUrl = m.openUrl?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (openUrl == null) {
                    Modifier
                } else {
                    Modifier.clickable(onClickLabel = "打开这条通知") {
                        // 先跳转再标已读：跳转是用户看得见的反馈，不能被一次网络往返挡住。
                        onOpenUrl(openUrl)
                        if (!m.isRead) onRead(m.id)
                    }
                }
            )
            .padding(horizontal = 0.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(Dims.loose),
        // 頂對齊是為了標題折成兩行時，日期仍停在第一行。
        verticalAlignment = Alignment.Top,
    ) {
        // 已讀也佔同寬的空位，標題左邊線才不會逐行跳。
        UnreadMark(unread = !m.isRead)
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dims.normal),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = m.title.ifBlank { "（无标题）" },
                    // 板 `.mrow .t{font-size:12px;font-weight:620}` × 0.886 ⇒ 10.6sp / 620
                    //    未读 = --ink，已读 = --sub（板上已读是**整条变淡**，不是同号同色）
                    fontSize = Type.summary,
                    fontWeight = if (m.isRead) FontWeight.Normal else Type.weightBody,
                    color = if (m.isRead) colors.onSurfaceVariantSummary else colors.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (m.createdDate.isNotBlank()) {
                    Text(
                        // 只留日期：門戶給的是「2025-11-06 18:00:00」，
                        // 96dp 裡放不下，真機上會被截成「2025-11-06 18:…」。
                        // 清單裡日期就夠定位，精確到分沒有用。
                        text = boardShortDate(m.createdDate),
                        fontSize = Type.micro,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 日期只是時間戳，不能長到把標題壓成一兩個字一行。
                        modifier = Modifier.widthIn(max = 96.dp),
                    )
                }
            }
            if (m.message.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = m.message,
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (openUrl != null) {
            // 箭頭只是「這行能點」的信號，不是獨立按鈕（整行都可點）。
            // 光學對齊首行文字，所以用固定 padding 而不是垂直居中。
            Text(
                text = "›",
                fontSize = Type.title,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun UnreadMark(unread: Boolean) {
    Box(
        modifier = Modifier
            // 讓 6dp 的圓點落在首行文字的光學中線上；用固定 padding 而不是
            // 垂直居中，否則標題兩行時圓點會跑到兩行中間。
            .padding(top = 7.dp)
            .size(6.dp)
            .then(
                if (unread) {
                    Modifier.background(MiuixTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            ),
    )
}

/**
 * 新聞分組卡。
 *
 * 不配縮略圖：門戶沒有穩定圖源，外鏈圖只會帶來加載失敗與版權風險。
 */
@Composable
private fun NewsGroup(items: List<PortalNewsItem>) {
    // 板上 `.newsrow` 之间**没有分隔线**（只有 6px 行距）：
    // 画上线就把「一列新闻」变成一张表格，和上面消息那种 hairline 列表撞成一类。
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { idx, n ->
            key("news-$idx-${n.id}") {
                NewsRow(n)
            }
        }
    }
}

@Composable
private fun NewsRow(n: PortalNewsItem) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 5.3.dp),
        horizontalArrangement = Arrangement.spacedBy(Dims.normal),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = n.title.ifBlank { "（无标题）" },
            // 板 `.newsrow{font-size:10.5px;color:var(--sub)}` × 0.886 ⇒ 9.3sp / --sub。
            // 新聞比消息低半級（板上就是這個意思），不該吃 13sp 的主行檔。
            fontSize = Type.summary,
            color = colors.onSurfaceVariantSummary,
            // 板 `.newsrow .nt{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}`
            // 是**一行**：真机上折成两行时右侧日期会跟着掉，整排对不齐（17:04 截图）。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (n.createdDate.isNotBlank()) {
            Text(
                // 同消息行：只留日期，別讓時間把日期截成省略號。
                text = boardShortDate(n.createdDate),
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

/**
 * 板上的时间列只放**短式**：`.mrow .when` 是「10:05 / 昨天 / 10/20」，不是完整日期时间。
 * 门户给的是「2026-10-21 18:00:00」这种全串，96dp 里放不下（真机上会被截成
 * 「2026-10-21 18:…」）。**只裁格式，不编内容**：今年的写成 MM/DD，
 * 往年的保留年份，解析不出来就原样给回（宁可长一点也不瞎猜）。
 */
internal fun boardShortDate(raw: String): String {
    val date = raw.trim().substringBefore(' ')
    val parts = date.split('-', '.')
    if (parts.size != 3) return raw.trim()
    val year = parts[0].toIntOrNull() ?: return date
    val month = parts[1].toIntOrNull() ?: return date
    val day = parts[2].toIntOrNull() ?: return date
    return if (year == java.time.LocalDate.now().year) "$month/$day" else date
}

/** 分組卡內的分隔線：0.5dp 是髮絲線，1dp 會把列表畫成表格。 */
@Composable
private fun ThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}
