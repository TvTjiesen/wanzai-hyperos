package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.tronclass.LiveRollcallRepository
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.TronclassClient
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AccountIsolationTronTest {
    @Test fun profileMemoIsResetAndNeverReusedAcrossOwners() = runBlocking {
        val owner = AccountEpoch().apply { activate("synthetic-a") }
        val profiles = AtomicInteger()
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val body = if (chain.request().url.encodedPath == "/api/profile") {
                "{\"id\":${profiles.incrementAndGet()}}"
            } else "{\"rollcalls\":[]}"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val client = TronclassClient(http, { "synthetic-user" }, { "synthetic-access" },
            cachedSessionId = { "synthetic-sid" }, limiter = HzcuRateLimiter.forTesting(0, 0),
            identityProvider = owner::identity)
        val repo = LiveRollcallRepository(client, epoch = owner)
        repo.refresh(); repo.refresh()
        assertEquals(1, profiles.get())
        owner.suspendOwner(); owner.activate("synthetic-b")
        assertTrue(repo.state.value is ListState.FirstLoad)
        repo.refresh()
        assertEquals(2, profiles.get())
    }

    @Test fun lateProfileCannotRefillMemoOrStateAfterSwitch() = runBlocking {
        val owner = AccountEpoch().apply { activate("synthetic-a") }
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val profiles = AtomicInteger()
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val body = if (chain.request().url.encodedPath == "/api/profile") {
                val number = profiles.incrementAndGet()
                if (number == 1) {
                    entered.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                }
                "{\"id\":$number}"
            } else "{\"rollcalls\":[]}"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val client = TronclassClient(http, { "synthetic-user" }, { "synthetic-access" },
            cachedSessionId = { "synthetic-sid" }, limiter = HzcuRateLimiter.forTesting(0, 0))
        val repo = LiveRollcallRepository(client, epoch = owner)
        try {
            val old = launch { repo.refresh() }
            entered.await()
            owner.suspendOwner(); owner.activate("synthetic-b")
            release.countDown(); old.join()
            assertTrue(repo.state.value is ListState.FirstLoad)
            repo.refresh()
            assertEquals(2, profiles.get())
        } finally { release.countDown() }
    }
}
