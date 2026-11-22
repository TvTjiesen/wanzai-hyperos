package com.muyao.wanzaihyperos.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import com.muyao.wanzaihyperos.ui.main.pages.motionSourceVisible
import kotlin.math.abs

internal data class MotionSourceStamp(
    val window: Rect,
    val viewport: Rect,
    val orientation: Int,
    val density: Float,
    val fontScale: Float,
)

internal fun motionSourceStampMatches(before: MotionSourceStamp, after: MotionSourceStamp): Boolean {
    fun same(a: Rect, b: Rect) = motionSourceVisible(a, a) && motionSourceVisible(b, b) &&
        abs(a.left - b.left) <= 1f && abs(a.top - b.top) <= 1f &&
        abs(a.right - b.right) <= 1f && abs(a.bottom - b.bottom) <= 1f
    return before.orientation == after.orientation && before.density == after.density &&
        before.fontScale == after.fontScale && same(before.window, after.window) && same(before.viewport, after.viewport)
}

internal fun motionSourceRect(bounds: Rect, clipped: Rect, viewport: Rect): Rect? =
    bounds.takeIf {
        motionSourceVisible(it, viewport) && motionSourceVisible(clipped, clipped) &&
            abs(it.left - clipped.left) <= 1f && abs(it.top - clipped.top) <= 1f &&
            abs(it.right - clipped.right) <= 1f && abs(it.bottom - clipped.bottom) <= 1f
    }

internal fun motionSourceBounds(coordinates: LayoutCoordinates?, viewport: Rect): Rect? {
    if (coordinates?.isAttached != true) return null
    return runCatching {
        val bounds = Rect(coordinates.localToWindow(Offset.Zero),
            androidx.compose.ui.geometry.Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        motionSourceRect(bounds, coordinates.boundsInWindow(), viewport)
    }.getOrNull()
}

internal class MotionSourceLease private constructor(
    val bounds: Rect,
    private val stamp: MotionSourceStamp,
    private val source: () -> LayoutCoordinates?,
) {
    fun matches(now: MotionSourceStamp): Boolean = motionSourceStampMatches(stamp, now)

    fun current(now: MotionSourceStamp): Rect? =
        if (matches(now)) motionSourceBounds(source(), now.viewport) else null

    companion object {
        fun capture(source: () -> LayoutCoordinates?, stamp: MotionSourceStamp): MotionSourceLease? =
            motionSourceBounds(source(), stamp.viewport)?.let { MotionSourceLease(it, stamp, source) }
    }
}

@Composable
internal fun rememberMotionSourceStamp(): () -> MotionSourceStamp {
    val view = LocalView.current
    val size = LocalWindowInfo.current.containerSize
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return {
        val screen = IntArray(2)
        val window = IntArray(2)
        val visible = android.graphics.Rect()
        view.getLocationOnScreen(screen)
        view.getLocationInWindow(window)
        view.getWindowVisibleDisplayFrame(visible)
        val origin = Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
        val frame = Rect(origin, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
        val viewport = Rect(visible.left - origin.x, visible.top - origin.y, visible.right - origin.x, visible.bottom - origin.y)
        MotionSourceStamp(frame, viewport, configuration.orientation, density.density, density.fontScale)
    }
}
