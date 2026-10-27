package com.muyao.wanzaihyperos.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * 首頁的**天空層**（v2）。
 *
 * ## 這一層為什麼要單獨存在
 *
 * 業主的原話是「保留液態玻璃，但要有優雅的壁紙」。一張圖鋪在頁面背景上並不難，
 * 難的是讓玻璃**真的採到那片天空** —— 首頁以前用 `LocalGlassBackdrop`（恆 null），
 * 所以卡片一直是實心；天空層必須是一份真的 `LayerBackdrop`，玻璃才有東西可採。
 * 這個檔案只負責「畫」，掛層與玻璃卡在 `HomeSectionPage` 裡（`GlassBackdropHost`
 * 會把這裡的內容錄進採樣層，玻璃是它的兄弟節點，不會採到自己）。
 *
 * ## 為什麼不用「實時模糊整張壁紙」來做柔化
 *
 * 那是每幀一次全屏採樣，而這是一片 100% 時間都在螢幕上的背景。
 * 「柔化」在這裡是一層同色薄霧：視覺上降低對比、讓字更安靜，成本是一次靜態繪製。
 *
 * ## 時間只讀一次，不逐幀算
 *
 * 天空跟真實時間走（清晨金、正午藍、黃昏橙、夜裡深藍），但沒必要每秒重畫。
 * 每 10 分鐘對一次時鐘；換小時才重組。
 */
