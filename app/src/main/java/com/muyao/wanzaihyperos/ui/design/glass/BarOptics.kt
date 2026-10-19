package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.blur.sensor.DeviceTilt
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt

/**
 * **液態玻璃底欄的光學定稿值** —— 目標效果 = 變焦環 App（OmniRing）真機上那一套。
 *
 * ## 為什麼數值抽在這裡
 *
 * 這一份是從參考實現裡**逐條讀出來的常數**（`GlassTuning.BAR_DEFAULT_DP = 4f`、
 * `LENS_DEFAULT_DP = 24f`、`surfaceContainer.copy(alpha = 0.4f)`、
 * `highlight.alpha = 0.75f / 0.85f`、光源基準角 −45° / +90°），不是湊出來的。
 * 定稿值一旦散進組件裡就沒人知道哪個數還算數（規範紅線 #11）。
 *
 * ## ★ 一個元素只許一條渲染路徑
 *
 * 折射 = 庫自帶的 `com.kyant.backdrop.effects.lens(...)`；
 * 高光 = [bloomStrokeHighlight]（Miuix BloomStroke 移植版，帶光源方向）。
 * **不要**再疊 `drawBackdrop(highlight = ...)` 那個庫原生槽位（規範紅線 #2：兩套會互相蓋掉）。
 * 我讀過 `LensKt` 內嵌的 AGSL：`sdRoundedRect` + `gradSdRoundedRect` + `depthEffect`，
 * 和原先手寫那份**演算法一致** —— 手寫等於重新發明的同时还背了 `size = NaN` 的坑。
 */
object BarOptics {

    // ── 容器（整條欄）────────────────────────────────────────────

    /**
     * 背後模糊半徑 = **4dp**（與參考一致）。
     *
     * ## 我中途把它推到 12dp 是錯的，已改回
     *
     * 當時我拿「壓平比」（欄內亮度 std ÷ 欄外 std）當依據：參考 0.65、我 1.41，
     * 於是結論說「模糊不夠」。但那次的量測區間**把自家黑色圖標和 13sp 文字一起算進去了**，
     * std 一直是圖標在主導，跟玻璃無關。改用純玻璃帶（欄頂 42px、圖標之上）重量：
     * 4dp 時帶內 std 只有 7.3，12dp 時反而 11.5 —— **模糊加大並沒有讓它更「平」**。
     *
     * 更根本的一條：兩個 App 壓在欄底下的內容本來就不一樣，
     * 跨 App 比對比度，量的是頁面，不是玻璃。
     *
     * 所以回到參考值 4dp。真玻璃的合理區間是 2~12dp，34dp 那次已被打回（磨砂板）。
     */
    val CONTAINER_BLUR: Dp = 4.dp

    /** 折射帶高度：離邊界多遠開始折射。 */
    val CONTAINER_LENS_HEIGHT: Dp = 24.dp

    /** 折射量：边缘最多偏移多少。變焦環容器用的是同一個 24。 */
    val CONTAINER_LENS_AMOUNT: Dp = 24.dp

    /**
     * ★ 折射的兩個開關：**與兩個參考實現保持一致 —— 都關掉**。
     *
     * 我第一版開了 `depth = true` + 色散，結果真機上在折射帶的**內邊界**
     *（離上邊緣正好 24dp = 84px）出現一條 1px 暗線：
     * 亮度從 242 掉到 102，只有一行，且**只在上邊緣**（下邊緣與左右帶內沒有）。
     * 反觀參考：Lx 的位元組碼是 `lens(h, a, false, false)`，
     * 變焦環是 `lens(refractionHeight = ..., refractionAmount = ...)`（後面兩個默認 false）。
     * 也就是說**那兩個工程從來沒開過這兩個開關**，是我自己加的。
     *
     * 先按參考的取值關掉；要恢復色散必須**先在真機上確認那條 1px 線不再出現**。
     */
    const val LENS_DEPTH = false
    const val LENS_DISPERSION = false

