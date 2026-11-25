package com.muyao.wanzaihyperos.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.RefreshToastController
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.ui.design.ai.AiBallNoticeState
import com.muyao.wanzaihyperos.ui.design.ai.AiBallNoticeTone
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RefreshStatusCapsule(meta: ListMeta, settledLabel: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val motion = rememberMotion()
    val tint by animateColorAsState(
        if (meta.lastRefreshError != null) colors.error else if (meta.refreshing) colors.primary else colors.onSurfaceVariantSummary,
        animationSpec = if (motion.canMove) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else snap(), label = "refresh-tint",
    )
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite }
        .background(tint.copy(alpha = if (motion.highContrast) .16f else .08f), RoundedCornerShape(24.dp))
        .animateContentSize(if (motion.canMove) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else snap())
        .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (meta.refreshing) 8.dp else 5.dp).background(tint, RoundedCornerShape(50)))
        Text(if (meta.refreshing) "正在更新 · 保留已有内容" else settledLabel,
            color = tint, fontSize = Type.caption, modifier = Modifier.weight(1f, fill = false))
    }
}

internal data class AppFeedback(val text: String, val warning: Boolean = false)
internal val LocalAppFeedback = staticCompositionLocalOf<AppFeedback?> { null }
internal val LocalFeedbackInBall = staticCompositionLocalOf { false }

/** One timer at the window root, not one new timer every time a header is recomposed. */
@Composable
internal fun rememberAppFeedback(inFlight: StateFlow<Int>, notices: AiBallNoticeState): AppFeedback? {
    val controller = remember(inFlight) { RefreshToastController() }
    LaunchedEffect(controller) { controller.observe(inFlight) }
    val refreshing by controller.message.collectAsState()
    val notice = notices.current
    return if (notice != null) AppFeedback(notice.text, notice.tone == AiBallNoticeTone.Warning)
        else refreshing?.let { AppFeedback(it) }
}

/** The caller supplies the original ball. No second character or pointer target is created. */
@Composable
fun AiBallFeedbackRow(
    modifier: Modifier = Modifier,
    onRowSizeChanged: ((IntSize) -> Unit)? = null,
    maxWidth: Dp = Dp.Unspecified,
    stackToast: Boolean = false,
    ball: @Composable () -> Unit,
) {
    val feedback = LocalAppFeedback.current.takeIf { LocalPageActive.current }
    val colors = MiuixTheme.colorScheme
    val motion = rememberMotion()
    val fill by animateColorAsState(
        if (feedback != null) colors.surface else colors.surface.copy(alpha = 0f),
        animationSpec = if (motion.canMove) spring(dampingRatio = Motion.FEEDBACK_DAMPING, stiffness = Motion.FEEDBACK_STIFFNESS) else snap(),
        label = "ball-feedback-fill",
    )
    val fontScale = LocalDensity.current.fontScale
    Layout(
        modifier = modifier.onSizeChanged { onRowSizeChanged?.invoke(it) }.drawBehind {
            drawRoundRect(fill, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
        },
        content = {
            Box { ball() }
            Box {
                CompositionLocalProvider(LocalFeedbackInBall provides true) {
                    InlineRefreshToast(vertical = stackToast)
                }
            }
        },
    ) { measurables, constraints ->
        val widthLimit = if (maxWidth != Dp.Unspecified) maxWidth.roundToPx().coerceAtMost(constraints.maxWidth)
            else if (constraints.hasBoundedWidth) (constraints.maxWidth * .42f).toInt() else 240.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val ballPlaceable = measurables[0].measure(loose)
        val available = (widthLimit - ballPlaceable.width).coerceAtLeast(0)
        val stacked = stackToast || available < (88.dp * fontScale.coerceAtMost(1.5f)).roundToPx()
        val toast = measurables[1].measure(loose.copy(maxWidth = if (stacked) widthLimit else available))
        val width = if (stacked) maxOf(ballPlaceable.width, toast.width) else ballPlaceable.width + toast.width
        val height = if (stacked) ballPlaceable.height + toast.height else maxOf(ballPlaceable.height, toast.height)
        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            ballPlaceable.placeRelative(0, if (stacked) 0 else (height - ballPlaceable.height) / 2)
            toast.placeRelative(if (stacked) 0 else ballPlaceable.width, if (stacked) ballPlaceable.height else (height - toast.height) / 2)
        }
    }
}

/** Takes real layout width: siblings move away and return along the same interruptible spring. */
@Composable
fun InlineRefreshToast(modifier: Modifier = Modifier, vertical: Boolean = false) {
    val feedback = LocalAppFeedback.current.takeIf { LocalPageActive.current }
    var retained by remember { mutableStateOf<AppFeedback?>(null) }
    SideEffect { if (feedback != null) retained = feedback }
    val motion = rememberMotion()
    val colors = MiuixTheme.colorScheme
    val inBall = LocalFeedbackInBall.current
    val spec = spring<IntSize>(dampingRatio = Motion.FEEDBACK_DAMPING, stiffness = Motion.FEEDBACK_STIFFNESS)
    AnimatedVisibility(
        visible = feedback != null,
        modifier = modifier,
        enter = if (!motion.canMove) EnterTransition.None else fadeIn() +
            if (vertical) expandVertically(spec, expandFrom = Alignment.Top) else expandHorizontally(spec, expandFrom = Alignment.Start),
        exit = if (!motion.canMove) ExitTransition.None else fadeOut() +
            if (vertical) shrinkVertically(spec, shrinkTowards = Alignment.Top) else shrinkHorizontally(spec, shrinkTowards = Alignment.Start),
    ) {
        val shown = feedback ?: retained
        Text(
            text = shown?.text.orEmpty(),
            color = if (shown?.warning == true) colors.error else colors.onSurface,
            fontSize = Type.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 148.dp)
                .then(if (inBall) Modifier else Modifier.background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.dividerLine, RoundedCornerShape(20.dp)))
                .padding(start = if (inBall) 2.dp else 10.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
