package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.data.jw.OwnerSnapshotStore
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AccountIsolationSnapshotTest {
    @Test fun ownerEnvelopeSurvivesRestartButNeverSeedsAnotherOwnerOrSource() {
        val dir = Files.createTempDirectory("synthetic-owner-snapshot").toFile()
        try {
            val file = dir.resolve("snapshot.json")
            val epoch = AccountEpoch().apply { activate("synthetic-a") }
            val store = OwnerSnapshotStore(file, "jw", epoch)
            val payload = """{"rows":[{"course":"synthetic-course"}]}"""
            assertTrue(store.write(epoch.token(), payload))
            assertEquals(payload, store.read(epoch.token()))
            val restarted = AccountEpoch().apply { activate("synthetic-a") }
            assertEquals(payload, OwnerSnapshotStore(file, "jw", restarted).read(restarted.token()))
            assertNull(OwnerSnapshotStore(file, "szty", restarted).read(restarted.token()))
            restarted.activate("synthetic-b")
            assertNull(OwnerSnapshotStore(file, "jw", restarted).read(restarted.token()))
            assertFalse(file.readText().contains("synthetic-a"))
            assertTrue(file.readText().contains("\"generation\""))
        } finally { dir.deleteRecursively() }
    }

    @Test fun oldTokenCannotOverwriteSnapshotAfterAccountSwitch() {
        val dir = Files.createTempDirectory("synthetic-owner-write").toFile()
        try {
            val file = dir.resolve("snapshot.json")
            val epoch = AccountEpoch().apply { activate("synthetic-a") }
            val store = OwnerSnapshotStore(file, "szty", epoch)
            val oldToken = epoch.token()
            assertTrue(store.write(oldToken, "{\"run\":\"A\"}"))
            epoch.suspendOwner(); epoch.activate("synthetic-b")
            assertTrue(store.write(epoch.token(), "{\"run\":\"B\"}"))
            val current = file.readText()
            assertFalse(store.write(oldToken, "{\"run\":\"late-A\"}"))
            assertEquals(current, file.readText())
            assertEquals("{\"run\":\"B\"}", store.read(epoch.token()))
            assertNull(store.read(oldToken))
        } finally { dir.deleteRecursively() }
    }

    @Test fun legacyUnboundMalformedOversizedAndMissingOwnerAreRejected() {
        val dir = Files.createTempDirectory("synthetic-owner-legacy").toFile()
        try {
            val file = dir.resolve("snapshot.json")
            val epoch = AccountEpoch()
            val store = OwnerSnapshotStore(file, "jw", epoch)
            assertFalse(store.write(epoch.token(), "{\"rows\":[]}"))
            epoch.activate("synthetic-a")
            listOf("{\"rows\":[]}", "not-json", "{}", " ").forEach {
                file.writeText(it)
                assertNull(store.read(epoch.token()))
            }
            file.writeText("x".repeat(2_097_153))
            assertNull(store.read(epoch.token()))
            assertFalse(store.write(epoch.token(), "x".repeat(1_048_577)))
            assertEquals(1, dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }
}
