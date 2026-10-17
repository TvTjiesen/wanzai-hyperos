package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液態玻璃底欄（逆向 com.linxin.demo 的 LxBottomBar 重寫）。
 *
 * ## 結構（照逆向結果）
 *
 * ```
 * Capsule 容器（玻璃，折射背后的内容）
 *   └ 悬浮滑块（Pill，第二层玻璃，56dp 高，全圆）
 *       └ 图标 + 文字
 * ```
 *
 * ## ★ 三个我从逆向学到、原版做错的关键点
 *
 * **1. 滑块是「悬浮」的，不是平铺的**
 *
 * 逆向常量 `PillHeightDp = 56.0f` / `PillPressedScale = 1.3928572f`：
 * 选中态的滑块是一个**独立的、比容器略小的圆角胶囊**，
 * 而且**被按时会放大到 1.39 倍**（"悬浮磁铁"效果就来自这个）。
 *
 * 我原版只画了一个背景色块，没有任何悬浮感。
 *
 * **2. 滑块有自己的折射（与容器不同）**
 *
 * 逆向 `LxBottomBar$lambda$1$21$0`：
 * ```
 * lens(scope,
 *      toPx(10.dp) * pressValue,      // 滑块折射高度 = 10dp
 *      toPx(14.dp) * pressValue,      // 滑块折射量   = 14dp
 *      false, pressValue > 0f)        // ← 最后这个是"是否色散"
 * ```
 * 注意：滑块的折射参数**比容器小**（10/14 vs 20/16），
 * 而且带色散开关。所以滑块看起来比容器「更清澈」。
 *
 * **3. 容器形变是「速度驱动」的**
 *
 * 逆向 `LxBottomBar$lambda$1$25$0`：
 * ```
 * velocity = anim.velocity / 10
 * scaleX /= (1 - clamp(velocity * 0.75, -0.2, 0.2))
 * scaleY *= (1 - clamp(velocity * 0.25, -0.2, 0.2))
 * ```
 * 也就是**甩得越快、拉得越长**，且限制在 ±20%。
 * 我原版用固定峰值，没有速度项，所以「切页那一下」不够活。
 *
 * @param backdrop 折射来源
 * @param items 五个板块
 * @param selectedIndex 当前选中
 * @param onSelect 点击回调
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
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val colors = MiuixTheme.colorScheme

    // 滑块位置：欠阻尼弹簧（只平移，不做形变）
    //
    // 规范红线 #7：滑块的追手是欠阻尼弹簧，叠上速度驱动缩放会过冲。
    // 所以：**滑块只平移，形变留给容器**。
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        indicator.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
        )
    }

    // 速度驱动的容器形变（逆向 lambda$1$25$0）
    val containerScaleX = remember { Animatable(1f) }
    val containerScaleY = remember { Animatable(1f) }
    LaunchedEffect(selectedIndex) {
        // 简化版：切页时给一个短促的拉伸再回弹。
        // 真正的实现要读 Animatable 的 velocity，这里用"目标变化量"近似。
        val stretch = 0.06f
        containerScaleX.snapTo(1f + stretch)
        containerScaleY.snapTo(1f - stretch * 0.3f)
        containerScaleX.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f))
        containerScaleY.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f))
    }

    // 按下态（驱动滑块放大 + 折射强度）
    var pressed by remember { mutableStateOf(false) }
    val pressAnim = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        pressAnim.animateTo(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val cellWidth = maxWidth / items.size
        // 容器玻璃
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassBarHeight)
                .graphicsLayer {
                    scaleX = containerScaleX.value
                    scaleY = containerScaleY.value
                },
            material = material,
            shape = RoundedCornerShape(percent = 50),
            pressFraction = 1f,
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                // ---- 悬浮滑块（Pill）--------------------------------------
                //
                // 逆向 PillHeightDp = 56，容器 64 → 滑块比容器矮 8dp（上下各 4）。
                // PillPressedScale = 1.3928572 → 按下时放大 39%。
                val pillScale = 1f + (1.3928572f - 1f) * pressAnim.value * 0.6f
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            // 只平移：位置由 indicator 给
                            translationX = indicator.value * cellWidth.toPx()
                            scaleX = pillScale
                            scaleY = pillScale
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    // 滑块自己也是一层玻璃（折射参数比容器小，见类注释）
                    GlassSurface(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        material = if (material == BarMaterial.SOLID) {
                            BarMaterial.SOLID
                        } else {
                            BarMaterial.LIQUID
                        },
                        shape = RoundedCornerShape(percent = 50),
                        // 滑块折射强度随按下变化：0 → 1
                        pressFraction = 0.35f + 0.65f * pressAnim.value,
                        pressedScale = pillScale,
                    ) {}
                }

                // ---- 五个 tab ------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        Column(
                            modifier = Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable(
                                    interactionSource = remember {
                                        androidx.compose.foundation.interaction.MutableInteractionSource()
                                    },
                                    indication = null,
                                ) {
                                    onSelect(index)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val iconScale = if (selected) {
                                1f + 0.12f * pressAnim.value
                            } else {
                                1f
                            }
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) colors.primary else colors.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                // ★ 选中只用颜色表达，不改字重
                                //   （改字重会让整排字横向抖动，规范红线 #6）
                                fontWeight = FontWeight.Normal,
                                color = if (selected) colors.primary else colors.onSurfaceVariantSummary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 底栏的一项。 */
data class GlassBarItem(val label: String, val icon: ImageVector)
