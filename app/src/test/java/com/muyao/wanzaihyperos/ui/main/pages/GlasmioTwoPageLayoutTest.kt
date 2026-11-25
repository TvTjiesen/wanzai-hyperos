package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GlasmioTwoPageLayoutTest {
    private val root = File("src/main/java/com/muyao/wanzaihyperos/ui")
    private fun code(path: String) = File(root, path).readText()

    private fun homeSection(home: String, id: String): String {
        val sections = home.substringAfter("when (section) {")
        val start = sections.indexOf("\"$id\" ->")
        assertTrue("找不到首页可排序模块 $id", start >= 0)
        val next = Regex("\\n\\s*\"[^\"]+\" ->").find(sections, start + 1)?.range?.first
        return sections.substring(start, next ?: sections.indexOf("item(key = \"tail\")", start))
    }

    @Test fun `首页日期与课程层级使用密度令牌`() {
        val home = SourceScan.stripComments(code("main/pages/HomeSectionPage.kt"))
        assertTrue(home.contains("Type.homeDateCompact else Type.homeDate"))
        assertTrue(home.contains("Type.homeFocusCompact else Type.homeFocus"))
        assertTrue(home.contains("Type.lineHomeFocusCompact else Type.lineHomeFocus"))
        val course = home.substringAfter("internal fun FocusGlassCard(").substringBefore("private fun CountdownRing(")
        assertTrue("长标题和来源状态必须自然增长", !course.contains("maxLines") && !course.contains("heightIn(max"))
        assertTrue("课程内容必须读取预览草稿开关并回退正常偏好", homeSection(home, "hero").contains("courseEnabled = \"courses\" in (layoutEditing?.settings?.modules ?: UiPrefs.homeModules)"))
        val timeline = homeSection(home, "timeline")
        val timelineGate = timeline.substringBefore("item(key = \"timeline\")")
        assertTrue("正常时间线仍须同时满足显示、课程开关与有话可说", timelineGate.contains("previewOnly || (\"timeline\" !in UiPrefs.hiddenHomeSections && \"courses\" in UiPrefs.homeModules && !timelineHasNothingToSay)"))
        listOf("rollcall", "todos", "szty").forEach { id ->
            val gate = homeSection(home, id).substringBefore("item(key = \"$id\")")
            assertTrue("模块 $id 的显示开关丢失", gate.contains("previewOnly || (\"$id\" in UiPrefs.homeModules && \"$id\" !in UiPrefs.hiddenHomeSections)"))
        }
        assertTrue(home.contains("onClick = refreshAll"))
        val status = course.substringAfter("text = statusDetail,").substringBefore("Spacer(")
        assertTrue("来源正文不再叠块状白底，保留不减淡字色和字缘保护", !status.contains(".background(") && status.contains("color = titleInk, style = bodyStyle"))
        assertTrue("错误与等待签到必须先于倒计时", course.indexOf("AiBallState.Waiting") < course.indexOf("focus.minutes != null"))
        assertTrue(course.contains("add(slot.periodLabel)"))
        assertTrue("课程结束摘要只画一处且仍保留原课程统计", course.contains("headlineInStatus -> headline") && course.contains("if (!headlineInStatus) Text(text = headline"))
        assertTrue("主卡必须彻底去掉文字白底", !course.contains("textBacking") && !course.contains("TEXT_BACKING_ALPHA"))
    }

    @Test fun `快捷操作全部委托真实回调而非装饰按钮`() {
        val helper = code("home/redesign/HomeDashboard.kt")
        listOf("Timetable", "Jw", "Messages", "Appearance").forEach {
            assertTrue(helper.contains("onOpen$it: () -> Unit"))
            assertTrue(Regex("HomeQuickAction\\([^\\n]+onOpen$it").containsMatchIn(helper))
        }
        assertTrue(helper.contains("GlassSurface("))
        assertTrue(helper.contains("onClick = onClick"))
        assertTrue(helper.contains("semantics { contentDescription = description }"))
        assertTrue(helper.contains("backdrop = backdrop"))
        assertTrue(helper.contains("sampleBackdrop = rememberMotion().glassIsTranslucent"))
        assertTrue(helper.contains("elevatedBackground.copy(alpha = alpha)"))
        assertTrue(helper.contains("if (narrow) 12.dp else if (compact) 16.dp else 20.dp"))
        assertTrue("大字与窄屏必须退两行", helper.contains("maxWidth < 304.dp || LocalDensity.current.fontScale >= 1.3f"))
        assertTrue("快捷入口至少48dp", helper.contains("heightIn(min = 52.dp)"))
    }

    @Test fun `首页六个真实模块可排序且签到校园跑独立全宽`() {
        val home = SourceScan.stripComments(code("main/pages/HomeSectionPage.kt"))
        assertTrue(home.contains("HomeLayoutOrder.normalize("))
        assertTrue(home.contains("layoutEditing?.settings?.homeOrder ?: UiPrefs.homeLayoutOrder"))
        val orderSource = SourceScan.sources().firstOrNull { it.first == "main/data/HomeLayoutOrder.kt" }?.second.orEmpty()
        assertTrue("排序默认列表必须包含并保留六个真实模块",
            orderSource.contains("listOf(\"hero\", \"quick-actions\", \"todos\", \"rollcall\", \"szty\", \"timeline\")"))
        val ordering = home.indexOf("sectionOrder.forEach { section ->")
        val branches = home.indexOf("when (section) {", ordering.coerceAtLeast(0))
        assertTrue("真实模块必须按归一化顺序逐个渲染，而不是只排编辑占位卡", ordering >= 0 && branches > ordering)
        val renderers = mapOf("hero" to "HomeSkyHeader(", "quick-actions" to "HomeQuickActions(",
            "todos" to "TodoSummaryCard(", "rollcall" to "RollcallSection(",
            "szty" to "RunSummaryCard(", "timeline" to "TodayTimelineCard(")
        renderers.forEach { (id, renderer) ->
            val block = homeSection(home, id)
            val item = block.indexOf("item(key = \"$id\")")
            val editable = block.indexOf("HomeLayoutEditableBlock(\"$id\", layoutEditing, listState)")
            assertTrue("模块 $id 必须在稳定 item 中包住真实内容", item >= 0 && editable > item && block.indexOf(renderer) > editable)
        }
        assertTrue("不能恢复合并服务双列，两个模块要能独立排序", !home.contains("item(key = \"services\")"))
        val rollcall = homeSection(home, "rollcall")
        val runBlock = homeSection(home, "szty")
        listOf(rollcall, runBlock).forEach { block ->
            assertTrue("签到与校园跑各自使用全宽分组而非等分双列", block.contains("HomeDashboardGroup(compact, modifier =") && block.contains("narrow = false") && !block.contains("Modifier.weight(") && !block.contains("Row("))
        }
        assertTrue(rollcall.contains("onOpen = { if (!previewOnly) { morphState?.prepare(\"rollcall\"); onOpenRollcall() } }"))
        assertTrue(runBlock.contains("onClick = { if (!previewOnly) { morphState?.prepare(\"szty\"); onOpenSzty() } }"))
        assertTrue("preview 不得注册正常页的形变导航源", listOf(rollcall, runBlock).all { it.contains("if (previewOnly) null else morphState") })
        val editor = SourceScan.stripComments(code("home/HomeLayoutEditor.kt"))
        assertTrue("六个真实模块必须共用长按拖动排序手势", editor.contains("detectDragGesturesAfterLongPress(") && editor.contains("latest.onMove(id, targetId)"))
        assertTrue("拖动目标只允许六个已知模块，排除本项", editor.contains("it.key != id && it.key in HomeLayoutOrder.defaults"))
        assertTrue("排序必须有可访问名称和上移/下移替代动作", editor.contains("长按拖动排序") && editor.contains("CustomAccessibilityAction(\"上移\"") && editor.contains("CustomAccessibilityAction(\"下移\""))
        assertTrue("编辑时须显示各模块真实内容并拦截卡片手势", editor.contains("if (visible && enabled)") && editor.contains("clearAndSetSemantics") && editor.contains("Modifier.matchParentSize().pointerInput(Unit)"))
        val group = SourceScan.stripComments(code("home/redesign/HomeDashboard.kt"))
            .substringAfter("fun HomeDashboardGroup(").substringBefore("fun HomeQuickActions(")
        assertTrue("服务分组必须占据整个列表宽度", group.contains("Modifier.fillMaxWidth()"))
        assertTrue(home.contains("if (ratio != null)"))
        assertTrue(home.contains("if (headlineInStatus) AiBall.lineOf(ballState) else statusTitle"))
        assertTrue("只有课程已结束才可合并同义摘要，紧急待办与取数状态不能丢", home.contains("ballState == com.muyao.wanzaihyperos.ui.design.ai.AiBallState.Done"))
        assertTrue("非近课不得用数百分钟的长倒计时挤占球状态", home.contains("focus.ongoing || focus.minutes in 0..59"))
        assertTrue("加载与上课状态只精确去重，来源后缀保留", home.contains("if (focus.ongoing) AiBall.lineOf(ballState) else headline"))
        assertTrue("球拖至右下仍须避让文字", home.contains("if (compact) 120.dp else 128.dp") && home.contains("Spacer(Modifier.height(6.dp))"))
        assertTrue("半球与反馈留白必须与卡高测量分层", home.contains("val cardTopTarget = if (feedbackHeight > ballBox) feedbackHeight - ballBox / 2 + 8.dp else ballBox / 2") && home.contains("Box(Modifier.padding(top = cardTop))") && home.contains("Column(Modifier.onSizeChanged { cardHeight"))
        val run = home.substringAfter("internal fun RunSummaryCard(").substringBefore("private fun runRequirementText(")
        assertTrue("窄卡也不能丢失错误与来源全文", !run.contains("maxLines"))
    }

    @Test fun `设置新标题仍测高并且长行可增长`() {
        val helper = code("main/pages/redesign/SettingsOverview.kt")
        assertTrue(helper.contains("item(key = \"large_title\")"))
        assertTrue(helper.contains("pilot.titleHeightPx = it.height.toFloat()"))
        assertTrue(helper.contains("heading()"))
        assertTrue(helper.contains("leading()"))
        assertTrue("标题球必须避让右侧48dp悬浮搜索", helper.contains("padding(end = 60.dp)"))
        val row = helper.substringAfter("fun SettingsOverviewRow(")
        assertTrue(row.contains("heightIn(min = 72.dp)"))
        assertTrue(row.contains("LocalDensity.current.fontScale >= 1.2f"))
        assertTrue(row.contains("if (largeText && (trailing != null || trailingChevron))"))
        assertTrue(!row.contains("maxLines") && !row.contains("heightIn(max"))
        assertTrue(row.contains("Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)"))
    }

    @Test fun `首页新刻度不改变字号地板与两种密度层级`() {
        val tokens = code("design/DesignTokens.kt")
        listOf("homeDate = 26.sp", "homeDateCompact = 24.sp", "lineHomeDate = 32.sp",
            "lineHomeDateCompact = 30.sp", "homeFocus = 20.sp", "homeFocusCompact = 18.sp",
            "lineHomeFocus = 25.sp", "lineHomeFocusCompact = 23.sp").forEach {
            assertTrue("缺少字号令牌 $it", tokens.contains(it))
        }
    }
}
