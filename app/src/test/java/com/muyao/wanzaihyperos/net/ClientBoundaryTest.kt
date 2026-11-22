package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ClientBoundaryTest {
    private val base = "https://synthetic.invalid"
    private fun epoch() = AccountEpoch().apply { activate("synthetic-A") }
    private fun session() = PortalSession(InMemorySecureStore()).apply { save("old-token", "synthetic-A", "{}") }
    private fun reply(chain: Interceptor.Chain, code: Int = 200, body: String = "{\"code\":200,\"success\":true,\"data\":[]}") =
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    private fun client(session: PortalSession, epoch: AccountEpoch?, action: (Interceptor.Chain) -> Response) =
        HzcuClient.create(session, portalBaseUrl = base, epoch = epoch,
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 3, backoffBaseMs = 0,
            httpClient = OkHttpClient.Builder().addInterceptor(action).build()).apply { renewLog = {} }
    private suspend fun failure(action: suspend () -> Unit): Throwable = try { action(); error("Expected failure") } catch (e: Throwable) { e }

    @Test fun `late portal response neither dumps nor renews nor returns to new owner`() = runBlocking {
        val epoch = epoch(); val session = session(); val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        val dumps = AtomicInteger(); val renews = AtomicInteger()
        val client = client(session, epoch) { chain -> entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS)); reply(chain, 401) }
        client.debugDump = { _, _ -> dumps.incrementAndGet() }
        client.renewSessionForOwner = { renews.incrementAndGet(); true }
        val old = async { failure { client.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) } }
        entered.await(); epoch.activate("synthetic-B"); release.countDown()
        assertTrue(old.await() is CancellationException); assertEquals(0, dumps.get()); assertEquals(0, renews.get())
    }

    @Test fun `old CJH unauthorized cannot clear new session`() = runBlocking {
        val epoch = epoch(); val session = session(); val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        val clears = AtomicInteger()
        val transport = client(session, epoch) { chain -> entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS)); reply(chain, 200, "{\"code\":401,\"msg\":\"denied\"}") }
        val cjh = Cjh52Client(transport, { "synthetic-cjh-token" }, { clears.incrementAndGet() }, epoch)
        val old = async { failure { cjh.post("/dapi/v2/synthetic") } }
        entered.await(); epoch.activate("synthetic-B"); release.countDown()
        assertTrue(old.await() is CancellationException); assertEquals(0, clears.get())
    }

    @Test fun `same owner unauthorized callback runs once and exceptions redact before cut`() = runBlocking {
        val epoch = epoch(); val session = session(); val clears = AtomicInteger()
        val transport = client(session, epoch) { chain -> reply(chain, 200, "{\"code\":401,\"msg\":\"password=synthetic-secret " + "x".repeat(300) + "\"}") }
        val cjh = Cjh52Client(transport, { "synthetic-token" }, { clears.incrementAndGet() }, epoch)
        val error = failure { cjh.post("/dapi/v2/synthetic") }
        assertTrue(error is HzcuException.LoginExpired); assertEquals(1, clears.get())
        assertFalse(error.message.orEmpty().contains("synthetic-secret")); assertTrue(error.message.orEmpty().length <= 160)
    }

    @Test fun `current CJH expiry remains visible while replacing credentials still invalidates`() = runBlocking {
        val monitor = Any()
        val epoch = AccountEpoch(monitor).apply { activate("synthetic-A") }
        val source = com.muyao.wanzaihyperos.session.Cjh52Session(InMemorySecureStore()).apply {
            save("synthetic-cjh", 1L); markActivated()
        }
        var boundaryWrite = false
        val observer = com.muyao.wanzaihyperos.app.SessionChangeObserver(monitor,
            { "${source.state}:${source.replacementRevision}" }) {
            if (!boundaryWrite) epoch.invalidateCurrent()
        }
        source.addListener(observer::observe)
        val original = epoch.token()
        val transport = client(session(), epoch) { chain -> reply(chain, 401) }
        val cjh = Cjh52Client(transport, { source.token() }, {
            synchronized(monitor) {
                boundaryWrite = true
                try { source.clear() } finally { boundaryWrite = false }
            }
        }, epoch)
        val error = failure { cjh.post("/dapi/v2/synthetic") }
        assertTrue(error is HzcuException.LoginExpired)
        assertNull(source.token())
        assertEquals(original, epoch.token())
        observer.observe()
        assertEquals(original, epoch.token())
        source.save("synthetic-replacement", 2L)
        assertTrue(epoch.token() > original)
    }

    @Test fun `same owner unauthorized callback storage failure propagates`() = runBlocking {
        val epoch = epoch()
        val transport = client(session(), epoch) { chain -> reply(chain, 401) }
        val cjh = Cjh52Client(transport, { "synthetic-token" }, { throw HzcuException.Storage("clear", "SyntheticFailure") }, epoch)
        assertTrue(failure { cjh.post("/dapi/v2/synthetic") } is HzcuException.Storage)
    }

    @Test fun `HTTP coroutine cancellation cancels underlying call without replay`() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1); val observed = CompletableDeferred<Boolean>(); val calls = AtomicInteger()
        val client = client(session(), epoch()) { chain ->
            calls.incrementAndGet(); entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS))
            observed.complete(chain.call().isCanceled()); reply(chain)
        }
        val request = async { client.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) }
        entered.await(); request.cancel(); request.join(); release.countDown()
        assertTrue(observed.await()); assertEquals(1, calls.get())
    }

    @Test fun `bound cookie jar blocks stale and untagged writes while preserving host isolation`() {
        val epoch = epoch(); val jar = HzcuCookieJar(); jar.bindEpoch(epoch)
        val a = jar.forRequest(epoch, epoch.token()); val host = "$base/cas".toHttpUrl()
        fun cookie(value: String) = Cookie.Builder().name("CASTGC").value(value).hostOnlyDomain(host.host).build()
        a.saveFromResponse(host, listOf(cookie("synthetic-A")))
        assertEquals(1, a.loadForRequest(host).size); assertTrue(a.loadForRequest("https://other.invalid/".toHttpUrl()).isEmpty())
        epoch.activate("synthetic-B"); val b = jar.forRequest(epoch, epoch.token()); b.saveFromResponse(host, listOf(cookie("synthetic-B")))
        val stale = runCatching { a.saveFromResponse(host, listOf(cookie("late-A"))) }.exceptionOrNull()
        assertTrue(stale is CancellationException)
        jar.saveFromResponse(host, listOf(cookie("untagged")))
        assertEquals("synthetic-B", jar.valueOf(host.host, "CASTGC"))
        assertTrue(runCatching { a.loadForRequest(host) }.exceptionOrNull() is CancellationException)
    }

    @Test fun `portal writes and unknown POST never renew or retry IO or status failures`() = runBlocking {
        for (path in listOf(HzcuEndpoints.PORTAL_MESSAGE_READ_PATH + "/7", HzcuEndpoints.PORTAL_MESSAGE_READ_ALL_PATH + "/g", "/unknown")) {
            for (io in listOf(false, true)) {
                val calls = AtomicInteger(); val renews = AtomicInteger()
                val client = client(session(), epoch()) { chain -> calls.incrementAndGet(); if (io) throw IOException("synthetic"); reply(chain, 401) }
                client.renewSessionForOwner = { renews.incrementAndGet(); true }
                failure { client.portalPost(path) }
                assertEquals(1, calls.get()); assertEquals(0, renews.get())
            }
        }
    }

    @Test fun `allowlisted reads renew once but forbidden and maintenance never do`() = runBlocking {
        val calls = AtomicInteger(); val renews = AtomicInteger(); val session = session(); val epoch = epoch()
        val client = client(session, epoch) { chain -> if (calls.incrementAndGet() == 1) reply(chain, 401) else reply(chain) }
        client.renewSessionForOwner = { token -> assertEquals(epoch.token(), token); renews.incrementAndGet(); session.save("new-token", "synthetic-A", "{}"); true }
        client.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH)
        assertEquals(2, calls.get()); assertEquals(1, renews.get())
        for (status in listOf(403, 503, 200)) {
            val forbidden = client(session(), epoch()) { chain -> reply(chain, status, "<html>统一身份认证维护中</html>") }
            forbidden.renewSessionForOwner = { fail("Not authentication evidence"); false }
            failure { forbidden.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH, maxAttempts = 1) }
        }
    }

    @Test fun `missing local token does not renew or send even for allowlisted read`() = runBlocking {
        val session = PortalSession(InMemorySecureStore()); val calls = AtomicInteger()
        val client = client(session, epoch()) { chain -> calls.incrementAndGet(); reply(chain) }
        client.renewSessionForOwner = { fail("No authentication response exists"); false }
        assertTrue(failure { client.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) } is HzcuException.LoginExpired)
        assertEquals(0, calls.get())
    }

    @Test fun `renewal cancellation propagates without replay`() = runBlocking {
        val calls = AtomicInteger(); val client = client(session(), epoch()) { chain -> calls.incrementAndGet(); reply(chain, 401) }
        client.renewSessionForOwner = { throw CancellationException("synthetic-cancel") }
        assertTrue(failure { client.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) } is CancellationException)
        assertEquals(1, calls.get())
    }

    @Test fun `renewal storage failure is not disguised as authentication failure`() = runBlocking {
        val client = client(session(), epoch()) { chain -> reply(chain, 401) }
        client.renewSessionForOwner = { throw HzcuException.Storage("renew", "SyntheticFailure") }
        assertTrue(failure { client.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) } is HzcuException.Storage)
    }

    @Test fun `concurrent same owner readers share one renewal and each replay once`() = runBlocking {
        val epoch = epoch(); val session = session(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val rejected = CompletableDeferred<Unit>(); val calls = AtomicInteger(); val renews = AtomicInteger()
        val client = client(session, epoch) { chain ->
            if (calls.incrementAndGet() == 2) rejected.complete(Unit)
            if (chain.request().header("token") == "old-token") reply(chain, 401) else reply(chain)
        }
        client.renewSessionForOwner = { renews.incrementAndGet(); entered.complete(Unit); release.await(); session.save("new-token", "synthetic-A", "{}"); true }
        val first = async { client.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) }; entered.await()
        val second = async { client.portalPost(HzcuEndpoints.PORTAL_MESSAGE_LIST_PATH) }; rejected.await()
        release.complete(Unit); first.await(); second.await()
        assertEquals(1, renews.get()); assertEquals(4, calls.get())
    }

    @Test fun `concurrent same owner renewal is shared and follower cancellation is independent`() = runBlocking {
        supervisorScope {
            val epoch = epoch(); val session = session(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger(); val renews = AtomicInteger(); val rejected = CompletableDeferred<Unit>()
            val client = client(session, epoch) { chain -> if (calls.incrementAndGet() == 2) rejected.complete(Unit); if (chain.request().header("token") == "old-token") reply(chain, 401) else reply(chain) }
            client.renewSessionForOwner = { renews.incrementAndGet(); entered.complete(Unit); release.await(); session.save("new-token", "synthetic-A", "{}"); true }
            val first = async { client.portalPost(HzcuEndpoints.PORTAL_NEWS_LIST_PATH) }; entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { client.portalPost(HzcuEndpoints.PORTAL_MESSAGE_LIST_PATH) }
            rejected.await()
            second.cancel(); second.join(); release.complete(Unit); first.await()
            assertEquals(1, renews.get()); assertEquals("new-token", session.accessToken())
        }
    }
}
