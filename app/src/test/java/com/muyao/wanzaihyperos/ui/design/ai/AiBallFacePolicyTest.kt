package com.muyao.wanzaihyperos.ui.design.ai

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBallFacePolicyTest {
    private fun source(path: String): String = sequenceOf(File(path), File("../$path"))
        .first { it.isFile }.readText(Charsets.UTF_8)

    @Test fun allTwelveStatesHaveDistinctStaticFacesIncludingIdleLoadingAndFocus() {
        assertEquals(12, AiBallState.entries.size)
        assertEquals(12, AiBallState.entries.map(AiBallFacePolicy::faceOf).distinct().size)
        assertTrue(AiBallFacePolicy.faceOf(AiBallState.Loading).height > AiBallFacePolicy.faceOf(AiBallState.Idle).height)
        assertTrue(AiBallFacePolicy.faceOf(AiBallState.Focus).height < AiBallFacePolicy.faceOf(AiBallState.Idle).height)
        assertTrue(AiBallState.Loading.isWorking)
        assertFalse(AiBallState.Error.isWorking)
    }

    @Test fun everyEyePairIsSeparatedFiniteAndTranslatedIdenticalRatherThanInwardTilted() {
        AiBallState.entries.forEach { state ->
            val eyes = AiBallGlyphs.eyesOf(state)
            assertEquals(2, eyes.size)
            assertEquals(AiBallFacePolicy.POINTS * 2, eyes[0].size)
            val left = eyes[0]; val right = eyes[1]
            for (i in left.indices step 2) {
                assertTrue(left[i].isFinite() && left[i + 1].isFinite())
                assertTrue(left[i] < 0 && right[i] > 0)
                assertEquals((2 * AiBallFacePolicy.EYE_CENTER_X).toDouble(), (right[i] - left[i]).toDouble(), 0.000001)
                assertEquals(left[i + 1].toDouble(), right[i + 1].toDouble(), 0.000001)
                assertTrue(abs(left[i]) < 0.3f && abs(left[i + 1]) < 0.2f)
            }
            assertEquals(AiBallFacePolicy.eyesOf(state)[0].toList(), left.toList())
        }
    }

    @Test fun policyManifestAndGeneratedKotlinHaveIdenticalStateParameters() {
        val manifest = source("app/src/debug/assets/grokball/safe-face-policy.json")
        val regex = Regex("\\\"state\\\":\\\"(\\w+)\\\".*?\\\"width\\\":([0-9.]+).*?\\\"height\\\":([0-9.]+).*?\\\"y\\\":(-?[0-9.]+)")
        val rows = regex.findAll(manifest).toList()
        assertEquals(12, rows.size)
        rows.forEach { row ->
            val face = AiBallFacePolicy.faceOf(AiBallState.valueOf(row.groupValues[1]))
            assertEquals(row.groupValues[2].toFloat(), face.width, 0f)
            assertEquals(row.groupValues[3].toFloat(), face.height, 0f)
            assertEquals(row.groupValues[4].toFloat(), face.y, 0f)
        }
    }

    @Test fun generatorCannotBakeLegacyDomAndDebugImportsToursFallbacksShareTheGate() {
        val generator = source("tools/gen_ai_ball_glyphs.py")
        assertTrue(generator.contains("--check"))
        assertTrue(generator.contains("safe-face-policy.json"))
        assertFalse(generator.contains("engine_dom12"))
        val js = source("app/src/debug/assets/grokball/grok-ball.js")
        assertTrue(js.contains("EXPRESSIONS: window.EB_SAFE_FACE.expressions"))
        assertFalse(js.contains("EXPRESSIONS: [[[["))
        assertTrue(js.contains("var pool = [window.EB_SAFE_FACE.indexOf(raw.id)]"))
        assertTrue(js.contains("return window.EB_SAFE_FACE.constrain(pose)"))
        assertTrue(js.contains("function applyPose(pose) {\n      window.EB_SAFE_FACE.constrain(pose)"))
        assertTrue(js.contains("a.rotate = b.rotate = 0"))
        assertTrue(js.contains("pose.body.scale = 1"))
        listOf("spin: function", "bounce: function", "setGaze: function", "registerEmotion: function", "startTour: function", "importConfig: function").forEach {
            assertTrue("Missing existing API: $it", js.contains(it))
        }
    }
}
