package com.muyao.wanzaihyperos.ui.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B4-02 羽化带**起点色**的守卫（2026-10-25 真机实测之后补的）。
 *
 * 缺陷外观（真机 SYNTHETIC0000001，帧 `verify/b4-02/r3_builtin_8da225db_a.png`）：
 *   y=489 (156,127,115) → y=490 (184,140,127)，**单行 +16.05 灰阶**，四条 x 带（2/300/600/1200）
 *   读数逐行完全相同 ⇒ 整条水平接缝，不是窄带边缘现象。
 * 根因：羽化带第一行的颜色取的是 `look.colorAt()` 的**原始**天空色，而它正上方那片天空
 *   早已被「柔化雾」（soft/100×0.30）与「暗度」（dim/80×0.62，仅亮字档）压过；
 *   `featherStops(transparent = true)` 在 t=0 处 alpha 是 1 ⇒ 把压过的那层原样盖回去。
 * 修法：起点色 = 原色 → 叠雾 → 叠暗（`dimmedSkyAt`），与绘制顺序一致。
 *
 * 为什么这条守的是**数值**而不是"源码里有没有写 dimmedSkyAt"：
 * 只查字面量的话，把 `over()` 换成 Compose 的 `lerp()`（走 Oklab，实测偏 2~3/255）
 * 仍会全绿，而这条接缝的判据是 ≤0.5 灰阶。所以期望值在这里**独立重算**一遍 sRGB source-over
 * （不看生产里的 `over()`），只要生产混色换了空间就会红。
 */
class HomeSkyFadeStartTest {

    /** AppTheme.kt:105 —— 亮档 `surface`，HomeSky 里叫 veil（雾色）。 */
    private val veil = Color(0xFFFBFAF7)

    /** 与 skyFadeStartY / skyBottomY 同一个比例：fadeStart = skyBottom − 0.24h ⇒ f = 0.70。 */
    private val f = 0.70f

    private fun src(): String {
        val file = File("src/main/java/com/muyao/wanzaihyperos/ui/home/HomeSky.kt")
        assertTrue("找不到 ${file.absolutePath}", file.isFile)
        return file.readText(Charsets.UTF_8)
    }

    private fun code(): String = src()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    /** 测试自己算一遍 sRGB source-over —— 刻意不复用生产代码。 */
    private fun mix(dst: Color, src: Color, a: Float): Color = Color(
        red = dst.red * (1f - a) + src.red * a,
        green = dst.green * (1f - a) + src.green * a,
        blue = dst.blue * (1f - a) + src.blue * a,
        alpha = 1f,
    )

    private fun assertClose(what: String, want: Color, got: Color, tol: Float = 1.5f / 255f) {
        val d = maxOf(
            Math.abs(want.red - got.red),
            Math.abs(want.green - got.green),
            Math.abs(want.blue - got.blue),
        ) * 255f
        assertTrue("$what：期望 ${hex(want)} 实测 ${hex(got)}，单通道差 ${"%.2f".format(d)}/255 > ${"%.2f".format(tol * 255f)}", d <= tol * 255f)
    }

    private fun hex(c: Color): String = String.format(
        "#%02X%02X%02X",
        Math.round(c.red * 255f), Math.round(c.green * 255f), Math.round(c.blue * 255f),
    )

    @Test
    fun `柔化雾与暗度都必须折进羽化带起点色`() {
        val look = skyLookOf(SkyPreset.AUTO, 13)
        val raw = look.colorAt(f)
        val got = dimmedSkyAt(look, f, veil, soft = 26, dim = 24, lightIcons = true)

        val want = mix(mix(raw, veil, 0.26f * 0.30f), Color.Black, (24f / 80f) * 0.62f)
        assertClose("起点色必须 = 原色 → 叠雾 → 叠暗（sRGB source-over）", want, got)

        // 反证：如果起点色退回"原始天空色"，这条会红（这就是真机上 +16 灰阶的那一档）。
        val drop = (Math.abs(raw.red - got.red) + Math.abs(raw.green - got.green) + Math.abs(raw.blue - got.blue)) / 3f * 255f
        assertTrue("起点色与原始天空色几乎一样（平均差 ${"%.2f".format(drop)}/255）—— 雾与暗没折进去", drop >= 5f)
    }

    @Test
    fun `暗度只对亮字档生效`() {
        val look = skyLookOf(SkyPreset.AUTO, 13)
        val light = dimmedSkyAt(look, f, veil, soft = 26, dim = 40, lightIcons = true)
        val dark = dimmedSkyAt(look, f, veil, soft = 26, dim = 40, lightIcons = false)
        val darkNoDim = dimmedSkyAt(look, f, veil, soft = 26, dim = 0, lightIcons = false)
        assertClose("深色字档必须忽略 dim（天空自己已经是深色）", darkNoDim, dark, 0f)
        assertTrue("亮字档的 dim 没有把天空压暗", light.red < dark.red - 0.02f)
    }

    @Test
    fun `雾与暗都为 0 时起点色就是原色`() {
        val look = skyLookOf(SkyPreset.DUSK, 17)
        assertClose("soft=0 且 dim=0 时不许改动颜色", look.colorAt(f),
            dimmedSkyAt(look, f, veil, soft = 0, dim = 0, lightIcons = true), 0f)
    }

    @Test
    fun `起点色不许再读原始 colorAt`() {
        val text = code()
        assertTrue(
            "羽化带起点又直接读原始天空色了 —— 那就是真机 y=490 那条 +16 灰阶的全宽硬边",
            "look.colorAt((fadeStart" !in text,
        )
        assertTrue(
            "羽化带起点没有走 dimmedSkyAt（雾与暗必须折进去）",
            "val skyAtFadeStart = dimmedSkyAt(" in text,
        )
    }
}
