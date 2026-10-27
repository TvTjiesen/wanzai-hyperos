package com.muyao.wanzaihyperos.ui.design.ai

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 接线守卫：**五个板块各有一只球，谁掉线就红**。
 *
 * 为什么用源码守卫而不是截图：球是 36dp 的静态图形，截图只能证明「这一帧这颗在」，
 * 证明不了「明天有人新加一页时还记得给它一只脸」。而「每页一只」是业主 2026-10-25 的原话
 * （「每个页面最好都设计一套」），漏掉一页就是漏掉一个页面 —— 这个缺陷只有把五处写进
 * 判据才拦得住。
 *
 * 2026-10-25 业主又改了一条口径：「小球不要单独一个图层，要与其他内容同层、并且可以变化位置」。
 * 于是球从顶栏 `actions` 挪进了内容层 —— 四个页面的球现在挂在各自大标题的左边
 * （`largeTitleItem(leading = …)` / 课表页自己那行 `AiBallRow`），跟着列表一起滚。
 * 这条守卫因此也反过来钉死：**壳里不许再出现球**，否则就是又长出一枚钉在顶栏的徽章。
 */
class AiBallWiringTest {

    private val base = "src/main/java/com/muyao/wanzaihyperos/ui"

    private fun code(rel: String): String {
        val f = File("$base/$rel")
        assertTrue("找不到源文件：$f", f.isFile)
        // 先去注释再判：KDoc 里写着「球放在这里」不算接上了。
        return SourceScan.stripComments(f.readText(Charsets.UTF_8))
    }

    @Test
    fun 四个内容页各自在标题左边挂一只球() {
        // 消息 / 应用 / 设置走共享的大标题（leading 槽），课表页自己画标题所以用 AiBallRow。
        val viaLargeTitle = mapOf(
            "消息" to "main/pages/NotifySectionPage.kt",
            "应用" to "main/pages/AppsSectionPage.kt",
            "设置" to "main/pages/SettingsSectionPage.kt",
        )
        viaLargeTitle.forEach { (name, rel) ->
            val src = code(rel)
            assertTrue(
                "$name 页的大标题没有把小球当 leading —— 那一页就没有脸了",
                src.contains("largeTitleItem(pilot, leading = { AiBall(state = ballState, hitSlop = AiBall.INLINE_SLOP) })"),
            )
        }
        val timetable = code("main/pages/TimetableSectionPage.kt")
        assertTrue(
            "课表页的标题没有用 AiBallRow 把小球的字排在标题左边",
            timetable.contains("AiBallRow(state = ballState"),
        )
    }

    @Test
    fun 每页用的是自己的那份数据() {
        // 五页各读各的仓库。写成同一句（例如都 ofCount(...)）说明有人复制粘贴没改。
        val expected = mapOf(
            "main/pages/TimetableSectionPage.kt" to "AiBallStates.ofTimetable(",
            "main/pages/NotifySectionPage.kt" to "AiBallStates.ofCount(",
            "main/pages/AppsSectionPage.kt" to "AiBallStates.ofList(",
            "main/pages/SettingsSectionPage.kt" to "AiBallStates.ofAccounts(",
        )
        expected.forEach { (rel, call) ->
            val src = code(rel)
            assertTrue("$rel 应该用 $call —— 那一页唯一说得出口的事实就是它", src.contains(call))
            assertTrue("$rel 算出来的脸色没交给 ballState，球会停在默认值", src.contains("val ballState = $call"))
        }
    }

    @Test
    fun 球在内容层里而不是钉在顶栏() {
        val shell = code("main/WanzaiMainShell.kt")
        assertTrue(
            "壳里又出现 AiBall —— 业主 2026-10-25「小球不要单独一个图层」，顶栏不该有球",
            !shell.contains("AiBall("),
        )
        assertTrue("壳里还留着旧的状态槽（AiBallSlot）", !shell.contains("AiBallSlot"))
        // 旧的 ProvideAiBall 写槽机制必须彻底消失，否则会有人以为「写槽才是接上」。
        listOf(
            "main/pages/NotifySectionPage.kt",
            "main/pages/AppsSectionPage.kt",
            "main/pages/SettingsSectionPage.kt",
            "main/pages/TimetableSectionPage.kt",
        ).forEach { rel ->
            assertTrue("$rel 还在用旧的 ProvideAiBall 写槽", !code(rel).contains("ProvideAiBall("))
        }
        assertTrue(
            "AiBallSlot.kt 已经没有任何调用方了，文件应该删掉",
            !File("$base/design/ai/AiBallSlot.kt").isFile,
        )
    }

    @Test
    fun 首页的球在标题区而不是顶栏() {
        val home = code("main/pages/HomeSectionPage.kt")
        assertTrue("首页没有 AiBall", home.contains("AiBall("))
        assertTrue("首页球的状态没用 ofHome 算", home.contains("AiBallStates.ofHome("))
        // 第 0 期：球从卡内 36dp 搬到标题区左侧当 96dp 主视觉（B4-12-BALL-AS-CORE §2）。
        assertTrue(
            "首页的球没放大到主视觉档 —— 它现在是首页唯一一张脸，不是角落里的装饰",
            home.contains("diameter = AiBall.DIAMETER_FIELD"),
        )
        // 一处状态只在屏幕上一个地方说：卡内那颗已经删了，不能再冒出来。
        assertTrue(
            "卡里还留着一颗球 —— 第 0 期把球搬到了标题区，屏幕上只该有一张脸",
            !home.contains(".align(Alignment.BottomEnd).padding(Dims.cardPadding - AiBall.HIT_SLOP)"),
        )
    }

    @Test
    fun 没有呼吸没有常驻动画进程() {
        // 业主 2026-10-25 第一条：「删掉放大缩小（呼吸）动画」。
        val src = code("design/ai/AiBall.kt")
        assertTrue("AiBall 里又出现了无限动画（呼吸的写法）", !src.contains("rememberInfiniteTransition"))
        assertTrue("AiBall 里又出现了 infiniteRepeatable", !src.contains("infiniteRepeatable"))
        // 动作是「一次一个、做完就停」，靠 actOnce 排队，不靠无限过渡。
        assertTrue("动作制被改回去了：找不到 actOnce", src.contains("suspend fun actOnce("))
    }

    @Test
    fun 大标题的leading槽还在() {
        val ios = code("design/liquid/IosChrome.kt")
        assertTrue("LargeTitle 丢了 leading 参数，三页的球会没地方挂", ios.contains("leading: (@Composable () -> Unit)? = null"))
        val pilot = code("design/liquid/PageChromePilot.kt")
        assertTrue("largeTitleItem 没把 leading 透传给 LargeTitle", pilot.contains("leading = leading"))
    }
}
