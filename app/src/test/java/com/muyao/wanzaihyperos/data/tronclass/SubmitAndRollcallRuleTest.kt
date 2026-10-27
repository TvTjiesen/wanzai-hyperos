package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.todo.TodoSource
import com.muyao.wanzaihyperos.data.todo.toTodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提交與簽到兩條「**能不能點**」的規則。
 *
 * 為什麼要單獨測這些規則而不是只測解析：
 * 這兩個地方都是**寫操作**的閘門。閘門開錯的代價不是界面難看，
 * 而是「他以为交了其实没交」或「重复签到送出两笔」。
 */
class SubmitAndRollcallRuleTest {

    private fun homework(
        id: Int = 1,
        closed: Boolean = false,
        submitTimes: Int? = null,
        unlimited: Boolean = true,
        group: Boolean = false,
    ) = TronHomework(
        id = id,
        title = "第一次作业",
        deadline = "",
        endTime = "",
        closed = closed,
        inProgress = !closed,
        homeworkType = "homework",
        description = "",
        submitTimes = submitTimes,
        scorePercentage = "10",
        unlimitedSubmits = unlimited,
        submitByGroup = group,
        allowRetract = false,
    )

    private fun rollcall(
        source: String,
        status: String = "in_progress",
        expired: Boolean = false,
        myStatus: String = "",
    ) = TronRollcall(
        id = 7,
        courseId = 3,
        title = "课上的签到",
        source = source,
        status = status,
        expired = expired,
        rollcallTime = "",
        endTime = "",
        numberCode = "",
        myStatus = myStatus,
    )

    // ── 可交次数：三种说法不能混 ────────────────────────────────────────

    @Test
    fun `不限次数时不看 submit_times`() {
        val hw = homework(submitTimes = 3, unlimited = true)
        assertEquals("不限次数", hw.submitLimitLabel)
        assertTrue(hw.acceptsAnotherSubmission(submittedCount = 9))
    }

    @Test
    fun `有上限时到顶就不让再交`() {
        val hw = homework(submitTimes = 2, unlimited = false)
        assertEquals("可交 2 次", hw.submitLimitLabel)
        assertTrue(hw.acceptsAnotherSubmission(1))
        assertFalse(hw.acceptsAnotherSubmission(2))
    }

    @Test
    fun `上限不知道时不断言「不能交」`() {
        // 「不知道」→ 放行，让服务端裁决并把原话显示出来。
        // 反过来（当作 0 次）会打死按钮，而真实情况多半只是这个键没返回。
        val hw = homework(submitTimes = null, unlimited = false)
        assertEquals("次数未知", hw.submitLimitLabel)
        assertTrue(hw.acceptsAnotherSubmission(0))
        assertTrue(hw.acceptsAnotherSubmission(5))
    }

    // ── 详情的闸门 ──────────────────────────────────────────────────────

    private fun detail(
        hw: TronHomework?,
        listFailed: Boolean = false,
        submissions: List<TronSubmission> = emptyList(),
    ) = HomeworkDetail(
        courseId = 10,
        courseName = "计算机系统",
        title = "第一次作业",
        homework = hw,
        listFailed = listFailed,
        myStatus = null,
        submissions = submissions,
        submissionsKnown = true,
        userId = 42,
    )

    @Test
    fun `要求没取到时不给提交，并说清是哪一支失败`() {
        val blocked = detail(hw = null, listFailed = true)
        assertNotNull(blocked.submitBlockReason)
        assertTrue(blocked.submitBlockReason!!.contains("没取到"))
        assertFalse(blocked.submittable)
    }

    @Test
    fun `已关闭的作业不给提交`() {
        assertTrue(detail(hw = homework(closed = true)).submitBlockReason!!.contains("关闭"))
    }

