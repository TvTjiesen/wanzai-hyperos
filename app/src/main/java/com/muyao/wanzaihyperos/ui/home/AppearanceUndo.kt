package com.muyao.wanzaihyperos.ui.home

import android.content.Context
import com.muyao.wanzaihyperos.data.AppearanceSettings
import com.muyao.wanzaihyperos.data.UiPrefs
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persistent checkpoint; a new image never overwrites the previous checkpoint's image. */
class AppearanceUndo(
    context: Context,
    private val directory: File = File(context.filesDir, "appearance_undo"),
    private val activeImage: File = wallpaperFile(context),
    private val commit: (AppearanceSettings) -> Unit = { UiPrefs.applyAppearance(it, persistSynchronously = true); UiPrefs.bumpWallpaperRev() },
    private val discardPendingImage: () -> Unit = { WallpaperLibrary.discardPendingApply(context) },
) {
    private val journal = File(directory, "snapshot.json")
    private val pending = File(directory, "pending")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Record(val settings: AppearanceSettings, val hadImage: Boolean, val imageName: String = "wallpaper.img")

    suspend fun available(): Boolean = withContext(Dispatchers.IO) { journal.isFile }

    suspend fun prepare(settings: AppearanceSettings) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            settings.validated()
            check(directory.isDirectory || directory.mkdirs())
            val name = "wallpaper-${UUID.randomUUID()}.img"
            val image = File(directory, name)
            val hadImage = activeImage.isFile
            if (hadImage) activeImage.copyTo(image, overwrite = false)
            val stage = File(directory, "snapshot.tmp")
            stage.writeText(json.encodeToString(Record(settings, hadImage, name)), Charsets.UTF_8)
            Files.move(stage.toPath(), journal.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            pending.writeText("restore-on-interruption", Charsets.UTF_8)
            directory.listFiles()?.filter { it.name.startsWith("wallpaper-") && it != image }?.forEach { it.delete() }
        }
    }

    suspend fun finish() = withContext(Dispatchers.IO) {
        synchronized(lock) { check(!pending.exists() || pending.delete()) }
    }

    suspend fun restore(): AppearanceSettings = withContext(Dispatchers.IO) { restoreNow() }

    private fun restoreNow(): AppearanceSettings = synchronized(lock) {
        require(journal.isFile && journal.length() <= 16_384)
        val record = json.decodeFromString<Record>(journal.readText(Charsets.UTF_8))
        record.settings.validated()
        require(record.imageName.matches(Regex("[a-zA-Z0-9.-]{1,80}")))
        val backup = File(directory, record.imageName)
        if (record.hadImage) check(backup.isFile) { "Appearance checkpoint image unavailable" }
        pending.writeText("restore-on-interruption", Charsets.UTF_8)
        discardPendingImage()
        if (record.hadImage) {
            val stage = File(activeImage.parentFile, "appearance_restore.tmp")
            backup.copyTo(stage, overwrite = true)
            Files.move(stage.toPath(), activeImage.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else if (activeImage.exists()) check(activeImage.delete())
        commit(record.settings)
        check(!pending.exists() || pending.delete())
        record.settings
    }

    companion object {
        private val lock = Any()
        /** Called before UI preferences are loaded; only an unfinished appearance transaction is rolled back. */
        fun recoverInterrupted(context: Context) {
            val undo = AppearanceUndo(context)
            if (undo.pending.isFile) undo.restoreNow()
        }
    }
}
