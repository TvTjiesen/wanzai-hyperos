package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.main.pages.motionRevealRect
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberScanConfirmationProgress(
    identity: Any?,
    closing: Boolean,
    sourceReusable: Boolean,
    onClosed: () -> Unit,
): State<Float> {
    val expansion = remember(identity) { Animatable(0f) }
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val move = motion.canMove && !motion.highContrast
    val latestClosed by rememberUpdatedState(onClosed)
    LaunchedEffect(identity, closing, move, sourceReusable) {
        if (identity == null) return@LaunchedEffect
        expansion.animateTo(if (closing) 0f else 1f, tween(scanRevealDurationMillis(move, sourceReusable)))
        if (closing) latestClosed()
    }
    return expansion.asState()
}

/** Only the carrier/clip changes geometry; confirmation text and write callbacks do not. */
@Composable
internal fun scanConfirmationReveal(
    identity: Any,
    source: Rect?,
    progress: Float,
    sourceIsValid: () -> Boolean,
): Modifier {
    var destination by remember(identity) { mutableStateOf<Rect?>(null) }
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val move = motion.canMove && !motion.highContrast
    val surface = MiuixTheme.colorScheme.surface
    val radius = with(LocalDensity.current) { 20.dp.toPx() }
    val path = remember(identity) { Path() }
    val paint = remember(identity) { Paint() }
    return Modifier.onGloballyPositioned { destination = it.boundsInWindow() }.drawWithContent {
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) { drawContent(); return@drawWithContent }
        val target = destination
        val localTarget = Rect(0f, 0f, size.width, size.height)
        val frame = if (move && sourceIsValid() && source != null && target != null)
            motionRevealRect(source.translate(-target.topLeft), localTarget, p) else null
        if (frame != null) drawRoundRect(surface, frame.topLeft, frame.size, CornerRadius(radius))
        paint.alpha = if (frame != null) ((p - .25f) / .75f).coerceIn(0f, 1f) else p
        fun drawFadedContent() {
            drawContext.canvas.saveLayer(localTarget, paint)
            try { this@drawWithContent.drawContent() } finally { drawContext.canvas.restore() }
        }
        if (frame == null) drawFadedContent() else {
            path.rewind()
            path.addRoundRect(RoundRect(frame, CornerRadius(radius)))
            clipPath(path) { drawFadedContent() }
        }
    }
}
