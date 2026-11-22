package com.muyao.wanzaihyperos.ui.design.ai

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Generated safe-face policy v1. MIT Grok Ball motion attribution retained; no legacy eye rings. */
internal object AiBallFacePolicy {
    const val POINTS = 48
    const val EYE_CENTER_X = 0.145f
    data class Face(val width: Float, val height: Float, val y: Float)

    fun faceOf(state: AiBallState): Face = when (state) {
        AiBallState.Sleep -> Face(0.150f, 0.014f, 0.035f)
        AiBallState.WakeUp -> Face(0.095f, 0.065f, 0.015f)
        AiBallState.Idle -> Face(0.090f, 0.125f, 0.000f)
        AiBallState.Curious -> Face(0.112f, 0.160f, -0.025f)
        AiBallState.Focus -> Face(0.130f, 0.070f, 0.000f)
        AiBallState.Thinking -> Face(0.102f, 0.095f, -0.035f)
        AiBallState.Busy -> Face(0.115f, 0.082f, 0.020f)
        AiBallState.Done -> Face(0.145f, 0.045f, -0.015f)
        AiBallState.Error -> Face(0.110f, 0.145f, 0.025f)
        AiBallState.Waiting -> Face(0.100f, 0.135f, -0.012f)
        AiBallState.Loading -> Face(0.128f, 0.155f, 0.000f)
        AiBallState.Searching -> Face(0.085f, 0.165f, -0.020f)
    }

    /** Equal, horizontal stadiums translated to fixed separated centers; no inward tilt. */
    fun eyesOf(state: AiBallState): List<FloatArray> {
        val face = faceOf(state)
        val radius = minOf(face.width, face.height) / 2.0
        val horizontal = face.width / 2.0 - radius
        val vertical = face.height / 2.0 - radius
        return listOf(-EYE_CENTER_X, EYE_CENTER_X).map { center ->
            FloatArray(POINTS * 2).also { out ->
                repeat(POINTS) { i ->
                    val angle = 2.0 * PI * i / POINTS
                    val cx = cos(angle)
                    val sy = sin(angle)
                    out[i * 2] = (center + radius * cx + if (cx >= 0) horizontal else -horizontal).toFloat()
                    out[i * 2 + 1] = (face.y + radius * sy + if (sy >= 0) vertical else -vertical).toFloat()
                }
            }
        }
    }
}
