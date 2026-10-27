package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 整周模式 v4 的源码守卫（业主 2026-10-24 拍板「保持 5 列，只把视觉与交互重做」）。
 *
 * ## 为什么是源码守卫
 *
 * 这一批改动的验收面全是**像素几何**（块宽 47.49dp、缝 2dp、轴字 5.07:1），
 * 单测拿不到渲染结果；但「谁给这一栏加了横向 padding」是纯文本事实，
 * 读得到、钉得住，而且改回去立刻红。
 *
 * ## 实测过的两个数（真机 SYNTHETIC0000001，density 560）
 *
 * 改前（APK 74452e69）：课块宽 **42.86dp**，列缝 **6.86dp**。
 * 改后（APK 8da225db）：课块宽 **47.43dp**，列缝 **2.29dp**，与稿的 47.49 / 2 相差一个抗锯齿像素。
 * 差值 5dp 的来源就是下面第 1、2 条守的两处 padding 相加
 * （Column 2dp×2 + 课块外层 1.5dp×2 = 7dp，扣掉稿本来就有的 1dp×2 就是多出来的 5dp）。
 */
class WeekBoardV4Test {

    private val pageFile =
        "src/main/java/com/muyao/wanzaihyperos/ui/main/pages/TimetableSectionPage.kt"

    private fun code(): String {
        val f = File(pageFile)
        assertTrue("找不到整周页源码：\$pageFile", f.isFile)
        return SourceScan.stripComments(f.readText(Charsets.UTF_8))
    }

    /** 取 [marker] 之后、下一个顶层 fun / @Composable 之前的那段函数体。 */
    private fun bodyAfter(code: String, marker: String): String {
        val i = code.indexOf(marker)
        if (i < 0) return ""
        val rest = code.substring(i)
        val next = Regex("""\n\s*(@\w+\s+)*(private |internal |public )?fun\s""").find(rest, 1)
        return if (next == null) rest else rest.substring(0, next.range.first)
    }

    // ---- 1. 每一天那一栏不许挂横向 padding -------------------------------
    //
    // 稿把列缝交给课块自己的 1dp 外边距（CSS .chip{margin:1px}）。
    // 这里多挂 2dp，块宽就从 47.49 掉到 43.49，13sp 每行少半个字。
    @Test
    fun `每一天那一栏不许再挂横向内边距`() {
        // 注意：「columns.forEach { col ->」在 WeekBoard 里出现两次 ——
        // 表头那一次、按天铺列那一次。只取第一次的窗口是量不到的（MUT-1 就这么漏过一次）。
        // 所以这里扫整个 WeekBoard 函数体，任何一处 weight(1f) 后跟 horizontal padding 都算红。
        val body = bodyAfter(code(), "internal fun WeekBoard(")
        assertTrue("没找到 WeekBoard 函数体", body.isNotBlank())
        val hits = Regex("""weight\(1f\)[^)]{0,200}?padding\(\s*horizontal""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body).map { it.value.replace('\n', ' ') }.toList()
        assertTrue(
            "整周页里又有「" + hits.firstOrNull()?.take(80) + "」这种写法（共 " + hits.size + " 处）—— " +
                "它会和课块外层 1dp 叠成 6dp 的缝，把块宽从 47.49dp 压到 42.49dp" +
                "（真机实测 42.86dp）。列缝交给课块自己的 margin。",
            hits.isEmpty(),
        )
    }

    // ---- 2. 课块外层边距必须是 1dp -------------------------------------
    @Test
    fun `课块外层边距必须是 1dp`() {
        val code = code()
        val body = bodyAfter(code, "private fun CourseChip(")
        assertTrue("没找到 CourseChip 函数体", body.isNotBlank())
        assertTrue("CourseChip 外层应该恰好占 rows 行", body.contains("height(rowH * rows)"))
        val tail = body.substringAfter("height(rowH * rows)").take(240)
        assertTrue(
            "课块外层边距应写成 .padding(1.dp)",
            Regex("""\.padding\(1\.dp\)""").containsMatchIn(tail),
        )
        assertFalse(
            "课块外层边距又写成 1.5dp（或别的值）了 —— 稿要求 1dp：" +
                "两块之间 2dp 缝、块宽 47.49dp。实测 1.5dp 时块宽只有 42.49dp。",
            Regex("""\.padding\(\s*(?!1\.dp)\d+(\.\d+)?\.dp\s*\)""").containsMatchIn(tail),
        )
    }

    // ---- 3. 今天那一栏不许再铺淡底 -------------------------------------
    //
    // 原来那层 6% 淡底对白卡只有 1.09:1，等于没标；而且它会和课块左缘那条色脊
    // 并排成两条竖线。今天的位置由表头那条 2dp 短线负责。
    @Test
    fun `今天那一栏不许再铺 6% 淡底`() {
        // 同第 1 条：扫整个函数体，别只取第一次出现的那个窗口。
        val window = bodyAfter(code(), "internal fun WeekBoard(")
        assertTrue("没找到 WeekBoard 函数体", window.isNotBlank())
        assertFalse(
            "今天那一栏又铺上淡底了 —— 6% primary 对白卡只有 1.09:1，看不出是标记，" +
                "还会和课块色脊并排成两条竖线。今天的标记只放在表头那 2dp 短线上。",
            Regex("""primary[^\n]{0,40}alpha\s*=\s*0?\.0?6""").containsMatchIn(window),
        )
        assertFalse(
            "今天那一栏又出现了 isToday 分支的 background(...) —— 见上一条理由。",
            Regex("""if\s*\(\s*isToday\s*\)[^\n]{0,120}background\(""").containsMatchIn(window),
        )
    }

    // ---- 4. 轴上的时刻必须是实色次墨 -----------------------------------
    //
    // 现网写的是 onSurfaceVariantSummary.copy(alpha = 0.75f) ⇒ 合成出 #949087，
    // 对白卡只有 3.10:1，10sp 小字远不够 WCAG 1.4.3 的 4.5:1。
    // 现在是 Tone.quiet（#736E64）实色，对白卡 5.07:1。
    @Test
    fun `轴上的时刻必须是实色次墨 不许再压 alpha`() {
        val code = code()
        val body = bodyAfter(code, "PeriodClock.startOf(times, n)")
        assertTrue("没找到轴上的时刻那段代码", body.isNotBlank())
        val win = body.take(420)
        assertTrue(
            "轴上的时刻应该用 Tone.quiet 实色（对白卡 5.07:1）",
            win.contains("Tone.quiet"),
        )
        assertFalse(
            "轴上的时刻又压上 alpha 了 —— 0.75 那版合成出 #949087，对白卡只有 3.10:1，10sp 小字不合格。",
            Regex("""\.copy\(\s*alpha\s*=""").containsMatchIn(win),
        )
    }

    // ---- 5. 表头必须是 28dp --------------------------------------------
    //
    // 稿的 334dp = 34 × 9 节 + 28dp 表头。表头若回到任意旧值，可视区就少一整行。
    @Test
    fun `表头高度必须是 28dp`() {
        val code = code()
        assertTrue(
            "表头高度常量应是 private val HeaderHeight = 28.dp（稿 §1.1 的可视区计算靠它）",
            Regex("""val\s+HeaderHeight\s*=\s*28\.dp""").containsMatchIn(code),
        )
    }
}