package com.muyao.wanzaihyperos.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning
import com.muyao.wanzaihyperos.ui.design.liquid.backdropIsLive
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.progressiveBlurBackdrop
import com.muyao.wanzaihyperos.ui.design.liquid.rememberSampleSettled
import androidx.compose.runtime.remember

/** Page-local source; never tint or sample the toolbar into itself. */
@Composable
fun TopVeil(
    progress: Float,
    source: Backdrop?,
    modifier: Modifier = Modifier,
    heightDp: Dp = Dims.barHeight + VEIL_TAIL,
) {
    val settled = rememberSampleSettled(source)
    val radius = LocalThemeCustomization.current.topBlur.toFloat() * .55f
    val alpha = AppColors.veilAlpha(progress)
    if (radius <= 0f || alpha <= 0f || !settled) return
    if (source == null || !backdropIsLive(source)) return
    Box(modifier.fillMaxWidth().height(heightDp)
        .progressiveBlurBackdrop(source, alpha, radius.coerceIn(0f, 32f).dp))
}

/** Short feather below the toolbar; content farther down remains sharp. */
private val VEIL_TAIL = 4.dp

/**
 * 把 [androidx.compose.foundation.lazy.LazyListState] 的滾動位置換成
 * [TopVeil] 要的 `progress`（0~1）。
 *
 * ## 為什麼不是「已滾 / 總高度」
 *
 * 兩個原因：
 *
 * 1. **總高度在清單很長時會很大**，於是「滾了兩屏，progress 才 0.1」——
 *    紗幾乎不動，看不出漸變。而使用者感知的是「滾了多少」，
 *    前兩屏就該從無到有。
 * 2. 總高度要等所有 item 量完才知道，**首幀拿不到**（會是 0 → 除零）。
 *
 * 所以用「已滾離頂部的距離 / 一屏高度」——超過一屏就視為 1（紗全出）。
 * 這也符合直覺：往下滑一屏，頂欄就該完全「實」了。
 *
 * ## 為什麼回傳 `State<Float>` 而不是 Float
 *
 * 滾動每幀都變。用 `derivedStateOf` 包起來，只有**結果真的變了**才觸發重組
 *（例如從 0.30 到 0.31 可能不影響視覺，但每幀重組整個頁面會掉幀）。
 */
@Composable
fun rememberScrollProgress(
    state: androidx.compose.foundation.lazy.LazyListState,
): State<Float> {
    return remember(state) { derivedStateOf {
        if (state.firstVisibleItemIndex > 0) return@derivedStateOf 1f
        val viewport = state.layoutInfo.viewportEndOffset.coerceAtLeast(1)
        (state.firstVisibleItemScrollOffset.toFloat() / viewport).coerceIn(0f, 1f)
    } }
}

/**
 * 同上，但給 `Modifier.verticalScroll` 的 [androidx.compose.foundation.ScrollState]。
 *
 * 為什麼要有這支：`LazyColumn` 那支吃的是 `LazyListState`，而**短頁面**（關於、開源許可、
 * 品牌與圖標、取景）用的是普通 `Column + verticalScroll` —— 它們一樣會把內容捲到頂欄下面，
 * 卻拿不到 progress，於是頂欄底下沒有紗、字直接壓在標題上。
 *
 * 分母同樣取「一屏高度」（[androidx.compose.foundation.ScrollState.viewportSize]），
 * 超過一屏即視為 1，與 LazyColumn 那支的行為一致。
 */
@Composable
fun rememberScrollProgress(
    state: androidx.compose.foundation.ScrollState,
): State<Float> {
    return derivedStateOf {
        val viewport = state.viewportSize.coerceAtLeast(1)
        (state.value.toFloat() / viewport).coerceIn(0f, 1f)
    }
}
