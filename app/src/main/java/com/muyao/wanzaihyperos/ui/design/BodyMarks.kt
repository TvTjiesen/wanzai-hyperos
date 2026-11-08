package com.muyao.wanzaihyperos.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 正文前图标（Body Marks）—— 正文、分区标题、列表条目前面那一枚小图形。
 *
 * ## 为什么单独一套
 *
 * 工程里此前已经有三套图形，各自解决不同的问题，但**没有一套是给「正文前」用的**：
 *
 * | 套件 | 位置 | 尺寸 | 用途 |
 * |---|---|---|---|
 * | `SettingIcon` + `SettingGlyph` | `SettingsChrome.kt` | 38dp 彩色圆角磁贴 | 设置页的行首 |
 * | `TileIcon` + `TileGlyph` | `HomeStudio.kt` | 21dp 线性 | 首页卡内的小图标底 |
 * | `ic_board_*`（24 枚矢量） | `res/drawable` | 13~23dp | 应用格 / 网页行 |
 *
 * 这一套的定位是**「读者第一眼看到的那一枚」**：分区标题左边、消息条目开头、
 * 课程行前面。它必须比上面三套**更轻**（线更细、尺寸更小、不铺底），
 * 否则每一行都顶着一块彩色方片，整页会变成格子布。
 *
 * ## 设计规则（不要随手破坏）
 *
 * 1. **统一在 24×24 的单位空间里画**，再等比缩放到实际尺寸 —— 这样同一枚图标
 *    在 14dp 的分区标题和 20dp 的空状态里长得一模一样，不会变形。
 * 2. **只用描边，不用填充**（除了 `dotAt` 的小圆点）。线宽按 [Mark.STROKE_RATIO]
 *    相对画布算，不写字面量。
 * 3. **内缩 [Mark.INSET]**：图形不贴边，这样即使外面套一层圆角底也不会切到线。
 * 4. 相邻两枚不许长得像。同一屏里出现两个语义不同的图标却几乎一样，
 *    读者会以为它们是同一个东西（这条是 `SettingIcon` 那边付过学费的教训）。
 */
enum class MarkIcon {
    /** 课表 / 整周视图。 */
    Schedule,

    /** 课程 / 一节课。 */
    Course,

    /** 待办 / 作业。 */
    Todo,

    /** 校园跑。 */
    Run,

    /** 消息 / 通知。 */
    Message,

    /** 应用 / 服务入口。 */
    App,

    /** 账号 / 人。 */
    Account,

    /** 外观 / 调色盘。 */
    Appearance,

    /** 数据 / 成绩。 */
    Data,

    /** 网页 / 联网来源。 */
    Web,

    /** 地点 / 教室。 */
    Pin,

    /** 一卡通 / 卡。 */
    Card,

    /** 图书 / 资料。 */
    Library,

    /** 外链 / 链接（稿的「GitHub」「酷安」用链环）。 */
    Link,

    /** 许可 / 书（稿的「开源许可」用摊开的书）。 */
    Book,

    /** 品牌 / 星（稿的「品牌与图标方案」用空心星）。 */
    Star,

    /** 网络。 */
    Wifi,

    /** 班车 / 交通。 */
    Bus,

    /** 预约 / 票。 */
    Ticket,

    /** 扫一扫。 */
    Scan,

    /** 提醒 / 注意。 */
    Warn,

    /** 说明 / 关于。 */
    Info,

    /** 时间 / 节次。 */
    Clock,

    /** 文件 / 证明。 */
    File,

    /** 成长 / 趋势。 */
    Growth,

    /** 搜索。 */
    Search,

    /** 报修 / 工具。 */
    Wrench,

    /** 电话 / 联系。 */
    Phone,
}

/**
 * 正文前图标的尺寸与线宽令牌。
 *
 * ⚠️ 这些值是「一枚小图形」的常数，**不是布局间距**，所以放在这里而不是 [Dims]。
 * 改了会同时影响所有调用点，这正是想要的：一处改、全局一致。
 */
object Mark {

    /** 分区标题左边那一枚的默认边长。 */
    val size: Dp = 15.dp

