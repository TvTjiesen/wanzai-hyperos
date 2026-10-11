package com.muyao.wanzaihyperos.data.cjh52

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 課表解析的離線驗證。
 *
 * ### 樣本從哪來
 *
 * 結構取自 `docs/B0-CJH52-CONTRACT.md` §4.2 的**真機取證形狀**
 * （欄位名與型別是實測的），但**值全部是編造的假資料** ——
 * 契約文檔與這裡都刻意不保存任何真實課程/教師資料。
 *
 * ### 為什麼要專門測「一課多段」
 *
 * 這是 B1 最容易靜默出錯的地方：如果模型只留一個時段，
 * 同一門課的第二個時段會**無聲消失**（不報錯、不留痕）。
 * 所以下面有兩條測試專盯它。
 */
class CourseParserTest {

    private fun obj(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    /** 一門課帶**兩個時段**（不同星期、不同週次、不同教室）。 */
    private val twoSlotCourse = """
    {
      "cid": 1001,
      "className": "範例課程A",
      "classNo": "(2026-2027-1)-A0101-4-J05018-5",
      "collegeName": "範例學院",
      "teaName": "範例教師",
      "courseCode": "A0101",
      "natureContent": "必修",
      "classTimeInfoList": [
        {"day":"星期一","start":"13:30:00","end":"15:05:00",
         "startWeek":"1","endWeek":"8",
         "location":"教六310","classroomName":"310","campusName":"範例校區","classroomSid":11},
        {"day":"星期三","start":"09:50:00","end":"12:15:00",
         "startWeek":"9","endWeek":"16",
         "location":"理4-205","classroomName":"205","campusName":"範例校區","classroomSid":22}
      ]
    }
    """.trimIndent()

    @Test
    fun `一門課的兩個時段都要保留`() {
        val courses = CourseParser.parse(listOf(obj(twoSlotCourse)))

        assertEquals(1, courses.size)
        val c = courses.single()
        assertEquals("範例課程A", c.name)
        // ★ 核心斷言：不是 1 個而是 2 個。
        assertEquals("兩個時段都必須留下來", 2, c.slots.size)
        assertEquals(listOf("星期一", "星期三"), c.slots.map { it.day })
        assertEquals(listOf("教六310", "理4-205"), c.slots.map { it.displayLocation })
    }

    @Test
    fun `多個時段的週次合併成課程級區間`() {
        val c = CourseParser.parse(listOf(obj(twoSlotCourse))).single()

        // 1-8 與 9-16 合起來是 1-16
        assertEquals(1..16, c.weekRange)
    }

    @Test
    fun `週次是字串也能讀 且兩位數不會被截斷`() {
        val json = """
        {"cid":1,"className":"C","classTimeInfoList":[
          {"day":"星期五","start":"08:00:00","end":"09:35:00",
           "startWeek":"10","endWeek":"16","location":"L"}
        ]}
        """.trimIndent()

        val slot = CourseParser.parse(listOf(obj(json))).single().slots.single()

        // ★ 契約文檔 §4.3：startWeek/endWeek 是**字串**，且 endWeek 實測出現過兩位數。
        //   用 toInt() 硬轉會在這裡炸，所以必須是寬鬆讀取。
        assertEquals(10..16, slot.weekRange)
    }

    @Test
    fun `沒有時段的課程仍要保留`() {
        // 已選上但尚未排時段的課：學生要看到「有這門課」，不能整條消失。
        val json = """{"cid":7,"className":"無時段課"}"""

        val c = CourseParser.parse(listOf(obj(json))).single()

        assertEquals("無時段課", c.name)
        assertTrue("沒有時段不代表沒有課", c.slots.isEmpty())
        assertNull("沒有時段就沒有週次區間（不是 0..0）", c.weekRange)
    }

    @Test
    fun `缺 cid 或 className 的條目被丟掉`() {
        val json = """
        [
          {"className":"沒有 cid"},
          {"cid":2},
          {"cid":3,"className":"正常課"}
        ]
        """.trimIndent()

        val arr = Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonArray
        val courses = CourseParser.parse(arr.map { it as JsonObject })

        // 前兩條無法展示也無法去重，留著只會在課表上留一格空白。
        assertEquals(1, courses.size)
        assertEquals("正常課", courses.single().name)
    }

    @Test
    fun `星期解析相容中文與數字 且日與天都歸 7`() {
        fun day(d: String) = CourseSlot(
            day = d, start = "", end = "", startWeek = "", endWeek = "",
            location = "", classroomName = "", campusName = "", classroomSid = null,
        ).weekdayIndex

        assertEquals(1, day("星期一"))
        assertEquals(1, day("周一"))
        assertEquals(1, day("週一"))
        assertEquals(1, day("1"))
        assertEquals(6, day("星期六"))
        assertEquals(7, day("星期日"))
        assertEquals(7, day("星期天"))
        assertEquals(7, day("週日"))
        assertNull(day(""))
        assertNull(day("亂寫"))
    }

    @Test
    fun `地點優先取 location 沒有就退回 classroomName`() {
        fun loc(l: String, cn: String) = CourseSlot(
            day = "星期一", start = "", end = "", startWeek = "", endWeek = "",
            location = l, classroomName = cn, campusName = "", classroomSid = null,
        ).displayLocation

        assertEquals("教六310", loc("教六310", "310"))
        assertEquals("310", loc("", "310"))
    }

    @Test
    fun `缺欄位不會讓整張課表失敗`() {
        // 只有最小欄位：其餘都缺。寬進嚴出 —— 讀不到給空，不拋。
        val json = """{"cid":9,"className":"最小課"}"""

        val c = CourseParser.parse(listOf(obj(json))).single()

        assertEquals("", c.teacherName)
        assertEquals("", c.classNo)
        assertEquals("", c.collegeName)
        assertEquals("", c.nature)
    }
}
