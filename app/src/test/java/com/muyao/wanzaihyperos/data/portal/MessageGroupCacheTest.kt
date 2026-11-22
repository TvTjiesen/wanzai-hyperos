package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.net.PortalCredentials
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class MessageGroupCacheTest {
    private class Credentials : PortalCredentials {
        override fun accessToken() = "synthetic-token"
        override fun userCode() = "synthetic-owner"
        override fun portalCookiesJson(): String? = null
        override fun savePortalCookies(json: String) = Unit
        override fun saveAccessToken(accessToken: String, userCode: String?) = Unit
        override fun savePortalSession(accessToken: String, userCode: String?, portalCookiesJson: String) = Unit
        override fun clear() = Unit
    }

    private class Pending(val group: String, val page: Int) {
        val reply = CompletableDeferred<Pair<Int, String>>()
        fun respond(vararg ids: String) {
            val rows = ids.joinToString(",") {
                """{"id":"$it","title":"Synthetic notice","createdate":"2026-11-20","isread":false}"""
            }
            reply.complete(200 to """{"code":200,"success":true,"rows":[$rows]}""")
        }
        fun fail() { reply.complete(500 to "unavailable") }
    }

    /** Every call terminates inside this interceptor; no socket or real credential is used. */
    private class Fixture : Closeable {
        val epoch = AccountEpoch().apply { activate("synthetic-owner") }
        var now = 1_000L
        val requests = CopyOnWriteArrayList<Pending>()
        private val incoming = Channel<Pending>(Channel.UNLIMITED)
        private val http = OkHttpClient.Builder().addInterceptor { chain ->
            val body = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            val json = Json.parseToJsonElement(body).jsonObject
            val request = Pending(json.getValue("messagegroup").jsonPrimitive.content,
                json.getValue("page").jsonPrimitive.int)
            requests += request
            incoming.trySend(request).getOrThrow()
            val (status, payload) = runBlocking { request.reply.await() }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("Synthetic response")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repo = LivePortalMessageRepository(PortalApi(HzcuClient.create(
            credentials = Credentials(), portalBaseUrl = "https://portal.invalid",
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1,
            httpClient = http,
        )), pageSize = 2, clock = { now }, epoch = epoch)

        suspend fun next(group: String, page: Int = 1): Pending = withTimeout(5_000) {
            incoming.receive().also {
                assertEquals(group, it.group)
                assertEquals(page, it.page)
            }
        }
        fun content() = repo.messages.value as ListState.Content<PortalMessageItem>
        fun ids() = content().items.map { it.id }
        override fun close() {
            requests.forEach { it.reply.complete(500 to "fixture closed") }
            http.dispatcher.cancelAll()
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
            incoming.close()
        }
    }

    private val a = PortalMessageGroup("activity", 187)
    private val b = PortalMessageGroup("lecture", 82)

    @Test fun switchingBackRestoresOnlyThatGroupAndRefreshesItsWholeWindow() = runBlocking {
        Fixture().use { f ->
            val initial = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            f.next(a.name).respond("a1", "a2")
            initial.await()
            val more = async(start = CoroutineStart.UNDISPATCHED) { f.repo.loadMore() }
            f.next(a.name, 2).respond("a3")
            more.await()
            val old = f.content()
            val second = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(b) }
            val secondRequest = f.next(b.name)
            assertEquals(ListState.FirstLoad, f.repo.messages.value)
            secondRequest.respond("b1")
            second.await()

            f.now = 2_000L
            val back = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a.copy(unread = 186)) }
            val firstPage = f.next(a.name)
            assertEquals(old.items, f.content().items)
            assertEquals(2, f.content().meta.page)
            assertFalse(f.content().meta.hasMore)
            assertEquals(1_000L, f.content().meta.updatedAtMillis)
            assertTrue(f.content().meta.refreshing)
            f.repo.selectGroup(a.copy(unread = 185))
            assertEquals(4, f.requests.size)
            firstPage.respond("a4", "a5")
            f.next(a.name, 2).respond("a3")
            back.await()
            assertEquals(listOf("a4", "a5", "a3"), f.ids())
            assertEquals(2_000L, f.content().meta.updatedAtMillis)
            assertFalse(f.content().meta.refreshing)
            f.repo.selectGroup(a)
            f.repo.loadMore()
            assertEquals(5, f.requests.size)
        }
    }

    @Test fun failedAndCancelledRefreshRetainTheGroupsRowsAndSuccessfulTime() = runBlocking {
        Fixture().use { f ->
            val initial = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            f.next(a.name).respond("a1")
            initial.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(b) }
            f.next(b.name).respond("b1")
            second.await()
            val back = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            f.next(a.name).fail()
            back.await()
            assertEquals(listOf("a1"), f.ids())
            assertEquals(1_000L, f.content().meta.updatedAtMillis)
            assertTrue(f.content().meta.stale)
            val previousError = f.content().meta.lastRefreshError

            val refresh = async(start = CoroutineStart.UNDISPATCHED) { f.repo.refreshKeepingContent() }
            val pending = f.next(a.name)
            refresh.cancelAndJoin()
            pending.respond("discarded")
            assertEquals(listOf("a1"), f.ids())
            assertFalse(f.content().meta.refreshing)
            // Starting a new check clears its previous error; cancellation adds no error.
            assertNotNull(previousError)
            assertNull(f.content().meta.lastRefreshError)
            assertEquals(1_000L, f.content().meta.updatedAtMillis)
        }
    }

    @Test fun lateResponseCannotReplaceNewSelectionOrOverwriteItsOldSnapshot() = runBlocking {
        Fixture().use { f ->
            val initial = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            f.next(a.name).respond("a1")
            initial.await()
            val oldRefresh = async(start = CoroutineStart.UNDISPATCHED) { f.repo.refreshKeepingContent() }
            val oldRequest = f.next(a.name)
            val second = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(b) }
            assertEquals(b.name, f.repo.selectedGroup.value?.name)
            oldRequest.respond("late-a")
            f.next(b.name).respond("b1")
            second.await()
            oldRefresh.await()
            assertEquals(b.name, f.repo.selectedGroup.value?.name)
            assertEquals(listOf("b1"), f.ids())
            val back = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            val request = f.next(a.name)
            assertEquals(listOf("a1"), f.ids())
            request.respond("fresh-a")
            back.await()
        }
    }

    @Test fun switchingDuringPaginationRestoresTheLastCompletedPageWithoutBusyFlags() = runBlocking {
        Fixture().use { f ->
            val initial = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            f.next(a.name).respond("a1", "a2")
            initial.await()
            val more = async(start = CoroutineStart.UNDISPATCHED) { f.repo.loadMore() }
            val oldPage = f.next(a.name, 2)
            val second = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(b) }
            assertEquals(b.name, f.repo.selectedGroup.value?.name)
            more.cancelAndJoin()
            oldPage.respond("discarded")
            f.next(b.name).respond("b1")
            second.await()
            val back = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            val request = f.next(a.name)
            assertEquals(listOf("a1", "a2"), f.ids())
            assertEquals(1, f.content().meta.page)
            assertTrue(f.content().meta.hasMore)
            assertFalse(f.content().meta.loadingMore)
            request.respond("a1", "a2")
            back.await()
            val retry = async(start = CoroutineStart.UNDISPATCHED) { f.repo.loadMore() }
            f.next(a.name, 2).respond("a3")
            retry.await()
            assertEquals(listOf("a1", "a2", "a3"), f.ids())
        }
    }

    @Test fun duplicateInitialSelectionDoesNotStartAnotherRequestAndCancellationCanRetry() = runBlocking {
        Fixture().use { f ->
            val initial = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            val request = f.next(a.name)
            f.repo.selectGroup(a.copy(unread = 0))
            f.repo.refreshKeepingContent()
            assertEquals(1, f.requests.size)
            initial.cancelAndJoin()
            request.respond("cancelled")
            val retry = async(start = CoroutineStart.UNDISPATCHED) { f.repo.refreshKeepingContent() }
            f.next(a.name).respond("a1")
            retry.await()
            assertEquals(listOf("a1"), f.ids())
        }
    }

    @Test fun expiredSessionRetainsOnlyTheSelectedGroupsReadableContentAndTimestamp() = runBlocking {
        Fixture().use { f ->
            val first = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            f.next(a.name).respond("a1", "a2")
            first.await()
            val more = async(start = CoroutineStart.UNDISPATCHED) { f.repo.loadMore() }
            f.next(a.name, 2).reply.complete(401 to """{"code":401,"success":false}""")
            more.await()
            assertEquals(listOf("a1", "a2"), f.ids())
            assertEquals(1, f.content().meta.page)
            assertEquals(1_000L, f.content().meta.updatedAtMillis)
            assertTrue(f.content().meta.lastRefreshError is com.muyao.wanzaihyperos.net.HzcuException.LoginExpired)
            assertFalse(f.content().meta.loadingMore)
            val retry = async(start = CoroutineStart.UNDISPATCHED) { f.repo.refreshKeepingContent() }
            f.next(a.name).respond("fresh-a")
            retry.await()
            assertNull(f.content().meta.lastRefreshError)
        }
    }

    @Test fun expiredSessionKeepsConfirmedEmptyButDoesNotInventContentForAnUnloadedGroup() = runBlocking {
        Fixture().use { f ->
            val first = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            f.next(a.name).respond()
            first.await()
            val refresh = async(start = CoroutineStart.UNDISPATCHED) { f.repo.refreshKeepingContent() }
            f.next(a.name).reply.complete(401 to """{"code":401,"success":false}""")
            refresh.await()
            val empty = f.repo.messages.value as ListState.Empty
            assertEquals(1_000L, empty.meta.updatedAtMillis)
            assertTrue(empty.meta.lastRefreshError is com.muyao.wanzaihyperos.net.HzcuException.LoginExpired)
            val other = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(b) }
            f.next(b.name).reply.complete(401 to """{"code":401,"success":false}""")
            other.await()
            assertEquals(ListState.SessionExpired, f.repo.messages.value)
        }
    }

    @Test fun accountResetClearsEveryGroupAndRejectsThePreviousOwnersResponse() = runBlocking {
        Fixture().use { f ->
            val initial = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            f.next(a.name).respond("old-a")
            initial.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(b) }
            f.next(b.name).respond("old-b")
            second.await()
            val old = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            val oldRequest = f.next(a.name)
            f.epoch.activate("different-synthetic-owner")
            assertNull(f.repo.selectedGroup.value)
            assertEquals(ListState.FirstLoad, f.repo.messages.value)
            val fresh = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(a) }
            assertEquals(ListState.FirstLoad, f.repo.messages.value)
            oldRequest.respond("late-old-a")
            val freshRequest = f.next(a.name)
            freshRequest.respond("new-a")
            fresh.await()
            old.await()
            assertEquals(listOf("new-a"), f.ids())
            val other = async(start = CoroutineStart.UNDISPATCHED) { f.repo.selectGroup(b) }
            val otherRequest = f.next(b.name)
            assertEquals(ListState.FirstLoad, f.repo.messages.value)
            otherRequest.respond("new-b")
            other.await()
            assertEquals(listOf("new-b"), f.ids())
        }
    }
}
