package com.muyao.wanzaihyperos.ui.courses

import com.muyao.wanzaihyperos.ui.web.Cjh52WarmUp
import com.muyao.wanzaihyperos.ui.design.AppCard
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import com.muyao.wanzaihyperos.data.cjh52.Course
import com.muyao.wanzaihyperos.data.cjh52.CourseRepository
import com.muyao.wanzaihyperos.data.cjh52.CourseSlot
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
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
 * 「我的課程」原生頁（B1）。
 *
 * ### 這個頁面要解決的核心問題
 *
 * PLAN §B1 要求「模型支援**多周次、多時段、多教室**」。
 * 資料層已經做到了（[Course.slots] 是清單），
 * **但介面也必須把它顯示出來** —— 否則資料層留住的第二個時段，
 * 到了使用者眼前還是消失了，等於沒做。
 *
 * 所以每張課程卡片下面列出**全部時段**，每個時段一行：
 * 星期 + 時間 + 地點 + 週次。
 *
 * ### 狀態處理
 *
 * 走既有的 [toPageState] 橋接（與其它頁面同一套判據），
 * 所以「取不到」與「沒有課」在界面上是不同的：
 * 前者是錯誤頁可重試，後者是空態。
 */
@Composable
fun CoursesPage(
    repository: CourseRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 原生頁取不到資料時（尤其**會話無效**）的出口：改用網頁版。
     *
     * ★ 這條是 B1 的**必要設計**，不是可選項。
     * 原生頁依賴的 CJH52 會話可能失效（門戶 tempcode 有壽命），
     * 所以必須讓使用者有路可走 —— WebView 那條是已被真機走查驗證過的。
     */
    onFallbackToWeb: (() -> Unit)? = null,
    /**
     * 會話是否就緒（由 [com.muyao.wanzaihyperos.ui.web.Cjh52WarmUpGate] 給）。
     *
     * 為 false 時**不要取數** —— 那一定 401，只會讓使用者先看到一個假錯誤。
     * 頁面此時顯示自己的載入態即可。
     */
    ready: Boolean = true,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val pageState = state.toPageState()
    val courses = (state as? ListState.Content)?.items.orEmpty()

    // 會話就緒後才取數。未就緒時畫面停在 FirstLoad（= 載入態），
    // 這段等待就是預熱的時間（通常 1-2 秒）。
    LaunchedEffect(ready) {
        if (ready && state is ListState.FirstLoad) repository.refresh()
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "我的課程",
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
                    IconButton(onClick = { /* 由外層 scope 觸發 */ }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "重新載入",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (courses.isEmpty()) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier.padding(innerPadding),
                // ★ 原生頁取不到資料時的出口。
                //
                // 刻意**在所有非內容狀態**都提供（不只錯誤態）：會話失效、離線、
                // 甚至空態都給 —— 因為原生頁與網頁版的資料來源雖然同一個接口，
                // 但網頁版是**已被驗證**的那條路。使用者不該因為我們改用了原生頁
                // 就失去一條能用的路。
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
                    SummaryBar(
                        courseCount = courses.size,
                        slotCount = courses.sumOf { it.slots.size },
                        isStale = pageState == PageState.Stale,
                    )
                }
                items(courses, key = { it.id }) { course ->
                    CourseCard(course)
                }
            }
        }
    }
}

/** 頂部統計：課程數與**時段數**分開顯示 —— 這樣「一課多段」是否被保留，一眼可見。 */
@Composable
private fun SummaryBar(courseCount: Int, slotCount: Int, isStale: Boolean) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            text = "共 $courseCount 門課 · $slotCount 個上課時段",
            style = MiuixTheme.textStyles.body1,
            color = colors.onBackground,
        )
        if (isStale) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "這次沒重新整理成功，顯示的是上一次的資料",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onBackgroundVariant,
            )
        }
    }
}

/**
 * 一張課程卡：課程資訊 + **全部時段**。
 *
 * 時段數 >1 時刻意顯示「N 個時段」的標記 ——
 * 讓「這門課不止一個時段」這件事在使用者面前是**顯式**的，
 * 而不是要靠自己數。
 */
@Composable
private fun CourseCard(course: Course) {
    val colors = MiuixTheme.colorScheme
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = course.name,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (course.slots.size > 1) {
                    Text(
                        text = "${course.slots.size} 個時段",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.primary,
                    )
                }
            }

            if (course.teacherName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = course.teacherName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                )
            }

            if (course.slots.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "尚未排定上課時段",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                course.slots.forEachIndexed { index, slot ->
                    if (index > 0) {
                        Spacer(Modifier.height(6.dp))
                        // Miuix 0.9.4 没有 HorizontalDivider（见 BUILD-KOTLIN §5），
                        // 用一条 1dp 的背景线代替 —— 与其它页面保持一致的观感。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.dividerLine),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    SlotRow(slot)
                }
            }

            if (course.classNo.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = course.classNo,
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 單個時段：`星期一 13:30:00~15:05:00 · 教六310 · 1-8 週`。 */
@Composable
private fun SlotRow(slot: CourseSlot) {
    val colors = MiuixTheme.colorScheme
    val parts = buildList {
        if (slot.day.isNotBlank()) add(slot.day)
        if (slot.start.isNotBlank() && slot.end.isNotBlank()) {
            add("${slot.start}~${slot.end}")
        }
        if (slot.displayLocation.isNotBlank()) add(slot.displayLocation)
        slot.weekRange?.let { add("${it.first}-${it.last} 週") }
    }
    Text(
        text = parts.joinToString(" · "),
        style = MiuixTheme.textStyles.body2,
        color = colors.onSurface,
    )
}
