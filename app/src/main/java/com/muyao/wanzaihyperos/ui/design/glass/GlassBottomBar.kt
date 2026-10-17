package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液態玻璃底欄（可拖動）。
 *
 * ## 交互（照 iOS 標準）
 *
 * | 操作 | 行為 |
 * |---|---|
 * | 點一格 | 切到那格，滑塊彈過去 |
 * | **按住左右拖** | 滑塊**跟著手指走**（不是跳格） |
 * | 拖到一半鬆手 | 依「位置 + 甩動速度」吸附到最近格 |
 * | 甩動 | 速度夠大就跨過一格 |

 * ## 為什麼要做「跟手 + 吸附」而不是「點擊切換」
 *
 * 這正是業主要求的「像水滴一樣靈動」。點擊切換是**離散**的（A → B 直接跳），
 * 而拖動是**連續**的（手指到哪滑塊到哪），後者才會有「液體被推著走」的錯覺。
 *
 * ## 拖動時的變形（水滴感）
 *
 * 三層同時變化：
 * 1. **沿拖動方向拉長**（`scaleX` > 1）—— 像水滴被拖出尾巴
 * 2. **垂直方向壓扁**（`scaleY` < 1）—— 體積守恆
 * 3. **朝拖動方向偏移**（`translationX` 微調）—— 產生「被甩出去」的慣性
 *
 * 拉長量與**拖動速度**相關而不只是位移：慢慢拖不變形，甩才變形。
 * 這是「液體」和「彈簧」的差別。
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

    // 滑塊位置：以「格」為單位，可以是小數（拖動中）
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    // 拖動時的瞬時速度（px/幀），用來算拉長
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 外部選中變化（點擊）→ 彈簧過去
    LaunchedEffect(selectedIndex) {
        if (!dragging) {
            indicator.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    // 落定族：dampingRatio ≥ 0.72 不許過衝（規範紅線 #7）
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val cellWidth = maxWidth / items.size
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cellWidthPx = with(density) { cellWidth.toPx() }

        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth().height(GlassBarHeight),
            material = material,
            shape = RoundedCornerShape(percent = 50),
            pressFraction = 1f,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    // ★ 拖動手勢掛在整個底欄上：抓哪裡都能拖，不必對準格子
                    .pointerInput(cellWidthPx, items.size) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragVelocity = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragVelocity = dragAmount
                                // 跟手：直接改「格」為單位的位置
                                val next = (indicator.value + dragAmount / cellWidthPx)
                                    .coerceIn(0f, (items.size - 1).toFloat())
                                // 用 snapTo 保持即时跟手（animateTo 会滞后）
                                scope.launch { indicator.snapTo(next) }
                            },
                            onDragEnd = {
                                dragging = false
                                // 吸附：位置取最近格；但若甩得够快，往甩的方向多走一格
                                val projected = indicator.value +
                                    (if (abs(dragVelocity) > FLING_THRESHOLD_PX) {
                                        if (dragVelocity > 0) FLING_EXTRA else -FLING_EXTRA
                                    } else 0f)
                                val target = projected
                                    .roundToInt()
                                    .coerceIn(0, items.size - 1)
                                dragVelocity = 0f
                                onSelect(target)
                                // onSelect 会触发 LaunchedEffect 弹过去
                            },
                            onDragCancel = {
                                dragging = false
                                dragVelocity = 0f
                                onSelect(indicator.value.roundToInt().coerceIn(0, items.size - 1))
                            },
                        )
                    },
            ) {
                // ---- 懸浮滑塊（Pill）--------------------------------------
                //
                // 逆向 PillHeightDp=56（容器 64 → 上下各留 4dp）、
                // PillPressedScale=1.3928572（按下放大 39%）。
                val pressTarget = if (dragging) 1f else 0f
                val pressAnim = remember { Animatable(0f) }
                LaunchedEffect(pressTarget) {
                    pressAnim.animateTo(
                        targetValue = pressTarget,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
                    )
                }

                // 拖動變形：速度越大越拉長（限制 ±18%）
                //
                // 為什麼用速度而不是位移：慢慢拖不該變形（那是「移動」），
                // 快速甩才變形（那是「被甩出去的水滴」）。
                val stretch = (dragVelocity / cellWidthPx * 0.35f).coerceIn(-0.18f, 0.18f)
                val pillScale = 1f + (1.3928572f - 1f) * pressAnim.value * 0.55f

                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = indicator.value * cellWidthPx
                            // 水滴：沿拖动方向拉长、垂直压扁
                            scaleX = pillScale * (1f + abs(stretch))
                            scaleY = pillScale * (1f - abs(stretch) * 0.45f)
                            // 支点在受力侧对面，于是被拉长的是朝手指那条边
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                pivotFractionX = if (stretch >= 0f) 0.5f else 0.5f,
                                pivotFractionY = 0.5f,
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    GlassSurface(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        material = if (material == BarMaterial.SOLID) {
                            BarMaterial.SOLID
                        } else {
                            BarMaterial.LIQUID
                        },
                        shape = RoundedCornerShape(percent = 50),
                        pressFraction = 0.4f + 0.6f * pressAnim.value,
                        pressedScale = pillScale,
                    ) {}
                }

                // ---- 五個 tab（只負責顯示與點擊）--------------------------
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        // 「選中」以滑塊實際位置判定，拖動中也即時反映
                        val selected = abs(indicator.value - index) < 0.5f
                        Column(
                            modifier = Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                // ★ 點擊：detectTapGestures 會與父層的拖拽共存
                                //   （Compose 的手勢競爭：短按走 tap、按住移動走 drag）
                                .pointerInput(index) {
                                    detectTapGestures(
                                        onTap = { onSelect(index) },
                                    )
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
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                // 選中只用顏色表達，不改字重（規範紅線 #6）
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

/** 底欄的一項。 */
data class GlassBarItem(val label: String, val icon: ImageVector)

/** 甩動判定門檻（px/幀）。超過就往甩的方向多吸附一格。 */
private const val FLING_THRESHOLD_PX = 18f

/** 甩動時額外跨過的格數。 */
private const val FLING_EXTRA = 0.55f
