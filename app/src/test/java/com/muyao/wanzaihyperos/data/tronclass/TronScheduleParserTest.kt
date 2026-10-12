package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * B13：上課時間字串的解析測試。
 *
 * 輸入全部是**真機撈到的原文**（我 30 門課的 25 種寫法，
 * 存 `docs/B13-TRONCLASS-SCHEDULE.txt`）—— 不是照著解析器反推的。
 */
class TronScheduleParserTest {

    /** 真機樣本 #0。 */
    private val sample0 = "周2第3-4节第1,3周;周2第3-4节第5,17周"

    @Test
    fun `多段用分號切開`() {
        val r = TronScheduleParser.parse(sample0, courseId = 67436, courseName = "数据结构与算法")
        assertTrue(r.unparsed.isEmpty())
        assertEquals(2, r.slots.size)
        val a = r.slots[0]
        assertEquals(2, a.dayOfWeek)
        assertEquals(3, a.periodStart)
        assertEquals(4, a.periodEnd)
        assertEquals(1, a.weekStart)
        assertEquals(3, a.weekEnd)
        assertEquals("周二", a.dayLabel)
        assertEquals("第3-4节", a.periodLabel)
        assertEquals("第1-3周", a.weekLabel)
    }

    @Test
    fun `逗號是範圍不是清單`() {
        // 關鍵判斷：`第1,3周` = 第 1 到第 3 週（不是「第1週和第3週」）。
        // 反證在 `第1,1周`：當清單會變成「第1週和第1週」。
        val single = TronScheduleParser.parse("周3第8-9节第1,1周", 1, "课")
        assertEquals(1, single.slots.size)
        assertEquals(1, single.slots[0].weekStart)
        assertEquals(1, single.slots[0].weekEnd)
        assertEquals("第1周", single.slots[0].weekLabel)
        // 閉區間：第 1 週有課，第 2 週沒有。
        assertTrue(single.slots[0].activeIn(1))
        assertFalse(single.slots[0].activeIn(2))
    }

    @Test
    fun `範圍的兩端都算在內`() {
        val slot = TronScheduleParser.parse("周2第3-4节第5,17周", 1, "课").slots[0]
        assertTrue(slot.activeIn(5))
        assertTrue(slot.activeIn(11))
        assertTrue(slot.activeIn(17))
        assertFalse(slot.activeIn(4))
        assertFalse(slot.activeIn(18))
    }

    @Test
    fun `25 種真機寫法全部解析成功`() {
        // 逐字抄自探測輸出（docs/B13-TRONCLASS-SCHEDULE.txt）。
        val real = listOf(
            "周2第3-4节第1,3周;周2第3-4节第5,17周",
            "周2第6-7节第1,3周;周2第6-7节第5,17周;周4第8-9节第1,2周;周4第8-9节第4,17周",
            "周2第8-9节第10,16周;周2第8-9节第1,3周;周2第8-9节第5,9周;周2第8-9节第17,17周",
            "周3第8-9节第1,1周;周3第8-9节第5,5周;周3第8-9节第8,9周;周3第8-9节第2,3周;周3第8-9节第6,7周",
            "周4第1-4节第1,2周;周4第1-4节第4,17周",
            "周4第6-7节第1,2周;周4第6-7节第4,17周",
            "周4第10-11节第12,17周;周4第10-11节第7,11周;周4第10-11节第17,17周;周4第10-11节第1,2周;周4第10-11节第4,6周",
            "周1第1-2节第18,18周;周5第1-2节第1,1周;周5第1-2节第4,6周;周5第1-2节第8,15周;周5第1-2节第17,17周;周6第1-2节第4,4周;周7第1-2节第1,1周",
            "周1第3-4节第18,18周;周5第3-4节第1,1周;周5第3-4节第4,6周;周5第3-4节第8,15周;周5第3-4节第17,17周;周6第3-4节第4,4周;周7第3-4节第1,1周",
            "周1第6-9节第1,3周;周1第6-9节第5,17周",
            "周3第8-9节第10,17周",
            "周4第6-8节第1,16周",
            "周1第1-2节第1,5周;周1第1-2节第7,9周;周1第1-2节第11,17周;周6第1-2节第10,10周",
            "周3第3-5节第17,17周;周4第3-5节第17,17周;周5第3-5节第1,8周;周5第3-5节第10,15周",
            "周1第6-7节第1,5周;周1第6-7节第7,9周;周1第6-7节第11,17周;周6第6-7节第10,10周",
            "周3第6-7节第1,16周",
            "周3第10-11节第11,11周;周5第1-2节第1,8周;周5第1-2节第11,15周;周7第10-11节第11,11周;周1第3-4节第1,5周;周1第3-4节第7,9周;周1第3-4节第11,17周;周3第1-2节第1,17周;周4第1-2节第17,17周",
            "周2第3-4节第5,5周;周2第3-4节第13,13周;周4第1-2节第13,13周;周2第3-4节第1,4周;周2第3-4节第6,9周;周2第3-4节第11,12周;周2第3-4节第14,17周;周4第1-2节第1,12周;周4第1-2节第14,16周",
            "周2第1-2节第1,9周;周2第1-2节第11,17周;周4第3-4节第1,16周",
            "周2第6-7节第1,9周;周2第6-7节第11,17周",
            "周1第8-11节第1,5周;周1第8-11节第7,9周;周1第8-11节第11,17周;周6第8-11节第10,10周",
            "周3第8-9节第3,14周;周3第8-9节第16,16周;周7第8-9节第15,15周;周3第8-9节第1,2周",
            "周3第3-4节第1,1周;周3第3-4节第14,15周;周7第8-9节第3,4周;周3第3-4节第2,2周;周3第3-4节第5,13周;周3第3-4节第16,16周",
            "周4第3-5节第1,3周;周1第3-5节第18,18周;周4第3-5节第4,15周;周4第3-5节第17,17周",
            "周2第1-2节第1,17周",
        )
        assertEquals("樣本數應為 25", 25, real.size)
        var total = 0
        real.forEachIndexed { i, text ->
            val r = TronScheduleParser.parse(text, courseId = i, courseName = "课$i")
            assertTrue("第 $i 條有認不出的段落：${r.unparsed}（原文 $text）", r.unparsed.isEmpty())
            assertTrue("第 $i 條一個格子都沒解析出來", r.slots.isNotEmpty())
            // 每一格的週次區間必須合法。
            r.slots.forEach { s ->
                assertTrue("星期超出 1..7：${s.dayOfWeek}", s.dayOfWeek in 1..7)
                assertTrue("節次範圍顛倒", s.periodStart <= s.periodEnd)
                assertTrue("週次範圍顛倒", s.weekStart <= s.weekEnd)
            }
            total += r.slots.size
        }
        assertTrue("總格數應該不少（實際 $total）", total > 60)
    }

