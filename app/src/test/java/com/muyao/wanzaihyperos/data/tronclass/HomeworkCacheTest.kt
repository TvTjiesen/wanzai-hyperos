package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import com.muyao.wanzaihyperos.net.TronRequestPolicy
import com.muyao.wanzaihyperos.net.TronclassClient
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** All GETs terminate in an application interceptor; no sockets or school writes. */
class HomeworkCacheTest {
    private class Hold(val path: String) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class Fixture : Closeable {
        val epoch = AccountEpoch().apply { activate("synthetic-homework-owner-A") }
        var now = 1_000L
        val paths = CopyOnWriteArrayList<String>()
        @Volatile var hold: Hold? = null
        @Volatile var failCourse = 0
        @Volatile var emptyCourse = 0
        @Volatile var authPath: String? = null
        @Volatile var failPath: String? = null
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            check(request.method == "GET") { "Unexpected school write" }
            val path = request.url.encodedPath
            paths += path
            hold?.takeIf { it.path == path }?.let { pause ->
                pause.entered.complete(Unit)
                runBlocking { pause.release.await() }
            }
            val course = path.substringAfter("/api/courses/", "").substringBefore('/').toIntOrNull()
            val code = when {
                path == authPath -> 401
                path == failPath || (course == failCourse && path.endsWith("/homework-activities")) -> 500
                else -> 200
            }
            val body = when {
                path.endsWith("/homework-activities") -> {
                    check(course != null)
                    if (course == emptyCourse) """{"homework_activities":[]}"""
                    else """{"homework_activities":[{"id":${course * 100 + 1},"title":"Synthetic first"},{"id":${course * 100 + 2},"title":"Synthetic second"}]}"""
                }
                path.endsWith("/submission-status") -> """{"homework_activities":[]}"""
                path == "/api/profile" -> """{"id":7}"""
                path.endsWith("/submission_list") -> """{"list":[]}"""
                else -> error("Unexpected synthetic request: $path")
            }
            check(TronRequestPolicy.validContract(path, Json.parseToJsonElement(body))) {
                "Synthetic response violates contract: $path"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("synthetic")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = TronclassClient(http, { "synthetic-account" },
            { if (authPath == null) "synthetic-access" else null },
            cachedSessionId = { "synthetic-session" }, limiter = HzcuRateLimiter.forTesting(0, 0),
            identityProvider = epoch::identity)
        val list = LiveHomeworkRepository(client, { now }, epoch)
        val detail = LiveHomeworkDetailRepository(client, { now }, epoch)
        fun rows() = list.state.value as ListState.Content<ClassHomework>
        fun details() = detail.state.value as ListState.Content<HomeworkDetail>
        fun item() = details().items.single()
        override fun close() {
            hold?.release?.complete(Unit)
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
        }
    }

