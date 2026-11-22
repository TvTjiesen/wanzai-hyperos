package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize

internal fun motionWindowMatches(initial: IntSize, current: IntSize, initialOrientation: Int, currentOrientation: Int): Boolean =
    initial.width > 0 && initial.height > 0 && initial == current && initialOrientation == currentOrientation

internal fun motionSourceVisible(source: Rect?, viewport: Rect): Boolean {
    if (source == null) return false
    fun valid(rect: Rect) = listOf(rect.left, rect.top, rect.right, rect.bottom, rect.width, rect.height).all { it.isFinite() } && rect.width > 0f && rect.height > 0f
    return valid(source) && valid(viewport) && source.left >= viewport.left && source.top >= viewport.top && source.right <= viewport.right && source.bottom <= viewport.bottom
}

internal fun motionScreenWindowMatches(initial: Rect, current: Rect, initialOrientation: Int, currentOrientation: Int): Boolean =
    initialOrientation == currentOrientation && motionSourceVisible(initial, initial) && motionSourceVisible(current, current) &&
        kotlin.math.abs(initial.left - current.left) <= 1f && kotlin.math.abs(initial.top - current.top) <= 1f &&
        kotlin.math.abs(initial.width - current.width) <= 1f && kotlin.math.abs(initial.height - current.height) <= 1f

internal fun motionRevealRect(source: Rect, destination: Rect, progress: Float): Rect? {
    if (!progress.isFinite() || !motionSourceVisible(source, source) || !motionSourceVisible(destination, destination)) return null
    val p = progress.coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float) = a * (1f - p) + b * p
    return Rect(lerp(source.left, destination.left), lerp(source.top, destination.top), lerp(source.right, destination.right), lerp(source.bottom, destination.bottom))
}

/** Screen coordinates let a source on the settings window meet the full-screen editor window. */
@Composable
internal fun Modifier.wallpaperMotionBounds(onBounds: (Rect) -> Unit): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coordinates ->
        if (view.isAttachedToWindow && coordinates.isAttached) {
            val screen = IntArray(2)
            val window = IntArray(2)
            view.getLocationOnScreen(screen)
            view.getLocationInWindow(window)
            onBounds(coordinates.boundsInWindow().translate(Offset(
                (screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())))
        }
    }
}
