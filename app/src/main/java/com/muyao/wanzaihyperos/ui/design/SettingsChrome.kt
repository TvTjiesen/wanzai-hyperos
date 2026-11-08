package com.muyao.wanzaihyperos.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页的行组件（照参照站那套：彩色圆角方形图标 + 标题/副标题 + 右侧 chevron 或开关）。
 *
 * 图标全部 Canvas 自绘，和首页磁贴同一套画法：混用 MiuixIcons 会显得是两套系统，
 * 而且描边粗细/圆角跟我们的玻璃对不上。
 */
enum class SettingIcon {
    // `Theme` 已删（B4-13 第 19 轮）：外观页的主题是三段 `Segmented`，不用图标；
    // 设置页那三行主题撤掉后，全工程只剩这条 `when` 分支本身 ⇒ 渲染器支持一个没人要的东西。
    Glass, Timetable, Clock, Info, School,

    /** 「减弱玻璃透明度」专用：半填的对比圆。
     *  原来它和「外观与壁纸」共用 `Glass` 同一枚图形，同一屏两行一个图标，
     *  读起来像同一件事被写了两遍（复评 2026-10-22）。
     */
    Contrast,

    /**
     * 以下六枚 = v3 #8「账号与登录」六行，**一行一枚**：
     * 六源是六套独立登录态，共用图形就等于告诉用户「这几行是一回事」。
     * （原来的 `Account` 与 `School` 画的是同一个图形，`Account`/`Logout` 两枚
     *  在 #8 重做「帐号」组之后没人用了，一并删掉。）
     */
    /** 统一身份认证 · 门户：盾牌 + 勾。 */
    Shield,

    /** 学在城院：学位帽板 + 帽穗。 */
    Cap,

    /** 教务系统：带三行字的成绩单。 */
    Doc,

    /** 场景管理 CJH52：四向刻度的徽章。 */
    Badge,

    /** 智慧公寓：房子。 */
    House,

    /** 智云课堂：一块带播放三角的屏幕（它的登录态在网页里，只能开页面）。 */
    Screen,

    /**
     * 上课提醒：一只铃铛 + 铃舌。
     *
     * 为什么不用 `Clock`：设置页里「节次时间表」已经占了表盘，同一屏两行一个图形
     * 会读成同一件事（这条规矩在 `Contrast` 那枚上已经立过一次）。
     * 铃铛画法是「顶上的小圆 + 一段钟形弧 + 底边 + 下面一颗铃舌」，四笔都能落在 20dp 里。
     */
    Alarm,

    /**
     * 数智体育：一副**杠铃**（左右两片 + 中间横杠）。
     *
     * 为什么不用秒表 / 跑道：表盘已经被「节次时间表」的 `Clock` 占了，
     * 而徽章式的「环 + 中心点」是 `Badge`；杠铃三笔落在 20dp 里还不与任何一枚撞。
     */
    Dumbbell,

    /**
     * 静音时段：一枚**粗弧月牙**。
     *
     * 为什么不是铃铛加斜杠：同一张卡上面那行「上课提醒」已经是铃铛（`Alarm`），
     * 再画一个带斜杠的铃铛，读起来还是「同一件事被写了两遍」——
     * 这条规矩本文件已经为 `Contrast` 与 `Alarm` 立过两次。
     * 月牙与铃铛形状完全不像，一眼能分。
     */
    Moon,

    /**
     * 本地缓存：**斜柄扫帚**（柄 + 一个斜四边形刷头 + 两笔刷毛）。
     *
     * 「全部清掉」那行也用它：同一张卡里两行说的是同一件事（缓存），
     * 共用一枚是**表达归属**，不是撞车 —— 撞车指的是不相干的两件事长得一样
     * （`本地缓存` 与 `提醒为什么不响` 都画圆圈 i 才是那种错）。
     */
    Broom,

    /**
     * 减弱透明度：一只**睁开的眼睛**（上下两段弧 + 中间瞳孔）。
     *
     * 板 `settings.png` 那一行画的就是眼睛；原来用的 `Contrast`（半填圆）
     * 与「动画与辅助显示」的太阳在同一张卡里都读成「一个圆圈」，分不出两件事。
     */
    Eye,

