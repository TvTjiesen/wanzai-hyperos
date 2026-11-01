package com.muyao.wanzaihyperos.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text

/**
 * 按下缩、松手**过冲一下再停**（业主 2026-11-01 从那条抖音里挑的第 8 条）。
 *
 * ## 它补的是哪一半
 *
 * 工程里原本已经有「按下变 0.96」这类反馈（`GlassControls` 的 `1f - 0.04f * press.value`），
 * 但它用的是同一条弹簧去回正，回正**没有过冲** —— 按下去扎实，松手却像一摊水直接摊平。
 * 那条视频里最有价值的一句是「别只做透明度变化」：按压要能被手指读到，要有「弹回来」的那一下。
 *
 * ## 两段为什么用不同的曲线
 *
 * - **按下用 tween**：手指按下去是命令，不是弹簧。用弹簧压下去会在到位前先抖一下，
 *   手感是「黏」而不是「实」。`Motion.PRESS_MS` 那条注释（「必須快，慢了手感會黏」）说的是同一件事。
 * - **松手用欠阻尼弹簧**：它自己会越过终点再回来，那一点点过头就是「弹」。
 *
 * ## 过冲量是算出来的，不是调出来的
 *
 * 二阶欠阻尼系统的阶跃过冲 = `exp(-πζ / sqrt(1-ζ²))`。ζ = 0.42 时约 23%，
 * 乘以幅度 0.04 就是 **scale 峰值 1.009** —— 看得见（1% 在 3.5x 屏上约 1px 的外缘位移），
 * 又不会大到整块在跳。`PressMotionTest` 用同一条公式复算，改常数就会红。
 */
object PressMotion {

    /** 按住时的缩放：0.96（板与那条视频同一个值）。 */
    const val SCALE = 0.96f

    /** 松手回弹的阻尼比。**必须 < 1**，否则不会过冲（≥1 就是临界/过阻尼）。 */
    const val OVERSHOOT_DAMPING = 0.42f

    /** 松手回弹的刚度。比按下那条快，松手要比按下更利落。 */
    const val OVERSHOOT_STIFFNESS = 700f

    /**
     * 压缩幅度（1 - 0.96 = 0.04）。
     *
     * `press` 允许超过 1（弹簧回弹时会短暂到 −0.23 这种负值），所以这里不 coerce ——
     * 恰恰是超出去的那一段让 scale 超过 1，形成「弹」。
     */
    fun scaleAt(press: Float, magnitude: Float = 1f - SCALE): Float = 1f - magnitude * press

    /**
     * 阶跃过冲比例（只用于守卫与注释里的算式，运行时用不到）。
     *
     * @param damping 阻尼比；≥ 1 返回 0（不会过冲）。
     */
    fun overshootFraction(damping: Float): Float {
        if (damping >= 1f) return 0f
        if (damping <= 0f) return 1f
        val r = damping / kotlin.math.sqrt(1f - damping * damping)
        return kotlin.math.exp(-Math.PI.toFloat() * r)
    }
}

/**
 * 把 [interactionSource] 的按下状态接到缩放上。
 *
 * 调用方自己把这个 source 交给 `clickable(interactionSource = ...)`：
 * 自己再监听一次触摸会与 `clickable` 抢手势（滚动里的格子会变得很难点）。
 *
 * `motion.canMove == false`（系统关了动画 / 设置里关了）时**直接跳变**，不留 90ms 的中间态 ——
 * 关掉动画的人不该还要等一段弹簧。
 */
@Composable
fun Modifier.pressSpring(
    interactionSource: MutableInteractionSource,
    magnitude: Float = 1f - PressMotion.SCALE,
): Modifier {
    val motion = rememberMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (!motion.canMove) {
            press.snapTo(if (pressed) 1f else 0f)
            return@LaunchedEffect
        }
        if (pressed) {
            press.animateTo(1f, tween(Motion.PRESS_MS))
        } else {
            press.animateTo(
                0f,
                spring(
                    dampingRatio = PressMotion.OVERSHOOT_DAMPING,
                    stiffness = PressMotion.OVERSHOOT_STIFFNESS,
                ),
            )
        }
    }
    return this.graphicsLayer {
        val s = PressMotion.scaleAt(press.value, magnitude)
        scaleX = s
        scaleY = s
    }
}

/**
 * 列表首次出现的交错入场（业主 2026-11-01 挑的第 7 条）。
 *
 * ## 为什么需要一个「闸门」而不是每个格子自己数 index
 *
 * Lazy 列表滚出视野的项会被丢弃，滚回来时**重新组合**。只按 index 算延迟的话，
 * 每滑一下就重放一遍整排入场 —— 那不是质感，是折磨（这条视频的原话是「别让它们整体死板地淡入」，
 * 不是「让它们每次都弹一遍」）。所以延迟只在页面打开后的头一小段时间里生效，
 * 之后组合出来的项（也就是后来滚出来的）直接就在终态。
 */
object RevealMotion {

    /** 相邻两项的时间差。视频里的读数就是 0.10s。 */
    const val STEP_MS = 100L

    /** 最多错开到第几项：再往后就是「整排一起」而不是「一个一个」了。 */
    const val MAX_STEPS = 6

    /** 单项自己的入场时长（不含延迟）。 */
    const val DURATION_MS = 260

    /** 从下方多少 dp 升上来。大了像抽屉，小了看不出来。 */
    const val RISE_DP = 12f

