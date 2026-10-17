package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import com.muyao.wanzaihyperos.ui.design.Motion
import com.muyao.wanzaihyperos.ui.design.SmearMath
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **液態玻璃底欄**（iOS 26/27 風格，五個板塊）。
 *
 * ## 視覺結構
 *
 * ```
 * ┌──────────────────────────────────────┐
 * │  ╭──────╮                             │  ← 選中態：玻璃膠囊（浮起）
 * │  │ 圖示  │   圖示   圖示   圖示   圖示 │
 * │  ╰──────╯                             │
 * └──────────────────────────────────────┘
 *      整條是玻璃（會折射背後內容）
 * ```
 *
 * 兩層玻璃，**糊度必須不同**（規範 §6.1：「相鄰兩塊玻璃的糊度必須不同，
 * 否則糊成一片、看不出層次」）：
 * - **容器**：模糊小（4dp）、折射全量；
 * - **滑塊膠囊**：模糊是容器的 **2.2 倍**、折射是**一半**。
 *
 * ## 為什麼滑塊只平移、不做形變
 *
 * 規範紅線 #7：滑塊的追手是欠阻尼彈簧，疊上「速度驅動的縮放」會來回過衝 ——
 * 那就是業主說的「奇怪的抖動」。**形變留給容器**（見 [barSmear]）。
 *
 * @param backdrop 折射來源。null → 自動降級成實心（不會變灰塊）。
 * @param items 五個板塊
 * @param selectedIndex 目前選中
 * @param onSelect 點擊回調
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
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val colors = MiuixTheme.colorScheme

    // ---- 滑塊位置（彈簧驅動，只平移）------------------------------------
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        indicator.animateTo(
            targetValue = selectedIndex.toFloat(),
            // 落定族：dampingRatio ≥ 0.72（不許過衝）
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.78f,
                stiffness = 560f,
            ),
        )
    }

    // ---- 容器形變（規範 §6.3）--------------------------------------------
    //
    // ⚠️ 形變量要**反算**，不能直接把「格數差」當輸入。
    // 第一版（B20 前身）給 0.8，實際 smear 只有 0.16，scaleX 只有 1.0048 ——
    // 1123px 上 5px，量不出來，看起來就是「沒生效」。
    // 這是`SmearMath.smear` 的分母造成的：lead/(tabsCount-1)×0.6。
    val leadPeakUnits =
        (items.size - 1).coerceAtLeast(1) / SmearMath.LEAD_WEIGHT * 0.95f
    var peak by remember { mutableFloatStateOf(0f) }
    var lastIdx by remember { mutableIntStateOf(selectedIndex) }
    if (selectedIndex != lastIdx) {
        peak = (selectedIndex - lastIdx).coerceIn(-1, 1) * leadPeakUnits
        lastIdx = selectedIndex
    }
    val leadAnim = remember { Animatable(0f) }
    LaunchedEffect(peak) {
        if (peak == 0f) {
            leadAnim.snapTo(0f)
        } else {
            leadAnim.snapTo(peak)
            leadAnim.animateTo(0f, tween(Motion.NORMAL_MS))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer {
                val smear = SmearMath.smear(0f, 1f, leadAnim.value, items.size)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    pivotFractionX = SmearMath.pivotX(smear),
                    pivotFractionY = 0.5f,
                )
                scaleX = SmearMath.scaleX(smear)
                scaleY = SmearMath.scaleY(smear)
            },
    ) {
        GlassSurface(
            backdrop = backdrop,
            shape = RoundedCornerShape(spec.cornerRadius),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(GlassBarHeight)) {
                // ---- 選中態的玻璃膠囊（第二層玻璃，糊度是容器的 2.2 倍）----
                // 用與容器**不同**的 spec，才有「兩件東西」的層次感。
                val capsuleSpec = spec.copy(
                    blurRadius = spec.blurRadius * 2.2f,
                    refractionHeight = spec.refractionHeight * 0.5f,
                    refractionAmount = spec.refractionAmount * 0.5f,
                    surfaceAlpha = spec.surfaceAlpha * 1.15f,
                    cornerRadius = spec.cornerRadius,
                )
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalGlassSpec provides capsuleSpec,
                ) {
                    val cellWidth = 1f / items.size
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(cellWidth)
                            .height(GlassBarHeight - 12.dp)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                // 只平移：位置由 indicator 給
                                translationX = indicator.value * size.width * (items.size - 1)
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(GlassBarHeight - 12.dp)
                                .clip(RoundedCornerShape(spec.cornerRadius - 4.dp)),
                        )
                    }
                }

                // ---- 五個按鈕 -------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth().height(GlassBarHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(spec.cornerRadius - 6.dp))
                                .androidxClickable { onSelect(index) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) colors.primary else colors.onSurfaceVariantSummary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                // ★ 選中**只用顏色**表達，不改字重。
                                //   改字重會讓文字變寬，切頁時整排字橫向抖一下 ——
                                //   業主把那個叫「奇怪的抖動」（規範紅線 #6）。
                                fontWeight = FontWeight.Normal,
                                color = if (selected) colors.primary else colors.onSurfaceVariantSummary,
                                maxLines = 1,
                                modifier = Modifier.widthIn(min = 0.dp),
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

/**
 * 一個小的可點修飾符包裝。
 *
 * 為什麼不直接 `Modifier.clickable`：這裡要順手加上**最小觸控目標**（44dp）。
 * 規範 §4：「觸控目標最小 44×44，視覺尺寸可以更小」。
 */
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.androidxMinTouch().then(
            clickable(onClick = onClick),
        ),
    )

/** 保證最小觸控尺寸（見規範 §4）。 */
private fun Modifier.androidxMinTouch(): Modifier =
    this.then(sizeIn(minWidth = 44.dp, minHeight = 44.dp))
