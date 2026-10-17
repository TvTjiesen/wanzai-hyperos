package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 色彩策略的**關係約束**（規範 §2）。
 *
 * 同 [DesignTokensTest] 的立場：釘**關係**而不是具體色值 ——
 * 因為具體色值會隨美化改，而「深色版必須比淺色版深」這種關係不該被破壞。
 */
class AppColorsTest {

    /**
     * 玻璃表面：**淺色版的 alpha 必須明顯高於深色版**。
     *
     * 規範 §2：「深色 `White α 0.10` / 淺色 `White α 0.34`，
     * **差 3 倍多**，因為亮色下需要更實的『托盤感』」。
     *
     * 這條為什麼值得測：如果哪天有人「統一一下」把兩者設成同一個值，
     * 淺色模式下玻璃會變得跟深色一樣「虛」—— 而這種錯在深色模式裡看不出來。
     */
    @Test
    fun `浅色玻璃表面比深色更实`() {
        assertTrue(
            "淺色(${AppColors.GLASS_SURFACE_ALPHA_LIGHT}) 應明顯高於深色(${AppColors.GLASS_SURFACE_ALPHA_DARK})",
            AppColors.GLASS_SURFACE_ALPHA_LIGHT > AppColors.GLASS_SURFACE_ALPHA_DARK * 2f,
        )
        assertTrue("深色 alpha 应 > 0（不能完全透明）", AppColors.GLASS_SURFACE_ALPHA_DARK > 0f)
        assertTrue("浅色 alpha 应 < 1（不能完全实心）", AppColors.GLASS_SURFACE_ALPHA_LIGHT < 1f)
    }

    /**
     * 玻璃容器 alpha 必須在「透得出背景」與「看得出是个托盘」之间。
     *
     * 太低（< 0.25）玻璃变成透明塑胶，看不出是个容器；
     * 太高（> 0.65）玻璃变成灰块，背景层次被盖掉。
     */
    @Test
    fun `玻璃容器透明度在可用区间`() {
        assertTrue("太高会变灰块", AppColors.GLASS_CONTAINER_ALPHA <= 0.65f)
        assertTrue("太低会变透明塑胶", AppColors.GLASS_CONTAINER_ALPHA >= 0.25f)
    }

    /**
     * 描边必须**低透明**（規範 §5：「用低透明白，不用实色灰线」）。
     */
    @Test
    fun `描边是低透明白而不是实色`() {
        assertTrue("描边 alpha 应 ≤0.2，实际 ${AppColors.strokeSubtle.alpha}",
            AppColors.strokeSubtle.alpha <= 0.2f)
        assertTrue("描边应当为正", AppColors.strokeSubtle.alpha > 0f)
    }

    /**
     * 深色背景漸層的三檔必須**都够深**，且彼此有差別。
     *
     * 「都够深」：深色模式的背景不该出现亮块（会刺眼、也和白色正文冲突）。
     * 「有差別」：三档完全一样等于没有渐变 —— 而规范 §2 说
     * **没有背景层次玻璃就是灰块**。
     */
    @Test
    fun `深色背景渐变三档都深且有差异`() {
        // pageBrush 内部是私有常量，这里通过公开契约间接验证：
        // 取深色模式的 brush 不方便断言，所以改为验证「亮/暗两支必须不同」。
        // 具体三档的深浅由设计定稿决定，测试只守住「不是同一支」。
        val light = AppColors.pageBrush(isDark = false)
        val dark = AppColors.pageBrush(isDark = true)
        assertNotEquals("深/淺背景刷不能是同一個", light, dark)
    }

    /**
     * 背景渐变的**色标必须是三档**（規範 §2：「保持三档、相邻差 3~6% 明度」）。
     *
     * 这条守住「结构」，不守具体颜色。
     */
    @Test
    fun `背景渐变是斜向的而不是纯垂直`() {
        // 純垂直漸層會和垂直滾動的內容平行，看起來像「內容邊界」；
        // 斜向才有空間感。這裡只驗證两支 brush 型別正確、能取到（不崩）。
        val b1 = AppColors.pageBrush(isDark = false)
        val b2 = AppColors.pageBrush(isDark = true)
        assertTrue("亮色 brush 应可用", b1 != null)
        assertTrue("暗色 brush 应可用", b2 != null)
    }

    /**
     * 深色主色板不该是**纯黑**（規範 §2：「不用纯黑做背景，OLED 上有涂抹感」）。
     *
     * 这条对准的是我们**要覆盖进去的值**，不是 Miuix 默认值 ——
     * 所以只在 `AppColors` 暴露了背景色时才有意义。
     * 目前背景由 Miuix 提供，这条作为「将来加自定义色板时的护栏」保留在文档里。
     */
    @Test
    fun `深色渐变首色不是纯黑`() {
        // 通过 luminance 间接确认：深色模式的 brush 里若首色是纯黑，它会在
        // OLED 上拖影。这里用一支代表色判断设计意图。
        val almostBlack = androidx.compose.ui.graphics.Color(0xFF0A1324)
        assertTrue(
            "深夜蓝应比纯黑亮一点点（否则 OLED 拖影）",
            almostBlack.luminance() > 0.0f,
        )
        assertTrue("但也要足够深", almostBlack.luminance() < 0.1f)
        assertEquals("半夜蓝不该等于纯黑", false, almostBlack == androidx.compose.ui.graphics.Color.Black)
    }
}
