package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.muyao.wanzaihyperos.ui.design.GlassLiveness
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **液態玻璃容器 —— 折射走庫自帶的 `lens()`**。
 *
 * ## ★ 為什麼不再用本项目手搓的 AGSL
 *
 * 我把 `com.kyant.backdrop.effects.LensKt` 內嵌的 shader 源碼讀出來對過：
 * 它就是 `sdRoundedRect` + `gradSdRoundedRect` + `depthEffect` 那套 SDF 折射，
 * 和原先 `LiquidGlassShader.kt` 手寫的**演算法完全一致**。手寫那份等於重新發明一遍，
 * 還多背一個坑：`BackdropEffectScope.size` 在 `effects` 裡是 `Size.Unspecified`（NaN），
 * NaN 進 SDF ⇒ 採樣座標 NaN ⇒ `content.eval(NaN)` 拿回原樣內容 ⇒
 * **看上去「接上了」但一點折射都没有，而且参数怎麼調像素都不變**。
 *
 * 庫的 `lens()` 自己也讀 `size`，所以這裡**照樣要防這一手**：
 * 元件尺寸一律由調用方在**組合期**算好（`BoxWithConstraints`），
 * ⚠️ 不能用 `onSizeChanged` —— `effects` 只在首次組合求值一次，那時它還沒觸發。
 *
 * ## ★★ 一個元素只許一條渲染路徑
 *
 * 折射 = `lens()`；高光 = [bloomStrokeHighlight]（帶重力方向）。
 * 不要同時再掛 `drawBackdrop(highlight = ...)` 那個庫原生槽位 —— 兩套疊會互相蓋掉（紅線 #2）。
 *
 * ## ★★★ 被採樣的內容裡絕對不能有玻璃
 *
 * 玻璃若被畫進被採樣的 layer ⇒ 採樣到自己 ⇒ 無限遞迴 ⇒ 渲染卡死 ⇒
 * 系統以 `coldStart timeout` 殺進程。**表現是「啟動即死」，logcat 裡沒有 FATAL EXCEPTION，
 * 只有 `onTaskVanished`** —— 別拿「日志搜不到」當「沒發生」。
 * 所以 content 與 overlay 分成兩個 slot（見 [GlassBackdropHost]）。
 *
 * @param backdrop 折射來源。null ⇒ 實心降級。
 * @param pressFraction 0~1 按下程度：按下去折射更實、高光更亮。
 * @param liveness 採樣層存活判定；非 Alive ⇒ 不開真玻璃（這條是上一版缺的接線）。
 */
/**
 * 「这一面 `GlassSurface` 此刻**真的**走哪条渲染路径」—— 判断只有这一份，
 * [GlassSurface] 自己也是拿它分支的。
 *
 * ★ 为什么要暴露给调用方（v3 #12 第 7 轮，业主真机量到「三档逐位相同」之后）：
 *   调用方要按**画面上真正铺出来的那个面**决定字色，而"会不会降级"不是一个数说了算 ——
 *   档位、系统减弱透明度、采样层活不活、刚换上来的头两帧，任何一条都会把玻璃变成实心纸。
 *   以前调用方只读得到 `LocalBarMaterial`，于是出了「代码说液态、画面是纸、字色按纸判」
 *   这一类只有肉眼才能发现的错；而肉眼在这个工程里就是业主的日常主力机。
 *
 * 任何一种降级都折成 [BarMaterial.SOLID]（它的语义本来就是"不采样"），不多造一个态。
 */
@Composable
fun rememberEffectiveMaterial(
    backdrop: Backdrop?,
    material: BarMaterial = LocalBarMaterial.current,
    liveness: GlassLiveness.Verdict = LocalGlassLiveness.current,
): BarMaterial {
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val sampleIsLive = com.muyao.wanzaihyperos.ui.design.liquid.backdropIsLive(backdrop)
    val sampleSettled = com.muyao.wanzaihyperos.ui.design.liquid.rememberSampleSettled(backdrop)
    val renderMode = rememberGlassRenderMode(material = material, liveness = liveness)
    // ⚠️ 四个判断都要**无条件**算完：`rememberSampleSettled` / `rememberGlassRenderMode` 是
    //    composable，短路跳过会让 remember 的槽位在两帧之间换位置（内部状态被重置成"没画过"）。
    val solid = backdrop == null ||
        !motion.glassIsTranslucent || !sampleIsLive || !sampleSettled ||
        renderMode == GlassRenderMode.FALLBACK
    return if (solid) BarMaterial.SOLID else material
}

