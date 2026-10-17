package com.muyao.wanzaihyperos.ui.main.pages

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.portal.PortalMessageItem
import com.muyao.wanzaihyperos.data.portal.PortalNewsItem
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 訊息板塊：訊息與新聞分兩段。
 *
 * 為什麼要分開：
 * - 訊息是「給你的」（審批結果、通知），常要動作，會過期；
 * - 新聞是「給所有人看的」（校園新聞），通常只看一眼，不會過期。
 *
 * 混在一條時間軸上，使用者分不清「哪條需要我做事」。
 *
 * 資料來源：
 * - messages.messages（門戶消息介面，第一個分組的訊息）
 * - news.news（門戶新聞介面）
 *
 * 介面上不寫「數據來自某某介面」那種開發說明 ——
 * 那是開發者要知道的事，不是使用者要看的。
 */
@Composable
fun NotifySectionPage(
    graph: AppGraph,
    onOpenMessages: () -> Unit,
    onOpenNews: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val msgState by graph.messages.messages.collectAsState()
    val newsState by graph.news.news.collectAsState()

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

    LaunchedEffect(Unit) {
        runCatching { graph.messages.refreshGroups() }
        runCatching { graph.news.refreshGroups() }
    }

    // groups 到了、且還沒選分組 → 選第一個
    LaunchedEffect(msgGroupsState, msgSelected) {
        val groups = (msgGroupsState as? ListState.Content)?.items.orEmpty()
        if (msgSelected == null) {
            groups.firstOrNull()?.let { runCatching { graph.messages.selectGroup(it) } }
        }
    }
    LaunchedEffect(newsGroupsState, newsSelected) {
        val groups = (newsGroupsState as? ListState.Content)?.items.orEmpty()
        if (newsSelected == null) {
            groups.firstOrNull()?.let { runCatching { graph.news.selectGroup(it) } }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            top = 56.dp,
            bottom = 120.dp,   // 留出玻璃底欄的高度（讓內容能滾到它下面，玻璃才折射得到東西）
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        item(key = "title") {
            Text(
                text = "訊息",
                fontSize = Type.largeTitle,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                lineHeight = Type.largeTitle * Type.LINE_HEIGHT_TITLE,
            )
        }

        // ══════════ 訊息 ══════════
        item(key = "msg-header") {
            SectionHeader(text = "訊息", action = "全部 ›", onAction = onOpenMessages)
        }

        when (val s = msgState) {
            is ListState.FirstLoad -> item(key = "msg-loading") { Hint("正在取得訊息…") }

            is ListState.Failed -> item(key = "msg-failed") {
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text("取不到訊息", fontSize = Type.body, color = colors.error)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            s.error.message.orEmpty(),
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            is ListState.Content -> {
                val items = s.items
                if (items.isEmpty()) {
                    item(key = "msg-empty") { Hint("沒有訊息。") }
                } else {
                    // key 用 index 兜底：服務端 id 實測有重複（撞過 Key already used）
                    items.take(5).forEachIndexed { idx, m ->
                        item(key = "msg-$idx-${m.id}") { MessageRow(m) }
                    }
                }
            }

            else -> item(key = "msg-none") { Hint("沒有訊息。") }
        }

        // ══════════ 新聞 ══════════
        item(key = "news-header") {
            SectionHeader(text = "新聞", action = "更多 ›", onAction = onOpenNews)
        }

        when (val s = newsState) {
            is ListState.FirstLoad -> item(key = "news-loading") { Hint("正在取得新聞…") }

            is ListState.Failed -> item(key = "news-failed") {
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text("取不到新聞", fontSize = Type.body, color = colors.error)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            s.error.message.orEmpty(),
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            is ListState.Content -> {
                val items = s.items
                if (items.isEmpty()) {
                    item(key = "news-empty") { Hint("暫時沒有新聞。") }
                } else {
                    items.take(6).forEachIndexed { idx, n ->
                        item(key = "news-$idx-${n.id}") { NewsRow(n) }
                    }
                }
            }

            else -> item(key = "news-none") { Hint("暫時沒有新聞。") }
        }
    }
}

@Composable
private fun SectionHeader(text: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = Type.title,
            fontWeight = Type.weightTitle,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            fontSize = Type.caption,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.clickable { onAction() },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        fontSize = Type.secondary,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(Dims.cardPadding),
    )
}

/**
 * 一條訊息。
 *
 * 未讀**用顏色標**，不改字重（規範紅線 #6）。
 */
@Composable
private fun MessageRow(m: PortalMessageItem) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        Column(Modifier.padding(Dims.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = m.title.ifBlank { "（無標題）" },
                    fontSize = Type.body,
                    // 未讀用顏色區分，不用字重（避免寬度變化造成抖動）
                    color = if (m.isRead) colors.onSurfaceVariantSummary else colors.onBackground,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (!m.isRead) {
                    Spacer(Modifier.height(0.dp))
                    Text(text = "未讀", fontSize = Type.micro, color = colors.primary)
                }
            }
            if (m.message.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = m.message,
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                )
            }
            if (m.createdDate.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = m.createdDate,
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 一條新聞。 */
@Composable
private fun NewsRow(n: PortalNewsItem) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        Column(Modifier.padding(Dims.cardPadding)) {
            Text(
                text = n.title.ifBlank { "（無標題）" },
                fontSize = Type.body,
                color = colors.onBackground,
                maxLines = 2,
            )
            if (n.createdDate.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = n.createdDate,
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}
