package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 应用页「整理」开关的守卫（2026-11-02 真机缺陷）。
 *
 * ## 缺陷原文
 *
 * 应用页同一屏上有两颗都写着「整理」的开关：顶栏右上那颗（壳画的
 * [AppsTidyButton]）与「常用」区块行尾那颗。两颗读写同一个 `UiPrefs.appsTidy`，
 * 但顶栏那颗的字**恒为「整理」**，页内那颗才写「完成/整理」。
 * 于是整理中顶栏仍写「整理」，按下去却是退出整理 —— 标签在替自己撒谎。
 *
 * ## 判据
 *
 * 一页只留一颗，且它的字必须跟着状态走。这两件事都是**结构性**的，
 * 所以用源码扫描而不是截图：截图只能证明拍照那一刻对。
 */
class AppsTidyOnceTest {

    private val pageFile =
        "src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppsSectionPage.kt"
    private val shellFile =
        "src/main/java/com/muyao/wanzaihyperos/ui/main/WanzaiMainShell.kt"

    private fun code(path: String): String {
        val f = File(path)
        assertTrue("找不到源码：$path", f.isFile)
        return SourceScan.stripComments(f.readText(Charsets.UTF_8))
    }

    /** 从 [anchor] 起截 [chars] 个字符 —— 页面函数有几千行，整段配平括号会被模板串搅坏。 */
    private fun sliceAfter(text: String, anchor: String, chars: Int): String {
        val i = text.indexOf(anchor)
        assertTrue("锚点没找到：$anchor", i >= 0)
        return text.substring(i, (i + chars).coerceAtMost(text.length))
    }

    // ---- 1. 一页只留一颗 ---------------------------------------------------
    @Test
    fun `应用页里只许有一颗整理开关`() {
        val page = code(pageFile)
        val toggle = "if (tidy) \"完成\" else \"整理\""
        assertEquals(
            "「完成/整理」只在顶栏那颗 [AppsTidyButton] 里出现一次；多出来的一颗就是又长出第二颗开关了",
            1,
            Regex(Regex.escape(toggle)).findAll(page).count(),
        )
        // 那颗必须在 AppsTidyButton 体内，不是随便哪个函数里。
        val fn = sliceAfter(page, "fun AppsTidyButton(", 900)
        assertTrue("顶栏那颗的字应当跟着状态走：$fn", fn.contains(toggle))
    }

    // ---- 2. 壳把状态传进去了，而且是按值读 --------------------------------
    @Test
    fun `壳把状态传进顶栏那颗，并且按值读`() {
        val shell = code(shellFile)
        val call = sliceAfter(shell, "AppsTidyButton(", 260)
        assertTrue("壳应当把当前状态传进去：$call", call.contains("tidy = UiPrefs.appsTidy.value"))
        // `UiPrefs.appsTidy` 是 MutableState<Boolean>，少写 .value 编译就过不去；
        // 这条钉的是「别再退回无参调用」。
        assertFalse("顶栏那颗不许再无线程调用（它必须知道现在是整理还是整理完）：$call", call.contains("AppsTidyButton(onClick"))
    }
}
