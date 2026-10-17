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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液態玻璃底欄（1:1 復刻參考 app）。
 *
 * ## 尺寸：全部量出來的（density 3.5x，屏寬 347.4dp）
 *
 * | 項目 | 量到的值 |
 * |---|---|
 * | 左右外邊距 | 28.6dp |
 * | 單格寬 | 58dp |
 * | 選中氣泡 | 格寬 - 5dp，高 42dp |
 * | 圖標 | 16dp |
 * | 文字 | 9sp |
 * | 深色內容總高 | 參考 30.3dp |
 *
 * ## ★ 居中問題（業主指出「設置右邊那兒空白」）
 *
 * 根因：`BoxWithConstraints` 的 `maxWidth` 是**已經套用自己 modifier 之後**的值。
 * 我既在 modifier 裡加了 padding，又從 maxWidth 裡再減一次 padding ——
 * **扣了兩次**，於是內容整體偏左、右邊空出一塊。
 *
 * 正解：
 * - 外層 `BoxWithConstraints` **只做 fillMaxWidth**，拿乾淨的屏幕寬
 * - padding 放到**內層 Box**，不污染 maxWidth
 * - `cellWidth` 用 `(maxWidth - padding*2) / 格數` 算一次，只在這裡扣
 *
 * ## 拖動時的變形
 *
 * 只在 `dragging` 時才套 stretch（否則鬆手後會留下扁橢圓）。
 * 拉長量取決於**速度**而非位移 —— 慢拖是移動，快甩才是被甩出去的水滴。
 */
