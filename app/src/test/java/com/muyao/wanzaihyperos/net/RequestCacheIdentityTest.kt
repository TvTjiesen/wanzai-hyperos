package com.muyao.wanzaihyperos.net

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest

class RequestCacheIdentityTest {
    @get:Rule val temp = TemporaryFolder()

    private fun legacyEntry(path: String, body: String, modifiedAt: Long): File {
        val root = temp.root.resolve("http_cache").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-1").digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$key.json").apply {
            writeText(body)
            assertTrue(setLastModified(modifiedAt))
        }
    }

    @Test fun publicEntryAccessAlwaysRequiresSourceAndIdentity() {
        val expected = mapOf(
            "get" to listOf(String::class.java, RequestIdentity::class.java, String::class.java, java.lang.Long.TYPE),
            "put" to listOf(String::class.java, RequestIdentity::class.java, String::class.java, String::class.java),
            "invalidate" to listOf(String::class.java, RequestIdentity::class.java, String::class.java),
        )
        val methods = ResponseCache::class.java.methods.filter { it.name in expected }
        assertEquals(expected.keys, methods.map { it.name }.toSet())
        expected.forEach { (name, parameters) ->
            val matches = methods.filter { it.name == name }
            assertEquals("Only identity-scoped $name may be public", 1, matches.size)
            assertEquals(parameters, matches.single().parameterTypes.toList())
        }
        listOf("getEntry", "putEntry", "invalidateEntry").forEach { name ->
            val primitive = ResponseCache::class.java.declaredMethods.single { it.name == name }
            assertTrue("Entry primitive $name must remain private", Modifier.isPrivate(primitive.modifiers))
        }
    }

    @Test fun partitionsSourceOwnerGenerationAndIgnoresLegacyEntries() {
        var now = 10_000L
        val cache = ResponseCache(temp.root) { now }
        val a = RequestIdentity("synthetic-owner-a", 1)
        val b = RequestIdentity("synthetic-owner-b", 1)
        val path = "/api/todos?page=1"
        val legacy = legacyEntry(path, "legacy", now)
        assertTrue(legacy.isFile)
        assertEquals(1, cache.entries())
        assertNull(cache.get("tronclass", a, path, 100))
        cache.put("tronclass", a, path, "A")
        assertEquals("A", cache.get("tronclass", a, path, 100))
        assertNull(cache.get("portal", a, path, 100))
        assertNull(cache.get("tronclass", b, path, 100))
        assertNull(cache.get("tronclass", RequestIdentity(a.ownerKey, 2), path, 100))
        assertNull(cache.get("tronclass", a, "/api/todos?page=2", 100))
        now += 101
        assertNull(cache.get("tronclass", a, path, 100))
    }

    @Test fun scopedInvalidationLeavesOtherSourcesOwnersGenerationsAndQueriesUntouched() {
        val cache = ResponseCache(temp.root) { 10_000L }
        val a = RequestIdentity("synthetic-owner-a", 1)
        val b = RequestIdentity("synthetic-owner-b", 1)
        val next = RequestIdentity(a.ownerKey, 2)
        val path = "/api/todos?page=1"
        val otherPath = "/api/todos?page=2"
        cache.put("tronclass", a, path, "A")
        cache.put("portal", a, path, "portal")
        cache.put("tronclass", b, path, "B")
        cache.put("tronclass", next, path, "next")
        cache.put("tronclass", a, otherPath, "query")
        cache.invalidate("tronclass", a, path)
        assertNull(cache.get("tronclass", a, path, 100))
        assertEquals("portal", cache.get("portal", a, path, 100))
        assertEquals("B", cache.get("tronclass", b, path, 100))
        assertEquals("next", cache.get("tronclass", next, path, 100))
        assertEquals("query", cache.get("tronclass", a, otherPath, 100))
        assertEquals(4, cache.entries())
        assertEquals(4, temp.root.resolve("http_cache").listFiles().orEmpty().count { it.name.endsWith(".path") })
    }

    @Test fun scopedTtlKeepsExactBoundaryButRejectsExpiredOrFutureEntries() {
        var now = 10_000L
        val cache = ResponseCache(temp.root) { now }
        val id = RequestIdentity("synthetic-owner", 1)
        val path = "/api/todos"
        cache.put("tronclass", id, path, "fresh")
        now += 100
        assertEquals("fresh", cache.get("tronclass", id, path, 100))
        now++
        assertNull(cache.get("tronclass", id, path, 100))
        now = 9_999L
        assertNull(cache.get("tronclass", id, path, 100))
    }

    @Test fun scopedDomainsAndClearKeepLegacyFilesVisibleButNeverReadableAsResponses() {
        val cache = ResponseCache(temp.root) { 10_000L }
        val a = RequestIdentity("synthetic-owner-a", 1)
        val b = RequestIdentity("synthetic-owner-b", 2)
        val legacy = legacyEntry("/api/v2/todo/legacy", "old", 10_000L)
        cache.put("tronclass", a, "/api/v2/todo/items?page=1", "one")
        cache.put("portal", b, "/api/v2/todo/items?page=2", "two")
        cache.put("tronclass", a, "/api/v2/grade/items", "grade")
        val domains = cache.domains().associateBy { it.name }
        assertEquals(CacheDomain("待办", 2, 6L), domains["待办"])
        assertEquals(CacheDomain("成绩", 1, 5L), domains["成绩"])
        assertEquals(CacheDomain("旧版未分类", 1, 3L), domains["旧版未分类"])
        assertEquals(14L, cache.usedBytes())
        assertNull(cache.get("tronclass", a, "/api/v2/todo/legacy", 100))
        val beforeClear = cache.writeGeneration()
        assertEquals(6L, cache.clearDomain("待办"))
        assertNull(cache.get("tronclass", a, "/api/v2/todo/items?page=1", 100))
        assertNull(cache.get("portal", b, "/api/v2/todo/items?page=2", 100))
        assertEquals("grade", cache.get("tronclass", a, "/api/v2/grade/items", 100))
        assertTrue(legacy.isFile)
        assertFalse(cache.putIfCurrent("tronclass", a, "/api/v2/grade/items", "late", beforeClear) { true })
        assertEquals(3L, cache.clearDomain("旧版未分类"))
        assertFalse(legacy.exists())
        assertEquals(5L, cache.clearAll())
        assertEquals(0, cache.entries())
        assertEquals(0L, cache.usedBytes())
        assertTrue(cache.domains().isEmpty())
        assertTrue(temp.root.resolve("http_cache").listFiles().orEmpty().isEmpty())
    }

    @Test fun clearRejectsEarlierFlightAndSidecarsContainNoIdentityOrQuery() {
        val cache = ResponseCache(temp.root) { 10_000L }
        val id = RequestIdentity("synthetic-owner-secret-looking", 1)
        val path = "/api/todos?token=synthetic-query-value"
        val captured = cache.writeGeneration()
        cache.clearAll()
        assertFalse(cache.putIfCurrent("tronclass", id, path, "old-flight", captured) { true })
        assertEquals(0, cache.entries())
        assertTrue(cache.putIfCurrent("tronclass", id, path, "fresh", cache.writeGeneration()) { true })
        temp.root.resolve("http_cache").listFiles().orEmpty().forEach { file ->
            assertFalse(file.name.contains(id.ownerKey))
            if (file.name.endsWith(".path")) assertEquals("/api/todos", file.readText())
        }
        val beforeDomainClear = cache.writeGeneration()
        assertTrue(cache.clearDomain("待办") > 0L)
        assertFalse(cache.putIfCurrent("tronclass", id, path, "late", beforeDomainClear) { true })
        assertEquals(0, cache.entries())
    }

    @Test fun staleIdentityBeforeOrDuringCacheCommitDoesNotLeaveEntry() {
        val cache = ResponseCache(temp.root) { 10_000L }
        val id = RequestIdentity("synthetic-owner", 1)
        val epoch = cache.writeGeneration()
        assertFalse(cache.putIfCurrent("tronclass", id, "/api/todos", "old", epoch) { false })
        var checks = 0
        assertFalse(cache.putIfCurrent("tronclass", id, "/api/todos", "old", epoch) { ++checks == 1 })
        assertNull(cache.get("tronclass", id, "/api/todos", 100))
        assertEquals(0, cache.entries())
    }
}
