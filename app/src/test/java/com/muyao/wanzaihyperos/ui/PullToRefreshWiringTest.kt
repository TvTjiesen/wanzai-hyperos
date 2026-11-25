package com.muyao.wanzaihyperos.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息页的下拉刷新。
 *
 * 为什么值得钉：两个列表都要能刷新，但刷新**不能把列表清空**。
 * 切换分类的 `selectGroup` 会先把状态写成 `FirstLoad`，不能用它刷新当前分类。
 * 当前分类刷新必须走 `startRefresh()`：有内容就原地保留，只把 refreshing 置起来。
 */
class PullToRefreshWiringTest {

    private val repo = File(System.getProperty("user.dir"))
        .let { run -> val parent = run.parentFile; if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else run }
    private val page = File(repo, "app/src/main/java/com/muyao/wanzaihyperos/ui/main/pages/NotifySectionPage.kt")
    private val repositories = File(repo, "app/src/main/java/com/muyao/wanzaihyperos/data/portal/PortalRepositories.kt")
    private val home = File(repo, "app/src/main/java/com/muyao/wanzaihyperos/ui/main/pages/HomeSectionPage.kt")
    private val rollcall = File(repo, "app/src/main/java/com/muyao/wanzaihyperos/ui/tronclass/RollcallStationPage.kt")

    private fun text(file: File): String = file.readText()

    @Test
    fun `消息页两个列表都能下拉刷新`() {
        val s = text(page)
        assertTrue("消息页没有下拉刷新容器", s.contains("PullToRefresh("))
        assertTrue("下拉没刷新当前通知来源", s.contains("if (noticeSource == NOTICE_SOURCE_DAILY) graph.notices.refresh() else graph.lectures.refresh()"))
        assertFalse("旧门户通知不再读取", s.contains("graph.messages"))
        assertTrue("下拉没刷新新闻列表", s.contains("graph.news.refreshKeepingContent()"))
    }

    @Test
    fun `刷新走 startRefresh 而不是把列表清成 FirstLoad`() {
        val s = text(repositories)
        assertTrue("仓库没有不清空内容的刷新入口", s.contains("suspend fun refreshKeepingContent()"))
        val messageRepo = s.substringAfter("class LivePortalMessageRepository(")
        val refresh = messageRepo.substringAfter("override suspend fun refreshKeepingContent() {")
            .substringBefore("override suspend fun loadMore()")
        assertTrue("刷新没有走 startRefresh（会闪白）", refresh.contains(".startRefresh()"))
        assertFalse("刷新不能把已有通知清成 FirstLoad", refresh.contains("ListState.FirstLoad"))
        assertTrue("新闻刷新没有走 startRefresh（会闪白）", s.contains("_news.value = _news.value.startRefresh()"))
    }

    @Test
    fun `下拉刷新那块不许调会把列表清空的老入口`() {
        val s = text(page)
        val start = s.indexOf("PullToRefresh(")
        assertTrue("找不到下拉刷新容器", start >= 0)
        val end = s.indexOf("LazyColumn(", start)
        assertTrue("找不到被包住的列表", end > start)
        val block = s.substring(start, end)
        assertFalse("下拉刷新里用了 refreshMessages（会清空列表闪一下）", block.contains("refreshMessages()"))
        assertFalse("下拉刷新里用了 selectGroup（会清空列表闪一下）", block.contains("selectGroup("))
    }

    @Test
    fun `首页也能下拉刷新，四份数据都要重取`() {
        val s = text(home)
        val content = s.indexOf("val homeContent: @Composable () -> Unit = {")
        val list = s.indexOf("LazyColumn(", content.coerceAtLeast(0))
        val branch = s.indexOf("if (previewOnly) {\n            homeContent()\n        } else {")
        val start = s.indexOf("PullToRefresh(")
        val render = s.indexOf("homeContent()", start.coerceAtLeast(0))
        assertTrue("真实列表必须定义在 preview/正常页共用的 homeContent 内", content >= 0 && list > content && branch > list)
        assertTrue("只有正常首页应包下拉刷新并调用真实列表", start > branch && render > start)
        assertTrue("首页不能另外复制一份列表", Regex("\\bLazyColumn\\(").findAll(s).count() == 1)
        assertTrue("preview 和正常首页必须各调用一次同一份内容", Regex("\\bhomeContent\\(\\)").findAll(s).count() == 2)
        val block = s.substring(start, render)
        val refresh = block.substringAfter("onRefresh = {").substringBefore("pullToRefreshState =")
        assertTrue("下拉回调仍须阻止 preview 意外触发刷新", refresh.contains("if (previewOnly) return@PullToRefresh"))
        listOf("schedule", "todoBoard", "rollcall").forEach { source ->
            assertTrue("下拉没独立刷新 $source", refresh.contains("catchingActive { graph.$source.refresh() }"))
        }
        assertTrue("下拉没独立刷新未读", refresh.contains("catchingActive { graph.unread.refresh(force = true) }"))
        assertTrue("刷新完成或失败都必须复位指示器", refresh.contains("finally {") && refresh.contains("refreshing = false"))
        assertFalse("首页下拉不能把已有内容清成 FirstLoad", refresh.contains("ListState.FirstLoad"))
        assertTrue("下拉刷新没换掉英文气泡文案", block.contains("下拉刷新"))
    }

    // ── 签到页（2026-11-06 补）──────────────────────────────────────────
    //
    // 为什么值得钉：签到清单是**随时会变**的东西（老师这一秒发起），
    // 而人对着列表的第一反应就是往下拉。以前只有右上角那颗刷新钮，
    // 手指下拉毫无反应 —— 那种「这页是不是卡的」的错觉比功能缺失更伤人。
    @Test
    fun `签到页也能下拉刷新`() {
        val s = text(rollcall)
        assertTrue("签到页没有下拉刷新容器", s.contains("PullToRefresh("))
        val start = s.indexOf("PullToRefresh(")
        val end = s.indexOf("AppList(", start)
        assertTrue("找不到被包住的列表", end > start)
        val block = s.substring(start, end)
        assertTrue("下拉没刷新签到清单", block.contains("repository.refresh()"))
        assertTrue("下拉刷新没换掉英文气泡文案", block.contains("下拉刷新"))
    }

    @Test
    fun `签到页下拉刷新不许自己拼一个会清空列表的入口`() {
        val s = text(rollcall)
        val start = s.indexOf("PullToRefresh(")
        val end = s.indexOf("AppList(", start)
        val block = s.substring(start, end)
        // 仓库 `refresh()` 内部走 `startRefresh()`：有内容就原地保留，只把 refreshing 置起来。
        // 下拉里自己去改 state（或调 FirstLoad 那类入口）会让列表在手指底下闪一下。
        assertFalse("下拉刷新里直接改了 state（会闪空）", block.contains("ListState.FirstLoad"))
    }

    @Test
    fun `刷新完要重取未读数而不是本地减一`() {
        val s = text(page)
        assertTrue("刷新没重取未读总数", s.contains("graph.unread.refresh(force = true)"))
    }
}