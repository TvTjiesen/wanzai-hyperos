package com.muyao.wanzaihyperos.ui.web

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

internal data class Cjh52Credential(val token: String, val revision: Long) {
    override fun toString(): String = "Cjh52Credential(revision=$revision)"
}

internal class Cjh52Recovery(
    private val epoch: AccountEpoch,
    private val scope: CoroutineScope,
    private val readCredential: () -> Cjh52Credential?,
    private val clearCredential: () -> Unit,
    private val saveCredential: (String) -> Unit,
    private val acquire: suspend (owner: Long) -> String?,
    private val timeoutMillis: Long = 40_000,
) {
    enum class Status { Idle, Connecting, Ready, Failed }

    private val _status = MutableStateFlow(if (readCredential() != null) Status.Ready else Status.Idle)
    val status = _status.asStateFlow()
    private var flight: Deferred<Cjh52Credential?>? = null
    private var rejected: Cjh52Credential? = null

    init {
        epoch.registerReset {
            flight = null
            rejected = null
            _status.value = Status.Idle
        }
    }

    fun current(owner: Long): Cjh52Credential? {
        var credential: Cjh52Credential? = null
        withOwner(owner) { credential = usableCredential() }
        return credential
    }

    fun reject(owner: Long, used: Cjh52Credential, allowRecovery: Boolean) = withOwner(owner) {
        if (usableCredential() != used) return@withOwner
        rejected = used
        _status.value = if (allowRecovery) Status.Idle else Status.Failed
        try {
            clearCredential()
        } catch (error: Exception) {
            _status.value = Status.Failed
            throw error
        }
    }

    suspend fun ensure(owner: Long, manual: Boolean = false): Cjh52Credential? {
        currentCoroutineContext().ensureActive()
        var credential: Cjh52Credential? = null
        var pending: Deferred<Cjh52Credential?>? = null
        withOwner(owner) {
            credential = usableCredential()
            if (credential != null) {
                _status.value = Status.Ready
            } else if (_status.value != Status.Failed || manual) {
                pending = flight?.takeUnless { it.isCompleted } ?: startFlight(owner)
            }
        }
        return credential ?: pending?.await()
    }

    private fun startFlight(owner: Long): Deferred<Cjh52Credential?> {
        _status.value = Status.Connecting
        val task = scope.async(start = CoroutineStart.LAZY) {
            val token = try {
                withTimeoutOrNull(timeoutMillis) { acquire(owner) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            var result: Cjh52Credential? = null
            withOwner(owner) {
                result = usableCredential()
                if (result == null && token != null) {
                    try {
                        saveCredential(token)
                        result = usableCredential()
                    } catch (_: Exception) {
                        result = null
                    }
                }
                _status.value = if (result != null) Status.Ready else Status.Failed
            }
            result
        }
        flight = task
        epoch.track(owner, task)
        task.invokeOnCompletion {
            epoch.untrack(task)
            epoch.publishData(owner) {
                if (flight === task) {
                    flight = null
                    if (_status.value == Status.Connecting) _status.value = Status.Failed
                }
            }
        }
        return task
    }

    private fun usableCredential(): Cjh52Credential? = readCredential()?.takeUnless { it == rejected }

    private inline fun withOwner(owner: Long, crossinline action: () -> Unit) {
        if (!epoch.publishData(owner) { action() }) throw CancellationException("Account changed")
    }
}