    /**
     * 容器底色 alpha = **0.30**（深色 0.22）；參考是 0.4。
     *
     * ## 我中途用过的那个目标是错的，别再拿它当依据
     *
     * 我曾经拿「栏内平均亮度要对齐参考的 242」当目标，把 α 从 0.4 抬到 0.55、
     * 又把 blur 从 4dp 抬到 12dp —— **方向整个推反了**。原因是量测区间选错：
     * 它把自家黑色图标和 13sp 文字一起算了进去，std 一直是图标在主导，与玻璃无关。
     * 改用纯玻璃带（栏顶 42px、图标之上）再量：α 从 0.55 压回 0.30，
     * 玻璃带亮度只从 242.8 动到 241.4 —— **说明那个指标量的根本不是玻璃**。
     *
     * 更根本的一条：**两个 App 压在栏底下的内容不一样**（我们是白卡片黑字，
     * 参考是浅灰滑块），跨 App 比亮度/对比度，量的是页面，不是玻璃。
     *
     * ## 所以现在唯一的依据
     *
     * 业主要「更透」⇒ 取比参考更低的 0.30（深色 0.22）。
     * 要调就改这一个数，并且**在同一页上对比**，不要拿另一个 App 的截图当基线。
     * 上限仍然受折射约束：超过 ~0.5 这层白漆会把折射盖死（0.92 那次事故）。
     */
    const val CONTAINER_SURFACE_ALPHA = 0.30f
    const val CONTAINER_SURFACE_ALPHA_DARK = 0.22f

    const val CONTAINER_HIGHLIGHT_ALPHA = 0.75f

    /** 容器光源基準角：−45°（同一束光，不同元件不同朝向，才有「兩件東西」的感覺）。 */
    const val CONTAINER_LIGHT_DEGREES = -45f

    // ── 滑塊（選中氣泡）──────────────────────────────────────────

    /**
     * 滑塊模糊 = 容器的 **1.25 倍**（相鄰兩塊玻璃的糊度必須不同，否則糊成一塊）。
     *
     * ⚠️ 這個倍數是隨容器動的：容器 4dp 時代用 2.2 倍得到 8.8dp；
     * 容器升到 12dp 後還乘 2.2 就是 26dp —— 那就越過「真玻璃」上限變磨砂板了。
     */
    const val PILL_BLUR_FACTOR = 1.25f

    /** 滑塊折射 = 容器的**一半**（小面積折射量太大会變成透鏡畸變）。 */
    const val PILL_LENS_FACTOR = 0.5f

    /** 滑塊表面：淺色白 34%、深色白 10%（差 3 倍多 —— 淺色才托得住「托盤感」）。 */
    const val PILL_SURFACE_ALPHA_LIGHT = 0.34f
    const val PILL_SURFACE_ALPHA_DARK = 0.10f

    const val PILL_HIGHLIGHT_ALPHA = 0.85f

    /** 滑塊光源基準角：+90°。 */
    const val PILL_LIGHT_DEGREES = 90f

    // ── 重力跟隨高光的兩道保護 ───────────────────────────────────

    /**
     * 平放死區（|g_xy|² 閾值）。0.01 ⇒ |g_xy| > 0.1，大約傾斜 6° 才跟。
     *
     * **沒有這條會抖**：幾乎平放時 x/y 兩個軸上只剩噪聲，歸一化後的方向在 360° 上亂跳，
     * 光斑跟著亂轉。這時必須保持上一次的方向。
     */
    const val FLAT_DEAD_ZONE_SQ = 0.01f

    /** 低通係數（0~1，越小越黏）。0.15~0.25 是真機上確認過的舒服區間。 */
    const val LOW_PASS_ALPHA = 0.2f

    /**
     * 方向量化步長（1/QUANT_STEPS 個單位）。
     *
     * 光源方向必須走**組件重組**才能更新（`effects` 只被求值一次），
     * 但加速度計的節奏比幀還快 —— 不量化就會拿傳感器速率去砸重組。
     * 量化到 1/40（≈ 每 9° 一格）之後，靜置時根本不會產生新的重組。
     */
    const val QUANT_STEPS = 40f

