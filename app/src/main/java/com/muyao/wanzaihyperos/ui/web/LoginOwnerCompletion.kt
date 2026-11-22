package com.muyao.wanzaihyperos.ui.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Optional service failures may degrade, but an old primary login can never report success. */
internal suspend fun <T> WebDisplayLease.completeLogin(action: suspend () -> T): T {
    requireLoginOwner()
    val result = action()
    requireLoginOwner()
    return result
}

private suspend fun WebDisplayLease.requireLoginOwner() {
    currentCoroutineContext().ensureActive()
    if (!isCurrent()) throw CancellationException("Account changed")
}
