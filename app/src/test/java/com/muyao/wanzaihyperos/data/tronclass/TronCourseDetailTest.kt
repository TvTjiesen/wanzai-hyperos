package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B12：課程詳情 / 出勤 / 成績的解析測試。
 *
 * 輸入全部是**真機探測到的原文片段**（見 `app/build/tron-shapes.txt`），
 * 不是照著我們的程式碼反推的 —— 那樣只能測出「我們跟我們自己一致」。
 */
class TronCourseDetailTest {

    private fun obj(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    // -----------------------------------------------------------------------
    // 課程詳情 + 大綱
    // -----------------------------------------------------------------------

    @Test
    fun `課程詳情取 display_name 與教師`() {
        val detail = TronCourseDetailParser.parse(
            obj(
                """
                {"id":67436,"name":"数据结构与算法","display_name":"数据结构与算法",
                 "course_code":"(2026-2027-1)-301386-J03062-1","subject_code":"301386",
                 "credit":"3.0","department":{"id":83,"name":"计算机与计算科学学院"},
                 "instructors":[{"id":9507,"name":"周勇"}],
                 "semester":{"code":"2026-1","name":"2026-2027第1学期"},
                 "start_date":"2026-09-14","classroom_schedule":null,
                 "course_outline":{"status":"empty","common_fields":[],"custom_fields":[]}}
                """.trimIndent(),
            ),
        )!!
        assertEquals(67436, detail.id)
        assertEquals("数据结构与算法", detail.name)
        assertEquals("3.0", detail.credit)
        assertEquals("计算机与计算科学学院", detail.department)
        assertEquals("周勇", detail.teachers)
        assertEquals("2026-2027第1学期", detail.semesterName)
        assertEquals("2026-09-14", detail.startDate)
    }

    @Test
    fun `大綱欄位在但內容空時 outlineFilled 為 false`() {
        // 真機實情：11 個欄位 title 都在、description 全是空字串。
        val detail = TronCourseDetailParser.parse(
            obj(
                """
                {"id":1,"name":"课","course_outline":{"status":"empty","common_fields":[
                  {"key":"objective","title":"教学目标","description":""},
                  {"key":"material","title":"基本教材","description":""}
                ],"custom_fields":[]}}
                """.trimIndent(),
            ),
        )!!
        assertEquals(2, detail.outline.size)
        assertFalse("描述全空時不算填過", detail.outlineFilled)
        assertTrue(detail.filledOutline.isEmpty())
    }

    @Test
    fun `大綱有內容時只取有填的欄位`() {
        val detail = TronCourseDetailParser.parse(
            obj(
                """
                {"id":1,"name":"课","course_outline":{"common_fields":[
                  {"title":"教学目标","description":"掌握链表"},
                  {"title":"基本教材","description":"   "},
                  {"title":"评分方式","description":"期末 60%"}
                ],"custom_fields":[{"title":"自定义","description":"附加"}]}}
                """.trimIndent(),
            ),
        )!!
        assertTrue(detail.outlineFilled)
        assertEquals(listOf("教学目标", "评分方式", "自定义"), detail.filledOutline.map { it.title })
        // 純空白不算填過（否則界面上會出現一個空行）。
        assertEquals(4, detail.outline.size)
    }

    @Test
    fun `缺 id 或課名回 null`() {
        assertNull(TronCourseDetailParser.parse(obj("""{"name":"课"}""")))
        assertNull(TronCourseDetailParser.parse(obj("""{"id":1}""")))
        assertNull(TronCourseDetailParser.parse(null))
    }

    // -----------------------------------------------------------------------
    // 出勤記錄
    // -----------------------------------------------------------------------

    @Test
    fun `出勤記錄解析真機樣本並轉成本地時間`() {
        val list = TronAttendanceParser.parseList(
            Json.parseToJsonElement(
                """
                {"rollcalls":[
                 {"rollcall_id":230462,"rollcall_time":"2026-09-15T01:39:34Z",
                  "source":"qr","student_status":"on_call","title":"2026.09.15 09:39","type":"qr_rollcall"},
                 {"rollcall_id":235925,"rollcall_time":"2026-09-24T06:22:50Z",
                  "source":"number","student_status":"on_call","title":"2026.09.24 14:22","type":"another"}
                ]}
                """.trimIndent(),
            ),
        )
        assertEquals(2, list.size)
        assertEquals(230462, list[0].rollcallId)
        assertEquals("扫码", list[0].sourceLabel)
        assertEquals("已到", list[0].statusLabel)
        assertTrue(list[0].attended)
        assertEquals("数字", list[1].sourceLabel)
        // UTC 01:39 → 本地（+08）09:39。**不能照原樣顯示**，那會早 8 小時。
        assertTrue("本地時間應為 09:39，實際 ${list[0].timeLocal}", list[0].timeLocal.endsWith("09:39"))
    }

    @Test
    fun `出勤狀態認不出來時原樣顯示且不算到了`() {
        val list = TronAttendanceParser.parseList(
            Json.parseToJsonElement(
                """[{"rollcall_id":1,"source":"weird_source","student_status":"something_new"}]""",
            ),
        )
        assertEquals(1, list.size)
        assertEquals("something_new", list[0].statusLabel)
        assertEquals("weird_source", list[0].sourceLabel)
        assertFalse(list[0].attended)
    }

    @Test
    fun `出勤缺 rollcall_id 的條目丟掉`() {
        val list = TronAttendanceParser.parseList(
            Json.parseToJsonElement("""[{"source":"qr"},{"rollcall_id":9}]"""),
        )
        assertEquals(1, list.size)
        assertEquals(9, list[0].rollcallId)
    }

    // -----------------------------------------------------------------------
    // 成績
    // -----------------------------------------------------------------------

    @Test
    fun `成績未發布時 summary 是尚未公布而不是 0`() {
        // 真機實情：total_score 0.0 而 instructor_score_time 是 null。
        val score = TronScoreParser.parse(
            obj("""{"self_score":{"exceptional_case":"none","instructor_score_time":null,
                    "raw_score":"0.0","total_score":"0.0"}}"""),
            obj("""{"score_percentage_left":100,"score_percentage_total":100,"unpublished_percentage":0.0}"""),
        )!!
        assertFalse(score.published)
        assertEquals("尚未公布", score.summary)
        assertEquals(0, score.gradedPercentage)
        assertEquals("0.0", score.unpublishedPercentage)
    }

    @Test
    fun `成績已發布時顯示總分`() {
        val score = TronScoreParser.parse(
            obj("""{"self_score":{"instructor_score_time":"2026-01-10T00:00:00Z",
                    "raw_score":"88.5","total_score":"92.0"}}"""),
            obj("""{"score_percentage_left":20,"score_percentage_total":100,"unpublished_percentage":0.0}"""),
        )!!
        assertTrue(score.published)
        assertEquals("92.0", score.summary)
        assertEquals(80, score.gradedPercentage)
    }

    @Test
    fun `成績佔比缺一項時 gradedPercentage 回 null 而不是 0`() {
        val score = TronScoreParser.parse(
            obj("""{"self_score":{"instructor_score_time":"x","total_score":"1"}}"""),
            null,
        )!!
        assertNull("取不到佔比就是未知，不是 0", score.gradedPercentage)
    }

    @Test
    fun `出勤彙總不把 score 當成出勤得分`() {
        // 真機實情：5 次全到，score 仍是 "0" —— 所以模型刻意不收這個欄位。
        val summary = TronRollcallScoreParser.parse(
            obj(
                """{"last_rollcall_time":"2026-09-29T01:46:23Z","public":null,
                    "rollcall_count":5,"rollcall_times":5,"score":"0",
                    "score_percentage":"0.0","scored":true}""",
            ),
        )!!
        assertEquals("5 / 5 次", summary.summary)
        assertTrue(summary.lastRollcallLocal.isNotBlank())
    }

    @Test
    fun `出勤彙總的回應不像彙總時回 null`() {
        assertNull(TronRollcallScoreParser.parse(obj("""{"message":"未找到资源"}""")))
        assertNull(TronRollcallScoreParser.parse(null))
    }
}

/** `/api/my-courses` 的解析（B12 從「最近訪問」改成全量之後的契約）。 */
class TronMyCourseParserTest {

