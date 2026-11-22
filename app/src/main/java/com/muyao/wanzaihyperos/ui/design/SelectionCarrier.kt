package com.muyao.wanzaihyperos.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.min

internal fun validSelectionBounds(bounds: Rect?): Rect? = bounds?.takeIf {
    it.left.isFinite() && it.top.isFinite() && it.right.isFinite() && it.bottom.isFinite() &&
        it.width > 0f && it.height > 0f
}

/** Moves the selected carrier without scaling the labels inside it. */
@Composable
internal fun Modifier.slidingSelectionCarrier(selectedKey: Any?, bounds: Rect?, tint: Color): Modifier {
    val motion = rememberMotion()
    val move = motion.canMove && !motion.highContrast
    val target = validSelectionBounds(bounds)
    var resting by remember { mutableStateOf<Rect?>(null) }
    var restingKey by remember { mutableStateOf<Any?>(null) }
    var previous by remember { mutableStateOf<Rect?>(null) }
    val separation = remember { Animatable(1f) }
    LaunchedEffect(selectedKey, target, move) {
        val changed = restingKey != selectedKey
        previous = resting
        resting = target
        restingKey = selectedKey
        if (move && changed && previous != null && target != null) {
            separation.snapTo(0f)
            separation.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
        } else separation.snapTo(1f)
    }
    val spec = if (move) spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow) else snap()
    val x by animateFloatAsState(target?.left ?: 0f, spec, label = "selection-x")
    val y by animateFloatAsState(target?.top ?: 0f, spec, label = "selection-y")
    val width by animateFloatAsState(target?.width ?: 0f, spec, label = "selection-width")
    val height by animateFloatAsState(target?.height ?: 0f, spec, label = "selection-height")
    return drawBehind {
        if (target == null || width <= 0f || height <= 0f) return@drawBehind
        val radius = min(width, height) / 2f
        val center = Offset(x + width / 2f, y + height / 2f)
        val contour = Path().apply {
            addRoundRect(RoundRect(Rect(x, y, x + width, y + height), CornerRadius(radius)))
        }
        val old = previous
        val remaining = if (move) 1f - separation.value.coerceIn(0f, 1f) else 0f
        if (old != null && remaining > .02f && abs(old.center.x - center.x) < radius * 6f &&
            abs(old.center.y - center.y) < radius) {
            val bubbleRadius = radius * .7f * remaining
            val oldCenter = old.center
            val rightward = oldCenter.x < center.x
            val a = oldCenter.x + if (rightward) bubbleRadius * .5f else -bubbleRadius * .5f
            val b = if (rightward) x + radius * .6f else x + width - radius * .6f
            val half = bubbleRadius * .6f
            contour.addOval(Rect(oldCenter.x - bubbleRadius, oldCenter.y - bubbleRadius,
                oldCenter.x + bubbleRadius, oldCenter.y + bubbleRadius))
            contour.moveTo(a, oldCenter.y - half)
            contour.cubicTo((a + b) / 2f, oldCenter.y - half * .2f,
                (a + b) / 2f, center.y - half * .2f, b, center.y - half)
            contour.lineTo(b, center.y + half)
            contour.cubicTo((a + b) / 2f, center.y + half * .2f,
                (a + b) / 2f, oldCenter.y + half * .2f, a, oldCenter.y + half)
            contour.close()
        }
        drawPath(contour, tint)
    }
}
