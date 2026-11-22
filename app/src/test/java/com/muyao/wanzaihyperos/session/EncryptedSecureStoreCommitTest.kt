package com.muyao.wanzaihyperos.session

import android.content.SharedPreferences
import com.muyao.wanzaihyperos.net.HzcuException
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedSecureStoreCommitTest {
    private class FakePreferences {
        val disk = linkedMapOf<String, String>()
        private val cache = linkedMapOf<String, String>()
        var failCommit = false
        var throwOnCommit = false
        var commits = 0
        var editors = 0

        val prefs: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> cache[args!![0]] ?: args[1]
                "contains" -> cache.containsKey(args!![0])
                "getAll" -> cache.toMap()
                "edit" -> editor()
                "toString" -> "SyntheticPreferences"
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            editors++
            val pending = linkedMapOf<String, String?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "putString" -> { pending[args!![0] as String] = args[1] as String?; proxy }
                    "remove" -> { pending[args!![0] as String] = null; proxy }
                    "commit" -> {
                        commits++
                        // Android can update its process cache before reporting failed disk persistence.
                        pending.forEach { (key, value) -> if (value == null) cache.remove(key) else cache[key] = value }
                        if (throwOnCommit) throw IllegalStateException("SYNTHETIC-SECRET-MUST-NOT-ESCAPE")
                        if (!failCommit) {
                            disk.clear()
                            disk.putAll(cache)
                        }
                        !failCommit
                    }
                    "apply" -> error("Credentials must never use apply")
                    "toString" -> "SyntheticEditor"
                    else -> error("Unexpected editor method: ${method.name}")
                }
            } as SharedPreferences.Editor
        }

        fun restart(): FakePreferences = FakePreferences().also {
            it.disk.putAll(disk)
            it.cache.putAll(disk)
        }
    }

    private fun store(prefs: FakePreferences, encrypt: (String) -> String = { "v1:$it" }) =
        EncryptedSecureStore(prefs.prefs, encrypt, { encoded ->
            check(encoded.startsWith("v1:")) { "SYNTHETIC-SECRET-MUST-NOT-ESCAPE" }
            encoded.removePrefix("v1:")
        })

    @Test
    fun `multiple encrypted keys and deletions use exactly one synchronous commit`() {
        val prefs = FakePreferences()
        val s = store(prefs)
        s.write("removed", "old")
        val before = prefs.commits
        s.writeAtomically(mapOf("a" to "A", "b" to "B", "removed" to null))
        assertEquals(before + 1, prefs.commits)
        assertEquals(mapOf("a" to "v1:A", "b" to "v1:B"), prefs.disk)
        val restarted = store(prefs.restart())
        assertEquals("A", restarted.read("a"))
        assertEquals("B", restarted.read("b"))
        assertNull(restarted.read("removed"))
    }

    @Test
    fun `nth encryption failure creates no editor and changes no committed keys`() {
        for (nth in 1..3) {
            val prefs = FakePreferences()
            store(prefs).writeAtomically(mapOf("a" to "old-A", "b" to "old-B", "c" to "old-C"))
            val disk = prefs.disk.toMap()
            val editors = prefs.editors
            var encryptions = 0
            val s = store(prefs) {
                encryptions++
                if (encryptions == nth) throw IllegalStateException("SYNTHETIC-SECRET-MUST-NOT-ESCAPE")
                "v1:$it"
            }
            val error = assertThrows(HzcuException.Storage::class.java) {
                s.writeAtomically(mapOf("a" to "new-A", "b" to "new-B", "c" to "new-C"))
            }
            assertEquals("IllegalStateException", error.code)
            assertFalse(error.message!!.contains("SYNTHETIC-SECRET"))
            assertEquals(editors, prefs.editors)
            assertEquals(disk, prefs.disk)
            assertEquals("old-A", s.read("a"))
        }
    }

    @Test
    fun `false commit throws and unconfirmed preference cache cannot become loaded credentials`() {
        val prefs = FakePreferences()
        val s = store(prefs)
        val session = JwSession(s).apply { save("A", "old-password", 1) }
        val disk = prefs.disk.toMap()
        prefs.failCommit = true
        val error = assertThrows(HzcuException.Storage::class.java) { session.save("B", "new-password", 2) }
        assertEquals("writeAtomically", error.op)
        assertEquals("CommitFailed", error.code)
        assertEquals("A", session.account())
        assertEquals("old-password", session.password())
        assertEquals(disk, prefs.disk)
        val unconfirmed = JwSession(s)
        unconfirmed.ensureLoaded()
        assertEquals(SessionState.Unknown, unconfirmed.state)
        assertEquals("CommitOutcomeUnknown", unconfirmed.lastStorageError!!.code)
        val restarted = JwSession(store(prefs.restart()))
        assertEquals("A", restarted.account())
        assertEquals("old-password", restarted.password())
    }

    @Test
    fun `false single write and deletion are reported and restart retains old disk`() {
        val prefs = FakePreferences()
        val s = store(prefs)
        s.write("a", "old")
        prefs.failCommit = true
        val writeError = assertThrows(HzcuException.Storage::class.java) { s.write("a", "new") }
        assertEquals("write", writeError.op)
        assertEquals("CommitFailed", writeError.code)
        val deletePrefs = prefs.restart().apply { failCommit = true }
        val deletion = store(deletePrefs)
        val deleteError = assertThrows(HzcuException.Storage::class.java) { deletion.delete("a") }
        assertEquals("delete", deleteError.op)
        assertEquals("CommitFailed", deleteError.code)
        assertEquals("old", store(deletePrefs.restart()).read("a"))
        assertThrows(HzcuException.Storage::class.java) { s.read("a") }
        deletePrefs.failCommit = false
        val retry = assertThrows(HzcuException.Storage::class.java) { deletion.delete("a") }
        assertEquals("CommitOutcomeUnknown", retry.code)
        val recovered = store(deletePrefs.restart())
        recovered.delete("a")
        assertNull(recovered.read("a"))
    }

    @Test
    fun `delete false leaves all session fields and state unchanged`() {
        val prefs = FakePreferences()
        val s = store(prefs)
        val session = PortalSession(s).apply { save("token", "A", "cookie") }
        prefs.failCommit = true
        assertThrows(HzcuException.Storage::class.java) { session.clear() }
        assertEquals(SessionState.LoggedIn, session.state)
        assertEquals("token", session.accessToken())
        assertEquals("cookie", session.portalCookiesJson())
        assertEquals("token", PortalSession(store(prefs.restart())).accessToken())
    }

    @Test
    fun `corrupt ciphertext cleanup failure is reported without leaking values`() {
        val prefs = FakePreferences()
        val writer = store(prefs, encrypt = { "corrupt" })
        writer.write("a", "synthetic")
        prefs.failCommit = true
        val error = assertThrows(HzcuException.Storage::class.java) { store(prefs).read("a") }
        assertEquals("read", error.op)
        assertEquals("IllegalStateException", error.code)
        assertEquals(1, error.suppressed.size)
        val cleanup = error.suppressed.single() as HzcuException.Storage
        assertEquals("delete", cleanup.op)
        assertEquals("CommitFailed", cleanup.code)
        assertFalse(error.message!!.contains("SYNTHETIC-SECRET"))
        assertEquals("corrupt", prefs.disk["a"])
    }

    @Test
    fun `commit exceptions are normalized without raw messages`() {
        val prefs = FakePreferences().apply { throwOnCommit = true }
        val error = assertThrows(HzcuException.Storage::class.java) { store(prefs).write("a", "synthetic") }
        assertEquals("IllegalStateException", error.code)
        assertFalse(error.message!!.contains("SYNTHETIC-SECRET"))
        assertNull(error.cause)
    }

    @Test
    fun `uncertain keys do not invalidate another source or stable device id`() {
        val prefs = FakePreferences()
        val s = store(prefs)
        s.writeAtomically(mapOf("source-A" to "A", "source-B" to "B", TronclassSession.KEY_DEVICE_ID to "stable-device"))
        prefs.failCommit = true
        assertThrows(HzcuException.Storage::class.java) { s.write("source-A", "new-A") }
        assertEquals("B", s.read("source-B"))
        assertEquals("stable-device", TronclassSession(s).deviceId())
        prefs.failCommit = false
        val before = prefs.commits
        val retry = assertThrows(HzcuException.Storage::class.java) { s.write("source-A", "confirmed-A") }
        assertEquals("CommitOutcomeUnknown", retry.code)
        val otherSource = assertThrows(HzcuException.Storage::class.java) { s.write("source-B", "new-B") }
        assertEquals("CommitOutcomeUnknown", otherSource.code)
        val reopenedWrapper = store(prefs)
        assertThrows(HzcuException.Storage::class.java) { reopenedWrapper.read("source-A") }
        assertThrows(HzcuException.Storage::class.java) { reopenedWrapper.write("source-B", "new-B") }
        assertEquals(before, prefs.commits)
        val restarted = store(prefs.restart())
        assertEquals("A", restarted.read("source-A"))
        restarted.write("source-A", "confirmed-A")
        assertEquals("confirmed-A", restarted.read("source-A"))
    }
}
