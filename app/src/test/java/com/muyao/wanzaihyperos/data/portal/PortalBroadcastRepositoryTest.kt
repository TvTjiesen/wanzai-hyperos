package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuResponse
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class PortalBroadcastRepositoryTest {
    private fun response(text: String): HzcuResponse {
        val raw = Json.parseToJsonElement(text).jsonObject
        return HzcuResponse(200, true, "", raw["data"], raw)
    }
    private fun listing(title: String = "Synthetic") = response("""{"rows":[{"id":"7","title":"$title"}]}""")
    private val item = PortalBroadcastItem("7", "Synthetic", "", "")

    @Test fun refreshFailureKeepsContentAndSuccessfulTimestamp() = runBlocking {
        var broken = false
        val repo = LivePortalBroadcastRepository({ if (broken) throw IOException("Synthetic failure") else listing() },
            { response("""{"data":{"content":"Text"}}""") }, clock = { 100L })
        repo.refresh(); broken = true; repo.refresh()
        val state = repo.state.value as ListState.Content
        assertEquals("Synthetic", state.items.single().title)
        assertEquals(100L, state.meta.updatedAtMillis)
        assertTrue(state.meta.hasRefreshProblem)
        assertFalse(state.meta.refreshing)
        assertFalse(state.meta.hasMore)
    }

    @Test fun malformedFirstResponseIsFailureNotEmpty() = runBlocking {
        val repo = LivePortalBroadcastRepository({ response("{}") }, { response("{}") })
        repo.refresh()
        assertTrue(repo.state.value is ListState.Failed)
    }

    @Test fun concurrentListReadsAreCoalescedWithoutDuplicateRequests() = runBlocking {
        var calls = 0
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repo = LivePortalBroadcastRepository({ calls++; entered.complete(Unit); release.await(); listing() }, { response("{}") })
        val first = launch { repo.refresh() }; entered.await(); repo.refresh()
        assertEquals(1, calls); release.complete(Unit); first.join()
        assertTrue(repo.state.value is ListState.Content)
    }

    @Test fun cancelledRefreshClearsLoadingAndAllowsRetry() = runBlocking {
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        val repo = LivePortalBroadcastRepository({
            calls++
            if (calls == 2) { entered.complete(Unit); awaitCancellation() }
            listing()
        }, { response("{}") })
        repo.refresh()
        val pending = launch { repo.refresh() }; entered.await(); pending.cancelAndJoin()
        val state = repo.state.value as ListState.Content
        assertFalse(state.meta.refreshing)
        assertNull(state.meta.lastRefreshError)
        repo.refresh(); assertEquals(3, calls)
    }

    @Test fun replacedOwnerRejectsLateListAndDetailPublication() = runBlocking {
        val epoch = AccountEpoch().apply { activate("synthetic-A") }
        val listEntered = CompletableDeferred<Unit>(); val detailEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repo = LivePortalBroadcastRepository({ listEntered.complete(Unit); release.await(); listing() },
            { detailEntered.complete(Unit); release.await(); response("""{"data":{"content":"Old"}}""") }, epoch = epoch)
        val list = launch { repo.refresh() }; val detail = launch { repo.open(item) }
        listEntered.await(); detailEntered.await(); epoch.activate("synthetic-B"); release.complete(Unit)
        list.join(); detail.join()
        assertEquals(ListState.FirstLoad, repo.state.value)
        assertEquals(ListState.FirstLoad, repo.detail.value)
    }

    @Test fun closedOrSupersededDetailCannotResurrect() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repo = LivePortalBroadcastRepository({ listing() }, { id ->
            if (id == "7") { entered.complete(Unit); release.await() }
            response("""{"data":{"content":"$id"}}""")
        })
        val old = launch { repo.open(item) }; entered.await()
        val next = item.copy(id = "8")
        repo.open(next); release.complete(Unit); old.join()
        assertEquals(next, (repo.detail.value as ListState.Content).items.single().item)
        repo.closeDetail()
        assertEquals(ListState.FirstLoad, repo.detail.value)
    }

    @Test fun closingInFlightDetailRejectsLateResponse() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repo = LivePortalBroadcastRepository({ listing() }, {
            entered.complete(Unit); release.await(); response("""{"data":{"content":"Old"}}""")
        })
        val old = launch { repo.open(item) }; entered.await(); repo.closeDetail(); release.complete(Unit); old.join()
        assertEquals(ListState.FirstLoad, repo.detail.value)
    }

    @Test fun blockedOwnerDoesNotIssueReads() = runBlocking {
        var calls = 0
        val epoch = AccountEpoch().apply { activate("synthetic-A", allowData = false) }
        val repo = LivePortalBroadcastRepository({ calls++; listing() }, { calls++; response("{}") }, epoch = epoch)
        repo.refresh(); repo.open(item)
        assertEquals(0, calls)
    }
}
