package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class MotionSourceGeometryTest {
    private val window = Rect(0f, 0f, 1080f, 2400f)
    private val source = Rect(24f, 100f, 600f, 220f)

    @Test fun `only a complete finite visible source can be reused`() {
        assertTrue(motionSourceVisible(source, window))
        assertFalse(motionSourceVisible(source.copy(left = -1f), window))
        assertFalse(motionSourceVisible(source.copy(bottom = 2401f), window))
        assertFalse(motionSourceVisible(source.copy(right = source.left), window))
        assertFalse(motionSourceVisible(source.copy(left = Float.NaN), window))
        assertFalse(motionSourceVisible(null, window))
    }

    @Test fun `resize and orientation changes invalidate window stamp`() {
        val original = IntSize(1080, 2400)
        assertTrue(motionWindowMatches(original, original, 1, 1))
        assertFalse(motionWindowMatches(original, IntSize(1080, 2200), 1, 1))
        assertFalse(motionWindowMatches(original, original, 1, 2))
        assertFalse(motionWindowMatches(IntSize.Zero, IntSize.Zero, 1, 1))
    }

    @Test fun `dialog screen origin changes invalidate even equal sized windows`() {
        assertTrue(motionScreenWindowMatches(window, window, 1, 1))
        assertFalse(motionScreenWindowMatches(window, Rect(0f, 30f, 1080f, 2430f), 1, 1))
        assertFalse(motionScreenWindowMatches(window, window, 1, 2))
        assertFalse(motionScreenWindowMatches(window, window.copy(right = 900f), 1, 1))
    }

    @Test fun `carrier interpolation has exact endpoints and bounded intermediate edges`() {
        assertEquals(source, motionRevealRect(source, window, 0f))
        assertEquals(window, motionRevealRect(source, window, 1f))
        for (step in 0..100) {
            val frame = requireNotNull(motionRevealRect(source, window, step / 100f))
            assertTrue(motionSourceVisible(frame, window))
            assertTrue(frame.left <= source.left && frame.top <= source.top)
            assertTrue(frame.right >= source.right && frame.bottom >= source.bottom)
        }
    }

    @Test fun `invalid progress and geometry decline instead of producing transforms`() {
        assertNull(motionRevealRect(source, window, Float.POSITIVE_INFINITY))
        assertNull(motionRevealRect(source, window.copy(bottom = Float.NaN), .5f))
        assertNull(motionRevealRect(source.copy(right = source.left), window, .5f))
        assertEquals(source, motionRevealRect(source, window, -1f))
        assertEquals(window, motionRevealRect(source, window, 2f))
    }
}