    /** 第 [index] 项的延迟。超过 [MAX_STEPS] 之后不再加，避免第 20 项等 2 秒。 */
    fun delayOf(index: Int): Long = STEP_MS * index.coerceIn(0, MAX_STEPS)
}

/** 交错入场的闸门：页面刚打开时是开的，[holdMs] 之后自己关上。 */
class RevealGate internal constructor() {
    internal var open = true
        private set

    internal fun close() {
        open = false
    }
}

/**
 * 建一枚闸门。一个页面建一枚（放在列表外面），列表里的每一项共用它。
 *
 * [holdMs] 要盖住「首屏那些项组合完」的时间：太短则首屏还没铺完闸门就关了（等于没有入场），
 * 太长则用户已经开始滚了、滚出来的项还在延迟。0.7s 是按首屏 20 个 item 组合完的实测量级取的。
 */
@Composable
fun rememberRevealGate(holdMs: Long = 700L): RevealGate {
    val gate = remember { RevealGate() }
    val motion = rememberMotion()
    LaunchedEffect(Unit) {
        if (!motion.canMove) {
            gate.close()
            return@LaunchedEffect
        }
        delay(holdMs)
        gate.close()
    }
    return gate
}

/**
 * 交错入场：透明度 0→1、同时从下方 [RevealMotion.RISE_DP] 升到位。
 *
 * 闸门已经关上、或者系统关了动画 ⇒ 什么都不做（**不是**「立刻跳到终态」那种写法：
 * 那样每一帧都要多一层 graphicsLayer，而这一层对不动的项没有任何意义）。
 */
@Composable
fun Modifier.revealIn(gate: RevealGate, index: Int): Modifier {
    val motion = rememberMotion()
    val animate = gate.open && motion.canMove
    if (!animate) return this
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(RevealMotion.delayOf(index))
        shown = true
    }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(RevealMotion.DURATION_MS, easing = FastOutSlowInEasing),
        label = "reveal",
    )
    val risePx = with(LocalDensity.current) { RevealMotion.RISE_DP.dp.toPx() }
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * risePx
    }
}

/**
 * 数字像机械码表一样翻滚（业主 2026-11-01 从那条抖音里挑的第 4 条）。
 *
 * ## 它解决的问题
 *
 * 首页「校园跑」与数智体育页的公里数，以前是刷新时**直接换字**：16.8 变 17.2 没有任何过渡，
 * 眼睛读到的是「页面闪了一下」。码表的做法是每一位数字自己从下往上滚过去 ——
 * 数字变了这件事因此变成看得见的运动，而不是一次重绘。
 *
 * ## 只滚数字位
 *
 * 「公里」「.」这些字符不滚（它们不会变，滚起来只是抖）。判断用的是 [Char.isDigit]，
 * 所以「—」（取不到时的占位）也是静止的 —— 它是「没有数」的表示，不该表演一次滚动。
 *
 * ## 方向跟着数走
 *
 * 变大 ⇒ 新数字从下面进来、旧的从上面走；变小反过来。方向错了读起来像故障。
 */
@Composable
fun OdometerText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    style: TextStyle = TextStyle.Default,
) {
    val motion = rememberMotion()
    // 关掉动画 / 第一次组合：直接写字，不给每一位都套一层 AnimatedContent。
    if (!motion.canMove) {
        OdometerPlain(text, modifier, fontSize, fontWeight, color, style)
        return
    }
    // 方向：这一串数比上一串大还是小。只用来决定滚动的上下，不参与取值。
    var previous by remember { mutableStateOf<Double?>(null) }
    val value = text.toDoubleOrNull()
    val rising = remember(previous, value) { (value ?: 0.0) >= (previous ?: value ?: 0.0) }
    LaunchedEffect(value) { previous = value }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // 连续的非数字字符**并成一段**：「公里」是一个词，逐字拆开会丢掉字距与换行规则；
        // 数字才逐位拆，因为逐位滚才是码表。
        var i = 0
        while (i < text.length) {
            val digit = text[i].isDigit()
            var j = i
            while (j < text.length && text[j].isDigit() == digit) j++
            if (digit) {
                for (k in i until j) {
                    OdometerDigit(text[k], rising, fontSize, fontWeight, color, style)
                }
            } else {
                OdometerPlain(text.substring(i, j), Modifier, fontSize, fontWeight, color, style)
            }
            i = j
        }
    }
}

@Composable
private fun OdometerPlain(
    text: String,
    modifier: Modifier,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    style: TextStyle,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        style = style,
        maxLines = 1,
    )
}

/** 一位数字的滚动窗口：新来的从下（或上）进来，旧的从另一头走掉。 */
@Composable
private fun OdometerDigit(
    ch: Char,
    rising: Boolean,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    style: TextStyle,
) {
    val spec = tween<IntOffset>(Motion.FAST_MS)
    // clipToBounds 是「码表窗口」这个比喻的本体：没有它，进出的数字会画到行外面去。
    Box(Modifier.clipToBounds()) {
        AnimatedContent(
            targetState = ch,
            transitionSpec = {
                val fromBelow = if (rising) 1 else -1
                (slideInVertically(spec) { h -> fromBelow * h }
                    .togetherWith(slideOutVertically(spec) { h -> -fromBelow * h }))
            },
            label = "odometer",
        ) { target ->
            OdometerPlain(target.toString(), Modifier, fontSize, fontWeight, color, style)
        }
    }
}
