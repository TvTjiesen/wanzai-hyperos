package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B15：我的**提交狀態**解析測試。
 *
 * 輸入逐字抄自真機（`docs/B15-SUBMIT-PROBE.txt`）。
 */
class TronHomeworkStatusTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // 真機原文（c=60830 的一部分 + c=67577 有一條已交）。
    private val real = """
        {"course_id":60830,"homework_activities":[
         {"homework_type":"file_upload","id":492680,"is_announce_score_time_passed":true,
          "score":null,"status":"未交","status_code":"absent"},
         {"homework_type":"file_upload","id":481607,"is_announce_score_time_passed":true,
          "score":"95.0","status":"已交","status_code":"submitted"}
        ]}
    """.trimIndent()

    @Test
    fun `解析成 id 到狀態的映射`() {
        val m = TronHomeworkStatusParser.parse(obj(real))
        assertEquals(2, m.size)
        assertEquals(false, m[492680]!!.submitted)
        assertEquals("未交", m[492680]!!.label)
        assertEquals(true, m[481607]!!.submitted)
        assertEquals("已交", m[481607]!!.label)
    }

    @Test
    fun `未評分時 score 是 null 而不是 0 分`() {
        val m = TronHomeworkStatusParser.parse(obj(real))
        // 真機：未交的那條 score 是 null。顯示成「0 分」就是說謊。
        assertNull("未評分不該變成 0", m[492680]!!.score)
        assertEquals("95.0", m[481607]!!.score)
    }

    @Test
    fun `認不出 status_code 時原樣顯示服務端的說法`() {
        // 學校哪天新增一種狀態，我們不該編一個自己的詞。
        val m = TronHomeworkStatusParser.parse(
            obj("""{"homework_activities":[{"id":1,"status":"遲交","status_code":"late"}]}"""),
        )
        assertNull("認不出就不猜交了沒", m[1]!!.submitted)
        assertEquals("遲交", m[1]!!.label)
    }

    @Test
    fun `status_code 優先於中文 status`() {
        // 就算文案被改成別的，邏輯仍照 status_code 走。
        val m = TronHomeworkStatusParser.parse(
            obj("""{"homework_activities":[{"id":1,"status":"完全不同的文案","status_code":"submitted"}]}"""),
        )
        assertEquals(true, m[1]!!.submitted)
        assertEquals("已交", m[1]!!.label)
    }

    @Test
    fun `缺 id 的條目丟掉、外層不對回空映射`() {
        assertEquals(0, TronHomeworkStatusParser.parse(
            obj("""{"homework_activities":[{"status":"已交","status_code":"submitted"}]}"""),
        ).size)
        assertEquals(0, TronHomeworkStatusParser.parse(obj("""{"message":"未找到资源"}""")).size)
        assertEquals(0, TronHomeworkStatusParser.parse(null).size)
    }

    @Test
    fun `分數的數字形態也能讀`() {
        // 服務端有時給數字而不是字串（契約寫「可能」）。
        val m = TronHomeworkStatusParser.parse(
            obj("""{"homework_activities":[{"id":1,"status":"已交","status_code":"submitted","score":88}]}"""),
        )
        assertEquals("88", m[1]!!.score)
    }

    @Test
    fun `狀態取不到時是不知道而不是沒交`() {
        // 這是界面最要緊的一條：空映射 ≠ 全部未交。
        val empty = TronHomeworkStatusParser.parse(null)
        assertTrue(empty.isEmpty())
        // 呼叫方用 map[id] 取 → null → 界面不顯示任何尾值（而不是顯示「未交」）。
        assertNull(empty[492680])
        assertFalse(empty.containsKey(492680))
    }
}
