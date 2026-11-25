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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

fun LazyListScope.settingsTitleItem(pilot: PageChromePilot) {
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
            }
            Text(
                "让界面顺手，也让连接有据可查。",
                style = IosTypography.subheadline,
                color = GlasmioTheme.colors.secondaryContent,
            )
        }
    }
}

@Composable
fun SettingsConnectionCard(
    configuration: com.muyao.wanzaihyperos.data.account.AccountConfigurationSummary,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val colors = GlasmioTheme.colors
    val density = LocalDensity.current
    val ballBox = 72.dp
    var feedbackSize by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val feedbackWidth = maxWidth / 2
        val cardTop = with(density) {
            if (feedbackSize.height.toDp() > ballBox) feedbackSize.height.toDp() + 8.dp else ballBox / 2
        }
        Box(Modifier.fillMaxWidth().padding(top = cardTop)) {
            GlasmioGroupedList {
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "查看账号与连接", onClick = onClick)
                        .heightIn(min = 116.dp).padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 76.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("账号与连接", style = IosTypography.title2, color = colors.content)
                            Text(configuration.countText(), style = IosTypography.subheadline.merge(TextStyle(fontFeatureSettings = Type.tabular)), color = colors.accent)
                        }
                        SfIcon("chevron.forward", null, size = 18.dp, tint = colors.accent)
                    }
                    Text(configuration.boundaryText(), style = IosTypography.subheadline, color = colors.secondaryContent)
                }
            }
        }
        com.muyao.wanzaihyperos.ui.design.AiBallFeedbackRow(
            modifier = Modifier.padding(start = 12.dp).onSizeChanged { feedbackSize = it },
            maxWidth = feedbackWidth,
            stackToast = true,
            ball = { Box(Modifier.size(ballBox), contentAlignment = Alignment.Center) { leading() } },
        )
    }
}

internal data class SettingsCacheClearPlan(val domain: String?, val entries: Int, val bytes: Long) {
    val title: String get() = if (domain == null) "清理全部本地缓存？" else "清理「$domain」缓存？"

    fun impactText(): String = "$entries 条 · ${settingsCacheSizeLabel(bytes)}。只删除本机读取缓存，不删除凭据、偏好、草稿或学校数据。下次读取可能重新取数，也可能离线不可用；取消不会删除。"

    fun observedResult(domains: List<com.muyao.wanzaihyperos.net.CacheDomain>): String {
        val remaining = domains.filter { domain == null || it.name == domain }.sumOf { it.entries }
        return if (remaining == 0) {
            "清理已执行，重新扫描未发现目标缓存；不将操作前大小当成实际释放量。"
        } else {
            "清理已执行，重新扫描仍有 $remaining 条目标缓存，可能未删净或有新读取；未报告全部清除。"
        }
    }
}

internal fun settingsCacheSizeLabel(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${(bytes / 104857.6).toLong() / 10.0} MB"
    bytes > 0 -> "${(bytes + 1023) / 1024} KB"
    else -> "0 KB"
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
