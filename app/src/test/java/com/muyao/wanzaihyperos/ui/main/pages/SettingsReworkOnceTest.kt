package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
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
    private val preferences = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/PreferencesPage.kt")
    private val reminders = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/RemindersPage.kt")

    private fun compact(text: String) = text.replace(Regex("\\s+"), "")

    private fun block(text: String, marker: String): String {
        val markerStart = text.indexOf(marker)
        assertTrue("找不到实际代码区段: $marker", markerStart >= 0)
        val start = text.indexOf('{', markerStart)
        assertTrue("代码区段没有起始括号: $marker", start >= 0)
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
            } else when (char) {
                '"', '\'' -> quote = char
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start + 1, index)
                }
            }
        }
        error("代码区段未闭合: $marker")
    }

    private fun read(f: File): String {
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    @Test
    fun `A5 提醒页按稿排：开关卡、三颗胶囊、状态说明`() {
        val page = read(reminders)
        val settings = read(settings)
        // 稿是 .paper.flush 的白色卡，不是设置页的玻璃组。
        assertTrue(page.contains("PaperCard {"))
        assertTrue(page.contains("item(key = \"lead-chips\")"))
        assertTrue(page.contains("item(key = \"lead-notice\")"))
        // notice 必须按真实状态分叉，不许一句静态文案糊过去。
        val notice = settings.substringAfter("internal fun reminderNotice")
        assertTrue(notice.contains("尚未开启"))
        assertTrue(notice.contains("还没排上"))
        assertTrue(notice.contains("这七天没有要提醒的课"))
        assertTrue(notice.contains("只存这台手机"))
    }
    @Test
    fun `A5 续：隐私组与三级诊断也用白纸卡，诊断小标题不再是设置页图标路标`() {
        // 2026-11-09：这条页里三个分组（开关 / 隐私 / 排程诊断）统一成稿的 .paper.flush，
        // 三级诊断的小标题从 SettingSectionLabel（带磁贴图标）降级为纯标题——
        // 图标路标是设置总览页的语法，二级页的折叠里再用就成了两套语言混排。
        val page = read(reminders)
        // 只查「用」不查「提」：文件里那句解释「SettingGroup 是玻璃卡」的注释要留着。
        assertTrue("提醒页不该再 import SettingGroup", !page.contains("import com.muyao.wanzaihyperos.ui.design.SettingGroup"))
        assertTrue("提醒页不该再摆 SettingGroup 卡片", !page.contains("SettingGroup {"))
        assertEquals("开关卡 + 隐私 + 诊断 = 三张白纸卡", 3, Regex("PaperCard \\{").findAll(page).count())
        assertTrue("排程诊断该是纯标题", page.contains("text = \"排程诊断\""))
        assertTrue("诊断标题该挂 heading 语义", page.contains("Modifier.semantics { heading() }"))
        assertTrue("诊断标题不该再带图标", !page.contains("SettingSectionLabel(\"排程诊断\""))
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
        // 导入已迁到偏好页；验证确认后的实际调用，不把注释或旧位置当证据。
        val preferencesText = SourceScan.stripComments(read(preferences))
        val preview = block(preferencesText, "preview?.let { pending ->")
        val restore = compact(block(preview, "AppearanceChoice(\"确认恢复这份偏好备份\""))
        assertTrue("确认恢复后必须在 IO 写入偏好",
            "withContext(Dispatchers.IO){PrefsBackup.apply(pending)}" in restore)
        assertTrue("导入成功后必须同步运行期状态，失败不得伪装成功",
            "if(count>=0){com.muyao.wanzaihyperos.ui.syncUiStateFromPrefs(uiState)" in restore)
    }

    @Test
    fun `C6 导出的数据流拿不到时必须报错，不许静默成功`() {
        // 原来的 `openOutputStream(uri)?.use { … }`：拿不到流就整段跳过，
        // 界面还提示「已导出」，而文件根本没写出来。
        val text = SourceScan.stripComments(read(preferences))
        val code = compact(text)
        assertTrue("偏好页缺实际导出 launcher", "valexport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(" in code)
        val export = compact(block(text, "val export = rememberLauncherForActivityResult("))
        assertTrue("导出仍然用 `?.use` 静默跳过（拿不到流就当写成功）", "?.use" !in export)
        val io = compact(block(export, "withContext(Dispatchers.IO)"))
        assertTrue("导出拿不到流时必须直接抛错",
            "openOutputStream(uri)?:error(\"系统没有给出可写的数据流\")" in io)
        assertTrue("输出流必须在 IO 中写入并关闭",
            "output.use{it.write(text.toByteArray(Charsets.UTF_8))}" in io)
        assertTrue("只有 IO 写入与关闭成功后才允许报告已导出",
            "withContext(Dispatchers.IO){$io}result=\"偏好已导出\"" in export)
        assertTrue("导出取消必须继续抛出，不能误报成功",
            "catch(cancel:CancellationException){throwcancel}" in export)
        assertTrue("导出空流失败必须给用户一句能看懂的话",
            "导出未完成：系统没有给出可写的数据流，请重新选择保存位置" in export)
        assertTrue("导出其他失败不得报告成功", "else\"导出未完成，请重试\"" in export)
    }

    @Test
    fun `A5 提醒页的正文列与设置页同宽，组标题图标不许顶到屏幕边`() {
        // 2026-11-08 真机发现：提醒页只在 hero / footnote 上加了左右 16dp，
        // 组标题与卡片没有 ⇒ 时钟图标贴到屏幕最左边（约 2.5dp），卡片也比设置页宽一截。
        // 正解是把左右 16dp 放到 LazyColumn 的 contentPadding 上，item 不再各加一份。
        val page = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/RemindersPage.kt")
        assertTrue("RemindersPage.kt 不见了", page.isFile)
        val text = page.readText(Charsets.UTF_8)
        val pad = text.substringAfter("contentPadding = PaddingValues(").substringBefore("),")
        assertTrue("提醒页 contentPadding 没有左右 16dp（组标题图标会顶到屏幕边）",
            "start = 16.dp" in pad && "end = 16.dp" in pad)
        assertTrue("提醒页 hero 又自己加了左右 16dp（会和 contentPadding 叠成 32dp）",
            "Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)" !in text)
        assertTrue("提醒页脚注又自己加了左右 16dp（会和 contentPadding 叠成 32dp）",
            "Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp)" !in text)
    }

    @Test
    fun `C1 通透度滑块越往右越通透，不许符号反`() {
        // 界面左端写「更厚实」、右端写「更通透」⇒ 滑块值越大，alpha 系数必须越小。
        // 上一版写成 1 + (v-50)/50×0.38，v=100 反而更厚，界面与代码各说一套。
        val glass = File("src/main/java/com/muyao/wanzaihyperos/ui/design/glass/GlassSpec.kt")
        assertTrue("GlassSpec.kt 不见了", glass.isFile)

        val mid = com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(50f)
        val max = com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(100f)
        val min = com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(0f)

        // 50 是原值：系数必须正好 1
        assertTrue("通透度 50% 应该等于原样（1.0），实际 $mid", kotlin.math.abs(mid - 1f) < 0.001f)
        // 100 = 更通透 ⇒ 系数 < 1；0 = 更厚实 ⇒ 系数 > 1
        assertTrue("通透度拉满反而变厚了（v=100 得 $max）", max < 1f)
        assertTrue("通透度拉到最左没有变厚（v=0 得 $min）", min > 1f)
        // 方向单调：越往右越小
        assertTrue("0..100 上系数不是单调下降", min > mid && mid > max)
        // 具体幅度按稿：100 ⇒ 0.62
        assertTrue("v=100 应为 0.62，实际 $max", kotlin.math.abs(max - 0.62f) < 0.001f)
        assertTrue("v=0 应为 1.38，实际 $min", kotlin.math.abs(min - 1.38f) < 0.001f)
    }

    @Test
    fun `C4 应用内的减少动态与高对比必须真的进到 Motion 的判定里`() {
        // 原来这两项只能跳系统设置 ⇒ 应用内没有开关。现在有两个真偏好，
        // 但光有偏好不够：Motion 的计算属性必须把它们并进结果，否则开关是摆设。
        val motion = File("src/main/java/com/muyao/wanzaihyperos/ui/design/Motion.kt")
        assertTrue("Motion.kt 不见了", motion.isFile)
        val text = motion.readText(Charsets.UTF_8)

        assertTrue("Motion 没有应用内「减少动态」的原始字段",
            "systemAnimationsEnabled" in text && "reduceMotion" in text)
        assertTrue("animationsEnabled 没有把应用内开关并进来",
            "systemAnimationsEnabled && !reduceMotion" in text)
        assertTrue("Motion 没有应用内「高对比」的原始字段", "highContrastOverride" in text)
        assertTrue("highTextContrast 没有把应用内开关或进来",
            "systemHighTextContrast || highContrastOverride" in text)

        // 两条偏好必须落在 UiPrefs（有读写、有键名），否则装不住
        val prefsText = read(prefs)
        assertTrue("UiPrefs 缺 reduce_motion 键", "reduce_motion" in prefsText)
        assertTrue("UiPrefs 缺 high_contrast_override 键", "high_contrast_override" in prefsText)
        assertTrue("UiPrefs 缺 saveReduceMotion", "fun saveReduceMotion" in prefsText)
        assertTrue("UiPrefs 缺 saveHighContrastOverride", "fun saveHighContrastOverride" in prefsText)
    }

    @Test
    fun `C5 辅助显示并入外观后，展开与调整不得关闭外观层`() {
        val shell = File("src/main/java/com/muyao/wanzaihyperos/ui/main/WanzaiMainShell.kt")
        val appearance = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppearancePage.kt")
        val shellText = SourceScan.stripComments(read(shell))
        val shellCode = compact(shellText)
        val settingsCode = compact(SourceScan.stripComments(read(settings)))
        assertTrue("设置页必须仍能打开外观", "onClick=onOpenAppearance" in settingsCode)
        val settingsEntry = shellText.substringAfter("MainSection.Settings -> SettingsSectionPage(", "")
        val openAppearance = compact(block(settingsEntry, "onOpenAppearance ="))
        assertEquals("设置入口必须真的打开外观且不关掉其它层", "showAppearance=true", openAppearance)
        assertTrue("外观必须由同一可见层承载", "visible=showAppearance" in shellCode)
        assertTrue("关闭外观时才退出外观层", "AppearancePage(onClose={showAppearance=false}," in shellCode)
        val appearanceText = SourceScan.stripComments(read(appearance))
        val text = compact(appearanceText)
        val interaction = compact(block(appearanceText, "item(key = \"section-interaction\")"))
        assertTrue("辅助显示必须仍在外观页实际可展开区段内",
            "ThemeExpandableSection(\"动画与显示\"" in interaction && "toggleSection(\"interaction\")" in interaction)
        listOf(
            "减少动态" to "reduceMotion", "高对比显示" to "highContrast", "减弱透明度" to "reduceTransparency",
        ).forEach { (label, field) ->
            assertTrue("$label 必须仍有实际开关", "AppearanceToggle(\"$label\"" in interaction)
            assertTrue("$label 必须更新真实偏好", "current=current.copy($field=!current.$field)" in interaction)
        }
        assertTrue("辅助显示开关必须走落盘链",
            "varcurrentbyLiveAppearanceState(onThemeMode)" in text && "UiPrefs.applyAppearance(next)" in text)
        val toggle = compact(block(appearanceText, "fun toggleSection("))
        listOf("onClose(", "close(", "closePage(", "showAppearance=false", "onOpenAccessibility", "showAccessibility").forEach { token ->
            assertTrue("展开/调整辅助显示不许关闭或切走外观层: $token", token !in interaction && token !in toggle)
        }
    }
}
