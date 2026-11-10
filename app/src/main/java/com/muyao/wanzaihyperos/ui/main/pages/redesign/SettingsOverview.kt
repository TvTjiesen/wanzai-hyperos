package com.muyao.wanzaihyperos.ui.main.pages.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.muyao.wanzaihyperos.ui.design.SettingGlyph
import com.muyao.wanzaihyperos.ui.design.SettingIcon
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.liquid.PageChromePilot
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.glasmioGroupedListSeparator

fun LazyListScope.settingsTitleItem(pilot: PageChromePilot, leading: @Composable () -> Unit) {
    item(key = "large_title") {
        Column(
            Modifier.fillMaxWidth()
                .onSizeChanged { if (it.height > 0) pilot.titleHeightPx = it.height.toFloat() }
                .padding(top = statusBarTopPadding() + 18.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(end = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "设置",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = IosTypography.largeTitle.copy(fontWeight = FontWeight.Bold),
                    color = GlasmioTheme.colors.content,
                )
                leading()
            }
            Text(
                "连接校园，按你的方式使用。",
                style = IosTypography.subheadline,
                color = GlasmioTheme.colors.secondaryContent,
            )
        }
    }
}

@Composable
fun SettingsConnectionCard(summary: String, sourceCount: String, reading: Boolean, onClick: () -> Unit) {
    val colors = GlasmioTheme.colors
    GlasmioGroupedList {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看账号与连接", onClick = onClick)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(colors.accent.copy(alpha = 0.12f), GlasmioTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) { SfIcon("person.crop.circle", null, size = 28.dp, tint = colors.accent) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("账号与连接", style = IosTypography.title2, color = colors.content)
                    Text("各来源独立登录", style = IosTypography.subheadline, color = colors.secondaryContent)
                }
                SfIcon("chevron.forward", null, size = 18.dp, tint = colors.tertiaryContent)
            }
            Text(
                summary,
                style = IosTypography.headline.merge(TextStyle(fontFeatureSettings = Type.tabular)),
                color = colors.accent,
            )
            Text(
                if (reading) "正在读取真实连接状态，不将未知视为未登录。" else "$sourceCount · 各系统的登录状态互不影响。",
                style = IosTypography.footnote,
                color = colors.secondaryContent,
            )
        }
    }
}

@Composable
fun SettingsGroupTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 10.dp, bottom = 8.dp).semantics { heading() },
        style = IosTypography.headline,
        color = GlasmioTheme.colors.secondaryContent,
    )
}

/** Native list typography and separators, with growing rows instead of the fixed 68dp cell. */
@Composable
fun SettingsOverviewRow(
    icon: SettingIcon,
    tint: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = GlasmioTheme.colors
    val largeText = LocalDensity.current.fontScale >= 1.2f
    val accessory: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            trailing?.invoke()
            if (trailingChevron) SfIcon("chevron.forward", null, size = 16.dp, tint = colors.tertiaryContent)
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .glasmioGroupedListSeparator(colors.separator, horizontalInset = 58.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick) else Modifier)
            .heightIn(min = 72.dp).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(30.dp).background(tint.copy(alpha = 0.10f), GlasmioTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) { SettingGlyph(icon = icon, tint = tint) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = IosTypography.body, color = colors.content)
                subtitle?.let { Text(it, style = IosTypography.subheadline, color = colors.secondaryContent) }
            }
            if (!largeText && (trailing != null || trailingChevron)) accessory()
        }
        if (largeText && (trailing != null || trailingChevron)) {
            Box(Modifier.fillMaxWidth().padding(start = 44.dp), contentAlignment = Alignment.CenterEnd) { accessory() }
        }
    }
}
