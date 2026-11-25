package com.muyao.wanzaihyperos.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Shared, request-scoped toast state. It deliberately never reports success: BusyTracker only
 * knows that tracked work ended, not whether any response was successful or useful. */
class RefreshToastController(
    private val showDelayMs: Long = 650L,
    private val hideDelayMs: Long = 400L,
    private val cooldownMs: Long = 8_000L,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    suspend fun observe(busy: StateFlow<Int>) {
        var lastShownAt: Long? = null
        try {
            busy.map { it > 0 }.distinctUntilChanged().collectLatest { active ->
                if (active && _message.value == null) {
                    if (lastShownAt?.let { nowMs() - it < cooldownMs } == true) return@collectLatest
                    delay(showDelayMs)
                    if (busy.value > 0) {
                        lastShownAt = nowMs()
                        _message.value = "正在更新 · 保留已有内容"
                    }
                } else if (!active && _message.value != null) {
                    delay(hideDelayMs)
                    if (busy.value == 0) _message.value = null
                }
            }
        } finally {
            _message.value = null
        }
    }
}
