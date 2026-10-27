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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.toPixelMap
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
    /**
     * 給了就是一顆**直接跳轉**的磁貼：點一下進目標頁，不再就地展開。
     *
     * ★ 為什麼允許兩種磁貼并存（业主 2026-10-19：「簽點功能…做成一個簽到的磁貼
     *   點進去可以掃碼或者其他簽到方式」）：
     * 「展开看说明」适合**他还没决定要不要去**的功能（课表、教务）；
     * 而签到是**正在发生的事** —— 老师在讲台上发码的时候，
     * 多一步展开就是多一次「我是不是来不及了」。所以那颗磁贴要一步到位。
     */
    onClick: (() -> Unit)? = null,
    detail: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // 按下去要有反馈（原来的版本只有"展开后变色"，直接跳转的磁贴按下去没反应
    // 会被怀疑没点上而连按两下）。indication=null：涟漪和这块 tint 的圆角不搭。
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.cardCorner * 1.4f))
            .background(
                tint.copy(
                    alpha = when {
                        pressed -> 0.30f
                        expanded && onClick == null -> 0.22f
                        else -> 0.13f
                    },
                ),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = when {
                    onClick != null -> "打开$label"
                    expanded -> "收起$label"
                    else -> "展开$label"
                },
            ) { if (onClick != null) onClick() else onToggle() }
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
                text = when {
                    onClick != null -> "›"          // 直接进
                    expanded -> "▾"
                    else -> "▸"
                },
                fontSize = Type.secondary,
                color = tint,
            )
        }
        AnimatedVisibility(visible = expanded && onClick == null) {
            Column(Modifier.padding(top = 10.dp)) { detail() }
        }
    }
}

/**
 * 磁粘贴的小图标：**全部 Canvas 自绘**。
 *
 * 不用 MiuixIcons：要的那几个语义对不齐，混两套图标会显得是两套系统；
 * 自绘还能保证描边粗细与圆角跟底栏那套一致。
 */
enum class TileIcon {
    Timetable, Todo, Messages, Rollcall, Calendar, Apps,

    /**
     * 文件/作业图形。板上「要处理」那三格是**两种**：
     * `.task .badge` = accent-soft 底 + 勾（普通待办），`.badge.warm` = 暖底 + 文件（作业）。
     * 原来全用勾选框画：勾 = 已完成，画在未完成的作业上是自相矛盾（复评 2026-10-22）。
     */
    File,
}

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

            TileIcon.File -> {
                // 一页纸 + 两条文字横线：一眼是「有东西要交」，不是「已经勾掉」。
                // 板 `.task .badge.warm` 那一格画的就是文件，勾只留给普通待办
                //（复评 2026-10-22：未完成的事画成勾选框是自相矛盾）。
                rr(l + w * 0.12f, t, l + w * 0.88f, t + h, stroke, tint)
                line(l + w * 0.30f, t + h * 0.38f, l + w * 0.70f, t + h * 0.38f, stroke, tint)
                line(l + w * 0.30f, t + h * 0.62f, l + w * 0.58f, t + h * 0.62f, stroke, tint)
            }
        }
        }
    }

private fun DrawScope.drawSkyScene(hour: Int, parallax: Float, breath: Float, dark: Boolean) {
    // 这个函数头在 2026-10-22 凌晨被我自己的补丁脚本按花括号找结尾时切掉过一次，
    // 现在按调用点与函数体用到的自由变量重建：参数 hour/parallax/breath/dark，
    // 体里用 w/h、skyPalette(hour) 与下面的两层 ridge。行为与切掉之前一致。
    val w = size.width
    val h = size.height
    val (skyTop, skyBottom) = skyPalette(hour)
    drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)))
    // 日/月：半径随 breath 轻微起伏，就是那口「会呼吸」的呼吸感
    drawCircle(
        color = (if (dark) Color(0xFFEAF0FF) else Color(0xFFFFF3CF)).copy(alpha = 0.9f),
        radius = minOf(w, h) * (0.09f + 0.012f * breath),
        center = Offset(w * 0.74f, h * 0.24f),
    )

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
    // ★ 先写临时文件、解码成功后才原子替换。
    //   上一版直接覆盖 `home_wallpaper.img` 再解码：只要这张图读不出来，
    //   旧壁纸已经被毁掉，而外觀頁还在说「原来的壁纸没有动」—— 假安慰比报错更坏。
    val tmp = File(dst.parentFile, dst.name + ".tmp")
    val copied = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } != null
    }.getOrDefault(false)
    if (!copied) {
        runCatching { tmp.delete() }
        return null
    }
    val img = decodeWallpaper(tmp)
    if (img == null) {
        runCatching { tmp.delete() }
        return null
    }
    // 同目录下的 rename 是原子的：要么还是旧图，要么已经是完整的新图。
    val moved = runCatching {
        java.nio.file.Files.move(
            tmp.toPath(),
            dst.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        true
    }.getOrDefault(false)
    if (!moved) {
        runCatching { tmp.delete() }
        return null
    }
    return img
}

