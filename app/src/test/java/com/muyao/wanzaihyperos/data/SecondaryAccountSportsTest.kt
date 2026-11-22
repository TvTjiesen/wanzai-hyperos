package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.szty.LiveSztyRepository
import com.muyao.wanzaihyperos.net.SztyClient
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.SztySession
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SecondaryAccountSportsTest {
    @Test fun secondaryReplacementRejectsLateStateSyncAndSnapshotWithoutMovingPrimaryEpoch() = runBlocking {
        val dir = Files.createTempDirectory("synthetic-secondary-sports").toFile()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val owner = AccountEpoch().apply { activate("synthetic-primary") }
        val ownerToken = owner.token()
        val session = SztySession(InMemorySecureStore()).apply { save("synthetic-old", 7, 1_000L) }
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            calls.incrementAndGet()
            val data = if (chain.request().url.encodedPath.endsWith("getMyGrade")) {
                entered.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                """[{"year":2026,"scoreData":[{"name":"synthetic","data":"1"}]}]"""
            } else "null"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"statusCode\":1,\"obj\":$data}".toResponseBody()).build()
        }.build()
        try {
            val repo = LiveSztyRepository(
                SztyClient("https://synthetic.invalid", httpClient = http, sleeper = {}, clock = { 10_000L }),
                session, clock = { 10_000L }, snapshotDir = dir, epoch = owner,
            )
            val request = launch { repo.refresh() }
            entered.await()
            session.save("synthetic-new", 8, 2_000L)
            release.countDown(); request.join()
            assertEquals(ownerToken, owner.token())
            assertTrue(repo.state.value is ListState.FirstLoad)
            assertEquals(2_000L, session.lastSyncMillis())
            assertFalse(dir.resolve("szty_snapshot.json").exists())
            assertEquals("The old credential chain must not issue another read", 1, calls.get())
            assertTrue(request.isCancelled)
        } finally { release.countDown(); dir.deleteRecursively() }
    }

    @Test fun replacementOrClearStopsAfterEveryStartedRead() = runBlocking {
        for (clear in listOf(false, true)) {
            for (boundary in 1..6) {
                val dir = Files.createTempDirectory("synthetic-sports-boundary").toFile()
                val entered = CompletableDeferred<Unit>()
                val release = CountDownLatch(1)
                val calls = AtomicInteger()
                val owner = AccountEpoch().apply { activate("synthetic-primary") }
                val ownerToken = owner.token()
                val session = SztySession(InMemorySecureStore()).apply { save("synthetic-old", 7, 1_000L) }
                val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
                    if (calls.incrementAndGet() == boundary) {
                        entered.complete(Unit)
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    val data = when {
                        chain.request().url.encodedPath.endsWith("getMyGrade") ->
                            """[{"year":2026,"scoreData":[]}]"""
                        chain.request().url.encodedPath.endsWith("getAssessmentYears") -> """[{"termId":15}]"""
                        chain.request().url.encodedPath.endsWith("semesterStuEduClass") -> """[{"classId":7}]"""
                        chain.request().url.encodedPath.endsWith("stuAttendanceList") -> "[]"
                        else -> "null"
                    }
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{\"statusCode\":1,\"obj\":$data}".toResponseBody()).build()
                }.build()
                try {
                    val repo = LiveSztyRepository(
                        SztyClient("https://synthetic.invalid", httpClient = http, sleeper = {}, clock = { 10_000L }),
                        session, clock = { 10_000L }, snapshotDir = dir, epoch = owner,
                    )
                    val request = launch { repo.refresh() }
                    entered.await()
                    if (clear) session.clear() else session.save("synthetic-new", 8, 2_000L)
                    assertTrue(repo.state.value is ListState.FirstLoad)
                    release.countDown(); request.join()
                    assertEquals("clear=$clear boundary=$boundary", boundary, calls.get())
                    assertTrue(request.isCancelled)
                    assertEquals(ownerToken, owner.token())
                    assertTrue(repo.state.value is ListState.FirstLoad)
                    if (clear) assertNull(session.lastSyncMillis()) else assertEquals(2_000L, session.lastSyncMillis())
                    assertFalse(dir.resolve("szty_snapshot.json").exists())
                } finally { release.countDown(); dir.deleteRecursively() }
            }
        }
    }

    @Test fun replacingOrClearingDuringThrottlePreventsNetworkDispatch() = runBlocking {
        for (clear in listOf(false, true)) {
            val dir = Files.createTempDirectory("synthetic-sports-throttle").toFile()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val owner = AccountEpoch().apply { activate("synthetic-primary") }
            val ownerToken = owner.token()
            val session = SztySession(InMemorySecureStore()).apply { save("synthetic-old", 7, 1_000L) }
            val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
                calls.incrementAndGet()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"statusCode\":1,\"obj\":null}".toResponseBody()).build()
            }.build()
            val client = SztyClient("https://synthetic.invalid", httpClient = http, clock = { 10_000L }, sleeper = {
                entered.complete(Unit)
                release.await()
            })
            try {
                client.fitness("synthetic-prime", 1)
                assertEquals(1, calls.get())
                val repo = LiveSztyRepository(client, session, snapshotDir = dir, epoch = owner)
                val request = launch { repo.refresh() }
                entered.await()
                if (clear) session.clear() else session.save("synthetic-new", 8, 2_000L)
                release.complete(Unit); request.join()
                assertEquals("No request may dispatch after a changed throttle ticket", 1, calls.get())
                assertTrue(request.isCancelled)
                assertEquals(ownerToken, owner.token())
                assertTrue(repo.state.value is ListState.FirstLoad)
                if (clear) assertNull(session.lastSyncMillis()) else assertEquals(2_000L, session.lastSyncMillis())
                assertFalse(dir.resolve("szty_snapshot.json").exists())
            } finally { release.complete(Unit); dir.deleteRecursively() }
        }
    }

    @Test fun cancellingFirstLoadLeavesUnknownInsteadOfFabricatingFailure() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val session = SztySession(InMemorySecureStore()).apply { save("synthetic", 7, 1_000L) }
        val client = SztyClient("https://synthetic.invalid", sleeper = { entered.complete(Unit); hold.await() })
        val repo = LiveSztyRepository(client, session)
        val request = launch { repo.refresh() }
        entered.await()
        request.cancelAndJoin()
        assertTrue(repo.state.value is ListState.FirstLoad)
        assertEquals(1_000L, session.lastSyncMillis())
    }

    @Test fun cancellingPageRefreshClearsRefreshingWithoutInventingFailure() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        var pause = false
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val data = if (chain.request().url.encodedPath.endsWith("getMyGrade"))
                """[{"year":2026,"scoreData":[]}]""" else "null"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"statusCode\":1,\"obj\":$data}".toResponseBody()).build()
        }.build()
        val session = SztySession(InMemorySecureStore()).apply { save("synthetic", 7, 1_000L) }
        val client = SztyClient("https://synthetic.invalid", httpClient = http, clock = { 10_000L }, sleeper = {
            if (pause) { entered.complete(Unit); hold.await() }
        })
        val repo = LiveSztyRepository(client, session)
        repo.refresh()
        val before = repo.state.value
        pause = true
        val request = launch { repo.refresh() }
        entered.await()
        assertTrue((repo.state.value as ListState.Content).meta.refreshing)
        val queued = launch(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        queued.cancelAndJoin()
        assertTrue((repo.state.value as ListState.Content).meta.refreshing)
        request.cancelAndJoin()
        assertEquals(before, repo.state.value)
        assertEquals(1_000L, session.savedAtMillis())
    }
}
