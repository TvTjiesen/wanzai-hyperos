package com.muyao.wanzaihyperos.session

import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AtomicSessionPersistenceTest {
    private class AtomicFaultStore : SecureStore {
        private val committed = InMemorySecureStore()
        var failAtKey: Int? = null
        var lastBatchSize = 0

        override fun read(key: String): String? = committed.read(key)
        override fun write(key: String, value: String?) = writeAtomically(mapOf(key to value))
        override fun delete(key: String) = writeAtomically(mapOf(key to null))
        override fun writeAtomically(changes: Map<String, String?>) {
            lastBatchSize = changes.size
            changes.entries.forEachIndexed { index, _ ->
                if (failAtKey == index + 1) throw HzcuException.Storage("writeAtomically", "InjectedFailure")
            }
            committed.writeAtomically(changes)
        }
    }

    private data class Fixture(
        val session: StoredSession,
        val save: (Boolean) -> Unit,
        val clear: () -> Unit,
        val values: () -> List<Any?>,
    )

    private val fixtures: List<(SecureStore) -> Fixture> = listOf(
        { store ->
            val s = PortalSession(store)
            Fixture(s, { newer -> s.save(if (newer) "new-token" else "old-token", if (newer) "B" else "A", if (newer) "new-cookie" else "old-cookie") }, s::clear,
                { listOf(s.accessToken(), s.userCode(), s.savedAtMillis(), s.portalCookiesJson()) })
        },
        { store ->
            val s = TronclassSession(store)
            Fixture(s, { newer -> s.saveTokens(if (newer) "new-access" else "old-access", if (newer) "new-refresh" else "old-refresh", if (newer) "new-sso" else "old-sso") }, s::clear,
                { listOf(s.accessToken(), s.refreshToken(), s.savedAtMillis(), s.ssoCookiesJson(), s.xSessionId()) })
        },
        { store ->
            val s = Cjh52Session(store)
            Fixture(s, { newer -> s.save(if (newer) "new-code" else "old-code", if (newer) 2L else 1L) }, s::clear,
                { listOf(s.token(), s.obtainedAtMillis(), s.isActivated()) })
        },
        { store ->
            val s = ZhgySession(store)
            Fixture(s, { newer -> s.save(if (newer) "new-code" else "old-code", if (newer) 2L else 1L) }, s::clear,
                { listOf(s.code(), s.obtainedAtMillis()) })
        },
        { store ->
            val s = JwSession(store)
            Fixture(s, { newer -> s.save(if (newer) "B" else "A", if (newer) "new-password" else "old-password", if (newer) 2L else 1L) }, s::clear,
                { listOf(s.account(), s.password(), s.lastLoginMillis()) })
        },
        { store ->
            val s = SztySession(store)
            Fixture(s, { newer -> s.save(if (newer) "new-token" else "old-token", if (newer) 22 else 11, if (newer) 2L else 1L) }, s::clear,
                { listOf(s.token(), s.userId(), s.lastSyncMillis(), s.savedAtMillis()) })
        },
    )

    @Test
    fun `every source rejects each failed key without mixed memory or restart state`() {
        fixtures.forEach { create ->
            val store = AtomicFaultStore()
            val f = create(store)
            f.save(false)
            val before = f.values()
            val batchSize = store.lastBatchSize
            var notifications = 0
            f.session.addListener { notifications++ }
            for (key in 1..batchSize) {
                store.failAtKey = key
                assertThrows(HzcuException.Storage::class.java) { f.save(true) }
                assertEquals(before, f.values())
                assertEquals(SessionState.LoggedIn, f.session.state)
                assertNotNull(f.session.lastStorageError)
                assertEquals(before, create(store).values())
                assertEquals(0, notifications)
            }
            store.failAtKey = null
            f.save(true)
            assertEquals(f.values(), create(store).values())
            assertNull(f.session.lastStorageError)
            assertEquals(1, notifications)
        }
    }

    @Test
    fun `every source rejects each failed deletion and does not announce logout`() {
        fixtures.forEach { create ->
            val store = AtomicFaultStore()
            val f = create(store)
            f.save(false)
            val before = f.values()
            // TronClass includes all legacy credential keys in its deletion batch.
            val clearSize = if (f.session is TronclassSession) 6 else store.lastBatchSize
            var notifications = 0
            f.session.addListener { notifications++ }
            for (key in 1..clearSize) {
                store.failAtKey = key
                assertThrows(HzcuException.Storage::class.java) { f.clear() }
                assertEquals(before, f.values())
                assertEquals(SessionState.LoggedIn, f.session.state)
                assertEquals(before, create(store).values())
                assertEquals(0, notifications)
            }
            store.failAtKey = null
            f.clear()
            assertEquals(SessionState.LoggedOut, f.session.state)
            val reopened = create(store)
            reopened.session.ensureLoaded()
            assertEquals(SessionState.LoggedOut, reopened.session.state)
            assertEquals(1, notifications)
        }
    }

    @Test
    fun `failed first save leaves every source unloaded`() {
        fixtures.forEach { create ->
            val store = AtomicFaultStore().apply { failAtKey = 1 }
            val f = create(store)
            assertThrows(HzcuException.Storage::class.java) { f.save(true) }
            assertEquals(SessionState.Unknown, f.session.state)
            store.failAtKey = null
            f.session.ensureLoaded()
            assertEquals(SessionState.LoggedOut, f.session.state)
            assertNull(f.session.lastStorageError)
        }
    }

    @Test
    fun `single field updates publish only after disk success`() {
        val store = AtomicFaultStore()
        val portal = PortalSession(store).apply { save("token", "A", "old-cookie") }
        val tron = TronclassSession(store).apply { saveTokens("access", "refresh", "old-sso"); saveXSessionId("old-sid") }
        val cjh = Cjh52Session(store).apply { save("code", 1) }
        val jw = JwSession(store).apply { save("A", "password", 1) }
        val sports = SztySession(store).apply { save("token", 11, 1) }
        store.failAtKey = 1
        listOf<() -> Unit>(
            { portal.savePortalCookies("new-cookie") },
            { tron.saveSsoCookies("new-sso") },
            { tron.saveXSessionId("new-sid") },
            { cjh.markActivated() },
            { jw.markLogin(2) },
            { sports.markSync(2) },
        ).forEach { update -> assertThrows(HzcuException.Storage::class.java) { update() } }
        assertEquals("old-cookie", portal.portalCookiesJson())
        assertEquals("old-sso", tron.ssoCookiesJson())
        assertEquals("old-sid", tron.xSessionId())
        assertEquals(false, cjh.isActivated())
        assertEquals(1L, jw.lastLoginMillis())
        assertEquals(1L, sports.lastSyncMillis())
    }

    @Test
    fun `secondary field save loads existing primary credentials instead of masking them`() {
        val store = InMemorySecureStore()
        TronclassSession(store).saveTokens("existing-access", "existing-refresh")
        val reopened = TronclassSession(store)
        reopened.saveXSessionId("sid")
        assertEquals("existing-access", reopened.accessToken())
        assertEquals("existing-refresh", reopened.refreshToken())
        assertEquals(SessionState.LoggedIn, reopened.state)
    }

    @Test
    fun `full save retains optional fields when existing session has not been loaded`() {
        val store = InMemorySecureStore()
        PortalSession(store).save("old-token", "A", "retained-cookie")
        val portal = PortalSession(store)
        portal.save("new-token", "B")
        assertEquals("retained-cookie", portal.portalCookiesJson())
        assertEquals("retained-cookie", PortalSession(store).portalCookiesJson())
        TronclassSession(store).apply { saveTokens("old-access", "old-refresh", "retained-sso"); saveXSessionId("retained-sid") }
        val tron = TronclassSession(store)
        tron.saveTokens("new-access", "new-refresh", renewal = true)
        assertEquals("retained-sso", tron.ssoCookiesJson())
        assertEquals("retained-sid", tron.xSessionId())
        assertEquals("retained-sso", TronclassSession(store).ssoCookiesJson())
    }

    @Test
    fun `secondary save cannot hide a failed primary credential load`() {
        val store = object : SecureStore {
            override fun read(key: String): String? = throw HzcuException.Storage("read", "InjectedReadFailure")
            override fun write(key: String, value: String?) = Unit
            override fun delete(key: String) = Unit
        }
        val sessions = listOf(
            Cjh52Session(store).apply { markActivated() },
            JwSession(store).apply { markLogin(1) },
            SztySession(store).apply { markSync(1) },
            TronclassSession(store).apply { saveXSessionId("sid") },
            PortalSession(store).apply { savePortalCookies("cookie") },
        )
        sessions.forEach { session ->
            assertEquals(SessionState.Unknown, session.state)
            assertEquals("InjectedReadFailure", session.lastStorageError!!.code)
        }
    }

    @Test
    fun `tron clear removes legacy credentials but keeps device id and other sources`() {
        val store = InMemorySecureStore(mapOf(
            TronclassSession.KEY_DEVICE_ID to "synthetic-device",
            TronclassSession.KEY_COOKIES to "legacy-cookie",
            TronclassSession.KEY_DISPLAY_NAME to "legacy-name",
        ))
        val portal = PortalSession(store).apply { save("portal-token") }
        val tron = TronclassSession(store).apply { saveTokens("access", "refresh"); saveXSessionId("sid") }
        tron.clear()
        assertEquals("synthetic-device", tron.deviceId())
        assertEquals("synthetic-device", TronclassSession(store).deviceId())
        assertNull(store.read(TronclassSession.KEY_COOKIES))
        assertNull(store.read(TronclassSession.KEY_DISPLAY_NAME))
        assertNull(store.read(TronclassSession.KEY_X_SESSION_ID))
        assertEquals("portal-token", portal.accessToken())
    }

    @Test
    fun `default strategy rejects multiple keys without issuing any single writes`() {
        var writes = 0
        val store = object : SecureStore {
            override fun read(key: String): String? = null
            override fun write(key: String, value: String?) { writes++ }
            override fun delete(key: String) { writes++ }
        }
        val error = assertThrows(HzcuException.Storage::class.java) {
            store.writeAtomically(mapOf("a" to "A", "b" to "B"))
        }
        assertEquals("AtomicWriteUnsupported", error.code)
        assertEquals(0, writes)
        store.writeAtomically(mapOf("a" to "A"))
        assertEquals(1, writes)
    }
}
