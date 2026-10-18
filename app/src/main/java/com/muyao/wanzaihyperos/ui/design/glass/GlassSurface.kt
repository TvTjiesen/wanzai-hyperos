package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **液態玻璃容器 —— 用 kyant/backdrop 引擎 + 自寫 AGSL 折射**。
 *
 * ## ★ 為什麼是這套，不是 Haze
 *
 * 我試過 Haze 2.x（`haze-glass`），折騰了很多輪，**像素驗證始終證明
 * 背後內容沒有透進來** —— 也就是折射根本沒生效。
 *
 * 用像素量過三次：
 * - 把表面色設成 0（完全透明）→ 背後文字完整透上來，但**字形完全正常**，
 *   沒有任何拉伸/偏移 → 證明 shader 沒套用
 * - 逐行掃描底欄內部 → 除了圖標與標題，無任何背後內容
 *
 * 所以換回 kyant/backdrop —— 它至少有兩個優點：
 * 1. 引擎簡單直接：`layerBackdrop` 把內容畫進層，`drawBackdrop` 採樣那一層
 * 2. 折射是**我自己寫的 AGSL**，參數、算法全部可控，不依賴庫的黑盒
 *
 * ## 折射怎麼來的
 *
 * `LiquidGlassShader.liquidGlassRefraction()` 裡的 AGSL：
 * 對圓角矩形做 SDF，算出邊緣法線，把採樣座標往法線方向推 ——
 * 這就是物理上的折射。色散則是對 R/G/B 三通道做對稱偏移。
 *
 * 參數全部來自 [GlassSpec]，所以「一切參數可調節」是真的。
 *
 * ## ★★ 關鍵限制：內容裡不能有玻璃
 *
 * 玻璃若也被畫進被採樣的 layer，會採樣到自己 → 無限遞迴 → 渲染卡死。
 * 所以 content 與 overlay 分開兩個 slot（見 [GlassBackdropHost]）。
 *
 * @param backdrop 折射來源（由 [GlassBackdropHost] 提供）。
 * @param pressFraction 0~1 按下程度，影響折射強度。
 */
@Composable
fun GlassSurface(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    material: BarMaterial = LocalBarMaterial.current,
    cornerRadius: Dp = 24.dp,
    pressFraction: Float = 1f,
    pressedScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val p = pressFraction.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(cornerRadius)

    android.util.Log.w(
        "GlassDiag",
        "backdrop=" + (if (backdrop != null) "有" else "NULL") +
            " material=" + material + " usesGlass=" + spec.usesGlass(material),
    )

    // 降級：沒有折射來源、或材質是 SOLID
    if (backdrop == null || !spec.usesGlass(material)) {
        Box(
            modifier = modifier.background(
                color = spec.surfaceTint.copy(alpha = if (isDark) 0.88f else 0.94f),
                shape = shape,
            ),
            content = content,
        )
        return
    }

    val blurRadius = spec.blurFor(material) * (0.6f + 0.4f * p)
    val refractHeight = spec.lensHeightDp * (0.7f + 0.3f * p)
    val refractAmount = spec.lensAmountDp * p

    // ★★★ 折射能不能生效，全看這一段。
    //
    // ## 真正的根因（我用 logcat 抓出來的）
    //
    // `BackdropEffectScope.size` 在 effects lambda 裡是 **Size.Unspecified（NaN）**：
    // ```
    // LensDiag: 執行：height=34.6 amount=33.0 size=Size.Unspecified
    // ```
    // 這個 shader 的一切都建立在 size 上：
    //   SDF 到圓角矩形的距離 → 邊緣法線 → 採樣座標偏移
    // size 是 NaN，SDF 就是 NaN，`content.eval(NaN)` 採到原樣內容
    // → **看起來完全沒有折射**。
    //
    // 這解釋了為什麼我把 refractionAmount 18→60、height 20→40 都毫無變化：
    // 參數根本沒參與計算。
    //
    // ## 為什麼用 BoxWithConstraints 而不是 onSizeChanged
    //
    // `effects` lambda 只在**首次組合時求值一次**。
    // 用 `onSizeChanged` 時那時尺寸還是 0×0 → 拿到 size=0 → 之後再也不重算。
    // `BoxWithConstraints` 的 maxWidth/maxHeight 在**組合期**就是確定值，
    // effects 第一次求值就能拿到正確尺寸。
    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val sizePx = with(density) {
            androidx.compose.ui.geometry.Size(
                maxWidth.toPx(),
                maxHeight.toPx(),
            )
        }
        android.util.Log.w(
            "GlassDiag",
            "BC maxW=" + maxWidth + " maxH=" + maxHeight + " sizePx=" + sizePx,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        // ① 模糊（真玻璃只糊一點，糊太狠就變磨砂板）
                        try {
                            if (blurRadius.value > 0f) {
                                blur(blurRadius.toPx())
                            }
                            android.util.Log.w("LensDiag", "blur 完成，準備折射")
                        } catch (e: Throwable) {
                            android.util.Log.e("LensDiag", "blur 失敗: " + e, e)
                        }
                        // ② 折射 + 色散（自寫 AGSL，見 LiquidGlassShader.kt）
                        try {
                            android.util.Log.w(
                                "LensDiag",
                                "調用折射 h=" + refractHeight.toPx() +
                                    " a=" + refractAmount.toPx() +
                                    " disp=" + spec.chromaticAberration,
                            )
                            liquidGlassRefraction(
                                height = refractHeight.toPx(),
                                amount = refractAmount.toPx(),
                                depth = true,
                                dispersion = spec.chromaticAberration,
                                explicitSize = sizePx,
                            )
                        } catch (e: Throwable) {
                            android.util.Log.e("LensDiag", "折射失敗: " + e, e)
                        }
                    },
                    // ③ 表面色：**要透**。太實會把折射整個蓋死。
                    onDrawSurface = {
                        drawRect(
                            color = spec.surfaceTint.copy(
                                alpha = if (isDark) 0.30f else 0.26f,
                            ),
                        )
                    },
                    layerBlock = {
                        if (pressedScale != 1f) {
                            scaleX = pressedScale
                            scaleY = pressedScale
                        }
                    },
                )
                // ★ 必須裁形狀：折射的 padding 在圓角之外的區域，
                //   不裁會露出一塊異色方塊（真機上表現為「底欄右邊多出一塊白」）。
                .clip(shape),
        ) {
            content()
        }
    }
}

/**
 * 玻璃折射來源：把 [content] 畫進一個可被採樣的層，[overlay] 裡的玻璃採樣它。
 *
 * ★★ **content 裡絕對不能有玻璃** —— 否則採樣自己 → 無限遞迴 → 渲染卡死 →
 * 系統以 coldStart timeout 殺進程（這是我踩過的坑，表現是「啟動即死」）。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    // ★ 每一頁一份。共享一個 backdrop 會被離場頁清掉，
    //   表現是「切頁之後模糊永久消失」。
    val backdrop = rememberLayerBackdrop()

    Box(modifier = modifier) {
        // ① 內容：畫進 layer，供玻璃採樣
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            content()
        }
        // ② 玻璃：在 layer 之外，折射上面那份內容
        overlay(backdrop)
    }
}

/** 目前的折射來源。 */
val LocalGlassBackdrop =
    androidx.compose.runtime.staticCompositionLocalOf<LayerBackdrop?> { null }

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