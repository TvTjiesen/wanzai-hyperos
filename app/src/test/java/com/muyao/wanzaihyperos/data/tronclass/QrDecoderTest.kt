package com.muyao.wanzaihyperos.data.tronclass

import com.google.zxing.MultiFormatReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // -------------------------------------------------------------------------
    // ★ 四趟阶梯：盯的是「怎么扫都没反应」那个真缺陷
    // -------------------------------------------------------------------------

    /** 把一張灰度圖反相：亮模組 / 暗底（投影幕與某些相機出的就是這樣）。 */
    private fun inverted(data: ByteArray): ByteArray =
        ByteArray(data.size) { i -> (255 - (data[i].toInt() and 0xFF)).toByte() }

    /** 把對比度壓到中間灰附近：整塊碼被洗白。 */
    private fun washed(data: ByteArray, keep: Float): ByteArray = ByteArray(data.size) { i ->
        val v = 128f + ((data[i].toInt() and 0xFF) - 128f) * keep
        v.coerceIn(0f, 255f).toInt().toByte()
    }

    @Test
    fun `反相的码也能解出来`() {
        val text = "INVERTED-QR-PAYLOAD"
        val (data, size) = QrDecoder.renderQrToLuminance(text, size = 300)
        val flipped = inverted(data)

        // 先把「反相确实不是原图」钉住，否则下面这条断言可能只是原图侥幸通过。
        org.junit.Assert.assertFalse(flipped.contentEquals(data))

        val decoded = QrDecoder.decodeLuminance(flipped, size, size, MultiFormatReader())

        assertEquals(text, decoded)
    }

    @Test
    fun `反相又被洗白的码也能解出来`() {
        // 这是四趟阶梯里只有「Global + 反相」那一趟救得回来的情形。
        // 实测（[local]/qrfix/ZxA.java）：keep 降到 0.35 及以下时 Hybrid+反相 也认不出，
        // 只有 Global+反相 能解；0.45 时 Hybrid+反相 自己就中了，第 4 趟根本没被考到。
        // 所以这个数字是被量出来的，不是随手写的——改大就等于把这一条变成空断言。
        val text = "WASHED-INVERTED-QR"
        val (data, size) = QrDecoder.renderQrToLuminance(text, size = 300)
        val hard = washed(inverted(data), 0.30f)

        val decoded = QrDecoder.decodeLuminance(hard, size, size, MultiFormatReader())

        assertEquals(text, decoded)
    }

    @Test
    fun `四趟都认不出时回null而不是抛出去`() {
        // 解码跑在相机帧回调上：这里抛异常等于相机预览直接断。
        assertNull(
            QrDecoder.decodeLuminance(ByteArray(200 * 200) { 128.toByte() }, 200, 200, MultiFormatReader()),
        )
    }

    @Test
    fun `相册那条路也能解出二维码`() {
        // 相簿那条路走 RGBLuminanceSource（ARGB 像素），与相机那条不是同一段代码。
        val text = "GALLERY-QR-PAYLOAD"
        val (gray, size) = QrDecoder.renderQrToLuminance(text, size = 240)
        val argb = IntArray(size * size) { i ->
            val v = gray[i].toInt() and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val decoded = QrDecoder.decodeArgb(argb, size, size, MultiFormatReader())

        assertEquals(text, decoded)
    }

    @Test
    fun `相册的坏输入回null`() {
        assertNull(QrDecoder.decodeArgb(IntArray(0), 0, 0, MultiFormatReader()))
        assertNull(QrDecoder.decodeArgb(IntArray(10), 100, 100, MultiFormatReader()))
    }

    @Test
    fun `相簿照片先試小的，認不出再換大一檔`() {
        // 一張 3000x2000（600 萬像素，正好在預算內）的相機照片：
        // 先試長邊 2400 以內（sample=2），認不出再回原圖（sample=1）。
        // 少了後面那一檔，這類照片就永遠認不出。
        assertEquals(listOf(2, 1), QrDecoder.sampleLadder(3000, 2000))

        // 本來就小的圖不必縮，也不必重試。
        assertEquals(listOf(1), QrDecoder.sampleLadder(1080, 1440))

        // 階梯必須以 1 結尾（總要試一次原圖），而且是由小到大排。
        val big = QrDecoder.sampleLadder(12000, 9000, maxPixels = Int.MAX_VALUE)
        assertEquals(1, big.last())
        assertEquals(big.sortedDescending(), big)
        assertTrue(big.first() > 1)

        // 壞輸入不能拋：呼叫方在相簿回呼裡，拋出去等於沒有提示。
        assertEquals(listOf(1), QrDecoder.sampleLadder(0, 0))
    }

    @Test
    fun `階梯不會排出解不完的檔次`() {
        // 4000x3000 原圖是 1200 萬像素，解一次就是 48 MB 陣列 —— 中階機會 OOM。
        // 預算 600 萬像素時，sample=1 這一檔根本不該被排進來。
        val ladder = QrDecoder.sampleLadder(4000, 3000)
        assertFalse(ladder.contains(1))
        assertTrue(ladder.isNotEmpty())

        // 每一檔都必須在預算之內（這是這條的唯一職責：排進來的都解得動）。
        for (s in ladder) {
            val px = (4000L / s) * (3000L / s)
            assertTrue("sample=$s 排出 $px 像素，超出預算", px <= QrDecoder.MAX_DECODE_PIXELS)
        }

        // 預算放寬之後 sample=1 就該回來，否則「預算」是假的。
        assertEquals(1, QrDecoder.sampleLadder(4000, 3000, maxPixels = Int.MAX_VALUE).last())

        // 預算窄到連縮過的都放不下時，仍要給一檔 —— 回空清單等於整個相簿按鈕沒作用。
        assertTrue(QrDecoder.sampleLadder(4000, 3000, maxPixels = 1).isNotEmpty())
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
