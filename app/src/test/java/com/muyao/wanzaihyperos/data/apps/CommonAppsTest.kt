package com.muyao.wanzaihyperos.data.apps

import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.PortalSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用页「常用」那一排的算法（v3 清单 #7，业主 2026-10-22：
 * 「应用『常用』最多 4 格，默认算法自动匹配 + 支持自定义」）。
 *
 * 这里守的是两条硬判据：
 * ① **恒 ≤4 格**（信号再多也不许多出第五格）；
 * ② 每一格都归得清"为什么在"（[CommonReason]），界面上那句说明就是从这里拼的，
 *    不许再出现旧版那种写死的「最近使用」—— 那一版根本没记过打开，是在撒谎。
 */
class CommonAppsTest {

    private val sys = PortalSystem.entries.first()

    private fun app(id: Int, name: String = "app$id") =
        PortalApp(id = id, name = name, url = "https://example/$id", system = sys)

    private val pool = (1..12).map { app(it) }

    @Test
    fun `最多四格，信号再多也不许出第五格`() {
        val picks = pickCommon(
            pool,
            CommonSignals(
                pinnedIds = listOf(7, 3, 9, 11, 2),
                liveIds = setOf(4, 5),
                schoolFrequentIds = listOf(6, 8, 10),
                openCounts = mapOf(1 to 30),
            ),
        )
        assertEquals(4, picks.size)
        assertEquals(listOf(7, 3, 9, 11), picks.map { it.app.id })
    }

    @Test
    fun `档位先到先得，同一格不重复归两档，第四格才落到目录顺序补位`() {
        val picks = pickCommon(
            pool,
            CommonSignals(
                pinnedIds = listOf(2),
                liveIds = setOf(2, 3),
                schoolFrequentIds = listOf(2, 3, 4),
                openCounts = mapOf(2 to 9),
            ),
        )
        assertEquals(listOf(2, 3, 4, 1), picks.map { it.app.id })
        assertEquals(
            listOf(
                CommonReason.PINNED,
                CommonReason.LIVE,
                CommonReason.SCHOOL,
                CommonReason.FALLBACK,
            ),
            picks.map { it.reason },
        )
    }

    @Test
    fun `没钉没事项时按学校名单顺序，再退到打开次数`() {
        val picks = pickCommon(
            pool,
            CommonSignals(schoolFrequentIds = listOf(5, 2), openCounts = mapOf(8 to 4, 9 to 7)),
        )
        assertEquals(listOf(5, 2, 9, 8), picks.map { it.app.id })
        assertEquals(
            listOf(
                CommonReason.SCHOOL,
                CommonReason.SCHOOL,
                CommonReason.RECENT,
                CommonReason.RECENT,
            ),
            picks.map { it.reason },
        )
    }

    @Test
    fun `一点信号都没有就照目录顺序补，并承认是目录顺序`() {
        val picks = pickCommon(pool.take(6), CommonSignals())
        assertEquals(listOf(1, 2, 3, 4), picks.map { it.app.id })
        assertTrue(picks.all { it.reason == CommonReason.FALLBACK })
        assertEquals("目录顺序", commonCaption(picks))
    }

    @Test
    fun `钉过的应用从目录里下架了不许占格`() {
        val picks = pickCommon(pool.filter { it.id != 3 }, CommonSignals(pinnedIds = listOf(3, 4)))
        assertEquals(listOf(4), picks.map { it.app.id }.take(1))
        assertFalse(picks.any { it.app.id == 3 })
    }

    @Test
    fun `说明行把用到的档位都列出来，不藏`() {
        val picks = pickCommon(
            pool,
            CommonSignals(pinnedIds = listOf(1), liveIds = setOf(2), schoolFrequentIds = listOf(3, 4)),
        )
        assertEquals("你钉住的·现在有事·学校常用名单×2", commonCaption(picks))
    }

    /**
     * 应用页那一屏必须**真的接上**第 3 档的数据源，并把"每格几行"绑在同一个常数上。
     *
     * 为什么用扫源码（独立审计 v3 #4/#7 第 2 轮 B-11 追的第二条）：上面 6 条全是 `pickCommon`
     * 的纯函数测试 —— 第 1 轮那个"档 3 去目录分组里找一个根本不存在组名"的 bug，
     * 纯函数一条都抓不到（函数拿到的入参本来就是空清单，它按规矩补位，错在**接线**）。
     * 接线错了只有源码扫描会红。手法与 `LazyColumnKeyUniquenessTest` 一致。
     */
    @Test
    fun `应用页必须真接 graph_frequent 并把每格数钉在 COMMON_LIMIT 上`() {
        val file = java.io.File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppsSectionPage.kt")
        assertTrue("找不到 ${file.absolutePath}", file.isFile)
        val text = file.readText()
        assertTrue(
            "第 3 档的数据源必须在这一页自己刷新：`graph.frequent` 全工程唯一刷新点若在已废弃的" +
                " `ui/home/HomePage.kt` 里，这一档就永远空转（第 1 轮 B-3 的根因）",
            text.contains("graph.frequent.refresh()") && text.contains("graph.frequent.state"),
        )
        // 大字/窄屏会把格数降到 3 或 2（§14），所以常用排的格数 = min(算法上限, 当前列数)：
        // 上限必须仍然来自 COMMON_LIMIT（写死 4 会让算法与界面分叉），降列只是显示约束。
        assertTrue(
            "常用那一排每行几格必须以 COMMON_LIMIT 为上限 —— 写死 4 的话，改上限时算法与界面会分叉",
            text.contains("val commonColumns = minOf(COMMON_LIMIT, gridColumns)"),
        )
        assertTrue(
            "常用排要用 commonColumns 切行，不许绕开它写死列数",
            text.contains("common.chunked(commonColumns)"),
        )
        // 常用那一排的**补位空格**也必须跟着同一个数：`chunked` 换了而 `repeat` 没换，
        // 半路就会歪一格（目录那两组每行几格是版式选择，不归这条管，所以只查常用那一排）。
        assertTrue(
            "常用行的补位要写 repeat(commonColumns - rowApps.size)，不许写字面 4",
            text.contains("repeat(commonColumns - rowApps.size)"),
        )
        assertTrue(
            "大字/窄屏降列必须走 appGridColumns，而不是另写一套阈值",
            text.contains("appGridColumns(configuration.screenWidthDp, configuration.fontScale)"),
        )
    }
}
