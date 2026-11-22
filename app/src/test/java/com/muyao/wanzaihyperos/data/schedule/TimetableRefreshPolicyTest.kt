package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import org.junit.Assert.*
import org.junit.Test

class TimetableRefreshPolicyTest {
    private val now = 50_000_000L

    @Test fun freshCachedContentAndConfirmedEmptyAvoidNetwork() {
        val meta = ListMeta(updatedAtMillis = now - 1000, stale = true)
        assertFalse(shouldRefreshTimetable(ListState.Content(listOf("cached"), meta), now))
        assertFalse(shouldRefreshTimetable(ListState.Empty(meta), now))
    }

    @Test fun expiredCacheRefreshesAtTheBoundary() {
        val meta = ListMeta(updatedAtMillis = now - TIMETABLE_REFRESH_INTERVAL_MS)
        assertTrue(shouldRefreshTimetable(ListState.Content(listOf("cached"), meta), now))
        assertFalse(shouldRefreshTimetable(ListState.Content(listOf("cached"), meta), now - 1))
    }

    @Test fun missingTimeAndClockRollbackRequestAnUpdate() {
        assertTrue(shouldRefreshTimetable(ListState.FirstLoad, now))
        assertTrue(shouldRefreshTimetable(ListState.Empty(), now))
        assertTrue(shouldRefreshTimetable(ListState.Empty(ListMeta(updatedAtMillis = now + 1)), now))
    }

    @Test fun anExistingRefreshIsNotDuplicated() {
        assertFalse(shouldRefreshTimetable(ListState.Empty(ListMeta(refreshing = true)), now))
    }
}
