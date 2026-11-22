package com.muyao.wanzaihyperos.ui.web

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.cjh52.LiveGrowthRepository
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class Cjh52RecoveryTest {
    private class Rig(
        scope: CoroutineScope,
        initial: Cjh52Credential? = Cjh52Credential("synthetic-old", 1L),
        timeoutMillis: Long = 40_000,
        acquire: suspend (Long) -> String? = { "synthetic-new" },
    ) {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }
        @Volatile var credential = initial
        val acquisitions = AtomicInteger()
        val saves = AtomicInteger()
        val clears = AtomicInteger()
        private val revision = AtomicLong(1L)
        val coordinator = Cjh52Recovery(
            epoch = epoch, scope = scope, readCredential = { credential },
            clearCredential = { clears.incrementAndGet(); credential = null },
            saveCredential = { saves.incrementAndGet(); credential = Cjh52Credential(it, revision.incrementAndGet()) },
            acquire = { acquisitions.incrementAndGet(); acquire(it) },
            timeoutMillis = timeoutMillis,
        )

        fun client(action: (Interceptor.Chain) -> Response): Cjh52Client {
            val session = PortalSession(InMemorySecureStore()).apply {
                save("synthetic-portal", "synthetic-A", "{}")
            }
            val transport = HzcuClient.create(
                session, portalBaseUrl = "https://synthetic.invalid", epoch = epoch,
                rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1,
                httpClient = OkHttpClient.Builder().addInterceptor(action).build(),
            ).apply { renewLog = {} }
            return Cjh52Client(transport, { credential?.token }, epoch = epoch).apply { recovery = coordinator }
        }
    }

    private fun reply(chain: Interceptor.Chain, body: String = "{\"code\":200,\"data\":{}}", status: Int = 200): Response =
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("synthetic")
            .body(body.toResponseBody("application/json".toMediaType())).build()

    @Test(timeout = 5_000) fun `readers share activation and cancelling a waiter does not cancel it`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val rig = Rig(this, initial = null) { entered.complete(Unit); release.await(); "synthetic-new" }
        val first = async { rig.coordinator.ensure(rig.epoch.token()) }
        entered.await()
        val follower = async(start = CoroutineStart.UNDISPATCHED) { rig.coordinator.ensure(rig.epoch.token()) }
        follower.cancel(); follower.join()
        release.complete(Unit)
        assertEquals("synthetic-new", first.await()?.token)
        assertEquals(1, rig.acquisitions.get())
        assertEquals(1, rig.saves.get())
        assertEquals(Cjh52Recovery.Status.Ready, rig.coordinator.status.value)
    }

    @Test(timeout = 5_000) fun `activation timeout fails closed until explicit reconnect`() = runBlocking {
        var available = false
        val rig = Rig(this, initial = null, timeoutMillis = 20) {
            if (available) "synthetic-new" else awaitCancellation()
        }
        assertNull(rig.coordinator.ensure(rig.epoch.token()))
        assertEquals(Cjh52Recovery.Status.Failed, rig.coordinator.status.value)
        assertNull(rig.coordinator.ensure(rig.epoch.token()))
        assertEquals(1, rig.acquisitions.get())
        assertEquals(0, rig.saves.get())
        available = true
        assertNotNull(rig.coordinator.ensure(rig.epoch.token(), manual = true))
        assertEquals(2, rig.acquisitions.get())
    }

    @Test(timeout = 5_000) fun `account replacement cancels activation and prevents late persistence`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val rig = Rig(this, initial = null) { entered.complete(Unit); awaitCancellation() }
        val pending = async { runCatching { rig.coordinator.ensure(rig.epoch.token()) } }
        entered.await()
        rig.epoch.activate("synthetic-B")
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(0, rig.saves.get())
        assertNull(rig.credential)
        assertEquals(Cjh52Recovery.Status.Idle, rig.coordinator.status.value)
    }

    @Test fun `late same owner rejection cannot clear a replacement even with the same token text`() = runBlocking {
        val rig = Rig(this)
        val old = rig.credential!!
        rig.credential = old.copy(revision = old.revision + 1)
        rig.coordinator.reject(rig.epoch.token(), old, allowRecovery = true)
        assertEquals(0, rig.clears.get())
        assertEquals(old.revision + 1, rig.coordinator.current(rig.epoch.token())?.revision)
        assertEquals(0, rig.acquisitions.get())
    }

    @Test(timeout = 5_000) fun `known read replays once after HTTP unauthorized with replacement credential`() = runBlocking {
        val rig = Rig(this)
        val sent = CopyOnWriteArrayList<String>()
        val client = rig.client { chain ->
            sent += chain.request().header("Authorization").orEmpty()
            reply(chain, status = if (sent.size == 1) 401 else 200)
        }
        client.post(HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH)
        assertEquals(listOf("Bearer synthetic-old", "Bearer synthetic-new"), sent.toList())
        assertEquals(1, rig.acquisitions.get())
        assertEquals(1, rig.clears.get())
        assertEquals(Cjh52Recovery.Status.Ready, rig.coordinator.status.value)
    }

    @Test(timeout = 5_000) fun `business unauthorized after replay stops at two sends and requires reconnect`() = runBlocking {
        val rig = Rig(this)
        val sends = AtomicInteger()
        val client = rig.client { chain -> sends.incrementAndGet(); reply(chain, "{\"code\":401,\"msg\":\"synthetic\"}") }
        val failure = runCatching { client.post(HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH) }.exceptionOrNull()
        assertTrue(failure is HzcuException.LoginExpired)
        assertEquals(2, sends.get())
        assertEquals(1, rig.acquisitions.get())
        assertEquals(Cjh52Recovery.Status.Failed, rig.coordinator.status.value)
        assertNull(rig.credential)
    }

    @Test fun `unknown write never replays and nonauth read failures never clear or activate`() = runBlocking {
        val writeRig = Rig(this)
        val sends = AtomicInteger()
        val write = writeRig.client { chain -> sends.incrementAndGet(); reply(chain, status = 401) }
        assertTrue(runCatching { write.post("/dapi/v2/synthetic_write") }.exceptionOrNull() is HzcuException.LoginExpired)
        assertEquals(1, sends.get())
        assertEquals(0, writeRig.acquisitions.get())
        for (status in listOf(403, 503)) {
            val readRig = Rig(this)
            val read = readRig.client { chain -> reply(chain, "{\"code\":$status,\"msg\":\"synthetic\"}", status) }
            assertNotNull(runCatching { read.post(HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH) }.exceptionOrNull())
            assertEquals(0, readRig.acquisitions.get())
            assertEquals(0, readRig.clears.get())
            assertNotNull(readRig.credential)
        }
    }

    @Test fun `activation origin requires exact official HTTPS host and standard port`() {
        val host = HzcuEndpoints.CJH52_HOST
        assertTrue(isCjh52Origin("https://$host/course/student?token=synthetic"))
        assertTrue(isCjh52Origin("https://$host:443/"))
        for (address in listOf(null, "http://$host/", "https://$host.synthetic.invalid/", "https://$host:444/", "https://user@$host/", "not a URL")) {
            assertFalse(address.orEmpty(), isCjh52Origin(address))
        }
    }

    @Test(timeout = 5_000) fun `growth auth failure in a subrequest is not swallowed into an empty section`() = runBlocking {
        val rig = Rig(this, acquire = { null })
        val sends = AtomicInteger()
        val client = rig.client { chain ->
            sends.incrementAndGet()
            if (chain.request().url.encodedPath == HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH)
                reply(chain, "{\"code\":200,\"data\":{\"user\":{\"sid\":\"7\"}}}")
            else reply(chain, status = 401)
        }
        val repository = LiveGrowthRepository(client, epoch = rig.epoch)
        repository.refresh()
        assertEquals(ListState.SessionExpired, repository.state.value)
        assertEquals(2, sends.get())
        assertEquals(Cjh52Recovery.Status.Failed, rig.coordinator.status.value)
    }

    @Test(timeout = 5_000) fun `growth preserves cached sections and marks stale on partial network or auth failure`() = runBlocking {
        val rig = Rig(this, acquire = { null })
        var mode = "success"
        val client = rig.client { chain ->
            when (chain.request().url.encodedPath) {
                HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH -> reply(chain, "{\"code\":200,\"data\":{\"user\":{\"sid\":\"7\"}}}")
                HzcuEndpoints.CJH52_KQA_SCORE_PATH -> when (mode) {
                    "network" -> throw IOException("synthetic")
                    "auth" -> reply(chain, status = 401)
                    else -> reply(chain, "{\"code\":200,\"data\":{\"Q\":7,\"GY\":2,\"K\":1}}")
                }
                HzcuEndpoints.CJH52_PHYSIQUE_PATH -> reply(chain, "{\"code\":200,\"data\":[]}")
                HzcuEndpoints.CJH52_ACTIVITY_MONTHS_PATH -> reply(chain, "{\"code\":200,\"data\":{\"results\":[]}}")
                else -> reply(chain)
            }
        }
        val repository = LiveGrowthRepository(client, epoch = rig.epoch)
        repository.refresh()
        val initial = repository.state.value as ListState.Content
        assertNotNull(initial.items.single().kqa)
        mode = "network"
        repository.refresh()
        val partial = repository.state.value as ListState.Content
        assertEquals(initial.items.single().kqa, partial.items.single().kqa)
        // 部分分区沿用了旧资料 ⇒ 整页更新时间不能前进到「刚刚」。
        assertEquals(initial.meta.updatedAtMillis, partial.meta.updatedAtMillis)
        assertTrue(partial.meta.stale)
        assertFalse(partial.meta.refreshing)
        mode = "auth"
        repository.refresh()
        val expired = repository.state.value as ListState.Content
        assertEquals(partial.items, expired.items)
        assertTrue(expired.meta.stale)
        assertFalse(expired.meta.refreshing)
        assertTrue(expired.meta.lastRefreshError is HzcuException.LoginExpired)
        assertEquals(Cjh52Recovery.Status.Failed, rig.coordinator.status.value)
    }

    /**
     * §4「一次幂等读重试」的成长侧实测：
     * 成长是**一包四接口**，任一子读 401 都必须走同一条「换新凭证后只重放一次」的路，
     * 且重放成功后该分区要拿到真实资料（而不是被静默吞成空分区）。
     * 这里让 KQA 第一次 401、重放时 200，其余子读直接成功——四接口各发一次 + KQA 多一次重放。
     */
    @Test(timeout = 5_000) fun `growth subrequest replays exactly once with fresh credential and keeps real data`() = runBlocking {
        val rig = Rig(this)
        val kqaSends = CopyOnWriteArrayList<String>()
        val calls = AtomicInteger()
        val client = rig.client { chain ->
            val path = chain.request().url.encodedPath
            calls.incrementAndGet()
            when (path) {
                HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH -> reply(chain, "{\"code\":200,\"data\":{\"user\":{\"sid\":\"7\"}}}")
                HzcuEndpoints.CJH52_KQA_SCORE_PATH -> {
                    kqaSends += chain.request().header("Authorization").orEmpty()
                    // 第一次用旧凭证 ⇒ 业务 401（HTTP 200）；重放时用新凭证 ⇒ 真实资料。
                    if (kqaSends.size == 1) reply(chain, "{\"code\":401,\"msg\":\"synthetic expired\"}")
                    else reply(chain, "{\"code\":200,\"data\":{\"Q\":7,\"GY\":2,\"K\":1}}")
                }
                HzcuEndpoints.CJH52_PHYSIQUE_PATH -> reply(chain, "{\"code\":200,\"data\":[]}")
                HzcuEndpoints.CJH52_ACTIVITY_MONTHS_PATH -> reply(chain, "{\"code\":200,\"data\":{\"results\":[]}}")
                else -> reply(chain)
            }
        }
        val repository = LiveGrowthRepository(client, epoch = rig.epoch)
        repository.refresh()
        // 只重放一次：旧凭证一次 + 新凭证一次。
        assertEquals(listOf("Bearer synthetic-old", "Bearer synthetic-new"), kqaSends.toList())
        assertEquals(1, rig.acquisitions.get())
        assertEquals("重放成功后应落新凭证而不是清空", "synthetic-new", rig.credential?.token)
        val state = repository.state.value as ListState.Content
        val snapshot = state.items.single()
        assertNotNull("重放成功后 KQA 分区必须是真实资料，不能被吞成空", snapshot.kqa)
        assertFalse(state.meta.stale)
        assertFalse(state.meta.refreshing)
        assertNull(state.meta.lastRefreshError)
        assertEquals(Cjh52Recovery.Status.Ready, rig.coordinator.status.value)
    }

    /**
     * 反向契约：重放后仍然 401 ⇒ 必须停在第 2 次发送，不得无限重试。
     * 成长页四个子读里任一持续 401，整页判定为会话失效并按既有降级路径保留旧内容。
     */
    @Test(timeout = 5_000) fun `growth subrequest that stays unauthorized stops after one replay`() = runBlocking {
        val rig = Rig(this, initial = Cjh52Credential("synthetic-old", 1L))
        val kqaSends = AtomicInteger()
        val client = rig.client { chain ->
            when (chain.request().url.encodedPath) {
                HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH -> reply(chain, "{\"code\":200,\"data\":{\"user\":{\"sid\":\"7\"}}}")
                HzcuEndpoints.CJH52_KQA_SCORE_PATH -> { kqaSends.incrementAndGet(); reply(chain, "{\"code\":401,\"msg\":\"synthetic expired\"}") }
                else -> reply(chain, "{\"code\":200,\"data\":{}}")
            }
        }
        val repository = LiveGrowthRepository(client, epoch = rig.epoch)
        repository.refresh()
        assertEquals("KQA 只应被发送两次：原请求 + 一次重放", 2, kqaSends.get())
        assertEquals(1, rig.acquisitions.get())
        assertTrue(repository.state.value is ListState.SessionExpired || repository.state.value is ListState.Content)
        assertEquals(Cjh52Recovery.Status.Failed, rig.coordinator.status.value)
    }
}
