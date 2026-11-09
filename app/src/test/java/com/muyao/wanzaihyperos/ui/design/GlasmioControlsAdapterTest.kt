package com.muyao.wanzaihyperos.ui.design

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlasmioControlsAdapterTest {
    private val ui = File("src/main/java/com/muyao/wanzaihyperos/ui")
    private val controls = SourceScan.stripComments(File(ui, "components/WanzaiGlasmioControls.kt").readText(Charsets.UTF_8))

    @Test
    fun `legacy button slots receive native foreground color without changing the default emphasis`() {
        val button = controls.substringAfter("fun WanzaiButton(").substringBefore("fun WanzaiTextButton(")
        assertTrue("Keep the existing regular default", "prominent: Boolean = false" in button)
        assertEquals("Both native and reduced-motion slots bridge their actual foreground", 2,
            Regex("LocalContentColor provides LocalContentColor.current").findAll(button).count())
        assertTrue("Forward enabled to both control paths", Regex("enabled = enabled").findAll(button).count() == 2)
        assertTrue("Text actions stay regular", "enabled = enabled, prominent = false" in controls)
    }

    @Test
    fun `dialog viewport is bounded and scrolls title summary and caller content together`() {
        val dialog = controls.substringAfter("fun WanzaiOverlayDialog(")
        assertTrue("Use the actual window height", "LocalWindowInfo.current.containerSize.height.toDp()" in dialog)
        assertTrue("Reserve space for panel padding and system insets", "(windowHeight - 160.dp).coerceAtLeast(1.dp)" in dialog)
        assertTrue("Bound the viewport before enabling vertical scroll", ".heightIn(max = bodyMaxHeight).verticalScroll(rememberScrollState())" in dialog)
        assertTrue("Preserve the public content scope", "content: @Composable ColumnScope.() -> Unit" in dialog)
        assertTrue("The header must be part of the scroll body, not duplicated outside", "titleContent = {}" in dialog)
        assertTrue("Both dialog implementations share the bounded body", "content = dialogBody" in dialog)
        assertTrue("Preserve title and summary", "Text(title," in dialog && "summary?.let { Text(it," in dialog)
        assertTrue("Keep native dismissal and exit animation", "visible = show" in dialog && "onDismissRequest = onDismissRequest" in dialog)
        val body = dialog.substringAfter("val dialogBody: @Composable ColumnScope.() -> Unit = {")
        assertTrue("Consume back in the dialog window content", "BackHandler(enabled = show) { onDismissRequest() }" in body)
        assertFalse("Never navigate out of the app from this adapter", "onBack(" in dialog || "finish(" in dialog)
    }

    @Test
    fun `all approved button pages use aliases and retain the login return label`() {
        val paths = listOf("zy/ZyPage.kt", "zy/ZyReplayPage.kt", "jw/JwPage.kt", "login/LoginPage.kt",
            "busyness/BusynessPage.kt", "szty/SztyPage.kt", "web/ZhgyGatePage.kt", "tronclass/CourseDetailPage.kt",
            "tronclass/HomeworkDetailPage.kt", "tronclass/RollcallStationPage.kt", "tronclass/ScannerPage.kt", "tronclass/QrScannerPage.kt")
        paths.forEach { path ->
            val page = SourceScan.stripComments(File(ui, path).readText(Charsets.UTF_8))
            assertTrue("Missing actual button alias: $path", "import com.muyao.wanzaihyperos.ui.components.WanzaiButton as Button" in page)
            assertFalse("Legacy button import must not remain: $path", "import top.yukonga.miuix.kmp.basic.Button" in page)
        }
        val login = SourceScan.stripComments(File(ui, "login/LoginPage.kt").readText(Charsets.UTF_8))
        assertTrue("The original anti-duplicate-submit return target must remain", "if (submitting) return@Button" in login)
        assertTrue("Explicit onPrimary lock and progress indicator require the prominent login surface", "prominent = true" in login)
    }
}
