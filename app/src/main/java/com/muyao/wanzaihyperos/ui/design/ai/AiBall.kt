package com.muyao.wanzaihyperos.ui.design.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import com.muyao.wanzaihyperos.ui.main.HorizontalDragZones
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import com.muyao.wanzaihyperos.ui.design.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import io.github.neoruaa.glasmio.glass.applyGlassDragScale
import io.github.neoruaa.glasmio.glass.pressHighlightSpring

/**
 * 内容层的真实状态球：固定球径，安全平行眼形，不采样 backdrop。
 * 单击看你、双击一圈，静止长按后可临时拖拽并看真实说明；离页/后台立即归中。
 * 自旋弹簧和球面 yaw 改编自 grok-ball（MIT），眼形由本地安全 policy 提供，非旧 DOM。
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
    castShadow: Boolean = diameter >= AiBall.DIAMETER_FIELD,
    stateDescription: String? = null,
    outline: Boolean = false,
    presentation: AiBallPresentation? = AiBallPresentation.Home,
    work: AiBallWork = AiBallWork(),
    onStatusRequested: (() -> Unit)? = null,
) {
    val motion = rememberMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pageActive = LocalPageActive.current

    val arrival = rememberArrivalState(state)
    val shown = if (announceArrival) arrival else state
    val description = stateDescription?.takeIf { it.isNotBlank() }
        ?: if (presentation != null) AiBallPresentationPolicy.description(state, work) else AiBall.labelOf(state)
    val captureOwner = remember { Any() }
    val dragPress = remember { Animatable(0f, 0.001f) }
    val returnOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var heldOffset by remember { mutableStateOf(Offset.Zero) }
    var tipVisible by remember { mutableStateOf(false) }
    var autoDismissStatus by remember { mutableStateOf(false) }
    val tipAlpha = remember { Animatable(0f) }
    var ballCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var lifecycleStarted by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var interaction by remember { mutableStateOf(AiBallInteraction()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!lifecycleStarted) {
                interaction = interaction.cancel(); heldOffset = Offset.Zero; tipVisible = false
                HorizontalDragZones.releasePointers(captureOwner)
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            HorizontalDragZones.releasePointers(captureOwner)
        }
    }
    val enabled = pageActive && lifecycleStarted
    val moving = enabled && motion.canMove
    val latestInteraction = rememberUpdatedState(interaction)
    val blink = remember { Animatable(0f) }
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var ribbonAlpha by remember { mutableFloatStateOf(0f) }
    var feedback by remember(presentation) { mutableStateOf(AiBallFeedback()) }
    val latestWork = rememberUpdatedState(work)
    val latestShown = rememberUpdatedState(shown)
    val latestStatusRequest = rememberUpdatedState(onStatusRequested)
    LaunchedEffect(enabled, motion.canMove) {
        interaction = interaction.cancel(); heldOffset = Offset.Zero; tipVisible = false
        HorizontalDragZones.releasePointers(captureOwner)
    }

    // One coroutine owns every animated channel; a new user ticket cancels autonomous work.
    LaunchedEffect(
        if (interaction.action == AiBallAction.REST) shown else null,
        if (presentation != null && interaction.action == AiBallAction.REST) work else null,
        presentation, enabled, moving, interaction.action, interaction.ticket,
    ) {
        if (presentation != null) {
            feedback = feedback.observe(work, moving && interaction.action == AiBallAction.REST)
        }
        blink.snapTo(0f); gazeX.snapTo(0f); gazeY.snapTo(0f)
        yaw = 0f; ribbonAlpha = 0f
        if (!enabled) { dragPress.snapTo(0f); returnOffset.snapTo(Offset.Zero); tipAlpha.snapTo(0f); tipVisible = false; return@LaunchedEffect }
        if (interaction.action == AiBallAction.RETURN) {
            try {
                returnOffset.snapTo(heldOffset)
                if (moving) {
                    coroutineScope {
                        launch { dragPress.animateTo(0f, pressHighlightSpring()) }
                        launch { returnOffset.animateTo(Offset.Zero, spring(0.5f, 300f)) }
                        launch { tipAlpha.animateTo(0f, tween(240)) }
                    }
                } else {
                    dragPress.snapTo(0f); returnOffset.snapTo(Offset.Zero)
                    tipAlpha.animateTo(0f, tween(120))
                }
                heldOffset = Offset.Zero; tipVisible = false
                interaction = interaction.cancel()
            } finally { returnOffset.snapTo(Offset.Zero) }
            return@LaunchedEffect
        }
        if (interaction.action == AiBallAction.TRACK || interaction.action == AiBallAction.HOLD) {
            coroutineScope {
                if (interaction.action == AiBallAction.HOLD) launch {
                    tipAlpha.animateTo(1f, tween(if (moving) 260 else 120))
                }
                if (moving) launch { dragPress.animateTo(1f, pressHighlightSpring()) }
            }
        } else {
            dragPress.snapTo(0f); heldOffset = Offset.Zero
            returnOffset.snapTo(Offset.Zero); tipAlpha.snapTo(0f); tipVisible = false
        }
        if (interaction.action == AiBallAction.HOLD && autoDismissStatus) {
            delay(AiBallDrag.TIP_MS)
            autoDismissStatus = false
            interaction = interaction.release()
            return@LaunchedEffect
        }
        if (!moving) return@LaunchedEffect
        try {
            when (interaction.action) {
                AiBallAction.REST -> {
                    val response = feedback.response
                    feedback = feedback.copy(response = AiBallResponse.None)
                    if (presentation != null && response != AiBallResponse.None) {
                        presentationResponse(response, gazeX, gazeY, blink)
                    }
                    while (isActive) {
                        val profileActive = presentation != null
                        delay((if (profileActive) AiBallPresentationPolicy.gapMs(shown, work) else AiBall.gapMs(shown)) + Random.nextLong(0L, 700L))
                        if (profileActive) presentationActOnce(latestShown.value, latestWork.value, gazeX, gazeY, blink)
                        else AiBall.actOnce(shown, gazeX, gazeY, blink)
                    }
                }
                AiBallAction.TRACK, AiBallAction.HOLD -> {
                    var previous = withFrameNanos { it }
                    while (isActive) {
                        val now = withFrameNanos { it }
                        val dt = (now - previous) / 1_000_000_000.0
                        previous = now
                        gazeX.snapTo(AiBallSpin.smooth(gazeX.value, latestInteraction.value.x, dt))
                        gazeY.snapTo(AiBallSpin.smooth(gazeY.value, latestInteraction.value.y, dt))
                    }
                }
                AiBallAction.LOOK -> {
                    gazeX.animateTo(interaction.x, tween(180, easing = FastOutSlowInEasing))
                    gazeY.animateTo(interaction.y, tween(180, easing = FastOutSlowInEasing))
                    delay(AiBall.GAZE_HOLD_MS)
                    gazeX.animateTo(0f, tween(240)); gazeY.animateTo(0f, tween(240))
                    interaction = interaction.copy(action = AiBallAction.REST)
                }
                AiBallAction.SPIN -> {
                    var spring = AiBallSpin.Spring()
                    val start = withFrameNanos { it }
                    var previous = start
                    var seconds: Double
                    do {
                        val now = withFrameNanos { it }
                        seconds = (now - start) / 1_000_000_000.0
                        spring = AiBallSpin.step(spring, (now - previous) / 1_000_000_000.0)
                        previous = now
                        yaw = spring.angle.toFloat()
                        ribbonAlpha = AiBallSpin.ribbonAlpha(seconds, spring.velocity)
                    } while (isActive && !spring.settled && seconds < AiBallSpin.MAX_SECONDS)
                    interaction = interaction.cancel()
                }
                AiBallAction.RETURN -> Unit
            }
        } finally { yaw = 0f; ribbonAlpha = 0f }
    }

    fun showStatus() {
        if (!enabled) return
        latestStatusRequest.value?.invoke()
        heldOffset = Offset.Zero; tipVisible = true; autoDismissStatus = true
        interaction = aiBallStatusInteraction(interaction)
    }
    fun accessibleLook() {
        if (moving) interaction = interaction.cancel().observe(0.5f, 0f).copy(action = AiBallAction.LOOK)
    }

    val describeTap = "让小球看一眼"
    val describeLong = "查看当前状态"

    Box(
        modifier = modifier
            .size(diameter + hitSlop * 2)
            .pointerInput(enabled, motion.canMove, diameter, hitSlop) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                    val half = size.width / 2f
                    fun observe(at: Offset) {
                        if (motion.canMove) interaction = interaction.observe((at.x - half) / half, (at.y - half) / half)
                    }
                    var released = false
                    var held = false
                    var tap = down
                    try {
                        val result = withTimeoutOrNull(AiBallDrag.HOLD_MS) {
                            var event = awaitPointerEvent(PointerEventPass.Final)
                            while (true) {
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@withTimeoutOrNull AiBallPressDecision.CANCEL
                                val decision = aiBallPressDecision(
                                    change.uptimeMillis - down.uptimeMillis,
                                    (change.position - down.position).getDistance(),
                                    viewConfiguration.touchSlop, change.pressed,
                                    event.changes.count { it.pressed }, change.isConsumed,
                                )
                                if (decision == AiBallPressDecision.TAP) tap = change
                                if (decision != AiBallPressDecision.WAIT) return@withTimeoutOrNull decision
                                event = awaitPointerEvent(PointerEventPass.Initial)
                                val initial = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@withTimeoutOrNull AiBallPressDecision.CANCEL
                                if (aiBallPressDecision(
                                        initial.uptimeMillis - down.uptimeMillis,
                                        (initial.position - down.position).getDistance(),
                                        viewConfiguration.touchSlop, initial.pressed,
                                        event.changes.count { it.pressed }, initial.isConsumed,
                                    ) == AiBallPressDecision.CANCEL
                                ) return@withTimeoutOrNull AiBallPressDecision.CANCEL
                                event = awaitPointerEvent(PointerEventPass.Final)
                            }
                            @Suppress("UNREACHABLE_CODE")
                            AiBallPressDecision.CANCEL
                        }
                        val last = currentEvent.changes.firstOrNull { it.id == down.id }
                        val confirmed = result == AiBallPressDecision.HOLD || (result == null && last != null &&
                            aiBallPressDecision(
                                AiBallDrag.HOLD_MS, (last.position - down.position).getDistance(),
                                viewConfiguration.touchSlop, last.pressed,
                                currentEvent.changes.count { it.pressed }, last.isConsumed,
                            ) == AiBallPressDecision.HOLD)
                        if (result == AiBallPressDecision.TAP) {
                            released = true
                            observe(tap.position)
                            if (motion.canMove) interaction = interaction.tap(tap.uptimeMillis, tap.position.x, tap.position.y, viewConfiguration.touchSlop * 2)
                        } else if (confirmed) {
                            HorizontalDragZones.claimPointer(down.id.value, captureOwner)
                            held = true; autoDismissStatus = false; tipVisible = true
                            heldOffset = Offset.Zero
                            interaction = aiBallStatusInteraction(interaction)
                            latestStatusRequest.value?.invoke()
                            last?.consume()
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                                heldOffset = change.position - down.position
                                observe(change.position)
                                change.consume()
                                if (!change.pressed) { released = true; break }
                                awaitPointerEvent(PointerEventPass.Main)
                            }
                        }
                    } finally {
                        HorizontalDragZones.releasePointers(captureOwner)
                        if (held) interaction = interaction.release()
                        else if (!released) {
                            heldOffset = Offset.Zero; tipVisible = false; interaction = interaction.cancel()
                        }
                    }
                }
            }
            .semantics {
                contentDescription = "状态球"
                this.stateDescription = description
                role = Role.Button
                onClick(label = describeTap) {
                    accessibleLook()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("让小球转一圈") {
                        if (moving) interaction = interaction.spin()
                        true
                    },
                    CustomAccessibilityAction(describeLong) { showStatus(); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val visualOffset = if (!enabled) Offset.Zero else when (interaction.action) {
            AiBallAction.TRACK, AiBallAction.HOLD -> heldOffset
            AiBallAction.RETURN -> if (moving) returnOffset.value else Offset.Zero
            else -> Offset.Zero
        }
        val tipBounds = ballCoordinates?.takeIf { it.isAttached }?.boundsInWindow()
        CompositionLocalProvider(LocalAiBallTipBounds provides tipBounds) {
            if (enabled && tipVisible) AiBallStatusTooltip(description, tipAlpha.value)
        }
        Canvas(
            modifier = Modifier
                .size(diameter)
                .graphicsLayer {
                    applyGlassDragScale(
                        pressProgress = if (moving) dragPress.value else 0f,
                        offset = visualOffset,
                        nativeButtonLift = true,
                    )
                }
                .onGloballyPositioned { ballCoordinates = it }
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
            val r = d / 2f

            if (!outline) drawCircle(
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

            val shiftX = if (moving) gazeX.value * AiBall.GAZE_SHIFT * d else 0f
            val shiftY = if (moving) gazeY.value * AiBall.GAZE_SHIFT * d else 0f
            val shut = if (moving) 1f - AiBall.BLINK_SQUASH * blink.value else 1f
            val eyeColor = Color(AiBallGlyphs.EYE_COLOR)
            val eyes = if (presentation != null) {
                AiBallPresentationPolicy.eyes(presentation, shown, work)
            } else AiBallGlyphs.eyesOf(shown)
            eyes.forEach { pts ->
                if (pts.size >= 6) {
                    var sumX = 0f
                    var sumY = 0f
                    var k = 0
                    while (k + 1 < pts.size) {
                        sumX += pts[k]; sumY += pts[k + 1]
                        k += 2
                    }
                    val eyeCx = sumX / (pts.size / 2f)
                    val eyeCy = sumY / (pts.size / 2f)
                    val projected = AiBallSpin.project(eyeCx, eyeCy, if (moving) yaw.toDouble() else 0.0)
                    if (!projected.visible) return@forEach
                    val eye = Path()
                    var i = 0
                    while (i + 1 < pts.size) {
                        val x = c.x + (projected.centerX + (pts[i] - eyeCx) * projected.scaleX) * d + shiftX
                        val y = c.y + (eyeCy + (pts[i + 1] - eyeCy) * shut) * d + shiftY
                        if (i == 0) eye.moveTo(x, y) else eye.lineTo(x, y)
                        i += 2
                    }
                    eye.close()
                    drawPath(eye, if (outline) Tone.quiet else eyeColor,
                        style = if (outline) Stroke(width = (d * 0.018f).coerceAtLeast(1f)) else androidx.compose.ui.graphics.drawscope.Fill)
                }
            }

            if (moving && ribbonAlpha > 0f) {
                val colors = listOf(Color(0xFF83A9B0), Color(0xFFB3A3C7))
                repeat(AiBallSpin.RIBBON_COUNT) { index ->
                    val path = Path()
                    repeat(17) { step ->
                        val angle = yaw.toDouble() - step * 0.055 + index * Math.PI
                        val x = c.x + sin(angle).toFloat() * r * 0.94f
                        val y = c.y + cos(angle * 1.3 + index).toFloat() * r * 0.38f
                        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, colors[index].copy(alpha = ribbonAlpha), style = Stroke(width = (d * 0.015f).coerceAtLeast(1f)))
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
    stateDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AiBall(state = state, diameter = diameter, hitSlop = AiBall.INLINE_SLOP, stateDescription = stateDescription)
        Spacer(Modifier.width(AiBall.INLINE_GAP))
        content()
    }
}

private suspend fun presentationResponse(
    response: AiBallResponse,
    gazeX: Animatable<Float, *>,
    gazeY: Animatable<Float, *>,
    blink: Animatable<Float, *>,
) {
    if (response == AiBallResponse.Success) {
        gazeY.animateTo(0.25f, tween(160)); gazeY.animateTo(0f, tween(220))
        blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(110))
    } else if (response == AiBallResponse.Error) {
        gazeX.animateTo(-0.25f, tween(120)); gazeX.animateTo(0.25f, tween(180))
        gazeX.animateTo(0f, tween(150))
    }
}

private suspend fun presentationActOnce(
    state: AiBallState,
    work: AiBallWork,
    gazeX: Animatable<Float, *>,
    gazeY: Animatable<Float, *>,
    blink: Animatable<Float, *>,
) {
    when {
        state == AiBallState.Sleep -> AiBall.actOnce(state, gazeX, gazeY, blink)
        work.phase == AiBallWorkPhase.Reading -> {
            gazeX.animateTo(-0.35f, tween(260)); gazeX.animateTo(0.35f, tween(320))
            gazeX.animateTo(0f, tween(220))
        }
        state == AiBallState.Error || work.phase == AiBallWorkPhase.Error -> {
            blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(110))
        }
        state == AiBallState.Waiting || work.phase == AiBallWorkPhase.Waiting -> {
            gazeX.animateTo(0.35f, tween(220)); delay(350); gazeX.animateTo(0f, tween(240))
        }
        else -> when (Random.nextInt(3)) {
            0 -> { blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(110)) }
            1 -> { gazeX.animateTo(0.35f, tween(220)); delay(300); gazeX.animateTo(0f, tween(240)) }
            else -> { gazeY.animateTo(-0.25f, tween(200)); delay(280); gazeY.animateTo(0f, tween(240)) }
        }
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

    /** 注视时眼睛最多偏移 0.10 球径（36dp 上 3.6dp）—— 再远眼珠就出球面了。 */
    const val GAZE_SHIFT = 0.10f

    /** 看完之后保持多久再归位。 */
    const val GAZE_HOLD_MS = 1400L

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
        AiBallState.Sleep -> "现在是休息时段"
        AiBallState.WakeUp -> "早上好，今天的课在后面"
        AiBallState.Idle -> "当前页面已就绪"
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

    /** Without source facts, announce only the state represented by this face. */
    fun labelOf(state: AiBallState): String = lineOf(state)

    /** 球体渐变三站（引擎的 0% / 62% / 100%）。 */
    fun bodyColors(): List<Color> = AiBallGlyphs.BODY_COLORS.map { Color(it) }
}
