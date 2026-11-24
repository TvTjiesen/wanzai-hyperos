package com.muyao.wanzaihyperos.ui.main.pages

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppearanceOneStopTest {
    private fun source(path: String) = File("src/main/java/com/muyao/wanzaihyperos/$path").readText()
    @Test fun optionsSaveImmediatelyWithoutApplyOrDiscardFlow() {
        val page = source("ui/main/pages/AppearancePage.kt")
        assertTrue(page.contains("current = current.copy"))
        assertTrue(page.contains("UiPrefs.applyAppearance(next)"))
        assertFalse(page.contains("undo.prepare"))
        assertFalse(page.contains("confirmLeave"))
        assertFalse(page.contains("应用全部更改"))
        assertFalse(page.contains("使用桌面壁纸"))
        assertFalse(source("ui/design/liquid/GlassBar.kt").contains("customIcon"))
    }
    @Test fun previewIsLightAndDoesNotRenderASecondLiveHome() {
        val page = source("ui/main/pages/AppearancePage.kt")
        val editor = source("ui/main/pages/WallpaperEditor.kt")
        assertTrue(page.contains("WanzaiTheme(AppThemeMode.Light"))
        assertTrue(page.contains("ThemeOverviewPreview(graph,"))
        assertTrue(editor.contains("LiveHomePreview("))
        assertTrue(editor.contains("fullPage = true"))
        assertTrue(editor.contains("editing = false"))
        assertFalse(editor.contains("graph.fetch"))
        assertFalse(editor.contains("collectAsVisibleStateWithLifecycle"))
        assertFalse(editor.contains("GlassSurface("))
        assertTrue(editor.contains("LocalPageActive provides false"))
        assertTrue(editor.contains("hourOverride = 12"))
    }
    @Test fun wallpaperEditorWaitsForMeasuredBoundsWithoutPermanentlyInvalidatingMotion() {
        val editor = source("ui/main/pages/WallpaperEditor.kt")
        assertTrue(editor.contains("snapshotFlow { previewBounds to dialogBounds }"))
        assertTrue(editor.contains("preview?.let { it.width > 0f && it.height > 0f }"))
        assertFalse(editor.contains("geometryInvalid"))
        assertTrue(editor.contains("it.setWindowAnimations(0)"))
        assertTrue(editor.contains("it.setDimAmount(0f)"))
        assertTrue(editor.contains("it.setBackgroundDrawableResource(android.R.color.transparent)"))
        assertTrue(editor.contains("if (closing) result?.let(finish) ?: cancel()"))
    }
    @Test fun singlePreferenceCommitPublishesWholeAppearanceAndRetainsBusinessKeys() {
        val prefs = source("data/UiPrefs.kt").substringAfter("fun applyAppearance(").substringBefore("fun resetAppearance")
        assertEquals(1, Regex("editor.commit\\(\\)").findAll(prefs).count())
        assertTrue(prefs.contains("else editor?.apply()"))
        assertTrue(source("ui/home/AppearanceUndo.kt").contains("persistSynchronously = true"))
        assertTrue(prefs.contains("Snapshot.withMutableSnapshot"))
        assertFalse(prefs.contains(".clear()"))
        assertTrue(prefs.contains("KEY_THEME, v.theme"))
        assertTrue(prefs.contains("setReduceTransparency(v.reduceTransparency)"))
        assertTrue(prefs.contains("setRefractsGlass(v.glass == \"liquid\")"))
    }
    @Test fun themeHasOneEntryAndShellDoesNotSaveTwice() {
        assertFalse(source("ui/main/pages/SettingsSectionPage.kt").contains("AppThemeMode.entries.forEach"))
        val route = source("ui/main/WanzaiMainShell.kt").substringAfter("AppearancePage(").substringBefore("Route.")
        assertTrue(route.contains("uiState.themeMode = it"))
        assertFalse(route.contains("saveThemeMode"))
        assertTrue(source("ui/AppTheme.kt").contains("appearanceSettings = appearance"))
    }
}
