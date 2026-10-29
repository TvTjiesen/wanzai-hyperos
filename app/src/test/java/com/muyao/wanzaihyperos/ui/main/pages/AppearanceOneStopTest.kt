package com.muyao.wanzaihyperos.ui.main.pages

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B4-14（「外观与壁纸」一站式）的阶段 1 守卫。
 *
 * 判据是「**七件事都在这一页**，而且改完不用重启」。前者可以扫源码，
 * 后者在本工程的结构里由一条链路保证：控件只改状态 → `WanzaiTheme` 以 `themeMode` 为 key
 * 重建 `ThemeController` → 整棵树重组（不是"存盘再读"那种要重启的做法）。
 * 这条链路由第三段测试盯住：`WanzaiMainShell` 必须把 `uiState.themeMode` 和它的 setter 传进这一页。
 *
 * 「跟随系统」是否**真跟随**：`AppThemeMode.System` → `ColorSchemeMode.System`（Miuix 自己读
 * `Configuration.uiMode`）。这一步是运行时的，单测判不了 —— 阶段 1 用模拟器
 * `cmd uimode night yes/no` 实拍两帧，证据在 `verify/b4-14/`。
 */
class AppearanceOneStopTest {

    private val page = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppearancePage.kt")
    private val shell = File("src/main/java/com/muyao/wanzaihyperos/ui/main/WanzaiMainShell.kt")
    private val prefs = File("src/main/java/com/muyao/wanzaihyperos/data/UiPrefs.kt")
    private val home = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/HomeSectionPage.kt")

    private fun read(f: File): String {
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    @Test
    fun `七件事都在这一页`() {
        val text = read(page)
        listOf("主题", "壁纸", "柔化", "暗度", "玻璃", "密度").forEach { label ->
            assertTrue("外观页少了「$label」这一项（B4-14 要求一站式）", "label = \"$label\"" in text || "\"$label\"" in text)
        }
        // 「模块」是一组开关，标题由页面自己写
        assertTrue("外观页少了「模块」那一组", "模块" in text || "今日课程" in text)
    }

    @Test
    fun `主题三档会真的写到应用状态上`() {
        val text = read(page)
        assertTrue("外观页没有主题控件", "CtlRow(label = \"主题\"" in text)
        assertTrue(
            "主题三档必须是 跟随/浅色/深色（少了任何一档就不是「一站式」）",
            "AppThemeMode.System" in text && "AppThemeMode.Light" in text && "AppThemeMode.Dark" in text,
        )
        val shellText = read(shell)
        assertTrue(
            "主壳没有把 themeMode 传进外观页 ⇒ 那一档改了也不会立刻生效",
            "themeMode = uiState.themeMode" in shellText && "uiState.themeMode = it" in shellText,
        )
    }

    /**
     * 「每项改完立刻生效」做到了，但**记住**曾经被悄悄省掉：外观页的主题只写 `uiState.themeMode`，
     * 杀进程重进就回退（设置页那一份是写盘的）。这条守两件事：
     *  ① 主题这一档必须走 `UiPrefs.saveThemeMode`（运行期状态 + 落盘各一次）；
     *  ② `HOME_MODULES` 里**每一个 id** 都要在首页有 `in UiPrefs.homeModules` 的读点 ——
     *     否则它就是个"拨了没反应"的假开关（`shortcuts` 就是这么被抓出来的）。
     *
     * 变异验收：删掉 `saveThemeMode` 那行 ⇒ 红；往 `HOME_MODULES` 加一个没接线的 id ⇒ 红。
     */
    @Test
    fun `每个开关都要么落盘要么真的接上了消费`() {
        val shellText = read(shell)
        assertTrue(
            "外观页改主题没有落盘（杀进程会回退）",
            "UiPrefs.saveThemeMode(it.name)" in shellText,
        )

        val prefsText = read(prefs)
        val modules = Regex("""HOME_MODULES = listOf\(([^)]*)\)""").find(prefsText)
            ?.groupValues?.get(1)
            ?.split(',')
            ?.map { it.trim().trim('"') }
            ?.filter { it.isNotEmpty() }
            ?: error("找不到 HOME_MODULES")
        assertTrue("HOME_MODULES 空了？", modules.isNotEmpty())

        val homeText = read(home)
        val unwired = modules.filter { "\"$it\" in UiPrefs.homeModules" !in homeText }
        assertTrue(
            "这些首页模块是假开关（拨了首页没有任何消费点）：$unwired —— 要么接线要么从 HOME_MODULES 删掉",
            unwired.isEmpty(),
        )
    }

    /**
     * 主题入口**只能有一个**，且承重链路必须接死（工单第 15 轮 c）：
     *  - 设置页不许再有 `AppThemeMode.entries.forEach`（三行是第二个入口）；
     *  - 外观页的 `Segmented(... onSelect = onThemeMode)` 必须真的接上；
     *  - 主壳 `onThemeMode` 必须写盘；
     *  - `WanzaiTheme` 的 `remember` 必须 **key 含 themeMode**（换成裸 `remember` 就永远不重建控制器）。
     *
     * 变异验收：删 `onSelect = onThemeMode` 或把 key 去掉 ⇒ 本条红。
     */
    @Test
    fun `主题只有一个入口且链路接死`() {
        val settingsText = read(File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/SettingsSectionPage.kt"))
        assertTrue(
            "设置页又出现了主题三行（与外观页重复的第二入口）",
            "AppThemeMode.entries.forEach" !in settingsText,
        )
        val pageText = read(page)
        assertTrue("外观页的主题 Segmented 没接 onThemeMode", "onSelect = onThemeMode" in pageText)
        assertTrue("外观页副标题该提主题（设置页那行已改，这里只看外观页控件）", "label = \"主题\"" in pageText)

        val shellText = read(shell)
        assertTrue("主壳 onThemeMode 没落盘", "UiPrefs.saveThemeMode(it.name)" in shellText)

        val themeText = read(File("src/main/java/com/muyao/wanzaihyperos/ui/AppTheme.kt"))
        // 2026-10-29：加了背景预设后 key 变成 remember(themeMode, background)。
        // 判据要的是「themeMode 在 key 里」，不是「key 只有它一个」—— 所以按正则认
        // themeMode 作**首个 key**，后面允许多个。把 themeMode 从 key 里删掉仍会判红。
        assertTrue(
            "WanzaiTheme 的 controller 不是按 themeMode 重建的（remember 的 key 里没有 themeMode）",
            Regex("""remember\(\s*themeMode\s*[,)]""").containsMatchIn(themeText),
        )
    }
}
