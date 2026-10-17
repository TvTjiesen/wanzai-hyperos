package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **液態玻璃容器**（iOS 26/27 風格）。
 *
 * ## 它是什麼
 *
 * 一層「真的會折射背後內容」的玻璃：模糊 + 邊緣折射（lens）+ 高光 + 陰影。
 * 用的是 [io.github.kyant0:backdrop](https://github.com/Kyant0/AndroidLiquidGlass)
 * 2.0.1（Apache-2.0，minSdk 21）。
 *
 * ## ★ 為什麼這裡是「兩段式」用法
 *
 * 這個庫的模型是：
 * 1. 先把**要折射的內容**畫進一個 [LayerBackdrop]（[GlassBackdropHost] 負責）；
 * 2. 玻璃元件（本函式）從那個 backdrop 取像並折射。
 *
 * 所以順序是：`GlassBackdropHost { 內容 }` 裡再放 `GlassSurface { ... }`。
 * 直接把 `GlassSurface` 放在沒有 host 的地方，它會折射到**空白**——
 * 表現是「一塊灰玻璃」，而且**不報錯**（規範 §6.4 警告的那類靜默失效）。
 *
 * ## 降級
 *
 * [GlassSpec.FALLBACK] 是「沒有模糊也好看」的那套（實心 + 高光描邊）。
 * 由 [GlassTheme] 依裝置能力與深淺選定，這裡照 [LocalGlassSpec] 用。
 *
 * @param modifier 修飾符
 * @param shape 容器形狀。**圓角必須與 [GlassSpec.cornerRadius] 一致**，
 *              否則高光會浮在圓角外面。
 */
/** 診斷開關：強制走降級分支，用來確認會不會崩。 */
private const val GLASS_FORCE_FALLBACK = true

@Composable
fun GlassSurface(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(
        LocalGlassSpec.current.cornerRadius,
    ),
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f

    // 沒有 backdrop（或降級）→ 走「實心 + 高光描邊」的外觀。
    //
    // 這一支不是可選項：庫在低版本/無 backdrop 時**不會報錯，只會畫成一塊灰**。
    // 所以我們主動退成一個「看得過去的實心容器」，而不是讓它自己壞。
    if (backdrop == null || spec.refractionAmount <= 0f || GLASS_FORCE_FALLBACK) {
        Box(
            modifier = modifier
                .then(
                    Modifier.drawGlassFallback(spec, shape, isDark),
                ),
            content = content,
        )
        return
    }

    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 模糊：讓背後內容「糊掉但不失形」。0 = 不糊。
                //
                // ⚠️ 簽名（從 2.0.1 的 class 讀出來）：
                //   `blur(radius: Float, tileMode: TileMode)`
                // **只有一個半徑參數**，第二個是 TileMode（不是 radiusY，也不是飽和度）。
                // 我第一版按常規猜「(x, y, tileMode)」，編譯器檔下來了。
                if (spec.blurRadius > 0.dp) {
                    blur(spec.blurRadius.toPx())
                }
                // 折射：玻璃邊緣的「鏡片」效果 —— 這是 liquid glass 的靈魂。
                //
                // 簽名：`lens(refractionHeight: Float, refractionAmount: Float, a: Boolean, b: Boolean)`
                // **沒有 saturation 參數**。
                // 0 = 純磨砂玻璃（就變成普通的 blur 卡片了）。
                if (spec.refractionAmount > 0f) {
                    lens(
                        refractionHeight = spec.refractionHeight.toPx(),
                        refractionAmount = spec.refractionAmount,
                    )
                }
            },
            highlight = {
                // 邊緣高光。**這是「玻璃感」的關鍵** ——
                // 沒有它，一塊半透明就只是「半透明」，看不出材質。
                Highlight(
                    width = spec.highlightWidth,
                    blurRadius = spec.highlightWidth * 2,
                    alpha = spec.highlightAlpha,
                    // ⚠️ `HighlightStyle.Ambient(intensity: Float)` ——
                    // **不能指定顏色**（顏色由庫內部依形狀算）。
                    style = HighlightStyle.Ambient(intensity = spec.saturation),
                )
            },
            onDrawSurface = {
                // 表面底色。iOS 的玻璃不是完全透明，有一層很淡的底 ——
                // 深色 10% / 淺色 34%（差 3 倍多，因為亮色下需要更實的托盤感）。
                drawRect(
                    color = spec.surfaceTint.copy(alpha = spec.surfaceAlpha),
                )
            },
            // 圓角與 shape 一致
        ),
        content = content,
    )
}

