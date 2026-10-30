package com.muyao.wanzaihyperos.data.todo

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「最近要交的那一件」的守卫。
 *
 * 这里钉的每一条都是**会让人错过作业**的错法：
 * 把没截止时间的编一个时间、把过期的跳过、每次刷新顺序都变、
 * 把「不知道什么时候到期」说成「没有」。
 */
class TodoNextTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private val now = LocalDateTime.of(2026, 11, 2, 9, 0)

    private fun todo(
        key: String,
        title: String = "作业" + key,
        due: Long? = null,
        source: TodoSource = TodoSource.TRONCLASS,
    ) = TodoItem(
        source = source,
        key = key,
        title = title,
        subtitle = "计算机系统原理",
        sortTimeMillis = due,
        timeText = "",
        courseId = 1,
        actionable = true,
        hasDeadline = due != null,
    )

    private fun group(vararg items: TodoItem, source: TodoSource = TodoSource.TRONCLASS) =
        TodoGroup(source = source, items = items.toList(), total = items.size)

    @Test
    fun `挑出最近要交的那一件`() {
        val g = group(
            todo("3", due = millis(2026, 11, 13, 23, 59)),
            todo("1", due = millis(2026, 11, 4, 23, 30)),
            todo("2", due = millis(2026, 11, 6, 23, 59)),
        )
        val next = TodoNext.pick(listOf(g), now)
        assertEquals("1", next?.item?.key)
        assertEquals(2L, next?.daysAway)
        assertEquals(false, next?.overdue)
    }

    @Test
    fun `没有截止时间的条目不许参与挑选`() {
        val g = group(
            todo("a"), // 没有时间，绝不能当「今天到期」
            todo("b", due = millis(2026, 11, 20, 12, 0)),
        )
        assertEquals("b", TodoNext.pick(listOf(g), now)?.item?.key)
        assertEquals(1, TodoNext.unknownTimeCount(listOf(g)))
        assertEquals(1, TodoNext.datedCount(listOf(g)))
    }

    @Test
    fun `一条有截止时间的都没有就不显示`() {
        val g = group(todo("a"), todo("b"))
        assertNull(TodoNext.pick(listOf(g), now))
        assertEquals(2, TodoNext.unknownTimeCount(listOf(g)))
        assertEquals(0, TodoNext.datedCount(listOf(g)))
    }

    @Test
    fun `已经过期的排在所有未来的前面`() {
        val g = group(
            todo("future", due = millis(2026, 11, 3, 8, 0)),
            todo("overdue", due = millis(2026, 10, 30, 23, 59)),
        )
        val next = TodoNext.pick(listOf(g), now)
        assertEquals("overdue", next?.item?.key)
        assertTrue("过期了就得说过期", next?.overdue == true)
        assertEquals(-3L, next?.daysAway)
        assertEquals("已过期 3 天", next?.let { TodoNext.dueText(it.daysAway) })
    }

    @Test
    fun `跨来源一起挑而不是每组各挑一件`() {
        val tron = group(todo("t1", due = millis(2026, 11, 9, 10, 0)), source = TodoSource.TRONCLASS)
        val portal = group(
            todo("p1", due = millis(2026, 11, 3, 10, 0), source = TodoSource.PORTAL),
            source = TodoSource.PORTAL,
        )
        assertEquals("p1", TodoNext.pick(listOf(tron, portal), now)?.item?.key)
    }

    @Test
    fun `天数按日历天算不是按二十四小时整除`() {
        // 今天 09:00 看明天 00:10 —— 只差 15 小时，但那是「明天」。
        val g = group(todo("x", due = millis(2026, 11, 3, 0, 10)))
        val next = TodoNext.pick(listOf(g), now)
        assertEquals(1L, next?.daysAway)
        assertEquals("明天到期", next?.let { TodoNext.dueText(it.daysAway) })
    }

    @Test
    fun `今天到期说今天`() {
        val g = group(todo("x", due = millis(2026, 11, 2, 23, 59)))
        val next = TodoNext.pick(listOf(g), now)
        assertEquals(0L, next?.daysAway)
        assertEquals("今天到期", next?.let { TodoNext.dueText(it.daysAway) })
    }

    @Test
    fun `两次刷新挑到的必须是同一件`() {
        val same = millis(2026, 11, 6, 23, 59)
        val a = group(todo("b", due = same), todo("a", due = same))
        val b = group(todo("a", due = same), todo("b", due = same))
        assertEquals("a", TodoNext.pick(listOf(a), now)?.item?.key)
        assertEquals("a", TodoNext.pick(listOf(b), now)?.item?.key)
    }

    @Test
    fun `首页排序把过期的和最近的顶到前面`() {
        val items = listOf(
            todo("none"),
            todo("far", due = millis(2026, 11, 20, 12, 0)),
            todo("late", due = millis(2026, 10, 29, 12, 0)),
            todo("soon", due = millis(2026, 11, 3, 12, 0)),
        )
        val keys = TodoNext.ordered(items, now).map { it.key }
        assertEquals(listOf("late", "soon", "far", "none"), keys)
    }

    @Test
    fun `排序不动没时间的那些的相对顺序`() {
        val items = listOf(todo("n1"), todo("n2"), todo("n3"))
        assertEquals(listOf("n1", "n2", "n3"), TodoNext.ordered(items, now).map { it.key })
    }

    /**
     * 这一条是 2026-11-01 真机抓到的真错：
     * 門戶那条的 sortTimeMillis 是 **创建时间**（2025-08-29），
     * 当年把它当截止时间，于是 14 个月前的老审批被顶成「最近要交」的第一条。
     */
    @Test
    fun `门户的创建时间不许当截止时间`() {
        val old = todo("p1", title = "Blackboard密码找回", due = millis(2025, 8, 29, 18, 37), source = TodoSource.PORTAL)
            .copy(hasDeadline = false)
        val homework = todo("t1", due = millis(2026, 11, 4, 23, 30))
        val groups = listOf(
            TodoGroup(source = TodoSource.PORTAL, items = listOf(old), total = 1),
            TodoGroup(source = TodoSource.TRONCLASS, items = listOf(homework), total = 1),
        )
        assertEquals("t1", TodoNext.pick(groups, now)?.item?.key)
        assertEquals(0, TodoNext.datedCount(listOf(groups[0])))
        assertEquals(1, TodoNext.datedCount(listOf(groups[1])))
        // 排到后面去，而不是凭「最早创建」占到第一位。
        val ordered = TodoNext.ordered(listOf(old, homework), now).map { it.key }
        assertEquals(listOf("t1", "p1"), ordered)
    }

    @Test
    fun `天数文案三档`() {
        assertEquals("已过期 1 天", TodoNext.dueText(-1))
        assertEquals("今天到期", TodoNext.dueText(0))
        assertEquals("明天到期", TodoNext.dueText(1))
        assertEquals("5 天后到期", TodoNext.dueText(5))
    }
}
