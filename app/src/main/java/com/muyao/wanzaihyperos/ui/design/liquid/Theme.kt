// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
//
// 本目录（ui/liquid）整体取自 Miuix 官方示例的 iOS 液态玻璃导航条
//   github.com/compose-miuix-ui/miuix  example/shared/src/commonMain/kotlin/component/liquid/
// 而 Miuix 那份又是 Kyant0/AndroidLiquidGlass（Apache-2.0）的改编。
// 保留原始 SPDX 头，改动仅为：改包名、把示例里的 isInDarkTheme 换成本地实现。
package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 这一份**必须读应用内主题**，不能读系统深浅色。
 *
 * ★ 工单第 15 轮（Block 级存量）：原来是 `isSystemInDarkTheme()` ⇒ 业主把主题锁成「浅色」
 *   而系统是深色时，底栏/搜索盘/顶栏纱按**深色**画，而同一个 composable 里
 *   `primary/onSurface/surfaceContainer` 走 Miuix 的浅色板 ⇒ **一处两套深浅色**。
 *   现在统一成"实际铺出来的那个背景色"（`MiuixTheme.colorScheme.background`）。
 */
@Composable
internal fun isInDarkTheme(): Boolean =
    MiuixTheme.colorScheme.background.luminance() < 0.5f
