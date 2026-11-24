package com.muyao.wanzaihyperos.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息列表的分页入口。
 *
 * 为什么值得钉：门户的 `listmessage` 一次只回 10 条（`rows=10` 写死在门户 JS 里），
 * 而一个分组实测有 189 条未读。没有「加载更多」，第 11 条之后在界面上根本走不到 ——
 * 那不是少一个按钮，是 179 条消息对用户不存在。
 */
class MessageLoadMoreTest {

    private val repo = File(System.getProperty("user.dir"))
        .let { run -> val parent = run.parentFile; if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else run }
    private val page = File(repo, "app/src/main/java/com/muyao/wanzaihyperos/ui/main/pages/NotifySectionPage.kt")

    private fun text(): String = page.readText()

    @Test
    fun `还有下一页时列表末尾要有加载更多`() {
        val s = text()
        assertTrue("讲座分页订阅", s.contains("graph.lectures.hasMore.collectAsVisibleStateWithLifecycle()"))
        assertTrue("没有加载更多入口", s.contains("graph.lectures.loadMore()"))
        assertTrue("加载更多只在还有下一页时出现", s.contains("if (lectureHasMore) {"))
    }

    @Test
    fun `组头要说明后面还有而不是只报一页的条数`() {
        val s = text()
        assertTrue("组头没说还有更多", s.contains("条 · 还有更多"))
        assertTrue("组头在取的时候不许把条数说成 0", s.contains("newsCount == null ->"))
    }
}