package com.muyao.wanzaihyperos.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.muyao.wanzaihyperos.ui.design.LocalThemeCustomization
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun decorationImage(id: String?): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id) {
        image = if (id == null) null else try {
            loadWallpaper(WallpaperLibrary(context).file(WallpaperEntry(id)), 480, 480,
                WallpaperBudget(400_000, 1_600_000))?.bitmap
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { null }
    }
    return image
}

@Composable
fun homeCardImageModifier(): Modifier {
    val image = decorationImage(LocalThemeCustomization.current.cardImage) ?: return Modifier
    return Modifier.paint(BitmapPainter(image), sizeToIntrinsics = false, contentScale = ContentScale.Crop)
        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = .72f))
}
