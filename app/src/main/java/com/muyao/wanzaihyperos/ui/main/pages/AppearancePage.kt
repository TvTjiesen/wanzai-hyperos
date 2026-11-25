package com.muyao.wanzaihyperos.ui.main.pages

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.components.WanzaiButton
import com.muyao.wanzaihyperos.ui.components.WanzaiSwitch
import com.muyao.wanzaihyperos.ui.components.WanzaiOverlayDialog
import com.muyao.wanzaihyperos.ui.components.WanzaiSlider
import com.muyao.wanzaihyperos.ui.components.WanzaiTextButton
import com.muyao.wanzaihyperos.ui.design.*
import com.muyao.wanzaihyperos.ui.home.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.toArgb

import com.muyao.wanzaihyperos.data.AppearanceSettings
import com.muyao.wanzaihyperos.ui.WanzaiTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private class LiveAppearanceState(private val onThemeMode: (AppThemeMode) -> Unit) :
    kotlin.properties.ReadWriteProperty<Any?, AppearanceSettings> {
    override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = UiPrefs.appearanceSnapshot()
    override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: AppearanceSettings) {
        val next = value.validated()
        val previous = UiPrefs.appearanceSnapshot()
        if (next == previous) return
        UiPrefs.applyAppearance(next)
        if (next.theme != previous.theme) onThemeMode(appearanceTheme(next.theme))
    }
}

private fun appearanceTheme(name: String) = when (name) {
    "Light" -> AppThemeMode.Light
    "Dark" -> AppThemeMode.Dark
    else -> AppThemeMode.System
}

