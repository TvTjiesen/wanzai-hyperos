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
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurface
import com.muyao.wanzaihyperos.ui.design.glass.LocalGlassBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首頁板塊：今日課程 / 簽到 / 待辦 / 快捷。
 *
 * 資料來源：
 * - 今日課程：教務系統個人課表，尚未接入（見下）
 * - 簽到：學在城院 rollcall，已原生
 * - 待辦：門戶審批 + 學在城院作業 todoBoard，已原生
 *
 * 為什麼今日課程寫「尚未接入」而不是顯示空：
 * 教務系統的個人課表沒有 JSON 介面（B18 實測，內容直接畫在 HTML 裡），
 * 要另寫 DOM 解析才能取。所以不能顯示「今天沒有課」——
 * 那是在替服務端撒謊（本專案反覆栽的坑）。
 * 要明確說「還沒接入」，讓使用者知道是缺功能，不是沒課。
 */
@Composable
fun HomeSectionPage(
    graph: AppGraph,
    onOpenJw: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val backdrop = LocalGlassBackdrop.current

    val rollcallState by graph.rollcall.state.collectAsState()
    val todoState by graph.todoBoard.state.collectAsState()

    LaunchedEffect(Unit) {
        runCatching { graph.rollcall.refresh() }
        runCatching { graph.todoBoard.refresh() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            top = 56.dp,
            bottom = 320.dp,   // 留出玻璃底欄的高度 + 一屏餘量：讓最後幾張卡能滾到玻璃底下，玻璃才折射得到東西
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        item(key = "title") {
            Text(
                text = "今天",
                fontSize = Type.largeTitle,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                lineHeight = Type.largeTitle * Type.LINE_HEIGHT_TITLE,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        item(key = "today-courses") {
            GlassCard(
                backdrop = backdrop,
                title = "今日課程",
                actionLabel = "教務系統 ›",
                onAction = onOpenJw,
            ) {
                Text(
                    text = "尚未接入教務系統的個人課表。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "課表板塊目前顯示學在城院的課程；教務課表要另外解析，還沒做。",
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }

        item(key = "rollcall") {
            GlassCard(
                backdrop = backdrop,
                title = "簽到",
                actionLabel = null,
                onAction = null,
            ) {
                when (val s = rollcallState) {
                    is ListState.FirstLoad -> BodyHint("正在取得簽到狀態…")

                    is ListState.Failed -> BodyError(
                        "取不到簽到狀態",
                        s.error.message.orEmpty(),
                    )

                    is ListState.Content -> {
                        val open = s.items.firstOrNull { !it.expired }
                        if (open == null) {
                            BodyHint("目前沒有進行中的簽到。")
                        } else {
                            Text(
                                text = open.title,
                                fontSize = Type.body,
                                fontWeight = Type.weightBody,
                                color = colors.onBackground,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (open.endTime.isBlank()) {
                                    "可以簽到"
                                } else {
                                    "可以簽到 · 截止 ${open.endTime}"
                                },
                                fontSize = Type.caption,
                                color = colors.primary,
                            )
                        }
                    }

                    else -> BodyHint("目前沒有進行中的簽到。")
                }
            }
        }

        item(key = "todos") {
            GlassCard(
                backdrop = backdrop,
                title = "待辦",
                actionLabel = "全部 ›",
                onAction = onOpenTodos,
            ) {
                when (val s = todoState) {
                    is ListState.FirstLoad -> BodyHint("正在整理待辦…")

                    is ListState.Failed -> BodyError(
                        "取不到待辦",
                        s.error.message.orEmpty(),
                    )

                    is ListState.Content -> {
                        // ⚠️ `todoBoard.state` 是 `ListState<TodoBoard>`，而
                        // `ListState.Content.items` 是 `List<T>` —— 也就是
                        // **List<TodoBoard>**（倉庫把單一 board 包成單元素清單）。
                        // 所以要先取 `firstOrNull()` 才是 TodoBoard 本身。
                        // 這是本專案既有的約定，見 TodoBoardPage 的寫法。
                        val all = s.items.firstOrNull()?.groups?.flatMap { it.items }.orEmpty()
                        if (all.isEmpty()) {
                            BodyHint("沒有待辦事項。")
                        } else {
                            Text(
                                text = "${all.size} 項待處理",
                                fontSize = Type.body,
                                fontWeight = Type.weightBody,
                                color = colors.onBackground,
                            )
                            all.take(3).forEach { t ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "· ${t.title}",
                                    fontSize = Type.caption,
                                    color = colors.onSurfaceVariantSummary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    else -> BodyHint("沒有待辦事項。")
                }
            }
        }

        // 今日更多課程（讓內容延伸到玻璃底下 —— 玻璃要折射真的內容才看得出來）
        item(key = "quick-more") {
            GlassCard(
                backdrop = backdrop,
                title = "本週還有",
                actionLabel = "課表 ›",
                onAction = onOpenTimetable,
            ) {
                BodyHint("到課表板塊看整週安排。")
            }
        }

        item(key = "shortcuts") {
            Row(horizontalArrangement = Arrangement.spacedBy(Dims.cardGap)) {
                QuickCard(
                    backdrop = backdrop,
                    title = "課表",
                    onClick = onOpenTimetable,
                    modifier = Modifier.weight(1f),
                )
                QuickCard(
                    backdrop = backdrop,
                    title = "訊息",
                    onClick = onOpenMessages,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GlassCard(
    backdrop: dev.chrisbanes.haze.HazeState?,
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = Type.summary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                if (actionLabel != null && onAction != null) {
                    Text(
                        text = actionLabel,
                        fontSize = Type.caption,
                        color = colors.primary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onAction() },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun BodyHint(text: String) {
    Text(
        text = text,
        fontSize = Type.secondary,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun BodyError(title: String, detail: String) {
    Text(
        text = title,
        fontSize = Type.body,
        color = MiuixTheme.colorScheme.error,
    )
    if (detail.isNotBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            fontSize = Type.caption,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun QuickCard(
    backdrop: dev.chrisbanes.haze.HazeState?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(backdrop = backdrop, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = Type.body,
                fontWeight = Type.weightBody,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}
