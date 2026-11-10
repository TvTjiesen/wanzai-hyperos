package com.muyao.wanzaihyperos.ui.home.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.components.WanzaiButton
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList

@Composable
fun HomeDashboardGroup(compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    GlasmioGroupedList {
        Column(
            Modifier.fillMaxWidth().background(GlasmioTheme.colors.elevatedBackground.copy(alpha = 1f))
                .padding(if (compact) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun HomeQuickActions(
    compact: Boolean,
    onOpenTimetable: () -> Unit,
    onOpenJw: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    HomeDashboardGroup(compact) {
        Text("快捷操作", style = io.github.neoruaa.glasmio.core.IosTypography.title3, fontWeight = FontWeight.SemiBold, color = GlasmioTheme.colors.content)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeQuickAction("整周课表", onOpenTimetable, Modifier.weight(1f))
            HomeQuickAction("教务与成绩", onOpenJw, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeQuickAction("消息与通知", onOpenMessages, Modifier.weight(1f))
            HomeQuickAction("外观与壁纸", onOpenAppearance, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeQuickAction(label: String, onClick: () -> Unit, modifier: Modifier) {
    WanzaiButton(onClick = onClick, modifier = modifier) {
        Text(label, fontSize = Type.secondary, fontWeight = FontWeight.Medium)
    }
}
