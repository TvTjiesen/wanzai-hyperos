package com.muyao.wanzaihyperos.ui.design.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay

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

/** Only owns lifetime; the page's original ball renders the shared feedback inline. */
@Composable
internal fun AiBallNoticeHost(state: AiBallNoticeState) {
    val notice = state.current ?: return
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(notice, accessibility) {
        val duration = if (notice.text.length > 28) 5400L else 3600L
        delay(accessibility?.calculateRecommendedTimeoutMillis(duration, containsIcons = true, containsText = true) ?: duration)
        state.expire(notice)
    }
}
