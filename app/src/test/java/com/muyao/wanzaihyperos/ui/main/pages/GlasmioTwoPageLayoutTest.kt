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
        assertTrue(home.contains("maxLines = 3"))
        assertTrue(home.contains("if (!timelineHasNothingToSay)"))
        listOf("courses", "rollcall", "todos", "szty").forEach {
            assertTrue("模块 $it 的显示开关丢失", home.contains("\"$it\" in UiPrefs.homeModules"))
        }
        assertTrue(home.contains("onClick = refreshAll"))
        val status = home.substringAfter("text = subline,").substringBefore("/**")
        assertTrue("来源状态不能白字落在壁纸白色渐隐区", status.contains("surface.copy(alpha = 1f)") && status.contains("color = MiuixTheme.colorScheme.onSurface"))
    }

    @Test fun `快捷操作全部委托真实回调而非装饰按钮`() {
        val helper = code("home/redesign/HomeDashboard.kt")
        listOf("Timetable", "Jw", "Messages", "Appearance").forEach {
            assertTrue(helper.contains("onOpen$it: () -> Unit"))
            assertTrue(Regex("HomeQuickAction\\([^\\n]+onOpen$it").containsMatchIn(helper))
        }
        assertTrue(helper.contains("WanzaiButton(onClick = onClick"))
        assertTrue(helper.contains("elevatedBackground.copy(alpha = 1f)"))
        assertTrue(helper.contains("if (compact) 16.dp else 20.dp"))
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
        listOf("homeDate = 38.sp", "homeDateCompact = 32.sp", "lineHomeDate = 44.sp",
            "lineHomeDateCompact = 38.sp", "homeFocus = 28.sp", "homeFocusCompact = 24.sp",
            "lineHomeFocus = 34.sp", "lineHomeFocusCompact = 30.sp").forEach {
            assertTrue("缺少字号令牌 $it", tokens.contains(it))
        }
    }
}
