package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.Type
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * iOS 那套"大标题 + 悬浮玻璃"顶栏的公共外壳。
 *
 * 这块被推翻过两次，教训记在这里免得再犯：
 *  1. 第一版是一条**均匀模糊 + 不透明色渐变**的宽带 —— 把大标题和 logo 一起盖住了，
 *     下边缘还是一刀切（用户原话"雷霆大模糊"）。
 *  2. 第二版改成 4 段不同半径的矩形直接摞起来 —— 段与段之间是硬切，看得见分界线。
 * 现在这版：
 *  - 顶栏中间在**折叠后**接住页面名（小标题），展开时完全透明 ——
 *    大标题还在原位时不出现第二份标题（业主否掉过“双标题”）；
 *  - 背后是三段**互相重叠、各自带软边**的渐变纱，软边由 AGSL 竖向 alpha 窗口实现
 *    （见 ui/liquid/VeilWindow.kt），叠加后模糊强度连续变化，没有任何一条横线；
 *  - 出现曲线有死区：大标题刚起步时完全不糊，过了分界点才 smoothstep 渐进出现。
 */
class PageChrome {
    /** 0 = 大标题还在原位（顶栏不出现），1 = 完全折叠 */
    var progress by mutableFloatStateOf(0f)

    /**
     * 「**当前这一页的列表正在滚**」—— 第 9 轮审计必修 4（全工程原本 `scroll` 命中 0 次）。
     *
     * 标准 §4：「静止时停更（内容没变就别重采样），**滚动时降采样**」。这块界面上的玻璃
     * （顶栏纱 / 底栏 / 搜索钮）都是**每帧各采一次全屏**，而列表滚动时背景每帧都在变 —— 正是最贵的时候。
     *
     * ⚠️ kyant 的 backdrop **没有暴露"降采样倍率"**（javap 核过它的 effects API：只有
     *   `blur` / `lens` / `vibrancy` / 高光），所以这里的"降采样"取的是**能拿到的那个把手**：
     *   滚动期间把最贵的一步（`lens` 折射，逐像素 SDF）**整条关掉 + 模糊减半**，停手后下一帧恢复。
     *   别把它写成"渲染到半分辨率" —— 那是另一件事。
     */
    var scrolling by mutableStateOf(false)

    /** 页面把列表的滚动状态报进来（入口只有这一个）。 */
    fun onScroll(scrolling: Boolean) {
        this.scrolling = scrolling
    }
}

val LocalPageChrome = staticCompositionLocalOf { PageChrome() }

/** 当前页的大标题文案，由 MainContent 提供，Page 自动渲染成列表首项 */
val LocalLargeTitle = compositionLocalOf { "" }

/**
 * 这一页是不是"活动页"。
 *
 * 换页动画期间离场页还在组合树里，它也会报告自己的滚动量。不加这个开关，
 * 两个页面会抢同一个 progress，顶栏会在切页那一瞬闪一下。
 */
val LocalPageActive = compositionLocalOf { true }

/** 底部玻璃栏此刻在不在。二级页要把它收起来，页面底部留白也得跟着变小 */
val LocalNavBarVisible = compositionLocalOf { true }

/**
 * 每一页自己的"活着的状态"：滚动位置 + 已经播过入场动画的条目。
 *
 * AnimatedContent 换 targetState 时目标页整棵子树是重建的，不缓存的话
 * 从二级页返回就会看到"整页刷新一遍"：滚回顶部 + 交错动画重播（用户明确否掉的行为）。
 * MainContent 按 Screen 存一份，返回时复用同一个 LazyListState 和 played 集合。
 */
class PageSlot {
    var listState: androidx.compose.foundation.lazy.LazyListState? = null
    var played: MutableState<Set<Int>>? = null
}

val LocalPageSlot = compositionLocalOf { PageSlot() }

/** 打开"表冠沉浸态"的入口，由 MainContent 提供，设置页头图点它 */
val LocalOpenCrownStage = compositionLocalOf<(() -> Unit)?> { null }

/** 顶栏栏身高度（不含状态栏） */
val IosBarHeight = 52.dp

/** 渐变纱往下多延伸的一段：模糊在这里彻底淡到没有 */
private val IosTailHeight = 44.dp

