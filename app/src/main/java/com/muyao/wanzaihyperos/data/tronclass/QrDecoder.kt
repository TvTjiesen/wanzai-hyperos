package com.muyao.wanzaihyperos.data.tronclass

import com.google.zxing.BarcodeFormat
import com.google.zxing.Binarizer
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
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

    /**
     * 從緊湊的灰度資料解碼。
     *
     * ### 為什麼要試四趟，而不是一趟
     *
     * 二維碼認不出來**不是只有「太小」一種原因**。教室裡那塊投影幕上真實發生的是：
     * 燈光在螢幕上打出一片反光（局部過曝）、或者整塊碼被洗白（對比度被壓）、
     * 或者相機把「亮模組 / 暗底」拍反了。**HybridBinarizer 在這些情況下會把整片判成一色**，
     * 於是讀出來永遠是「什麼都沒有」——使用者看到的正是「怎麼掃都沒反應」。
     *
     * 實測（[local]/qrfix 那組交叉表）：
     *
     * | 圖 | Hybrid | Hybrid+反相 | Global | Global+反相 |
     * |---|---|---|---|---|
     * | 正常碼 | 得 | 得 | 得 | 得 |
     * | 反相碼 | 不得 | 得 | 不得 | 得 |
     * | 反相+洗白 | 不得 | 不得 | 不得 | 得 |
     * | 過曝 | 不得 | 不得 | 得 | 得 |
     * | 低對比 | 不得 | 不得 | 得 | 得 |
     *
     * 也就是說舊的「只跑 Hybrid、不給反相」**正好是唯一一種會全軍覆沒的組合**。
     * 四趟裡任意一趟認出來就返回，全都不行才返回 null。
     *
     * 代價：1280x720 上最壞（整幀沒有碼）約 5ms，認出來就停（第一趟就中的話 1~3ms）。
     * 解碼跑在掃碼頁的單線程 executor 上，不佔主線程。
     *
     * ⚠️ 提示（hints）由**這個函式**負責設，呼叫方不要再自己 setHints：
     * MultiFormatReader 是同一個實例複用的，兩處都設會互相覆蓋。
     */
    fun decodeLuminance(
        data: ByteArray,
        width: Int,
        height: Int,
        reader: MultiFormatReader,
    ): String? {
        if (width <= 0 || height <= 0 || data.size < width * height) return null
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        for (pass in PASSES) {
            val text = runCatching {
                reader.setHints(pass.hints)
                reader.decodeWithState(BinaryBitmap(pass.binarizer(source))).text
            }.getOrNull()
            if (text != null) return text
        }
        return null
    }

    /**
     * 一趟嘗試：用哪種二值化器、給什麼提示。
     *
     * 抽成資料而不是四段複製貼上的程式碼，是為了讓「試了哪幾種」一眼看得到——
     * 少一種就是少救回一類碼，而那種碼在現場表現成「這 App 掃不出來」。
     */
    private class Pass(
        val binarizer: (LuminanceSource) -> Binarizer,
        val hints: Map<DecodeHintType, Any>,
    )

    /** 只認二維碼：畫面上若剛好有個條碼，不該被當成簽到碼回報上去。 */
    private val QR_HINTS: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    )

    /** 同上，外加「也試試反相」：投影幕與某些相機出的是亮模組暗底。 */
    private val QR_HINTS_INVERTED: Map<DecodeHintType, Any> =
        QR_HINTS + (DecodeHintType.ALSO_INVERTED to true)

    /** 順序即代價：大多數碼第一趟就中，後三趟只在前面失敗時才花時間。 */
    private val PASSES: List<Pass> = listOf(
        Pass({ s -> HybridBinarizer(s) }, QR_HINTS),
        Pass({ s -> HybridBinarizer(s) }, QR_HINTS_INVERTED),
        Pass({ s -> GlobalHistogramBinarizer(s) }, QR_HINTS),
        Pass({ s -> GlobalHistogramBinarizer(s) }, QR_HINTS_INVERTED),
    )

    /**
     * 從**相簿裡的一張圖**解碼（ARGB 像素）。
     *
     * ### 為什麼要有這條路
     *
     * 現場掃不出來時，人手上通常已經有一張照片了（同學拍了大屏、或自己先拍下來）。
     * 有了它，「我這張照片裡到底有沒有碼」這件事當場就能驗，不必再對著螢幕試。
     * 而且照片是靜止的，可以慢慢試滿四趟。
     *
     * ⚠️ 像素必須是 ARGB_8888 的 int 陣列（Bitmap.getPixels 的輸出）。
     * 呼叫方負責縮圖（相簿裡的照片常有幾千萬像素，整張轉換會 OOM）。
     */
    fun decodeArgb(
        pixels: IntArray,
        width: Int,
        height: Int,
        reader: MultiFormatReader,
    ): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val source = RGBLuminanceSource(width, height, pixels)
        for (pass in PASSES) {
            val text = runCatching {
                reader.setHints(pass.hints)
                reader.decodeWithState(BinaryBitmap(pass.binarizer(source))).text
            }.getOrNull()
            if (text != null) return text
        }
        return null
    }

    /** 相簿照片先縮到最長邊不超過這個像素數，純粹為了不把記憶體撐爆。 */
    const val PICKED_LONGEST_PX = 2_400

    /**
     * 每次嘗試最多解這麼多像素（約 24 MB 的 int 陣列）。
     *
     * 相機照片動輒四千像素寬，原圖解一次就是 48 MB 位圖 + 48 MB 陣列，
     * 在中階機上等於直接 OOM。超過這個預算的檔次不排進階梯。
     */
    const val MAX_DECODE_PIXELS = 6_000_000

    /**
     * 相簿照片要試哪幾檔縮放（**由小到大**排，即 sample 由大到小）。
     *
     * ### 為什麼不能只試一檔
     *
     * 縮圖是為了不 OOM，不是為了認得更準。實測（[local]/qrfix/ZxC.java，用一張真的
     * 教室大屏照片）：原圖 1080x1440 解得出來，純跳採樣縮到 540x720 就一個二值化器
     * 都認不出了；同一張用塊平均縮到 540x720 又解得出來。
     * 也就是說**縮小本身就會吃掉模組邊界**，而相機照片動輒四千像素寬。
     *
     * 所以先試小的（快、省記憶體），認不出再換大一檔重試。
     * 只有在「小檔失敗」時才付這個代價，正常情況第一檔就中。
     *
     * @return 至少一項；每一項都保證 w/sample * h/sample 不超過 [maxPixels]
     */
    fun sampleLadder(
        width: Int,
        height: Int,
        capPx: Int = PICKED_LONGEST_PX,
        maxPixels: Int = MAX_DECODE_PIXELS,
    ): List<Int> {
        if (width <= 0 || height <= 0 || capPx <= 0 || maxPixels <= 0) return listOf(1)
        val longest = maxOf(width, height)
        var smallest = 1
        while (longest / smallest > capPx) smallest *= 2
        // 從最小畫面（sample 最大）往原圖走，只收記憶體預算之內的檔次。
        return generateSequence(smallest) { if (it > 1) it / 2 else null }
            .filter { s ->
                val w = width.toLong() / s
                val h = height.toLong() / s
                w > 0 && h > 0 && w * h <= maxPixels
            }
            .toList()
            .ifEmpty { listOf(smallest) }
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
