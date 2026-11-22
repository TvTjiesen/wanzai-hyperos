package com.muyao.wanzaihyperos.ui.home

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class WallpaperGeometryTest {
    @Test fun everyRotatedViewportCornerRemainsInsideImageAtPanExtremes() {
        for ((width, height) in listOf(360f to 780f, 1080f to 600f, 400f to 400f)) {
            for ((imgW, imgH) in listOf(1080 to 1440, 1800 to 800, 700 to 700)) {
                for (angle in 0..359 step 7) for (zoom in listOf(1f, 1.6f, 4f)) {
                    val result = wallpaperGeometry(imgW, imgH, width, height, zoom, angle.toFloat())
                    val radians = Math.toRadians(angle.toDouble())
                    for (panX in listOf(-1f, 0f, 1f)) for (panY in listOf(-1f, 0f, 1f)) {
                        for (x in listOf(-width / 2, width / 2)) for (y in listOf(-height / 2, height / 2)) {
                            val localX = x * cos(radians) + y * sin(radians) - panX * result.slackX
                            val localY = -x * sin(radians) + y * cos(radians) - panY * result.slackY
                            assertTrue("Uncovered X at $angle degrees", abs(localX) <= imgW * result.scale / 2 + .01)
                            assertTrue("Uncovered Y at $angle degrees", abs(localY) <= imgH * result.scale / 2 + .01)
                        }
                    }
                }
            }
        }
    }

    @Test fun zeroDegreesPreservesExistingCoverAndSlack() {
        val result = wallpaperGeometry(1000, 500, 300f, 400f, 2f, 0f)
        assertEquals(.8f * 1.06f * 2f, result.scale, .0001f)
        assertEquals((1000 * result.scale - 300) / 2, result.slackX, .001f)
        assertEquals((500 * result.scale - 400) / 2, result.slackY, .001f)
    }

    @Test fun rotationsWrapAndInvalidGeometryDoesNotProduceNonFinitePixels() {
        assertEquals(359, normalizeWallpaperRotation(-1f))
        assertEquals(1, normalizeWallpaperRotation(721f))
        assertEquals(0, normalizeWallpaperRotation(359.8f))
        assertEquals(0, normalizeWallpaperRotation(Float.NaN))
        assertEquals(WallpaperGeometry(1f, 0f, 0f), wallpaperGeometry(0, 100, 100f, 100f, 1f, 45f))
        assertEquals(WallpaperGeometry(1f, 0f, 0f), wallpaperGeometry(100, 100, Float.POSITIVE_INFINITY, 100f, 1f, 45f))
    }
}
