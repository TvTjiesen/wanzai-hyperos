package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.muyao.wanzaihyperos.R
import com.muyao.wanzaihyperos.ui.design.rememberHaptics
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.render.Backdrop

@Composable
fun GlasmioSearchButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    GlassSurface(
        backdrop = backdrop,
        shape = Capsule(),
        onClick = { haptics.tick(); onClick() },
        modifier = modifier.size(48.dp).semantics { contentDescription = "搜索课程、应用、通知" },
    ) {
        androidx.compose.material3.Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_board_search),
            contentDescription = null,
            tint = LocalGlassColors.current.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}
