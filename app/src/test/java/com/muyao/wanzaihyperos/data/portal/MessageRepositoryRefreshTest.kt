package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.net.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MessageRepositoryRefreshTest {
    private class Credentials : PortalCredentials {
        override fun accessToken() = "synthetic-token"
        override fun userCode() = "synthetic-user"
        override fun portalCookiesJson(): String? = null
        override fun savePortalCookies(json: String) = Unit
        override fun saveAccessToken(accessToken: String, userCode: String?) = Unit
        override fun savePortalSession(accessToken: String, userCode: String?, portalCookiesJson: String) = Unit
        override fun clear() = Unit
    }
    private fun repository(server: TestHttpServer) = LivePortalMessageRepository(PortalApi(HzcuClient.create(
        credentials = Credentials(), portalBaseUrl = server.baseUrl,
        rateLimiter = HzcuRateLimiter.forTesting(0, 0), maxAttempts = 1)), pageSize = 2)
    private fun groups(name: String, unread: Int) = """{
        "code": 200, "success": true, "data": [{"messagegroup": "$name", "noread": $unread}]
    }"""
    private fun rows(id: String, date: String) = """{
        "code": 200, "success": true, "data": null,
        "rows": [{"id": "$id", "title": "Synthetic notice", "createdate": "$date", "isread": false}]
    }"""
    @Test fun initialGroupsLoadRowsAndRemovedGroupReloadsWithoutWrites() = runTest {
        val server = TestHttpServer()
        try {
            val repo = repository(server)
            server.enqueue(body = groups("old-group", 280))
            server.enqueue(body = rows("old", "2025-11-06 10:00:00"))
            repo.refreshGroups()
            assertEquals("old-group", repo.selectedGroup.value?.name)
            assertEquals("old", (repo.messages.value as ListState.Content).items.single().id)
            server.enqueue(body = groups("new-group", 281))
            server.enqueue(body = rows("new", "2026-11-19 10:00:00"))
            repo.refreshGroups()
            assertEquals("new-group", repo.selectedGroup.value?.name)
            assertEquals("new", (repo.messages.value as ListState.Content).items.single().id)
            server.enqueue(body = groups("new-group", 282))
            repo.refreshGroups()
            assertEquals(282, repo.selectedGroup.value?.unread)
            assertEquals(5, server.requestCount)
            assertTrue(server.requests.none { "setmessageread" in it.path || "setallmessageread" in it.path })
            assertTrue(server.requests.filter { "listmessage" in it.path && "group" !in it.path }.all {
                it.contentType?.contains("application/json") == true && "messagegroup" in it.body
            })
        } finally { server.stop() }
    }
    @Test fun paginationSortsLoadedRowsAndFailedRefreshRetainsThem() = runTest {
        val server = TestHttpServer()
        try {
            val repo = repository(server)
            server.enqueue(body = groups("same", 280))
            server.enqueue(body = """{
                "code": 200, "success": true, "rows": [
                    {"id": "old", "title": "Old", "createdate": "2025-11-06"},
                    {"id": "middle", "title": "Middle", "createdate": "2026-01-01"}
                ], "total": 1
            }""")
            repo.refreshGroups()
            server.enqueue(body = rows("new", "2026-11-19"))
            repo.loadMore()
            val content = repo.messages.value as ListState.Content
            assertEquals(listOf("new", "middle", "old"), content.items.map { it.id })
            assertEquals("2025-11-06", content.items.last().createdDate)
            server.enqueue(500, "unavailable")
            repo.refreshKeepingContent()
            val failed = repo.messages.value as ListState.Content
            assertEquals(content.items, failed.items)
            assertTrue(failed.meta.stale)
        } finally { server.stop() }
    }
}
