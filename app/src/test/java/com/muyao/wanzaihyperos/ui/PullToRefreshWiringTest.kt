package com.muyao.wanzaihyperos.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息页的下拉刷新。
 *
 * 为什么值得钉：两个列表都要能刷新，但刷新**不能把列表清空**。
 * 仓库的 `selectGroup`/`refreshMessages` 会先把状态写成 `FirstLoad`，
 * 界面就闪一下「正在取得…」再冒出来 —— 手指往下拉的时候那是明显的抖动。
 * 所以刷新必须走 `startRefresh()`：有内容就原地保留，只把 refreshing 置起来。
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
        assertTrue("下拉没刷新消息列表", s.contains("graph.messages.refreshKeepingContent()"))
        assertTrue("下拉没刷新新闻列表", s.contains("graph.news.refreshKeepingContent()"))
    }

    @Test
    fun `刷新走 startRefresh 而不是把列表清成 FirstLoad`() {
        val s = text(repositories)
        assertTrue("仓库没有不清空内容的刷新入口", s.contains("suspend fun refreshKeepingContent()"))
        assertTrue("刷新没有走 startRefresh（会闪白）", s.contains("_messages.value = _messages.value.startRefresh()"))
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
        assertTrue("首页没有下拉刷新容器", s.contains("PullToRefresh("))
        val start = s.indexOf("PullToRefresh(")
        val end = s.indexOf("LazyColumn(", start)
        assertTrue("找不到被包住的列表", end > start)
        val block = s.substring(start, end)
        assertTrue("下拉没刷新课表", block.contains("graph.schedule.refresh()"))
        assertTrue("下拉没刷新待办", block.contains("graph.todoBoard.refresh()"))
        assertTrue("下拉没刷新签到", block.contains("graph.rollcall.refresh()"))
        assertTrue("下拉没刷新未读", block.contains("graph.unread.refresh(force = true)"))
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