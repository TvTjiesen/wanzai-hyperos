package com.muyao.wanzaihyperos.ui.home.redesign

/** 与 HomeSky 的相册/内置羽化终点同源；测量不包含滚动或下拉位移。 */
internal fun homeDepthCanvasHeight(cardTop: Float, cardHeight: Float, album: Boolean): Float =
    (cardTop.coerceAtLeast(0f) + cardHeight.coerceAtLeast(0f) * 0.5f) /
        (if (album) 0.80f else 0.78f)

internal fun homeStatusDetail(status: String, title: String): String = when {
    status == title -> ""
    title.isNotBlank() && status.startsWith("$title · ") -> status.removePrefix("$title · ")
    else -> status
}
