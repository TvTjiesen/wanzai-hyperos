package com.muyao.wanzaihyperos.ui.design.liquid

import com.muyao.wanzaihyperos.ui.SourceScan
import io.github.neoruaa.glasmio.ui.NavigationBarSurfaceHeight
import androidx.compose.ui.unit.dp
import java.io.File
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
 * 三件东西必须同源 —— native 底栏条高（NavigationBarSurfaceHeight）、
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
        assertEquals("实际 native 条高必须62dp", 62.dp, NavigationBarSurfaceHeight)
        var root: File? = File(System.getProperty("user.dir"))
        while (root != null && !File(root, "app/build.gradle.kts").isFile) root = root.parentFile
        val native = File(requireNotNull(root), "third_party/glasmio/glasmio-ui/src/main/java/io/github/neoruaa/glasmio/ui/GlassBottomBar.kt")
            .readText(Charsets.UTF_8).let(SourceScan::stripComments)
        assertEquals("native 条高声明必须唯一", 1, Regex("""val\s+NavigationBarSurfaceHeight\s*=\s*62\.dp""").findAll(native).count())
        assertTrue("native renderer 必须读取这一个条高", "modifier.height(NavigationBarSurfaceHeight)" in native)
        val bar = source("main/ui/design/liquid/GlassBar.kt")
        assertTrue("实际 adapter 必须委托 native nav", "GlasmioNavigationBar(" in bar)
        assertTrue("adapter 不得另外写死条高", !Regex("""\.height\(\s*(?:58|62|64|66)\.dp\s*\)""").containsMatchIn(bar))
    }

    @Test
    fun `内距里的条高必须等于底栏条高`() {
        val text = insetBody()
        assertTrue("内距没有引用 native 同一条高：" + text, "NavigationBarSurfaceHeight + barBottomGap + 4.dp" in text)
        assertTrue("内距不得另写数字条高：" + text, !Regex("""(?:58|62|64|66)\.dp""").containsMatchIn(text))
        assertTrue("内距里没有 4.dp 收尾余量：" + text, text.contains("4.dp"))
        assertTrue("隐藏底栏时必须保留系统手势区并留24dp收尾空间", "if (!barVisible) return inset + 24.dp" in text)
    }

    @Test
    fun `内距必须读真的 navigationBars inset`() {
        val text = insetBody()
        assertTrue("inset 没读 navigationBars（写死 dp 就是旧缺陷）：" + text, text.contains("navigationBars"))
        assertTrue("inset 没用实算值：" + text, text.contains("calculateBottomPadding"))
    }

    @Test
    fun `实际底栏和内容内距必须共用六dp与系统导航栏空隙`() {
        val inset = insetBody()
        val bar = source("main/ui/design/liquid/GlassBar.kt")
        assertTrue("内容必须留系统inset加6dp，无系统栏时16dp", "if (inset > 0.dp) 6.dp + inset else 16.dp" in inset)
        assertTrue("native adapter 必须使用同样空隙", "bottom = if (custom.floatingNavigation) { if (inset > 0.dp) inset + 6.dp else 16.dp } else inset" in bar)
        assertTrue("native adapter 必须实际计算系统导航栏inset", "WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()" in bar)
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
