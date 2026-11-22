package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.data.portal.*
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.net.*
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AccountIsolationRepositoryTest {
    private fun epoch() = AccountEpoch().apply { activate("synthetic-owner-a") }

    private fun api(reply: (Request) -> String): PortalApi {
        val session = PortalSession(InMemorySecureStore()).apply { save("synthetic-access", "synthetic-user", "{}") }
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply(chain.request()).toResponseBody()).build()
        }.build()
        return PortalApi(HzcuClient.create(
            session, portalBaseUrl = "https://synthetic.invalid", httpClient = http,
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1,
        ))
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

    @Test fun oldNewsResponseCannotRestoreSelectionOrContentAfterSwitch() = runBlocking {
        val owner = epoch()
        val blocked = Blocked()
        val repo = LivePortalNewsRepository(api { blocked.reply("""{"code":200,"data":[{"id":"old","title":"A"}]}""") }, epoch = owner)
        val request = launch { repo.selectGroup(PortalNewsGroup("g-a", "A", 1)) }
        blocked.entered.await()
        owner.suspendOwner(); owner.activate("synthetic-owner-b")
        assertNull(repo.selectedGroup.value)
        assertTrue(repo.news.value is ListState.FirstLoad)
        blocked.release(); request.join()
        assertNull(repo.selectedGroup.value)
        assertTrue(repo.news.value is ListState.FirstLoad)
    }

    @Test fun oldPageCannotAppendAfterAccountReset() = runBlocking {
        val owner = epoch()
        val blocked = Blocked()
        var calls = 0
        val repo = LivePortalNewsRepository(api {
            if (++calls == 1) """{"code":200,"data":[{"id":"first","title":"A"}]}"""
            else blocked.reply("""{"code":200,"data":[{"id":"old-page","title":"A2"}]}""")
        }, pageSize = 1, epoch = owner)
        repo.selectGroup(PortalNewsGroup("g-a", "A", 1))
        val request = launch { repo.loadMore() }
        blocked.entered.await()
        owner.suspendOwner(); owner.activate("synthetic-owner-b")
        blocked.release(); request.join()
        assertNull(repo.selectedGroup.value)
        assertTrue(repo.news.value is ListState.FirstLoad)
    }

    @Test fun newsGroupsAndListHaveIndependentFlights() = runBlocking {
        val owner = epoch()
        val blocked = Blocked()
        val repo = LivePortalNewsRepository(api { request ->
            if (request.url.encodedPath.contains("listnewsgroup")) blocked.reply("""{"code":200,"data":[{"id":"g","newsgroup":"G","dataid":1}]}""")
            else """{"code":200,"data":[{"id":"n","title":"N"}]}"""
        }, epoch = owner)
        val groups = launch { repo.refreshGroups() }
        blocked.entered.await()
        val list = launch { repo.selectGroup(PortalNewsGroup("g", "G", 1)) }
        yield()
        blocked.release(); groups.join(); list.join()
        assertTrue(repo.groups.value is ListState.Content)
        assertTrue(repo.news.value is ListState.Content)
    }

    @Test fun oldCountResponseCannotTurnUnknownIntoOldAccountCount() = runBlocking {
        val owner = epoch()
        val blocked = Blocked()
        val repo = LivePortalUnreadRepository(api { blocked.reply("""{"code":200,"data":17}""") }, epoch = owner)
        val request = launch { repo.refresh() }
        blocked.entered.await()
        owner.suspendOwner(); owner.activate("synthetic-owner-b")
        blocked.release(); request.join()
        assertTrue(repo.state.value.value is CountValue.Unknown)
    }

    @Test fun oldMarkReadFailureCannotRollbackNewAccountList() = runBlocking {
        val owner = epoch()
        val blocked = Blocked()
        val repo = LivePortalMessageRepository(api { request ->
            when {
                request.url.encodedPath.contains("setmessageread") -> blocked.reply("""{"code":500,"message":"synthetic rejected"}""")
                else -> """{"code":200,"rows":[{"id":"m","title":"A","isread":false}]}"""
            }
        }, epoch = owner)
        repo.selectGroup(PortalMessageGroup("A", 1, false))
        val write = async { repo.markRead("m") }
        blocked.entered.await()
        owner.suspendOwner(); owner.activate("synthetic-owner-b")
        blocked.release()
        assertFalse(write.await())
        assertNull(repo.selectedGroup.value)
        assertTrue(repo.messages.value is ListState.FirstLoad)
    }

    @Test fun cancellationIsNeverConvertedIntoFailureResult() {
        val cancelled = CancellationException("synthetic cancellation")
        try {
            catchingActive<Unit> { throw cancelled }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }
}