    /**
     * 列表条目开头那一枚（比标题略大一点，因为它旁边是两行文字）。
     *
     * 只大 1dp：再大就会跟 `TileGlyph`（21dp）撞档，整页层次反而变糊。
     */
    val rowSize: Dp = 16.dp

    /** 线宽相对边长的比例。比 `Glyph.STROKE_RATIO`(0.11) 略细，因为尺寸更小。 */
    const val STROKE_RATIO = 0.095f

    /** 图形相对画布的内缩比例（每边）。 */
    const val INSET = 0.08f

    /** 小圆点相对边长的半径（`dotAt` 用它，避免每个图标各写一个数）。 */
    const val DOT_RATIO = 0.045f

    /** 需要铺底时的托盘边长（消息条目、无图标的空状态之类）。 */
    val tile: Dp = 28.dp

    /** 托盘圆角。 */
    val tileCorner: Dp = 9.dp
}

/**
 * 画一枚正文前图标。
 *
 * @param icon 选哪一枚（见 [MarkIcon] 的注释，按语义挑，**不要按形状挑**）。
 * @param tint 线色。调用点传主题色，不要传十六进制字面量。
 * @param size 边长，默认 [Mark.size]。列表条目用 [Mark.rowSize]。
 * @param strokeScale 线宽倍率。压在深色照片上时传 1.2 让它更结实一点。
 */
@Composable
fun BodyMark(
    icon: MarkIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = Mark.size,
    strokeScale: Float = 1f,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * Mark.STROKE_RATIO * strokeScale
        val inset = s * Mark.INSET
        val w = s - inset * 2f
        drawMark(icon, left = inset, top = inset, w = w, stroke = stroke, color = tint)
    }
}

/**
 * 分区标题左边那一枚 + 标题文字的组合。
 *
 * 这一枚取代的正是板上 `.section-heading h3::before` 那条 4×14dp 的斜条 ——
 * 斜条只能表达「这里有个分区」，说不出**是哪一个**分区；
 * 换成图标之后，「今天的安排」前面是课表、「要处理」前面是待办、
 * 「校园跑」前面是跑道，扫一眼就知道下面是什么。
 *
 * @param trailing 右侧那一小块（计数、操作入口）。传 null 就不占位。
 */
@Composable
fun MarkedHeading(
    text: String,
    icon: MarkIcon,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
    val ink = tint ?: colors.primary
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        BodyMark(icon = icon, tint = ink.copy(alpha = 0.92f))
        Spacer(Modifier.width(7.dp))
        top.yukonga.miuix.kmp.basic.Text(
            text = text,
            fontSize = Type.body,
            fontWeight = Type.weightTitle,
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * 带托盘的那一枚（消息条目、网页行、空状态）。
 *
 * 托盘是不透明的淡底 + 图标本色，跟在 [BodyMark] 后面用它，
 * **不要整页都用**：只有「这一行没有别的视觉抓手」时才铺底。
 */
@Composable
fun MarkTile(
    icon: MarkIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = Mark.tile,
    glyphSize: Dp = size * 0.56f,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Mark.tileCorner))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        BodyMark(icon = icon, tint = tint, size = glyphSize)
    }
}

// ---------------------------------------------------------------------------
// 图形定义
// ---------------------------------------------------------------------------

/**
 * 在给定的方框里画一枚图标。
 *
 * 所有坐标都是**画布绝对像素**，由调用方把「0~1 的单位空间」换算好传进来；
 * 这样同一份图形描述可以在任意尺寸下复用。
 */