    /**
     * 动画与辅助显示：一颗**太阳**（圆 + 八道短光）。
     *
     * 板面用它表示「跟随系统」——系统动了，这里跟着动。
     */
    Sun,
}

@Composable
fun SettingGlyph(icon: SettingIcon, tint: Color, modifier: Modifier = Modifier) {
    // ★ 2026-11-07：**去掉彩色磁贴底，改裸线稿**。
    //
    // 依据：v2.1 稿的 `screens/settings.png` 与 `screens/accounts.png` 里，设置行左侧
    // 只有一枚**灰色裸线图标**（像素采样：x=41..62、宽约 21px，底色恒为卡面 `(255,253,248)`，
    // 笔画色 `(96,103,117)`；磁贴不存在）。用户 2026-11-07 的口径是「其余页面一比一复刻」。
    //
    // 与 B4-13（2026-10-23，业主当时要「彩色底 + 白色图形」）的冲突以**更晚的口径**为准；
    // 颜色没有丢，只是从「底色」搬到「笔画」：登入态仍然靠 `tint` 区分（`tileTintFor`），
    // 只是现在读起来是一条靛蓝的线，而不是一块靛蓝的方块。
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(Glyph.size)) {
            val s = size.minDimension
            // B4-13：線寬與圓角走令牌（`Glyph`），不在某一枚圖標裡偷偷改數
            val stroke = s * Glyph.STROKE_RATIO
            val c = tint
            when (icon) {
                SettingIcon.School -> {
                    dot(s * 0.5f, s * 0.34f, s * 0.17f, c)
                    arc(s * 0.16f, s * 0.56f, s * 0.68f, s * 0.5f, stroke, c)
                }

                // 「减弱玻璃透明度」专用：**两张叠起来的薄片**（材质/透明度的语义）。
                // 上一版画的是半填圆，结果就是 `Theme` 那枚的镜像 —— 在设置页里
                // 读起来像第四个主题项（复评 2026-10-22）。
                SettingIcon.Contrast -> {
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(s * 0.22f, s * 0.34f),
                        size = Size(s * 0.46f, s * 0.34f),
                        cornerRadius = CornerRadius(s * Glyph.CORNER_RATIO, s * Glyph.CORNER_RATIO),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                    drawRoundRect(
                        color = c.copy(alpha = 0.55f),
                        topLeft = Offset(s * 0.32f, s * 0.46f),
                        size = Size(s * 0.46f, s * 0.34f),
                        cornerRadius = CornerRadius(s * Glyph.CORNER_RATIO, s * Glyph.CORNER_RATIO),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                }

                SettingIcon.Glass -> {
                    // 调色盘：盘面 + 拇指孔 + 三枚颜料点。
                    //
                    // 原来这里画的是「水滴」（一个圆 + 下面一个尖），落到 24dp 上读起来
                    // 就是一枚地图定位针 —— 真机上「外观与壁纸」那行看着像「位置」。
                    // 板面那一行要表达的是「色彩、材质」，所以改画调色盘。
                    ring(s * 0.5f, s * 0.5f, s * 0.36f, stroke, c)
                    ring(s * 0.56f, s * 0.66f, s * 0.10f, stroke * 0.9f, c)
                    dot(s * 0.34f, s * 0.38f, stroke * 0.72f, c)
                    dot(s * 0.56f, s * 0.32f, stroke * 0.72f, c)
                    dot(s * 0.70f, s * 0.46f, stroke * 0.72f, c)
                }

                SettingIcon.Timetable -> {
                    rr(s * 0.12f, s * 0.16f, s * 0.88f, s * 0.88f, stroke, c)
                    line(s * 0.12f, s * 0.4f, s * 0.88f, s * 0.4f, stroke, c)
                    line(s * 0.36f, s * 0.06f, s * 0.36f, s * 0.28f, stroke, c)
                    line(s * 0.64f, s * 0.06f, s * 0.64f, s * 0.28f, stroke, c)
                }

                SettingIcon.Clock -> {
                    ring(s * 0.5f, s * 0.5f, s * 0.34f, stroke, c)
                    line(s * 0.5f, s * 0.5f, s * 0.5f, s * 0.28f, stroke, c)
                    line(s * 0.5f, s * 0.5f, s * 0.68f, s * 0.56f, stroke, c)
                }

                SettingIcon.Info -> {
                    ring(s * 0.5f, s * 0.5f, s * 0.34f, stroke, c)
                    dot(s * 0.5f, s * 0.32f, stroke * 0.62f, c)
                    line(s * 0.5f, s * 0.46f, s * 0.5f, s * 0.7f, stroke, c)
                }

                // ---- v3 #8 六行账号源：一行一枚，形状彼此不撞 ------------------
                SettingIcon.Shield -> {
                    val shield = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.5f, s * 0.12f)
                        lineTo(s * 0.84f, s * 0.25f)
                        lineTo(s * 0.84f, s * 0.5f)
                        lineTo(s * 0.5f, s * 0.88f)
                        lineTo(s * 0.16f, s * 0.5f)
                        lineTo(s * 0.16f, s * 0.25f)
                        close()
                    }
                    drawPath(shield, c, style = Stroke(width = stroke))
                    line(s * 0.35f, s * 0.47f, s * 0.46f, s * 0.6f, stroke, c)
                    line(s * 0.46f, s * 0.6f, s * 0.67f, s * 0.35f, stroke, c)
                }

                SettingIcon.Cap -> {
                    val board = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.5f, s * 0.2f)
                        lineTo(s * 0.9f, s * 0.42f)
                        lineTo(s * 0.5f, s * 0.64f)
                        lineTo(s * 0.1f, s * 0.42f)
                        close()
                    }
                    drawPath(board, c, style = Stroke(width = stroke))
                    line(s * 0.74f, s * 0.5f, s * 0.74f, s * 0.76f, stroke, c)
                    dot(s * 0.74f, s * 0.8f, stroke * 0.7f, c)
                }

                SettingIcon.Doc -> {
                    rr(s * 0.2f, s * 0.12f, s * 0.8f, s * 0.88f, stroke, c)
                    line(s * 0.33f, s * 0.34f, s * 0.67f, s * 0.34f, stroke, c)
                    line(s * 0.33f, s * 0.52f, s * 0.67f, s * 0.52f, stroke, c)
                    line(s * 0.33f, s * 0.7f, s * 0.55f, s * 0.7f, stroke, c)
                }

                SettingIcon.Badge -> {
                    ring(s * 0.5f, s * 0.5f, s * 0.28f, stroke, c)
                    dot(s * 0.5f, s * 0.5f, s * 0.09f, c)
                    line(s * 0.5f, s * 0.06f, s * 0.5f, s * 0.17f, stroke, c)
                    line(s * 0.5f, s * 0.83f, s * 0.5f, s * 0.94f, stroke, c)
                    line(s * 0.06f, s * 0.5f, s * 0.17f, s * 0.5f, stroke, c)
                    line(s * 0.83f, s * 0.5f, s * 0.94f, s * 0.5f, stroke, c)
                }

                SettingIcon.House -> {
                    val roof = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.1f, s * 0.48f)
                        lineTo(s * 0.5f, s * 0.14f)
                        lineTo(s * 0.9f, s * 0.48f)
                    }
                    drawPath(roof, c, style = Stroke(width = stroke, cap = StrokeCap.Round))
                    rr(s * 0.22f, s * 0.46f, s * 0.78f, s * 0.88f, stroke, c)
                    line(s * 0.5f, s * 0.88f, s * 0.5f, s * 0.64f, stroke, c)
                }

                SettingIcon.Alarm -> {
                    // 钟形：顶上小圆 + 两侧竖边 + 底边，再挂一颗铃舌。
                    dot(s * 0.5f, s * 0.2f, stroke * 0.5f, c)
                    line(s * 0.26f, s * 0.6f, s * 0.26f, s * 0.4f, stroke, c)
                    line(s * 0.74f, s * 0.6f, s * 0.74f, s * 0.4f, stroke, c)
                    line(s * 0.26f, s * 0.6f, s * 0.74f, s * 0.6f, stroke, c)
                    line(s * 0.5f, s * 0.6f, s * 0.5f, s * 0.82f, stroke, c)
                }

                SettingIcon.Dumbbell -> {
                    line(s * 0.3f, s * 0.5f, s * 0.7f, s * 0.5f, stroke, c)
                    rr(s * 0.08f, s * 0.26f, s * 0.3f, s * 0.74f, stroke, c)
                    rr(s * 0.7f, s * 0.26f, s * 0.92f, s * 0.74f, stroke, c)
                }

                // ---- 2026-11-07 五项新增设置：两枚新线稿 -------------------
                SettingIcon.Moon -> {
                    // 粗弧月牙：一条 270° 的弧 + 很粗的描边，读起来就是缺了右边一块的月亮。
                    // 用「粗弧」而不是「两个圆相减」：相减要拿卡面底色去盖，
                    // 而设置行在玻璃底上，底色不是恒定的 surface，盖出来会有一个色斑。
                    drawArc(
                        color = c,
                        startAngle = -35f,
                        sweepAngle = 290f,
                        useCenter = false,
                        topLeft = Offset(s * 0.17f, s * 0.13f),
                        size = Size(s * 0.66f, s * 0.74f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = s * 0.21f,
                            cap = StrokeCap.Round,
                        ),
                    )
                }

                SettingIcon.Broom -> {
                    // 柄：右上到中
                    line(s * 0.86f, s * 0.10f, s * 0.52f, s * 0.44f, stroke, c)
                    // 刷头：一个斜四边形（填充）
                    val head = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.46f, s * 0.34f)
                        lineTo(s * 0.72f, s * 0.60f)
                        lineTo(s * 0.38f, s * 0.92f)
                        lineTo(s * 0.12f, s * 0.66f)
                        close()
                    }
                    drawPath(head, c)
                    // 两笔刷毛，压在下沿
                    line(s * 0.24f, s * 0.74f, s * 0.40f, s * 0.90f, stroke * 0.7f, c)
                }

                SettingIcon.Eye -> {
                    // 上下两段二次贝塞尔合成梭形眼眶，中间一颗实心瞳孔。
                    val lens = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.07f, s * 0.5f)
                        quadraticBezierTo(s * 0.5f, s * 0.10f, s * 0.93f, s * 0.5f)
                        quadraticBezierTo(s * 0.5f, s * 0.90f, s * 0.07f, s * 0.5f)
                        close()
                    }
                    drawPath(lens, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
                    drawCircle(c, radius = s * 0.12f, center = Offset(s * 0.5f, s * 0.5f))
                }

                SettingIcon.Sun -> {
                    drawCircle(c, radius = s * 0.20f, center = Offset(s * 0.5f, s * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
                    // 八道短光：内 0.31 / 外 0.45，四正四斜。
                    for (i in 0 until 8) {
                        val a = Math.toRadians((i * 45).toDouble())
                        val cx = kotlin.math.cos(a).toFloat()
                        val cy = kotlin.math.sin(a).toFloat()
                        line(s * (0.5f + cx * 0.31f), s * (0.5f + cy * 0.31f), s * (0.5f + cx * 0.45f), s * (0.5f + cy * 0.45f), stroke, c)
                    }
                }

                SettingIcon.Screen -> {
                    rr(s * 0.1f, s * 0.16f, s * 0.9f, s * 0.7f, stroke, c)
                    val play = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.43f, s * 0.32f)
                        lineTo(s * 0.66f, s * 0.43f)
                        lineTo(s * 0.43f, s * 0.54f)
                        close()
                    }
                    drawPath(play, c)
                    line(s * 0.34f, s * 0.86f, s * 0.66f, s * 0.86f, stroke, c)
                    line(s * 0.5f, s * 0.7f, s * 0.5f, s * 0.86f, stroke, c)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.dot(
    cx: Float, cy: Float, r: Float, color: Color,
) = drawCircle(color, r, Offset(cx, cy))

private fun androidx.compose.ui.graphics.drawscope.DrawScope.line(
    x1: Float, y1: Float, x2: Float, y2: Float, stroke: Float, color: Color,
) = drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke, cap = StrokeCap.Round)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.ring(
    cx: Float, cy: Float, r: Float, stroke: Float, color: Color,
) = drawCircle(color, r, Offset(cx, cy), style = Stroke(width = stroke))

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rr(
    l: Float, t: Float, r: Float, b: Float, stroke: Float, color: Color,
) = drawRoundRect(
    color = color,
    topLeft = Offset(l, t),
    size = Size(r - l, b - t),
    cornerRadius = CornerRadius((r - l).coerceAtMost(b - t) * Glyph.INNER_RECT_CORNER_RATIO),
    style = Stroke(width = stroke),
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.arc(
    l: Float, t: Float, w: Float, h: Float, stroke: Float, color: Color,
) = drawArc(
    color = color,
    startAngle = 200f,
    sweepAngle = 140f,
    useCenter = false,
    topLeft = Offset(l, t),
    size = Size(w, h),
    style = Stroke(width = stroke, cap = StrokeCap.Round),
)

/**
 * 一行设置项。`trailing` 放 chevron 或开关；副标题走小灰字（规范 §12）。
 *
 * ★ 选中态**只用颜色不用字重**（规范红线：改字重会让整排字横向抖动）。
 */
@Composable
fun SettingRow(
    icon: SettingIcon,
    tint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /**
     * 有 `trailing` 时**默认不画 chevron** —— 开关和「清除」这种自带语义的尾件旁边
     * 再塞一个 `›` 就是两个箭头抢话（`节次时间表` 那行自己画了 `▾`，也不能被顶出一个 `›`）。
     * 但「状态 + 时间」是纯信息尾件，不表达可点，所以账号那几行显式要回 chevron：
     * 六行里有一行不可点，看不出区别就会被当成按坏了（v3 #8 复审）。
     */
    trailingChevron: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = title) { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ★ 2026-11-07：38dp 的彩色磁贴退役，换成 26dp 的**留白格**（图标 20dp 居中）。
        //   行内距 14dp + 26dp + 12dp ⇒ 文字列左缘 52dp，与 v2.1 稿的能量到的对齐一致
        //   （`screens/settings.png` 卡面 x=20、文字列 x=79 ⇒ (79-20)×0.886 ≈ 52dp）。
        SettingGlyph(icon = icon, tint = tint, modifier = Modifier.size(Glyph.rowSlot))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = Type.body,
                color = colors.onBackground,
            )
            Text(
                text = subtitle,
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
                // ★ 截了要看得出来（与课表那几处同规矩）：`Clip` 是**静悄悄少字**，
                //   业主不会知道这一行还有后半句。
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        // 文字列与尾件之间必须留缝：`weight(1f)` 只保证文字列吃掉剩余宽度，
        // 副标题写满时会一路顶到右侧状态字上，两句粘成一句（03:04 模拟器像素）。
        // 只在**真有尾件**时加，否则没有尾件的行右边凭空多 10dp。
        when {
            trailing != null -> {
                Spacer(Modifier.width(10.dp))
                trailing()
                // chevron 走**固定宽度的槽**，不随有无伸缩：上一版直接 `if (有箭头) Text("›")`，
                // 量到带箭头的五行「未登录」右边缘在 963px、不带箭头那行在 999px ——
                // 同一列差 13.7dp，第二行明显凸出去一块（RUBRIC 必查 7 对齐漂移）。
                //
                // ★ 但这个槽**只给显式要它的行**（账号那六行）。把它无条件挂在
                // `trailing != null` 上，全页 12 处带尾件的行会被一起推内 20dp：
                // 主题的「✓」从贴边 963..999 搬到 910..946，而同组「外观与壁纸」的「›」
                // 走下面那条分支仍在 985..997 ⇒ 同一组里 ✓ 与 › 差 19.4dp，
                // 比它要修的 13.7dp 还大（第 3 轮复审计量出来的）。
                if (trailingChevron) {
                    Box(
                        modifier = Modifier.width(ChevronSlot),
                        // 右对齐：槽是 20dp 的占位盒，箭头墨迹要落在**其它组 › 所在的那根线上**
                        // （居中会把账号组的 › 留在 981，而「外观与壁纸」的 › 在 997 ⇒ 6.1dp 跨组内缩）。
                        // antd RN 的 `arrow="empty"` 也是同一手法：占同一只盒子的同一个位置，只是不画。
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (onClick != null) {
                            Text(text = "›", fontSize = Type.title, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }

            onClick != null -> {
                Spacer(Modifier.width(10.dp))
                Text(text = "›", fontSize = Type.title, color = colors.onSurfaceVariantSummary)
            }
        }
    }
}

/**
 * 尾件右边那只 chevron 的槽宽；**这一组里画与不画都占这么多**，右边缘才不会两档
 * （antd RN 的 `List.Item arrow="empty"` 是同一手法）。
 * ★ 只有显式传 `trailingChevron = true` 的行才保留这个槽 —— 见 [SettingRow] 里那段注释。
 * ★ 改这个宽度或改挂它的位置，必须回装机图量一遍右侧各列的 x（本文件这两轮就是靠量图才改对的：
 *   轮 2 量出组内 13.7dp 漂移，轮 3 量出无条件挂槽把全页「✓」推内 20dp）。
 */
internal val ChevronSlot = 20.dp

/**
 * 分组标题前的正文图标尺寸（**13dp**）。
 *
 * 比正文条目的 [Mark.size]（15dp）小一档：组标题的字是 `Type.summary`（12sp），
 * 图标若按 15dp 画会比字还高，两个东西并排时读起来像两颗头。
 * 与 [Mark.rowSize]（16dp，列表条目里用）同属「图标跟字走」的一族。
 */
internal val GroupLabelMarkSize = 13.dp

/** 分组卡：参照站那种"一个圆角容器里塞几行"。 */
@Composable
fun SettingGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.cardCorner * 1.6f))
            .background(colors.onBackground.copy(alpha = 0.05f))
            .padding(vertical = 2.dp),
    ) {
        content()
    }
}

