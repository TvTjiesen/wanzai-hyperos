package com.muyao.wanzaihyperos.ui.home

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WallpaperApplyRecoveryTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun metadataFailureKeepsOldImageAndJournalForRetry() {
        val pending = directory.newFile("pending").apply { writeText("selection") }
        val stage = directory.newFile("stage").apply { writeText("new") }
        val active = directory.newFile("active").apply { writeText("old") }
        try { completeWallpaperApply(pending, stage, active, { error("disk") }, {}) } catch (_: IllegalStateException) { }
        assertEquals("old", active.readText())
        assertTrue(pending.exists()); assertTrue(stage.exists())
        completeWallpaperApply(pending, stage, active, {}, {})
        assertEquals("new", active.readText()); assertFalse(pending.exists())
    }

    @Test fun preferenceFailureAfterReplaceIsRecoverableWithoutStagedImage() {
        val pending = directory.newFile("pending")
        val stage = directory.newFile("stage").apply { writeText("new") }
        val active = directory.newFile("active").apply { writeText("old") }
        try { completeWallpaperApply(pending, stage, active, {}, { error("preferences") }) } catch (_: IllegalStateException) { }
        assertEquals("new", active.readText()); assertTrue(pending.exists()); assertFalse(stage.exists())
        var saved = false
        completeWallpaperApply(pending, stage, active, {}, { saved = true })
        assertTrue(saved); assertFalse(pending.exists()); assertEquals("new", active.readText())
    }

    @Test fun missingImagesDoNotCommitPreferencesOrDeleteJournal() {
        val pending = directory.newFile("pending")
        var saved = false
        try { completeWallpaperApply(pending, File(directory.root, "missing-stage"), File(directory.root, "missing-active"), {}, { saved = true }) } catch (_: IllegalStateException) { }
        assertFalse(saved); assertTrue(pending.exists())
    }
}