    /** 光源參考點 —— 必須與 `BloomStrokeShader.LIGHT_REF_X/Y` 保持一致。 */
    const val LIGHT_REF_X = 0.5f
    const val LIGHT_REF_Y = 0.7f

    // ── 彈簧（照參考 DampedDragAnimation 的四條 spec）───────────────

    /**
     * 位置歸位：`spring(dampingRatio = 1f, stiffness = 1000)` —— **臨界阻尼 + 高剛度**。
     *
     * 跟手、不過衝、停得乾脆。我上一版給 0.78/560 是偏軟的，
     * 氣泡到位時有一種拖泥感 —— 那就是业主說的「不夠靈動」的一半。
     */
    const val SETTLE_DAMPING = 1f
    const val SETTLE_STIFFNESS = 1000f

    /**
     * 氣泡形狀歸位：橫向 `spring(0.6, 250)`、縱向 `spring(0.7, 250)` —— **故意留一點過衝**。
     *
     * 這才是「水滴被拽過去後輕輕蕩一下」的来源。它不會退化成「奇怪的抖動」，
     * 因為**位置那條鏈是臨界阻尼的**：兩個自由度分開之後，
     * 過衝只發生在形狀上、不發生在位置上。紅線 #7 禁的是兩者叠在同一自由度上。
     */
    const val SCALE_X_DAMPING = 0.6f
    const val SCALE_Y_DAMPING = 0.7f
    const val SCALE_STIFFNESS = 250f

    /** 按下進度：`spring(1f, 1000)`。 */
    const val PRESS_DAMPING = 1f
    const val PRESS_STIFFNESS = 1000f

    /** 按下時氣泡長到多大（參考 `PillPressedScale = 1.3928572`）。 */
    const val PRESSED_SCALE = 1.3928572f

    /** 甩動判定門檻（px/幀）：低於它算「慢慢拖」= 平移，不該有水滴形變。 */
    const val FLING_THRESHOLD_PX = 18f

    /** 甩動時額外跨過的格數。 */
    const val FLING_EXTRA = 0.55f

    // ── 幾何（照參考實現的真實比例，不是憑手感）───────────────────

    /** 欄身高度 = **64dp**（參考 `IosLiquidGlassNavigationBar` 的 `.height(64.dp).padding(4.dp)`）。 */
    const val BAR_HEIGHT_DP = 64f

    /** 氣泡高度 56dp（參考 `PillHeightDp = 56`），在 64dp 欄裡上下各留 4dp。 */
    const val PILL_HEIGHT_DP = 56f

    /**
     * 氣泡寬度 = 一格寬 − 這個內縮（參考氣泡是 `.height(56).padding(horizontal = 4)`）。
     *
     * ⚠️ 不要寫成固定 dp：氣泡必須**正好罩住自己那一格**。
     * 上一版固定 72dp 比格子(59dp)寬出一大圈，圖標被蓋在白塊裡 ——
     * 那就是「卡死在底栏里面」和「拖一團黑」的共同来源。
     */
    const val PILL_H_INSET_DP = 8f

    /**
     * 氣泡投影：**3dp**。
     *
     * 上一版給 10dp，真機上在欄面左端拖出一團黑影（业主：「左邊還有拖黑色的不知道什麼東西」）。
     * 半透明欄會把陰影接住再被折射放大，所以只能給很輕一層；
     * 想要更「浮」的感覺應該靠**輪廓溢出與高光**，不是靠堆陰影。
     */
    const val PILL_SHADOW_DP = 3f

    /** 欄的左右外邊距（參考 `start = 24.dp, end = 24.dp`）。 */
    const val BAR_SIDE_PADDING_DP = 24f

    // ── 水滴拖拽（业主：不夠靈動，沒有被拽過去的感覺）──────────────

