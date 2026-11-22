package com.muyao.wanzaihyperos.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.muyao.wanzaihyperos.R
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlin.math.roundToInt

internal class SearchSourceLease {
    var attached by mutableStateOf(false)
    var bounds by mutableStateOf(Rect.Zero)
    var window by mutableStateOf(Rect.Zero)

    fun matches(capturedBounds: Rect, capturedWindow: Rect): Boolean =
        attached && capturedBounds.width > 0f && capturedBounds.height > 0f &&
            capturedWindow.width > 0f && capturedWindow.height > 0f &&
            capturedBounds == bounds && capturedWindow == window &&
            bounds.left >= window.left && bounds.top >= window.top &&
            bounds.right <= window.right && bounds.bottom <= window.bottom
}

internal data class SearchOrigin(
    val bounds: Rect,
    val backdrop: Backdrop,
    val isValid: () -> Boolean = { true },
    val returnAllowed: () -> Boolean = { true },
    val restoreFocus: () -> Unit,
) {
    fun canReturn(): Boolean = isValid() && returnAllowed()
}

internal class SearchMotionState {
    private var captured: SearchOrigin? = null
    var origin by mutableStateOf<SearchOrigin?>(null)
        private set
    var expanded by mutableStateOf(false)
        private set

    fun capture(origin: SearchOrigin) { captured = origin }
    fun openCaptured(returnAllowed: () -> Boolean = { true }): Boolean {
        val capturedSource = captured ?: return false
        captured = null
        val source = capturedSource.copy(returnAllowed = { capturedSource.returnAllowed() && returnAllowed() })
        if (!source.canReturn() || source.bounds.width <= 0f || source.bounds.height <= 0f) return false
        pendingFocus = null
        origin = source
        expanded = true
        return true
    }
    var pendingFocus by mutableStateOf<SearchOrigin?>(null)
        private set
    fun close() { expanded = false }
    fun finish(restoreFocus: Boolean = true) {
        val source = origin
        origin = null
        expanded = false
        pendingFocus = source?.takeIf { restoreFocus && it.canReturn() }
    }
    fun restorePendingFocus() {
        val source = pendingFocus
        pendingFocus = null
        if (origin == null && source?.canReturn() == true) source.restoreFocus()
    }
}

internal val LocalSearchMotion = staticCompositionLocalOf<SearchMotionState?> { null }

@Composable
internal fun SearchSourceContent(state: SearchMotionState, content: @Composable () -> Unit) {
    val covered = state.origin != null
    LaunchedEffect(state.pendingFocus) {
        if (state.pendingFocus != null) {
            withFrameNanos { }
            state.restorePendingFocus()
        }
    }
    Box(Modifier.fillMaxSize()
        .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)
        .focusProperties {
            canFocus = !covered
            onEnter = { if (covered) cancelFocusChange() }
        }.focusGroup()
        .onPreviewKeyEvent { covered }) {
        content()
    }
}

internal fun searchMotionBounds(source: Rect, destination: Rect, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * p
    return Rect(mix(source.left, destination.left), mix(source.top, destination.top),
        mix(source.right, destination.right), mix(source.bottom, destination.bottom))
}

/** The source and search keep their real layout; only the shared surface and clipping bounds move. */
@Composable
internal fun SearchMotionHost(state: SearchMotionState, content: @Composable (Boolean, () -> Unit) -> Unit) {
    val source = state.origin ?: return
    val motion = rememberMotion()
    val progress = remember(source) { Animatable(0f) }
    var windowBounds by remember { mutableStateOf(Rect.Zero) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    androidx.compose.runtime.DisposableEffect(source) {
        onDispose { keyboard?.hide() }
    }
    val fade = remember(source) { Animatable(1f) }
    val sourceValid = source.canReturn()
    var geometryInvalid by remember(source) { mutableStateOf(false) }
    val canReturn = sourceValid && !geometryInvalid
    LaunchedEffect(source) { focusManager.clearFocus(force = true) }
    val close: () -> Unit = { keyboard?.hide(); focusManager.clearFocus(force = true); state.close() }
    BackHandler { close() }
    LaunchedEffect(state.expanded, motion.canMove, canReturn) {
        val target = if (state.expanded) 1f else 0f
        if (!canReturn) {
            progress.snapTo(1f)
            if (!state.expanded) fade.animateTo(0f, tween(140))
        } else if (motion.canMove) progress.animateTo(target, tween(300, easing = FastOutSlowInEasing))
        else progress.snapTo(target)
        if (!state.expanded) state.finish(restoreFocus = canReturn)
    }
    val p = if (!canReturn) 1f else if (motion.canMove) progress.value else if (state.expanded) 1f else 0f
    val localSource = source.bounds.translate(-windowBounds.topLeft)
    val target = Rect(0f, 0f, windowBounds.width, windowBounds.height)
    val bounds = searchMotionBounds(localSource, target, p)
    val radius = (localSource.height / 2f) * (1f - p)
    val shape = GenericShape { _, _ -> addRoundRect(RoundRect(bounds, CornerRadius(radius))) }
    Box(Modifier.fillMaxSize().semantics { paneTitle = "搜索应用" }.onGloballyPositioned {
        val current = it.boundsInWindow()
        if (windowBounds.width > 0f && current.width != windowBounds.width) geometryInvalid = true
        windowBounds = current
    }) {
        // Modal hit target prevents empty search areas from clicking the retained source page.
        Box(Modifier.fillMaxSize().clearAndSetSemantics { }.focusProperties { canFocus = false }.clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { })
        if (windowBounds.width > 0f) {
            Box(Modifier.fillMaxSize().clip(shape).alpha(fade.value)) {
                content(p >= 1f && state.expanded, close)
            }
            if (p < 1f && motion.canMove && canReturn) {
                val density = LocalDensity.current
                GlassSurface(
                    backdrop = source.backdrop,
                    shape = Capsule(),
                    material = NavigationGlassMaterial.Clear,
                    modifier = Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                        .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                        .alpha((1f - p * 2f).coerceIn(0f, 1f)),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_board_search),
                        contentDescription = null,
                        tint = LocalGlassColors.current.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
