package com.muyao.wanzaihyperos.ui.design

import com.muyao.wanzaihyperos.ui.design.glass.BarMaterial
import com.muyao.wanzaihyperos.ui.design.glass.GlassSpec
import com.muyao.wanzaihyperos.ui.design.glass.GlassStack
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurfaceId
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurfaceKind
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurfaceStyle
import com.muyao.wanzaihyperos.ui.design.glass.barMaterialFor
import com.muyao.wanzaihyperos.ui.design.glass.cardFaceAlpha
import com.muyao.wanzaihyperos.ui.design.glass.glassMechanismFiles
import com.muyao.wanzaihyperos.ui.design.glass.unglassedSites
import com.muyao.wanzaihyperos.ui.design.glass.legacyGlassSites
import com.muyao.wanzaihyperos.ui.SourceScan
import io.github.neoruaa.glasmio.core.GlassAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 玻璃三档的**接线**守卫（v3 清单 #12）。
 *
 * 第 1 条测映射，第 2 条测"这根线真的还连着"：
 * `LocalBarMaterial` 曾经**全工程无人 provide**，所有玻璃卡一路吃默认的 LIQUID，
 * 设置页写"磨砂"而屏幕上是折射液态 —— 那种假开关靠注释守不住，所以在这里钉一条源码级断言。
 */
class GlassModeWiringTest {

    private val repoRoot: File
        get() {
            var d: File? = File(System.getProperty("user.dir"))
            while (d != null) {
                if (File(d, "app/build.gradle.kts").isFile) return d
                d = d.parentFile
            }
            error("找不到工程根目录")
        }

    private fun source(name: String): String =
        File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos/$name")
            .readText(Charsets.UTF_8).let(SourceScan::stripComments)

    private fun vendor(module: String, name: String): String =
        File(repoRoot, "third_party/glasmio/glasmio-$module/src/main/java/io/github/neoruaa/glasmio/$module/$name")
            .readText(Charsets.UTF_8).let(SourceScan::stripComments)

    private fun functionCode(code: String, name: String): String {
        val start = Regex("""fun\s+(?:<[^>]+>\s+)?${Regex.escape(name)}\s*\(""").find(code)?.range?.first
            ?: error("Missing function $name")
        var parentheses = 0
        var signatureEnd = -1
        for (i in code.indexOf('(', start) until code.length) {
            if (code[i] == '(') parentheses++
            if (code[i] == ')' && --parentheses == 0) {
                signatureEnd = i
                break
            }
        }
        check(signatureEnd >= 0) { "Unclosed function signature $name" }
        val bodyStart = code.indexOf('{', signatureEnd)
        check(bodyStart >= 0) { "Missing function body $name" }
        var braces = 0
        for (i in bodyStart until code.length) {
            if (code[i] == '{') braces++
            if (code[i] == '}' && --braces == 0) return code.substring(start, i + 1)
        }
        error("Unclosed function body $name")
    }

    private val drawSite = Regex("""(?<![.\w])(?:GlassSurface|GlasmioNavigationBar|GlasmioSwitch|GlasmioButton|GlasmioAlertDialog)\s*\(|\.drawBackdrop\s*\(""")

    @Test
    fun `三档映射：减弱透明度永远优先退实底`() {
        assertEquals(BarMaterial.LIQUID, barMaterialFor("liquid", translucent = true))
        assertEquals(BarMaterial.FROSTED, barMaterialFor("frost", translucent = true))
        assertEquals(BarMaterial.SOLID, barMaterialFor("off", translucent = true))
        // 任何一档，只要系统/设置要求不透明 ⇒ 一律 SOLID
        listOf("liquid", "frost", "off").forEach {
            assertEquals("$it 在不透明下必须实底", BarMaterial.SOLID, barMaterialFor(it, translucent = false))
        }
    }

    @Test
    fun `主题根节点必须真的 provide LocalBarMaterial`() {
        val theme = source("ui/AppTheme.kt")
        assertTrue(
            "AppTheme 里找不到 `LocalBarMaterial provides` —— 三档又断线了（v3 #12）",
            theme.contains("LocalBarMaterial provides"),
        )
        assertTrue(
            "provide 的值必须由 barMaterialFor(盘里的 glassMode) 算，不许写死 LIQUID",
            theme.contains("barMaterialFor("),
        )
    }

