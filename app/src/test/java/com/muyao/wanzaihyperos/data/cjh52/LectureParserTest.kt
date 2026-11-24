package com.muyao.wanzaihyperos.data.cjh52

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B16：愛講座（CJH52）解析測試。
 *
 * 輸入逐字抄自真機探測原文（`docs/B16-CJH52-PROBE.txt`，
 * `total: 2541` 那次的第一頁）。
 */
class LectureParserTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // 真機原文（節錄要顯示的欄位，值未改動）。
    private val real = """
        {"code":200,"msg":"成功","data":{"total":2541,"pageSize":10,"pageNum":1,"data":[
         {"speakerName":"吕新业","appliedUnit":"国土空间规划学院","normalRegisteredCount":4,
          "type":"国土空间规划学院学术讲座","head":"654","signedCount":0,
          "isRegistering":true,"startTime":1792204200000,"id":"6aca062b46d7b90c85d083c7",
          "state":"published","registrationDeadline":1792202400000,
          "speakerJob":"研究员","address":"科技楼714会议室","standbyRegisteredCount":0,
          "registrationNumber":30,"scored":false,
          "name":"以积累沉淀破项目申报之局——国家社科基金项目申请经历分享","endTime":1792211400000}
        ]}}
    """.trimIndent()

    @Test
    fun `解析講座並把毫秒時間轉成本地`() {
        val list = LectureParser.parse(obj(real))
        assertEquals(1, list.size)
        val l = list[0]
        assertEquals("6aca062b46d7b90c85d083c7", l.id)
        assertTrue(l.title.startsWith("以积累沉淀破项目申报之局"))
        assertEquals("吕新业", l.speaker)
        assertEquals("研究员", l.speakerJob)
        assertEquals("国土空间规划学院", l.unit)
        assertEquals("科技楼714会议室", l.address)
        // ★ 毫秒 → 本地時間。照原樣顯示會是 1792204200000 這串數字。
        assertTrue("開始時間應為可讀日期，實際 ${l.startLocal}", l.startLocal.startsWith("20"))
        assertTrue(l.startLocal.contains("-"))
        assertTrue(l.startLocal.contains(":"))
        assertTrue(l.deadlineLocal.isNotBlank())
    }

    @Test
    fun `名額與已報名分開顯示`() {
        val l = LectureParser.parse(obj(real))[0]
        assertEquals(30, l.capacity)
        assertEquals(4, l.registered)
        // 顯示成「4 / 30」而不是只說「未滿」——
        // 使用者要知道自己大概排第幾、要不要早點報。
        assertEquals("4 / 30", l.capacityLabel)
        assertTrue(l.canRegister)
        assertEquals("报名中", l.statusLabel)
    }

    @Test
    fun `名額為 0 或未知時說不限而不是 0 個名額`() {
        // 顯示「0 個名額」會讓人以為不能報。
        val unlimited = LectureParser.parse(
            obj(
                """{"data":{"data":[{"id":"a","name":"n","isRegistering":true,
                     "registrationNumber":0,"normalRegisteredCount":7}]}}""",
            ),
        )[0]
        assertEquals("已报名 7", unlimited.capacityLabel)
        assertTrue("不限名額應該可以報名", unlimited.canRegister)

        val unknown = LectureParser.parse(
            obj("""{"data":{"data":[{"id":"a","name":"n"}]}}"""),
        )[0]
        assertEquals("名额未知", unknown.capacityLabel)
        assertNull(unknown.capacity)
    }

    @Test
    fun `名額滿了就不能報名`() {
        val full = LectureParser.parse(
            obj(
                """{"data":{"data":[{"id":"a","name":"n","isRegistering":true,
                     "registrationNumber":30,"normalRegisteredCount":30}]}}""",
            ),
        )[0]
        assertFalse(full.canRegister)
        assertEquals("已满", full.statusLabel)
    }

    @Test
    fun `列表接口的 signedCount 未验证为个人签到状态`() {
        // 真机契约只记录到 signedCount 字段及样本值 0，无法证明其为当前用户状态；
        // 任何正计数也不得让全部场次错误显示「已签到」。
        for (signedCount in listOf("0", "1", "25")) {
            val lecture = LectureParser.parse(
                obj("""{"data":{"data":[{"id":"a","name":"n","isRegistering":true,
                    "registrationNumber":30,"normalRegisteredCount":1,"signedCount":$signedCount}]}}"""),
            )[0]
            assertNull(lecture.signed)
            assertEquals("报名中", lecture.statusLabel)
            assertFalse(lecture.statusLabel.contains("签到"))
        }

        val missing = LectureParser.parse(obj("""{"data":{"data":[{"id":"b","name":"n"}]}}"""))[0]
        assertNull(missing.signed)
        assertEquals("已结束报名", missing.statusLabel)
    }

    @Test
    fun `報名已截止時說已結束報名`() {
        val closed = LectureParser.parse(
            obj("""{"data":{"data":[{"id":"a","name":"n","isRegistering":false}]}}"""),
        )[0]
        assertFalse(closed.canRegister)
        assertEquals("已结束报名", closed.statusLabel)
    }

    @Test
    fun `缺 id 或講題的條目丟掉`() {
        val list = LectureParser.parse(
            obj("""{"data":{"data":[{"name":"没有 id"},{"id":"x"},{"id":"y","name":"好的"}]}}"""),
        )
        assertEquals(1, list.size)
        assertEquals("好的", list[0].title)
    }

    @Test
    fun `外層不對時回空清單而不是崩掉`() {
        assertEquals(0, LectureParser.parse(obj("""{"code":401,"msg":"没有权限访问接口"}""")).size)
        assertEquals(0, LectureParser.parse(obj("""{"data":{"data":[]}}""")).size)
        assertEquals(0, LectureParser.parse(null).size)
    }

    @Test
    fun `毫秒為 null 或 0 時時間顯示為空字串`() {
        // 空字串讓界面能判斷「不顯示這一行」，而不是顯示 1970 年。
        assertEquals("", formatCjh52Time(null))
        assertEquals("", formatCjh52Time(0L))
        assertEquals("", formatCjh52Time(-1L))
        assertTrue(formatCjh52Time(1792204200000L).isNotBlank())
    }
}
