package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** All HTTP is answered by an application interceptor; no socket, school or credential access. */
class RequestClientPolicyTest {
    @get:Rule val temp = TemporaryFolder()
    private data class Reply(val status: Int = 200, val body: String = """{"todo_list":[]}""", val sid: String? = null)
    private class FakeHttp(var answer: (Request) -> Reply) {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            counts.computeIfAbsent(request.url.encodedPath) { AtomicInteger() }.incrementAndGet()
            val reply = answer(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(reply.status)
                .message("synthetic response")
                .body(reply.body.toResponseBody("application/json".toMediaType()))
                .apply { reply.sid?.let { header("X-SESSION-ID", it) } }.build()
        }.build()
        fun calls(path: String) = counts[path]?.get() ?: 0
    }
    private fun limiter() = HzcuRateLimiter.forTesting(minIntervalMs = 0, perPathMinIntervalMs = 0)
    private fun client(
        fake: FakeHttp,
        identity: AtomicReference<RequestIdentity?> = AtomicReference(RequestIdentity("synthetic-owner", 1)),
        sid: () -> String? = { "synthetic-old-sid" },
        published: (String) -> Unit = {},
        refresh: suspend () -> String? = { null },
    ) = TronclassClient(fake.client, { "synthetic-account" }, { "synthetic-access" },
        onSessionId = published, cachedSessionId = sid, limiter = limiter(),
        refreshAccessToken = refresh, identityProvider = { identity.get() })

