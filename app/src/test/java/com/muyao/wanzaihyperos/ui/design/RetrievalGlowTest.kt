package com.muyao.wanzaihyperos.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.hypot

/**
 * 取数流光的守卫。
 *
 * 这条动效最容易骗人的地方是「看着在动」—— 它完全可以一边转一边画错位置（跑到框外、
 * 或者在「压根没人发起过取数」的时候也转）。所以这里钉三件事：
 *
 * 1. 周界上的点**真的在周界上**（几何是纯函数，能量，不必靠肉眼）；
 * 2. 尾迹从亮到无、单调，且尾巴真的淡到看不见（否则整圈都是亮线＝变成描边）；
 * 3. 调用点给它的 `active` 必须是数据层认过的正面证据，**不许拿 `loading` 顶替**
 *    （`loading` 里还含「取不到」与「没人发起过」，那两种情况下转圈是在撒谎）。
 */
class RetrievalGlowTest {

    private val repoRoot: File
        get() {
            var d: File? = File(System.getProperty("user.dir"))
            while (d != null) {
                if (File(d, "app/build.gradle.kts").isFile) return d
                d = d.parentFile
            }
            error("找不到工程根目录")
        }

    private fun source(rel: String): String =
        File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos/$rel").readText(Charsets.UTF_8)

    /** 去掉注释再判：注释里写「不许用 loading」不算违规，真写了才算。 */
    private fun code(rel: String): String =
        source(rel).split("\n").joinToString("\n") { line ->
            val i = line.indexOf("//")
            if (i >= 0 && line.take(i).count { it == '"' } % 2 == 0) line.take(i) else line
        }

    // ── 几何 ────────────────────────────────────────────────────────────────

    @Test
    fun `周界上的点真的落在周界上，不是随手一个圆角矩形近似`() {
        val left = 0f; val top = 0f; val w = 100f; val h = 60f; val r = 10f
        // 起点是上边左圆角**之后**那一点，顺时针走。
        assertEquals(10f, RetrievalGlow.ringPoint(left, top, w, h, r, 0f).x, 0.01f)
        assertEquals(0f, RetrievalGlow.ringPoint(left, top, w, h, r, 0f).y, 0.01f)

        // 上边走到头 = 右上圆角的起点（x 已到 right - r）。
        val a = w - 2f * r
        val arc = PI.toFloat() * r / 2f
        val total = 2f * a + 2f * (h - 2f * r) + 4f * arc
        val atRightArc = RetrievalGlow.ringPoint(left, top, w, h, r, a / total)
        assertEquals(w - r, atRightArc.x, 0.01f)
        assertEquals(0f, atRightArc.y, 0.01f)

        // t 走完一圈回到原点；负数取模等价于正数（追光的 t 会一路减到 0 以下）。
        val start = RetrievalGlow.ringPoint(left, top, w, h, r, 0f)
        val wrap = RetrievalGlow.ringPoint(left, top, w, h, r, 1f)
        assertEquals(start.x, wrap.x, 0.01f)
        assertEquals(start.y, wrap.y, 0.01f)
        val half = RetrievalGlow.ringPoint(left, top, w, h, r, 0.37f)
        val halfNeg = RetrievalGlow.ringPoint(left, top, w, h, r, 0.37f - 1f)
        assertEquals(half.x, halfNeg.x, 0.01f)
        assertEquals(half.y, halfNeg.y, 0.01f)
    }

    @Test
    fun `一段采样走过的总长等于圆角矩形的周长`() {
        val left = 3f; val top = 7f; val w = 330f; val h = 170f; val r = 23.4f
        val n = 720
        var walked = 0.0
        var prev = RetrievalGlow.ringPoint(left, top, w, h, r, 0f)
        for (i in 1..n) {
            val p = RetrievalGlow.ringPoint(left, top, w, h, r, i.toFloat() / n)
            walked += hypot((p.x - prev.x).toDouble(), (p.y - prev.y).toDouble())
            prev = p
        }
        val expected = (2 * (w - 2 * r) + 2 * (h - 2 * r) + 4 * (PI.toFloat() * r / 2f)).toDouble()
        assertEquals(expected, walked, expected * 0.01)
    }

