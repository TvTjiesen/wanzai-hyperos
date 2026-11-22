package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.*
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.net.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TronMembersResourcesRepositoryTest {
    private class Rig {
        val epoch = AccountEpoch().apply { activate("synthetic-owner") }
        val calls = CopyOnWriteArrayList<Request>()
        var now = 100L
        @Volatile var reply: (Request) -> Pair<Int, String> = { 200 to "{}" }
        val client = TronclassClient(
            http = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                calls += request
                val (code, body) = reply(request)
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build(),
            accountProvider = { "synthetic-account" }, accessTokenProvider = { "synthetic-access" },
            cachedSessionId = { "synthetic-session" }, limiter = HzcuRateLimiter.forTesting(0,0),
            identityProvider = epoch::identity,
        )
        val members = LiveTronMembersRepository(client, epoch) { now }
        val resources = LiveTronResourcesRepository(client, epoch) { now }
    }
    private fun course(id: Int) = TronCourse(id,"课程$id","","","")
    private fun member(id: Int) = """{"enrollments":[{"id":$id,"roles":["student"],"user":{"id":$id,"name":"成员$id","user_no":"synthetic"}}]}"""
    private fun resourceEnvelope(request: Request, pages: Int = 2): String {
        val page = request.url.queryParameter("page")!!.toInt()
        val conditions = Json.parseToJsonElement(request.url.queryParameter("conditions")!!).jsonObject
        val folder = conditions.getValue("sourceType").jsonPrimitive.content == "MyResourcesFolder"
        val id = page * 10 + conditions.getValue("parentId").jsonPrimitive.int
        val rows = if (folder) "[]" else """[{"id":$id,"name":"文件$id","type":"file"}]"""
        return """{"uploads":$rows,"page":$page,"pages":${if (folder) 0 else pages},"page_size":10}"""
    }

    @Test fun memberGetUsesOfficialBaselineAndKeepsContentOn403() = runBlocking {
        val rig = Rig()
        rig.reply = { 200 to member(7) }
        rig.members.open(course(7))
        val request = rig.calls.single()
        assertEquals("GET", request.method)
        assertEquals("/api/course/7/enrollments", request.url.encodedPath)
        assertEquals("id,roles,aliases,total_score,published,instructor_score_time,user(id,name,user_no,avatar_small_url,comment,department,grade,klass)", request.url.queryParameter("fields"))
        rig.reply = { 403 to "{}" }; rig.now = 200
        rig.members.refresh()
        assertEquals(7, rig.members.state.value.items.single().userId)
        assertEquals(100L, rig.members.state.value.metaOrNull?.updatedAtMillis)
        assertTrue(rig.members.state.value.metaOrNull?.lastRefreshError is HzcuException.HttpStatus)
        assertFalse(rig.members.state.value.metaOrNull!!.refreshing)
        val first = Rig().apply { reply = { 403 to "{}" } }
        first.members.open(course(1))
        assertTrue(first.members.state.value is ListState.Failed)
        assertEquals(1, first.calls.size)
    }

    @Test fun explicitEmptyIsCachedButMalformedIsFailure() = runBlocking {
        val rig = Rig()
        rig.reply = { 200 to """{"enrollments":[]}""" }
        rig.members.open(course(1))
        assertTrue(rig.members.state.value is ListState.Empty)
        rig.reply = { 200 to "{}" }
        rig.members.open(course(2))
        assertTrue(rig.members.state.value is ListState.Failed)
        rig.members.open(course(1))
        assertTrue(rig.members.state.value is ListState.Empty)
        assertNotNull(rig.members.state.value.metaOrNull?.lastRefreshError)
    }

    @Test fun resourcesUseTwoSequentialQueriesAndRefreshWholeLoadedWindowAtomically() = runBlocking {
        val rig = Rig().apply { reply = { 200 to resourceEnvelope(it) } }
        rig.resources.open(TronResourcesQuery(3,"中文 & x"))
        assertEquals(2, rig.calls.size)
        assertTrue(rig.calls.all { it.method == "GET" && it.url.encodedPath == "/api/user/resources" && it.url.queryParameter("page_size") == "10" })
        val conditions = rig.calls.map { Json.parseToJsonElement(it.url.queryParameter("conditions")!!).jsonObject }
        assertEquals(listOf("MyResourcesFolder","MyResourcesFile"), conditions.map { it.getValue("sourceType").jsonPrimitive.content })
        assertTrue(conditions.all { it.getValue("keyword").jsonPrimitive.content == "中文 & x" })
        rig.resources.loadMore()
        val before = rig.resources.state.value
        assertEquals(listOf(13,23), before.items.map { it.id })
        assertEquals(2, before.metaOrNull?.page)
        rig.now = 200
        rig.reply = { request -> if (request.url.queryParameter("page") == "2") 503 to "{}" else 200 to resourceEnvelope(request) }
        rig.resources.refresh()
        assertEquals(before.items, rig.resources.state.value.items)
        assertEquals(100L, rig.resources.state.value.metaOrNull?.updatedAtMillis)
        assertEquals(2, rig.resources.state.value.metaOrNull?.page)
        assertTrue(rig.resources.state.value.metaOrNull?.stale == true)
        rig.reply = { 200 to resourceEnvelope(it, pages = 1) }
        rig.resources.refresh()
        assertEquals(listOf(13), rig.resources.state.value.items.map { it.id })
        assertEquals(1, rig.resources.state.value.metaOrNull?.page)
        assertFalse(rig.resources.state.value.metaOrNull!!.hasMore)
    }

    @Test fun resourceQuerySwitchRestoresOldWindowBeforePendingRefreshAndCoalesces() = runBlocking {
        val rig = Rig().apply { reply = { 200 to resourceEnvelope(it,1) } }
        rig.resources.open(TronResourcesQuery(1))
        val cached = rig.resources.state.value.items
        rig.resources.open(TronResourcesQuery(2))
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        rig.reply = { request ->
            entered.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
            200 to resourceEnvelope(request,1)
        }
        val read = launch(Dispatchers.Default) { rig.resources.open(TronResourcesQuery(1)) }
        try {
            withTimeout(3_000) { entered.await() }
            assertEquals(cached, rig.resources.state.value.items)
            val count = rig.calls.size
            rig.resources.refresh(); rig.resources.loadMore()
            assertEquals(count, rig.calls.size)
        } finally { release.countDown(); read.join() }
        assertFalse(rig.resources.state.value.metaOrNull!!.refreshing)
    }

    @Test fun cancellationReleasesFlagsAndAllowsSameQueryRetry() = runBlocking {
        val rig = Rig().apply { reply = { 200 to resourceEnvelope(it,1) } }
        rig.resources.open()
        val old = rig.resources.state.value.items
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        rig.reply = { request -> entered.complete(Unit); check(release.await(5,TimeUnit.SECONDS)); 200 to resourceEnvelope(request,1) }
        val read = launch(Dispatchers.Default) { rig.resources.refresh() }
        try { withTimeout(3_000) { entered.await() }; read.cancel() }
        finally { release.countDown(); read.join() }
        assertEquals(old, rig.resources.state.value.items)
        assertFalse(rig.resources.state.value.metaOrNull!!.refreshing)
        rig.reply = { 200 to resourceEnvelope(it,1) }; rig.now = 300
        rig.resources.refresh()
        assertEquals(300L, rig.resources.state.value.metaOrNull?.updatedAtMillis)
    }

    @Test fun lateResourceQueryCannotReplaceCurrentFolder() = runBlocking {
        val rig = Rig()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        rig.reply = { request ->
            val query = Json.parseToJsonElement(request.url.queryParameter("conditions")!!).jsonObject
            if (query.getValue("parentId").jsonPrimitive.int == 1) {
                entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS))
            }
            200 to resourceEnvelope(request,1)
        }
        val old = launch(Dispatchers.Default) { rig.resources.open(TronResourcesQuery(1)) }
        try {
            withTimeout(3_000) { entered.await() }
            val current = launch(start = CoroutineStart.UNDISPATCHED) { rig.resources.open(TronResourcesQuery(2)) }
            assertEquals(2, rig.resources.query.value.folderId)
            assertTrue(rig.resources.state.value is ListState.FirstLoad)
            release.countDown()
            current.join()
        } finally { release.countDown(); old.join() }
        assertEquals(2, rig.resources.query.value.folderId)
        assertEquals(12, rig.resources.state.value.items.single().id)
    }

    @Test fun resourceOwnerResetAndForbiddenDoNotExposeAnotherOwnerOrFakeEmpty() = runBlocking {
        val rig = Rig().apply { reply = { 200 to resourceEnvelope(it,1) } }
        rig.resources.open(TronResourcesQuery(1))
        val old = rig.resources.state.value.items
        rig.reply = { 403 to "{}" }
        rig.resources.refresh()
        assertEquals(old, rig.resources.state.value.items)
        assertTrue(rig.resources.state.value.metaOrNull?.lastRefreshError is HzcuException.HttpStatus)
        rig.epoch.activate("another-synthetic-owner")
        assertTrue(rig.resources.state.value is ListState.FirstLoad)
        rig.resources.open(TronResourcesQuery(1))
        assertTrue(rig.resources.state.value is ListState.Failed)
        assertTrue(rig.resources.state.value.items.isEmpty())
    }

    @Test fun accountReplacementClearsMemberCacheAndRejectsLateResponse() = runBlocking {
        val rig = Rig().apply { reply = { 200 to member(1) } }
        rig.members.open(course(1))
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        rig.reply = { entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS)); 200 to member(1) }
        val read = launch(Dispatchers.Default) { rig.members.refresh() }
        try { withTimeout(3_000) { entered.await() }; rig.epoch.activate("another-synthetic-owner") }
        finally { release.countDown(); read.join() }
        assertTrue(rig.members.state.value is ListState.FirstLoad)
        assertNull(rig.members.course.value)
        rig.reply = { 200 to member(2) }
        rig.members.open(course(1))
        assertEquals(2, rig.members.state.value.items.single().userId)
    }
}
