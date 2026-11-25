package com.muyao.wanzaihyperos.ui.main.pages

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRepairContractTest {
    private fun blockAfter(source: String, anchor: String): String {
        val start = source.indexOf(anchor)
        assertTrue("Missing source anchor: $anchor", start >= 0)
        val open = source.indexOf('{', start + anchor.length)
        assertTrue("Missing block after: $anchor", open >= 0)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        throw AssertionError("Unclosed block after: $anchor")
    }
    private val root = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages")
    private val components = File(root, "ThemeSettingsComponents.kt").readText()
    private val appearance = File(root, "AppearancePage.kt").readText()

    @Test fun `setting segments stack children vertically`() {
        val segment = blockAfter(components, "internal fun ThemeSettingSegment(")
        assertTrue("ThemeSettingSegment must use a vertical layout", segment.contains("Column("))
        assertTrue("segment children need spacing, not overlapping Box placement", segment.contains("verticalArrangement = Arrangement.spacedBy("))
        assertTrue(segment.contains("content()"))
    }

    @Test fun `appearance overview invokes phone preview`() {
        assertTrue(appearance.contains("PhoneThemePreview { Preview() }") && appearance.contains("ThemeOverviewPreview("))
    }

    @Test fun `wallpaper removal is confirmed and excludes bundled wallpaper`() {
        assertTrue(appearance.contains("pendingRemoval = entry"))
        assertTrue(appearance.contains("show = pendingRemoval != null"))
        assertTrue(appearance.contains("entries.filter { it.id != \"builtin-sea\" }"))
        val deleteClick = blockAfter(appearance, "WanzaiButton(onClick = {\n                val entry = pendingRemoval")
        assertTrue(deleteClick.contains("pendingRemoval ?: return@WanzaiButton"))
        assertTrue(deleteClick.contains("library.remove(entry)"))
        assertFalse("requesting deletion must not remove the image immediately", appearance.substringBefore("pendingRemoval = entry").contains("library.remove("))
        assertTrue("confirmation precedes library mutation", deleteClick.indexOf("pendingRemoval ?: return@WanzaiButton") < deleteClick.indexOf("library.remove(entry)"))
        assertTrue("removing a library copy must leave the applied home wallpaper intact", deleteClick.contains("当前首页壁纸保留"))
    }

    @Test fun `layout entrance animation respects reduced motion`() {
        val anchor = "visibleState = layoutVisibility,"
        assertTrue(appearance.contains(anchor))
        val animation = appearance.substringAfter(anchor).substringBefore(") {\n")
        assertFalse("the sliding page itself must not swallow pointer events", animation.contains("pointerInput"))
        assertTrue("background needs its own touch shield", appearance.indexOf("Box(Modifier.matchParentSize().pointerInput") < appearance.indexOf(anchor))
        assertTrue(animation.contains("if (motion.canMove)"))
        assertTrue(animation.contains("slideInHorizontally("))
        assertTrue(animation.contains("slideOutHorizontally("))
        assertTrue(animation.contains("EnterTransition.None"))
        assertTrue(animation.contains("ExitTransition.None"))
    }

    @Test fun `wallpaper section starts with visibility and real image sources`() {
        val home = appearance.substringAfter("item(key = \"section-home\")").substringBefore("item(key = \"preview\")")
        assertTrue(home.indexOf("AppearanceToggle(\"显示首页壁纸\"") in 0 until home.indexOf("VisualChoice("))
        assertTrue(home.contains("editorSource = captureSource(\"builtin-sea\")"))
        assertTrue(home.contains("editorOrigin = editorSource?.bounds"))
        assertTrue(home.contains("sampleModifier = Modifier.wallpaperMotionCoordinates { wallpaperSources[\"builtin-sea\"] = it }"))
        assertTrue(home.contains("sampleModifier = Modifier.wallpaperMotionCoordinates { wallpaperSources[\"current-preview\"] = it }"))
        assertTrue(home.contains("WallpaperCanvas(current.copy(container = \"designed\"), loaded, Modifier.fillMaxSize())"))
        assertFalse(appearance.contains("PhoneThemePreview(Modifier.wallpaperMotionCoordinates"))
        assertTrue(appearance.contains("editorSource = null; editorOrigin = null"))
    }

    @Test fun `theme choices use shared glass buttons and preserve disabled state`() {
        val visual = blockAfter(appearance, "private fun VisualChoice(")
        val choice = blockAfter(appearance, "internal fun AppearanceChoice(")
        listOf(visual, choice).forEach { source ->
            assertTrue(source.contains("WanzaiButton(onClick = onClick, enabled = enabled"))
            assertTrue(source.contains("this.selected = selected"))
            assertTrue(source.contains("colors.primaryContainer"))
            assertFalse(source.contains(".clickable("))
        }
        assertTrue(visual.contains("sampleModifier: Modifier = Modifier, onClick: () -> Unit)"))
        assertTrue(visual.contains(".then(sampleModifier)"))
        val toggle = blockAfter(appearance, "private fun AppearanceToggle(")
        assertTrue(toggle.contains("onCheckedChange = { if (enabled) onToggle() }"))
        assertTrue(toggle.contains("if (!enabled) Box(Modifier.matchParentSize().pointerInput"))
        assertTrue(toggle.contains("clearAndSetSemantics"))
    }

    @Test fun `wallpaper reveal crops a real canvas and can reverse without stretching`() {
        val editor = File(root, "WallpaperEditor.kt").readText()
        assertTrue(editor.contains("internal fun WallpaperCanvas("))
        assertTrue(editor.contains("LiveHomePreview("))
        assertTrue(editor.contains("BackHandler { dismiss() }"))
        assertTrue(editor.contains("LaunchedEffect(closing, move)"))
        assertTrue(editor.contains("val target = if (closing) 0f else 1f"))
        assertTrue(editor.contains("motionRevealRect(originBounds, destination, transition.value)"))
        assertTrue(editor.contains("Constraints.fixed(width, height)"))
        assertFalse(editor.contains("scaleX ="))
        assertFalse(editor.contains("scaleY ="))
        assertFalse(editor.contains("}.graphicsLayer { alpha = transition.value.coerceIn"))
    }

    @Test fun `wallpaper fallback fades only the preview and background reads progress while drawing`() {
        val editor = File(root, "WallpaperEditor.kt").readText()
        assertTrue(editor.contains("}.drawBehind { drawRect(colors.background.copy(alpha = transition.value.coerceIn(0f, 1f))) }"))
        assertFalse(editor.contains("}.background(colors.background.copy(alpha = transition.value"))
        assertTrue(editor.contains("alpha = if (move && geometryUsable && originBounds != null && previewBounds != null) 1f"))
        assertTrue(editor.contains("else transition.value.coerceIn(0f, 1f)"))
        assertTrue(editor.contains("if (closing) result?.let(finish) ?: cancel()"))
    }

    @Test fun `theme section animation does not read frame progress around child composition`() {
        val section = blockAfter(components, "internal fun ThemeExpandableSection(")
        assertTrue(section.contains("val progress = transition.segmentProgress(\"SegmentedHeader\")"))
        assertFalse(section.contains("progress.value"))
        assertTrue(section.contains("ThemeExpandableHeader(title, summary, icon, expanded, enabled, toggle, progress)"))
        assertTrue(section.contains("val gap = arrangement.spacing.roundToPx()"))
        assertTrue(section.contains("verticalArrangement = arrangement, content = content"))
        val header = blockAfter(components, "private fun ThemeExpandableHeader(")
        assertTrue(header.contains("progress.value.coerceIn(0f, 1f)"))
        val arrangement = blockAfter(components, "private class ThemeSectionArrangement(")
        assertTrue(arrangement.contains("override val spacing get() = (2f * (1f - progress.value.coerceIn(0f, 1f))).dp"))
        assertTrue(arrangement.contains("val gap = spacing.roundToPx()"))
        assertTrue(arrangement.contains("outPositions[index] = position"))
    }

    @Test fun `all theme chevrons keep their accessory inline`() {
        val uses = listOf(
            components.substringAfter("ThemeOptionMenu(").substringBefore("ThemePagerMenu("),
            components.substringAfter("internal fun ThemePagerMenu(").substringBefore("internal fun themePreviewAspect("),
            blockAfter(appearance, "internal fun AppearanceSectionHeader("),
        )
        uses.forEachIndexed { index, source ->
            assertTrue("arrow call ${index + 1} must keep trailing accessory inline", source.contains("trailingInline = true"))
        }
        assertTrue("section header supplies its chevron as a trailing accessory", uses.last().contains("trailing = {"))
    }
}
