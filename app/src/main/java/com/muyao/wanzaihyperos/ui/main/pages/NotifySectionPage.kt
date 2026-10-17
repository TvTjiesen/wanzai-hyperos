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

    LaunchedEffect(Unit) {
        // 訊息：先取分組，再取第一個分組的訊息。
        // 這兩步是必須的 —— `messages` 依賴 `selectedGroup`，
        // 不先選分組就永遠是空的（那是既有倉庫的契約）。
        runCatching {
            graph.messages.refreshGroups()
            val groups = (graph.messages.groups.value as? ListState.Content)?.items.orEmpty()
            groups.firstOrNull()?.let { graph.messages.selectGroup(it) }
        }
        // 新聞同理。
        runCatching {
            graph.news.refreshGroups()
            val groups = (graph.news.groups.value as? ListState.Content)?.items.orEmpty()
            groups.firstOrNull()?.let { graph.news.selectGroup(it) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            top = 56.dp,
            bottom = 16.dp,
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
                    items.take(5).forEach { m ->
                        item(key = "msg-${m.id}") { MessageRow(m) }
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
                    items.take(6).forEach { n ->
                        item(key = "news-${n.id}") { NewsRow(n) }
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
