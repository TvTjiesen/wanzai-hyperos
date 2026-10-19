package com.muyao.wanzaihyperos.ui.home

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.Calendar
import kotlin.math.sin

/**
 * 首页新排版用的三件套：[HomeHero]（超大日期 + 每日一句）、[HomeWindow]（可交互视窗）、
 * [HomeTile]（就地展开的功能磁贴）。业主 2026-10-18 的原话拆成这三块。
 */
private val MORNING = 5..10

/**
 * 「今天」区：超大日期 + 星期 + 每日一句。
 *
 * ## 为什么文案是本地池而不是接口
 *
 * 学校没有「每日一句」接口；为一句励志语新增网络依赖，代价是**断网时首页最上面
 * 就出现一个错误块** —— 那是最难看的失败位置。所以本地池 + 按日期确定性挑选：
 * 同一天永远同一句（不随机，随机会让「今天那句话」失去意义），换天才变。
 */
@Composable
fun HomeHero(modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val cal = remember { Calendar.getInstance() }
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val month = cal.get(Calendar.MONTH) + 1
    val weekLabel = WEEKDAYS.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "" }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..18 -> "下午好"
        in 19..22 -> "晚上好"
        else -> "夜深了"
    }
    val line = QUOTES[(dayOfMonth + month * 3) % QUOTES.size]

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = dayOfMonth.toString(),
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                lineHeight = 80.sp,
            )
            Column(modifier = Modifier.padding(bottom = 13.dp, start = 10.dp)) {
                Text(
                    text = "$month 月 · $weekLabel",
                    fontSize = Type.title,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                )
                Text(
                    text = greeting,
                    fontSize = Type.summary,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = line,
            fontSize = Type.secondary,
            color = colors.onSurfaceVariantSummary,
            maxLines = 2,
        )
    }
}

/**
 * 「视窗」：一块会动的窗。
 *
 * 1. **内置场景**：天空配色与太阳/月亮位置跟着**真实时间**走（清晨偏金、正午偏蓝、
 *    黄昏偏橙、夜里深蓝带星），下面两层山脊。
 * 2. **视差拖拽**：横着拖，远山几乎不动、近山偏得多 —— 是"挪头看窗外"的手感，
 *    不是"挪那张图"。松手弹回原位。
 * 3. **点开放大 / 再点收回**：宽高比在**它自己身上**变化，收回时回到原来那块矩形
 *    （业主要求的「从哪来到哪去」）。
 * 4. **自定义壁纸**：传 [wallpaper] 后画用户的图，同样吃视差与放大。
 *
 * 零新依赖、零网络：静态图不叫"可交互"，而内置素材走网络会变成"断网即空块"。
 */
@Composable
fun HomeWindow(
    modifier: Modifier = Modifier,
    wallpaper: ImageBitmap? = null,
    onPickWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    var expanded by remember { mutableStateOf(false) }

    // 只有一个数：拖动时=手指位置，松手时=0，由 animateFloatAsState 负责回弹。
    var parallaxTarget by remember { mutableFloatStateOf(0f) }
    val parallax by animateFloatAsState(
        targetValue = parallaxTarget,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "windowParallax",
    )

    // 极慢的呼吸：不碰它也是活的（光晕起伏）。14 秒一个来回，慢到不会打扰，快到能察觉。
    val trans = rememberInfiniteTransition(label = "windowBreath")
    val breath by trans.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (expanded) 0.72f else 1.62f)
            .clip(RoundedCornerShape(Dims.cardCorner * 2))
            .background(if (isDark) Color(0xFF101014) else Color(0xFFEFF1F6))
            .pointerInput(expanded) { detectTapGestures { expanded = !expanded } }
            .pointerInput(expanded) {
                val widthPx = size.width.toFloat().coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragEnd = { parallaxTarget = 0f },
                    onDragCancel = { parallaxTarget = 0f },
                ) { _, dragAmount ->
                    parallaxTarget = (parallaxTarget + dragAmount / widthPx * 2f).coerceIn(-1f, 1f)
                }
            }
            .semantics {
                contentDescription = if (expanded) "视窗已放大，点一下收回" else "今日视窗，可拖动、可点按放大"
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (wallpaper != null) drawBitmapCover(wallpaper, parallax)
            else drawSkyScene(hour = hour, parallax = parallax, breath = breath, dark = isDark)
        }

        Text(
            text = if (wallpaper != null) "我的壁纸 · 拖动看视差" else "此刻的窗外 · 拖动看视差",
            fontSize = Type.caption,
            color = Color.White.copy(alpha = 0.88f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(Dims.chipCorner))
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )

        Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            WindowChip(label = if (wallpaper == null) "换壁纸" else "还原") {
                if (wallpaper == null) onPickWallpaper() else onClearWallpaper()
            }
            Spacer(Modifier.width(6.dp))
            WindowChip(label = if (expanded) "收起" else "放大") { expanded = !expanded }
        }
    }
}

