package com.muyao.wanzaihyperos.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muyao.wanzaihyperos.data.*
import com.muyao.wanzaihyperos.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class ThemeRenderingTest {
    @Test fun accentDensityWallpaperEffectsAndRotationReachRenderedOutput() {
        require(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk"))
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent().setClassName(inst.targetContext,
            "com.muyao.wanzaihyperos.dev.AppearanceLabActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val original = UiPrefs.appearanceSnapshot()
        val settings = mutableStateOf(AppearanceSettings(theme = "Light"))
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        for (y in 0 until 80) for (x in 0 until 120) bitmap.setPixel(x, y, if (x % 12 < 6) android.graphics.Color.RED else android.graphics.Color.BLUE)
        val photo = bitmap.asImageBitmap()
        var accent = Color.Transparent
        var density = 0f
        var fontScale = 0f
        inst.runOnMainSync {
            activity.setContent {
                WanzaiTheme(AppThemeMode.Light, appearance = settings.value) {
                    val currentDensity = LocalDensity.current
                    val color = MiuixTheme.colorScheme.primary
                    SideEffect { accent = color; density = currentDensity.density; fontScale = currentDensity.fontScale }
                    Box(Modifier.fillMaxSize()) {
                        HomeSky(SkyPreset.ALBUM, photo, 0, 0, Modifier.fillMaxWidth().height(350.dp), hourOverride = 12)
                    }
                }
            }
        }
        fun capture(): Bitmap { SystemClock.sleep(600); return checkNotNull(inst.uiAutomation.takeScreenshot()) }
        fun difference(a: Bitmap, b: Bitmap): Int {
            var count = 0
            for(y in 160 until 650 step 5) for(x in 60 until a.width - 60 step 5) {
                val first = a.getPixel(x,y); val second = b.getPixel(x,y)
                if (kotlin.math.abs(android.graphics.Color.red(first)-android.graphics.Color.red(second)) +
                    kotlin.math.abs(android.graphics.Color.blue(first)-android.graphics.Color.blue(second)) > 30) count++
            }
            return count
        }
        try {
            val baseline = capture(); val baseAccent = accent; val baseDensity = density; val baseFont = fontScale
            inst.runOnMainSync { settings.value = settings.value.copy(customization = ThemeCustomization(accentHex = "#008800", displayScale = 110, textScale = 120)) }
            capture().recycle()
            assertNotEquals(baseAccent, accent)
            assertTrue(accent.luminance() <= .17f)
            assertEquals(baseDensity * 1.1f, density, .01f)
            assertEquals(baseFont * 1.2f, fontScale, .01f)
            inst.runOnMainSync { settings.value = AppearanceSettings(theme = "Light", customization = ThemeCustomization(wallpaperBlur = 24, wallpaperLightness = 60)) }
            val effects = capture()
            assertTrue("Wallpaper blur/lightness must visibly change pixels", difference(baseline, effects) > 500)
            inst.runOnMainSync { settings.value = AppearanceSettings(theme = "Light", customization = ThemeCustomization(wallpaperRotation = 90)) }
            val rotated = capture()
            assertTrue("Wallpaper rotation must reach drawing", difference(baseline, rotated) > 500)
            assertEquals(original, UiPrefs.appearanceSnapshot())
            baseline.recycle(); effects.recycle(); rotated.recycle()
        } finally { inst.runOnMainSync { activity.finish() }; bitmap.recycle() }
    }
}
