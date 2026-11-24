package com.muyao.wanzaihyperos.ui.main.pages

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppearanceOrderOnceTest {
    private val page = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppearancePage.kt").readText()
    @Test fun threeSectionsExpandInPlace() {
        val sections = listOf("home", "colors", "interaction")
        val keys = listOf("preview") + sections.map { "section-$it" } + "restore-default"
        val positions = keys.map { page.indexOf("item(key = \"$it\")").also { position -> assertTrue("Missing section: $it", position >= 0) } }
        assertEquals(positions.sorted(), positions)
        assertEquals(positions.size, positions.toSet().size)
        sections.forEachIndexed { index, id ->
            val item = page.substring(positions[index + 1], positions[index + 2])
            assertTrue("$id expands independently in place", "ThemeExpandableSection(" in item && "\"$id\" in expandedSections" in item)
        }
        val components = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/ThemeSettingsComponents.kt").readText()
        assertTrue("spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)" in components)
        assertTrue("placeable.height * state.value" in components && "verticalArrangement = Arrangement.spacedBy(gap)" in components)
        assertTrue("Each segment registers independent height progress", "transition.segmentProgress(\"SegmentedProgress\")" in components)
        assertTrue("themeSegment(last)" in page)
        assertFalse(page.contains("section-background"))
        assertFalse(page.contains("section-appearance"))
        assertTrue(page.contains("背景与颜色"))
        assertTrue(page.contains("首页壁纸"))
        assertFalse(page.contains("stickyHeader(key = \"categories\")"))
        assertFalse(page.contains("section-package"))
        assertFalse(page.contains("GlassSurface("))
    }
    @Test fun themesColorsMaterialAndLayoutHaveVisibleSamples() {
        listOf("AppColors.pageBrush(false, background)", "SkyPreview(sky,").forEach {
            assertTrue("Missing visual sample: $it", page.contains(it))
        }
        assertTrue(page.contains("HomeLayoutPage(onClose"))
        val layout = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/HomeLayoutPage.kt").readText()
        assertFalse("贴纸功能已整体移除", layout.contains("HomeSticker"))
        assertTrue(layout.contains("draft.modules - id") && layout.contains("draft.modules + id"))
        assertTrue(page.contains("height(64.dp)"))
        assertFalse(page.contains("AppColors.pageBrush(true, background)"))
        assertTrue(page.indexOf("系统自带壁纸") < page.indexOf("添加壁纸"))
        assertTrue(page.contains("library.bundledSea()"))
    }
    @Test fun phonePreviewUsesReferenceScreenRatioAndWidthCap() {
        assertEquals(.46f, themePreviewAspect(460, 1000), .0001f)
        assertEquals(.42f, themePreviewAspect(360, 900), .0001f)
        assertEquals(.62f, themePreviewAspect(900, 390), .0001f)
        assertEquals(.46f, themePreviewAspect(0, 0), .0001f)
        assertEquals(244.8f, themePreviewWidth(360f), .0001f)
        assertEquals(300f, themePreviewWidth(720f), .0001f)
        val components = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/ThemeSettingsComponents.kt").readText()
        assertTrue("source bounds must wrap the enlarged phone shell", components.contains("Box(modifier.width(width).height(width / aspect)"))
    }

    @Test fun liveResetDoesNotDeleteImages() {
        val reset = page.substringAfter("show = confirmReset,").substringBefore("\n@Composable")
        assertTrue(reset.contains("current = AppearanceSettings()"))
        assertFalse(reset.contains("library.remove("))
        assertFalse(reset.contains(".delete("))
        assertFalse(page.contains("UiPrefs.resetAppearance"))
        assertTrue(page.contains("library.remove(entry)"))
        assertTrue(page.contains("pendingRemoval != null"))
        assertTrue(page.contains("it.id != \"builtin-sea\""))
        assertFalse(page.contains("confirmLeave"))
    }
}
