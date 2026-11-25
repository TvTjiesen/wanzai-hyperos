package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页「同一件事只说一遍」的守卫。
 *
 * ## 这条测试在防什么
 *
 * 2026-10-31 21:44 真机 dump（周六、今天一节没有课）里，首页同屏出现了：
 *
 * ```
 * 今天的课上完了 · 这是上次存的        ← 天空副行
 * 今天没有课                          ← 「此刻」卡大标题
 * 下一节：周一 第6-9节 B/S架构应用开发   ← 「此刻」卡副行
 * 下一节：周一 第6-9节 B/S架构应用开发   ← 时间线卡，逐字相同
 * ```
 *
 * 三件事，两个问题：
 *
 * 1. **「今天的课上完了」是假话**。它由 `remainingCount == 0` 推出，而周六
 *    今天**压根没课**；「都上完了」和「本来就没有」对读的人不是同一件事。
 * 2. **「下一节」在同一屏说了两遍**，逐字相同。同一句话出现两次，读起来
 *    像两个独立的结论，反而让人怀疑哪个是真的。
 *
 * 判据钉三件事：副行必须按「今天有几节」而不是「还剩几节」分叉、
 * 时间线卡不再吃 `plan`（它复述的就是 plan.detail）、没课时那张卡不渲染。
 */
class HomeCopyOnceTest {

    private val path = "main/ui/main/pages/HomeSectionPage.kt"

    private fun source(): String {
        val hit = SourceScan.sources().firstOrNull { it.first == path }
        assertTrue("扫不到 " + path + " —— 扫描器坏了，这条判据形同虚设", hit != null)
        return hit!!.second
    }

    private fun section(src: String, id: String): String {
        val sections = src.substringAfter("when (section) {")
        val start = sections.indexOf("\"$id\" ->")
        assertTrue("找不到首页模块 $id", start >= 0)
        val next = Regex("\\n\\s*\"[^\"]+\" ->").find(sections, start + 1)?.range?.first
        return sections.substring(start, next ?: sections.indexOf("item(key = \"tail\")", start))
    }

