package com.muyao.wanzaihyperos.ui.design.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * 「刚看见东西」的那一秒（返工总单 B3）。
 *
 * ### 为什么需要它
 *
 * [AiBallStates.curiousIfJustArrived] 早就写好了，但**没有任何页面调用它** ——
 * 于是 12 张脸里有 2 张（Curious / Searching）永远不出场，对着代码说「12 张脸
 * 都能画」，等于只画了 10 张。
 *
 * 判断「上一帧还在加载」需要页面自己的 `remember`：纯函数拿不到历史。
 * 这个 composable 把那点历史收在一处，页面只写 `AiBall(state = rememberArrivalState(raw))`。
 *
 * ### 为什么只停 1.6 秒
 *
 * Curious 是**转录**，不是状态：它说的是「刚刚变了」。停在上面不动就变成撒谎
 * （看起来像一直在好奇）。1.6 秒约等于一次呼吸，之后回到真实状态。
 *
 * @param target 页面对当前数据算出来的真实状态
 * @param holdMillis Curious 停留时长
 */
@Composable
fun rememberArrivalState(target: AiBallState, holdMillis: Long = 1_600L): AiBallState {
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val next = AiBallStates.curiousIfJustArrived(shown == AiBallState.Loading, target)
        shown = next
        if (next == AiBallState.Curious) {
            delay(holdMillis)
            // 再读一次 target：这一秒里页面可能已经又变了，别把旧值写回去。
            shown = target
        }
    }
    return shown
}
