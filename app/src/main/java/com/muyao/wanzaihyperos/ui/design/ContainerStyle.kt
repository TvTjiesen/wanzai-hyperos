package com.muyao.wanzaihyperos.ui.design

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush

/**
 * 容器背景的两版风格。
 *
 * ## 这两个词到底指什么
 *
 * 设计稿（campus-redesign-v2.1）里其实**没有**一层「圆角容器包住整页」的结构 ——
 * 逐像素量过五张主截图：`.page` 直接从 x=18px 的页边距开始铺内容，
 * 没有任何内缩的圆角层。稿子里叫 `--container` 的那个变量（`#E5E9F4`）
 * 是**次动作底 / 图标底**（`.btn`、`.pill`、`.task-icon` 的浅色填充），
 * 不是页面容器。
 *
 * 所以「保留容器背景」在这台机器上唯一说得通的落地，就是**页内那层承载面**：
 *
 * 1. **`Designed`（默认，一比一复刻稿子的做法）**
 *    页内天光（`HomeSky` 的渐变收口）+ 纸张底色 + 卡面分层，
 *    三者叠出「内容浮在一张有光线的纸上」的层次。这是稿子的观感。
 *
 * 2. **`Bare`（去掉容器背景，只留最外层整页背景）**
 *    页内天光撤掉，`AppColors.pageBrush` 那层整页渐变直接透到内容底部；
 *    卡片依旧是卡（它有自己的面），但**页面不再自带一层底色**。
 *    结果是更平、更亮、更像「一整块屏幕」而不是「一张纸」。
 *
 * ## 为什么用 CompositionLocal 而不是到处读 UiPrefs
 *
 * 读 `UiPrefs` 的地方必须能参与重组，而 `UiPrefs` 的字段是 `mutableStateOf`，
 * 本来也能重组；但天光/纸色这种「一屏只有一次决定」的值会被很多层读到，
 * 让每一层各自去读会造成两件事：
 * - 开关切换时**局部先变、局部后变**，出现半秒的错位；
 * - 单元测试没法在不碰磁盘的情况下验证渲染分支。
 *
 * 所以：**根上读一次，往下用 CompositionLocal 传**。
 */
enum class ContainerStyle(val id: String, val label: String) {
    /** 保留容器背景（稿子原样）：页内天光 + 纸张底色 + 卡面分层。 */
    Designed("designed", "保留容器背景"),

    /** 去掉容器背景：只留最外层那一层整页背景。 */
    Bare("bare", "只留最外层背景"),
    ;

    companion object {
        val DEFAULT = Designed

        fun of(id: String?): ContainerStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 当前生效的容器背景风格。
 *
 * 默认 [ContainerStyle.Designed]，好让**没接这根线的页面**保持既有样子 ——
 * 这是个外观开关，不该因为某个页面没读完文档就变样。
 */
val LocalContainerStyle = staticCompositionLocalOf { ContainerStyle.DEFAULT }

/**
 * 「这一层要不要铺页内容器背景」。
 *
 * 用法：外壳里那句 `Modifier.background(AppColors.pageBrush(isDark))` 换成
 * `Modifier.containerBackground(isDark)`，一行改完，两版都在。
 *
 * ## 为什么 `Bare` 是"什么都不画"而不是"换一个更浅的色"
 *
 * 「去掉容器背景、只保留最外层整页背景」这句话里，**最外层那一层仍然在** ——
 * 它由根节点（`WanzaiApp`）画一次，覆盖整块屏幕。所以页内这一层要做的
 * 恰恰是**让开**：不铺、不遮，把根上那层整页渐变直接透出来。
 *
 * 如果这里改成"换一个更浅的色"，那么根上那层就永远看不见了，
 * 两版会退化成"同一种底色、两种深浅"，白白多一个开关。
 */
fun Modifier.containerBackground(isDark: Boolean): Modifier =
    composed {
        if (LocalContainerStyle.current == ContainerStyle.Bare || LocalThemeCustomization.current.useSystemWallpaper) {
            this
        } else {
            background(AppColors.pageBrush(isDark))
        }
    }

/** 同上，但给不需要 `isDark` 的场景（调色板外壳之类）。 */
@Composable
fun containerBrushFor(isDark: Boolean): Brush? =
    if (LocalContainerStyle.current == ContainerStyle.Bare) null else AppColors.pageBrush(isDark)