    @Test
    fun `卡太小时点退到中心，不会画出负尺寸`() {
        val p = RetrievalGlow.ringPoint(0f, 0f, 0f, 0f, 5f, 0.4f)
        assertEquals(0f, p.x, 0.01f)
        assertEquals(0f, p.y, 0.01f)
    }

    // ── 尾迹 ────────────────────────────────────────────────────────────────

    /** 实测卡几何：1103×519px、圆角 82px（Dims.cardCorner 18dp × 1.3 × 3.5）。 */
    private val card = floatArrayOf(1103f, 519f, 82f)

    private fun perimeterOf(w: Float, h: Float, r: Float): Float =
        2 * (w - 2 * r) + 2 * (h - 2 * r) + 4 * (PI.toFloat() * r / 2f)

    private val strokePx: Float get() = RetrievalGlow.THICKNESS_DP * 3.5f

    private fun cardSteps(): Int =
        RetrievalGlow.tailSteps(perimeterOf(card[0], card[1], card[2]), strokePx)

    @Test
    fun `尾巴必须淡到看不见，否则整圈都在亮就成了描边`() {
        val steps = cardSteps()
        assertTrue("这张卡上只算出 $steps 段，太粗了", steps >= 8)
        assertEquals(RetrievalGlow.HEAD_ALPHA, RetrievalGlow.tailAlpha(0, steps), 0.0001f)
        var prev = RetrievalGlow.tailAlpha(0, steps)
        for (i in 1 until steps) {
            val v = RetrievalGlow.tailAlpha(i, steps)
            assertTrue("尾迹第 $i 段比前一段更亮，那不是尾迹", v <= prev)
            prev = v
        }
        assertTrue(
            "尾点还亮着 ${RetrievalGlow.tailAlpha(steps - 1, steps)}，" +
                "整圈就成了描边、不再是「有一个头在跑」",
            RetrievalGlow.tailAlpha(steps - 1, steps) < 0.01f,
        )
    }

    /**
     * 段数必须**由几何反算**，不能写死。
     *
     * 2026-11-02 逐像素量的现状：固定 48 点铺满整条尾巴 ⇒ 一张 1103×519px 的卡上点距
     * 约 19.8px、点直径 5.25px，真机左沿那一列只站得住 7 个孤立蓝块。看得见「几个点」，
     * 看不见「一道边」。改正后段长 ≈ 半个笔画，相邻两段首尾相接；
     * 卡越大段数越多，**点距与卡的尺寸无关**。
     */
    @Test
    fun `段数由几何反算：卡更大时点距不变`() {
        val small = RetrievalGlow.tailSteps(1200f, strokePx)
        val big = RetrievalGlow.tailSteps(4800f, strokePx)
        assertTrue("周长翻四倍，段数却只有 $small → $big，说明段数没跟着几何走", big >= small * 3)
        val smallStep = 1200f * RetrievalGlow.TAIL_SPAN / small
        val bigStep = 4800f * RetrievalGlow.TAIL_SPAN / big
        assertTrue(
            "两种卡的段长 $smallStep px 与 $bigStep px 差太多，大卡仍然会散成点",
            kotlin.math.abs(smallStep - bigStep) <= strokePx * 0.5f,
        )
        assertEquals("段数要有上限，否则卡被量成几千 px 时会白烧 GPU", RetrievalGlow.MAX_TAIL_STEPS, RetrievalGlow.tailSteps(1e9f, strokePx))
        assertEquals("量不出周长时返回 0，由调用方直接不画", 0, RetrievalGlow.tailSteps(0f, strokePx))
    }