enum class SkyPreset(
    val id: String,
    val label: String,
    val hint: String,
) {
    AUTO("auto", "跟随时刻", "按现在的时间选天空"),
    MORNING("morning", "晨雾", "清早的淡金与雾蓝"),
    DUSK("dusk", "暮色", "傍晚的橘与靛"),
    NIGHT("night", "夜航", "深蓝、月亮与星"),
    ALBUM("album", "相册", "你自己的那张图"),
    ;

    companion object {
        fun of(id: String?): SkyPreset = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** 一版天空的配色与文字墨色。 */
data class SkyLook(
    val top: Color,
    val bottom: Color,
    /**
     * 板上 `linear-gradient(180deg,#A9CBD8 0%,#C9DEE5 46%,#F1E9DC 100%)` 的**中间停靠色**（wz-v2.html:166）。
     *
     * ★ 为什么单独有这一栏（独立审计 v3 #2 第 4 轮 §4.2）：以前画的是
     *   `0f→top, 0.55f→top, 1f→bottom` —— **0~55% 是一条完全水平的色带**
     *   （真机 x=200 那一列 y=1/120/241/289 四点逐位相等 (176,206,218)，290 行零梯度）。
     *   业主报的「糊 / 没出处」到最后就是这片死平的天空：既不是板上的渐变，也没有方向感。
     *   补上板上 46% 那一站，解析重算 CR(盘,环) 从 1.237 收到 **1.126**。
     * ⚠️ 第 6 轮把这句**删掉的等价说法**记档：以前这里写「null = 这一档板上本来就只有两站」——
     *   **那句被板否掉了**（浅色 :166 与深色 :169 都是三站），0.55 那条水平段是我们自造的。
     *   现在 null 只表示"没显式取板色，用 `lerp(top, bottom, 0.46)` 顶上"，任何一档都不再有水平段。
     */
    val mid: Color? = null,
    val ridgeFar: Color,
    val ridgeNear: Color,
    /** 压在这片天空上的大字颜色。 */
    val ink: Color,
    /** 状态栏图标是否要用浅色（深色天空上必须）。 */
    val lightIcons: Boolean,
)

/**
 * 板上三段渐变的**中间停靠点**（`wz-v2.html:166/:169` 的 46%）。
 *
 * 画的那支 `Brush.verticalGradient` 与卡面算字色的 [SkyLook.colorAt] 共用这一个数：
 * 两处各写一份 0.46 早晚会飘，飘了就是「按 A 颜色判的字色、按 B 颜色画的底」。
 */
const val SKY_MID_STOP = 0.46f

/** 天空渐变在分数 [f]（0=顶，1=skyBottom）处**真正画出来的那个颜色**。 */
fun SkyLook.colorAt(f: Float): Color {
    val x = f.coerceIn(0f, 1f)
    val m = mid ?: lerp(top, bottom, SKY_MID_STOP)
    return if (x <= SKY_MID_STOP) {
        lerp(top, m, x / SKY_MID_STOP)
    } else {
        lerp(m, bottom, (x - SKY_MID_STOP) / (1f - SKY_MID_STOP))
    }
}

/**
 * 「此刻卡身后**真的会出现**的那几层底色」。
 *
 * ★ 第 7 轮独立审计把我原来那条「取带内最亮档」判死了，理由成立：
 *   卡顶 ≈167dp，而天空只画到 `skyBottom = 0.80 × 250dp = 200dp`
 *   （`HomeSectionPage.kt:302-309` 那块 `height(...)` + `HomeSky.kt:592` 那个 0.80f。
 *   业主 2026-10-25「壁纸范围再往下到卡的上三分之一」之后那块盒子是 **256dp**
 *   ⇒ `skyBottom = 0.80 × 896 = 716px ≈ 204.7dp`；下面这段审计结论不变，只是边界更靠下 4.7dp）
 *   ⇒ 这张卡有**八成高度压在"天空已经淡成页面底色"的那一段以下**，
 *   而我以前采的 f∈[0.10,0.58] 根本不是卡背后那个面。
 *   后果不是"差一点"：深色主题 × 白天 × 液态那一格，按错的底色判出深墨（模型 4.74:1），
 *   压在真实那块面上只有 **1.02:1 = 黑压黑**（审计给的解析复算，公式就是这份代码里的）。
 *
 * 所以这里给的是**卡会经过的那几层底**：天空靠底那一段（f=0.84 起）、天空淡到底那一站、
 * 以及淡完之后真正的页面底色。判厚度与字色的必须对**这一组**取最坏情况，不是对某一个 y 取一个数。
 */
/**
 * 「此刻卡身后**真的会出现**的那几层底色」—— 必须覆盖**它滚过的全程**，不是静止位。
 *
 * ★ 第 8 轮审计把第 7 轮的修法又判掉一半，理由成立：
 *   天空在 `GlassBackdropHost.content` 里是**静止层**，卡却在 `LazyColumn` 里
 *   （`HomeSectionPage.kt:268-292` vs `:340-353`）⇒ 往上滚时这张卡从 f≈0.84 一路压到 f≈0，
 *   **整片天空都从它背后走过**。第 7 版只补了"往下那两段"（0.84 / 1.0 / 纸色），
 *   于是"卡会滚 ⇒ 取最坏"这句话只兑现了一半。
 *
 * ⚠️ 顺带钉死一个几何口径：卡顶 ≈167dp（`iosHeaderInset()` + 大标题那一屏），
 *   天空底 = `0.80 × 250dp = 200dp`（`HomeSky.kt:430`）。`verify/glass_modes.py` 里那句
 *   "卡顶 ≈152dp"是截图量出来的**画面位置**，与代码里的 167dp 不是同一个量（一个含状态栏 inset
 *   的排版值、一个像素带），两处都写清楚，别让人以为其中一个是拍脑袋的。
 */
/**
 * 相册档的底：**量出来的**照片亮度（卡所在那一段 + 整张）+ 页面纸色。
 *
 * ★ 以前这里传 `{黑, 白}` 兜"亮度未知"。字色判据扩到三档之后，那个"两端都算"会把垫色
 *   一路抬到 ≈0.9 ⇒ **液态与磨砂在卡上逐像素相同（模拟器实测 MAD 0.000）**，深色主题同病。
 *   现在这张图在手上（`wallpaperStats`），量过就不必再兜两端。
 */
fun photoBackdrops(stats: WallpaperStats, pageBg: Color): List<Color> =
    listOf(stats.topBand, stats.overall, pageBg)

fun SkyLook.cardBackdrops(pageBg: Color): List<Color> =
    listOf(
        colorAt(0f), colorAt(0.25f), colorAt(SKY_MID_STOP),
        colorAt(0.7f), colorAt(0.84f), colorAt(1f),
        pageBg,
    )

/**
 * 一层 alpha 垫色压在天空上，**实际铺出来**的颜色（alpha=1 ⇒ 只剩垫色 = 纸面）。
 *
 * 这就是"卡面亮度"的唯一算法：字色要按它判，不能按垫色那个数猜 ——
 * 0.30 的白压在夜里是暗面、压在正午是亮面，同一个 alpha 两种字色。
 */
/**
 * 文字块底下那层**局部衬底**的不透明度 —— B4 / #12 业主 2026-10-23 拍板的口径：
 * 「**保厚度，可读性放到字这一侧**」。
 *
 * 为什么不再用"抬卡面厚度"来保可读：那会把液态与磨砂**抬成同一个数**（真机实测：夜档与照片上
 * 两档在卡内逐像素相同、关闭档差 48 MAD）⇒ 三档退化成两态。
 * 现在：卡面厚度 = 档位自己的（`cardFaceAlpha`），文字块下面垫一层很窄的同色衬底，
 * **字色按"天空 → 卡面 → 衬底"叠出来的那个颜色算**，卡面本身不再为字让厚度。
 *
 * 取 0.55 的理由：`surfaceContainer` 在浅/深主题下都足够接近纸/墨的一端，
 * 0.55 的额外叠层足以把主/次/警三档字色都抬到合格线（实测值见 `HomeSkyTest` 的全档扫描），
 * 而又薄到在卡面上读不出"一块贴片"（衬底只在字块外扩 8dp / 6dp 的圆角矩形内）。
 */
const val TEXT_BACKING_ALPHA = 0.55f

/**
 * 「天空 → 卡面（档位厚度）→ 文字块衬底」三层叠出来、**字真正压在上面的那个颜色**。
 *
 * 与 [faceOverSky] 的关系：`textBackingOver(sky, tint, a, 0f) == faceOverSky(sky, tint, a)`
 * （衬底为 0 就是卡面本身）—— 保证"没有衬底"这条路与老算法逐位一致。
 */
fun textBackingOver(
    sky: Color,
    tint: Color,
    faceAlpha: Float,
    backingAlpha: Float = TEXT_BACKING_ALPHA,
): Color = faceOverSky(faceOverSky(sky, tint, faceAlpha), tint, backingAlpha)

fun faceOverSky(sky: Color, tint: Color, alpha: Float): Color =
    lerp(sky, tint, alpha.coerceIn(0f, 1f))

/** 一张卡面上要用的三个字色（主/次/警示），全部来自定稿板的 token。 */
@Immutable
data class CardInk(val main: Color, val quiet: Color, val warm: Color, val onLightFace: Boolean)

/** 板的 `--ink`（`wz-v2.html:15`）与 `body.dark --ink`（:38）。 */
private val INK_ON_LIGHT_FACE = Color(0xFF1A1917)
private val INK_ON_DARK_FACE = Color(0xFFF2EFE7)

/** 合格线：正文 4.5:1；次级与警示两档按 3:1 判（理由见 [setMargin]）。 */
private const val MIN_CONTRAST = 4.5f
private const val MIN_SECONDARY = 3.0f

/**
 * 两套字色，按 主/次/警示 排。
 *
 * ★ 次级那一档用 **主墨的 0.74 透明版**，不再直接用板的 `--sub #736E64`。
 *   为什么（第 8 轮审计致命 2 的推论，我自己在扫相册档时撞到的）：
 *   `--sub` 是一枚**压在不透明纸面上**才成立的灰（板自己压 `--phone-2` 也只有 3.9:1）。
 *   把它压在**半透明卡面**上时，只要卡面是中段明度它就永远不合格，于是"抬厚度"这条出路
 *   会把**三档全部抬到 ≈0.9**（液体与磨砂在卡上逐像素相同，模拟器实测 MAD 0.000）。
 *   换成"主墨压 0.74 透明"之后：主墨既然保证 ≥4.5:1，它压出来的次级自然落在 3:1 上方，
 *   而**用不着一路加厚**——去强调的手段从"换一枚灰"变成"同一支墨降透明度"，
 *   这正是 iOS vibrancy 那套做法（也是本工程 §4 材质规则里写的"字用 vibrancy、不要扁平灰字"）。
 */
private val INK_SET_LIGHT = listOf(
    INK_ON_LIGHT_FACE,
    INK_ON_LIGHT_FACE.copy(alpha = 0.74f),
    Color(0xFFB65E3D),
)
private val INK_SET_DARK = listOf(
    INK_ON_DARK_FACE,
    INK_ON_DARK_FACE.copy(alpha = 0.74f),
    Color(0xFFE09270),
)

/** 半透明字色压在卡面上**实际铺出来**的样子（Compose 画文字时就是这么混的）。 */
fun blendedOver(face: Color, ink: Color): Color = lerp(face, ink, ink.alpha.coerceIn(0f, 1f))

/** 一块面上的字色对比度 —— **带 alpha 的字按混合后的色算**，不许拿原色蒙过去。 */
private fun inkContrast(face: Color, ink: Color): Float =
    wcagContrast(face, blendedOver(face, ink))

/**
 * 一套字色压在给定那些卡面上的**离合格还差多少**（≥0 = 三档全合格）。
 *
 * ★ 第 8 轮审计的致命 2（成立）：第 7 版只对 `main` 判，`quiet`/`warm` 从没进过判据，
 *   而这两枚是真上屏的（eyebrow「此刻 · 13:34」、subline、那行「签到进行中」）。
 *   审计独立复算 16 格无一合格（夜航 × 浅色 × 液态的 quiet 只有 1.32:1）——
 *   同一类病，只是搬到了 `CardInk` 的另外两格上。
 *
 * ⚠️ 为什么次级两档按 3:1 而不是 4.5：**板自己**的 `--sub #736E64` 压在板的纸面
 *   （`--phone-2 #FBF8F1`）上只有 3.9:1。要求它 4.5 等于要求改掉板色，
 *   那是观感决定、归业主，不由我在判据里替他改。这条口径写在判据旁边，不藏在数字里。
 */
private fun setMargin(faces: List<Color>, set: List<Color>): Float =
    faces.minOf { f ->
        minOf(
            inkContrast(f, set[0]) - MIN_CONTRAST,
            inkContrast(f, set[1]) - MIN_SECONDARY,
            inkContrast(f, set[2]) - MIN_SECONDARY,
        )
    }

/**
 * 卡的那套字色：对**所有可能的底**与**三档字色**取最坏情况，选"最坏那一格也最清楚"的一套。
 *
 * ⚠️ 为什么不用 `MiuixTheme.colorScheme.onBackground`：深浅要跟着**卡面**走，不是跟着主题走
 *   （浅色主题夜里那块半透明卡压出来是暗面，主题墨色压上去就是黑压黑）。
 * ⚠️ 为什么不比一个亮度阈值（前一版写的 `luminance() >= 0.5f`）：黄昏那档实算 0.436，
 *   落在阈值下 ⇒ 判给浅墨，而浅墨对这块面只有 4.0:1 —— 差的是"哪套真读得清"，不是"哪边过半"。
 * ⚠️ 为什么是**一组底**：这张卡在滚动列表里，而天空是静止层 ⇒ 它从 f≈0.84 一路压到 f≈0，
 *   整片天空加页面纸色都从它背后走过（见 [cardBackdrops]，第 8 轮审计判死"只算静止位"那条）。
 */
fun cardInkWorstCase(faces: List<Color>): CardInk {
    val light = CardInk(
        main = INK_SET_LIGHT[0], quiet = INK_SET_LIGHT[1], warm = INK_SET_LIGHT[2],
        onLightFace = true,
    )
    val dark = CardInk(
        main = INK_SET_DARK[0], quiet = INK_SET_DARK[1], warm = INK_SET_DARK[2],
        onLightFace = false,
    )
    val chosen = if (setMargin(faces, INK_SET_LIGHT) >= setMargin(faces, INK_SET_DARK)) light else dark
    // ★★ 警示色（那行「签到进行中」）是一枚**固定橙**：它在整段行程的某些面上只有 **2.08~2.20:1**
    //   （B4/#12 改口径后由全档扫描实测出来的 —— 以前"抬厚度"把卡面推到了橙能读的那一段，
    //   现在厚度按档位给，橙在半透明卡面上就读不清了）。
    //   与 `readableAccent` 同一条优先级：**读得清 > 品牌色** ⇒ 不合格就让位给主墨。
    val warmOk = faces.all { inkContrast(it, chosen.warm) >= MIN_SECONDARY }
    return if (warmOk) chosen else chosen.copy(warm = chosen.main)
}

/**
 * 强调色（那颗「上课中」胶囊、倒计时环的弧）能不能留在卡上。
 *
 * ★ 第 7 轮审计必修 2：卡上还有两处吃 `colors.primary`，卡面被抬到中间明度时品牌绿只有
 *   1.46:1 ⇒ **读得清的优先级高于品牌色**：任何一层底上不到 4.5 就退回这套字色的主墨。
 * ⚠️ 环里那行「分钟」用的是 `accent.copy(alpha = 0.72f)`：主色既然已经保证 ≥4.5，
 *   0.72 的透明版铺出来约在 3:1 那一档（次级字），所以调用点不必再算一遍 ——
 *   但**别把它当成"已经验过"**，它是这条判据的推论，不是实测。
 */
fun readableAccent(faces: List<Color>, accent: Color, fallback: Color): Color =
    if (faces.all { inkContrast(it, accent) >= MIN_CONTRAST }) accent else fallback

/** WCAG 2.x 的相对亮度比（`(L1+0.05)/(L2+0.05)`），自己算：不押 Compose 那个扩展在不在。 */
fun wcagContrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * 某個預設在某一小時的配色。
 *
 * `auto` 的色板刻意比舊版更淡、更灰一點：首頁上的字比「視窗」裡多，
 * 天空太飽和會把注意力從課表搶走。
 */
fun skyLookOf(preset: SkyPreset, hour: Int): SkyLook = when (preset) {
    SkyPreset.MORNING -> SkyLook(
        top = Color(0xFFA9C0DC),
        // B4-03 抬白：底端 #F2E5D6（R−B=28）→ #F7F2EB（12）——「背景太黄」量出来的就是这一站
        bottom = Color(0xFFF4F4F7),
        ridgeFar = Color(0xFF8297B5),
        ridgeNear = Color(0xFF617593),
        ink = Color(0xFF1A1917),
        lightIcons = false,
    )

    SkyPreset.DUSK -> SkyLook(
        top = Color(0xFF46517A),
        bottom = Color(0xFFECA47B),
        ridgeFar = Color(0xFF343D5A),
        ridgeNear = Color(0xFF202840),
        ink = Color.White,
        lightIcons = true,
    )

    SkyPreset.NIGHT -> SkyLook(
        top = Color(0xFF0A1226),
        bottom = Color(0xFF263252),
        ridgeFar = Color(0xFF111A30),
        ridgeNear = Color(0xFF070B16),
        ink = Color.White,
        lightIcons = true,
    )

    SkyPreset.ALBUM -> SkyLook(
        top = Color(0xFF1B2436),
        bottom = Color(0xFF3A455C),
        ridgeFar = Color.Transparent,
        ridgeNear = Color.Transparent,
        ink = Color.White,
        lightIcons = true,
    )

    // B4-03（业主 2026-10-23「背景太黄」）：亮档的天空底端与页面纸色一起抬白 ——
    // 底端 R−B 28/62/21 → 12/30/11，顶与中间站不动（`#A9CBD8` / `#C9DEE5` 本来就是冷色）。
    // 判据与改前原值钉在 `SkyWhiteningTest`。
    SkyPreset.AUTO -> when (hour) {
        in 5..7 -> SkyLook(
            top = Color(0xFF8EA7CE), bottom = Color(0xFFF7EEF2),
            ridgeFar = Color(0xFF6D7E9E), ridgeNear = Color(0xFF4F5F78),
            ink = Color(0xFF1A1917), lightIcons = false,
        )

        in 8..11 -> SkyLook(
            // 板 `.atmos` 的白天天光：#A9CBD8 0% → #C9DEE5 46% → #F1E9DC 100%（**三站**）。
            // 以前只取两端，理由是"中间那档线性插值本来就落在 #C9DEE5 附近" ——
            // **那句是错的**：`0→top, 0.55→top` 是水平段，插不出一丝方向（第 4 轮 §4.2）。
            top = Color(0xFFA9CBD8), bottom = Color(0xFFF4F6FA),
            mid = Color(0xFFC9DEE5),
            ridgeFar = Color(0xFF4A676C), ridgeNear = Color(0xFF354D50),
            ink = Color(0xFF1A1917), lightIcons = false,
        )

        in 12..16 -> SkyLook(
            top = Color(0xFFA9CBD8), bottom = Color(0xFFF4F6FA),
            mid = Color(0xFFC9DEE5),
            ridgeFar = Color(0xFF4A676C), ridgeNear = Color(0xFF354D50),
            ink = Color(0xFF1A1917), lightIcons = false,
        )

        in 17..19 -> SkyLook(
            top = Color(0xFF4A5578), bottom = Color(0xFFF0A97A),
            ridgeFar = Color(0xFF3A4360), ridgeNear = Color(0xFF252E47),
            ink = Color.White, lightIcons = true,
        )

        in 20..22 -> SkyLook(
            top = Color(0xFF1B2545), bottom = Color(0xFF3D4A72),
            ridgeFar = Color(0xFF232C4A), ridgeNear = Color(0xFF141B32),
            ink = Color.White, lightIcons = true,
        )

        else -> SkyLook(
            // ★ 业主 2026-10-24「天空你自己选一个我觉得好看的」⇒ 这一支由我定，记录在此：
            //   第 6 轮 P0#3a 曾照板 `body.dark .atmos` 改成 #1B2A31→#22373C→#2D3634（色相 199/192/167°），
            //   而 20..22 那支是 #1B2545→#3D4A72（色相 225°）。实测（2026-10-24，模拟器 22:00 与 02:00 各采一帧）：
            //   照板那支 hue 193±6 / sat 0.31，20..22 那支 hue 225 / sat 0.44，**亮度几乎相同**
            //   ⇒ 23 点整那块暗面只是换了色相、没有变暗，看起来就是「同一块布拉了一层绿」。
            //   现在改回与 17..19 / 20..22 / NIGHT 同一支蓝（色相差 0.2~1.3°），
            //   并让亮度沿 黄昏 0.0931 → 20..22 0.0199 → 凌晨 0.0125 真的一路变暗。
            //   ⚠️ 板 html（wz-v2.html）已不在盘上，那三个十六进制只有台账转录可核；
            //   若业主看过之后仍喜欢那支青灰，改回来只需动这四行 —— 判据是 NightSkyHueTest。
            top = Color(0xFF141C36), bottom = Color(0xFF2E3A5E),
            mid = Color(0xFF1F2A4A),
            ridgeFar = Color(0xFF101A33), ridgeNear = Color(0xFF070B1C),
            ink = Color.White, lightIcons = true,
        )
    }
}

/**
 * 首頁整片天空。**必須放在 `GlassBackdropHost.content` 裡**；
 * 它自己不是玻璃，所以不會採樣到自己。
 */
@Composable
fun HomeSky(
    preset: SkyPreset,
    wallpaper: ImageBitmap?,
    dim: Int,
    soft: Int,
    modifier: Modifier = Modifier,
    panX: Float = 0f,
    panY: Float = 0f,
    zoom: Float = 1f,
) {
    val pageBg = MiuixTheme.colorScheme.background
    val veil = MiuixTheme.colorScheme.surface
    // 每 10 分鐘對一次時鐘；只有真的換了小時才寫狀態（避免無意義重組）。
    var hour by remember { mutableIntStateOf(java.time.LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10 * 60 * 1000L)
            val h = java.time.LocalTime.now().hour
            if (h != hour) hour = h
        }
    }
    val look = skyLookOf(preset, hour)
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // 页面底色那支**对角渐变**：天空最后那一段要淡进"同一支"，否则画布底边会留一条
    // 实色 ↔ 渐变的台阶（B4-02 560dpi 实测 y=875 处 2.71、30 行均值差 2.27 = 持续位移）。
    val pageGradient = com.muyao.wanzaihyperos.ui.design.AppColors.pageBrush(isDark)
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pageHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    // ★ B4-02：相册档的 `drawCoverBitmap` 会**平移+放大**，而 `drawBehind` 默认**不裁剪** ——
    //   实测照片溢出画布底边 79px（y=656..735 那条蓝带），在相册壁纸下就是业主说的「割裂线」。
    //   这里把整块天空裁回它自己的框：溢出到框外的一律不画。
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        drawSkyScene(
            look = look,
            preset = preset,
            hour = hour,
            wallpaper = wallpaper,
            dim = dim,
            soft = soft,
            veil = veil,
            fadeTo = pageBg,
            pageGradient = pageGradient,
            pageHeightPx = pageHeightPx,
            panX = panX,
            panY = panY,
            zoom = zoom,
        )
    }
}

