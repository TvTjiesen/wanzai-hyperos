package com.muyao.wanzaihyperos.data.zy

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.items
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.net.HzcuException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZyRecoveryTest {
    @Test
    fun `only an explicit valid empty list settles as empty`() {
        assertTrue(parseZyWeek(EMPTY_WEEK).isEmpty())
        assertEquals(ZyFailure.UnexpectedResponse, failureOf { parseZyWeek("{}") })
        assertEquals(ZyFailure.UnexpectedResponse, failureOf {
            parseZyWeek("""{"success":false,"result":{"code":500,"list":[]}}""")
        })
        assertEquals(ZyFailure.UnexpectedResponse, failureOf {
            parseZyWeek("""{"success":true,"result":{"code":200,"list":[{"course":[{}]}]}}""")
        })
        assertEquals(ZyFailure.NotLoggedIn, failureOf {
            parseZyWeek("""{"success":false,"result":{"code":401}}""")
        })
    }

    @Test
    fun `fetch metadata distinguishes login transport and malformed HTML`() {
        assertEquals(ZyFailure.NotLoggedIn, failureOf {
            decodeZyFetch("""{"status":200,"url":"https://ca.hzcu.edu.cn/cas/login?service=private"}""")
        })
        assertEquals(ZyFailure.NotLoggedIn, failureOf {
            decodeZyFetch("""{"status":401,"url":"${ZyEngine.BASE}/courseapi"}""")
        })
        assertEquals(ZyFailure.Http(503), failureOf {
            decodeZyFetch("""{"status":503,"url":"${ZyEngine.BASE}/courseapi"}""")
        })
        assertEquals(ZyFailure.UnexpectedResponse, failureOf {
            decodeZyFetch("""{"status":200,"url":"${ZyEngine.BASE}/courseapi","text":"<html>proxy page</html>"}""")
        })
        assertEquals(ZyFailure.NoResponse, failureOf {
            decodeZyFetch("""{"status":200,"url":"https://livemobileapp.hzcu.edu.cn.example.test/login"}""")
        })
        assertEquals(ZyFailure.Timeout, failureOf { decodeZyFetch("""{"timeout":true}""") })
    }

    @Test
    fun `a new read resolves identity and explicit reconnect forces initialization`() = runBlocking {
        val reader = FakeReader()
        val repository = LiveZyRepository(reader)
        repository.load()
        assertEquals(ListState.SessionExpired, repository.week.value)
        assertEquals(1, reader.invalidations)

        reader.id = "new &id"
        reader.fetch = { EMPTY_WEEK }
        repository.load(force = true)
        assertTrue(repository.week.value is ListState.Empty)
        assertEquals(listOf(false, true), reader.forceCalls)
        assertTrue(reader.lastPath.contains("user_id=new+%26id&"))

        reader.id = "reattached"
        repository.load()
        assertEquals(listOf(false, true, false), reader.forceCalls)
        assertTrue(reader.lastPath.contains("user_id=reattached&"))
    }

    @Test
    fun `refresh retains the old schedule through a timeout`() = runBlocking {
        val reader = FakeReader().apply {
            fetch = {
                """{"success":true,"result":{"code":200,"list":[{"day":"2026-11-16","course":[{"id":"1","course_title":"Example"}]}]}}"""
            }
        }
        val repository = LiveZyRepository(reader)
        repository.load()
        val old = repository.week.value.items
        assertEquals(1, old.size)

        val running = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        reader.fetch = {
            running.complete(Unit)
            finish.await()
            throw ZyDomainFailure(ZyFailure.Timeout)
        }
        val refresh = launch { repository.load(force = true) }
        running.await()
        assertEquals(old, repository.week.value.items)
        assertEquals(true, repository.week.value.metaOrNull?.refreshing)
        finish.complete(Unit)
        refresh.join()
        assertEquals(old, repository.week.value.items)
        assertEquals(false, repository.week.value.metaOrNull?.refreshing)
        assertTrue(repository.week.value.metaOrNull?.lastRefreshError is HzcuException.RequestTimeout)
    }

    @Test
    fun `expired session retains readable rows and last successful time with a real login error`() = runBlocking {
        val reader = FakeReader().apply { fetch = { ONE_COURSE } }
        val repository = LiveZyRepository(reader, clock = { 1_795_000_000_000L })
        repository.load()
        val old = repository.week.value
        reader.fetch = { throw ZyDomainFailure(ZyFailure.NotLoggedIn) }
        repository.load(force = true)
        assertEquals(old.items, repository.week.value.items)
        assertEquals(old.metaOrNull?.updatedAtMillis, repository.week.value.metaOrNull?.updatedAtMillis)
        assertTrue(repository.week.value.metaOrNull?.stale == true)
        assertTrue(repository.week.value.metaOrNull?.lastRefreshError is HzcuException.LoginExpired)
        assertEquals(false, repository.week.value.metaOrNull?.refreshing)
        reader.fetch = { EMPTY_WEEK }
        repository.load(force = true)
        assertTrue(repository.week.value is ListState.Empty)
        assertEquals(null, repository.week.value.metaOrNull?.lastRefreshError)
    }

    @Test
    fun `confirmed empty also survives expiry without becoming an infinite first load`() = runBlocking {
        val reader = FakeReader().apply { fetch = { EMPTY_WEEK } }
        val repository = LiveZyRepository(reader)
        repository.load()
        reader.fetch = { throw ZyDomainFailure(ZyFailure.NotLoggedIn) }
        repository.load()
        assertTrue(repository.week.value is ListState.Empty)
        assertTrue(repository.week.value.metaOrNull?.lastRefreshError is HzcuException.LoginExpired)
    }

    @Test
    fun `overlapping reads do not queue another session initialization`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val reader = FakeReader().apply { fetch = { entered.complete(Unit); finish.await(); ONE_COURSE } }
        val repository = LiveZyRepository(reader)
        val first = launch { repository.load() }
        entered.await()
        repository.load(force = true)
        assertEquals(listOf(false), reader.forceCalls)
        finish.complete(Unit)
        first.join()
        assertEquals(1, repository.week.value.items.size)
        assertEquals(false, repository.week.value.metaOrNull?.refreshing)
    }

    @Test
    fun `changed official identity cannot retain the old users schedule on failure`() = runBlocking {
        val reader = FakeReader().apply { fetch = { ONE_COURSE } }
        val repository = LiveZyRepository(reader)
        repository.load()
        reader.id = "another-verified-uid"
        reader.fetch = { throw ZyDomainFailure(ZyFailure.NotLoggedIn) }
        repository.load(force = true)
        assertEquals(ListState.SessionExpired, repository.week.value)
        assertTrue(repository.week.value.items.isEmpty())
    }

    @Test
    fun `week rollover never presents last weeks rows as this week`() = runBlocking {
        var now = 1_795_000_000_000L
        val reader = FakeReader().apply { fetch = { ONE_COURSE } }
        val repository = LiveZyRepository(reader, clock = { now })
        repository.load()
        now += 7L * 24 * 60 * 60 * 1000
        reader.fetch = { throw ZyDomainFailure(ZyFailure.Timeout) }
        repository.load()
        assertTrue(repository.week.value.items.isEmpty())
        assertTrue(repository.week.value is ListState.Failed)
    }

    private class FakeReader : ZyReader {
        var id = "first"
        var lastPath = ""
        var invalidations = 0
        val forceCalls = mutableListOf<Boolean>()
        var fetch: suspend () -> String = { throw ZyDomainFailure(ZyFailure.NotLoggedIn) }

        override suspend fun awaitAttached() = true
        override suspend fun ensureSession(force: Boolean): String {
            forceCalls += force
            return id
        }
        override suspend fun fetchText(path: String): String {
            lastPath = path
            return fetch()
        }
        override suspend fun invalidateSession() { invalidations++ }
    }

    private fun failureOf(block: () -> Any): ZyFailure = try {
        block()
        error("Expected a ZyDomainFailure")
    } catch (e: ZyDomainFailure) {
        e.failure
    }

    private companion object {
        const val ONE_COURSE = """{"success":true,"result":{"code":200,"list":[{"day":"2026-11-16","course":[{"id":"1","course_title":"Example"}]}]}}"""
        const val EMPTY_WEEK = """{"success":true,"result":{"code":200,"list":[]}}"""
    }
}
