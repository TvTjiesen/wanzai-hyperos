package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 簽到解析。
 *
 * 樣本形狀取自源碼的 mobx 模型（`this.rollcallId=0, this.courseId=0, …`），
 * 值全是編造的。
 */
class TronRollcallParserTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private val myId = 36519

    private fun sample(
        source: String = "number",
        status: String = "in_progress",
        myStatus: String = "on_call",
    ) = obj(
        """
        {
          "id": 1234,
          "course_id": 67577,
          "title": "示例课程",
          "source": "$source",
          "status": "$status",
          "is_expired": false,
          "rollcall_time": "2026-10-12 10:00",
          "end_time": "2026-10-12 10:05",
          "number_code": "8888",
          "student_rollcalls": [
            {"student_id": 11111, "status": "absent"},
            {"student_id": $myId, "status": "$myStatus"}
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `解析基本字段`() {
        val r = TronRollcallParser.parse(sample(), myId)!!
        assertEquals(1234, r.id)
        assertEquals(67577, r.courseId)
        assertEquals("示例课程", r.title)
        assertEquals("number", r.source)
        assertEquals("8888", r.numberCode)
    }

    @Test
    fun `从多条同学记录里挑出我自己的`() {
        // ★ 這條盯的是「別把同學的狀態當成我的」——
        //   沒有自己的 student_id 時會拿到第一條（別人），
        //   界面就會顯示錯誤的簽到狀態。
        val r = TronRollcallParser.parse(sample(myStatus = "on_call"), myId)!!
        assertEquals("on_call", r.myStatus)
        assertEquals("已签到", r.myStatusLabel)
    }

    @Test
    fun `没有我的studentId时状态为空而不是拿别人的`() {
        val r = TronRollcallParser.parse(sample(), null)!!
        assertEquals("", r.myStatus)
        // 空狀態顯示「—」，**不是**「缺勤」——那是「不知道」，不是「沒簽」。
        assertEquals("—", r.myStatusLabel)
    }

    @Test
    fun `进行中且未过期才算开着`() {
        assertTrue(TronRollcallParser.parse(sample(), myId)!!.isOpen)
        assertFalse(TronRollcallParser.parse(sample(status = "finished"), myId)!!.isOpen)
    }

    @Test
    fun `过期的不算开着`() {
        val expired = obj(
            """{"id":1,"source":"number","status":"in_progress","is_expired":true}""",
        )
        assertFalse(TronRollcallParser.parse(expired, myId)!!.isOpen)
    }

    @Test
    fun `签到的七种方式都能翻成人话`() {
        val cases = mapOf(
            "manual" to "老师点名",
            "number" to "数字签到",
            "radar" to "雷达签到",
            "qr" to "扫码签到",
            "roomis" to "扫码签到",
            "self_registration" to "自助签到",
            "auto_attendance" to "自动签到",
            "merged_rollcall" to "合并签到",
        )
        for ((src, label) in cases) {
            val r = TronRollcallParser.parse(sample(source = src), myId)!!
            assertEquals("source=$src", label, r.sourceLabel)
        }
    }

    @Test
    fun `认不出的方式原样显示而不编造`() {
        // 服務端哪天加了新方式，我們顯示原文 —— 這是資訊，不是垃圾。
        val r = TronRollcallParser.parse(sample(source = "some_new_kind"), myId)!!
        assertEquals("some_new_kind", r.sourceLabel)
    }

    @Test
    fun `缺 id 的条目丢掉`() {
        val root = Json.parseToJsonElement(
            """[{"course_id":1},{"id":9,"source":"number"}]""",
        )
        val list = TronRollcallParser.parseList(root, myId)
        assertEquals(1, list.size)
        assertEquals(9, list.single().id)
    }

    @Test
    fun `三种外层包装都能解析`() {
        // 這個站的回應外層不統一，三種都接。
        val arr = Json.parseToJsonElement("""[{"id":1,"source":"number"}]""")
        val wrapped = obj("""{"rollcalls":[{"id":1,"source":"number"}]}""")
        val dataWrapped = obj("""{"data":[{"id":1,"source":"number"}]}""")

        assertEquals(1, TronRollcallParser.parseList(arr, myId).size)
        assertEquals(1, TronRollcallParser.parseList(wrapped, myId).size)
        assertEquals(1, TronRollcallParser.parseList(dataWrapped, myId).size)
        assertTrue(TronRollcallParser.parseList(null, myId).isEmpty())
    }

    @Test
    fun `我的状态三种取值都翻对`() {
        assertEquals("已签到", TronRollcallParser.parse(sample(myStatus = "on_call"), myId)!!.myStatusLabel)
        assertEquals("请假", TronRollcallParser.parse(sample(myStatus = "on_leave"), myId)!!.myStatusLabel)
        assertEquals("缺勤", TronRollcallParser.parse(sample(myStatus = "absent"), myId)!!.myStatusLabel)
    }
}