/**
 * 外觀頁的小樣：同一套畫法，裁成圓角，底下不留「漸變到頁面底色」之外的分界。
 *
 * 小樣必須跟首頁用同一個 `drawSkyScene`，否則「預覽好看、進去不一樣」
 * 遲早會發生（這個工程已經因為兩份映射漂移吃過虧）。
 */
@Composable
fun SkyPreview(
    preset: SkyPreset,
    wallpaper: ImageBitmap?,
    dim: Int,
    soft: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    panX: Float = 0f,
    panY: Float = 0f,
    zoom: Float = 1f,
) {
    val pageBg = MiuixTheme.colorScheme.background
    val veil = MiuixTheme.colorScheme.surface
    val hour = remember { java.time.LocalTime.now().hour }
    val look = skyLookOf(preset, hour)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(pageBg),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawSkyScene(
                look = look,
                preset = preset,
                hour = hour,
                wallpaper = wallpaper,
                dim = dim,
                soft = soft,
                veil = veil,
                fadeTo = pageBg,
                // 小樣高度只有 180dp：山脊與漸變的比例要往上提，不然整張只剩天空
                compact = true,
                panX = panX,
                panY = panY,
                zoom = zoom,
            )
        }
    }
}

/**
 * 天空的**唯一畫法**。
 *
 * @param fadeTo 下方漸變到的頁面底色（透明底會讓底下的內容透出來，這裡一律給實色）
 * @param compact 小樣用：山脊上移、太陽半徑縮小
 */