private fun DrawScope.drawMark(
    icon: MarkIcon,
    left: Float,
    top: Float,
    w: Float,
    stroke: Float,
    color: Color,
) {
    // 单位空间里的 (x, y) → 画布坐标。0 在 left，1 在 left + w。
    fun x(v: Float) = left + w * v
    fun y(v: Float) = top + w * v

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(
            color = color,
            start = Offset(x(x1), y(y1)),
            end = Offset(x(x2), y(y2)),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

    fun dot(cx: Float, cy: Float, r: Float = Mark.DOT_RATIO) =
        drawCircle(color, radius = w * r, center = Offset(x(cx), y(cy)))

    fun ring(cx: Float, cy: Float, r: Float) =
        drawCircle(
            color = color,
            radius = w * r,
            center = Offset(x(cx), y(cy)),
            style = Stroke(width = stroke),
        )

    // corner 是「圆角占较短边的比例」，所以乘的是 min(宽, 高) 而不是固定值。
    fun box(l: Float, t: Float, r: Float, b: Float, corner: Float = 0.08f) {
        val bw = w * (r - l)
        val bh = w * (b - t)
        drawRoundRect(
            color = color,
            topLeft = Offset(x(l), y(t)),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bw.coerceAtMost(bh) * corner),
            style = Stroke(width = stroke),
        )
    }

    fun arc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float) =
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(x(l), y(t)),
            size = Size(w * (r - l), w * (b - t)),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

    when (icon) {
        MarkIcon.Schedule -> {
            box(0.10f, 0.18f, 0.90f, 0.90f, corner = 0.22f)
            line(0.10f, 0.38f, 0.90f, 0.38f)
            line(0.30f, 0.10f, 0.30f, 0.26f)
            line(0.70f, 0.10f, 0.70f, 0.26f)
            dot(0.34f, 0.56f, 0.055f)
            dot(0.62f, 0.56f, 0.055f)
            dot(0.34f, 0.74f, 0.055f)
        }

        MarkIcon.Course -> {
            // 摊开的书：中缝 + 左右两页的弧
            line(0.50f, 0.24f, 0.50f, 0.82f)
            arc(0.10f, 0.18f, 0.52f, 0.84f, start = 268f, sweep = 152f)
            arc(0.48f, 0.18f, 0.90f, 0.84f, start = 120f, sweep = 152f)
        }

        MarkIcon.Todo -> {
            box(0.14f, 0.10f, 0.86f, 0.90f, corner = 0.24f)
            line(0.30f, 0.50f, 0.44f, 0.64f)
            line(0.44f, 0.64f, 0.72f, 0.34f)
        }

        MarkIcon.Run -> {
            ring(0.50f, 0.52f, 0.34f)
            ring(0.50f, 0.52f, 0.17f)
            dot(0.74f, 0.30f, 0.06f)
        }

        MarkIcon.Message -> {
            box(0.10f, 0.24f, 0.90f, 0.80f, corner = 0.20f)
            line(0.10f, 0.28f, 0.50f, 0.58f)
            line(0.90f, 0.28f, 0.50f, 0.58f)
        }

        MarkIcon.App -> {
            box(0.12f, 0.12f, 0.44f, 0.44f, corner = 0.30f)
            box(0.56f, 0.12f, 0.88f, 0.44f, corner = 0.30f)
            box(0.12f, 0.56f, 0.44f, 0.88f, corner = 0.30f)
            box(0.56f, 0.56f, 0.88f, 0.88f, corner = 0.30f)
        }

        MarkIcon.Account -> {
            ring(0.50f, 0.32f, 0.16f)
            arc(0.18f, 0.52f, 0.82f, 1.04f, start = 196f, sweep = 148f)
        }

        MarkIcon.Appearance -> {
            ring(0.50f, 0.50f, 0.36f)
            dot(0.36f, 0.38f, 0.05f)
            dot(0.62f, 0.36f, 0.05f)
            dot(0.66f, 0.60f, 0.05f)
        }

        MarkIcon.Data -> {
            line(0.18f, 0.86f, 0.86f, 0.86f)
            line(0.30f, 0.80f, 0.30f, 0.54f)
            line(0.52f, 0.80f, 0.52f, 0.32f)
            line(0.74f, 0.80f, 0.74f, 0.46f)
        }

        MarkIcon.Web -> {
            ring(0.50f, 0.50f, 0.36f)
            line(0.14f, 0.50f, 0.86f, 0.50f)
            arc(0.32f, 0.14f, 0.68f, 0.86f, start = 0f, sweep = 360f)
        }

        MarkIcon.Pin -> {
            ring(0.50f, 0.36f, 0.22f)
            line(0.34f, 0.52f, 0.50f, 0.88f)
            line(0.66f, 0.52f, 0.50f, 0.88f)
        }

        MarkIcon.Card -> {
            box(0.10f, 0.26f, 0.90f, 0.76f, corner = 0.20f)
            line(0.10f, 0.42f, 0.90f, 0.42f)
            line(0.24f, 0.60f, 0.50f, 0.60f)
        }

        MarkIcon.Library -> {
            line(0.18f, 0.84f, 0.86f, 0.84f)
            line(0.28f, 0.18f, 0.28f, 0.84f)
            line(0.50f, 0.24f, 0.50f, 0.84f)
            line(0.72f, 0.18f, 0.72f, 0.84f)
        }

        MarkIcon.Link -> {
            // 两个互扣的环 —— 全站唯一表示「这是个能跳出去的外链」的形状。
            ring(0.33f, 0.65f, 0.20f)
            ring(0.67f, 0.35f, 0.20f)
        }

        MarkIcon.Book -> {
            // 摊开的两页 + 一道书脊。
            box(0.12f, 0.26f, 0.50f, 0.80f, corner = 0.10f)
            box(0.50f, 0.26f, 0.88f, 0.80f, corner = 0.10f)
            line(0.50f, 0.26f, 0.50f, 0.80f)
        }

        MarkIcon.Star -> {
            // 没有 polygon 助手，就用十段折线接一颗空心五角星（外径 0.40 / 内径 0.17）。
            val cx = 0.50f
            val cy = 0.52f
            var fx = 0f
            var fy = 0f
            var px = 0f
            var py = 0f
            for (i in 0 until 10) {
                val a = Math.toRadians(-90.0 + i * 36.0)
                val r = if (i % 2 == 0) 0.40f else 0.17f
                val nx = cx + r * Math.cos(a).toFloat()
                val ny = cy + r * Math.sin(a).toFloat()
                if (i == 0) {
                    fx = nx
                    fy = ny
                } else {
                    line(px, py, nx, ny)
                }
                px = nx
                py = ny
            }
            line(px, py, fx, fy)
        }

        MarkIcon.Wifi -> {
            arc(0.16f, 0.30f, 0.84f, 0.86f, start = 205f, sweep = 130f)
            arc(0.30f, 0.46f, 0.70f, 0.92f, start = 205f, sweep = 130f)
            dot(0.50f, 0.74f, 0.06f)
        }

        MarkIcon.Bus -> {
            box(0.12f, 0.20f, 0.88f, 0.72f, corner = 0.20f)
            line(0.12f, 0.44f, 0.88f, 0.44f)
            dot(0.30f, 0.84f, 0.07f)
            dot(0.70f, 0.84f, 0.07f)
        }

        MarkIcon.Ticket -> {
            box(0.08f, 0.28f, 0.92f, 0.72f, corner = 0.28f)
            line(0.60f, 0.28f, 0.60f, 0.72f)
            dot(0.60f, 0.28f, 0.05f)
            dot(0.60f, 0.72f, 0.05f)
        }

        MarkIcon.Scan -> {
            line(0.10f, 0.30f, 0.10f, 0.14f); line(0.10f, 0.14f, 0.30f, 0.14f)
            line(0.70f, 0.14f, 0.90f, 0.14f); line(0.90f, 0.14f, 0.90f, 0.30f)
            line(0.90f, 0.70f, 0.90f, 0.86f); line(0.90f, 0.86f, 0.70f, 0.86f)
            line(0.30f, 0.86f, 0.10f, 0.86f); line(0.10f, 0.86f, 0.10f, 0.70f)
            line(0.20f, 0.50f, 0.80f, 0.50f)
        }

        MarkIcon.Warn -> {
            ring(0.50f, 0.50f, 0.38f)
            line(0.50f, 0.30f, 0.50f, 0.56f)
            dot(0.50f, 0.70f, 0.055f)
        }

        MarkIcon.Info -> {
            ring(0.50f, 0.50f, 0.38f)
            dot(0.50f, 0.32f, 0.055f)
            line(0.50f, 0.46f, 0.50f, 0.70f)
        }

        MarkIcon.Clock -> {
            ring(0.50f, 0.50f, 0.38f)
            line(0.50f, 0.50f, 0.50f, 0.28f)
            line(0.50f, 0.50f, 0.66f, 0.58f)
        }

        MarkIcon.File -> {
            line(0.24f, 0.10f, 0.60f, 0.10f)
            line(0.60f, 0.10f, 0.78f, 0.30f)
            line(0.78f, 0.30f, 0.78f, 0.90f)
            line(0.78f, 0.90f, 0.24f, 0.90f)
            line(0.24f, 0.90f, 0.24f, 0.10f)
            line(0.60f, 0.10f, 0.60f, 0.30f)
            line(0.60f, 0.30f, 0.78f, 0.30f)
        }

        MarkIcon.Growth -> {
            line(0.12f, 0.80f, 0.38f, 0.52f)
            line(0.38f, 0.52f, 0.56f, 0.68f)
            line(0.56f, 0.68f, 0.84f, 0.28f)
            line(0.84f, 0.28f, 0.66f, 0.28f)
            line(0.84f, 0.28f, 0.84f, 0.48f)
        }

        MarkIcon.Search -> {
            ring(0.44f, 0.42f, 0.28f)
            line(0.64f, 0.62f, 0.86f, 0.84f)
        }

        MarkIcon.Wrench -> {
            // 开口扳手：一段斜柄 + 头部的开口弧 + 尾部的圆孔
            line(0.26f, 0.78f, 0.62f, 0.42f)
            arc(0.46f, 0.16f, 0.88f, 0.58f, start = 200f, sweep = 230f)
            ring(0.24f, 0.80f, 0.10f)
        }

        MarkIcon.Phone -> {
            // 听筒：一条斜的听筒身 + 两端的话筒块
            box(0.16f, 0.16f, 0.38f, 0.38f, corner = 0.34f)
            box(0.62f, 0.62f, 0.84f, 0.84f, corner = 0.34f)
            line(0.36f, 0.34f, 0.66f, 0.64f)
        }
    }
}

