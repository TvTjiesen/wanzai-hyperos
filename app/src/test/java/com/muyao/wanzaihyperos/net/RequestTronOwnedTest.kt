package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Application interceptors answer every request without opening a socket. */
class RequestTronOwnedTest {
    private fun reply(chain: Interceptor.Chain, code: Int, body: String, sid: String? = null): Response =
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
            .body(body.toResponseBody("application/json".toMediaType()))
            .apply { sid?.let { header("X-SESSION-ID", it) } }.build()

    private fun tron(
        epoch: AccountEpoch,
        http: OkHttpClient,
        publish: ((String, RequestIdentity?) -> Unit)? = null,
        refresh: (suspend (RequestIdentity?) -> String?)? = null,
    ) = TronclassClient(http, { "synthetic-account" }, { "synthetic-access" },
        onSessionId = { fail("Owned publication must take precedence") },
        refreshAccessToken = { fail("Owned refresh must take precedence"); null },
        limiter = HzcuRateLimiter.forTesting(0, 0), identityProvider = epoch::identity,
        onSessionIdForOwner = publish, refreshAccessTokenForOwner = refresh)

    private suspend fun failure(action: suspend () -> Unit): Throwable = try {
        action(); error("Expected failure")
    } catch (error: Throwable) { error }

    @Test fun `old 401 renewal carries original identity and cannot write or replay after switch`() = runBlocking {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }
        val original = epoch.identity()
        val calls = AtomicInteger()
        val writes = AtomicInteger()
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            calls.incrementAndGet(); reply(chain, 401, "{}")
        }.build()
        val client = tron(epoch, http, refresh = { identity ->
            assertEquals(original, identity)
            epoch.activate("synthetic-B")
            if (!epoch.publishData(identity!!.generation) { writes.incrementAndGet() })
                throw CancellationException("Account changed")
            "synthetic-fresh-access"
        })
        assertTrue(failure { client.sessionId() } is CancellationException)
        assertEquals(1, calls.get())
        assertEquals(0, writes.get())
    }

    @Test fun `late SID callback receives original identity and known session never leaks to next owner`() = runBlocking {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }
        val original = epoch.identity()
        val calls = AtomicInteger()
        val writes = AtomicInteger()
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val n = calls.incrementAndGet()
            reply(chain, 200, """{"user_id":123}""", "synthetic-sid-$n")
        }.build()
        val client = tron(epoch, http, publish = { _, identity ->
            if (calls.get() == 1) {
                assertEquals(original, identity)
                epoch.activate("synthetic-B")
            }
            if (!epoch.publishData(identity!!.generation) { writes.incrementAndGet() })
                throw CancellationException("Account changed")
        })
        assertTrue(failure { client.sessionId() } is CancellationException)
        assertEquals(0, writes.get())
        assertEquals("synthetic-sid-2", client.sessionId())
        assertEquals("synthetic-sid-2", client.sessionId())
        assertEquals(2, calls.get())
        epoch.activate("synthetic-C")
        assertEquals("synthetic-sid-3", client.sessionId())
        assertEquals(3, calls.get())
        assertEquals(2, writes.get())
    }

    @Test fun `owned renewal and SID persistence storage failures propagate without replay or caching`() = runBlocking {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }
        val calls = AtomicInteger()
        val renewalError = HzcuException.Storage("renew", "CommitFailed")
        val rejectedHttp = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            calls.incrementAndGet(); reply(chain, 401, "{}")
        }.build()
        val renewalClient = tron(epoch, rejectedHttp, refresh = { throw renewalError })
        assertSame(renewalError, failure { renewalClient.sessionId() })
        assertEquals(1, calls.get())
        val saveError = HzcuException.Storage("save-sid", "CommitFailed")
        val successHttp = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            calls.incrementAndGet(); reply(chain, 200, """{"user_id":123}""", "synthetic-sid")
        }.build()
        val saveClient = tron(epoch, successHttp, publish = { _, _ -> throw saveError })
        repeat(2) { assertSame(saveError, failure { saveClient.sessionId() }) }
        assertEquals(3, calls.get())
    }

    @Test fun `OIDC refresh refuses supplied old owner before HTTP instead of recapturing new owner`() = runBlocking {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }
        val original = epoch.token()
        val calls = AtomicInteger()
        val session = PortalSession(InMemorySecureStore())
        val client = HzcuClient.create(session, epoch = epoch, rateLimiter = HzcuRateLimiter.forTesting(0, 0),
            httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                calls.incrementAndGet()
                reply(chain, 200, """{"access_token":"synthetic-new-access"}""")
            }.build())
        val service = HzcuCasService.create(client, session, epoch = epoch)
        epoch.activate("synthetic-B")
        assertTrue(failure { service.refreshTronclassTokens("synthetic-refresh", ownerToken = original) } is CancellationException)
        assertEquals(0, calls.get())
    }
}
