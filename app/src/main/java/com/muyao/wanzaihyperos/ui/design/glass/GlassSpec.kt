package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **玻璃的光學參數**。
 *
 * ## 這些欄位怎麼對應到 shader
 *
 * | 欄位 | 進 [LiquidGlassShader] 的哪個 uniform |
 * |---|---|
 * | [lensHeightDp] | `refractionHeight` —— 從邊界往內多遠開始折射 |
 * | [lensAmountDp] | `refractionAmount`（取負）—— 邊緣最多偏移多少 |
 * | [chromaticAberration] | `chromaticAberration` —— R/G/B 三通道偏移量 |
 * | [blurDp] | backdrop 的 `blur()` 半徑 |
 * | [surfaceAlpha] | `onDrawSurface` 畫的那層底色 alpha |
 *
 * 換言之：**改這裡的數字，玻璃的物理行為就變**。
 * 業主要求的「玻璃一切參數可調節」由此落地，不是裝飾性配置。
 *
 * ## ★ 之前踩過的坑（別再犯）
 *
 * 1. 把 [surfaceAlpha] 拉到 0.92 想讓底欄「看起來白」——
 *    那層白漆把折射整個蓋住。業主：「折射都沒了」。
 *    表面色**必須透**，白感靠模糊後的背景 + 高光，不是靠堆白漆。
 *
 * 2. 把 [blurDp] 設到 34dp —— 背後什麼都糊沒了，看起來像磨砂板不是玻璃。
 *    真玻璃只糊一點（iOS 大約 2~12dp）。
 */
@Immutable
data class GlassSpec(
    // ── 折射（AGSL 的兩個核心 uniform）──────────────────────────
    /**
     * 鏡片高度（dp）：從邊界往內多遠開始折射。
     *
     * 參考 app 逆向：`LensHeightMin = 14`、`LensHeightMax = 28`。
     * 太小折射帶很窄（看不出），太大整個玻璃都在扭曲（像水杯）。
     */
    val lensHeightDp: Dp = 20.dp,

    /**
     * 折射量（dp）：邊緣最多偏移多少像素。
     *
     * 參考 app 逆向：`LensAmountMax = 24`。
     * 這是「扭曲有多明顯」的直接控制 —— 想更強就調大這個。
     */
    val lensAmountDp: Dp = 18.dp,

    /**
     * 色散強度（0~1）。
     *
     * shader 會沿折射方向對 R/G/B 做**對稱偏移**：
     * 紅往一邊、藍往另一邊、綠不動。這是物理上的色散。
     * 0 = 無色散；太大會在邊緣出現明顯的彩虹邊。
     */
    val chromaticAberration: Float = 0.55f,

    // ── 模糊 ──────────────────────────────────────────────────────
    /**
     * 背後模糊半徑（dp）。
     *
     * ★ 真玻璃**只糊一點**：iOS 的 Liquid Glass 大約 2~12dp。
     * 糊到 30dp 就看不出背後是什麼 —— 那不是玻璃，是磨砂板。
     */
    val blurDp: Dp = 7.dp,

    /** 磨砂材質用的模糊（更重，但沒有折射）。 */
    val frostBlurDp: Dp = 18.dp,

    // ── 表面 ──────────────────────────────────────────────────────
    /**
     * 表面底色 alpha。**要透**（見類註解的坑）。
     *
     * 参考 app 逆向：`SurfaceAlphaLight = 0.34`、`SurfaceAlphaDark = 0.16`。
     */
    val surfaceAlpha: Float = 0.22f,

    /** 表面底色。玻璃用略帶冷調的白，比純白有質感。 */
    val surfaceTint: Color = Color(0xFFF4F7FF),

    /** 圓角。 */
    val cornerRadius: Dp = 24.dp,
) {

    /** 目前模式下要用的模糊半徑。 */
    fun blurFor(material: BarMaterial): Dp = when (material) {
        BarMaterial.SOLID -> 0.dp
        BarMaterial.FROSTED -> frostBlurDp
        BarMaterial.LIQUID -> blurDp
    }

    /** 是否走真玻璃。SOLID 不走。 */
    fun usesGlass(material: BarMaterial): Boolean = material != BarMaterial.SOLID

    companion object {
        /** **淺色定稿**。 */
        val LIGHT = GlassSpec(
            lensHeightDp = 17.dp,
            lensAmountDp = 9.dp,
            chromaticAberration = 0.55f,
            blurDp = 7.dp,
            frostBlurDp = 18.dp,
            surfaceAlpha = 0.22f,
            surfaceTint = Color(0xFFF4F7FF),
            cornerRadius = 24.dp,
        )

        /** **深色定稿**：折射更強（深色背景下玻璃更明顯）。 */
        val DARK = GlassSpec(
            lensHeightDp = 22.dp,
            lensAmountDp = 22.dp,
            chromaticAberration = 0.65f,
            blurDp = 9.dp,
            frostBlurDp = 22.dp,
            surfaceAlpha = 0.16f,
            surfaceTint = Color(0xFFF2F5FC),
            cornerRadius = 24.dp,
        )

        /** **降級**：不支援時的近似（實心 + 無折射）。 */
        val FALLBACK = GlassSpec(
            lensHeightDp = 0.dp,
            lensAmountDp = 0.dp,
            chromaticAberration = 0f,
            blurDp = 0.dp,
            frostBlurDp = 0.dp,
            surfaceAlpha = 0.94f,
            surfaceTint = Color(0xFFF4F7FF),
            cornerRadius = 24.dp,
        )

        fun forDark(isDark: Boolean): GlassSpec = if (isDark) DARK else LIGHT
    }
}

