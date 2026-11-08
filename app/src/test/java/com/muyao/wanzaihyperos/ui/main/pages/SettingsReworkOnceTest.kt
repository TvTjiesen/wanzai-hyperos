package com.muyao.wanzaihyperos.ui.main.pages

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C2 / C3 / C6：返工总单里「已写坏的行为」中，三条**只能靠读源码证明**的。
 *
 * 它们坏起来都不会崩、也不会报错，只会让用户看到「界面说的和实际做的不是一回事」，
 * 所以每条都必须在改坏的当场就被这条测试拦住。
 */
class SettingsReworkOnceTest {

    private val prefs = File("src/main/java/com/muyao/wanzaihyperos/data/UiPrefs.kt")
    private val theme = File("src/main/java/com/muyao/wanzaihyperos/ui/AppTheme.kt")
    private val settings = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/SettingsSectionPage.kt")

    private fun read(f: File): String {
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    @Test
    fun `C2 恢复默认必须连取景三元组一起写盘`() {
        // 只在内存里清、不写盘 ⇒ 「恢复默认 → 杀进程重进 → 点相册」旧裁切原地复活。
        val text = read(prefs)
        val reset = text.substringAfter("fun resetAppearance").substringBefore("\n    fun ")
        listOf(
            "KEY_WALLPAPER_PAN_X" to "平移 x",
            "KEY_WALLPAPER_PAN_Y" to "平移 y",
            "KEY_WALLPAPER_ZOOM" to "缩放",
            "KEY_WALLPAPER_REV" to "版本号（让图片缓存失效的那一位）",
        ).forEach { (key, what) ->
            assertTrue("resetAppearance 写盘链漏了 $key（$what）⇒ 恢复默认对取景不生效", key in reset)
        }
        // 内存那一份也不能少（写盘链读的是它）
        assertTrue("resetAppearance 没把内存里的 wallpaperZoom 归 1", "wallpaperZoom = 1f" in reset)
    }

    @Test
    fun `C3 导入备份后要能把运行期状态拉回盘里`() {
        // 导入只写了盘，内存里那份 uiState 还是旧的 ⇒ 不重启就一直显示导入前的主题。
        val themeText = read(theme)
        val fn = themeText.substringAfter("fun syncUiStateFromPrefs").substringBefore("\n/**")
        listOf("Light", "Dark").forEach { mode ->
            assertTrue("syncUiStateFromPrefs 没映射 $mode 档", "\"$mode\"" in fn)
        }
        // System 档走的是 else 分支（不写 "System" 字面量），所以要判「兜底落在 System」，
        // 不能像上面两档那样找字符串。
        assertTrue("syncUiStateFromPrefs 的 else 分支必须落 System（读不到的档就是跟随系统）",
            "else -> AppThemeMode.System" in fn)
        assertTrue("syncUiStateFromPrefs 必须同时拉回课表网格（导入的备份里也有它）",
            "timetableGrid" in fn)
        // 枚举全量遍历只许出现在读盘那一处（ThemeEntryGuardTest 的纪律），这里不能用 entries。
        // 注释里那句「不用 AppThemeMode.entries」是在解释这条纪律，本身不算违规 —— 只看代码行。
        val codeLines = fn.lines().filterNot { it.trimStart().startsWith("//") }
        assertTrue("syncUiStateFromPrefs 不许用 AppThemeMode.entries（那条纪律是硬性的）",
            codeLines.none { "AppThemeMode.entries" in it })
        // 调用点：导入成功之后必须调它
        val settingsText = read(settings)
        val afterImport = settingsText.substringAfter("syncUiStateFromPrefs")
        assertTrue("设置页没有调用 syncUiStateFromPrefs，导入后要重启才生效", afterImport.isNotEmpty())
    }

    @Test
    fun `C6 导出的数据流拿不到时必须报错，不许静默成功`() {
        // 原来的 `openOutputStream(uri)?.use { … }`：拿不到流就整段跳过，
        // 界面还提示「已导出」，而文件根本没写出来。
        val text = read(settings)
        // 从真正的写入语句起算，到 onSuccess 为止：注释里写着旧代码 `?.use`，
        // 从 "openOutputStream" 起算会把注释也吞进来，判据就废了。
        val export = text.substringAfter("val out = context.contentResolver.openOutputStream")
            .substringBefore("}.onSuccess")
        assertTrue("导出仍然用 `?.use` 静默跳过（拿不到流就当写成功）", "?.use" !in export)
        assertTrue("导出拿不到流时没有抛错", "error(" in export)
        assertTrue("导出失败必须给用户一句能看懂的话",
            "系统没有给出可写的数据流" in export)
    }
}
