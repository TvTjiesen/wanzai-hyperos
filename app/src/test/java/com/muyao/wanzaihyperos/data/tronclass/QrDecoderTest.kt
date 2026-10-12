package com.muyao.wanzaihyperos.data.tronclass

import com.google.zxing.MultiFormatReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

/**
 * 二維碼解碼的**完整路徑驗證**。
 *
 * ### 為什麼這個測試有價值
 *
 * 解碼原本綁在 Android 的 `ImageProxy` 上，**JVM 跑不了** ——
 * 於是「到底能不能解碼」只能靠真機碰運氣（得有人拿真的碼給你掃）。
 *
 * 現在 [QrDecoder.renderQrToLuminance] 能**自己造一張二維碼**，
 * 於是整條路（灰度 → 二值化 → 解碼）隨時可驗。
 */
class QrDecoderTest {

    @Test
    fun `能解出自己生成的一维码`() {
        val text = """{"scan_code":{"scan_result":"TEST-PAYLOAD-123"}}"""
        val (data, size) = QrDecoder.renderQrToLuminance(text, size = 300)

        val decoded = QrDecoder.decodeLuminance(data, size, size, MultiFormatReader())

        assertEquals(text, decoded)
    }

    @Test
    fun `解出的载荷能被解析出真实凭据`() {
        // 端到端：造碼 → 解碼 → 解析。這兩步合起來就是掃碼簽到的核心。
        val inner = "ROLLCALL-XYZ-789"
        val (data, size) = QrDecoder.renderQrToLuminance(
            """{"scan_code":{"scan_result":"$inner"}}""",
            size = 300,
        )
        val decoded = QrDecoder.decodeLuminance(data, size, size, MultiFormatReader())

        assertEquals(inner, QrPayloadParser.extract(decoded))
    }

    // -------------------------------------------------------------------------
    // ★ rowStride：這條盯的是「怎麼掃都掃不到」那個坑
    // -------------------------------------------------------------------------

    @Test
    fun `带padding的行能被正确摊平`() {
        // 造一張 4x2 的圖，但每行 stride = 6（比寬多 2 個 padding 位元組）。
        val width = 4
        val height = 2
        val stride = 6
        val buf = ByteBuffer.allocate(stride * height)
        // 第 0 行：1 2 3 4 + padding(9 9)
        buf.put(byteArrayOf(1, 2, 3, 4, 9, 9))
        // 第 1 行：5 6 7 8 + padding(9 9)
        buf.put(byteArrayOf(5, 6, 7, 8, 9, 9))
        buf.flip()

        val flat = QrDecoder.extractLuminance(buf, width, height, stride)

        // ★ 關鍵：padding 必須被丟掉。若把整塊當緊湊用，
        //   會得到 [1,2,3,4,9,9,5,6]（圖像錯位）而不是下面的結果。
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            flat,
        )
    }

    @Test
    fun `紧凑排列时直接整块取`() {
        val buf = ByteBuffer.allocate(8)
        buf.put(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        buf.flip()

        val flat = QrDecoder.extractLuminance(buf, 4, 2, 4)

        org.junit.Assert.assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), flat)
    }

    @Test
    fun `带padding的真实二维码也能解出`() {
        // ★ 最有價值的一條：造一張真二維碼，把它放進**帶 padding 的 buffer**，
        //   走完整的 decodeWithStride。這一條同時驗證了 stride 處理與解碼。
        val text = "PADDED-QR-TEST"
        val size = 300
        val (qr, _) = QrDecoder.renderQrToLuminance(text, size)
        val pad = 37
        val stride = size + pad
        val buf = ByteBuffer.allocate(stride * size)
        for (y in 0 until size) {
            buf.put(qr, y * size, size)
            buf.put(ByteArray(pad) { 0x7F })   // 填充值刻意不是黑也不是白
        }
        buf.flip()

        val decoded = QrDecoder.decodeWithStride(buf, size, size, stride, MultiFormatReader())

        assertEquals(text, decoded)
    }

    @Test
    fun `空图或坏数据回null而不是崩`() {
        assertNull(QrDecoder.decodeLuminance(ByteArray(0), 0, 0, MultiFormatReader()))
        assertNull(QrDecoder.decodeLuminance(ByteArray(10), 100, 100, MultiFormatReader()))
        // 全白圖（沒有碼）
        assertNull(QrDecoder.decodeLuminance(ByteArray(300 * 300) { 255.toByte() }, 300, 300, MultiFormatReader()))
    }

    @Test
    fun `渲染出的二维码尺寸与内容可预测`() {
        val (data, size) = QrDecoder.renderQrToLuminance("A", size = 128)
        assertEquals(128, size)
        assertEquals(128 * 128, data.size)
        // 一定同時有黑與白（不然不是二維碼）
        org.junit.Assert.assertTrue(data.any { it == 0.toByte() })
        org.junit.Assert.assertTrue(data.any { it == 255.toByte() })
        assertNotNull(data)
    }
}
