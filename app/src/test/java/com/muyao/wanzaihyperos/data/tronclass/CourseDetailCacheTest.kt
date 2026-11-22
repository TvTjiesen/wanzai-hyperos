package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.TronclassClient
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Every request ends in an application interceptor: no sockets or school writes. */
class CourseDetailCacheTest {
    private val a = TronCourse(17, "Synthetic A", "", "", "")
    private val b = TronCourse(29, "Synthetic B", "", "", "")
    private class Hold(val path: String) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }
    private class Fixture : Closeable {
        val epoch = AccountEpoch().apply { activate("synthetic-owner-A") }
        var now = 1_000L
        val paths = CopyOnWriteArrayList<String>()
        @Volatile var hold: Hold? = null
        @Volatile var failHomework = false
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            check(request.method == "GET") { "Repository must only read" }
            val path = request.url.encodedPath
            paths += path
            hold?.takeIf { it.path == path }?.let { pause ->
                pause.entered.complete(Unit)
                runBlocking { pause.release.await() }
            }
            val code = if (failHomework && path.endsWith("/homework-activities")) 500 else 200
            val id = path.substringAfter("/api/courses/", "").toIntOrNull() ?: 7
            val body = when {
                path.matches(Regex("/api/courses/[0-9]+")) -> """{"id":$id,"name":"Synthetic $id"}"""
                path == "/api/profile" -> """{"id":7}"""
                path.endsWith("/student-self-score") -> """{"self_score":{"total_score":"0","raw_score":"0"}}"""
                path.endsWith("/score-percentages") -> """{"score_percentage_total":100,"score_percentage_left":0,"unpublished_percentage":100}"""
                path.endsWith("/rollcall-score") -> """{"rollcall_times":0,"rollcall_count":0}"""
                path.endsWith("/rollcalls") -> """{"rollcalls":[]}"""
                path.endsWith("/homework-activities") -> """{"homework_activities":[]}"""
                path.endsWith("/bulletins") -> """{"bulletins":[]}"""
                path.endsWith("/exams") -> """{"exams":[]}"""
                path.endsWith("/topic-categories") -> """{"topic_categories":[]}"""
                else -> error("Unexpected synthetic request: $path")
            }
            check(com.muyao.wanzaihyperos.net.TronRequestPolicy.validContract(
                path, kotlinx.serialization.json.Json.parseToJsonElement(body),
            )) { "Synthetic response violates endpoint contract: $path" }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = TronclassClient(http, { "synthetic-account" }, { "synthetic-access" },
            cachedSessionId = { "synthetic-session" }, limiter = HzcuRateLimiter.forTesting(0, 0),
            identityProvider = epoch::identity)
        val repository = LiveCourseDetailRepository(client, { now }, epoch)
        fun content() = repository.state.value as ListState.Content<TronCourseDetailBundle>
        fun bundle() = content().items.single()
        override fun close() {
            hold?.release?.complete(Unit)
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
        }
    }

    @Test fun `summary and each part publish before completion and hot A B A reuse makes no requests`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                val pause = Hold("/api/course/17/student-self-score")
                f.hold = pause
                val loading = launch { f.repository.load(a) }
                pause.entered.await()
                assertEquals(a, f.bundle().course)
                assertNotNull(f.bundle().detail)
                assertFalse(CourseDetailPart.DETAIL in f.bundle().pendingParts)
                assertTrue(CourseDetailPart.HOMEWORK in f.bundle().pendingParts)
                val during = f.paths.size
                f.repository.load(a)
                f.repository.refresh()
                assertEquals(during, f.paths.size)
                pause.release.complete(Unit)
                loading.join()
                assertTrue(f.bundle().pendingParts.isEmpty())
                assertTrue("Unexpected part errors: ${f.bundle().partErrors}", f.bundle().partErrors.isEmpty())
                assertEquals(1_000L, f.content().meta.updatedAtMillis)
                f.hold = null
                f.repository.load(b)
                val beforeReturn = f.paths.size
                f.repository.load(a)
                assertEquals(a, f.bundle().course)
                assertEquals(beforeReturn, f.paths.size)
                f.repository.refresh()
                assertTrue(f.paths.size > beforeReturn)
            }
        }
    }

    @Test fun `one failed part remains unconfirmed and retry records success time only after all parts settle`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                f.failHomework = true
                f.repository.load(a)
                assertTrue(CourseDetailPart.HOMEWORK in f.bundle().partErrors)
                assertTrue(f.bundle().isUnconfirmed(CourseDetailPart.HOMEWORK))
                assertTrue(f.bundle().pendingParts.isEmpty())
                assertNull(f.content().meta.updatedAtMillis)
                assertTrue(f.content().meta.stale)
                f.failHomework = false
                f.now = 2_000L
                f.repository.refresh()
                assertFalse(f.bundle().isUnconfirmed(CourseDetailPart.HOMEWORK))
                assertTrue("Unexpected part errors: ${f.bundle().partErrors}", f.bundle().partErrors.isEmpty())
                assertEquals(2_000L, f.content().meta.updatedAtMillis)
                assertFalse(f.content().meta.stale)
            }
        }
    }

    @Test fun `cancel retains earlier sections and unfinished parts can retry without a fabricated error`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                val pause = Hold("/api/course/17/student-self-score")
                f.hold = pause
                val loading = launch { f.repository.load(a) }
                pause.entered.await()
                assertNotNull(f.bundle().detail)
                loading.cancel()
                pause.release.complete(Unit)
                loading.join()
                assertFalse(f.content().meta.refreshing)
                assertTrue(f.bundle().pendingParts.isNotEmpty())
                assertTrue("Unexpected part errors: ${f.bundle().partErrors}", f.bundle().partErrors.isEmpty())
                assertNull(f.content().meta.updatedAtMillis)
                f.hold = null
                f.repository.load(a)
                assertTrue(f.bundle().pendingParts.isEmpty())
                assertEquals(1_000L, f.content().meta.updatedAtMillis)
            }
        }
    }

    @Test fun `late old course response cannot replace the new selected course`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                val pause = Hold("/api/courses/17")
                f.hold = pause
                val old = launch { f.repository.load(a) }
                pause.entered.await()
                val fresh = launch { f.repository.load(b) }
                // Loading B must publish its own summary before waiting on the shared gate.
                kotlinx.coroutines.yield()
                assertEquals(b, f.bundle().course)
                assertNull(f.bundle().detail)
                pause.release.complete(Unit)
                old.join()
                fresh.join()
                assertEquals(b, f.bundle().course)
                assertEquals(29, f.bundle().detail?.id)
            }
        }
    }

    @Test fun `account reset clears cached courses and rejects late prior owner publication`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                f.repository.load(a)
                val pause = Hold("/api/courses/29")
                f.hold = pause
                val old = launch { f.repository.load(b) }
                pause.entered.await()
                f.epoch.activate("synthetic-owner-B")
                assertTrue(f.repository.state.value is ListState.FirstLoad)
                pause.release.complete(Unit)
                old.join()
                assertTrue(f.repository.state.value is ListState.FirstLoad)
                f.hold = null
                val before = f.paths.size
                f.repository.load(a)
                assertTrue(f.paths.size > before)
                assertEquals(a, f.bundle().course)
            }
        }
    }
}
