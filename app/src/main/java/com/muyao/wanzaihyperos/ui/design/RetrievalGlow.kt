package com.muyao.wanzaihyperos.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 「这张卡正在取数」的那一圈流光。
 *
 * 缘起：2026-11-01 抖音那条《8 个动效》的第 6 条 CONIC GLOW —— 给核心模块绕一圈缓慢旋转的
 * 渐变微边框，让「在等」这件事有个持续可见的形状。这里的取舍有三条：
 *
 * 1. **只在真有一次请求在路上时画**（[active]）。常驻就变成装饰，装饰的边界随时间被磨平，
 *    真正在等的时候反而没人注意。判据由调用点给，且必须是数据层认过的正面证据
 *    —— 全工程只有 `jwVerdictOf` 那一份算法，不许在界面里拿 `ListState.FirstLoad` 猜（见 SlotSource.kt 头注）。
 * 2. **不动画的时候仍然看得见**（`motion.canMove == false`）：退成一道静态描边。
 *    关掉系统动画的人也该知道「它在等」，而不是看着一张安静的卡以为取完了。
 * 3. **不用 sweepGradient 旋转**：Compose 的渐变要在画布上旋转，只能连路径一起转，
 *    而圆角矩形的路径不是旋转对称的（一转就跑到框外）。所以流光用「一串沿周界排队的点」画：
 *    几何是纯函数（[ringPoint]），能被单测钉住，也不依赖任何 shader。
 */
object RetrievalGlow {
    /** 一圈走完的时长。见 [tween] 那行的注释：它属于环境类循环，所以进了 MotionGuardTest 的白名单。 */
    const val ROTATE_MS = 2_600

    /** 描边粗细。视频里是 2px；3.5x 的屏上 2px 不到 1dp，画出来会被抗锯齿糊成灰边。 */
    const val THICKNESS_DP = 1.5f

    /**
     * 尾迹占周界的比例。
     *
     * 0.30 ⇒ 这张卡（周界约 3100px）上尾巴约 930px，读起来是「一节在跑的边」；
     * 再长就糊满整圈变成描边，再短则看不出方向。
     */
    const val TAIL_SPAN = 0.30f

    /**
     * 尾迹最多拆成多少段。
     *
     * 段数由 [tailSteps] 按「段长不超过半个笔画」反算，这样**再大的卡也不会断成散点**；
     * 上限只是防一手「卡被量成几千 px 时白烧 GPU」，512 段对 4K 宽的卡都还够密。
     */
    const val MAX_TAIL_STEPS = 512

    /** 「头」那一点的不透明度。 */
    const val HEAD_ALPHA = 0.85f

    /** 底下那圈静态描边的不透明度：让「在等」有一个完整轮廓，而不是只有一个亮点在跑。 */
    const val BASE_ALPHA = 0.12f

    /** 关掉动画时那圈描边的不透明度。比 [BASE_ALPHA] 亮，因为它是唯一的信号了。 */
    const val STILL_ALPHA = 0.34f

    /** 尾迹第 [index] 段（0 = 头）的不透明度：二次衰减，头亮尾无。 */
    fun tailAlpha(index: Int, count: Int): Float {
        if (count <= 0) return 0f
        val f = (index.toFloat() / count).coerceIn(0f, 1f)
        val k = 1f - f
        return HEAD_ALPHA * k * k
    }

    /**
     * 尾迹要拆成多少段。
     *
     * ★ 这个函数存在的理由是一条**量出来的**缺陷：早先固定 48 点铺满整条尾巴，
     *   一张 1103×519px 的卡上点距 19.8px、点直径 5.25px ⇒ 真机左沿只看得到
     *   7 个孤立蓝块，读出来是「边上有几个点」而不是「一道在跑的边」。
     *   段长必须 ≤ 半个笔画，相邻两段才首尾相接；段数因此由几何反算，不能写死。
     */
    fun tailSteps(perimeter: Float, strokePx: Float): Int {
        if (perimeter <= 0f || strokePx <= 0f) return 0
        val tailLength = perimeter * TAIL_SPAN
        return ceil(tailLength / (strokePx * 0.5f)).toInt().coerceIn(2, MAX_TAIL_STEPS)
    }

