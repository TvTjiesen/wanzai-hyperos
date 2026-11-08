package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.home.SkyPreset
import com.muyao.wanzaihyperos.ui.home.coverSlackPx
import com.muyao.wanzaihyperos.ui.home.decodeWallpaper
import com.muyao.wanzaihyperos.ui.home.deleteWallpaper
import com.muyao.wanzaihyperos.ui.home.drawCoverBitmap
import com.muyao.wanzaihyperos.ui.home.wallpaperFile
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A10：本地照片取景（三级页，从外观页「调整照片」进来）。
 *
 * ## 为什么要单独一页，而不是留在外观页里
 *
 * 业主 2026-11-08 的判据是「取景要能取消」。原来那个框每拖一帧就
 * `UiPrefs.saveWallpaperCrop(...)` **直接落盘** —— 拖歪了没有回头路，
 * 只能自己再拖回去。取消语义要求「编辑中的值」与「盘上的值」是两份状态，
 * 而外观页是 `LazyColumn`，滚走就会被回收，没地方摆这份草稿。
 *
 * ## 三份状态的关系
 *
 * - 进页时从 [UiPrefs] 抄一份草稿（`panX/panY/zoom`）；
 * - 拖动、缩放、重置**只改草稿**，框里的预览立刻跟着变；
 * - 点「应用」才落盘（一次写三个数），点「取消」直接返回、盘上什么都不动。
 */
