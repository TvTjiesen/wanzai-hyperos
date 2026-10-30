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
        File(root, "app/src/main/java/com/muyao/wanzaihyperos/ui/AppTheme.kt").readText()
    }

    @Test
    fun `主题里必须挂一次弹层宿主`() {
        assertTrue(
            "AppTheme 里没有 MiuixPopupHost()，全 App 的 OverlayDialog 都会点了没反应",
            "MiuixPopupHost()" in theme,
        )
    }

    @Test
    fun `弹层宿主必须包住页面内容、并且叠在内容之上`() {
        val host = theme.substringAfter("MiuixPopupHost()", "")
        assertTrue("找不到 MiuixPopupHost 调用", host.isNotEmpty() || "MiuixPopupHost()" in theme)
        // 宿主与 content 必须在同一个 Box 里，且宿主写在 content 之后 —— 否则弹层会被页面内容盖住。
        val box = theme.substringAfter("androidx.compose.foundation.layout.Box(", "")
        assertTrue("弹层宿主不在 Theme 里，页面会盖住弹窗", box.isNotEmpty())
        val contentAt = box.indexOf("content = content")
        val hostAt = box.indexOf("MiuixPopupHost()")
        assertTrue("Box 里没找到 content", contentAt >= 0)
        assertTrue("Box 里没找到宿主", hostAt >= 0)
        assertTrue("宿主必须写在 content 之后，否则弹层被内容盖住", hostAt > contentAt)
    }
}
