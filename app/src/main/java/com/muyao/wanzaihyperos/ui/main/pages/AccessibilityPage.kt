package com.muyao.wanzaihyperos.ui.main.pages

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.common.NoticeLine
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.Motion as MotionTokens
import com.muyao.wanzaihyperos.ui.design.MotionPrefs
import com.muyao.wanzaihyperos.ui.design.PillSwitch
import com.muyao.wanzaihyperos.ui.design.PressMotion
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.PaperCard
import com.muyao.wanzaihyperos.ui.design.SettingIcon
import com.muyao.wanzaihyperos.ui.design.SettingRow
import com.muyao.wanzaihyperos.ui.design.SettingSectionLabel
import com.muyao.wanzaihyperos.ui.design.SmearMath
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberHaptics
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 动画与辅助显示（v2.1 稿 `screens/accessibility.png`）。
 *
 * ## 为什么这一页**只有两个开关是我们自己的**
 *
 * 板上四行长得一样（四个开关），但底下的东西完全不同：
 *
 * | 行 | 真值在哪 | 能不能由本应用改 |
 * |---|---|---|
 * | 减少动态 | 系统「动画时长缩放」`animator_duration_scale` | ❌ 只能跳系统设置 |
 * | 高对比显示 | 系统 `accessibility_high_text_contrast_enabled` | ❌ 只有系统应用能写 |
 * | 减弱透明度 | **我们自己的** `UiPrefs.reduce_transparency` | ✅ |
 * | 系统动画缩放 | 同上第一行，读数是那个倍数 | ❌ 只显示 |
 *
 * 前两行如果画成开关，按下去只会「动了但没生效」——那正是本工程红线里
 * 「留一颗假按钮」的反面。所以它们的尾件是**状态字 + 去系统设置**，
 * 而不是一个按了不动的 `PillSwitch`。第三行是真开关，第四行是纯读数。
 *
 * ## 消费者（不是装饰）
 *
 * - `MotionPrefs.animationsEnabled` → `MotionFlags.canMove` → 全工程位移/弹簧动效
 *   （关掉后只剩淡入淡出，见 `ui/design/Motion.kt` 档头）。
 * - `MotionPrefs.highTextContrast` → `glassIsTranslucent` → 玻璃退成近实底。
 * - `MotionPrefs.reduceTransparency` → 同上，且是**用户自己**的选择，落盘。
 */