    @Test fun identicalReadsAndSessionExchangeAreSingleFlight() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fake = FakeHttp { request ->
            if (request.url.encodedPath == "/api/login") {
                entered.complete(Unit)
                runBlocking { release.await() }
                Reply(body = """{"user_id":123}""", sid = "synthetic-new-sid")
            } else Reply()
        }
        val tron = client(fake, sid = { null })
        val first = async(start = CoroutineStart.UNDISPATCHED) { tron.todos() }
        entered.await()
        val others = List(7) { async(start = CoroutineStart.UNDISPATCHED) { tron.todos() } }
        val sessionFollower = async(start = CoroutineStart.UNDISPATCHED) { tron.sessionId() }
        release.complete(Unit)
        assertTrue(first.await().isEmpty())
        others.forEach { assertTrue(it.await().isEmpty()) }
        assertEquals("synthetic-new-sid", sessionFollower.await())
        assertEquals(1, fake.calls("/api/login"))
        assertEquals(1, fake.calls("/api/todos"))
    }

    @Test fun contractFailuresNeverCacheAndNextCallCanRecover() = runBlocking {
        val fake = FakeHttp { Reply(body = "{}") }
        val tron = client(fake).apply { cache = ResponseCache(temp.root) }
        listOf("{}", "not-json", "<html>maintenance</html>", """{"todo_list":[],"success":false}""").forEach { body ->
            fake.answer = { Reply(body = body) }
            assertTrue(runCatching { tron.todos() }.exceptionOrNull() is HzcuException.Parse)
            assertEquals(0, tron.cache!!.entries())
        }
        fake.answer = { Reply() }
        assertTrue(tron.todos().isEmpty())
        assertTrue(tron.todos().isEmpty())
        assertEquals(5, fake.calls("/api/todos"))
        assertEquals(1, tron.cache!!.entries())
    }

    @Test fun explicitRefreshOnlyBypassesItsOwnRead() = runBlocking {
        val fake = FakeHttp { request ->
            if (request.url.encodedPath == "/api/my-courses") Reply(body = """{"courses":[]}""") else Reply()
        }
        val tron = client(fake).apply { cache = ResponseCache(temp.root) }
        tron.todos(); tron.myCourses()
        tron.todos(forceRefresh = true)
        tron.myCourses(); tron.todos()
        assertEquals(2, fake.calls("/api/todos"))
        assertEquals(1, fake.calls("/api/my-courses"))
        assertEquals(0, fake.calls("/api/login"))
    }

    @Test fun forbiddenAndMaintenanceReadsNeverRenewOrCache() = runBlocking {
        val refreshes = AtomicInteger()
        val fake = FakeHttp { Reply(403, """{"error_code":"session_expired"}""") }
        val tron = client(fake, refresh = { refreshes.incrementAndGet(); "synthetic-refreshed" })
            .apply { cache = ResponseCache(temp.root) }
        for (status in listOf(403, 503)) {
            fake.answer = { Reply(status, "<html>authserver 统一身份认证维护</html>") }
            assertTrue(runCatching { tron.todos() }.exceptionOrNull() is HzcuException.HttpStatus)
        }
        fake.answer = { Reply(200, "<html>authserver 统一身份认证维护</html>") }
        assertTrue(runCatching { tron.todos() }.exceptionOrNull() is HzcuException.Parse)
        assertEquals(0, refreshes.get())
        assertEquals(0, fake.calls("/api/login"))
        assertEquals(0, tron.cache!!.entries())
    }

    @Test fun read401RenewsSessionExactlyOnceThenRetriesReadOnce() = runBlocking {
        val fake = FakeHttp { request ->
            when {
                request.url.encodedPath == "/api/login" -> Reply(body = """{"user_id":123}""", sid = "synthetic-new-sid")
                request.header("X-SESSION-ID") == "synthetic-old-sid" -> Reply(401, "{}")
                else -> Reply()
            }
        }
        val tron = client(fake)
        assertTrue(tron.todos().isEmpty())
        assertEquals(2, fake.calls("/api/todos"))
        assertEquals(1, fake.calls("/api/login"))
        fake.answer = { request ->
            if (request.url.encodedPath == "/api/login") Reply(body = """{"user_id":123}""", sid = "synthetic-again-sid")
            else Reply(401, "{}")
        }
        assertTrue(runCatching { tron.todos() }.exceptionOrNull() is HzcuException.LoginExpired)
        assertEquals(4, fake.calls("/api/todos"))
        assertEquals(2, fake.calls("/api/login"))
    }

    @Test fun exchangeOnlyRefreshesAccessForExplicitAuthenticationFailure() = runBlocking {
        val refreshes = AtomicInteger()
        val fake = FakeHttp { Reply(403, "{}") }
        val tron = client(fake, sid = { null }, refresh = { refreshes.incrementAndGet(); "synthetic-fresh" })
        assertTrue(runCatching { tron.sessionId() }.exceptionOrNull() is HzcuException.HttpStatus)
        assertEquals(0, refreshes.get())
        fake.answer = { Reply(body = "{}", sid = "synthetic-sid") }
        assertTrue(runCatching { tron.sessionId() }.exceptionOrNull() is HzcuException.Parse)
        assertEquals(0, refreshes.get())
        var attempts = 0
        fake.answer = {
            if (++attempts == 1) Reply(401, "{}")
            else Reply(body = """{"user_id":123}""", sid = "synthetic-fresh-sid")
        }
        assertEquals("synthetic-fresh-sid", tron.sessionId())
        assertEquals(1, refreshes.get())
        assertEquals(2, attempts)
    }

    @Test fun accountSwitchRejectsLateReadAndNeverCachesOldOwner() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fake = FakeHttp {
            entered.complete(Unit)
            runBlocking { release.await() }
            Reply()
        }
        val old = RequestIdentity("synthetic-a", 1)
        val identity = AtomicReference<RequestIdentity?>(old)
        val tron = client(fake, identity).apply { cache = ResponseCache(temp.root) }
        val oldFlight = async(start = CoroutineStart.UNDISPATCHED) { runCatching { tron.todos() }.exceptionOrNull() }
        entered.await()
        identity.set(RequestIdentity("synthetic-b", 2))
        release.complete(Unit)
        assertTrue(oldFlight.await() is HzcuException.Parse)
        assertEquals(0, tron.cache!!.entries())
        assertNull(tron.cache!!.get("tronclass", old, "/api/todos", 30_000))
        assertTrue(tron.todos().isEmpty())
        assertEquals(2, fake.calls("/api/todos"))
    }

    @Test fun accountSwitchRejectsLateSessionPublication() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fake = FakeHttp {
            entered.complete(Unit)
            runBlocking { release.await() }
            Reply(body = """{"user_id":123}""", sid = "synthetic-old-owner-sid")
        }
        val identity = AtomicReference<RequestIdentity?>(RequestIdentity("synthetic-a", 1))
        val published = AtomicInteger()
        val tron = client(fake, identity, sid = { null }, published = { published.incrementAndGet() })
        val oldFlight = async(start = CoroutineStart.UNDISPATCHED) { runCatching { tron.sessionId() }.exceptionOrNull() }
        entered.await(); identity.set(RequestIdentity("synthetic-b", 2)); release.complete(Unit)
        assertTrue(oldFlight.await() is HzcuException.Parse)
        assertEquals(0, published.get())
    }

    @Test fun cacheClearDuringReadDoesNotRepopulateFromEarlierFlight() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fake = FakeHttp { entered.complete(Unit); runBlocking { release.await() }; Reply() }
        val tron = client(fake).apply { cache = ResponseCache(temp.root) }
        val read = async(start = CoroutineStart.UNDISPATCHED) { tron.todos() }
        entered.await(); tron.cache!!.clearAll(); release.complete(Unit)
        assertTrue(read.await().isEmpty())
        assertEquals(0, tron.cache!!.entries())
        tron.todos()
        assertEquals(2, fake.calls("/api/todos"))
        assertEquals(1, tron.cache!!.entries())
    }

    @Test fun nullIdentityDisablesDiskCacheAndWriteFailuresAreNeverRetried() = runBlocking {
        val fake = FakeHttp { Reply() }
        val tron = client(fake, AtomicReference<RequestIdentity?>(null)).apply { cache = ResponseCache(temp.root) }
        tron.todos(); tron.todos()
        assertEquals(2, fake.calls("/api/todos"))
        assertEquals(0, tron.cache!!.entries())
        fake.answer = { Reply(401, "{}") }
        assertTrue(runCatching { tron.answerRollcall(9, RollcallAnswerKind.NUMBER, "synthetic-code") }.exceptionOrNull() is HzcuException.LoginExpired)
        assertEquals(1, fake.calls("/api/rollcall/9/answer_number_rollcall"))
        assertTrue(runCatching { tron.submitHomework(8, "synthetic-comment", false) }.exceptionOrNull() is HzcuException.LoginExpired)
        assertEquals(1, fake.calls("/api/course/activities/8/submissions"))
        fake.answer = { throw IOException("synthetic offline") }
        assertTrue(runCatching { tron.submitHomework(8, "synthetic-comment", false) }.exceptionOrNull() is IOException)
        assertEquals(2, fake.calls("/api/course/activities/8/submissions"))
        assertEquals(0, fake.calls("/api/login"))
    }
}