@Composable
fun AppearancePage(
    onClose: () -> Unit,
    graph: AppGraph,
    themeMode: AppThemeMode = AppThemeMode.System,
    onThemeMode: (AppThemeMode) -> Unit = {},
    onOpenCrop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var layoutEditing by rememberSaveable { mutableStateOf(false) }
    val layoutVisibility = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    SideEffect { layoutVisibility.targetState = layoutEditing }
    val layoutOverlayActive = layoutEditing || layoutVisibility.currentState || layoutVisibility.targetState
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val motion = rememberMotion()
    val scope = rememberCoroutineScope()
    val library = remember(context) { WallpaperLibrary(context) }
    var current by LiveAppearanceState(onThemeMode)
    var entries by remember { mutableStateOf<List<WallpaperEntry>>(emptyList()) }
    val wallpaperSaver = Saver<WallpaperEntry, String>(
        save = { Json.encodeToString(it) }, restore = { runCatching { Json.decodeFromString<WallpaperEntry>(it) }.getOrNull() })
    val selectedSaver = Saver<WallpaperEntry?, String>(
        save = { it?.let { entry -> Json.encodeToString(entry) } ?: "" },
        restore = { runCatching { Json.decodeFromString<WallpaperEntry>(it) }.getOrNull() })
    var selected by rememberSaveable(stateSaver = selectedSaver) { mutableStateOf<WallpaperEntry?>(null) }
    var crop by rememberSaveable(stateSaver = wallpaperSaver) { mutableStateOf(WallpaperEntry("preview", current.panX, current.panY, current.zoom)) }
    val album = current.container != "bare" && current.sky == "album"
    val wallpaperRevision = UiPrefs.wallpaperRev
    var loaded by remember(album, selected?.id, wallpaperRevision) { mutableStateOf<LoadedWallpaper?>(null) }
    var imageLoading by remember(album, selected?.id, wallpaperRevision) { mutableStateOf(album) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editorEntry by rememberSaveable(stateSaver = selectedSaver) { mutableStateOf<WallpaperEntry?>(null) }
    var editorImage by remember { mutableStateOf<LoadedWallpaper?>(null) }
    val wallpaperSources = remember { mutableMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>() }
    val sourceStamp = rememberMotionSourceStamp()
    var editorSource by remember { mutableStateOf<WallpaperEditorSource?>(null) }
    fun captureSource(key: String) = WallpaperEditorSource.capture({ wallpaperSources[key] }, { sourceStamp() })
    var editorOrigin by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<WallpaperEntry?>(null) }
    var managingWallpapers by rememberSaveable { mutableStateOf(false) }
    val closePage by rememberUpdatedState(onClose)
    fun close() { if (!busy) closePage() }
    var backProgress by remember { mutableFloatStateOf(0f) }
    BackHandler(enabled = managingWallpapers && !editing) { managingWallpapers = false }
    if (!layoutOverlayActive && !managingWallpapers) ThemeBackGesture(current.customization.predictiveBack, onBack = ::close, onProgress = { backProgress = it })
    BackHandler(enabled = layoutOverlayActive && !layoutEditing) { /* Wait for the layout page to leave. */ }
    var expandedSections by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val list = rememberLazyListState()
    val scrollProgress by rememberScrollProgress(list)
    fun toggleSection(id: String) {
        expandedSections = ArrayList(if (id in expandedSections) expandedSections - id else expandedSections + id)
    }
    suspend fun stageWallpaper(entry: WallpaperEntry) {
        val decoded = loadWallpaper(library.file(entry), 720, 1600, WallpaperBudget(1_200_000, 4_800_000))
            ?: error("Image cannot be decoded")
        currentCoroutineContext().ensureActive()
        editorEntry = entry
        editorImage = decoded
        editing = true
    }
    fun chooseWallpaper(entry: WallpaperEntry) {
        if (busy) return
        editorSource = captureSource(entry.id)
        editorOrigin = editorSource?.bounds
        busy = true
        scope.launch {
            try {
                stageWallpaper(entry)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { note = "图片未切换成功，原壁纸保留" }
            finally { busy = false }
        }
    }
    LaunchedEffect(library) {
        try { entries = library.entries() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { note = "壁纸库暂时无法读取，当前外观未改动" }
    }
    LaunchedEffect(album, selected?.id, wallpaperRevision) {
        if (!album) return@LaunchedEffect
        val entry = selected
        try {
            val decoded = loadWallpaper(
                if (entry == null) wallpaperFile(context) else library.file(entry),
                720, 1600, WallpaperBudget(1_200_000, 4_800_000),
            )
            currentCoroutineContext().ensureActive()
            if (UiPrefs.wallpaperRev != wallpaperRevision || selected?.id != entry?.id ||
                current.container == "bare" || current.sky != "album") return@LaunchedEffect
            loaded = decoded
            crop = WallpaperEntry(entry?.id ?: "preview", current.panX, current.panY, current.zoom)
            if (decoded == null) note = "图片暂时无法读取，请重新选择；当前壁纸未改动"
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { loaded = null; note = "图片暂时无法读取，当前壁纸未改动" }
        finally { imageLoading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(WallpaperLibrary.MAX_ENTRIES)) { uris ->
        if (uris.isEmpty() || busy) return@rememberLauncherForActivityResult
        editorSource = captureSource("current-preview")
        editorOrigin = editorSource?.bounds
        busy = true
        scope.launch {
            try {
                val previous = entries.map { it.id }.toSet()
                val (next, rejected) = library.addImages(uris)
                entries = next
                next.firstOrNull { it.id !in previous }?.let {
                    stageWallpaper(it)
                }
                note = if (rejected == 0) "已导入，请在编辑器中确认应用" else "已导入可用图片，未导入 $rejected 张（最多24张，每张32MB）"
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { note = "导入失败，当前壁纸保留" }
            finally { busy = false }
        }
    }
    LaunchedEffect(editing, editorEntry?.id) {
        val entry = editorEntry
        if (editing && entry != null && editorImage == null) {
            try { stageWallpaper(entry) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { editing = false; note = "图片暂时无法读取，原壁纸保留" }
        }
    }
    fun openEditor(sourceKey: String = "current-preview") {
        if (!busy) {
            managingWallpapers = false
            editorSource = captureSource(sourceKey)
            editorOrigin = editorSource?.bounds
            editorEntry = null
            editorImage = loaded
            editing = true
        }
    }
    @Composable fun Preview() {
        WanzaiTheme(AppThemeMode.Light, appearance = current.copy(theme = "Light")) {
            Box(Modifier.fillMaxSize()) {
                ThemeOverviewPreview(graph, current.copy(theme = "Light"), loaded)
                if (imageLoading) Text("正在读取本机照片…", color = MiuixTheme.colorScheme.onSurface,
                    fontSize = Type.caption, modifier = Modifier.align(Alignment.BottomCenter)
                        .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(Dims.chipCorner)).padding(8.dp))
            }
        }
    }
    Box(modifier.fillMaxSize()) {
    SecondaryScaffold(title = "主题设置", onBack = ::close, scrollProgress = scrollProgress,
        modifier = Modifier
            .then(if (layoutOverlayActive) Modifier.semantics { hideFromAccessibility() } else Modifier)
            .themeSwipeDismiss(!layoutOverlayActive && current.customization.swipeDismiss, ::close)
            .graphicsLayer { translationX = size.width * backProgress * .12f; scaleX = 1f - backProgress * .04f; scaleY = scaleX },
    ) { padding ->
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dims.screenH, 12.dp, Dims.screenH,
                com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp(com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible.current)),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "section-home") {
                ThemeExpandableSection("首页壁纸", "顶部壁纸、裁切与显示", SettingIcon.House, "home" in expandedSections, !busy, { toggleSection("home") }) {
                    ThemeSettingSegment {
                        AppearanceToggle("显示首页壁纸", "只影响首页顶部，容器背景单独选择", current.container != "bare", !busy) {
                            current = current.copy(container = if (current.container == "bare") "designed" else "bare")
                        }
                        VisualChoice("当前壁纸", "轻点编辑取景与效果", false, !busy && !imageLoading, Modifier.fillMaxWidth(), {
                            WanzaiTheme(AppThemeMode.Light, appearance = current.copy(theme = "Light")) {
                                WallpaperCanvas(current.copy(container = "designed"), loaded, Modifier.fillMaxSize())
                            }
                        }, sampleModifier = Modifier.wallpaperMotionCoordinates { wallpaperSources["current-preview"] = it }) { openEditor() }
                    }
                    AppearanceSection("系统自带壁纸") {
                        VisualChoice("晴海 · 原画", "海湾、帆船与雏菊", selected?.id == "builtin-sea", !busy, Modifier.fillMaxWidth(), {
                            Image(androidx.compose.ui.res.painterResource(com.muyao.wanzaihyperos.R.drawable.wallpaper_sea),
                                "原绘海湾壁纸", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }, sampleModifier = Modifier.wallpaperMotionCoordinates { wallpaperSources["builtin-sea"] = it }) {
                            editorSource = captureSource("builtin-sea")
                            editorOrigin = editorSource?.bounds
                            busy = true
                            scope.launch {
                                try {
                                    val entry = library.bundledSea()
                                    entries = library.entries()
                                    stageWallpaper(entry)
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { note = "原画暂时无法载入，原壁纸保留" }
                                finally { busy = false }
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(SkyPreset.entries.filter { it != SkyPreset.ALBUM }, key = { it.id }) { sky ->
                                VisualChoice(sky.label, sky.hint, current.sky == sky.id, !busy, Modifier.width(112.dp), {
                                    SkyPreview(sky, null, 0, 0, Modifier.fillMaxSize())
                                }) { selected = null; current = current.copy(sky = sky.id, container = "designed", customization = current.customization.copy(cardImage = null)) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WanzaiButton(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, enabled = !busy, prominent = true, modifier = Modifier.weight(1f)) { Text("添加壁纸") }
                            WanzaiTextButton("编辑当前壁纸", onClick = { openEditor() },
                                enabled = !busy && !imageLoading, modifier = Modifier.weight(1f))
                        }
                        if (entries.any { it.id != "builtin-sea" }) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (managingWallpapers) "我的壁纸 · 管理中" else "我的壁纸",
                                    modifier = Modifier.weight(1f), fontSize = Type.body, fontWeight = Type.weightTitle)
                                if (managingWallpapers) WanzaiTextButton("完成",
                                    onClick = { managingWallpapers = false }, enabled = !busy)
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(entries.filter { it.id != "builtin-sea" }, key = { it.id }) { entry ->
                                    Column(Modifier.width(100.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        WallpaperThumbnail(library, entry, selected?.id == entry.id, !busy && !managingWallpapers,
                                            managing = managingWallpapers,
                                            modifier = Modifier.wallpaperMotionCoordinates { wallpaperSources[entry.id] = it },
                                            onLongPress = { if (!busy) managingWallpapers = true }) {
                                            if (managingWallpapers) pendingRemoval = entry else chooseWallpaper(entry)
                                        }
                                    }
                                }
                            }
                            if (managingWallpapers) Text("轻点壁纸可删除；当前壁纸仍会保留。长按任意自选壁纸可进入管理。",
                                fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        }
                        Text("图片保留在本机。取景、旋转、模糊、明暗和柔化统一在壁纸编辑器中调整。", fontSize = 13.sp, lineHeight = 19.sp)
                    }

                }
            }
            item(key = "preview") {
                AppearanceSection("概览") {
                    PhoneThemePreview { Preview() }
                    VisualChoice("编辑壁纸", "实际壁纸取景 · 轻点打开编辑器", false, !busy && !imageLoading, Modifier.fillMaxWidth(), {
                        WanzaiTheme(AppThemeMode.Light, appearance = current.copy(theme = "Light")) {
                            WallpaperCanvas(current.copy(container = "designed"), loaded, Modifier.fillMaxSize())
                        }
                    }, sampleModifier = Modifier.wallpaperMotionCoordinates { wallpaperSources["overview-wallpaper"] = it }) { openEditor("overview-wallpaper") }
                    Text("浅色界面预览 · 选项自动保存；壁纸在编辑器完成后应用，取消保留原设置", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    note?.let { Text(it, color = if (motion.highContrast) colors.onSurface else colors.onSurfaceVariantSummary,
                        fontSize = 13.sp, lineHeight = 19.sp) }
                }
            }
            item(key = "home-layout") {
                AppearanceSection("首页布局") {
                    Text("独立调整首页模块、标题和密度，隐藏模块后自动补位。", fontSize = 13.sp)
                    WanzaiTextButton("打开首页布局", onClick = { layoutEditing = true }, enabled = !busy)
                }
            }
            item(key = "section-colors") {
                ThemeExpandableSection("背景与颜色", "整页容器背景、主题色与明暗模式", SettingIcon.Contrast, "colors" in expandedSections, !busy, { toggleSection("colors") }) {
                    ThemeSettingSegment {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("System" to "跟随系统", "Light" to "浅色", "Dark" to "深色").forEach { (id, label) ->
                                AppearanceChoice(label, current.theme == id, !busy, Modifier.weight(1f), showCheck = false) { current = current.copy(theme = id) }
                            }
                        }
                    }
                    ThemeSettingSegment {
                        WanzaiTextButton("从首页壁纸取色", enabled = !busy && loaded != null, onClick = {
                            loaded?.let { image ->
                                val hex = "#" + (image.stats.overall.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')
                                current = current.copy(customization = current.customization.copy(accentHex = hex, dynamicColor = false))
                            }
                        })
                    }
                    var hexInput by rememberSaveable(current.customization.accentHex) { mutableStateOf(current.customization.accentHex) }
                    ThemeSettingSegment {
                        // ⚠️ 这里用 material3 的 OutlinedTextField 是**有意的**：它在 Miuix/玻璃
                        // 主题下确实会画出一条跟主题不同的描边，但换成 Miuix 的 TextField 后
                        // 真机实测更糟 —— 大号灰底圆角块 + 占位文字悬在中间，比原样更突兀。
                        // 「输入框统一」属于液态玻璃接口统一那一批（m18833：等批准后实施），
                        // 这里保持原样，不在 UI bug 修复里夹带。
                        androidx.compose.material3.OutlinedTextField(value = hexInput, onValueChange = { value ->
                            hexInput = value.take(7)
                            if (value.matches(Regex("#[0-9a-fA-F]{6}"))) current = current.copy(customization = current.customization.copy(accentHex = value, dynamicColor = false))
                        }, label = { androidx.compose.material3.Text("自定义颜色 #RRGGBB") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    }
                    ThemeSettingSegment {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("tonal" to "柔和", "vivid" to "鲜明", "neutral" to "中性").forEach { (id, label) ->
                                AppearanceChoice(label, current.customization.colorStyle == id, !busy, Modifier.weight(1f), showCheck = false) {
                                    current = current.copy(customization = current.customization.copy(colorStyle = id))
                                }
                            }
                        }
                    }
                    ThemeSettingSegment {
                        Text("柔和：浅色调的选中区域；鲜明：增强选中区域的颜色；中性：灰阶强调，减少彩色。下方预览展示实际主色与选中色块。", fontSize = 12.sp, lineHeight = 18.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 原来这里是两条没有文字的纯色条：读者只看到一深一浅两个方块，
                            // 既不知道它代表什么，也分不清哪条是主色。色块下补一行说明让它自解释；
                            // 文字放在色块外面而不是压在上面，动态取色/亮暗切换时也不会糊成一片。
                            listOf(
                                "主色" to colors.primary,
                                "容器色" to colors.primaryContainer,
                            ).forEach { (label, fill) ->
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(Dims.chipCorner)).background(fill))
                                    Text(label, fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("系统 Monet 配色", "使用系统动态颜色", current.customization.dynamicColor, !busy) {
                            current = current.copy(customization = current.customization.copy(dynamicColor = !current.customization.dynamicColor))
                        }
                    }
                    listOf("indigo" to "靛蓝", "blue" to "蓝色", "teal" to "青绿", "purple" to "紫色", "rose" to "玫红", "orange" to "橙色").chunked(3).forEach { row ->
                        ThemeSettingSegment {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (id, label) -> AppearanceChoice(label, current.customization.accent == id && !current.customization.dynamicColor, !busy, Modifier.weight(1f), showCheck = false) {
                                    current = current.copy(customization = current.customization.copy(accent = id, dynamicColor = false, accentHex = ""))
                                } }
                            }
                        }
                    }
                    ThemeSettingSegment(last = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(BackgroundPreset.entries, key = { it.id }) { background ->
                                    VisualChoice(background.label, "容器底色", current.background == background.id, !busy, Modifier.width(100.dp), {
                                        Box(Modifier.fillMaxSize().background(AppColors.pageBrush(false, background)))
                                    }) { current = current.copy(background = background.id) }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "section-interaction") {
                ThemeExpandableSection("动画与显示", "模糊、底栏、角标、手势与辅助显示", SettingIcon.Sun, "interaction" in expandedSections, !busy, { toggleSection("interaction") }) {
                    ThemeSettingSegment {
                        AppearanceToggle("启用模糊", "顶栏与底栏采用背景模糊", current.customization.topBlur > 0 || current.customization.navigationBlur, !busy) {
                            val on = current.customization.topBlur > 0 || current.customization.navigationBlur
                            current = current.copy(customization = current.customization.copy(topBlur = if (on) 0 else 14, navigationBlur = !on))
                        }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("悬浮底栏", "底栏与屏幕边缘保留间距", current.customization.floatingNavigation, !busy) {
                            current = current.copy(customization = current.customization.copy(floatingNavigation = !current.customization.floatingNavigation))
                        }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("液态玻璃", "开启折射；关闭后使用磨砂材质", current.glass == "liquid", !busy) {
                            current = current.copy(glass = if (current.glass == "liquid") "frost" else "liquid")
                        }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("底栏使用玻璃材质", "开启时跟随液态／磨砂；关闭时仅底栏改为实色", current.customization.bottomBarGlass, !busy) {
                            current = current.copy(customization = current.customization.copy(bottomBarGlass = !current.customization.bottomBarGlass))
                        }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("显示导航未读角标", "消息入口显示实际未读数", current.customization.showNavigationBadges, !busy) {
                            current = current.copy(customization = current.customization.copy(showNavigationBadges = !current.customization.showNavigationBadges))
                        }
                    }
                    ThemeSettingSegment {
                        AppearanceSlider("顶栏模糊半径", current.customization.topBlur, 32,
                            { current = current.copy(customization = current.customization.copy(topBlur = it)) },
                            { current = current.copy(customization = current.customization.copy(topBlur = it)) })
                    }
                    ThemeSettingSegment {
                        AppearanceSlider("玻璃厚度", current.opacity, 100, { current = current.copy(opacity = it) }, { current = current.copy(opacity = it) })
                    }
                    ThemeSettingSegment {
                        val pagerModes = com.muyao.wanzaihyperos.data.PagerGestureMode.entries
                        val pagerLabels = listOf("标准跟手", "防误触横滑", "轻快跟手")
                        ThemePagerMenu(pagerLabels.getOrNull(current.customization.pagerGestureMode.ordinal)
                            ?.takeIf { current.customization.swipePages } ?: "关闭", !busy, pagerLabels + "关闭") { index ->
                            val mode = pagerModes.getOrNull(index)
                            current = current.copy(customization = current.customization.copy(
                                swipePages = mode != null, pagerGestureMode = mode ?: current.customization.pagerGestureMode,
                            ))
                        }
                    }
                    ThemeSettingSegment {
                        Text("页面横滑切换首页、课表、消息等主页面。标准跟手随拖动移动；防误触横滑避让边缘与纵向滚动，松手后切换；轻快跟手用较短拖动距离切换、回弹更快。关闭后仍可点底栏。", fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("预测性返回", "系统返回手势随手指预览主题页退出", current.customization.predictiveBack, !busy) {
                            current = current.copy(customization = current.customization.copy(predictiveBack = !current.customization.predictiveBack))
                        }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("滑动关闭", "从页面左侧边缘向右滑动返回", current.customization.swipeDismiss, !busy) {
                            current = current.copy(customization = current.customization.copy(swipeDismiss = !current.customization.swipeDismiss))
                        }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("减少动态", "保留状态变化，关闭位移和弹性", current.reduceMotion, !busy) { current = current.copy(reduceMotion = !current.reduceMotion) }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("高对比显示", "增强文字对比，不关闭液态玻璃", current.highContrast, !busy) { current = current.copy(highContrast = !current.highContrast) }
                    }
                    ThemeSettingSegment {
                        AppearanceToggle("减弱透明度", "使用实色承载层，保留所选材质", current.reduceTransparency, !busy) { current = current.copy(reduceTransparency = !current.reduceTransparency) }
                    }
                    ThemeSettingSegment(last = true) {
                        WanzaiTextButton("系统动画与无障碍设置", enabled = !busy, onClick = {
                            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                                .onFailure { note = "系统设置暂时无法打开" }
                        })
                    }

                }
            }
            item(key = "restore-default") {
                WanzaiTextButton("恢复默认主题", enabled = !busy, onClick = { confirmReset = true })
            }
        }
    }
    if (layoutOverlayActive) {
        // A sibling underneath the sliding page catches touches on the exposed background.
        Box(Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        })
    }
    androidx.compose.animation.AnimatedVisibility(
        visibleState = layoutVisibility,
        enter = if (motion.canMove) androidx.compose.animation.slideInHorizontally(
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 450f),
            initialOffsetX = { it },
        ) else androidx.compose.animation.EnterTransition.None,
        exit = if (motion.canMove) androidx.compose.animation.slideOutHorizontally(
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 450f),
            targetOffsetX = { it },
        ) else androidx.compose.animation.ExitTransition.None,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().background(colors.background)
            .then(if (!layoutEditing) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
            HomeLayoutPage(onClose = { layoutEditing = false }, graph = graph)
            if (!layoutEditing) {
                // During exit the outgoing page must not take taps; the shield belongs above
                // that page, never on AnimatedVisibility's stable/entering modifier.
                Box(Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                }.semantics { hideFromAccessibility() })
            }
        }
    }
    }
    if (editing) {
        val pending = editorEntry
        val initial = if (pending == null) current else current.copy(sky = "album", container = "designed",
            panX = pending.panX, panY = pending.panY, zoom = pending.zoom,
            customization = current.customization.copy(cardImage = null, wallpaperRotation = 0))
        val preview = if (pending == null) editorImage ?: loaded else editorImage
        if (pending == null || preview != null) WallpaperEditor(initial, preview, originBounds = editorOrigin,
            sourceIsValid = { editorSource?.isValid() == true },
            onCancel = { editing = false; editorEntry = null; editorImage = null; editorSource = null; editorOrigin = null },
            onDone = { edited ->
                editing = false
                editorSource = null
                editorOrigin = null
                editorEntry = null
                editorImage = null
                if (pending == null) {
                    current = edited
                    crop = crop.copy(panX = edited.panX, panY = edited.panY, zoom = edited.zoom)
                } else {
                    busy = true
                    scope.launch {
                        try {
                            val applied = pending.copy(panX = edited.panX, panY = edited.panY, zoom = edited.zoom)
                            library.apply(applied)
                            selected = applied; crop = applied
                            current = edited
                            entries = library.entries()
                            note = "已应用首页壁纸"
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { note = "应用失败，原壁纸与设置保留" }
                        finally { busy = false }
                    }
                }
            })
    }
    WanzaiOverlayDialog(
        show = pendingRemoval != null,
        onDismissRequest = { if (!busy) pendingRemoval = null },
        title = "删除这张壁纸？",
        summary = "仅删除本应用图库中的副本，不删除相册原图；已应用的首页壁纸仍然保留。",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WanzaiTextButton("取消", onClick = { pendingRemoval = null }, enabled = !busy, modifier = Modifier.weight(1f))
            WanzaiButton(onClick = {
                val entry = pendingRemoval ?: return@WanzaiButton
                if (busy) return@WanzaiButton
                busy = true
                scope.launch {
                    try {
                        entries = library.remove(entry)
                        if (selected?.id == entry.id) selected = null
                        wallpaperSources.remove(entry.id)
                        pendingRemoval = null
                        note = "已从壁纸库删除，当前首页壁纸保留"
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { note = "删除失败，请重试" }
                    finally { busy = false }
                }
            }, enabled = !busy, prominent = true, modifier = Modifier.weight(1f)) { Text("删除") }
        }
    }
    // 原来是 material3 的 AlertDialog + TextButton：白底、直角、Material 蓝字，在这套
    // 玻璃/Miuix 主题里像另一个 app 的弹窗。改用项目统一的 WanzaiOverlayDialog
    // （减动效时退成实底，两种动效档都有材质），按钮走 WanzaiTextButton，
    // 与「管理账号」「重试公告」那些确认框是同一套。玻璃绘制点在 adapter 文件里，
    // 所以本页仍然不产生任何玻璃绘制点（AppearanceOrderOnceTest 盯着这条）。
    WanzaiOverlayDialog(
        show = confirmReset,
        onDismissRequest = { confirmReset = false },
        title = "恢复默认外观？",
        summary = "立即恢复默认外观。图库、账号和学习数据保留。",
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 危险操作不能和「取消」长一个样：两个都是灰字按钮时，读者要靠读文字才分得清
            // 哪个是确认，误触代价是整份外观设置被清掉。确认键走实心主色（prominent）。
            WanzaiTextButton(text = "取消", onClick = { confirmReset = false },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            WanzaiButton(
                onClick = {
                    current = AppearanceSettings(); selected = null; crop = WallpaperEntry("preview", 0f, 0f, 1f)
                    confirmReset = false
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                prominent = true,
            ) { Text("恢复默认") }
        }
    }
}

@Composable
private fun VisualChoice(label: String, detail: String, selected: Boolean, enabled: Boolean, modifier: Modifier,
    sample: @Composable BoxScope.() -> Unit, sampleModifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    WanzaiButton(onClick = onClick, enabled = enabled,
        modifier = modifier.semantics { this.selected = selected; role = Role.RadioButton }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(Dims.wallpaperTileCorner)).then(sampleModifier), content = sample)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.chipCorner))
                .background(if (selected) colors.primaryContainer else Color.Transparent).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label + if (selected) " ✓" else "", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface)
                Text(detail, fontSize = Type.caption, lineHeight = 15.sp, color = colors.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable private fun ThemeSample(id: String) {
    Row(Modifier.fillMaxSize()) {
        val variants = if (id == "System") listOf(false, true) else listOf(id == "Dark")
        variants.forEach { dark ->
            Column(Modifier.weight(1f).fillMaxHeight().background(if (dark) PreviewInk.darkPage else PreviewInk.lightPage).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(.7f).height(4.dp).background(if (dark) Color.White else PreviewInk.darkLine, RoundedCornerShape(Dims.chipCorner)))
                Box(Modifier.fillMaxWidth().height(24.dp).background(if (dark) PreviewInk.darkCard else Color.White, RoundedCornerShape(Dims.chipCorner)))
            }
        }
    }
}

@Composable private fun MaterialSample(id: String) {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(PreviewInk.skyTop, PreviewInk.skyBottom)))) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth(.8f).padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.fillMaxWidth().height(3.dp).background(PreviewInk.inkLine.copy(alpha = .65f))) }
        }
        Box(Modifier.align(Alignment.Center).size(60.dp, 40.dp).clip(RoundedCornerShape(Dims.chipCorner))
            .background(Color.White.copy(alpha = when (id) { "liquid" -> .3f; "frost" -> .65f; else -> 1f }))) {
            if (id == "liquid") Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = .85f)))
        }
    }
}

