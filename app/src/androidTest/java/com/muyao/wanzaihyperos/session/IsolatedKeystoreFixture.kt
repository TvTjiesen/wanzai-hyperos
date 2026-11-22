package com.muyao.wanzaihyperos.session

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.rules.ExternalResource

/** targetContext still uses the target UID; isolation here is by fresh names, not a separate UID. */
class IsolatedKeystoreFixture : ExternalResource() {
    companion object {
        const val TEST_NAMESPACE_PREFIX = "wanzai_instrumentation_only_"
    }

    private val namespace = TEST_NAMESPACE_PREFIX + UUID.randomUUID().toString()
    private val aliases = mutableListOf<KeystoreCrypto>()
    lateinit var prefs: SharedPreferences
        private set
    lateinit var crypto: KeystoreCrypto
        private set

    override fun before() {
        check(namespace.startsWith(TEST_NAMESPACE_PREFIX))
        check(namespace != EncryptedSecureStore.PREFS_FILE_NAME)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        crypto = isolatedCrypto("store")
    }

    fun isolatedCrypto(suffix: String): KeystoreCrypto {
        require(suffix.matches(Regex("[a-z_]+")))
        val alias = "${namespace}_$suffix"
        check(alias.startsWith(TEST_NAMESPACE_PREFIX))
        check(alias != EncryptedSecureStore.MASTER_KEY_ALIAS)
        return KeystoreCrypto(alias).also { aliases += it }
    }

    fun store(): SecureStore = EncryptedSecureStore(prefs, crypto::encrypt, crypto::decrypt)

    override fun after() {
        var failure: Throwable? = null
        try {
            assertTrue("Isolated test preferences must be cleared", prefs.edit().clear().commit())
        } catch (error: Throwable) {
            failure = error
        } finally {
            for (alias in aliases) {
                try {
                    alias.deleteKeyForTesting()
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }
}
