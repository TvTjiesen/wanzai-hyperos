package com.muyao.wanzaihyperos.ui.design.liquid

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 搜索盘「中心清核」不变量（工单第 14 轮主项）。
 *
 * ## 为什么这条必须存在
 *
 * `liquid/Lens.kt:140` 的着色器里 `if (-sd >= refractionHeight) return content.eval(coord)`
 * 是"中心清晰核"的**唯一出口**，而 `-sd` 的最大值就是盘半径 ⇒ **折射高度必须小于半径**。
 * B4-01 把盘 52dp→46dp 时把这条不等式缩反了：半径 23dp=80.5px < `LENS_DEFAULT_DP`(24dp=84px)
 * ⇒ 整盘每个像素都在折 = 业主 2026-10-20 报的「糊成一团的白饼」。
 * **660 条测试当时全绿**：`grep -rn "lensDp|refraction" app/src/test` 0 命中 —— 没有任何测试守过它。
 *
 * ## 两条断言
 *
 * ① **数值**：`searchRefractionDp(24f, 46f)` 必须严格小于半径，且清核余量 ≥ 2dp；
 * ② **源码**：盘径只能有 `discSize` 一份（`.size(discSize)`），且折射走 `searchRefractionDp(...)` ——
 *    两处各写 `46.dp` 就会再次把不等式写反而没人发现。
 */
class SearchRefractionTest {

    private fun src(): String {
        val f = File(
            "src/main/java/com/muyao/wanzaihyperos/ui/design/liquid/GlassControls.kt",
        )
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    @Test
    fun `46dp 盘上折射必须留出清核`() {
        val lens = 24f          // GlassTuning.LENS_DEFAULT_DP
        val disc = 46f
        val r = searchRefractionDp(lens, disc)
        val radiusPx = disc / 2f * 3.5f         // 560dpi = 3.5x
        val refractionPx = r * 3.5f
        assertTrue(
            "折射高度 ${refractionPx}px 不小于半径 ${radiusPx}px ⇒ 中心清核永不触发（白饼回来了）",
            refractionPx < radiusPx,
        )
        val clearanceDp = disc / 2f - r
        assertTrue("清核余量只有 ${clearanceDp}dp（< 2dp，读不出一片清楚）", clearanceDp >= 2f)
    }

    @Test
    fun `盘更小时也自动跟钳`() {
        listOf(38f, 40f, 46f, 52f).forEach { disc ->
            val r = searchRefractionDp(24f, disc)
            assertTrue("盘 ${disc}dp：折射 ${r}dp 不该 >= 半径 ${disc / 2f}dp", r < disc / 2f)
        }
        // 盘足够大时不钳（保持原配比）
        assertTrue("52dp 盘不该被钳（半径 26 − 3 = 23 ≥ 24 吗？这里应钳到 23）", searchRefractionDp(24f, 52f) == 23f)
        assertTrue("小折射量不该被抬高", searchRefractionDp(12f, 46f) == 12f)
    }

    @Test
    fun `盘径只有一份且折射走钳过的函数`() {
        val text = src()
        assertTrue(
            "盘子的 `.size(…)` 不是读 `discSize`（两处各写 46.dp 会把不等式再写反）",
            ".size(discSize)" in text,
        )
        assertTrue(
            "折射没有走 `searchRefractionDp(...)`（直接读 GlassTuning.lensDp 就是白饼）",
            "searchRefractionDp(" in text && "refractionDp = if (motion.refractsGlass) refraction else 0.dp" in text,
        )
        // 反例：.size(46.dp) 仍出现在搜索盘那段里
        val searchBlock = text.substringAfter("fun GlassSearchButton(").substringBefore("private fun")
        assertTrue("搜索盘那段里还有 `.size(46.dp)` 字面量", ".size(46.dp)" !in searchBlock)
    }
}