    /**
     * 拖拽時氣泡的最大拉伸量（橫向）。
     *
     * 之前按紅線 #7 把氣泡的形變**全摘了**，結果就是「滑塊卡死在欄裡平移到移」。
     * 紅線 #7 真正禁的是「欠阻尼位置彈簧 + 速度驅動縮放」疊在一起會來回過衝 ——
     * 解法是**位置用臨界阻尼（不過衝），形變用速度驅動並快速衰減**，
     * 而不是把形變整個砍掉。兩件事分開，就既有水滴又不抖。
     */
    const val DRAG_STRETCH_MAX = 0.12f

    /** 縱向壓扁相對於橫向拉伸的比例（1.0 = 等面積；0.55 视觉上更像水滴）。 */
    const val DRAG_STRETCH_SQUASH = 0.55f

    /** 多少「格/秒」算拖到滿形變。低於這個速度幾乎不變形（慢慢拖 = 平移）。 */
    const val DRAG_FULL_SPEED_CELLS_PER_SEC = 3.0f

    /** 鬆手後形變的衰減時長：要快，慢了就是「果凍回彈」，會與下一次拖拽叠上。 */
    const val STRETCH_DECAY_MS = 140

    /**
     * 滑塊模糊 ÷ 容器模糊 —— **從真正會被使用的 [GlassSurfaceStyle] 裡算**，
     * 不是把上面那個 factor 再唸一遍。這樣一旦 style 被拆掉，這個數就跟著錯，測試才會紅。
     */
    val pillBlurRatio: Float get() =
        GlassSurfaceStyle.pill(false).blur.value / CONTAINER_BLUR.value

    /** 滑塊折射 ÷ 容器折射（同上，取實際 style 的值）。 */
    val pillLensRatio: Float get() =
        GlassSurfaceStyle.pill(false).lensHeight.value / CONTAINER_LENS_HEIGHT.value
}

/**
 * 把「材質三態」翻譯成實際光學參數。
 *
 * ⚠️ 抽成純函數是因為**這段判斷必須能被單測覆蓋**：
 * 上一版的 `BarMaterial` 列了 SOLID / FROSTED / LIQUID 三態，但繪製處根本沒讀它 ——
 * 那個選項是假的，拖了什麼都不變。現在 `GlassSurface` 一律經過這裡。
 */
fun styleFor(
    material: BarMaterial,
    style: GlassSurfaceStyle,
    frostBlur: Dp,
): GlassSurfaceStyle = when (material) {
    // 磨砂 = 只有模糊、**不折射**：那是磨砂玻璃，不是液態玻璃。
    BarMaterial.FROSTED -> style.copy(blur = frostBlur, lensHeight = 0.dp, lensAmount = 0.dp)

    BarMaterial.SOLID, BarMaterial.LIQUID -> style
}

/** 表面色用哪個語義色 —— 別再用引用比較去猜是哪一種。 */
enum class GlassSurfaceColor { SURFACE_CONTAINER, WHITE }

/**
 * 一幀裡真正用來畫一塊玻璃的那組參數。
 *
 * 由 [GlassRenderMode] 決定要不要開真折射；**降級必須是真的會走到的分支**，
 * 不是寫了沒人調的摆设（上一版 `GlassSpec.FALLBACK` 就是摆设）。
 */
