package com.muyao.wanzaihyperos.session

import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.*
import org.junit.Test

class SessionReplacementTest {
    private class AtomicStore(initial: Map<String, String> = emptyMap()) : SecureStore {
        var values: Map<String, String> = initial.toMap()
            private set
        val batches = mutableListOf<Map<String, String?>>()
        var fail = false
        var beforeCommit: () -> Unit = {}
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String?) = error("Expected atomic session commit")
        override fun delete(key: String) = error("Expected atomic session commit")
        override fun writeAtomically(changes: Map<String, String?>) {
            beforeCommit()
            if (fail) throw HzcuException.Storage("writeAtomically", "SyntheticReplacementFailure")
            val next = values.toMutableMap()
            changes.forEach { (key, value) -> if (value == null) next.remove(key) else next[key] = value }
            values = next.toMap()
            batches += changes.toMap()
        }
    }

    private fun expectStorage(block: () -> Unit) {
        try {
            block()
            fail("Expected storage failure")
        } catch (error: HzcuException.Storage) {
            assertEquals("SyntheticReplacementFailure", error.code)
        }
    }

    @Test fun sameJwAccountPasswordSaveIsReplacementButLoginTimestampIsNot() {
        val store = AtomicStore()
        val jw = JwSession(store)
        val notified = mutableListOf<Long>()
        jw.addListener { notified += jw.replacementRevision }
        store.beforeCommit = { assertEquals(0L, jw.replacementRevision) }
        jw.save("synthetic-account", "synthetic-password-a", 1L)
        assertEquals(1L, jw.replacementRevision)
        store.beforeCommit = { assertEquals(1L, jw.replacementRevision) }
        jw.save("synthetic-account", "synthetic-password-b", 2L)
        assertEquals(2L, jw.replacementRevision)
        store.beforeCommit = {}
        jw.markLogin(3L)
        assertEquals(2L, jw.replacementRevision)
        assertEquals(listOf(1L, 2L, 2L), notified)
        val oldDisk = store.values
        store.fail = true
        expectStorage { jw.save("synthetic-account", "synthetic-password-c", 4L) }
        assertEquals(2L, jw.replacementRevision)
        assertEquals(oldDisk, store.values)
        assertEquals("synthetic-password-b", jw.password())
        assertEquals(3L, jw.lastLoginMillis()!!)
        assertEquals(3, notified.size)
    }

    @Test fun secondaryCodeAndTokenSavesAdvanceOnlyAfterSuccessfulCommit() {
        val store = AtomicStore()
        val cjh = Cjh52Session(store)
        val residence = ZhgySession(store)
        val sports = SztySession(store)
        val cases: List<Pair<StoredSession, () -> Unit>> = listOf(
            cjh to { cjh.save("synthetic-cjh", 1L) },
            residence to { residence.save("synthetic-residence", 1L) },
            sports to { sports.save("synthetic-sports", 7, 1L) },
        )
        for ((session, save) in cases) {
            var notifications = 0
            session.addListener { notifications++ }
            store.beforeCommit = { assertEquals(0L, session.replacementRevision) }
            save()
            assertEquals(1L, session.replacementRevision)
            store.beforeCommit = { assertEquals(1L, session.replacementRevision) }
            save()
            assertEquals(2L, session.replacementRevision)
            val oldDisk = store.values
            store.fail = true
            store.beforeCommit = { assertEquals(2L, session.replacementRevision) }
            expectStorage(save)
            assertEquals(2L, session.replacementRevision)
            assertEquals(oldDisk, store.values)
            assertEquals(SessionState.LoggedIn, session.state)
            assertEquals(2, notifications)
            store.fail = false
        }
        store.beforeCommit = {}
        cjh.markActivated()
        sports.markSync(2L)
        assertEquals(2L, cjh.replacementRevision)
        assertEquals(2L, sports.replacementRevision)
    }

    @Test fun tronNewLoginRemovesOldSidInTheSameCommitAndPublishesReplacement() {
        val store = AtomicStore(mapOf(TronclassSession.KEY_X_SESSION_ID to "synthetic-old-sid"))
        val tron = TronclassSession(store)
        val notified = mutableListOf<Pair<Long, String?>>()
        tron.addListener { notified += tron.replacementRevision to tron.xSessionId() }
        store.beforeCommit = { assertEquals(0L, tron.replacementRevision) }
        tron.saveTokens("synthetic-access-a", "synthetic-refresh-a")
        assertEquals(1, store.batches.size)
        assertTrue(store.batches.single().containsKey(TronclassSession.KEY_SESSION_ID))
        assertNull(store.batches.single()[TronclassSession.KEY_SESSION_ID])
        assertNull(store.values[TronclassSession.KEY_X_SESSION_ID])
        assertNull(tron.xSessionId())
        assertEquals(listOf(1L to null), notified)
        store.beforeCommit = {}
        tron.saveXSessionId("synthetic-new-sid")
        tron.saveTokens("synthetic-access-b", "synthetic-refresh-b")
        assertEquals(2L, tron.replacementRevision)
        assertNull(tron.xSessionId())
    }

    @Test fun tronExplicitRenewalKeepsSidAndRevisionEvenWhenSessionWasNotLoaded() {
        val store = AtomicStore(mapOf(
            TronclassSession.KEY_TOKENS to TronclassSession.writeTokenPair("synthetic-old-access", "synthetic-old-refresh"),
            TronclassSession.KEY_X_SESSION_ID to "synthetic-sid",
            TronclassSession.KEY_SSO to "synthetic-sso",
        ))
        val tron = TronclassSession(store)
        assertEquals(SessionState.Unknown, tron.state)
        tron.saveTokens("synthetic-renewed-access", "synthetic-renewed-refresh", renewal = true)
        assertEquals(0L, tron.replacementRevision)
        assertEquals("synthetic-sid", tron.xSessionId())
        assertEquals("synthetic-sso", tron.ssoCookiesJson())
        assertFalse(store.batches.single().containsKey(TronclassSession.KEY_X_SESSION_ID))
        tron.saveSsoCookies("synthetic-updated-sso")
        tron.saveXSessionId("synthetic-updated-sid")
        assertEquals(0L, tron.replacementRevision)
        tron.saveTokens("synthetic-second-renewal", null, renewal = true)
        assertEquals(0L, tron.replacementRevision)
        assertEquals("synthetic-updated-sid", tron.xSessionId())
    }

    @Test fun failedTronReplacementOrRenewalRetainsMemoryDiskSidAndRevision() {
        val store = AtomicStore()
        val tron = TronclassSession(store)
        tron.saveTokens("synthetic-original-access", "synthetic-original-refresh")
        tron.saveXSessionId("synthetic-original-sid")
        val oldDisk = store.values
        val oldTime = tron.savedAtMillis()
        var notifications = 0
        tron.addListener { notifications++ }
        store.fail = true
        for (renewal in listOf(false, true)) {
            expectStorage { tron.saveTokens("synthetic-failed-access", "synthetic-failed-refresh", renewal = renewal) }
            assertEquals(1L, tron.replacementRevision)
            assertEquals(oldDisk, store.values)
            assertEquals("synthetic-original-access", tron.accessToken())
            assertEquals("synthetic-original-refresh", tron.refreshToken())
            assertEquals("synthetic-original-sid", tron.xSessionId())
            assertEquals(oldTime!!, tron.savedAtMillis()!!)
            assertEquals(0, notifications)
        }
    }

    @Test fun loadingAndClearingDoNotInventReplacementEvents() {
        val store = AtomicStore(mapOf(
            JwSession.KEY_ACCOUNT to "synthetic-account",
            JwSession.KEY_PASSWORD to "synthetic-password",
        ))
        val jw = JwSession(store)
        jw.ensureLoaded()
        assertEquals(0L, jw.replacementRevision)
        jw.save("synthetic-account", "synthetic-replacement", 1L)
        jw.clear()
        assertEquals(1L, jw.replacementRevision)
        assertEquals(SessionState.LoggedOut, jw.state)
        jw.acceptCommittedClear()
        assertEquals(1L, jw.replacementRevision)
    }
}
