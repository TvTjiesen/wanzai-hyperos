package com.muyao.wanzaihyperos.ui.main.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.muyao.wanzaihyperos.data.AppearanceSettings
import com.muyao.wanzaihyperos.ui.components.WanzaiButton
import com.muyao.wanzaihyperos.ui.components.WanzaiTextButton
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.WanzaiTheme
import com.muyao.wanzaihyperos.ui.design.BackgroundPreset
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import com.muyao.wanzaihyperos.ui.home.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** A visual sample, without live repositories, animated glass or a second navigation hierarchy. */
@Composable
internal fun ThemeOverviewPreview(graph: com.muyao.wanzaihyperos.app.AppGraph, appearance: AppearanceSettings, image: LoadedWallpaper?) {
    // 用和首页相同的真实组件拼成小样：当前课程/待办/小球和布局开关一套组件，
    // 但 LiveHomePreview 的入口占位回调为空、editing=false，因此不会触发刷新、导航或学校写操作。
    CompositionLocalProvider(LocalPageActive provides false) {
        LiveHomePreview(
            graph = graph,
            preset = SkyPreset.of(appearance.sky),
            image = image,
            crop = WallpaperEntry("preview", appearance.panX, appearance.panY, appearance.zoom),
            editing = false,
            onCrop = {},
            modifier = Modifier.fillMaxSize(),
            dim = appearance.dim,
            soft = appearance.soft,
            fullPage = true,
            scrollable = false,
            appearance = appearance,
        )
    }
}

/** 与 `HomeLayoutPage` 的模块名保持一致；未知 id 直接回显，避免静默丢信息。 */
private fun moduleLabel(id: String): String = when (id) {
    "courses" -> "课程"
    "todos" -> "待办"
    "rollcall" -> "签到"
    "szty" -> "校园跑"
    else -> id
}

@Composable
private fun WallpaperCanvas(appearance: AppearanceSettings, image: LoadedWallpaper?, modifier: Modifier) {
    if (appearance.container == "bare") {
        ThemePageBackground(modifier, dark = false, preset = BackgroundPreset.of(appearance.background))
        return
    }
    val selected = SkyPreset.of(appearance.sky)
    val preset = if (selected == SkyPreset.ALBUM && image == null) SkyPreset.AUTO else selected
    CompositionLocalProvider(LocalPageActive provides false) {
        HomeSky(preset, image?.bitmap, appearance.dim, appearance.soft, modifier,
            panX = appearance.panX, panY = appearance.panY, zoom = appearance.zoom,
            hourOverride = 12, backgroundPreset = BackgroundPreset.of(appearance.background))
    }
}

