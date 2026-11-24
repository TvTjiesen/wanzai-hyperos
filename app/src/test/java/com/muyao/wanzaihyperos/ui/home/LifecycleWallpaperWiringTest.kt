package com.muyao.wanzaihyperos.ui.home

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleWallpaperWiringTest {
    private fun source(name: String) = SourceScan.sources().first { it.first == name }.second
    private fun page(name: String) = source("main/ui/main/pages/$name.kt")

    @Test fun threePagesLoadBitmapAndStatsAsOneBackgroundResultWithoutPublishingCancelledOrOldRevision() {
        val loaders = listOf(
            Triple("HomeSectionPage", "LaunchedEffect(context, wallpaperRev,", "val wallpaper ="),
            Triple("AppearancePage", "LaunchedEffect(album, selected?.id, wallpaperRevision)", "val picker ="),
            Triple("WallpaperCropPage", "LaunchedEffect(context, rev)", "val wallpaper ="),
        )
        for ((name, start, end) in loaders) {
            val text = page(name)
            assertFalse("$name still synchronously decodes", text.contains("decodeWallpaper("))
            assertTrue(text.contains("mutableStateOf<LoadedWallpaper?>(null)"))
            assertTrue("$name loader effect must exist", text.contains(start))
            val effect = text.substringAfter(start).substringBefore(end)
            val publication = if (name == "AppearancePage") "loaded = decoded" else "loadedWallpaper = loaded"
            assertTrue("$name does not call IO loader", effect.contains("loadWallpaper("))
            assertTrue("$name must reject cancelled result", effect.contains("currentCoroutineContext().ensureActive()"))
            assertTrue("$name must reject obsolete revision", effect.contains("UiPrefs.wallpaperRev !="))
            assertTrue("$name must publish after cancellation check", effect.indexOf("ensureActive()") in 0 until effect.indexOf(publication))
            assertTrue("$name must publish after revision check", effect.indexOf("UiPrefs.wallpaperRev !=") in 0 until effect.indexOf(publication))
            assertTrue("$name must rethrow cancellation", effect.contains("throw cancel"))
            assertTrue("$name must settle its loading flag even on failure", effect.contains("finally {") &&
                effect.contains(if (name == "AppearancePage") "imageLoading = false" else "wallpaperLoading = false"))
            assertFalse("$name must not remove a private file during load", effect.contains(".delete("))
        }
        val home = page("HomeSectionPage")
        assertTrue(home.contains("else loadedWallpaper?.stats"))
        assertFalse(home.contains("wallpaperStats(it)"))
        assertTrue(home.contains("SkyPreset.of(UiPrefs.wallpaperPreset) != requestedPreset"))
        val appearance = page("AppearancePage")
        assertTrue("selected entry changes must reject the old preview", appearance.contains("selected?.id != entry?.id"))
        assertTrue(appearance.contains("if (entry == null) wallpaperFile(context) else library.file(entry)"))
    }

    @Test fun loadingIsNotEvidenceOfMissingPhotoAndCropRetainsDraftOnlyApplyContract() {
        val appearance = page("AppearancePage")
        assertTrue(appearance.contains("mutableStateOf(album)"))
        assertTrue("loading must disable entry into an empty image editor", appearance.contains("!busy && !imageLoading"))
        assertTrue("loading has its own visible message", appearance.contains("if (imageLoading) Text(\"正在读取本机照片…\""))
        assertFalse("preview reads must never delete a photo", appearance.substringAfter("LaunchedEffect(album, selected?.id, wallpaperRevision)")
            .substringBefore("val picker =").contains("deleteWallpaper("))
        val crop = page("WallpaperCropPage")
        assertTrue(crop.indexOf("if (wallpaperLoading)") < crop.indexOf("else if (wallpaper == null)"))
        assertTrue(crop.contains("不会自动删除"))
        assertTrue(crop.contains("UiPrefs.saveWallpaperCrop(panX, panY, zoom)"))
        assertTrue(crop.contains("var confirmRemove by remember"))
        assertTrue(crop.contains("onClick = onBack"))
    }

    @Test fun skyClockIsVisibleAndStartedOnlyAndDebugOverrideNeverStartsIt() {
        val sky = source("main/ui/home/HomeSky.kt")
        val clock = sky.substringAfter("var liveHour by remember").substringBefore("val hour = hourOverride ?: liveHour")
        assertTrue(clock.contains("LaunchedEffect(hourOverride, pageActive, lifecycleOwner)"))
        assertTrue(clock.contains("if (hourOverride != null || !pageActive) return@LaunchedEffect"))
        assertTrue(clock.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertTrue(clock.indexOf("liveHour = java.time.LocalTime.now().hour") < clock.indexOf("while (true)"))
        assertTrue(clock.contains("delay(10 * 60 * 1000L)"))
        assertTrue(sky.contains("Canvas(modifier = backgroundModifier.fillMaxSize().clipToBounds())"))
        val home = page("HomeSectionPage")
        assertEquals(7, Regex("\\.collectAsVisibleStateWithLifecycle\\(\\)").findAll(home).count())
        assertFalse(home.contains(".collectAsStateWithLifecycle()"))
        assertTrue(home.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
    }

    @Test fun fourTabsUnsubscribeWhenInvisibleAndDoNotCollectBelowStarted() {
        val sky = source("main/ui/home/HomeSky.kt")
        val helper = sky.substringAfter("internal fun <T> StateFlow<T>.collectAsVisibleStateWithLifecycle()").substringBefore("enum class SkyPreset")
        assertTrue(helper.contains("val active = LocalPageActive.current"))
        assertTrue(helper.contains("remember(this, active) { if (active) this else emptyFlow<T>() }"))
        assertTrue(helper.contains("collectAsStateWithLifecycle(initialValue = value, minActiveState = Lifecycle.State.STARTED)"))
        assertFalse(helper.contains("Lifecycle.State.RESUMED"))
        // 数量是「每一处订阅都必须经过可见性闸门」的登记簿：新增订阅就得在这里加，
        // 逼人确认它真的走了 collectAsVisibleStateWithLifecycle 而不是裸 collectAsState。
        // NotifySectionPage 11 → 14：2026-11-24 消息页加了三来源（日常通知/爱讲座/门户），
        // 对应 notices.state、lectures.state、lectures.hasMore 三处，都走同一个闸门。
        for ((name, count) in mapOf("TimetableSectionPage" to 2, "AppsSectionPage" to 5, "NotifySectionPage" to 14, "SettingsSectionPage" to 0)) {
            val text = page(name)
            assertEquals(name, count, Regex("\\.collectAsVisibleStateWithLifecycle\\(\\)").findAll(text).count())
            assertFalse("$name still has unrestricted flow collection", text.contains(".collectAsState()"))
        }
        val settings = page("SettingsSectionPage")
        assertFalse(settings.contains("graph.inFlight"))
        val localRead = settings.substringAfter("LaunchedEffect(graph, uiState.loggedIn, refreshKey, pageActive, entryOwner)")
            .substringBefore("val pilot =")
        assertTrue(localRead.indexOf("if (!pageActive)") < localRead.indexOf("withContext(Dispatchers.IO)"))
    }

    @Test fun logoutDoesNotSwallowStorageFailureOrCancellationAndFeedbackSurvivesPartialLogout() {
        val text = page("AccountsPage")
        val action = text.substringAfter("onClick = if (loggingOut) null else").substringBefore("logoutFeedback?.let")
        assertFalse(action.contains("runCatching"))
        assertTrue(action.contains("try {"))
        assertTrue(action.indexOf("graph.logout()") < action.indexOf("本机退出操作已完成"))
        val failure = action.substringAfter("catch (_: Exception)").substringBefore("finally")
        assertTrue(failure.contains("未能确认退出完成"))
        assertTrue(failure.contains("部分凭据可能仍在"))
        assertFalse(failure.contains("本机退出操作已完成"))
        assertTrue(action.contains("catch (cancelled: CancellationException)"))
        assertTrue(action.contains("throw cancelled"))
        assertTrue(action.substringAfter("finally").contains("loggingOut = false"))
        assertTrue(text.contains("logoutFeedback?.let { feedback ->"))
        assertFalse(failure.contains(".message"))
    }
}
