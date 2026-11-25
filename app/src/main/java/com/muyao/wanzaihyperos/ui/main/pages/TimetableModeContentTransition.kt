package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.muyao.wanzaihyperos.ui.design.rememberMotion

/**
 * Animate today's agenda and the week board as one stable list item. The caller keeps the same
 * LazyColumn anchor; this transition only moves the inner content. A transparent pointer shield
 * prevents tapping two overlapping courses while the outgoing layout is still visible.
 */
@Composable
internal fun TimetableModeContentTransition(
    weekMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (showWeek: Boolean) -> Unit,
) {
    val motion = rememberMotion()
    AnimatedContent(
        targetState = weekMode,
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            if (!motion.canMove) {
                (EnterTransition.None togetherWith ExitTransition.None)
                    .using(SizeTransform(sizeAnimationSpec = { _, _ -> snap() }))
            } else {
                val direction = if (targetState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 9 * direction } +
                    fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)))
                    .togetherWith(slideOutHorizontally(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { -it / 9 * direction } +
                        fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)))
            }
        },
        label = "timetable-mode-content",
    ) { target ->
        Box(Modifier.fillMaxWidth()) {
            val changing = transition.currentState != transition.targetState
            Box(if (changing) Modifier.clearAndSetSemantics { } else Modifier) { content(target) }
            if (changing) {
                Box(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
                )
            }
        }
    }
}
