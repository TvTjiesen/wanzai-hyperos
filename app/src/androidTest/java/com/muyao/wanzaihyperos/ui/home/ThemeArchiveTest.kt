package com.muyao.wanzaihyperos.ui.home

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muyao.wanzaihyperos.data.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeArchiveTest {
    @Test fun imageThemeImportsAsPortableDraftAndRejectsUnsafeArchive() = runBlocking {
        require(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = WallpaperLibrary(context)
        val before = library.entries().map { it.id }.toSet()
        val prefsBefore = UiPrefs.appearanceSnapshot()
        val image = File(context.cacheDir, "theme-test-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        try {
            val (entries, rejected) = library.addImages(listOf(Uri.fromFile(image)))
            assertEquals(0, rejected)
            val source = entries.first { it.id !in before }
            val custom = ThemeCustomization(accentHex = "#AA3399", cardImage = source.id,
                navigationIcons = mapOf(2 to source.id))
            val settings = AppearanceSettings(sky = "album", customization = custom)
            val output = ByteArrayOutputStream()
            AppearanceThemeArchive.write(context, output, settings, image, true)
            val imported = AppearanceThemeArchive.read(context, ByteArrayInputStream(output.toByteArray()))
            assertNotNull(imported.wallpaper)
            val importedId = checkNotNull(imported.settings.customization.cardImage)
            assertNotEquals(source.id, importedId)
            assertEquals(importedId, imported.settings.customization.cardImage)
            assertEquals(importedId, imported.settings.customization.navigationIcons[2])
            assertArrayEquals(image.readBytes(), library.file(WallpaperEntry(importedId)).readBytes())
            assertEquals(prefsBefore, UiPrefs.appearanceSnapshot())
            val noImages = ByteArrayOutputStream()
            AppearanceThemeArchive.write(context, noImages, settings, image, false)
            val plain = AppearanceThemeArchive.read(context, ByteArrayInputStream(noImages.toByteArray()))
            assertEquals("auto", plain.settings.sky)
            assertNull(plain.wallpaper)
            assertNull(plain.settings.customization.cardImage)
            val unsafe = ByteArrayOutputStream()
            ZipOutputStream(unsafe).use { it.putNextEntry(ZipEntry("../outside.img")); it.write(byteArrayOf(1)); it.closeEntry() }
            assertTrue(runCatching { AppearanceThemeArchive.read(context, ByteArrayInputStream(unsafe.toByteArray())) }.isFailure)
            assertEquals(prefsBefore, UiPrefs.appearanceSnapshot())
        } finally {
            library.entries().filter { it.id !in before }.forEach { library.remove(it) }
            image.delete()
        }
    }
}
