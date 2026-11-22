package com.muyao.wanzaihyperos.ui.home

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class WallpaperGeometry(val scale: Float, val slackX: Float, val slackY: Float)

/** Project the viewport into image coordinates so every rotated corner remains covered. */
fun wallpaperGeometry(imgW: Int, imgH: Int, boxW: Float, boxH: Float, zoom: Float, rotation: Float): WallpaperGeometry {
    if (imgW <= 0 || imgH <= 0 || boxW <= 0f || boxH <= 0f || !boxW.isFinite() || !boxH.isFinite() || !zoom.isFinite() || !rotation.isFinite()) {
        return WallpaperGeometry(1f, 0f, 0f)
    }
    val radians = Math.toRadians(rotation.toDouble())
    val c = abs(cos(radians)).toFloat()
    val s = abs(sin(radians)).toFloat()
    val projectedW = boxW * c + boxH * s
    val projectedH = boxW * s + boxH * c
    val scale = maxOf(projectedW / imgW, projectedH / imgH) * 1.06f * zoom.coerceAtLeast(1f)
    return WallpaperGeometry(scale, ((imgW * scale - projectedW) / 2f).coerceAtLeast(0f),
        ((imgH * scale - projectedH) / 2f).coerceAtLeast(0f))
}

internal fun normalizeWallpaperRotation(degrees: Float): Int =
    if (!degrees.isFinite()) 0 else ((kotlin.math.round(degrees % 360f).toInt() % 360) + 360) % 360
