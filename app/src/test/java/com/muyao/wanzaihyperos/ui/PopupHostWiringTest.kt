package com.muyao.wanzaihyperos.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弹层宿主守卫：全 App 的 OverlayDialog 只有在 [WanzaiTheme] 里挂过
 * `MiuixPopupUtils.MiuixPopupHost()` 才画得出来。
 *
 * 为什么会漏（2026-10-30 真机实测）：miuix 0.9.4 的 OverlayDialog 自己不画任何东西，
 * 它只往 `LocalDialogStates` 里塞一个 DialogState，真正渲染的是 MiuixPopupHost；
 * 而整个 miuix jar 里**只有它自己的 Scaffold** 调用过这个宿主。本工程主壳是自定义
 * Box + AnimatedContent，没有 miuix Scaffold ⇒ 一旦这层宿主掉了，
 * 设置页「节次时间表」、课表页、消息页「全部已读」、我的页「退出登录」四处弹窗
 * 会集体变成「点了没反应」，而且不报错、不崩溃、logcat 干净 —— 最难查的那种坏法。
 */
class PopupHostWiringTest {

    private val root = File(System.getProperty("user.dir")).let { run ->
        // Gradle 的测试工作目录是模块目录（app/），源码在它的上一级。
        val parent = run.parentFile
        if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else run
    }

    private val theme: String by lazy {
        SourceScan.stripComments(File(root, "app/src/main/java/com/muyao/wanzaihyperos/ui/AppTheme.kt").readText())
    }

    private fun blockAfter(code: String, marker: String): String {
        val markerAt = code.indexOf(marker)
        assertTrue("找不到代码块 $marker", markerAt >= 0)
        val open = code.indexOf('{', markerAt + marker.length)
        assertTrue("$marker 没有内容槽", open >= 0)
        var depth = 1
        var cursor = open + 1
        while (cursor < code.length && depth > 0) {
            when (code[cursor++]) {
                '{' -> depth++
                '}' -> depth--
            }
        }
        assertTrue("$marker 内容槽没有闭合", depth == 0)
        return code.substring(open + 1, cursor - 1)
    }

    @Test
    fun `主题里必须挂一次弹层宿主`() {
        org.junit.Assert.assertEquals(
            "AppTheme 必须且只能挂一个 legacy 弹层宿主，不能遗漏或重复渲染",
            1,
            Regex("""\bMiuixPopupHost\(\)""").findAll(theme).count(),
        )
    }

    @Test
    fun `弹层宿主必须包住页面内容、并且叠在内容之上`() {
        val box = blockAfter(theme, "androidx.compose.foundation.layout.Box(")
        val overlay = blockAfter(box, "WanzaiGlasmioHost(themeMode = themeMode)")
        val contentAt = overlay.indexOf("content()")
        val hostAt = overlay.indexOf("MiuixPopupHost()")
        assertTrue("同一 Box 的 Glasmio 内容槽里没找到页面 content", contentAt >= 0)
        assertTrue("同一内容槽里没找到 legacy 宿主", hostAt >= 0)
        assertTrue("宿主必须写在 content 之后，否则弹层被内容盖住", hostAt > contentAt)
        val hostTheme = SourceScan.stripComments(File(root, "app/src/main/java/com/muyao/wanzaihyperos/ui/design/glasmio/WanzaiGlasmioTheme.kt").readText())
        assertTrue("页面与 legacy 弹层必须位于不采自身的 overlay", "overlayContent = { content() }" in hostTheme && "sampledContent" !in hostTheme)
    }
}
