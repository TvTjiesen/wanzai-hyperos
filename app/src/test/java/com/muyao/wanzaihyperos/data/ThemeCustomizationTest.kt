package com.muyao.wanzaihyperos.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ThemeCustomizationTest {
    @Test fun roundTripPreservesEveryNewChoiceAndOldDefaults() {
        val custom = ThemeCustomization(accent = "rose", dynamicColor = true, homeTitle = "我的校园", showNavigationBadges = false,
            topBlur = 30, textScale = 125, displayScale = 110, wallpaperRotation = 270,
            floatingNavigation = false, bottomBarGlass = false, navigationBlur = false, useSystemWallpaper = true,
            predictiveBack = false, swipeDismiss = false, swipePages = false,
            pagerGestureMode = PagerGestureMode.IosLike, colorStyle = "neutral",
            cardOpacity = 40, wallpaperBlur = 22, wallpaperLightness = -60,
            accentHex = "#A04590", navigationIcons = mapOf(0 to "home-image", 4 to "settings-image"), cardImage = "card-image")
        val original = AppearanceSettings(customization = custom, reduceMotion = true, highContrast = true).validated()
        val buffer = ByteArrayOutputStream()
        AppearanceThemePackage.write(buffer, original)
        assertEquals(original, AppearanceThemePackage.read(ByteArrayInputStream(buffer.toByteArray())))
        assertEquals(ThemeCustomization(), Json.decodeFromString<AppearanceSettings>("{}").customization)
    }

    @Test fun continuousRotationAndLegacyRightAnglesRemainPortable() {
        for (angle in listOf(0, 1, 45, 90, 137, 180, 270, 359)) {
            val value = AppearanceSettings(customization = ThemeCustomization(wallpaperRotation = angle)).validated()
            assertEquals(angle, Json.decodeFromString<AppearanceSettings>(Json.encodeToString(value)).validated().customization.wallpaperRotation)
        }
    }

    @Test fun invalidValuesAndAssetsAreRejected() {
        val invalid = listOf(ThemeCustomization(topBlur = -1), ThemeCustomization(textScale = 200), ThemeCustomization(cardOpacity = 29),
            ThemeCustomization(homeTitle = "bad\ntext"), ThemeCustomization(accentHex = "#bad"),
            ThemeCustomization(displayScale = 200), ThemeCustomization(wallpaperRotation = 360), ThemeCustomization(wallpaperRotation = -1), ThemeCustomization(colorStyle = "unknown"),
            ThemeCustomization(cardImage = "../../photo"), ThemeCustomization(navigationIcons = mapOf(5 to "image")))
        invalid.forEach { assertTrue(runCatching { it.validated() }.isFailure) }
    }

    @Test fun malformedOversizedAndForeignPackagesLeaveNoValidTheme() {
        listOf("{}", "{\"format\":\"foreign\",\"appearance\":{}}", "{\"version\":999,\"appearance\":{}}", " ".repeat(65537)).forEach {
            assertTrue(runCatching { AppearanceThemePackage.read(ByteArrayInputStream(it.toByteArray())) }.isFailure)
        }
    }
}
