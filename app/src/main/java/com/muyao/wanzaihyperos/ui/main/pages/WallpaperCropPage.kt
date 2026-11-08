package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

    // 内容会捲到透明顶栏下面：用「已捲距离 / 一屏」驱动顶栏那层纱，
    // 否则标题会被正文压住（真机在开源许可页看到过）。
    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)
    SecondaryScaffold(
        scrollProgress = veil,
        title = "调整照片",
        subtitle = "只在这台手机上",
        onBack = onBack,
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
            Text(
                text = "这张照片只保存在本机，不会上传到任何服务器。",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))

            if (wallpaper == null) {
                // 没图就照实说，不拿一片自动天空冒充「你的图」。
                Text(
                    text = "相册里还没有存过照片。回上一页点「相册」选一张。",
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
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "应用",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            UiPrefs.saveWallpaperCrop(panX, panY, zoom)
                            onBack()
                        },
                    )
                    ActionButton(text = "取消", primary = false, modifier = Modifier.weight(1f), onClick = onBack)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "重置",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // 只重置草稿 —— 未点应用之前，盘上仍是原来那份。
                            panX = 0f
                            panY = 0f
                            zoom = 1f
                            note = "已回到居中裁满，点应用才生效。"
                        },
                    )
                    ActionButton(
                        text = "移除照片",
                        primary = false,
                        danger = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            deleteWallpaper(context)
                            UiPrefs.resetWallpaperCrop()
                            UiPrefs.saveWallpaperPreset(SkyPreset.AUTO.id)
                            UiPrefs.bumpWallpaperRev()
                            onBack()
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "一根手指拖动，两根手指缩放。取景比例与首页天空一致，这里看到的构图就是首页的构图。",
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
        else -> colors.onBackground.copy(alpha = 0.05f)
    }
    Text(
        text = text,
        fontSize = Type.body,
        fontWeight = Type.weightTitle,
        color = fg,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(Dims.containerCorner))
            .background(bg)
            .clickable(onClickLabel = text) { onClick() }
            .padding(vertical = 12.dp),
    )
}
