package com.muyao.wanzaihyperos.ui.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 无障碍三条 → 界面到底走哪条路，这张真值表。
 *
 * ## 为什么要钉住它
 *
 * 这三条信号一旦只写在"看起来会读到的地方"，就会出现**用户明确表了态、界面没反应**
 * （`GlassTuning` 档头那条"读到过值不等于这一版跑过"是同一类坑）。
 * 真值表放单测里，改任何一条判定都会当场红。
 */
class MotionFlagsTest {

    @Test
    fun `三条全默认 = 一切照旧`() {
        val m = MotionFlags()
        assertTrue("默认该是半透明玻璃", m.glassIsTranslucent)
        assertTrue("默认该有位移动效", m.canMove)
    }

    @Test
    fun `减弱透明度 = 玻璃不再半透明，但动效保留`() {
        val m = MotionFlags(reduceTransparency = true)
        assertFalse(m.glassIsTranslucent)
        assertTrue("关透明不等于关动画", m.canMove)
    }

    @Test
    fun `高对比文字同样压掉半透明`() {
        assertFalse(MotionFlags(highContrast = true).glassIsTranslucent)
    }

    @Test
    fun `系统动画关掉只停位移，不停材质`() {
        val m = MotionFlags(animationsEnabled = false)
        assertFalse("位移/弹簧必须停", m.canMove)
        assertTrue("玻璃本身不该因此变实（那是另一条信号管的）", m.glassIsTranslucent)
    }

    @Test
    fun `动画和透明度是两条独立的信号`() {
        // 这条是防"以后有人图省事把两条合成一条"：它们各自有各自的用户。
        val both = MotionFlags(animationsEnabled = false, reduceTransparency = true)
        assertFalse(both.canMove)
        assertFalse(both.glassIsTranslucent)
    }
}
