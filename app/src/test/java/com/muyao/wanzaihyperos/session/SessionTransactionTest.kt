package com.muyao.wanzaihyperos.session

import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.*
import org.junit.Test

class SessionTransactionTest {
    private class FakeSecureStore(initial: Map<String, String>) : SecureStore {
        private var values = initial.toMap()
        val batches = mutableListOf<Map<String, String?>>()
        var singleWrites = 0
        var failAtomic = false
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String?) {
            singleWrites++
            error("Transaction must not use per-key writes")
        }
        override fun delete(key: String) {
            singleWrites++
            error("Transaction must not use per-key deletes")
        }
        override fun writeAtomically(changes: Map<String, String?>) {
            batches += changes.toMap()
            if (failAtomic) throw HzcuException.Storage("writeAtomically", "SyntheticCommitFailed")
            val next = values.toMutableMap()
            changes.forEach { (key, value) -> if (value == null) next.remove(key) else next[key] = value }
            values = next.toMap()
        }
        fun snapshot(): Map<String, String> = values.toMap()
    }

    private val portalKeys = setOf(PortalSession.KEY_ACCESS_TOKEN, PortalSession.KEY_USER_CODE,
        PortalSession.KEY_SAVED_AT, PortalSession.KEY_PORTAL_COOKIES)
    private val secondaryKeys = setOf(
        TronclassSession.KEY_TOKENS, TronclassSession.KEY_SSO, TronclassSession.KEY_SESSION_ID,
        TronclassSession.KEY_COOKIES, TronclassSession.KEY_SAVED_AT, TronclassSession.KEY_DISPLAY_NAME,
        Cjh52Session.KEY_TOKEN, Cjh52Session.KEY_OBTAINED_AT, Cjh52Session.KEY_ACTIVATED,
        ZhgySession.KEY_CODE, ZhgySession.KEY_OBTAINED_AT,
        JwSession.KEY_ACCOUNT, JwSession.KEY_PASSWORD, JwSession.KEY_LAST_LOGIN,
        SztySession.KEY_TOKEN, SztySession.KEY_USER_ID, SztySession.KEY_LAST_SYNC, SztySession.KEY_SAVED_AT,
    )
    private val retained = mapOf(TronclassSession.KEY_DEVICE_ID to "synthetic-device",
        "synthetic_preference" to "synthetic-appearance", "synthetic_draft" to "synthetic-draft")

    private fun seed(): Map<String, String> = retained + mapOf(
        PortalSession.KEY_ACCESS_TOKEN to "synthetic-portal-a",
        PortalSession.KEY_USER_CODE to "synthetic-account-a", PortalSession.KEY_SAVED_AT to "100",
        PortalSession.KEY_PORTAL_COOKIES to "synthetic-portal-cookie-a",
        TronclassSession.KEY_TOKENS to TronclassSession.writeTokenPair("synthetic-tron-a", "synthetic-refresh-a"),
        TronclassSession.KEY_SSO to "synthetic-sso-a", TronclassSession.KEY_SESSION_ID to "synthetic-sid-a",
        TronclassSession.KEY_COOKIES to "synthetic-courses-cookie-a", TronclassSession.KEY_SAVED_AT to "101",
        TronclassSession.KEY_DISPLAY_NAME to "synthetic-display-a",
        Cjh52Session.KEY_TOKEN to "synthetic-cjh-a", Cjh52Session.KEY_OBTAINED_AT to "102",
        Cjh52Session.KEY_ACTIVATED to "true", ZhgySession.KEY_CODE to "synthetic-residence-a",
        ZhgySession.KEY_OBTAINED_AT to "103", JwSession.KEY_ACCOUNT to "synthetic-jw-a",
        JwSession.KEY_PASSWORD to "synthetic-password-a", JwSession.KEY_LAST_LOGIN to "104",
        SztySession.KEY_TOKEN to "synthetic-sports-a", SztySession.KEY_USER_ID to "7",
        SztySession.KEY_LAST_SYNC to "105", SztySession.KEY_SAVED_AT to "106",
    )

    private inner class Fixture(load: Boolean = true) {
        val store = FakeSecureStore(seed())
        val portal = PortalSession(store)
        val tron = TronclassSession(store)
        val cjh = Cjh52Session(store)
        val residence = ZhgySession(store)
        val jw = JwSession(store)
        val sports = SztySession(store)
        val sessions = listOf(portal, tron, cjh, residence, jw, sports)
        val transaction = SessionTransaction(store, portal, tron, cjh, residence, jw, sports)
        init { if (load) sessions.forEach { it.ensureLoaded() } }
        fun memory(): List<Any?> = listOf(
            portal.accessToken(), portal.userCode(), portal.portalCookiesJson(), portal.savedAtMillis(),
            tron.accessToken(), tron.refreshToken(), tron.ssoCookiesJson(), tron.xSessionId(), tron.savedAtMillis(),
            cjh.token(), cjh.isActivated(), cjh.obtainedAtMillis(), residence.code(), residence.obtainedAtMillis(),
            jw.account(), jw.password(), jw.lastLoginMillis(), sports.token(), sports.userId(),
            sports.lastSyncMillis(), sports.savedAtMillis(),
        ) + sessions.map { it.state }
    }

    private fun storageFailure(action: () -> Unit) {
        try { action(); fail("Synthetic atomic failure must propagate") }
        catch (error: HzcuException.Storage) { assertEquals("SyntheticCommitFailed", error.code) }
    }

    @Test fun clearAllUsesOneCommitForSixSourcesAndRetainsDeviceAndPreferences() {
        val f = Fixture()
        assertTrue(f.sessions.all { it.state == SessionState.LoggedIn })
        val seen = mutableListOf<List<SessionState>>()
        f.sessions.forEach { it.addListener { seen += f.sessions.map { session -> session.state } } }
        f.transaction.clearAll()
        assertEquals(1, f.store.batches.size)
        val batch = f.store.batches.single()
        assertEquals(portalKeys + secondaryKeys, batch.keys)
        assertTrue(batch.values.all { it == null })
        assertEquals(0, f.store.singleWrites)
        assertEquals(retained, f.store.snapshot())
        assertEquals("synthetic-device", f.tron.deviceId())
        assertTrue(f.sessions.all { it.state == SessionState.LoggedOut })
        assertEquals(6, seen.size)
        assertTrue(seen.all { states -> states.all { it == SessionState.LoggedOut } })
        assertEquals(1, f.store.batches.size)
    }

    @Test fun failedClearChangesNeitherDiskNorMemoryAndNotifiesNobody() {
        val f = Fixture()
        val disk = f.store.snapshot()
        val memory = f.memory()
        var notifications = 0
        f.sessions.forEach { it.addListener { notifications++ } }
        f.store.failAtomic = true
        storageFailure { f.transaction.clearAll() }
        assertEquals(1, f.store.batches.size)
        assertEquals(disk, f.store.snapshot())
        assertEquals(memory, f.memory())
        assertEquals(0, notifications)
        assertEquals(0, f.store.singleWrites)
    }

    @Test fun replacingPrimaryAtomicallyClearsAllSecondarySourcesBeforeNotifications() {
        val f = Fixture()
        val coherent = mutableListOf<Boolean>()
        f.sessions.forEach { it.addListener {
            coherent += f.portal.userCode() == "synthetic-account-b" &&
                f.sessions.drop(1).all { session -> session.state == SessionState.LoggedOut }
        } }
        f.transaction.savePortal("synthetic-portal-b", "synthetic-account-b", "synthetic-cookie-b")
        assertEquals(1, f.store.batches.size)
        val batch = f.store.batches.single()
        assertEquals(portalKeys + secondaryKeys, batch.keys)
        assertTrue(secondaryKeys.all { batch[it] == null })
        assertEquals("synthetic-account-b", f.portal.userCode())
        assertEquals("synthetic-portal-b", f.portal.accessToken())
        assertEquals("synthetic-cookie-b", f.portal.portalCookiesJson())
        assertTrue(f.sessions.drop(1).all { it.state == SessionState.LoggedOut })
        assertEquals(retained, f.store.snapshot().filterKeys { it !in portalKeys })
        assertEquals(6, coherent.size)
        assertTrue(coherent.all { it })
        assertEquals(0, f.store.singleWrites)
    }

    @Test fun failedPrimaryReplacementKeepsOldPrimaryAndAllSecondarySources() {
        val f = Fixture()
        val disk = f.store.snapshot()
        val memory = f.memory()
        var notifications = 0
        f.sessions.forEach { it.addListener { notifications++ } }
        f.store.failAtomic = true
        storageFailure {
            f.transaction.savePortal("synthetic-portal-b", "synthetic-account-b", "synthetic-cookie-b")
        }
        assertEquals(1, f.store.batches.size)
        assertEquals(portalKeys + secondaryKeys, f.store.batches.single().keys)
        assertEquals(disk, f.store.snapshot())
        assertEquals(memory, f.memory())
        assertEquals(0, notifications)
        assertEquals(0, f.store.singleWrites)
    }

    @Test fun samePrimaryRenewalCommitsOnlyPortalAndPreservesSecondaryDiskAndMemory() {
        val f = Fixture()
        val secondaryDisk = f.store.snapshot().filterKeys { it in secondaryKeys }
        val secondaryMemory = f.memory().drop(4)
        var primaryNotifications = 0
        var secondaryNotifications = 0
        f.portal.addListener { primaryNotifications++ }
        f.sessions.drop(1).forEach { it.addListener { secondaryNotifications++ } }
        f.transaction.savePortal("synthetic-renewed-a", "synthetic-account-a", "synthetic-renewed-cookie-a")
        assertEquals(1, f.store.batches.size)
        assertEquals(portalKeys, f.store.batches.single().keys)
        assertEquals(secondaryDisk, f.store.snapshot().filterKeys { it in secondaryKeys })
        assertEquals(secondaryMemory, f.memory().drop(4))
        assertEquals("synthetic-renewed-a", f.portal.accessToken())
        assertEquals("synthetic-renewed-cookie-a", f.portal.portalCookiesJson())
        assertEquals(1, primaryNotifications)
        assertEquals(0, secondaryNotifications)
        assertEquals(retained, f.store.snapshot().filterKeys { it in retained })
    }

    @Test fun clearAllAlsoClearsNotYetLoadedSessionsInTheSameCommit() {
        val f = Fixture(load = false)
        assertTrue(f.sessions.all { it.state == SessionState.Unknown })
        f.transaction.clearAll()
        assertEquals(1, f.store.batches.size)
        assertTrue(f.sessions.all { it.state == SessionState.LoggedOut })
        assertEquals(retained, f.store.snapshot())
        assertNull(f.portal.accessToken())
        assertNull(f.tron.accessToken())
        assertNull(f.cjh.token())
        assertNull(f.residence.code())
        assertNull(f.jw.account())
        assertNull(f.sports.token())
    }
}
