package com.muyao.wanzaihyperos.ui.design.liquid

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏内距的守卫（#58 第 6 条：业主说「消息/应用底部 inset 不足，最后一行被底栏切」）。
 *
 * ## 这条测试在防什么
 *
 * 「列表底部要给玻璃底栏留多少」这个数，工程里曾经有两个真值：四个板块页走
 * pageBottomInsetDp()，而课表页自己写死 132.dp。后果不是好看难看：底栏高度一改，
 * 就有一页的最后一行被压住，而那一页没人会去动。
 *
 * 三件东西必须同源 —— 底栏条高（LiquidGlassNavigationBar.kt 的 .height(64.dp)）、
 * 内距里的条高项、以及内距必须读真的 navigationBars inset 而不是写死一个 dp。
 * 改任何一处，这里都会有测试红。
 */
class PageBottomInsetTest {

    private val scan = SourceScan.sources()

    private fun source(path: String): String {
        val hit = scan.firstOrNull { it.first == path }
        assertTrue("扫不到 " + path + "（扫描器坏了？）", hit != null)
        return hit!!.second
    }

    /** 五个板块页（主壳里的五个 tab）—— 它们才是会被玻璃底栏压住的那一层。 */
    private val sectionPages = listOf(
        "main/ui/main/pages/HomeSectionPage.kt",
        "main/ui/main/pages/TimetableSectionPage.kt",
        "main/ui/main/pages/NotifySectionPage.kt",
        "main/ui/main/pages/AppsSectionPage.kt",
        "main/ui/main/pages/SettingsSectionPage.kt",
    )

    @Test
    fun `五个板块页都必须走同一份底栏内距`() {
        val missing = sectionPages.filterNot { source(it).contains("pageBottomInsetDp(") }
        assertEquals("这些板块页还在自己算底栏内距：" + missing, emptyList<String>(), missing)
    }

    @Test
    fun `板块页里不许再写死底栏内距`() {
        // 曾经的形态：bottom = if (barVisible) 132.dp else 44.dp
        val hardcoded = Regex("bottom\\s*=\\s*if\\s*\\(\\s*barVisible\\s*\\)\\s*[0-9.]+\\s*\\.dp")
        val offenders = sectionPages.filter { hardcoded.containsMatchIn(source(it)) }
        assertEquals("这些板块页又写死了底栏内距：" + offenders, emptyList<String>(), offenders)
    }

    @Test
    fun `底栏条高只有一个真值`() {
        val bar = source("main/ui/design/liquid/LiquidGlassNavigationBar.kt")
        val heights = Regex("\\.height\\(([0-9.]+)\\.dp\\)").findAll(bar)
            .map { it.groupValues[1] }.filter { it == "64" || it == "58" || it == "66" }.toList()
        assertEquals("底栏条高必须恰好一处 64dp，实际：" + heights, listOf("64"), heights)
    }

    @Test
    fun `内距里的条高必须等于底栏条高`() {
        val text = insetBody()
        assertTrue("内距里没有 64.dp 这一项：" + text, text.contains("64.dp"))
        assertTrue("内距里没有 4.dp 收尾余量：" + text, text.contains("4.dp"))
    }

    @Test
    fun `内距必须读真的 navigationBars inset`() {
        val text = insetBody()
        assertTrue("inset 没读 navigationBars（写死 dp 就是旧缺陷）：" + text, text.contains("navigationBars"))
        assertTrue("inset 没用实算值：" + text, text.contains("calculateBottomPadding"))
    }

    private fun insetBody(): String {
        val src = source("main/ui/design/liquid/PageChromePilot.kt")
        val m = Regex("fun pageBottomInsetDp\\(barVisible: Boolean\\): Dp\\s*\\{(.*?)\\n\\}",
            RegexOption.DOT_MATCHES_ALL).find(src)
        assertTrue("找不到 pageBottomInsetDp 的函数体 —— 这条判据就失效了", m != null)
        return m!!.groupValues[1]
    }

    @Test
    fun `扫描面必须真的覆盖到五个板块页`() {
        val pages = scan.count { it.first.startsWith("main/ui/main/pages/") && it.first.endsWith("SectionPage.kt") }
        assertTrue("只扫到 " + pages + " 个板块页，少于 5 => 判据形同虚设", pages >= 5)
    }
}
