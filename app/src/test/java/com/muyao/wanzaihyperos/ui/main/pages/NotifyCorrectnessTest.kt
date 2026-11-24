package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.portal.PortalNewsItem
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.ui.SourceScan
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyFreshnessLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyHeroLabel
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyCorrectnessTest {
    private fun source(name: String): String = SourceScan.sources().first { it.first == name }.second
    private fun page(): String = source("main/ui/main/pages/NotifySectionPage.kt")
    private fun redesign(): String = source("main/ui/main/pages/redesign/NotifyOverview.kt")

    @Test fun noticeSourcesAreOnlyCjh52AndOldPortalMessagesAreDetached() {
        val src = page()
        val chips = src.substringAfter("private fun NoticeSourceChips(").substringBefore("private fun FilterPill(")
        assertTrue(chips.contains("listOf(NOTICE_SOURCE_DAILY to \"日常通知\", NOTICE_SOURCE_LECTURE to \"讲座通知\")"))
        assertFalse(src.contains("graph.messages"))
        assertFalse(src.contains("NOTICE_SOURCE_PORTAL"))
        assertFalse(src.contains("ReadAllAction("))
        assertFalse(src.contains("confirmReadAll"))
        assertFalse(src.contains("localFullText"))
        assertTrue("公告保持可见", src.contains("notifyBroadcastItems("))
        assertTrue("新闻仓库保留", src.contains("graph.news"))
        assertTrue("待处理保留", src.contains("todoGroups.forEach"))
    }

    @Test fun refreshUsesSelectedCjh52SourceAndPreservesOtherPortalSections() {
        val src = page()
        assertTrue(src.contains("2, 0 -> if (noticeSource == NOTICE_SOURCE_DAILY) graph.notices.refresh() else graph.lectures.refresh()"))
        assertTrue(src.contains("graph.lectures.loadMore()"))
        assertTrue(src.contains("graph.news.loadMore()"))
        assertTrue(src.contains("graph.broadcasts.refresh()"))
        assertFalse(src.contains("graph.messages"))
    }

    @Test fun refreshFailureKeepsContentAndOriginalTimestamp() {
        val row = PortalNewsItem("1", "普通新闻", "2026-11-11")
        val initial = ListState.Content(listOf(row), ListMeta(updatedAtMillis = 0L, hasMore = true))
        val failure = initial.startRefresh().failWith(HzcuException.Transport()) as ListState.Content
        assertSame(row, failure.items.single())
        assertEquals(initial.meta.updatedAtMillis, failure.meta.updatedAtMillis)
        assertTrue(failure.meta.stale)
        assertEquals("更新未成功 · 暂用已有内容 · 01-01 00:00", notifyFreshnessLabel(failure.meta, ZoneId.of("UTC")))
        assertTrue(notifyFreshnessLabel(ListMeta(stale = true)).contains("本地内容"))
        assertTrue(notifyFreshnessLabel(ListMeta()).contains("时间未知"))
        assertTrue(notifyFreshnessLabel(initial.startRefresh().let { (it as ListState.Content).meta }).contains("正在更新"))
    }

    @Test fun knownEmptyRefreshFailureRemainsEmptyAndLoginExpiryIsNotEmpty() {
        val empty = ListState.Empty(ListMeta(updatedAtMillis = 0L, hasMore = false))
        val failedEmpty = empty.startRefresh().failWith(HzcuException.Transport()) as ListState.Empty
        assertEquals(empty.meta.updatedAtMillis, failedEmpty.meta.updatedAtMillis)
        assertTrue(failedEmpty.meta.stale)
        assertTrue(notifyFreshnessLabel(failedEmpty.meta, ZoneId.of("UTC")).contains("更新未成功"))
        assertSame(ListState.SessionExpired, ListState.Content(listOf(PortalNewsItem("1", "x", ""))).failWith(HzcuException.LoginExpired("替身会话失效")))
    }

    @Test fun notificationHeroOmitsUnknownValues() {
        assertEquals("总未读 304（服务端） · 09:40", notifyHeroLabel(304, false, "09:40"))
        assertTrue(notifyHeroLabel(304, true, "09:40").contains("上次结果"))
        assertEquals("", notifyHeroLabel(null, false, ""))
        assertEquals("09:40", notifyHeroLabel(null, false, "09:40"))
    }

    @Test fun filtersAndImportantActionsRemainReachable() {
        val src = page()
        val filters = src.substringAfter("private fun NotifyFilters(").substringBefore("private const val NOTICE_SOURCE_DAILY")
        val selector = source("main/ui/main/pages/ScheduleNoticeControls.kt")
        for (label in listOf("通知", "公告", "待处理", "新闻")) assertTrue(filters.contains("\"$label\""))
        assertTrue(filters.contains("ScheduleNoticeSelector("))
        assertTrue(selector.contains("GlasmioSegmentedControl("))
        assertTrue(selector.contains("selectOnIndicatorTap = selectOnIndicatorTap"))
        assertTrue(selector.contains("indication = null"))
        assertTrue("full-row actions remain accessible buttons", selector.contains("role = Role.Button"))
        assertTrue("row action cues are not nested focusable controls", selector.contains("clearAndSetSemantics"))
        assertTrue(src.contains("onClick = onOpenTodos"))
        assertTrue(src.contains("openRollcalls.forEach"))
        assertTrue(src.contains("graph.unread.refresh(force = true)"))
        assertTrue(src.contains("notifyBroadcastItems("))
        assertTrue(src.contains("graph.news"))
        assertFalse(src.contains("graph.messages"))
    }
}
