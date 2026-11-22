package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.MotionSourceLease
import com.muyao.wanzaihyperos.ui.design.MotionSourceStamp
import com.muyao.wanzaihyperos.ui.main.pages.motionRevealRect
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class CourseRevealDirection { Enter, Exit, Replace }

internal fun courseRevealDirection(from: TronSub, to: TronSub): CourseRevealDirection = when {
    to is TronSub.Course && (from == TronSub.None || from == TronSub.Schedule) -> CourseRevealDirection.Enter
    from is TronSub.Course && (to == TronSub.None || to == TronSub.Schedule) -> CourseRevealDirection.Exit
    else -> CourseRevealDirection.Replace
}

@Composable
internal fun TronCourseReveal(
    target: TronSub,
    source: MotionSourceLease?,
    root: Rect,
    sourceStamp: () -> MotionSourceStamp,
    onSettled: (TronSub) -> Unit = {},
    content: @Composable (TronSub) -> Unit,
) {
    var shown by remember { mutableStateOf(target) }
    val expansion = remember { Animatable(1f) }
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val move = motion.canMove && !motion.highContrast
    var capturedSource by remember { mutableStateOf<MotionSourceLease?>(null) }
    var closing by remember { mutableStateOf(false) }
    val latestStamp by rememberUpdatedState(sourceStamp)
    val latestSettled by rememberUpdatedState(onSettled)
    LaunchedEffect(target, move) {
        val direction = courseRevealDirection(shown, target)
        when (direction) {
            CourseRevealDirection.Enter -> {
                capturedSource = source
                closing = false
                shown = target
                expansion.snapTo(0f)
                expansion.animateTo(1f, tween(if (move && source != null) 260 else 120))
            }
            CourseRevealDirection.Exit -> {
                closing = true
                expansion.animateTo(0f, tween(if (move && capturedSource?.current(latestStamp()) != null) 220 else 120))
                shown = target
                closing = false
            }
            CourseRevealDirection.Replace -> {
                shown = target
                closing = false
                expansion.snapTo(1f)
            }
        }
        latestSettled(target)
    }
    fun currentSource(): Rect? {
        val lease = capturedSource ?: return null
        val now = latestStamp()
        if (!move || !lease.matches(now)) return null
        return if (closing) lease.current(now) else lease.bounds
    }
    val surface = MiuixTheme.colorScheme.surface
    val radius = with(LocalDensity.current) { 20.dp.toPx() }
    val course = shown is TronSub.Course
    Box(Modifier.fillMaxSize().drawWithContent {
        if (!course) { drawContent(); return@drawWithContent }
        val p = expansion.value.coerceIn(0f, 1f)
        val localTarget = Rect(0f, 0f, size.width, size.height)
        val frame = currentSource()?.let { motionRevealRect(it.translate(-root.topLeft), localTarget, p) }
        if (frame == null) {
            drawRoundRect(surface, alpha = p)
            drawContent()
        } else {
            val corner = CornerRadius(radius * (1f - p))
            drawRoundRect(surface, frame.topLeft, frame.size, corner)
            val path = Path().apply { addRoundRect(RoundRect(frame, corner)) }
            clipPath(path) { this@drawWithContent.drawContent() }
        }
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = expansion.value.coerceIn(0f, 1f)
            alpha = if (!course) 1f else if (currentSource() != null) ((p - .20f) / .80f).coerceIn(0f, 1f) else p
        }.then(if (closing) Modifier.semantics { hideFromAccessibility() } else Modifier)) { content(shown) }
        if (closing) {
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {},
            ))
        }
    }
}
