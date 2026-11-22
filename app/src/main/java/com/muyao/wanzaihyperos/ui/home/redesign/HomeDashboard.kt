package com.muyao.wanzaihyperos.ui.home.redesign

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList

@Composable
fun HomeDashboardGroup(compact: Boolean, modifier: Modifier = Modifier, narrow: Boolean = false, stretch: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val alpha = com.muyao.wanzaihyperos.ui.design.LocalThemeCustomization.current.cardOpacity / 100f
    GlasmioGroupedList(modifier = modifier.then(Modifier.clipHomeCard()), framed = false) {
        Column(
            Modifier.fillMaxWidth().then(if (stretch) Modifier.fillMaxHeight() else Modifier)
                .background(GlasmioTheme.colors.elevatedBackground.copy(alpha = alpha))
                .padding(if (narrow) 12.dp else if (compact) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun HomeQuickActions(
    backdrop: LayerBackdrop,
    compact: Boolean,
    onOpenTimetable: () -> Unit,
    onOpenJw: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val twoRows = maxWidth < 304.dp || LocalDensity.current.fontScale >= 1.3f
        val gap = if (compact) 8.dp else 10.dp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                HomeQuickAction(backdrop, "课表", onOpenTimetable, Modifier.weight(1f), "整周课表")
                HomeQuickAction(backdrop, "教务", onOpenJw, Modifier.weight(1f), "教务与成绩")
                if (!twoRows) {
                    HomeQuickAction(backdrop, "消息", onOpenMessages, Modifier.weight(1f), "消息与通知")
                    HomeQuickAction(backdrop, "主题", onOpenAppearance, Modifier.weight(1f), "主题设置")
                }
            }
            if (twoRows) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                HomeQuickAction(backdrop, "消息", onOpenMessages, Modifier.weight(1f), "消息与通知")
                HomeQuickAction(backdrop, "主题", onOpenAppearance, Modifier.weight(1f), "主题设置")
            }
        }
    }
}

@Composable
private fun HomeQuickAction(backdrop: LayerBackdrop, label: String, onClick: () -> Unit, modifier: Modifier, description: String) {
    val icon = when (label) {
        "课表" -> MarkIcon.Schedule
        "教务" -> MarkIcon.Book
        "消息" -> MarkIcon.Message
        else -> MarkIcon.Appearance
    }
    GlassSurface(
        backdrop = backdrop,
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp).semantics { contentDescription = description },
        shape = com.kyant.capsule.ContinuousRoundedRectangle(18.dp),
        sampleBackdrop = rememberMotion().glassIsTranslucent,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            BodyMark(icon, tint = GlasmioTheme.colors.content, size = 16.dp)
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = Type.caption, fontWeight = FontWeight.Medium, color = GlasmioTheme.colors.content)
        }
    }
}

private fun Modifier.clipHomeCard(): Modifier = this.then(Modifier.clip(com.kyant.capsule.ContinuousRoundedRectangle(26.dp)))