@Composable
fun GlassSurface(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    material: BarMaterial = LocalBarMaterial.current,
    cornerRadius: Dp = 24.dp,
    pressFraction: Float = 1f,
    pressedScale: Float = 1f,
    style: GlassSurfaceStyle = GlassSurfaceStyle.CONTAINER,
    liveness: GlassLiveness.Verdict = LocalGlassLiveness.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val p = pressFraction.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(cornerRadius)
    // ★ 用户要"别透明"（设置里的减弱透明度 / 系统高对比文字）⇒ **强制走 FALLBACK**，
    //   连采样都不做。这一支是首页卡片/課表卡片用的那条栈（另一支在 `liquid/IosChrome`），
    //   两边都要接，否则就是"底栏变实了、卡片还在赌采样"——半套比没有更难看。
    //
    // ★ 独立审计（v3 #12 第 1 轮，禁令 1 真漏②）：**这一面玻璃以前一道闸门都没有**。
    //   它只靠 `LocalGlassLiveness`（判据 = 尺寸 > 0），而 `liquid/PageBackdrop.kt:73-76`
    //   明文写着这个判据骗过一次 —— "登记过坐标"不等于"画过内容"，
    //   Miuix 拿空缓冲去模糊/折射铺出来的是**不透明黑**（T67 那条黑带就是这么来的）。
    //
    // ★ v3 #12 第 7 轮：所有闸门收进 [rememberEffectiveMaterial] **一份**，
    //   因为调用方也得知道"这一刻到底会不会降级"（它要按真正铺出来的那个面算字色）。
    //   以前调用方只能读 `LocalBarMaterial`，于是出了「代码说液态、画面是纸、字色按纸判」
    //   这种只有肉眼能发现的错。
    val effectiveMaterial = rememberEffectiveMaterial(backdrop, material, liveness)
    // 通透度（外观页那颗滑块）必须在**组合期**读出：下面 `onDrawSurface` 是 DrawScope，
    // 不是 @Composable 上下文，在那里调 `glassOpacityFactor()` 编译不过。
    val opacityFactor = glassOpacityFactor()

    // ── 降級外觀：實心 + 1dp 低透明描邊 ──────────────────────────
    //
    // 規範 §6.4：採樣為空時組件**不報錯**，只是鋪一層不透明底色。
    // 所以寧可主動退成一個過得去的外觀，也不要被動變成一塊突兀的黑/白。
    // `backdrop == null` 这一支行为上是 [rememberEffectiveMaterial] 已经折进 SOLID 了的，
    // 留在这里是为了让下面那条玻璃路径拿到非空智能转换 —— 去掉它编译不过，
    // 换成 `backdrop!!` 更是拿运行期炸换编译期。
    if (effectiveMaterial == BarMaterial.SOLID || backdrop == null) {
        val fallback = GlassLiveness.fallbackFor(
            isDark = isDark,
            // ★ 深色下**不能**用 `spec.surfaceTint`：那是一枚冷调**浅白**（`GlassSpec.DARK` =
            //   0xFFF2F5FC，它给的是玻璃表面的「提亮」，不是纸面），0.88 铺出来是一块近白的卡，
            //   再压上深色主题的白字就是白压白。v3 #12 走读到 `cardFaceAlpha(SOLID, dark)`
            //   那条分支时撞见：三档里唯一"实心"的那档在深色下反而是最亮的。
            //   降级 = 实心纸面 ⇒ 拿主题的纸面色。
            surface = (
                if (isDark) MiuixTheme.colorScheme.surfaceContainer else spec.surfaceTint
                ).copy(
                    alpha = if (isDark) GlassSpec.FALLBACK_ALPHA_DARK
                    else GlassSpec.FALLBACK_ALPHA_LIGHT,
                ),
        )
        Box(
            modifier = modifier
                .background(color = fallback.containerColor, shape = shape)
                .border(fallback.strokeWidthDp.dp, fallback.strokeColor, shape),
            content = content,
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        // ★ 尺寸在組合期算好，傳進 effects。這是折射能不能生效的前提。
        val density = LocalDensity.current
        val sizePx = Size(
            with(density) { maxWidth.toPx() },
            with(density) { maxHeight.toPx() },
        )
        // ★ 材質三態都要真的走到：磨砂 = **只有模糊、不折射**。
        //   少了這一步，FROSTED 與 LIQUID 畫出來一樣，那個選項就是假的。
        //   判斷抽到 [styleFor] 是因為它必須能被單測覆蓋（繪製路徑裡的 when 測不到）。
        val effective = styleFor(material = effectiveMaterial, style = style, frostBlur = spec.frostBlur)
        // ★ 光源方向也必須在組合期讀（`effects` 只跑一次；讀進 effects 裡就不會跟著重力轉）。
        val lightDir = rememberGravityLightDir(effective.lightDegrees)
        val highlight = gravityHighlight(lightDir = lightDir, style = effective, pressFraction = p)
        val surfaceColor = when (effective.surfaceColor) {
            GlassSurfaceColor.WHITE -> Color.White
            GlassSurfaceColor.SURFACE_CONTAINER -> MiuixTheme.colorScheme.surfaceContainer
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        // ① 色彩振動：透出來的東西更鮮，這是 iOS 那層「活」的感覺
                        if (effective.lensHeight > 0.dp) vibrancy()
                        // ② 只糊一點點（**引擎收 px，不收 Dp**）
                        if (effective.blur > 0.dp) blur(effective.blur.toPx())
                        // ③ 折射（庫自帶那條路徑）。depth / 色散兩個開關**與參考實現一致地關掉** ——
                        //    開著的時候真機上在折射帶內邊界出現一條 1px 暗線，見 [BarOptics.LENS_DEPTH]。
                        if (effective.lensHeight > 0.dp && effective.lensAmount > 0.dp) {
                            lens(
                                effective.lensHeight.toPx() * (0.7f + 0.3f * p),
                                effective.lensAmount.toPx() * p,
                                BarOptics.LENS_DEPTH,
                                BarOptics.LENS_DISPERSION,
                            )
                        }
                        // ④ 雙光源高光，主光方向跟著重力轉
                        if (highlight.alpha > 0f) {
                            bloomStrokeHighlight(
                                highlight = highlight,
                                shape = shape,
                                // ⚠️ 只传有限尺寸：maxHeight 在 effects 求值那一刻可能是
                                //    Constraints.Infinity ⇒ halfView.y = ∞ ⇒ SDF 退化成一维、
                                //    整块卡面落进高光带 ⇒ 玻璃被涂成实色板（#58 第 4 条）。
                                //    传 null 让它退回 BackdropEffectScope.size（真尺寸）。
                                explicitSize = finiteSizeOrNull(sizePx),
                            )
                        }
                    },
                    onDrawSurface = {
                        // 表面色要透。白感靠「模糊後的背景 + 高光」，不靠堆白漆。
                        drawRect(color = surfaceColor.copy(alpha = (effective.surfaceAlpha * opacityFactor).coerceIn(0f, 1f)))
                    },
                    layerBlock = {
                        if (pressedScale != 1f) {
                            scaleX = pressedScale
                            scaleY = pressedScale
                        }
                    },
                )
                // ★ 必須裁形狀：折射會往元件外抓像素，圓角之外那塊不裁會露出異色方塊
                //   （真機上表現為「底欄右邊多出來一塊白」）。
                .clip(shape),
            content = content,
        )
    }
}

