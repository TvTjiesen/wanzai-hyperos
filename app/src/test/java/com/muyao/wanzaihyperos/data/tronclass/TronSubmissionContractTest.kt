package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提交契約的兩條線：**讀**（`submission_list`）與**寫**（提交包體）。
 *
 * 寫的那一半是全部測試裡最重要的幾個斷言之一 —— 包體多一個鍵、
 * 少一個轉義、或把 `is_draft` 打錯，代價都是**他的作業**，
 * 不是一次可以重跑的測試。所以這裡把包體**逐字**釘死。
 *
 * 響應形狀取自 H5 包裡 `getStudentSubmissionList` 的
 * `toClasses(n, Yo, "list")`（那個 `"list"` 就是外層鍵名），值全是編造的。
 */
class TronSubmissionContractTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // ── 提交包體 ────────────────────────────────────────────────────────

    @Test
    fun `包體恰好四個鍵，順序與源碼模型一致`() {
        assertEquals(
            """{"comment":"第一段","is_draft":false,"slides":[],"uploads":[]}""",
            homeworkSubmissionPayload("第一段", draft = false),
        )
    }

    @Test
    fun `草稿把 is_draft 打成 true（草稿不佔可交次數）`() {
        assertTrue(
            homeworkSubmissionPayload("还没写完", draft = true).contains("\"is_draft\":true"),
        )
    }

    @Test
    fun `正文里的引号反斜线换行都要转义`() {
        // 不转义会得到一个非法 JSON，服务端 400 ——
        // 而界面显示「提交失败」，他会再按一次（可能已经发出去一半）。
        val original = "老师说\"注意\"\n先写这里\\然后换行"
        val body = homeworkSubmissionPayload(original, draft = false)
        // 回环一次：能解析、且取回来的正文与原文**逐字相同**，就证明转义没弄坏结构。
        val parsed = (Json.parseToJsonElement(body) as JsonObject)["comment"] as JsonPrimitive
        assertEquals(original, parsed.content)
        assertTrue(body.contains("\\\""))
        assertTrue(body.contains("\\\\"))
        assertTrue(body.contains("\\n"))
    }

    @Test
    fun `正文里的控制字符走 u 转义而不是原样进包体`() {
        val body = homeworkSubmissionPayload("a${'\u0001'}b", draft = false)
        assertFalse("控制字符不能原样出现在包里", body.contains('\u0001'))
        assertTrue(body.contains("\\u0001"))
        // 仍然必须是合法 JSON。
        Json.parseToJsonElement(body)
    }

    // ── 我的提交記錄 ─────────────────────────────────────────────────────

    @Test
    fun `解析 list 外层`() {
        val rows = TronSubmissionParser.parseList(
            obj(
                """
                {"list":[
                  {"id":88,"comment":"<p>我的答案</p>","created_at":"2026-10-18T12:00:00Z",
                   "is_draft":false,"is_latest_version":true,"instructor_score":88,
                   "instructor_comment":"可以"},
                  {"id":87,"comment":"草稿内容","created_at":"2026-10-17T09:00:00Z","is_draft":true}
                ]}
                """.trimIndent(),
            ),
        )
        assertEquals(2, rows.size)
        val first = rows.first()
        assertEquals(88, first.id)
        assertFalse(first.draft)
        assertTrue(first.latest)
        assertEquals("我的答案", first.plainComment.trim())
        assertEquals("88", first.instructorScore)
        assertEquals("可以", first.instructorComment)
        assertTrue(rows[1].draft)
        // 未评分 ≠ 0 分。
        assertNull(rows[1].instructorScore)
    }

    @Test
    fun `裸数组与 data 外层都要接住（这个站外层不统一）`() {
        val bare = TronSubmissionParser.parseList(
            Json.parseToJsonElement("""[{"id":1,"comment":"x"}]"""),
        )
        assertEquals(1, bare.size)
        val wrapped = TronSubmissionParser.parseList(
            obj("""{"data":{"list":[{"id":2,"comment":"y"}]}}"""),
        )
        assertEquals(1, wrapped.size)
        assertEquals(2, wrapped.first().id)
    }

    @Test
    fun `缺 id 的条目丢掉而不是编号 0`() {
        // 编号 0 会在「第 0 版」这种地方说谎，而且草稿/正式混在一起时无法区分。
        val rows = TronSubmissionParser.parseList(obj("""{"list":[{"comment":"没有 id"}]}"""))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `score 为 null 不是字符串 null`() {
        val rows = TronSubmissionParser.parseList(
            obj("""{"list":[{"id":5,"instructor_score":null}]}"""),
        )
        assertNull(rows.single().instructorScore)
        assertEquals("未评分", rows.single().scoreLabel)
    }
}
