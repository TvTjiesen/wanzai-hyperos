package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **玻璃的光學參數**（對應 Haze `GlassOptics` 的每一項）。
 *
 * ## 為什麼是這一組欄位
 *
 * 它們**一一對應** `dev.chrisbanes.haze.glass.GlassOptics` 的建構參數：
 * ```
 * GlassOptics(
 *   refractionStrength, refractionHeightFraction, refractionDisplacement,
 *   depth, blurRadius, progressive, refractionFoldStrength,
 *   refractionDetailIntensity, refractionProfile,
 * )
 * ```
 * 換言之：**這個 data class 就是 Haze 那一組參數的可調入口**，
 * 業主要求的「玻璃一切參數可調節」由此落地 —— 改這裡，玻璃就變。
 *
 * ## 各參數的物理意義
 *
 * | 參數 | 意義 | 調大的後果 |
 * |---|---|---|
 * | [refractionStrength] | 折射強度 | 邊緣扭曲更明顯；太大變哈哈鏡 |
 * | [refractionHeightFraction] | 折射環帶佔整體的比例 | 小 = 只在真正的邊緣折射；大 = 整片都扭 |
 * | [refractionDisplacement] | 邊緣位移量 | 邊緣「吸進去」的深度 |
 * | [depth] | 玻璃厚度 | 有厚度感（像一塊實體）而非一層膜 |
 * | [blurRadius] | 背後模糊 | 太大就看不出背後是什麼（= 磨砂板，不是玻璃） |
 * | [refractionDetailIntensity] | 細節/色散強度 | 邊緣的彩虹與細節紋理 |
 * | [specularIntensity] | 高光（鏡面反射）強度 | 邊緣的「玻璃感」 |
 * | [surfaceAlpha] | 表面底色透明度 | **要透**；太實會把折射蓋死 |
 *
 * ## ★ 之前踩過的坑（別再犯）
 *
 * 我曾把 [surfaceAlpha] 拉到 0.92 想讓底欄「看起來白」，
 * 結果那一層白漆把折射整個蓋住 —— 業主：「折射都沒了」。
 * 表面色**必須透**，白感靠模糊後的背景 + 高光，不是靠堆白漆。
 */
