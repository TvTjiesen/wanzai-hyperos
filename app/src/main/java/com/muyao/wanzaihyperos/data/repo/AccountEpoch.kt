package com.muyao.wanzaihyperos.data.repo

import com.muyao.wanzaihyperos.net.RequestIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.security.MessageDigest

/** One process owner boundary shared by request publication, reset and disk writes. */
class AccountEpoch(private val lock: Any = Any()) {
    private var generation = 0L
    private var owner: String? = null
    private var accepting = true
    private var dataAllowed = true
    private val resets = mutableListOf<() -> Unit>()
    private val jobs = mutableSetOf<Job>()

    fun identity(): RequestIdentity? = synchronized(lock) {
        owner?.takeIf { accepting && dataAllowed }?.let { RequestIdentity(it, generation) }
    }

    fun token(): Long = synchronized(lock) { generation }
    fun isCurrent(token: Long): Boolean = synchronized(lock) { accepting && token == generation }

    fun publish(token: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (!accepting || token != generation) return false
        action()
        true
    }

    fun registerReset(reset: () -> Unit) = synchronized(lock) { resets += reset }
    fun isDataCurrent(token: Long): Boolean = synchronized(lock) {
        accepting && dataAllowed && token == generation
    }
    fun publishData(token: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (!accepting || !dataAllowed || token != generation) return false
        action()
        true
    }
    fun allowData(token: Long): Boolean = synchronized(lock) {
        if (!accepting || token != generation) return false
        dataAllowed = true
        true
    }
    fun track(token: Long, job: Job): Boolean = synchronized(lock) {
        if (!accepting || !dataAllowed || token != generation) { job.cancel(); false } else { jobs += job; true }
    }
    fun untrack(job: Job) = synchronized(lock) { jobs -= job }

    /** Must run before credential replacement. Failed deletion leaves the boundary closed. */
    fun suspendOwner() = synchronized(lock) {
        accepting = false
        dataAllowed = false
        generation++
        jobs.toList().forEach { it.cancel(CancellationException("Account changed")) }
        jobs.clear()
        resets.forEach { it() }
    }

    fun invalidateCurrent() = synchronized(lock) {
        generation++
        jobs.toList().forEach { it.cancel(CancellationException("Account changed")) }
        jobs.clear()
        resets.forEach { it() }
    }

    fun activate(nonSecretAccount: String?, allowData: Boolean = true) = synchronized(lock) {
        val next = nonSecretAccount?.takeIf { it.isNotBlank() }?.let(::ownerDigest)
        if (!accepting || next != owner) {
            generation++
            jobs.toList().forEach { it.cancel(CancellationException("Account changed")) }
            jobs.clear()
            resets.forEach { it() }
        }
        owner = next
        accepting = true
        dataAllowed = allowData
    }

    private fun ownerDigest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

inline fun <T> catchingActive(action: () -> T): Result<T> = try {
    Result.success(action())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
