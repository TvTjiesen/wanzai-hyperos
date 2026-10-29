package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.Color

/**
 * 全局背景预设（业主 2026-10-29「全局背景的颜色也改改，做个自定义界面，让用户自己选择
 * 背景颜色，并且推荐几个主流的好看的颜色」）。
 *
 * ## 为什么是"预设"而不是取色盘
 *
 * 取色盘能选出对比度不及格的底色 —— 那时字还是这几颗字，只是看不见了。所以这里给
 * **五套算过对比度**的底：每套三档渐层（相邻差 3~6% 明度，见 [AppColors.pageBrush]）
 * 加一张卡面。五套在 [Tone.quiet] / [Tone.ink] 两档字色下的最坏对比度都在 4.5:1 以上，
 * 见 [BackgroundPreset.check] 里的实测值注释。
 *
 * ## 深浅两套必须成对给
 *
 * 只换亮档会在切深色时跳回冷白（用户以为"设了没生效"）。所以每套都带 darkStops。
 */
enum class BackgroundPreset(
    val id: String,
    val label: String,
    /** 亮档渐层：顶 → 纸 → 右下。中间那档就是主题的 background。 */
    val lightStops: List<Color>,
    /** 深档渐层：同序。 */
    val darkStops: List<Color>,
    /** 亮档卡面（比纸更亮，让卡片浮起来）。 */
    val cardLight: Color,
    /** 深档卡面（比最深那档稍亮）。 */
    val cardDark: Color,
) {
    // 现状即默认。quiet 5.14/4.96/4.69，ink 16.8/16.2/15.3，primary 7.20/6.95/6.58。
    Porcelain(
        id = "porcelain",
        label = "冷白",
        lightStops = listOf(Color(0xFFF8FAFC), Color(0xFFF4F6F8), Color(0xFFECF0F4)),
        darkStops = listOf(Color(0xFF101213), Color(0xFF131617), Color(0xFF171A1B)),
        cardLight = Color(0xFFFBFCFE),
        cardDark = Color(0xFF222526),
    ),

    // quiet 5.15/4.94/4.56，ink 16.8/16.1/14.9，primary 7.22/6.92/6.39。
    Cream(
        id = "cream",
        label = "米白",
        lightStops = listOf(Color(0xFFFCFAF5), Color(0xFFF8F5EE), Color(0xFFF1ECE1)),
        darkStops = listOf(Color(0xFF14120E), Color(0xFF171410), Color(0xFF1B1813)),
        cardLight = Color(0xFFFFFCF6),
        cardDark = Color(0xFF26221C),
    ),

    // quiet 5.06/4.84/4.58，ink 16.5/15.8/15.0，primary 7.09/6.79/6.41。
    Mist(
        id = "mist",
        label = "雾灰",
        lightStops = listOf(Color(0xFFF7F8FA), Color(0xFFF2F3F6), Color(0xFFEAEDF1)),
        darkStops = listOf(Color(0xFF101112), Color(0xFF131415), Color(0xFF171819)),
        cardLight = Color(0xFFFCFCFD),
        cardDark = Color(0xFF222324),
    ),

    // quiet 5.11/4.89/4.55，ink 16.7/16.0/14.9，primary 7.16/6.85/6.38。
    Sakura(
        id = "sakura",
        label = "樱粉",
        lightStops = listOf(Color(0xFFFDF8F9), Color(0xFFFBF2F4), Color(0xFFF6E9EC)),
        darkStops = listOf(Color(0xFF150F12), Color(0xFF181215), Color(0xFF1C1619)),
        cardLight = Color(0xFFFFFCFE),
        cardDark = Color(0xFF271F23),
    ),

    // quiet 5.16/4.94/4.66，ink 16.9/16.1/15.2，primary 7.23/6.91/6.53。
    Lake(
        id = "lake",
        label = "湖蓝",
        lightStops = listOf(Color(0xFFF7FBFD), Color(0xFFF0F6FB), Color(0xFFE7F0F7)),
        darkStops = listOf(Color(0xFF0E1215), Color(0xFF111619), Color(0xFF151B1F)),
        cardLight = Color(0xFFFBFDFE),
        cardDark = Color(0xFF202830),
    ),
    ;

    /** 这一套在亮档/深档下的"纸色"（渐层中间/最深那一档）。 */
    fun paper(isDark: Boolean): Color =
        if (isDark) darkStops.last() else lightStops[1]

    fun stops(isDark: Boolean): List<Color> = if (isDark) darkStops else lightStops

    companion object {
        /** 存进偏好里的永远是 [id]，不是序号（序号会随枚举顺序漂）。 */
        val DEFAULT: BackgroundPreset = Porcelain

        /** 非法 id 一律退回默认，绝不抛 —— 偏好里可能有上一版卸载残留的字符串。 */
        fun of(id: String?): BackgroundPreset =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
