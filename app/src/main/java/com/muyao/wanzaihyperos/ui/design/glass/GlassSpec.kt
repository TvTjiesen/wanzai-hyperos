package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液態玻璃的全部光學參數。
 *
 * 數值來源：逆向 com.linxin.demo 1.3.9-beta63（安大信）。
 * 業主提供了那個 APK 說「細節直接參考這個」。
 * 玻璃常數在 com.linxin.feature.home.ui.LxGlass：
 *
 *   BlurMinDp = 2.0f;        BlurMaxDp = 12.0f;
 *   FrostBlurMinDp = 6.0f;   FrostBlurMaxDp = 30.0f;
 *   LensHeightMinDp = 14.0f; LensHeightMaxDp = 28.0f;
 *   LensAmountMaxDp = 24.0f;
 *   SurfaceAlphaLight = 0.34f; SurfaceAlphaDark = 0.16f;
 *   PillHeightDp = 56.0f;
 *   PillPressedScale = 1.3928572f;
 *   PillRefractionHeightDp = 10.0f; PillRefractionAmountDp = 14.0f;
 *   PillInnerShadowDp = 8.0f;
 *   BarPressGrow = 16.dp;
 *
 * 插值公式在 LxBottomBar（blur/refraction/distortion 三個 0~1 強度）：
 *   blurDp       = 10.0f * blur + 2.0f;
 *   frostBlurDp  = blur * 24.0f + 6.0f;
 *   lensHeightDp = refraction * 14.0f + 14.0f;
 *   lensAmountDp = distortion * 24.0f;
 *
 * 我原先錯在哪（重點，別再犯）：
 * 1. blurRadius 用了 34dp —— 是它 BlurMaxDp 的三倍。糊到看不出背後的東西，
 *    那就不是玻璃而是磨砂板。真正的液態玻璃只糊 2~12dp，
 *    靠的是「邊緣折射扭曲」而不是「糊」。
 * 2. 把 refractionAmount 當 0~1 係數直接用，而庫的
 *    lens(scope, refractionHeight, refractionAmount, …) 兩個參數都是長度。
 * 3. 完全沒做內陰影與色散。沒有內陰影 = 玻璃沒厚度 = 像半透明貼紙；
 *    沒有色散 = 邊緣沒彩色 = 不像光線折射。
 */
@Immutable
data class GlassSpec(
    /** 模糊半徑。2~12dp，不是 30+。 */
    val blurDp: Dp = 8.dp,
    /** 磨砂模式的模糊（6~30）。只在 FROSTED 用。 */
    val frostBlurDp: Dp = 18.dp,
    /** 折射邊緣高度。14~28dp。 */
    val lensHeightDp: Dp = 20.dp,
    /** 折射位移量。0~24dp。 */
    val lensAmountDp: Dp = 16.dp,
    /** 色散強度（0~1）。彩色邊緣，這是「真玻璃」的關鍵。 */
    val chromaticAberration: Float = 0.35f,
    /** 深度感（0~1）。讓折射朝中心收，玻璃會「鼓起來」。 */
    val depthEffect: Float = 0.5f,
    /** 表面底色 alpha。淺 0.34 / 深 0.16。 */
    val surfaceAlpha: Float = 0.34f,
    /** 表面底色。逆向出來用的是米白（LxParchment），不是純白。 */
    val surfaceTint: Color = Color(0xFFF7F2E8),
    /** 邊緣高光 alpha。 */
    val highlightAlpha: Float = 0.85f,
    /** 內陰影（厚度感）。沒有它玻璃看起來像貼紙。 */
    val innerShadowDp: Dp = 8.dp,
    val innerShadowAlpha: Float = 0.55f,
    val shadowAlpha: Float = 0.18f,
    val shadowRadiusDp: Dp = 12.dp,
    /** 圓角。底欄用 Capsule（全圓）。 */
    val cornerRadius: Dp = 999.dp,
) {
    fun blurFor(material: BarMaterial): Dp = when (material) {
        BarMaterial.SOLID -> 0.dp
        BarMaterial.FROSTED -> frostBlurDp
        BarMaterial.LIQUID -> blurDp
    }

    fun usesGlass(material: BarMaterial): Boolean = material != BarMaterial.SOLID

    companion object {
        /** 淺色定稿。表面底色用米白 —— 純白在淺色背景上會「白上白」。 */
        val LIGHT = GlassSpec(
            blurDp = 8.dp,
            frostBlurDp = 18.dp,
            lensHeightDp = 20.dp,
            lensAmountDp = 16.dp,
            chromaticAberration = 1.1f,
            depthEffect = 0.5f,
            surfaceAlpha = 0.34f,
            surfaceTint = Color(0xFFF7F2E8),
            highlightAlpha = 0.30f,
            innerShadowDp = 8.dp,
            innerShadowAlpha = 0.50f,
            shadowAlpha = 0.14f,
            shadowRadiusDp = 12.dp,
        )

        /** 深色定稿。SurfaceAlphaDark = 0.16（淺色的不到一半）。 */
        val DARK = GlassSpec(
            blurDp = 10.dp,
            frostBlurDp = 24.dp,
            lensHeightDp = 22.dp,
            lensAmountDp = 18.dp,
            chromaticAberration = 1.1f,
            depthEffect = 0.55f,
            surfaceAlpha = 0.16f,
            surfaceTint = Color(0xFFEDE7DC),
            highlightAlpha = 0.22f,
            innerShadowDp = 8.dp,
            innerShadowAlpha = 0.65f,
            shadowAlpha = 0.40f,
            shadowRadiusDp = 16.dp,
        )

        /** 降級：API < 33 無 RuntimeShader 時。只有表面色 + 高光描邊。 */
        val FALLBACK = GlassSpec(
            blurDp = 0.dp,
            frostBlurDp = 0.dp,
            lensHeightDp = 0.dp,
            lensAmountDp = 0.dp,
            chromaticAberration = 0f,
            depthEffect = 0f,
            surfaceAlpha = 0.94f,
            surfaceTint = Color(0xFFF7F2E8),
            highlightAlpha = 0.35f,
            innerShadowDp = 0.dp,
            innerShadowAlpha = 0f,
            shadowAlpha = 0.10f,
            shadowRadiusDp = 8.dp,
        )

        fun forDark(isDark: Boolean): GlassSpec = if (isDark) DARK else LIGHT
    }
}