/**
 * 把一枚 [MarkIcon] 描述成一句话，给无障碍读屏用。
 *
 * 不给的话 `uiautomator dump` 里是 `NAF="true"` —— 盲读时那一条只剩文字，
 * 而图标的语义（「这是待办，不是消息」）就丢了。
 */
fun markDescription(icon: MarkIcon): String = when (icon) {
    MarkIcon.Schedule -> "课表"
    MarkIcon.Course -> "课程"
    MarkIcon.Todo -> "待办"
    MarkIcon.Run -> "校园跑"
    MarkIcon.Message -> "消息"
    MarkIcon.App -> "应用"
    MarkIcon.Account -> "账号"
    MarkIcon.Appearance -> "外观"
    MarkIcon.Data -> "成绩"
    MarkIcon.Web -> "网页来源"
    MarkIcon.Pin -> "地点"
    MarkIcon.Card -> "一卡通"
    MarkIcon.Library -> "图书"
    MarkIcon.Link -> "外链"
    MarkIcon.Book -> "许可"
    MarkIcon.Star -> "品牌"
    MarkIcon.Wifi -> "校园网"
    MarkIcon.Bus -> "班车"
    MarkIcon.Ticket -> "预约"
    MarkIcon.Scan -> "扫一扫"
    MarkIcon.Warn -> "提醒"
    MarkIcon.Info -> "说明"
    MarkIcon.Clock -> "时间"
    MarkIcon.File -> "文件"
    MarkIcon.Growth -> "成长"
    MarkIcon.Search -> "搜索"
    MarkIcon.Wrench -> "报修"
    MarkIcon.Phone -> "联系电话"
}

/** 给图标挂无障碍描述的小工具（`contentDescription` 只能这样挂）。 */
fun Modifier.markSemantics(icon: MarkIcon): Modifier =
    this.semantics { contentDescription = markDescription(icon) }