@Composable private fun LayoutSample(compact: Boolean, wallpaper: Boolean) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxSize().background(if (wallpaper) PreviewInk.wallpaperTint else colors.background).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp)) {
        repeat(if (compact) 4 else 3) { Box(Modifier.fillMaxWidth().height(if (compact) 8.dp else 10.dp).background(colors.surface, RoundedCornerShape(Dims.chipCorner))) }
    }
}

@Composable
internal fun AppearanceChoice(label: String, selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, subtitle: String? = null, showCheck: Boolean = true, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    WanzaiButton(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { this.selected = selected }) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.chipCorner))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, fontSize = 14.sp, color = if (!enabled) colors.onSurfaceVariantSummary else if (selected) colors.onPrimaryContainer else colors.onSurface)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
            }
            if (selected && showCheck) Text("✓", color = colors.onPrimaryContainer, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
internal fun AppearanceSectionHeader(
    title: String,
    summary: String,
    icon: SettingIcon,
    expanded: Boolean,
    enabled: Boolean,
    toggle: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val colors = MiuixTheme.colorScheme
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "section-chevron")
    val animate = rememberMotion().canMove
    SettingRow(
        icon = icon,
        tint = colors.onSurface,
        title = title,
        subtitle = summary,
        modifier = Modifier.clip(shape).background(colors.surface).semantics {
            stateDescription = if (expanded) "已展开" else "已折叠"
        },
        onClick = toggle.takeIf { enabled },
        // 箭头是固定 20dp 的图标尾件：mini 屏 347dp + 1.15 倍字号会把它挤到副标题下面，
        // 变成左对齐的一只小箭头（业主报的「展开箭头偏左」），这里强制留在行尾。
        trailingInline = true,
        trailing = {
            Canvas(Modifier.size(20.dp).graphicsLayer { rotationZ = if (animate) angle else if (expanded) 180f else 0f }) {
                val left = Offset(size.width * .25f, size.height * .4f)
                val right = Offset(size.width * .75f, size.height * .4f)
                val middle = Offset(size.width * .5f, size.height * .65f)
                drawLine(colors.onSurfaceVariantSummary, left, middle, 1.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(colors.onSurfaceVariantSummary, middle, right, 1.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            }
        },
    )
}

@Composable
private fun AppearanceToggle(title: String, summary: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
        .pressSpring(interaction).padding(vertical = 6.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                val colors = MiuixTheme.colorScheme
                Text(title, fontSize = 15.sp, color = colors.onSurface)
                Text(summary, fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariantSummary)
            }
            Box(Modifier.clearAndSetSemantics { }) {
                WanzaiSwitch(checked = checked, label = title, onCheckedChange = { if (enabled) onToggle() },
                    modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else .45f })
                if (!enabled) Box(Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                })
            }
        }
    }
}

