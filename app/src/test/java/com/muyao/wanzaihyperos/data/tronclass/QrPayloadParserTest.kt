package com.muyao.wanzaihyperos.data.tronclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 掃碼載荷解析。
 *
 * 行為逐字對照源碼的 `wx.scanQRCode` 回呼：
 * ```js
 * var o = JSON.parse(e.resultStr), i = o?.["scan_code"]?.["scan_result"];
 * t("string" === typeof i ? i : null)   // 取到用它
 * // 解析失敗 → 用原字串
 * ```
 */
class QrPayloadParserTest {

    @Test
    fun `从嵌套JSON里取出真正的凭据`() {
        // 學在城院的二維碼是一層 JSON，憑據在 scan_code.scan_result。
        val raw = """{"scan_code":{"scan_result":"ROLLCALL-ABC-123"}}"""
        assertEquals("ROLLCALL-ABC-123", QrPayloadParser.extract(raw))
    }

    @Test
    fun `裸字串原样使用`() {
        // 源碼的 catch 分支就是把原字串拿去用。
        assertEquals("PLAIN-CODE", QrPayloadParser.extract("PLAIN-CODE"))
    }

    @Test
    fun `合法JSON但没有scan_result时回null而不是整串送去`() {
        // ★ 這條最容易漏：一個合法但結構不符的 JSON（別的二維碼），
        //   整串送去簽到一定失敗 —— 回 null 讓上層說「這不是簽到碼」更誠實。
        assertNull(QrPayloadParser.extract("""{"other":"thing"}"""))
        assertNull(QrPayloadParser.extract("""{"scan_code":{}}"""))
        assertNull(QrPayloadParser.extract("""{"scan_code":{"scan_result":123}}"""))
    }

    @Test
    fun `空内容回null`() {
        assertNull(QrPayloadParser.extract(null))
        assertNull(QrPayloadParser.extract(""))
        assertNull(QrPayloadParser.extract("   "))
    }

    @Test
    fun `scan_result为空串时回null`() {
        assertNull(QrPayloadParser.extract("""{"scan_code":{"scan_result":"   "}}"""))
    }

    @Test
    fun `两端空白被去掉`() {
        assertEquals("ABC", QrPayloadParser.extract("  ABC  "))
        assertEquals("ABC", QrPayloadParser.extract("""{"scan_code":{"scan_result":" ABC "}}"""))
    }

    @Test
    fun `看起来像签到码的判断`() {
        assertTrue(QrPayloadParser.looksLikeRollcallCode("""{"scan_code":{"scan_result":"x"}}"""))
        assertTrue(QrPayloadParser.looksLikeRollcallCode("PLAIN"))
        // 合法但结构不符 → 不像
        assertFalse(QrPayloadParser.looksLikeRollcallCode("""{"other":"thing"}"""))
        assertFalse(QrPayloadParser.looksLikeRollcallCode(""))
    }
}