/**
 * 底欄材質（逆向自 com.linxin.core.settings.BarMaterial）。
 *
 * 它的設定頁真的給了這三個選項（bar_solid / bar_frosted / bar_liquid）=>
 * 純色 / 磨砂 / 液態。我們也照做。
 */
enum class BarMaterial(val label: String) {
    /** 純色，無玻璃。最省電。 */
    SOLID("純色"),

    /** 磨砂玻璃（只有模糊，沒有折射）。 */
    FROSTED("磨砂"),

    /** 液態玻璃（模糊 + 折射 + 色散 + 內陰影）。 */
    LIQUID("液態"),
}

/**
 * 調參用的可寫狀態。
 *
 * 對應逆向出來的 ThemePrefs.setGlassBlur/setGlassRefraction/setGlassDistortion/
 * setGlassDispersion —— 它真的把這四個做成使用者可調的設定項
 *（SP key: glass_blur / glass_refraction / glass_distortion / glass_dispersion）。
 */
class GlassSpecTuning(initial: GlassSpec) {
    var blurDp by mutableStateOf(initial.blurDp)
    var frostBlurDp by mutableStateOf(initial.frostBlurDp)
    var lensHeightDp by mutableStateOf(initial.lensHeightDp)
    var lensAmountDp by mutableStateOf(initial.lensAmountDp)
    var chromaticAberration by mutableStateOf(initial.chromaticAberration)
    var depthEffect by mutableStateOf(initial.depthEffect)
    var surfaceAlpha by mutableStateOf(initial.surfaceAlpha)
    var highlightAlpha by mutableStateOf(initial.highlightAlpha)
    var innerShadowDp by mutableStateOf(initial.innerShadowDp)

    fun current(base: GlassSpec = GlassSpec.LIGHT): GlassSpec = base.copy(
        blurDp = blurDp,
        frostBlurDp = frostBlurDp,
        lensHeightDp = lensHeightDp,
        lensAmountDp = lensAmountDp,
        chromaticAberration = chromaticAberration,
        depthEffect = depthEffect,
        surfaceAlpha = surfaceAlpha,
        highlightAlpha = highlightAlpha,
        innerShadowDp = innerShadowDp,
    )

    fun resetTo(spec: GlassSpec) {
        blurDp = spec.blurDp
        frostBlurDp = spec.frostBlurDp
        lensHeightDp = spec.lensHeightDp
        lensAmountDp = spec.lensAmountDp
        chromaticAberration = spec.chromaticAberration
        depthEffect = spec.depthEffect
        surfaceAlpha = spec.surfaceAlpha
        highlightAlpha = spec.highlightAlpha
        innerShadowDp = spec.innerShadowDp
    }
}

/** 目前生效的 [GlassSpec]。 */
val LocalGlassSpec = staticCompositionLocalOf { GlassSpec.LIGHT }

/** 目前的底欄材質。 */
val LocalBarMaterial = staticCompositionLocalOf { BarMaterial.LIQUID }