@Composable
private fun AppearanceSection(title: String, last: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().themeSegment(last).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        content()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WallpaperThumbnail(library: WallpaperLibrary, entry: WallpaperEntry, selected: Boolean, enabled: Boolean,
    managing: Boolean = false, modifier: Modifier = Modifier, onLongPress: () -> Unit = {}, onClick: () -> Unit) {
    var image by remember(library, entry.id) { mutableStateOf<LoadedWallpaper?>(null) }
    LaunchedEffect(library, entry.id) {
        try {
            val decoded = loadWallpaper(library.file(entry), 180, 240, WallpaperBudget(80_000, 320_000))
            currentCoroutineContext().ensureActive()
            image = decoded
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { image = null }
    }
    val colors = MiuixTheme.colorScheme
    val motion = rememberMotion()
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var wiggleDirection by remember(entry.id) { mutableIntStateOf(1) }
    val haptics = rememberHaptics()
    val hapticScope = rememberCoroutineScope()
    LaunchedEffect(managing, motion.canMove) {
        if (!managing || !motion.canMove) { wiggleDirection = 1; return@LaunchedEffect }
        wiggleDirection = -1
        while (true) {
            kotlinx.coroutines.delay(170)
            wiggleDirection = -wiggleDirection
        }
    }
    val tilt by animateFloatAsState(
        targetValue = if (managing && motion.canMove) 2.2f * wiggleDirection else 0f,
        animationSpec = if (motion.canMove) androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ) else androidx.compose.animation.core.snap(),
        label = "WallpaperManagementWiggle",
    )
    Box(modifier.width(84.dp).height(110.dp)
        .graphicsLayer { rotationZ = if ((entry.id.hashCode() and 1) == 0) tilt else -tilt }
        .clip(RoundedCornerShape(Dims.wallpaperTileCorner))
        .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button,
            enabled = enabled || managing, onClick = { if (!managing) onClick() }, onLongClick = {
                if (!managing) {
                    onLongPress()
                    hapticScope.launch {
                        repeat(3) { pulse ->
                            haptics.tick()
                            if (pulse < 2) kotlinx.coroutines.delay(45)
                        }
                    }
                }
            })
        .pressSpring(interaction)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(Dims.cardCorner))) {
            image?.let { Image(it.bitmap, "壁纸", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (selected) Text("✓", color = colors.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(colors.surface, RoundedCornerShape(Dims.chipCorner)).padding(4.dp))
            if (managing) {
                val removeInteraction = remember(entry.id) { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(colors.error, RoundedCornerShape(Dims.chipCorner))
                    .clickable(interactionSource = removeInteraction, indication = null, role = Role.Button, onClick = onClick)
                    .pressSpring(removeInteraction).semantics { contentDescription = "删除壁纸 ${entry.id}" }
                    .padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("−", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AppearanceSlider(label: String, value: Int, max: Int, onChange: (Int) -> Unit, onPreview: (Int) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(label, fontSize = 13.sp); ValueSlider(value, max, true, onChange) }
}

/**
 * 数值滑杆。
 *
 * 内部本轮改走 [WanzaiSlider]（vendor 的液态玻璃滑杆）：原来这里是自绘 Canvas —
 * 一根 4dp 实色轨道加一个圆点，和全站玻璃控件不是一套材质，三档（液态/磨砂/关）
 * 对它也完全无效。现在轨道与手柄的材质由 vendor 按统一 appearance 决定。
 *
 * **保留的行为**：拖动过程中持续回调 [onPreview]（用于实时预览模糊半径/透明度），
 * 松手才回调 [onChange]（真正落盘）。
 * **放弃的行为**：原来拖动被系统取消（指针离开）会回滚到旧值；vendor 滑杆没有取消回调，
 * 现在会保留最后拖动到的值。这个差异只在这类边界手势下出现，且用户看得见数值本身。
 */
@Composable
internal fun ValueSlider(value: Int, max: Int, percent: Boolean = false, onChange: (Int) -> Unit, onPreview: (Int) -> Unit = {}) {
    val latestChange by rememberUpdatedState(onChange)
    val latestPreview by rememberUpdatedState(onPreview)
    // vendor 滑杆自己维护拖动中的临时值，这里只记最后一次，松手时落盘。
    var latest by remember(max) { mutableIntStateOf(value) }
    LaunchedEffect(value) { latest = value }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WanzaiSlider(
            value = value,
            max = max,
            onValueChange = { next -> latest = next; latestPreview(next) },
            onValueChangeFinished = { latestChange(latest) },
            modifier = Modifier.weight(1f),
        )
        Text(if (percent) "$latest%" else latest.toString(), modifier = Modifier.width(42.dp), fontSize = 12.sp)
    }
}
