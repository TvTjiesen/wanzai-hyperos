package com.muyao.wanzaihyperos.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.rememberCrossWindowBackdrop
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioSlider
import io.github.neoruaa.glasmio.ui.GlasmioSwitch
import kotlin.math.roundToInt

@Composable
fun WanzaiSwitch(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlasmioTheme.colors
    if (rememberMotion().canMove) {
        GlasmioSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            onTintColor = colors.accent,
            modifier = modifier.heightIn(min = 44.dp).semantics { contentDescription = label },
        )
    } else {
        // Keep the material; only the thumb movement becomes immediate.
        Box(
            modifier = modifier.defaultMinSize(minWidth = 63.dp, minHeight = 44.dp)
                .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(width = 63.dp, height = 28.dp).clip(CircleShape)
                    .background(if (checked) colors.accent else colors.separator),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                GlassSurface(
                    modifier = Modifier.padding(horizontal = 2.dp).size(width = 37.dp, height = 24.dp),
                    shape = CircleShape,
                    content = {},
                )
            }
        }
    }
}

@Composable
fun WanzaiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    if (rememberMotion().canMove) {
        GlasmioButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled,
            emphasis = if (prominent) GlassEmphasis.Prominent else GlassEmphasis.Regular,
            content = {
                CompositionLocalProvider(top.yukonga.miuix.kmp.theme.LocalContentColor provides LocalContentColor.current) {
                    content()
                }
            },
        )
    } else {
        GlassSurface(
            modifier = modifier.heightIn(min = 48.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            shape = CircleShape,
            navigationSurfaceAlphaMultiplier = 1.25f,
            emphasis = if (prominent) GlassEmphasis.Prominent else GlassEmphasis.Regular,
            enabled = enabled,
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(top.yukonga.miuix.kmp.theme.LocalContentColor provides LocalContentColor.current) {
                    content()
                }
            }
        }
    }
}

@Composable
fun WanzaiTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    WanzaiButton(onClick = onClick, modifier = modifier, enabled = enabled, prominent = false) {
        Text(text, color = if (enabled) GlasmioTheme.colors.accent else GlasmioTheme.colors.secondaryContent)
    }
}

/**
 * 滑杆。vendor 的 [GlasmioSlider] 只在液态档有材质（减透明/减弱动态时它自己退实底），
 * 但它的轨道与手柄是 iOS 尺寸（40dp 高、37dp 手柄），比本项目的设置行高得多；
 * 所以这里只做「值域换算 + 高度收口」，材质与手感全部交给 vendor。
 *
 * 调用方用 Int（0..max）；vendor 用 Float 值域，换算在适配器里做，
 * 免得 4 个调用点各写一遍 `it.roundToInt()`。
 */
@Composable
fun WanzaiSlider(
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val ceiling = max.coerceAtLeast(1)
    val clamped = value.coerceIn(0, ceiling)
    GlasmioSlider(
        value = clamped.toFloat(),
        onValueChange = { next -> onValueChange(next.roundToInt().coerceIn(0, ceiling)) },
        modifier = modifier.heightIn(min = 40.dp),
        valueRange = 0f..ceiling.toFloat(),
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/**
 * 图标按钮。vendor 的 [GlasmioIconButton] 本身就是一个玻璃面（内部调绘制器），
 * 所以它**要**进 [com.muyao.wanzaihyperos.ui.design.glass.GlassSurfaceId] 的登记表，
 * 否则「三档覆盖哪些面」这句话对它是假的。
 *
 * `label` 同时用于读屏描述：图标本身没有文字，没有这一步读屏只会念「按钮」。
 */
@Composable
fun WanzaiIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlasmioIconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        enabled = enabled,
        size = size,
        content = content,
    )
}

/** Drop-in host for existing confirmations and editors; caller retains all state/actions. */
@Composable
fun WanzaiOverlayDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val bodyMaxHeight = (windowHeight - 160.dp).coerceAtLeast(1.dp)
    val dialogBody: @Composable ColumnScope.() -> Unit = {
        // 对话框窗口自己吃掉返回：弹窗开着时返回只该关它，绝不能让宿主页面在背后翻页。
        BackHandler(enabled = show) { onDismissRequest() }
        Column(
            Modifier.fillMaxWidth().heightIn(max = bodyMaxHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = io.github.neoruaa.glasmio.core.IosTypography.headline, color = GlasmioTheme.colors.content)
            summary?.let { Text(it, style = io.github.neoruaa.glasmio.core.IosTypography.body, color = GlasmioTheme.colors.content) }
            CompositionLocalProvider(top.yukonga.miuix.kmp.theme.LocalContentColor provides LocalContentColor.current) {
                content()
            }
        }
    }
    if (rememberMotion().canMove) {
        GlasmioAlertDialog(
            visible = show,
            onDismissRequest = onDismissRequest,
            title = title,
            titleContent = {},
            content = dialogBody,
        )
    } else if (show) {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
            GlassSurface(shape = GlasmioTheme.shapes.medium, backdrop = rememberCrossWindowBackdrop(LocalGlassBackdrop.current)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), content = dialogBody)
            }
        }
    }
}
