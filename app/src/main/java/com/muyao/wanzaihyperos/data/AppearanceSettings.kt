package com.muyao.wanzaihyperos.data

import kotlinx.serialization.Serializable

@Serializable
data class AppearanceSettings(
    val theme: String = "System",
    val background: String = "porcelain",
    val glass: String = "liquid",
    val opacity: Int = 50,
    val container: String = "designed",
    val sky: String = "auto",
    val dim: Int = 24,
    val soft: Int = 26,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val density: String = "comfortable",
    val modules: Set<String> = setOf("courses", "todos", "rollcall", "szty"),
    val appsCollapsed: Boolean = false,
    val reduceTransparency: Boolean = false,
    val customization: ThemeCustomization = ThemeCustomization(),
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val homeOrder: List<String> = HomeLayoutOrder.defaults,
    val hiddenHomeSections: Set<String> = emptySet(),
) {
    fun validated(): AppearanceSettings {
        customization.validated()
        require(theme in setOf("System", "Light", "Dark"))
        require(background in setOf("porcelain", "cream", "mist", "sakura", "lake"))
        require(glass in setOf("liquid", "frost", "off"))
        require(container in setOf("designed", "bare"))
        require(sky in setOf("auto", "morning", "dusk", "night", "slate", "aurora", "blossom", "album"))
        require(density in setOf("comfortable", "compact"))
        require(opacity in 0..100 && dim in 0..80 && soft in 0..100)
        require(panX.isFinite() && panY.isFinite() && zoom.isFinite())
        require(panX in -1f..1f && panY in -1f..1f && zoom in 1f..4f)
        require(modules.all { it in setOf("courses", "todos", "rollcall", "szty") })
        require(hiddenHomeSections.all { it in HomeLayoutOrder.defaults })
        require(homeOrder.all { it in HomeLayoutOrder.defaults })
        require(homeOrder.distinct().size == homeOrder.size)
        return copy(homeOrder = HomeLayoutOrder.normalize(homeOrder))
    }
}
