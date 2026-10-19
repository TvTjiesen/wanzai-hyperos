package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表状态机。这里钉住的每一条，都对应一种「界面替服务端撒谎」的坏法。
 */
class ListStateTest {

    private data class Item(val id: Int, val name: String)

    private val first: ListState<Item> = ListState.FirstLoad

    private val items = listOf(Item(1, "一"), Item(2, "二"))

    // ---------------------------------------------------------------------------
    // 三种「空」必须分得开
    // ---------------------------------------------------------------------------

    @Test
    fun `首次加载不是空也不是失败`() {
        assertFalse("还没发请求不能算「已出结论」", first.hasSettled)
        assertTrue(first.items.isEmpty())
        assertNull(first.metaOrNull)
    }

    @Test
    fun `服务端明确返回空才是 Empty`() {
        val settled = first.settleFirstPage(emptyList(), nowMillis = 100L) { it.id }
        assertTrue(settled is ListState.Empty)
        assertTrue(settled.hasSettled)
    }

    @Test
    fun `取数失败是 Failed，不是 Empty`() {
        val failed = first.failWith(HzcuException.Transport())
        assertTrue("失败被当成了「没有内容」", failed is ListState.Failed)
        assertFalse(failed.hasSettled)
    }

    // ---------------------------------------------------------------------------
    // ★ 取消 ≠ 失败（真机根因：切页面把请求取消，假「取不到」被永久写进仓库）
    // ---------------------------------------------------------------------------

    /** Compose 抛的 `LeftCompositionCancellationException` 不是 CancellationException，
     *  所以判据要认它的类名与消息；这里用同样的消息构造替身，避免测试依赖 Compose。 */
    private fun cancelledLike() = HzcuException.Transport(
        details = "异常类型：LeftCompositionCancellationException",
        cause = IllegalStateException("The coroutine scope left the composition"),
    )

    @Test
    fun `被取消不能写成取不到`() {
        val after = first.failWith(cancelledLike())
        assertTrue("取消被当成了 Failed，界面会显示「取不到」并一直挂着", after is ListState.FirstLoad)
    }

    @Test
    fun `有内容时被取消只停转圈不标旧数据`() {
        val content = first.settleFirstPage(items, nowMillis = 100L) { it.id }
        val refreshing = content.startRefresh()
        val c = refreshing.failWith(cancelledLike()) as ListState.Content
        assertFalse("取消不该把内容标成 stale", c.meta.stale)
        assertNull("取消不该留下刷新错误", c.meta.lastRefreshError)
        assertFalse("转圈标志必须清掉，否则会一直「正在取得…」", c.meta.refreshing)
    }

    @Test
    fun `真失败仍然要标 stale（别把诚实路径一起拆掉）`() {
        val content = first.settleFirstPage(items, nowMillis = 100L) { it.id }
        val c = content.startRefresh().failWith(HzcuException.Transport()) as ListState.Content
        assertTrue("真失败必须标旧数据", c.meta.stale)
    }

    @Test
    fun `翻页被取消不记错误`() {
        val content = first.settleFirstPage(items, nowMillis = 100L) { it.id }
        val c = content.startLoadMore().failLoadMore(cancelledLike()) as ListState.Content
        assertNull(c.meta.lastRefreshError)
        assertFalse(c.meta.loadingMore)
    }

    @Test
    fun `认证失效单独一类且不能是 Failed`() {
        val expired = first.failWith(HzcuException.LoginExpired("没登录"))
        assertTrue(expired is ListState.SessionExpired)
        // 界面据此引导去登录；如果混成 Failed，用户只会看到「加载失败」然后反复重试。
        assertFalse(expired is ListState.Failed)
    }

    // ---------------------------------------------------------------------------
    // 刷新失败不能把已有内容清掉
    // ---------------------------------------------------------------------------

    @Test
    fun `刷新失败保留旧数据并标注不新鲜`() {
        val loaded = first.settleFirstPage(items, nowMillis = 100L) { it.id }
        val refreshed = loaded.startRefresh()
        assertTrue("刷新时内容不能被清掉", refreshed is ListState.Content)
        assertTrue((refreshed as ListState.Content).meta.refreshing)

        val failed = refreshed.failWith(HzcuException.Transport())

        assertTrue("刷新失败后内容不见了", failed is ListState.Content)
        val content = failed as ListState.Content
        assertEquals(items, content.items)
        assertTrue(content.meta.stale)
        assertFalse(content.meta.refreshing)
        assertTrue(content.meta.lastRefreshError is HzcuException.Transport)
    }

    @Test
    fun `翻页失败保留内容且不把已加载的说成过期`() {
        val loaded = first.settleFirstPage(items, nowMillis = 100L) { it.id }
        val loading = loaded.startLoadMore()
        val failed = loading.failLoadMore(HzcuException.HttpStatus(500, "http://x/y", "<空响应>"))

        val content = failed as ListState.Content
        assertEquals(items, content.items)
        assertFalse("翻页失败不代表已有内容过期", content.meta.stale)
        assertFalse(content.meta.loadingMore)
        assertTrue(content.meta.lastRefreshError is HzcuException.HttpStatus)
    }

    @Test
    fun `在首次加载态下拉刷新仍然是首次加载`() {
        // 还没有内容时「刷新」其实就是首次加载，界面该显示加载态。
        assertTrue(first.startRefresh() is ListState.FirstLoad)
    }

    // ---------------------------------------------------------------------------
    // 去重：门户目录历史上出现过跨分组重复 id
    // ---------------------------------------------------------------------------

    @Test
    fun `首次页会按 id 去重`() {
        val withDupe = listOf(Item(1, "一"), Item(1, "一的副本"), Item(2, "二"))
        val settled = first.settleFirstPage(withDupe, nowMillis = 1L) { it.id }
        assertEquals(2, (settled as ListState.Content).items.size)
    }

    @Test
    fun `翻页追加也会去重`() {
        val page1 = first.settleFirstPage(items, nowMillis = 1L) { it.id }
        val page2 = page1.appendPage(
            newItems = listOf(Item(2, "二"), Item(3, "三")),
            page = 2,
            hasMore = false,
            nowMillis = 2L,
        ) { it.id }

        val content = page2 as ListState.Content
        assertEquals(listOf(1, 2, 3), content.items.map { it.id })
        assertEquals(2, content.meta.page)
        assertFalse(content.meta.hasMore)
    }

    @Test
    fun `withHasMore 只动翻页标志`() {
        val settled = first.settleFirstPage(items, nowMillis = 1L) { it.id }
            .withHasMore(hasMore = true)
        val content = settled as ListState.Content
        assertEquals(items, content.items)
        assertTrue(content.meta.hasMore)
    }

    // ---------------------------------------------------------------------------
    // 未知不得当成 0
    // ---------------------------------------------------------------------------

    @Test
    fun `未知计数与零计数必须能区分`() {
        // ★ 这是业主在 Flutter 版明确提过的问题：取不到时显示 0，
        //   等于告诉用户「你没有未读消息」，而我们其实不知道。
        assertNull(CountValue.Unknown.orNull)
        assertEquals(0, CountValue.Known(0).orNull)
        assertFalse(CountValue.Unknown == CountValue.Known(0))
    }

    @Test
    fun `失败后的状态里仍然没有把计数写成 0`() {
        val meta = ListMeta(refreshing = false, stale = true)
        assertNull(CountValue.Unknown.orNull)
        assertTrue(meta.hasRefreshProblem)
    }

    @Test
    fun `正常情况下没有刷新问题`() {
        assertFalse(ListMeta().hasRefreshProblem)
    }
}
