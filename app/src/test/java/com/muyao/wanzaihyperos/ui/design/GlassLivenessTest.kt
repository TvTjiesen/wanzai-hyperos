package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 玻璃存活判據的測試（規範 §6.4 的哨兵驗證）。
 *
 * ## 為什麼這組測試重要
 *
 * 規範裡的原話：「『坐標非空』不等於『裡面有內容』—— 我們拿它當存活判據
 * **被騙過一次**。要做哨兵驗證：往那層寫一個獨特值，看有沒有別人真的讀到/改掉它，
 * 而不是『沒報錯就算好』。」
 *
 * 而「沒報錯就算好」正是本專案反覆栽的那類坑
 *（B18「沒有查到考試安排」就是：解析成功、內容為空、界面在撒謊）。
 *
 * 所以這裡把每一種「假活」的情形都釘住。
 */
class GlassLivenessTest {

    @Test
    fun `哨兵读回原值才算活`() {
        val w = GlassLiveness.newSentinel(42L)
        assertEquals(GlassLiveness.Verdict.Alive, GlassLiveness.judge(w, w))
    }

    @Test
    fun `读不回来算死`() {
        // 這是關鍵的一條：「座標非空」可能只是有人登記過，
        // 但那一層根本沒被繪製 —— 此時讀回來是 null。
        val v = GlassLiveness.judge(123L, null)
        assertTrue("读不回应判死，实际 $v", v is GlassLiveness.Verdict.Dead)
        assertTrue(
            "死因应说明是读不回，实际 ${(v as GlassLiveness.Verdict.Dead).reason}",
            v.reason.contains("讀不回來"),
        )
    }

    @Test
    fun `值被改掉算死`() {
        // 有人（離場的頁面）把共享層清了 —— 這正是規範紅線 #3 的情形。
        val v = GlassLiveness.judge(123L, 456L)
        assertTrue("值被改掉应判死，实际 $v", v is GlassLiveness.Verdict.Dead)
        assertTrue(
            "死因应说明被别人改掉，实际 ${(v as GlassLiveness.Verdict.Dead).reason}",
            v.reason.contains("被別人改掉"),
        )
    }

    @Test
    fun `只有 Alive 才允许用真玻璃`() {
        assertTrue(GlassLiveness.Verdict.Alive.canUseGlass)
        assertFalse(GlassLiveness.Verdict.Unknown.canUseGlass)
        assertFalse(GlassLiveness.Verdict.Dead("x").canUseGlass)
    }

    @Test
    fun `Unknown 不等于 Dead`() {
        // ★ 「還沒驗」與「驗了、死了」在界面上該顯示完全不同的東西。
        //   混成一態就會出現「明明是沒驗過，卻顯示成壞了」。
        assertNotEquals(GlassLiveness.Verdict.Unknown, GlassLiveness.Verdict.Dead("x"))
    }

    @Test
    fun `哨兵每次都不一样`() {
        // 固定常量會被「其它地方也寫了同一個值」騙過。
        // 每次用不同的值，才能確認「讀到的是我剛寫的那個」。
        val a = GlassLiveness.newSentinel(1L)
        val b = GlassLiveness.newSentinel(2L)
        assertNotEquals("不同 seed 应产生不同哨兵", a, b)
        // 同一個 seed 要可重現（方便排查：記下 seed 就能複算）
        assertEquals(a, GlassLiveness.newSentinel(1L))
    }

    @Test
    fun `哨兵值落在合理范围`() {
        // 不能溢出成 0（0 常被當成「未初始化」的哨兵）
        for (seed in listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val s = GlassLiveness.newSentinel(seed)
            assertNotEquals("哨兵不应为 0（0 常被当未初始化），seed=$seed", 0L, s)
        }
    }

    @Test
    fun `降级外观用主题色而不是硬编码黑白`() {
        val darkSurface = Color(0xFF282828)
        val lightSurface = Color(0xFFFFFFFF)
        val d = GlassLiveness.fallbackFor(isDark = true, surface = darkSurface)
        val l = GlassLiveness.fallbackFor(isDark = false, surface = lightSurface)
        // 底色必须就是传进来的 surface（跟着主题走，不会和环境不搭）
        assertEquals(darkSurface, d.containerColor)
        assertEquals(lightSurface, l.containerColor)
        // 描边在两态下应不同（深色用白、浅色用黑）
        assertNotEquals("深/浅描边应不同", d.strokeColor, l.strokeColor)
        assertEquals("描边宽度应为 1dp", 1f, d.strokeWidthDp, 0.001f)
    }
}