    /**
     * `GlassSurface.kt:117` 那句注释写着「判斷抽到 styleFor 是因為它必須能被單測覆蓋」——
     * 但一直**没有**这条测试。补上：磨砂档必须真的把折射归零，
     * 否则"界面说磨砂、画面还是液态"这句判据只是嘴上满足。
     */
    @Test
    fun `磨砂档必须把折射归零、只留模糊；液态档不许被改动`() {
        val liquid = com.muyao.wanzaihyperos.ui.design.glass.styleFor(
            material = BarMaterial.LIQUID,
            style = GlassSurfaceStyle.CONTAINER,
            frostBlur = 18.dp,
        )
        assertTrue(
            "液态档应该原样透传 style",
            liquid.lensHeight > 0.dp && liquid.lensAmount > 0.dp,
        )

        val frost = com.muyao.wanzaihyperos.ui.design.glass.styleFor(
            material = BarMaterial.FROSTED,
            style = GlassSurfaceStyle.CONTAINER,
            frostBlur = 18.dp,
        )
        assertEquals("磨砂 = 折射高度归零", 0.dp, frost.lensHeight)
        assertEquals("磨砂 = 折射量归零", 0.dp, frost.lensAmount)
        assertEquals("磨砂 = 用主题的 frostBlur", 18.dp, frost.blur)
        assertNotEquals(
            "磨砂与液态必须画得出来不一样（否则三档是假的）",
            liquid.blur to liquid.lensHeight,
            frost.blur to frost.lensHeight,
        )
    }

