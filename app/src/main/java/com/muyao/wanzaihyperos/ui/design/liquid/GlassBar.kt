package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.ui.GlasmioNavigationBar
import io.github.neoruaa.glasmio.ui.GlasmioTabItem

data class GlassNavItem(
    val label: String,
    @DrawableRes val iconRes: Int,
    val badge: Int = 0,
)

@Composable
fun GlassNavBar(
    items: List<GlassNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    systemInset: androidx.compose.ui.unit.Dp? = null,
) {
    val custom = com.muyao.wanzaihyperos.ui.design.LocalThemeCustomization.current
    val inset = systemInset ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabs = items.mapIndexed { index, item ->
        GlasmioTabItem(
            key = index,
            label = item.label,
            symbol = SfSymbol.House,
            icon = ImageVector.vectorResource(item.iconRes),
            badge = item.badge.coerceAtLeast(0),
        )
    }
    val appearance = LocalGlassAppearance.current
    CompositionLocalProvider(LocalGlassAppearance provides appearance.copy(
        materialAlphaMultiplier = appearance.materialAlphaMultiplier * 0.45f,
        reduceTransparency = appearance.reduceTransparency || !custom.bottomBarGlass,
    )) {
        GlasmioNavigationBar(
            items = tabs,
            selectedKey = selected.coerceIn(0, items.lastIndex),
            onSelected = onSelect,
            onExpand = {},
            compactProgress = rememberUpdatedState(0f),
            compactSize = 48.dp,
            backdrop = backdrop ?: LocalGlassBackdrop.current,
            blurRadius = if (custom.navigationBlur) androidx.compose.ui.unit.Dp.Unspecified else 0.dp,
            modifier = modifier
                .padding(start = if (custom.floatingNavigation) 6.dp else 0.dp,
                    end = if (custom.floatingNavigation) 6.dp else 0.dp,
                    bottom = if (custom.floatingNavigation) { if (inset > 0.dp) inset + 6.dp else 16.dp } else inset)
                .fillMaxWidth(),
        )
    }
}
