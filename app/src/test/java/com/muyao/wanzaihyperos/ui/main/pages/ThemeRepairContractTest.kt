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
        assertTrue(appearance.contains("PhoneThemePreview(") && appearance.contains("ThemeOverviewPreview("))
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