    @Test fun `list initial duplicates merge and same course reload keeps content`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                val pause = Hold("/api/courses/17/homework-activities")
                f.hold = pause
                val initial = launch { f.list.load(17, "A") }
                pause.entered.await()
                val count = f.paths.size
                f.list.load(17, "Renamed A")
                f.list.refresh()
                assertEquals(count, f.paths.size)
                pause.release.complete(Unit)
                initial.join()
                assertEquals(1701, f.rows().items.first().homework.id)
                assertEquals("Renamed A", f.list.courseName.value)
                val again = Hold(pause.path)
                f.hold = again
                val reentry = launch { f.list.load(17, "A") }
                again.entered.await()
                assertEquals(1701, f.rows().items.first().homework.id)
                assertTrue(f.rows().meta.refreshing)
                assertEquals(1_000L, f.rows().meta.updatedAtMillis)
                again.release.complete(Unit)
                reentry.join()
                assertFalse(f.rows().meta.refreshing)
            }
        }
    }

    @Test fun `list A B A restores its own snapshot and failed refresh keeps success time`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                f.list.load(17, "A")
                f.now = 2_000L
                f.list.load(29, "B")
                val pause = Hold("/api/courses/17/homework-activities")
                f.hold = pause
                f.failCourse = 17
                val returning = launch { f.list.load(17, "A") }
                pause.entered.await()
                assertEquals(17, f.list.currentCourseId)
                assertEquals(1701, f.rows().items.first().homework.id)
                assertEquals(1_000L, f.rows().meta.updatedAtMillis)
                pause.release.complete(Unit)
                returning.join()
                assertEquals(1701, f.rows().items.first().homework.id)
                assertTrue(f.rows().meta.stale)
                assertNotNull(f.rows().meta.lastRefreshError)
                assertEquals(1_000L, f.rows().meta.updatedAtMillis)
            }
        }
    }

    @Test fun `list confirmed empty survives switching and cancelled refresh can retry`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                f.emptyCourse = 17
                f.list.load(17, "Empty A")
                assertTrue(f.list.state.value is ListState.Empty)
                f.list.load(29, "B")
                val pause = Hold("/api/courses/17/homework-activities")
                f.hold = pause
                val returning = launch { f.list.load(17, "Empty A") }
                pause.entered.await()
                assertTrue(f.list.state.value is ListState.Empty)
                returning.cancel()
                pause.release.complete(Unit)
                returning.join()
                assertFalse(f.list.state.value.metaOrNull!!.refreshing)
                assertNull(f.list.state.value.metaOrNull!!.lastRefreshError)
                f.hold = null
                f.failCourse = 17
                f.list.refresh()
                assertTrue(f.list.state.value is ListState.Empty)
                assertTrue(f.list.state.value.metaOrNull!!.stale)
                assertEquals(1_000L, f.list.state.value.metaOrNull!!.updatedAtMillis)
                f.failCourse = 0
                f.now = 3_000L
                f.list.refresh()
                assertEquals(3_000L, f.list.state.value.metaOrNull!!.updatedAtMillis)
            }
        }
    }

    @Test fun `late list response cannot replace selection or contaminate its cache`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                f.list.load(17, "A")
                val pause = Hold("/api/courses/17/homework-activities")
                f.hold = pause
                val old = launch { f.list.refresh() }
                pause.entered.await()
                val fresh = launch { f.list.load(29, "B") }
                yield()
                assertEquals(29, f.list.currentCourseId)
                assertTrue(f.list.state.value is ListState.FirstLoad)
                pause.release.complete(Unit)
                old.join()
                fresh.join()
                assertEquals(2901, f.rows().items.first().homework.id)
                f.hold = null
                f.failCourse = 17
                f.list.load(17, "A")
                assertEquals(1701, f.rows().items.first().homework.id)
            }
        }
    }

    @Test fun `detail duplicates merge and cache includes course and requested homework`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                val pause = Hold("/api/courses/17/homework-activities")
                f.hold = pause
                val initial = launch { f.detail.load(17, 1701, "A", "First") }
                pause.entered.await()
                val count = f.paths.size
                f.detail.load(17, 1701, "A", "First")
                f.detail.refresh()
                assertEquals(count, f.paths.size)
                pause.release.complete(Unit)
                initial.join()
                assertEquals(1701, f.item().homework?.id)
                f.hold = null
                f.detail.load(17, 1702, "A", "Second")
                assertEquals(1702, f.item().homework?.id)
                f.detail.load(29, 1701, "B", "")
                assertEquals(29, f.item().courseId)
                assertNull(f.item().homework)
                val again = Hold(pause.path)
                f.hold = again
                val returning = launch { f.detail.load(17, 1701, "A", "First") }
                again.entered.await()
                assertEquals(17, f.item().courseId)
                assertEquals(1701, f.item().homework?.id)
                assertTrue(f.details().meta.refreshing)
                again.release.complete(Unit)
                returning.join()
            }
        }
    }

    @Test fun `detail failed refresh retains prior requirement and success time`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                f.detail.load(17, 1701, "A", "First")
                f.failCourse = 17
                f.now = 2_000L
                f.detail.refresh()
                assertEquals(1701, f.item().homework?.id)
                assertEquals(1_000L, f.details().meta.updatedAtMillis)
                assertTrue(f.details().meta.stale)
                assertNotNull(f.details().meta.lastRefreshError)
                assertFalse(f.details().meta.refreshing)
                f.failCourse = 0
                f.detail.refresh()
                assertEquals(2_000L, f.details().meta.updatedAtMillis)
                assertFalse(f.details().meta.stale)
            }
        }
    }

    @Test fun `cancelled detail initial read and content refresh both release duplicate gate`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                val first = Hold("/api/courses/17/homework-activities")
                f.hold = first
                val initial = launch { f.detail.load(17, 1701, "A", "First") }
                first.entered.await()
                initial.cancel()
                first.release.complete(Unit)
                initial.join()
                f.hold = null
                f.detail.load(17, 1701, "A", "First")
                assertEquals(1701, f.item().homework?.id)
                val second = Hold(first.path)
                f.hold = second
                val refresh = launch { f.detail.refresh() }
                second.entered.await()
                refresh.cancel()
                second.release.complete(Unit)
                refresh.join()
                assertEquals(1701, f.item().homework?.id)
                assertFalse(f.details().meta.refreshing)
                assertNull(f.details().meta.lastRefreshError)
                assertEquals(1_000L, f.details().meta.updatedAtMillis)
                f.hold = null
                f.detail.load(17, 1701, "A", "First")
                assertFalse(f.details().meta.refreshing)
            }
        }
    }

    @Test fun `late detail response cannot replace different homework in same course`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                f.detail.load(17, 1701, "A", "First")
                val pause = Hold("/api/courses/17/homework-activities")
                f.hold = pause
                val old = launch { f.detail.refresh() }
                pause.entered.await()
                val fresh = launch { f.detail.load(17, 1702, "A", "Second") }
                yield()
                assertTrue(f.detail.state.value is ListState.FirstLoad)
                pause.release.complete(Unit)
                old.join()
                fresh.join()
                assertEquals(1702, f.item().homework?.id)
            }
        }
    }

    @Test fun `list auth failure retains content or empty and cold load expires`() = runBlocking {
        withTimeout(10_000) {
            val authPaths = listOf(
                "/api/courses/17/homework-activities",
                "/api/course/17/homework/submission-status",
            )
            for (path in authPaths) {
                for (empty in listOf(false, true)) Fixture().use { f ->
                    if (empty) f.emptyCourse = 17
                    f.list.load(17, "A")
                    val before = f.list.state.value
                    f.now = 2_000L
                    f.authPath = path
                    f.list.refresh()
                    val after = f.list.state.value
                    if (before is ListState.Content) assertEquals(before.items, f.rows().items)
                    else assertTrue(after is ListState.Empty)
                    val meta = after.metaOrNull!!
                    assertEquals(1_000L, meta.updatedAtMillis)
                    assertTrue(meta.stale)
                    assertFalse(meta.refreshing)
                    assertTrue(meta.lastRefreshError is HzcuException.LoginExpired)
                    assertEquals("学在城院登录状态已失效，请重新登录", meta.lastRefreshError?.message)
                    f.authPath = null
                    f.list.refresh()
                    assertEquals(2_000L, f.list.state.value.metaOrNull!!.updatedAtMillis)
                    assertNull(f.list.state.value.metaOrNull!!.lastRefreshError)
                }
                Fixture().use { f ->
                    f.authPath = path
                    f.list.load(17, "A")
                    assertTrue(f.list.state.value is ListState.SessionExpired)
                }
            }
        }
    }

    @Test fun `detail auth at each subrequest retains confirmed bundle and cold load expires`() = runBlocking {
        withTimeout(10_000) {
            val authPaths = listOf(
                "/api/courses/17/homework-activities",
                "/api/profile",
                "/api/activities/1701/students/7/submission_list",
                "/api/course/17/homework/submission-status",
            )
            for (path in authPaths) {
                Fixture().use { f ->
                    // A transient profile failure leaves a confirmed requirement but no cached user id.
                    if (path == "/api/profile") f.failPath = path
                    f.detail.load(17, 1701, "A", "First")
                    f.failPath = null
                    val before = f.details()
                    f.now = 2_000L
                    f.authPath = path
                    f.detail.refresh()
                    val after = f.details()
                    assertEquals(before.items, after.items)
                    assertEquals(1_000L, after.meta.updatedAtMillis)
                    assertTrue(after.meta.stale)
                    assertFalse(after.meta.refreshing)
                    assertTrue(after.meta.lastRefreshError is HzcuException.LoginExpired)
                    assertEquals("学在城院登录状态已失效，请重新登录", after.meta.lastRefreshError?.message)
                    assertEquals(path, f.paths.last())
                    f.authPath = null
                    f.detail.refresh()
                    assertEquals(2_000L, f.details().meta.updatedAtMillis)
                    assertNull(f.details().meta.lastRefreshError)
                }
                Fixture().use { f ->
                    f.authPath = path
                    f.detail.load(17, 1701, "A", "First")
                    assertTrue(f.detail.state.value is ListState.SessionExpired)
                    assertEquals(path, f.paths.last())
                }
            }
        }
    }

    @Test fun `owner reset clears both repositories caches and rejects old detail response`() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { f ->
                f.list.load(17, "A")
                f.list.load(29, "B")
                f.detail.load(17, 1701, "A", "First")
                val pause = Hold("/api/courses/29/homework-activities")
                f.hold = pause
                val old = launch { f.detail.load(29, 2901, "B", "First") }
                pause.entered.await()
                f.epoch.activate("synthetic-homework-owner-B")
                assertTrue(f.list.state.value is ListState.FirstLoad)
                assertTrue(f.detail.state.value is ListState.FirstLoad)
                assertEquals(0, f.list.currentCourseId)
                assertEquals("", f.list.courseName.value)
                pause.release.complete(Unit)
                old.join()
                assertTrue(f.detail.state.value is ListState.FirstLoad)
                f.hold = null
                val newList = Hold("/api/courses/17/homework-activities")
                f.hold = newList
                val loadingList = launch { f.list.load(17, "A") }
                newList.entered.await()
                assertTrue(f.list.state.value is ListState.FirstLoad)
                newList.release.complete(Unit)
                loadingList.join()
                val newDetail = Hold(newList.path)
                f.hold = newDetail
                val loadingDetail = launch { f.detail.load(17, 1701, "A", "First") }
                newDetail.entered.await()
                assertTrue(f.detail.state.value is ListState.FirstLoad)
                newDetail.release.complete(Unit)
                loadingDetail.join()
                assertEquals(1701, f.item().homework?.id)
            }
        }
    }
}
