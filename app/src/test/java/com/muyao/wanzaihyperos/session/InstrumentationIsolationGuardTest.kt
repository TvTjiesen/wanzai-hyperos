package com.muyao.wanzaihyperos.session

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source red lines supplement, but do not replace, the eight Android Keystore scenarios. */
class InstrumentationIsolationGuardTest {
    private val root = File(System.getProperty("user.dir")).let { run ->
        val parent = run.parentFile
        if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else run
    }
    private val testDir = File(root, "app/src/androidTest/java/com/muyao/wanzaihyperos/session")
    private fun source(name: String) = File(testDir, name).readText()

    @Test fun instrumentationCannotOpenProductionStorageOrStartTheApp() {
        val files = testDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Instrumented storage sources must be present", files.isNotEmpty())
        val forbidden = listOf(
            Regex("EncryptedSecureStore\\s*\\.\\s*open\\s*\\("),
            Regex("KeystoreCrypto\\s*\\(\\s*\\)"),
            Regex("getSharedPreferences\\s*\\(\\s*EncryptedSecureStore\\.PREFS_FILE_NAME"),
            Regex("KeystoreCrypto\\s*\\(\\s*EncryptedSecureStore\\.MASTER_KEY_ALIAS"),
            Regex("ActivityScenario|ActivityTestRule|startActivity\\s*\\(|AppGraph\\s*\\("),
            Regex("OkHttpClient|HttpURLConnection|WebView\\s*\\("),
            Regex("(?:println|print|Log\\.[vdiew]|assertNotEquals)\\s*\\("),
            Regex("WanzaiHzcuSecureStorage|wanzai_hzcu_master_key"),
        )
        files.forEach { file ->
            val text = file.readText()
            forbidden.forEach { pattern ->
                assertFalse("${file.name} crossed instrumentation isolation red line", pattern.containsMatchIn(text))
            }
        }
        val fixture = source("IsolatedKeystoreFixture.kt")
        assertEquals("Only the isolated fixture may open preferences", 1,
            files.sumOf { Regex("getSharedPreferences\\s*\\(").findAll(it.readText()).count() })
        assertEquals("Only the isolated fixture may construct Keystore aliases", 1,
            files.sumOf { Regex("KeystoreCrypto\\s*\\(").findAll(it.readText()).count() })
        assertTrue("The fixture must generate a fresh test namespace", "UUID.randomUUID()" in fixture &&
            "wanzai_instrumentation_only_" in fixture)
        assertTrue("Production names must be explicitly rejected",
            "namespace != EncryptedSecureStore.PREFS_FILE_NAME" in fixture &&
                "alias != EncryptedSecureStore.MASTER_KEY_ALIAS" in fixture)
    }

    @Test fun cleanupAndFixtureRulesCoverEveryStorageScenario() {
        val fixture = source("IsolatedKeystoreFixture.kt")
        assertTrue("Cleanup must run outside the test body", "ExternalResource" in fixture && "override fun after()" in fixture)
        val cleanup = fixture.substringAfter("override fun after()")
        assertTrue("Preference cleanup failure must not bypass any tracked alias", "finally" in cleanup &&
            "for (alias in aliases)" in cleanup && "alias.deleteKeyForTesting()" in cleanup &&
            "failure?.let { throw it }" in cleanup)
        assertTrue("Every constructed alias must be tracked", "aliases += it" in fixture)
        listOf("KeystoreSecureStoreTest.kt", "AtomicKeystoreCompatibilityTest.kt").forEach { name ->
            val text = source(name)
            assertTrue("$name must use the per-test cleanup rule", "@get:Rule" in text &&
                "val storage = IsolatedKeystoreFixture()" in text)
        }
    }

    @Test fun allEightOriginalKeystoreScenariosRemainPresent() {
        val crypto = source("KeystoreSecureStoreTest.kt")
        val atomic = source("AtomicKeystoreCompatibilityTest.kt")
        val names = listOf(
            "roundTripThroughAndroidKeystore", "storedValueIsNotPlaintext", "newIvForEveryEncryption",
            "tamperedCiphertextIsRejectedAndDropped", "ciphertextIsBoundToKeystoreKey",
            "unicodeAndLongValuesSurviveRoundTrip", "multipleKeysAreIndependent",
        )
        names.forEach { name -> assertTrue("Original scenario $name must remain", "fun $name(" in crypto) }
        assertTrue("Original atomic compatibility scenario must remain",
            "fun existingV1CiphertextAndKeysRemainReadableAfterAtomicUpdate(" in atomic)
        assertEquals(8, Regex("@Test\\b").findAll(crypto + atomic).count())
        assertTrue("IV reuse must still fail without exposing ciphertext", "first == second" in crypto)
        assertTrue("Atomic format version and preserved device assertions must remain",
            "assertEquals(KeystoreCrypto.FORMAT_VERSION, packed[0])" in atomic &&
                "store.read(TronclassSession.KEY_DEVICE_ID)" in atomic)
    }
}
