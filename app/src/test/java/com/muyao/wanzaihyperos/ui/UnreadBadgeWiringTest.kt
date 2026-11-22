package com.muyao.wanzaihyperos.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏「消息」那颗 tab 的未读角标接线守卫。
 *
 * 为什么要钉这一段：`WanzaiRoot.kt` 里 `unreadCount` 算出来之后，有很长一段时间
 * **一个消费点都没有**（DeadPageRegistryTest 头注释把这件事记成「工单第 24 轮抓到的
 * 一条真问题」）—— 未读数取了、缓存了、就是不上屏。这种「算了不用」的接线断点，
 * 编译和运行都不报错，只有人肉盯着源码才看得出来，所以钉成测试。
 */
class UnreadBadgeWiringTest {

    private val root = File("src/main/java/com/muyao/wanzaihyperos")

    private fun text(rel: String): String = File(root, rel).readText()

    @Test
    fun `底栏消息那颗 tab 上的角标真的接到了未读数`() {
        val shell = text("ui/main/WanzaiMainShell.kt")
        assertTrue(
            "WanzaiMainShell 没有 unreadCount 这个形参 —— 未读数传不进底栏",
            Regex("""unreadCount\s*:\s*Int\s*=""").containsMatchIn(shell),
        )
        assertTrue(
            "底栏没有把 unreadCount 挂到 MainSection.Notify 上",
            Regex("""badge\s*=\s*if\s*\(\s*it\s*==\s*MainSection\.Notify\s*&&\s*UiPrefs\.themeCustomization\.showNavigationBadges\s*\)\s*unreadCount\s+else\s+0""")
                .containsMatchIn(shell),
        )
    }

    @Test
    fun `根导航把未读数交给了主壳`() {
        val rootText = text("WanzaiRoot.kt")
        assertTrue(
            "WanzaiRoot 没有把 unreadCount 传给 WanzaiMainShell —— 算了不用，底栏永远是空的",
            Regex("""unreadCount\s*=\s*unreadCount\s*\?:\s*0""").containsMatchIn(rootText),
        )
    }

    @Test
    fun staleUnreadDoesNotKeepAFalseFreshBadge() {
        val source = text("WanzaiRoot.kt")
        assertTrue(source.contains("if (unreadState.stale) null"))
        assertTrue(source.contains("takeIf { it >= 0 }"))
    }

    @Test
    fun `角标 0 不画、超过 99 折成 99加`() {
        val nav = text("ui/design/liquid/LiquidGlassNavigationBar.kt")
        assertTrue(
            "底栏角标没有「0 就不画」这道闸 —— 未知未读数会被画成一个 0",
            Regex("""if\s*\(\s*badge\s*>\s*0\s*\)""").containsMatchIn(nav),
        )
        assertTrue(
            "角标没有 99 以上的折叠，三位数会把 14dp 的圆撑破",
            Regex("""n\s*>\s*99""").containsMatchIn(nav),
        )
    }
}
