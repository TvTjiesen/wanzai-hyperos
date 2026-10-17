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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurface
import com.muyao.wanzaihyperos.ui.design.glass.LocalGlassBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **課程表板塊**：完整一週 + 可切週。
 *
 * ## 資料來源（真實 API，不是猜的）
 *
 * 學在城院 `AppGraph.schedule`：
 * - `state: StateFlow<ListState<TronSchedule>>` —— 一次抓回**全部**課表格子
 *   （`TronScheduleSlot` 自帶 `weekStart..weekEnd` 的週次區間）；
 * - `slot.activeIn(week)` 判斷某週有沒有這堂課。
 *
 * 所以「切週」**不需要重新請求** —— 資料本來就含整學期。
 * 這一點很重要：切週若每次都打介面，滾一下就會撞限流。
 *
 * ## 為什麼用不透明卡而不是玻璃卡
 *
 * 規範 §14 的性能預算：「同屏實時折射的面積要預算；長列表裡的玻璃一律降級」。
 * 課表是密集格子，全開玻璃會很燙很卡。所以**只有頂部週切換條是玻璃**。
 */
@Composable
fun TimetableSectionPage(
    graph: AppGraph,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val state by graph.schedule.state.collectAsState()
    val backdrop = LocalGlassBackdrop.current

    // 要顯示哪一週。
    //
    // `0` = 目前教學週（由 schedule 自己算，它會問 `teachingWeeks()`）；
    // 正負數 = 相對偏移。**不重新請求資料** —— 資料本來就含全學期。
    var weekOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { graph.schedule.refresh() }
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
                text = "課程表",
                fontSize = Type.largeTitle,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                lineHeight = Type.largeTitle * Type.LINE_HEIGHT_TITLE,
            )
        }

        // ---- 週切換（玻璃）--------------------------------------------------
        item(key = "week-switch") {
            GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WeekNavButton("上一週") { weekOffset -= 1 }
                    Text(
                        text = when (weekOffset) {
                            0 -> "本週"
                            -1 -> "上一週"
                            1 -> "下一週"
                            else -> if (weekOffset > 0) "$weekOffset 週後" else "${-weekOffset} 週前"
                        },
                        fontSize = Type.body,
                        fontWeight = Type.weightBody,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    WeekNavButton("下一週") { weekOffset += 1 }
                }
            }
        }

        // ---- 課表內容 -------------------------------------------------------
        when (val s = state) {
            is ListState.FirstLoad -> item(key = "loading") {
                Text(
                    "正在取得課表…",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(Dims.cardPadding),
                )
            }

            is ListState.Failed -> item(key = "failed") {
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text(
                            "取不到課表",
                            fontSize = Type.body,
                            fontWeight = Type.weightBody,
                            color = colors.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.error.message.orEmpty(),
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            is ListState.Content -> {
                // ⚠️ `schedule.state` 是 `ListState<TronSchedule>`，而 `Content.items`
                // 是 `List<TronSchedule>` —— **單元素清單**（倉庫把單一 schedule 包起來）。
                // 所以要先 `firstOrNull()` 才是 TronSchedule 本身。
                val schedule = s.items.firstOrNull()
                // 目前教學週：schedule 自己算好的（CountValue.Known 才有值）。
                val baseWeek = (schedule?.currentWeek as? com.muyao.wanzaihyperos.data.CountValue.Known)?.value
                val targetWeek = baseWeek?.plus(weekOffset)

                if (targetWeek == null) {
                    item(key = "no-week") {
                        Text(
                            "還不知道目前是第幾教學週，所以無法對應課表。",
                            fontSize = Type.secondary,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier.padding(Dims.cardPadding),
                        )
                    }
                } else {
                    val slots = schedule?.slots.orEmpty()
                    // 依星期分組，只保留這一週有效的格子。
                    val byDay = (1..7).map { day ->
                        day to slots.filter { it.dayOfWeek == day && it.activeIn(targetWeek) }
                    }.filter { it.second.isNotEmpty() }

                    if (byDay.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "第 $targetWeek 週沒有課程。",
                                fontSize = Type.secondary,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(Dims.cardPadding),
                            )
                        }
                    } else {
                        item(key = "week-label") {
                            Text(
                                text = "第 $targetWeek 週",
                                fontSize = Type.summary,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        byDay.forEach { (day, daySlots) ->
                            item(key = "day-$day") {
                                Text(
                                    text = daySlots.first().dayLabel,
                                    fontSize = Type.summary,
                                    color = colors.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 2.dp),
                                )
                            }
                            daySlots.sortedBy { it.periodStart }.forEach { slot ->
                                item(key = "slot-${slot.courseId}-$day-${slot.periodStart}") {
                                    CourseCell(slot)
                                }
                            }
                        }
                    }
                }
            }

            else -> item(key = "none") {
                Text(
                    "還沒有課表資料。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(Dims.cardPadding),
                )
            }
        }
    }
}

@Composable
private fun WeekNavButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = Type.caption,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable { onClick() },
    )
}

/**
 * 一堂課的格子。
 *
 * ⚠️ 顯示用的是 `TronScheduleSlot` **自帶的顯示名**（`dayLabel` / `periodLabel`），
 * 不是自己拼的 —— 那些屬性已經處理好「單節 vs 連堂」的差別
 * （`第3節` vs `第3-4節`）。自己拼容易在連堂時顯示成「第3節」而漏掉 4。
 */
@Composable
private fun CourseCell(slot: TronScheduleSlot) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.courseName.ifBlank { "（未命名課程）" },
                    fontSize = Type.body,
                    fontWeight = Type.weightBody,
                    color = colors.onBackground,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = slot.periodLabel,
                    fontSize = Type.caption,
                    color = colors.primary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = slot.raw.ifBlank { slot.weekLabel },
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
            )
        }
    }
}
