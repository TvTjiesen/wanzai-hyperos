package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #58 第 5 条「消息：黑 tab + 大白卡，应为小胶囊 + hairline 行」的守卫。
 *
 * ## 这条测试在防什么
 *
 * 板上筛选胶囊 `.chip .n{opacity:.62}` —— 计数比标签淡一档，因为计数字会变、标签不变。
 * 曾经的实现把标签和计数拼成一个字符串塞进同一个 Text（`pillLabel(fixed, n) = fixed + " " + n`），
 * 于是这层信息整个丢了：屏幕上看不出哪个是名字哪个是数。
 *
 * 判据只有一条硬的：**计数必须是自己的一个 Text，而且带弱化**。谁把两者再拼回去，这里就红。
 */
class NotifyPillCountTest {

    private val path = "main/ui/main/pages/NotifySectionPage.kt"

    private fun source(): String {
        val hit = SourceScan.sources().firstOrNull { it.first == path }
        assertTrue("扫不到 " + path + " —— 扫描器坏了，这条判据形同虚设", hit != null)
        return hit!!.second
    }

    /** 括号配平地切出某个函数的函数体（用来只看 FilterPill 自己）。 */
    private fun bodyOf(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("找不到 " + signature + " —— 这条判据就失效了", start >= 0)
        var i = src.indexOf('{', start)
        var depth = 0
        var inString = false
        while (i < src.length) {
            val c = src[i]
            if (inString) {
                if (c == '\\') { i += 2; continue }
                if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return src.substring(start, i + 1)
                    }
                }
            }
            i += 1
        }
        throw AssertionError("函数体没闭合：" + signature)
    }

    @Test
    fun `筛选胶囊必须有独立的计数参数`() {
        val body = bodyOf(source(), "private fun FilterPill(")
        val sig = body.substringBefore("{")
        assertTrue("FilterPill 没有 count 形参，计数只能拼进标签 ⇒ 无法单独弱化：" + sig,
            Regex("count\\s*:\\s*String").containsMatchIn(sig))
    }

    @Test
    fun `计数必须比标签淡一档`() {
        val body = bodyOf(source(), "private fun FilterPill(")
        assertTrue("FilterPill 里找不到 0.62f 的弱化（板上 .chip .n{opacity:.62}）：" + body,
            body.contains("0.62f"))
        val countText = body.indexOf("text = count")
        assertTrue("FilterPill 里没有 text = count 这个独立的 Text", countText >= 0)
        val tail = body.substring(countText)
        assertTrue("计数那个 Text 没挂 0.62f 的 alpha：" + tail.take(300), tail.contains("0.62f"))
    }

    @Test
    fun `不许再把标签和计数拼成一个字符串`() {
        val src = source()
        assertEquals("又出现了 pillLabel 拼接（标签与计数必须各是一个 Text）：" + src.contains("pillLabel"),
            false, src.contains("pillLabel"))
        val body = bodyOf(src, "private fun NotifyFilters(")
        val calls = Regex("FilterPill\\(\\s*\"([^\"]*)\"\\s*,\\s*([^,\\n]+)").findAll(body).toList()
        assertEquals("四颗筛选胶囊必须都是 FilterPill(标签, 计数)：" + calls.size, 4, calls.size)
    }

    @Test
    fun `计数取不到时仍然写破折号而不是 0`() {
        val body = bodyOf(source(), "private fun NotifyFilters(")
        assertTrue("计数为 null 时必须写「—」而不是 0（data/ListState.kt 红线）：" + body,
            body.contains("\"—\""))
    }
}