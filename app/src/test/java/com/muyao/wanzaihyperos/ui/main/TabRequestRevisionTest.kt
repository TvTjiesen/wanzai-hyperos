package com.muyao.wanzaihyperos.ui.main

import com.muyao.wanzaihyperos.ui.AppUiState
import org.junit.Assert.*
import org.junit.Test

class TabRequestRevisionTest {
    @Test fun samePageRequestRejectsTheOldSwipeSettlement() {
        val state = AppUiState()
        val oldRevision = state.tabRequestRevision
        state.selectedTab = 0
        assertTrue(state.tabRequestRevision > oldRevision)
        assertFalse(state.publishSettledTab(1, oldRevision))
        assertEquals(0, state.selectedTab)
        assertTrue(state.publishSettledTab(0, state.tabRequestRevision))
    }

    @Test fun repeatedSameValueRequestsRemainDistinctAndLatestWins() {
        val state = AppUiState()
        state.selectedTab = 0
        val first = state.tabRequestRevision
        state.selectedTab = 0
        val second = state.tabRequestRevision
        assertEquals(first + 1L, second)
        assertFalse(state.publishSettledTab(1, first))
        assertTrue(state.publishSettledTab(0, second))
        assertEquals(second, state.tabRequestRevision)
    }

    @Test fun settlementIsNotAnotherNavigationRequest() {
        val state = AppUiState()
        state.selectedTab = 4
        val consumedRevision = state.tabRequestRevision
        assertTrue(state.publishSettledTab(4, consumedRevision))
        assertEquals(consumedRevision, state.tabRequestRevision)
        state.selectedTab = 1
        assertFalse(state.publishSettledTab(4, consumedRevision))
        assertEquals(1, state.selectedTab)
    }
}
