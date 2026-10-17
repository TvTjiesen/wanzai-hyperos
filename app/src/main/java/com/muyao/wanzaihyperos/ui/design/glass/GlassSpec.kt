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
 * **液態玻璃的全部光學參數**（iOS 26/27 風格）。
 *
 * ## 為什麼要集中成一個 data class
 *
 * 業主要求「玻璃的**一切參數可調節**」。散在調用點的話，
 * 調一個參數要翻遍所有用到玻璃的地方，而且**改一處漏一處**的後果是
 * 「底欄和頂欄的玻璃看起來不是同一種材質」——那種不一致肉眼看得出來，
 * 但很難定位。
 *
 * 集中在這裡之後：
 * - 調參只改一處（或傳一份新的 [GlassSpec]）；
 * - 有 [GlassSpec.LIGHT] / [GlassSpec.DARK] 兩套定稿；
 * - 有 [GlassSpecTuning] 給 debug 調參頁直接綁 slider。
 *
 * ## 庫與許可
 *
 * 用的是 **[io.github.kyant0:backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 2.0.1**
 * （Apache-2.0，minSdk 21 —— 我們是 24，可用）。
 *
 * 為什麼不用 `miuix-blur`：它**要 minSdk 33**，抬上去會丟掉 Android 13 以下全部機型
 *（`miuix-skill/references/styling-icons-and-effects.md`：
 * 「On Android, blur requires API 33」）。規範 §0.2 也是這個判斷。
 *
 * ## 各參數的物理意義（改之前先讀）
 *
 * | 參數 | 作用 | 調大的後果 |
 * |---|---|---|
 * | [blurRadius] | 背後內容糊多深 | 越大越「磨砂」，太大就看不出背後是什麼 |
 * | [refractionHeight] | 邊緣折射的高度（玻璃「厚度」） | 越大邊緣越厚，像厚鏡片 |
 * | [refractionAmount] | 折射強度（邊緣扭曲程度） | 太大會變哈哈鏡 |
 * | [saturation] | 飽和度 | >1 讓背後顏色更濃，玻璃才「有顏色」 |
 * | [surfaceAlpha] | 表面底色透明度 | 太實=灰塊，太透=看起來沒材質 |
 * | [highlightAlpha] | 邊緣高光 | 這是「玻璃感」的關鍵，沒有它就是一塊半透明色 |
 * | [lightAngle] | 光源方向（度） | 要跟環境一致；固定值比跟重力穩 |
 * | [cornerRadius] | 圓角 | 要與容器一致，否則高光會浮在圓角外 |
 *
 * @property blurRadius 模糊半徑（dp）。iOS 導航欄大約 20~30。
 * @property refractionHeight 折射高度（dp）。越大玻璃越「厚」。
 * @property refractionAmount 折射量。0 = 不折射（純模糊），1 = 全量。
 * @property saturation 飽和度倍數。1 = 原樣，1.5~2 讓玻璃有「顏色感」。
 * @property surfaceAlpha 表面底色 alpha。深色 0.10 / 淺色 0.34 是 iOS 的做法。
 * @property surfaceTint 表面底色（通常白色或主題色）。
 * @property highlightAlpha 邊緣高光 alpha。0 = 沒有玻璃感。
 * @property highlightWidth 高光寬度（dp）。
 * @property lightAngleDeg 光源角度（度）。0 = 從右來，-45 = 右上。
 * @property shadowAlpha 外陰影 alpha（讓玻璃「浮起來」）。
 * @property shadowRadius 外陰影半徑（dp）。
 * @property innerShadowAlpha 內陰影（讓玻璃邊緣「有厚度」）。
 * @property cornerRadius 圓角（dp）。**必須與容器一致**。
 */
@Immutable
data class GlassSpec(
    val blurRadius: Dp = 24.dp,
    val refractionHeight: Dp = 16.dp,
    val refractionAmount: Float = 0.7f,
    val saturation: Float = 1.8f,
    val surfaceAlpha: Float = 0.18f,
    val surfaceTint: Color = Color.White,
    val highlightAlpha: Float = 0.75f,
    val highlightWidth: Dp = 1.dp,
    val lightAngleDeg: Float = -45f,
    val shadowAlpha: Float = 0.20f,
    val shadowRadius: Dp = 12.dp,
    val innerShadowAlpha: Float = 0.12f,
    val cornerRadius: Dp = 26.dp,
) {
    companion object {
        /**
         * **淺色定稿**。
         *
         * iOS 在亮色下需要**更實**的「托盤感」（背後是亮的，玻璃太透就看不見）。
         * 所以 surfaceAlpha 取 0.34 而不是深色那套的 0.10 —— **差 3 倍多**。
         */
        val LIGHT = GlassSpec(
            blurRadius = 34.dp,
            refractionHeight = 26.dp,
            refractionAmount = 2.2f,
            saturation = 2.6f,
            surfaceAlpha = 0.55f,
            // ★ 淺色下用**極淡的藍紫**而不是純白：
            //   純白玻璃疊在白色卡片上等於「白上白」，看不出材質（真機實測）。
            surfaceTint = Color(0xFFEFF3FF),
            highlightAlpha = 1.0f,
            highlightWidth = 1.2.dp,
            lightAngleDeg = -45f,
            shadowAlpha = 0.14f,
            shadowRadius = 14.dp,
            innerShadowAlpha = 0.10f,
            cornerRadius = 26.dp,
        )

        /**
         * **深色定稿**。
         *
         * 深色下表面只用 10% 白（比淺色低 3 倍多）——
         * 因為背景本來就暗，加太多白會變成「灰塊」而不是玻璃。
         */
        val DARK = GlassSpec(
            blurRadius = 24.dp,
            refractionHeight = 20.dp,
            refractionAmount = 0.7f,
            saturation = 1.5f,
            surfaceAlpha = 0.10f,
            surfaceTint = Color.White,
            highlightAlpha = 0.60f,
            highlightWidth = 1f.dp,
            lightAngleDeg = -45f,
            shadowAlpha = 0.40f,
            shadowRadius = 16.dp,
            innerShadowAlpha = 0.16f,
            cornerRadius = 26.dp,
        )

        /**
         * **降級**：設備不支援時用的近似外觀。
         *
         * 規範與庫都提到「低版本上模糊不是降級，是崩或不畫」。
         * 所以必須有一份「不依賴 shader 也好看」的參數：
         * 只有表面底色 + 高光描邊，沒有模糊與折射。
         */
        val FALLBACK = GlassSpec(
            blurRadius = 0.dp,
            refractionHeight = 0.dp,
            refractionAmount = 0f,
            saturation = 1f,
            surfaceAlpha = 0.92f,
            surfaceTint = Color.White,
            highlightAlpha = 0.9f,
            highlightWidth = 1.dp,
            lightAngleDeg = -45f,
            shadowAlpha = 0.10f,
            shadowRadius = 8.dp,
            innerShadowAlpha = 0f,
            cornerRadius = 26.dp,
        )

        /** 依深淺挑定稿。 */
        fun forDark(isDark: Boolean): GlassSpec = if (isDark) DARK else LIGHT
    }
}

/**
 * 給 debug 調參頁用的可寫狀態。
 *
 * ## 為什麼要多這一層
 *
 * [GlassSpec] 是 `@Immutable` 的（好處：Compose 能跳過重組）。
 * 但調參頁要**邊拖 slider 邊看效果**，所以需要一份可寫的、
 * 能即時轉成 [GlassSpec] 的狀態。
 *
 * 規範 §15 的建議：「一次只改一個變量，並**同時給新舊兩組值讓業主二選一**；
 * 連著猜三個參數是最省不了時間的做法。」——這個類就是為此存在。
 */
class GlassSpecTuning(initial: GlassSpec) {
    var blurRadius by mutableStateOf(initial.blurRadius)
    var refractionHeight by mutableStateOf(initial.refractionHeight)
    var refractionAmount by mutableStateOf(initial.refractionAmount)
    var saturation by mutableStateOf(initial.saturation)
    var surfaceAlpha by mutableStateOf(initial.surfaceAlpha)
    var highlightAlpha by mutableStateOf(initial.highlightAlpha)
    var lightAngleDeg by mutableStateOf(initial.lightAngleDeg)
    var shadowAlpha by mutableStateOf(initial.shadowAlpha)
    var cornerRadius by mutableStateOf(initial.cornerRadius)

    /** 目前值。每次讀都組一份新的（`data class` 很便宜）。 */
    fun current(base: GlassSpec = GlassSpec.LIGHT): GlassSpec = base.copy(
        blurRadius = blurRadius,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        saturation = saturation,
        surfaceAlpha = surfaceAlpha,
        highlightAlpha = highlightAlpha,
        lightAngleDeg = lightAngleDeg,
        shadowAlpha = shadowAlpha,
        cornerRadius = cornerRadius,
    )

    /** 還原到某份定稿。 */
    fun resetTo(spec: GlassSpec) {
        blurRadius = spec.blurRadius
        refractionHeight = spec.refractionHeight
        refractionAmount = spec.refractionAmount
        saturation = spec.saturation
        surfaceAlpha = spec.surfaceAlpha
        highlightAlpha = spec.highlightAlpha
        lightAngleDeg = spec.lightAngleDeg
        shadowAlpha = spec.shadowAlpha
        cornerRadius = spec.cornerRadius
    }
}

/**
 * 目前生效的 [GlassSpec]。
 *
 * 用 CompositionLocal 而不是到處傳參數：玻璃出現在底欄、頂欄、卡片、
 * 懸浮鈕等**很多地方**，逐個傳會讓每個函式簽名都多一個參數。
 *
 * 默認給 [GlassSpec.LIGHT]（淺色定稿）—— 由 [GlassTheme] 依深淺覆蓋。
 */
val LocalGlassSpec = staticCompositionLocalOf { GlassSpec.LIGHT }
