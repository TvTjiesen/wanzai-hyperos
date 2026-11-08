package com.muyao.wanzaihyperos.ui.design.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * AI 小球：一枚 36dp 的表情球，表达「这一页现在是什么状况」。
 *
 * ## 为什么是 36dp + 1dp 环（不是随手定的）
 *
 * 引擎给的脸里，02（待机）与 36（加载）的墨迹最细：直径 28dp 时眼睛外接框只有
 * 26x18 / 26x16 px，认不出是张脸；36dp 才到 33x24 / 33x20，是**所有脸都能用的最小直径**。
 * 再往上一档 44dp 就会跟顶栏那颗 46dp 的搜索钮抢层级。
 *
 * 球体本色 #F3F0E9 对纸面只有 **1.05–1.06:1** —— 裸球在浅底上等于不存在。所以外面必须有
 * 那条 **1dp 的 Tone.quiet 环**：环对纸面 4.70–4.76、环对球体 4.50（帧上众数）。
 * 环不是装饰，是这个元素能存在的前提。
 *
 * ## 它怎么动（2026-10-27 重做）
 *
 * 业主三条口径：「不要放大缩小（呼吸）」「重新设计一套与本 app 匹配的交互与动画，要包含完整动作」
 * 「不要单独一个图层，要与其它内容同层、可以变化位置」。于是：
 *
 * - **没有呼吸**。球径恒定，永不吃掉邻居的边距。
 * - 动的是**眼睛**，不是整个球：眨眼 / 看左 / 看右 / 抬头 / 低头 / 摇头 / 扫视 / 打盹。
 *   每一次都是一个**有始有终的动作**，做完回到静止 —— 不是一条永不结束的循环。
 * - 动作由这一页的真实状态决定：出错会摇头，加载会左右扫，下课会点头，深夜会打盹。
 * - motion.canMove == false（系统关了动画）时一律静止，靠读屏文案区分状态。
 *
 * 为什么不是贴图：眼睛是 96 个顶点逐帧现算的矢量多边形（[AiBallGlyphs]，由
 * tools/gen_ai_ball_glyphs.py 从开源引擎 grok-ball 的真实渲染 DOM 反解而来），
 * 没有一张位图参与。
 *
 * ## 它和内容在同一层
 *
 * 2026-10-26 之前它挂在顶栏 actions 里 —— 那是 chrome 层，不跟页面滚，切页也不跟着淡入，
 * 等于一枚浮在所有内容之外的徽章。现在它由 [AiBallRow] 放在每一页**第一个区块标题的左边**，
 * 随列表一起滚走：它是这一页内容的一部分，不是叠加物。
 *
 * ## 它能被戳
 *
 * - 点一下：眼睛看向你手指那侧，1.4s 后归位。**不改表情** —— 表情是真实数据的读数。
 * - 长按：切下一张脸试看，5 秒后自动回到真实状态。
 *
 * ## 它不是玻璃
 *
 * 不采样 backdrop、不加 RenderEffect、不新增任何全屏玻璃面，画在页面自己的一小块画布上。
 */
