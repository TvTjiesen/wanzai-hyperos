package com.muyao.wanzaihyperos.ui.design.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class AiBallInteractionTest {
    @Test fun singleLooksAndNearbySecondTapSpinsOnce() {
        val first = AiBallInteraction().observe(.6f, -.2f).tap(1000, 20f, 30f, 12f)
        assertEquals(AiBallAction.LOOK, first.action)
        assertEquals(.6f, first.x, 0f)
        val second = first.observe(.4f, 0f).tap(1250, 23f, 32f, 12f)
        assertEquals(AiBallAction.SPIN, second.action)
        assertEquals(0f, second.x, 0f)
        assertEquals(second, second.spin())
        assertEquals(second, second.observe(-1f, 1f).tap(1300, 20f, 30f, 12f))
    }

    @Test fun distantOrLateTapDoesNotSpin() {
        val first = AiBallInteraction().tap(1000, 10f, 10f, 12f)
        assertEquals(AiBallAction.LOOK, first.tap(1301, 10f, 10f, 12f).action)
        assertEquals(AiBallAction.LOOK, first.tap(1200, 40f, 10f, 12f).action)
        assertEquals(AiBallAction.LOOK, first.tap(900, 10f, 10f, 12f).action)
    }

    @Test fun trackingChangesTargetWithoutStartingMultipleOwners() {
        val first = AiBallInteraction().observe(4f, -4f)
        val next = first.observe(.5f, .2f)
        assertEquals(1f, first.x, 0f)
        assertEquals(-1f, first.y, 0f)
        assertEquals(first.ticket, next.ticket)
        assertEquals(AiBallAction.TRACK, next.action)
        assertEquals(0f, first.observe(Float.NaN, Float.POSITIVE_INFINITY).x, 0f)
    }

    @Test fun cancellationClearsTapAndMotionSoNewTapCannotFinishOldDouble() {
        val first = AiBallInteraction().observe(.5f, .5f).tap(1000, 10f, 10f, 12f)
        val cancelled = first.cancel()
        assertEquals(AiBallAction.REST, cancelled.action)
        assertEquals(0f, cancelled.x, 0f)
        assertEquals(0f, cancelled.y, 0f)
        assertEquals(null, cancelled.tapAt)
        assertTrue(cancelled.ticket > first.ticket)
        assertEquals(AiBallAction.LOOK, cancelled.tap(1200, 10f, 10f, 12f).action)
        assertEquals(AiBallAction.REST, first.spin().cancel().action)
    }

    @Test fun grokSpringConvergesToOneTurnWithinFiniteBudgetAcrossFrameRates() {
        for (fps in listOf(30, 60, 120)) {
            var spring = AiBallSpin.Spring()
            repeat((AiBallSpin.MAX_SECONDS * fps).toInt()) {
                spring = AiBallSpin.step(spring, 1.0 / fps)
                assertTrue(spring.angle in 0.0..AiBallSpin.TURN)
                assertTrue(spring.velocity.isFinite())
            }
            assertTrue("$fps fps", spring.settled)
            assertEquals(AiBallSpin.TURN, spring.angle, .01)
        }
        val start = AiBallSpin.Spring()
        assertEquals(start, AiBallSpin.step(start, Double.NaN))
        assertEquals(AiBallSpin.step(start, .05), AiBallSpin.step(start, 5.0))
    }

    @Test fun sphereProjectionReturnsOriginalFaceAndHidesBackWithoutInwardRotation() {
        for (x in listOf(-.12f, .12f)) {
            val initial = AiBallSpin.project(x, -.05f, 0.0)
            val end = AiBallSpin.project(x, -.05f, AiBallSpin.TURN)
            assertTrue(initial.visible)
            assertEquals(x, initial.centerX, .00001f)
            assertEquals(1f, initial.scaleX, .00001f)
            assertEquals(initial.centerX, end.centerX, .00001f)
            assertEquals(initial.scaleX, end.scaleX, .00001f)
            assertFalse(AiBallSpin.project(x, -.05f, Math.PI).visible)
        }
        for (i in 0..360) {
            val p = AiBallSpin.project(.12f, -.05f, i / 180.0 * Math.PI)
            assertTrue(p.centerX.isFinite() && p.scaleX.isFinite())
            assertTrue(abs(p.centerX) <= .5f)
            assertTrue(p.scaleX >= 0f)
        }
    }

    @Test fun sharedGazeSmoothingAndRibbonsAreBoundedAndFinite() {
        var gaze = 0f
        repeat(60) { gaze = AiBallSpin.smooth(gaze, 1f, 1.0 / 60) }
        assertTrue(gaze in .99f..1f)
        assertEquals(2, AiBallSpin.RIBBON_COUNT)
        assertEquals(0f, AiBallSpin.ribbonAlpha(1.7, 8.0), 0f)
        assertEquals(0f, AiBallSpin.ribbonAlpha(.2, .8), 0f)
        assertTrue(AiBallSpin.ribbonAlpha(.2, 8.0) in 0f..0.42f)
    }

    @Test fun holdReleaseClearsDoubleTapHistoryAndPreservesHeldGaze() {
        val held = AiBallInteraction().tap(1000, 10f, 10f, 12f).hold().observe(.4f, -.2f)
        assertEquals(AiBallAction.HOLD, held.action)
        assertEquals(null, held.tapAt)
        assertEquals(.4f, held.x, 0f)
        val released = held.release()
        assertEquals(AiBallAction.RETURN, released.action)
        assertEquals(0f, released.x, 0f)
        assertEquals(null, released.tapAt)
        assertEquals(AiBallAction.REST, released.cancel().action)
        val (x, y) = AiBallDrag.limit(30f, 40f, 10f)
        assertEquals(6f, x, .001f)
        assertEquals(8f, y, .001f)
    }

    @Test fun productionWiringClaimsOnlyConfirmedHoldAndKeepsSingleAnimationOwner() {
        val source = File("src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBall.kt").readText()
        assertTrue(source.contains("PointerEventPass.Initial"))
        assertTrue(source.contains("PointerEventPass.Final"))
        assertTrue(source.contains("requireUnconsumed = true"))
        assertTrue(source.contains("viewConfiguration.touchSlop"))
        val confirmation = source.indexOf("else if (confirmed)")
        val claim = source.indexOf("HorizontalDragZones.claimPointer")
        assertTrue(confirmation > source.indexOf("withTimeoutOrNull(AiBallDrag.HOLD_MS)"))
        assertTrue(claim > confirmation)
        assertFalse(source.substring(0, claim).contains(".consume()"))
        assertTrue(source.contains("== AiBallPressDecision.CANCEL"))
        assertTrue(source.contains("interaction = aiBallStatusInteraction(interaction)"))
        assertTrue(source.contains("delay(AiBallDrag.TIP_MS)"))
        assertTrue(source.contains("HorizontalDragZones.releasePointers(captureOwner)"))
        assertTrue(source.contains("applyGlassDragScale("))
        assertTrue(source.contains("offset = visualOffset"))
        assertFalse(source.contains("detectDragGestures"))
        assertFalse(source.contains("previewIndex"))
        assertFalse(source.contains("previewNext"))
        assertFalse(source.contains("PRESS_SCALE"))
        assertFalse(source.contains("rememberCoroutineScope"))
        assertTrue(source.contains("lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)"))
        assertTrue(source.contains("val moving = enabled && motion.canMove"))
        assertTrue(source.contains("if (moving) gazeX.value"))
        assertTrue(source.contains("CustomAccessibilityAction(\"让小球转一圈\")"))
        assertTrue(source.contains("AiBallStatusTooltip(description, tipAlpha.value)"))
        assertTrue(source.contains("AiBall.actOnce(shown, gazeX, gazeY, blink)"))
        val parent = File("src/main/java/com/muyao/wanzaihyperos/ui/main/SwipeToSwitchSection.kt").readText()
        assertTrue(parent.indexOf("isPointerCaptured(change.id.value)") < parent.indexOf("dx += change.position.x"))
    }

    @Test fun draggedBallParksInsteadOfReturningToOrigin() {
        val source = File("src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBall.kt").readText()
        assertTrue(source.contains("var parkedOffset by remember { mutableStateOf(Offset.Zero) }"))
        assertTrue(source.contains("var dragged = false"))
        // 按下即拖（原有手势优先）：CANCEL 被改判成拖球，而不是丢掉手势。
        assertTrue(source.contains("return@withTimeoutOrNull AiBallPressDecision.HOLD"))
        assertTrue(source.contains("if (dragged) {"))
        // 静止到超时才弹状态浮签；拖球路径不设 HOLD。
        // 从 else if (confirmed) 起算，避开 showStatus() 里更早的那次 aiBallStatusInteraction。
        val confirmed = source.indexOf("else if (confirmed)")
        assertTrue(confirmed > 0)
        assertTrue(source.indexOf("if (dragged) {", confirmed) <
            source.indexOf("interaction = aiBallStatusInteraction(interaction)", confirmed))
        assertTrue(source.contains("AiBallDrag.limit("))
        assertTrue(source.contains("parkedOffset = Offset(lx, ly)"))
        assertTrue(source.contains("parkedOffset + heldOffset"))
        assertTrue(source.contains("else -> parkedOffset"))
        // 拖过就走 cancel() 停住，静止长按仍走 release() 回弹，两条路径都在源码里。
        val parked = source.indexOf("if (held && dragged)")
        val bounced = source.indexOf("} else if (held) interaction = interaction.release()")
        assertTrue(parked >= 0 && bounced > parked)
    }
}
