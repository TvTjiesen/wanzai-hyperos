package com.muyao.wanzaihyperos.data.szty

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数智体育（`ty.hzcu.edu.cn`）的解析测试。
 *
 * 形状逐字抄自 2026-10-31 用真 token 打在线接口拿到的 `obj`，只把姓名/学号换成合成值
 *（本仓会公开，真实 PII 不进测试）。
 */
class SztyParserTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun obj(text: String): JsonElement =
        (json("""{"obj":$text}""") as kotlinx.serialization.json.JsonObject)["obj"]!!

    // ── 体测 ────────────────────────────────────────────────────────────

    /**
     * ★ 这一条是「空串 = 未测，不是 0」的守卫。
     *
     * 真响应里 `50米` 与 `1000米` 的 `data` 就是空串 —— 学校还没录，不是他跑了 0 秒。
     * 把它画成 0 等于替学校编了一个成绩。
     */
    @Test
    fun `体测里空串读成未测而不是零`() {
        val years = SztyParsers.fitnessYears(
            obj(
                """[{"year":2026,"seasonName":"2026","grade":"缺项","allScore":"缺项","level":"",
                   "scoreData":[{"name":"50米","data":"","score":0.0},
                               {"name":"肺活量","data":"3744.00","score":15.0}]}]""",
            ),
        )
        assertEquals(1, years.size)
        val year = years[0]
        assertEquals("2026", year.year)
        // grade 原样留着「缺项」—— 那是学校还没录完，不是我们没取到。
        assertEquals("缺项", year.gradeText)
        // allScore 是「缺项」这三个字，不是数字 ⇒ 总评分数必须是 null（不是 0.0）。
        assertNull(year.score)
        assertEquals(2, year.items.size)
        assertNull("空串必须读成 null（未测），读成 0 就是编成绩", year.items[0].value)
        assertEquals("3744.00", year.items[1].value)
    }

    @Test
    fun `体测有总评时按数字读`() {
        val years = SztyParsers.fitnessYears(
            obj("""[{"year":2025,"grade":"80.0","allScore":"80.0","level":"良好","scoreData":[]}]"""),
        )
        assertEquals(80.0, years[0].score!!, 1e-9)
        assertEquals("良好", years[0].level)
    }

    @Test
    fun `体测的 obj 不是数组时给空表而不是崩`() {
        assertTrue(SztyParsers.fitnessYears(obj("null")).isEmpty())
        assertTrue(SztyParsers.fitnessYears(obj("""{"a":1}""")).isEmpty())
    }

    // ── 校园跑 ──────────────────────────────────────────────────────────

    /**
     * ★ 「跑步」与「总目标」是**两条不同的数**，不能相加。
     *
     * 实测：跑步 effectiveDistance 16.78 / targetDistance 120 / number 4；
     * 总目标 effectiveDistance 168.28 / number 54（含其它运动类型）。
     */
    @Test
    fun `校园跑把跑步与总目标拆成两条不许相加`() {
        val run = SztyParsers.run(
            obj(
                """[{"info":{"eventName":"总目标"},"effectiveDistance":168.28,"targetDistance":0.0,
                   "number":54,"targetNumber":224},
                   {"info":{"eventName":"跑步"},"effectiveDistance":16.78,"targetDistance":120.0,
                   "number":4,"targetNumber":0}]""",
            ),
        )!!
        assertEquals(16.78, run.runKm!!, 1e-9)
        assertEquals(120.0, run.runTargetKm!!, 1e-9)
        assertEquals(4, run.runCount)
        assertEquals(168.28, run.totalKm!!, 1e-9)
        assertEquals(54, run.totalCount)
        assertEquals(224, run.totalTargetCount)
    }

    @Test
    fun `总目标那条的 targetDistance 是 0 要读成没有目标`() {
        val run = SztyParsers.run(
            obj("""[{"info":{"eventName":"总目标"},"effectiveDistance":168.28,"targetDistance":0.0}]"""),
        )!!
        assertNull("targetDistance 0 是「这条没有目标」，不是「目标 0 公里」", run.runTargetKm)
        assertNull(run.runKm)
    }

    @Test
    fun `两条汇总都没有时返回 null 而不是零公里的跑步`() {
        assertNull(SztyParsers.run(obj("""[{"info":{"eventName":"其它"}}]""")))
        assertNull(SztyParsers.run(obj("[]")))
        assertNull(SztyParsers.run(obj("null")))
    }

    @Test
    fun `没有 info 包裹的 eventName 也认得`() {
        val run = SztyParsers.run(obj("""[{"eventName":"跑步","effectiveDistance":3.5}]"""))!!
        assertEquals(3.5, run.runKm!!, 1e-9)
    }

    // ── 学期与体育课 ────────────────────────────────────────────────────

    /**
     * ★ 学期号取 **termId 最大**，不信数组顺序。
     *
     * 交接包的 API_NOTES 写的是 `id`，真机实测字段名是 `termId` —— 两个都认，
     * 因为写死任一个都可能在换学期那天静默退回 null（体育课与签到两块一起消失还不报错）。
     */
    @Test
    fun `学期号取最大的而不是数组第一个`() {
        val id = SztyParsers.currentTermId(
            obj(
                """[{"termId":13,"termName":"2024-2025学年"},
                   {"termId":15,"termName":"2026-2027学年,秋冬学期"},
                   {"termId":14,"termName":"2025-2026学年"}]""",
            ),
        )
        assertEquals(15, id)
    }

    @Test
    fun `学期号写在 id 里也认`() {
        assertEquals(15, SztyParsers.currentTermId(obj("""[{"id":15},{"id":14}]""")))
    }

    @Test
    fun `没有学期时返回 null 让界面说没查`() {
        assertNull(SztyParsers.currentTermId(obj("[]")))
        assertNull(SztyParsers.currentTermId(obj("null")))
    }

    @Test
    fun `体育课没有 classId 就当没有这门课`() {
        assertNull(SztyParsers.peClass(obj("""[{"name":"匹克球","week":"星期五"}]""")))
        val pe = SztyParsers.peClass(
            obj(
                """[{"classId":3062,"name":"匹克球Ⅰ(周五第3,4节)","week":"星期五",
                   "jc":"3,4","address":"西网球场"}]""",
            ),
        )!!
        assertEquals(3062, pe.classId)
        assertEquals("匹克球Ⅰ(周五第3,4节)", pe.name)
        assertEquals("3,4", pe.periods)
        assertEquals("西网球场", pe.address)
    }

    // ── 签到 ────────────────────────────────────────────────────────────

    @Test
    fun `签到记录按服务端给的顺序读出来`() {
        val records = SztyParsers.attendance(
            obj(
                """[{"name":"2026-09-18 第1次课 考勤","signInType":"已签到","signClassStageId":23740},
                   {"name":"2026-10-09 第3次课 考勤","signInType":"请假","signClassStageId":24246}]""",
            ),
        )
        assertEquals(2, records.size)
        assertEquals("2026-09-18 第1次课 考勤", records[0].title)
        assertEquals("已签到", records[0].status)
        assertEquals("请假", records[1].status)
    }

    @Test
    fun `签到汇总用服务端自己写的那句话`() {
        val summary = SztyParsers.attendanceSummary(
            obj(
                """{"normal":4,"late":0,"leave":2,"absenteeism":0,
                   "headStatistics":"旷课0次,请假2次,未签到0次,已签到4次,迟到0次,早退0次"}""",
            ),
        )!!
        assertEquals(4, summary.signed)
        assertEquals(2, summary.leave)
        assertEquals("旷课0次,请假2次,未签到0次,已签到4次,迟到0次,早退0次", summary.headStatistics)
    }

    @Test
    fun `签到汇总为 null 时不当成零次`() {
        assertNull(SztyParsers.attendanceSummary(obj("null")))
    }
}