@Composable
private fun WindowChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = Type.caption,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClickLabel = label) { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * 磁贴：一块**就地展开**的功能卡。
 *
 * 「从哪来到哪去」在这里的具体做法：展开/收起都发生在这块磁贴自己身上
 * （它自己长高，详情从它下面滑出），不弹一个和它没有位置关系的对话框，
 * 视线不用跳。收起同理回到原位。
 */
@Composable
fun HomeTile(
    label: String,
    value: String,
    icon: TileIcon,
    tint: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    detail: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.cardCorner * 1.4f))
            .background(tint.copy(alpha = if (expanded) 0.22f else 0.13f))
            .clickable(onClickLabel = if (expanded) "收起$label" else "展开$label") { onToggle() }
            .padding(Dims.cardPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileGlyph(icon = icon, tint = tint, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = Type.summary,
                    color = colors.onSurfaceVariantSummary,
                )
                Text(
                    text = value,
                    fontSize = Type.title,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                    maxLines = 1,
                )
            }
            Text(
                text = if (expanded) "▾" else "▸",
                fontSize = Type.secondary,
                color = tint,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 10.dp)) { detail() }
        }
    }
}

/**
 * 磁贴上的小图标：**全部 Canvas 自绘**。
 *
 * 不用 MiuixIcons：要的那几个语义对不齐，混两套图标会显得是两套系统；
 * 自绘还能保证描边粗细与圆角跟底栏那套一致。
 */
enum class TileIcon { Timetable, Todo, Messages, Rollcall, Calendar, Apps }

