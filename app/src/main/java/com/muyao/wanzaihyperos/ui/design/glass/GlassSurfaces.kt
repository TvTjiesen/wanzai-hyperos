package com.muyao.wanzaihyperos.ui.design.glass

/** Actual material-bearing adapters. Non-sampling surfaces remain explicit for honest scope copy. */
enum class GlassSurfaceId(
    val label: String,
    val siteFile: String,
    val stack: GlassStack,
    val kind: GlassSurfaceKind = GlassSurfaceKind.GLASS,
    val siteFunction: String? = null,
    val drawSites: Int = 1,
) {
    // One native navigation adapter owns three independently rendered surfaces.
    NavBarContainer("底部导航条", "ui/design/liquid/GlassBar.kt", GlassStack.GLASMIO, siteFunction = "GlassNavBar"),
    NavBarSlider("导航条滑块", "ui/design/liquid/GlassBar.kt", GlassStack.GLASMIO, siteFunction = "GlassNavBar"),
    NavBarHighlight("导航条按压高光", "ui/design/liquid/GlassBar.kt", GlassStack.GLASMIO, siteFunction = "GlassNavBar"),
    TopBarBlur("顶栏滚动模糊", "ui/design/liquid/IosChrome.kt", GlassStack.GLASMIO, siteFunction = "IosTopBar"),
    SecondaryTopBarBlur("次级页面顶栏模糊", "ui/design/TopVeil.kt", GlassStack.GLASMIO, siteFunction = "TopVeil"),
    SearchButton("搜索按钮", "ui/design/liquid/GlasmioSearchButton.kt", GlassStack.GLASMIO, siteFunction = "GlasmioSearchButton"),
    SearchReturnCapsule("搜索返回胶囊", "ui/search/SearchMotion.kt", GlassStack.GLASMIO, siteFunction = "SearchMotionHost", drawSites = 1),
    HomeFocusCard("首页此刻卡", "ui/main/pages/HomeSectionPage.kt", GlassStack.GLASMIO, siteFunction = "FocusGlassCard"),
    HomeQuickTray("首页快捷按钮", "ui/home/redesign/HomeDashboard.kt", GlassStack.GLASMIO, siteFunction = "HomeQuickAction", drawSites = 4),
    CommonSwitch("公共开关", "ui/components/WanzaiGlasmioControls.kt", GlassStack.GLASMIO, siteFunction = "WanzaiSwitch", drawSites = 2),
    CommonButton("公共按钮", "ui/components/WanzaiGlasmioControls.kt", GlassStack.GLASMIO, siteFunction = "WanzaiButton", drawSites = 2),
    CommonSlider("公共滑杆", "ui/components/WanzaiGlasmioControls.kt", GlassStack.GLASMIO, siteFunction = "WanzaiSlider"),
    CommonIconButton("公共图标按钮", "ui/components/WanzaiGlasmioControls.kt", GlassStack.GLASMIO, siteFunction = "WanzaiIconButton"),
    OverlayDialog("公共弹层", "ui/components/WanzaiGlasmioControls.kt", GlassStack.GLASMIO, siteFunction = "WanzaiOverlayDialog", drawSites = 2),
    AppearanceChoice("外观选项", "ui/main/pages/AppearancePage.kt", GlassStack.GLASMIO, GlassSurfaceKind.NOT_GLASS, "AppearanceChoice", drawSites = 0),
    WallpaperThumbnail("壁纸缩略图", "ui/main/pages/AppearancePage.kt", GlassStack.GLASMIO, GlassSurfaceKind.NOT_GLASS, "WallpaperThumbnail", drawSites = 0),
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