@Composable
fun WallpaperCropPage(
    onBack: () -> Unit,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val rev = UiPrefs.wallpaperRev
    val wallpaper = remember(rev) { decodeWallpaper(wallpaperFile(context)) }

    var panX by remember { mutableFloatStateOf(UiPrefs.wallpaperPanX) }
    var panY by remember { mutableFloatStateOf(UiPrefs.wallpaperPanY) }
    var zoom by remember { mutableFloatStateOf(UiPrefs.wallpaperZoom) }
    var note by remember { mutableStateOf<String?>(null) }
    // 移除照片会直接删掉 files/home_wallpaper.img，没有「取消」可退 ——
    // 所以先问一句。它与节次时间表的「清除个人覆盖」用同一种二次确认。
    var confirmRemove by remember { mutableStateOf(false) }

    // 内容会捲到透明顶栏下面：用「已捲距离 / 一屏」驱动顶栏那层纱，
    // 否则标题会被正文压住（真机在开源许可页看到过）。
    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)
    SecondaryScaffold(
        scrollProgress = veil,
        title = "本地照片取景",
        onBack = onBack,
        onSearch = onSearch,
        modifier = modifier,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Dims.screenH)
                .padding(
                    top = inner.calculateTopPadding(),
                    bottom = inner.calculateBottomPadding(),
                ),
        ) {
            NoticeBlock(
                "仅本地预览，不上传。请使用无敏感内容的照片；未选择时保留上一张壁纸。",
            )
            Spacer(Modifier.height(12.dp))

            if (wallpaper == null) {
                // 没图就照实说，不拿一片自动天空冒充「你的图」。
                Text(
                    text = "选择照片后可以拖动与缩放。相册里还没有存过照片，回上一页点「相册」选一张。",
                    fontSize = Type.body,
                    color = colors.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(16.dp))
                ActionButton(text = "返回", primary = false, onClick = onBack)
            } else {
                CropEditor(
                    wallpaper = wallpaper,
                    panX = panX,
                    panY = panY,
                    zoom = zoom,
                    onCrop = { x, y, z ->
                        panX = x
                        panY = y
                        zoom = z
                    },
                )
                Spacer(Modifier.height(10.dp))
                // 手势之外再给一条能精确落点的路：双指缩放落在哪一格是手感，
                // 滑杆才是「我要 2 倍」。数值是百分比，右端 100% = 4 倍。
                Text(
                    text = "缩放（0% = 居中裁满，100% = 四倍）",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(4.dp))
                ValueSlider(
                    value = ((zoom - 1f) / 3f * 100f).toInt().coerceIn(0, 100),
                    max = 100,
                    percent = true,
                    onChange = { v -> zoom = (1f + v / 100f * 3f).coerceIn(1f, 4f) },
                )
                Spacer(Modifier.height(20.dp))
                // 稿的 .bottom-action：三颗全宽竖排，间距 10dp。
                ActionButton(
                    text = "应用取景",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        UiPrefs.saveWallpaperCrop(panX, panY, zoom)
                        onBack()
                    },
                )
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = "重置取景",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // 只重置草稿 —— 未点应用之前，盘上仍是原来那份。
                        panX = 0f
                        panY = 0f
                        zoom = 1f
                        note = "已回到居中裁满，点应用才生效。"
                    },
                )
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = "移除本地照片",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    danger = true,
                    onClick = { confirmRemove = true },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "拖动平移；滑杆缩放。Android 保留原双指手势。不点应用直接返回，这次调整就作废。",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                )
                note?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = msg, fontSize = Type.micro, color = colors.onSurfaceVariantSummary)
                }
            }
        }
    }

    if (confirmRemove) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmRemove = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Text(
                    text = "移除这张照片？",
                    fontSize = Type.title,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                )
                Text(
                    text = "首页会回到内置天空。相册里那一张不动，只是本机这份取景与照片一起清掉，要再来得重新选。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    lineHeight = Type.secondary * 1.6f,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 14.dp)) {
                    Text(
                        text = "取消",
                        fontSize = Type.secondary,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier
                            .clickable(onClickLabel = "不移除，留在这一页") { confirmRemove = false }
                            .padding(6.dp),
                    )
                    Text(
                        text = "移除",
                        fontSize = Type.secondary,
                        fontWeight = Type.weightTitle,
                        color = colors.error,
                        modifier = Modifier
                            .padding(start = 20.dp)
                            .clickable(onClickLabel = "确认移除这张照片") {
                                confirmRemove = false
                                deleteWallpaper(context)
                                UiPrefs.resetWallpaperCrop()
                                UiPrefs.saveWallpaperPreset(SkyPreset.AUTO.id)
                                UiPrefs.bumpWallpaperRev()
                                onBack()
                            }
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

/** 取景框本体：只画，不落盘（落盘由调用方在「应用」时做）。 */
@Composable
private fun CropEditor(
    wallpaper: ImageBitmap,
    panX: Float,
    panY: Float,
    zoom: Float,
    onCrop: (Float, Float, Float) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val curPanX = rememberUpdatedState(panX)
    val curPanY = rememberUpdatedState(panY)
    val curZoom = rememberUpdatedState(zoom)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / 0.62f)
            .clip(RoundedCornerShape(Dims.cardCorner))
            .background(colors.surfaceContainer)
            // key 只留 wallpaper：三个取景量走 rememberUpdatedState，
            // 既读到最新值又不重启手势协程（重启就会出现「拖到一半变黏」）。
            .pointerInput(wallpaper) {
                detectTransformGestures { _, pan, zoomIn, _ ->
                    val zoomNow = curZoom.value
                    val (sx, sy) = coverSlackPx(
                        imgW = wallpaper.width,
                        imgH = wallpaper.height,
                        boxW = size.width.toFloat(),
                        boxH = size.height.toFloat(),
                        zoom = zoomNow,
                    )
                    val nx = curPanX.value + if (sx > 0f) pan.x / sx else 0f
                    val ny = curPanY.value + if (sy > 0f) pan.y / sy else 0f
                    onCrop(nx.coerceIn(-1f, 1f), ny.coerceIn(-1f, 1f), (zoomNow * zoomIn).coerceIn(1f, 4f))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCoverBitmap(wallpaper, panX = panX, panY = panY, zoom = zoom)
        }
        // 构图辅助线：只在编辑态画轮廓，不进壁纸位图、也不落盘成开关。
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val guideColor = Color.White.copy(alpha = 0.82f)
            val shadowColor = Color.Black.copy(alpha = 0.28f)
            val stroke = 1.6.dp.toPx()

            fun ring(cx: Float, cy: Float, r: Float) {
                drawCircle(shadowColor, radius = r + 1.dp.toPx(), center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawCircle(guideColor, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            }

            fun band(l: Float, t: Float, r: Float, b: Float, corner: Float) {
                drawRoundRect(shadowColor, topLeft = Offset(l, t), size = Size(r - l, b - t),
                    cornerRadius = CornerRadius(corner), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawRoundRect(guideColor, topLeft = Offset(l, t), size = Size(r - l, b - t),
                    cornerRadius = CornerRadius(corner), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            }

            ring(w * 0.20f, h * 0.15f, w * 0.13f)
            band(w * 0.08f, h * 0.30f, w * 0.62f, h * 0.375f, 6.dp.toPx())
            band(w * 0.08f, h * 0.405f, w * 0.42f, h * 0.455f, 5.dp.toPx())
            band(w * 0.06f, h * 0.62f, w * 0.94f, h * 0.86f, 20.dp.toPx())
        }
    }
}

/** 页内按钮：主次两档，够用就不引组件库。 */
@Composable
private fun ActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val fg = when {
        danger -> colors.error
        primary -> colors.onPrimary
        else -> colors.onBackground
    }
    val bg = when {
        primary -> colors.primary
        else -> Color.Transparent
    }
    Text(
        text = text,
        fontSize = Type.body,
        fontWeight = Type.weightTitle,
        color = fg,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            // 稿 .btn.outline：次按钮只描边、透明底。
            .then(
                if (primary) Modifier else Modifier.border(
                    1.dp,
                    colors.dividerLine.copy(alpha = 0.8f),
                    RoundedCornerShape(999.dp),
                )
            )
            .clickable(onClickLabel = text) { onClick() }
            .padding(vertical = 12.dp),
    )
}

/** 稿 `.notice`：左侧 2dp 竖线 + 表面色底 + 11dp 圆角，字是 Type.caption。 */
@Composable
private fun NoticeBlock(text: String) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(colors.onSurfaceVariantSummary.copy(alpha = 0.45f)),
        )
        Text(
            text = text,
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
