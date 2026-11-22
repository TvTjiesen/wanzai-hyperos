package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class PortalBroadcastApiTest {
    private fun session() = PortalSession(InMemorySecureStore()).apply { save("synthetic-token", "synthetic-A", "{}") }
    private fun client(session: PortalSession, action: (okhttp3.Interceptor.Chain) -> Response): HzcuClient =
        HzcuClient.create(session, portalBaseUrl = "https://synthetic.invalid",
            epoch = AccountEpoch().apply { activate("synthetic-A") },
            rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1,
            httpClient = OkHttpClient.Builder().addInterceptor(action).build()).apply { renewLog = {} }
    private fun reply(chain: okhttp3.Interceptor.Chain, code: Int = 200) =
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic")
            .body("""{"code":200,"success":true,"rows":[],"data":{"content":"Synthetic"}}""".toResponseBody()).build()

    @Test fun listAndDetailUseCapturedPathsAndSavedFrontendJsonContract() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val transport = client(session()) { chain ->
            val request = chain.request()
            assertEquals("POST", request.method)
            assertEquals("synthetic-token", request.header("token"))
            assertTrue(request.body!!.contentType().toString().startsWith("application/json"))
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            requests += request.url.encodedPath to body
            reply(chain)
        }
        val api = PortalApi(transport)
        api.fetchBroadcasts(); api.fetchBroadcastDetail("a-b_7")
        assertEquals(HzcuEndpoints.PORTAL_BROADCAST_LIST_PATH, requests[0].first)
        val list = Json.parseToJsonElement(requests[0].second).jsonObject
        assertEquals(setOf("pageindex", "pagesize", "token"), list.keys)
        assertEquals("synthetic-token", list["token"]!!.jsonPrimitive.content)
        assertEquals("1", list["pageindex"]!!.jsonPrimitive.content)
        assertEquals("1000", list["pagesize"]!!.jsonPrimitive.content)
        assertEquals(HzcuEndpoints.PORTAL_BROADCAST_DETAIL_PATH + "/a-b_7", requests[1].first)
        val detail = Json.parseToJsonElement(requests[1].second).jsonObject
        assertEquals(setOf("token"), detail.keys)
        assertEquals("synthetic-token", detail["token"]!!.jsonPrimitive.content)
    }

    @Test fun invalidDetailIdentifierCannotDispatch() = runBlocking {
        var calls = 0
        val api = PortalApi(client(session()) { chain -> calls++; reply(chain) })
        for (id in listOf("../7", "7/x", "7?write=true", "", "x".repeat(129))) {
            val failure = runCatching { api.fetchBroadcastDetail(id) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        }
        assertEquals(0, calls)
    }

    @Test fun bothReadPathsRenewOnlyOnceAndUnknownDetailPathNeverRenews() = runBlocking {
        for (path in listOf(HzcuEndpoints.PORTAL_BROADCAST_LIST_PATH, HzcuEndpoints.portalBroadcastDetailPath("7")!!)) {
            var calls = 0; var renews = 0
            val source = session()
            val transport = client(source) { chain -> calls++; reply(chain, if (calls == 1) 401 else 200) }
            transport.renewSessionForOwner = { renews++; source.save("synthetic-new", "synthetic-A", "{}"); true }
            transport.portalPost(path)
            assertEquals(2, calls); assertEquals(1, renews)
        }
        var calls = 0; var renews = 0
        val transport = client(session()) { chain -> calls++; reply(chain, 401) }
        transport.renewSessionForOwner = { renews++; true }
        val failure = runCatching { transport.portalPost(HzcuEndpoints.PORTAL_BROADCAST_DETAIL_PATH + "/7/extra") }.exceptionOrNull()
        assertTrue(failure is HzcuException.LoginExpired)
        assertEquals(1, calls); assertEquals(0, renews)
    }
}