fun decodeWallpaper(
    file: File,
    /**
     * 解码上限（px）。壁纸只铺一屏，没必要把 48MP 原图整张读进内存：
     * 8000×6000 的 ARGB_8888 是 192MB，真机上会直接把进程撑爆。
     */
    maxWidth: Int = 1440,
    maxHeight: Int = 3200,
): ImageBitmap? = runCatching {
    if (!file.exists()) return null
    // 用 BitmapFactory 而不是 ImageDecoder：后者可能给出 HARDWARE 配置的 bitmap，
    // 那种不能 `asImageBitmap()`（Compose 要能读像素的软件位图）。
    // 壁纸是静态图，软件位图在这里没有任何代价。
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    // inSampleSize 只能是 2 的幂；用「缩一半仍不小于上限」的最大倍数，
    // 保证不会为了省内存把图缩到比屏幕还糊。
    //
    // ★★★ 这里原来是 `&&` —— **一个字符的雷，业主选一张手机原图就可能撞 OOM**。
    //   `&&` 要求**两边都还超上限**才继续缩，而常见 4:3 照片高永远到不了 3200：
    //     4032×3024 → 2016>=1440 ✓ 但 1512>=3200 ✗ ⇒ `inSampleSize = 1`
    //     ⇒ 4032×3024×4B = **48,771,072 B ≈ 46.5 MiB**（还只是软件位图，两份 decodeFile 更是翻倍）
    //     8000×6000 同样卡住 ⇒ **183 MiB** —— 正是上面那句注释自己写的"会把进程撑爆"那一档。
    //   改 `||`（**任一边**还超上限就继续缩）之后，4032×3024 落到 sample=2 ⇒ 2016×1512 ≈ 12.2 MiB。
    //   ⚠️ 别以为会到 1008×756：循环的语义是「**再缩一半就不小于上限才继续**」，
    //   1008 < maxWidth(1440) ⇒ 停在 2。台账那条"sample=4 / 3.1MiB"是它的算错，
    //   真正要紧的量级差是 **46.5MiB → 12.2MiB（8000×6000 那档 183MiB → 45.6MiB）**，
    //   而这条测试钉的就是"不许回到 46.5MiB 那一档"。
    //   ⚠️ 外层是 `runCatching`，OOM 会被吞成 null、界面上一个字都不说 ⇒
    //   `getOrNull()` 那条路必须配一句实话上屏（见 `AppearancePage` 的 `note` 与 `wallpaperLoadFailed`）。
    //   落带由 `HomeSkyTest` 钉住（4032×3024 / 8000×6000 两组输入 + `slack >= 0`），不留给录屏。
    var sample = 1
    while (w / (sample * 2) >= maxWidth || h / (sample * 2) >= maxHeight) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
    bmp.asImageBitmap()
}.getOrNull()

fun deleteWallpaper(context: Context) {
    runCatching { wallpaperFile(context).delete() }
}

/**
 * 壁纸的**实地亮度**（两枚，量出来的，不是猜的）—— [topBand] 是卡所在那一段、[overall] 是整张。
 *
 * ## ★ 为什么必须量（第 8 轮审计必修 7 + 我自己在模拟器上量到的那个 0.000）
 *
 * 「此刻」卡在相册档下的底色曾经用 `{黑, 白}` 两端兜"照片亮度未知"。那在字色判据放宽到
 * 三档（主/次/警示）之后会把垫色一路抬到 ≈0.9 ⇒ **液态与磨砂在卡上逐像素相同（MAD 0.000）**，
 * #12 在相册档又变回假开关；深色主题那边同样（审计复算 0.48→0.68，与磨砂只差 0.04）。
 * 而这张图**本来就在手上**（`decodeWallpaper` 刚解出来），采一遍就知道它有多亮 ——
 * "未知"只是我们没看，不是拿不到。工程红线是"没有来源就说不知道"，
 * 我们有来源（这张位图）就该去看。
 *
 * 采样按网格跳点（最多 24×24 次取像素），只在解码那一次算。
 */
data class WallpaperStats(val topBand: Color, val overall: Color)

fun wallpaperStats(img: ImageBitmap): WallpaperStats {
    val px = img.toPixelMap()
    val stepX = maxOf(1, img.width / 24)
    val stepY = maxOf(1, img.height / 24)
    var tr = 0.0; var tg = 0.0; var tb = 0.0; var tn = 0
    var ar = 0.0; var ag = 0.0; var ab = 0.0; var an = 0
    var y = 0
    while (y < img.height) {
        var x = 0
        while (x < img.width) {
            val c = px[x, y]
            ar += c.red; ag += c.green; ab += c.blue; an++
            // 卡压在画面上部（顶栏 + 大标题之下、约 15%~45% 高度那一段）
            if (y.toFloat() / img.height in 0.15f..0.45f) {
                tr += c.red; tg += c.green; tb += c.blue; tn++
            }
            x += stepX
        }
        y += stepY
    }
    fun avg(r: Double, g: Double, b: Double, n: Int) =
        if (n == 0) Color(0.5f, 0.5f, 0.5f) else Color(
            (r / n).toFloat().coerceIn(0f, 1f),
            (g / n).toFloat().coerceIn(0f, 1f),
            (b / n).toFloat().coerceIn(0f, 1f),
        )
    return WallpaperStats(
        topBand = avg(tr, tg, tb, tn),
        overall = avg(ar, ag, ab, an),
    )
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
