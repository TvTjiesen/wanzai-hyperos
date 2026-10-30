package com.muyao.wanzaihyperos.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 死页台账：定义了却**没有任何调用点**的页面，必须逐个登记。
 *
 * ## 为什么要台账
 *
 * 工单第 24 轮抓到的一条真问题：`WanzaiRoot.kt:421` 算出来的 `unreadCount` 全工程没人用，
 * 而承载它的 `MessagesPage` 与「我的」页 `MinePage` **都只有定义没有调用点** ⇒
 * 「未读」在界面上根本不显示。这类页面在代码里看起来是"已实现的功能"，
 * 读代码的人（包括下一轮的我）会以为它接好了 —— 台账的作用就是**让这件事写在明面上**。
 *
 * 死页本身不是罪（有些是等着接线的），**没登记的死页**才是。
 * 所以这条守卫是双向的：
 *  - 源码里新出现一个死页 ⇒ 红（必须登记）
 *  - 台账里登记的死页被接上了 / 被删了 ⇒ 红（台账不能撒谎）
 *
 * ## 处置约定
 *  - `MessagesPage`：**不许删**（B4-12 的小球要吃未读角标，接线前置就是它）；
 *  - `MinePage`：主题入口已摘（B4-14 ④），本体留着待接线；
 *  - `AppsPage`：门户应用目录旧实现，2026-10-30 已连同上文 `toUiGroup`/`toUiGroups`/`AppTile` 一并删除。
 */
class DeadPageRegistryTest {

    private data class Dead(val file: String, val name: String, val why: String)

    private val registry = listOf(
        Dead(
            "main/ui/messages/MessagesPage.kt",
            "MessagesPage",
            "B4-12：未读角标与消息页实现在这里 ⇒ 小球接线前不许删",
        ),
        Dead(
            "main/ui/mine/MinePage.kt",
            "MinePage",
            "B4-14 ④：主题入口已摘（唯一入口在外观页）；本体留着待接线",
        ),
    )

    private val pageDef = Regex("""@Composable\s+(?:@\w+\s+)*fun\s+(\w*Page)\s*\(""")

    private fun deadPages(): Set<Pair<String, String>> {
        val sources = SourceScan.sources()
        val dead = mutableSetOf<Pair<String, String>>()
        for ((path, code) in sources) {
            for (name in pageDef.findAll(code).map { it.groupValues[1] }.toSet()) {
                val calledElsewhere = sources
                    .filter { it.first != path }
                    .any { (_, other) -> Regex("""\b${Regex.escape(name)}\s*\(""").containsMatchIn(other) }
                if (!calledElsewhere) dead += path to name
            }
        }
        return dead
    }

    @Test
    fun `死页台账和源码一致`() {
        val found = deadPages()
        val declared = registry.map { it.file to it.name }.toSet()

        val undeclared = found - declared
        assertTrue(
            "这些页面定义了却没有调用点，但台账里没登记（要么接上，要么登记）：$undeclared",
            undeclared.isEmpty(),
        )
        val stale = declared - found
        assertTrue(
            "台账里登记的死页，源码里已经不是死页了（被接上了或被删了）⇒ 台账要改：$stale",
            stale.isEmpty(),
        )
    }

    @Test
    fun `每条登记都要写清为什么留着`() {
        registry.forEach { entry ->
            assertTrue("${entry.name} 没写为什么留着", entry.why.isNotBlank())
            assertTrue("${entry.name} 的文件不存在：${entry.file}", File("src/main/java/com/muyao/wanzaihyperos/" + entry.file.removePrefix("main/")).isFile)
        }
    }

    /**
     * `MessagesPage` 里的未读通道是 B4-12 的前置：B4-12 的小球要显示未读，
     * 前置是「把已有 state 接进界面」，而 `unreadCount` 这个形参就是那个 state 的入口。
     * 接线之前它必须原样留着 —— 谁把这一行删了，B4-12 就得重新造一遍。
     */
    @Test
    fun `消息页的未读通道还在`() {
        val page = File("src/main/java/com/muyao/wanzaihyperos/ui/messages/MessagesPage.kt")
        assertTrue("找不到消息页", page.isFile)
        val code = SourceScan.stripComments(page.readText(Charsets.UTF_8))
        assertTrue("消息页的未读形参不见了（B4-12 的前置）", "unreadCount" in code)
    }
}