    @Test
    fun `次数到顶时理由里带着数字`() {
        val submitted = listOf(
            TronSubmission(5, "甲", "", draft = false, latest = true, instructorScore = null, instructorComment = ""),
            TronSubmission(6, "乙", "", draft = false, latest = true, instructorScore = null, instructorComment = ""),
        )
        val d = detail(hw = homework(submitTimes = 2, unlimited = false), submissions = submitted)
        assertEquals(2, d.submittedCount)
        assertTrue(d.submitBlockReason!!.contains("2"))
    }

    @Test
    fun `草稿不计入已交次数`() {
        val onlyDraft = detail(
            hw = homework(submitTimes = 1, unlimited = false),
            submissions = listOf(
                TronSubmission(5, "还没写完", "", draft = true, latest = false,
                    instructorScore = null, instructorComment = ""),
            ),
        )
        assertEquals(0, onlyDraft.submittedCount)
        assertEquals(1, onlyDraft.draftCount)
        assertNull(onlyDraft.submitBlockReason)
    }

    // ── 签到：方式 → 端点 的对应 ────────────────────────────────────────

    @Test
    fun `四种方式各自落到正确的端点`() {
        assertEquals(RollcallAnswerKind.NUMBER, rollcall("number").answerKind)
        assertEquals(RollcallAnswerKind.QR, rollcall("qr_rollcall").answerKind)
        assertEquals(RollcallAnswerKind.QR, rollcall("roomis").answerKind)
        assertEquals(RollcallAnswerKind.SELF_REGISTRATION, rollcall("self_registration").answerKind)
    }

    @Test
    fun `雷达与点名不给按钮，但要说清为什么`() {
        for (source in listOf("radar", "manual", "auto_attendance")) {
            val r = rollcall(source)
            assertNull("$source 不该有端点", r.answerKind)
            assertNotNull("$source 必须有一句理由", r.actionBlockReason)
        }
        assertTrue(rollcall("radar").actionBlockReason!!.contains("定位"))
        assertTrue(rollcall("manual").actionBlockReason!!.contains("老师"))
    }

    @Test
    fun `已签过的不再是未答，且给理由不给按钮`() {
        val signed = rollcall("number", myStatus = "on_call")
        assertFalse(signed.unanswered)
        assertTrue(signed.actionBlockReason!!.contains("已经签过"))
    }

    @Test
    fun `截止的与没开始的都不算开放`() {
        assertFalse(rollcall("number", expired = true).isOpen)
        assertTrue(rollcall("number", expired = true).actionBlockReason!!.contains("截止"))
        assertFalse(rollcall("number", status = "waiting").isOpen)
    }

    // ── 待办里只有作业那一类可点 ─────────────────────────────────────────

    private fun todo(type: String, courseId: Int = 123) = TronTodo(
        id = 900,
        title = "C2.1 计算机系统作业",
        courseName = "计算机系统",
        courseId = courseId,
        courseCode = "C2.1",
        type = type,
        endTimeMillis = null,
        timeText = "",
        locked = false,
    )

    @Test
    fun `待办的作业条目带上课程号并可点`() {
        val item = todo("homework").toTodoItem()
        assertTrue(item.actionable)
        assertEquals(123, item.courseId)
        assertEquals(TodoSource.TRONCLASS, item.source)
    }

    @Test
    fun `非作业的待办不可点（防止拿错 id 发写请求）`() {
        for (type in listOf("exam", "notice", "budget", "", "Homework")) {
            val actionable = todo(type).toTodoItem().actionable
            if (type.equals("homework", ignoreCase = true)) {
                assertTrue("$type 应该可点", actionable)   // 大小写不敏感（这个站栏位不统一）
            } else {
                assertFalse("$type 不该可点", actionable)
            }
        }
    }

    @Test
    fun `课程号缺失时界面不该亮`() {
        // courseId==0 是「不知道」，UI 的门槛（actionable && courseId>0）据此不亮。
        val item = todo("homework", courseId = 0).toTodoItem()
        assertTrue(item.actionable)
        assertEquals(0, item.courseId)
    }
}
