package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * 作業解析（含**截止時間的時區**）。
 *
 * 形狀取自活體探測的 `/api/courses/{id}/homework-activities`
 *（逐字對照源碼的 `getHomeworkList`），值全是編造的。
 */
class TronHomeworkParserTest {

    private lateinit var original: TimeZone

    @Before
    fun fixTimezone() {
        original = TimeZone.getDefault()
        // 固定東八區，免得測試結果取決於跑測試那台機器的時區。
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun restore() {
        TimeZone.setDefault(original)
    }

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private val sample = """
    {
      "homework_activities": [
        {
          "id": 61455,
          "deadline": "2026-10-14T15:30:00Z",
          "end_time": "2026-10-14T15:30:00Z",
          "is_closed": 0,
          "is_in_progress": 1,
          "data": {
            "homework_type": "individual",
            "description": "示例要求：交一份实验报告",
            "submit_times": 3,
            "score_percentage": "20"
          }
        }
      ],
      "page": 1, "page_size": 20, "pages": 1, "total": 1
    }
    """.trimIndent()

    @Test
    fun `解析作业核心字段`() {
        val hw = TronHomeworkParser.parseList(obj(sample)).single()
        assertEquals(61455, hw.id)
        assertEquals("individual", hw.homeworkType)
        assertEquals("示例要求：交一份实验报告", hw.description)
        assertEquals(3, hw.submitTimes)
        assertEquals("20", hw.scorePercentage)
        assertTrue(hw.inProgress)
    }

    @Test
    fun `截止时间按 UTC 转本地`() {
        // 與待辦同一條教訓：deadline 帶 Z 是 UTC，照原樣顯示會早 8 小時。
        val hw = TronHomeworkParser.parseList(obj(sample)).single()
        assertEquals("2026-10-14 23:30", hw.deadlineLocal)
    }

    @Test
    fun `缺 deadline 时显示破折号`() {
        val hw = TronHomeworkParser.parseList(
            obj("""{"homework_activities":[{"id":1}]}"""),
        ).single()
        assertEquals("—", hw.deadlineLocal)
    }

    @Test
    fun `缺 id 的作业丢掉`() {
        val list = TronHomeworkParser.parseList(
            obj("""{"homework_activities":[{"deadline":"x"},{"id":9}]}"""),
        )
        assertEquals(1, list.size)
        assertEquals(9, list.single().id)
    }

    @Test
    fun `data 缺失时其余字段照样可用`() {
        // 服務端哪天不給 data，作業本身（id/截止/狀態）仍然要顯示得出來。
        val hw = TronHomeworkParser.parseList(
            obj("""{"homework_activities":[{"id":5,"deadline":"2026-10-14T15:30:00Z"}]}"""),
        ).single()
        assertEquals(5, hw.id)
        assertEquals("2026-10-14 23:30", hw.deadlineLocal)
        assertEquals("", hw.description)
        assertNull(hw.submitTimes)
    }

    @Test
    fun `空列表与外层缺失都回空`() {
        assertTrue(TronHomeworkParser.parseList(obj("""{"homework_activities":[]}""")).isEmpty())
        assertTrue(TronHomeworkParser.parseList(obj("""{}""")).isEmpty())
        assertTrue(TronHomeworkParser.parseList(null).isEmpty())
    }
}
