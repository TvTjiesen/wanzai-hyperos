package com.muyao.wanzaihyperos.ui.design.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiBallNoticeStateTest {
    @Test fun blankMessageDoesNotReplaceVisibleNotice() {
        val state = AiBallNoticeState()
        state.show("saved")
        state.show("  ")
        assertEquals("saved", state.current?.text)
    }

    @Test fun newestNoticeWinsAndStaleTimeoutCannotDismissIt() {
        val state = AiBallNoticeState()
        state.show("first")
        val first = state.current!!
        state.show("second", AiBallNoticeTone.Success)
        state.expire(first)
        assertEquals("second", state.current?.text)
        assertEquals(AiBallNoticeTone.Success, state.current?.tone)
    }

    @Test fun currentNoticeExpires() {
        val state = AiBallNoticeState()
        state.show("warning", AiBallNoticeTone.Warning)
        val current = state.current!!
        state.expire(current)
        assertNull(state.current)
    }
}