@Composable
fun TileGlyph(icon: TileIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.09f
        val pad = size.minDimension * 0.18f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val l = pad
        val t = pad
        when (icon) {
            TileIcon.Timetable -> {
                rr(l, t, l + w, t + h, stroke, tint)
                line(l, t + h * 0.3f, l + w, t + h * 0.3f, stroke, tint)
                line(l + w * 0.35f, t, l + w * 0.35f, t + h * 0.3f, stroke, tint)
                line(l + w * 0.65f, t, l + w * 0.65f, t + h * 0.3f, stroke, tint)
            }

            TileIcon.Todo -> {
                rr(l, t, l + w, t + h, stroke, tint, 0.28f)
                line(l + w * 0.2f, t + h * 0.55f, l + w * 0.42f, t + h * 0.75f, stroke, tint)
                line(l + w * 0.42f, t + h * 0.75f, l + w * 0.82f, t + h * 0.3f, stroke, tint)
            }

            TileIcon.Messages -> {
                rr(l, t + h * 0.1f, l + w, t + h * 0.8f, stroke, tint, 0.3f)
                dot(l + w * 0.3f, t + h * 0.45f, stroke * 0.7f, tint)
                dot(l + w * 0.5f, t + h * 0.45f, stroke * 0.7f, tint)
                dot(l + w * 0.7f, t + h * 0.45f, stroke * 0.7f, tint)
            }

            TileIcon.Rollcall -> {
                dot(l + w * 0.5f, t + h * 0.3f, w * 0.19f, tint)
                drawArc(
                    color = tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(l + w * 0.16f, t + h * 0.62f),
                    size = Size(w * 0.68f, h * 0.76f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            TileIcon.Calendar -> {
                rr(l, t + h * 0.16f, l + w, t + h, stroke, tint)
                line(l + w * 0.28f, t, l + w * 0.28f, t + h * 0.3f, stroke, tint)
                line(l + w * 0.72f, t, l + w * 0.72f, t + h * 0.3f, stroke, tint)
            }

            TileIcon.Apps -> {
                val s = w * 0.4f
                sq(l, t, s, tint)
                sq(l + w * 0.55f, t, s, tint)
                sq(l, t + h * 0.55f, s, tint)
                sq(l + w * 0.55f, t + h * 0.55f, s, tint)
            }
        }
    }
}

// ─────────────────────────── 内置场景 ───────────────────────────

private fun DrawScope.drawSkyScene(hour: Int, parallax: Float, breath: Float, dark: Boolean) {
    val w = size.width
    val h = size.height
    val (topA, botA) = skyPalette(hour)
    drawRect(brush = Brush.verticalGradient(0f to topA, 1f to botA))

    val isNight = hour < MORNING.first || hour >= 19
    // 太阳 5→19 点走一条弧；月亮 19→次日 5 点走同一条弧。
    val t = if (isNight) (((hour + 5) % 24) / 12f) else ((hour - 5f) / 14f).coerceIn(0f, 1f)
    val cx = w * (0.12f + 0.76f * t)
    val cy = h * (0.72f - 0.52f * sin((t * Math.PI).toFloat()))
    val glow = 26f + 8f * breath

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                (if (isNight) Color(0xFFCBD8FF) else Color(0xFFFFE9A8)).copy(alpha = 0.55f),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = glow * 3f,
        ),
        radius = glow * 3f,
        center = Offset(cx, cy),
    )
    drawCircle(
        color = if (isNight) Color(0xFFEFF3FF) else Color(0xFFFFD86B),
        radius = glow,
        center = Offset(cx, cy),
    )
    if (isNight) {
        // 月亮缺的那一口：用天空主色再叠一个圆，简单且不用混合模式。
        drawCircle(color = topA.copy(alpha = 0.95f), radius = glow * 0.86f, center = Offset(cx + glow * 0.42f, cy - glow * 0.28f))
        repeat(14) { i ->
            drawCircle(
                color = Color.White.copy(alpha = 0.3f + 0.4f * ((i + (breath * 3f).toInt()) % 3) / 3f),
                radius = 1.6f,
                center = Offset((i * 97f) % w, (i * 53f) % (h * 0.55f)),
            )
        }
    }

    // 两层视差：远山几乎不动，近山跟手多 —— 这就是"窗"的深度感。
    ridge(
        color = (if (dark) Color(0xFF2A3350) else Color(0xFFB9C7E6)).copy(alpha = 0.85f),
        baseY = h * 0.72f,
        amp = h * 0.10f,
        shift = parallax * w * 0.02f,
        step = w / 3f,
    )
    ridge(
        color = if (dark) Color(0xFF141A2C) else Color(0xFF7E93C4),
        baseY = h * 0.86f,
        amp = h * 0.13f,
        shift = parallax * w * 0.07f,
        step = w / 2f,
    )
}

private fun skyPalette(hour: Int): Pair<Color, Color> = when (hour) {
    in 5..7 -> Pair(Color(0xFFF6B77A), Color(0xFFFDE8C8))
    in 8..11 -> Pair(Color(0xFF7FB6F0), Color(0xFFDCEBFB))
    in 12..16 -> Pair(Color(0xFF5FA8EA), Color(0xFFCFE6FA))
    in 17..19 -> Pair(Color(0xFFE98A57), Color(0xFFFBD3A6))
    in 20..22 -> Pair(Color(0xFF2B3A63), Color(0xFF54628C))
    else -> Pair(Color(0xFF141B33), Color(0xFF2E3A5C))
}

