package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
) {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabs = items.mapIndexed { index, item ->
        GlasmioTabItem(
            key = index,
            label = item.label,
            symbol = SfSymbol.House,
            icon = ImageVector.vectorResource(item.iconRes),
            badge = item.badge.coerceAtLeast(0),
        )
    }
    GlasmioNavigationBar(
        items = tabs,
        selectedKey = selected.coerceIn(0, items.lastIndex),
        onSelected = onSelect,
        onExpand = {},
        compactProgress = rememberUpdatedState(0f),
        compactSize = 48.dp,
        backdrop = backdrop ?: LocalGlassBackdrop.current,
        modifier = modifier
            .padding(start = 6.dp, end = 6.dp, bottom = if (inset > 0.dp) inset + 6.dp else 16.dp)
            .fillMaxWidth(),
    )
}
