package com.muyao.wanzaihyperos.data.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「点开一条消息之后标已读」的本地变换。
 *
 * 钉的是三件容易写错的事：
 * ① 只动那一条，别动别的行；
 * ② 回滚要退回**基线里的值**，不是无条件写 false；
 * ③ 基线里没有这条（换了分组/换了页）就原样返回，不许凭空造一行。
 */
class MessageReadTest {

    private fun row(id: String, read: Boolean = false) = PortalMessageItem(
        id = id,
        title = "标题$id",
        message = "",
        isRead = read,
        group = "系统通知",
        createdDate = "2026-10-30 10:00",
        lastUpdateDate = "",
    )

    @Test
    fun `标已读只动点开的那一条`() {
        val items = listOf(row("a"), row("b"), row("c", read = true))
        val out = MessageRead.markOne(items, "b")

        assertEquals(3, out.size)
        assertEquals(listOf("a", "b", "c"), out.map { it.id })
        assertFalse("a 不该被动", out[0].isRead)
        assertTrue("b 被点开，必须变已读", out[1].isRead)
        assertTrue("c 本来就是已读", out[2].isRead)
    }

    @Test
    fun `列表里没有这个 id 就原样返回同一个对象`() {
        val items = listOf(row("a"), row("b"))
        // 同一个实例：没命中就不该白造一份新列表，否则上层会误以为状态变了。
        assertSame(items, MessageRead.markOne(items, "zzz"))
        assertSame(items, MessageRead.revertOne(items, items, "zzz"))
    }

    @Test
    fun `回滚退回基线里的值而不是硬写未读`() {
        val baseline = listOf(row("a"), row("b", read = true))
        val optimistic = MessageRead.markOne(baseline, "a")
        assertTrue(optimistic[0].isRead)

        // 写失败：a 退回基线（未读）；b 在基线里就是已读，回滚不能把它变成未读。
        val reverted = MessageRead.revertOne(optimistic, baseline, "a")
        assertFalse(reverted[0].isRead)
        assertTrue("基线是已读的行，回滚后必须还是已读", reverted[1].isRead)
    }

    @Test
    fun `期间刷新过列表也只退回那一条`() {
        val baseline = listOf(row("a"), row("b"))
        // 乐观态之后列表被刷新：多了 c，且 a 的位置变了。
        val refreshed = listOf(row("c"), row("a", read = true), row("b", read = true))
        val reverted = MessageRead.revertOne(refreshed, baseline, "a")

        assertEquals(3, reverted.size)
        assertFalse(reverted[1].isRead)
        // b 在基线里是未读，但这次回滚只针对 a —— b 现在是服务端说的已读，不许改。
        assertTrue("回滚只针对 a，不许连坐 b", reverted[2].isRead)
        assertEquals("新出现的行不许被删", "c", reverted[0].id)
    }

    @Test
    fun `本来是未读的标成已读才叫变化`() {
        val items = listOf(row("a", read = false))
        assertFalse(items[0].isRead)
        assertTrue(MessageRead.markOne(items, "a")[0].isRead)
    }
}
