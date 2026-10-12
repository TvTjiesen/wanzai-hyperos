package com.muyao.wanzaihyperos.data.tronclass

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二維碼**解碼**（純函式，可單測）。
 *
 * ### 為什麼要把它從 UI 抽出來
 *
 * 原本解碼寫在 `QrScannerPage.decode(ImageProxy)` 裡 —— 那綁死了 Android 的
 * `ImageProxy`，**在 JVM 單測裡跑不了**，於是「能不能解碼」這件事
 * 只能靠真機碰運氣（要有真的二維碼）。
 *
 * 抽成「輸入灰度位元組 + 寬高 → 輸出文字」之後，測試可以**自己生成一張二維碼**
 * 餵進來，把整條解碼路徑驗證掉（見 QrDecoderTest）。
 *
 * 這比真機更容易復現：真機要有人拿碼給你掃，而這裡任何時候都能跑。
 *
 * ### 為什麼只吃灰度（Y）
 *
 * 相機給的是 `YUV_420_888`，第 0 個平面是亮度。二維碼是黑白圖案，
 * 灰度足夠解碼，省掉一次 YUV→RGB 轉換（720p 上每次要幾毫秒）。
 *
 * ### ⚠️ `rowStride` 的坑
 *
 * 相機 buffer 每行**可能帶 padding**（`rowStride > width`）。
 * 把整塊 buffer 當緊湊陣列用會**圖像錯位** —— 表現是「怎麼掃都掃不到」，
 * 而不是報錯。所以取行時必須按 `rowStride` 跳。
 */
object QrDecoder {

    /**
     * 從**帶 stride 的相機 buffer** 解碼。
     *
     * @param buffer 相機平面資料（通常是 `planes[0].buffer` 的內容）
     * @param width 圖像寬（像素）
     * @param height 圖像高
     * @param rowStride 每行實際佔用的位元組數（**可能大於 width**）
     * @return 解出的文字；解不出回 null
     */
    fun decodeWithStride(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        reader: MultiFormatReader,
    ): String? {
        if (width <= 0 || height <= 0) return null
        val data = extractLuminance(buffer, width, height, rowStride)
        return decodeLuminance(data, width, height, reader)
    }

    /**
     * 把帶 stride 的 buffer 攤平成**緊湊的**灰度陣列。
     *
     * 抽出來是為了**能單獨測**：這個函式錯了，解碼就永遠失敗，
     * 而那時候界面只表現成「掃不到」，看不出是這裡的問題。
     */
    fun extractLuminance(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
    ): ByteArray {
        val data = ByteArray(width * height)
        if (rowStride == width) {
            // 緊湊排列：直接整塊取（取小的那個，buffer 可能不足）。
            buffer.get(data, 0, minOf(data.size, buffer.remaining()))
            return data
        }
        // 帶 padding：逐行只取前 width 個位元組。
        val row = ByteArray(rowStride)
        var out = 0
        while (out + width <= data.size && buffer.remaining() >= rowStride) {
            buffer.get(row, 0, rowStride)
            System.arraycopy(row, 0, data, out, width)
            out += width
        }
        return data
    }

    /** 從緊湊的灰度資料解碼。 */
    fun decodeLuminance(
        data: ByteArray,
        width: Int,
        height: Int,
        reader: MultiFormatReader,
    ): String? {
        if (width <= 0 || height <= 0 || data.size < width * height) return null
        return runCatching {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decodeWithState(bitmap).text
        }.getOrNull()
    }

    /**
     * 產生一張測試用的二維碼灰度圖（**只給測試/自檢用**）。
     *
     * 有了它，解碼路徑不必依賴「剛好有人拿碼給你掃」——見 QrDecoderTest。
     */
    fun renderQrToLuminance(text: String, size: Int = 256): Pair<ByteArray, Int> {
        val matrix: BitMatrix = QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val data = ByteArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                // 黑=0、白=255（ZXing 的約定：true 是黑模組）。
                data[y * size + x] = if (matrix.get(x, y)) 0 else 255.toByte()
            }
        }
        return data to size
    }
}
