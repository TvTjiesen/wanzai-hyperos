package com.muyao.wanzaihyperos.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 底欄形變量的**純函數測試**。
 *
 * ## 為什麼這一組測試特別重要
 *
 * 這塊「看截圖判斷不了」：靜態圖上看不出「有沒有回到 0」「方向對不對」。
 * 而它一旦不回到 0，底欄會**永久歪著** ——
 * 那是「用著用著才發現、而且說不清從哪來的」那類 bug。
 *
 * 所以這裡把每一條容易錯的行為都釘住。
 */
class SmearMathTest {

    /** 一個 tab 寬 300px，四格，方便算。 */
    private val tabW = 300f
    private val tabs = 4

    @Test
    fun `不拖不動时形变为 0`() {
        assertEquals(0f, SmearMath.smear(0f, tabW, 0f, tabs), 0.0001f)
    }

    @Test
    fun `宽度未量出时强制为 0`() {
        // 沒量出寬度 = 這一幀根本沒畫。此時若讓領先項生效，
        // 布局完成那一幀會「跳形」。
        assertEquals(0f, SmearMath.smear(500f, 0f, 3f, tabs), 0.0001f)
        assertEquals(0f, SmearMath.smear(500f, -10f, 3f, tabs), 0.0001f)
    }

    @Test
    fun `向右拖为正 向左拖为负`() {
        val right = SmearMath.smear(200f, tabW, 0f, tabs)
        val left = SmearMath.smear(-200f, tabW, 0f, tabs)
        assertTrue("向右拖应为正，实际 $right", right > 0f)
        assertTrue("向左拖应为负，实际 $left", left < 0f)
        // 對稱：同樣距離左右應該等大
        assertEquals(abs(right), abs(left), 0.0001f)
    }

    @Test
    fun `形变量被限制在正负 1`() {
        // 拖到很遠也不該超過 1（否則 scaleX 會超出 3%）
        assertEquals(1f, SmearMath.smear(100_000f, tabW, 0f, tabs), 0.0001f)
        assertEquals(-1f, SmearMath.smear(-100_000f, tabW, 0f, tabs), 0.0001f)
    }

    @Test
    fun `滑块领先量也会产生形变（切页时整条栏会倾一下）`() {
        // 沒有手指拖動，只有「滑塊跑到前面」——切頁就是這種情況。
        // 如果這條不成立，純點擊切換就完全沒有形變，看起來是「死」的。
        val s = SmearMath.smear(0f, tabW, 3f, tabs)
        assertTrue("領先量应产生正形变，实际 $s", s > 0f)
    }

    @Test
    fun `只有一格时没有领先量`() {
        // 一格時「領先」沒有意義（除以 tabsCount-1 會除零）
        assertEquals(0f, SmearMath.smear(0f, tabW, 5f, 1), 0.0001f)
        assertEquals(0f, SmearMath.smear(0f, tabW, 5f, 0), 0.0001f)
    }

    @Test
    fun `横向最多胖百分之三`() {
        assertEquals("s=0 时不形变", 1f, SmearMath.scaleX(0f), 0.00001f)
        assertEquals("s=1 时胖 3%", 1.03f, SmearMath.scaleX(1f), 0.00001f)
        assertEquals("对称：s=-1 也是 3%", 1.03f, SmearMath.scaleX(-1f), 0.00001f)
        // 規範明確：3% 是從 7.5% 砍下來的，不許再放大
        assertTrue("不许超过 3%", SmearMath.scaleX(1f) <= 1.0301f)
    }

    @Test
    fun `纵向最多瘦百分之一点五`() {
        assertEquals(1f, SmearMath.scaleY(0f), 0.00001f)
        assertEquals("s=1 时瘦 1.5%", 0.985f, SmearMath.scaleY(1f), 0.00001f)
        assertTrue("不许超过 1.5%", SmearMath.scaleY(1f) >= 0.9849f)
    }

    @Test
    fun `拉伸时纵向一定变瘦 反之亦然`() {
        // 這是「液體」的關鍵：面積大致守恆，看起來才像被擠壓
        for (s in listOf(-1f, -0.5f, 0.5f, 1f)) {
            assertTrue("s=$s 时横向应变胖", SmearMath.scaleX(s) > 1f)
            assertTrue("s=$s 时纵向应变瘦", SmearMath.scaleY(s) < 1f)
        }
    }

    @Test
    fun `支点在受力侧的对面`() {
        // 往右拖（s>0）→ 支點在左（0），於是右邊被拉出去
        assertEquals(0f, SmearMath.pivotX(0.5f), 0.0001f)
        // 往左拖（s<0）→ 支點在右（1），於是左邊被拉出去
        assertEquals(1f, SmearMath.pivotX(-0.5f), 0.0001f)
        // s=0 時兩邊等價，取 0
        assertEquals(0f, SmearMath.pivotX(0f), 0.0001f)
    }

    @Test
    fun `越界位移最多 2dp 而不是 4dp`() {
        // 規範 §6.3：原本 4dp 被判「整條欄晃太多」，收到 2dp
        val density = 2.75f  // 這台機器的密度
        val huge = SmearMath.edgeRubberBandDp(10_000f, density)
        assertEquals("应被夹到 2dp", 2f, huge, 0.0001f)
        val hugeNeg = SmearMath.edgeRubberBandDp(-10_000f, density)
        assertEquals(-2f, hugeNeg, 0.0001f)
    }

    @Test
    fun `越界有阻力 越界量不会等量变成位移`() {
        val density = 2.75f
        val overPx = 100f
        val movedDp = SmearMath.edgeRubberBandDp(overPx, density)
        val overDp = overPx / density
        assertTrue(
            "位移($movedDp dp) 应小于越界量($overDp dp)，否则手感没阻力",
            movedDp < overDp,
        )
    }

    @Test
    fun `密度非法时返回 0 而不是崩溃`() {
        assertEquals(0f, SmearMath.edgeRubberBandDp(100f, 0f), 0.0001f)
        assertEquals(0f, SmearMath.edgeRubberBandDp(100f, -1f), 0.0001f)
    }
}
