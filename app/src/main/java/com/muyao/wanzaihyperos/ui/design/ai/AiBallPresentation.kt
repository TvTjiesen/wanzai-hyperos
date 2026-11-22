package com.muyao.wanzaihyperos.ui.design.ai

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class AiBallPresentation { Home, Settings }
enum class AiBallWorkPhase { Ready, Reading, Waiting, Unknown, Error }

data class AiBallWork(
    val phase: AiBallWorkPhase = AiBallWorkPhase.Ready,
    val resultRevision: Long = 0L,
    val errorRevision: Long = 0L,
)

internal enum class AiBallResponse { None, Success, Error }

internal data class AiBallFeedback(
    val initialized: Boolean = false,
    val lastResult: Long = 0L,
    val lastError: Long = 0L,
    val readingFrom: Long? = null,
    val response: AiBallResponse = AiBallResponse.None,
    val ticket: Long = 0L,
) {
    fun observe(work: AiBallWork, active: Boolean): AiBallFeedback {
        if (!active) return copy(
            initialized = true, lastResult = work.resultRevision, lastError = work.errorRevision,
            readingFrom = null, response = AiBallResponse.None,
        )
        val event = when {
            !initialized -> AiBallResponse.None
            work.phase == AiBallWorkPhase.Error && work.errorRevision > lastError -> AiBallResponse.Error
            work.phase == AiBallWorkPhase.Ready && readingFrom != null &&
                work.resultRevision > readingFrom -> AiBallResponse.Success
            else -> AiBallResponse.None
        }
        return copy(
            initialized = true,
            lastResult = work.resultRevision,
            lastError = work.errorRevision,
            readingFrom = if (work.phase == AiBallWorkPhase.Reading) readingFrom ?: work.resultRevision else null,
            response = event,
            ticket = if (event != AiBallResponse.None) ticket + 1L else ticket,
        )
    }
}

internal object AiBallPresentationPolicy {
    fun faceState(profile: AiBallPresentation?, state: AiBallState, work: AiBallWork): AiBallState =
        if (profile == AiBallPresentation.Settings && state == AiBallState.Idle &&
            work.phase == AiBallWorkPhase.Ready) AiBallState.Curious else state

    fun eyes(profile: AiBallPresentation?, state: AiBallState, work: AiBallWork): List<FloatArray> {
        val visual = faceState(profile, state, work)
        if (profile == null || visual == AiBallState.Sleep) return AiBallGlyphs.eyesOf(visual)
        val face = AiBallFacePolicy.faceOf(visual)
        val width = face.width.coerceIn(0.090f, 0.112f)
        val height = face.height.coerceAtLeast(0.180f)
        return listOf(-AiBallFacePolicy.EYE_CENTER_X, AiBallFacePolicy.EYE_CENTER_X).map { center ->
            FloatArray(AiBallFacePolicy.POINTS * 2).also { points ->
                repeat(AiBallFacePolicy.POINTS) { i ->
                    val angle = 2.0 * PI * i / AiBallFacePolicy.POINTS
                    points[i * 2] = center + (cos(angle) * width / 2.0).toFloat()
                    points[i * 2 + 1] = face.y + (sin(angle) * height / 2.0).toFloat()
                }
            }
        }
    }

    fun gapMs(state: AiBallState, work: AiBallWork): Long = when {
        state == AiBallState.Sleep -> 4_200L
        work.phase == AiBallWorkPhase.Reading -> 1_900L
        else -> 3_600L
    }

    fun description(state: AiBallState, work: AiBallWork): String = when (work.phase) {
        AiBallWorkPhase.Reading -> "本页正在读取"
        AiBallWorkPhase.Unknown -> "本页信息尚未确认"
        AiBallWorkPhase.Error -> "本次读取未成功"
        AiBallWorkPhase.Waiting -> "等待你查看或输入"
        AiBallWorkPhase.Ready -> AiBall.labelOf(state)
    }


}