/**
 * 玻璃折射來源：把 [content] 畫進一個可被採樣的層，[overlay] 裡的玻璃採樣它。
 *
 * ## 為什麼這裡帶一個「活不活」的判據
 *
 * 「座標非空」不等於「裡面有內容」—— 我們拿它當存活判據被騙過一次。
 * 這裡的正面證據是**這一層是不是真的被畫過、而且尺寸非零**（首幀 0×0 不算活著）。
 * 判定通過 [LocalGlassLiveness] 交給所有玻璃元件；頁離場時清回 [GlassLiveness.Verdict.Unknown]，
 * 於是「切頁之後玻璃糊成實心色」那條路徑被主動攔掉。
 *
 * ⚠️ **每個頁面一份 host**（外層用 `key(section)` 重建）。共享一份會被離場頁清掉座標，
 * 表現是「用著用著實時模糊沒了」。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val liveness = remember { mutableStateOf<GlassLiveness.Verdict>(GlassLiveness.Verdict.Unknown) }

    DisposableEffect(Unit) {
        // 離場就把結論收回來：下一頁不該沿用上一頁的「活著」。
        onDispose { liveness.value = GlassLiveness.Verdict.Unknown }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // ★ 被采样的这一层**必须整屏都不透明**。以前它只有天空那 250dp 有像素，
                //   其余是**透明** —— 而 kyant 拿透明/空区域去折射不是"没效果"，是铺**黑**
                //   （T67 那条黑带、以及 v3 #12 第 7 轮在模拟器上量到的"此刻卡 = 白@α 压在黑上"
                //   是同一个机制：卡带实测 (159,163,165) 正好等于 0.66 的白压在黑上，std 2.5，
                //   而它身后那张照片的 std 是 64.4 ⇒ 透过来的像素一个都没有）。
                //   ★★ 2026-10-24 真机复现（此刻卡内 U 形暗带 (76,75,73) = 0.30x251 压纯黑）：
                //   光有这层刷子还不够 —— **它必须排在 layerBackdrop 的里面**。
                //   Modifier 链是「外先内后」：写成 background() 再 layerBackdrop() 时，那层刷子
                //   在录制开始**之前**就画完了，采到的只有 content()（250dp 天空盒），
                //   天空盒以下依旧透明 ⇒ 依旧铺黑。换成 layerBackdrop() 再 background() 之后
                //   整屏不透明才算真进了采样（模拟器实测：暗带 57 行 → 0 行，其余像素不动）。
                //   守卫：GlassBackdropLayerTest 读本文件断言这个先后，改回去就会红。
                //   补一层**与外壳同一支** pageBrush 之后：坐标对得上 ⇒ 采到真内容；
                //   坐标万一偏了 ⇒ 采到的是纸色（最坏"玻璃不透"），而不是把整块卡染黑。
                //   用同一支刷子而不是 `colors.background`：外壳 `WanzaiMainShell.kt:277`
                //   画的就是它，这样这一发在正常路径上是**视觉零变化**。
                .layerBackdrop(backdrop)
                .background(
                    com.muyao.wanzaihyperos.ui.design.AppColors.pageBrush(
                        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
                    ),
                )
                .drawWithContent {
                    drawContent()
                    val verdict = GlassLiveness.verdictOf(
                        contentWidthPx = size.width,
                        contentHeightPx = size.height,
                    )
                    // 只在結論真的變化時寫，避免逐幀重組。
                    if (liveness.value != verdict) liveness.value = verdict
                },
        ) {
            content()
        }
        CompositionLocalProvider(LocalGlassLiveness provides liveness.value) {
            overlay(backdrop)
        }
    }
}

/** 目前的採樣層存活判定。host 之外讀到 [GlassLiveness.Verdict.Unknown] ⇒ 走降級。 */
val LocalGlassLiveness = staticCompositionLocalOf<GlassLiveness.Verdict> {
    GlassLiveness.Verdict.Unknown
}

/** 目前的折射來源。 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 依深淺提供 [GlassSpec] 與 [BarMaterial]。 */
@Composable
fun GlassTheme(
    override: GlassSpec? = null,
    // ★ 参数改成**必填非空**（第 2 轮 P0#1 的根因就是那个默认 null 会退成常量 LIQUID）：
    //   以后任何一层想再包一个"不传 material 的 GlassTheme"就直接编译不过，
    //   不会再出现"外层算好的档位被内层静默覆写"这种只能靠读源码才发现的事。
    //   唯一调用点：`WanzaiMainShell.kt` 传 `LocalBarMaterial.current`。
    material: BarMaterial,
    content: @Composable () -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val spec = override ?: GlassSpec.forDark(isDark)
    CompositionLocalProvider(
        LocalGlassSpec provides spec,
        LocalBarMaterial provides material,
        content = content,
    )
}

/** 玻璃底欄標準高度（量自參考：內容 62dp）。 */
val GlassBarHeight = 62.dp