/**
 * 羽化带的 alpha 斜坡（B4-02）：把 `0..1` 采成 13 个 smoothstep 点。
 *
 * 为什么不是两站线性：线性斜坡在**两端**的导数不为 0，末端会和下面那块实色
 * 形成最后一跳（模拟器实测 2.37 灰阶，判据 ≤2）。smoothstep 两端斜率都是 0，
 * 于是"天空淡完"和"纸面开始"接在同一条切线上。
 */
internal fun featherStops(fadeTo: Color, steps: Int = 12, transparent: Boolean = false): Array<Pair<Float, Color>> =
    Array(steps + 1) { i ->
        val t = i / steps.toFloat()
        // transparent 那支（天空淡出、让页面渐变接手）**提前收到 0 并保持平**：
        // 收在 88% 处之后 alpha 恒为 0 ⇒ 接缝两侧各 30 行同色，
        // 「上 30 行 − 下 30 行」不再被斜坡本身顶到 ~2 灰阶（B4-02 第 18 轮）。
        val u = if (transparent) (t / 0.88f).coerceAtMost(1f) else t
        val a = u * u * (3f - 2f * u)
        t to fadeTo.copy(alpha = if (transparent) 1f - a else a)
    }

/**
 * 天空在 f 处**真正上屏**的颜色：原色 → 叠柔化雾 → 叠暗度。
 *
 * ★ 2026-10-25 真机实测（B4-02 阶段 2）：羽化带第一行是一道**全宽**硬边 ——
 *   y=489 (156,127,115) → y=490 (184,140,127)，+16.05 灰阶；四条 x 带（2/300/600/1200）
 *   读数逐行完全相同 ⇒ 不是窄带边缘现象，是整条水平接缝。
 *   根因：skyAtFadeStart 取的是 look.colorAt() 的**原始**天空色，而它上方那片天空早已被
 *   「柔化雾」与「暗度」两笔压过；羽化带在 t = 0 处的 alpha 是 **1**（见 featherStops 的
 *   透明支）⇒ 等于把压过的那层原样盖回去，第一行立刻亮 16 灰阶。
 *   ⇒ 起点色必须与它正上方那一行同色：这里按绘制顺序（先雾、后暗）把两支压制折进颜色。
 */
internal fun dimmedSkyAt(
    look: SkyLook,
    f: Float,
    veil: Color,
    soft: Int,
    dim: Int,
    lightIcons: Boolean,
): Color {
    var c = look.colorAt(f.coerceIn(0f, 1f))
    val softA = (soft.coerceIn(0, 100) / 100f) * 0.30f
    if (softA > 0f) c = over(c, veil, softA)
    if (dim > 0 && lightIcons) {
        c = over(c, Color.Black, (dim.coerceIn(0, 80) / 80f) * 0.62f)
    }
    return c
}

/**
 * sRGB 空间里的 source-over —— Skia 画一笔半透明实色时就是这么混的。
 *
 * 不用 lerp：Compose 的 Color 版走 Oklab，实测同一个起点会偏 2~3/255，
 * 而这条接缝的判据是 ≤0.5 灰阶（verify/b4-02/seam_step.py 的 STEP_MAX）。
 */
private fun over(dst: Color, src: Color, alpha: Float): Color {
    val a = alpha.coerceIn(0f, 1f)
    return Color(
        red = dst.red * (1f - a) + src.red * a,
        green = dst.green * (1f - a) + src.green * a,
        blue = dst.blue * (1f - a) + src.blue * a,
        alpha = dst.alpha * (1f - a) + src.alpha * a,
    )
}

/**
 * 天空底边 y（px）。
 *
 * 取整是硬要求：`fadeEnd` 落在小数上时，渐变的最后一行与下面那块实色的第一行**各覆盖半个像素**，
 * 合成出一条 1px 暗线（B4-02 实测：y=523 一行掉 13 灰阶、下一行又涨 12）。
 */
internal fun skyBottomY(canvasHeightPx: Float, compact: Boolean): Float =
    (canvasHeightPx * if (compact) 0.86f else 0.80f).toInt().toFloat()

/**
 * 羽化带起点 y（px）：天空底边往上 [SKY_FADE_SPAN] × 画布高，同样取整。
 *
 * 带长 0.24h 是判据定的：相册照片比纸面暗得多（实测 143 → 245 ≈ 100 灰阶），
 * 118px 的带里每行要掉 0.85 灰阶，超出"地板+2"的余量；放到 0.24h（210px）后
 * 相册约 0.63/行、内置天空约 0.47/行（`verify/b4-02/measure.py` 复测）。
 */
internal const val SKY_FADE_SPAN = 0.24f

internal fun skyFadeStartY(canvasHeightPx: Float, skyBottom: Float): Float =
    (skyBottom - canvasHeightPx * SKY_FADE_SPAN).toInt().toFloat()

/**
 * 羽化带里天空 alpha 变成 0 的那一行（px），**之后一直是 0**。
 *
 * 这是接缝判据的核心数字：这一行往下（一直到画布末行）天空**一点贡献都不留**，
 * 画面上只剩页面那支渐变 —— 与画布外第一行同色 ⇒ 画布内最后一行 == 画布外第一行。
 * 只要天空在这段里还有残留，画布底边就会比周围暗/亮一档（B4-02 实测 y=704 STEP 2.10）。
 */
internal fun skyAlphaZeroRow(canvasHeightPx: Float, compact: Boolean): Int {
    val skyBottom = skyBottomY(canvasHeightPx, compact)
    val fadeStart = skyFadeStartY(canvasHeightPx, skyBottom)
    val band = skyBottom - fadeStart
    val stops = featherStops(Color.White, transparent = true)
    // 渐变在相邻色标之间是**线性插值** ⇒ alpha 从第一枚 0 的色标起就是 0，且此后保持平。
    val zeroIndex = stops.indexOfFirst { it.second.alpha == 0f }
    require(zeroIndex > 0) { "羽化带必须先有非零 alpha 再收 0（否则分界处没有斜坡）" }
    require(stops.drop(zeroIndex).all { it.second.alpha == 0f }) { "alpha 收 0 之后必须保持平" }
    return (fadeStart + stops[zeroIndex].first * band).toInt()
}

