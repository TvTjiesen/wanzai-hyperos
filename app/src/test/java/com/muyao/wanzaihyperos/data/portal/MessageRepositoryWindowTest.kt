package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.net.PortalCredentials
import com.muyao.wanzaihyperos.net.TestHttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MessageRepositoryWindowTest {
    private val selected = PortalMessageGroup("synthetic-window", 9)

    private class Credentials : PortalCredentials {
        override fun accessToken() = "synthetic-token"
        override fun userCode() = "synthetic-user"
        override fun portalCookiesJson(): String? = null
        override fun savePortalCookies(json: String) = Unit
        override fun saveAccessToken(accessToken: String, userCode: String?) = Unit
        override fun savePortalSession(accessToken: String, userCode: String?, portalCookiesJson: String) = Unit
        override fun clear() = Unit
    }

    private fun repository(server: TestHttpServer, clock: () -> Long = { 1_000L }) =
        LivePortalMessageRepository(PortalApi(HzcuClient.create(
            credentials = Credentials(), portalBaseUrl = server.baseUrl,
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1,
        )), pageSize = 2, clock = clock)

    private fun repository(epoch: AccountEpoch = AccountEpoch(), reply: (Request) -> String): LivePortalMessageRepository {
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply(chain.request()).toResponseBody()).build()
        }.build()
        return LivePortalMessageRepository(PortalApi(HzcuClient.create(
            credentials = Credentials(), portalBaseUrl = "https://synthetic.invalid", httpClient = http,
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1,
        )), pageSize = 2, clock = { 1_000L }, epoch = epoch)
    }

    private fun row(id: String, date: String = "2026-11-19", title: String = id, read: Boolean = false) =
        """{"id":"$id","title":"$title","createdate":"$date","isread":$read}"""

    private fun rows(vararg entries: String) =
        """{"code":200,"success":true,"data":null,"rows":[${entries.joinToString(",")}],"total":1}"""

    private fun content(repo: LivePortalMessageRepository) = repo.messages.value as ListState.Content<PortalMessageItem>
    private fun page(body: String) = Json.parseToJsonElement(body).jsonObject.getValue("page").jsonPrimitive.int
    private fun requestBody(request: Request) = Buffer().also { requireNotNull(request.body).writeTo(it) }.readUtf8()
    private fun requestPage(request: Request) = page(requestBody(request))
    private fun requestGroup(request: Request) =
        Json.parseToJsonElement(requestBody(request)).jsonObject.getValue("messagegroup").jsonPrimitive.content

    private suspend fun seedWindow(repo: LivePortalMessageRepository, server: TestHttpServer) {
        server.enqueue(body = rows(row("first", "2025-11-06", "Before"), row("deleted", "2026-01-01")))
        server.enqueue(body = rows(row("tail", "2026-02-01"), row("changed", "2026-03-01", "Before")))
        repo.selectGroup(selected)
        repo.loadMore()
        assertEquals(2, content(repo).meta.page)
        assertTrue(content(repo).meta.hasMore)
    }

    @Test fun successfulRefreshReplacesWindowUpdatesRowsAndKeepsNextPageCursor() = runTest {
        val server = TestHttpServer()
        try {
            var now = 1_000L
            val repo = repository(server) { now }
            seedWindow(repo, server)
            now = 2_000L
            server.enqueue(body = rows(
                row("first", "2025-11-06", "Updated first", true),
                row("changed", "2026-11-20", "Updated tail", true),
            ))
            server.enqueue(body = rows(row("changed", "2026-11-20", "Updated tail", true), row("added", "2026-11-21")))
            repo.refreshKeepingContent()
            val refreshed = content(repo)
            assertEquals(listOf("added", "changed", "first"), refreshed.items.map { it.id })
            assertEquals("Updated tail", refreshed.items.single { it.id == "changed" }.title)
            assertEquals("Updated first", refreshed.items.single { it.id == "first" }.title)
            assertTrue(refreshed.items.filter { it.id != "added" }.all { it.isRead })
            assertEquals("2025-11-06", refreshed.items.last().createdDate)
            assertEquals(2, refreshed.meta.page)
            assertTrue(refreshed.meta.hasMore)
            assertFalse(refreshed.meta.refreshing || refreshed.meta.loadingMore || refreshed.meta.stale)
            assertNull(refreshed.meta.lastRefreshError)
            assertEquals(2_000L, refreshed.meta.updatedAtMillis)
            now = 3_000L
            server.enqueue(body = rows(row("end", "2026-11-22")))
            repo.loadMore()
            assertEquals(listOf("end", "added", "changed", "first"), content(repo).items.map { it.id })
            assertEquals(3, content(repo).meta.page)
            assertFalse(content(repo).meta.hasMore)
            assertEquals(listOf(1, 2, 1, 2, 3), server.requests.map { page(it.body) })
            server.requests.forEach {
                val fields = Json.parseToJsonElement(it.body).jsonObject
                assertEquals(selected.name, fields.getValue("messagegroup").jsonPrimitive.content)
                assertEquals(2, fields.getValue("rows").jsonPrimitive.int)
                assertTrue(it.contentType.orEmpty().startsWith("application/json"))
                assertTrue(it.path.endsWith("/ummessage/listmessage"))
            }
        } finally { server.stop() }
    }

    @Test fun failedLaterRefreshPageRetainsWholeWindowMetadataAndAllowsRetryAtNextPage() = runTest {
        val server = TestHttpServer()
        try {
            var now = 1_000L
            val repo = repository(server) { now }
            seedWindow(repo, server)
            val before = content(repo)
            now = 2_000L
            server.enqueue(body = rows(row("replacement-a"), row("replacement-b")))
            server.enqueue(500, "synthetic unavailable")
            repo.refreshKeepingContent()
            val failed = content(repo)
            assertEquals(before.items, failed.items)
            assertEquals(before.meta.page, failed.meta.page)
            assertEquals(before.meta.hasMore, failed.meta.hasMore)
            assertEquals(before.meta.updatedAtMillis, failed.meta.updatedAtMillis)
            assertTrue(failed.meta.stale)
            assertNotNull(failed.meta.lastRefreshError)
            assertFalse(failed.meta.refreshing || failed.meta.loadingMore)
            now = 3_000L
            server.enqueue(body = rows(row("retry-tail")))
            repo.loadMore()
            assertEquals(before.items.map { it.id }.toSet() + "retry-tail", content(repo).items.map { it.id }.toSet())
            assertEquals(3, content(repo).meta.page)
            assertFalse(content(repo).meta.loadingMore || content(repo).meta.hasMore)
            assertEquals(listOf(1, 2, 1, 2, 3), server.requests.map { page(it.body) })
        } finally { server.stop() }
    }

    @Test fun shortRefreshPageStopsEarlyAndConfirmedEmptyClearsOldWindow() = runTest {
        val server = TestHttpServer()
        try {
            val repo = repository(server)
            seedWindow(repo, server)
            server.enqueue(body = rows(row("third-a"), row("third-b")))
            repo.loadMore()
            assertEquals(3, content(repo).meta.page)
            server.enqueue(body = rows(row("only")))
            repo.refreshKeepingContent()
            assertEquals(listOf("only"), content(repo).items.map { it.id })
            assertEquals(1, content(repo).meta.page)
            assertFalse(content(repo).meta.hasMore)
            assertEquals(listOf(1, 2, 3, 1), server.requests.map { page(it.body) })
            server.enqueue(body = rows())
            repo.refreshKeepingContent()
            val empty = repo.messages.value as ListState.Empty
            assertEquals(1, empty.meta.page)
            assertFalse(empty.meta.hasMore || empty.meta.refreshing || empty.meta.loadingMore || empty.meta.stale)
            assertEquals(1_000L, empty.meta.updatedAtMillis)
            repo.loadMore()
            assertEquals(listOf(1, 2, 3, 1, 1), server.requests.map { page(it.body) })
        } finally { server.stop() }
    }

    @Test fun fullRawPagesWithInvalidRowsDoNotPrematurelyEndSelectionPaginationOrRefresh() = runTest {
        val server = TestHttpServer()
        try {
            val repo = repository(server)
            val missingId = """{"title":"Missing id"}"""
            val missingTitle = """{"id":"missing-title"}"""
            server.enqueue(body = rows(row("first"), missingId))
            repo.selectGroup(selected)
            assertEquals(listOf("first"), content(repo).items.map { it.id })
            assertTrue(content(repo).meta.hasMore)
            server.enqueue(body = rows(row("tail", "2026-11-20"), missingTitle))
            repo.loadMore()
            assertEquals(2, content(repo).meta.page)
            assertTrue(content(repo).meta.hasMore)
            server.enqueue(body = rows(row("new-first"), missingId))
            server.enqueue(body = rows(row("new-tail", "2026-11-20"), missingTitle))
            repo.refreshKeepingContent()
            assertEquals(listOf("new-tail", "new-first"), content(repo).items.map { it.id })
            assertEquals(2, content(repo).meta.page)
            assertTrue(content(repo).meta.hasMore)
            server.enqueue(body = rows(row("end", "2026-11-21")))
            repo.loadMore()
            assertEquals(3, content(repo).meta.page)
            assertFalse(content(repo).meta.hasMore)
            assertEquals(listOf(1, 2, 1, 2, 3), server.requests.map { page(it.body) })
        } finally { server.stop() }
    }

    private class Blocked {
        val entered = CompletableDeferred<Unit>()
        private val released = CountDownLatch(1)
        fun reply(body: String): String {
            entered.complete(Unit)
            check(released.await(5, TimeUnit.SECONDS)) { "Synthetic response was not released" }
            return body
        }
        fun release() = released.countDown()
    }

    private inner class WindowFlight(epoch: AccountEpoch = AccountEpoch()) {
        val blocked = Blocked()
        val requests = CopyOnWriteArrayList<Pair<String, Int>>()
        private val refreshing = AtomicBoolean(false)
        val repo = repository(epoch) { request ->
            val name = requestGroup(request)
            val page = requestPage(request)
            requests.add(name to page)
            when {
                name == "new-group" -> rows(row("new-group-row"))
                !refreshing.get() && page == 1 -> rows(row("old-a"), row("old-b"))
                !refreshing.get() && page == 2 -> rows(row("old-c"), row("old-d"))
                refreshing.get() && page == 1 -> rows(row("new-a", "2026-10-01"), row("new-b", "2026-11-01"))
                refreshing.get() && page == 2 -> blocked.reply(rows(row("new-tail", "2026-11-21")))
                else -> error("Unexpected synthetic window request: $name page=$page")
            }
        }
        suspend fun seed() {
            repo.selectGroup(selected)
            repo.loadMore()
            assertEquals(2, content(repo).meta.page)
            assertTrue(content(repo).meta.hasMore)
            refreshing.set(true)
        }
    }

    @Test fun overlappingLoadMoreCallsDoNotInvalidateFirstResponseOrLeaveLoadingFlag() = runBlocking {
        val blocked = Blocked()
        val requests = CopyOnWriteArrayList<Int>()
        val repo = repository { request ->
            val page = requestPage(request)
            requests.add(page)
            when (page) {
                1 -> rows(row("first"), row("second"))
                2 -> blocked.reply(rows(row("tail", "2026-11-21")))
                else -> error("Unexpected synthetic page: $page")
            }
        }
        repo.selectGroup(selected)
        val first = launch { repo.loadMore() }
        try {
            withTimeout(5_000) { blocked.entered.await() }
            assertTrue(content(repo).meta.loadingMore)
            val duplicate = launch(start = CoroutineStart.UNDISPATCHED) { repo.loadMore() }
            blocked.release()
            withTimeout(5_000) { first.join(); duplicate.join() }
            assertEquals(listOf("tail", "first", "second"), content(repo).items.map { it.id })
            assertEquals(2, content(repo).meta.page)
            assertFalse(content(repo).meta.loadingMore || content(repo).meta.refreshing || content(repo).meta.hasMore)
            assertNull(content(repo).meta.lastRefreshError)
            assertEquals(listOf(1, 2), requests.toList())
        } finally { blocked.release() }
    }

    @Test fun loadMoreDuringRefreshCannotInvalidateAtomicWindowReplacement() = runBlocking {
        val flight = WindowFlight()
        flight.seed()
        val before = content(flight.repo)
        val refresh = launch { flight.repo.refreshKeepingContent() }
        try {
            withTimeout(5_000) { flight.blocked.entered.await() }
            val pending = content(flight.repo)
            assertEquals(before.items, pending.items)
            assertEquals(before.meta.page, pending.meta.page)
            assertEquals(before.meta.updatedAtMillis, pending.meta.updatedAtMillis)
            assertTrue(pending.meta.refreshing)
            val more = launch(start = CoroutineStart.UNDISPATCHED) { flight.repo.loadMore() }
            assertEquals(before.items, content(flight.repo).items)
            flight.blocked.release()
            withTimeout(5_000) { refresh.join(); more.join() }
            val replaced = content(flight.repo)
            assertEquals(listOf("new-tail", "new-b", "new-a"), replaced.items.map { it.id })
            assertEquals(2, replaced.meta.page)
            assertFalse(replaced.meta.loadingMore || replaced.meta.refreshing || replaced.meta.hasMore || replaced.meta.stale)
            assertNull(replaced.meta.lastRefreshError)
            assertEquals(listOf(1, 2, 1, 2), flight.requests.map { it.second })
        } finally { flight.blocked.release() }
    }

    @Test fun overlappingRefreshCallsReuseInFlightWindowWithoutExtraRequests() = runBlocking {
        val flight = WindowFlight()
        flight.seed()
        val first = launch { flight.repo.refreshKeepingContent() }
        try {
            withTimeout(5_000) { flight.blocked.entered.await() }
            val duplicate = launch(start = CoroutineStart.UNDISPATCHED) { flight.repo.refreshMessages() }
            flight.blocked.release()
            withTimeout(5_000) { first.join(); duplicate.join() }
            assertEquals(listOf("new-tail", "new-b", "new-a"), content(flight.repo).items.map { it.id })
            assertFalse(content(flight.repo).meta.refreshing || content(flight.repo).meta.loadingMore)
            assertEquals(listOf(1, 2, 1, 2), flight.requests.map { it.second })
        } finally { flight.blocked.release() }
    }

    @Test fun cancelledWindowRefreshPreservesOldWindowClearsFlagsAndCanRetry() = runBlocking {
        val flight = WindowFlight()
        flight.seed()
        val before = content(flight.repo)
        val refresh = launch { flight.repo.refreshKeepingContent() }
        try {
            withTimeout(5_000) { flight.blocked.entered.await() }
            refresh.cancel()
            flight.blocked.release()
            withTimeout(5_000) { refresh.join() }
            assertEquals(before, content(flight.repo))
            flight.repo.refreshKeepingContent()
            assertEquals(listOf("new-tail", "new-b", "new-a"), content(flight.repo).items.map { it.id })
            assertFalse(content(flight.repo).meta.refreshing || content(flight.repo).meta.loadingMore)
            assertEquals(listOf(1, 2, 1, 2, 1, 2), flight.requests.map { it.second })
        } finally { flight.blocked.release() }
    }

    @Test fun cancelledPaginationClearsFlagReleasesMutexAndCanRetry() = runBlocking {
        val blocked = Blocked()
        val firstTail = AtomicBoolean(true)
        val repo = repository { request ->
            when (requestPage(request)) {
                1 -> rows(row("first"), row("second"))
                2 -> {
                    val tail = rows(row("tail", "2026-11-21"))
                    if (firstTail.compareAndSet(true, false)) blocked.reply(tail) else tail
                }
                else -> error("Unexpected synthetic page")
            }
        }
        repo.selectGroup(selected)
        val before = content(repo)
        val more = launch { repo.loadMore() }
        try {
            withTimeout(5_000) { blocked.entered.await() }
            more.cancel()
            blocked.release()
            withTimeout(5_000) { more.join() }
            assertEquals(before, content(repo))
            repo.loadMore()
            assertEquals(listOf("tail", "first", "second"), content(repo).items.map { it.id })
            assertEquals(2, content(repo).meta.page)
            assertFalse(content(repo).meta.loadingMore || content(repo).meta.refreshing || content(repo).meta.hasMore)
            assertNull(content(repo).meta.lastRefreshError)
        } finally { blocked.release() }
    }

    @Test fun groupSwitchRejectsOldWindowEvenAfterItsFirstPageWasRead() = runBlocking {
        val flight = WindowFlight()
        flight.seed()
        val refresh = launch { flight.repo.refreshKeepingContent() }
        try {
            withTimeout(5_000) { flight.blocked.entered.await() }
            val switch = launch(start = CoroutineStart.UNDISPATCHED) {
                flight.repo.selectGroup(PortalMessageGroup("new-group", 0))
            }
            assertEquals("new-group", flight.repo.selectedGroup.value?.name)
            assertTrue(flight.repo.messages.value is ListState.FirstLoad)
            flight.blocked.release()
            withTimeout(5_000) { refresh.join(); switch.join() }
            val replacement = content(flight.repo)
            assertEquals(listOf("new-group-row"), replacement.items.map { it.id })
            assertFalse(replacement.meta.refreshing || replacement.meta.loadingMore)
            assertEquals(listOf(selected.name to 1, selected.name to 2, selected.name to 1, selected.name to 2, "new-group" to 1), flight.requests.toList())
        } finally { flight.blocked.release() }
    }

    @Test fun accountResetRejectsOldWindowAndCannotRestoreClearedSelection() = runBlocking {
        val owner = AccountEpoch().apply { activate("synthetic-owner-a") }
        val flight = WindowFlight(owner)
        flight.seed()
        val refresh = launch { flight.repo.refreshKeepingContent() }
        try {
            withTimeout(5_000) { flight.blocked.entered.await() }
            owner.suspendOwner()
            owner.activate("synthetic-owner-b")
            assertNull(flight.repo.selectedGroup.value)
            assertTrue(flight.repo.messages.value is ListState.FirstLoad)
            flight.blocked.release()
            withTimeout(5_000) { refresh.join() }
            assertNull(flight.repo.selectedGroup.value)
            assertTrue(flight.repo.messages.value is ListState.FirstLoad)
            assertEquals(listOf(1, 2, 1, 2), flight.requests.map { it.second })
        } finally { flight.blocked.release() }
    }
}
