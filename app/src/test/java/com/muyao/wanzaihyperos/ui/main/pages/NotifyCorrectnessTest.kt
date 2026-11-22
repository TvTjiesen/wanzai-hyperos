package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.portal.PortalMessageItem
import com.muyao.wanzaihyperos.data.portal.PortalNewsItem
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.ui.SourceScan
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyFreshnessLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyGroupCountLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyHeroLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyMatchingMessages
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyMatchingNews
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifyMessageDateLabel
import com.muyao.wanzaihyperos.ui.main.pages.redesign.notifySearchRangeLabel
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
    private fun message(id: String, title: String = "普通通知", body: String = "正文", group: String = "活动通知") =
        PortalMessageItem(id, title, body, false, group, "2026-11-11 09:40:00", "2026-11-11 09:41:00")

    @Test fun loadedGroupUnreadAndHeroRemainIndependent() {
        val label = notifyGroupCountLabel(10, 189, true)
        assertEquals("已加载 10 条 · 还有更多 · 当前组未读 189（服务端）", label)
        val src = page()
        assertTrue(src.contains("notifyGroupCountLabel(msgCount, openGroup?.unread"))
        assertTrue(src.contains("val heroUnread = (unreadState.value as? CountValue.Known)?.value"))
        assertTrue(src.contains("notifyHeroLabel(heroUnread, unreadState.stale, heroClock)"))
        assertEquals("总未读 304（服务端） · 09:40", notifyHeroLabel(304, false, "09:40"))
        assertTrue(notifyHeroLabel(304, true, "09:40").contains("上次结果"))
        assertEquals("", notifyHeroLabel(null, false, ""))
        assertEquals("09:40", notifyHeroLabel(null, false, "09:40"))
        assertFalse(label.contains("304"))
        assertTrue(notifyGroupCountLabel(null, null, false).contains("当前组未读 —"))
        assertTrue(notifyGroupCountLabel(0, 0, false).contains("已加载 0 条"))
    }

    @Test fun refreshFailureKeepsContentAndOriginalTimestamp() {
        val row = message("1")
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

    @Test fun messageDateIsDerivedFromCreatedDateNotSuccessfulCheckOrReadTimestamp() {
        val old = message("old").copy(createdDate = "2025-11-06 10:00:00", lastUpdateDate = "2026-11-16 18:00:00")
        val invalid = message("invalid").copy(createdDate = "2026-99-99", lastUpdateDate = "2026-11-21")
        val missing = message("missing").copy(createdDate = "")
        assertEquals("已加载通知最新日期：2025-11-06 · 检查时间不是通知日期",
            notifyMessageDateLabel(listOf(old, invalid, missing), true))
        assertEquals("通知最新日期：2025-11-06 · 检查时间不是通知日期",
            notifyMessageDateLabel(listOf(old), false))
        assertEquals("已加载通知最新日期：未知 · 检查时间不是通知日期",
            notifyMessageDateLabel(listOf(invalid, missing), true))
        val recent = message("recent").copy(createdDate = "2026-11-21T09:40:00")
        assertTrue(notifyMessageDateLabel(listOf(recent, old), true).contains("2026-11-21"))
        assertTrue(page().contains("notifyMessageDateLabel(s.items, s.meta.hasMore)"))
        assertTrue(page().contains("loading = msgMeta.loadingMore || msgMeta.refreshing"))
    }

    @Test fun expiredSourcesHaveAccountRecoveryAndGroupFailuresHaveRetry() {
        val src = page()
        for (key in listOf("msg-expired", "news-expired", "msg-groups-expired", "news-groups-expired")) {
            assertTrue("missing $key", src.substringAfter("key = \"$key\"").substringBefore("}\n").contains("onOpenAccounts"))
        }
        assertTrue(src.contains("onOpenAccounts: () -> Unit = {}"))
        assertTrue(src.contains("重试消息分组"))
        assertTrue(src.contains("重试新闻分组"))
        assertTrue(src.contains("ListFreshness(s.meta)"))
        assertTrue(src.contains("meta.lastRefreshError"))
        assertTrue(src.contains("msg-groups-freshness"))
        assertTrue(src.contains("news-groups-freshness"))
        assertTrue(src.contains("msgSelected != null || msgState is ListState.SessionExpired"))
        assertTrue(src.contains("newsSelected != null || newsState is ListState.SessionExpired"))
    }

    @Test fun knownEmptyRefreshFailureRemainsEmptyAndLoginExpiryIsNotEmpty() {
        val empty = ListState.Empty(ListMeta(updatedAtMillis = 0L, hasMore = false))
        val failedEmpty = empty.startRefresh().failWith(HzcuException.Transport()) as ListState.Empty
        assertEquals(empty.meta.updatedAtMillis, failedEmpty.meta.updatedAtMillis)
        assertTrue(failedEmpty.meta.stale)
        assertTrue(notifyFreshnessLabel(failedEmpty.meta, ZoneId.of("UTC")).contains("更新未成功"))
        assertSame(ListState.SessionExpired, ListState.Content(listOf(message("1"))).failWith(HzcuException.LoginExpired("替身会话失效")))
    }

    @Test fun localFullTextRetainsLongPayloadAndHasNoWriteCapability() {
        val row = message("long", "原".repeat(100), "文".repeat(300))
        assertEquals(100, row.title.length)
        assertEquals(300, row.message.length)
        val sheet = redesign().substringAfter("fun NotifyLocalFullTextSheet(")
        for (field in listOf("message.title", "message.message", "message.createdDate", "message.lastUpdateDate", "message.group")) {
            assertTrue("missing $field", sheet.contains(field))
        }
        assertTrue(sheet.contains("verticalScroll"))
        assertFalse(sheet.contains("maxLines"))
        assertFalse(sheet.contains("onRead"))
        assertFalse(sheet.contains("AppGraph"))
        assertFalse(sheet.contains("graph."))
        val rowSource = page().substringAfter("private fun MessageRow(").substringBefore("private fun UnreadMark(")
        val localBranch = rowSource.substringAfter("if (openUrl == null) {").substringBefore("} else {")
        assertTrue(localBranch.contains("onOpenLocal(m)"))
        assertFalse(localBranch.contains("onRead"))
        assertTrue(rowSource.contains("onOpenUrl(openUrl)"))
        assertFalse("Opening an unverified detail must not mark it read", rowSource.contains("onRead(m.id)"))
    }

    @Test fun activityNoticeShowsPortalTextBeforeOpeningSchoolDetail() {
        val row = page().substringAfter("private fun MessageRow(").substringBefore("private fun UnreadMark(")
        assertTrue(page().contains("opensPortalTextFirst = message.opensPortalTextFirst(msgSelected?.name)"))
        assertTrue(row.contains("!opensPortalTextFirst"))
        assertTrue(row.substringAfter("if (openUrl == null)").substringBefore("} else {").contains("onOpenLocal(m)"))
        val local = page().substringAfter("localFullText?.let { message ->").substringBefore("selectedBroadcast?.let")
        assertTrue(local.contains("WebTarget.Kind.BLOCKED"))
        assertTrue(local.contains("onOpenDetail = detailUrl?.let"))
        assertTrue(redesign().contains("打开学校详情"))
        assertTrue(page().contains("var localFullText by remember(graph, broadcastOwner)"))
    }

    @Test fun zeroSearchMatchesStillExposeMoreAndLaterMatch() {
        val first = (1..10).map { message(it.toString()) }
        assertTrue(notifyMatchingMessages(first, "needle").isEmpty())
        assertEquals("在已加载 10 条消息中无匹配；还有后页，可继续加载后查找。",
            notifySearchRangeLabel("消息", 10, 0, true))
        val second = first + message("11", body = "Needle in body")
        assertEquals("11", notifyMatchingMessages(second, "needle").single().id)
        assertEquals("1", notifyMatchingMessages(listOf(message("1", group = "NEEDLE")), "needle").single().id)
        assertEquals(11, notifyMatchingMessages(second, " ").size)
        assertTrue(notifySearchRangeLabel("新闻", 11, 1, false).contains("已加载到末页"))
        val src = page()
        val messageRow = src.indexOf("opensPortalTextFirst = message.opensPortalTextFirst(msgSelected?.name)")
        assertTrue(messageRow >= 0)
        assertTrue(src.indexOf("if (msgMeta?.hasMore == true)") > messageRow)
        assertTrue(src.indexOf("if (newsMeta?.hasMore == true)") > src.indexOf("NewsRow(news,"))
        assertTrue(src.contains("graph.messages.loadMore()"))
        assertTrue(src.contains("graph.news.loadMore()"))
        assertFalse("Use the shared top search instead of a duplicate inline search", src.contains("msg-search-range"))
        assertFalse(src.contains("news-search-range"))
        assertEquals("n", notifyMatchingNews(listOf(PortalNewsItem("n", "NEEDLE", "2026-11-11")), "needle").single().id)
    }

    @Test fun filtersAndImportantActionsAreReachableAndBusinessIsPreserved() {
        val src = page()
        val filters = src.substringAfter("private fun NotifyFilters(").substringBefore("private fun MsgGroupChips(")
        val row = source("main/ui/design/SlidingChoiceRow.kt")
        assertTrue(filters.contains("SlidingChoiceRow("))
        assertTrue("分类底托负责横向滚动", row.contains("horizontalScroll"))
        for (label in listOf("通知", "公告", "待处理", "新闻")) assertTrue(filters.contains("\"$label\""))
        assertTrue("标签必须经 itemContent 传入 FilterPill", filters.contains("FilterPill(labels[index]"))
        assertFalse(filters.contains("全部"))
        assertFalse("The shared ball tooltip owns status; no central status dialog", src.contains("showBallStatus"))
        assertFalse(src.contains("onStatusRequested ="))
        assertTrue(src.contains("refreshCurrent()"))
        val pill = src.substringAfter("private fun FilterPill(").substringBefore("private fun PendingRow(")
        assertTrue(pill.contains("text = count"))
        assertTrue("选中/Tab 语义在共享底托上", row.contains("role = Role.Tab"))
        assertTrue("胶囊点击目标 ≥48dp 在共享底托上", row.contains("heightIn(min = 48.dp)"))
        val button = source("main/ui/components/WanzaiGlasmioControls.kt")
            .substringAfter("fun WanzaiButton(").substringBefore("\n@Composable")
        assertTrue(button.contains("heightIn(min = 48.dp)"))
        for (signature in listOf("private fun ReadAllAction(", "private fun MoreRow(", "private fun PendingRow(")) {
            val body = src.substringAfter(signature).substringBefore("\n@Composable")
            assertTrue("small action $signature", body.contains("heightIn(min = 48.dp)"))
        }
        assertTrue(src.contains("todoGroups.forEach { group ->"))
        assertTrue(src.contains("group.source.label"))
        assertTrue(src.contains("openRollcalls.forEach"))
        assertTrue(src.contains("onClick = onOpenTodos"))
        assertTrue(src.contains("ReadAllAction(onClick = { confirmReadAll = openGroup })"))
        assertTrue(src.contains("g?.let { onReadAll(it.name) }"))
    }
}
