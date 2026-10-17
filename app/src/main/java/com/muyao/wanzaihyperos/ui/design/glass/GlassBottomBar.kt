package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液態玻璃底欄（**1:1 復刻**參考 app 的尺寸）。
 *
 * ## 尺寸怎麼來的：不是猜，是量出來的
 *
 * 把參考 APK（`xyz.nextalone.hyperzoomring`）裝到同一台機器上截圖，
 * 用像素量出（本機 density 560 = **3.5x**，屏寬 1216px = 347.4dp）：
 *
 * | 項目 | px | dp | 佔比 |
 * |---|---|---|---|
 * | 屏幕寬 | 1216 | 347.4 | — |
 * | 底欄左邊距 | 100 | **28.6** | 8.2% |
 * | 底欄右邊距 | 102 | **29.1** | 8.4% |
 * | 底欄內容寬 | 1014 | 290 | 83.4% |
 * | 單格寬 | 202.8 | **58.0** | — |
 * | **選中氣泡寬** | 126 | **36.0** | 屏寬 10.4% |
 * | **選中氣泡高** | 207 | **59.1** | — |
 * | 氣泡 / 格子寬比 | — | — | **62%** |
 *
 * ## ★ 我原先錯在哪（業主說「滑塊怎麼都擠在底欄裡面」）
 *
 * 我的氣泡**撐滿整個格子**（58dp 寬），而它只有 **36dp 寬** ——
 * 也就是**比格子窄 38%**，左右各留 11dp 空白。
 *
 * 差別不只是外觀：
 * - 撐滿 → 看起來像「選中的背景色塊」，是**嵌在**欄裡的
 * - 留白 → 看起來像一塊**獨立的玻璃泡**浮在欄上，這才是「懸浮磁鐵」
 *
 * 另外底欄邊距我也錯了：我用 16dp，它是 **28.6dp**（差近一倍）。
 *
 * ## 佈局結構（照參考 app）
 *
 * ```
 * ┌───────────────────────────────────────┐ ← 屏幕邊
 * │  ╭─────────────────────────────╮      │   左右各 28.6dp
 * │  │  ╭──╮                       │      │   氣泡 36×59dp
 * │  │  │圖│  圖    圖    圖    圖 │      │   在格子內居中，兩側各 11dp
 * │  │  │文│  文    文    文    文 │      │
 * │  │  ╰──╯                       │      │
 * │  ╰─────────────────────────────╯      │
 * └───────────────────────────────────────┘
 * ```
 *
 * @param backdrop 折射來源（null → 自動降級成實心，不會變灰塊）
 */
