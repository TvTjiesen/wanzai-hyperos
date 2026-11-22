package com.muyao.wanzaihyperos.data.portal

import org.junit.Assert.*
import org.junit.Test

class MessageSelectionTest {
    @Test fun firstGroupIsSelectedAndRemovedGroupCannotStick() {
        val first = PortalMessageGroup("new", 280)
        assertEquals(first, reconcileMessageGroup(listOf(first), null))
        assertEquals(first, reconcileMessageGroup(listOf(first), PortalMessageGroup("removed", 1)))
        assertNull(reconcileMessageGroup(emptyList(), first))
    }
    @Test fun refreshedSelectedObjectCarriesCurrentUnreadCount() {
        val refreshed = PortalMessageGroup("same", 281)
        assertSame(refreshed, reconcileMessageGroup(listOf(PortalMessageGroup("other", 1), refreshed), PortalMessageGroup("same", 280)))
    }
    @Test fun activitySelectionPrefersPortalTextEvenWhenRowGroupIsMissing() {
        val row = PortalMessageItem("notice", "Synthetic", "Body", false, "", "", "")
        assertTrue(row.opensPortalTextFirst("活动通知"))
        assertTrue(row.copy(group = " 活动通知 ").opensPortalTextFirst(null))
        assertFalse(row.opensPortalTextFirst("讲座通知"))
        assertFalse(row.opensPortalTextFirst(null))
    }

    @Test fun loadedRowsSortNewestFirstWithoutInventingDateOrUnread() {
        fun row(id: String, date: String) = PortalMessageItem(id, "Synthetic", "", false, "test", date, "")
        val old = row("old", "2025-11-06 10:00:00")
        val recent = row("new", "2026-11-19 09:00:00")
        val sorted = newestMessageRows(listOf(old, recent))
        assertEquals(listOf(recent, old), sorted)
        assertEquals("2025-11-06 10:00:00", sorted.last().createdDate)
        assertFalse(sorted.last().isRead)
    }
}
