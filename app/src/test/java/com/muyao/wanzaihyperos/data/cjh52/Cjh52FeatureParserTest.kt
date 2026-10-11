package com.muyao.wanzaihyperos.data.cjh52

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B3 剩餘三項的解析驗證。
 *
 * 樣本形狀逐字取自真機取證（`build/three.json`），**值全是編造的**。
 */
class Cjh52FeatureParserTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // ---- 日常通知 ---------------------------------------------------------

    @Test
    fun `通知条目解析出核心字段`() {
        val json = """
        {
          "id": "0123456789abcdef01234567",
          "title": "範例通知標題",
          "username": "張三",
          "detail": "這是編造的通知正文。",
          "createTime": 1757000000000,
          "pubTime": 1757000001000,
          "level": "普通"
        }
        """.trimIndent()

        val n = Cjh52NoticeParser.parse(listOf(obj(json))).single()

        assertEquals("0123456789abcdef01234567", n.id)
        assertEquals("範例通知標題", n.title)
        assertEquals("張三", n.publisher)
        assertEquals(1757000000000L, n.createdAt)
        assertEquals(1757000001000L, n.publishedAt)
    }

    @Test
    fun `通知时间戳用 Long 读 不会溢出成 1970`() {
        // ★ 這條盯的是實際踩過的坑：毫秒時間戳是 13 位，用 Int 讀會**靜默溢出**，
        //   界面上表現成「時間顯示成 1970 年」。
        val json = """{"id":"x","title":"t","pubTime":1757000001000}"""

        val n = Cjh52NoticeParser.parse(listOf(obj(json))).single()

        assertEquals(1757000001000L, n.publishedAt)
        // 年份必須是 2025 左右，不是 1970。
        val year = java.util.Calendar.getInstance().apply { timeInMillis = n.publishedAt!! }.get(java.util.Calendar.YEAR)
        assertTrue("時間戳讀錯了：$year", year > 2000)
    }

    @Test
    fun `通知缺 id 被丢掉`() {
        val arr = Json.parseToJsonElement(
            """[{"title":"没有 id"},{"id":"ok","title":"正常"}]""",
        ) as kotlinx.serialization.json.JsonArray

        val list = Cjh52NoticeParser.parse(arr.map { it as JsonObject })

        assertEquals(1, list.size)
        assertEquals("ok", list.single().id)
    }

    @Test
    fun `通知排序优先用发布时间`() {
        val onlyCreated = Cjh52NoticeParser.parse(
            listOf(obj("""{"id":"a","title":"t","createTime":1000}""")),
        ).single()
        assertEquals(1000L, onlyCreated.sortKey)
    }

    // ---- 我的考試 ---------------------------------------------------------

    @Test
    fun `考试条目不编造字段名`() {
        // 取證時沒有考試，所以欄位名未知。模型只保留原始欄位袋，
        // **不猜** —— PLAN 明文要求「未核實接口不編造資料」。
        val raw = obj("""{"someUnknownKey":"v","another":1}""")

        val exam = Cjh52ExamParser.parse(listOf(raw)).single()

        assertNull("認不出來的欄位不該硬當標題", exam.title)
        assertEquals(2, exam.raw.keys.size)
    }

    @Test
    fun `考试能认出常见标题字段`() {
        assertEquals("期末", Cjh52ExamParser.parse(listOf(obj("""{"name":"期末"}"""))).single().title)
        assertEquals("期末", Cjh52ExamParser.parse(listOf(obj("""{"title":"期末"}"""))).single().title)
        assertEquals("期末", Cjh52ExamParser.parse(listOf(obj("""{"examName":"期末"}"""))).single().title)
    }

    // ---- 我的成長 ---------------------------------------------------------

    @Test
    fun `KQA 三维分解析`() {
        val kqa = Cjh52GrowthParser.parseKqaScore(obj("""{"Q":7.0,"GY":2.44,"K":0}"""))!!

        assertEquals(7.0, kqa.q!!, 0.001)
        assertEquals(2.44, kqa.gy!!, 0.001)
        assertEquals(0.0, kqa.k!!, 0.001)
        assertEquals(false, kqa.isEmpty)
    }

    @Test
    fun `KQA 全缺时判为空`() {
        val kqa = Cjh52GrowthParser.parseKqaScore(obj("""{}"""))!!
        assertTrue(kqa.isEmpty)
    }

    @Test
    fun `学分进度比例：分母缺失时不给比例`() {
        // ★ 這條盯的是「不編造 0%」：拿不到總數時必須回 null，
        //   而不是顯示 0%（那等於告訴使用者「你一門都沒修」）。
        val noTotal = Cjh52GrowthParser.parseCreditProgress(
            obj("""{"compulsoryCourseNumber":14}"""),
        )!!
        assertNull(noTotal.compulsoryRatio)

        val zeroTotal = Cjh52GrowthParser.parseCreditProgress(
            obj("""{"compulsoryCourseNumber":14,"compulsoryCourseTotal":0}"""),
        )!!
        assertNull("分母為 0 時不能除", zeroTotal.compulsoryRatio)

        val normal = Cjh52GrowthParser.parseCreditProgress(
            obj("""{"compulsoryCourseNumber":14,"compulsoryCourseTotal":43}"""),
        )!!
        assertEquals(14.0 / 43.0, normal.compulsoryRatio!!, 0.001)
    }

    @Test
    fun `体测成绩解析`() {
        val json = """
        [{"score":85.5,"name":"第一学年","schoolYear":"2025-2026","comprehensiveScore":90.0,"id":"abc"}]
        """.trimIndent()
        val arr = Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonArray

        val list = Cjh52GrowthParser.parsePhysique(arr.map { it as JsonObject })

        assertEquals(1, list.size)
        assertEquals("第一学年", list.single().name)
        assertEquals(85.5, list.single().score!!, 0.001)
    }

    @Test
    fun `行为记录按月解析`() {
        val data = obj("""{"min":0,"max":10,"results":[{"count":3,"year":"2025","month":"09","totalScore":6.0}]}""")

        val months = Cjh52GrowthParser.parseActivityMonths(data)

        assertEquals(1, months.size)
        assertEquals("2025", months.single().year)
        assertEquals("09", months.single().month)
        assertEquals(3, months.single().count)
    }

    @Test
    fun `行为记录缺 year 或 month 被丢掉`() {
        val data = obj("""{"results":[{"count":1,"year":"2025"},{"count":2,"month":"09"},{"count":3,"year":"2025","month":"10"}]}""")

        val months = Cjh52GrowthParser.parseActivityMonths(data)

        assertEquals(1, months.size)
        assertEquals("10", months.single().month)
    }

    @Test
    fun `缺字段不会让整条失败`() {
        val n = Cjh52NoticeParser.parse(listOf(obj("""{"id":"only-id"}"""))).single()
        assertEquals("", n.title)
        assertEquals("", n.detail)
        assertNull(n.publishedAt)
    }
}
