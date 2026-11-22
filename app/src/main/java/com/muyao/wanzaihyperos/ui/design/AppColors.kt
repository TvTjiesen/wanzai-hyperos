package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.muyao.wanzaihyperos.data.UiPrefs

/**
 * 全 App 的**色彩策略**。
 *
 * ## 取向：繼續用 Miuix 語義色，只做**兩處**局部覆蓋
 *
 * 這不是省事 —— 是自己挑一整套色板的代價太高：
 * Miuix 有 40+ 個語義色（`primary` / `onSurface` / `surfaceContainerHigh` …），
 * 自己挑一套等於要保證 40 個色兩兩搭配都對，而**改一個就可能破壞某個不相干元件**
 *（例如把 `secondaryContainer` 調深，`TextField` 的輸入框底就跟著變）。
 *
 * 規範 §2.1 也是這個建議：**保持 `ColorSchemeMode`，只在兩處做局部覆蓋**。
 *
 * ## 兩處覆蓋
 *
 * | 位置 | 值 | 為什麼 |
 * |------|-----|--------|
 * | 玻璃容器底色 | `surfaceContainer` α **0.4** | 真機實測：亮色下 0.4 才透得出背景層次；實色會讓玻璃變灰塊 |
 * | 玻璃表面提亮 | 深 `White α 0.10` / 淺 `White α 0.34` | **差 3 倍多** —— 亮色下需要更實的「托盤感」 |
 *
 * ## 三條紀律
 *
 * 1. **文字只用兩檔**：`onSurface`（正文）+ `onSurfaceVariantSummary`（次級）。
 *    不要出現第三檔灰；禁用態請**整體降一檔透明度**，而不是另挑一個灰。
 * 2. **不用純黑做背景**：OLED 上有「塗抹感」，且和玻璃折射對不上。
 * 3. **強調色只用兩處**：選中態 + 那一個必須被看到的點（未讀、危險操作）。
 *    一屏超過 3 處就等於沒重點。
 */
object AppColors {

    /**
     * 玻璃容器底色的透明度。
     *
     * 實測：亮色下 0.4 才透得出背景層次；太低（0.2）玻璃變透明塑膠，
     * 太高（0.6+）玻璃變灰塊。
     */
    const val GLASS_CONTAINER_ALPHA = 0.4f

    /** 玻璃表面提亮 —— **深色**。 */
    const val GLASS_SURFACE_ALPHA_DARK = 0.10f

    /** 玻璃表面提亮 —— **淺色**。比深色高 3 倍多，因為亮色下需要更實的托盤感。 */
    const val GLASS_SURFACE_ALPHA_LIGHT = 0.34f

    /** 玻璃表面提亮色（深色模式用）。 */
    val glassSurfaceDark = Color.White.copy(alpha = GLASS_SURFACE_ALPHA_DARK)

    /**
     * 当前选中的背景预设（业主 2026-10-29「做个自定义界面，让用户自己选择背景颜色」）。
     *
     * ⚠️ 每次读都现取 `UiPrefs.backgroundPreset` —— 它是个 `mutableStateOf`，
     * 读它的人自动订阅重组，所以外观页换一套底，整屏（含主题令牌）跟着换。
     * 缓存成字段会得到一个「设置里点了没反应」的假开关。
     */
    val background: BackgroundPreset
        get() = BackgroundPreset.of(UiPrefs.backgroundPreset)

    /** 卡片那一层（比纸更亮，让卡片浮起来）。`surface` 令牌也用它。 */
    fun surface(isDark: Boolean): Color =
        if (isDark) background.cardDark else background.cardLight

    /**
     * 主題的紙面色（`AppTheme` 裡 `surfaceContainer` 用的就是這兩枚，**同一個定義**）。
     *
     * ★ 為什麼提到這裡：`HomeSectionPage` 那張此刻卡要算「墊色壓在天空上真正鋪出來的顏色」
     *   來決定字色，而它拿的必須是**畫面上那個** surfaceContainer。以前測試裡自己寫了
     *   `0xFFF4F7FF / 0xFF1C1F20` 兩枚"近似值"（第 7 轮审计判死：近似值扫出来的 4.5:1
     *   不是真机上那个数）⇒ 现在主题与测试读同一份，谁改色卡面判据跟着变。
     */
    val surfaceContainerLight: Color get() = background.cardLight
    val surfaceContainerDark: Color get() = background.cardDark