    /** 括号配平地切出某个函数的函数体。 */
    private fun bodyOf(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("找不到 " + signature + " —— 这条判据就失效了", start >= 0)
        var i = src.indexOf('{', start)
        var depth = 0
        var inString = false
        while (i < src.length) {
            val c = src[i]
            if (inString) {
                if (c == '\\') { i += 2; continue }
                if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return src.substring(start, i + 1)
                    }
                }
            }
            i += 1
        }
        throw AssertionError("函数体没闭合：" + signature)
    }

    @Test
    fun `天空副行改成球的话，不再复述课表统计`() {
        // ★ 返工总单 B1（2026-11-08）改判：这一行以前是「还有 N 节课 · 下一节 09:40」这类统计，
        //   现在按稿的口径换成**球的话**（AiBall.lineOf）——「字不许重复脸说过的话」。
        //   原来那条「今天没课 vs 都上完了」的区别没有丢，它搬到了脸上：
        //   前者是 Idle/Sleep，后者是 Done，两张脸的台词不同（见下一条断言）。
        val body = bodyOf(source(), "internal fun HomeSkyHeader(")
        assertTrue("副行没有用球的话 —— 又退回纯统计了",
            body.contains("AiBall.lineOf(ballState)"))
        assertFalse("副行还在画课表统计（应该只在「此刻」卡里说）",
            body.contains("节课 · 下一节"))
    }

    @Test
    fun `调用点真的把今天的节数传进去了`() {
        // 只切 hero 那一小段：整页的作用域切法会被页面里那些带花括号的模板串搅坏，
        // 而这条判据要的就只是「那一处调用点」，不必赌括号。
        val src = source()
        val at = src.indexOf("item(key = \"hero\")")
        assertTrue("首页里找不到 hero 那一项", at >= 0)
        val seg = src.substring(at, minOf(at + 700, src.length))
        assertTrue("调用 HomeSkyHeader 时没传 todayCount —— 新形参等于骗人的",
            seg.contains("todayCount = todaySlots.size"))
    }

    @Test
    fun `时间线卡不再复述 plan`() {
        val body = bodyOf(source(), "internal fun TodayTimelineCard(")
        assertFalse("时间线卡又吃回 plan 了 —— 它复述的就是 plan.detail，会和「此刻」卡逐字重复",
            body.contains("plan: TodayPlan?"))
        assertFalse("时间线卡里还在画 plan.detail",
            body.contains("plan?.detail"))
    }

    @Test
    fun `今天真没课时那张卡不渲染`() {
        val src = source()
        assertTrue("没有计算时间线为空时是否仍有内容可说", src.contains("val timelineHasNothingToSay = !loading && shownFailure == null &&\n        week != null && todaySlots.isEmpty()"))
        val sectionOrder = src.indexOf("sectionOrder.forEach { section ->")
        val timelineBranch = src.indexOf("\"timeline\" ->", sectionOrder.coerceAtLeast(0))
        val timeline = section(src, "timeline")
        val item = timeline.indexOf("item(key = \"timeline\")")
        assertTrue("时间线必须作为可排序真实模块渲染", sectionOrder >= 0 && timelineBranch > sectionOrder && item >= 0)
        val gate = timeline.substringBefore("item(key = \"timeline\")")
        assertTrue("正常时间线只有未隐藏、启用课程且有内容可说才渲染", gate.contains("previewOnly || (\"timeline\" !in UiPrefs.hiddenHomeSections && \"courses\" in UiPrefs.homeModules && !timelineHasNothingToSay)"))
        assertTrue("timeline 无内容判定必须在正常页 item gate 内生效", gate.contains("!timelineHasNothingToSay"))
        assertTrue("时间线真实项必须经过编辑器包装且编辑预览可显示", timeline.contains("HomeLayoutEditableBlock(\"timeline\", layoutEditing, listState)"))
        assertTrue("preview 不得把正常偏好中的课程关闭状态套到草稿时间线", timeline.contains("previewOnly || (\"timeline\" !in UiPrefs.hiddenHomeSections && \"courses\" in UiPrefs.homeModules"))
        assertTrue("timeline 卡片仍只画当前日期筛出的 slots", timeline.contains("TodayTimelineCard(slots = todaySlots, week = week"))
    }

    @Test
    fun `编辑预览使用真实六块但屏蔽刷新导航与周次持久写入`() {
        val src = source()
        assertTrue("layoutEditing 必须标记真实编辑预览", src.contains("val previewOnly = layoutEditing != null"))
        assertTrue("首页六块按编辑草稿顺序定义且保留默认顺序回退", src.contains("layoutEditing?.settings?.homeOrder ?: UiPrefs.homeLayoutOrder"))
        val defaults = SourceScan.sources().firstOrNull { it.first == "main/data/HomeLayoutOrder.kt" }?.second.orEmpty()
        assertTrue("首页排序默认列表必须保留真实的六个 block", defaults.contains("listOf(\"hero\", \"quick-actions\", \"todos\", \"rollcall\", \"szty\", \"timeline\")"))
        listOf("hero", "quick-actions", "todos", "rollcall", "szty", "timeline").forEach { id ->
            assertTrue("首页排序循环必须分发 $id", src.contains("\"$id\" ->"))
            val block = section(src, id)
            assertTrue("preview 下 $id 模块没有通过真实编辑器内容渲染", block.contains("previewOnly ||") && block.contains("HomeLayoutEditableBlock(\"$id\", layoutEditing, listState)"))
        }
        assertTrue("preview 不能领取正常首页首刷", src.contains("if (!previewOnly && pageActive && entryGate.claim(\"home\", entryOwner))"))
        assertTrue("周次在 preview 只能读取，不能持久写入", src.contains("persist = !previewOnly"))
        assertTrue("preview 必须直接调用同一个内容列表而非包下拉刷新", src.contains("if (previewOnly) {\n            homeContent()\n        } else {\n            PullToRefresh("))
        assertTrue("即使刷新回调被触达也必须拒绝 preview", src.contains("if (previewOnly) return@PullToRefresh"))
        assertTrue("preview 不显示可刷新/周次导航的正常页悬浮栏", src.contains("if (!previewOnly && headerAlpha > 0.01f)"))
        val hero = section(src, "hero")
        assertTrue("课程预览必须按草稿模块开关绘制真实焦点卡", hero.contains("courseEnabled = \"courses\" in (layoutEditing?.settings?.modules ?: UiPrefs.homeModules)"))
        assertTrue("hero 课程卡预览不能导航到课表或签到", hero.contains("onClick = if (previewOnly) ({}) else onOpenTimetable") && hero.contains("onOpenRollcall = if (previewOnly) ({}) else onOpenRollcall"))
        val quick = section(src, "quick-actions")
        listOf("Timetable", "Jw", "Messages", "Appearance").forEach { target ->
            assertTrue("preview 快捷入口 $target 不能导航", quick.contains("onOpen$target = if (previewOnly) ({}) else onOpen$target"))
        }
        assertTrue("preview 待办卡不能导航", section(src, "todos").contains("onClick = if (previewOnly) ({}) else"))
        assertTrue("preview 签到卡不能导航", section(src, "rollcall").contains("onOpen = { if (!previewOnly)"))
        assertTrue("preview 校园跑卡不能导航", section(src, "szty").contains("onClick = { if (!previewOnly)"))
        assertTrue("preview 时间线不能导航", section(src, "timeline").contains("onOpenTimetable = if (previewOnly) ({}) else onOpenTimetable"))
        assertTrue("正常首页 refreshSources 必须拒绝 preview", src.contains("if (previewOnly) return@refresh"))
    }

    @Test
    fun `删掉的是那一句，不是诚实的三句`() {
        // 时间线卡里那三句「还在取 / 没取到 / 不知道第几周」是**不许被顺手删掉**的：
        // 它们各自对应一种「现在还不能下结论」，删掉就只剩一句假装确定的空话。
        val body = bodyOf(source(), "internal fun TodayTimelineCard(")
        assertTrue("「正在取得课表…」没了", body.contains("正在取得课表…"))
        assertTrue("「课表没取到…」没了", body.contains("课表没取到，暂时画不出今天的时间线。"))
        assertTrue("「还不知道目前是第几教学周…」没了", body.contains("还不知道目前是第几教学周"))
    }
}
