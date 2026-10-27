package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.ScheduleRepository
import com.muyao.wanzaihyperos.data.tronclass.TronSchedule
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.BackRow
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.infoItems
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **課表**（「我的課」板塊裡的第二級頁面）。
 *
 * ## 兩種看法
 *
 * 「本週」與「全部」用同一個 `TabRow` 切換 ——
 * 這是把 [TronSchedule] 那兩組查詢方法（[TronSchedule.slotsAt] 與
 * [TronSchedule.slots]）直接對上界面，**沒有第三種資料**。
 *
 * ## ★ 兩個「不編造」的地方
 *
 * 1. **節次不換算成時間** —— 學校沒有給節次時間表，寫「10:00-11:40」
 *    就是編的。只顯示「第 3-4 節」。
 * 2. **「本週」不知道時顯示「—」** —— 教學週取不到就不知道現在第幾週，
 *    這時不假裝是第 1 週（[CountValue.Unknown]），照實說「不知道本週是第幾週」。
 *
 * ## 認不出的段落會列出來
 *
 * 上課時間是**字串**，解析器認不出的段落會原樣保留（見 `TronScheduleParser`）。
 * 這裡把它們**明確列出來**：寧可顯示一句看不懂的字串，
 * 也不要讓某門課的上課時間**靜默消失**（學生會以為那門課不用上）。
 */
@Composable
fun ScheduleContent(
    repository: ScheduleRepository,
    onBack: () -> Unit,
    onOpenCourse: (courseId: Int, courseName: String) -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val schedule = (state as? ListState.Content)?.items?.firstOrNull()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.refresh() }
    }

    if (schedule == null) {
        StatePlaceholder(
            state = state.toPageState(),
            modifier = modifier,
            onAction = onOpenWeb,
            actionLabel = "改用网页版",
        )
        return
    }

    // 0 = 本週，1 = 全部。用 Int 而不是枚舉：只有兩項，不會再長。
    var tab by remember(schedule) { mutableIntStateOf(TAB_WEEK) }
    val currentWeek = schedule.currentWeek.orNull

    AppList(modifier = modifier) {
        item { BackRow("返回", onBack) }

        item {
            TabRow(
                tabs = listOf("本周", "全部"),
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
                minWidth = 88.dp,
                maxWidth = 88.dp,
                modifier = Modifier.padding(horizontal = Dims.screenH),
            )
        }

        when {
            // 一週課表都沒有：分「真的沒排課」與「解析不出來」兩種講法。
            schedule.slots.isEmpty() -> {
                item {
                    HintText(
                        if (schedule.unparsed.isEmpty()) {
                            "没有取到上课时间（不是“这学期没课”，是学校没给这个字段）。"
                        } else {
                            "上课时间的格式认不出来，已原样列在下面。"
                        },
                    )
                }
            }

            tab == TAB_WEEK && currentWeek == null -> {
                item {
                    HintText("不知道现在是第几周（没有取到教学周），所以没法算“本周”。切到“全部”看整学期的课。")
                }
            }

            tab == TAB_WEEK -> {
                val week = currentWeek!!
                item { SmallTitle(text = "第 $week 周") }
                val days = schedule.activeDays
                if (days.isEmpty()) {
                    item { HintText("这一周没有课。") }
                } else {
                    days.forEach { day ->
                        val atDay = schedule.slotsAt(week, day)
                        if (atDay.isEmpty()) return@forEach
                        item { SmallTitle(text = DAY_NAMES.getOrElse(day - 1) { "周$day" }) }
                        infoItems(atDay, key = { "${it.courseId}-${it.periodStart}-${it.weekStart}" }) { slot ->
                            SlotItem(slot, onOpenCourse)
                        }
                    }
                }
            }

            else -> {
                item { SmallTitle(text = "整学期（${schedule.slots.size} 格）") }
                // 依「星期 → 節次」排，同一天同節次的合併顯示課名。
                schedule.activeDays.forEach { day ->
                    val atDay = schedule.slots.filter { it.dayOfWeek == day }
                    item { SmallTitle(text = DAY_NAMES.getOrElse(day - 1) { "周$day" }) }
                    infoItems(atDay, key = { "${it.courseId}-${it.periodStart}-${it.weekStart}" }) { slot ->
                        SlotItem(slot, onOpenCourse)
                    }
                }
            }
        }

        if (schedule.unparsed.isNotEmpty()) {
            item { SmallTitle(text = "认不出的上课时间（${schedule.unparsed.size}）") }
            infoItems(schedule.unparsed, key = { "${it.first}|${it.second}" }) { (course, raw) ->
                InfoItem(
                    title = course,
                    subtitle = raw,
                    trailingColor = MiuixTheme.colorScheme.error,
                )
            }
            item { HintText("上面这些是学校给的原文，本 App 认不出格式，所以原样列出（没有丢）。") }
        }

        item {
            HintText(
                "说明：学校没有给“第几节是几点”的对照表，所以只显示第几节，" +
                    "不写具体时间（写出来就是编的）。要精确到点请用网页版。",
            )
        }
        item {
            ActionRow(
                title = "完整课表",
                action = "改用网页版",
                onClick = onOpenWeb,
            )
        }
    }
}

/** 一格課：課名 + 節次 +（全部檢視時）週次。點進去是課程詳情。 */
@Composable
private fun SlotItem(slot: TronScheduleSlot, onOpenCourse: (Int, String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenCourse(slot.courseId, slot.courseName) },
    ) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.courseName.ifBlank { "（未知课程）" },
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Dims.tight))
                Text(
                    text = slot.periodLabel,
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.primary,
                )
            }
            Spacer(Modifier.height(Dims.tight))
            Text(
                text = slot.weekLabel,
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

private const val TAB_WEEK = 0

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
