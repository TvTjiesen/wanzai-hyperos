package com.muyao.wanzaihyperos.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muyao.wanzaihyperos.data.AppearanceSettings
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.WanzaiTheme
import com.muyao.wanzaihyperos.ui.components.WanzaiButton
import com.muyao.wanzaihyperos.ui.components.WanzaiSwitch
import com.muyao.wanzaihyperos.ui.components.WanzaiOverlayDialog
import com.muyao.wanzaihyperos.ui.design.LocalMotionPreview
import com.muyao.wanzaihyperos.ui.design.MotionFlags
import com.muyao.wanzaihyperos.ui.design.liquid.IosTopBar
import com.muyao.wanzaihyperos.ui.design.liquid.PageChrome
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GlassRepairUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun launch(): ComponentActivity {
        require(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk"))
        return instrumentation.startActivitySync(Intent().setClassName(instrumentation.targetContext,
            "com.muyao.wanzaihyperos.dev.AppearanceLabActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
    }
    private fun capture(label: String): Bitmap {
        SystemClock.sleep(350)
        val image = checkNotNull(automation.takeScreenshot())
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "glass-repair-test").apply { mkdirs() }
        File(dir, "$label.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return image
    }
    private fun touch(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try { event.source = InputDevice.SOURCE_TOUCHSCREEN; assertTrue(automation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }
    private fun difference(a: Bitmap, b: Bitmap, bounds: Rect): Float {
        var changed = 0
        var total = 0
        for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(a.height) step 3) {
            for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(a.width) step 3) {
                val ca = a.getPixel(x, y); val cb = b.getPixel(x, y)
                val delta = kotlin.math.abs(android.graphics.Color.red(ca) - android.graphics.Color.red(cb)) +
                    kotlin.math.abs(android.graphics.Color.green(ca) - android.graphics.Color.green(cb)) +
                    kotlin.math.abs(android.graphics.Color.blue(ca) - android.graphics.Color.blue(cb))
                if (delta > 24) changed++
                total++
            }
        }
        return changed.toFloat() / total.coerceAtLeast(1)
    }

    @Test fun actualControlsRenderThreeModesAndRetainGlassWithoutMotion() {
        val activity = launch()
        val mode = mutableStateOf("liquid")
        val moving = mutableStateOf(true)
        val highContrast = mutableStateOf(false)
        val dialog = mutableStateOf(false)
        val ready = CompletableFuture<Unit>()
        var bounds = Rect.Zero
        var usesBackdrop = false
        var usesRefraction = false
        var checked by mutableStateOf(false)
        var switchBounds = Rect.Zero
        var clicks = 0
        var dialogActionBounds = Rect.Zero
        var dialogSurfaceBounds = Rect.Zero
        instrumentation.runOnMainSync {
            activity.setContent {
                CompositionLocalProvider(LocalMotionPreview provides MotionFlags(animationsEnabled = moving.value,
                    refractsGlass = mode.value == "liquid", reduceTransparency = mode.value == "off", highContrast = highContrast.value)) {
                    WanzaiTheme(AppThemeMode.Light, appearance = AppearanceSettings(theme = "Light", glass = mode.value)) {
                        val appearance = LocalGlassAppearance.current
                        SideEffect { usesBackdrop = appearance.usesBackdrop; usesRefraction = appearance.usesRefraction; ready.complete(Unit) }
                        GlasmioBackdropHost(Modifier.fillMaxSize(), sampledContent = {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(Color(0xFF0B2442))
                                for (i in 0..40) drawRect(if (i % 2 == 0) Color(0xFF50D9C5) else Color(0xFFFFB56A),
                                    topLeft = Offset(i * 24.dp.toPx(), 0f), size = Size(10.dp.toPx(), size.height))
                            }
                        }, overlayContent = {
                            Column(Modifier.fillMaxSize().padding(top = 180.dp, start = 44.dp)) {
                                WanzaiButton({ clicks++ }, Modifier.width(220.dp).height(64.dp).onGloballyPositioned { bounds = it.boundsInRoot() }) { Text("真实液态按钮") }
                                Spacer(Modifier.height(32.dp))
                                WanzaiSwitch(checked, "真实开关", { checked = it }, Modifier.onGloballyPositioned { switchBounds = it.boundsInRoot() })
                            }
                            WanzaiOverlayDialog(dialog.value, { dialog.value = false }, "静态玻璃弹窗") {
                                Column(Modifier.onGloballyPositioned { dialogSurfaceBounds = it.boundsInRoot() }) {
                                    Text("弹窗仍采屏幕后方条纹")
                                    WanzaiButton({ dialog.value = false }, Modifier.onGloballyPositioned { coordinates ->
                                        val topLeft = coordinates.localToScreen(Offset.Zero)
                                        dialogActionBounds = Rect(topLeft, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                                    }) { Text("关闭测试弹窗") }
                                }
                            }
                        })
                    }
                }
            }
        }
        ready.get(10, TimeUnit.SECONDS)
        val liquid = capture("liquid-rest")
        assertTrue(usesBackdrop); assertTrue(usesRefraction)
        val now = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, bounds.center.x, bounds.center.y, now)
        val pressed = capture("liquid-held")
        touch(MotionEvent.ACTION_MOVE, bounds.center.x + 35f, bounds.center.y + 10f, now)
        val dragged = capture("liquid-dragged")
        touch(MotionEvent.ACTION_UP, bounds.center.x + 35f, bounds.center.y + 10f, now)
        assertTrue("Press must change the actual glass pixels", difference(liquid, pressed, bounds) > .02f)
        assertTrue("Drag must move the highlight/lens", difference(pressed, dragged, bounds) > .01f)
        SystemClock.sleep(700)
        instrumentation.runOnMainSync { mode.value = "frost" }
        val frost = capture("frost-rest")
        assertTrue(usesBackdrop); assertFalse(usesRefraction)
        assertTrue("Frost must not be the liquid rendering", difference(liquid, frost, bounds) > .005f)
        instrumentation.runOnMainSync { mode.value = "off" }
        val solid = capture("solid-rest")
        assertFalse(usesBackdrop); assertFalse(usesRefraction)
        assertTrue("Solid mode must stop the visible background", difference(frost, solid, bounds) > .02f)
        instrumentation.runOnMainSync { mode.value = "liquid"; moving.value = false }
        val reduced = capture("liquid-reduced-motion")
        assertTrue("Reduced motion must retain background sampling", usesBackdrop)
        assertTrue("Reduced motion must retain static liquid optics", usesRefraction)
        assertTrue("Reduced motion must not become solid", difference(reduced, solid, bounds) > .02f)
        val beforeClick = clicks
        val stillBounds = bounds
        val staticDown = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, bounds.center.x, bounds.center.y, staticDown)
        SystemClock.sleep(180)
        assertEquals("Reduced motion must not scale or drag the surface", stillBounds, bounds)
        touch(MotionEvent.ACTION_UP, bounds.center.x, bounds.center.y, staticDown)
        SystemClock.sleep(100)
        assertEquals("Reduced-motion button must remain actionable", beforeClick + 1, clicks)
        val switchDown = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, switchBounds.center.x, switchBounds.center.y, switchDown)
        touch(MotionEvent.ACTION_UP, switchBounds.center.x, switchBounds.center.y, switchDown)
        SystemClock.sleep(100)
        assertTrue("Static glass switch must update its state", checked)
        instrumentation.runOnMainSync { dialog.value = true }
        capture("reduced-motion-dialog")
        assertTrue("Static dialog must be laid out", dialogSurfaceBounds.width > 0f)
        val dialogDown = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, dialogActionBounds.center.x, dialogActionBounds.center.y, dialogDown)
        touch(MotionEvent.ACTION_UP, dialogActionBounds.center.x, dialogActionBounds.center.y, dialogDown)
        SystemClock.sleep(100)
        assertFalse("Static dialog must retain its action", dialog.value)
        instrumentation.runOnMainSync { highContrast.value = true }
        val high = capture("high-contrast-liquid")
        assertTrue("Text contrast must retain the selected glass sampling", usesBackdrop)
        assertTrue("Text contrast must retain the selected liquid refraction", usesRefraction)
        assertTrue("High-contrast liquid must visibly differ from solid", difference(high, solid, bounds) > .02f)
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun toolbarBlursWithoutWhiteCoatingEvenWithHighContrast() {
        val activity = launch()
        val contrast = mutableStateOf(false)
        val chrome = PageChrome().apply { progress = 1f }
        var bounds = Rect.Zero
        instrumentation.runOnMainSync {
            activity.setContent {
                WanzaiTheme(AppThemeMode.Light) {
                    CompositionLocalProvider(
                        LocalMotionPreview provides MotionFlags(highContrast = contrast.value),
                        LocalGlassAppearance provides LocalGlassAppearance.current.copy(reduceTransparency = false),
                    ) {
                        GlasmioBackdropHost(Modifier.fillMaxSize(), sampledContent = {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(Color(0xFF071224))
                                val stripe = 16.dp.toPx()
                                var x = 0f
                                while (x < size.width) {
                                    drawRect(Color(0xFF18425A), Offset(x, 0f), Size(stripe, size.height))
                                    x += stripe * 2f
                                }
                            }
                        }, overlayContent = { source ->
                            IosTopBar("真实模糊", chrome, false, {}, source, Color(0xFF071224),
                                Modifier.onGloballyPositioned { bounds = it.boundsInRoot() })
                        })
                    }
                }
            }
        }
        val normal = capture("blur-no-coat-normal")
        val y = (bounds.top + bounds.height * .55f).toInt()
        val x = normal.width / 8
        assertTrue("Dark wallpaper must stay dark without a white coating", Color(normal.getPixel(x, y)).luminance() < .08f)
        instrumentation.runOnMainSync { contrast.value = true }
        val high = capture("blur-no-coat-high-contrast")
        assertTrue("High contrast must not introduce a white surface", Color(high.getPixel(x, y)).luminance() < .08f)
        instrumentation.runOnMainSync { chrome.progress = 0f }
        SystemClock.sleep(800)
        val clear = capture("blur-no-coat-source")
        val region = Rect(0f, bounds.top + bounds.height * .3f, normal.width * .28f, bounds.top + bounds.height * .75f)
        assertTrue("The stripes must actually be blurred, not simply left transparent", difference(high, clear, region) > .02f)
        instrumentation.runOnMainSync { activity.finish() }
    }
}
