package com.muyao.wanzaihyperos.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muyao.wanzaihyperos.data.AppearanceSettings
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only synthetic image bytes and a private random test directory. */
@RunWith(AndroidJUnit4::class)
class AppearanceUndoTest {
    @Test fun checkpointSurvivesReopeningAndRepeatedRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "appearance-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val image = File(root, "active.img").apply { writeText("synthetic-A") }
            val directory = File(root, "undo")
            val a = AppearanceSettings(theme = "Dark", sky = "album", panX = -.4f, zoom = 2f)
            var restored: AppearanceSettings? = null
            fun reopen() = AppearanceUndo(context, directory, image, { restored = it }, {})
            reopen().prepare(a)
            image.writeText("synthetic-B")
            reopen().finish()
            assertTrue(reopen().available())
            assertEquals(a, reopen().restore())
            assertEquals(a, restored)
            assertEquals("synthetic-A", image.readText())
            assertEquals(a, reopen().restore())
            assertEquals("synthetic-A", image.readText())
            val b = a.copy(theme = "Light")
            reopen().prepare(b)
            image.writeText("synthetic-C")
            assertEquals(b, reopen().restore())
            assertEquals("synthetic-A", image.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun failedCommitLeavesRecoveryPendingAndRetriesExactly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "appearance-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val image = File(root, "active.img").apply { writeText("synthetic-original") }
            val directory = File(root, "undo")
            var fail = true
            var restored: AppearanceSettings? = null
            fun reopen() = AppearanceUndo(context, directory, image, {
                if (fail) error("synthetic save failure")
                restored = it
            }, {})
            val before = AppearanceSettings(theme = "Dark", glass = "off", reduceTransparency = true)
            reopen().prepare(before)
            image.writeText("synthetic-new")
            assertTrue(runCatching { reopen().restore() }.isFailure)
            assertTrue(File(directory, "pending").exists())
            fail = false
            assertEquals(before, reopen().restore())
            assertEquals(before, restored)
            assertEquals("synthetic-original", image.readText())
            assertFalse(File(directory, "pending").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun applyingFirstImageCanUndoBackToNoImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "appearance-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val image = File(root, "active.img")
            val undo = AppearanceUndo(context, File(root, "undo"), image, {}, {})
            undo.prepare(AppearanceSettings())
            image.writeText("synthetic-new-image")
            undo.restore()
            assertFalse(image.exists())
            assertTrue(undo.available())
        } finally { root.deleteRecursively() }
    }
}