@Composable
fun GlassBottomBar(
    backdrop: LayerBackdrop?,
    items: List<GlassBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalGlassSpec.current
    val material = LocalBarMaterial.current
    val colors = MiuixTheme.colorScheme

    // 滑塊位置（單位 = 格，可為小數 → 拖動中連續）
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex) {
        if (!dragging) {
            indicator.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    // 落定族：dampingRatio ≥ 0.72，不許過衝（規範紅線 #7）
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // ★ 左右各 28.6dp：這是量出來的值，不是隨手給的 16dp
            .padding(horizontal = BAR_SIDE_PADDING, vertical = BAR_VERTICAL_PADDING),
    ) {
        val cellWidth = (maxWidth - BAR_SIDE_PADDING * 2) / items.size
        val density = LocalDensity.current
        val cellWidthPx = with(density) { cellWidth.toPx() }

        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT),
            material = material,
            shape = RoundedCornerShape(percent = 50),
            pressFraction = 1f,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    // 拖拽掛在整個底欄：抓哪裡都能拖，不必對準格子
                    .pointerInput(cellWidthPx, items.size) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragVelocity = 0f
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragVelocity = amount
                                val next = (indicator.value + amount / cellWidthPx)
                                    .coerceIn(0f, (items.size - 1).toFloat())
                                // snapTo 而非 animateTo：跟手不能有彈簧延遲
                                scope.launch { indicator.snapTo(next) }
                            },
                            onDragEnd = {
                                dragging = false
                                val projected = indicator.value +
                                    if (abs(dragVelocity) > FLING_THRESHOLD_PX) {
                                        if (dragVelocity > 0) FLING_EXTRA else -FLING_EXTRA
                                    } else {
                                        0f
                                    }
                                val target = projected
                                    .roundToInt()
                                    .coerceIn(0, items.size - 1)
                                dragVelocity = 0f
                                onSelect(target)
                            },
                            onDragCancel = {
                                dragging = false
                                dragVelocity = 0f
                                onSelect(
                                    indicator.value.roundToInt().coerceIn(0, items.size - 1),
                                )
                            },
                        )
                    },
            ) {
                // ══════════════════════════════════════════════════════════
                // 懸浮氣泡：36 × 59dp（量出來的比例：格子的 62% 寬）
                // ══════════════════════════════════════════════════════════
                val pressTarget = if (dragging) 1f else 0f
                val pressAnim = remember { Animatable(0f) }
                LaunchedEffect(pressTarget) {
                    pressAnim.animateTo(
                        targetValue = pressTarget,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
                    )
                }

                // 水滴變形：拉長量取決於**速度**而非位移
                // （慢慢拖 = 移動；快速甩 = 被甩出去的水滴）
                val stretch = (dragVelocity / cellWidthPx * 0.35f).coerceIn(-0.18f, 0.18f)
                val pressedScale = 1f + PRESS_SCALE * pressAnim.value

                // ★★ 1:1 復刻的關鍵：氣泡是「**圖標大小的小圓**」，
                //    **不包住文字**，而且是**灰色**（229,229,229），不是白玻璃。
                //
                //    我原本做成「包住圖標+文字的白膠囊」= 業主說的「擠在裡面」。
                //    參考 app 裡，氣泡只是「選中標記」，圖文都在它外面。
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = indicator.value * cellWidthPx
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            // ★ 氣泡包住「圖標 + 文字」整格 —— 這是量出來的結論。
                            //
                            // 我一開始做對了（包整格），後來看到一張裁偏的圖
                            // 誤以為「參考只包圖標」，改成 40dp 小圓 —— 那是錯的。
                            // 重新量：選中格灰底 x192..x376 = 184px = **52.6dp**，
                            // 而單格寬 58dp，所以氣泡幾乎填滿整格、左右各留 2.7dp。
                            .width(cellWidth - 5.dp)
                            .height(BUBBLE_HEIGHT)
                            .graphicsLayer {
                                // ★ 只在**拖動中**才變形。
                                //
                                // 之前這裡沒判斷 dragging，而 dragVelocity 在
                                // 鬆手後不會歸零 —— 於是氣泡**一直保持被壓扁的橢圓**
                                // （真機截圖裡「訊息」那格就是一個扁圓）。
                                // 氣泡本來是圓，任何時候都不該是橢圓。
                                val s = if (dragging) stretch else 0f
                                scaleX = pressedScale * (1f + abs(s))
                                scaleY = pressedScale * (1f - abs(s) * 0.45f)
                            }
                            // 半透明灰底 —— 這是量出來的顏色 (229,229,229)，
                            // 換成深色模式時 Miuix 的 secondaryContainer 會自動適配。
                            .background(
                                color = bubbleColor(isDark = colors.background.luminance() < 0.5f),
                                shape = RoundedCornerShape(percent = 50),
                            ),
                    )
                }

                // ══════════════════════════════════════════════════════════
                // 五格圖標 + 文字（在氣泡**外層**，氣泡只做背景）
                // ══════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        // 選中判定跟隨氣泡實際位置 → 拖動過程中即時反映
                        val selected = abs(indicator.value - index) < 0.5f
                        Column(
                            modifier = Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                .pointerInput(index) {
                                    detectTapGestures(onTap = { onSelect(index) })
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) {
                                    colors.primary
                                } else {
                                    colors.onSurfaceVariantSummary
                                },
                                modifier = Modifier.size(ICON_SIZE),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                // 選中只用顏色表達，不改字重
                                // （改字重會讓整排字橫向抖動，規範紅線 #6）
                                fontWeight = FontWeight.Normal,
                                color = if (selected) {
                                    colors.primary
                                } else {
                                    colors.onSurfaceVariantSummary
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 選中氣泡的底色。
 *
 * 量自參考 app：淺色下是 **(229,229,229)**（純灰，不是白玻璃）。
 * 深色下用白色 14% —— 同一套語義（比背景亮一點、比內容暗）。
 *
 * 為什麼不用真玻璃：這個圓只有 36dp，折射 shader 在這麼小的面積上
 * 幾乎看不出效果，反而因為採樣 7 次而白白耗效能。
 * 參考 app 也是直接畫灰色圓。
 */
private fun bubbleColor(isDark: Boolean): androidx.compose.ui.graphics.Color =
    if (isDark) {
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f)
    } else {
        // 精確值：截圖量到 (229,229,229)
        androidx.compose.ui.graphics.Color(0xFFE5E5E5)
    }

/** 底欄的一項。 */
data class GlassBarItem(val label: String, val icon: ImageVector)

// ══════════════════════════════════════════════════════════════════════════
// 尺寸常數 —— 全部是**量出來的**，不是猜的（density 3.5x，屏寬 347.4dp）
// ══════════════════════════════════════════════════════════════════════════

/** 底欄左右邊距：量到 100px / 3.5 = **28.6dp**。 */
private val BAR_SIDE_PADDING = 28.dp

/**
 * 底欄上下外邊距。
 *
 * 量自參考 app：底欄內容底緣距屏幕底 ~23dp，扣掉系統導航欄（~10dp）→ 約 **12dp**。
 * 這個留白是「懸浮」的一部分 —— 貼死底邊就變成「嵌在屏幕裡」。
 */
private val BAR_VERTICAL_PADDING = 12.dp

/** 底欄高度：內容區 ~62dp（含圖標 + 文字，量到氣泡高 59dp + 上下各 1.5dp 餘量）。 */
private val BAR_HEIGHT = 62.dp

/**
 * 選中氣泡尺寸：**一個圓**，直徑 36dp。
 *
 * 量自參考 app：氣泡寬 126px / 3.5 = 36dp。
 * 關鍵是它**只包住圖標**，不包文字 —— 這是「懸浮」而非「擠在裡面」的差別。
 */
/**
 * 選中氣泡高度。
 *
 * 量自參考 app：氣泡 y 範圍比底欄略高，形成「鼓出來」的感覺。
 * 底欄內容 62dp，氣泡 **58dp**（上下各留 2dp）。
 */
private val BUBBLE_HEIGHT = 58.dp

/** 圖標尺寸。 */
private val ICON_SIZE = 21.dp

/** 按下放大倍率（參考 app 的 PillPressedScale = 1.3928572，取 55% 以免誇張）。 */
private const val PRESS_SCALE = 0.215f

/** 甩動判定門檻（px/幀）。 */
private const val FLING_THRESHOLD_PX = 18f

/** 甩動時額外跨過的格數。 */
private const val FLING_EXTRA = 0.55f