@Composable
fun AiBall(
    state: AiBallState,
    modifier: Modifier = Modifier,
    diameter: Dp = AiBall.DIAMETER,
    hitSlop: Dp = AiBall.HIT_SLOP,
    /**
     * 刚拿到内容时先播一秒 [AiBallState.Curious]（返工总单 B3）。
     *
     * 默认关：首页那颗球说的是「此刻什么最重要」，插一段转录会打断它；
     * 内容页那颗球只回答「这一页现在怎样」，播一次正好解释「刚刚刷新过」。
     */
    announceArrival: Boolean = false,
    /**
     * 是否投一层柔和投影（返工总单 D3，按 GPT 稿）。
     *
     * 稿 `style.css:84`：`.ball-button svg{filter:drop-shadow(0 6px 7px #3e455517)}`
     * —— 色 #3e4555、透明度 0x17≈9%、y=6px、blur=7px；而 `.small-ball` /
     * `.inline-ball` 明确 `filter:none`。字面照抄会是「所有球都发光」，
     * 恰好相反。默认只在首页那颗（DIAMETER 及以上）投，页面内 44dp 不投。
     */
    castShadow: Boolean = diameter >= AiBall.DIAMETER,
) {
    val motion = rememberMotion()
    val scope = rememberCoroutineScope()

    // 长按试看：索引 -1 = 跟随真实状态。5 秒后自动归位，免得留在假状态上骗人。
    var previewIndex by remember { mutableIntStateOf(-1) }
    val realShown = if (previewIndex >= 0) AiBall.FACES[previewIndex] else state
    // 无条件调用（composable 不允许条件调用），只在 announceArrival 时才采信结果。
    val arrival = rememberArrivalState(realShown)
    val shown = if (announceArrival) arrival else realShown

    LaunchedEffect(previewIndex) {
        if (previewIndex >= 0) {
            delay(AiBall.PREVIEW_MS)
            previewIndex = -1
        }
    }

    val blink = remember { Animatable(0f) }
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }

    // 一次一个动作：做完就静止，等下一拍。停机时先把眼睛放回正中，
    // 免得停在「正在摇头」的半路上动不了。
    LaunchedEffect(shown, motion.canMove) {
        blink.snapTo(0f)
        gazeX.snapTo(0f)
        gazeY.snapTo(0f)
        if (!motion.canMove) return@LaunchedEffect
        while (true) {
            delay(AiBall.gapMs(shown) + Random.nextLong(0L, 700L))
            AiBall.actOnce(shown, gazeX, gazeY, blink)
        }
    }

    val describeTap = "让小球看一眼"
    val describeLong = "换一张脸试看"

    Box(
        modifier = modifier
            .size(diameter + hitSlop * 2)
            .pointerInput(shown, motion.canMove) {
                detectTapGestures(
                    onPress = {
                        press.animateTo(1f, tween(90))
                        tryAwaitRelease()
                        press.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
                    },
                    onTap = { at ->
                        val half = size.width / 2f
                        val nx = ((at.x - half) / half).coerceIn(-1f, 1f)
                        val ny = ((at.y - half) / half).coerceIn(-1f, 1f)
                        scope.launch {
                            if (motion.canMove) {
                                gazeX.animateTo(nx, tween(220, easing = FastOutSlowInEasing))
                                gazeY.animateTo(ny, tween(220, easing = FastOutSlowInEasing))
                                delay(AiBall.GAZE_HOLD_MS)
                                gazeX.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                                gazeY.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                            }
                        }
                    },
                    onLongPress = {
                        previewIndex = if (previewIndex < 0) 0 else (previewIndex + 1) % AiBall.FACES.size
                    },
                )
            }
            .semantics {
                contentDescription = if (previewIndex >= 0) {
                    "表情试看：" + AiBall.labelOf(shown)
                } else {
                    AiBall.labelOf(shown)
                }
                role = Role.Button
                onClick(label = describeTap) {
                    previewIndex = if (previewIndex < 0) 0 else (previewIndex + 1) % AiBall.FACES.size
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(diameter)
                .then(
                    if (castShadow) {
                        Modifier.shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color(0x2E3E4555),
                            spotColor = Color(0x2E3E4555),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            val d = size.minDimension
            val c = Offset(size.width / 2f, size.height / 2f)
            val squash = 1f - AiBall.PRESS_SCALE * press.value
            val r = d / 2f * squash

            drawCircle(
                brush = Brush.radialGradient(
                    colors = AiBall.bodyColors(),
                    center = Offset(
                        c.x + (AiBallGlyphs.GRADIENT_CX - 0.5f) * d,
                        c.y + (AiBallGlyphs.GRADIENT_CY - 0.5f) * d,
                    ),
                    radius = AiBallGlyphs.GRADIENT_R * d,
                ),
                radius = r,
                center = c,
            )

            val shiftX = gazeX.value * AiBall.GAZE_SHIFT * d
            val shiftY = gazeY.value * AiBall.GAZE_SHIFT * d
            val shut = 1f - AiBall.BLINK_SQUASH * blink.value
            val eyeColor = Color(AiBallGlyphs.EYE_COLOR)
            AiBallGlyphs.eyesOf(shown).forEach { pts ->
                if (pts.size >= 6) {
                    var sumY = 0f
                    var k = 1
                    while (k < pts.size) {
                        sumY += pts[k]
                        k += 2
                    }
                    val eyeCy = sumY / (pts.size / 2f)
                    val eye = Path()
                    var i = 0
                    while (i + 1 < pts.size) {
                        val x = c.x + pts[i] * d * squash + shiftX
                        val y = c.y + (eyeCy + (pts[i + 1] - eyeCy) * shut) * d * squash + shiftY
                        if (i == 0) eye.moveTo(x, y) else eye.lineTo(x, y)
                        i += 2
                    }
                    eye.close()
                    drawPath(eye, eyeColor)
                }
            }

            val ringPx = AiBall.RING_WIDTH.toPx()
            drawCircle(
                color = Tone.quiet,
                radius = r - ringPx / 2f,
                center = c,
                style = Stroke(width = ringPx),
            )
        }
    }
}

/**
 * 「球 + 一段内容」的横排：球在最左，[AiBall.INLINE_GAP] 之后才是内容。
 *
 * 每一页的第一个区块标题都长这样 —— 球在内容层里，跟着列表一起滚，不是顶栏上的一枚徽章。
 * 传进来的 modifier 直接作用在 Row 上（各页自己决定外边距）。
 */
@Composable
fun AiBallRow(
    state: AiBallState,
    modifier: Modifier = Modifier,
    diameter: Dp = AiBall.DIAMETER,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AiBall(state = state, diameter = diameter, hitSlop = AiBall.INLINE_SLOP)
        Spacer(Modifier.width(AiBall.INLINE_GAP))
        content()
    }
}

private const val AiBallLabel = "aiball"

/** 小球的固定尺寸、文案与动作参数。数值理由见 [AiBall] 的文档，别在这里改数。 */
object AiBall {
    /** 直径（行内：每页第一个区块标题左边那颗）。 */
    val DIAMETER = 36.dp

    /**
     * 页面级直径（48dp 顶栏里那颗）：**44dp**（返工总单 B5）。
     *
     * 44 是「眼睛能读出表情」的下限，又不抢 46dp 搜索钮的层级；顶栏 52dp 放得下。
     * 36dp 是旧版装饰尺寸 —— 那时球只是图标，现在它是这一页的语气，得看清脸。
     */
    val DIAMETER_PAGE = 44.dp

    /** 首页标题区的主视觉直径：96dp = 560dpi 下 336px，眼睛墨迹约 88x64px —— 那是一张脸。 */
    val DIAMETER_FIELD = 96.dp

    /** 单独摆放时的命中区外扩：36 + 6x2 = 48dp，正好到触控下限。 */
    val HIT_SLOP = 6.dp

    /**
     * 行内摆放时的外扩：**4dp**（返工总单 B8）。
     *
     * 36 + 4×2 = **44dp**，正好到触控下限。原来是 3dp（42dp，低于底线）——
     * 那时靠「反正旁边没别的东西」蒙混，但无障碍检查器量出来就是不够。
     * 改外扩而不是改直径：视觉尺寸是设计定的，命中区是我们该补的。
     */
    val INLINE_SLOP = 4.dp

    /** 行内球与右侧文字的视觉间隙（球自己还有 3dp 外扩，合起来 6dp）。 */
    val INLINE_GAP = 3.dp

    /** 环宽。裸球对纸面只有 1.05:1，没有这条环它就不存在。 */
    val RING_WIDTH = 1.dp

    /** 闭眼 / 睁眼时长。合起来 180ms，比真眼略快，因为球小。 */
    const val BLINK_CLOSE_MS = 70
    const val BLINK_OPEN_MS = 110

    /** 闭眼时眼睛纵向压到原来的 18% —— 留一线，全压平会变成横线，认不出是眼。 */
    const val BLINK_SQUASH = 0.82f

    /** 按下时球体缩 6%。和顶栏按钮的按压量一致（GlassControls 用的是 4%，球更小所以多一点）。 */
    const val PRESS_SCALE = 0.06f

    /** 注视时眼睛最多偏移 0.10 球径（36dp 上 3.6dp）—— 再远眼珠就出球面了。 */
    const val GAZE_SHIFT = 0.10f

    /** 看完之后保持多久再归位。 */
    const val GAZE_HOLD_MS = 1400L

    /** 长按试看一张脸的时间。 */
    const val PREVIEW_MS = 5000L

    /** 随机动作的两个间隔基数（真实间隔再加 0..700ms 抖动，避免整屏一个节奏）。 */
    private const val GAP_FAST = 1800L
    private const val GAP_SLOW = 3000L

    /** 十二张脸（AiBallGlyphs 里的名字必须与这里一一对应）。 */
    val FACES = listOf(
        AiBallState.Sleep, AiBallState.WakeUp, AiBallState.Idle, AiBallState.Curious,
        AiBallState.Focus, AiBallState.Thinking, AiBallState.Busy, AiBallState.Done,
        AiBallState.Error, AiBallState.Waiting, AiBallState.Loading, AiBallState.Searching,
    )

    /** 每个状态两拍之间的间隔：越「忙」的状态动得越勤。 */
    fun gapMs(state: AiBallState): Long = when (state) {
        AiBallState.Loading, AiBallState.Searching -> 620L
        AiBallState.Error -> 2100L
        AiBallState.Busy, AiBallState.Focus, AiBallState.Thinking -> GAP_FAST
        AiBallState.Sleep -> 4200L
        else -> GAP_SLOW
    }

    /**
     * 做一个动作，做完就停。
     *
     * 八个动作都有明确语义，不是装饰轮播：眨眼（活着）· 看左 / 看右（在留意）·
     * 抬头（好奇）· 低头（专注 / 打盹）· 摇头（出错了）· 扫视（正在取）· 点头（做完了）。
     */
    suspend fun actOnce(
        state: AiBallState,
        gazeX: Animatable<Float, *>,
        gazeY: Animatable<Float, *>,
        blink: Animatable<Float, *>,
    ) {
        when (state) {
            AiBallState.Sleep -> {
                // 打盹：眼向下沉、半闭，停一会儿再睁开。
                gazeY.animateTo(0.5f, tween(280, easing = FastOutSlowInEasing))
                blink.animateTo(0.86f, tween(280, easing = FastOutSlowInEasing))
                delay(1600)
                blink.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                gazeY.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            }
            AiBallState.Loading, AiBallState.Searching -> {
                // 扫视：从左看到右一次（不是无限往返）。
                gazeX.animateTo(-0.9f, tween(300, easing = FastOutSlowInEasing))
                gazeX.animateTo(0.9f, tween(280, easing = FastOutSlowInEasing))
                gazeX.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            }
            AiBallState.Error -> {
                // 摇头：两下短促的左右摆。
                repeat(2) {
                    gazeX.animateTo(0.75f, tween(70, easing = LinearEasing))
                    gazeX.animateTo(-0.75f, tween(90, easing = LinearEasing))
                }
                gazeX.animateTo(0f, tween(120, easing = LinearEasing))
            }
            AiBallState.Done -> {
                // 点头：眼睛向下点一下，顺带眨一次。
                gazeY.animateTo(0.55f, tween(170, easing = FastOutSlowInEasing))
                gazeY.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                delay(120)
                blink.animateTo(1f, tween(BLINK_CLOSE_MS, easing = LinearEasing))
                blink.animateTo(0f, tween(BLINK_OPEN_MS, easing = LinearEasing))
            }
            AiBallState.Busy, AiBallState.Focus, AiBallState.Thinking -> {
                // 低头专注：眼睛略向下沉，停 0.9s 再抬起来。
                gazeY.animateTo(0.4f, tween(280, easing = FastOutSlowInEasing))
                delay(900)
                gazeY.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            }
            AiBallState.Curious -> {
                // 抬头：看向上方。
                gazeY.animateTo(-0.65f, tween(200, easing = FastOutSlowInEasing))
                delay(520)
                gazeY.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            }
            AiBallState.Waiting -> {
                // 左右各看一眼（在等人）。
                gazeX.animateTo(-0.8f, tween(240, easing = FastOutSlowInEasing))
                delay(420)
                gazeX.animateTo(0.8f, tween(280, easing = FastOutSlowInEasing))
                delay(420)
                gazeX.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            }
            else -> when (Random.nextInt(4)) {
                0 -> {
                    blink.animateTo(1f, tween(BLINK_CLOSE_MS, easing = LinearEasing))
                    blink.animateTo(0f, tween(BLINK_OPEN_MS, easing = LinearEasing))
                }
                1 -> {
                    gazeX.animateTo(-0.85f, tween(220, easing = FastOutSlowInEasing))
                    delay(360)
                    gazeX.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                }
                2 -> {
                    gazeX.animateTo(0.85f, tween(220, easing = FastOutSlowInEasing))
                    delay(360)
                    gazeX.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                }
                else -> {
                    gazeY.animateTo(-0.45f, tween(200, easing = FastOutSlowInEasing))
                    delay(300)
                    gazeY.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    /**
     * 球的**台词**（返工总单 B1，语言层）。
     *
     * 设计稿 `docs/B4-12-BALL-AS-CORE.md` §4：「脸说情绪，字说事实，字不许重复脸说过的话」。
     * 所以这句不是描述表情，而是**把这张脸反译成一句人话** —— 它是这一页的
     * 第三行，替掉原来那句纯文字的「距离下一节课」。
     *
     * 与 [labelOf] 共用一套措辞：读屏念的、屏幕上写的必须是同一件事，
     * 否则「看得见的人和看不见的人听到两个版本」。
     */
    fun lineOf(state: AiBallState): String = when (state) {
        AiBallState.Sleep -> "这会儿没事，先睡一会儿"
        AiBallState.WakeUp -> "早上好，今天的课在后面"
        AiBallState.Idle -> "今天暂时没有要你处理的事"
        AiBallState.Curious -> "刚拿到新的内容"
        AiBallState.Focus -> "有件事马上要你去办"
        AiBallState.Thinking -> "正在处理，稍等一下"
        AiBallState.Busy -> "正在上课"
        AiBallState.Done -> "今天的课都上完了"
        AiBallState.Error -> "这一页没能取到数据"
        AiBallState.Waiting -> "有一张签到在等你"
        AiBallState.Loading -> "正在读取"
        AiBallState.Searching -> "正在翻找资料"
    }

    /** 读屏文案：说事实，不说「小球」。 */
    fun labelOf(state: AiBallState): String = when (state) {
        AiBallState.Sleep -> "这会儿没课也没待办"
        AiBallState.WakeUp -> "新的一天开始了"
        AiBallState.Idle -> "当前没有待办"
        AiBallState.Curious -> "刚拿到新的内容"
        AiBallState.Focus -> "有一件待办快到期了"
        AiBallState.Thinking -> "正在处理"
        AiBallState.Busy -> "正在上课"
        AiBallState.Done -> "今天的课已结束"
        AiBallState.Error -> "这一页没能取到数据"
        AiBallState.Waiting -> "有一张签到在等你"
        AiBallState.Loading -> "正在读取"
        AiBallState.Searching -> "正在检索资料"
    }

    /** 球体渐变三站（引擎的 0% / 62% / 100%）。 */
    fun bodyColors(): List<Color> = AiBallGlyphs.BODY_COLORS.map { Color(it) }
}
