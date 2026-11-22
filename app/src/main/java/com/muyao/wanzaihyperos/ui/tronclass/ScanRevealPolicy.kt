package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.muyao.wanzaihyperos.ui.main.pages.motionSourceVisible
import com.muyao.wanzaihyperos.ui.main.pages.motionWindowMatches

internal data class ScanWindowStamp(
    val size: IntSize,
    val orientation: Int,
    val density: Float,
    val fontScale: Float,
    val imeBottom: Int,
)

internal fun scanSourceReusable(
    captured: Rect?,
    current: Rect?,
    viewport: Rect?,
    capturedWindow: ScanWindowStamp,
    currentWindow: ScanWindowStamp,
): Boolean = captured != null && current == captured && viewport != null &&
    motionSourceVisible(current, viewport) &&
    motionWindowMatches(capturedWindow.size, currentWindow.size, capturedWindow.orientation, currentWindow.orientation) &&
    capturedWindow.density.isFinite() && capturedWindow.density > 0f &&
    capturedWindow.fontScale.isFinite() && capturedWindow.fontScale > 0f &&
    capturedWindow.density == currentWindow.density && capturedWindow.fontScale == currentWindow.fontScale &&
    capturedWindow.imeBottom == currentWindow.imeBottom

internal fun scanRevealDurationMillis(canMove: Boolean, sourceReusable: Boolean): Int =
    if (canMove && sourceReusable) 240 else 120