@Composable
fun AccessibilityPage(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    // 订阅 version：highContrast / animationsEnabled 是 @Volatile 普通字段，
    // 直接读不会触发重组 —— 系统里改完，这一页必须跟着变（Motion.kt 档头同一条警告）。
    val motion = rememberMotion()
    val scale = remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    val canMove = motion.animationsEnabled
    val highContrast = motion.highContrast
    val translucentOff = motion.reduceTransparency

    // ★ 返工总单 C4：跳系统设置失败时原来被 runCatching 静默吞掉 —— 按了没反应，
    //   用户只会以为「这个 App 坏了」。现在把失败原因交回界面显示。
    val systemError = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    fun openSystem(action: String) {
        runCatching { context.startActivity(Intent(action)) }
            .onFailure {
                systemError.value = "打不开系统设置：" + (it.message ?: "系统拒绝了这次跳转")
            }
            .onSuccess { systemError.value = null }
    }

    val listState = rememberLazyListState()
    val veil by rememberScrollProgress(listState)

    SecondaryScaffold(
        title = "动画与辅助显示",
        onBack = onClose,
        scrollProgress = veil,
        modifier = modifier,
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dims.screenH,
                end = Dims.screenH,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
        ) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "安静，不意味着静止。",
                        fontSize = Type.displayTitle,
                        fontWeight = Type.weightTitle,
                        color = colors.onBackground,
                        lineHeight = Type.displayTitle * 1.24f,
                    )
                    Text(
                        text = "让动态只在需要回应你的时候出现。系统关闭动画时，不再播放缩放、位移或表情轮播。",
                        fontSize = Type.secondary,
                        color = colors.onSurfaceVariantSummary,
                        lineHeight = Type.secondary * 1.65f,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item(key = "motion-group") {
                Column {
                    // 稿上这一组是 .paper.flush：没有分组小标题，也没有玻璃底。
                    PaperCard {
                        SettingRow(
                            icon = SettingIcon.Moon,
                            tint = if (canMove) Tone.quiet else Tone.ok,
                            title = "减少动态",
                            subtitle = if (canMove) {
                                "保留状态变化，去掉位移与弹性"
                            } else {
                                "保留状态变化，去掉位移与弹性 · 系统动画已关闭"
                            },
                            // ★ 返工总单 C4：这一行以前只能跳系统设置（应用内没有开关）。
                            //   现在它是真开关：写 UiPrefs + 推给 MotionPrefs，
                            //   由 MotionFlags.canMove 消费，全工程位移/弹簧立刻停。
                            trailing = {
                                PillSwitch(
                                    checked = UiPrefs.reduceMotion,
                                    label = "减少动态",
                                ) {
                                    val next = !UiPrefs.reduceMotion
                                    MotionPrefs.setReduceMotion(next)
                                    UiPrefs.saveReduceMotion(next)
                                }
                            },
                        )
                        SettingRow(
                            icon = SettingIcon.Contrast,
                            tint = if (highContrast) Tone.ok else Tone.quiet,
                            title = "高对比显示",
                            subtitle = when {
                                MotionPrefs.systemHighTextContrast -> "提高文字对比，保留所选玻璃材质 · 系统已开启"
                                UiPrefs.highContrastOverride -> "提高文字对比，保留所选玻璃材质 · 应用内已开启"
                                else -> "提高文字对比，保留所选玻璃材质"
                            },
                            trailing = {
                                PillSwitch(
                                    checked = UiPrefs.highContrastOverride,
                                    label = "高对比显示",
                                ) {
                                    val next = !UiPrefs.highContrastOverride
                                    MotionPrefs.setHighContrastOverride(next)
                                    UiPrefs.saveHighContrastOverride(next)
                                }
                            },
                        )
                        SettingRow(
                            icon = SettingIcon.Screen,
                            tint = Tone.quiet,
                            title = "系统级开关（可选）",
                            subtitle = systemError.value
                                ?: "动画缩放与高对比文字在 Android 系统设置里；本页也能跳过去",
                            onClick = { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                            trailingChevron = true,
                        )
                        SettingRow(
                            icon = SettingIcon.Eye,
                            tint = if (translucentOff) Tone.ok else Tone.quiet,
                            title = "减弱透明度",
                            subtitle = "强制实色，但记住液态/磨砂选择",
                            trailing = {
                                PillSwitch(
                                    checked = translucentOff,
                                    label = "减弱透明度",
                                ) {
                                    val next = !translucentOff
                                    MotionPrefs.setReduceTransparency(next)
                                    UiPrefs.saveReduceTransparency(next)
                                }
                            },
                        )
                        SettingRow(
                            icon = SettingIcon.Badge,
                            tint = Tone.quiet,
                            title = "系统动画缩放",
                            subtitle = when {
                                scale <= 0f -> "0 · 系统里动效全关了"
                                scale < 1f -> "$scale × · 比标准更快"
                                else -> "1 × · 标准速度"
                            },
                            trailing = {
                                Text(
                                    text = if (scale <= 0f) "0" else "${scale}×",
                                    fontSize = Type.summary,
                                    color = colors.onSurfaceVariantSummary,
                                )
                            },
                        )
                    }
                }
            }

            item(key = "rhythm-group") {
                Column {
                    Text(
                        // 稿 h3 = 20.3px × 0.886 ≈ 18sp，正好是 Type.title。
                        text = "响应节奏",
                        modifier = Modifier
                            .padding(top = 18.dp, bottom = 8.dp)
                            .semantics { heading() },
                        fontSize = Type.title,
                        fontWeight = Type.weightTitle,
                        color = colors.onBackground,
                    )
                    PaperCard {
                        // 右值一律**从实现里的常量算出来**，不在这一页手写数字：
                        // 手写的表很快就会和真正的动效时长脱节，而它唯一的用处就是「说的是真的」。
                        TimingRow("按下 / 释放", "${MotionTokens.PRESS_MS} / ${MotionTokens.FAST_MS} ms")
                        TimingRow("页面 / 弹层", "${MotionTokens.NORMAL_MS} / ${MotionTokens.EXPAND_MS} ms")
                        // 稿上这一行写 1.06×，实现是 SmearMath 的横向 3% 上限 ⇒ 1.03×。
                        // 页面给真实值（差异记在对稿报告的结构性冲突里），不按稿编数字。
                        TimingRow(
                            "底栏形变上限",
                            String.format(Locale.US, "%.2f×", 1f + SmearMath.MAX_SCALE_X),
                        )
                        TimingRow(
                            "小球看向你",
                            String.format(Locale.US, "%.1f 秒后归位", AiBall.GAZE_HOLD_MS / 1000f),
                        )
                    }
                }
            }

            item(key = "notice") {
                NoticeLine("没有呼吸缩放、轨道粒子或持续漂浮。触觉与视觉同一状态源；错误不播放完成动作。")
            }

            item(key = "feedback") {
                // 「轻触体验反馈」不是另造一段演出：按一下 = 一次 90ms 按压形变 + 一次触觉，
                // 就是上面那张表前两行的真实节奏（`PressMotion.scaleAt` 与 `Motion.PRESS_MS`）。
                // 系统动画缩放为 0 / 设置里关掉动态时**只发触觉、不播形变** ——
                // 关掉动画的人不该还要等一段弹簧（`MotionFlags.canMove` 的既有语义）。
                val haptics = rememberHaptics()
                val scope = rememberCoroutineScope()
                val press = remember { Animatable(0f) }
                Button(
                    onClick = {
                        haptics.tick()
                        scope.launch {
                            if (!motion.canMove) return@launch
                            press.animateTo(1f, tween(MotionTokens.PRESS_MS))
                            press.animateTo(
                                0f,
                                spring(
                                    dampingRatio = PressMotion.OVERSHOOT_DAMPING,
                                    stiffness = PressMotion.OVERSHOOT_STIFFNESS,
                                ),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val s = PressMotion.scaleAt(press.value)
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    Text("轻触体验反馈")
                }
            }
        }
    }
}

/**
 * 响应节奏的一行：左标签，右值。
 *
 * 右值是**字符串**而不是 `Int`：这一表里既有「90 / 150 ms」这种双段毫秒，
 * 也有「1.03×」「1.4 秒后归位」这种别的单位 —— 让调用方自己格式化，
 * 免得每加一种单位就在渲染层加一个分支。
 */
@Composable
private fun TimingRow(label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dims.cardPadding, vertical = 13.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = Type.body,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = Type.summary,
            color = colors.onSurfaceVariantSummary,
        )
    }
}
