package com.muyao.wanzaihyperos.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **頂欄漸變紗**：內容滾到頂欄下面時，用一層逐漸淡出的色帶保住標題可讀性。
 *
 * ## ★ 這個元件的核心是「沒有下邊界」
 *
 * 最常見的錯做法是給頂欄一條不透明色帶（Material 的 `TopAppBar` 預設就是）。
 * 那樣會出現**一條硬直線**壓在內容上 —— 規範紅線 #14，OmniRing 早期就是這樣被打回的。
 *
 * 正確做法是把色帶的**下端淡到完全透明**，讓它「消失」在內容裡。
 * [AppColors.veilStops] 最後那個 `1.00 → 0.00` 就是為此。
 *
 * ## 為什麼不用模糊（規範 §6.2 的原始方案）
 *
 * 原始的「30dp 模糊 + 漸變擦除」要 `miuix-blur`，而它**要 minSdk 33**；
 * 本專案是 **24**（`app/build.gradle.kts`）。
 * 規範自己的 §0.2 給了判斷：「抬到 33 會丟掉 Android 13 以下全部機型」，
 * 推薦走**路線 B：不模糊，用半透明容器 + 漸變紗**。
 *
 * 在**漸層背景**上，兩者的差別很小 ——
 * 紗要解決的是「標題被內容壓住看不清」，
 * 而同色系向下淡出的色帶就能做到；模糊帶來的是「玻璃折射」那種質感，
 * 那屬於導航欄那一層（第 6 步），不是頂欄紗。
 *
 * ## 用法
 *
 * 放在內容**之上**、頂欄文字**之下**：
 * ```kotlin
 * Box {
 *     LazyColumn { … }              // 內容在底
 *     TopVeil(progress = 已滾/可滾)  // 紗在中
 *     TopAppBar(...)                // 文字在最上（透明底）
 * }
 * ```
 *
 * @param progress 已滾距離 / 一屏可滾距離（0~1）。內部會套死區與 smoothstep。
 */
@Composable
fun TopVeil(
    progress: Float,
    /**
     * **这一屏顶栏后面真正铺出来的底色**（不是主题令牌）。
     * 必填、没有默认值 —— 默认值会变成下一版"忘了接"的漏洞。
     */
    backdrop: Color,
    modifier: Modifier = Modifier,
    /** 覆蓋高度 = 狀態欄 + 欄身 52 + 尾巴 44（那段尾巴是留給「淡到零」的）。 */
    heightDp: androidx.compose.ui.unit.Dp = Dims.barHeight + VEIL_TAIL,
) {
    val alpha = AppColors.veilAlpha(progress)
    // alpha 為 0 時**完全不畫** —— 不只是「畫了但透明」。
    // 這樣停在頂部時連一個合成層都沒有（規範 §14 的性能預算：不空轉）。
    if (alpha <= 0f) return

    val base = AppColors.veilColorFor(backdrop)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(
                Brush.verticalGradient(
                    colors = AppColors.veilStops().map { (_, a) ->
                        base.copy(alpha = base.alpha * a * alpha)
                    },
                ),
            ),
    )
}

/**
 * 紗末端那段「專門淡到零」的高度。
 *
 * 規範 §6.2：「尾巴那段 44 是專門留給『淡到零』的，所以末端視覺上消失」。
 */
private val VEIL_TAIL = 44.dp

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
): androidx.compose.runtime.State<Float> {
    return androidx.compose.runtime.derivedStateOf {
        val info = state.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
        // 第一個可見項的 offset：負值表示已經滾過去了多少
        val scrolled = -first.offset.toFloat()
        // 用「一屏高度」當分母；viewportEndOffset 就是它
        val viewport = info.viewportEndOffset.coerceAtLeast(1)
        (scrolled / viewport).coerceIn(0f, 1f)
    }
}