    private fun obj(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `從 my-courses 的 courses 外層解析`() {
        val list = TronCourseParser.parse(
            obj(
                """
                {"courses":[{
                  "id":67436,"name":"数据结构与算法","course_code":"(2026-2027-1)-301386-J03062-1",
                  "credit":"3.0",
                  "course_attributes":{"teaching_class_name":"周二第3-4节第1,3周"},
                  "department":{"name":"计算机与计算科学学院"},
                  "instructors":[{"id":9507,"name":"周勇"}],
                  "semester":{"code":"2026-1","name":"2026-2027第1学期","real_name":"第1学期"}
                }]}
                """.trimIndent(),
            ),
        )
        assertEquals(1, list.size)
        val c = list[0]
        assertEquals("数据结构与算法", c.name)
        assertEquals("3.0", c.credit)
        assertEquals("周勇", c.teachers)
        assertEquals("周二第3-4节第1,3周", c.teachingClass)
        // 顯示用 `name`（跨學期清單裡「第1学期」會有多條同名）。
        assertEquals("2026-2027第1学期", c.semesterName)
        assertTrue(c.subtitle.contains("周勇"))
        assertTrue(c.subtitle.contains("3.0 学分"))
    }

    @Test
    fun `仍相容舊的 visited_courses 外層`() {
        val list = TronCourseParser.parse(
            obj("""{"visited_courses":[{"id":1,"name":"课","course_attributes":{"teaching_class_name":"周一"}}]}"""),
        )
        assertEquals(1, list.size)
        assertEquals("周一", list[0].teachingClass)
    }

    @Test
    fun `多個教師用頓號連接、缺 id 的丟掉`() {
        val list = TronCourseParser.parse(
            obj(
                """{"courses":[
                    {"id":1,"name":"课","instructors":[{"name":"甲"},{"name":"乙"},{"name":null}]},
                    {"name":"没有 id 的课"}
                ]}""",
            ),
        )
        assertEquals(1, list.size)
        assertEquals("甲、乙", list[0].teachers)
    }
}
