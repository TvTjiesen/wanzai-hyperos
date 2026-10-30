package com.muyao.wanzaihyperos.data.tronclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学校签到码的解析守卫。
 *
 * 这里喂的是**真机上扫到的原文**（`files/tron_raw_scanned.txt` 2026-10-29 09:43 那两行，
 * 逐字节 hexdump 过：`/j?p=0~\u00101g6b!3~<40 字符>!4~\u00105cmw`）。
 * 值前面的 0x10 是前端 `decodeQrCode` 的 36 进制数字前缀，只看可打印字符会把它吞掉。
 */
class RollcallQrCodeTest {

    /** 真机扫到的第一串（2026-10-29 09:43:02）。 */
    private val scanned1 =
        "/j?p=0~\u00101g6b!3~0123456789abcdef0123456789abcdef12345678!4~\u00105cmw"

    /** 真机扫到的第二串（2026-10-29 09:43:12，同一次签到，data 不同）。 */
    private val scanned2 =
        "/j?p=0~\u00101g6b!3~179323819065a5c05a70acf2101ad570e3cb3be3fa!4~\u00105cmw"

    @Test
    fun `真机扫到的码能解出签到编号和课程编号`() {
        val p = RollcallQrCode.parse(scanned1)
        assertEquals("4 号段是签到 id（36 进制 5cmw）", 249656, p?.rollcallId)
        assertEquals("0 号段是课程 id（36 进制 1g6b）", 67619, p?.courseId)
        assertEquals(
            "3 号段原样就是提交用的 data",
            "0123456789abcdef0123456789abcdef12345678",
            p?.data,
        )
    }

    @Test
    fun `同一张码的两次扫描只有 data 不同`() {
        val a = RollcallQrCode.parse(scanned1)
        val b = RollcallQrCode.parse(scanned2)
        assertEquals("签到 id 必须相同（同一次签到）", a?.rollcallId, b?.rollcallId)
        assertEquals("课程 id 必须相同", a?.courseId, b?.courseId)
        assertTrue("data 必须不同（每次刷新都换）", a?.data != b?.data)
    }

    @Test
    fun `提交用的 data 是码里的 3 号段而不是整串原文`() {
        assertEquals(
            "把整串 /j?p= 交上去服务端只会回 no_request_data",
            "0123456789abcdef0123456789abcdef12345678",
            RollcallQrCode.submissionData(scanned1),
        )
    }

    @Test
    fun `解不出来的东西不猜`() {
        assertNull("空串", RollcallQrCode.parse(null))
        assertNull("空白", RollcallQrCode.parse("   "))
        assertNull("普通文本", RollcallQrCode.parse("hello world"))
        assertNull("/j?p= 但值是空的", RollcallQrCode.parse("/j?p="))
    }

    @Test
    fun `直接给 p 的值也能解`() {
        val value = scanned1.substringAfter("/j?p=")
        val p = RollcallQrCode.parse(value)
        assertEquals(249656, p?.rollcallId)
        assertEquals("0123456789abcdef0123456789abcdef12345678", p?.data)
    }

    @Test
    fun `官方分发器的 JSON 那一支也能解`() {
        val p = RollcallQrCode.parse("""{"rollcallId":249656,"data":"abc123","courseId":67619}""")
        assertEquals(249656, p?.rollcallId)
        assertEquals("abc123", p?.data)
        assertEquals(67619, p?.courseId)
    }

    @Test
    fun `解不出 data 时退回原文`() {
        assertEquals(
            "宁可照实交原文让服务端说话，也不替它编一个值",
            "some-unknown-code",
            RollcallQrCode.submissionData("some-unknown-code"),
        )
    }
}
