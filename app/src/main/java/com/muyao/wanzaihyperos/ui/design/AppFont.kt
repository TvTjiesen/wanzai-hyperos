package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.muyao.wanzaihyperos.R

/**
 * 全 App 唯一字体：**MiSans**（定稿板的 `font-family:"MiSans VF","MiSans",…`）。
 *
 * 为什么要打进包里而不是 `FontFamily.SansSerif`：这台 HyperOS 的 /system/fonts
 * 里**没有 MiSans 文件**（2026-10-21 实测 `ls /system/fonts | grep -i misans` 为空），
 * 系统中文回落到 Noto Sans CJK，和板子的字形不是一个东西。四个静态字重
 * （Regular/Medium/Semibold/Bold）直接随 APK 走，离线、可预期。
 */
val MiSans = FontFamily(
    Font(R.font.misans_regular, FontWeight.Normal),
    Font(R.font.misans_medium, FontWeight.Medium),
    Font(R.font.misans_semibold, FontWeight.SemiBold),
    Font(R.font.misans_bold, FontWeight.Bold),
)
