package com.muyao.wanzaihyperos.ui.design.glasmio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.muyao.wanzaihyperos.ui.design.BackgroundPreset
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
    appearanceSettings: com.muyao.wanzaihyperos.data.AppearanceSettings? = null,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val legacy = MiuixTheme.colorScheme
    val motion = rememberMotion()
    val glassMode = appearanceSettings?.glass ?: UiPrefs.glassMode
    val colors = defaultGlassColors(isDark, legacy.primary).copy(
        content = legacy.onSurface,
        secondaryContent = legacy.onSurfaceVariantSummary,
        groupedBackground = legacy.background,
        elevatedBackground = legacy.surface,
        container = legacy.surface.copy(alpha = if (isDark) 0.54f else 0.64f),
        separator = legacy.dividerLine,
        destructive = legacy.error,
    )
    val appearance = GlassAppearance(
        liquidGlassEnabled = glassMode == "liquid" && motion.refractsGlass,
        reduceTransparency = glassMode == "off" || !motion.glassIsTranslucent,
        adaptiveColorScheme = false,
        materialAlphaMultiplier = appearanceSettings?.let { com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(it.opacity.toFloat()) }
            ?: com.muyao.wanzaihyperos.ui.design.glass.glassOpacityFactor(),
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
        val background = BackgroundPreset.of(appearanceSettings?.background ?: UiPrefs.backgroundPreset)
        GlasmioBackdropHost(
            modifier = Modifier.fillMaxSize(),
            sampledContent = {
                com.muyao.wanzaihyperos.ui.home.ThemePageBackground(Modifier.fillMaxSize(), isDark, background)
            },
            overlayContent = { content() },
        )
    }
}
