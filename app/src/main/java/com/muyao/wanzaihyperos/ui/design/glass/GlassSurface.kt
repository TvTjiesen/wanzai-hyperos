package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.rememberHazeState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **玻璃容器 —— 用 Haze 2.x 的折射玻璃**（`dev.chrisbanes.haze:haze-glass`）。
 *
 * ## ★ 為什麼換成 Haze（業主：「去 github 看看開源的安卓液態是啥樣的」）
 *
 * 查完的結論：**業界做 iOS 26 液態玻璃的標準做法就是 Haze**，不是 kyant/backdrop。
 *
 * - Haze 2.0 的官方描述：*"source-backed or own-content Blur,
 *   **refraction-driven Glass**"*；
 * - `haze-glass` 提供 `refractionStrength` / `refractionHeightFraction` /
 *   `refractionDisplacement` / `blurRadius` / `depth` /
 *   `refractionDetailIntensity` —— 這正是 iOS 玻璃的物理參數；
 * - 內建 `BuiltInGlassStyle.Regular` / `.Clear` 預設；
 * - 自帶交互（`hovered`/`focused`/`pressed` 會改變光學表現）= 「靈動」的來源。
 *
 * 參考 app（安大信）用的也是 **haze + kyant/backdrop** 這套組合。
 *
 * ## 之前為什麼不像
 *
 * 我手寫 AGSL 調了十幾輪參數，而參考用的**就是 Haze 的 shader**。
 * 手搓不會比成品好 —— 這是業主說「抄代碼不會抄嗎」的核心。
 *
 * ## 「一切參數可調節」怎麼落地
 *
 * 樣式是**從 [GlassSpec] 生成**的，不是硬編碼：
 * 調節 [GlassSpec] 就會改變折射強度/邊緣比例/位移/模糊/厚度/高光。
 *
 * @param backdrop 折射來源（Haze 的 source）。
 * @param material 材質。SOLID 不走玻璃。
 * @param pressFraction 0~1 按下程度 → 折射與模糊變強。
 */
