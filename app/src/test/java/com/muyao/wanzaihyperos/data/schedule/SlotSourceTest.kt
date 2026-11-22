package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 课表两份数据源的取舍规则（v3 清单 #4，业主 2026-10-22：
 * 「课表会闪出**没有的课**再回正」）。
 *
 * 这条规则被修过两次都没修掉，因为两处页面各写一遍、且都只加了"标明来源"的说明，
 * 没人管**教务还在爬时那份兜底根本不该上屏**。抽成 [pickSlots] + [jwVerdictOf] 后由这里守着：
 * 任何一次"顺手给兜底开个口子"的改动都会打红这条。
 *
 * ★ 判据别说满（独立审计 v3 #4 第 1 轮 A-2）：课表页正文那张"教务还在取"的卡
 *   是靠 `if (jwPending) … else when(…)` 的**结构**兜住的，不是靠这条纯函数；
 *   纯函数管的是"选哪一份"，UI 层还有一段"什么时候根本不选"。
 *   所以本文件另外钉了一条源码扫描（最后那条测试），管住"新页面绕过 [pickSlots]"。
 */
class SlotSourceTest {

    private fun slot(id: Int) = TronScheduleSlot(
        courseId = id,
        courseName = "测试课$id",
        dayOfWeek = 1,
        periodStart = 1,
        periodEnd = 2,
        weekStart = 1,
        weekEnd = 20,
        raw = "第1-2节",
    )

    @Test
    fun `教务有货就用教务，哪怕还在爬（快照播种态）`() {
        assertEquals(
            SlotSource.JIAOWU,
            pickSlots(listOf(slot(1)), JwVerdict.IN_FLIGHT, listOf(slot(9))).source,
        )
    }

    @Test
    fun `教务还在爬时两份都不画 —— 这就是那个闪`() {
        assertEquals(
            SlotSource.WAIT,
            pickSlots(emptyList(), JwVerdict.IN_FLIGHT, listOf(slot(9))).source,
        )
    }

    @Test
    fun `教务确认无课不被旧学在城院课程覆盖`() {
        assertEquals(
            SlotSource.JIAOWU,
            pickSlots(emptyList(), JwVerdict.SETTLED_EMPTY, listOf(slot(9))).source,
        )
    }

    @Test
    fun `两份都没有`() {
        assertEquals(
            SlotSource.NONE,
            pickSlots(emptyList(), JwVerdict.NO_CONCLUSION, emptyList()).source,
        )
    }

    @Test
    fun `WAIT 与 NONE 不给任何格子，界面因此不能把它们当没课`() {
        val tron = listOf(slot(7))
        val waiting = pickSlots(
            emptyList<TronScheduleSlot>(),
            jwVerdict = JwVerdict.IN_FLIGHT,
            tronSlots = tron,
        )
        assertEquals(SlotSource.WAIT, waiting.source)
        assertTrue("WAIT 时手里必须没格子", waiting.slots.isEmpty())
        assertTrue("WAIT 不许被当成教务那份", !waiting.fromJiaowu)
        assertTrue("WAIT 更不许被当成学在城院那份", !waiting.fromTronclass)

        val none = pickSlots(
            emptyList<TronScheduleSlot>(),
            jwVerdict = JwVerdict.NOT_STARTED,
            tronSlots = emptyList(),
        )
        assertEquals(SlotSource.NONE, none.source)
        assertTrue(none.slots.isEmpty())
    }

    @Test
    fun `pickSlots 只在有货的那一支把格子交出去`() {
        val jw = listOf(slot(1), slot(2))
        val tron = listOf(slot(7))
        assertEquals(jw, pickSlots(jw, JwVerdict.HAS_SLOTS, tron).slots)
        // 教务**从没取过**时允许退学在城院 —— 但界面必须照实说是哪一份（见下面那两条措辞测试）。
        assertEquals(tron, pickSlots(emptyList(), JwVerdict.NOT_STARTED, tron).slots)
        assertTrue(pickSlots(jw, JwVerdict.HAS_SLOTS, tron).fromJiaowu)
        assertTrue(pickSlots(emptyList(), JwVerdict.NO_CONCLUSION, tron).fromTronclass)
    }

    @Test
    fun `四个教务子态各自说各自的话`() {
        // 这是第 3 轮审计必修 1：`TRONCLASS` 底下压着四个子态，以前全说成「这周没给课」。
        assertEquals("教务那份这台机子上还没取过", jwVerdictSentence(JwVerdict.NOT_STARTED))
        assertEquals("教务那份这次没取到", jwVerdictSentence(JwVerdict.NO_CONCLUSION))
        assertEquals("教务那份这周没给课", jwVerdictSentence(JwVerdict.SETTLED_EMPTY))
        assertEquals("正在取教务课表…", jwVerdictSentence(JwVerdict.IN_FLIGHT))
        // 措辞不许互相包含：否则页面少写一个分支也会"看着对"。
        val s = listOf(
            JwVerdict.NOT_STARTED, JwVerdict.NO_CONCLUSION,
            JwVerdict.SETTLED_EMPTY, JwVerdict.IN_FLIGHT, JwVerdict.HAS_SLOTS,
        ).map { jwVerdictSentence(it) }
        assertEquals("五态五句，必须各不相同", s.size, s.distinct().size)
    }

    @Test
    fun `jwVerdictOf 只在真在跑的时候才说 IN_FLIGHT`() {
        // 「没内容时 FirstLoad 原样返回」那条坑：crawling=false 的 FirstLoad 必须判成从没取过，
        // 否则界面会挂一句永远不会结束的「正在取」。
        assertEquals(JwVerdict.NOT_STARTED, jwVerdictOf(ListState.FirstLoad, crawling = false, hasSlots = false))
        assertEquals(JwVerdict.IN_FLIGHT, jwVerdictOf(ListState.FirstLoad, crawling = true, hasSlots = false))
        assertEquals(
            JwVerdict.IN_FLIGHT,
            jwVerdictOf(
                ListState.Content(items = listOf("x"), meta = ListMeta(refreshing = true)),
                crawling = false, hasSlots = false,
            ),
        )
        assertEquals(
            JwVerdict.SETTLED_EMPTY,
            jwVerdictOf(ListState.Empty(), crawling = false, hasSlots = false),
        )
        assertEquals(
            JwVerdict.NO_CONCLUSION,
            jwVerdictOf(ListState.SessionExpired, crawling = false, hasSlots = false),
        )
        assertTrue("有槽位永远是 HAS_SLOTS（爬取中也算有货，不空一屏）",
            jwVerdictOf(ListState.FirstLoad, crawling = true, hasSlots = true) == JwVerdict.HAS_SLOTS)
    }

    @Test
    fun `界面不许再用 FirstLoad 猜正在取，也不许把画着课与没取到并排说`() {
        val ui = java.io.File("src/main/java/com/muyao/wanzaihyperos/ui")
        assertTrue("找不到 $ui.absolutePath", ui.isDirectory)
        val offenders = mutableListOf<String>()
        ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            if ("jwTimetable" in text) {
                if ("jwVerdictOf(" !in text) offenders += "${f.name}: 没走 jwVerdictOf（会拿 FirstLoad 当正在取）"
            }
            if (f.name == "HomeSectionPage.kt" &&
                Regex("""failure\s*=\s*failure,""").containsMatchIn(text)
            ) {
                offenders += "HomeSectionPage.kt: failure 直接传了原始值，画着课时也会说「没取到」"
            }
        }
        assertTrue(offenders.joinToString("; "), offenders.isEmpty())
    }

}
