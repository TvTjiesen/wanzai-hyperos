package com.muyao.wanzaihyperos.ui.design.ai

internal enum class AiBallPressDecision { WAIT, TAP, HOLD, CANCEL }

internal fun aiBallPressDecision(
    elapsedMillis: Long,
    distance: Float,
    touchSlop: Float,
    pressed: Boolean,
    activePointers: Int,
    consumed: Boolean,
): AiBallPressDecision = when {
    consumed || elapsedMillis < 0 || !distance.isFinite() || !touchSlop.isFinite() ||
        distance < 0f || touchSlop < 0f || distance > touchSlop -> AiBallPressDecision.CANCEL
    activePointers != (if (pressed) 1 else 0) -> AiBallPressDecision.CANCEL
    !pressed -> AiBallPressDecision.TAP
    elapsedMillis >= AiBallDrag.HOLD_MS -> AiBallPressDecision.HOLD
    else -> AiBallPressDecision.WAIT
}

internal fun aiBallStatusInteraction(interaction: AiBallInteraction): AiBallInteraction =
    interaction.cancel().hold()
