package com.muyao.wanzaihyperos.ui.home

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.muyao.wanzaihyperos.data.UiPrefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (node == null) return emptyList()
        return listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    }

    private fun node(text: String): AccessibilityNodeInfo? = nodes(automation.rootInActiveWindow)
        .firstOrNull { it.text?.toString() == text }

    private fun await(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            node(text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Appearance UI did not show: $text")
    }

    private fun reveal(text: String, forward: Boolean) {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        repeat(12) {
            if (node(text)?.isVisibleToUser == true) return
            val list = nodes(automation.rootInActiveWindow).firstOrNull { it.isScrollable &&
                it.actionList.any { available -> available.id == action } }
            if (list?.performAction(action) != true) return@repeat
            SystemClock.sleep(350)
        }
        assertTrue("Not visible after scrolling: $text", node(text)?.isVisibleToUser == true)
    }

    private fun click(text: String) {
        automation.waitForIdle(200, 3_000)
        var bounds = Rect().also { await(text).getBoundsInScreen(it) }
        var stable = false
        repeat(20) {
            if (!stable) {
                SystemClock.sleep(100)
                val next = Rect().also { await(text).getBoundsInScreen(it) }
                stable = next == bounds
                bounds = next
            }
        }
        assertTrue("Click target must stop moving: $text", stable)
        val now = SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN,
            bounds.exactCenterX(), bounds.exactCenterY(), 0)
        val up = android.view.MotionEvent.obtain(now, now + 60, android.view.MotionEvent.ACTION_UP,
            bounds.exactCenterX(), bounds.exactCenterY(), 0)
        try {
            down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(down, true))
            assertTrue(automation.injectInputEvent(up, true))
        } finally { down.recycle(); up.recycle() }
    }

    private fun section(text: String, expanded: Boolean) {
        var target: AccessibilityNodeInfo? = await(text)
        while (target != null && !target.isClickable) target = target.parent
        val row = checkNotNull(target) { "Section has no click action: $text" }
        assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val expected = if (expanded) "已展开" else "已折叠"
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var current: AccessibilityNodeInfo? = node(text)
            while (current != null) {
                if (current.stateDescription?.toString() == expected) return
                current = current.parent
            }
            SystemClock.sleep(100)
        }
        error("Section state did not become $expected: $text")
    }

    private fun activity(): Activity {
        var current: Activity? = null
        instrumentation.runOnMainSync {
            current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return checkNotNull(current)
    }

    private fun awaitTheme(name: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (UiPrefs.appearanceSnapshot().theme == name) return
            SystemClock.sleep(100)
        }
        assertEquals(name, UiPrefs.appearanceSnapshot().theme)
    }

    @Test fun mixedAppearanceChangesRestoreEveryOriginalChoice() {
        require(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk"))
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync { UiPrefs.load(context) }
        val before = UiPrefs.appearanceSnapshot()
        val root = java.io.File(context.cacheDir, "appearance-cycle-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val undo = AppearanceUndo(context, java.io.File(root, "undo"), java.io.File(root, "image"),
            { UiPrefs.applyAppearance(it) }, {})
        try {
            kotlinx.coroutines.runBlocking { undo.prepare(before) }
            val variants = listOf(
                before.copy(theme = "Dark", background = "sakura", glass = "off", reduceTransparency = true,
                    container = "bare", sky = "night", opacity = 100, density = "compact", modules = emptySet(), appsCollapsed = true),
                before.copy(theme = "Light", background = "lake", glass = "frost", reduceTransparency = false,
                    container = "designed", sky = "morning", opacity = 0, dim = 80, soft = 100,
                    panX = -.5f, panY = .5f, zoom = 3f, modules = setOf("todos")),
                com.muyao.wanzaihyperos.data.AppearanceSettings(),
            )
            repeat(3) {
                variants.forEach { value ->
                    instrumentation.runOnMainSync { UiPrefs.applyAppearance(value) }
                    assertEquals(value, UiPrefs.appearanceSnapshot())
                    assertEquals(value.reduceTransparency, com.muyao.wanzaihyperos.ui.design.MotionPrefs.reduceTransparency)
                    assertEquals(value.glass == "liquid", com.muyao.wanzaihyperos.ui.design.MotionPrefs.refractsGlass)
                    val disk = context.getSharedPreferences("wanzai_ui", android.content.Context.MODE_PRIVATE)
                    assertEquals(value.theme, disk.getString("theme_mode", null))
                    assertEquals(value.glass, disk.getString("glass_mode", null))
                }
            }
            kotlinx.coroutines.runBlocking { undo.finish(); undo.restore() }
            assertEquals(before, UiPrefs.appearanceSnapshot())
        } finally {
            instrumentation.runOnMainSync { UiPrefs.applyAppearance(before) }
            root.deleteRecursively()
        }
    }

    @Test fun sectionsExpandIndependentlyInTheSameList() {
        require(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk"))
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent().setClassName(context, "com.muyao.wanzaihyperos.dev.AppearanceLabActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            await("全屏预览")
            reveal("背景", forward = true)
            assertNull(node("首页天空与图片"))
            section("背景", expanded = true)
            reveal("首页天空与图片", forward = true)
            reveal("外观", forward = true)
            section("外观", expanded = true)
            reveal("底栏图标", forward = true)
            reveal("背景", forward = false)
            section("背景", expanded = false)
            assertNull("Closing background must remove its controls", node("首页天空与图片"))
            reveal("外观", forward = true)
            assertTrue("The independent appearance section must remain open", node("底栏图标")?.isVisibleToUser == true)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun realPageDraftFullscreenRotationApplyAndUndo() {
        require(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk"))
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync { UiPrefs.load(context) }
        val before = UiPrefs.appearanceSnapshot()
        try {
            instrumentation.startActivitySync(Intent().setClassName(context, "com.muyao.wanzaihyperos.dev.AppearanceLabActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("全屏预览")
            val nextTheme = if (before.theme == "Dark") "Light" else "Dark"
            val themeLabel = if (nextTheme == "Dark") "深色" else "浅色"
            reveal("颜色", forward = true)
            assertNull("Color choices must start folded", node(themeLabel))
            click("颜色")
            reveal(themeLabel, forward = true)
            click(themeLabel)
            reveal("颜色", forward = false)
            click("颜色")
            reveal("应用全部更改", forward = false)
            assertEquals("Selecting a theme must not persist", before, UiPrefs.appearanceSnapshot())
            click("全屏预览")
            await("首页预览 · 尚未应用")
            val bounds = Rect().also { await("完成").getBoundsInScreen(it) }
            repeat(8) {
                SystemClock.sleep(100)
                val next = Rect().also { await("完成").getBoundsInScreen(it) }
                assertEquals("Fullscreen geometry must not oscillate", bounds, next)
            }
            assertEquals(before, UiPrefs.appearanceSnapshot())
            click("完成")
            await("全屏预览")
            val current = activity()
            instrumentation.runOnMainSync { current.recreate() }
            await("应用全部更改")
            assertEquals("Recreation must keep draft but not save it", before, UiPrefs.appearanceSnapshot())
            click("全屏预览")
            await("首页预览 · 尚未应用")
            val portrait = activity()
            instrumentation.runOnMainSync { portrait.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            SystemClock.sleep(500)
            await("首页预览 · 尚未应用")
            click("完成")
            val landscape = activity()
            instrumentation.runOnMainSync { landscape.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(500)
            await("应用全部更改")
            click("应用全部更改")
            awaitTheme(nextTheme)
            await("已应用全部外观。可撤销；重启后撤销入口仍保留。")
            assertEquals("Applied theme must be committed on disk", nextTheme,
                context.getSharedPreferences("wanzai_ui", android.content.Context.MODE_PRIVATE).getString("theme_mode", null))
            click("撤销上次应用")
            await("已恢复上次应用前的外观与取景，账号和学习数据未改变")
            assertEquals(before, UiPrefs.appearanceSnapshot())
        } finally {
            instrumentation.runOnMainSync { UiPrefs.applyAppearance(before) }
        }
    }
}
