package com.muyao.wanzaihyperos.ui.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 四个**有因果**的触觉点位。
 *
 * ## 为什么只有这四个（业主 2026-10-20 批准的清单第 1 条：全工程原本 0 处触觉）
 *
 * 参照的是 Apple《Designing Audio-Haptic Experiences》那三条准则，也是这套 skill 里唯一
 * 讲触觉的那段：
 *
 * - **因果要清楚**：反馈必须挂在"真正造成变化的那一下"上（签到成功的那一瞬、磁贴吸住的那一帧），
 *   不是挂在"我顺手加个震动"上。
 * - **要同帧**：视觉、声音、触觉错帧，错觉就破了。所以这些函数**在状态改变的那一行同步调用**，
 *   不放进 `LaunchedEffect` / 协程里延后一拍。
 * - **要省着用**：只在**成功 / 错误 / 提交 / 吸附**这四刻。按压、滚动、悬停一律不给 ——
 *   "过度反馈会训练用户忽略所有反馈"。
 *
 * ## 为什么走 `View.performHapticFeedback` 而不是 Compose 的 `LocalHapticFeedback`
 *
 * Compose 那层只暴露 `LongPress / TextHandleMove / VirtualKey` 三种，映射不到
 * `CONFIRM / REJECT / CLOCK_TICK` 这些**带语义**的系统触感（它们在各家 ROM 上被映射成不同波形，
 * 语义化那个通道才对得上 HyperOS/ColorOS 自己的触感方案）。
 *
 * ⚠️ 系统触感被关掉时**不发**：`view.isHapticFeedbackEnabled` 由系统/无障碍统一管，
 *   这里再叠一层 [MotionPrefs] 的开关，避免"设置里明明关了还在震"。
 */
class Haptics internal constructor(private val view: View, private val enabled: Boolean) {

    private fun send(constant: Int) {
        if (!enabled || !view.isHapticFeedbackEnabled) return
        runCatching { view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING) }
    }

    /** 轻：吸附、落位、展开到位。给的是"碰到了"的确认，不是"成功了"。 */
    fun tick() = send(HapticFeedbackConstants.CLOCK_TICK)

    /** 中：一次有后果的提交（按下"签到"、按下"交作业"）。 */
    fun commit() = send(HapticFeedbackConstants.CONTEXT_CLICK)

    /** 成功：服务端认了（201 / 签上）。 */
    fun success() = send(HapticFeedbackConstants.CONFIRM)

    /** 失败/被拒：取不到、被服务端拒绝、危险动作被确认。 */
    fun failure() = send(HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberHaptics(
    view: View = LocalView.current,
    enabled: Boolean = true,
): Haptics = remember(view, enabled) { Haptics(view, enabled) }

/**
 * `CLOCK_TICK` 要 API 23、`CONFIRM/REJECT` 要 API 30。
 *
 * 本工程 `minSdk = 33`，所以这里**不需要**分支；留这个注释是因为这套常量在旧包上会
 * 静默不响（`performHapticFeedback` 对未知常量不抛异常），哪天有人降 minSdk 会踩。
 */
@Suppress("unused")
private val minimumApiForTheseConstants: Int = Build.VERSION_CODES.R
