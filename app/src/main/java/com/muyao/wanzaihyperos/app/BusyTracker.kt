package com.muyao.wanzaihyperos.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** IDs keep a cancelled old generation from decrementing new work. */
class BusyTracker {
    private val active = mutableSetOf<Long>()
    private var sequence = 0L
    private val count = MutableStateFlow(0)
    val state: StateFlow<Int> = count

    @Synchronized fun begin(): Long = (++sequence).also { active += it; count.value = active.size }
    @Synchronized fun end(id: Long) { active -= id; count.value = active.size }
    @Synchronized fun reset() { active.clear(); count.value = 0 }
}
