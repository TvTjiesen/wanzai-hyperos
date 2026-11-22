package com.muyao.wanzaihyperos.ui.main.pages

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GlasmioTwoPageLayoutTest {
    private val root = File("src/main/java/com/muyao/wanzaihyperos/ui")
    private fun code(path: String) = File(root, path).readText()

    @Test fun `首页日期与课程层级使用密度令牌`() {
        val home = code("main/pages/HomeSectionPage.kt")
        assertTrue(home.contains("Type.homeDateCompact else Type.homeDate"))
        assertTrue(home.contains("Type.homeFocusCompact else Type.homeFocus"))
        assertTrue(home.contains("Type.lineHomeFocusCompact else Type.lineHomeFocus"))
        val course = home.substringAfter("internal fun FocusGlassCard(").substringBefore("private fun CountdownRing(")
        assertTrue("长标题和来源状态必须自然增长", !course.contains("maxLines") && !course.contains("heightIn(max"))
        assertTrue(home.contains("if (!timelineHasNothingToSay)"))
        listOf("courses", "rollcall", "todos", "szty").forEach {
            assertTrue("模块 $it 的显示开关丢失", home.contains("\"$it\" in UiPrefs.homeModules"))
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

    @Test fun `首页双列服务保留开关与大字纵排回退`() {
        val home = code("main/pages/HomeSectionPage.kt")
        assertTrue(home.contains("maxWidth >= 304.dp && LocalDensity.current.fontScale < 1.3f"))
        assertTrue(home.contains("item(key = \"services\")"))
        assertTrue(home.contains("narrow = true"))
        assertTrue(home.contains("onOpen = onOpenRollcall"))
        assertTrue(home.contains("onClick = onOpenSzty"))
        assertTrue(home.contains("if (ratio != null)"))
        assertTrue(home.contains("if (headlineInStatus) AiBall.lineOf(ballState) else statusTitle"))
        assertTrue("只有课程已结束才可合并同义摘要，紧急待办与取数状态不能丢", home.contains("ballState == com.muyao.wanzaihyperos.ui.design.ai.AiBallState.Done"))
        assertTrue("非近课不得用数百分钟的长倒计时挤占球状态", home.contains("focus.ongoing || focus.minutes in 0..59"))
        assertTrue("加载与上课状态只精确去重，来源后缀保留", home.contains("if (focus.ongoing) AiBall.lineOf(ballState) else headline"))
        assertTrue("球拖至右下仍须避让文字", home.contains("if (compact) 120.dp else 128.dp") && home.contains("Spacer(Modifier.height(6.dp))"))
        assertTrue("半球留白必须与卡高测量分层", home.contains("Box(Modifier.padding(top = ballBox / 2))") && home.contains("Column(Modifier.onSizeChanged { cardHeight"))
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