private fun DrawScope.drawSkyScene(
    look: SkyLook,
    preset: SkyPreset,
    hour: Int,
    wallpaper: ImageBitmap?,
    dim: Int,
    soft: Int,
    veil: Color,
    fadeTo: Color,
    /** 页面底色的对角渐变（整屏首页传；外观小样传 null = 仍旧用实色 `fadeTo`）。 */
    pageGradient: androidx.compose.ui.graphics.Brush? = null,
    /** 那支渐变所在的**整屏高度**（px）：几何要和画布外逐位一致，不能按画布大小重算。 */
    pageHeightPx: Float = 0f,
    panX: Float = 0f,
    panY: Float = 0f,
    zoom: Float = 1f,
    compact: Boolean = false,
) {
    val w = size.width
    val h = size.height
    drawRect(fadeTo)

    // 天空真正的底边。板上是 `.atmos{height:220px}` 一整块硬边 + 山棱坐在底边上；
    // 我们这里要在底边之前收一点渐隐（滚动时内容会从天空下穿出来，硬边会割眼），
    // 但**渐隐必须结束在 skyBottom**，否则山棱会画到已经淡成纸色的那一段上，
    // 变成一条灰带（2026-10-21 真机截图就是这个）。
    // ★ 取整：fadeEnd 落在小数上时，渐变的最后一行与下面那块实色的第一行**各覆盖半个像素**，
    //   合成出一条 1px 的暗线（B4-02 实测：y=523 一行掉 13 灰阶、下一行又涨 12）。
    //   把底边对齐到整像素，两条边就落在同一行上。
    val skyBottom = skyBottomY(h, compact)
    // B4-02（业主 2026-10-23「壁纸与底下页面那条割裂线」）：**羽化带**。
    //   实测（`verify/b4-02/`，模拟器静止帧噪声地板 0.0）：0.10h 的带在分界处
    //   相邻行差峰值 2.37 灰阶，判据是 ≤2 ⇒ 那条末端的台阶就是他看到的那条线。
    //   带长加到 0.18h，并把 alpha 斜坡换成 smoothstep（两端斜率为 0）：
    //   同样 46 灰阶的总落差摊到 118px，末端的硬台阶消失。
    // 相册档的照片通常比纸面暗得多（实测 143 → 245 ≈ 100 灰阶），118px 的带里每行要掉 0.85 灰阶；
    // 内置天空那条的 46 灰阶摊在同样长度上也有 0.7/行 —— 两条都超了判据（地板+2）里的抖动余量。
    // 统一放到 0.24h：相册约 0.63/行、内置天空约 0.47/行（`verify/b4-02/measure.py` 复测）。
    val fadeStart = skyFadeStartY(h, skyBottom)
    val fadeEnd = skyBottom

    if (wallpaper != null && preset == SkyPreset.ALBUM) {
        drawCoverBitmap(wallpaper, panX = panX, panY = panY, zoom = zoom)
        // 相冊圖不確定明暗：先壓一層可讀性底座，白字才站得住。
        drawRect(
            // ★ 第 6 轮独立审计必修 2：这第三支**也**漏了 startY/endY（第 5 轮那两条只补了
            //   天空本体与 fade）。漏的后果：按整块画布 656px 解算 ⇒ 0.55 站落在 361 而不是 289，
            //   画到 skyBottom=525 只走完 80% 的衰减，在相册档的底边留一条 alpha≈0.053 的黑边（硬切）。
            //   由 `HomeSkyTest` 钉住：凡配 `size = Size(w, skyBottom)` 的 verticalGradient 必须带 startY。
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.34f),
                0.55f to Color.Black.copy(alpha = 0.12f),
                1f to Color.Transparent,
                startY = 0f,
                endY = skyBottom,
            ),
            size = Size(w, skyBottom),
        )
    } else {
        // ★ 第 5 轮独立审计（装机帧实测）：**不写 startY/endY 的 linear brush 是按整块 DrawScope 解算的**
        //   （875px = 250dp），而这一发 `size = Size(w, skyBottom)` 只画到 700px。
        //   于是 x<56 那条竖带 y=0..481 **逐字节死平 = 482 行零梯度**，拐点 481±2 = 0.55×875，
        //   而代码一直以为它是 0.55×700 = 385；`look.bottom` 永远走不到（画到底也只到渐变的 80%），
        //   新补的 `mid` 那一站会落在 0.46×875 = 402 而不是板上要的 322（偏 80px）。
        //   业主报的「糊」这一版没闭 —— 量级还是代码自认的 1.25 倍。
        //   同文件下面那支 fade 一直写着显式 startY/endY（实测拐点 612.5 与 700−0.10h 逐位吻合）⇒ 照它写。
        //   验收：x<56 条带的死平行数必须为 0；白天档 `mid` 的拐点应在 322±2。
        drawRect(
            // 板上是**三站**（wz-v2.html:166 浅色、:169 深色），没有一处画成"0→top, 0.55→top"。
                // ★ 独立审计（v3 #2 第 6 轮 P0#3a）：`mid == null` 的那四支（5..7 晨、17..19 昏、
                //   20..22、23+ 夜）还在走两站老画法 ⇒ 0~55% 是一条**水平色带**，
                //   他 06:0x 看到的正是这一档，实测死平 **286–291 行 ≈ 110dp**（第 5 轮只修了 AUTO 白天两支）。
                //   现在两支合并成一条：没有 `mid` 就取 `lerp(top, bottom, 0.46)` ——
        //   单调、不硬造颜色、也不需要给每档再开一份令牌。
            brush = Brush.verticalGradient(
                0f to look.top,
                SKY_MID_STOP to (look.mid ?: lerp(look.top, look.bottom, SKY_MID_STOP)),
                1f to look.bottom,
                startY = 0f,
                endY = skyBottom,
            ),
            size = Size(w, skyBottom),
        )
        // ★ 板 `.atmos` 在竖向渐变之上还有**两层椭圆 radial 光晕**，此前整层漏做：
        //   `radial-gradient(120% 90% at 86% 6%, rgba(255,250,225,.92), transparent 46%)`
        //   `radial-gradient(70% 60% at 78% 18%, rgba(255,255,255,.70), transparent 60%)`
        // 独立审计（2026-10-22，v3 #2 第 1 轮）量的就是这个后果：板上日盘与紧邻背景的
        // CR=1.08（日盘几乎融进被抬亮的右上角），实现里是 **1.50，而且背景完全平**
        // （y=160 一行六个取样点全等 (143,167,176)）⇒ 日盘成了平场上唯一的鼓包，读作霉点。
        // 浅色天空才补这两层：板的深色档另有自己的冷色光晕（见 drawCelestialBody 那段）。
        if (!look.lightIcons) drawAtmosGlow(w = w, skyBottom = skyBottom)
        else drawAtmosGlowDark(w = w, skyBottom = skyBottom)
        drawCelestialBody(
            look = look,
            preset = preset,
            hour = hour,
            w = w,
            h = h,
            skyBottom = skyBottom,
            compact = compact,
        )
        if (preset == SkyPreset.NIGHT || (preset == SkyPreset.AUTO && look.lightIcons)) {
            drawStars(w = w, h = h, compact = compact)
        }
    }

    // 柔化：一層同色薄霧（主題面色）。深色主題用 surface 壓暗，淺色用偏白的面色提亮。
    //
    // ★ 2026-10-22 獨立審計把「壁紙模糊過重 = 這層霧糊的」這個診斷**推翻了**：
    //   默認 soft=26 換算出來只有 7.8% alpha，改顏色前後同一片天空頂只差 4/255、
    //   山棱台階只差 3% —— 這一層在數學上撐不起「把整片洗灰」。
    //   真正的元先是 ① 默認 dim=24 折成 18.6% 全不透明黑蓋滿天空、
    //   ② 頁面底色 fade 畫在山棱**之後**吃掉了它下面 55%、③ 遠山色值抄錯。
    //   所以這層保留原樣（它無害），病根在下面三處各自修。
    if (soft > 0) {
        drawRect(
            color = veil.copy(alpha = (soft.coerceIn(0, 100) / 100f) * 0.30f),
            size = Size(w, fadeEnd),
        )
    }

    // 暗度：只壓天空那一段。
    //
    // ★ 只在**深色天空**生效（獨立審計必修第 1 條）。板上對淺色天光**沒有任何一層 dim**，
    //   而默認 dim=24 折出來是 18.6% 全不透明黑，把板上的 #A9CBD8(169,203,216)
    //   壓成實測 (143,167,176) —— 整片被壓暗到 0.81×，這才是業主看到的「糊」。
    //   滑桿與默認值都保留（暮色/夜航那兩檔要用它壓暗），只是淺色檔不再吃它。
    if (dim > 0 && look.lightIcons) {
        drawRect(
            color = Color.Black.copy(alpha = (dim.coerceIn(0, 80) / 80f) * 0.62f),
            size = Size(w, fadeEnd),
        )
    }

    // 天空溶進內容：從 fadeStart 到尾端完全變成頁面底色，沒有硬邊。
    //
    // ★ 這裡踩過一次**靜默失靈**（復評 2026-10-21 量到 y=696→702 一行內亮度 157→231）：
    //   上一版把 `fadeStart/fadeEnd`（**像素**）直接當 colorStop 的 0~1 分數傳進去，
    //   兩個數都被鉤到 1.0 ⇒ 整層漸變等於空操作，硬邊一直留在那兒。
    //   像素座標只能給 `startY/endY`。
    // 画布底边这一段：**有页面渐变就用它**（同一支、同一个整屏几何），
    // 这样画布内最后一行与画布外第一行是同一个颜色 ⇒ 那条 2.7 的台阶消失。
    // 没有渐变（外观小样）时维持实色 —— 小样底下是卡片，不是整页渐变。
    // 相册档与内置天空档要分成两支，理由见下面那一支的注释。
    val albumPhoto = wallpaper != null && preset == SkyPreset.ALBUM
    if (pageGradient != null && pageHeightPx > 0f && albumPhoto) {
        // ★ 2026-10-24 真机实测（B4-02 阶段 2）：相册档在羽化带上沿有一行 113 灰阶的硬切。
        //   内置档底下**本来就是天空自己在 fadeStart 那一行的颜色**，所以「天空色淡出」两端都接得上；
        //   相册档底下是「他的照片 + scrim」，天空色 (ALBUM colorAt(490/700) = (49,59,81)) 压上去
        //   第一行就掉 113 灰阶（真机 y=489 (170,178,175) → y=490 (48,59,80)；
        //   同位置内置天空档只掉 4.33，两代 APK 完全一样 ⇒ 是既有缺陷，与 B4-03 抬白无关）。
        //   ⇒ 相册档改成「纸色淡入」：featherStops(fadeTo)（不带 transparent）是 alpha 0→1，
        //   带顶照片原样、带底纸色，两端都接得上，不再有台阶。
        clipRect(left = 0f, top = fadeEnd, right = w, bottom = h) {
            drawRect(brush = pageGradient, size = Size(w, pageHeightPx))
        }
        // ★ 2026-10-25 第 40 轮：淡入的**底色不能是平涂的 pageBg**，必须就是下面那支页面渐变。
        //   实测（verify/b4-02/r5_album_4034d05f_a.png，模拟器 560dpi）：上一版 fadeTo 是
        //   MiuixTheme.colorScheme.background 这个**单一颜色**，而 y>=fadeEnd 是 pageGradient
        //   那支**对角**渐变（AppColors.pageBrush，左上→右下）。两者只在某些 x 上恰好相等，
        //   于是 y=699→700 出现一条方向随 x 翻转的台阶：左条 x[2,18) 亮 1.38、右条 x[1200,1214)
        //   暗 1.78 灰阶（判据 STEP_MAX 0.5）。两帧各自内部水平极平（max|dq| 0.22/0.25）⇒ 是边界不是噪声。
        //   做法：把 pageGradient 铺进一层离屏层，再用纵向 alpha 0→1 的 **DstIn** 蒙版削出斜坡 ——
        //   带顶 alpha=0 照片原样、带底 alpha=1 恰好等于带下第一行 ⇒ 两侧都接得上。
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, fadeStart, w, fadeEnd), Paint())
            drawRect(brush = pageGradient, size = Size(w, pageHeightPx))
            drawRect(
                brush = Brush.verticalGradient(
                    *featherStops(Color.White),
                    startY = fadeStart,
                    endY = fadeEnd,
                ),
                topLeft = Offset(0f, fadeStart),
                size = Size(w, (fadeEnd - fadeStart).coerceAtLeast(0f)),
                blendMode = BlendMode.DstIn,
            )
            canvas.restore()
        }
    } else if (pageGradient != null && pageHeightPx > 0f) {
        // 从羽化带起点就铺页面渐变（一直铺到画布底边），再用**天空自己的颜色淡出**盖在上面：
        //   结果 = 天空×(1−a) + 页面渐变×a —— 带的两端都与两侧同色，700 与 875 都不再有台阶。
        clipRect(left = 0f, top = fadeStart, right = w, bottom = h) {
            drawRect(brush = pageGradient, size = Size(w, pageHeightPx))
        }
        // 起点色必须是**上屏后**的颜色（雾 + 暗都折进来了），否则羽化带第一行会亮一档：
        // 2026-10-25 真机实测 y=489→490 全宽 +16.05 灰阶，见 [dimmedSkyAt] 的注释。
        val skyAtFadeStart = dimmedSkyAt(
            look = look,
            f = (fadeStart / skyBottom).coerceIn(0f, 1f),
            veil = veil,
            soft = soft,
            dim = dim,
            lightIcons = look.lightIcons,
        )
        // ★ 2026-10-24 业主 #58「首页壁纸偏灰」的真正根因就在这一个 drawRect：**忘了写 topLeft**。
        //   渐变自己写了 startY = fadeStart / endY = fadeEnd（像素坐标，按画布解算），
        //   可这个矩形的左上角默认在 (0,0)、高 fadeEnd ⇒ 它盖住 y=0..fadeEnd，
        //   而 y < fadeStart 那一段落在渐变的 t = 0 上；featherStops(transparent = true)
        //   在 t = 0 处 alpha = 1 − smoothstep(0) = **1** ⇒ 整片天空被 skyAtFadeStart
        //   这一种颜色**不透明地平涂**，三站渐变、两层 atmos 光晕、日轮/星点全被盖掉。
        //   实测坐实（verify/58-unknown-count/analyze_flat_sky.py，exit 0）：
        //     日档 y=0..470 众数 (221,231,232) == colorAt(490/700) = (221.00,230.89,231.67) 偏差 0；
        //     夜档 (29,40,69) == colorAt(490/700) = (29.60,40.40,68.80) 偏差 1。
        //   ⇒ 矩形必须从 fadeStart 起跳，高度只到 fadeEnd。
        drawRect(
            brush = Brush.verticalGradient(
                *featherStops(skyAtFadeStart, transparent = true),
                startY = fadeStart,
                endY = fadeEnd,
            ),
            topLeft = Offset(0f, fadeStart),
            size = Size(w, (fadeEnd - fadeStart).coerceAtLeast(0f)),
        )
    } else {
        drawRect(
            color = fadeTo,
            topLeft = Offset(0f, fadeEnd),
            size = Size(w, (h - fadeEnd).coerceAtLeast(0f)),
        )
    }
    // 没有页面渐变（外观小样）时才用"淡到实色"这条老路；
    // 有渐变时上面那支"渐变 + 天空色淡出"已经接管了整段，再画一次会把台阶盖回去。
    if (pageGradient == null || pageHeightPx <= 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                // 12 段 smoothstep：两端斜率为 0 ⇒ 分界处不再有"最后一跳"（B4-02 实测的就是它）。
                *featherStops(fadeTo),
                startY = fadeStart,
                endY = fadeEnd,
            ),
            size = Size(w, fadeEnd),
        )
    }

    // 山棱**最後**畫，騎在 fade 之上（獨立審計必修第 4 條）。
    // 画在 fade 之前时，`fadeStart = skyBottom − 0.22h`（第 4 轮已削到 0.10h）从 y≈381 起就把这条 99dp 剪影带的
    // 下 55% 洗成纸色 ⇒ 实测远山台阶 ΔL 只有 0.032，而板上是 0.110（差 3.4 倍）。
    if (look.ridgeFar != Color.Transparent) {
        // 板 `.hills{height:112px}` × 0.886 ⇒ **99dp**，两条贝塞尔剪影的**透明度是板上的值**：
        // 远山 rgba(74,103,108,.12)、近山 rgba(53,77,80,.16)。
        // 色相跟 look.ridgeFar/ridgeNear 走（早/昏/夜各有各的山色），
        // 但**浓度按板**——原来 .88/.105 那种实色山脊在浅色天光下就是两条黑杠。
        boardHills(
            far = look.ridgeFar.copy(alpha = if (look.lightIcons) 0.16f else 0.12f),
            near = look.ridgeNear.copy(alpha = if (look.lightIcons) 0.20f else 0.16f),
            bandHeight = HILL_BAND_DP.dp.toPx(),
            bottom = skyBottom,
        )
        // 不要再补一条"底边收口"：剪影自己的 alpha 羽化（`HILL_FEATHER`）已经在画布底边之前
        // （y ≈ skyBottom − 25px）把山棱溶进纸面，实测那条边 ΔL ≤ 0.05。
        // 旧的那条用**实色** `fadeTo` 收到 `skyBottom + 4f`，而画布底边以下是**页面对角渐变**
        // —— 两支颜色不同 ⇒ 右边缘 y=704 实测 STEP 2.10 / PEAK 2.38（B4-02 判据 ❌）。
    }
}

