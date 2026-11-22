package com.muyao.wanzaihyperos.data.repo

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.session.StoredSession

internal fun <T> ListState<T>.finishRefresh(): ListState<T> = when (this) {
    is ListState.Content -> copy(meta = meta.copy(refreshing = false, loadingMore = false))
    is ListState.Empty -> copy(meta = meta.copy(refreshing = false, loadingMore = false))
    else -> this
}

/** Secondary replacements invalidate only this source, under its credential commit monitor. */
internal class SecondaryGuard(
    private val epoch: AccountEpoch,
    private val session: StoredSession,
    private val reset: () -> Unit,
) {
    private val guard = StaleGuard(epoch)
    private var revision = session.credentialRevision()
    private var observed = revision

    init {
        session.addListener {
            epoch.publish(epoch.token()) {
                val current = session.credentialRevision()
                session.publishCredential(current) { observe(current) }
            }
        }
    }

    fun begin(): Long {
        var token = 0L
        epoch.publish(epoch.token()) {
            val current = session.credentialRevision()
            session.publishCredential(current) {
                observe(current)
                revision = current
                token = guard.begin()
            }
        }
        return token
    }

    private fun observe(current: Long) {
        if (observed == current) return
        observed = current
        guard.invalidate()
        reset()
    }

    fun invalidate() = guard.invalidate()
    fun isCurrent(token: Long): Boolean = publish(token) {}

    fun publish(token: Long, action: () -> Unit): Boolean {
        var accepted = false
        guard.publish(token) { accepted = session.publishCredential(revision, action) }
        return accepted
    }
}
