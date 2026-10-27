package com.muyao.wanzaihyperos.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「底栏之间左右滑切板块」的落点判据（B4-08，业主 2026-10-23「不丝滑」）。
 *
 * 事故形状：以前只看**位移**（>0.28 宽才翻）。于是
 *  · 轻轻一甩（位移不够）⇒ **翻不了页**，手要拖很远才动 ⇒ 发黏；
 *  · 慢慢推过去（位移够）⇒ 突然整页翻过去 ⇒ 发硬。
 * 标准（`wanzai-motion` §0 第 3 条 / §1 第 4 步）：**落点按速度投影，不是按松手位置就近吸附**。
 *
 * 这条测试钉三件事：① 轻甩能翻；② 慢推的行为与旧口径**逐位一致**（不把"慢慢推过去"判成不翻）；
 * ③ 方向跟着**投影**的符号走（不是跟着位移）。
 */
class SectionSwipeTest {

    private val w = 1216f          // 真机宽（PLZ110）
    private val flingSec = 0.11f   // 默认投影时长（`wz_fling_ms` 110）

    private fun step(delta: Float, v: Float) = sectionSwipeStep(delta, v, w, flingSec)

    @Test
    fun `轻轻一甩也能翻页（这次修的就是这一半）`() {
        // 位移只走了 0.15 宽（不到 0.28 门槛），但抬手指速 −1200 px/s
        val delta = -0.15f * w
        assertEquals("旧口径：不翻", 0, sectionSwipeStep(delta, 0f, w, flingSec))
        assertEquals("带速度：投影 −0.15w −132 = −314px > 0.28w(340)? 不够，仍不翻", 0, step(delta, -1200f))
        // 甩得更快一点就够：−0.15w − (2000×0.11)=−0.15w−220 = −402px > 340 ⇒ 翻
        assertEquals("快甩：翻", 1, step(delta, -2000f))
    }

    @Test
    fun `慢推的行为与旧口径逐位一致`() {
        // v = 0 时：恰好 0.28w 不翻（严格大于才翻）、超过一点点就翻 —— 与旧实现同一个边界
        assertEquals(0, step(-0.28f * w, 0f))
        assertEquals(1, step(-0.281f * w, 0f))
        assertEquals(0, step(0.28f * w, 0f))
        assertEquals(-1, step(0.281f * w, 0f))
    }

    @Test
    fun `方向跟着投影而不是跟着位移`() {
        // 位移向左一点点（−20px）、但手指是**向右**猛甩：投影 = −20 + 4000×0.11 = +420px
        // （门槛 0.28×1216 = 340px）⇒ 回上一个板块。
        // ⚠️ 第一版我写 +2000（投影 +200 < 340）→ 仍然不翻，是**测试自己的算术错了**，不是判据坏了。
        assertEquals(-1, step(-20f, +4000f))
        // 反过来：位移向右一点点、手指向左猛甩 ⇒ 去下一个
        assertEquals(1, step(+20f, -4000f))
        // 速度不足以盖过门槛时，方向仍按位移走（不会因为"甩的方向"就翻）
        assertEquals(0, step(-20f, +2000f))
    }

    @Test
    fun `宽度为 0 时不许翻`() {
        assertEquals(0, sectionSwipeStep(-500f, -3000f, 0f, flingSec))
    }
}