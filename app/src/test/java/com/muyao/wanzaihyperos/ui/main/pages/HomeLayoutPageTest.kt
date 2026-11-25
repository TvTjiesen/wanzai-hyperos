package com.muyao.wanzaihyperos.ui.main.pages

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPageTest {
    private val source = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/HomeLayoutPage.kt").readText()
    private val homeSource = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/HomeSectionPage.kt").readText()
    private val editorSource = File("src/main/java/com/muyao/wanzaihyperos/ui/home/HomeLayoutEditor.kt").readText()

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

    @Test fun `preview renders editable blocks but cannot refresh navigate or persist school week`() {
        assertTrue(homeSource.contains("val previewOnly = layoutEditing != null"))
        assertTrue(homeSource.contains("if (!previewOnly && pageActive && entryGate.claim(\"home\", entryOwner))"))
        assertTrue(homeSource.contains("if (previewOnly) {\n            homeContent()\n        } else {\n            PullToRefresh("))
        assertTrue(homeSource.contains("persist = !previewOnly"))
        assertTrue(homeSource.contains("onOpenTimetable = if (previewOnly) ({}) else onOpenTimetable"))
        assertTrue(homeSource.contains("onOpen = { if (!previewOnly)"))
        assertTrue(homeSource.contains("courseEnabled = \"courses\" in (layoutEditing?.settings?.modules ?: UiPrefs.homeModules)"))
        assertTrue(editorSource.contains("HomeLayoutOrder.normalize(editing.settings.homeOrder)"))
    }

    @Test fun `feedback background stays content width while measured toast keeps card offset`() {
        val feedback = homeSource.substringAfter("if (showRefreshToast) {").substringBefore("} else ball()")
        assertTrue(feedback.contains("AiBallFeedbackRow("))
        assertTrue(feedback.contains("ball = ball"))
        assertFalse(feedback.contains("fillMaxWidth"))
        assertTrue(feedback.contains("onRowSizeChanged = { feedbackHeight = with(density) { it.height.toDp() } }"))
        assertTrue(homeSource.contains("val cardTopTarget = if (feedbackHeight > ballBox) feedbackHeight - ballBox / 2 + 8.dp else ballBox / 2"))
        assertTrue(homeSource.contains("targetValue = cardTopTarget"))
    }
}