/**
 * 板 `.atmos` 那两层椭圆光晕（暖米 + 白），**只给浅色天空**。
 *
 * Compose 的 `Brush.radialGradient` 只有正圆，所以按 CSS 的椭圆比例
 * 在 y 方向压扁：`ry/rx = (220 × 0.90) / (392 × 1.20) = 0.427`。
 * 圆心位置照板上的 `at 86% 6%` 与 `at 78% 18%`（百分比是相对 `.atmos` 那块 392×220 的盒子，
 * 我们这里对应的就是 `w` 与 `skyBottom`）。
 */
private fun DrawScope.drawAtmosGlow(w: Float, skyBottom: Float) {
    val squash = 0.427f
    // 画完之后 y 方向被压成 0.427，所以**矩形必须先按逆比例加长**，
    // 否则压扁时矩形自己就被裁短 —— 第 2 轮独立审计量到的三条一像素硬边
    // （y=18 处 ΔL 0.083/0.177/**0.250**，06:30 档到 0.359）就是这么来的。
    // 要让压扁后的像覆盖 [0, skyBottom]，原像就得覆盖
    //   top = cy·(1 − 1/s)，height = skyBottom / s
    val inv = 1f / squash
    fun glow(cx: Float, cy: Float, core: Color, stop: Float, radius: Float) {
        withTransform({ scale(1f, squash, pivot = Offset(cx, cy)) }) {
            drawRect(
                brush = Brush.radialGradient(
                    0f to core,
                    stop to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                topLeft = Offset(0f, cy * (1f - inv)),
                size = Size(w, skyBottom * inv),
            )
        }
    }
    glow(w * 0.86f, skyBottom * 0.06f, Color(0xFFFFFAE1).copy(alpha = 0.92f), 0.46f, w * 1.20f)
    glow(w * 0.78f, skyBottom * 0.18f, Color.White.copy(alpha = 0.70f), 0.60f, w * 0.70f)
}

/**
 * 板 `body.dark .atmos` 那一层角落光晕（深色天空档此前**完全没有**，第 3 轮审计必修第 3 条）：
 * `radial-gradient(95% 80% at 82% 10%, rgba(186,210,220,.30), transparent 52%)`。
 *
 * 椭圆比：rx = 0.95w，ry = 0.80 × atmos 高 ⇒ `ry/rx = (220×0.80)/(392×0.95) = 0.4726`。
 * 有了它，暮色/夜航那两档的日盘才有东西可站 —— 上一版盘落到远山棱上被「此刻」卡
 * 100% 吃掉（实测 ΔL 0.0015），等于偷偷把黄斑"藏"掉而不是改掉。
 */
private fun DrawScope.drawAtmosGlowDark(w: Float, skyBottom: Float) {
    val squash = 0.4726f
    val inv = 1f / squash
    val cx = w * 0.82f
    val cy = skyBottom * 0.10f
    withTransform({ scale(1f, squash, pivot = Offset(cx, cy)) }) {
        drawRect(
            brush = Brush.radialGradient(
                0f to Color(0xFFBAD2DC).copy(alpha = 0.30f),
                0.52f to Color.Transparent,
                center = Offset(cx, cy),
                radius = w * 0.95f,
            ),
            topLeft = Offset(0f, cy * (1f - inv)),
            size = Size(w, skyBottom * inv),
        )
    }
}

/** 太陽 / 月亮。位置只取決於預設與小時，不做逐幀動畫（背景不該一直動）。 */
private fun DrawScope.drawCelestialBody(
    look: SkyLook,
    preset: SkyPreset,
    hour: Int,
    w: Float,
    h: Float,
    skyBottom: Float,
    compact: Boolean,
) {
    val scale = if (compact) 0.62f else 1f
    // 板 `.atmos .sun{background:radial-gradient(circle,
    //      rgba(255,246,204,.92) 0%, rgba(255,240,190,.42) 38%, transparent 70%)}`
    // —— 整颗球**没有一条硬边**：38% 处已经掉到 .42，70% 就透明了。
    // 原来这里画的是 `drawCircle(color = core, radius = r)` 一枚实心圆片，
    // 真机上就是一枚贴在天空上的米色硬币（2026-10-21 截图就是这个）。
    //
    // ★ `midA` 是独立审计（v3 #2 第 1 轮必修第 5 条）要的：**核心与 38% 档必须差一个台阶**。
    //   原来 38% 处硬编码 `glow.copy(alpha = 0.42f)`，而暮色档传进来的 core 已经是 .42
    //   ⇒ 两个停靠点同色同 alpha，画出来是一枚"平顶圆片"，那正是"糊斑"而不是"光"的形状。
    //   板上白天的峰谷比是 0.92/0.42 = 2.19，这里按同一个比给：0.42/0.19 = 2.21。
    fun body(cx: Float, cy: Float, core: Color, glow: Color, r: Float, midA: Float = 0.42f) {
        val ball = r * 2.2f
        drawCircle(
            brush = Brush.radialGradient(
                0f to core,
                0.38f to glow.copy(alpha = midA),
                0.70f to Color.Transparent,
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = ball,
            ),
            radius = ball,
            center = Offset(cx, cy),
        )
    }

    when (preset) {
        SkyPreset.MORNING -> body(
            w * 0.80f, h * 0.16f,
            Color(0xFFFFF3CF), Color(0xFFFFE6AE),
            h * 0.045f * scale,
        )

        SkyPreset.DUSK -> body(
            w * 0.74f, h * 0.38f,
            Color(0xFFFFCE9A), Color(0xFFFFB27A),
            h * 0.052f * scale,
        )

        SkyPreset.NIGHT -> {
            body(
                w * 0.78f, h * 0.15f,
                Color(0xFFEAF0FF), Color(0xFFCBD8FF),
                h * 0.040f * scale,
            )
            // 缺一口的月亮：用天空主色再蓋一個圓，比混合模式可靠。
            drawCircle(
                color = look.top.copy(alpha = 0.96f),
                radius = h * 0.034f * scale,
                center = Offset(w * 0.78f + h * 0.016f, h * 0.15f - h * 0.011f),
            )
        }

        SkyPreset.ALBUM -> Unit

        SkyPreset.AUTO -> {
            val isNight = hour < 6 || hour >= 19
            val t = if (isNight) (((hour + 5) % 24) / 12f) else ((hour - 5f) / 14f).coerceIn(0f, 1f)
            // 板 `.atmos .sun{width:120px;height:120px;right:36px;top:34px}`，
            // 而 `.atmos` 高 220px、宽 392px ⇒ 圆心在 (0.756w, 0.427h)、半径 60px⇒53dp。
            // 旧的 0.09h + 6dp 是把「一颗小光点」当成了太阳，板上是一轮**发虚的大日盘**
            // （radial 里 .92 只占最中心，38% 就掉到 .42，70% 透明）。
            val cx = w * (0.756f - 0.10f * t)
            // 板上日盘**可见**半径 = 52.6dp（120px 盒 ÷ 0.886，radial 在 70% 处透明 ⇒
            // 看得见的那圈是 ball 的 0.70）。`body()` 里 ball = r × 2.2，
            // 所以 r 要给 **34dp**：34 × 2.2 × 0.70 = 52.4dp ≈ 板值。
            // 以前给 24dp ⇒ 可见圈只有 37dp，盘比板上小一圈、边界更硬，
            // 这正是第 3 轮审计说"CR 与环色还差一点"的来源。
            val sunR = 34.dp.toPx()
            if (isNight) {
                body(cx, skyBottom * 0.427f, Color(0xFFEAF0FF), Color(0xFFCBD8FF), h * 0.030f * scale)
                drawCircle(
                    color = look.top.copy(alpha = 0.95f),
                    radius = h * 0.026f * scale,
                    center = Offset(cx + h * 0.012f, skyBottom * 0.427f - h * 0.008f),
                )
            } else if (look.lightIcons) {
                // ★ 17–19 點這一份天空是**靛底**（#4A5578 → #F0A97A），板上白天那枚
                // rgba(255,246,204,.92) 的淡日盘压上去就是一块**外来的黄绿糊斑**
                // （2026-10-22 模拟器逐帧扫出来的：17:30 档 2182px，质心正是日盘心）。
                //
                // 第 3 轮审计把上一版"落到远山棱上"的修法**打回**了：盘落到 `skyBottom − 0.10h`
                // 之后 100% 被「此刻」卡吃掉（实测该处 ΔL 0.0015 ⇒ 等于没画），
                // 那句"CR ≤ 1.30"是假绿灯。现在按板的深色档原文改：
                //   `body.dark .atmos .sun{radial-gradient(circle,
                //        rgba(210,230,235,.55) 0%, rgba(170,205,212,.16) 42%, transparent 72%)}`
                // —— **降饱和 + 转冷**（H≈194°），不是我们自造的暖米色；
                // 位置回到与浅色档同一个基准 `skyBottom·0.427`，看得见、也不再是黄色。
                body(
                    cx = cx,
                    cy = skyBottom * 0.427f,
                    core = Color(0xFFD2E6EB).copy(alpha = 0.30f),
                    glow = Color(0xFFAACDD4),
                    r = sunR * scale,
                    midA = 0.16f,
                )
            } else {
                // 板上写死的是 rgba(255,246,204,**.92**)，这里以前漏掉了那个 .92 直接铺满不透明
                // —— 浅色天空上差别不大，但它是这块日盘唯一的"实"的来源。
                // 浅色档的日盘**不落地**：板的 `.atmos .sun` 就挂在 `top:34px`（0.427h），
                // 而且上面刚补的两层 radial 会把它垫进一片被抬亮的右上角里（审计第 2 条）。
                body(
                    cx = cx,
                    // 板的 `.atmos .sun{top:34px}` 是相对 **`.atmos` 那块 220px 的盒**算的
                    // ⇒ 圆心 94/220 = 0.427 × atmos 高。我们这里 atmos 对应的是 `0..skyBottom`
                    // 那一段（不是整块 250dp 画布），所以必须乘 `skyBottom`。
                    // 第 2 轮独立审计量的就是搞错了基准：盘比光晕心低了 51px，站在晕外，
                    // 于是"日盘"又变回平场上的一个孤立鼓包。
                    cy = skyBottom * 0.427f,
                    core = Color(0xFFFFF6CC).copy(alpha = 0.92f),
                    glow = Color(0xFFFFF0BE),
                    r = sunR * scale,
                )
            }
        }
    }
}

private fun DrawScope.drawStars(w: Float, h: Float, compact: Boolean) {
    val top = h * if (compact) 0.55f else 0.42f
    repeat(18) { i ->
        // 固定位置：同一片天空每帧星星不該跳。抖動靠 (i * 37 % 5) 這種整數散列。
        val x = ((i * 97 + 13) % 1000) / 1000f * w
        val y = ((i * 53 + 7) % 1000) / 1000f * top
        val r = 0.7f + ((i * 29) % 3) * 0.5f
        drawCircle(
            color = Color.White.copy(alpha = 0.18f + ((i * 17) % 4) * 0.09f),
            radius = r,
            center = Offset(x, y),
        )
    }
}

/**
 * 山棱：**逐点照抄 v2 定稿板 `.hills` 那两条三次贝塞尔**。
 *
 * 板上是 `viewBox="0 0 392 112" preserveAspectRatio="none"` 的两条曲线剪影，
 * 旧的画法是「折线锯齿」(ridge/amp/step 三角峰) —— 业主与复评都判它「山棱太重、
 * 像两个黑三角」。所以这次不做近似：按 392×112 的原坐标把每条 cubicTo 搬进来，
 * 再按 x=w/392、y=bandHeight/112 非等比缩放（和 CSS 的 preserveAspectRatio=none 同一件事）。
 *
 * 坐标（板上原样）：
 *  远山 `M0 74 C46 52 92 84 142 66 C196 47 232 30 286 52 C330 70 360 62 392 48 L392 112 L0 112Z`
 *  近山 `M0 92 C52 76 86 96 134 86 C190 74 226 62 278 78 C326 92 356 86 392 76 L392 112 L0 112Z`
 */
/** 山棱底边羽化带占山棱高度的比例（B4-02：那条 2.35 灰阶的台阶就是它的硬底边）。 */
internal const val HILL_FEATHER = 0.18f

/** 山棱带高度（dp）：板 `.hills{height:112px}` × 0.886。 */
internal const val HILL_BAND_DP = 99

/**
 * 山棱剪影的 alpha 羽化带（山棱带内相对位置 0..1 → alpha）。
 *
 * 收 0 之后**保持平**：山棱底边正好落在画布底边，紧邻接缝的 30 行若还在被它拉出斜坡，
 * 就会在分界处再合成一条台阶（B4-02 第 18 轮实测 y=703 的"上30−下30"就是它）。
 */
internal fun hillFeatherStops(): List<Pair<Float, Float>> = listOf(
    0f to 1f,
    (1f - HILL_FEATHER) to 1f,
    (1f - HILL_FEATHER * 0.4f) to 0f,
    1f to 0f,
)

/**
 * 山棱 alpha 收到 0 的那条 y（px）——**必须落在画布底边之前**。
 *
 * 山棱的底边收口不再靠"补一条实色"（那条实色与页面对角渐变不同色 ⇒ 右边缘实测 STEP 2.10 ❌），
 * 改由剪影自己的 alpha 羽化溶进纸面；这里给出羽化终点，供守卫算出"离底边还差多少像素"。
 */
internal fun hillAlphaZeroY(skyBottom: Float, bandHeightPx: Float): Float {
    // 渐变在相邻色标之间线性插值 ⇒ alpha 从**第一枚** 0 的色标起就是 0。
    val alphaZeroStop = hillFeatherStops().first { it.second == 0f }.first
    return skyBottom - bandHeightPx + bandHeightPx * alphaZeroStop
}

private fun DrawScope.boardHills(
    far: Color,
    near: Color,
    bandHeight: Float,
    bottom: Float,
) {
    val w = size.width
    val top = bottom - bandHeight
    val sx = w / 392f
    val sy = bandHeight / 112f

    fun hill(points: Array<FloatArray>, color: Color) {
        val path = Path()
        path.moveTo(points[0][0] * sx, top + points[0][1] * sy)
        var i = 1
        while (i + 2 < points.size) {
            path.cubicTo(
                points[i][0] * sx, top + points[i][1] * sy,
                points[i + 1][0] * sx, top + points[i + 1][1] * sy,
                points[i + 2][0] * sx, top + points[i + 2][1] * sy,
            )
            i += 3
        }
        path.lineTo(w, bottom)
        path.lineTo(0f, bottom)
        path.close()
        // B4-02：山棱的**底边**才是那条割裂线的来源 —— 它原来是实色一刀切在 `skyBottom` 上，
        //   而天空在那里的渐隐已经是纸色了（实测台阶 2.35 灰阶，判据 ≤2）。
        //   把剪影最下面 18% 的 alpha 收到 0，让它自己溶进纸面，就不需要"用一刀当地平线"了。
        drawPath(
            path,
            Brush.verticalGradient(
                *hillFeatherStops()
                    .map { (stop, a) -> stop to color.copy(alpha = color.alpha * a) }
                    .toTypedArray(),
                startY = top,
                endY = bottom,
            ),
        )
    }

    hill(
        arrayOf(
            floatArrayOf(0f, 74f),
            floatArrayOf(46f, 52f), floatArrayOf(92f, 84f), floatArrayOf(142f, 66f),
            floatArrayOf(196f, 47f), floatArrayOf(232f, 30f), floatArrayOf(286f, 52f),
            floatArrayOf(330f, 70f), floatArrayOf(360f, 62f), floatArrayOf(392f, 48f),
        ),
        far,
    )
    hill(
        arrayOf(
            floatArrayOf(0f, 92f),
            floatArrayOf(52f, 76f), floatArrayOf(86f, 96f), floatArrayOf(134f, 86f),
            floatArrayOf(190f, 74f), floatArrayOf(226f, 62f), floatArrayOf(278f, 78f),
            floatArrayOf(326f, 92f), floatArrayOf(356f, 86f), floatArrayOf(392f, 76f),
        ),
        near,
    )
}

/**
 * cover 之后**每一轴还能移动多少像素**（一半余量）。
 *
 * 单独拿出来是因为裁切框要把"手指拖了 dx 像素"换算成归一化的 `pan ∈ [-1,1]`，
 * 而那个换算必须和 [drawCoverBitmap] 用的是同一个数 —— 两边各算一份迟早漂移，
 * 漂了就是"预览里没切到、首页却切过头露白边"。
 */
fun coverSlackPx(
    imgW: Int,
    imgH: Int,
    boxW: Float,
    boxH: Float,
    zoom: Float,
): Pair<Float, Float> {
    val z = zoom.coerceAtLeast(1f)
    if (imgW <= 0 || imgH <= 0 || boxW <= 0f || boxH <= 0f) return 0f to 0f
    val scale = maxOf(boxW / imgW, boxH / imgH) * 1.06f * z
    // ★★★ 独立审计（v3 #2 第 6 轮 P0#2，同时命中 #10）：**被除数写错了**。
    //   slack 是「**放大后的图**比框多出来多少」⇒ 必须是 `(imgW * scale − boxW)/2`，
    //   原来写的是 `(boxW * scale − boxW)/2`（拿框的宽去乘缩放比）。
    //   实算 4032×3024 → 1080×656：scale=0.284 ⇒ slackX = **−386.7**、slackY = **−234.9**
    //   ⇒ 图被画到 x[387,1531]/y[235,1093]：**左 147dp、上 89dp 露出页面底色**；
    //   而且 `AppearancePage.kt:1438` 那句 `if (sx > 0f)` 因此恒假 ⇒ **相册壁纸档拖动完全失灵**。
    //   修后同一组数：slackX = +32.5、slackY = +32.6（cover 的 1.06 余量），恒 ≥ 0。
    //   数值判据在 `HomeSkyTest`（四种输入 + `slack >= 0`），不留给录屏。
    return (imgW * scale - boxW) / 2f to (imgH * scale - boxH) / 2f
}

/**
 * 相冊壁紙：等比鋪滿（1.06 倍餘量，避免圓角取樣露邊），再疊上他自己裁的那一刀。
 *
 * v3 #10：`zoom ≥ 1` 是在 cover 之上再放大，`panX/panY ∈ [-1, 1]` 是**在还能动的余量里**
 * 归一化的位移 —— 所以 `pan = ±1` 正好推到边，永远不会把画面拖出框外露出底色，
 * 也就不需要额外的钳制代码。`zoom = 1, pan = 0` 就是以前那个"居中裁满"。
 */
internal fun DrawScope.drawCoverBitmap(
    img: ImageBitmap,
    panX: Float = 0f,
    panY: Float = 0f,
    zoom: Float = 1f,
) {
    val z = zoom.coerceAtLeast(1f)
    val scale = maxOf(size.width / img.width, size.height / img.height) * 1.06f * z
    val (slackX, slackY) = coverSlackPx(img.width, img.height, size.width, size.height, z)
    withTransform({
        translate(
            left = -slackX + slackX * panX.coerceIn(-1f, 1f),
            top = -slackY + slackY * panY.coerceIn(-1f, 1f),
        )
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawImage(img)
    }
}

/** 給外觀頁的說明用：天空的墨色（預覽上的日期文字跟著它走）。 */
fun SkyPreset.inkAtNow(): Color = skyLookOf(this, java.time.LocalTime.now().hour).ink

/** 一顆很小的「真人」高光邊，讓小樣預覽的圓角不像一張貼紙。 */
internal fun DrawScope.previewHairline(color: Color) {
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * com.muyao.wanzaihyperos.ui.design.Glyph.CORNER_RATIO),
        style = Stroke(width = 1f),
    )
}
