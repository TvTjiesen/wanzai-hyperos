package com.muyao.wanzaihyperos.ui.home

import android.content.Context
import android.net.Uri
import android.os.Looper
import com.muyao.wanzaihyperos.data.UiPrefs
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WallpaperEntry(val id: String, val panX: Float = 0f, val panY: Float = 0f, val zoom: Float = 1f)

class WallpaperLibrary(context: Context) {
    private val context = context.applicationContext
    private val directory = File(context.filesDir, "wallpaper_library")
    private val manifest = File(directory, "library.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun file(entry: WallpaperEntry): File {
        require(entry.id.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        return File(directory, "${entry.id}.img")
    }

    suspend fun entries(): List<WallpaperEntry> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            directory.mkdirs()
            recoverPending(context)
            var entries = readEntries()
            val old = wallpaperFile(context)
            if (!manifest.exists() && old.isFile) {
                val entry = WallpaperEntry(UUID.randomUUID().toString(), UiPrefs.wallpaperPanX, UiPrefs.wallpaperPanY, UiPrefs.wallpaperZoom)
                old.copyTo(file(entry), overwrite = false)
                entries = listOf(entry)
                writeEntries(entries)
            }
            entries
        }
    }

    suspend fun addImages(uris: List<Uri>): Pair<List<WallpaperEntry>, Int> = withContext(Dispatchers.IO) {
        val operation = currentCoroutineContext()
        synchronized(lock) {
            check(Looper.myLooper() != Looper.getMainLooper())
            directory.mkdirs()
            val entries = readEntries().toMutableList()
            var rejected = 0
            uris.forEach { uri ->
                operation.ensureActive()
                if (entries.count { it.id != "builtin-sea" } >= MAX_ENTRIES) { rejected++; return@forEach }
                val entry = WallpaperEntry(UUID.randomUUID().toString())
                val temporary = File(directory, "${entry.id}.tmp")
                try {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read image")
                    input.use { source ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(32768)
                            var total = 0L
                            while (true) {
                                operation.ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= MAX_IMAGE_BYTES)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    val probe = decodeWallpaperBitmap(temporary, 180, 320, WallpaperBudget(160_000, 640_000))
                        ?: error("Unsupported image")
                    probe.recycle()
                    Files.move(temporary.toPath(), file(entry).toPath(), StandardCopyOption.REPLACE_EXISTING)
                    writeEntries(entries + entry)
                    entries += entry
                } catch (cancel: CancellationException) {
                    temporary.delete()
                    file(entry).delete()
                    throw cancel
                } catch (_: Exception) {
                    temporary.delete()
                    file(entry).delete()
                    rejected++
                }
            }
            entries.toList() to rejected
        }
    }

    suspend fun bundledSea(): WallpaperEntry = withContext(Dispatchers.IO) {
        synchronized(lock) {
            directory.mkdirs()
            recoverPending(context)
            val entry = WallpaperEntry("builtin-sea")
            if (!file(entry).isFile) {
                val temporary = File(directory, "builtin-sea.tmp")
                try {
                    context.resources.openRawResource(com.muyao.wanzaihyperos.R.drawable.wallpaper_sea).use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    Files.move(temporary.toPath(), file(entry).toPath(), StandardCopyOption.REPLACE_EXISTING)
                } finally { temporary.delete() }
            }
            val entries = readEntries()
            if (entries.none { it.id == entry.id }) writeEntries(entries + entry)
            entries.firstOrNull { it.id == entry.id } ?: entry
        }
    }

    suspend fun apply(entry: WallpaperEntry) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            recoverPending(context)
            require(entry.panX.isFinite() && entry.panY.isFinite() && entry.zoom.isFinite())
            val saved = entry.copy(panX = entry.panX.coerceIn(-1f, 1f), panY = entry.panY.coerceIn(-1f, 1f), zoom = entry.zoom.coerceIn(1f, 4f))
            val entries = readEntries()
            require(entries.any { it.id == entry.id })
            val source = file(entry)
            require(source.isFile)
            val destination = wallpaperFile(context)
            val temporary = File(destination.parentFile, "home_wallpaper_library.tmp")
            try {
                source.copyTo(temporary, overwrite = true)
                val pending = File(directory, "pending-apply.json")
                val journal = File(directory, "pending-apply.tmp")
                journal.writeText(json.encodeToString(saved), Charsets.UTF_8)
                Files.move(journal.toPath(), pending.toPath(), StandardCopyOption.REPLACE_EXISTING)
                completeWallpaperApply(pending, temporary, destination,
                    saveMetadata = { writeEntries(entries.map { if (it.id == saved.id) saved else it }) },
                    savePreferences = { commitSelection(context, saved) })
            } finally {
                if (!File(directory, "pending-apply.json").exists()) temporary.delete()
            }
        }
    }

    suspend fun remove(entry: WallpaperEntry): List<WallpaperEntry> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            recoverPending(context)
            val next = readEntries().filterNot { it.id == entry.id }
            writeEntries(next)
            file(entry).delete()
            next
        }
    }

    private fun readEntries(): List<WallpaperEntry> {
        if (!manifest.exists()) return emptyList()
        require(manifest.isFile && manifest.length() <= 65536) { "Invalid wallpaper library" }
        val entries = json.decodeFromString<List<WallpaperEntry>>(manifest.readText(Charsets.UTF_8))
        require(entries.size <= MAX_ENTRIES + 1 && entries.map { it.id }.distinct().size == entries.size)
        require(entries.all { it.id.matches(Regex("[a-zA-Z0-9-]{1,64}")) &&
            it.panX.isFinite() && it.panY.isFinite() && it.zoom.isFinite() })
        return entries.filter { file(it).isFile }.map {
            it.copy(panX = it.panX.coerceIn(-1f, 1f), panY = it.panY.coerceIn(-1f, 1f), zoom = it.zoom.coerceIn(1f, 4f))
        }
    }

    private fun writeEntries(entries: List<WallpaperEntry>) {
        val temporary = File(directory, "library.tmp")
        try {
            temporary.writeText(json.encodeToString(entries))
            Files.move(temporary.toPath(), manifest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    companion object {
        const val MAX_ENTRIES = 24
        private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
        private val lock = Any()

        private fun commitSelection(context: Context, entry: WallpaperEntry) {
            val preferences = context.getSharedPreferences("wanzai_ui", Context.MODE_PRIVATE)
            val revision = preferences.getInt("wallpaper_rev", 0) + 1
            check(preferences.edit()
                .putFloat("wallpaper_pan_x", entry.panX)
                .putFloat("wallpaper_pan_y", entry.panY)
                .putFloat("wallpaper_zoom", entry.zoom)
                .putString("wallpaper_preset", "album")
                .putString("container_style", "designed")
                .putInt("wallpaper_rev", revision)
                .commit()) { "Wallpaper preferences could not be saved" }
            UiPrefs.publishRecoveredWallpaper(entry.panX, entry.panY, entry.zoom, revision)
        }

        internal fun discardPendingApply(context: Context) = synchronized(lock) {
            val pending = File(context.filesDir, "wallpaper_library/pending-apply.json")
            check(!pending.exists() || pending.delete())
            File(context.filesDir, "home_wallpaper_library.tmp").delete()
        }

        /** Only a small interrupted-save journal is inspected before preferences are loaded. */
        fun recoverPending(context: Context) = synchronized(lock) {
            val library = WallpaperLibrary(context)
            val pending = File(library.directory, "pending-apply.json")
            if (!pending.exists()) return@synchronized
            require(pending.length() <= 4096) { "Invalid wallpaper recovery journal" }
            val entry = library.json.decodeFromString<WallpaperEntry>(pending.readText(Charsets.UTF_8))
            require(entry.panX.isFinite() && entry.panY.isFinite() && entry.zoom.isFinite() &&
                entry.panX in -1f..1f && entry.panY in -1f..1f && entry.zoom in 1f..4f)
            val source = library.file(entry)
            check(source.isFile) { "Wallpaper recovery image unavailable" }
            val entries = library.readEntries()
            check(entries.any { it.id == entry.id })
            val destination = wallpaperFile(context)
            val temporary = File(destination.parentFile, "home_wallpaper_library.tmp")
            completeWallpaperApply(pending, temporary, destination,
                saveMetadata = { library.writeEntries(entries.map { if (it.id == entry.id) entry else it }) },
                savePreferences = { commitSelection(context, entry) })
        }
    }
}
