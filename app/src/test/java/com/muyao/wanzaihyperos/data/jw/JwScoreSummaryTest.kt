package com.muyao.wanzaihyperos.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成績匯總：已修學分 / 加權平均分 / 加權績點。
 *
 * 為什麼值得釘：這三個數字是**算出來的**，算錯不會有人報錯 ——
 * 學生只會看到一個偏低的均分，然後以為自己考砸了。
 * 特別是等第制（优秀/良好）與學分缺失這兩種情況：
 * 把讀不出來的當 0 分參與加權，就是把「不知道」說成「不及格」。
 */
class JwScoreSummaryTest {

    private fun score(
        name: String,
        score: String = "",
        raw: String = "",
        gp: String = "",
        credit: String = "",
    ) = JwScore(
        courseName = name,
        score = score,
        rawScore = raw,
        gradePoint = gp,
        credit = credit,
        teachers = "",
        college = "",
        publishedAt = "",
        className = "",
    )

    @Test
    fun `学分按数字加起来，按学分加权算均分`() {
        val s = JwScoreSummarizer.summarize(
            listOf(
                score("线性代数", score = "90", credit = "3"),
                score("大学物理", score = "80", credit = "2"),
            ),
        )
        assertEquals(5.0, s.credits!!, 1e-9)
        assertEquals(2, s.creditCount)
        // (90*3 + 80*2) / 5 = 86
        assertEquals(86.0, s.weightedScore!!, 1e-9)
    }

    @Test
    fun `等第制不参与加权，但要数出来告诉用户`() {
        val s = JwScoreSummarizer.summarize(
            listOf(
                score("线性代数", score = "90", credit = "3"),
                score("形势与政策", score = "优秀", credit = "1"),
            ),
        )
        // 学分仍算 4（这门课确实修了）
        assertEquals(4.0, s.credits!!, 1e-9)
        // 均分只用线性代数：90
        assertEquals(90.0, s.weightedScore!!, 1e-9)
        assertEquals(1, s.excludedFromAverage)
    }

    @Test
    fun `等第制有百分制成绩时按百分制参与加权`() {
        val s = JwScoreSummarizer.summarize(
            listOf(score("形势与政策", score = "优秀", raw = "95", credit = "1")),
        )
        assertEquals(95.0, s.weightedScore!!, 1e-9)
        assertEquals(0, s.excludedFromAverage)
    }

    @Test
    fun `学分缺失的课既不进分母也不进分子`() {
        val s = JwScoreSummarizer.summarize(
            listOf(
                score("线性代数", score = "90", credit = "3"),
                score("没有学分的课", score = "0"),
            ),
        )
        assertEquals(3.0, s.credits!!, 1e-9)
        assertEquals(1, s.creditCount)
        // 那门 0 分的不许把均分拉下来
        assertEquals(90.0, s.weightedScore!!, 1e-9)
        assertEquals(1, s.excludedFromAverage)
    }

    @Test
    fun `绩点也按学分加权`() {
        val s = JwScoreSummarizer.summarize(
            listOf(
                score("线性代数", score = "90", gp = "4.0", credit = "3"),
                score("大学物理", score = "80", gp = "3.0", credit = "1"),
            ),
        )
        // (4*3 + 3*1) / 4 = 3.75
        assertEquals(3.75, s.weightedGradePoint!!, 1e-9)
    }

    @Test
    fun `一条都算不出来时三个数字都是 null 而不是 0`() {
        val s = JwScoreSummarizer.summarize(emptyList())
        assertNull(s.credits)
        assertNull(s.weightedScore)
        assertNull(s.weightedGradePoint)
        assertTrue("空清单不该画汇总卡", s.isEmpty)
    }

    @Test
    fun `全是没有学分的课也不画汇总卡`() {
        val s = JwScoreSummarizer.summarize(listOf(score("某课", score = "90")))
        assertNull(s.credits)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `超过一百分或负数的学分不算学分`() {
        assertNull(JwScoreSummarizer.number("-1"))
        assertNull(JwScoreSummarizer.number("120"))
        assertNull(JwScoreSummarizer.number("优秀"))
        assertEquals(3.5, JwScoreSummarizer.number(" 3.5 ")!!, 1e-9)
    }
}
