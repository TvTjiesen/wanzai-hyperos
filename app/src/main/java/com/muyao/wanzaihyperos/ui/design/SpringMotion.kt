package com.muyao.wanzaihyperos.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object SpringMotion {
    const val Damping = 0.78f
    const val Stiffness = 380f
    const val StaggerMillis = 24L
}

internal fun scrollMotionDelta(previous: List<Pair<Any, Int>>, current: List<Pair<Any, Int>>): Float {
    val shared = current.firstNotNullOfOrNull { now -> previous.firstOrNull { it.first == now.first }?.let { it.second - now.second } }
    return shared?.toFloat() ?: 0f
}

internal fun scrollMotionLag(current: Float, delta: Float, limit: Float): Float =
    (current + delta * .65f).coerceIn(-limit, limit)

internal data class ScrollSpring(val offset: Float = 0f, val velocity: Float = 0f)

internal fun scrollSpringStep(state: ScrollSpring, delta: Float, elapsed: Float, limit: Float): ScrollSpring {
    val dt = elapsed.coerceIn(.001f, .032f)
    val displaced = scrollMotionLag(state.offset, delta, limit)
    val velocity = state.velocity + (-180f * displaced - 20f * state.velocity) * dt
    val offset = (displaced + velocity * dt).coerceIn(-limit, limit)
    return if (kotlin.math.abs(offset) < .05f && kotlin.math.abs(velocity) < .5f && delta == 0f) ScrollSpring()
    else ScrollSpring(offset, velocity)
}

class SpringListEntrance internal constructor(val list: LazyListState) {
    internal var lag by mutableFloatStateOf(0f)
}

@Composable
fun rememberSpringListEntrance(list: LazyListState): SpringListEntrance {
    val entrance = remember(list) { SpringListEntrance(list) }
    val active = LocalPageActive.current
    val motion = rememberMotion()
    val limit = with(LocalDensity.current) { 32.dp.toPx() }
    LaunchedEffect(entrance, active, motion.canMove, limit) {
        entrance.lag = 0f
        if (!active || !motion.canMove) return@LaunchedEffect
        coroutineScope {
            var pendingDelta = 0f
            val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
            launch {
                var previous = emptyList<Pair<Any, Int>>()
                snapshotFlow { list.layoutInfo.visibleItemsInfo.map { it.key to it.offset } }
                    .collect { position ->
                        val delta = if (list.isScrollInProgress) scrollMotionDelta(previous, position) else 0f
                        if (delta != 0f) { pendingDelta += delta; wake.trySend(Unit) }
                        previous = position
                    }
            }
            var state = ScrollSpring()
            while (isActive) {
                wake.receive()
                var previousFrame = withFrameNanos { it }
                do {
                    val frame = withFrameNanos { it }
                    val delta = pendingDelta
                    pendingDelta = 0f
                    state = scrollSpringStep(state, delta, (frame - previousFrame) / 1_000_000_000f, limit)
                    previousFrame = frame
                    entrance.lag = state.offset
                } while (state != ScrollSpring() || pendingDelta != 0f || list.isScrollInProgress)
            }
        }
    }
    return entrance
}

/** Drawing trails the list with different depth per card; no appearance fade. */
@Composable
fun Modifier.springListEntrance(entrance: SpringListEntrance, key: Any): Modifier {
    val motion = rememberMotion()
    val active = LocalPageActive.current
    if (!motion.canMove || !active) return this
    return graphicsLayer {
        val info = entrance.list.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key }
        val height = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        val position = item?.let { ((it.offset + it.size * .5f - info.viewportStartOffset) / height).coerceIn(0f, 1f) } ?: .5f
        translationY = entrance.lag * (.15f + .85f * position)
    }
}

@Composable
fun Modifier.springPress(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = rememberMotion()
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && motion.canMove) 0.96f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, SpringMotion.Stiffness),
        label = "springPress",
    )
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
