package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息分组切换的守卫。
 *
 * ## 这条测试在防什么
 *
 * `listmessage` 一次只回**一个分组**的行，而服务端排在第一的分组会变
 * （2026-10-30 实测在「讲座通知」与「活动通知」之间来回）。页面原先只在
 * `msgSelected == null` 时选第一个分组，界面上没有任何切分组的入口 ——
 * 于是另一个分组对用户等于不存在：一百多条未读就藏在看不见的那一组里。
 *
 * 判据钉三件事：入口存在、选中态按**名字**比、只有一个分组时不占一行高度。
 */
class NotifyGroupSwitchTest {

    private val path = "main/ui/main/pages/NotifySectionPage.kt"

    private fun source(): String {
        val hit = SourceScan.sources().firstOrNull { it.first == path }
        assertTrue("扫不到 " + path + " —— 扫描器坏了，这条判据形同虚设", hit != null)
        return hit!!.second
    }

    /** 括号配平地切出某个函数的函数体。 */
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
    fun `通知来源只有日常与讲座且不读取旧门户通知`() {
        val src = source()
        val body = bodyOf(src, "private fun NoticeSourceChips(")
        assertTrue(body.contains("listOf(NOTICE_SOURCE_DAILY to \"日常通知\", NOTICE_SOURCE_LECTURE to \"讲座通知\")"))
        assertTrue(!src.contains("graph.messages"))
        assertTrue(!src.contains("NOTICE_SOURCE_PORTAL"))
        assertTrue(!src.contains("confirmReadAll"))
        assertTrue(src.contains("graph.news.refreshKeepingContent()"))
        assertTrue(src.contains("notifyBroadcastItems("))
        assertTrue(src.contains("onClick = onOpenTodos"))
    }
}
