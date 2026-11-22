package com.muyao.wanzaihyperos.ui.design

import org.junit.Assert.*
import org.junit.Test

class ScrollMotionTest {
    @Test fun `shared item keeps motion continuous when first item leaves`() {
        assertEquals(30f, scrollMotionDelta(listOf("a" to -90, "b" to 100), listOf("b" to 70, "c" to 200)), .001f)
    }

    @Test fun `reverse scroll reverses lag without resetting it`() {
        assertEquals(-25f, scrollMotionDelta(listOf("b" to 70), listOf("b" to 95)), .001f)
        assertTrue(scrollMotionLag(8f, -60f, 18f) < 0f)
    }

    @Test fun `new page or unrelated items cannot produce entrance movement`() {
        assertEquals(0f, scrollMotionDelta(emptyList(), listOf("a" to 100)), 0f)
        assertEquals(0f, scrollMotionDelta(listOf("a" to 100), listOf("z" to 0)), 0f)
        assertEquals(0f, scrollMotionLag(0f, 0f, 18f), 0f)
    }

    @Test fun `frame spring preserves momentum and settles after scrolling`() {
        var state = scrollSpringStep(ScrollSpring(), 40f, 1f / 60f, 32f)
        assertTrue(state.offset > 18f)
        val moving = state.offset
        state = scrollSpringStep(state, 0f, 1f / 60f, 32f)
        assertTrue(state.offset < moving)
        repeat(300) { state = scrollSpringStep(state, 0f, 1f / 60f, 32f) }
        assertEquals(0f, state.offset, .05f)
    }

    @Test fun `displacement is bounded in both directions`() {
        assertEquals(18f, scrollMotionLag(0f, 10000f, 18f), 0f)
        assertEquals(-18f, scrollMotionLag(0f, -10000f, 18f), 0f)
    }
}
