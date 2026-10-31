package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数智体育页的「这份数据是什么时候的、这次为什么没取到」守卫。
 *
 * ## 这条测试在防什么
 *
 * 2026-10-31 给这个仓库加了磁盘快照之后（SztyRepository.kt 的 seedFromSnapshot），
 * 冷启动第一帧就画的是上一次的数据。快照本身是对的（快是快，seeded 的也不丢），
 * 但界面当时只画一行 `agoLabel(...)` —— 于是三种完全不同的处境在屏幕上长得一模一样：
 *
 * - 刚刚成功取过 → 「刚刚更新」
 * - 播的是上次存的 → 「3 小时前更新」  ← 读者无从知道这是**旧**的
 * - 播的是上次存的、且这次又失败了 → 「3 小时前更新」 ← 失败原因一个字都没有
 *
 * 第三行是最坏的：时间停在上一次成功的那一刻，看起来一切正常，
 * 而真实情况是刚发出去的请求刚失败（网络不通 / token 被顶下线 / 服务端 500）。
 * 等于替服务端把失败藏了起来。data/ListState.kt 早就有 `ListMeta.lastRefreshError`，
 * `failWith` 也一直在往里写，只是没人读。
 *
 * 对照写法是课表页 TimetableSectionPage.kt:1222-1228 那一支 `when`。
 */
class SztyFreshnessTest {

    private val path = "main/ui/szty/SztyPage.kt"

    private fun source(): String {
        val hit = SourceScan.sources().firstOrNull { it.first == path }
        assertTrue("扫不到 " + path + " —— 扫描器坏了，这条判据形同虚设", hit != null)
        return hit!!.second
    }

    /** 括号配平地切出某个函数的函数体（只切小段，别切整页：页面里有带花括号的模板串）。 */
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
        throw AssertionError("函数体没配平：" + signature)
    }

    private fun content(): String = bodyOf(source(), "fun androidx.compose.foundation.lazy.LazyListScope.sztyContent(")

    @Test
    fun `旧数据必须自己说出来，不许只画一个时间`() {
        val body = content()
        assertTrue(
            "新鲜度那一行没有 stale 分支 —— 播快照时读者看不出这是旧的",
            "meta?.stale == true -> \" · 这是上次存的\"" in body,
        )
    }

    @Test
    fun `正在重新取与这是上次存的是两件事，且先判正在取`() {
        val body = content()
        val refreshing = body.indexOf("meta?.refreshing == true -> \" · 正在重新取\"")
        val stale = body.indexOf("meta?.stale == true -> \" · 这是上次存的\"")
        assertTrue("没有 refreshing 分支 —— 转圈的时候那一行不说话", refreshing >= 0)
        assertTrue("没有 stale 分支", stale >= 0)
        assertTrue(
            "两个分支顺序反了：seeded 的快照本来 stale=true，刷新中会一直说「这是上次存的」",
            refreshing < stale,
        )
    }

    @Test
    fun `这次刷新失败必须把原因写出来，不能只留一个时间`() {
        val body = content()
        assertTrue(
            "没有读 meta.lastRefreshError —— 有旧内容时刷新失败会被完全藏起来",
            "meta?.lastRefreshError?.let" in body,
        )
        assertTrue(
            "失败那句没带上真正的原因 —— 只写「刷新失败」等于不说",
            "problem.message" in body || "problem.userText" in body,
        )
        // ★ 真机实测（2026-10-31 23:10，网络被 iptables 掐断）：用 userText 时那句话变成
        //   「SocketTimeoutException: failed to connect to /127.79.51.164 (port 41355) from
        //    /127.0.0.1 (port 58748) after 20000ms」，四行红字，读的人还得自己翻译。
        //   所以默认必须是 message（仓库侧已写成「体测：连不上数智体育」这种人话），
        //   只有 message 为空才退回 userText。
        assertTrue(
            "优先用 userText 会把最深一层异常的原文整段糊上来",
            body.indexOf("problem.message") in 0 until body.indexOf("problem.userText"),
        )
        assertTrue(
            "失败那句要说明「上面这些是上次取到的」，否则读者以为这些数是刚失败的",
            "上次取到的" in body,
        )
    }

    @Test
    fun `失败提示只加一句话，内容照样画出来`() {
        // 内容还在的时候不许因为「这次失败了」就整块不画：失败提示是**追加**的一句，
        // 不是替代品。判据：失败提示的 key 排在校园跑卡之前，且跑卡仍然存在。
        val body = content()
        val problem = body.indexOf("item(key = \"refresh-problem\")")
        val runCard = body.indexOf("item(key = \"run-card\")")
        assertTrue("没有 refresh-problem 这一项", problem >= 0)
        assertTrue("有失败提示却把校园跑卡弄丢了", runCard >= 0)
        assertTrue("失败提示排到了内容后面 —— 读者要滚到底才知道这次没说成", problem < runCard)
        assertFalse(
            "失败提示前面不许有 return：那会让整页只剩一句失败",
            "return" in body.substring(body.indexOf("item(key = \"freshness\")"), problem),
        )
    }
}
