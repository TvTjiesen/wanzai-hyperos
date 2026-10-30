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
    fun `页面必须把分组交给可切换的胶囊`() {
        val src = source()
        assertTrue("页面没有渲染分组胶囊 —— 用户只能看门户排在第一个的那个分组",
            src.contains("MsgGroupChips("))
        assertTrue("分组胶囊没有把选中的分组回送给仓库（selectGroup）",
            src.contains("graph.messages.selectGroup(g)"))
        val body = bodyOf(src, "private fun MsgGroupChips(")
        assertTrue("MsgGroupChips 没有 groups 形参：" + body.substringBefore("{"),
            Regex("groups\\s*:\\s*List<PortalMessageGroup>").containsMatchIn(body.substringBefore("{")))
    }

    @Test
    fun `选中态按分组名字比而不是按位置`() {
        val body = bodyOf(source(), "private fun MsgGroupChips(")
        // 分组的标识就是 messagegroup 这个名字，不是数字 id（data/portal/PortalModels.kt）。
        // 按索引比会在服务端重排分组之后把「选中」错挂到别的分组上。
        assertTrue("选中态不是按名字比的：选中会挂到重排后的另一个分组上：" + body,
            body.contains("selected?.name == g.name"))
        assertTrue("分组胶囊丢了未读数（noread）—— 那正是用户用来判断该看哪一组的信息",
            body.contains("g.unread"))
    }

    @Test
    fun `只有一个分组时不占一行高度`() {
        val body = bodyOf(source(), "private fun MsgGroupChips(")
        assertTrue("只有一个分组时仍画一排单选胶囊，白占一行高度：" + body,
            body.contains("if (groups.size < 2) return"))
    }
}
