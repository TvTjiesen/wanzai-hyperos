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
 * **玻璃的主題級參數** —— 只放「跨元件共享」的那幾項。
 *
 * ## 為什麼這一版縮水了（重要）
 *
 * 上一版這裡同時扮演兩個角色：① 主題級（底色、色散），② 每個元件的光學值
 *（模糊、折射高/量、表面 alpha、圓角）。第 ② 類現在有更準確的落點 ——
 * [BarOptics] + [GlassSurfaceStyle]，因為**容器和滑塊的光學值本來就不同**
 *（相鄰兩塊玻璃的糊度必須不同，否則看不出層次），一個 `GlassSpec` 表達不了。
 *
 * 留著兩套只會讓人猜「我改這個有沒有用」—— 上一版 `surfaceAlpha` 就是這樣騙人的：
 * 這裡寫 0.22/0.16，而 `GlassSurface` 的 `onDrawSurface` 硬編了 0.26/0.30，
 * 調參滑桿（[GlassSpecTuning]）拖了根本不會變。
 * **所以第 ② 類一律以 [BarOptics] 為準，這裡不再重複。**
 */
@Immutable
data class GlassSpec(
    /** 表面底色（降級時也用它）。玻璃用略帶冷調的白，比純白有質感。 */
    val surfaceTint: Color = Color(0xFFF4F7FF),

    /** 磨砂材質的模糊半徑（**只有模糊、沒有折射** —— 那是磨砂玻璃，不是液態玻璃）。 */
    val frostBlur: Dp = 18.dp,
) {
    companion object {

        /** 淺色定稿。 */
        val LIGHT = GlassSpec(surfaceTint = Color(0xFFF4F7FF), frostBlur = 18.dp)

        /** 深色定稿。 */
        val DARK = GlassSpec(surfaceTint = Color(0xFFF2F5FC), frostBlur = 22.dp)

        fun forDark(isDark: Boolean): GlassSpec = if (isDark) DARK else LIGHT
    }
}

/**
 * 底欄材質。三態都必須**真的會走到**，不是列舉装点：
 *
 * - [SOLID] 實心（不透明，最省電）→ 走 [GlassRenderMode.FALLBACK]
 * - [FROSTED] 磨砂（只有模糊，沒有折射）
 * - [LIQUID] 液態（折射 + 色散 + 雙光源高光）
 */
enum class BarMaterial(val label: String) {
    SOLID("純色"),
    FROSTED("磨砂"),
    LIQUID("液態"),
}

/** 目前生效的 [GlassSpec]。 */
val LocalGlassSpec = staticCompositionLocalOf { GlassSpec.LIGHT }

/** 目前的底欄材質。 */
val LocalBarMaterial = staticCompositionLocalOf { BarMaterial.LIQUID }

/**
 * 調參用的可寫狀態（**目前沒有接線**，留給下一步的 debug 調參頁）。
 *
 * ⚠️ 它調的是**主題級**兩項。要調模糊/折射/表面 alpha/水滴拉伸，改的是 [BarOptics]，
 * 別在這裡加滑桿 —— 加了的確能拖，但畫面上不會變（上一版就是这么骗人的）。
 */
class GlassSpecTuning(initial: GlassSpec) {
    var surfaceTint by mutableStateOf(initial.surfaceTint)
    var frostBlur by mutableStateOf(initial.frostBlur)

    fun current(): GlassSpec = GlassSpec(surfaceTint = surfaceTint, frostBlur = frostBlur)

    fun resetTo(spec: GlassSpec) {
        surfaceTint = spec.surfaceTint
        frostBlur = spec.frostBlur
    }
}
