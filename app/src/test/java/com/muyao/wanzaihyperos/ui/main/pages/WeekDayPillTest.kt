package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「回到今天」那颗药丸与周条高亮的守卫（2026-10-31 真机缺陷）。
 *
 * ## 缺陷原文
 *
 * 周六进课表页：左边写着「周五」，右边那颗药丸写着「周五」，按下去切的却是**周六**
 * （`onFirst` 里是 `selectedDay = today.dayOfWeek.value`，周六那天是 6）。
 * 根因是 `selectedDay` 的初值被 `coerceIn(1, 5)` 夹过 —— 当时夹它的理由是
 * 「周条只到周五，6/7 进来五点全无高亮」，代价是**周六打开看到的是周五的课表**。
 *
 * ## 判据
 *
 * 一条：**药丸上的字必须等于按下去会看到的那一天**。它由 [dayPillLabel] 编码成纯函数，
 * 所以这里能直接跑真值表，而不是去源码里找字面量。
 */
class WeekDayPillTest {

    private val pageFile =
        "src/main/java/com/muyao/wanzaihyperos/ui/main/pages/TimetableSectionPage.kt"

    private fun code(): String {
        val f = File(pageFile)
        assertTrue("找不到整周页源码：$pageFile", f.isFile)
        return SourceScan.stripComments(f.readText(Charsets.UTF_8))
    }

    // ---- 1. 真值表：字跟着动作走 -----------------------------------------
    @Test
    fun `药丸上的字等于按下去会看到的那一天`() {
        // 本周内按下去 = 回到今天，所以七天的标签全是「今天」。
        (1..7).forEach { dow ->
            assertEquals("本周内的第 $dow 天按下去就是今天", "今天", dayPillLabel(dow, weekOffset = 0))
        }
        // 翻到别的周，按下去看到的是**那一周的同一个星期几** —— 字必须跟着变，
        // 不然"翻到下周再点今天"会写着「今天」却看到下周三。
        val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        (1..7).forEach { dow ->
            assertEquals(
                "翻周后第 $dow 天",
                names[dow - 1],
                dayPillLabel(dow, weekOffset = if (dow <= 3) 1 else -1),
            )
        }
    }

    // ---- 2. 初值不许再夹进 1..5 -----------------------------------------
    //
    // 夹它就是为了让周条有高亮，但代价更大：周六打开课表看到的是周五的课表。
    @Test
    fun `选中那天就是今天，不夹到周五`() {
        val body = code()
        val i = body.indexOf("var selectedDay by androidx.compose.runtime.saveable.rememberSaveable")
        assertTrue("没找到 selectedDay 的声明", i >= 0)
        val window = body.substring(i, (i + 260).coerceAtMost(body.length))
        assertTrue(
            "selectedDay 的初值不再是「今天」了：${window.take(160)}",
            "LocalDate.now().dayOfWeek.value" in window,
        )
        assertFalse(
            "selectedDay 的初值又被夹进 1..5 了 —— 周六打开课表就会看到周五的课表",
            "coerceIn(1, 5)" in window,
        )
    }

    // ---- 3. 周条的高亮判没有夹过的 selected -------------------------------
    @Test
    fun `日期条使用 Glasmio 七日选择并暴露具体可访问日期`() {
        val body = code()
        val strip = body.substringAfter("private fun WeekDateStrip(").substringBefore("private fun AgendaDayHeader(")
        assertTrue(strip.contains("(1..7).associateWith"))
        assertTrue(strip.contains("ScheduleNoticeSelector("))
        assertTrue(strip.contains("selectOnIndicatorTap = true"))
        assertTrue(strip.contains("date.monthValue"))
        assertTrue(strip.contains("date.dayOfMonth"))
        assertTrue(strip.contains("DAY_WEEK[day - 1]"))
        assertTrue(strip.contains("dotsByDay.getValue(day)"))
        assertFalse(strip.contains("slidingSelectionCarrier"))
        assertFalse(strip.contains("pointerInput("))
    }

    // ---- 4. 调用点真的接上了纯函数 ---------------------------------------
    @Test
    fun `模式选择器保持今天与整周的切换语义`() {
        val body = code()
        val i = body.indexOf("ScheduleNoticeSelector(", body.indexOf("private fun DayModeSwitch("))
        assertTrue("DayModeSwitch uses the shared Glasmio selector", i >= 0)
        val window = body.substring(i, (i + 620).coerceAtMost(body.length))
        assertTrue(window.contains("items = listOf(false to firstLabel, true to \"整周\")"))
        assertTrue(window.contains("selected = weekMode"))
        assertTrue(window.contains("if (showWeek) onWeek() else onFirst()"))
        assertTrue(window.contains("selectOnIndicatorTap = true"))
        assertTrue("tapping first mode still resets selectedDay to today", body.contains("selectedDay = today.dayOfWeek.value"))
    }
}
