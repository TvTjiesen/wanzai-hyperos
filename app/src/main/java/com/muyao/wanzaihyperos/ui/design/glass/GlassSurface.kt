package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.muyao.wanzaihyperos.ui.design.GlassLiveness
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **液態玻璃容器 —— 折射走庫自帶的 `lens()`**。
 *
 * ## ★ 為什麼不再用本项目手搓的 AGSL
 *
 * 我把 `com.kyant.backdrop.effects.LensKt` 內嵌的 shader 源碼讀出來對過：
 * 它就是 `sdRoundedRect` + `gradSdRoundedRect` + `depthEffect` 那套 SDF 折射，
 * 和原先 `LiquidGlassShader.kt` 手寫的**演算法完全一致**。手寫那份等於重新發明一遍，
 * 還多背一個坑：`BackdropEffectScope.size` 在 `effects` 裡是 `Size.Unspecified`（NaN），
 * NaN 進 SDF ⇒ 採樣座標 NaN ⇒ `content.eval(NaN)` 拿回原樣內容 ⇒
 * **看上去「接上了」但一點折射都没有，而且参数怎麼調像素都不變**。
 *
 * 庫的 `lens()` 自己也讀 `size`，所以這裡**照樣要防這一手**：
 * 元件尺寸一律由調用方在**組合期**算好（`BoxWithConstraints`），
 * ⚠️ 不能用 `onSizeChanged` —— `effects` 只在首次組合求值一次，那時它還沒觸發。
 *
 * ## ★★ 一個元素只許一條渲染路徑
 *
 * 折射 = `lens()`；高光 = [bloomStrokeHighlight]（帶重力方向）。
 * 不要同時再掛 `drawBackdrop(highlight = ...)` 那個庫原生槽位 —— 兩套疊會互相蓋掉（紅線 #2）。
 *
 * ## ★★★ 被採樣的內容裡絕對不能有玻璃
 *
 * 玻璃若被畫進被採樣的 layer ⇒ 採樣到自己 ⇒ 無限遞迴 ⇒ 渲染卡死 ⇒
 * 系統以 `coldStart timeout` 殺進程。**表現是「啟動即死」，logcat 裡沒有 FATAL EXCEPTION，
 * 只有 `onTaskVanished`** —— 別拿「日志搜不到」當「沒發生」。
 * 所以 content 與 overlay 分成兩個 slot（見 [GlassBackdropHost]）。
 *
 * @param backdrop 折射來源。null ⇒ 實心降級。
 * @param pressFraction 0~1 按下程度：按下去折射更實、高光更亮。
 * @param liveness 採樣層存活判定；非 Alive ⇒ 不開真玻璃（這條是上一版缺的接線）。
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    material: BarMaterial = LocalBarMaterial.current,
    cornerRadius: Dp = 24.dp,
    pressFraction: Float = 1f,
    pressedScale: Float = 1f,
    style: GlassSurfaceStyle = GlassSurfaceStyle.CONTAINER,
    liveness: GlassLiveness.Verdict = LocalGlassLiveness.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val p = pressFraction.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(cornerRadius)
    val renderMode = rememberGlassRenderMode(material = material, liveness = liveness)

    // ── 降級外觀：實心 + 1dp 低透明描邊 ──────────────────────────
    //
    // 規範 §6.4：採樣為空時組件**不報錯**，只是鋪一層不透明底色。
    // 所以寧可主動退成一個過得去的外觀，也不要被動變成一塊突兀的黑/白。
    if (backdrop == null || renderMode == GlassRenderMode.FALLBACK) {
        val fallback = GlassLiveness.fallbackFor(
            isDark = isDark,
            surface = spec.surfaceTint.copy(alpha = if (isDark) 0.88f else 0.94f),
        )
        Box(
            modifier = modifier
                .background(color = fallback.containerColor, shape = shape)
                .border(fallback.strokeWidthDp.dp, fallback.strokeColor, shape),
            content = content,
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        // ★ 尺寸在組合期算好，傳進 effects。這是折射能不能生效的前提。
        val density = LocalDensity.current
        val sizePx = Size(
            with(density) { maxWidth.toPx() },
            with(density) { maxHeight.toPx() },
        )
        // ★ 材質三態都要真的走到：磨砂 = **只有模糊、不折射**。
        //   少了這一步，FROSTED 與 LIQUID 畫出來一樣，那個選項就是假的。
        //   判斷抽到 [styleFor] 是因為它必須能被單測覆蓋（繪製路徑裡的 when 測不到）。
        val effective = styleFor(material = material, style = style, frostBlur = spec.frostBlur)
        // ★ 光源方向也必須在組合期讀（`effects` 只跑一次；讀進 effects 裡就不會跟著重力轉）。
        val lightDir = rememberGravityLightDir(effective.lightDegrees)
        val highlight = gravityHighlight(lightDir = lightDir, style = effective, pressFraction = p)
        val surfaceColor = when (effective.surfaceColor) {
            GlassSurfaceColor.WHITE -> Color.White
            GlassSurfaceColor.SURFACE_CONTAINER -> MiuixTheme.colorScheme.surfaceContainer
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        // ① 色彩振動：透出來的東西更鮮，這是 iOS 那層「活」的感覺
                        if (effective.lensHeight > 0.dp) vibrancy()
                        // ② 只糊一點點（**引擎收 px，不收 Dp**）
                        if (effective.blur > 0.dp) blur(effective.blur.toPx())
                        // ③ 折射（庫自帶那條路徑）。depth / 色散兩個開關**與參考實現一致地關掉** ——
                        //    開著的時候真機上在折射帶內邊界出現一條 1px 暗線，見 [BarOptics.LENS_DEPTH]。
                        if (effective.lensHeight > 0.dp && effective.lensAmount > 0.dp) {
                            lens(
                                effective.lensHeight.toPx() * (0.7f + 0.3f * p),
                                effective.lensAmount.toPx() * p,
                                BarOptics.LENS_DEPTH,
                                BarOptics.LENS_DISPERSION,
                            )
                        }
                        // ④ 雙光源高光，主光方向跟著重力轉
                        if (highlight.alpha > 0f) {
                            bloomStrokeHighlight(
                                highlight = highlight,
                                shape = shape,
                                explicitSize = sizePx,
                            )
                        }
                    },
                    onDrawSurface = {
                        // 表面色要透。白感靠「模糊後的背景 + 高光」，不靠堆白漆。
                        drawRect(color = surfaceColor.copy(alpha = effective.surfaceAlpha))
                    },
                    layerBlock = {
                        if (pressedScale != 1f) {
                            scaleX = pressedScale
                            scaleY = pressedScale
                        }
                    },
                )
                // ★ 必須裁形狀：折射會往元件外抓像素，圓角之外那塊不裁會露出異色方塊
                //   （真機上表現為「底欄右邊多出來一塊白」）。
                .clip(shape),
            content = content,
        )
    }
}

