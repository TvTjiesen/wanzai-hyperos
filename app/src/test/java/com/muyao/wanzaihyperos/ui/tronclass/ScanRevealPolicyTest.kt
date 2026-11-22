package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRevealPolicyTest {
    private val source = Rect(20f, 80f, 180f, 128f)
    private val viewport = Rect(0f, 48f, 400f, 760f)
    private val window = ScanWindowStamp(IntSize(400, 800), 1, 2f, 1f, 0)

    @Test fun attachedUnmovedVisibleSourceCanReturn() {
        assertTrue(scanSourceReusable(source, source, viewport, window, window))
    }

    @Test fun missingMovedClippedOrNonFiniteSourceFallsBack() {
        assertFalse(scanSourceReusable(null, source, viewport, window, window))
        assertFalse(scanSourceReusable(source, null, viewport, window, window))
        assertFalse(scanSourceReusable(source, source.translate(1f, 0f), viewport, window, window))
        assertFalse(scanSourceReusable(source, source, Rect(0f, 100f, 400f, 760f), window, window))
        assertFalse(scanSourceReusable(source, source, null, window, window))
        val invalid = Rect(Float.NaN, 80f, 180f, 128f)
        assertFalse(scanSourceReusable(invalid, invalid, viewport, window, window))
    }

    @Test fun windowRotationDensityFontAndImeChangesInvalidateSource() {
        listOf(
            window.copy(size = IntSize(400, 760)), window.copy(orientation = 2),
            window.copy(density = 2.1f), window.copy(fontScale = 1.3f), window.copy(imeBottom = 260),
        ).forEach { changed ->
            assertFalse(scanSourceReusable(source, source, viewport, window, changed))
        }
        val invalidDensity = window.copy(density = Float.NaN)
        assertFalse(scanSourceReusable(source, source, viewport, invalidDensity, invalidDensity))
        val invalidFont = window.copy(fontScale = 0f)
        assertFalse(scanSourceReusable(source, source, viewport, invalidFont, invalidFont))
    }

    @Test fun reducedMotionAndLostSourceUseShortFade() {
        assertEquals(240, scanRevealDurationMillis(canMove = true, sourceReusable = true))
        assertEquals(120, scanRevealDurationMillis(canMove = false, sourceReusable = true))
        assertEquals(120, scanRevealDurationMillis(canMove = true, sourceReusable = false))
        assertEquals(120, scanRevealDurationMillis(canMove = false, sourceReusable = false))
    }
}
