package com.muyao.wanzaihyperos.ui.design.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBallTipGeometryTest {
    private val ball = AiTipRect(80f, 200f, 160f, 280f)
    private val target = AiTipRect(20f, 140f, 220f, 188f)

    @Test fun `body grows only within measured source and target envelope`() {
        assertEquals(target, aiTipBody(ball, target, 1f))
        for (step in 0..100) {
            val body = requireNotNull(aiTipBody(ball, target, step / 100f))
            assertTrue(body.valid)
            assertTrue(body.left >= target.left && body.right <= target.right)
            assertTrue(body.top >= target.top && body.bottom <= ball.bottom)
        }
    }

    @Test fun `neck exists in transit and is absent at both resting endpoints`() {
        assertNull(aiTipNeck(ball, target, 0f, 24f))
        assertNotNull(aiTipNeck(ball, target, .7f, 24f))
        assertNull(aiTipNeck(ball, target, 1f, 24f))
        val wide = requireNotNull(aiTipNeck(ball, target, .7f, 24f))
        val pinched = requireNotNull(aiTipNeck(ball, target, .93f, 24f))
        assertTrue(pinched.sourceRight.x - pinched.sourceLeft.x < wide.sourceRight.x - wide.sourceLeft.x)
    }

    @Test fun `source attachment lies on real ellipse and controls do not cross neck`() {
        val ellipse = ball.copy(bottom = 248f)
        for (step in 1..93) {
            val neck = aiTipNeck(ellipse, target, step / 100f, 24f) ?: continue
            val x = (neck.sourceLeft.x - ellipse.centerX) / (ellipse.width / 2f)
            val y = (neck.sourceLeft.y - ellipse.centerY) / (ellipse.height / 2f)
            assertEquals(1f, x * x + y * y, .00001f)
            assertTrue(neck.sourceLeft.x <= neck.sourceLeftControl.x)
            assertTrue(neck.sourceLeftControl.x < neck.sourceRightControl.x)
            assertTrue(neck.targetLeftControl.x < neck.targetRightControl.x)
            assertTrue(neck.targetLeft.x < neck.targetRight.x)
            val tangentDotNormal = (neck.sourceLeftControl.x - neck.sourceLeft.x) * x / (ellipse.width / 2f) +
                (neck.sourceLeftControl.y - neck.sourceLeft.y) * y / (ellipse.height / 2f)
            assertEquals(0f, tangentDotNormal, .0001f)
        }
    }

    @Test fun `below placement mirrors above without reversed or nonfinite controls`() {
        fun mirror(rect: AiTipRect) = AiTipRect(rect.left, 480f - rect.bottom, rect.right, 480f - rect.top)
        val top = requireNotNull(aiTipNeck(ball, target, .7f, 24f))
        val bottom = requireNotNull(aiTipNeck(mirror(ball), mirror(target), .7f, 24f))
        assertEquals(top.sourceLeft.x, bottom.sourceLeft.x, 0f)
        assertEquals(480f - top.sourceLeft.y, bottom.sourceLeft.y, .0001f)
        assertEquals(480f - top.targetLeftControl.y, bottom.targetLeftControl.y, .0001f)
    }

    @Test fun `invalid displaced or distant geometry safely declines a neck`() {
        assertNull(aiTipBody(ball.copy(left = Float.NaN), target, .5f))
        assertNull(aiTipNeck(ball, target, Float.POSITIVE_INFINITY, 24f))
        assertNull(aiTipNeck(ball, target.copy(top = 0f, bottom = 40f), .7f, 24f))
        assertNull(aiTipNeck(ball, AiTipRect(400f, 140f, 600f, 188f), .7f, 24f))
        assertNull(aiTipNeck(ball.copy(right = ball.left), target, .7f, 24f))
    }
}
