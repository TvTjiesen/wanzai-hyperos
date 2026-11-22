package com.muyao.wanzaihyperos.ui.design.ai

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AiBallPresentationTest {
    @Test fun optInOnlyKeepsDefaultGeometry() {
        for (state in AiBallState.entries) {
            val baseline = AiBallGlyphs.eyesOf(state)
            val actual = AiBallPresentationPolicy.eyes(null, state, AiBallWork(AiBallWorkPhase.Reading))
            baseline.zip(actual).forEach { (a, b) -> assertArrayEquals(a, b, 0f) }
        }
    }

    @Test fun settingsReadyLooksCuriousWithoutChangingFact() {
        val work = AiBallWork()
        assertEquals(AiBallState.Curious, AiBallPresentationPolicy.faceState(AiBallPresentation.Settings, AiBallState.Idle, work))
        assertEquals(AiBall.labelOf(AiBallState.Idle), AiBallPresentationPolicy.description(AiBallState.Idle, work))
        for (phase in listOf(AiBallWorkPhase.Unknown, AiBallWorkPhase.Error, AiBallWorkPhase.Reading)) {
            assertEquals(AiBallState.Idle, AiBallPresentationPolicy.faceState(AiBallPresentation.Settings, AiBallState.Idle, AiBallWork(phase)))
        }
    }

    @Test fun homeWorkFacesOpenWhileSleepIsUnchanged() {
        for (state in listOf(AiBallState.Focus, AiBallState.Busy, AiBallState.Thinking, AiBallState.Done)) {
            val pair = AiBallPresentationPolicy.eyes(AiBallPresentation.Home, state, AiBallWork())
            val height = pair[0].filterIndexed { index, _ -> index % 2 == 1 }.let { it.max() - it.min() }
            assertTrue(height >= 0.1349f)
            for (i in pair[0].indices step 2) {
                assertEquals(pair[0][i] + AiBallFacePolicy.EYE_CENTER_X * 2, pair[1][i], 0.00001f)
                assertEquals(pair[0][i + 1], pair[1][i + 1], 0f)
            }
        }
        AiBallGlyphs.eyesOf(AiBallState.Sleep).zip(AiBallPresentationPolicy.eyes(AiBallPresentation.Home, AiBallState.Sleep, AiBallWork()))
            .forEach { (a, b) -> assertArrayEquals(a, b, 0f) }
    }

    @Test fun awakeProfileEyesAreFullVerticalEllipses() {
        for (profile in AiBallPresentation.entries) for (state in AiBallState.entries) {
            if (state == AiBallState.Sleep) continue
            val work = AiBallWork()
            val visual = AiBallPresentationPolicy.faceState(profile, state, work)
            val face = AiBallFacePolicy.faceOf(visual)
            val width = face.width.coerceIn(0.090f, 0.112f)
            val height = face.height.coerceAtLeast(0.180f)
            val eyes = AiBallPresentationPolicy.eyes(profile, state, work)
            assertTrue(height > width)
            eyes.forEachIndexed { index, points ->
                val center = if (index == 0) -AiBallFacePolicy.EYE_CENTER_X else AiBallFacePolicy.EYE_CENTER_X
                for (i in points.indices step 2) {
                    val x = (points[i] - center) / (width / 2)
                    val y = (points[i + 1] - face.y) / (height / 2)
                    assertEquals(1f, x * x + y * y, 0.00001f)
                }
            }
        }
    }

    @Test fun successNeedsObservedReadAndNewRevision() {
        var feedback = AiBallFeedback().observe(AiBallWork(resultRevision = 5), true)
        assertEquals(AiBallResponse.None, feedback.response)
        feedback = feedback.observe(AiBallWork(resultRevision = 6), true)
        assertEquals(AiBallResponse.None, feedback.response)
        feedback = feedback.observe(AiBallWork(AiBallWorkPhase.Reading, 6), true)
        feedback = feedback.observe(AiBallWork(resultRevision = 6), true)
        assertEquals(AiBallResponse.None, feedback.response)
        feedback = feedback.observe(AiBallWork(AiBallWorkPhase.Reading, 6), true)
        feedback = feedback.observe(AiBallWork(resultRevision = 7), true)
        assertEquals(AiBallResponse.Success, feedback.response)
        assertEquals(1L, feedback.ticket)
        feedback = feedback.observe(AiBallWork(resultRevision = 7), true)
        assertEquals(AiBallResponse.None, feedback.response)
        assertEquals(1L, feedback.ticket)
    }

    @Test fun unknownFailureAndCancellationNeverCelebrate() {
        for (phase in listOf(AiBallWorkPhase.Unknown, AiBallWorkPhase.Error, AiBallWorkPhase.Waiting)) {
            var feedback = AiBallFeedback().observe(AiBallWork(AiBallWorkPhase.Reading, 3), true)
            feedback = feedback.observe(AiBallWork(phase, 4), true)
            assertNotEquals(AiBallResponse.Success, feedback.response)
            feedback = feedback.observe(AiBallWork(resultRevision = 4), true)
            assertEquals(AiBallResponse.None, feedback.response)
        }
        var feedback = AiBallFeedback().observe(AiBallWork(AiBallWorkPhase.Reading, 3), true)
        feedback = feedback.observe(AiBallWork(AiBallWorkPhase.Reading, 3), false)
        feedback = feedback.observe(AiBallWork(resultRevision = 4), true)
        assertEquals(AiBallResponse.None, feedback.response)
        assertEquals(AiBallResponse.None, AiBallFeedback().observe(AiBallWork(resultRevision = 4), true).response)
    }

    @Test fun newErrorRespondsOnceAndInactiveDropsHistory() {
        var feedback = AiBallFeedback().observe(AiBallWork(), true)
        feedback = feedback.observe(AiBallWork(AiBallWorkPhase.Error, errorRevision = 1), true)
        assertEquals(AiBallResponse.Error, feedback.response)
        feedback = feedback.observe(AiBallWork(AiBallWorkPhase.Error, errorRevision = 1), true)
        assertEquals(AiBallResponse.None, feedback.response)
        feedback = feedback.observe(AiBallWork(AiBallWorkPhase.Error, errorRevision = 2), false)
        feedback = feedback.observe(AiBallWork(AiBallWorkPhase.Error, errorRevision = 2), true)
        assertEquals(AiBallResponse.None, feedback.response)
    }

    @Test fun workFactsRemainWithoutDecorativeHalfRing() {
        val file = File("src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBall.kt")
            .takeIf { it.isFile } ?: File("app/src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBall.kt")
        val source = file.readText()
        assertFalse(source.contains("AiBallPresentationPolicy.showWork"))
        assertFalse(source.contains("AiBallPresentationPolicy.toolPoint"))
        assertEquals("本页正在读取", AiBallPresentationPolicy.description(AiBallState.Loading, AiBallWork(AiBallWorkPhase.Reading)))
        assertEquals("本页信息尚未确认", AiBallPresentationPolicy.description(AiBallState.Loading, AiBallWork(AiBallWorkPhase.Unknown)))
    }

    @Test fun sourceKeepsSingleOwnerStatusSemanticsAndBoundaryGates() {
        val file = File("src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBall.kt")
            .takeIf { it.isFile } ?: File("app/src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBall.kt")
        val source = file.readText()
        assertTrue(source.contains("presentation: AiBallPresentation? = AiBallPresentation.Home"))
        assertTrue(source.contains("moving && interaction.action == AiBallAction.REST"))
        assertTrue(source.contains("moving, interaction.action, interaction.ticket"))
        assertTrue(source.contains("AiBallPresentationPolicy.description(state, work)"))
        assertTrue(source.contains("else AiBallGlyphs.eyesOf(shown)"))
        assertFalse(source.contains("previewIndex"))
        assertTrue(source.contains("interaction = interaction.cancel(); heldOffset = Offset.Zero; tipVisible = false"))
        assertTrue(source.contains("AiBallStatusTooltip(description, tipAlpha.value)"))
        assertTrue(source.contains("AiBallAction.SPIN ->"))
        assertTrue(source.contains("finally { yaw = 0f; ribbonAlpha = 0f }"))
    }
}
