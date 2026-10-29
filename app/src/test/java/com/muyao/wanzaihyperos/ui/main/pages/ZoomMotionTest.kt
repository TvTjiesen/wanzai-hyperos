package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.design.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 放大层轨迹的守卫。
 *
 * 为什么这几条必须存在：进场「从那一格长出来」是业主点名要的（原话「我想要那种点击放大的
 * 动画」「我不喜欢这种生硬的在底下显示」），而退场在 2026-10-28 之前**根本没有** ——
 * `onClose` 直接把 `picked` 置空，整层一帧消失。这种缺陷肉眼只看到「有一点跳」，
 * 但只要把轨迹算术写成纯函数，它就是可以被断言钉住的东西。
 */
class ZoomMotionTest {

    private val s0 = 0.24f

    @Test
    fun `放大的起点就是那一格、终点是满尺寸`() {
        assertEquals("首帧必须正好等于那一格占面板的比例", s0, ZoomMotion.scaleAt(s0, 0f), 0f)
        assertEquals("末帧必须正好铺满最终位置", 1f, ZoomMotion.scaleAt(s0, 1f), 0f)
        var prev = ZoomMotion.scaleAt(s0, 0f)
        for (i in 1..40) {
            val cur = ZoomMotion.scaleAt(s0, i / 40f)
            assertTrue("缩放必须单调变大，第 $i 步退回去了：$prev -> $cur", cur >= prev - 1e-6f)
            prev = cur
        }
    }

    @Test
    fun `进场首帧不是全透明`() {
        assertEquals(
            "首帧就全透明等于「闪一下」，必须留一个地板值",
            ZoomMotion.ENTER_ALPHA_FLOOR,
            ZoomMotion.alphaAt(0f, closing = false),
            0f,
        )
        assertEquals(1f, ZoomMotion.alphaAt(1f, closing = false), 1e-6f)
    }

    @Test
    fun `退场末帧必须真的收到零而不是停在残影上`() {
        assertEquals(
            "退场结束时还留不透明度，就会停一张半透明残影再凭空消失",
            0f,
            ZoomMotion.alphaAt(0f, closing = true),
            0f,
        )
        assertEquals(
            "退场起点必须与进场终点接得上，否则切换的瞬间会闪一下",
            ZoomMotion.alphaAt(1f, closing = false),
            ZoomMotion.alphaAt(1f, closing = true),
            1e-6f,
        )
    }

    @Test
    fun `退场的不透明度一路只减不增`() {
        var prev = ZoomMotion.alphaAt(1f, closing = true)
        for (i in 39 downTo 0) {
            val cur = ZoomMotion.alphaAt(i / 40f, closing = true)
            assertTrue("第 $i 步亮度反弹了：$prev -> $cur", cur <= prev + 1e-6f)
            prev = cur
        }
    }

    @Test
    fun `关掉动效时退场也不是零`() {
        val spec = ZoomMotion.exitSpec(canMove = false)
        assertTrue(
            "减少动效不等于没有动效：关掉动效时必须退化成一段回落到零的短淡出，拿到的是 $spec",
            spec is androidx.compose.animation.core.TweenSpec<*>,
        )
        assertEquals(
            "关掉动效用的是最短那档令牌，不是随手一个数",
            Motion.FAST_MS,
            (spec as androidx.compose.animation.core.TweenSpec<*>).durationMillis,
        )
        assertTrue(
            "淡出窗口必须落在整段轨迹里面，不能等于 0 或 1",
            ZoomMotion.EXIT_FADE_SPAN > 0f && ZoomMotion.EXIT_FADE_SPAN < 1f,
        )
    }
}