private fun DrawScope.ridge(color: Color, baseY: Float, amp: Float, shift: Float, step: Float) {
    val path = Path().apply {
        moveTo(-step, size.height)
        lineTo(-step, baseY + amp)
        var x = -step
        var up = true
        while (x < size.width + step) {
            lineTo(x + step / 2f + shift, if (up) baseY - amp else baseY + amp)
            lineTo(x + step + shift, baseY + amp)
            x += step
            up = !up
        }
        lineTo(size.width + step, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBitmapCover(img: ImageBitmap, parallax: Float) {
    // 1.12 倍余量就是给视差留的：偏 5% 也不会露出边缘空白。
    val scale = maxOf(size.width / img.width, size.height / img.height) * 1.12f
    withTransform({
        translate(
            left = -(size.width * scale - size.width) / 2f + parallax * size.width * 0.05f,
            top = -(size.height * scale - size.height) / 2f,
        )
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawImage(img)
    }
}

// ─────────────────────────── Canvas 小画法 ───────────────────────────

private fun DrawScope.rr(
    l: Float, t: Float, r: Float, b: Float,
    stroke: Float, color: Color, radiusFrac: Float = 0.18f,
) {
    val rad = minOf(r - l, b - t) * radiusFrac
    drawRoundRect(
        color = color,
        topLeft = Offset(l, t),
        size = Size(r - l, b - t),
        cornerRadius = CornerRadius(rad, rad),
        style = Stroke(width = stroke),
    )
}

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, stroke: Float, color: Color) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.dot(cx: Float, cy: Float, r: Float, color: Color) = drawCircle(color, r, Offset(cx, cy))

private fun DrawScope.sq(l: Float, t: Float, s: Float, color: Color) =
    drawRoundRect(color, Offset(l, t), Size(s, s), CornerRadius(s * 0.3f, s * 0.3f))

// ─────────────────────────── 壁纸存取 ───────────────────────────

/**
 * 把相册里的图读成 [ImageBitmap]，并**拷进应用私有目录**。
 *
 * 为什么拷一份：`UriPermission` 不一定跨重启有效，只存 URI 会出现
 * 「昨天还有壁纸，今天只剩内置场景」。存进 files 目录最稳。
 */
fun loadWallpaperFrom(context: Context, uri: Uri): ImageBitmap? {
    val dst = wallpaperFile(context)
    val copied = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dst.outputStream().use { input.copyTo(it) }
        } != null
    }.getOrDefault(false)
    if (!copied) {
        runCatching { dst.delete() }
        return null
    }
    return decodeWallpaper(dst)
}

fun decodeWallpaper(file: File): ImageBitmap? = runCatching {
    if (!file.exists()) return null
    // 用 BitmapFactory 而不是 ImageDecoder：后者可能给出 HARDWARE 配置的 bitmap，
    // 那种不能 `asImageBitmap()`（Compose 要能读像素的软件位图）。
    // 壁纸是静态图，软件位图在这里没有任何代价。
    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
    bmp.asImageBitmap()
}.getOrNull()

fun deleteWallpaper(context: Context) {
    runCatching { wallpaperFile(context).delete() }
}

fun wallpaperFile(context: Context): File = File(context.filesDir, WALLPAPER_FILE)

private const val WALLPAPER_FILE = "home_wallpaper.img"

// ─────────────────────────── 素材 ───────────────────────────

/** 同一天永远同一句（按日期确定性挑选，不随机）。 */
private val QUOTES = listOf(
    "把今天这一节课上完，就已经比昨天多了。",
    "别急，先把眼前这件事做完。",
    "早八很难，但你每次都到了。",
    "状态不好就先学十分钟，剩下的交给惯性。",
    "你不需要很厉害才开始，但需要开始才会厉害。",
    "把计划写小一点，小到你今天就能划掉它。",
    "今天也要好好吃饭，剩下的事明天也能做。",
    "慢一点没关系，别停。",
    "会考的东西，今天看一眼就是赚。",
    "把手机放远一点，你会发现你其实做得到。",
    "你已经走到这里了，别只盯着没走的那段。",
    "先把书打开，动机会自己跟上来。",
    "这一周撑过去，下周会轻一点。",
    "认真上一节课，比焦虑一整天有用。",
    "把不会的那道题抄下来，明天它就是你的了。",
    "你不需要完美的一天，只需要一个开始。",
    "先做完，再做好。",
    "风会吹过山脊，也会把你吹到更好的位置。",
    "今天背的单词，期末会替你说谢谢。",
    "允许自己慢，但别允许自己不开始。",
    "把大目标切成今天这一小块。",
    "再坚持十分钟，通常就能坚持一小时。",
    "把该交的东西交了，心里那块石头就落地了。",
    "别怕起点低，怕的是站着不动。",
    "学不完是正常的，学一点是一点。",
    "对自己诚实一点：现在该做什么？",
    "所有的好运气，都是练习的利息。",
    "先出门，剩下的路上会想明白。",
    "今天的太阳是给早起的人的。",
    "把这周过完，你会感谢现在的自己。",
)

private val WEEKDAYS = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
