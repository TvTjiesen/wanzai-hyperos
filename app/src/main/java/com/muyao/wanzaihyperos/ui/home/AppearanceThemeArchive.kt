package com.muyao.wanzaihyperos.ui.home

import android.content.Context
import android.net.Uri
import com.muyao.wanzaihyperos.data.AppearanceSettings
import com.muyao.wanzaihyperos.data.AppearanceThemePackage
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded, fixed-name archive. No zip entry is ever resolved as an output path. */
object AppearanceThemeArchive {
    private const val TOTAL_LIMIT = 64L * 1024 * 1024
    private const val IMAGE_LIMIT = 32L * 1024 * 1024
    data class Imported(val settings: AppearanceSettings, val wallpaper: WallpaperEntry?)

    private fun imageIds(settings: AppearanceSettings): Set<String> = buildSet {
        settings.customization.cardImage?.let(::add)
        addAll(settings.customization.stickers.map { it.image })
        addAll(settings.customization.navigationIcons.values)
    }

    suspend fun write(context: Context, output: OutputStream, settings: AppearanceSettings, wallpaper: File?, includeImages: Boolean) = withContext(Dispatchers.IO) {
        settings.validated()
        val portable = if (includeImages) {
            require(settings.sky != "album" || wallpaper?.isFile == true) { "Theme wallpaper is missing" }
            settings
        } else settings.copy(sky = if (settings.sky == "album") "auto" else settings.sky,
            customization = settings.customization.copy(cardImage = null, stickers = emptyList(), navigationIcons = emptyMap()))
        var total = 0L
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("theme.json"))
            AppearanceThemePackage.write(zip, portable)
            zip.closeEntry()
            fun image(name: String, file: File) {
                require(file.isFile && file.length() in 1..IMAGE_LIMIT)
                total += file.length(); require(total <= TOTAL_LIMIT)
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            if (includeImages) {
                val library = WallpaperLibrary(context)
                imageIds(settings).forEach { id -> image("images/$id.img", library.file(WallpaperEntry(id))) }
                if (settings.sky == "album" && wallpaper?.isFile == true) image("wallpaper.img", wallpaper)
            }
        }
    }

    suspend fun read(context: Context, input: InputStream): Imported = withContext(Dispatchers.IO) {
        val buffered = input.buffered()
        buffered.mark(4)
        val signature = ByteArray(2)
        val count = buffered.read(signature)
        buffered.reset()
        if (count != 2 || signature[0] != 80.toByte() || signature[1] != 75.toByte()) {
            return@withContext Imported(AppearanceThemePackage.read(buffered), null)
        }
        val staging = File(context.cacheDir, "theme-import-${UUID.randomUUID()}")
        check(staging.mkdirs())
        try {
            var settings: AppearanceSettings? = null
            val images = linkedMapOf<String, File>()
            val names = mutableSetOf<String>()
            var wallpaper: File? = null
            var total = 0L
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(names.add(entry.name) && names.size <= 14 && !entry.isDirectory)
                    when {
                        entry.name == "theme.json" -> settings = AppearanceThemePackage.read(zip)
                        entry.name == "wallpaper.img" || entry.name.matches(Regex("images/[a-zA-Z0-9-]{1,64}\\.img")) -> {
                            val file = File(staging, UUID.randomUUID().toString() + ".img")
                            var imageBytes = 0L
                            file.outputStream().use { destination ->
                                val bytes = ByteArray(32768)
                                while (true) {
                                    val n = zip.read(bytes)
                                    if (n < 0) break
                                    imageBytes += n; total += n
                                    require(imageBytes <= IMAGE_LIMIT && total <= TOTAL_LIMIT)
                                    destination.write(bytes, 0, n)
                                }
                            }
                            require(imageBytes > 0)
                            if (entry.name == "wallpaper.img") wallpaper = file
                            else images[entry.name.removePrefix("images/").removeSuffix(".img")] = file
                        }
                        else -> error("Unsupported theme entry")
                    }
                    zip.closeEntry()
                }
            }
            val original = requireNotNull(settings).validated()
            require(images.keys == imageIds(original)) { "Theme images are missing" }
            require(original.sky != "album" || wallpaper != null) { "Theme wallpaper is missing" }
            val library = WallpaperLibrary(context)
            val sources = images.values.toList() + listOfNotNull(wallpaper)
            val before = library.entries().map { it.id }.toSet()
            require(before.size + sources.size <= WallpaperLibrary.MAX_ENTRIES) { "Wallpaper library is full" }
            sources.forEach { file ->
                val probe = decodeWallpaperBitmap(file, 180, 320, WallpaperBudget(160_000, 640_000))
                requireNotNull(probe) { "Unsupported theme image" }.recycle()
            }
            val added = try {
                val (all, rejected) = library.addImages(sources.map { Uri.fromFile(it) })
                require(rejected == 0) { "Theme images could not be imported" }
                all.filter { it.id !in before }.also { require(it.size == sources.size) }
            } catch (failure: Exception) {
                withContext(kotlinx.coroutines.NonCancellable) {
                    library.entries().filter { it.id !in before }.forEach { library.remove(it) }
                }
                throw failure
            }
            val ids = images.keys.zip(added.map { it.id }).toMap()
            val custom = original.customization
            val restored = original.copy(customization = custom.copy(
                cardImage = custom.cardImage?.let { ids.getValue(it) },
                stickers = custom.stickers.map { it.copy(image = ids.getValue(it.image)) },
                navigationIcons = custom.navigationIcons.mapValues { ids.getValue(it.value) }))
            Imported(restored.validated(), if (wallpaper != null) added.last() else null)
        } finally { staging.deleteRecursively() }
    }
}
