package com.muyao.wanzaihyperos.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutOrderTest {
    @Test fun `six stable blocks include independently movable quick actions`() {
        assertEquals(listOf("hero", "quick-actions", "todos", "rollcall", "szty", "timeline"), HomeLayoutOrder.defaults)
        for (from in HomeLayoutOrder.defaults) {
            for (to in HomeLayoutOrder.defaults) {
                val moved = HomeLayoutOrder.move(HomeLayoutOrder.defaults, from, to)
                assertEquals(6, moved.size)
                assertEquals(HomeLayoutOrder.defaults.toSet(), moved.toSet())
                assertEquals(HomeLayoutOrder.defaults.indexOf(to), moved.indexOf(from))
            }
        }
    }

    @Test fun `legacy missing duplicate and obsolete blocks normalize without losing user order`() {
        assertEquals(HomeLayoutOrder.defaults, HomeLayoutOrder.normalize(emptyList()))
        val expected = listOf("timeline", "quick-actions", "hero", "todos", "rollcall", "szty")
        assertEquals(expected, HomeLayoutOrder.normalize(listOf("timeline", "obsolete", "quick-actions", "timeline")))
        assertEquals(expected, HomeLayoutOrder.normalize(expected))
    }

    @Test fun `unknown move endpoints cannot remove a block`() {
        assertEquals(HomeLayoutOrder.defaults, HomeLayoutOrder.move(HomeLayoutOrder.defaults, "unknown", "hero"))
        assertEquals(HomeLayoutOrder.defaults, HomeLayoutOrder.move(HomeLayoutOrder.defaults, "hero", "unknown"))
    }

    @Test fun `partial appearance layout retains hidden position and appends missing sections`() {
        val settings = AppearanceSettings(homeOrder = listOf("timeline", "hero"), hiddenHomeSections = setOf("hero")).validated()
        assertEquals(listOf("timeline", "hero", "quick-actions", "todos", "rollcall", "szty"), settings.homeOrder)
        assertEquals(setOf("hero"), settings.hiddenHomeSections)
    }
}
