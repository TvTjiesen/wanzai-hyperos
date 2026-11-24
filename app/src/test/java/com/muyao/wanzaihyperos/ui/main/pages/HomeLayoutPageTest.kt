package com.muyao.wanzaihyperos.ui.main.pages

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPageTest {
    private val source = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/HomeLayoutPage.kt").readText()

    @Test fun `layout controls use shared settings surfaces and glass slider`() {
        assertTrue(source.contains("SecondaryScaffold(title = \"首页布局\""))
        assertTrue(source.contains(".themeSegment(last = false)"))
        assertTrue(source.contains(".themeSegment(last = true)"))
        assertTrue(source.contains("AppearanceChoice("))
        assertTrue(source.contains("ValueSlider(value, maximum"))
        assertFalse(source.contains("GlassSurface("))
    }

    @Test fun `module semantics and debounced persistence remain intact`() {
        assertTrue(source.contains("modules = if (id in draft.modules) draft.modules - id else draft.modules + id"))
        assertTrue(source.contains("draft = draft.copy(appsCollapsed = !draft.appsCollapsed)"))
        assertTrue(source.contains("delay(200); UiPrefs.applyAppearance(draft)"))
        assertTrue(source.contains("onDispose { UiPrefs.applyAppearance(latest) }"))
        assertTrue(source.contains("if (it.length <= 24 && it.none(Char::isISOControl))"))
        assertTrue(source.contains("draft.density == id"))
    }
}
