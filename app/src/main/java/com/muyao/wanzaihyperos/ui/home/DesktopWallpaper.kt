package com.muyao.wanzaihyperos.ui.home

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.BackgroundPreset
import com.muyao.wanzaihyperos.ui.design.LocalThemeCustomization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A denied read stays denied: never substitute WallpaperManager's bundled default image. */
suspend fun loadDesktopWallpaper(context: Context): LoadedWallpaper? = withContext(Dispatchers.IO) {
    try {
        val manager = WallpaperManager.getInstance(context)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val descriptor = manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return@withContext null
        descriptor.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, bounds) }
        val plan = wallpaperDecodePlan(bounds.outWidth, bounds.outHeight, 720, 1600,
            WallpaperBudget(1_200_000, 4_800_000)) ?: return@withContext null
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use {
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, options)
        } ?: return@withContext null
        if (bitmap.allocationByteCount > 4_800_000) { bitmap.recycle(); return@withContext null }
        val image = bitmap.asImageBitmap()
        LoadedWallpaper(image, wallpaperStats(image))
    } catch (_: SecurityException) { null }
      catch (_: java.io.IOException) { null }
      catch (_: OutOfMemoryError) { null }
}

val LocalDesktopWallpaper = staticCompositionLocalOf<LoadedWallpaper?> { null }

@Composable
fun rememberDesktopWallpaper(enabled: Boolean): LoadedWallpaper? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var image by remember(enabled) { mutableStateOf<LoadedWallpaper?>(null) }
    LaunchedEffect(enabled, revision) { image = if (enabled) loadDesktopWallpaper(context) else null }
    return image
}

private tailrec fun Context.activityWindow(): android.view.Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.activityWindow()
    else -> null
}

/** The platform wallpaper layer also supports live wallpapers without requesting file access. */
@Composable
fun DesktopWallpaperWindow(enabled: Boolean) {
    val window = LocalContext.current.activityWindow() ?: return
    DisposableEffect(window, enabled) {
        val previous = window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0
        val background = window.decorView.background
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        } else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        onDispose {
            if (previous) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(background)
        }
    }
}

@Composable
fun ThemePageBackground(modifier: Modifier = Modifier, dark: Boolean, preset: BackgroundPreset = AppColors.background) {
    val custom = LocalThemeCustomization.current
    val image = LocalDesktopWallpaper.current
    if (!custom.useSystemWallpaper) {
        Box(modifier.background(com.muyao.wanzaihyperos.ui.design.AppColors.pageBrush(dark, preset)))
    } else if (image != null) {
        Box(modifier) {
            Image(image.bitmap, null, Modifier.fillMaxSize().blur(custom.wallpaperBlur.dp), contentScale = ContentScale.Crop)
            if (custom.wallpaperLightness != 0) Box(Modifier.fillMaxSize().background(
                (if (custom.wallpaperLightness > 0) Color.White else Color.Black).copy(alpha = kotlin.math.abs(custom.wallpaperLightness) / 100f)))
        }
    } else {
        // The protected layer is composed by Android, outside the app's backdrop recorder.
        val lightness = custom.wallpaperLightness
        Box(if (lightness == 0) modifier else modifier.background(
            (if (lightness > 0) Color.White else Color.Black).copy(alpha = kotlin.math.abs(lightness) / 100f),
        ))
    }
}