@Composable
fun GlassSurface(
    backdrop: HazeState?,
    modifier: Modifier = Modifier,
    material: BarMaterial = LocalBarMaterial.current,
    cornerRadius: Dp = 26.dp,
    pressFraction: Float = 1f,
    pressedScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val p = pressFraction.coerceIn(0f, 1f)

    // 降級：沒有折射來源、或材質是 SOLID
    if (backdrop == null || !spec.usesGlass(material)) {
        Box(
            modifier = modifier.background(
                color = spec.surfaceTint.copy(alpha = if (isDark) 0.88f else 0.94f),
                shape = RoundedCornerShape(cornerRadius),
            ),
            content = content,
        )
        return
    }

    // ★★ 樣式由 [GlassSpec] 生成 —— 調參就會改變玻璃行為。
    //
    // 入口是公開的 `GlassStyle { ... }`（`BuiltInGlassStyle` 是 private，不能用）。
    // 每個 setter 的語義見 [GlassSpec] 的欄位註解。
    val style: GlassStyle = remember(spec, cornerRadius, p, isDark) {
        GlassStyle {
            shape(RoundedCornerShape(cornerRadius))
            optics(
                refractionStrength = spec.refractionStrength * (0.55f + 0.45f * p),
                refractionHeightFraction = spec.refractionHeightFraction,
                refractionDisplacement = spec.refractionDisplacement,
                // depth 在 Haze 裡是**比例**（0~1），不是長度
                depth = spec.depthFraction,
                refractionDetailIntensity = spec.refractionDetailIntensity * p,
                // ★★ 折射輪廓：**這一項沒設就沒有折射**。
                //
                // Haze 用 `RefractionProfile` 決定「哪裡折射」：
                //   Edge(width)  只在邊緣 width 這一圈折射（= 玻璃的正確行為）
                //   Surface      整片都折射（像隔著水看，不是玻璃）
                // 不指定時它不會自己猜 —— 所以折射計算了但沒有套用。
                refractionProfile = dev.chrisbanes.haze.glass.RefractionProfile.Edge(
                    spec.refractionDisplacement,
                ),
            )
            // 高光（玻璃感的直接來源）
            specularIntensity(spec.specularIntensity)
            // 表面色：**要透**，太實會把折射蓋死
            alpha(spec.surfaceAlpha)
            // 邊緣柔和度（長度）：越大邊緣越「水」
            edgeSoftness(spec.edgeSoftnessDp)
            // 邊緣陰影：讓玻璃有厚度
            edgeShadow(spec.edgeShadowColor)
            // 色散
            chromaticAberrationStrength(spec.chromaticAberrationStrength)
            // 菲涅耳：邊緣反射增強，玻璃的物理特徵
            fresnelExponent(spec.fresnelExponent)
            // 環境反應：玻璃對環境光的響應
            ambientResponse(spec.ambientResponse)
        }
    }

    Box(
        modifier = modifier.hazeGlass(
                // 折射來源：取 HazeState 裡標記的所有 hazeSource 區域。
                input = HazeInput.Sources(backdrop),
                style = style,
                // ★★ 這一項決定「有沒有折射」。
                //
                // Haze 的折射要往外採樣（邊緣會「吸」到元件外的像素），
                // 預設 `expandLayerBounds = false` 時採樣層只覆蓋元件自身，
                // 邊緣採到的是空白 —— 結果就是**看起來完全沒有折射**。
                //
                // 業主三次說「沒有折射扭曲」，根因就是這個。
                expandLayerBounds = true,
            ),
    ) {
        // ★★ 在玻璃**之上**再鋪一層表面色。
        //
        // 為什麼是「之上」而不是「之下」：
        // Haze 的玻璃會把底下的內容折射上來（那是折射的本質），
        // 但底欄必須讓圖標清晰可讀 —— 完全透會變成「內容和圖標疊在一起」。
        //
        // 加在之上 = 背後的內容被**壓淡**成淡淡的輪廓（看得出有東西），
        // 但圖標與文字仍然清楚；折射與色散在邊緣照樣可見。
        // 這是 iOS 玻璃的實際做法：玻璃本體有一層極淡的乳白底色。
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    color = spec.surfaceTint.copy(
                        alpha = if (isDark) 0.72f else 0.80f,
                    ),
                    shape = RoundedCornerShape(cornerRadius),
                ),
        )
        content()
    }
}

/**
 * 玻璃折射來源（包住要折射的內容）。
 *
 * ## ★★ 關鍵：要被折射的內容裡不能有玻璃
 *
 * 這是「啟動即死」的真正原因：內容裡若也有玻璃，它會在同一層裡
 * 採樣那一層 → 採樣自己 → 無限遞迴 → 渲染卡死 → 系統殺進程。
 *
 * 所以分成兩個 slot：[content] 被折射、[overlay] 放玻璃。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.(dev.chrisbanes.haze.HazeState) -> Unit,
) {
    val hazeState = rememberHazeState()

    Box(modifier = modifier) {
        // ① 內容：被折射（不能含玻璃）
        Box(modifier = Modifier.matchParentSize().hazeSource(hazeState)) {
            content()
        }
        // ② 玻璃：在外面，折射上面那份內容
        overlay(hazeState)
    }
}

/** 目前的 HazeState。 */
val LocalGlassBackdrop =
    androidx.compose.runtime.staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/** 依深淺提供 [GlassSpec] 與 [BarMaterial]。 */
@Composable
fun GlassTheme(
    override: GlassSpec? = null,
    material: BarMaterial? = null,
    content: @Composable () -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val spec = override ?: GlassSpec.forDark(isDark)
    CompositionLocalProvider(
        LocalGlassSpec provides spec,
        LocalBarMaterial provides (material ?: BarMaterial.LIQUID),
        content = content,
    )
}

/** 玻璃底欄標準高度。 */
val GlassBarHeight = 46.dp