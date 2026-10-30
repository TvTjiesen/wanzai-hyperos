package com.muyao.wanzaihyperos.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 網頁「選檔案上傳」的接線守衛。
 *
 * 為什麼要有這條：`<input type="file">` 在 WebView 裡要是不覆寫 `onShowFileChooser`，
 * 點下去**什麼都不會發生** —— 沒有報錯、沒有對話框。校園網裡傳附件的地方很多
 * （請假、報修、助學貸款回執），這種「靜默失效」只能靠守衛釘住。
 *
 * 2026-10-30 巡察時 `grep -rn onShowFileChooser app/src` = 0 命中。
 */
class WebFileChooserWiringTest {

    private val root: File = File(System.getProperty("user.dir")).let { run ->
        val parent = run.parentFile
        if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else run
    }

    private val page: String by lazy {
        File(root, "app/src/main/java/com/muyao/wanzaihyperos/ui/web/WebShellPage.kt")
            .readText()
    }

    @Test
    fun `网页的文件选择必须被覆写`() {
        assertTrue(
            "WebShellPage 没有覆写 onShowFileChooser：页面里的 <input type=\"file\"> 点了会毫无反应",
            page.contains("override fun onShowFileChooser("),
        )
        // 參數型別也釘住：寫成非空型別再判 null 是不成立的，Chromium 傳進來可能為 null。
        assertTrue(
            "onShowFileChooser 的 filePathCallback 必須是可空的 ValueCallback<Array<Uri>>",
            page.contains("filePathCallback: ValueCallback<Array<Uri>>?,"),
        )
    }

    @Test
    fun `回调必须每次都回话否则那个 input 永远卡住`() {
        val at = page.indexOf("override fun onShowFileChooser(")
        assertTrue("找不到 onShowFileChooser", at >= 0)
        val body = page.substring(at, minOf(page.length, at + 1400))
        assertTrue(
            "onShowFileChooser 里没有把回调存起来（pendingFileCallback = filePathCallback）—— 不存就没法在选完之后回话，那个 file input 会永远卡住",
            body.contains("pendingFileCallback = filePathCallback"),
        )
        assertTrue(
            "上一次没回话的回调没有先收掉（pendingFileCallback?.onReceiveValue(null)）",
            body.contains("pendingFileCallback?.onReceiveValue(null)"),
        )
    }

    @Test
    fun `选完或取消都要回一句`() {
        val at = page.indexOf("val fileChooserLauncher = rememberLauncherForActivityResult(")
        assertTrue("找不到 fileChooserLauncher", at >= 0)
        val body = page.substring(at, minOf(page.length, at + 900))
        assertTrue(
            "文件选择的结果没交给 FileChooserParams.parseResult（多选 / 取消 / 没 data 都要由它兜）",
            body.contains("FileChooserParams.parseResult("),
        )
        assertTrue(
            "选完之后没有回调 onReceiveValue —— 页面那边收不到任何东西",
            body.contains("callback?.onReceiveValue("),
        )
    }
}
