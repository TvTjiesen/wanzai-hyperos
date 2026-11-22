package com.muyao.wanzaihyperos.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 加密存储的**真机验证**。
 *
 * 为什么必须在真机上跑：Android Keystore 在 JVM 单测里不存在，
 * 而这正是「凭据永不落盘」这条红线的实现方式。只做代码审查证明不了
 * 「写下去的确实是密文」「密文确实绑在这台设备的密钥上」。
 *
 * 设备证据要求见 docs/A2-WALKTHROUGH.md。
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecureStoreTest {

    @get:Rule
    val storage = IsolatedKeystoreFixture()

    private val testKey = "a2_instrumented_test_key"

    private fun prefs() = storage.prefs

    @Test
    fun roundTripThroughAndroidKeystore() {
        val store = storage.store()
        store.delete(testKey)

        val value = "城院-access-token-0123456789abcdef"
        store.write(testKey, value)

        assertEquals(value, store.read(testKey))

        store.delete(testKey)
        assertNull("删除后不该还能读到", store.read(testKey))
    }

    /** ★ 这条是红线的直接证据：落盘的不是明文。 */
    @Test
    fun storedValueIsNotPlaintext() {
        val store = storage.store()
        store.write(testKey, "PLAINTEXT-MARKER-1234567890")

        val raw = prefs().getString(testKey, null)

        assertNotNull(raw)
        assertFalse("明文落盘了，红线被踩", raw!!.contains("PLAINTEXT-MARKER"))
        store.delete(testKey)
    }

    /** 同一个明文两次加密必须不同 —— 否则说明 GCM 的 IV 被复用了。 */
    @Test
    fun newIvForEveryEncryption() {
        val store = storage.store()
        store.write(testKey, "same-value")
        val first = prefs().getString(testKey, null)
        store.write(testKey, "same-value")
        val second = prefs().getString(testKey, null)

        assertFalse("两次加密结果相同 = IV 被复用，GCM 会泄漏明文", first == second)
        store.delete(testKey)
    }

    /** 篡改密文必须被 GCM 的认证标签挡下，并且坏数据被就地清掉。 */
    @Test
    fun tamperedCiphertextIsRejectedAndDropped() {
        val store = storage.store()
        store.write(testKey, "secret-value")

        val raw = prefs().getString(testKey, null)!!
        val packed = android.util.Base64.decode(raw, android.util.Base64.NO_WRAP)
        val ciphertextIndex = 1 + KeystoreCrypto.IV_LENGTH
        assertTrue("合成密文必须包含非头部数据", packed.size > ciphertextIndex)
        packed[ciphertextIndex] = (packed[ciphertextIndex].toInt() xor 1).toByte()
        val tampered = android.util.Base64.encodeToString(packed, android.util.Base64.NO_WRAP)
        assertTrue(prefs().edit().putString(testKey, tampered).commit())

        try {
            store.read(testKey)
            fail("被篡改的密文居然解开了，说明没有做完整性校验")
        } catch (expected: HzcuException.Storage) {
            // 期望：认证失败
        }
        assertTrue("解不开的密文应当被就地清掉，否则每次读都抛", prefs().getString(testKey, null) == null)
    }

    /**
     * ★ 证明密文**真的绑定在 Keystore 密钥上**：删掉密钥后，
     * 即使拿着完整密文也解不开。这条对应「刷机后密文还在但密钥没了」。
     */
    @Test
    fun ciphertextIsBoundToKeystoreKey() {
        val crypto = storage.isolatedCrypto("key_loss")
        crypto.deleteKeyForTesting()

        val encoded = crypto.encrypt("bound-to-this-device")
        crypto.deleteKeyForTesting()

        try {
            crypto.decrypt(encoded)
            fail("删掉 Keystore 主密钥后仍然解开了 —— 说明密文根本没有依赖 Keystore")
        } catch (expected: Exception) {
            // 期望：解不开
        }
    }

    @Test
    fun unicodeAndLongValuesSurviveRoundTrip() {
        val store = storage.store()
        val long = "学号2021001".repeat(200) + "★⚠️🚀"

        store.write(testKey, long)

        assertEquals(long, store.read(testKey))
        store.delete(testKey)
    }

    @Test
    fun multipleKeysAreIndependent() {
        val store = storage.store()
        val a = "a2_test_key_a"
        val b = "a2_test_key_b"

        store.write(a, "value-a")
        store.write(b, "value-b")

        assertEquals("value-a", store.read(a))
        assertEquals("value-b", store.read(b))

        store.delete(a)
        assertNull(store.read(a))
        assertEquals("删一个键不该影响另一个", "value-b", store.read(b))

        store.delete(b)
    }
}
