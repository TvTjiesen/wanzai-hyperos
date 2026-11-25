package com.muyao.wanzaihyperos.ui.home

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class WallpaperBudgetTest {
    @Test fun commonPhotosStayUnderTheActualBitmapBudget() {
        val phone = wallpaperDecodePlan(4032, 3024, 1440, 3200)!!
        val large = wallpaperDecodePlan(8000, 6000, 1440, 3200)!!
        assertEquals(2, phone.sampleSize)
        assertEquals(4, large.sampleSize)
        assertEquals(2016, phone.width)
        assertEquals(1512, phone.height)
        assertEquals(2000, large.width)
        assertEquals(1500, large.height)
        listOf(phone, large).forEach {
            assertTrue(it.pixels <= 4_000_000L)
            assertTrue(it.argbBytes <= 16_000_000L)
        }
    }

    @Test fun smallImagesAndCoverGeometryKeepTheirResolution() {
        assertEquals(1, wallpaperDecodePlan(1200, 900, 1440, 3200)!!.sampleSize)
        val square = wallpaperDecodePlan(4096, 4096, 500, 500)!!
        assertEquals(8, square.sampleSize)
        assertEquals(512, square.width)
        val landscape = wallpaperDecodePlan(4000, 3000, 1000, 700)!!
        assertEquals(4, landscape.sampleSize)
        listOf(square, landscape).forEach { plan ->
            val scale = maxOf(1080f / plan.width, 671f / plan.height)
            assertTrue(plan.width * scale >= 1079.5f)
            assertTrue(plan.height * scale >= 670.5f)
        }
    }

    @Test fun hugeAndExtremeAspectRatiosNeverOverflowTheAllocationGate() {
        listOf(Int.MAX_VALUE to Int.MAX_VALUE, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE,
            100_000 to 100_000, 12_345 to 6_789).forEach { (width, height) ->
            val plan = wallpaperDecodePlan(width, height, 1440, 3200)!!
            assertTrue(plan.sampleSize > 0)
            assertEquals(0, plan.sampleSize and (plan.sampleSize - 1))
            assertTrue(plan.width > 0 && plan.height > 0)
            assertTrue(plan.pixels in 1..4_000_000L)
            assertTrue(plan.argbBytes in 4..16_000_000L)
        }
        val onePixel = wallpaperDecodePlan(8000, 6000, 1440, 3200, WallpaperBudget(1, 4))!!
        assertEquals(1L, onePixel.pixels)
        assertEquals(4L, onePixel.argbBytes)
    }

    @Test fun invalidBoundsRejectAndByteBudgetTakesPriority() {
        listOf(0 to 1, -1 to 1, 1 to 0).forEach { (width, height) ->
            assertNull(wallpaperDecodePlan(width, height, 1, 1))
        }
        assertNull(wallpaperDecodePlan(100, 100, 0, 10))
        val plan = wallpaperDecodePlan(4032, 3024, 1440, 3200, WallpaperBudget(4_000_000, 4_000_000))!!
        assertEquals(4, plan.sampleSize)
        assertTrue(plan.argbBytes <= 4_000_000)
    }

    @Test fun wallpaperCacheEvictsByAccessAndHonorsBothBounds() {
        val cache = WeightedLruCache<String, String>(maxWeight = 5, maxEntries = 2) { it.length.toLong() }
        cache.put("a", "aa")
        cache.put("b", "bb")
        assertEquals("aa", cache.get("a")) // a becomes most recently used
        cache.put("c", "cc") // b is the least recently used entry
        assertNull(cache.get("b"))
        assertEquals("aa", cache.get("a"))
        assertEquals("cc", cache.get("c"))
        assertTrue(cache.size() <= 2)
        assertTrue(cache.weight() <= 5)

        cache.put("oversized", "123456")
        assertNull(cache.get("oversized"))
        assertTrue(cache.weight() <= 5)
        cache.clear()
        assertEquals(0, cache.size())
        assertEquals(0L, cache.weight())
    }

    @Test fun loaderKeepsBoundsAllocationAndSparseStatsOffTheMainThread() {
        val loader = File("src/main/java/com/muyao/wanzaihyperos/ui/home/WallpaperLoader.kt").readText()
        assertTrue(loader.contains("withContext(Dispatchers.IO)"))
        assertTrue(loader.contains("inJustDecodeBounds = true"))
        assertTrue(loader.contains("inPreferredConfig = Bitmap.Config.ARGB_8888"))
        assertTrue(loader.contains("allocationByteCount.toLong() > budget.maxArgbBytes"))
        assertTrue(loader.contains("bitmap.getPixel(x, y)"))
        assertFalse(loader.contains("toPixelMap"))
        assertTrue(loader.contains("Looper.getMainLooper()"))
        val studio = File("src/main/java/com/muyao/wanzaihyperos/ui/home/HomeStudio.kt").readText()
        val tools = studio.substringAfter("壁纸存取").substringBefore("// ─────────────────────────── 素材")
        assertTrue(tools.contains("java.nio.file.Files.move("))
        assertTrue(tools.contains("StandardCopyOption.REPLACE_EXISTING"))
        assertTrue(tools.contains("decodeWallpaperBitmap(file, maxWidth, maxHeight)"))
        assertFalse(tools.contains("toPixelMap"))
    }
}