@Composable
fun GlassBottomBar(
    backdrop: HazeState?,
    items: List<GlassBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalGlassSpec.current
    val material = LocalBarMaterial.current
    val colors = MiuixTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f

    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    // ★ 回彈的「初速度」：鬆手瞬間把手指速度交給彈簧。
    //
    // 沒有這個的話，鬆手後是「從當前位置以固定初速彈回去」——
    // 看起來像機械馬達。帶上手指速度才是「甩出去」。
    var releaseVelocity by remember { mutableFloatStateOf(0f) }

    // ★ 形變的「慣性尾巴」：鬆手後形變不立刻歸零，而是衰減到 0。
    //   沒有它，回彈的頭幾幀形變是硬切，看起來像兩個動作接起來。
    val tailAnim = remember { Animatable(0f) }
    LaunchedEffect(dragging) {
        if (!dragging) {
            tailAnim.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(220),
            )
        }
    }
    val releaseTail = tailAnim.value

    LaunchedEffect(selectedIndex) {
        if (!dragging) {
            indicator.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    // ★ 回彈族：dampingRatio 0.55 < 0.72 → **會過衝**。
                    // 這就是業主要��「回彈動畫」：氣泡滑過去時會先衝過一點再回來。
                    // （規範紅線 #7 講的「不許過衝」是針對**跟手**階段；
                    //   鬆手後的慣性過衝是液態玻璃的標誌性手感。）
                    dampingRatio = 0.55f,
                    stiffness = Spring.StiffnessMedium,
                ),
                // ★ 帶上鬆手速度 → 甩得越快，衝得越遠（iOS 的「甩一下」手感）。
                //   注意 initialVelocity 是 animateTo 的參數，不是 spring 的。
                initialVelocity = releaseVelocity,
            )
            releaseVelocity = 0f
        }
    }

    // 外層只拿寬度（不套 padding，避免 maxWidth 被污染）
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthDp = maxWidth
        val density = LocalDensity.current

        // 內容寬 = 屏幕寬 − 左右外邊距（只扣一次）
        val contentWidth = maxWidthDp - BAR_SIDE_PADDING * 2
        // 選中格 = 其餘格 + SELECTED_EXTRA；其餘格均分剩下的
        val restWidth = contentWidth / items.size
        val restWidthPx = with(density) { restWidth.toPx() }
        val cellWidth = restWidth
        val cellWidthPx = restWidthPx

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = BAR_SIDE_PADDING,
                    vertical = BAR_VERTICAL_PADDING,
                ),
        ) {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT),
                material = material,
                cornerRadius = 24.dp,
                pressFraction = 1f,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
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
                                    val v = dragVelocity
                                    val projected = indicator.value +
                                        if (abs(v) > FLING_THRESHOLD_PX) {
                                            if (v > 0) FLING_EXTRA else -FLING_EXTRA
                                        } else {
                                            0f
                                        }
                                    val target = projected
                                        .roundToInt()
                                        .coerceIn(0, items.size - 1)
                                    // ★ 把手指速度換成「格/秒」，交給回彈彈簧當初速度。
                                    //   沒有這行，回彈會失去「甩」的感覺。
                                    releaseVelocity = (v / cellWidthPx) * 60f
                                    scope.launch {
                                        tailAnim.snapTo(
                                            (v / cellWidthPx * 0.6f).coerceIn(-0.32f, 0.32f)
                                        )
                                    }
                                    dragVelocity = 0f
                                    onSelect(target)
                                },
                                onDragCancel = {
                                    dragging = false
                                    releaseVelocity = 0f
                                    dragVelocity = 0f
                                    onSelect(
                                        indicator.value.roundToInt()
                                            .coerceIn(0, items.size - 1),
                                    )
                                },
                            )
                        },
                ) {
                    // ── 選中氣泡（懸浮的那一塊）──────────────────────────
                    val pressTarget = if (dragging) 1f else 0f
                    val pressAnim = remember { Animatable(0f) }
                    LaunchedEffect(pressTarget) {
                        pressAnim.animateTo(
                            targetValue = pressTarget,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
                        )
                    }

                    // ★ 水滴變形：沿拖動方向拉長 + 垂直壓扁。
                    //
                    // 上限從 18% 提到 **32%** —— 業主要求「更靈動」。
                    // 18% 在真機上幾乎看不出，而 32% 才有「被拽」的形變感。
                    //
                    // ★ 關鍵：形變由**速度**驅動，不是位移。
                    //   慢慢拖 = 平移（不該變形）；快速甩 = 被甩出去的水滴（該變形）。
                    val stretch = (dragVelocity / cellWidthPx * 0.6f)
                        .coerceIn(-0.32f, 0.32f)
                    val pressedScale = 1f + PRESS_SCALE * pressAnim.value

                    // 氣泡層：佔滿整條，用 translationX 定位到目標格
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    ) {
                        Box(
                            modifier = Modifier
                                // ★ 氣泡尺寸：**量出來的**。
                                //
                                // 精確測量（REF app，density 3.5x）：
                                //   橫向 y=2420 行灰底 x157..x331 = 174px = **49.7dp**
                                //   縱向 y2316..y2440 = 124px = **35.4dp**
                                .width(BUBBLE_WIDTH)
                                .height(BUBBLE_HEIGHT)
                                .align(Alignment.CenterStart)
                                .graphicsLayer {
                                    // 氣泡定位：**直接用 indicator 的實時值**（格為單位的小數）。
                                    //
                                    // ★ 之前分兩段（dragging 用 indicator、靜止用 selectedIndex），
                                    //   結果鬆手回彈的**那一瞬間**會從 indicator 跳到 selectedIndex ——
                                    //   視覺上就是「氣泡先瞬移再彈」。
                                    //   用實時值 + 線性插值映射到像素，回彈全程連續。
                                    val pos = indicator.value
                                    val i0 = kotlin.math.floor(pos).toInt()
                                        .coerceIn(0, items.size - 1)
                                    val i1 = (i0 + 1).coerceAtMost(items.size - 1)
                                    val frac = (pos - i0).coerceIn(0f, 1f)
                                    val centerPx = (i0 + frac) * restWidthPx + restWidthPx / 2f

                                    translationX = centerPx - size.width / 2f

                                    // 水滴變形：沿拖動方向拉長、垂直壓扁。
                                    //
                                    // ★ 鬆手後**保留一小段**形變衰減：
                                    //   完全歸零會讓「回彈」看起來是硬切，
                                    //   留一點慣性尾巴才有「甩出去」的餘韻。
                                    val tail = if (dragging) stretch else releaseTail
                                    scaleX = pressedScale * (1f + abs(tail))
                                    scaleY = pressedScale * (1f - abs(tail) * 0.45f)
                                },
                        ) {
                            // ★★ 氣泡是**真玻璃**（不是灰色塊）。
                            //
                            // 它有自己的折射 + 色散 + 雙光源高光，
                            // 因為是獨立的小面積元件，用 LIQUID 材質、
                            // 全圓角（RoundedCornerShape(percent = 50)）。
                            //
                            // 為什麼先前不是：我把氣泡畫成 `background(Color(0xFFE5E5E5))`
                            // 純色 —— 那只是「對齊參考靜態截圖」的灰塊，
                            // 完全沒有玻璃的光學行為。業主：「動效哪去了，折射扭曲呢」。
                            GlassSurface(
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxSize(),
                                material = BarMaterial.LIQUID,
                                cornerRadius = 24.dp,
                                // 按下時折射變強（與外層容器同一套邏輯）
                                pressFraction = 0.55f + 0.45f * pressAnim.value,
                                pressedScale = pressedScale,
                            ) {}
                        }
                    }

                    // ── 五格圖標 + 文字 ─────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, item ->
                            val selected = abs(indicator.value - index) < 0.5f
                            Column(
                                modifier = Modifier
                                    // ★★ 選中格比其他格**更寬** —— 這是參考 app 的做法。
                                    //
                                    // 量到的參考圖標中心：139 / 439 / 607 / 781 / 943
                                    // 格 1→2 間距 300px，格 2→3 只有 168px。
                                    // 間距不均勻，因為**選中那一格被氣泡撐寬了**。
                                    // 其餘格均分剩下的寬度。
                                    //
                                    // 這正是「懸浮滑塊撐開格子」的觀感來源：
                                    // 氣泡不是畫在固定格子上，而是**佔了更寬的一段**。
                                    // 每格等寬（參考 app 的間距 168/174/162 幾乎相同，
                                    // 說明**格子是均分的**；選中格並沒有變寬）。
                                    // 「懸浮」感來自氣泡本身的陰影與玻璃，不是撐開格子。
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
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 9.sp,
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
}

