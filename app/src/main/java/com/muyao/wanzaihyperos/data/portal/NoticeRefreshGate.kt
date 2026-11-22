package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull

internal const val NOTICE_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

internal class NoticeRefreshGate {
    private val attempts = mutableMapOf<String, Long>()

    fun claim(key: String, state: ListState<*>, now: Long): Boolean {
        val meta = state.metaOrNull
        if (meta?.refreshing == true || meta?.loadingMore == true) return false
        val updated = meta?.updatedAtMillis
        if (updated != null && now >= updated && now - updated < NOTICE_REFRESH_INTERVAL_MS) return false
        val attempted = attempts[key]
        if (attempted != null && now >= attempted && now - attempted < NOTICE_REFRESH_INTERVAL_MS) return false
        attempts[key] = now
        return true
    }
}
