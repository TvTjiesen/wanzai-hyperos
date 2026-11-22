package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull

const val TIMETABLE_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L

fun shouldRefreshTimetable(state: ListState<*>, now: Long): Boolean {
    val meta = state.metaOrNull
    if (meta?.refreshing == true) return false
    val updated = meta?.updatedAtMillis ?: return true
    return state !is ListState.Content && state !is ListState.Empty ||
        now < updated || now - updated >= TIMETABLE_REFRESH_INTERVAL_MS
}