    /**
     * 主題的頁面底色（`AppTheme` 的 `background` 用它，同一個定義）。
     *
     * ★ 卡面字色的解算必須拿到它：天空只畫到 200dp，而「此刻」卡有八成高度壓在
     *   **已經淡成紙色的那一段**上（第 7 輪審計判死那條"取帶內最亮檔"的原因就在這）。
     */
    /**
     * 亮色页面底色（B4-03，业主 2026-10-23「背景太黄 ⇒ 改白一点」）。
     *
     * 旧的 `#F4F0E8` 黄度 `R−B = 12`；B4-03 改成 `#F8F6F1`（`7`）；
     * 2026-10-25 业主「整个应用的色调我还是感觉有点黄，可以再白一点」⇒ `#F8F7F4`（`4`）；
     * 2026-10-27 业主「背景太黄了，帮我改一种色调」⇒ `#F4F6F8`（`R−B = −4`）。
     * 那一步是**换色相**不是继续抬白：黄度到 0 以后再降没意义，而这枚冷白
     * 亮度反而从 93.0% 升到 94.4%，不算压灰。判据仍是同一条。
     * 同 ROI 改前/改后实测见 `verify/b4-03/`。
     * ⚠️ 相册那条路径的收尾正是渐到这一枚色（`fadeTo`）⇒ 这条同时是相册档变白的根。
     */
    val pageBackgroundLight: Color get() = background.paper(isDark = false)
    val pageBackgroundDark: Color get() = background.paper(isDark = true)

    /** 玻璃表面提亮色（淺色模式用）。 */
    val glassSurfaceLight = Color.White.copy(alpha = GLASS_SURFACE_ALPHA_LIGHT)

    /**
     * 低透明描邊（區分同色相鄰兩層時用）。
     *
     * 規範 §5：**用低透明白，不用實色灰線** —— 實色線在深色下像貼紙。
     */
    val strokeSubtle = Color.White.copy(alpha = 0.12f)

    /**
     * 卡片描邊。
     *
     * ## ★ 為什麼需要它（真機量到的數字）
     *
     * 深色下量測：卡片 `(36,36,36)` 對上漸層背景 `(15,26,52)`，
     * **對比度只有 1.11:1** —— 肉眼看不出卡片邊界，整頁糊成一片。
     *
     * 把卡片提亮到 `(40,40,40)` 也只是 1.22:1，**仍然不足**
     *（正常 UI 分層要 ≥3:1）。
     *
     * 真正的解法是**描邊**：一條 1dp 的亮線能明確畫出邊界，
     * 而它不改變卡片整體明度（所以不會像「把卡片調很亮」那樣刺眼）。
     *
     * 深色下用 10% 白（比 [strokeSubtle] 略淡，因為它要貼在更亮的卡片上）；
     * 淺色下用 8% 黑 —— 淺色卡片本來就比背景亮，需要暗線才看得出邊。
     */
    val cardStrokeDark = Color.White.copy(alpha = 0.10f)

    /** 淺色模式的卡片描邊（低透明黑）。 */
    val cardStrokeLight = Color.Black.copy(alpha = 0.08f)

    /** 依深淺回傳卡片描邊色。 */
    fun cardStroke(isDark: Boolean): Color = if (isDark) cardStrokeDark else cardStrokeLight

    // -----------------------------------------------------------------------
    // 頂欄漸變紗（規範 §6.2）
    // -----------------------------------------------------------------------

