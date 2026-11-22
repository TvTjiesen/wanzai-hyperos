package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.data.jw.OwnerSnapshotStore
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SecondaryAccountSnapshotTest {
    @Test fun samePrimaryNeverSeedsDifferentSecondaryButSameIdentitySurvivesRestart() {
        val dir = Files.createTempDirectory("synthetic-secondary-snapshot").toFile()
        try {
            for (source in listOf("jw", "szty")) {
                val file = dir.resolve("$source.json")
                val primary = AccountEpoch().apply { activate("synthetic-main") }
                var secondary: String? = "synthetic-secondary-a"
                val store = OwnerSnapshotStore(file, source, primary) { secondary }
                assertTrue(store.write(primary.token(), "{\"rows\":[1]}"))
                val restarted = AccountEpoch().apply { activate("synthetic-main") }
                assertEquals("{\"rows\":[1]}", OwnerSnapshotStore(file, source, restarted) { secondary }.read(restarted.token()))
                assertFalse(file.readText().contains("synthetic-secondary-a"))
                secondary = "synthetic-secondary-b"
                assertNull(store.read(primary.token()))
                secondary = null
                assertNull(store.read(primary.token()))
                assertFalse(store.write(primary.token(), "{}"))
                assertTrue(OwnerSnapshotStore(file, source, primary).write(primary.token(), "{\"legacy\":true}"))
                secondary = "synthetic-secondary-a"
                assertNull(store.read(primary.token()))
            }
        } finally { dir.deleteRecursively() }
    }
}
