package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyReturnNavigationTest {
    private fun source(name: String) = SourceScan.sources().first { it.first == name }.second
    private fun page() = source("main/ui/main/pages/NotifySectionPage.kt")

    @Test fun newSessionStartsInAnnouncementsAndExplicitSourceSelectionSurvivesDetailNavigation() {
        val state = AppUiState()
        assertEquals(4, state.notifyFilter)
        val listBeforeDetail = state.notifyListState
        state.notifyFilter = 3
        state.selectedTab = 2
        assertEquals(3, state.notifyFilter)
        assertSame(listBeforeDetail, state.notifyListState)
        state.notifyFilter = 2
        assertEquals("Explicitly selected historical messages stay reachable", 2, state.notifyFilter)
    }

    @Test fun shellOwnsSourceAndScrollOutsideDisposableNotifyPage() {
        val shell = source("main/ui/main/WanzaiMainShell.kt")
        assertTrue(shell.contains("selectedFilter = uiState.notifyFilter"))
        assertTrue(shell.contains("onFilterChange = { uiState.notifyFilter = it }"))
        assertTrue(shell.contains("listState = uiState.notifyListState"))
        val page = page()
        assertTrue(page.contains("val filter = selectedFilter ?: localFilter"))
        assertTrue(page.contains("rememberPageChromePilot(listState)"))
        assertFalse(page.contains("mutableStateOf(2)"))
        val select = page.substringAfter("onSelect = {").substringBefore("            if (filter == 1)")
        assertTrue(select.contains("if (it != filter)"))
        assertTrue(select.contains("onFilterChange(it)"))
    }

    @Test fun notificationBallUsesHomePresentationWithoutExtraStatusCardOrCentralDialog() {
        val page = page()
        assertTrue(page.contains("presentation = com.muyao.wanzaihyperos.ui.design.ai.AiBallPresentation.Home"))
        assertTrue(page.contains("work = com.muyao.wanzaihyperos.ui.design.ai.AiBallWork("))
        assertFalse(page.contains("source-status"))
        assertFalse(page.contains("showBallStatus"))
        assertFalse(page.contains("onStatusRequested ="))
    }
}
