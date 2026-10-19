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
enum class SettingIcon { Account, Logout, Theme, Glass, Timetable, Clock, Info, School }

@Composable
fun SettingGlyph(icon: SettingIcon, tint: Color, modifier: Modifier = Modifier) {
    // 参照站那颗彩色底 + 白色线性图形：底色用饱和色，图形用白，圆角比方块小一圈
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val s = size.minDimension
            val stroke = s * 0.11f
            val c = Color.White
            when (icon) {
                SettingIcon.Account, SettingIcon.School -> {
                    dot(s * 0.5f, s * 0.34f, s * 0.17f, c)
                    arc(s * 0.16f, s * 0.56f, s * 0.68f, s * 0.5f, stroke, c)
                }

                SettingIcon.Logout -> {
                    line(s * 0.42f, s * 0.5f, s * 0.86f, s * 0.5f, stroke, c)
                    line(s * 0.66f, s * 0.3f, s * 0.86f, s * 0.5f, stroke, c)
                    line(s * 0.66f, s * 0.7f, s * 0.86f, s * 0.5f, stroke, c)
                    line(s * 0.2f, s * 0.16f, s * 0.2f, s * 0.84f, stroke, c)
                }

                SettingIcon.Theme -> {
                    ring(s * 0.5f, s * 0.5f, s * 0.26f, stroke, c)
                    // 半填充：左半亮右半暗，一眼读出"浅色/深色"
                    drawArc(
                        color = c,
                        startAngle = 90f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(s * 0.24f, s * 0.24f),
                        size = Size(s * 0.52f, s * 0.52f),
                    )
                }

                SettingIcon.Glass -> {
                    // 水滴：一个圆 + 下面一个尖
                    dot(s * 0.5f, s * 0.42f, s * 0.24f, c)
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.28f, s * 0.52f)
                        lineTo(s * 0.5f, s * 0.88f)
                        lineTo(s * 0.72f, s * 0.52f)
                        close()
                    }
                    drawPath(p, c)
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
    cornerRadius = CornerRadius((r - l).coerceAtMost(b - t) * 0.2f),
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
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = title) { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingGlyph(icon = icon, tint = tint, modifier = Modifier.size(38.dp))
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
            )
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Text(text = "›", fontSize = Type.title, color = colors.onSurfaceVariantSummary)
        }
    }
}

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
fun SettingSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = Type.summary,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

/**
 * 自绘开关（44×26）。
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
                .size(22.dp)
                .padding(start = 3.dp + 18.dp * knob)
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
 * 输入用 `BasicTextField`（foundation，签名稳定），不用 Miuix 输入组件：
 * 那个的参数字典我没核到产物级，猜错是"看着能打字其实没写进去"。
 */
@Composable
fun PeriodTimeEditor(
    rows: List<Pair<String, String>>,
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
            text = "填了课表左列才会显示时间；留空就只显示第几节。格式 08:00。",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
        val count = if (rows.isEmpty()) 8 else rows.size
        (1..count).forEach { i ->
            val cur = rows.getOrNull(i - 1) ?: ("" to "")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$i",
                    fontSize = Type.summary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.width(20.dp),
                )
                TimeField(value = cur.first, hint = "上课") { v -> onChange(setRow(rows, i, v, cur.second)) }
                Spacer(Modifier.width(6.dp))
                Text(text = "–", fontSize = Type.summary, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.width(6.dp))
                TimeField(value = cur.second, hint = "下课") { v -> onChange(setRow(rows, i, cur.first, v)) }
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
private fun TimeField(value: String, hint: String, onValueChange: (String) -> Unit) {
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
                    Text(text = hint, fontSize = Type.summary, color = colors.onSurfaceVariantSummary)
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
