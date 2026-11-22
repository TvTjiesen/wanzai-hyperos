package com.muyao.wanzaihyperos.ui.design.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.muyao.wanzaihyperos.ui.main.pages.motionSourceVisible
import com.muyao.wanzaihyperos.ui.main.pages.motionWindowMatches
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

internal val LocalAiBallTipBounds = staticCompositionLocalOf<Rect?> { null }

internal object AiBallDrag {
    const val HOLD_MS = 450L
    const val TIP_MS = 1000L

    fun limit(x: Float, y: Float, radius: Float): Pair<Float, Float> {
        if (!x.isFinite() || !y.isFinite() || !radius.isFinite() || radius <= 0f) return 0f to 0f
        val distance = hypot(x, y)
        val ratio = if (distance > radius) radius / distance else 1f
        return x * ratio to y * ratio
    }
}

internal class AiBallTipPosition(
    private val margin: Int,
    private val gap: Int,
    private val ball: Rect? = null,
    private val gutter: Int = 0,
    private val onPlaced: (IntOffset) -> Unit = {},
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val source = ball?.let { IntRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) }
            ?: anchorBounds
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(0)
        val minX = margin.coerceAtMost(maxX)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(0)
        val minY = margin.coerceAtMost(maxY)
        val above = source.top - gap - popupContentSize.height + gutter
        val y = if (above >= minY) above else source.bottom + gap - gutter
        return IntOffset(
            (source.left + (source.width - popupContentSize.width) / 2).coerceIn(minX, maxX),
            y.coerceIn(minY, maxY),
        ).also(onPlaced)
    }
}

@Composable
internal fun AiBallStatusTooltip(text: String, alpha: Float) {
    val density = LocalDensity.current
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val window = LocalWindowInfo.current.containerSize
    val configuration = LocalConfiguration.current
    val initialWindow = remember { window }
    val initialOrientation = remember { configuration.orientation }
    val initialFontScale = remember { configuration.fontScale }
    var sourceInvalid by remember { mutableStateOf(false) }
    val viewport = Rect(0f, 0f, window.width.toFloat(), window.height.toFloat())
    val ball = LocalAiBallTipBounds.current?.takeIf { motionSourceVisible(it, viewport) }
    val usable = !sourceInvalid && ball != null && configuration.fontScale == initialFontScale &&
        motionWindowMatches(initialWindow, window, initialOrientation, configuration.orientation)
    LaunchedEffect(usable) { if (!usable) sourceInvalid = true }
    val morphing = motion.canMove && !motion.highContrast && usable
    val progress = if (morphing) alpha.coerceIn(0f, 1f) else 1f
    // This small local window includes the source cap; it never allocates a screen-sized layer.
    val gutter = if (morphing) with(density) { ceil(ball!!.height + 8.dp.toPx()).toInt() + 1 } else 0
    var origin by remember { mutableStateOf<IntOffset?>(null) }
    val provider = remember(density, ball, gutter) {
        with(density) { AiBallTipPosition(12.dp.roundToPx(), 8.dp.roundToPx(), ball, gutter) { origin = it } }
    }
    val colors = MiuixTheme.colorScheme
    val bodyColors = AiBall.bodyColors()
    val neckPath = remember { Path() }
    val gutterDp = with(density) { gutter.toDp() }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = true)) {
        Box(Modifier.widthIn(max = 240.dp).drawWithContent {
            val target = AiTipRect(0f, gutter.toFloat(), size.width, size.height - gutter)
            val localBall = origin?.let { at -> ball?.let {
                AiTipRect(it.left - at.x, it.top - at.y, it.right - at.x, it.bottom - at.y)
            } }?.takeIf { it.left >= 0f && it.top >= 0f && it.right <= size.width && it.bottom <= size.height }
            val body = if (morphing && localBall != null) aiTipBody(localBall, target, progress) ?: target else target
            val radius = min(24.dp.toPx(), min(body.width, body.height) / 2f)
            val opacity = alpha.coerceIn(0f, 1f)
            if (morphing && localBall != null) {
                aiTipNeck(localBall, body, progress, radius)?.let { neck ->
                    neckPath.rewind()
                    neckPath.moveTo(neck.sourceLeft.x, neck.sourceLeft.y)
                    neckPath.cubicTo(neck.sourceLeftControl.x, neck.sourceLeftControl.y,
                        neck.targetLeftControl.x, neck.targetLeftControl.y, neck.targetLeft.x, neck.targetLeft.y)
                    neckPath.lineTo(neck.targetRight.x, neck.targetRight.y)
                    neckPath.cubicTo(neck.targetRightControl.x, neck.targetRightControl.y,
                        neck.sourceRightControl.x, neck.sourceRightControl.y, neck.sourceRight.x, neck.sourceRight.y)
                    neckPath.close()
                    val above = body.centerY < localBall.centerY
                    drawPath(neckPath, Brush.linearGradient(
                        listOf(colors.surface, if (above) bodyColors.first() else bodyColors.last()),
                        Offset(body.centerX, neck.targetLeft.y), Offset(localBall.centerX, neck.sourceLeft.y),
                    ), alpha = opacity)
                }
            }
            drawRoundRect(colors.surface, Offset(body.left, body.top), Size(body.width, body.height),
                CornerRadius(radius), alpha = opacity)
            drawContent()
        }.padding(vertical = gutterDp)) {
            Box(Modifier.graphicsLayer {
                this.alpha = if (morphing) ((progress - .6f) / .4f).coerceIn(0f, 1f) else alpha.coerceIn(0f, 1f)
            }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(text = text, color = colors.onSurface, style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
