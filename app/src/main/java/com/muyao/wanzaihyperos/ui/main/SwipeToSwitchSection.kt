package com.muyao.wanzaihyperos.ui.main

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 「左右滑动屏幕切板块」的手势层。
 *
 * ## 为什么不换成 HorizontalPager
 *
 * 主壳现在的换页是 `AnimatedContent` + 一个自己维护的 `section` 下标，
 * 而且**每一页各挂一份采样层**给玻璃底栏采（`liquid/PageBackdrop.kt` 那条纪律：
 * 共享一份会被离场页 onDetach 清掉坐标，实时折射全丢）。
 * Pager 会把这两样都拆了重搭 —— 为了一个滑动手势去动已经验证过的玻璃链路，不值。
 * 所以这里只做一件事：**识别出水平拖动，然后调用已经存在的那条切换路径**
 * （`navDir` + 280ms transitionSpec），滑动和点底栏出来的动画因此必然一模一样。
 *
 * ## 三个必须写死的判定
 *
 * 1. **`PointerEventPass.Initial`**：父层要**先**于列表看到这一帧。
 *    用默认的 `Main` 时，`LazyColumn` 已经把竖向消耗掉了，父层读到的
 *    `positionChange()` 是零向量，表现就是「横向怎么拖都没反应」。
 * 2. **未定轴向时绝不 consume**：只从 `position - previousPosition` 自己累加增量。
 *    `positionChange()` 会顺手把事件标记成已消耗 —— 那样竖向滚动就跟着死了。
 * 3. **起手区要排除**：左右各 26dp 是系统侧滑返回的地盘（在里侧起手的拖动**整个放弃**，
 *    不然会和系统返回抢）；底部 170dp 是玻璃底栏 + 悬浮搜索胶囊的地盘，
 *    底栏那颗水滴滑块的拖拽**本来就是水平手势**，抢过来就变成「拖滑块却换了页」。
 */
internal fun Modifier.swipeToSwitchSection(
    enabled: Boolean,
    onDrag: (Float) -> Unit,
    onSwipeEnd: (deltaPx: Float, widthPx: Float) -> Unit,
): Modifier = this.then(
    if (!enabled) Modifier else Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val edgePx = 26.dp.toPx()
        val bottomDeadPx = 170.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val w = size.width.toFloat()
            val x = down.position.x
            val y = down.position.y
            // 边缘区 / 底栏区起手：整条手势放弃（不 consume，让系统或底栏自己处理）
            if (x < edgePx || x > w - edgePx || y > size.height - bottomDeadPx) return@awaitEachGesture

            var dx = 0f
            var dy = 0f
            var claimed = false

            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                dx += change.position.x - change.previousPosition.x
                dy += change.position.y - change.previousPosition.y

                if (!claimed) {
                    // 比例判轴向：竖向先赢 → 交还给列表；横向先赢 → 我们接管
                    if (abs(dy) > slop * 1.5f && abs(dy) > abs(dx) * 1.2f) break
                    if (abs(dx) > slop * 1.5f && abs(dx) > abs(dy) * 1.2f) claimed = true
                }

                if (claimed) {
                    change.consume()
                    onDrag(damped(dx, w))
                }
                if (!change.pressed) break
            }

            // ★ 判定用**原始位移**，视觉才走橡皮筋。
            //   早先这里传的是 `damped(dx, w)`，真机实测一次 700px 的整屏快滑被压成
            //   339.5px，而阈值是 0.28×1216 = 340px —— 差 0.5px，**怎么滑都不翻页**，
            //   而且探针日志看起来一切正常（claimed=true、dx=-700）。这类"形变量参与判定"
            //   的坑，判据必须钉在输入量上。
            onSwipeEnd(if (claimed) dx else 0f, w)
        }
    }
)

/**
 * 橡皮筋：推到边界外（第一格往右 / 最后一格往左）只给 25% 位移。
 *
 * 完全不给位移会显得「卡住了」；给满又会让整块内容滑出屏幕露出一条空白边。
 */
private fun damped(raw: Float, width: Float): Float {
    val over = abs(raw) - width * 0.18f
    return if (over > 0f) (width * 0.18f + over * 0.25f) * raw.sign else raw
}

private val Float.sign: Float get() = if (this >= 0f) 1f else -1f
