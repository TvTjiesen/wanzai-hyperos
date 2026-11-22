package com.muyao.wanzaihyperos.ui.design

internal fun appGridColumns(widthDp: Int, fontScale: Float): Int = when {
    widthDp < 340 || fontScale >= 1.5f -> 2
    widthDp < 400 || fontScale >= 1.2f -> 3
    else -> 4
}

internal fun settingAccessoryBelow(widthDp: Int, fontScale: Float): Boolean =
    widthDp < 360 || fontScale >= 1.2f
