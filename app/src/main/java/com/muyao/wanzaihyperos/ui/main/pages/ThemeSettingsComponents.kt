package com.muyao.wanzaihyperos.ui.main.pages

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.neoruaa.glasmio.glass.glasmioMenuAnchor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LocalThemeSectionTransition = compositionLocalOf<Transition<Boolean>?> { null }

@Composable
private fun Transition<Boolean>.segmentProgress(label: String): State<Float> {
    val animate = rememberMotion().canMove
    return animateFloat(
        transitionSpec = { if (animate) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else snap() },
        label = label,
    ) { if (it) 1f else 0f }
}

@Composable
internal fun ThemeExpandableSection(
    title: String, summary: String, icon: SettingIcon, expanded: Boolean, enabled: Boolean,
    toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    val transition = updateTransition(expanded, label = "ThemeSection")
    val progress = transition.segmentProgress("SegmentedHeader").value.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        AppearanceSectionHeader(title, summary, icon, expanded, enabled, toggle,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp,
                bottomStart = (24f - 18f * progress).dp, bottomEnd = (24f - 18f * progress).dp))
        if (expanded || transition.currentState || transition.isRunning) {
            val accessibility = if (expanded) Modifier else Modifier.semantics { hideFromAccessibility() }
            val gap = (2f * progress).dp
            CompositionLocalProvider(LocalThemeSectionTransition provides transition) {
                Column(Modifier.fillMaxWidth().then(accessibility).padding(top = gap),
                    verticalArrangement = Arrangement.spacedBy(gap), content = content)
            }
        }
    }
}

@Composable
internal fun Modifier.themeSegment(last: Boolean): Modifier {
    val transition = LocalThemeSectionTransition.current ?: return this
    val state = transition.segmentProgress("SegmentedProgress")
    val bottomRadius = if (last) 24.dp else 6.dp
    return clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val height = (placeable.height * state.value.coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, constraints.constrainHeight(height)) { placeable.placeRelative(0, 0) }
    }.background(MiuixTheme.colorScheme.surface,
        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = bottomRadius, bottomEnd = bottomRadius))
}

@Composable
internal fun ThemeSettingSegment(last: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().themeSegment(last).padding(horizontal = 16.dp, vertical = 4.dp)) {
        content()
    }
}

@Composable
internal fun ThemeOptionMenu(
    title: String, selected: String, enabled: Boolean, options: List<String>, onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val motion = rememberMotion()
    io.github.neoruaa.glasmio.ui.GlasmioMenu(
        expanded = open && enabled,
        onExpandedChange = { open = it && enabled },
        itemCount = options.size,
        modifier = Modifier.fillMaxWidth(),
        morphFromAnchor = motion.canMove && !motion.highContrast,
        reduceMotion = !motion.canMove || motion.highContrast,
        anchor = { toggle ->
            SettingRow(
                icon = SettingIcon.Screen, tint = MiuixTheme.colorScheme.onSurface,
                title = title, subtitle = selected, onClick = toggle.takeIf { enabled },
            )
        },
    ) { childBackdrop, close ->
        options.forEachIndexed { index, label ->
            io.github.neoruaa.glasmio.ui.GlasmioMenuItem(
                title = label, systemName = if (label == selected) "checkmark" else null,
                backdrop = childBackdrop,
                onClick = { onSelect(index); close() },
            )
        }
    }
}

@Composable
internal fun ThemePagerMenu(
    selected: String, enabled: Boolean, options: List<String>, onSelect: (Int) -> Unit,
) {
    val motion = rememberMotion()
    if (!motion.canMove || motion.highContrast || options.size != 4) {
        ThemeOptionMenu("页面切换手势", selected, enabled, options, onSelect)
        return
    }
    var open by remember { mutableStateOf(false) }
    val source = io.github.neoruaa.glasmio.glass.rememberGlasmioMenuAnchorState()
    Box(Modifier.fillMaxWidth().glasmioMenuAnchor(source)) {
        SettingRow(icon = SettingIcon.Screen, tint = MiuixTheme.colorScheme.onSurface,
            title = "页面切换手势", subtitle = selected, onClick = { open = true }.takeIf { enabled })
    }
    if (open) {
        val choices = options.take(3).mapIndexed { index, label ->
            io.github.neoruaa.glasmio.ui.GlasmioCascadingMenuItem(title = label,
                systemName = if (label == selected) "checkmark" else null,
                onClick = { onSelect(index) })
        }
        io.github.neoruaa.glasmio.ui.GlasmioCascadingMenu(
            anchorBounds = source.bounds, menuAnchorState = source,
            title = "页面切换手势", expandedDescription = "选择横滑方式", collapsedDescription = selected,
            onDismiss = { open = false },
            items = listOf(
                io.github.neoruaa.glasmio.ui.GlasmioCascadingMenuItem(title = "开启横滑",
                    subtitle = selected.takeIf { it != options.last() }, children = choices),
                io.github.neoruaa.glasmio.ui.GlasmioCascadingMenuItem(title = options.last(),
                    systemName = if (selected == options.last()) "checkmark" else null,
                    onClick = { onSelect(3) }),
            ),
        )
    }
}

internal fun themePreviewAspect(widthDp: Int, heightDp: Int): Float =
    (widthDp.toFloat() / heightDp).takeIf { it.isFinite() && it > 0f }?.coerceIn(.42f, .62f) ?: .46f

internal fun themePreviewWidth(availableDp: Float): Float = minOf(availableDp * .52f, 248f)

@Composable
internal fun PhoneThemePreview(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    val aspect = themePreviewAspect(configuration.screenWidthDp, configuration.screenHeightDp)
    val shell = RoundedCornerShape(34.dp)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = themePreviewWidth(maxWidth.value).dp
        Box(Modifier.width(width).height(width / aspect)
            .shadow(22.dp, shell)
            .background(Brush.verticalGradient(listOf(Color(0xFF242428), Color(0xFF141417))), shell)
            .border(1.dp, Color.White.copy(alpha = .12f), shell)
            .padding(5.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(29.dp)), content = content)
        }
    }
}

@Composable
internal fun ThemeBackGesture(predictive: Boolean, onBack: () -> Unit, onProgress: (Float) -> Unit) {
    val latestBack by rememberUpdatedState(onBack)
    val latestProgress by rememberUpdatedState(onProgress)
    BackHandler(enabled = !predictive, onBack = onBack)
    PredictiveBackHandler(enabled = predictive) { events ->
        try {
            events.collect { latestProgress(it.progress) }
            latestProgress(0f)
            latestBack()
        } catch (cancel: CancellationException) {
            latestProgress(0f)
            throw cancel
        }
    }
}

@Composable
internal fun Modifier.themeSwipeDismiss(enabled: Boolean, onBack: () -> Unit): Modifier {
    val latestBack by rememberUpdatedState(onBack)
    val edge = with(LocalDensity.current) { 24.dp.toPx() }
    val distance = with(LocalDensity.current) { 96.dp.toPx() }
    return if (!enabled) this else pointerInput(edge, distance) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x > edge) return@awaitEachGesture
            var dismissed = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position - down.position
                if (delta.x > distance && kotlin.math.abs(delta.y) < edge * 2 && !dismissed) {
                    change.consume(); dismissed = true
                }
                if (!change.pressed) {
                    if (dismissed) latestBack()
                    break
                }
            }
        }
    }
}