/**
 * 底欄材質。
 *
 * 三態對應 iOS Liquid Glass 的三種風格：
 * - [SOLID] 純色（不透明，最省電）
 * - [FROSTED] 磨砂（只有模糊，沒有折射 —— 那是「磨砂玻璃」不是「液態玻璃」）
 * - [LIQUID] 液態（折射 + 色散 + 高光）
 */
enum class BarMaterial(val label: String) {
    SOLID("純色"),
    FROSTED("磨砂"),
    LIQUID("液態"),
}

/**
 * 調參用的可寫狀態（給調參頁綁 slider 用）。
 *
 * 欄位與 [GlassSpec] 一一對應，所以「玻璃一切參數可調節」在 UI 上是可實現的。
 */
class GlassSpecTuning(initial: GlassSpec) {
    var lensHeightDp by mutableStateOf(initial.lensHeightDp)
    var lensAmountDp by mutableStateOf(initial.lensAmountDp)
    var chromaticAberration by mutableStateOf(initial.chromaticAberration)
    var blurDp by mutableStateOf(initial.blurDp)
    var surfaceAlpha by mutableStateOf(initial.surfaceAlpha)

    fun current(base: GlassSpec = GlassSpec.LIGHT): GlassSpec = base.copy(
        lensHeightDp = lensHeightDp,
        lensAmountDp = lensAmountDp,
        chromaticAberration = chromaticAberration,
        blurDp = blurDp,
        surfaceAlpha = surfaceAlpha,
    )

    fun resetTo(spec: GlassSpec) {
        lensHeightDp = spec.lensHeightDp
        lensAmountDp = spec.lensAmountDp
        chromaticAberration = spec.chromaticAberration
        blurDp = spec.blurDp
        surfaceAlpha = spec.surfaceAlpha
    }
}

/** 目前生效的 [GlassSpec]。 */
val LocalGlassSpec = androidx.compose.runtime.staticCompositionLocalOf { GlassSpec.LIGHT }

/** 目前的底欄材質。 */
val LocalBarMaterial = androidx.compose.runtime.staticCompositionLocalOf { BarMaterial.LIQUID }