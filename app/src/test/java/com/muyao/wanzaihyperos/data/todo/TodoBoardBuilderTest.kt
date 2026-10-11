package com.muyao.wanzaihyperos.data.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6 多來源待辦的**聚合規則**。
 *
 * 這一組盯的是計劃書 §B6 的三條硬要求裡最容易寫錯的兩條：
 * 「來源內去重」與「未知總數不當 0」。
 * 第三條（按來源分組）由頁面結構保證，這裡測它的資料前提。
 */
class TodoBoardBuilderTest {

    private fun item(
        source: TodoSource,
        key: String,
        title: String = key,
        time: Long? = null,
    ) = TodoItem(
        source = source,
        key = key,
        title = title,
        subtitle = "",
        sortTimeMillis = time,
        timeText = "",
    )

    // ---- 去重 -------------------------------------------------------------

    @Test
    fun `来源内重复的 key 只留一条`() {
        val items = listOf(
            item(TodoSource.PORTAL, "a", time = 100),
            item(TodoSource.PORTAL, "a", time = 100),
            item(TodoSource.PORTAL, "b", time = 200),
        )

        val out = TodoBoardBuilder.dedupeAndSort(items)

        assertEquals("重复项必须被合并", 2, out.size)
        assertEquals(listOf("a", "b"), out.map { it.key })
    }

    @Test
    fun `保留第一次出现的那条`() {
        // 為什麼是「第一條」：服務端通常重要的排前面，
        // 而重複項往往是分頁重疊帶進來的尾巴。
        val items = listOf(
            item(TodoSource.PORTAL, "a", title = "第一条"),
            item(TodoSource.PORTAL, "a", title = "第二条"),
        )

        val out = TodoBoardBuilder.dedupeAndSort(items)

        assertEquals("第一条", out.single().title)
    }

    @Test
    fun `相同 key 跨来源都保留`() {
        // ★ 這條盯的是**最容易犯的错**：兩邊的 id 體系完全不同
        //   （門戶是字串 billid，學在城院是數字 id），
        //   跨來源「去重」會把兩邊碰巧相同的 key 誤刪。
        val items = listOf(
            item(TodoSource.PORTAL, "1"),
            item(TodoSource.TRONCLASS, "1"),
        )

        val out = TodoBoardBuilder.dedupeAndSort(items)

        assertEquals("不同來源的同名 key 不能互相抵消", 2, out.size)
    }

    // ---- 排序 -------------------------------------------------------------

    @Test
    fun `有截止时间的排前面且升序，没时间的排后面`() {
        val items = listOf(
            item(TodoSource.TRONCLASS, "no-time-a"),
            item(TodoSource.PORTAL, "late", time = 900),
            item(TodoSource.PORTAL, "soon", time = 100),
            item(TodoSource.TRONCLASS, "no-time-b"),
        )

        val out = TodoBoardBuilder.dedupeAndSort(items)

        assertEquals(listOf("soon", "late", "no-time-a", "no-time-b"), out.map { it.key })
    }

    @Test
    fun `同时间的顺序稳定（不随刷新跳动）`() {
        // 否則使用者每次進來看到的順序都在跳，會以為內容變了。
        val items = listOf(
            item(TodoSource.PORTAL, "z", time = 100),
            item(TodoSource.PORTAL, "m", time = 100),
            item(TodoSource.PORTAL, "a", time = 100),
        )

        repeat(5) {
            assertEquals(listOf("a", "m", "z"), TodoBoardBuilder.dedupeAndSort(items).map { it.key })
        }
    }

    // ---- 未知總數不當 0 ---------------------------------------------------

    private fun boardOf(
        tron: SourceResult?,
        portal: SourceResult?,
    ) = TodoBoardBuilder.build(listOfNotNull(tron, portal))

    @Test
    fun `取数失败的来源显示破折号而不是 0`() {
        val board = boardOf(
            tron = SourceResult(
                source = TodoSource.TRONCLASS,
                items = null,
                error = "尚未接入",
            ),
            portal = SourceResult(
                source = TodoSource.PORTAL,
                items = listOf(item(TodoSource.PORTAL, "a")),
                total = 1,
            ),
        )

        val tron = board.groups.first { it.source == TodoSource.TRONCLASS }
        assertTrue(tron.failed)
        assertEquals("失败不能表现成 0 条", "—", tron.countText)
    }

