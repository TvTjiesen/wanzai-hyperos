package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液態玻璃容器。
 *
 * 光學層次（由下到上）—— 這是逆向 com.linxin.demo 的 LxBottomBar 學到的：
 *
 *   1. vibrancy()          增飽和，讓背後的顏色更濃（玻璃才有「顏色」）
 *   2. blur(radius)        模糊 2~12dp（**不是** 30+）
 *   3. lens(h, amount)     邊緣折射 —— 液態玻璃的靈魂
 *   4. Highlight           邊緣高光
 *   5. Shadow              外陰影（讓玻璃浮起來）
 *   6. InnerShadow         內陰影（讓玻璃有厚度）★ 我第一版完全沒做
 *   7. onDrawSurface       表面底色（米白 34% 淺 / 16% 深）
 *
 * 少任何一層都不像玻璃：
 * - 沒有 lens → 只是磨砂板
 * - 沒有 InnerShadow → 像半透明貼紙，沒有厚度
 * - 沒有 vibrancy → 背後的顏色發灰
 *
 * 用法必須是兩段式（見 [GlassBackdropHost]），否則折射到空白。
 *
 * @param backdrop 折射來源。null → 自動降級成實心（不會變灰塊）。
 * @param material 材質。SOLID 不走玻璃。
 * @param pressedScale 按下時的放大倍率（滑塊用 1.39，見 LxGlass.PillPressedScale）。
 * @param pressFraction 0~1 的按下進度，驅動折射量與內陰影。
 */
@Composable
fun GlassSurface(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    material: BarMaterial = LocalBarMaterial.current,
    shape: Shape = RoundedCornerShape(LocalGlassSpec.current.cornerRadius),
    pressFraction: Float = 1f,
    pressedScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f

    // 降級：沒 backdrop、或材質是 SOLID、或 spec 是 FALLBACK（無模糊無折射）
    if (backdrop == null || !spec.usesGlass(material) || spec.blurDp <= 0.dp) {
        Box(
            modifier = modifier
                .background(
                    color = spec.surfaceTint.copy(
                        alpha = if (isDark) 0.14f else 0.94f,
                    ),
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.Black.copy(alpha = 0.08f)
                    },
                    shape = shape,
                ),
            content = content,
        )
        return
    }

    val blurRadius = spec.blurFor(material)

    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // ① 增飽和：讓背後的顏色更濃。
                //    少了這一步，玻璃背後的內容會發灰、看起來很「髒」。
                vibrancy()

                // ② 模糊。2~12dp 就夠 —— 液態玻璃靠折射不靠糊。
                if (blurRadius > 0.dp) {
                    blur(blurRadius.toPx())
                }

                // ③ 折射（lens）—— 液態玻璃的靈魂。
                //    ★ 兩個參數**都是長度**（dp→px），不是 0~1 係數。
                //    我第一版把 amount 當係數傳 0.7，等於 0.7px 的位移，
                //    難怪看不出扭曲。
                //
                //    FROSTED 只糊不折射（那才是「磨砂」）。
                if (material == BarMaterial.LIQUID && spec.lensAmountDp > 0.dp) {
                    lens(
                        refractionHeight = spec.lensHeightDp.toPx() * pressFraction.coerceIn(0f, 1f),
                        refractionAmount = spec.lensAmountDp.toPx() * pressFraction.coerceIn(0f, 1f),
                    )
                }
            },
            // ④ 邊緣高光。這是「玻璃感」最直接的來源。
            highlight = {
                Highlight(
                    width = 1.5.dp,
                    blurRadius = 3.dp,
                    alpha = spec.highlightAlpha * pressFraction.coerceIn(0f, 1f),
                )
            },
            // ⑤ 外陰影：讓玻璃「浮」在內容上。
            shadow = {
                Shadow(
                    radius = spec.shadowRadiusDp,
                    alpha = spec.shadowAlpha,
                )
            },
            // ⑥ ★ 內陰影：玻璃的「厚度」。
            //    逆向出來是 PillInnerShadowDp = 8dp，隨按壓強度縮放。
            //    我第一版完全沒做這一層，所以看起來像半透明貼紙。
            innerShadow = {
                InnerShadow(
                    radius = spec.innerShadowDp * pressFraction.coerceIn(0f, 1f),
                    alpha = spec.innerShadowAlpha * pressFraction.coerceIn(0f, 1f),
                )
            },
            // 按下放大：滑塊被按下時會「鼓起來」。
            layerBlock = {
                if (pressedScale != 1f) {
                    scaleX = pressedScale
                    scaleY = pressedScale
                }
            },
            // ⑦ 表面底色。淺 34% / 深 16%，米白不是純白。
            onDrawSurface = {
                drawRect(
                    color = spec.surfaceTint.copy(alpha = spec.surfaceAlpha),
                )
            },
        ),
        content = content,
    )
}

/**
 * 給玻璃提供折射來源。
 *
 * ★★ 關鍵規則：**要被折射的內容裡不能有玻璃。**
 *
 * 這是「啟動即死」的真正原因（查了很久）：內容裡若也有玻璃，
 * 它會在同一層裡採樣那一層 —— 採樣自己 → 無限遞迴 →
 * 渲染卡死 → 系統以 coldStart timeout 殺進程
 *（所以 logcat 沒有 FATAL EXCEPTION，只有 onTaskVanished）。
 *
 * 所以分成兩個 slot：
 * - [content]：被畫進 layer（**不能含玻璃**）
 * - [overlay]：在 layer 之外（玻璃放這裡）
 *
 * 逆向參考：com.linxin.demo 用
 * `rememberCombinedBackdrop(外部backdrop, rememberLayerBackdrop())`
 * 把兩個 backdrop 組合起來，效果類似——它的外部 backdrop 來自更上層，
 * 我們這裡簡化成「內容層 + 覆蓋層」就夠用。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()

    Box(modifier = modifier) {
        Box(modifier = Modifier.matchParentSize().layerBackdrop(backdrop)) {
            content()
        }
        overlay(backdrop)
    }
}

/** 目前頁面的 [LayerBackdrop]。 */
val LocalGlassBackdrop =
    androidx.compose.runtime.staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 依深淺提供 [GlassSpec] 與 [BarMaterial]。
 *
 * @param override 調參頁傳進來的值；null = 用深淺定稿。
 * @param material 底欄材質；null = 用 [LocalBarMaterial] 的預設（液態）。
 */
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

/** 玻璃底欄的標準高度（逆向：LxGlass.PillHeightDp = 56dp）。 */
val GlassBarHeight = 64.dp
