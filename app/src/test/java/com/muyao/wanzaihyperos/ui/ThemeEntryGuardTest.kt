package com.muyao.wanzaihyperos.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B4-14 ④：主题入口**全工程唯一**的守卫。
 *
 * ## 为什么必须是「全局扫描」而不是「单文件含串」
 *
 * 上一版守卫只判「设置页里不许出现 `AppThemeMode.entries.forEach`」——
 * 那是**单文件**判据：`values().forEach` 之类改写照样绿，而且死页 `MinePage` 里同一段代码它根本看不见。
 * 工单第 22 轮的复核结论是判据要改成两条：
 *  ① `AppThemeMode.entries`（枚举全量遍历）**全工程只许出现在读盘那一处**（`WanzaiRoot.kt`）；
 *  ② UI 侧的消费点 `AppThemeMode.(System|Light|Dark)` 走**白名单**，名单外的文件一律红。
 *
 * 死页 `MinePage` 的那三行已经摘掉（连形参一起），否则「只有一个入口」这句话对读者不成立，
 * 而且一旦被接回去就复活成第二个入口。
 *
 * ## 白名单（每一处都要有理由，少一处红、多一处也红）
 *  - `main/ui/AppTheme.kt` —— `AppUiState.themeMode` 的**默认值**（状态持有者，不是入口）
 *  - `main/ui/main/pages/AppearancePage.kt` —— **唯一的 UI 入口**（外观页「主题」分段控件）
 *  - `main/WanzaiRoot.kt` —— 读盘解析的兜底值（`?: AppThemeMode.System`）
 *  - `debug/dev/BoardPreviewActivity.kt` —— 开发预览台，不是用户界面
 */
class ThemeEntryGuardTest {

    @Test
    fun `枚举全量遍历全工程只许读盘那一处`() {
        val allowed = setOf("main/WanzaiRoot.kt")
        val hits = SourceScan.sources()
            .filter { "AppThemeMode.entries" in it.second }
            .map { it.first }
        val unexpected = hits - allowed
        assertTrue(
            "`AppThemeMode.entries` 只允许出现在 $allowed（读盘解析），这些是第二入口：$unexpected",
            unexpected.isEmpty(),
        )
        assertTrue("读盘那一处不见了：$allowed", hits.containsAll(allowed))
    }

    @Test
    fun `主题在 UI 侧只有一个消费点`() {
        val allowed = setOf(
            "main/ui/AppTheme.kt",
            "main/ui/main/pages/AppearancePage.kt",
            "main/WanzaiRoot.kt",
            "debug/dev/BoardPreviewActivity.kt",
        )
        val pattern = Regex("""AppThemeMode\.(System|Light|Dark)""")
        val hits = SourceScan.sources()
            .filter { pattern.containsMatchIn(it.second) }
            .map { it.first }
        val unexpected = hits - allowed
        assertTrue(
            "这些文件在 UI 侧消费了主题枚举但不在白名单里（要么是第二个入口，要么该改）：$unexpected",
            unexpected.isEmpty(),
        )
        val stale = allowed - hits
        assertTrue("白名单里这几处已经不消费主题了，名单要改：$stale", stale.isEmpty())
    }

    @Test
    fun `外观页那一个入口还接得死`() {
        val page = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppearancePage.kt")
        assertTrue("找不到外观页", page.isFile)
        val code = SourceScan.stripComments(page.readText(Charsets.UTF_8))
        assertTrue("外观页没有「主题」分段控件", "label = \"主题\"" in code)
        assertTrue("外观页的主题没接 onSelect = onThemeMode", "onSelect = onThemeMode" in code)
        listOf("System", "Light", "Dark").forEach { mode ->
            assertTrue("外观页少了 AppThemeMode.$mode 这一档", "AppThemeMode.$mode" in code)
        }
    }

    /**
     * 死页那一处不许再挂主题入口。
     *
     * 它本来就是死页（`MinePage(` 全工程除本文件外 0 命中），留着那三行不会立刻出事，
     * 但「唯一入口」这句话会立刻变成假的 —— 任何读者按它去找都会以为有两个地方能改主题。
     */
    @Test
    fun `死页不再挂主题入口`() {
        val mine = File("src/main/java/com/muyao/wanzaihyperos/ui/mine/MinePage.kt")
        assertTrue("找不到「我的」页", mine.isFile)
        val code = SourceScan.stripComments(mine.readText(Charsets.UTF_8))
        assertTrue("「我的」页又出现主题入口了（唯一入口在外观页）", "AppThemeMode" !in code)
        assertTrue("「我的」页还留着主题形参", "onThemeModeChange" !in code)
    }
}
