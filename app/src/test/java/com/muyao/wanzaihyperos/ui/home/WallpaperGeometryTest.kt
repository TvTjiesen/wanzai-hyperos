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

    @Test fun smallDragStepsEscapeSnapWithoutLosingTheirAccumulatedDistance() {
        val axis = StickerDragAxis(.5f)
        repeat(8) { assertEquals(.5f, axis.move(.005f), .0001f) }
        assertTrue(axis.move(.01f) > .54f)
        assertTrue(axis.move(.01f) > .55f)
        repeat(5) { axis.move(-.01f) }
        assertEquals(.5f, axis.move(-.01f), .0001f)
        assertEquals(.5f, axis.move(Float.NaN), .0001f)
        assertEquals(1f, axis.move(10f), 0f)
        assertEquals(0f, axis.move(-10f), 0f)
    }

    @Test fun scaledRotatedStickersStayInsideContainerIncludingSmallPreviews() {
        for ((width, height) in listOf(360f to 160f, 280f to 200f, 40f to 30f)) {
            for (rotation in -180..180 step 9) for (scale in listOf(.4f, 1f, 2f)) {
                for (x in listOf(0f, .5f, 1f)) for (y in listOf(0f, .5f, 1f)) {
                    val frame = stickerGeometry(width, height, 64f, scale, rotation.toFloat(), x, y)
                    val radians = Math.toRadians(rotation.toDouble())
                    val projectedSide = frame.side * (abs(cos(radians)) + abs(sin(radians)))
                    val centerX = frame.left + frame.side / 2
                    val centerY = frame.top + frame.side / 2
                    assertTrue(centerX - projectedSide / 2 >= -.001)
                    assertTrue(centerY - projectedSide / 2 >= -.001)
                    assertTrue(centerX + projectedSide / 2 <= width + .001)
                    assertTrue(centerY + projectedSide / 2 <= height + .001)
                }
            }
        }
        assertEquals(.5f, snapStickerPosition(.51f), 0f)
        assertEquals(.3f, snapStickerPosition(.3f), 0f)
        assertEquals(0f, snapStickerPosition(-.1f), 0f)
    }
}
