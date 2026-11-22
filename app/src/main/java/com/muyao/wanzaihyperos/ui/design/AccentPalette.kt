package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Keep user-selected hue while making primary text/controls readable in both modes. */
fun readableThemeAccent(hex: String, dark: Boolean): Color {
    val parsed = Color((0xFF000000L or hex.removePrefix("#").toLong(16)))
    var color = parsed
    repeat(24) {
        if ((!dark && color.luminance() <= .16f) || (dark && color.luminance() >= .5f)) return color
        color = lerp(color, if (dark) Color.White else Color.Black, .08f)
    }
    return color
}
