package com.muyao.wanzaihyperos.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Allocation ceiling includes the decoded ARGB bitmap; stats use sparse getPixel, not a full copy. */
data class WallpaperBudget(
    val maxPixels: Long = 4_000_000L,
    val maxArgbBytes: Long = 16_000_000L,
) {
    init { require(maxPixels > 0 && maxArgbBytes >= 4) }
}

data class WallpaperDecodePlan(val sampleSize: Int, val width: Int, val height: Int) {
    val pixels: Long get() = width.toLong() * height
    val argbBytes: Long get() = pixels * 4
}

/** Power-of-two, overflow-safe sampling balances cover resolution against a hard allocation budget. */
fun wallpaperDecodePlan(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    budget: WallpaperBudget = WallpaperBudget(),
): WallpaperDecodePlan? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return null
    fun dimension(value: Int, sample: Int) = ((value.toLong() + sample - 1) / sample).toInt().coerceAtLeast(1)
    var sample = 1
    while (sample <= (1 shl 29)) {
        val width = dimension(sourceWidth, sample)
        val height = dimension(sourceHeight, sample)
        val pixels = width.toLong() * height
        val withinBudget = pixels <= budget.maxPixels && pixels <= budget.maxArgbBytes / 4
        val next = sample * 2
        val nextCovers = dimension(sourceWidth, next) >= targetWidth && dimension(sourceHeight, next) >= targetHeight
        if (withinBudget && !nextCovers) return WallpaperDecodePlan(sample, width, height)
        sample = next
    }
    val width = dimension(sourceWidth, sample)
    val height = dimension(sourceHeight, sample)
    return WallpaperDecodePlan(sample, width, height).takeIf {
        it.pixels <= budget.maxPixels && it.pixels <= budget.maxArgbBytes / 4
    }
}

data class LoadedWallpaper(val bitmap: ImageBitmap, val stats: WallpaperStats)

/** Access-ordered cache bounded by both decoded bytes and entry count. */
internal class WeightedLruCache<K : Any, V : Any>(
    private val maxWeight: Long,
    private val maxEntries: Int,
    private val weightOf: (V) -> Long,
) {
    private data class Entry<V>(val value: V, val weight: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, .75f, true)
    private var totalWeight = 0L

    init { require(maxWeight > 0 && maxEntries > 0) }

    @Synchronized fun get(key: K): V? = entries[key]?.value

    @Synchronized fun put(key: K, value: V) {
        val weight = weightOf(value).coerceAtLeast(0L)
        entries.remove(key)?.let { totalWeight -= it.weight }
        if (weight > maxWeight) return
        entries[key] = Entry(value, weight)
        totalWeight += weight
        while (entries.size > maxEntries || totalWeight > maxWeight) {
            val eldest = entries.entries.iterator().next()
            totalWeight -= eldest.value.weight
            entries.remove(eldest.key)
        }
    }

    @Synchronized fun clear() { entries.clear(); totalWeight = 0L }
    @Synchronized fun size(): Int = entries.size
    @Synchronized fun weight(): Long = totalWeight
}

/** Keep active images and visible wallpaper thumbnails warm without unbounded bitmap retention. */
private object WallpaperMemoryCache {
    private val cache = WeightedLruCache<String, LoadedWallpaper>(24_000_000L, 8) {
        it.bitmap.width.toLong() * it.bitmap.height * 4L
    }
    fun get(key: String): LoadedWallpaper? = cache.get(key)
    fun put(key: String, value: LoadedWallpaper) = cache.put(key, value)
    fun clear() = cache.clear()
}

private fun wallpaperCacheKey(file: File, targetWidth: Int, targetHeight: Int, budget: WallpaperBudget): String =
    file.absoluteFile.path + ":" + file.lastModified() + ":" + file.length() + ":" + targetWidth + ":" + targetHeight + ":" + budget.maxPixels + ":" + budget.maxArgbBytes

/** Bounds decode, allocation and sparse luminance sampling all finish on IO before publication. */
suspend fun loadWallpaper(
    file: File,
    targetWidth: Int = 1440,
    targetHeight: Int = 3200,
    budget: WallpaperBudget = WallpaperBudget(),
): LoadedWallpaper? = withContext(Dispatchers.IO) {
        val key = wallpaperCacheKey(file, targetWidth, targetHeight, budget) + ":" + com.muyao.wanzaihyperos.data.UiPrefs.wallpaperRev
        WallpaperMemoryCache.get(key)?.let { return@withContext it }
        val bitmap = decodeWallpaperBitmap(file, targetWidth, targetHeight, budget) ?: return@withContext null
        try {
            val loaded = LoadedWallpaper(bitmap.asImageBitmap(), sparseWallpaperStats(bitmap))
            if (key == wallpaperCacheKey(file, targetWidth, targetHeight, budget) + ":" + com.muyao.wanzaihyperos.data.UiPrefs.wallpaperRev) WallpaperMemoryCache.put(key, loaded)
            loaded
        } catch (error: Exception) {
            bitmap.recycle()
            null
        }
}

/** Compatibility path for existing import/crop callers already executing on IO. */
internal fun decodeWallpaperBitmap(
    file: File,
    targetWidth: Int,
    targetHeight: Int,
    budget: WallpaperBudget = WallpaperBudget(),
): Bitmap? {
    if (Looper.myLooper() == Looper.getMainLooper()) return null
    if (!file.isFile) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val plan = wallpaperDecodePlan(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight, budget) ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = plan.sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        if (bitmap.width.toLong() * bitmap.height > budget.maxPixels || bitmap.allocationByteCount.toLong() > budget.maxArgbBytes) {
            bitmap.recycle()
            null
        } else bitmap
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

internal fun wallpaperStatsFromImage(img: ImageBitmap): WallpaperStats {
    check(Looper.myLooper() != Looper.getMainLooper()) { "Wallpaper stats require a background thread" }
    return sparseWallpaperStats(img.asAndroidBitmap())
}

private fun sparseWallpaperStats(bitmap: Bitmap): WallpaperStats {
    var topRed = 0.0; var topGreen = 0.0; var topBlue = 0.0; var topCount = 0
    var red = 0.0; var green = 0.0; var blue = 0.0; var count = 0
    val columns = minOf(24, bitmap.width)
    val rows = minOf(24, bitmap.height)
    for (row in 0 until rows) {
        val y = ((row.toLong() * bitmap.height) / rows).toInt()
        for (column in 0 until columns) {
            val x = ((column.toLong() * bitmap.width) / columns).toInt()
            val color = bitmap.getPixel(x, y)
            val r = (color ushr 16 and 255) / 255.0
            val g = (color ushr 8 and 255) / 255.0
            val b = (color and 255) / 255.0
            red += r; green += g; blue += b; count++
            if (y.toFloat() / bitmap.height in 0.15f..0.45f) {
                topRed += r; topGreen += g; topBlue += b; topCount++
            }
        }
    }
    fun average(r: Double, g: Double, b: Double, n: Int) =
        if (n == 0) Color(0.5f, 0.5f, 0.5f) else Color((r / n).toFloat(), (g / n).toFloat(), (b / n).toFloat())
    return WallpaperStats(average(topRed, topGreen, topBlue, topCount), average(red, green, blue, count))
}
