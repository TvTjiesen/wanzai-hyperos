package com.muyao.wanzaihyperos.ui.design.glasmio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.MiSans
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.core.glasmioColorScheme
import io.github.neoruaa.glasmio.core.glasmioTypography
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Legacy pages retain their semantic palette; Glasmio owns the new controls and optics. */
@Composable
fun WanzaiGlasmioHost(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val legacy = MiuixTheme.colorScheme
    val motion = rememberMotion()
    val colors = defaultGlassColors(isDark, legacy.primary).copy(
        content = legacy.onSurface,
        secondaryContent = legacy.onSurfaceVariantSummary,
        groupedBackground = if (isDark) AppColors.pageBackgroundDark else AppColors.pageBackgroundLight,
        elevatedBackground = AppColors.surface(isDark),
        container = AppColors.surface(isDark).copy(alpha = if (isDark) 0.54f else 0.64f),
        separator = legacy.dividerLine,
        destructive = legacy.error,
    )
    val appearance = GlassAppearance(
        liquidGlassEnabled = UiPrefs.glassMode == "liquid" && motion.refractsGlass,
        reduceTransparency = UiPrefs.glassMode == "off" || !motion.glassIsTranslucent,
        adaptiveColorScheme = false,
        materialAlphaMultiplier = com.muyao.wanzaihyperos.ui.design.glass.glassOpacityFactor(),
    )
    GlasmioTheme(
        isDark = isDark,
        colorScheme = glasmioColorScheme(isDark, colors.accent).copy(
            onPrimary = legacy.onPrimary,
            primaryContainer = legacy.primaryContainer,
            onPrimaryContainer = legacy.onPrimaryContainer,
            background = colors.groupedBackground,
            onBackground = legacy.onBackground,
            surface = colors.elevatedBackground,
            onSurface = colors.content,
            onSurfaceVariant = colors.secondaryContent,
            error = colors.destructive,
            outlineVariant = colors.separator,
        ),
        typography = glasmioTypography(MiSans),
        colors = colors,
        appearance = appearance,
    ) {
        // Controls sample only the uniform background, never their own rendered page.
        // Shell navigation/search may receive a separate page backdrop explicitly.
        GlasmioBackdropHost(
            sampledColor = colors.groupedBackground,
            modifier = Modifier.fillMaxSize(),
            overlayContent = { content() },
        )
    }
}
