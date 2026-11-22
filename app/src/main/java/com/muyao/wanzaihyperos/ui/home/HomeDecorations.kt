package com.muyao.wanzaihyperos.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.LocalThemeCustomization
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun decorationImage(id: String?): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id) {
        image = if (id == null) null else try {
            loadWallpaper(WallpaperLibrary(context).file(WallpaperEntry(id)), 480, 480,
                WallpaperBudget(400_000, 1_600_000))?.bitmap
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { null }
    }
    return image
}

@Composable
fun homeCardImageModifier(): Modifier {
    val image = decorationImage(LocalThemeCustomization.current.cardImage) ?: return Modifier
    return Modifier.paint(BitmapPainter(image), sizeToIntrinsics = false, contentScale = ContentScale.Crop)
        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = .72f))
}

/** Decorative only: never replaces the real status or intercepts its controls. */
@Composable
fun HomeStickers(modifier: Modifier = Modifier, onChange: ((com.muyao.wanzaihyperos.data.HomeSticker) -> Unit)? = null) {
    val stickers = LocalThemeCustomization.current.stickers
    if (stickers.isEmpty()) return
    var guides by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    val guideColor = MiuixTheme.colorScheme.primary.copy(alpha = .55f)
    BoxWithConstraints(modifier.drawBehind {
        if (guides?.first == true) drawLine(guideColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
        if (guides?.second == true) drawLine(guideColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
    }) {
        stickers.forEach { sticker ->
            key(sticker.id) {
                val image = decorationImage(sticker.image)
                val latest by rememberUpdatedState(sticker)
                val latestChange by rememberUpdatedState(onChange)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val base = with(density) { 64.dp.toPx() }
                val parentWidth = constraints.maxWidth.toFloat()
                val parentHeight = constraints.maxHeight.toFloat()
                val frame = stickerGeometry(parentWidth, parentHeight,
                    base, sticker.scale, sticker.rotation, sticker.x, sticker.y)
                if (image != null && frame.side > 0f) Image(image, contentDescription = if (onChange == null) null else "可编辑贴纸",
                    modifier = Modifier.offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                        .size(with(density) { frame.side.toDp() })
                        .then(if (onChange == null) Modifier else Modifier.pointerInput(sticker.id, constraints.maxWidth, constraints.maxHeight) {
                            try {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var current = latest
                                    val xAxis = StickerDragAxis(current.x)
                                    val yAxis = StickerDragAxis(current.y)
                                    var passedSlop = false
                                    var totalPan = Offset.Zero
                                    var totalZoom = 1f
                                    var totalRotation = 0f
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.isConsumed }) break
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        val rotation = event.calculateRotation()
                                        if (!passedSlop) {
                                            totalPan += pan; totalZoom *= zoom; totalRotation += rotation
                                            val radius = event.calculateCentroidSize(useCurrent = false)
                                            passedSlop = totalPan.getDistance() > viewConfiguration.touchSlop ||
                                                kotlin.math.abs(1f - totalZoom) * radius > viewConfiguration.touchSlop ||
                                                kotlin.math.abs(totalRotation) * kotlin.math.PI.toFloat() / 180f * radius > viewConfiguration.touchSlop
                                        }
                                        if (passedSlop) {
                                            val nextScale = (current.scale * zoom).coerceIn(.4f, 2f)
                                            val nextRotation = ((current.rotation + rotation + 540f) % 360f) - 180f
                                            val next = stickerGeometry(parentWidth, parentHeight,
                                                base, nextScale, nextRotation, current.x, current.y)
                                            current = current.copy(
                                                x = xAxis.move(if (next.travelX > 1f) pan.x / next.travelX else 0f),
                                                y = yAxis.move(if (next.travelY > 1f) pan.y / next.travelY else 0f),
                                                scale = nextScale, rotation = nextRotation)
                                            guides = (current.x == .5f) to (current.y == .5f)
                                            latestChange?.invoke(current)
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                    guides = null
                                }
                            } finally { guides = null }
                        }).graphicsLayer { rotationZ = sticker.rotation }, contentScale = ContentScale.Fit)
            }
        }
    }
}
