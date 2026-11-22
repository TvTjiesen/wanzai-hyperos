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

class AccountIsolationSportsTest {
    @Test fun lateSportsResultCannotPublishMarkSyncOrSnapshotForNewOwner() = runBlocking {
        val dir = Files.createTempDirectory("synthetic-sports-owner").toFile()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val owner = AccountEpoch().apply { activate("synthetic-a") }
        val session = SztySession(InMemorySecureStore()).apply { save("synthetic-token", 7, 1_000L) }
        val synced = session.lastSyncMillis()
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val data = if (chain.request().url.encodedPath.endsWith("getMyGrade")) {
                entered.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                """[{"year":2026,"scoreData":[{"name":"synthetic","data":"1"}]}]"""
            } else "null"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"statusCode\":1,\"obj\":$data}".toResponseBody()).build()
        }.build()
        try {
            val client = SztyClient("https://synthetic.invalid", httpClient = http, sleeper = {}, clock = { 10_000L })
            val repo = LiveSztyRepository(client, session, snapshotDir = dir, epoch = owner)
            val request = launch { repo.refresh() }
            entered.await()
            owner.suspendOwner(); owner.activate("synthetic-b")
            release.countDown(); request.join()
            assertTrue(repo.state.value is ListState.FirstLoad)
            assertEquals(synced, session.lastSyncMillis())
            assertFalse(dir.resolve("szty_snapshot.json").exists())
        } finally { release.countDown(); dir.deleteRecursively() }
    }
}