    @Test
    fun `任一来源未知时总数不给一个偏小的数字`() {
        val partial = boardOf(
            tron = SourceResult(TodoSource.TRONCLASS, items = null, error = "尚未接入"),
            portal = SourceResult(
                TodoSource.PORTAL,
                items = listOf(item(TodoSource.PORTAL, "a")),
                total = 1,
            ),
        )
        assertNull("有一路未知時，總數必須是 null", partial.knownTotal)

        val full = boardOf(
            tron = SourceResult(
                TodoSource.TRONCLASS,
                items = listOf(item(TodoSource.TRONCLASS, "t1")),
                total = 1,
            ),
            portal = SourceResult(
                TodoSource.PORTAL,
                items = listOf(item(TodoSource.PORTAL, "a")),
                total = 2,
            ),
        )
        assertEquals(3, full.knownTotal)
    }

    @Test
    fun `只取了第一页时总数不算已知`() {
        // ★ 這條盯的是「虛報」：第一頁取滿就说明後面還有，
        //   這時候的和只是**下限**。說「共 3 條」會讓使用者以為「就這 3 件」。
        val board = boardOf(
            tron = SourceResult(
                TodoSource.TRONCLASS,
                items = listOf(item(TodoSource.TRONCLASS, "t1")),
                total = 1,
            ),
            portal = SourceResult(
                TodoSource.PORTAL,
                items = listOf(item(TodoSource.PORTAL, "a")),
                total = null,
                hasMore = true,
            ),
        )

        assertNull("有來源還有下一頁時，總數是下限不是準確值", board.knownTotal)
        val portal = board.groups.first { it.source == TodoSource.PORTAL }
        assertEquals("來源標題要顯示「N+」", "1+", portal.countText)
    }

    @Test
    fun `两个来源都失败时判为整页失败`() {
        val board = boardOf(
            tron = SourceResult(TodoSource.TRONCLASS, items = null, error = "挂了"),
            portal = SourceResult(TodoSource.PORTAL, items = null, error = "挂了"),
        )
        assertTrue(board.allFailed)
    }

    @Test
    fun `一个来源真的为空时显示 0 而不是破折号`() {
        // ★ 與「失敗」對照：真的取到了、就是沒有 → 該顯示 0。
        //   把「沒有」也顯示成「—」會讓人以為系統壞了。
        val board = boardOf(
            tron = SourceResult(TodoSource.TRONCLASS, items = emptyList(), total = 0),
            portal = SourceResult(
                TodoSource.PORTAL,
                items = listOf(item(TodoSource.PORTAL, "a")),
                total = 1,
            ),
        )
        val tron = board.groups.first { it.source == TodoSource.TRONCLASS }
        assertTrue("空的來源不該算失敗", !tron.failed)
        assertEquals("0", tron.countText)
    }

    // ---- 空來源的語義 -----------------------------------------------------

    @Test
    fun `未接入的来源保留原因文字`() {
        val board = boardOf(
            tron = SourceResult(
                TodoSource.TRONCLASS,
                items = null,
                error = "这个来源还没接通",
            ),
            portal = SourceResult(TodoSource.PORTAL, items = emptyList(), total = 0),
        )
        val tron = board.groups.first { it.source == TodoSource.TRONCLASS }
        assertEquals("这个来源还没接通", tron.error)
        assertTrue("未接入時不該有條目", tron.items.isEmpty())
    }

    @Test
    fun `给用户的文字里不含内部术语`() {
        // ★ 第一版寫過「尚未接入（需要先换 sessionID，见代码注释）」，
        //   真機截圖裡就露出來了 —— 那是把開發筆記印在產品上。
        //   技術原因該待在代碼註釋與走查文檔裡，不是界面上。
        val board = boardOf(
            tron = SourceResult(
                TodoSource.TRONCLASS,
                items = null,
                error = "这个来源还没接通，暂时看不到作业",
            ),
            portal = SourceResult(TodoSource.PORTAL, items = emptyList(), total = 0),
        )
        val text = board.groups.first { it.source == TodoSource.TRONCLASS }.error.orEmpty()
        for (jargon in listOf("sessionID", "代码", "API", "token", "TODO")) {
            assertTrue("界面文字不該出現「$jargon」", !text.contains(jargon))
        }
    }
}
