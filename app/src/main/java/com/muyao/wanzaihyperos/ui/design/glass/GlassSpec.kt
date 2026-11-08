package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
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

        /** 浅色定稿。 */
        val LIGHT = GlassSpec(surfaceTint = Color(0xFFF4F7FF), frostBlur = 18.dp)

        /** 深色定稿。 */
        val DARK = GlassSpec(surfaceTint = Color(0xFFF2F5FC), frostBlur = 22.dp)

        fun forDark(isDark: Boolean): GlassSpec = if (isDark) DARK else LIGHT

        /**
         * 降级（不采样，走实心纸面）时垫色的不透明度。
         *
         * ★ 提到这里是因为**调用方也得用同一个数**：`HomeSectionPage` 那张此刻卡要按
         *   「卡面实际铺出来的亮度」决定字色，而 SOLID 那一档画面是 [GlassSurface] 的
         *   降级分支画的。两边各写一份 0.94/0.88，早晚会飘（飘了就是"白压白"那类只能靠
         *   眼睛发现的 bug —— 而眼睛发现不了 0.88 与 0.94 的差）。
         */
        const val FALLBACK_ALPHA_LIGHT = 0.94f
        const val FALLBACK_ALPHA_DARK = 0.88f
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
    SOLID("纯色"),
    FROSTED("磨砂"),
    LIQUID("液态"),
}

/**
 * 把「盘里存的玻璃档位」映射成运行期真正用的 [BarMaterial]。
 *
 * ★ v3 清单 #12 的接线点。此前 `LocalBarMaterial` **全工程没有任何一处 provide**
 * （唯一会 provide 的 `GlassTheme` 零调用），于是所有 `GlassSurface` 一路吃
 * `staticCompositionLocalOf` 的默认值 LIQUID —— 设置页写着"磨砂"、屏幕上还是折射的液态。
 *
 * @param glassMode `UiPrefs.glassMode`：`liquid` / `frost` / `off`
 * @param translucent `MotionPrefs.glassIsTranslucent`（系统"减弱透明度"或高对比时为 false）
 *                    —— 它比档位优先：任何一档都要先退成实底。
 */
fun barMaterialFor(glassMode: String, translucent: Boolean): BarMaterial = when {
    !translucent -> BarMaterial.SOLID
    glassMode == "frost" -> BarMaterial.FROSTED
    glassMode == "off" -> BarMaterial.SOLID
    else -> BarMaterial.LIQUID
}

/** 目前生效的 [GlassSpec]。 */
// ★ 独立审计（v3 #12 第 2 轮 P2#6）：这两条以前是 `staticCompositionLocalOf` ——
//   它承载的却是**会变的用户档位**。今天能刷新，纯粹靠 provider 的作用域被 `glassMode`
//   失效带着重建；哪天那条失效链断掉（比如有人把 provide 挪进一个 remember 里），
//   就会变成"切了档不重算"，而 static 版本**不会给你任何警告**。
//   换 `compositionLocalOf`：语义就是"这会变"，读它的组合在被换值时正确失效。
val LocalGlassSpec = compositionLocalOf { GlassSpec.LIGHT }

/** 目前的底欄材質。 */
val LocalBarMaterial = compositionLocalOf { BarMaterial.LIQUID }

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

/**
 * 玻璃面通透度系数：**50 ⇒ 1f**（原值），100 ⇒ 0.62（更通透），0 ⇒ 1.38（更厚实）。
 *
 * ★ 方向说明（返工总单 C1）：滑块左端写「更厚实」、右端写「更通透」，
 *   所以 v 越大系数必须越小。上一版把符号写反了（v=100 反而 1.38），
 *   界面与代码各说一套 —— 现在两边一致：**系数 = 1 - (v-50)/50 × 0.38**。
 *
 * 业主 2026-11-07 对稿：设计稿外观页有「通透度 · 新增」滑块，默认 50%，
 * 左「更厚实」右「更通透」，说明写「只影响顶栏、首页焦点卡、搜索与底栏等承载层；
 * 正文保持清楚」。
 *
 * 所以这个系数只乘**垫底那一层的 alpha**：
 *  · liquid 栈 → `ui/design/liquid/IosChrome.kt` 的 `baseAlpha`
 *  · glass 栈 → `ui/design/glass/BarOptics.kt` 给出来的 `surfaceAlpha`
 * 正文、描边高光、折射量都不动 —— 调"厚薄"不该顺手把字也调淡。
 *
 * 读的是 `UiPrefs.glassOpacity`（mutableStateOf）⇒ 拖动滑块时所有玻璃面同一帧跟着变。
 */
@Composable
fun glassOpacityFactor(): Float =
    opacityFactorFor(com.muyao.wanzaihyperos.data.UiPrefs.glassOpacity.toFloat())

/**
 * 通透度滑块的纯函数形式：`0..100` 的滑块值 → 垫底层 alpha 系数。
 *
 * 单独抽出来是为了**能被单元测试钉住方向**（返工总单 C1 就是因为符号反了、
 * 而且没有任何断言守着，才一路活到对稿）。Composable 版本读 `UiPrefs` 再调它，
 * 数学只有这一处，测试与界面不可能各说一套。
 *
 * - v = 0   ⇒ 1.38（更厚实）
 * - v = 50  ⇒ 1.00（默认，原样）
 * - v = 100 ⇒ 0.62（更通透）
 */
fun opacityFactorFor(v: Float): Float = 1f - (v - 50f) / 50f * 0.38f