    @Test
    fun `認不出的段落會被保留而不是丟掉`() {
        val r = TronScheduleParser.parse(
            "周2第3-4节第1,3周;這是一段看不懂的;周3第1-2节第1,1周",
            courseId = 7,
            courseName = "課",
        )
        assertEquals(2, r.slots.size)
        assertEquals(listOf("這是一段看不懂的"), r.unparsed)
    }

    @Test
    fun `空字串回空結果而不是 unparsed`() {
        val r = TronScheduleParser.parse("   ", 1, "课")
        assertTrue(r.slots.isEmpty())
        assertTrue("空白不該被當成「認不出」", r.unparsed.isEmpty())
    }

    @Test
    fun `全角分號也接`() {
        val r = TronScheduleParser.parse("周1第1-2节第1,2周；周3第1-2节第1,2周", 1, "课")
        assertEquals(2, r.slots.size)
        assertTrue(r.unparsed.isEmpty())
    }

    @Test
    fun `排序是週一到週日再按節次`() {
        val r = TronScheduleParser.parse(
            "周5第1-2节第1,1周;周1第6-7节第1,1周;周1第1-2节第1,1周",
            1,
            "课",
        )
        assertEquals(listOf(1, 1, 5), r.slots.map { it.dayOfWeek })
        assertEquals(listOf(1, 6, 1), r.slots.map { it.periodStart })
    }

    @Test
    fun `slotsAt 只取那一週那一天`() {
        val slots = TronScheduleParser.parse(sample0, 1, "课").slots
        val schedule = TronSchedule(slots, com.muyao.wanzaihyperos.data.CountValue.Unknown, emptyList())
        // 第 2 週：週二有課。第 4 週：兩段都不含 4（1-3 與 5-17）→ 沒課。
        assertEquals(1, schedule.slotsAt(2, 2).size)
        assertTrue(schedule.slotsAt(4, 2).isEmpty())
        assertEquals(listOf(2), schedule.activeDays)
        assertEquals(1, schedule.courseCount)
    }
}

/** 教學週（`/api/org/{id}/teaching-weeks`）。 */
class TronTeachingWeekTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // 真機樣本（節錄）。
    private val real = """
        {"items":[
         {"comment":null,"end_date":"2027-01-17","id":335,"name":"第18周","sequence":18,"start_date":"2027-01-11"},
         {"comment":null,"end_date":"2027-01-10","id":332,"name":"第17周","sequence":17,"start_date":"2027-01-04"},
         {"comment":null,"end_date":"2026-09-20","id":302,"name":"第1周","sequence":1,"start_date":"2026-09-14"}
        ]}
    """.trimIndent()

    @Test
    fun `解析教學週並依序排列`() {
        val weeks = TronTeachingWeekParser.parse(obj(real))
        assertEquals(3, weeks.size)
        assertEquals(listOf(1, 17, 18), weeks.map { it.sequence })
        assertEquals(LocalDate.of(2026, 9, 14), weeks[0].startDate)
    }

    @Test
    fun `算出目前是第幾週`() {
        val weeks = TronTeachingWeekParser.parse(obj(real))
        assertEquals(1, currentTeachingWeek(weeks, LocalDate.of(2026, 9, 15)))
        // 邊界：起訖兩端都算。
        assertEquals(1, currentTeachingWeek(weeks, LocalDate.of(2026, 9, 14)))
        assertEquals(1, currentTeachingWeek(weeks, LocalDate.of(2026, 9, 20)))
        assertEquals(18, currentTeachingWeek(weeks, LocalDate.of(2027, 1, 17)))
        // 兩週之間的空檔（放假）→ null，那是「現在沒課」，不是錯誤。
        assertNull(currentTeachingWeek(weeks, LocalDate.of(2026, 9, 21)))
    }

    @Test
    fun `缺 sequence 或日期的條目丟掉`() {
        val weeks = TronTeachingWeekParser.parse(
            obj(
                """{"items":[{"sequence":1,"start_date":"2026-09-14","end_date":"2026-09-20"},
                            {"name":"没有序号","start_date":"2026-09-21","end_date":"2026-09-27"},
                            {"sequence":3,"start_date":"乱码","end_date":"2026-10-04"}]}""",
            ),
        )
        assertEquals(1, weeks.size)
        assertEquals(1, weeks[0].sequence)
    }

    @Test
    fun `回應不是教學週時回空清單`() {
        assertTrue(TronTeachingWeekParser.parse(obj("""{"message":"未找到资源"}""")).isEmpty())
        assertTrue(TronTeachingWeekParser.parse(null).isEmpty())
    }
}
