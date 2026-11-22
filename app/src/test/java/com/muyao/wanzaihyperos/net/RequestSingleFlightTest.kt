package com.muyao.wanzaihyperos.net

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RequestSingleFlightTest {
    @Test fun identicalRequestsShareOneResultButRefreshAndOwnersDoNot() = runBlocking {
        val flight = RequestSingleFlight<RequestKey, Int>()
        val id = RequestIdentity("synthetic-owner", 1)
        val key = RequestKey(id, "GET", "/api/todos", false)
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val leader = async(start = CoroutineStart.UNDISPATCHED) { flight.run(key) { calls++; release.await(); 7 } }
        val follower = async(start = CoroutineStart.UNDISPATCHED) { flight.run(key) { error("must not execute") } }
        assertEquals(8, flight.run(key.copy(forceRefresh = true)) { calls++; 8 })
        assertEquals(9, flight.run(key.copy(identity = RequestIdentity(id.ownerKey, 2))) { calls++; 9 })
        release.complete(Unit)
        assertEquals(7, leader.await())
        assertEquals(7, follower.await())
        assertEquals(3, calls)
    }

    @Test fun failureIsSharedAndFollowingCallCanRecover() = runBlocking {
        val flight = RequestSingleFlight<String, String>()
        val release = CompletableDeferred<Unit>()
        val failure = IOException("synthetic offline failure")
        val leader = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { flight.run("same") { release.await(); throw failure } }.exceptionOrNull()
        }
        val follower = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { flight.run("same") { error("must not execute") } }.exceptionOrNull()
        }
        release.complete(Unit)
        assertSame(failure, leader.await())
        // Coroutine stacktrace recovery may copy the suspended follower's exception.
        val followerFailure = follower.await()
        assertTrue(generateSequence(followerFailure) { it.cause }.take(16).any { it === failure })
        assertEquals("recovered", flight.run("same") { "recovered" })
    }

    @Test fun cancellingFollowerDoesNotCancelLeader() = runBlocking {
        val flight = RequestSingleFlight<String, String>()
        val release = CompletableDeferred<Unit>()
        val leader = async(start = CoroutineStart.UNDISPATCHED) { flight.run("same") { release.await(); "ok" } }
        val follower = async(start = CoroutineStart.UNDISPATCHED) { flight.run("same") { error("must not execute") } }
        follower.cancelAndJoin()
        release.complete(Unit)
        assertEquals("ok", leader.await())
        assertEquals("next", flight.run("same") { "next" })
    }
}
