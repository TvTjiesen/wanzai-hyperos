package com.muyao.wanzaihyperos.ui.design.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class AiBallNoticeTone(val title: String, val face: AiBallState) {
    Information("提示", AiBallState.Curious),
    Success("已完成", AiBallState.Done),
    Warning("未完成", AiBallState.Error),
}

internal class AiBallNotice(val text: String, val tone: AiBallNoticeTone)

/** Transient, latest-wins feedback, scoped to this app window. */
internal class AiBallNoticeState {
    var current by mutableStateOf<AiBallNotice?>(null)
        private set

    fun show(text: String, tone: AiBallNoticeTone = AiBallNoticeTone.Information) {
        if (text.isNotBlank()) current = AiBallNotice(text, tone)
    }

    fun clear() { current = null }

    fun expire(notice: AiBallNotice) {
        if (current === notice) clear()
    }
}

@Composable
internal fun AiBallNoticeHost(state: AiBallNoticeState, modifier: Modifier = Modifier) {
    val notice = state.current ?: return
    val motion = rememberMotion()
    val accessibility = LocalAccessibilityManager.current
    val progress = remember(notice) { Animatable(0f) }
    LaunchedEffect(notice, motion.canMove, accessibility) {
        if (motion.canMove) progress.animateTo(1f, tween(210)) else progress.snapTo(1f)
        val duration = if (notice.text.length > 28) 5400L else 3600L
        delay(accessibility?.calculateRecommendedTimeoutMillis(duration, containsIcons = true, containsText = true) ?: duration)
        if (motion.canMove) progress.animateTo(0f, tween(150))
        state.expire(notice)
    }
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(Dims.cardCorner)
    val tint = if (notice.tone == AiBallNoticeTone.Warning) colors.error else colors.primary
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp).widthIn(max = 440.dp)
            .graphicsLayer {
                val p = if (motion.canMove) progress.value else 1f
                alpha = p
                translationY = (1f - p) * 10.dp.toPx()
                scaleX = .97f + .03f * p
                scaleY = scaleX
            }
            .shadow(if (motion.highContrast) 0.dp else 8.dp, shape)
            .background(colors.surface, shape)
            .border(1.dp, if (motion.highContrast) colors.onSurface else colors.dividerLine, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Use the real character and a message-specific expression; don't create a second
        // interactive ball or interfere with the page's existing press, drag and status tip.
        CompositionLocalProvider(LocalPageActive provides false) {
            Box(Modifier.size(36.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                AiBall(state = notice.tone.face, diameter = 34.dp, hitSlop = 0.dp, castShadow = false)
            }
        }
        Column(Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(notice.tone.title, color = tint, fontSize = Type.caption, fontWeight = Type.weightTitle)
            Text(notice.text, color = colors.onSurface, fontSize = Type.body, lineHeight = Type.body * Type.LINE_HEIGHT_BODY)
        }
    }
}
