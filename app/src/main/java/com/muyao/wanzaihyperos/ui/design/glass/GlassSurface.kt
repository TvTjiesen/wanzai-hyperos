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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液態玻璃容器。
 *
 * 光學層次（由下到上），逆向 com.linxin.demo 的 LxBottomBar 學來的：
 *
 *  1. vibrancy()        增飽和，讓背后的顏色更濃（玻璃才有「顏色」）
 *  2. blur(radius)      模糊 2~12dp（不是 30+）
 *  3. lens(h, amount)   邊緣折射 —— 液態玻璃的靈魂
 *  4. Highlight         邊緣高光
 *  5. Shadow            外陰影（讓玻璃浮起來）
 *  6. InnerShadow       內陰影（讓玻璃有厚度）
 *  7. onDrawSurface     表面底色（**要透**，0.30；太實會把折射蓋死）
 *
 * 少任何一層都不像玻璃：
 * - 沒有 lens → 只是磨砂板
 * - 沒有 InnerShadow → 像半透明貼紙，沒有厚度
 * - 沒有 vibrancy → 背后的顏色發灰
 *
 * ## ★ 表面不透明度與折射是互相打架的
 *
 * 我曾在這裡把 surfaceAlpha 拉到 0.92（為了「讓底欄看起來白」），
 * 結果那一層白漆把折射整個蓋住 —— 玻璃變白板，業主說「折射都沒了」。
 *
 * **表面要透。** 白感靠：① 模糊后的背景本身偏亮 ② 高光 ③ 陰影。
 *
 * ## ★ 右側出現白色方塊的原因（已修）
 *
 * shader 會往元件**外面**採樣（折射要抓邊界外的像素），所以設了 padding。
 * 但 padding 那一圈在**圓角形狀之外**，如果不安裝裁切就會露出來，
 * 表現是圓角旁邊多出一小塊異色方塊。
 *
 * 修法：把整個 drawBackdrop 包進 `Modifier.clip(shape)` ——
 * 讓超出形狀的部分一律被裁掉。這是業主說的「右邊多出來那塊」。
 *
 * @param backdrop 折射來源。null → 自動降級成實心（不會變灰塊）。
 * @param material 材質。SOLID 不走玻璃。
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

    // 降級：沒 backdrop、或材質 SOLID、或 spec 是無模糊（FALLBACK）
    if (backdrop == null || !spec.usesGlass(material) || spec.blurDp <= 0.dp) {
        Box(
            modifier = modifier
                .background(
                    color = spec.surfaceTint.copy(alpha = if (isDark) 0.14f else 0.94f),
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
        modifier = modifier
            // ★ 先裁切再畫：把 shader 往外採樣的 padding 區裁掉。
            //   少了這一行，圓角旁邊會露出一塊異色方塊。
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    // ① 增飽和：讓背後的顏色更濃。
                    //    少了這一步，玻璃背後的內容會發灰、看起來「髒」。
                    vibrancy()

                    // ② 模糊。2~12dp 就夠 —— 液態玻璃靠折射不靠糊。
                    if (blurRadius > 0.dp) {
                        blur(blurRadius.toPx())
                    }

                    // ③ 折射 + 色散 —— 液態玻璃的靈魂。
                    //
                    // 用的是自己寫的 AGSL shader（LiquidGlassShader.kt），
                    // 不是庫的 lens()。原因：lens() 只有「整片均勻偏移」，
                    // 而真正的液態玻璃要「只在邊緣折射 + 色散 + 跟圓角走」。
                    //
                    // FROSTED 只糊不折射（那才是「磨砂」）。
                    if (material == BarMaterial.LIQUID && spec.lensAmountDp > 0.dp) {
                        val f = pressFraction.coerceIn(0f, 1f)
                        liquidGlassRefraction(
                            height = spec.lensHeightDp.toPx() * f,
                            amount = spec.lensAmountDp.toPx() * f,
                            depth = spec.depthEffect > 0f,
                            dispersion = spec.chromaticAberration * f,
                        )
                    }
                },
                // ④ 邊緣高光。
                //
                // ⚠️ 這一層如果給「一圈均勻白線」就是業主說的「奇怪邊緣白光」。
                //   真高光應該是有位置的光源（左上亮、右下暗），
                //   那需要雙光源模型（Miuix 的 BloomStroke），屬下一步。
                //   在此之前：寧可很淡，也不要那圈假白線。
                highlight = {
                    if (spec.highlightAlpha > 0f) {
                        Highlight(
                            width = 0.6.dp,
                            blurRadius = 2.dp,
                            alpha = spec.highlightAlpha * pressFraction.coerceIn(0f, 1f),
                        )
                    } else {
                        null
                    }
                },
                // ⑤ 外陰影：讓玻璃「浮」在內容上。
                shadow = {
                    Shadow(
                        radius = spec.shadowRadiusDp,
                        alpha = spec.shadowAlpha,
                    )
                },
                // ⑥ 內陰影：玻璃的「厚度」。
                //    逆向出來是 PillInnerShadowDp = 8dp，隨按壓強度縮放。
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
                // ⑦ 表面底色。**要透**（0.30）—— 太實會把折射蓋死。
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
 * ## ★ 關鍵規則：要被折射的內容裡不能有玻璃
 *
 * 這是「啟動即死」的真正原因：內容裡若也有玻璃，
 * 它會在同一層裡採樣那一層 → 採樣自己 → 無限遞迴 →
 * 渲染卡死 → 系統以 coldStart timeout 殺進程
 *（所以 logcat 沒有 FATAL EXCEPTION，只有 onTaskVanished）。
 *
 * 所以分成兩個 slot：
 * - [content]：被畫進 layer（**不能含玻璃**）
 * - [overlay]：在 layer 之外（玻璃放這裡）
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

/** 玻璃底欄的標準高度。 */
val GlassBarHeight = 64.dp
