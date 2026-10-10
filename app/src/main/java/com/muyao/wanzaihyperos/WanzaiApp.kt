package com.muyao.wanzaihyperos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * 应用根：单例 [MiuixTheme]。
 * 这是 HyperOS 设计语言的骨架（见 miuix-skill 的 setup-and-theme）。
 */
@Composable
fun WanzaiApp() {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller) {
        // 页面内容后续填充（登录页 → 四个 tab）
    }
}
