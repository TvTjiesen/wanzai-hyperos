package com.muyao.wanzaihyperos.ui.main.pages.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.portal.PortalMessageItem
import com.muyao.wanzaihyperos.data.portal.PortalNewsItem
import com.muyao.wanzaihyperos.ui.design.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun notifyGroupCountLabel(loaded: Int?, groupUnread: Int?, hasMore: Boolean): String =
    "已加载 ${loaded?.toString() ?: "—"} 条" +
        (if (hasMore) " · 还有更多" else "") +
        " · 当前组未读 ${groupUnread?.toString() ?: "—"}（服务端）"

internal fun notifyHeroLabel(totalUnread: Int?, stale: Boolean, clock: String): String = buildString {
    if (totalUnread != null) {
        append("总未读 $totalUnread（服务端）")
        if (stale) append(" · 上次结果")
    }
    if (clock.isNotEmpty()) {
        if (isNotEmpty()) append(" · ")
        append(clock)
    }
}

internal fun notifySearchRangeLabel(source: String, loaded: Int, matches: Int, hasMore: Boolean): String =
    "在已加载 $loaded 条${source}中" + (if (matches == 0) "无匹配" else "匹配 $matches 条") +
        (if (hasMore) "；还有后页，可继续加载后查找。" else "；已加载到末页。")

internal fun notifyMatchingMessages(items: List<PortalMessageItem>, query: String): List<PortalMessageItem> =
    items.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) ||
        it.message.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true) }

internal fun notifyMatchingNews(items: List<PortalNewsItem>, query: String): List<PortalNewsItem> =
    items.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }

internal fun notifyFreshnessLabel(meta: ListMeta, zone: ZoneId = ZoneId.systemDefault()): String =
    com.muyao.wanzaihyperos.data.dataFreshnessLabel(meta, zone)

internal fun notifyMessageDateLabel(items: List<PortalMessageItem>, hasMore: Boolean): String {
    val latest = items.mapNotNull { item ->
        runCatching { LocalDate.parse(item.createdDate.trim().take(10)) }.getOrNull()
    }.maxOrNull()
    val date = latest?.toString() ?: "未知"
    return (if (hasMore) "已加载通知最新日期：" else "通知最新日期：") + date + " · 检查时间不是通知日期"
}

/** Only the already-loaded payload is shown. This component has no repository or read callback. */
@Composable
fun NotifyLocalFullTextSheet(
    message: PortalMessageItem,
    onDismiss: () -> Unit,
    onOpenDetail: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.88f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("本地全文 · 只读", fontSize = Type.body, fontWeight = Type.weightTitle, color = colors.onSurface)
                SelectionContainer(Modifier.weight(1f, fill = false)) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (onOpenDetail == null) {
                            Text("没有可打开的学校详情链接。", fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
                        }
                        Text(message.title.ifBlank { "（原标题为空）" }, fontSize = Type.title, fontWeight = Type.weightTitle,
                            color = colors.onSurface, modifier = Modifier.semantics { heading() })
                        Text("来源：门户 · ${message.group.ifBlank { "分组未提供" }}", fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
                        Text("原始日期：${message.createdDate.ifBlank { "未提供" }}", fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
                        if (message.lastUpdateDate.isNotBlank()) {
                            Text("原始更新日期：${message.lastUpdateDate}", fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
                        }
                        Text(message.message.ifBlank { "（门户未提供正文）" }, fontSize = Type.body, color = colors.onSurface)
                    }
                }
                if (onOpenDetail != null) {
                    TextButton("打开学校详情", onClick = onOpenDetail, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                }
                TextButton("关闭全文", onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
        }
    }
}
