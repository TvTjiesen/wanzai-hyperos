package com.muyao.wanzaihyperos.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未知不得当成 0（数量类字段）。
 *
 * ## 为什么这条守卫存在
 *
 * `data/ListState.kt` 的红线原文：「★ 另一条红线：**未知不得当成 0**。数量类字段
 * （未读数、待办数）取不到时必须是 `null` 而不是 0 —— 界面上显示「—」，而不是「0 条未读」。」
 *
 * 2026-10-24 的现场：访客态首页「要处理」右侧是 **0**，下面同时写着「正在整理待办…」，
 * 40 秒不收敛；消息页四颗筛选药丸把没取到的门户/新闻也印成 0。数据层其实是对的
 * （`TodoBoard.knownTotal` 任一来源未知就回 null、`TodoGroup.countText` 失败给「—」），
 * **是界面把 `as? ListState.Content` 之后又接了一个 `?: 0`**，把「不知道」压成了「没有」。
 *
 * ## 这条守卫钉的是什么
 *
 * 两种把未知压成 0 的写法，出现在 `main/ui/` 下就是红：
 *  1. 同一行里 `as? ListState.Content` 之后的兜底值是 `0`；
 *  2. `isEmpty()` 直接决定显示 `"0"`。
 *
 * 只管界面层：数据层的 `?: 0` 里合法的多（例如 `HzcuCookieJar.countFor` 数 cookie），
 * 那是已知的 0，不是「不知道」。
 */
class UnknownCountGuardTest {

    /** 「Content 取不到就 0」：把状态机的五态压成二态，最典型的一处。 */
    private val contentFallbackToZero = Regex("""as\?\s*ListState\.Content[^\n]*\?:\s*0\b""")

    /** 「空就当 0 件」：`it.isEmpty()) "0"` 这种直接拿 emptiness 冒充数量。 */
    private val emptyMeansZero = Regex("""\.isEmpty\(\)\)\s*"0"""")

    private val uiSources by lazy {
        SourceScan.sources()
            .filter { it.first.startsWith("main/ui/") }
            .map { (path, code) -> path to SourceScan.stripComments(code) }
    }

    @Test
    fun `扫描器必须真的扫到货`() {
        assertTrue(
            "一个界面文件都没扫到，下面的判据全是空转（扫描器坏了，不是世界干净了）：${uiSources.size}",
            uiSources.size >= 20,
        )
    }

    @Test
    fun `不许把没取到的数量显示成 0`() {
        val bad = mutableListOf<String>()
        for ((path, code) in uiSources) {
            code.lineSequence().forEachIndexed { i, line ->
                if (contentFallbackToZero.containsMatchIn(line)) {
                    bad += "$path:${i + 1} ${line.trim()}"
                }
                if (emptyMeansZero.containsMatchIn(line)) {
                    bad += "$path:${i + 1} ${line.trim()}"
                }
            }
        }
        assertTrue(
            "「未知」被画成了「0」。数量取不到时用 null / CountValue.Unknown，界面写「—」：" +
                bad.joinToString("\n"),
            bad.isEmpty(),
        )
    }

    @Test
    fun `未知要有一个能说「—」的入口`() {
        // 光禁止还不够：得确认界面上确实存在「—」这个兜底，否则改了 null 也只是画个空。
        val hasDashFallback = uiSources.any { (_, code) -> code.contains("\"—\"") }
        assertTrue("界面里一处「—」的兜底都没有，这条红线还是没落地", hasDashFallback)
    }
}