data class GlassSurfaceStyle(
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val surfaceColor: GlassSurfaceColor,
    val surfaceAlpha: Float,
    val highlightAlpha: Float,
    val lightDegrees: Float,
    /** 主光強度：深色下要壓低，否則邊緣發白像貼紙。 */
    val primaryIntensity: Float,
) {
    companion object {

        /** 容器（整條欄）。 */
        val CONTAINER = GlassSurfaceStyle(
            blur = BarOptics.CONTAINER_BLUR,
            lensHeight = BarOptics.CONTAINER_LENS_HEIGHT,
            lensAmount = BarOptics.CONTAINER_LENS_AMOUNT,
            surfaceColor = GlassSurfaceColor.SURFACE_CONTAINER,
            surfaceAlpha = BarOptics.CONTAINER_SURFACE_ALPHA,
            highlightAlpha = BarOptics.CONTAINER_HIGHLIGHT_ALPHA,
            lightDegrees = BarOptics.CONTAINER_LIGHT_DEGREES,
            primaryIntensity = 0.42f,
        )

        private val PILL_BASE = GlassSurfaceStyle(
            blur = BarOptics.CONTAINER_BLUR * BarOptics.PILL_BLUR_FACTOR,
            // 參考氣泡是 `lens(10.dp * progress, 14.dp * progress)` —— 按壓進度驅動，
            // 不是容器那個 24。小面積用大折射會變成放大鏡畸變。
            lensHeight = 10.dp,
            lensAmount = 14.dp,
            surfaceColor = GlassSurfaceColor.WHITE,
            surfaceAlpha = BarOptics.PILL_SURFACE_ALPHA_LIGHT,
            highlightAlpha = BarOptics.PILL_HIGHLIGHT_ALPHA,
            lightDegrees = BarOptics.PILL_LIGHT_DEGREES,
            primaryIntensity = 0.30f,
        )

        /** 滑塊（選中氣泡）：深淺兩套的表面 alpha 差 3 倍多，必須分開給。 */
        fun pill(isDark: Boolean): GlassSurfaceStyle = PILL_BASE.copy(
            surfaceAlpha = if (isDark) BarOptics.PILL_SURFACE_ALPHA_DARK
            else BarOptics.PILL_SURFACE_ALPHA_LIGHT,
        )

        /**
         * 容器：深淺兩套 separately 給 alpha。
         *
         * 淺色要壓得住背後的高對比文字（見 [BarOptics.CONTAINER_SURFACE_ALPHA] 的實測），
         * 深色則要留得住「透」的感覺，所以給得更低。
         */
        fun container(isDark: Boolean): GlassSurfaceStyle = CONTAINER.copy(
            surfaceAlpha = if (isDark) BarOptics.CONTAINER_SURFACE_ALPHA_DARK
            else BarOptics.CONTAINER_SURFACE_ALPHA,
        )
    }
}

/**
 * 當前光源方向（歸一化的 xy 向量），已做**平放死區 + 低通 + 量化**。
 *
 * ## 為什麼回的是一个「值」而不是一個 lambda
 *
 * `Modifier.drawBackdrop(effects = { ... })` 裡的 lambda **只在求值那次跑**，
 * 所以重力要能跟，就必須讓「方向變化」這件事發生在**組件層面**：
 * 讀 `tilt.value` ⇒ 值變了 ⇒ 重組 ⇒ effects 重新求值 ⇒ 高光換方向。
 * 反過來說，把 state 讀進 effects 裡是**不會**跟著轉的。
 *
 * 也因為這條鏈會觸發重組，輸出必須**量化**（[BarOptics.QUANT_STEPS]），
 * 否則就是拿傳感器速率去砸重組 —— 靜置時應該一次重組都不產生。
 */
@Composable
fun rememberGravityLightDir(
    extraDegrees: Float,
    holder: GravityLightHolder = rememberGravityLightHolder(),
): Pair<Float, Float> {
    val tilt: State<DeviceTilt> = rememberDeviceTilt()
    // ★ 必須用 derivedStateOf：加速度計上報節奏比幀還快，
    //   直接讀 tilt.value 會讓「每一次上報」都觸發一次重組 + effects 重新求值。
    //   包一層派生狀態之後，只有**量化後的方向真的跨格**才往下游失效；
    //   靜置（平放）時死區吃掉噪聲，一次重組都不該產生。
    val dir = remember(extraDegrees, holder, tilt) {
        derivedStateOf { holder.advance(tilt.value, extraDegrees) }
    }
    return dir.value
}

