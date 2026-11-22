package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import com.muyao.wanzaihyperos.session.SecureStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ClientBoundaryCasTest {
    private val base = "https://synthetic.invalid"
    private val token = "synthetic_token_0123456789abcdef"
    private val page = """<html><form id="fm1" method="post" action="/cas/login"><input name="username" value=""><input name="password" value=""><input name="authType" value="0"><input name="lt" value=""><input name="execution" value="e1s1"><input name="_eventId" value="submit"><input name="randomStr" value=""></form></html>"""
    private fun reply(chain: Interceptor.Chain, code: Int, body: String, location: String? = null): Response =
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
            .body(body.toResponseBody("text/html".toMediaType())).apply { location?.let { header("Location", it) } }.build()
    private fun service(session: PortalSession, epoch: AccountEpoch, action: (Interceptor.Chain) -> Response): HzcuCasService {
        val client = HzcuClient.create(session, portalBaseUrl = base, epoch = epoch,
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 3, backoffBaseMs = 0,
            httpClient = OkHttpClient.Builder().addInterceptor(action).build())
        return HzcuCasService.create(client, session, epoch = epoch, pageUriBuilder = { "$base/cas/login" })
    }
    private fun success(chain: Interceptor.Chain) =
        if (chain.request().method == "GET") reply(chain, 200, page) else reply(chain, 302, "", "$base/portal?token=$token")
    private suspend fun failure(action: suspend () -> Unit): Throwable = try { action(); error("Expected failure") } catch (e: Throwable) { e }

    @Test fun `before login activates owner and owned success hook receives original token after save`() = runBlocking {
        val epoch = AccountEpoch(); val session = PortalSession(InMemorySecureStore()); val calls = AtomicInteger(); val hooks = AtomicInteger()
        val service = service(session, epoch) { chain -> calls.incrementAndGet(); success(chain) }
        service.beforeLogin = { fail("Owned hook must take precedence") }
        service.beforeLoginForOwner = { account -> assertEquals("synthetic-A", account); epoch.activate(account); epoch.token() }
        service.afterLogin = { fail("Owned hook must take precedence") }
        service.afterLoginForOwner = { account, owner ->
            assertEquals("synthetic-A", account); assertEquals(epoch.token(), owner); assertEquals(token, session.accessToken())
            assertTrue(epoch.publish(owner!!) { hooks.incrementAndGet() })
        }
        service.login(" synthetic-A ", "synthetic-password", "$base/portal")
        assertEquals(2, calls.get()); assertEquals(1, hooks.get())
    }

    @Test fun `owned before hook cannot adopt account switched before hook return`() = runBlocking {
        val epoch = AccountEpoch(); val session = PortalSession(InMemorySecureStore()); val calls = AtomicInteger()
        val service = service(session, epoch) { chain -> calls.incrementAndGet(); success(chain) }
        service.beforeLoginForOwner = { account ->
            epoch.activate(account); val old = epoch.token(); epoch.activate("synthetic-B"); old
        }
        assertTrue(failure { service.login("synthetic-A", "synthetic-password", "$base/portal") } is CancellationException)
        assertEquals(0, calls.get()); assertNull(session.accessToken())
    }

    @Test fun `late login cannot overwrite new credentials or call success hook`() = runBlocking {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }; val session = PortalSession(InMemorySecureStore()).apply { save("old-A", "synthetic-A", "{}") }
        val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1); val hooks = AtomicInteger(); val posts = AtomicInteger()
        val service = service(session, epoch) { chain ->
            if (chain.request().method == "POST") { posts.incrementAndGet(); entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
            success(chain)
        }
        service.afterLoginForOwner = { _, _ -> hooks.incrementAndGet() }
        val old = async { failure { service.login("synthetic-A", "synthetic-password", "$base/portal") } }
        entered.await(); epoch.activate("synthetic-B"); session.save("new-B", "synthetic-B", "{}"); release.countDown()
        assertTrue(old.await() is CancellationException); assertEquals("new-B", session.accessToken()); assertEquals("synthetic-B", session.userCode())
        assertEquals(0, hooks.get()); assertEquals(1, posts.get())
    }

    @Test fun `login save and owned success failures propagate storage without swallowed hooks`() = runBlocking {
        for (failSave in listOf(true, false)) {
            val delegate = InMemorySecureStore()
            val store = object : SecureStore {
                override fun read(key: String) = delegate.read(key)
                override fun write(key: String, value: String?) = delegate.write(key, value)
                override fun delete(key: String) = delegate.delete(key)
                override fun writeAtomically(changes: Map<String, String?>) {
                    if (failSave) throw HzcuException.Storage("writeAtomically", "SyntheticFailure")
                    delegate.writeAtomically(changes)
                }
            }
            val epoch = AccountEpoch().apply { activate("synthetic-A") }; val session = PortalSession(store); val hooks = AtomicInteger()
            val service = service(session, epoch, ::success)
            service.afterLoginForOwner = { _, _ -> hooks.incrementAndGet(); throw HzcuException.Storage("afterLogin", "SyntheticFailure") }
            val error = failure { service.login("synthetic-A", "synthetic-password", "$base/portal") }
            assertTrue(error is HzcuException.Storage); assertEquals(if (failSave) 0 else 1, hooks.get())
            assertEquals(if (failSave) null else token, session.accessToken())
        }
    }

    @Test fun `same owner renewal saves token without login hooks and late renewal cannot save`() = runBlocking {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }; val session = PortalSession(InMemorySecureStore()).apply { save("old-A", "synthetic-A", "{}") }
        val service = service(session, epoch) { chain -> reply(chain, 302, "", "$base/portal?token=$token") }
        service.beforeLogin = { fail("Renewal is not login") }; service.afterLoginForOwner = { _, _ -> fail("Renewal must not open login hook") }
        assertEquals(token, service.renewWithExistingSession("$base/portal", epoch.token())); assertEquals(token, session.accessToken())
        val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        val late = service(session, epoch) { chain -> entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS)); reply(chain, 302, "", "$base/portal?token=$token") }
        val old = async { failure { late.renewWithExistingSession("$base/portal", epoch.token()) } }
        entered.await(); epoch.activate("synthetic-B"); session.save("new-B", "synthetic-B", "{}"); release.countDown()
        assertTrue(old.await() is CancellationException); assertEquals("new-B", session.accessToken())
    }

    @Test fun `login cancellation before transport neither submits nor invokes hooks`() = runBlocking {
        val epoch = AccountEpoch(); val session = PortalSession(InMemorySecureStore()); val calls = AtomicInteger()
        val service = service(session, epoch) { chain -> calls.incrementAndGet(); success(chain) }
        service.beforeLogin = { throw CancellationException("synthetic-cancel") }
        service.afterLoginForOwner = { _, _ -> fail("Cancelled login") }
        assertTrue(failure { service.login("synthetic-A", "synthetic-password", "$base/portal") } is CancellationException)
        assertEquals(0, calls.get()); assertNull(session.accessToken())
    }
}