    /**
     * G-1（**按监督员第 7 轮改写的判据**）：任何一层都不许 provide **常量**档位。
     *
     * ⚠️ 我上一轮给的判据「`LocalBarMaterial provides` 命中文件数 == 1」是**写错了**：
     *   合法的就是两处（`AppTheme` 算出来 + 壳里 `GlassTheme` 原样透传），
     *   数数既判不了成因也拦不住新增。真正的不变量是**provide 出去的那个值不许是常量** ——
     *   只要有一处写死 `BarMaterial.LIQUID`，它内层的每一面玻璃就都回到"档位无效"。
     */
    @Test
    fun `任何一层都不许把档位当常量 provide 出去`() {
        val root = File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos")
        val offenders = mutableListOf<String>()
        var hits = 0
        // **白名单**而不是黑名单：黑名单能被"上面先 `val m = BarMaterial.LIQUID`、这里 provide m"绕过去
        //（第 7 轮审计给的两条绕法之一）。白名单只认三种来源：盘里那枚字符串算出来的、
        // 外层传进来的那一份（`LocalBarMaterial.current`）、以及 `GlassTheme` 的参数原样透传。
        val allowedForms = listOf("barMaterialFor(", "LocalBarMaterial.current", "material,")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val lines = SourceScan.stripComments(f.readText(Charsets.UTF_8)).lines()
            lines.forEachIndexed { i, line ->
                if (!line.contains("LocalBarMaterial provides")) return@forEachIndexed
                hits++
                // provide 的值经常折行写到后面几行（`AppTheme` 就是），取一个窗口再剥注释。
                val value = lines.drop(i).take(6)
                    .filterNot { it.trim().startsWith("//") }
                    .joinToString(" ")
                    .substringAfter("LocalBarMaterial provides")
                    .trim()
                val constant = Regex("""BarMaterial\.(LIQUID|FROSTED|SOLID)""").containsMatchIn(value)
                val recognized = allowedForms.any { it in value }
                if (constant || !recognized) {
                    offenders += "${f.name}:${i + 1} → ${value.take(72)}" +
                        if (constant) "（常量档位）" else "（来源不在白名单：绕一层就能把常量塞进去）"
                }
            }
        }
        assertTrue(
            "全工程至少要有两处 provide（主题根 + 壳里的 GlassTheme），少了就是又断线了", hits >= 2,
        )
        assertTrue("这些 provide 不是从盘里/外层算出来的 ⇒ 它内层的玻璃三档全废：$offenders", offenders.isEmpty())
    }

    /**
     * G-1b：`GlassTheme` 的 `material` 必须**必填且由调用点传入**（第 2 轮 P0#1 那一条原样保留）。
     */
    @Test
    fun `GlassTheme 的 material 必须必填且由调用点传入`() {
        val glassSurface = source("ui/design/glass/GlassSurface.kt")
        // 只判 **GlassTheme 自己的参数表**：`GlassSurface` 那个
        // `material: BarMaterial = LocalBarMaterial.current` 是**对的**（读外层提供的那一份，
        // 不是常量兜底），第一版我把它一刀切判负了 —— 测试写宽比不写更坏，会逼人绕开它。
        val themeParams = glassSurface.substringAfter("fun GlassTheme(").substringBefore(") {")
        assertTrue(
            "`GlassTheme(material)` 又有了默认值 ⇒ 谁都能包一层不传的、静默覆写成 LIQUID：" +
                themeParams.lineSequence().filter { "material" in it }.toList(),
            themeParams.lineSequence().none { "material" in it && "=" in it },
        )
        assertTrue(
            "兜底常量回来了（`material ?: BarMaterial.LIQUID`）：内层会覆写外层算好的档位",
            !glassSurface.contains("?: BarMaterial.LIQUID"),
        )
        val shell = source("ui/main/WanzaiMainShell.kt")
        assertTrue(
            "壳里那层 GlassTheme 没把外层材质传进去 ⇒ 顶栏/四板块/此刻卡/底栏全部退回 LIQUID",
            shell.contains("GlassTheme(material = LocalBarMaterial.current)"),
        )
    }

    /**
     * G-2：读 `LocalBarMaterial.current` 的界面文件必须真的把它交给玻璃（读了不用＝假接线）。
     *
     * ⚠️ 第 7 轮补一条同族的豁免：`rememberEffectiveMaterial(backdrop)` 内部读的就是外层档位，
     *   调用点写成 `material = material`（先算进一个局部量）也是**接上了** ——
     *   这一族的判据是"线连着"，不是"必须写成某个字面量"。
     */
    @Test
    fun `读了 LocalBarMaterial 的文件必须把它传进玻璃`() {
        val root = File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos")
        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = SourceScan.stripComments(f.readText(Charsets.UTF_8))
            if (f.name == "HomeSectionPage.kt") {
                assertHomeMaterialFlow(functionCode(text, "FocusGlassCard"))
                return@forEach
            }
            if (text.contains("LocalBarMaterial.current") &&
                !text.contains("material = LocalBarMaterial.current") &&
                !text.contains("rememberEffectiveMaterial(") &&
                !text.contains("LocalBarMaterial provides")
            ) offenders += f.name
        }
        assertTrue("这些文件读了档位却没交给玻璃：$offenders", offenders.isEmpty())
    }

    /**
     * G-4（第 7 轮，业主真机量到「三档逐位相同 = 0.000 / 0px」之后）：
     * **卡面垫色必须跟着档位走**，而且三档必须单调。
     *
     * 数值逐条对板（`wz-v2.html`）：液态 = `.focus-card.glass` 三段白 .42/.19/.28 的均值；
     * 磨砂 = `--card`（浅 .66 / 深 .72）；实心 = 降级分支那两个数（同源，见 GlassSpec）。
     */
    @Test
    fun `卡面垫色必须按档位给且三档单调`() {
        listOf(false, true).forEach { isDark ->
            val liquid = cardFaceAlpha(BarMaterial.LIQUID, isDark)
            val frost = cardFaceAlpha(BarMaterial.FROSTED, isDark)
            val solid = cardFaceAlpha(BarMaterial.SOLID, isDark)
            assertTrue("液态($isDark)必须比磨砂更透：$liquid vs $frost", liquid < frost)
            assertTrue("磨砂($isDark)必须比实心更透：$frost vs $solid", frost < solid)
            if (!isDark) {
                assertEquals("板 .focus-card.glass 三段白均值", 0.30, liquid.toDouble(), 0.005)
                assertEquals("板 --card rgba(255,255,255,.66)", 0.66, frost.toDouble(), 0.005)
                assertEquals(
                    "实心 = 降级分支同一个数",
                    GlassSpec.FALLBACK_ALPHA_LIGHT.toDouble(), solid.toDouble(), 0.0,
                )
            } else {
                assertEquals("板 body.dark .focus-card.glass 均值", 0.48, liquid.toDouble(), 0.005)
                assertEquals("板 body.dark --card rgba(34,37,38,.72)", 0.72, frost.toDouble(), 0.005)
                assertEquals(
                    "实心 = 降级分支同一个数",
                    GlassSpec.FALLBACK_ALPHA_DARK.toDouble(), solid.toDouble(), 0.0,
                )
            }
        }
    }

    /**
     * G-5：此刻卡这一处**必须真的吃档位**（v3 #12 业主点名那一条，钉在调用点上）。
     *
     * 这一条是整件事的落点：链路前面全对，卡上还是零变化，因为卡的垫色写的是
     * `if (isDark) 0.30f else 0.94f` 两个与档位无关的常数。所以除了 G-4 判函数，
     * 还得判**这个调用点**不许再出现那两个数。
     */
    @Test
    fun `此刻卡的垫色与字色必须由档位算`() {
        val home = source("ui/main/pages/HomeSectionPage.kt")
        val card = functionCode(home, "FocusGlassCard")
        assertTrue(
            "卡的垫色又不走 cardFaceAlpha 了（三档会在卡带上糊成同一个数）",
            "cardFaceAlpha(" in card,
        )
        assertHomeMaterialFlow(card)
        assertTrue("字色必须基于同一个实际卡面 alpha", "textBackingOver(it, colors.surfaceContainer, faceAlpha)" in card)
        assertTrue("字色必须检验真实背景的最坏情况", "cardInkWorstCase(faces)" in card)
        // 那两个与档位无关的常数不许再以"卡面垫色"的身份回来
        assertTrue(
            "旧的那对与档位无关的常数（0.30 / 0.94）回来了",
            !Regex("""0\.30f else 0\.94f|0\.94f else 0\.30f""").containsMatchIn(card),
        )
    }

    private fun assertHomeMaterialFlow(card: String) {
        assertTrue("减透明时字色和卡面必须一起退实底", Regex("""val material\s*=\s*if\s*\(motion\.glassIsTranslucent\)\s*LocalBarMaterial\.current\s*else\s*BarMaterial\.SOLID""").containsMatchIn(card))
        assertTrue("有效材质必须决定卡面 alpha", "cardFaceAlpha(material, isDark)" in card)
        assertTrue("卡面必须用计算后的同一个 alpha", "navigationSurfaceColor = colors.surfaceContainer.copy(alpha = faceAlpha)" in card)
        assertTrue("不可额外乘隐含 alpha 让字色与卡面失配", "navigationSurfaceAlphaMultiplier = 1f" in card)
        assertTrue("实底必须关闭真实 backdrop sampling", "sampleBackdrop = material != BarMaterial.SOLID" in card)
    }

    private fun assertGlasmioThemeFlow() {
        val appTheme = source("ui/AppTheme.kt")
        val host = source("ui/design/glasmio/WanzaiGlasmioTheme.kt")
        assertTrue("主题根必须包住实际页面", "WanzaiGlasmioHost(themeMode = themeMode)" in appTheme)
        assertTrue("液态必须同时服从盘中档位和运行时折射偏好", "liquidGlassEnabled = UiPrefs.glassMode == \"liquid\" && motion.refractsGlass" in host)
        assertTrue("关闭或减透明必须退实底", "reduceTransparency = UiPrefs.glassMode == \"off\" || !motion.glassIsTranslucent" in host)
        assertTrue("计算结果必须传给真正 GlasmioTheme", "appearance = appearance" in host)
        assertTrue("vendor 必须继续提供宿主传入的 appearance", "LocalGlassAppearance provides appearance" in vendor("core", "GlasmioTheme.kt"))
        val surface = vendor("glass", "GlassSurface.kt")
        assertTrue("实底必须在最终绘制器用 emptyBackdrop 而非只改文案", "if (sampleBackdrop && appearance.usesBackdrop) backdrop else emptyBackdrop()" in surface)
        assertTrue("最终折射必须服从 appearance", "refractionEnabled = appearance.usesRefraction" in surface)
    }

    private fun assertGlasmioAdapter(face: GlassSurfaceId, code: String) {
        val call = when (face) {
            GlassSurfaceId.NavBarContainer, GlassSurfaceId.NavBarSlider, GlassSurfaceId.NavBarHighlight -> "GlasmioNavigationBar("
            GlassSurfaceId.SearchButton, GlassSurfaceId.HomeFocusCard -> "GlassSurface("
            GlassSurfaceId.CommonSwitch -> "GlasmioSwitch("
            GlassSurfaceId.CommonButton -> "GlasmioButton("
            GlassSurfaceId.OverlayDialog -> "GlasmioAlertDialog("
            else -> error("Unmapped native glass adapter: $face")
        }
        assertTrue("${face.label} 必须仍调用实际 native adapter", call in code)
        when (face) {
            GlassSurfaceId.NavBarContainer, GlassSurfaceId.NavBarSlider, GlassSurfaceId.NavBarHighlight -> {
                val native = functionCode(vendor("ui", "GlassBottomBar.kt"), "GlasmioNavigationBar")
                assertTrue("native nav 必须读取统一 appearance", "LocalGlassAppearance.current" in native)
                assertTrue("nav 实底必须停止采样", "if (appearance.usesBackdrop) sourceBackdrop else emptyBackdrop()" in native)
                assertTrue("nav 磨砂必须停止折射", "if (appearance.usesRefraction) selectionPressProgressState.value else 0f" in native)
                assertTrue("nav 必须传入独立 backdrop", "backdrop = backdrop ?: LocalGlassBackdrop.current" in code)
            }
            GlassSurfaceId.HomeFocusCard -> assertHomeMaterialFlow(code)
            GlassSurfaceId.SearchButton -> assertTrue("search 必须传独立采样源", "backdrop = backdrop" in code)
            GlassSurfaceId.CommonSwitch -> {
                val native = functionCode(vendor("ui", "GlassControls.kt"), "GlasmioSwitch")
                assertTrue("switch 必须读取统一 appearance", "LocalGlassAppearance.current" in native)
                assertTrue("switch 实底不得采样", "if (appearance.usesBackdrop) thumbBackdrop else emptyBackdrop()" in native)
                assertTrue("switch 磨砂不得折射", "if (appearance.usesRefraction) animation.pressProgress else 0f" in native)
                assertTrue("减动效必须有静态可操作 fallback", "rememberMotion().canMove" in code && "toggleable(" in code && "Role.Switch" in code && "onValueChange = onCheckedChange" in code)
            }
            GlassSurfaceId.CommonButton -> {
                val native = functionCode(vendor("ui", "GlassButtons.kt"), "GlasmioButton")
                assertTrue("button 必须把采样选择交给已审计 renderer", "GlassSurface(" in native && "sampleBackdrop = sampleBackdrop" in native)
                assertTrue("减动效按钮仍须可点击", "rememberMotion().canMove" in code && "Button(" in code && "onClick = onClick" in code)
            }
            GlassSurfaceId.OverlayDialog -> {
                val native = vendor("ui", "IosAlerts.kt")
                assertTrue("alert 必须通过真实 surface 接 appearance", "GlasmioAlertSurface(" in functionCode(native, "GlasmioAlertDialog"))
                val surface = functionCode(native, "GlasmioAlertSurface")
                assertTrue("alert 实底必须停采样", "LocalGlassAppearance.current" in surface && "if (appearance.usesBackdrop)" in surface && "appearance = appearance" in surface)
                assertTrue("减动效弹层保留关闭及业务 content", "rememberMotion().canMove" in code && "Dialog(onDismissRequest = onDismissRequest)" in code && "content()" in code)
            }
            else -> error("Unmapped native glass adapter: $face")
        }
    }

    @Test
    fun `名单里每一面玻璃都必须真的读材质`() {
        assertGlasmioThemeFlow()
        GlassSurfaceId.entries.forEach { face ->
            val text = source(face.siteFile)
            val code = face.siteFunction?.let { functionCode(text, it) } ?: text
            when (face.kind) {
                GlassSurfaceKind.GLASS -> when (face.stack) {
                    GlassStack.GLASS -> assertTrue(face.label, listOf("rememberEffectiveMaterial(", "LocalBarMaterial.current", "material = ").any { it in code })
                    GlassStack.LIQUID -> assertTrue(face.label, listOf("refractsGlass", "LocalBarMaterial.current", "rememberGlassRenderMode(").any { it in code })
                    GlassStack.GLASMIO -> assertGlasmioAdapter(face, code)
                }
                GlassSurfaceKind.NOT_GLASS -> assertTrue("${face.label} 已承诺不采样，却出现真实玻璃绘制点", !drawSite.containsMatchIn(code))
            }
        }
        assertTrue("顶栏必须仍是普通渐变", "Brush.verticalGradient(" in functionCode(source("ui/design/liquid/IosChrome.kt"), "IosTopBar"))
    }

    /**
     * G-7：**没登记的玻璃面不许存在**（"新增玻璃面必须先登记"这条纪律的执行体）。
     *
     * 判的是**绘制点**，不是提了一嘴名字：注释里写 `GlassSurface(` 不算一面玻璃，
     * 所以先剥掉注释行再扫。机制本体（怎么画玻璃的那几个文件）在白名单里，白名单**只许减不许加**。
     */
    @Test
    fun `没登记的玻璃绘制点必须先进名单`() {
        val dir = File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos/ui")
        assertTrue("找不到 ui 目录：$dir", dir.isDirectory)
        val offenders = mutableListOf<String>()
        // 第 8 轮审计必修 5：只判"这个文件被登记过"拦不住**第三面**——
        // `LiquidGlassNavigationBar.kt` 有 3 处绘制点、名单原先只登记 2 面，第 3 面谁都不用管它。
        // 所以再加一条**配额**：一处绘制点 = 必须有一面登记，例外要写进 [unglassedSites] 并给理由。
        val sitesPerFile = mutableMapOf<String, Int>()
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val rel = f.absolutePath.replace('\\', '/').substringAfter("com/muyao/wanzaihyperos/")
            // ⚠️ 只有**机制文件**跳过；登记过的文件也必须进配额表 ——
            //   第一版把 `allowed`（登记过的 ∪ 机制）整个跳过，于是"3 处绘制点只登记 2 面"
            //   这条永远不会被算到（我拿变异验收试出来的：拿掉 NavBarHighlight 竟然还是绿）。
            if (rel in glassMechanismFiles) return@forEach
            val code = SourceScan.stripComments(f.readText(Charsets.UTF_8))
            val n = drawSite.findAll(code).sumOf { if (it.value.startsWith("GlasmioNavigationBar")) 3 else 1 }
            if (n == 0) return@forEach
            sitesPerFile[rel] = n
        }
        sitesPerFile.forEach { (rel, n) ->
            val registered = GlassSurfaceId.entries.count { it.siteFile == rel && it.kind == GlassSurfaceKind.GLASS }
            val legacy = legacyGlassSites[rel] ?: 0
            val exempt = unglassedSites[rel] ?: 0
            if (registered + legacy < n - exempt) {
                offenders += "$rel：$n 处绘制点只登记了 $registered 面（豁免 $exempt）" +
                    " ⇒ 有一面玻璃没有 id、三档管不到它"
            }
        }
        assertTrue(
            "这些文件的玻璃绘制点没在 `GlassSurfaceId` 登记齐（先登记再加面；" +
                "名单就是为了让「三档覆盖哪些面」这句话可被证伪）：$offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * G-8：外观页那行范围说明**必须从名单拼**，且必须写清"开关和返回按钮始终是实心"。
     *
     * 为什么钉这一条：#12 的原话是"覆盖全应用"，而业主 13:2x 已经把范围签成"只浮动面"。
     * 文案一旦手写清单，它就会比代码慢一轮 —— 那又是一枚"界面说什么、画面做什么"对不上的假说法。
     */
    @Test
    fun `外观页的范围文案必须由名单生成`() {
        val page = source("ui/main/pages/AppearancePage.kt")
        // 只判**代码**：注释里提一句"覆盖全应用"是在记录踩过的坑，不是又犯一次
        //（第 7 轮审计点名过同一类假红：写法扫描必须先把注释剥掉，否则逼人不敢写注释）。
        val code = page.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertTrue(
            "外观页那行「作用于…」又不从 `GlassSurfaceId.entries` 拼了 ⇒ 名单一改文案就漂",
            "GlassSurfaceId.entries" in code && ".joinToString(" in code,
        )
        assertTrue(
            "文案必须**只**列 `GLASS` 那几面：把 `NOT_GLASS` 的面（外观预览卡）写进「作用于」，" +
                "就是替它承诺一个它根本没有的效果",
            "GlassSurfaceKind.GLASS" in code,
        )
        assertTrue(
            "范围文案必须准确描述实际开关状态、正文不采样和顶栏渐变，不能重复旧的实心开关承诺",
            Regex(""""[^"]*开关保留明确的开关状态，返回按钮和正文卡片不采样页面；顶栏使用渐变遮罩。""").containsMatchIn(code),
        )
        // 签字边界里明确**不做**的两处，不许被这句文案重新承诺进去
        assertTrue("界面文案里不许再出现「覆盖全应用」这类兑现不了的口径", "覆盖全应用" !in code)
    }

    @Test
    fun `native appearance 三档真值与减透明优先级必须实际不同`() {
        listOf("liquid", "frost", "off").forEach { mode ->
            listOf(false, true).forEach { translucent ->
                listOf(false, true).forEach { refracts ->
                    val appearance = GlassAppearance(
                        liquidGlassEnabled = mode == "liquid" && refracts,
                        reduceTransparency = mode == "off" || !translucent,
                        adaptiveColorScheme = false,
                    )
                    assertEquals("$mode/$translucent 必须控制采样", mode != "off" && translucent, appearance.usesBackdrop)
                    assertEquals("$mode/$translucent/$refracts 必须控制折射", mode == "liquid" && translucent && refracts, appearance.usesRefraction)
                }
            }
        }
    }

    @Test
    fun `native 与主壳采样源必须分离且实底不录制`() {
        val host = source("ui/design/glasmio/WanzaiGlasmioTheme.kt")
        assertTrue("公共控件必须采 uniform 色而非内容自身", "sampledColor = colors.groupedBackground" in host && "overlayContent = { content() }" in host)
        assertTrue("公共控件不得偷偷采整个主页面", "sampledContent" !in host && "layerBackdrop(" !in host)
        val nativeHost = functionCode(vendor("glass", "GlassBackdrop.kt"), "BackdropHost")
        assertTrue("vendor 实底必须停止 recording", "val recordBackdrop = LocalGlassAppearance.current.usesBackdrop" in nativeHost && ".then(if (recordBackdrop) Modifier.layerBackdrop(backdrop)" in nativeHost && "else Modifier)" in nativeHost)
        assertTrue("实际 source 和 overlay 必须仍是兄弟层", "sampledContent(backdrop)" in nativeHost && "overlayContent(backdrop)" in nativeHost)
        val shell = source("ui/main/WanzaiMainShell.kt")
        assertTrue("主壳必须只在透明且 recording 开启时记录", Regex("""if\s*\(motion\.glassIsTranslucent\s*&&\s*!recorderOff\)\s*Modifier\.layerBackdrop\(pageBackdrop\)\.trackBackdropPosition\(pageBackdrop\)\s*else Modifier""").containsMatchIn(shell))
        assertTrue("页面底色必须位于 layer recording 内，而不是透明内容外侧", Regex("""Modifier\.layerBackdrop\(pageBackdrop\)\.trackBackdropPosition\(pageBackdrop\)\s*else Modifier\s*\)\s*\.containerBackground\(isDark\)""").containsMatchIn(shell))
        assertTrue("根页面渐变必须提供独立 opaque 兜底", "val pageBaseBackdrop = rememberCanvasBackdrop { drawRect(pageBrush) }" in shell)
        assertEquals("nav/search 必须先画根底色再画当前页面，不能反序或丢失兜底", 2, Regex("""rememberCombinedBackdrop\(pageBaseBackdrop, it\)""").findAll(shell).count())
        val home = source("ui/main/pages/HomeSectionPage.kt")
        assertTrue("Home sampled source 必须先铺 full-size opaque 颜色，不能只录透明天空", Regex("""sampledContent\s*=\s*\{\s*Box\(Modifier\.fillMaxSize\(\)\.background\(colors\.background\)\)""").containsMatchIn(home))
        assertTrue("Home 业务内容必须仍留在 source 的兄弟 overlay 层", "overlayContent = { skyBackdrop ->" in home)
        assertTrue("必须按当前页面切换 source", "if (s == section) activeBackdrop.value = pageBackdrop" in shell)
        assertTrue("销毁页面必须释放 source", "if (activeBackdrop.value === pageBackdrop) activeBackdrop.value = null" in shell)
        assertEquals("nav/search 两处必须共同受透明录制闸门约束", 2, Regex("""activeBackdrop\.value\.takeIf\s*\{\s*motion\.glassIsTranslucent\s*&&\s*!recorderOff\s*\}""").findAll(shell).count())
    }

    @Test
    fun `保留 legacy 绘制点须审计但不得误报为当前主界面`() {
        legacyGlassSites.forEach { (path, quota) ->
            val code = source(path)
            assertEquals("legacy 配额不可因新增采样而静默漂移：$path", quota + (unglassedSites[path] ?: 0), drawSite.findAll(code).count())
            assertTrue("legacy 必须仍接材质：$path", "refractsGlass" in code || "rememberGlassRenderMode(" in code)
        }
        assertEquals("保留 legacy 范围不可任意扩张", setOf("ui/design/liquid/LiquidGlassNavigationBar.kt", "ui/design/liquid/GlassControls.kt"), legacyGlassSites.keys)
        assertEquals("机制范围不得借迁移扩大", setOf("ui/design/glass/GlassSurface.kt", "ui/design/glass/BarOptics.kt", "ui/design/liquid/CombinedBackdrop.kt", "ui/design/liquid/PageBackdrop.kt", "ui/design/liquid/IosChrome.kt"), glassMechanismFiles)
        val shell = source("ui/main/WanzaiMainShell.kt")
        assertTrue("主壳必须仅调用新 nav/search", "GlassNavBar(" in shell && "GlasmioSearchButton(" in shell && "LiquidGlassNavigationBar(" !in shell && "GlassSearchButton(" !in shell)
    }

    @Test
    fun `未读未知零不画角标且超过九十九真实显示99加`() {
        val root = source("WanzaiRoot.kt")
        assertTrue("未知数不得伪造为已知统计", "(unreadState.value as? CountValue.Known)?.value" in root && "unreadCount = unreadCount ?: 0" in root)
        val shell = source("ui/main/WanzaiMainShell.kt")
        assertTrue("仅消息tab拿真实未读", "badge = if (it == MainSection.Notify) unreadCount else 0" in shell)
        assertTrue("native adapter 不得丢弃或伪造 badge", "badge = item.badge.coerceAtLeast(0)" in source("ui/design/liquid/GlassBar.kt"))
        val native = vendor("ui", "GlassBottomBar.kt")
        assertTrue("零/未知必须不画角标", "if (showBadge && item.badge > 0)" in native && "hasSelection && selectedItem.badge > 0" in native)
        assertTrue("大数必须真实显示 99+ 而非裁成99", "if (item.badge > 99) \"99+\" else item.badge.toString()" in native)
        assertTrue("读屏必须仍承载真实未读数", "contentDescription = item.contentDescription +" in native && " 条未读" in native)
        val tabContent = native.substringAfter("private fun <T> NavigationTabContent(")
        val glyph = tabContent.substringAfter("val glyphModifier =").substringBefore("if (showBadge && item.badge > 0)")
        assertTrue("选中滤色只作用于glyph，不能把红底白字角标染成蓝色", "colorFilter = selectedColorFilter?.value" in glyph && "Box(glyphModifier" in glyph)
        val badge = tabContent.substringAfter("if (showBadge && item.badge > 0)").substringBefore("text = item.label")
        assertTrue("角标必须独立于滤色glyph层且保持红底白字", "colorFilter" !in badge && "Color.White" in badge && "Color(0xFFD83B47)" in badge)
        assertTrue("选中角标必须在native lens后单独叠加，不能参与滤色源", native.indexOf("badgeOnly = true") > native.indexOf(".then(indicatorBackdropModifier)") && "showBadge = badgeOnly" in native && "if (badgeOnly) Modifier.alpha(0f)" in tabContent)
    }

    /** G-3（第 2 轮 P1#5 的新假开关）：两条键必须一起写，恢复默认不许漏一档。 */
    @Test
    fun `玻璃档位的两条键必须同批落盘`() {
        val prefs = source("data/UiPrefs.kt")
        val funSep = "\n    fun "
        val save = prefs.substringAfter("fun saveGlassMode").substringBefore(funSep)
        assertTrue("saveGlassMode 必须写 KEY_GLASS_MODE", "KEY_GLASS_MODE" in save)
        assertTrue("saveGlassMode 必须同时写 KEY_REDUCE_TRANSPARENCY（两键是同一决定的两面）",
            "KEY_REDUCE_TRANSPARENCY" in save)
        val reset = prefs.substringAfter("fun resetAppearance").substringBefore(funSep)
        assertTrue(
            "resetAppearance 不清 KEY_GLASS_MODE ⇒ 「选关闭→恢复默认→杀进程重进」后盘里还是 off，" +
                "分段控件显示关闭、画面走液态",
            "KEY_GLASS_MODE" in reset,
        )
        // 调用点还得把运行期那一份推上去（偏好层不 import UI 层，所以这条只判调用点存在）
        val page = source("ui/main/pages/AppearancePage.kt")
        val block = page.substringAfter("UiPrefs.resetAppearance()").take(400)
        assertTrue("恢复默认后没推 MotionPrefs ⇒ 要等下次冷启动才生效（中间就是界面与画面脱钩）",
            "MotionPrefs.setReduceTransparency" in block && "MotionPrefs.setRefractsGlass" in block)
    }
}