/**
 * 玻璃折射來源：把 [content] 畫進一個可被採樣的層，[overlay] 裡的玻璃採樣它。
 *
 * ## 為什麼這裡帶一個「活不活」的判據
 *
 * 「座標非空」不等於「裡面有內容」—— 我們拿它當存活判據被騙過一次。
 * 這裡的正面證據是**這一層是不是真的被畫過、而且尺寸非零**（首幀 0×0 不算活著）。
 * 判定通過 [LocalGlassLiveness] 交給所有玻璃元件；頁離場時清回 [GlassLiveness.Verdict.Unknown]，
 * 於是「切頁之後玻璃糊成實心色」那條路徑被主動攔掉。
 *
 * ⚠️ **每個頁面一份 host**（外層用 `key(section)` 重建）。共享一份會被離場頁清掉座標，
 * 表現是「用著用著實時模糊沒了」。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val liveness = remember { mutableStateOf<GlassLiveness.Verdict>(GlassLiveness.Verdict.Unknown) }

    DisposableEffect(Unit) {
        // 離場就把結論收回來：下一頁不該沿用上一頁的「活著」。
        onDispose { liveness.value = GlassLiveness.Verdict.Unknown }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .drawWithContent {
                    drawContent()
                    val verdict = GlassLiveness.verdictOf(
                        contentWidthPx = size.width,
                        contentHeightPx = size.height,
                    )
                    // 只在結論真的變化時寫，避免逐幀重組。
                    if (liveness.value != verdict) liveness.value = verdict
                },
        ) {
            content()
        }
        CompositionLocalProvider(LocalGlassLiveness provides liveness.value) {
            overlay(backdrop)
        }
    }
}

/** 目前的採樣層存活判定。host 之外讀到 [GlassLiveness.Verdict.Unknown] ⇒ 走降級。 */
val LocalGlassLiveness = staticCompositionLocalOf<GlassLiveness.Verdict> {
    GlassLiveness.Verdict.Unknown
}

/** 目前的折射來源。 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

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

/** 玻璃底欄標準高度（量自參考：內容 62dp）。 */
val GlassBarHeight = 62.dp