/*
 * 渐变纱：**只采样一次**。
 *
 * 上一版是"三段重叠软窗口"各画一次 drawBackdrop，真机截图证明那是错的：
 * 同一个 backdrop 在一帧里被三个节点先后采样，从第二段起就采到空层，
 * 而 drawBackdrop 采样为空时会退回"铺一层 surface 底色" —— 三段白膜叠起来
 * 就是用户骂了三次的"顶部雷霆大白块"，并且带的尽头是一条硬直线（分界线明显）。
 *
 * 现在：一次模糊 + AGSL 竖向软窗口收到带子底边以下（淡到零，没有尽头那条线），
 * 并且 onDrawSurface 什么都不画 —— 就算哪天采样又变空，最坏是"没有纱"，
 * 绝不会再糊出一块白。
 */
private const val VEIL_BLUR_DP = 30f
private const val VEIL_WIN_START = 0f
private const val VEIL_WIN_END = 1.12f

/**
 * 出现曲线：前 [VEIL_DEAD_ZONE] 完全不出现（用户要的"分界点"），
 * 之后 smoothstep 平滑升到满，不会"啪"地一下糊上来。
 */
private const val VEIL_DEAD_ZONE = 0.22f

private fun veilAlphaOf(progress: Float): Float {
    val t = ((progress - VEIL_DEAD_ZONE) / (1f - VEIL_DEAD_ZONE)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
fun statusBarTopPadding(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** 顶栏那一条的高度（状态栏 + 栏身），页面内容要让出来的距离 */
@Composable
fun iosHeaderInset(): Dp = statusBarTopPadding() + IosBarHeight

/**
 * 一颗液态玻璃面：vibrancy + 模糊 + 透镜折射 + 高光边。
 *
 * backdrop 为 null（系统不支持 runtime shader）时退化成半透明底色，不崩。
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    blurDp: Dp = 12.dp,
    refractionDp: Dp = 14.dp,
    tintAlpha: Float = 0.20f,
    baseAlpha: Float = 0.88f,
    /**
     * false = 这颗面不采页面内容，只画"浅色实心 + 描边高光"。
     *
     * 为什么给这个开关：真机连拍证明 Miuix 0.9.2 的 vibrancy/lens 在采样层为空/错位时
     * 会直接画出不透明黑（那颗按钮从 735 掉到 445 并停住），垫浅色底也盖不住 ——
     * 因为黑是画在垫底之上的。44dp 的小按钮实时折射本来就看不出来，先用确定的浅色，
     * 别再拿一个"糊不出来就变黑"的路径赌。
     */
    liveBlur: Boolean = true,
    /**
     * 非 null 時用這一份高光覆蓋默認那條細描邊。
     *
     * ★ 為什麼要這個口子（业主 2026-10-20：「你这个液态玻璃没有玻璃的感觉，
     *   我要你照抄底栏玻璃的代码」）：底欄那颗滑块的高光是 `BloomStroke`
     *   （兩個光源 + innerBlur），而這裡默認用的是一条普通描边 ——
     *   「玻璃感」差的就是那一口。所以不是再調 alpha，是把底欄那份高光拿過來。
     */
    specular: Highlight? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val surface = MiuixTheme.colorScheme.surface
    // 底栏容器铺的是 `surfaceContainer.copy(alpha = 0.4f)`（LiquidGlassNavigationBar 的
    // containerColor）。这里以前铺 `surface`：差的那一点色调就是"两块玻璃不像同一套"的来源之一。
    val container = MiuixTheme.colorScheme.surfaceContainer
    // ★ 工单第 19 轮：读**应用内主题**（`isInDarkTheme()`，同包不用 import），不是系统深浅色。
    //   这是每一屏都看得见的顶栏纱；上一轮自报「5 个调用点全跟」实为 4/5，漏的就是这一处。
    val dark = isInDarkTheme()
    val motion = rememberMotion()
    // ★ 第 9 轮审计必修 4：这块纱要在**滚动期降采样**。这个值必须在**组合期**读出来 ——
    //   `effects` 那个 lambda 不是 @Composable 上下文（在里面读 CompositionLocal 编译不过）。
    val scrolling = LocalPageChrome.current.scrolling
    // ★ 用户说了"别透明"（设置里的减弱透明度 / 系统高对比文字）⇒ **连采样都不做**，
    //   直接一块近实底 + 描边。这既是无障碍，也是黑块那类事故的最终兜底：
    //   不采层，就没有"采到空层画黑"这条路。
    if (!motion.glassIsTranslucent) {
        Box(
            modifier = modifier
                .background(
                    if (dark) container.copy(alpha = 0.97f) else container.copy(alpha = 0.94f),
                    shape,
                )
                .border(1.dp, if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f), shape),
            content = content,
        )
        return
    }
    val highlight = if (dark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    // 玻璃底下必须先垫一层不透光的浅色底：Miuix 的 vibrancy/lens 在"采样层是空的"
    // 时候不会退回纯色，而是把透明黑直接混出来 —— 真机连拍量到那颗按钮从 750 掉到 442
    // 并且停住（就是他说的"点底栏之后右上角变黑"）。垫底之后：采到内容就是玻璃，
    // 采空就是浅色实心，最坏是"不糊"，绝不会再出黑块。
    Box(
        modifier = modifier
            .background(surface.copy(alpha = baseAlpha), shape)
            .then(
            if (liveBlur && backdrop != null && backdropIsLive(backdrop) && rememberSampleSettled(backdrop)) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        // ★ 独立审计（v3 #12 第 2 轮 P1#4）：**磨砂档以前在 `liquid/` 这一栈里
                        //   只关 lens、不动 blur 与 tint**，而顶栏这层纱连 lens 都没关 ⇒
                        //   液态/磨砂画出来一模一样（真机量的 0.000 就是这么来的）。
                        //   现在统一吃 `glass/BarOptics.kt:263` 那条唯一的材质语义：
                        //   **磨砂 = 糊加重(×2.25) + 不折射 + 不做色彩振动**，由 `motion.refractsGlass` 决定。
                        val frosted = !motion.refractsGlass
                        // ★ 第 9 轮审计必修 4：**滚动期降采样**（标准 §4「滚动时降采样、静止时停更」）。
                        //   这一层纱是**每帧一次全屏采样**，而列表滚动时背景每帧都在变 —— 正是最贵的时候。
                        //   kyant 没暴露降采样倍率，所以这里的把手是：滚动期**不折射 + 模糊减半**，
                        //   停手后下一帧恢复（松手那一刻的观感与滚动中不同是刻意的：
                        //   滚动时人眼在追内容，糊度差一档看不出来；静止时才需要那层厚度）。
                        if (!frosted && !scrolling) vibrancy()
                        val veilBlur = (if (frosted) blurDp * 2.25f else blurDp) * (if (scrolling) 0.5f else 1f)
                        blur(veilBlur.toPx(), veilBlur.toPx())
                        // depthEffect/chromaticAberration 只在热调打开时才用：空采样帧上
                        // 这两个会画出不透明黑（就是那颗钮之前"变黑"的来路）
                        if (frosted || scrolling) {
                            // 磨砂：一道折射棱都不给；滚动中同理（这一条就是"降采样"的落点）
                        } else if (GlassTuning.depthLens >= 1f) {
                            lens(
                                refractionHeight = refractionDp.toPx(),
                                refractionAmount = refractionDp.toPx(),
                                depthEffect = true,
                                chromaticAberration = 0.35f,
                            )
                        } else {
                            // 折射量 = 折射高度，**和底栏同一个配比**（底栏：
                            // lens(refractionHeight = lensDp, refractionAmount = lensDp)）。
                            // 这里以前乘 0.7：折射带被削薄，盘里就只剩"糊"，没有"凸起来的一块"。
                            lens(
                                refractionHeight = refractionDp.toPx(),
                                refractionAmount = refractionDp.toPx(),
                            )
                        }
                    },
                    highlight = { specular ?: highlight },
                    onDrawSurface = { drawRect(container.copy(alpha = tintAlpha)) },
                )
            } else {
                Modifier.background(surface.copy(alpha = 0.72f), shape)
            }
        ),
        content = content,
    )
}

