package com.muyao.wanzaihyperos.ui.main

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
/**
 * **横向手势归属登记处**（v3 #5）。
 *
 * 为什么要有这个：切板块这条手势跑在 `PointerEventPass.Initial` 上（父层必须**先**于列表看到，
 * 否则竖向消耗之后父层读到零向量）—— 代价是**任何**子层自己的横向手势都抢不过它。
 * 真机实测就是这样：在课表页的周条上横扫切天，结果被这里判成"横滑切板块"，
 * 三下之后人已经在设置页了（2026-10-22 真机 `SYNTHETIC0000001`）。
 *
 * 所以沿用这一档已有的"起手区排除"设计（左右 26dp、底部 170dp 那两个）：
 * 自己吃横向手势的控件把自己在 **root 坐标**里的矩形登记进来，起手落在里面就整条放弃。
 * 用 `@Volatile` 而不是状态：这里只在手势判定时读一次，不需要也不应该触发重组。
 */
internal object HorizontalDragZones {
    @Volatile
    private var rects: Map<String, Rect> = emptyMap()

    fun set(key: String, rect: Rect?) {
        rects = if (rect == null) rects - key else rects + (key to rect)
    }

    /** `rootPos` 必须是 root 坐标（与 [androidx.compose.ui.layout.LayoutCoordinates.boundsInRoot] 同系）。 */
    fun blockedAt(rootPos: Offset): Boolean = rects.values.any { it.contains(rootPos) }
}

internal fun Modifier.swipeToSwitchSection(
    enabled: Boolean,
    onDrag: (Float) -> Unit,
    /** [velocityPxPerSec] 是**抬手速度**（正 = 往右）；调用方用它算落点投影。 */
    onSwipeEnd: (deltaPx: Float, velocityPxPerSec: Float, widthPx: Float) -> Unit,
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
            // ★ v3 #5：起手落在"自己吃横向手势"的控件里（课表周条）⇒ 整条放弃，
            //   否则那个控件永远收不到横扫。
            if (HorizontalDragZones.blockedAt(down.position)) return@awaitEachGesture

            var dx = 0f
            var dy = 0f
            var claimed = false
            // ★ B4-08（业主 2026-10-23）：抬手速度的样本。记的是**累计位移 `dx`**，不是 `change.position`
            //   —— 这一页的内容跟着 `translationX = swipePx` 一起动，局部坐标系会随手指反着走
            //   （周条那轮踩过同一个坑：位置样本被自己的形变污染，速度连符号都会翻）。
            //   `dx` 是 `previousPosition` 与 `position` 的差累加起来的，与坐标系无关。
            val samples = ArrayDeque<Pair<Long, Float>>()
            samples.addLast(down.uptimeMillis to 0f)

            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                dx += change.position.x - change.previousPosition.x
                dy += change.position.y - change.previousPosition.y
                samples.addLast(change.uptimeMillis to dx)
                while (samples.size > 16) samples.removeFirst()

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
            // 抬手速度：与周条同一套取法（最近一窗首尾差 ÷ dt；样本不足 ⇒ 0，按纯位置判定）
            val v = if (claimed) {
                com.muyao.wanzaihyperos.ui.main.pages.releaseVelocityOf(
                    samples,
                    samples.lastOrNull()?.first ?: down.uptimeMillis,
                )
            } else 0f
            onSwipeEnd(if (claimed) dx else 0f, v, w)
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

/**
 * 「这一下切不切板块」——**生产与测试共用这一个函数**（B4-08）。
 *
 * 规则：落点 = 位移 + 速度 × 投影时长；超过 `0.28 × 宽` 就切。
 * - 慢推（v≈0）⇒ 落点≈位移 ⇒ 与旧行为（纯距离阈值）逐位一致，不会把"慢慢推过去"判成不翻页；
 * - 轻甩（位移不够但有速度）⇒ 投影把它补过去 —— 这正是业主说的"不丝滑"里最扎手的一半。
 *
 * @return `+1` 去下一个板块、`-1` 回上一个、`0` 不动
 */
internal fun sectionSwipeStep(
    deltaPx: Float,
    velocityPxPerSec: Float,
    widthPx: Float,
    flingSec: Float = com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.flingProjectionMs / 1000f,
): Int {
    if (widthPx <= 0f) return 0
    // 钳位与 `TimetableSectionPage.flingSeconds()` 同一条范围：热调键被写成 0 或 5000 时，
    // 这里也不能把"轻扫"算成"没动"或"必翻三页"。
    val sec = flingSec.coerceIn(0.02f, 0.40f)
    val projected = deltaPx + velocityPxPerSec * sec
    if (abs(projected) <= widthPx * 0.28f) return 0
    // 往左拖（投影为负）⇒ 去下一个板块
    return if (projected < 0f) 1 else -1
}