/** 分组之间的小标题。 */
@Composable
fun SettingSectionLabel(text: String, icon: MarkIcon? = null) {
    // 分组标题前的正文图标（2026-11-07 起）：与首页「今天 / 要处理 / 校园跑」、
    // 消息页「需要处理 / 门户通知 / 新闻」用的是同一套 [MarkIcon]，不是另造一个。
    // 组标题是**低对比度的路标**，不是标题，所以图标跟着文字一起走 quiet 色，
    // 不加磁贴、不加大——它只需要让人一眼分出「账号 / 外观 / 课表 / 关于」四堆。
    val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        // 左边不再多缩 6dp：全站正文列是 16dp（板 18px），组标题必须同一条基线
        modifier = Modifier.fillMaxWidth().padding(start = 0.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            BodyMark(
                icon = icon,
                tint = color,
                modifier = Modifier.markSemantics(icon),
                // 13dp 比正文图标的 15dp 小一档：组标题的字是 12sp，
                // 图标若按 15dp 画会比字还高，读起来像两颗并排的头。
                size = GroupLabelMarkSize,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = Type.summary,
            color = color,
        )
    }
}

/**
 * 自绘开关（**46×28**；圆钮 22dp，见 `Glyph.knob`）。
 *
 * 不用 Miuix `Switch`：签名没在产物级核过，猜错会静默不生效；这颗自己画 20 行就够，
 * 而且颜色跟设置页那套图标一致。
 */
