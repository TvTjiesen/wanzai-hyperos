package com.muyao.wanzaihyperos.ui.search

import androidx.compose.ui.geometry.Rect
import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMotionTest {
    private val source = Rect(300f, 24f, 348f, 72f)
    private val page = Rect(0f, 0f, 360f, 800f)
    private fun code(name: String) = SourceScan.sources().first { it.first == name }.second

    @Test fun expansionAndReturnHaveExactSourceAndDestinationBounds() {
        assertEquals(source, searchMotionBounds(source, page, 0f))
        assertEquals(page, searchMotionBounds(source, page, 1f))
        assertEquals(Rect(150f, 12f, 354f, 436f), searchMotionBounds(source, page, .5f))
        assertEquals(source, searchMotionBounds(source, page, -1f))
        assertEquals(page, searchMotionBounds(source, page, 2f))
    }

    @Test fun reverseUsesTheSameContinuousGeometry() {
        for (step in 0..10) {
            val p = step / 10f
            val forward = searchMotionBounds(source, page, p)
            val reverse = searchMotionBounds(page, source, 1f - p)
            assertEquals(forward.left, reverse.left, .001f)
            assertEquals(forward.top, reverse.top, .001f)
            assertEquals(forward.width, reverse.width, .001f)
            assertEquals(forward.height, reverse.height, .001f)
        }
    }

    @Test fun absentSourceCannotPretendToBeSharedAndClosingIsSafe() {
        val state = SearchMotionState()
        assertFalse(state.openCaptured())
        state.close()
        state.finish()
        assertFalse(state.expanded)
        assertEquals(null, state.origin)
    }

    @Test fun detachedMovedOrOffscreenSourceCannotBeReturnDestination() {
        val lease = SearchSourceLease()
        lease.bounds = source
        lease.window = page
        assertFalse(lease.matches(source, page))
        lease.attached = true
        assertTrue(lease.matches(source, page))
        lease.bounds = Rect(299f, 24f, 347f, 72f)
        assertFalse(lease.matches(source, page))
        lease.bounds = Rect(-10f, 24f, 38f, 72f)
        assertFalse(lease.matches(lease.bounds, page))
    }

    @Test fun resizedWindowRejectsTheOldDestinationEvenIfButtonPositionIsUnchanged() {
        val lease = SearchSourceLease()
        lease.attached = true
        lease.bounds = source
        lease.window = Rect(0f, 0f, 800f, 360f)
        assertFalse(lease.matches(source, page))
        lease.window = page
        assertTrue(lease.matches(source, page))
        lease.attached = false
        assertFalse(lease.matches(source, page))
    }

    @Test fun invalidSourceFadesWithoutShrinkingAndCannotRestoreFocusAfterAppOpen() {
        val host = code("main/ui/search/SearchMotion.kt")
        assertTrue(host.contains("progress.snapTo(1f)"))
        assertTrue(host.contains("fade.animateTo(0f, tween(140))"))
        assertTrue(host.contains("state.finish(restoreFocus = canReturn)"))
        assertTrue(host.contains("restoreFocus && it.canReturn()"))
        assertTrue(host.contains("LaunchedEffect(state.pendingFocus)"))
        assertTrue(host.contains("state.restorePendingFocus()"))
        val root = code("main/WanzaiRoot.kt")
        assertTrue(root.contains("SearchSourceContent(searchMotion)"))
        assertTrue(root.contains("searchMotion.finish(restoreFocus = false); openApp(selected)"))
    }

    @Test fun actualButtonCaptureReturnFocusAndReducedMotionAreWired() {
        val button = code("main/ui/design/liquid/GlasmioSearchButton.kt")
        assertTrue(button.contains("boundsInWindow()"))
        assertTrue(button.contains("SearchOrigin("))
        assertTrue(button.contains("bounds, backdrop,"))
        assertTrue(button.contains("lease.matches(bounds, window)"))
        assertTrue(button.contains("currentWindowRevision.value == revision"))
        assertTrue(button.contains("focus.requestFocus()"))
        val host = code("main/ui/search/SearchMotion.kt")
        assertTrue(host.contains("rememberMotion()"))
        assertTrue(host.contains("if (motion.canMove) progress.animateTo"))
        assertTrue(host.contains("else progress.snapTo(target)"))
        assertTrue(host.contains("BackHandler { close() }"))
        assertTrue(host.contains("source.restoreFocus()"))
        assertTrue(host.contains("source?.canReturn() == true"))
        assertTrue(host.contains("clearAndSetSemantics"))
        assertTrue(host.contains("onPreviewKeyEvent { covered }"))
        assertTrue(host.contains("cancelFocusChange()"))
        val root = code("main/WanzaiRoot.kt")
        assertTrue(root.contains("searchMotion.openCaptured { backStack.lastOrNull() == Route.Main && uiState.selectedTab == sourceTab }"))
        assertTrue(root.contains("searchMotion.finish(restoreFocus = false)"))
        assertTrue(root.contains("requestInputFocus = ready"))
    }
}
