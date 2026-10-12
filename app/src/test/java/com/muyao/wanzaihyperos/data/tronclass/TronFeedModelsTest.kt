package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公告解析（多種外層形態）。
 *
 * 值全是編造的；形狀取自活體探測（實測 `{"bulletins":[]}`）。
 */
class TronBulletinParserTest {

    private fun el(json: String) = Json.parseToJsonElement(json)

    @Test
    fun `解析公告核心字段`() {
        val root = el(
            """
            {"bulletins":[{
              "id": 1, "title": "示例公告", "content": "正文内容",
              "author_name": "示例作者", "created_at": "2026-10-14T15:30:00Z",
              "is_read": false
            }]}
            """.trimIndent(),
        )
        val b = TronBulletinParser.parseList(root).single()

        assertEquals(1, b.id)
        assertEquals("示例公告", b.title)
        assertEquals("正文内容", b.content)
        assertEquals("示例作者", b.authorName)
        assertEquals(false, b.isRead)
    }

    @Test
    fun `发布时间按UTC转本地`() {
        val root = el("""{"bulletins":[{"id":1,"created_at":"2026-10-14T15:30:00Z"}]}""")
        // 與待辦/作業同一條紀律：帶 Z 是 UTC，照原樣顯示會早 8 小時。
        val b = TronBulletinParser.parseList(root).single()
        assertEquals("2026-10-14 23:30", b.createdAtLocal)
    }

    @Test
    fun `三种外层形态都能解析`() {
        // ★ 這個站的回應外層不統一：實測有 {bulletins:[]}，源碼裡也有 {data:[...]}。
        //   先前只認一種的話，換個接口就整頁空白。
        assertTrue(TronBulletinParser.parseList(el("""{"bulletins":[]}""")).isEmpty())
        assertTrue(TronBulletinParser.parseList(el("""{"data":[]}""")).isEmpty())
        assertTrue(TronBulletinParser.parseList(el("[]")).isEmpty())

        // 三種都帶一條時都能取到
        assertEquals(1, TronBulletinParser.parseList(
            el("""{"bulletins":[{"id":1}]}"""),
        ).size)
        assertEquals(1, TronBulletinParser.parseList(
            el("""{"data":[{"id":1}]}"""),
        ).size)
        assertEquals(1, TronBulletinParser.parseList(
            el("""[{"id":1}]"""),
        ).size)
    }

    @Test
    fun `缺 id 的条目丢掉`() {
        val root = el("""{"bulletins":[{"title":"没有 id"},{"id":9}]}""")
        val list = TronBulletinParser.parseList(root)
        assertEquals(1, list.size)
        assertEquals(9, list.single().id)
    }

    @Test
    fun `缺时间时显示破折号`() {
        val b = TronBulletinParser.parseList(el("""{"bulletins":[{"id":1}]}""")).single()
        assertEquals("—", b.createdAtLocal)
    }

    @Test
    fun `null 或坏数据不抛异常`() {
        assertTrue(TronBulletinParser.parseList(null).isEmpty())
        assertTrue(TronBulletinParser.parseList(el("""{"bulletins":"不是数组"}""")).isEmpty())
    }
}

/** 個人資訊解析。**姓名/學號是編造的**，只在斷言裡用。 */
class TronProfileParserTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private val sample = """
    {
      "id": 36519, "name": "示例名", "user_no": "12345678",
      "avatar_big_url": "https://example.com/a.png",
      "role": "Student",
      "department": {"name": "示例学院"},
      "grade": {"name": "2025"},
      "klass": {"name": "示例班级"},
      "org": {"name": "示例学校"}
    }
    """.trimIndent()

    @Test
    fun `解析展示需要的字段`() {
        val p = TronProfileParser.parse(obj(sample))!!
        assertEquals("示例名", p.name)
        assertEquals("12345678", p.userNo)
        assertEquals("示例学院", p.department)
        assertEquals("2025", p.grade)
        assertEquals("示例班级", p.klass)
        assertEquals("示例学校", p.orgName)
    }

    @Test
    fun `身分大小写都能认`() {
        // ★ 實測服務端回的是 `Student`（首字母大寫）——
        //   不轉小寫的話每個人都會顯示成 `Student` 而不是「学生」，
        //   而且不會報錯，只會靜默顯示英文。
        assertEquals("学生", TronProfileParser.parse(obj(sample))!!.roleLabel)
        assertEquals(
            "学生",
            TronProfileParser.parse(obj("""{"name":"x","role":"student"}"""))!!.roleLabel,
        )
        assertEquals(
            "教师",
            TronProfileParser.parse(obj("""{"name":"x","role":"Teacher"}"""))!!.roleLabel,
        )
    }

    @Test
    fun `认不出的身分原样显示`() {
        val p = TronProfileParser.parse(obj("""{"name":"x","role":"someNewRole"}"""))!!
        assertEquals("someNewRole", p.roleLabel)
    }

    @Test
    fun `缺姓名整条不解析`() {
        // 沒有名字的「我的」頁沒有意義。
        assertNull(TronProfileParser.parse(obj("""{"user_no":"1"}""")))
        assertNull(TronProfileParser.parse(null))
    }

    @Test
    fun `http 头像不采用`() {
        // http 的頭像在校外會失敗 —— 顯示破圖不如不顯示。
        val p = TronProfileParser.parse(
            obj("""{"name":"x","avatar_big_url":"http://example.com/a.png"}"""),
        )!!
        assertNull(p.avatarUrl)
    }

    @Test
    fun `副标拼学号与班级`() {
        val p = TronProfileParser.parse(obj(sample))!!
        assertEquals("12345678　示例班级", p.identityLine)
    }
}