/**
 * 重力方向的**平滑容器**。
 *
 * ⚠️ 必須是穩定對象，不能每次重組現造 ——
 * 一期 Flutter 工程就因為「光源角度那條流每次返回新的單訂閱流」炸過整樹紅屏
 *（`tilt_light.dart` 的 `async*` getter）。Kotlin 側的對應写法就是把角度收進
 * 一個 `remember` 的持倉物件裡推，這裡照辦。
 */
@Composable
fun rememberGravityLightHolder(): GravityLightHolder = remember { GravityLightHolder() }

class GravityLightHolder {

    private var lx = 0f
    private var ly = -1f
    private var quantized: Pair<Float, Float> = 0f to -1f

    /**
     * 推進一步。
     *
     * @return 量化後的方向；**只有跨過量化格才返回新值**，於是不會逐幀重組。
     */
    fun advance(tilt: DeviceTilt, extraDegrees: Float): Pair<Float, Float> {
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val magSq = gx * gx + gy * gy

        if (magSq > BarOptics.FLAT_DEAD_ZONE_SQ) {
            val inv = 1f / sqrt(magSq)
            val k = BarOptics.LOW_PASS_ALPHA
            lx += k * (gx * inv - lx)
            ly += k * (gy * inv - ly)
        }
        // 平放（或剛起步）時保持上一次的角度，不跟噪聲轉。

        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val rx = c * lx - s * ly
        val ry = s * lx + c * ly

        val q = quantize(rx) to quantize(ry)
        if (q != quantized) quantized = q
        return quantized
    }

    private fun quantize(v: Float): Float =
        (v * BarOptics.QUANT_STEPS).roundToInt() / BarOptics.QUANT_STEPS
}

/**
 * 用重力方向轉過的主光源，构造一份雙光源高光。
 *
 * @param pressFraction 按下程度 0~1：按下去時光要更強（真機上「按下去玻璃亮起來」就是這條）。
 */
fun gravityHighlight(
    lightDir: Pair<Float, Float>,
    style: GlassSurfaceStyle,
    pressFraction: Float = 1f,
): GlassHighlight {
    val (lx, ly) = lightDir
    val p = pressFraction.coerceIn(0f, 1f)
    return GlassHighlight(
        strokeWidth = 1.2.dp,
        innerBlurRadius = 2.8.dp,
        primary = GlassLight(
            x = BarOptics.LIGHT_REF_X + lx,
            y = BarOptics.LIGHT_REF_Y + ly,
            z = -0.5f,
            intensity = style.primaryIntensity * (0.7f + 0.3f * p),
        ),
        secondary = GlassLight(
            x = BarOptics.LIGHT_REF_X - lx * 0.6f,
            y = BarOptics.LIGHT_REF_Y - 0.18f,
            z = -0.5f,
            intensity = style.primaryIntensity * 0.5f,
        ),
        alpha = style.highlightAlpha,
    )
}

/**
 * 這塊玻璃**到底能不能畫**。三層任一不過就走降級外觀：
 *
 * 1. **引擎能力** —— `com.kyant.backdrop.isRuntimeShaderSupported()`。
 *    ⚠️ 不能用 `SDK_INT >= 33` 猜：minSdk 33 只代表有 API，ROM/GPU 照样可能不给 shader。
 * 2. **採樣層活著** —— 见 [GlassBackdropHost] 的正面證據（那一層是不是真的被畫過）。
 * 3. **材質開關** —— SOLID 就是實心，不需要玻璃。
 */
enum class GlassRenderMode { GLASS, FALLBACK }

@Composable
fun rememberGlassRenderMode(
    material: BarMaterial,
    liveness: com.muyao.wanzaihyperos.ui.design.GlassLiveness.Verdict,
): GlassRenderMode {
    val supported = remember { com.kyant.backdrop.isRuntimeShaderSupported() }
    return remember(supported, material, liveness) {
        when {
            material == BarMaterial.SOLID -> GlassRenderMode.FALLBACK
            !supported -> GlassRenderMode.FALLBACK
            !liveness.canUseGlass -> GlassRenderMode.FALLBACK
            else -> GlassRenderMode.GLASS
        }
    }
}
