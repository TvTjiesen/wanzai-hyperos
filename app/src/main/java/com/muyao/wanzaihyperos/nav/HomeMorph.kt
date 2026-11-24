package com.muyao.wanzaihyperos.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.muyao.wanzaihyperos.ui.design.MotionSourceLease
import com.muyao.wanzaihyperos.ui.design.MotionSourceStamp
import com.muyao.wanzaihyperos.ui.design.rememberMotionSourceStamp

/** A single-use click capture: other entrances to the same route never inherit home geometry. */
class HomeMorphState {
    private val sources = mutableMapOf<String, () -> HomeRouteOrigin?>()
    private var pending: HomeRouteOrigin? = null
    internal var origin by mutableStateOf<HomeRouteOrigin?>(null)
        private set
    internal var target: Any? = null
        private set

    internal fun register(key: String, capture: () -> HomeRouteOrigin?) { sources[key] = capture }
    internal fun unregister(key: String, capture: () -> HomeRouteOrigin?) { if (sources[key] === capture) sources.remove(key) }
    fun prepare(key: String) { pending = sources[key]?.invoke() }
    fun open(route: String) {
        val captured = pending?.takeIf { it.isValid() }
        pending = null
        origin = captured
        target = route.takeIf { captured != null }
    }

    internal fun matches(route: Any?): Boolean = when (target) {
        "Route.TodoBoard" -> route == Route.TodoBoard || route == Route.Main
        "Route.Rollcall" -> route is Route.Rollcall || route == Route.Main
        "Route.Szty" -> route == Route.Szty || route == Route.Main
        else -> false
    }

    internal fun settled() {
        origin = null
        target = null
    }
}

internal class HomeRouteOrigin(
    val bounds: Rect,
    val viewport: Rect,
    val isValid: () -> Boolean,
)

/** Reuse the existing geometry lease checks (window, orientation, density, clipping, attachment). */
@Composable
internal fun Modifier.homeMorphSource(key: String, state: HomeMorphState?): Modifier {
    if (state == null) return this
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val stamp = rememberMotionSourceStamp()
    val capture = remember(state, key, stamp) {
        {
            val now: MotionSourceStamp = stamp()
            MotionSourceLease.capture({ coordinates }, now)?.let { lease ->
                HomeRouteOrigin(lease.bounds, now.viewport) {
                    lease.current(stamp()) == lease.bounds
                }
            }
        }
    }
    DisposableEffect(state, key, capture) {
        state.register(key, capture)
        onDispose { state.unregister(key, capture) }
    }
    return onGloballyPositioned { coordinates = it }
}
