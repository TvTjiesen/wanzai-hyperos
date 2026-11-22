package com.muyao.wanzaihyperos.ui.design.ai

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class AiBallAction { REST, TRACK, LOOK, SPIN, HOLD, RETURN }

internal data class AiBallInteraction(
    val action: AiBallAction = AiBallAction.REST,
    val ticket: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val tapAt: Long? = null,
    val tapX: Float = 0f,
    val tapY: Float = 0f,
) {
    fun observe(x: Float, y: Float): AiBallInteraction = if (action == AiBallAction.SPIN) this else copy(
        action = if (action == AiBallAction.HOLD) AiBallAction.HOLD else AiBallAction.TRACK,
        ticket = if (action == AiBallAction.TRACK || action == AiBallAction.HOLD) ticket else ticket + 1,
        x = finiteUnit(x), y = finiteUnit(y),
    )

    fun tap(now: Long, px: Float, py: Float, doubleSlop: Float): AiBallInteraction {
        if (action == AiBallAction.SPIN) return this
        val interval = tapAt?.let { now - it }
        val close = (px - tapX) * (px - tapX) + (py - tapY) * (py - tapY) <= doubleSlop * doubleSlop
        return if (interval != null && interval in 0..DOUBLE_TAP_MS && close) spin()
        else copy(action = AiBallAction.LOOK, ticket = ticket + 1, tapAt = now, tapX = px, tapY = py)
    }

    fun spin(): AiBallInteraction = if (action == AiBallAction.SPIN) this else copy(
        action = AiBallAction.SPIN, ticket = ticket + 1, x = 0f, y = 0f, tapAt = null,
    )

    fun hold(): AiBallInteraction = copy(action = AiBallAction.HOLD, ticket = ticket + 1, tapAt = null)

    fun release(): AiBallInteraction = copy(action = AiBallAction.RETURN, ticket = ticket + 1, x = 0f, y = 0f, tapAt = null)

    fun cancel(): AiBallInteraction = AiBallInteraction(ticket = ticket + 1)

    companion object {
        const val DOUBLE_TAP_MS = 300L
        fun finiteUnit(value: Float): Float = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
    }
}

/** grok-ball (MIT): springStep(w=6.2,z=1), integer-turn yaw and cosine surface projection. */
internal object AiBallSpin {
    const val FREQUENCY = 6.2
    const val TURN = 2.0 * Math.PI
    const val MAX_SECONDS = 2.4
    const val RIBBON_COUNT = 2

    data class Spring(val angle: Double = 0.0, val velocity: Double = 0.0) {
        val settled: Boolean get() = abs(TURN - angle) < 0.01 && abs(velocity) < 0.05
    }

    fun step(spring: Spring, elapsed: Double): Spring {
        if (!elapsed.isFinite() || elapsed <= 0.0) return spring
        val dt = elapsed.coerceAtMost(0.05)
        val steps = kotlin.math.ceil(dt * 120.0).toInt().coerceAtLeast(1)
        val h = dt / steps
        var x = spring.angle
        var v = spring.velocity
        repeat(steps) {
            v += (FREQUENCY * FREQUENCY * (TURN - x) - 2.0 * FREQUENCY * v) * h
            x += v * h
        }
        return Spring(x, v)
    }

    data class EyeProjection(val centerX: Float, val scaleX: Float, val visible: Boolean)

    fun project(centerX: Float, centerY: Float, yaw: Double): EyeProjection {
        if (!centerX.isFinite() || !centerY.isFinite() || !yaw.isFinite()) return EyeProjection(0f, 0f, false)
        val halfWidth = sqrt(max(0.01f, 0.25f - centerY * centerY))
        val theta = asin((centerX / halfWidth).coerceIn(-0.92f, 0.92f).toDouble())
        val cosine = cos(theta + yaw)
        return EyeProjection(
            (halfWidth * sin(theta + yaw)).toFloat(),
            (cosine / cos(theta)).toFloat().coerceIn(0f, 2.6f),
            cosine > 0.02,
        )
    }

    fun smooth(current: Float, target: Float, dt: Double): Float {
        val k = (1.0 - exp(-5.66 * dt.coerceIn(0.0, 0.05))).toFloat()
        return current + (target - current) * k
    }

    fun ribbonAlpha(seconds: Double, velocity: Double): Float =
        if (seconds !in 0.0..1.6 || abs(velocity) < 0.9) 0f
        else ((1.6 - seconds).coerceAtMost(0.35) / 0.35 * 0.42).toFloat()
}
