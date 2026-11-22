package com.muyao.wanzaihyperos.ui.design.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AiBallPressPolicyTest {
    private fun decision(
        elapsed: Long = 0L,
        distance: Float = 0f,
        pressed: Boolean = true,
        pointers: Int = if (pressed) 1 else 0,
        consumed: Boolean = false,
    ) = aiBallPressDecision(elapsed, distance, 8f, pressed, pointers, consumed)

    @Test fun stationaryPressOnlyClaimsAfterTimeout() {
        assertEquals(AiBallPressDecision.WAIT, decision())
        assertEquals(AiBallPressDecision.WAIT, decision(AiBallDrag.HOLD_MS - 1, 8f))
        assertEquals(AiBallPressDecision.HOLD, decision(AiBallDrag.HOLD_MS, 8f))
        assertEquals(AiBallPressDecision.TAP, decision(100L, pressed = false))
    }

    @Test fun swipeCancellationAndOtherOwnersNeverBecomeHold() {
        assertEquals(AiBallPressDecision.CANCEL, decision(40L, 8.01f))
        assertEquals(AiBallPressDecision.CANCEL, decision(AiBallDrag.HOLD_MS, 30f))
        assertEquals(AiBallPressDecision.CANCEL, decision(AiBallDrag.HOLD_MS, pointers = 2))
        assertEquals(AiBallPressDecision.CANCEL, decision(AiBallDrag.HOLD_MS, consumed = true))
        assertEquals(AiBallPressDecision.CANCEL, decision(100L, pressed = false, pointers = 1))
        assertEquals(AiBallPressDecision.CANCEL, decision(-1L))
        assertEquals(AiBallPressDecision.CANCEL, decision(distance = Float.NaN))
    }

    @Test fun accessibleStatusStartsVisibleHoldEvenDuringSpinAndCanRetire() {
        val spinning = AiBallInteraction().spin()
        val shown = aiBallStatusInteraction(spinning)
        assertEquals(AiBallAction.HOLD, shown.action)
        assertTrue(shown.ticket > spinning.ticket)
        assertEquals(null, shown.tapAt)
        assertEquals(0f, shown.x, 0f)
        assertEquals(AiBallAction.RETURN, shown.release().action)
        assertEquals(AiBallAction.REST, shown.release().cancel().action)
        val source = File("src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBall.kt").readText()
        val accessible = source.substringAfter("fun showStatus()").substringBefore("fun accessibleLook()")
        assertTrue(accessible.contains("autoDismissStatus = true"))
        assertTrue(accessible.contains("aiBallStatusInteraction(interaction)"))
        assertFalse(accessible.contains("interaction.release()"))
        assertTrue(source.contains("tipAlpha.animateTo(1f"))
        assertTrue(source.contains("delay(AiBallDrag.TIP_MS)"))
    }

    @Test fun tooltipKeepsPointerAndFocusOnOriginAndNeverScalesText() {
        val tooltip = File("src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBallStatusTooltip.kt").readText()
        assertTrue(tooltip.contains("PopupProperties(focusable = false"))
        assertFalse(tooltip.contains("scaleX"))
        assertFalse(tooltip.contains("scaleY"))
    }
}
