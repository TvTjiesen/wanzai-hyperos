package com.muyao.wanzaihyperos.ui.design

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlasmioCardAdapterTest {
    private val layout = SourceScan.stripComments(
        File("src/main/java/com/muyao/wanzaihyperos/ui/design/AppLayout.kt").readText(Charsets.UTF_8),
    )

    private fun function(name: String): String {
        val start = layout.indexOf("fun $name(")
        assertTrue("Missing shared adapter $name", start >= 0)
        val next = Regex("""\n\s*(?:@\w+\s+)*(?:(?:private|internal|public)\s+)?fun\s""")
            .find(layout, start + 4)
        return layout.substring(start, next?.range?.first ?: layout.length)
    }

    @Test
    fun `card uses one opaque continuous frame without recording a backdrop`() {
        val card = function("AppCard")
        assertTrue("Use native Glasmio grouped content", "GlasmioGroupedList(" in card)
        assertTrue("The adapter already owns the frame", "framed = false" in card)
        assertTrue("Use the actual upstream shape package", "import com.kyant.capsule.ContinuousRoundedRectangle" in layout)
        assertTrue("Use the native grouped-card curve", "ContinuousRoundedRectangle(26.dp)" in card)
        assertEquals("Shadow, border and clip must share one shape", 3, Regex("cardShape[),]").findAll(card).count())
        assertTrue("Card surface must stay opaque", ".background(colors.elevatedBackground.copy(alpha = 1f))" in card)
        listOf("GlassSurface(", "GlasmioBackdropHost(", "LocalGlassBackdrop", "sampleBackdrop", "glassBackdropSource", "Card(").forEach {
            assertFalse("Shared cards must not add an optical source or legacy inner card: $it", it in card.substringAfter(") {"))
        }
    }

    @Test
    fun `caller owns the card content scope modifier and padding`() {
        val card = function("AppCard")
        assertTrue("Keep the public ColumnScope slot", "content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit" in card)
        assertTrue("Keep the caller modifier", "val bordered = modifier" in card)
        assertTrue("Keep full width", ".fillMaxWidth()" in card)
        assertTrue("Forward the original slot directly", "content = content" in card)
        assertFalse("Do not introduce hidden card padding", ".padding(" in card)
    }

    @Test
    fun `click and long click retain their separate callbacks and conditional contract`() {
        val card = function("AppCard")
        assertTrue("Long click still requires a click callback", Regex("""onClick != null && onLongPress != null -> Modifier\.combinedClickable\(\s*onClick = onClick,\s*onLongClick = onLongPress,""").containsMatchIn(card))
        assertTrue("Click-only cards remain clickable", "onClick != null -> Modifier.clickable(onClick = onClick)" in card)
        assertTrue("Read-only cards remain non-interactive", "else -> Modifier" in card)
        assertTrue("Attach the chosen interaction to the actual frame", "modifier = bordered.then(interactive)" in card)
    }

    @Test
    fun `dark palette and high contrast border remain effective`() {
        val card = function("AppCard")
        assertTrue("Use the current Glasmio palette", "val colors = GlasmioTheme.colors" in card)
        assertTrue("Follow the existing motion preferences", "val motion = rememberMotion()" in card)
        assertTrue("Use actual theme darkness", "val isDark = colors.isDark" in card)
        assertTrue("High contrast replaces the subtle stroke", "color = if (motion.highContrast) colors.content else AppColors.cardStroke(isDark)" in card)
        assertTrue("Keep the one-dp outline", "width = 1.dp" in card)
        assertTrue("Keep the original shadow elevation", "elevation = 7.dp" in card)
    }

    @Test
    fun `information rows delegate to the card without changing text or insets`() {
        val item = function("InfoItem")
        assertTrue("InfoItem must use the real common card", "AppCard(modifier = modifier, onClick = onClick)" in item)
        assertEquals("Exactly one existing body inset", 1, Regex("Modifier\\.padding\\(Dims\\.cardPadding\\)").findAll(item).count())
        assertTrue("Keep title fallback", "title.ifBlank { \"（无标题）\" }" in item)
        assertTrue("Keep caller-owned trailing color", "trailingColor ?: colors.primary" in item)
        assertTrue("Keep caller-owned body truncation", "maxLines = bodyMaxLines" in item)
        assertTrue("Keep multiline ellipsis", "overflow = TextOverflow.Ellipsis" in item)
        assertFalse("Do not retain a legacy card underneath", Regex("""(?<!App)\bCard\(""").containsMatchIn(item))
    }
}
