package com.muyao.wanzaihyperos.ui.tronclass

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.HomeworkRepository
import com.muyao.wanzaihyperos.data.tronclass.TronHomework
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

/**
 * 某門課的作業（原生，**只讀**）。
 *
 * ### 這一頁解決什麼
 *
 * 待辦清單只有**標題**（實測 `title` 只有 6 個字），
 * 使用者看到「1、绪论部分」完全不知道要交什麼。
 * 這一頁給出**要求說明、截止時間、可交次數** —— 那才是能動手的資訊。
 *
 * ### ★ 為什麼不提供「提交」
 *
 * 提交是寫操作，後果是成績。本專案一貫不做寫操作
 *（§B4 充值 / §B5 續借 / §B9 待辦同一原則）。
 * 所以頁面底部明確給「去网页版交」的出口，而不是做一個半可靠的提交按鈕。
 */
@Composable
fun HomeworkPage(
    repository: HomeworkRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val courseName by repository.courseName.collectAsState()
    val pageState = state.toPageState()
    val items = (state as? ListState.Content)?.items.orEmpty()

    // 載入由呼叫方（選課那一刻）觸發；這裡只處理「還沒有資料就補一次」。
    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.refresh() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "作业",
                subtitle = courseName.ifBlank { "学在城院" },
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
        if (items.isEmpty()) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier.padding(innerPadding),
                detail = if (pageState == PageState.Empty) "这门课没有作业" else null,
                onAction = onFallbackToWeb,
                actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { hw -> HomeworkCard(hw) }
                if (onFallbackToWeb != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "要交作业？提交是写操作（会记成绩），这里只做只读查看。",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "去网页版提交 ›",
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onFallbackToWeb() }
                                        .padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkCard(hw: TronHomework) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row {
                Text(
                    text = hw.title.ifBlank { "（无标题）" },
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (hw.closed) {
                    Text(
                        text = "已截止",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.error,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val meta = buildList {
                add("截止 ${hw.deadlineLocal}")
                if (hw.submitTimes != null) add("可交 ${hw.submitTimes} 次")
                // 佔比：服務端給的是 `"0.0"` 這種**字串**（已含小數）。
                // ⚠️ 只有「非 0」才顯示 —— 實測多數作業是 `0.0`（老師沒設），
                //   一律顯示會讓每張卡都掛一個沒有資訊量的「占比 0.0%」。
                val pct = hw.scorePercentage.toDoubleOrNull()
                if (pct != null && pct > 0.0) add("占比 ${hw.scorePercentage}%")
                // 作業類型直接顯示服務端代號（file_upload 之類）——
                // 我們不知道全部種類的中文名，**不編**（與 Q/GY/K 同一原則）。
                if (hw.homeworkType.isNotBlank()) add(hw.homeworkType)
            }
            Text(
                text = meta.joinToString("　"),
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )
            if (hw.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    // 要求內容是這一頁的核心價值 —— 不清洗 HTML 標籤，
                    // 原樣顯示（服務端給的多是純文字；若帶標籤，使用者看到也能理解）。
                    text = hw.description,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
