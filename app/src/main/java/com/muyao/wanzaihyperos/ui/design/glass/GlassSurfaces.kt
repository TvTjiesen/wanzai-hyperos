package com.muyao.wanzaihyperos.ui.design.glass

/** Actual material-bearing adapters. Non-sampling surfaces remain explicit for honest scope copy. */
enum class GlassSurfaceId(
    val label: String,
    val siteFile: String,
    val stack: GlassStack,
    val kind: GlassSurfaceKind = GlassSurfaceKind.GLASS,
    val siteFunction: String? = null,
) {
    // One native navigation adapter owns three independently rendered surfaces.
    NavBarContainer("底部导航条", "ui/design/liquid/GlassBar.kt", GlassStack.GLASMIO, siteFunction = "GlassNavBar"),
    NavBarSlider("导航条滑块", "ui/design/liquid/GlassBar.kt", GlassStack.GLASMIO, siteFunction = "GlassNavBar"),
    NavBarHighlight("导航条按压高光", "ui/design/liquid/GlassBar.kt", GlassStack.GLASMIO, siteFunction = "GlassNavBar"),
    TopBarVeil("顶栏渐变遮罩", "ui/design/liquid/IosChrome.kt", GlassStack.LIQUID, GlassSurfaceKind.NOT_GLASS, "IosTopBar"),
    SearchButton("搜索按钮", "ui/design/liquid/GlasmioSearchButton.kt", GlassStack.GLASMIO, siteFunction = "GlasmioSearchButton"),
    HomeFocusCard("首页此刻卡", "ui/main/pages/HomeSectionPage.kt", GlassStack.GLASMIO, siteFunction = "FocusGlassCard"),
    CommonSwitch("公共开关", "ui/components/WanzaiGlasmioControls.kt", GlassStack.GLASMIO, siteFunction = "WanzaiSwitch"),
    CommonButton("公共按钮", "ui/components/WanzaiGlasmioControls.kt", GlassStack.GLASMIO, siteFunction = "WanzaiButton"),
    OverlayDialog("公共弹层", "ui/components/WanzaiGlasmioControls.kt", GlassStack.GLASMIO, siteFunction = "WanzaiOverlayDialog"),
    AppContentCard("正文卡片", "ui/design/AppLayout.kt", GlassStack.GLASMIO, GlassSurfaceKind.NOT_GLASS, "AppCard"),
    SettingsGroup("设置分组", "ui/design/SettingsChrome.kt", GlassStack.GLASMIO, GlassSurfaceKind.NOT_GLASS, "SettingGroup"),
    AppearancePreview("外观预览卡", "ui/home/HomeSky.kt", GlassStack.LIQUID, GlassSurfaceKind.NOT_GLASS);

    companion object {
        val registeredFiles: Set<String> get() = entries.map { it.siteFile }.toSet()
    }
}

enum class GlassSurfaceKind { GLASS, NOT_GLASS }

enum class GlassStack {
    GLASS,
    LIQUID,
    /** Root GlassAppearance flows through the native adapter into Glasmio's renderer. */
    GLASMIO,
}

/** Existing renderer implementations, not per-screen sampling sites. Do not broaden this set. */
val glassMechanismFiles = setOf(
    "ui/design/glass/GlassSurface.kt",
    "ui/design/glass/BarOptics.kt",
    "ui/design/liquid/CombinedBackdrop.kt",
    "ui/design/liquid/PageBackdrop.kt",
    "ui/design/liquid/IosChrome.kt",
)

/** Retained legacy implementations are still audited, but must not be advertised as active UI. */
val legacyGlassSites = mapOf(
    "ui/design/liquid/LiquidGlassNavigationBar.kt" to 3,
    "ui/design/liquid/GlassControls.kt" to 1,
)

/** Existing restart-action opaque surface and its modal dimming scrim are not glass material. */
val unglassedSites = mapOf(
    "ui/design/liquid/GlassControls.kt" to 2,
)
