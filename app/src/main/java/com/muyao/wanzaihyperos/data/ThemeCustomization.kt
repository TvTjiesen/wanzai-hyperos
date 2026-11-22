package com.muyao.wanzaihyperos.data

import kotlinx.serialization.Serializable

@Serializable
data class HomeSticker(val id: String, val image: String, val x: Float = .8f, val y: Float = .1f, val scale: Float = 1f, val rotation: Float = 0f) {
    fun validated() {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,64}")) && image.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        require(x.isFinite() && y.isFinite() && scale.isFinite() && rotation.isFinite())
        require(x in 0f..1f && y in 0f..1f && scale in .4f..2f && rotation in -180f..180f)
    }
}

@Serializable
enum class PagerGestureMode { Native, CrossAxis, IosLike }

/** Portable visual preferences. School data and credentials never belong in this model. */
@Serializable
data class ThemeCustomization(
    val accent: String = "indigo",
    val dynamicColor: Boolean = false,
    val homeTitle: String = "",
    val showNavigationBadges: Boolean = true,
    val floatingNavigation: Boolean = true,
    val bottomBarGlass: Boolean = true,
    val navigationBlur: Boolean = true,
    val useSystemWallpaper: Boolean = false,
    val predictiveBack: Boolean = true,
    val swipeDismiss: Boolean = true,
    val swipePages: Boolean = true,
    val pagerGestureMode: PagerGestureMode = PagerGestureMode.Native,
    val colorStyle: String = "tonal",
    val topBlur: Int = 14,
    val textScale: Int = 100,
    val displayScale: Int = 100,
    val wallpaperRotation: Int = 0,
    val cardOpacity: Int = 100,
    val wallpaperBlur: Int = 0,
    val wallpaperLightness: Int = 0,
    val accentHex: String = "",
    val navigationIcons: Map<Int, String> = emptyMap(),
    val cardImage: String? = null,
    val stickers: List<HomeSticker> = emptyList(),
) {
    fun validated(): ThemeCustomization {
        require(displayScale in 85..115 && wallpaperRotation in 0..359)
        require(colorStyle in setOf("tonal", "vivid", "neutral"))
        require(accentHex.isEmpty() || accentHex.matches(Regex("#[0-9a-fA-F]{6}")))
        require(navigationIcons.keys.all { it in 0..4 } && navigationIcons.values.all { it.matches(Regex("[a-zA-Z0-9-]{1,64}")) })
        require(cardImage == null || cardImage.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        require(stickers.size <= 5 && stickers.map { it.id }.distinct().size == stickers.size)
        stickers.forEach { it.validated() }
        require(accent in setOf("indigo", "blue", "teal", "purple", "rose", "orange"))
        require(homeTitle.length <= 24 && homeTitle.none { it.isISOControl() })
        require(topBlur in 0..32 && textScale in 85..130 && cardOpacity in 30..100)
        require(wallpaperBlur in 0..32 && wallpaperLightness in -80..80)
        return copy(useSystemWallpaper = false, navigationIcons = emptyMap())
    }
}
