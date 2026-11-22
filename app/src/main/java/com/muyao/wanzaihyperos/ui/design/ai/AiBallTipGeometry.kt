package com.muyao.wanzaihyperos.ui.design.ai

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal data class AiTipPoint(val x: Float, val y: Float)
internal data class AiTipRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val valid get() = listOf(left, top, right, bottom, width, height, centerX, centerY).all { it.isFinite() } && width > 0f && height > 0f
}

/** Two tangent cubics and two interior chords; no raster field, sampling loop or idle work. */
internal data class AiTipNeck(
    val sourceLeft: AiTipPoint, val sourceLeftControl: AiTipPoint,
    val targetLeftControl: AiTipPoint, val targetLeft: AiTipPoint,
    val targetRight: AiTipPoint, val targetRightControl: AiTipPoint,
    val sourceRightControl: AiTipPoint, val sourceRight: AiTipPoint,
)

internal fun aiTipBody(ball: AiTipRect, target: AiTipRect, progress: Float): AiTipRect? {
    if (!ball.valid || !target.valid || !progress.isFinite()) return null
    val p = progress.coerceIn(0f, 1f)
    val above = target.centerY < ball.centerY
    val start = AiTipRect(
        ball.centerX - ball.width * .15f,
        if (above) ball.top else ball.bottom - ball.height * .2f,
        ball.centerX + ball.width * .15f,
        if (above) ball.top + ball.height * .2f else ball.bottom,
    )
    fun mix(a: Float, b: Float) = a + (b - a) * p
    return AiTipRect(mix(start.left, target.left), mix(start.top, target.top),
        mix(start.right, target.right), mix(start.bottom, target.bottom))
}

internal fun aiTipNeck(ball: AiTipRect, body: AiTipRect, progress: Float, radius: Float): AiTipNeck? {
    if (!ball.valid || !body.valid || !progress.isFinite() || !radius.isFinite()) return null
    val p = progress.coerceIn(0f, 1f)
    // Pinch off before the resting capsule; reverse the same geometry on release.
    val strength = min((p / .25f).coerceIn(0f, 1f), ((.94f - p) / .24f).coerceIn(0f, 1f))
    if (strength <= .01f) return null
    val direction = if (body.centerY < ball.centerY) -1f else 1f
    val edge = if (direction < 0f) ball.top else ball.bottom
    val targetY = if (direction < 0f) body.bottom else body.top
    val gap = (targetY - edge) * direction
    val rx = ball.width / 2f
    val ry = ball.height / 2f
    if (gap < 0f || gap > min(rx, ry) * 1.8f) return null
    val corner = radius.coerceIn(0f, min(body.width, body.height) / 2f)
    val half = min(body.width * .2f, rx * .8f) * strength
    val targetHalf = min(half, (body.width - 2f * corner).coerceAtLeast(0f) / 2f)
    if (targetHalf <= .01f) return null
    val targetX = ball.centerX.coerceIn(body.left + corner + targetHalf, body.right - corner - targetHalf)
    if (abs(targetX - ball.centerX) > rx * 1.5f) return null
    val angle = .55f * strength
    val sourceHalf = rx * sin(angle)
    val sourceY = ball.centerY + direction * ry * cos(angle)
    val handle = min((gap + ry * .2f) * .6f, ry * .8f)
    val sourceHandle = min(handle, sourceHalf * .8f / cos(angle))
    val dx = cos(angle) * sourceHandle
    val targetHandle = min(handle, targetHalf * .8f)
    val dy = direction * sin(angle) * sourceHandle * ry / rx
    return AiTipNeck(
        AiTipPoint(ball.centerX - sourceHalf, sourceY),
        AiTipPoint(ball.centerX - sourceHalf + dx, sourceY + dy),
        AiTipPoint(targetX - targetHalf + targetHandle, targetY),
        AiTipPoint(targetX - targetHalf, targetY),
        AiTipPoint(targetX + targetHalf, targetY),
        AiTipPoint(targetX + targetHalf - targetHandle, targetY),
        AiTipPoint(ball.centerX + sourceHalf - dx, sourceY + dy),
        AiTipPoint(ball.centerX + sourceHalf, sourceY),
    )
}