@Composable
fun PillSwitch(checked: Boolean, label: String, onToggle: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val knob by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = 900f,
        ),
        label = "pillKnob",
    )
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(if (checked) colors.primary else colors.onBackground.copy(alpha = 0.14f))
            .clickable(onClickLabel = label) { onToggle() }
            .semantics { contentDescription = if (checked) "$label（已开启）" else "$label（已关闭）" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                // ★ 顺序不能反：先 padding 再 size，才是「把 22dp 的圆钮推到那个位置」。
                //   反过来写（size 后 padding）是在 22dp **内部**扣掉起始留白，
                //   滑到开态时圆钮只剩 1dp 宽 —— 真机上就是一条白竖线。
                .padding(start = 3.dp + 18.dp * knob)
                .size(Glyph.knob)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * 节次时间表编辑器：每节两栏「上课,下课」，`HH:mm`。
 *
 * 为什么让用户自己填而不是我给默认值：城院接口**没有**节次→时间的表
 * （`TronScheduleSlot` 档头那条纪律），编一组默认时间就是造数据 ——
 * 学生按我编的时间去上课是要迟到的。填了课表左列才显示时间，没填只显示节次号。
 *
 * ⚠️ 第 2026-10-19 起这行说明改过两次，都以**现在的界面**为准：
 *   课表早就没有「左列」了（五天課表板），时间是挂在**每一块课自己身上**
 *   （`PeriodClock`）。文案再写「左列」，他找不到那一列就会以为功能坏了 ——
 *   「对不上」这句抱怨有一半是这种文案造成的。
 *
 * 行数一率给到 [ROWS]：他真机的课有「第 11-12 节」，只给 8 行会**填不到那两节**，
 * 于是那两块课永远没时间（这种"少两行"的错在界面上完全看不出来）。
 *
 * 输入用 `BasicTextField`（foundation，签名稳定），不用 Miuix 输入组件：
 * 那个的参数字典我没核到产物级，猜错是"看着能打字其实没写进去"。
 */
private const val ROWS = 12

@Composable
fun PeriodTimeEditor(
    rows: List<Pair<String, String>>,
    /** 学校日历给的那一层（当占位文字显示，**不写进偏好**）。 */
    school: List<Pair<String?, String?>> = com.muyao.wanzaihyperos.data.schedule.SchoolPeriods.table,
    onChange: (List<Pair<String, String>>) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "第几行就是第几节。灰色那格是学校日历里有的时间，课表已经在用了、不用你抄一遍；" +
                "你填过的以你为准（会盖住学校那份）。留空且学校也没有 ⇒ 这块只显示第几节，不编时间。",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
        val count = maxOf(rows.size, ROWS)
        (1..count).forEach { i ->
            val cur = rows.getOrNull(i - 1) ?: ("" to "")
            val schoolStart = school.getOrNull(i - 1)?.first
            val schoolEnd = school.getOrNull(i - 1)?.second
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$i",
                    fontSize = Type.summary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.width(20.dp),
                )
                TimeField(
                    value = cur.first,
                    hint = schoolStart ?: "上课",
                    schoolHint = schoolStart != null,
                ) { v -> onChange(setRow(rows, i, v, cur.second)) }
                Spacer(Modifier.width(6.dp))
                Text(text = "–", fontSize = Type.summary, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.width(6.dp))
                TimeField(
                    value = cur.second,
                    hint = schoolEnd ?: "下课",
                    schoolHint = schoolEnd != null,
                ) { v -> onChange(setRow(rows, i, cur.first, v)) }
            }
        }
    }
}

