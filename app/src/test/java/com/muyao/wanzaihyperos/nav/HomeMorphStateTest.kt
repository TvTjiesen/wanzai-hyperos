package com.muyao.wanzaihyperos.nav

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMorphStateTest {
    @Test fun `only a valid prepared source is consumed for one route`() {
        val state = HomeMorphState()
        val origin = HomeRouteOrigin(
            bounds = Rect(10f, 20f, 110f, 80f),
            viewport = Rect(0f, 0f, 400f, 800f),
            isValid = { true },
        )
        val capture: () -> HomeRouteOrigin? = { origin }
        state.register("todos", capture)

        state.prepare("todos")
        state.open("Route.TodoBoard")
        assertSame(origin, state.origin)
        assertEquals("Route.TodoBoard", state.target)

        state.open("Route.TodoBoard")
        assertNull("a second open must not reuse the previous click geometry", state.origin)
        assertNull(state.target)
    }

    @Test fun `invalid or missing source falls back without arming transition`() {
        val state = HomeMorphState()
        val invalid = HomeRouteOrigin(
            bounds = Rect(10f, 20f, 110f, 80f),
            viewport = Rect(0f, 0f, 400f, 800f),
            isValid = { false },
        )
        state.register("rollcall", { invalid })

        state.prepare("rollcall")
        state.open("Route.Rollcall")
        assertNull(state.origin)
        assertNull(state.target)

        state.prepare("missing-source")
        state.open("Route.Szty")
        assertNull(state.origin)
        assertNull(state.target)
    }

    @Test fun `settled releases captured source and route`() {
        val state = HomeMorphState()
        val capture: () -> HomeRouteOrigin? = {
            HomeRouteOrigin(Rect(10f, 20f, 110f, 80f), Rect(0f, 0f, 400f, 800f)) { true }
        }
        state.register("todos", capture)
        state.prepare("todos")
        state.open("Route.TodoBoard")
        state.settled()

        assertNull(state.origin)
        assertNull(state.target)
        assertTrue(state.origin == null)
    }
}