    /**
     * 圆角矩形周界上的一点。[t] 是 0..1 的周长比例（会自动取模，允许负数）。
     *
     * 起点是上边左端（左上圆角之后那一点），顺时针走。纯函数，单测直接钉。
     */
    fun ringPoint(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
        t: Float,
    ): Offset {
        val r = radius.coerceIn(0f, min(width, height) / 2f)
        val a = (width - 2f * r).coerceAtLeast(0f)
        val b = (height - 2f * r).coerceAtLeast(0f)
        val arcLen = (PI.toFloat() * r / 2f)
        val total = 2f * a + 2f * b + 4f * arcLen
        if (total <= 0f) return Offset(left + width / 2f, top + height / 2f)
        var d = (t % 1f + 1f) % 1f * total

        val right = left + width
        val bottom = top + height
        // 上边：左圆角之后 → 右圆角之前
        if (d <= a) return Offset(left + r + d, top)
        d -= a
        // 右上圆角：-90° → 0°
        if (d <= arcLen) {
            val ang = -PI.toFloat() / 2f + (d / arcLen) * (PI.toFloat() / 2f)
            return Offset(right - r + r * cos(ang), top + r + r * sin(ang))
        }
        d -= arcLen
        // 右边
        if (d <= b) return Offset(right, top + r + d)
        d -= b
        // 右下圆角：0° → 90°
        if (d <= arcLen) {
            val ang = (d / arcLen) * (PI.toFloat() / 2f)
            return Offset(right - r + r * cos(ang), bottom - r + r * sin(ang))
        }
        d -= arcLen
        // 下边：从右往左
        if (d <= a) return Offset(right - r - d, bottom)
        d -= a
        // 左下圆角：90° → 180°
        if (d <= arcLen) {
            val ang = PI.toFloat() / 2f + (d / arcLen) * (PI.toFloat() / 2f)
            return Offset(left + r + r * cos(ang), bottom - r + r * sin(ang))
        }
        d -= arcLen
        // 左边：从下往上
        if (d <= b) return Offset(left, bottom - r - d)
        d -= b
        // 左上圆角：180° → 270°
        val ang = PI.toFloat() + (d / arcLen).coerceIn(0f, 1f) * (PI.toFloat() / 2f)
        return Offset(left + r + r * cos(ang), top + r + r * sin(ang))
    }
}

/**
 * 给这张卡绕一圈流光。[active] 为假时**一个修饰符都不加**（不是画一层透明的）。
 *
 * 调用点要把它写在 `dropShadow` 之后：阴影先铺，流光压在卡沿上。
 */
@Composable
fun Modifier.retrievalGlow(active: Boolean, cornerRadius: Dp, color: Color): Modifier {
    if (!active) return this
    val motion = rememberMotion()
    if (!motion.canMove) {
        // 关掉动效：一道静态描边。位置与粗细和流动时一致，只是不跑。
        return this.drawWithContent {
            drawContent()
            drawGlowBase(cornerRadius, color, RetrievalGlow.STILL_ALPHA)
        }
    }
    val transition = rememberInfiniteTransition(label = "retrievalGlow")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        // 2_600ms 一圈 = 0.38Hz 的周长流动，远慢于 Apple 点名难受的那条 0.2Hz 摆动带
        //（它说的是整屏位移；这里是沿边界的行进，读起来是一道追光，不是晃动）。
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glowPhase",
    )
    return this.drawWithContent {
        drawContent()
        drawGlowBase(cornerRadius, color, RetrievalGlow.BASE_ALPHA)
        drawGlowComet(phase.value, cornerRadius, color)
    }
}

/**
 * 底下那道完整的静态边。
 *
 * 用 `drawRoundRect` + `Stroke` 而不是也去排点：轮廓本来就该是连续的，
 * 排点只有在「追光」那种要沿周长定位的地方才必要（并且那里点多一个就多一次 drawCircle）。
 */
private fun DrawScope.drawGlowBase(
    cornerRadius: Dp,
    color: Color,
    alpha: Float,
) {
    val w = RetrievalGlow.THICKNESS_DP.dp.toPx()
    if (size.width <= w || size.height <= w) return
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = w),
    )
}

/** 追光：一串沿周界排在「头」后面的点。 */
private fun DrawScope.drawGlowComet(
    phase: Float,
    cornerRadius: Dp,
    color: Color,
) {
    val w = RetrievalGlow.THICKNESS_DP.dp.toPx()
    val inset = w / 2f
    val width = size.width - w
    val height = size.height - w
    if (width <= 0f || height <= 0f) return
    val r = cornerRadius.toPx()
    // ★ 尾迹画成**首尾相接的一串短线段**，不是等距散点（理由见 [RetrievalGlow.tailSteps]）。
    val perimeter = 2f * (width - 2f * r) + 2f * (height - 2f * r) + 4f * (PI.toFloat() * r / 2f)
    val steps = RetrievalGlow.tailSteps(perimeter, w)
    if (steps < 2) return
    val seg = Path()
    for (i in 0 until steps) {
        val a = RetrievalGlow.tailAlpha(i, steps)
        if (a <= 0.01f) continue
        val t0 = phase - i.toFloat() / steps * RetrievalGlow.TAIL_SPAN
        val t1 = phase - (i + 1).toFloat() / steps * RetrievalGlow.TAIL_SPAN
        val p0 = RetrievalGlow.ringPoint(inset, inset, width, height, r, t0)
        val p1 = RetrievalGlow.ringPoint(inset, inset, width, height, r, t1)
        seg.reset()
        seg.moveTo(p0.x, p0.y)
        seg.lineTo(p1.x, p1.y)
        drawPath(
            path = seg,
            color = color.copy(alpha = a),
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
    }
}