@Composable
internal fun WallpaperEditor(initial: AppearanceSettings, image: LoadedWallpaper?, onCancel: () -> Unit, onDone: (AppearanceSettings) -> Unit, originBounds: androidx.compose.ui.geometry.Rect? = null, sourceIsValid: () -> Boolean = { true }) {
    val saver = Saver<AppearanceSettings, String>(save = { Json.encodeToString(it) },
        restore = { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<AppearanceSettings>(it).validated() }.getOrNull() })
    var draft by rememberSaveable(stateSaver = saver) { mutableStateOf(initial) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var gestureRotation by rememberSaveable { mutableFloatStateOf(initial.customization.wallpaperRotation.toFloat()) }
    var finishing by remember { mutableStateOf(false) }
    val latestDraft by rememberUpdatedState(draft)
    val finish by rememberUpdatedState(onDone)
    val cancel by rememberUpdatedState(onCancel)
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val move = motion.canMove && !motion.highContrast
    val transition = remember { androidx.compose.animation.core.Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AppearanceSettings?>(null) }
    var previewBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val capturedOrigin = remember { originBounds }
    var dialogBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var initialDialogBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var initialOrientation by remember { mutableIntStateOf(-1) }
    fun dismiss() { if (!finishing) { finishing = true; closing = true } }
    LaunchedEffect(closing, move) {
        if (!closing) androidx.compose.runtime.snapshotFlow { previewBounds to dialogBounds }
            .first { (preview, window) -> preview?.let { it.width > 0f && it.height > 0f } == true && window?.let { it.width > 0f && it.height > 0f } == true }
        val target = if (closing) 0f else 1f
        if (move && (closing || previewBounds != null)) transition.animateTo(target,
            androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
        else transition.snapTo(target)
        if (closing) result?.let(finish) ?: cancel()
    }
    val bitmap = image?.bitmap
    val photo = draft.sky == "album" && bitmap != null
    fun save() { if (!finishing) { result = latestDraft; finishing = true; closing = true } }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let {
                it.setWindowAnimations(0)
                it.setDimAmount(0f)
                it.setBackgroundDrawableResource(android.R.color.transparent)
                it.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        BackHandler { dismiss() }
        WanzaiTheme(AppThemeMode.Light, appearance = draft.copy(theme = "Light")) {
            val colors = MiuixTheme.colorScheme
            val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
            val window = dialogBounds
            val capturedWindow = initialDialogBounds
            val geometryUsable = originBounds == capturedOrigin && (capturedOrigin == null || sourceIsValid()) && window != null && capturedWindow != null &&
                motionSourceVisible(originBounds, window) && motionScreenWindowMatches(capturedWindow, window, initialOrientation, orientation)
            Column(Modifier.fillMaxSize().wallpaperMotionBounds {
                if (initialDialogBounds == null) { initialDialogBounds = it; initialOrientation = orientation }
                dialogBounds = it
            }.graphicsLayer { alpha = transition.value.coerceIn(0f, 1f) }
                .background(colors.background.copy(alpha = transition.value.coerceIn(0f, 1f)))
                .systemBarsPadding().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().graphicsLayer { alpha = transition.value }, verticalAlignment = Alignment.CenterVertically) {
                    WanzaiTextButton("取消", onClick = ::dismiss, enabled = !finishing)
                    Text("壁纸编辑", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    WanzaiButton(onClick = ::save, enabled = !finishing, prominent = true) { Text("完成") }
                }
                Box(Modifier.fillMaxWidth().weight(1f)
                    .wallpaperMotionBounds { previewBounds = it }) {
                  Box(Modifier.fillMaxSize().graphicsLayer {
                        val destination = previewBounds
                        val source = originBounds
                        val p = transition.value.coerceIn(0f, 1f)
                        if (move && geometryUsable && source != null && destination != null && motionSourceVisible(destination, destination)) {
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                            translationX = (source.left - destination.left) * (1f - p)
                            translationY = (source.top - destination.top) * (1f - p)
                            scaleX = source.width / destination.width + (1f - source.width / destination.width) * p
                            scaleY = source.height / destination.height + (1f - source.height / destination.height) * p
                        } else alpha = p
                    }.clip(RoundedCornerShape(24.dp))) {
                    WallpaperCanvas(draft.copy(container = "designed"), image, Modifier.fillMaxSize())
                    if (photo && bitmap != null) Box(Modifier.fillMaxSize().pointerInput(bitmap) {
                        detectTransformGestures { _, pan, scale, rotation ->
                            val current = latestDraft
                            gestureRotation = (gestureRotation + rotation) % 360f
                            val degrees = normalizeWallpaperRotation(gestureRotation)
                            val nextZoom = (current.zoom * scale).coerceIn(1f, 4f)
                            val geometry = wallpaperGeometry(bitmap.width, bitmap.height,
                                size.width.toFloat(), size.height.toFloat(), nextZoom, degrees.toFloat())
                            val radians = Math.toRadians(degrees.toDouble())
                            val localX = pan.x * kotlin.math.cos(radians).toFloat() + pan.y * kotlin.math.sin(radians).toFloat()
                            val localY = -pan.x * kotlin.math.sin(radians).toFloat() + pan.y * kotlin.math.cos(radians).toFloat()
                            draft = current.copy(zoom = nextZoom,
                                panX = (current.panX + if (geometry.slackX > 1f) localX / geometry.slackX else 0f).coerceIn(-1f, 1f),
                                panY = (current.panY + if (geometry.slackY > 1f) localY / geometry.slackY else 0f).coerceIn(-1f, 1f),
                                customization = current.customization.copy(wallpaperRotation = degrees))
                        }
                    })
                    if (grid) Canvas(Modifier.fillMaxSize()) {
                        for (i in 1..2) {
                            drawLine(Color.White.copy(alpha = .7f), Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = .7f), Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1.dp.toPx())
                        }
                    }
                  }
                }
                Text(if (photo) "单指移动 · 双指缩放与旋转 · 完成后应用" else "内置天空支持效果调整；原画或相册图片还支持移动、缩放与旋转", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                LazyColumn(Modifier.weight(1f).graphicsLayer { alpha = ((transition.value - .25f) / .75f).coerceIn(0f, 1f) }, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppearanceChoice("居中裁满", false, photo, Modifier.weight(1f)) { draft = draft.copy(panX = 0f, panY = 0f, zoom = 1f) }
                            AppearanceChoice("旋转 ${draft.customization.wallpaperRotation}°", false, photo, Modifier.weight(1f)) {
                                gestureRotation = (draft.customization.wallpaperRotation + 90f) % 360f
                                draft = draft.copy(customization = draft.customization.copy(wallpaperRotation = normalizeWallpaperRotation(gestureRotation)))
                            }
                        }
                    }
                    if (photo) {
                        item { EditorSlider("旋转 · ${draft.customization.wallpaperRotation}°", draft.customization.wallpaperRotation, 359) { gestureRotation = it.toFloat(); draft = draft.copy(customization = draft.customization.copy(wallpaperRotation = it)) } }
                        item { EditorSlider("放大 · ${"%.2f".format(draft.zoom)} 倍", ((draft.zoom - 1f) * 100).roundToInt(), 300) { draft = draft.copy(zoom = 1f + it / 100f) } }
                        item { EditorSlider("水平位置", ((draft.panX + 1f) * 100).roundToInt(), 200) { draft = draft.copy(panX = it / 100f - 1f) } }
                        item { EditorSlider("垂直位置", ((draft.panY + 1f) * 100).roundToInt(), 200) { draft = draft.copy(panY = it / 100f - 1f) } }
                    }
                    item { EditorSlider("模糊 · ${draft.customization.wallpaperBlur} dp", draft.customization.wallpaperBlur, 32) { draft = draft.copy(customization = draft.customization.copy(wallpaperBlur = it)) } }
                    item { EditorSlider("明暗 · ${draft.customization.wallpaperLightness}", draft.customization.wallpaperLightness + 80, 160) { draft = draft.copy(customization = draft.customization.copy(wallpaperLightness = it - 80)) } }
                    item { EditorSlider("柔化 · ${draft.soft}%", draft.soft, 100) { draft = draft.copy(soft = it) } }
                    item { EditorSlider("深色天空压暗 · ${draft.dim}%", draft.dim, 80) { draft = draft.copy(dim = it) } }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppearanceChoice("构图网格", grid, modifier = Modifier.weight(1f)) { grid = !grid }
                            AppearanceChoice("重置效果", false, modifier = Modifier.weight(1f)) {
                                gestureRotation = 0f
                                draft = draft.copy(dim = 0, soft = 0, panX = 0f, panY = 0f, zoom = 1f,
                                    customization = draft.customization.copy(wallpaperBlur = 0, wallpaperLightness = 0, wallpaperRotation = 0))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSlider(label: String, value: Int, maximum: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp)
        ValueSlider(value, maximum, onChange = onChange, onPreview = onChange)
    }
}
