package com.muyao.wanzaihyperos.ui.tronclass

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.tronclass.TronCourse
import com.muyao.wanzaihyperos.data.tronclass.TronFeed
import com.muyao.wanzaihyperos.data.tronclass.TronHome
import com.muyao.wanzaihyperos.data.tronclass.TronMyStats
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.ui.SourceScan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PartialUiTest {
    private fun source(name: String): String = SourceScan.sources()
        .first { it.first.endsWith("ui/tronclass/$name") }.second

    @Test fun failedEmptySourcesAreNotConfirmedEmpty() {
        val error = HzcuException.Transport("synthetic-offline")
        val failures = listOf(TronSourceFailure("课程公告", error))
        val feed = TronFeed(emptyList(), emptyList(), emptyList(), bulletinsError = error)
        assertTrue(feed.isEmpty)
        assertTrue(tronHasPartialProblem(ListMeta(), failures))
        assertTrue(tronSourceEmptyText("课程公告", error, false).contains("不能判断为空"))
        assertTrue(tronSourceEmptyText("待交作业", null, true).contains("尚未确认"))
        assertEquals("目前没有待交作业", tronSourceEmptyText("待交作业", null, false))
        assertFalse(tronHasPartialProblem(ListMeta(), emptyList()))
    }

    @Test fun oldRowsAndTheirTimestampRemainDisplayable() {
        val course = TronCourse(7, "synthetic-course", "", "", "")
        val rows = listOf(course)
        val error = HzcuException.Transport("synthetic-offline")
        val home = TronHome(rows, emptyList(), coursesError = error)
        val feed = TronFeed(emptyList(), emptyList(), rows, coursesError = error)
        val oldTime = Instant.parse("2026-11-01T10:20:00Z").toEpochMilli()
        val meta = ListMeta(updatedAtMillis = oldTime, stale = true, lastRefreshError = error)
        assertSame(rows, home.courses)
        assertSame(rows, feed.courses)
        assertEquals("synthetic-course", home.courses.single().name)
        val text = tronPartialTimeText(meta, ZoneId.of("UTC"))
        assertTrue(text.contains("上次完整取得 11-01 10:20"))
        assertTrue(text.contains("本次未全部确认"))
        assertEquals(oldTime, meta.updatedAtMillis!!)
        assertTrue(tronPartialTimeText(ListMeta(), ZoneId.of("UTC")).contains("时间未知"))
    }

    @Test fun staleEmptyAndUnknownStatsNeverBecomeZero() {
        assertTrue(tronHasPartialProblem(ListMeta(stale = true), emptyList()))
        assertTrue(tronHasPartialProblem(
            ListMeta(lastRefreshError = HzcuException.Transport("synthetic-offline")), emptyList()))
        val stats = TronMyStats(null, 0, null)
        assertEquals("—", tronStatsCountText(stats.todoCount))
        assertEquals("0", tronStatsCountText(stats.courseCount))
        assertEquals("—", tronStatsCountText(stats.openRollcallCount))
    }

    @Test fun retryInvokesOnlySuppliedReadOnceAndPreservesCancellation() = runTest {
        var reads = 0
        assertNull(tronRetryRead { reads++ })
        assertEquals(1, reads)
        assertTrue(tronRetryRead { throw HzcuException.Transport("synthetic-offline") }!!
            .contains("重试未完成"))
        val cancelled = CancellationException("synthetic-cancel")
        var propagated: Throwable? = null
        try { tronRetryRead { throw cancelled } } catch (error: Throwable) { propagated = error }
        assertSame(cancelled, propagated)
        assertEquals(1, reads)
    }

    @Test fun allProductionBodiesConsumeErrorsBeforeRenderingExistingRows() {
        val feed = source("TronFeedPage.kt")
        val me = source("TronMePage.kt")
        val home = source("TronBodies.kt").substringBefore("fun RollcallContent(")
        for (error in listOf("feed?.bulletinsError", "feed?.todosError", "feed?.coursesError")) {
            assertTrue(feed.contains(error))
        }
        assertTrue(feed.contains("(feed == null || feed.isEmpty) && !hasProblem"))
        assertTrue(feed.contains("TronPartialNotice(meta, failures, repository::refresh)"))
        assertTrue(feed.contains("infoItems(feed.courses"))
        assertTrue(feed.contains("infoItems(ordered"))
        assertTrue(home.contains("home?.coursesError"))
        assertTrue(home.contains("home?.todosError"))
        assertTrue(home.contains("tronSourceEmptyText(\"课程\", home.coursesError"))
        assertTrue(home.contains("tronSourceEmptyText(\"待交作业\", home.todosError"))
        assertTrue(home.contains("infoItems(home.courses"))
        assertTrue(home.contains("infoItems(home.todos"))
        assertTrue(me.contains("me?.statsErrors.orEmpty()"))
        assertTrue(me.contains("TronPartialNotice(meta, failures, repository::refresh)"))
        assertTrue(me.contains("text = me.profile.name"))
        assertTrue(me.contains("tronStatsCountText(me.stats.todoCount)"))
    }

    @Test fun retryIsAnAccessibleExplicitReadNotAutomaticSchoolWrite() {
        val feed = source("TronFeedPage.kt")
        val notice = feed.substringAfter("internal fun TronPartialNotice(")
        assertTrue(notice.contains("heightIn(min = 48.dp)"))
        assertTrue(notice.contains("role = Role.Button"))
        assertTrue(notice.contains("if (retrying || meta.refreshing) return@clickable"))
        assertTrue(notice.contains("try { retryFailure = tronRetryRead(onRetry) }"))
        assertTrue(notice.contains("finally { retrying = false }"))
        assertFalse(notice.contains("LaunchedEffect("))
        for (name in listOf("TronFeedPage.kt", "TronMePage.kt", "TronBodies.kt")) {
            val page = source(name).substringBefore("fun RollcallContent(")
            assertTrue(page.contains("repository::refresh"))
            for (write in listOf("submitHomework(", "answerRollcall(", "markRead(", "portalPost(")) {
                assertFalse("$name must not perform school writes", page.contains(write))
            }
        }
    }
}
