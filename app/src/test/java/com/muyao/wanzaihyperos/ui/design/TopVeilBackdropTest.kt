package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 顶栏纱必须按**这一屏真正铺出来的底色**挑深浅，而不是按主题令牌
 * （工单第 21 轮；台账 §二十六.2「纱与页面打架」）。
 *
 * 三条：
 *  ① 浅底 → 浅纱、深底 → 深纱；
 *  ② 单调性（底越暗，挑出的纱越暗）；
 *  ③ **跨文件**：`TopVeil.kt` 剥掉注释后不得再出现 `MiuixTheme.colorScheme.background`
 *     —— "不许从主题取色"只有这条证得死（只测 ①②，把实现改回主题令牌照样绿）。
 *
 * 变异：把 `veilColorFor(backdrop)` 改回按 `MiuixTheme.colorScheme.background` 取 ⇒ ③ 红
 * （日志 `verify/b4-14/mutation_veil_theme.log`，`--rerun-tasks` 产出）。
 */
class TopVeilBackdropTest {

    private fun veil(backdrop: Color) = AppColors.veilColorFor(backdrop)

    @Test
    fun `浅底挑浅纱 深底挑深纱`() {
        val light = veil(Color(0xFFF8F7F4))   // 页面纸色
        val dark = veil(Color(0xFF101213))    // 深色页底
        assertTrue("浅底应挑浅纱（亮度 ${light.luminance()}）", light.luminance() > 0.5f)
        assertTrue("深底应挑深纱（亮度 ${dark.luminance()}）", dark.luminance() < 0.1f)
    }

    /**
     * 单调性：它是个**阶跃函数**（阈值 0.5），所以只允许在跨越阈值那一档"变亮一次"，
     * 同一侧里底越暗、纱只能同样或更暗。上一版写成"整串严格不升"⇒ 在阶跃处必红，
     * 那是把判据写错了，不是实现错。
     */
    @Test
    fun `同一侧里单调 只在阈值处阶跃一次`() {
        val samples = (0..20).map { i -> Color(i / 20f, i / 20f, i / 20f) }
        val pairs = samples.zipWithNext()
        var jumps = 0
        pairs.forEachIndexed { i, (a, b) ->
            val la = a.luminance()
            val lb = b.luminance()
            val va = veil(a).luminance()
            val vb = veil(b).luminance()
            if ((la < 0.5f) == (lb < 0.5f)) {
                assertTrue("同一侧里亮度不升：$la→$lb 得到 $va→$vb", vb <= va + 1e-6f)
            } else {
                jumps++
                assertTrue("越过 0.5 时纱必须由暗转亮：$va→$vb", vb > va)
            }
        }
        assertTrue("整串 0..1 只应跨一次阈值，实际 $jumps 次", jumps == 1)
        // 阈值两侧各取一个点（注意 `luminance()` 是**线性**亮度：
        // sRGB 0.51 的线性亮度只有 0.223，仍算"暗底" —— 上一版按 sRGB 取点，判据写错了）
        assertTrue("sRGB 0.80（线性 %.2f）应是浅底".format(Color(0.8f, 0.8f, 0.8f).luminance()),
            veil(Color(0.8f, 0.8f, 0.8f)).luminance() > 0.5f)
        assertTrue("sRGB 0.60（线性 %.2f）应是暗底".format(Color(0.6f, 0.6f, 0.6f).luminance()),
            veil(Color(0.6f, 0.6f, 0.6f)).luminance() < 0.5f)
    }

    @Test
    fun `TopVeil 不许再从主题令牌取色`() {
        val f = File("src/main/java/com/muyao/wanzaihyperos/ui/design/TopVeil.kt")
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        val code = Regex("""/\*[\s\S]*?\*/""").replace(f.readText(Charsets.UTF_8), "").lines()
            .joinToString("\n") { it.substringBefore("//") }
        assertFalse(
            "TopVeil 又在读 `MiuixTheme.colorScheme.background` —— 那是主题令牌，不是这一屏的像素真相",
            "colorScheme.background" in code,
        )
        assertFalse("TopVeil 又在调 `veilColor(`（旧 API，按主题布尔取色）", "veilColor(" in code)
        assertTrue("TopVeil 必须接收 backdrop 参数", "backdrop: Color" in code)
    }

    /**
     * 首页那条纱在 `liquid/IosChrome.kt` 的 `IosTopBar` 里（`TopVeil` 只在二级页的
     * `ThemedScaffold` 用）。它也必须按**实际底色**挑，不能按主题布尔。
     *
     * 变异：把 `veilBase` 改回 `surface`（主题令牌）⇒ 本条红
     * （`verify/b4-14/mutation_iveil_theme.log`）。
     */
    @Test
    fun `首页顶栏纱也必须按实际底色`() {
        val f = File("src/main/java/com/muyao/wanzaihyperos/ui/design/liquid/IosChrome.kt")
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        val code = Regex("""/\*[\s\S]*?\*/""").replace(f.readText(Charsets.UTF_8), "").lines()
            .joinToString("\n") { it.substringBefore("//") }
        assertTrue("IosTopBar 缺必填 backdropColor 参数", "backdropColor: Color" in code)
        assertTrue("纱的基色不是 veilColorFor(backdropColor)", "veilColorFor(backdropColor)" in code)
        val veil = code.substringAfter("val veilBase =").substringBefore("\n")
        assertTrue("纱基色那一行还在读主题：$veil", "veilColorFor(backdropColor)" in veil)
        // 渐变里四条 stop 必须都吃 veilBase（不再吃 surface）
        val grad = code.substringAfter("0f to veilBase.copy(").substringBefore(")")
        assertTrue("渐变第一站不是 veilBase", grad.isNotEmpty())
        assertFalse("渐变里仍有 `surface.copy(alpha = …)`（主题令牌）", "0f to surface.copy(" in code)
    }
}
