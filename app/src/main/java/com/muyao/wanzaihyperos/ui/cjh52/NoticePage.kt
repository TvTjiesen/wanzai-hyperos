package com.muyao.wanzaihyperos.ui.cjh52

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
import com.muyao.wanzaihyperos.data.cjh52.Cjh52Notice
import com.muyao.wanzaihyperos.data.cjh52.NoticeRepository
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日常通知（B3）。
 *
 * ### 為什麼做它
 *
 * 它是**「要我讀的公告」**：學校/學院發的、按你的院系年級專業篩過的。
 * 以前只能在門戶裡一層層點進去，現在原生一頁看完。
 *
 * ### 契約（真機實測）
 *
 * ```
 * POST courier/notice_service/find_all_for_reader
 *   { scope: {school, college[], grade[], specialty[], role:"stu"}, pageSize: 10, pageNum: 0 }
 *   → data.data[] = { id, title, username, detail, createTime, pubTime, level }
 * ```
 * `scope` 的四個值從 `get_user_by_token` 推出來 —— 不是硬編碼院系。
 */
@Composable
fun NoticePage(
    repository: NoticeRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
    ready: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val notices = (state as? ListState.Content)?.items.orEmpty()

    LaunchedEffect(ready) {
        if (ready && state is ListState.FirstLoad) repository.refresh()
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "日常通知",
color = Color.Transparent,
                subtitle = "來自場景管理（cjh52）",
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
        if (notices.isEmpty()) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier.padding(innerPadding),
                detail = if (pageState == PageState.Empty) "目前没有通知" else null,
                onAction = onFallbackToWeb,
                actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "共 ${notices.size} 条" +
                            if (pageState == PageState.Stale) "（这次没刷新成功，显示上一次的）" else "",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(notices, key = { it.id }) { notice -> NoticeCard(notice) }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: Cjh52Notice) {
    val colors = MiuixTheme.colorScheme
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = notice.title.ifBlank { "（无标题）" },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 級別不是「普通」時才標出來 —— 全部標等於沒標。
                if (notice.level.isNotBlank() && notice.level != "普通") {
                    Text(
                        text = notice.level,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.primary,
                    )
                }
            }

            if (notice.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notice.detail,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                    // 通知正文可以很長（實測 207 字元），列表裡只露三行。
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))
            val meta = buildList {
                if (notice.publisher.isNotBlank()) add(notice.publisher)
                formatMillis(notice.sortKey)?.let { add(it) }
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

/**
 * 毫秒時間戳 → `yyyy-MM-dd HH:mm`。
 *
 * 為什麼要單獨一個函式：cjh52 的時間欄位是**毫秒數**（實測 13 位），
 * 直接顯示會是一串數字；而用 `Int` 解析會**靜默溢出**（見 `asLongOrNull` 的註釋）。
 * 時間戳為 0 或 null 時回 null（**不顯示 1970 年**）。
 */
private fun formatMillis(millis: Long?): String? {
    if (millis == null || millis <= 0L) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }.getOrNull()
}
