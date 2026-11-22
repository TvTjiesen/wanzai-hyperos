package com.muyao.wanzaihyperos.ui.home

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal data class StickerGeometry(val left: Float, val top: Float, val side: Float, val travelX: Float, val travelY: Float)

internal fun stickerGeometry(width: Float, height: Float, base: Float, scale: Float, rotation: Float, x: Float, y: Float): StickerGeometry {
    if (listOf(width, height, base, scale, rotation, x, y).any { !it.isFinite() } || width <= 0 || height <= 0 || base <= 0) {
        return StickerGeometry(0f, 0f, 0f, 0f, 0f)
    }
    val radians = Math.toRadians(rotation.toDouble())
    val projection = (abs(cos(radians)) + abs(sin(radians))).toFloat()
    val side = minOf(base * scale.coerceIn(.4f, 2f), width / projection, height / projection)
    val bounds = side * projection
    val travelX = (width - bounds).coerceAtLeast(0f)
    val travelY = (height - bounds).coerceAtLeast(0f)
    return StickerGeometry(travelX * x.coerceIn(0f, 1f) + (bounds - side) / 2f,
        travelY * y.coerceIn(0f, 1f) + (bounds - side) / 2f, side, travelX, travelY)
}

internal class StickerDragAxis(start: Float) {
    private var raw = start.coerceIn(0f, 1f)
    private var anchor: Float? = listOf(0f, .5f, 1f).firstOrNull { abs(it - raw) < .02f }

    fun move(delta: Float): Float {
        if (!delta.isFinite()) return anchor ?: raw
        raw = (raw + delta).coerceIn(0f, 1f)
        if (anchor?.let { abs(raw - it) > .045f } == true) anchor = null
        if (anchor == null) anchor = listOf(0f, .5f, 1f).firstOrNull { abs(it - raw) < .02f }
        return anchor ?: raw
    }
}

internal fun snapStickerPosition(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return listOf(0f, .5f, 1f).firstOrNull { abs(it - clamped) < .02f } ?: clamped
}