/**
 * 玻璃的**降級畫法**：實心底 + 1dp 高光描邊。
 *
 * 為什麼不是「什麼都不畫」：那會讓底欄變成透明的一條，
 * 使用者會以為 App 壞了。實心 + 描邊至少是一個「看起來正常」的外觀。
 *
 * ⚠️ 這裡刻意用**全限定名**調 `androidx.compose.foundation.background/border`：
 * 如果先寫同名私有擴展，會把標準庫的那個**遮蔽**掉，
 * 於是別處的同名調用也會解析到這裡 —— 那是一種很難查的錯誤。
 */
private fun Modifier.drawGlassFallback(
    spec: GlassSpec,
    shape: Shape,
    isDark: Boolean,
): Modifier = this
    .background(
        color = spec.surfaceTint.copy(alpha = if (isDark) 0.14f else 0.92f),
        shape = shape,
    )
    .border(
        width = spec.highlightWidth,
        color = if (isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        },
        shape = shape,
    )

/**
 * **給玻璃提供折射來源**：把子內容畫進一個 [LayerBackdrop]。
 *
 * ## 為什麼必須有這一層
 *
 * 玻璃要「折射背後的東西」，而「背後的東西」必須先被畫進一個可被取樣的層。
 * 沒有這一層，玻璃折射到的是空白 —— 表現是「一塊灰玻璃」且**不報錯**
 *（規範 §6.4 說的就是這類靜默失效，所以我們把它做成必需的一步，而不是可選）。
 *
 * ## 用法
 *
 * ```kotlin
 * GlassBackdropHost(modifier = Modifier.fillMaxSize()) { backdrop ->
 *     LazyColumn { … }                        // 背景內容（會被折射）
 *     GlassSurface(backdrop) { … }            // 玻璃元件（浮在內容上）
 * }
 * ```
 *
 * @param content 拿到 backdrop 後畫內容；backdrop 只在這個作用域內有效。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    // 每頁一份（規範紅線 #3：離場頁清了共享 backdrop → 切頁後模糊永久消失）。
    val backdrop = rememberLayerBackdrop()

    Box(modifier = modifier) {
        // ① 內容：掛上 layerBackdrop，讓它被真正畫進可供取樣的層。
        //
        // ★ 這是整件事的關鍵一步。只調 `rememberLayerBackdrop()` 而不把內容
        //   掛上 `layerBackdrop(...)` 的話，backdrop 是空的 ——
        //   玻璃折射到空白，表現是「一顆不透明膠囊」，而且**不報錯**。
        //   我第一版就是這樣，靠真機截圖才看出來。
        Box(modifier = Modifier.matchParentSize().layerBackdrop(backdrop)) {
            // ★ 把 backdrop 發布出去，讓內容區裡的 GlassSurface（不顯式傳參的）
            //   也能拿到同一個折射來源。
            //
            //   第一版漏了這一步 —— 於是首頁 6 個玻璃卡片全部走降級分支
            //   （`LocalGlassBackdrop.current` 是默認的 null），
            //   真機上表現為「卡片一點也不玻璃」，而且不報錯。
            //   靠臨時加的 GlassDiag 日誌（backdrop=false ×6）才定位到。
            androidx.compose.runtime.CompositionLocalProvider(
                LocalGlassBackdrop provides backdrop,
            ) {
                content()
            }
        }
        // ② 玻璃：在 layer **之外**，折射上面那份內容。
        //    如果玻璃也被包進 layerBackdrop，它會折射自己 —— 變成無限糊。
        overlay(backdrop)
    }
}

/**
 * 目前頁面的 [LayerBackdrop]。
 *
 * 有它才能讓「內容在 LazyColumn 裡、玻璃浮在上面」這種常見佈局拿到同一個 backdrop，
 * 不必把 backdrop 一路當參數傳。
 */
val LocalGlassBackdrop =
    androidx.compose.runtime.staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 依深淺提供 [GlassSpec]，並可選地套上一份調參結果。
 *
 * 用法（放在 `WanzaiTheme` 內層一次即可）：
 * ```kotlin
 * GlassTheme { … }
 * ```
 *
 * @param override 調參頁傳進來的值；null = 用深淺定稿。
 */
@Composable
fun GlassTheme(
    override: GlassSpec? = null,
    content: @Composable () -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val spec = override ?: GlassSpec.forDark(isDark)
    CompositionLocalProvider(LocalGlassSpec provides spec, content = content)
}

/** 玻璃底欄的標準高度（含內距）。 */
val GlassBarHeight = 64.dp

/** 玻璃卡片的最小高度（單行）。 */
val GlassCardMinHeight = 56.dp

