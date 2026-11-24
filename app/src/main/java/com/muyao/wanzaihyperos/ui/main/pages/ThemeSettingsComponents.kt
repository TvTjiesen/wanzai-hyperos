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
        // 展开到底时标题卡的下沿必须收成直角：段落的顶角也是 0，两边圆角对着彼此会在
        // 卡片左右两侧留下两个 6dp 的凹口（看着像卡面被啃掉一块）。
        val headerBottom = (24f - 24f * progress).dp
        AppearanceSectionHeader(title, summary, icon, expanded, enabled, toggle,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp,
                bottomStart = headerBottom, bottomEnd = headerBottom))
        if (expanded || transition.currentState || transition.isRunning) {
            val accessibility = if (expanded) Modifier else Modifier.semantics { hideFromAccessibility() }
            // 完全展开时缝隙归零，否则同色的卡面之间会露出一条 2dp 的页面底色细线。
            val gap = (2f * (1f - progress)).dp
            CompositionLocalProvider(LocalThemeSectionTransition provides transition) {
                Column(Modifier.fillMaxWidth().then(accessibility).padding(top = gap),
                    verticalArrangement = Arrangement.spacedBy(gap), content = content)
            }
        }
    }
}

@Composable
internal fun Modifier.themeSegment(last: Boolean): Modifier {
    val surface = MiuixTheme.colorScheme.surface
    val transition = LocalThemeSectionTransition.current
    // 卡面圆角只走统一 token：原来这里写死 24dp，与外观页其它卡面的 Dims.cardCorner(18dp)
    // 在同一屏里相差 6dp，相邻两个 item 的边一眼能看出错开；CornerTierTest 不扫本文件，抓不到。
    val corner = Dims.cardCorner
    if (transition == null) {
        // 不挂在 ThemeExpandableSection 里的段落（「概览」「首页布局」）以前直接 return this，
        // 内容就裸铺在页面底色上：既没有卡面，左右边缘也和下面的折叠卡对不齐。
        // 补 clip：另一分支有 clipToBounds()，这里若只画底色不裁，将来内容溢出会「底色圆角、
        // 内容直角」地穿帮。
        val shape = RoundedCornerShape(corner)
        return clip(shape).background(surface, shape)
    }
    val state = transition.segmentProgress("SegmentedProgress")
    // 同一张卡片的连续分段：顶角必须贴齐标题（0dp），中间各行也不能各自带圆角 ——
    // 否则每行左右各留一个圆角豁口，整块看起来像一排互相错开的色块。
    val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp,
        bottomStart = if (last) corner else 0.dp, bottomEnd = if (last) corner else 0.dp)
    return clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val height = (placeable.height * state.value.coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, constraints.constrainHeight(height)) { placeable.placeRelative(0, 0) }
    }.background(surface, shape)
}

@Composable
internal fun ThemeSettingSegment(last: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().themeSegment(last).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                // 与折叠卡标题同一处理：尾件是固定宽度的箭头，窄屏／大字号下掉到副标题
                // 下面会变成左对齐的孤零零一只箭头（业主报的「展开箭头偏左」）。
                trailingInline = true,
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
            title = "页面切换手势", subtitle = selected, onClick = { open = true }.takeIf { enabled },
            trailingInline = true)
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

internal fun themePreviewWidth(availableDp: Float): Float = minOf(availableDp * .68f, 300f)

@Composable
internal fun PhoneThemePreview(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    val aspect = themePreviewAspect(configuration.screenWidthDp, configuration.screenHeightDp)
    val shell = RoundedCornerShape(34.dp)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = themePreviewWidth(maxWidth.value).dp
        Box(modifier.width(width).height(width / aspect)
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
