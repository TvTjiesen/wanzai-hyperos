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
import com.muyao.wanzaihyperos.data.tronclass.TronCourse
import com.muyao.wanzaihyperos.data.tronclass.TronHome
import com.muyao.wanzaihyperos.data.tronclass.TronHomeRepository
import com.muyao.wanzaihyperos.data.tronclass.TronTodo
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 學在城院（原生）。
 *
 * ### 為什麼只做兩塊
 *
 * 學在城院是個完整 LMS（討論、測驗、點名、互評、成績…），
 * 把它整個原生化既不現實、也沒必要 —— 使用者日常真正會看的就兩件事：
 * **我的課**、**有什麼要交**。所以這一頁只放這兩塊，
 * 其餘功能保留在網頁版（頁面底部有明確入口）。
 *
 * ### 與「全部待辦」的分工
 *
 * 「全部待办」是**跨系統聚合**（學在城院 + 門戶）。這一頁是**學在城院內部視圖**，
 * 帶課程清單。兩者不重複：一個回答「今天總共要辦什麼」，
 * 一個回答「我這門課怎麼樣」。
 */
@Composable
fun TronHomePage(
    repository: TronHomeRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val home = (state as? ListState.Content)?.items?.firstOrNull()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "学在城院",
                subtitle = "我的课与作业",
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
        if (home == null) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier.padding(innerPadding),
                onAction = onFallbackToWeb,
                actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 一句話摘要：最近要交什麼（沒有截止時間的待辦不算）。
                home.nextDue?.let { due ->
                    item {
                        Text(
                            text = "最近要交：${due.title}（${due.localTimeText.ifBlank { "—" }}）",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                item { SmallTitle(text = "我的课（${home.courses.size}）") }
                if (home.courses.isEmpty()) {
                    item { EmptyHint("没有取到课程") }
                } else {
                    items(home.courses, key = { it.id }) { c -> CourseCard(c) }
                }

                item { SmallTitle(text = "待交作业（${home.todos.size}）") }
                if (home.todos.isEmpty()) {
                    item { EmptyHint("没有待交作业") }
                } else {
                    items(home.todos, key = { it.id }) { t -> TodoCard(t) }
                }

                if (onFallbackToWeb != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "需要讨论、测验、点名、成绩等功能？",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "改用网页版 ›",
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
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun CourseCard(c: TronCourse) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = c.name,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (c.teachingClass.isNotBlank()) add(c.teachingClass)
                if (c.department.isNotBlank()) add(c.department)
                if (c.courseCode.isNotBlank()) add(c.courseCode)
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta.joinToString("　"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TodoCard(t: TronTodo) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = t.title.ifBlank { "（无标题）" },
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (t.courseName.isNotBlank()) add(t.courseName)
                // ★ 用 localTimeText（模型上已轉本地時間），**不要用 timeText**
                //   —— 後者是服務端原文（UTC），照它顯示會早 8 小時。
                if (t.localTimeText.isNotBlank()) add(t.localTimeText)
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta.joinToString("　"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}