@Immutable
data class GlassSpec(
    // ── 折射 ──────────────────────────────────────────────────────
    /** 折射強度。0 = 不折射（純模糊）。 */
    val refractionStrength: Float = 0.55f,
    /**
     * 折射環帶佔整體的比例（0~1）。
     *
     * ★ 這是「通透」的關鍵：**小**（如 0.18）表示只有真正的邊緣在折射，
     * 中心區域原樣透出 —— 那才是玻璃；大（如 0.6）會整片一起扭，變成水杯。
     */
    val refractionHeightFraction: Float = 0.18f,
    /** 邊緣位移量（dp）。 */
    val refractionDisplacement: Dp = 14.dp,
    /** 玻璃厚度比例（0~1）。Haze 的 `optics.depth` 是比例不是長度。 */
    val depthFraction: Float = 0.35f,
    /** 細節 / 色散強度（0~1）。 */
    val refractionDetailIntensity: Float = 0.35f,

    // ── 模糊 ──────────────────────────────────────────────────────
    /**
     * 背後模糊半徑（dp）。
     *
     * ★ 真玻璃**只糊一點**：iOS 的 Liquid Glass 大約 2~12dp。
     * 糊到 30dp 就看不出背後是什麼 —— 那不是玻璃，是磨砂板。
     */
    val blurDp: Dp = 6.dp,
    /** 磨砂材質用的模糊（更重，但沒有折射）。 */
    val frostBlurDp: Dp = 18.dp,

    // ── 高光 / 表面 ───────────────────────────────────────────────
    /** 鏡面高光強度（0~1）。玻璃感的直接來源。 */
    val specularIntensity: Float = 0.6f,
    /** 表面底色 alpha。**要透**（見類註解的坑）。 */
    val surfaceAlpha: Float = 0.30f,
    /** 表面底色。玻璃用略帶冷調的白，比純白有質感。 */
    val surfaceTint: Color = Color(0xFFF4F7FF),
    // ── Haze GlassStyleScope 的其餘 setter ───────────────────────
    /** 邊緣柔和度（長度）。越大邊緣越「水」。 */
    val edgeSoftnessDp: Dp = 6.dp,
    /** 邊緣陰影顏色。讓玻璃有厚度。 */
    val edgeShadowColor: Color = Color(0x1A000000),
    /** 色散強度（0~1）。邊緣的彩虹。 */
    val chromaticAberrationStrength: Float = 0.30f,
    /** 菲涅耳指數。邊緣反射增強 —— 玻璃的物理特徵。 */
    val fresnelExponent: Float = 4.0f,
    /** 環境反應（0~1）。玻璃對環境光的響應。 */
    val ambientResponse: Float = 0.5f,
    /** 圓角。 */
    val cornerRadius: Dp = 26.dp,
) {

    /** 目前模式下的模糊。 */
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
            refractionStrength = 0.60f,
            refractionHeightFraction = 0.16f,
            refractionDisplacement = 14.dp,
            depthFraction = 0.35f,
            refractionDetailIntensity = 0.32f,
            blurDp = 6.dp,
            frostBlurDp = 18.dp,
            specularIntensity = 0.62f,
            surfaceAlpha = 0.88f,
            surfaceTint = Color(0xFFF4F7FF),
            cornerRadius = 26.dp,
        )

        /** **深色定稿**：折射與高光都更強（深色背景下玻璃更明顯）。 */
        val DARK = GlassSpec(
            refractionStrength = 0.72f,
            refractionHeightFraction = 0.20f,
            refractionDisplacement = 16.dp,
            depthFraction = 0.40f,
            refractionDetailIntensity = 0.42f,
            blurDp = 8.dp,
            frostBlurDp = 22.dp,
            specularIntensity = 0.72f,
            surfaceAlpha = 0.85f,
            surfaceTint = Color(0xFFF2F5FC),
            cornerRadius = 26.dp,
        )

        /** **降級**：不支援時的近似（實心 + 高光描邊）。 */
        val FALLBACK = GlassSpec(
            refractionStrength = 0f,
            refractionHeightFraction = 0f,
            refractionDisplacement = 0.dp,
            depthFraction = 0f,
            refractionDetailIntensity = 0f,
            blurDp = 0.dp,
            frostBlurDp = 0.dp,
            specularIntensity = 0.4f,
            surfaceAlpha = 0.94f,
            surfaceTint = Color(0xFFF4F7FF),
            cornerRadius = 26.dp,
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
 * - [LIQUID] 液態（折射 + 色散 + 深度 + 高光）
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
    var refractionStrength by mutableStateOf(initial.refractionStrength)
    var refractionHeightFraction by mutableStateOf(initial.refractionHeightFraction)
    var refractionDisplacement by mutableStateOf(initial.refractionDisplacement)
    var depthFraction by mutableStateOf(initial.depthFraction)
    var refractionDetailIntensity by mutableStateOf(initial.refractionDetailIntensity)
    var blurDp by mutableStateOf(initial.blurDp)
    var specularIntensity by mutableStateOf(initial.specularIntensity)
    var surfaceAlpha by mutableStateOf(initial.surfaceAlpha)

    fun current(base: GlassSpec = GlassSpec.LIGHT): GlassSpec = base.copy(
        refractionStrength = refractionStrength,
        refractionHeightFraction = refractionHeightFraction,
        refractionDisplacement = refractionDisplacement,
        depthFraction = depthFraction,
        refractionDetailIntensity = refractionDetailIntensity,
        blurDp = blurDp,
        specularIntensity = specularIntensity,
        surfaceAlpha = surfaceAlpha,
    )

    fun resetTo(spec: GlassSpec) {
        refractionStrength = spec.refractionStrength
        refractionHeightFraction = spec.refractionHeightFraction
        refractionDisplacement = spec.refractionDisplacement
        depthFraction = spec.depthFraction
        refractionDetailIntensity = spec.refractionDetailIntensity
        blurDp = spec.blurDp
        specularIntensity = spec.specularIntensity
        surfaceAlpha = spec.surfaceAlpha
    }
}

/** 目前生效的 [GlassSpec]。 */
val LocalGlassSpec = androidx.compose.runtime.staticCompositionLocalOf { GlassSpec.LIGHT }

/** 目前的底欄材質。 */
val LocalBarMaterial = androidx.compose.runtime.staticCompositionLocalOf { BarMaterial.LIQUID }