    @Test
    fun `尾迹是首尾相接的一段弧，不是等距散点`() {
        val span = RetrievalGlow.TAIL_SPAN
        assertTrue("TAIL_SPAN 必须小于半圈，否则尾巴绕回来糊成一圈描边", span in 0.05f..0.45f)
        val w = card[0]
        val h = card[1]
        val r = card[2]
        val steps = cardSteps()
        var worst = 0f
        var total = 0f
        for (i in 0 until steps - 1) {
            val t0 = -i.toFloat() / steps * span
            val t1 = -(i + 1).toFloat() / steps * span
            val p0 = RetrievalGlow.ringPoint(0f, 0f, w, h, r, t0)
            val p1 = RetrievalGlow.ringPoint(0f, 0f, w, h, r, t1)
            val step = hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat()
            total += step
            if (step > worst) worst = step
        }
        assertTrue(
            "相邻两段之间隔着 $worst px（笔画才 $strokePx px），尾迹会断成散点",
            worst <= strokePx,
        )
        assertTrue(
            "整条尾巴走了 $total px，占周界 ${total / perimeterOf(w, h, r)}，不该到半圈",
            total / perimeterOf(w, h, r) < 0.5f,
        )
    }

    // ── 接线 ────────────────────────────────────────────────────────────────

    @Test
    fun `此刻卡的流光只看在途证据，不看 loading`() {
        val home = code("ui/main/pages/HomeSectionPage.kt")
        // 判据只看那一行的算术：两行，各自是一条正面证据。
        val def = home.substringAfter("val fetchingSchedule =").lines().take(2).joinToString(" ")
        assertTrue(
            "流光的判据里没有 JwVerdict.IN_FLIGHT —— 教务那趟的在途就认不出来",
            "JwVerdict.IN_FLIGHT" in def,
        )
        assertTrue(
            "流光的判据里没有 metaOrNull?.refreshing —— 学在城院那一发在路上时卡沿不亮",
            "metaOrNull?.refreshing" in def,
        )
        assertFalse(
            "拿 loading 当流光判据了：它包含「取不到」与「压根没发起过」，那两种情况下" +
                "转圈是在说数据证明不了的话",
            Regex("""fetchingSchedule\s*=[^\n]*\bloading\b""").containsMatchIn(home),
        )
        assertTrue(
            "卡体上没接 retrievalGlow（写了组件却没挂上，等于什么都没做）",
            "retrievalGlow(" in home,
        )
        assertTrue("流光的 active 必须来自 fetching", "active = fetching," in home)
    }

    /**
     * 那两张首页小卡以前写的是 `state is ListState.FirstLoad`。
     *
     * `startRefresh()` 在没有内容时**原样返回 FirstLoad**（`data/ListState.kt:130`），
     * 所以 FirstLoad 既可能是「正在取」、也可能是「还没人发起过」——
     * 后者写「正在取校园跑…」就是界面在说数据证明不了的话（SlotSource.kt 开头那条纪律）。
     */
    @Test
    fun `首页两张小卡不再拿 FirstLoad 单独当正在取`() {
        val home = code("ui/main/pages/HomeSectionPage.kt")
        listOf("internal fun TodoSummaryCard(", "internal fun RunSummaryCard(").forEach { head ->
            val body = home.substringAfter(head).substringBefore("\n@Composable")
            assertFalse(
                "$head 里又出现了 state is ListState.FirstLoad —— " +
                    "它分不清「正在取」和「没人发起过」",
                "state is ListState.FirstLoad" in body,
            )
            assertTrue("$head 必须吃调用点算好的 fetching", "fetching: Boolean," in body)
        }
    }

    @Test
    fun `课表页那张取数卡也绕了流光`() {
        val tt = code("ui/main/pages/TimetableSectionPage.kt")
        val block = tt.substringAfter("item(key = \"jw-pending\")").substringBefore("} else when")
        assertTrue("jw-pending 那张卡没接流光", "retrievalGlow(" in block)
    }
}
