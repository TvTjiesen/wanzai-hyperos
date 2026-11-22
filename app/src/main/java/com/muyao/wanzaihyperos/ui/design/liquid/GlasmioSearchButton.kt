package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.CompositionLocalProvider
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
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
    val searchMotion = com.muyao.wanzaihyperos.ui.search.LocalSearchMotion.current
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val lease = androidx.compose.runtime.remember { com.muyao.wanzaihyperos.ui.search.SearchSourceLease() }
    val view = androidx.compose.ui.platform.LocalView.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val windowRevision = Triple(configuration.screenWidthDp, configuration.screenHeightDp, configuration.orientation)
    val currentWindowRevision = androidx.compose.runtime.rememberUpdatedState(windowRevision)
    androidx.compose.runtime.DisposableEffect(lease) {
        onDispose { lease.attached = false }
    }
    val appearance = LocalGlassAppearance.current
    CompositionLocalProvider(LocalGlassAppearance provides appearance.copy(
        materialAlphaMultiplier = appearance.materialAlphaMultiplier * 0.65f,
    )) {
        GlassSurface(
            backdrop = backdrop,
            shape = Capsule(),
            material = NavigationGlassMaterial.Clear,
            onClick = {
                haptics.tick()
                val bounds = lease.bounds
                val window = lease.window
                val revision = windowRevision
                searchMotion?.capture(com.muyao.wanzaihyperos.ui.search.SearchOrigin(
                    bounds, backdrop,
                    isValid = { lease.matches(bounds, window) && currentWindowRevision.value == revision },
                    restoreFocus = { runCatching { focus.requestFocus() } },
                ))
                onClick()
            },
            modifier = modifier.size(48.dp)
                .onGloballyPositioned {
                    val visible = it.boundsInWindow()
                    lease.attached = it.isAttached && visible.width == it.size.width.toFloat() && visible.height == it.size.height.toFloat()
                    lease.bounds = visible
                    // Configuration dimensions ignore the temporary IME resize of the host view.
                    val density = view.resources.displayMetrics.density
                    lease.window = androidx.compose.ui.geometry.Rect(0f, 0f,
                        configuration.screenWidthDp * density, configuration.screenHeightDp * density)
                }
                .focusRequester(focus).focusable()
                .alpha(if (searchMotion?.origin != null) 0f else 1f)
                .semantics { contentDescription = "搜索课程、应用、通知" },
        ) {
            androidx.compose.material3.Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_board_search),
                contentDescription = null,
                tint = LocalGlassColors.current.accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
