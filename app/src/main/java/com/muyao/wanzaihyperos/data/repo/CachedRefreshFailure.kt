package com.muyao.wanzaihyperos.data.repo

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.net.HzcuException

/** Account/query guards establish ownership before a failed refresh may retain cached data. */
internal fun <T> retainCachedOnFailure(state: ListState<T>, error: HzcuException): ListState<T> {
    val failed = state.failWith(error)
    if (error !is HzcuException.LoginExpired || failed !is ListState.SessionExpired) return failed
    return when (state) {
        is ListState.Content -> state.copy(meta = state.meta.copy(
            refreshing = false, loadingMore = false, stale = true, lastRefreshError = error,
        ))
        is ListState.Empty -> state.copy(meta = state.meta.copy(
            refreshing = false, loadingMore = false, stale = true, lastRefreshError = error,
        ))
        else -> failed
    }
}
