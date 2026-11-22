package com.muyao.wanzaihyperos.data.szty

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.SztyClient
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.SztySession
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SztyCachedSafetyTest {
    private class Rig(snapshotDir: java.io.File? = null) : AutoCloseable {
        val epoch = AccountEpoch().apply { activate("synthetic-owner") }
        val session = SztySession(InMemorySecureStore()).apply { save("synthetic-sport", 7, 1L) }
        var now = 123L
        var term = 15
        var classId = 7
        val failed = mutableSetOf<String>()
        var expired = false
        var empty = false
        private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            val body = when {
                expired -> "{\"statusCode\":-3,\"message\":\"synthetic expired\",\"obj\":null}"
                failed.any { path.endsWith(it) } -> "{\"statusCode\":-1,\"message\":\"synthetic failure\",\"obj\":null}"
                empty -> ok("null")
                path.endsWith("getMyGrade") -> ok("[{\"year\":2026,\"scoreData\":[]}]")
                path.endsWith("getSemesterExerciseList") -> ok("[{\"info\":{\"eventName\":\"跑步\"},\"effectiveDistance\":3.5}]")
                path.endsWith("getAssessmentYears") -> ok("[{\"termId\":$term}]")
                path.endsWith("semesterStuEduClass") -> ok("[{\"classId\":$classId,\"name\":\"Synthetic class\"}]")
                path.endsWith("stuAttendanceList") -> ok("[{\"name\":\"Synthetic lesson\",\"signInType\":\"已签到\"}]")
                else -> ok("{\"normal\":4,\"leave\":2}")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repo = LiveSztyRepository(
            SztyClient(baseUrl = "https://synthetic.invalid", httpClient = http, sleeper = {}, clock = { now }),
            session, clock = { now }, snapshotDir = snapshotDir, epoch = epoch,
        )
        fun content() = repo.state.value as ListState.Content
        fun snapshot() = content().items.single()
        override fun close() {
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
        }
        companion object { private fun ok(payload: String) = "{\"statusCode\":1,\"obj\":$payload}" }
    }

    @Test(timeout = 8000) fun `failed dependency preserves same term and class attendance without a disk snapshot`() = runBlocking {
        for (dependency in listOf("getAssessmentYears", "semesterStuEduClass")) Rig().use { rig ->
            rig.repo.refresh()
            val before = rig.snapshot()
            assertEquals(1, before.attendance.size)
            rig.now = 999L
            rig.failed += dependency
            rig.repo.refresh()
            val after = rig.snapshot()
            assertEquals(before.peClass, after.peClass)
            assertEquals(before.attendance, after.attendance)
            assertEquals(before.attendanceSummary, after.attendanceSummary)
            assertEquals(123L, rig.content().meta.updatedAtMillis)
            assertEquals(123L, rig.session.lastSyncMillis())
            assertTrue(rig.content().meta.stale)
            assertNotNull(rig.content().meta.lastRefreshError)
            assertFalse(rig.content().meta.refreshing)
        }
    }

    @Test(timeout = 8000) fun `same term different class never borrows old attendance after a failed query`() = runBlocking {
        Rig().use { rig ->
            rig.repo.refresh()
            assertEquals(7, rig.snapshot().peClass!!.classId)
            rig.now = 999L
            rig.classId = 8
            rig.failed += listOf("stuAttendanceList", "getSignClassHistory")
            rig.repo.refresh()
            val after = rig.snapshot()
            assertEquals(8, after.peClass!!.classId)
            assertTrue(after.attendance.isEmpty())
            assertNull(after.attendanceSummary)
            assertTrue(rig.content().meta.stale)
            assertEquals(123L, rig.content().meta.updatedAtMillis)
            assertEquals(123L, rig.session.lastSyncMillis())
        }
    }

    @Test(timeout = 8000) fun `new term cannot reuse old class when its lookup fails`() = runBlocking {
        Rig().use { rig ->
            rig.repo.refresh()
            rig.term = 16
            rig.failed += "semesterStuEduClass"
            rig.repo.refresh()
            assertNull(rig.snapshot().peClass)
            assertTrue(rig.snapshot().attendance.isEmpty())
            assertNull(rig.snapshot().attendanceSummary)
        }
    }

    @Test(timeout = 8000) fun `successful empty is not a failed query and clears old results`() = runBlocking {
        Rig().use { rig ->
            rig.repo.refresh()
            rig.empty = true
            rig.now = 999L
            rig.repo.refresh()
            val after = rig.repo.state.value as ListState.Empty
            assertEquals(999L, after.meta.updatedAtMillis)
            assertFalse(after.meta.stale)
            assertNull(after.meta.lastRefreshError)
        }
    }

    @Test(timeout = 8000) fun `same owner login expiry retains both settled states but cold failure stays expired`() = runBlocking {
        for (empty in listOf(false, true)) Rig().use { rig ->
            rig.empty = empty
            rig.repo.refresh()
            val before = rig.repo.state.value
            rig.expired = true
            rig.now = 999L
            rig.repo.refresh()
            val after = rig.repo.state.value
            assertEquals(before::class, after::class)
            if (before is ListState.Content) assertEquals(before.items, (after as ListState.Content).items)
            val meta = requireNotNull(after.metaOrNull)
            assertEquals(123L, meta.updatedAtMillis)
            assertTrue(meta.stale)
            assertTrue(meta.lastRefreshError is HzcuException.LoginExpired)
            assertFalse(meta.refreshing)
            assertEquals(123L, rig.session.lastSyncMillis())
        }
        Rig().use { rig ->
            rig.expired = true
            rig.repo.refresh()
            assertSame(ListState.SessionExpired, rig.repo.state.value)
        }
    }

    @Test(timeout = 8000) fun `partial failure leaves disk bytes unchanged and confirmed empty survives cold restart`() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("synthetic-szty-cache").toFile()
        try {
            Rig(dir).use { rig ->
                rig.repo.refresh()
                val file = java.io.File(dir, "szty_snapshot.json")
                val before = file.readBytes()
                val beforeAt = file.lastModified()
                rig.now = 999L
                rig.failed += "semesterStuEduClass"
                rig.repo.refresh()
                assertArrayEquals(before, file.readBytes())
                assertEquals(beforeAt, file.lastModified())
                rig.failed.clear()
                rig.empty = true
                rig.repo.refresh()
            }
            Rig(dir).use { next ->
                val seeded = next.repo.state.value as ListState.Empty
                assertEquals(999L, seeded.meta.updatedAtMillis)
                assertTrue(seeded.meta.stale)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test(timeout = 8000) fun `owner reset discards in memory fallback before next partial request`() = runBlocking {
        Rig().use { rig ->
            rig.repo.refresh()
            rig.epoch.activate("synthetic-other-owner")
            assertSame(ListState.FirstLoad, rig.repo.state.value)
            rig.failed += "semesterStuEduClass"
            rig.repo.refresh()
            assertNull(rig.snapshot().peClass)
            assertTrue(rig.snapshot().attendance.isEmpty())
            assertNull(rig.snapshot().attendanceSummary)
        }
    }
}
