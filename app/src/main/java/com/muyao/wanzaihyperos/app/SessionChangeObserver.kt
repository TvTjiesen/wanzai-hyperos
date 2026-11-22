package com.muyao.wanzaihyperos.app

/** Read and advance the observed session revision under the same owner monitor. */
internal class SessionChangeObserver(
    private val monitor: Any,
    private val signature: () -> String,
    private val onChange: () -> Unit,
) {
    private var previous = synchronized(monitor) { signature() }

    fun observe() = synchronized(monitor) {
        val current = signature()
        if (current != previous) {
            previous = current
            onChange()
        }
    }
}

internal suspend fun <T> cleanupOnExit(
    action: suspend () -> T,
    vararg cleanup: suspend () -> Unit,
): T {
    var failure: Throwable? = null
    try {
        return action()
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        for (step in cleanup) {
            try { step() } catch (error: Throwable) {
                if (failure == null) failure = error
                else if (failure !== error) failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