/**
 * 折叠顶栏。
 *
 * @param title 折叠后显示在栏身中央的小标题（展开时 alpha=0，不和大标题同时出现）
 * @param backdrop 仅为调用点兼容保留；当前顶栏不采样（见带子上的 2026-10-21 注释）
 * @param veilBlurDp 同上，当前未使用
 * @param actions 右上角槽位（搜索钮）
 */
@Composable
fun IosTopBar(
    title: String,
    chrome: PageChrome,
    canGoBack: Boolean,
    onBack: () -> Unit,
    backdrop: Backdrop?,
    /**
     * **这一屏顶栏后面真正铺出来的底色**（不是主题令牌）。必填、无默认值 ——
     * 浅色主题 × 夜航/暗度压到底时，天空已经是暗的，而主题令牌还是浅的 ⇒ 纱会"白压黑"。
     * 取法：首页传天空最上面那一站（`skyLookOf(...).colorAt(0f)`），其它页传 `AppColors.pageTopColor(...)`。
     */
    backdropColor: Color,
    modifier: Modifier = Modifier,
    forceHidden: Boolean = false,
    veilBlurDp: Float = GlassTuning.VEIL_DEFAULT_DP,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val surface = MiuixTheme.colorScheme.surface
    val statusTop = statusBarTopPadding()
    val bandHeight = statusTop + IosBarHeight + IosTailHeight
    val progress by animateFloatAsState(chrome.progress, label = "barCollapse")
    // 死区 + smoothstep：大标题刚起步时完全不糊
    // 沉浸态（表冠放大居中）时顶栏纱必须完全消失，否则会和居中大表冠打架
    val veilAlpha = if (forceHidden) 0f else veilAlphaOf(progress)
    // 与 `AppColors.veilColorFor` **同一套阈值与配色**（不另建一个 0.5）
    val veilBase = AppColors.veilColorFor(backdropColor)
    val motion = rememberMotion()
    // 减弱透明度 / 高对比：同一套渐变，但顶部更实。
    val solidBand = !motion.glassIsTranslucent

    Box(modifier = modifier.fillMaxWidth().height(bandHeight)) {
        // ★ 2026-10-21 真机定案：顶栏这条带子**不再采样模糊**。
        //   证据（课表/消息/应用/设置四页滚动截图）：30dp blur + 0.18~0.45 同色底
        //   压不住从下面滚过去的正文 —— 状态栏图标、居中小标题、列表文字三层叠着，
        //   谁都读不出来；带子本身还是每帧一次全屏采样的玻璃。
        //   顶栏是"内容从底下滚过去"的平面，不是展示材质的玻璃（底栏那颗滑块才是）。
        //   所以改成纯渐变遮罩：顶部近实底保证图标/标题对比度，向下收到透明，
        //   没有硬下沿；不碰采样层，也就不存在"空采样画黑"那条路。
        //   结束色用 `surface.copy(alpha = 0f)` 而不是 `Color.Transparent`：
        //   否则 sRGB 插值会在底部擦出一条发灰的暗边。
        if (veilAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(veilAlpha)
                    .background(
                        Brush.verticalGradient(
                            // 只换**基色**（按这一屏实际底色挑），alpha 阶梯一个不动
                            0f to veilBase.copy(alpha = if (solidBand) 0.98f else 0.96f),
                            0.62f to veilBase.copy(alpha = if (solidBand) 0.98f else 0.90f),
                            0.80f to veilBase.copy(alpha = if (solidBand) 0.90f else 0.52f),
                            1f to veilBase.copy(alpha = 0f),
                        ),
                    ),
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.height(statusTop))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IosBarHeight)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGoBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                        )
                    }
                } else {
                    Box(Modifier.height(IosBarHeight).width(48.dp))
                }
                // 折叠后的小标题：透明度跟顶栏纱走同一条曲线。
                // **不新增玻璃面**，只用 already 有的那条纱当底；展开时 alpha=0，
                // 所以不会和大标题同时出现（双标题是上一版被否掉的原因）。
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (title.isNotEmpty()) {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.alpha(veilAlpha),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

/**
 * 大标题：列表的第 0 项，跟着内容滚，滚上去之后顶栏才渐进出现。
 *
 * [leading] 是标题左边那一格 —— 现在只放 AI 小球（业主 2026-10-25「小球不要单独一个图层，
 * 要与其他内容同层、并且可以变化位置」）。它跟着这一行一起滚，滚出屏幕就跟着走，
 * 这正是「在内容层里」而不是「钉在顶栏上的一枚徽章」的区别。
 *
 * ## 三行大标题（2026-11-07 按 GPT 设计稿 v2.1 复刻）
 *
 * 设计稿里 apps / settings / messages 三页的第一屏都是**左边一颗球 + 右边三行**：
 *
 * ```
 * ○   校园服务                      ← kicker（eyebrow）11.29px 灰
 *     校园集合                      ← 标题 29.35px / 600 / -1px
 *     79 个来源入口 · 原生与网页分开   ← 副行 12.42px 灰
 *     ─────────────────────────────  ← 1px 分隔线，横跨内容宽
 * ```
 *
 * 板宽 392px ↔ 本机 347dp（系数 0.886）换算：
 * - eyebrow `11.29px × 0.886 = 10sp` = [Type.micro]（本工程字号下限，再小不许）
 * - 标题 `29.35px × 0.886 = 26sp` = [Type.displayTitle]，**和原来的单行标题同一个字号**
 *   （设计稿的 29.35 就是照着 26sp 反推出来的），所以这次不新造字号档
 * - 副行 `12.42px × 0.886 = 11sp` = [Type.caption]
 * - 球 `42px × 0.886 = 37dp` ≈ [AiBall.DIAMETER] 36dp，**不用放大**
 *
 * [kicker] / [subtitle] 都为空时退回原来的单行布局，老调用点一个字都不用改。
 */
@Composable
fun LargeTitle(
    text: String,
    topInset: Dp,
    leading: (@Composable () -> Unit)? = null,
    kicker: String = "",
    subtitle: String = "",
    divider: Boolean = false,
) {
    val threeLine = kicker.isNotEmpty() || subtitle.isNotEmpty()
    val onBackground = MiuixTheme.colorScheme.onBackground
    val quiet = MiuixTheme.colorScheme.onSurfaceVariantSummary

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 左右不再自己缩 8dp：这一页的正文列已经是 `Dims.screenH`(16dp)，
                // 标题再缩一次就和正文不在一条竖线上（板上两者都是 18px）。
                .padding(
                    start = 0.dp,
                    end = 8.dp,
                    top = topInset,
                    // 三行版把底距交给下面那条分隔线管，改由 [heroBottom] 控制。
                    bottom = if (threeLine) HeroBottom else 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(if (threeLine) HeroBallGap else LargeTitleLeadingGap))
            }
            if (!threeLine) {
                Text(
                    text = text,
                    // 板 `.topbar h2{font-size:27px;font-weight:740;letter-spacing:-.02em}`
                    // × 0.886 ⇒ **24sp**（`Type.displayTitle`）。原来 32sp 比板大 31%，
                    // 把「大标题 : 正文」的字阶比从 1.8:1 拉到 2.5:1，五屏一起受影响。
                    fontSize = Type.displayTitle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                    lineHeight = Type.lineDisplayTitle,
                    color = onBackground,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    if (kicker.isNotEmpty()) {
                        Text(
                            text = kicker,
                            fontSize = Type.micro,
                            fontWeight = Type.weightBody,
                            // 板 `.eyebrow{letter-spacing:1px}` × 0.886 ⇒ 0.09em 左右；
                            // 中文加字距主要是给「校园服务」这种四字词组呼吸，取 0.08em。
                            letterSpacing = 0.08.em,
                            color = quiet,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    Text(
                        text = text,
                        fontSize = Type.displayTitle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em,
                        lineHeight = Type.lineDisplayLoose,
                        color = onBackground,
                        maxLines = 1,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            fontSize = Type.caption,
                            fontWeight = Type.weightBody,
                            lineHeight = Type.lineCaption,
                            color = quiet,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        if (threeLine && divider) {
            // 板：hero 底 → 线间隔 21px × 0.886 ≈ 18.6dp，但上面已有 HeroBottom(14dp)，
            // 再补 4dp 视觉上正好。线本身 1px，半透明到不抢内容。
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .height(HairlineHeight)
                    .background(MiuixTheme.colorScheme.dividerLine),
            )
        }
    }
}

/** 大标题左边那颗小球与标题字之间的间隙（球自己还有 3dp 命中区外扩，视觉上是 6dp 多一点）。 */
private val LargeTitleLeadingGap = 10.dp

/**
 * 三行版英雄区：球与文字列的间隙。
 *
 * 板：球右缘 x=64、文字左缘 x≈80~85 ⇒ 间隙 16~21px，× 0.886 = **14~18dp**。
 * 本机球自带 3dp 命中区外扩（视觉上右缘在 3dp 里侧），所以这里给 12dp，
 * 加起来正好落在 15dp，与板的中位对齐。
 */
private val HeroBallGap = 12.dp

/** 三行版英雄区：三行文字底 → 分隔线之间的留白。 */
private val HeroBottom = 14.dp

/** 分隔线高度。1px 级别的头发丝，在高密度屏上靠 0.5dp 落地。 */
private val HairlineHeight = 0.5.dp
