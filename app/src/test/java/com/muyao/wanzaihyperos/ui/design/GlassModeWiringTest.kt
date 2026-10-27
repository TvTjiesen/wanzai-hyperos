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
            .readText(Charsets.UTF_8)

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
            val lines = f.readText(Charsets.UTF_8).lines()
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
            val text = f.readText(Charsets.UTF_8)
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
        val card = home.substringAfter("private fun FocusGlassCard(")
        assertTrue(
            "卡的垫色又不走 cardFaceAlpha 了（三档会在卡带上糊成同一个数）",
            "cardFaceAlpha(" in card,
        )
        assertTrue(
            "字色/垫色必须按**此刻真正渲染的那条路径**判：读 rememberEffectiveMaterial，" +
                "不是读 LocalBarMaterial.current（后者会说谎：降级时画面是实心纸）",
            "rememberEffectiveMaterial(backdrop)" in card,
        )
        assertTrue(
            "`surfaceAlpha` 必须吃算出来的那个数（写第二份常数就是字色与实际表面对不上的来源）",
            "surfaceAlpha = faceAlpha," in card,
        )
        assertTrue("这张卡的 GlassSurface 必须显式带 material", "material = material," in card)
        // 那两个与档位无关的常数不许再以"卡面垫色"的身份回来
        assertTrue(
            "旧的那对与档位无关的常数（0.30 / 0.94）回来了",
            !Regex("""0\.30f else 0\.94f|0\.94f else 0\.30f""").containsMatchIn(card),
        )
    }

    /**
     * G-6（业主 13:2x 签字的口径要求的"单一真相名单"）：**名单里每一面都必须真的读玻璃材质**。
     *
     * 两条栈读的东西不一样，这是 #12 反复"接了却无效"的结构性原因：
     * `glass` 栈读 `LocalBarMaterial`（档位）/ `rememberEffectiveMaterial`（此刻真正会走哪条路），
     * `liquid` 栈读 `MotionPrefs.refractsGlass`（液态↔磨砂）与 `GlassTuning` 的旋钮。
     * 一面登记成玻璃、代码里却一个字都不读这两样 ⇒ 它就是第 7 轮那张卡的翻版（"接线正确、画面不动"）。
     */
    @Test
    fun `名单里每一面玻璃都必须真的读材质`() {
        val root = File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos")
        val glassTokens = listOf("rememberEffectiveMaterial(", "LocalBarMaterial.current", "material = ")
        val liquidTokens = listOf("refractsGlass", "LocalBarMaterial.current", "rememberGlassRenderMode(")
        GlassSurfaceId.entries.forEach { face ->
            val f = File(root, face.siteFile)
            assertTrue("${face.label} 登记在 ${face.siteFile}，可这个文件不存在", f.isFile)
            val text = f.readText(Charsets.UTF_8)
            when (face.kind) {
                GlassSurfaceKind.GLASS -> {
                    val need = when (face.stack) {
                        GlassStack.GLASS -> glassTokens
                        GlassStack.LIQUID -> liquidTokens
                    }
                    assertTrue(
                        "${face.label}（${face.siteFile}，${face.stack} 栈）没读玻璃材质 ⇒ 它是一面假玻璃：" +
                            "$need 一个都没有",
                        need.any { text.contains(it) },
                    )
                }
                // 名单里写「不是玻璃面」也要能被证：这个文件里不许有玻璃绘制点。
                // 否则它就是"标了不做、其实偷偷做了"（或者反过来：真做了却标成不做，两种都是谎）。
                GlassSurfaceKind.NOT_GLASS -> {
                    val code = text.lines().filterNot {
                        val t = it.trim()
                        t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                    }.joinToString("\n")
                    assertTrue(
                        "${face.label} 在名单里标着「不是玻璃面」，可 ${face.siteFile} 里出现了玻璃绘制点 —— " +
                            "要么真做（改 kind 并接材质），要么把这个面拆掉",
                        !Regex("""(?<![.\w])GlassSurface\(""").containsMatchIn(code) &&
                            !Regex("""\.drawBackdrop\(""").containsMatchIn(code),
                    )
                }
            }
        }
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
            val code = f.readLines().filterNot {
                val t = it.trim()
                t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            }.joinToString("\n")
            val n = Regex("""(?<![.\w])GlassSurface\(""").findAll(code).count() +
                Regex("""\.drawBackdrop\(""").findAll(code).count()
            if (n == 0) return@forEach
            sitesPerFile[rel] = n
        }
        sitesPerFile.forEach { (rel, n) ->
            val registered = GlassSurfaceId.entries.count { it.siteFile == rel }
            val exempt = unglassedSites[rel] ?: 0
            if (registered < n - exempt) {
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
            "范围文案必须写清开关与返回按钮不做玻璃，而且得是**界面真会说的那句**（在字符串里，不在注释里）",
            Regex(""""[^"]*开关和返回按钮始终是实心""").containsMatchIn(code),
        )
        // 签字边界里明确**不做**的两处，不许被这句文案重新承诺进去
        assertTrue("界面文案里不许再出现「覆盖全应用」这类兑现不了的口径", "覆盖全应用" !in code)
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
