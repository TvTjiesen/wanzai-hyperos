package com.muyao.wanzaihyperos.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Only synthetic data, an isolated preference file and an isolated Keystore alias. */
@RunWith(AndroidJUnit4::class)
class AtomicKeystoreCompatibilityTest {
    @get:Rule
    val storage = IsolatedKeystoreFixture()

    @Test
    fun existingV1CiphertextAndKeysRemainReadableAfterAtomicUpdate() {
        val prefs = storage.prefs
        val crypto = storage.crypto
        val keys = listOf(
            PortalSession.KEY_ACCESS_TOKEN,
            PortalSession.KEY_USER_CODE,
            PortalSession.KEY_SAVED_AT,
            PortalSession.KEY_PORTAL_COOKIES,
        )
        assertTrue(prefs.edit().clear().commit())
        crypto.deleteKeyForTesting()
        // Existing persisted format: each unchanged key holds its own version-1 GCM ciphertext.
        val legacy = mapOf(
            keys[0] to "old-synthetic-token",
            keys[1] to "synthetic-A",
            keys[2] to "1",
            keys[3] to "old-synthetic-cookie",
            TronclassSession.KEY_DEVICE_ID to "synthetic-stable-device",
        )
        val editor = prefs.edit()
        legacy.forEach { (key, value) -> editor.putString(key, crypto.encrypt(value)) }
        assertTrue(editor.commit())
        val store = storage.store()
        val session = PortalSession(store)
        assertEquals("old-synthetic-token", session.accessToken())
        assertEquals("old-synthetic-cookie", session.portalCookiesJson())
        session.save("new-synthetic-token", "synthetic-B", "new-synthetic-cookie")
        val reopened = PortalSession(storage.store())
        assertEquals("new-synthetic-token", reopened.accessToken())
        assertEquals("synthetic-B", reopened.userCode())
        assertEquals("new-synthetic-cookie", reopened.portalCookiesJson())
        keys.forEach { key ->
            val raw = prefs.getString(key, null)!!
            val packed = android.util.Base64.decode(raw, android.util.Base64.NO_WRAP)
            assertEquals(KeystoreCrypto.FORMAT_VERSION, packed[0])
            assertFalse(raw.contains("synthetic"))
        }
        session.clear()
        keys.forEach { key -> assertTrue(prefs.getString(key, null) == null) }
        assertEquals("synthetic-stable-device", store.read(TronclassSession.KEY_DEVICE_ID))
    }
}
