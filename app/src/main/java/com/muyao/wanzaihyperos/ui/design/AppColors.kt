package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
    private val lightBgStops = listOf(
        Color(0xFFEAF3FF), // 晨霧藍
        Color(0xFFDDE8FB),
        Color(0xFFEDE4F7), // 淡紫
    )

    /**
     * 背景漸層（暗色）。
     *
     * ⚠️ **深色不是簡單反相**：分層靠這三檔深藍，而不是純黑。
     * 純黑在 OLED 上有「塗抹感」（滑動時黑色區域會拖影）。
     */
    private val darkBgStops = listOf(
        Color(0xFF0A1324), // 深夜藍
        Color(0xFF0E1B34),
        Color(0xFF161436), // 靛紫
    )

    /**
     * 頁面背景刷。
     *
     * 用法：外殼的最底層 `Box(Modifier.fillMaxSize().background(AppColors.pageBrush(isDark)))`，
     * **內容照常疊在上面**。不要給每一頁都套一層 —— 那會讓滾動時背景跟著動，
     * 反而看得出接縫。
     *
     * @param isDark 由 `MiuixTheme.colorScheme.isDark` 或 controller 決定。
     */
    fun pageBrush(isDark: Boolean): Brush {
        val stops = if (isDark) darkBgStops else lightBgStops
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
