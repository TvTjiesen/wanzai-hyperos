package com.muyao.wanzaihyperos.data.cjh52

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 班級詳情的解析（真機取證的結構，值全是編造的）。
 */
class ClazzParserTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `班级详情解析出核心字段`() {
        val data = obj(
            """
            {
              "sid": 3069, "clazz": "示例班級8字", "specialty": "計算", "grade": "2025",
              "number": 38, "collegeName": "示例學院十個字",
              "teacherInfoList": [{"name": "張某某", "position": "班主任"}],
              "counselorInfoList": [{"name": "李某某", "position": "輔導員"}]
            }
            """.trimIndent(),
        )
        val c = ClazzParser.parse(data)!!
        assertEquals("示例班級8字", c.name)
        assertEquals("計算", c.specialty)
        assertEquals("2025", c.grade)
        assertEquals(38, c.headcount)
        assertEquals("張某某", c.teacherName)
        assertEquals("李某某", c.counselorName)
    }

    @Test
    fun `缺 clazz 时整条不解析`() {
        // clazz 是班級名的硬前提，沒有它就沒有可顯示的東西。
        assertNull(ClazzParser.parse(obj("""{"specialty":"計算"}""")))
        assertNull(ClazzParser.parse(null))
    }

    @Test
    fun `缺可选字段给空值不报错`() {
        val c = ClazzParser.parse(obj("""{"clazz":"只有名字"}"""))!!
        assertEquals("只有名字", c.name)
        assertEquals("", c.specialty)
        assertEquals("", c.teacherName)
        assertNull(c.headcount)
    }

    @Test
    fun `教师列表为空时名字为空串而不是崩`() {
        val c = ClazzParser.parse(
            obj("""{"clazz":"x","teacherInfoList":[],"counselorInfoList":[]}"""),
        )!!
        assertEquals("", c.teacherName)
        assertEquals("", c.counselorName)
    }
}
