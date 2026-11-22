package com.muyao.wanzaihyperos.ui.main.pages.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.portal.PortalBroadcastDetail
import com.muyao.wanzaihyperos.data.portal.PortalBroadcastItem
import com.muyao.wanzaihyperos.data.portal.PortalBroadcastParser
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.notifyBroadcastItems(
    state: ListState<PortalBroadcastItem>,
    onRefresh: () -> Unit,
    onOpen: (PortalBroadcastItem) -> Unit,
    onAccounts: () -> Unit,
) {
    item(key = "broadcast-header") {
        Text("系统公告", fontSize = Type.body, fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 16.dp))
    }
    when (state) {
        ListState.FirstLoad -> item(key = "broadcast-loading") { BroadcastHint("正在获取公告…") }
        ListState.SessionExpired -> item(key = "broadcast-login") {
            BroadcastHint("门户登录已失效")
            TextButton("管理账号", onAccounts)
        }
        is ListState.Failed -> item(key = "broadcast-error") {
            BroadcastHint(state.error.userText)
            TextButton("重试公告", onRefresh)
        }
        is ListState.Empty -> item(key = "broadcast-empty") {
            BroadcastHint(if (state.meta.hasRefreshProblem) "更新未完成，上次没有公告" else "暂无系统公告")
            TextButton("刷新公告", onRefresh)
        }
        is ListState.Content -> {
            item(key = "broadcast-freshness") {
                BroadcastHint(notifyFreshnessLabel(state.meta))
                if (state.meta.hasRefreshProblem) TextButton("重试公告", onRefresh)
                if (state.items.size >= 1000) BroadcastHint("已达到本次读取上限")
            }
            state.items.forEach { announcement ->
                item(key = "broadcast-${announcement.id}") {
                    Column(Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "查看公告") { onOpen(announcement) }
                        .heightIn(min = 56.dp).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(announcement.title, fontSize = 16.sp, lineHeight = 23.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onBackground)
                        if (announcement.description.isNotBlank()) Text(
                            PortalBroadcastParser.plainText(announcement.description), fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        if (announcement.sendTime.isNotBlank()) BroadcastHint(announcement.sendTime)
                    }
                }
            }
        }
    }
}

@Composable
fun NotifyBroadcastSheet(
    selected: PortalBroadcastItem,
    state: ListState<PortalBroadcastDetail>,
    onRetry: () -> Unit,
    onAccounts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.88f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(selected.title, fontSize = Type.title, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                if (selected.sendTime.isNotBlank()) BroadcastHint(selected.sendTime)
                SelectionContainer(Modifier.weight(1f, fill = false)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (state) {
                            ListState.FirstLoad -> BroadcastHint("正在获取正文…")
                            ListState.SessionExpired -> {
                                BroadcastHint("门户登录已失效")
                                TextButton("管理账号", onAccounts)
                            }
                            is ListState.Failed -> {
                                BroadcastHint(state.error.userText)
                                TextButton("重试正文", onRetry)
                            }
                            is ListState.Empty -> BroadcastHint("学校未提供正文")
                            is ListState.Content -> {
                                val detail = state.items.firstOrNull { it.item.id == selected.id }
                                if (detail != null) {
                                    Text(PortalBroadcastParser.plainText(detail.content).ifBlank { "学校未提供文字正文" },
                                        fontSize = Type.body, color = colors.onSurface)
                                    if (Regex("<\\s*img\\b", RegexOption.IGNORE_CASE).containsMatchIn(detail.content))
                                        BroadcastHint("正文含图片，此处仅显示文字")
                                }
                            }
                        }
                    }
                }
                TextButton("关闭", onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
        }
    }
}

@Composable
private fun BroadcastHint(text: String) {
    Text(text, fontSize = Type.caption, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