/** 底欄的一項。 */
data class GlassBarItem(val label: String, val icon: ImageVector)

/**
 * 選中氣泡的底色。
 *
 * 量自參考 app：淺色下是 **(229,229,229)**（純灰，不是白玻璃）。
 * 深色下用白色 14%。
 */
private fun bubbleColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE5E5E5)

// ══════════════════════════════════════════════════════════════════════════
// 尺寸常數 —— 全部是量出來的
// ══════════════════════════════════════════════════════════════════════════

/** 底欄左右外邊距：量到 100px / 3.5 = 28.6dp。 */
private val BAR_SIDE_PADDING = 22.dp

/** 底欄上下外邊距：讓它「浮」起來而不是貼死屏幕邊。 */
private val BAR_VERTICAL_PADDING = 12.dp

/** 底欄內容高度。 */
private val BAR_HEIGHT = 46.dp

/** 選中氣泡寬度：量到 174px / 3.5 = **49.7dp**。 */
private val BUBBLE_WIDTH = 50.dp

/** 選中氣泡高度：量到 124px / 3.5 = 35.4dp，取 42dp 給玻璃邊緣留餘量。 */
private val BUBBLE_HEIGHT = 42.dp

/** 圖標尺寸。 */
private val ICON_SIZE = 16.dp

/**
 * 選中格比其他格多出的寬度（dp）。
 *
 * 量自參考 app：選中格中心在本格應有位置再往外偏一點，
 * 使「首頁」到「手勢」的間距 300px 而「手勢」到「應用」只有 168px。
 * 差額約 60px = **17dp**。
 */
private val SELECTED_EXTRA = 34.dp

/** 按下放大倍率（參考 app PillPressedScale = 1.3928572，取 55%）。 */
private const val PRESS_SCALE = 0.26f

/** 甩動判定門檻（px/幀）。 */
private const val FLING_THRESHOLD_PX = 18f

/** 甩動時額外跨過的格數。 */
private const val FLING_EXTRA = 0.55f