    /**
     * 頂欄紗的**出現曲線**：死區 22% + smoothstep。
     *
     * ## 為什麼要有死區
     *
     * 業主的原話是「要有個分界點，剛滾動一點點時不許出現紗」。
     * 沒有死區的話，手指一動紗就冒出來，看起來像螢幕髒了。
     *
     * ## 為什麼是 smoothstep 而不是線性
     *
     * 線性在起步處仍是「突然有紗」——因為眼睛對 alpha 的感知不是線性的。
     * smoothstep（`t²(3-2t)`）在兩端導數為 0，所以進出都平滑。
     *
     * @param progress 已滾距離 / 一屏可滾距離
     * @return 紗的 alpha（0~1）
     */
    fun veilAlpha(progress: Float): Float {
        val t = ((progress - VEIL_DEAD_ZONE) / (1f - VEIL_DEAD_ZONE)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 顶栏开始显现的滚动死区（30%）。 */
    const val VEIL_DEAD_ZONE = 0.30f

    /**
     * 紗的漸變形狀（四個 stop）。
     *
     * ## ★ 為什麼是這四個數
     *
     * 這是真機定稿值（規範 §6.2）：
     * ```
     * 0.00 → 1.00   頂部最實
     * 0.42 → 0.62
     * 0.78 → 0.16
     * 1.00 → 0.00   末端完全消失
     * ```
     *
     * 最後那 22% 是**專門留給「淡到零」**的，所以末端視覺上看不到分界線。
     * 少了它就會出現「一條硬直線」—— 那是規範紅線 #2。
     */
    fun veilStops(): Array<Pair<Float, Float>> = arrayOf(
        0.00f to 1.00f,
        0.42f to 0.62f,
        0.78f to 0.16f,
        1.00f to 0.00f,
    )

    /**
     * 頂欄紗的顏色。
     *
     * ## ★ 一個重要的判斷：這裡不用模糊，用**背景色**做紗
     *
     * 規範 §6.2 的方案是「30dp 模糊 + 竖向 alpha 擦除」。
     * 但那個要 `miuix-blur`，而它**要 minSdk 33、我們是 24**（見 §0.2 與
     * `miuix-skill/references/styling-icons-and-effects.md`：
     * 「On Android, blur requires API 33」）。
     *
     * 所以走規範自己推薦的**路線 B**：不模糊，用半透明背景色漸層。
     *
     * 效果差別多大？在**漸層背景**上差別很小 ——
     * 因為紗要解決的是「內容滾到頂欄下面時看不清標題」，
     * 而一層和背景同色、向下淡出的色帶就能做到。
     * 真正需要模糊的是「玻璃折射」那種質感（第 6 步再說）。
     *
     * @param isDark 深色用更深的紗（亮紗在深色下會發灰）
     */
    fun veilColorFor(backdrop: Color): Color =
        if (backdrop.luminance() < 0.5f) {
            // 深底：用頁面背景的暗端，稍微加深一點
            Color(0xFF0C1526).copy(alpha = 0.92f)
        } else {
            // 淺底：用頁面背景的亮端
            Color(0xFFEAF3FF).copy(alpha = 0.92f)
        }

    /**
     * 頁面底色那支漸層**最上面那一站** —— 也就是頂欄後面真正鋪出來的顏色。
     *
     * ⚠️ 這與 `MiuixTheme.colorScheme.background` 不是同一個東西：後者是**主題令牌**，
     * 前面那支是**這一屏的像素真相**（例如淺色主題 + 暗度壓到底的壁紙）。
     * 顶栏纱按它挑深浅，才不会出现「页面已经暗了、纱仍是亮的」。
     */
    fun pageTopColor(isDark: Boolean): Color = background.stops(isDark).first()

    // -----------------------------------------------------------------------
    // 背景層次
    // -----------------------------------------------------------------------

    /**
     * 背景漸層（亮色）。
     *
     * ## ★ 為什麼要有背景層次
     *
     * 規範 §2：「**沒有背景層次，玻璃就是灰塊。**
     * 這是『我的玻璃為什麼不好看』的頭號原因，**不是參數問題**。」
     *
     * 玻璃是靠「折射身後的東西」產生質感的。純色背景上折射的還是那個純色，
     * 看起來就跟一塊半透明色板沒區別。
     *
     * 三檔、相鄰差 3~6% 明度 —— 差距太大會有明顯條帶，太小看不出層次。
     */
    internal val lightBgStops: List<Color>
        get() = BackgroundPreset.DEFAULT.lightStops

    /**
     * 背景漸層（暗色）。
     *
     * ⚠️ **深色不是簡單反相**：分層靠這三檔深藍，而不是純黑。
     * 純黑在 OLED 上有「塗抹感」（滑動時黑色區域會拖影）。
     */
    private val darkBgStops: List<Color>
        get() = BackgroundPreset.DEFAULT.darkStops

    /**
     * 頁面背景刷。
     *
     * 用法：外殼的最底層 `Box(Modifier.fillMaxSize().background(AppColors.pageBrush(isDark)))`，
     * **內容照常疊在上面**。不要給每一頁都套一層 —— 那會讓滾動時背景跟著動，
     * 反而看得出接縫。
     *
     * @param isDark 由 `MiuixTheme.colorScheme.isDark` 或 controller 決定。
     */
    fun pageBrush(isDark: Boolean, preset: BackgroundPreset = background): Brush {
        val stops = preset.stops(isDark)
        // 斜向漸層（左上 → 右下）。
        //
        // ⚠️ 這裡**不能**用 `Offset.Infinite` ——
        // `linearGradient` 的 end 傳 Infinite 會讓漸層退化成單色
        //（它需要一對確定的端點才算角度）。
        // 用一個夠大的固定位移表達「左上到右下」這個方向即可，
        // 漸層本身會按實際繪製區域伸縮。
        return Brush.linearGradient(
            colors = stops,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(1000f, 1600f),
        )
    }
}
