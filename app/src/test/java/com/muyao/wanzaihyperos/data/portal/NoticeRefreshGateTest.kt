package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import org.junit.Assert.*
import org.junit.Test

class NoticeRefreshGateTest {
    private val now = 1_000_000L

    @Test fun freshContentAndConfirmedEmptyDoNotRefresh() {
        val gate = NoticeRefreshGate()
        val meta = ListMeta(updatedAtMillis = now - 1000)
        assertFalse(gate.claim("messages", ListState.Content(listOf("kept"), meta), now))
        assertFalse(gate.claim("news", ListState.Empty(meta), now))
    }

    @Test fun outdatedContentRefreshesAtBoundary() {
        val state = ListState.Content(listOf("kept"), ListMeta(updatedAtMillis = now))
        val gate = NoticeRefreshGate()
        assertFalse(gate.claim("messages", state, now + NOTICE_REFRESH_INTERVAL_MS - 1))
        assertTrue(gate.claim("messages", state, now + NOTICE_REFRESH_INTERVAL_MS))
    }

    @Test fun failureWithoutTimestampDoesNotRetryOnEveryCheck() {
        val gate = NoticeRefreshGate()
        assertTrue(gate.claim("messages", ListState.FirstLoad, now))
        assertFalse(gate.claim("messages", ListState.FirstLoad, now + 60_000))
        assertTrue(gate.claim("messages", ListState.FirstLoad, now + NOTICE_REFRESH_INTERVAL_MS))
    }

    @Test fun alreadyRefreshingOrLoadingMoreIsNotDuplicated() {
        val gate = NoticeRefreshGate()
        assertFalse(gate.claim("messages", ListState.Empty(ListMeta(refreshing = true)), now))
        assertFalse(gate.claim("messages", ListState.Content(listOf("kept"), ListMeta(loadingMore = true)), now))
    }

    @Test fun groupsAndClockRollbackRemainIndependent() {
        val gate = NoticeRefreshGate()
        assertTrue(gate.claim("a", ListState.FirstLoad, now))
        assertTrue(gate.claim("b", ListState.FirstLoad, now))
        assertTrue(gate.claim("a", ListState.Empty(ListMeta(updatedAtMillis = now + 1)), now - 1))
    }
}