private fun setRow(rows: List<Pair<String, String>>, index: Int, start: String, end: String): List<Pair<String, String>> {
    val out = rows.toMutableList()
    while (out.size < index) out.add("" to "")
    out[index - 1] = start to end
    return out
}

@Composable
private fun TimeField(
    value: String,
    hint: String,
    /** true = 这个占位是**学校日历给的真值**（不是「上课/下课」这种提示词），画得更淡一点。 */
    schoolHint: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            // 只留数字和冒号，最长 5 个字符（HH:mm）：不做"校验后弹错"，直接不让输错的进来
            onValueChange(raw.filter { it.isDigit() || it == ':' }.take(5))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Time),
        singleLine = true,
        cursorBrush = SolidColor(colors.primary),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = Type.summary,
            color = colors.onBackground,
        ),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .width(66.dp)
                    .clip(RoundedCornerShape(Dims.chipCorner))
                    .background(colors.onBackground.copy(alpha = 0.05f))
                    .border(
                        width = if (focused) 1.dp else 0.dp,
                        color = if (focused) colors.primary else Color.Transparent,
                        shape = RoundedCornerShape(Dims.chipCorner),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        fontSize = Type.summary,
                        color = if (schoolHint) colors.onSurfaceVariantSummary.copy(alpha = 0.45f)
                                else colors.onSurfaceVariantSummary,
                    )
                }
                inner()
            }
        },
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
    )
}

/** 展开区（设置页里"就地展开"的那块），和首页磁贴同一套动画语义。 */
@Composable
fun SettingExpand(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible) { content() }
}

/** 顶部那条渐变细线：分组卡之间的视觉呼吸，参照站有类似的分隔处理。 */
@Composable
fun SettingHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to MiuixTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                    1f to Color.Transparent,
                ),
            ),
    )
}
