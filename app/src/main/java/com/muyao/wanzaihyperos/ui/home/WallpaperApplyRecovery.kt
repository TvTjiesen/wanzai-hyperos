package com.muyao.wanzaihyperos.ui.home

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Re-running after any interrupted step completes the same selection before deleting its journal. */
internal fun completeWallpaperApply(
    pending: File,
    stagedImage: File,
    activeImage: File,
    saveMetadata: () -> Unit,
    savePreferences: () -> Unit,
) {
    check(pending.isFile)
    check(stagedImage.isFile || activeImage.isFile) { "Wallpaper recovery image unavailable" }
    saveMetadata()
    if (stagedImage.isFile) Files.move(stagedImage.toPath(), activeImage.toPath(), StandardCopyOption.REPLACE_EXISTING)
    savePreferences()
    check(pending.delete()) { "Wallpaper save awaiting recovery" }
}
