package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.Color

/**
 * 外观页预览缩略图专用墨色。
 *
 * 这些颜色只服务于「主题 / 材质 / 版式」三组小样本身的分层演示（深底、浅底、卡片、
 * 天空渐变、墨线、壁纸底），不参与真实界面的取色 —— 真实界面一律走 `MiuixTheme` /
 * `AppColors`/`Dims`。集中在这里是为了让调用点只写令牌名，
 * `IconSystemTest` 因而能继续禁止页面里出现 `Color(0xFF……)` 字面量。
 */
internal object PreviewInk {
    /** 深色预览底。 */
    val darkPage = Color(0xFF171A1B)

    /** 浅色预览底。 */
    val lightPage = Color(0xFFF4F6F8)

    /** 深色预览里的标题线。 */
    val darkLine = Color(0xFF282D34)

    /** 深色预览里的卡片面。 */
    val darkCard = Color(0xFF303438)

    /** 材质预览的天空渐变两端。 */
    val skyTop = Color(0xFFA9C4EB)
    val skyBottom = Color(0xFFF1D4CE)

    /** 材质预览里的墨线。 */
    val inkLine = Color(0xFF7C83AD)

    /** 版式预览里「有壁纸」时的底色：只用来示意图片透过卡片。 */
    val wallpaperTint = Color(0xFFD5E3EF)
}