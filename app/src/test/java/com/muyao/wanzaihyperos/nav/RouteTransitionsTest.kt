package com.muyao.wanzaihyperos.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 路由转场的时长口径（v3 #1 第 9 轮审计头条的守卫）。
 *
 * 事故原文：`wanzaiRouteTransitions(0)` 走 `NavMotion()` = **Miuix 自带的默认运动曲线** ⇒
 * 用户把动效关掉之后，页面切换**照样位移**，而且时长不是我们定的、是库默认的。
 * 标准 §6 要的是「减弱动效 ≠ 没有反馈：位移与弹簧全部停掉，换成 120–160ms 纯 alpha」。
 *
 * 这条测试钉三件事：① 减弱动效有**自己的时长**（不是 0、不是库默认）；
 * ② 它落在 120–160ms 带内；③ `motionMs <= 0` 这个**旧的热调语义**（"用库默认"）已经不存在了。
 */
class RouteTransitionsTest {

    @Test
    fun `减弱动效有自己的时长而不是交给库默认`() {
        val reduced = routeMotionMs(motionMs = 280, reducedMotion = true)
        assertEquals("减弱动效就用 REDUCED_FADE_MS", REDUCED_FADE_MS, reduced)
        assertTrue("必须在标准的 120–160ms 带内（实得 $reduced）", reduced in 120..160)
        assertTrue("不许是 0（0 在旧实现里等于'用库默认曲线'）", reduced > 0)
    }

    @Test
    fun `热调 0 不再表示用库默认曲线`() {
        // 旧语义：`wz_nav_ms = 0` ⇒ NavMotion() = Miuix 默认（位移照旧）。
        // 现在 0 只表示"没有位移"：走同一个纯 alpha 档。
        assertEquals(
            "热调到 0 也该走纯 alpha 档，而不是把时长交给库",
            REDUCED_FADE_MS,
            routeMotionMs(motionMs = 0, reducedMotion = false),
        )
        assertEquals(
            "负数同样不走库默认",
            REDUCED_FADE_MS,
            routeMotionMs(motionMs = -50, reducedMotion = false),
        )
    }

    @Test
    fun `正常档照原样透传热调值`() {
        assertEquals(280, routeMotionMs(280, reducedMotion = false))
        assertEquals(180, routeMotionMs(180, reducedMotion = false))
    }
}