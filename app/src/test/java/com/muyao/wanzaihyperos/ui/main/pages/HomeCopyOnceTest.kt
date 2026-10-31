package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页「同一件事只说一遍」的守卫。
 *
 * ## 这条测试在防什么
 *
 * 2026-10-31 21:44 真机 dump（周六、今天一节没有课）里，首页同屏出现了：
 *
 * ```
 * 今天的课上完了 · 这是上次存的        ← 天空副行
 * 今天没有课                          ← 「此刻」卡大标题
 * 下一节：周一 第6-9节 B/S架构应用开发   ← 「此刻」卡副行
 * 下一节：周一 第6-9节 B/S架构应用开发   ← 时间线卡，逐字相同
 * ```
 *
 * 三件事，两个问题：
 *
 * 1. **「今天的课上完了」是假话**。它由 `remainingCount == 0` 推出，而周六
 *    今天**压根没课**；「都上完了」和「本来就没有」对读的人不是同一件事。
 * 2. **「下一节」在同一屏说了两遍**，逐字相同。同一句话出现两次，读起来
 *    像两个独立的结论，反而让人怀疑哪个是真的。
 *
 * 判据钉三件事：副行必须按「今天有几节」而不是「还剩几节」分叉、
 * 时间线卡不再吃 `plan`（它复述的就是 plan.detail）、没课时那张卡不渲染。
 */
class HomeCopyOnceTest {

    private val path = "main/ui/main/pages/HomeSectionPage.kt"

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
    fun `天空副行按今天有几节分叉，不是按还剩几节`() {
        val body = bodyOf(source(), "private fun HomeSkyHeader(")
        assertTrue("副行里没有 todayCount == 0 那一支 —— 周六又会说「今天的课上完了」",
            body.contains("todayCount == 0 ->"))
        val zero = body.indexOf("todayCount == 0 ->")
        val done = body.indexOf("remainingCount == 0 ->")
        assertTrue("「都上完了」必须排在「今天没课」之后判 —— 反了就先撞上假话", done > zero)
    }

    @Test
    fun `调用点真的把今天的节数传进去了`() {
        // 只切 hero 那一小段：整页的作用域切法会被页面里那些带花括号的模板串搅坏，
        // 而这条判据要的就只是「那一处调用点」，不必赌括号。
        val src = source()
        val at = src.indexOf("item(key = \"hero\")")
        assertTrue("首页里找不到 hero 那一项", at >= 0)
        val seg = src.substring(at, minOf(at + 700, src.length))
        assertTrue("调用 HomeSkyHeader 时没传 todayCount —— 新形参等于骗人的",
            seg.contains("todayCount = todaySlots.size"))
    }

    @Test
    fun `时间线卡不再复述 plan`() {
        val body = bodyOf(source(), "private fun TodayTimelineCard(")
        assertFalse("时间线卡又吃回 plan 了 —— 它复述的就是 plan.detail，会和「此刻」卡逐字重复",
            body.contains("plan: TodayPlan?"))
        assertFalse("时间线卡里还在画 plan.detail",
            body.contains("plan?.detail"))
    }

    @Test
    fun `今天真没课时那张卡不渲染`() {
        val src = source()
        assertTrue("没有 timelineHasNothingToSay 这道闸门 —— 空白卡会照旧占着一行高度",
            src.contains("val timelineHasNothingToSay ="))
        val gate = src.indexOf("if (!timelineHasNothingToSay) item(key = \"timeline\")")
        assertTrue("时间线卡的 item 没套上闸门", gate >= 0)
        // 闸门必须在「此刻」卡之后 —— 副行那句「今天没有课」得先画出来，
        // 否则这一屏关于今天一个字都没有。位置判据比字符串更结实。
        val focus = src.indexOf("item(key = \"focus\")")
        assertTrue("闸门排到「此刻」卡前面去了", focus in 0 until gate)
    }

    @Test
    fun `删掉的是那一句，不是诚实的三句`() {
        // 时间线卡里那三句「还在取 / 没取到 / 不知道第几周」是**不许被顺手删掉**的：
        // 它们各自对应一种「现在还不能下结论」，删掉就只剩一句假装确定的空话。
        val body = bodyOf(source(), "private fun TodayTimelineCard(")
        assertTrue("「正在取得课表…」没了", body.contains("正在取得课表…"))
        assertTrue("「课表没取到…」没了", body.contains("课表没取到，暂时画不出今天的时间线。"))
        assertTrue("「还不知道目前是第几教学周…」没了", body.contains("还不知道目前是第几教学周"))
    }
}
