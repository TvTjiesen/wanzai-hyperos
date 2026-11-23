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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    if (layoutEditing) {
        HomeLayoutPage(onClose = { layoutEditing = false })
        return
    }
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
    val wallpaperSources = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var editorOrigin by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val closePage by rememberUpdatedState(onClose)
    fun close() { if (!busy) closePage() }
    var backProgress by remember { mutableFloatStateOf(0f) }
    ThemeBackGesture(current.customization.predictiveBack, onBack = ::close, onProgress = { backProgress = it })
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
        editorOrigin = wallpaperSources[entry.id]
        editorEntry = entry
        editorImage = decoded
        editing = true
    }
    fun chooseWallpaper(entry: WallpaperEntry) {
        if (busy) return
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
    fun openEditor() {
        if (!busy) { editorOrigin = wallpaperSources["current-preview"]; editorEntry = null; editorImage = loaded; editing = true }
    }
    @Composable fun Preview() {
        WanzaiTheme(AppThemeMode.Light, appearance = current.copy(theme = "Light")) {
            Box(Modifier.fillMaxSize()) {
                ThemeOverviewPreview(current.copy(theme = "Light"), loaded)
                if (imageLoading) Text("正在读取本机照片…", color = MiuixTheme.colorScheme.onSurface,
                    fontSize = Type.caption, modifier = Modifier.align(Alignment.BottomCenter)
                        .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(Dims.chipCorner)).padding(8.dp))
            }
        }
    }
    SecondaryScaffold(title = "主题设置", onBack = ::close, scrollProgress = scrollProgress, modifier = modifier.themeSwipeDismiss(current.customization.swipeDismiss, ::close).graphicsLayer { translationX = size.width * backProgress * .12f; scaleX = 1f - backProgress * .04f; scaleY = scaleX }) { padding ->
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dims.screenH, 12.dp, Dims.screenH,
                com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp(com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible.current)),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "preview") {
                AppearanceSection("概览") {
                    Box(Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().height(220.dp).wallpaperMotionBounds { wallpaperSources["current-preview"] = it }
                        .clip(RoundedCornerShape(Dims.cardCorner))) { Preview() }
                    AppearanceChoice("编辑壁纸", false, !busy && !imageLoading) { openEditor() }
                    Text("浅色界面预览 · 选项自动保存；壁纸在编辑器完成后应用，取消保留原设置", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    note?.let { Text(it, color = if (motion.highContrast) colors.onSurface else colors.onSurfaceVariantSummary,
                        fontSize = 13.sp, lineHeight = 19.sp) }
                }
            }
            item(key = "section-home") {
                ThemeExpandableSection("首页壁纸", "顶部壁纸、裁切与显示", SettingIcon.House, "home" in expandedSections, !busy, { toggleSection("home") }) {
                    AppearanceSection("系统自带壁纸") {
                        VisualChoice("晴海 · 原画", "海湾、帆船与雏菊", selected?.id == "builtin-sea", !busy, Modifier.fillMaxWidth().wallpaperMotionBounds { wallpaperSources["builtin-sea"] = it }, {
                            Image(androidx.compose.ui.res.painterResource(com.muyao.wanzaihyperos.R.drawable.wallpaper_sea),
                                "原绘海湾壁纸", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }) {
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
                        AppearanceChoice("选择首页壁纸", album, !busy) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        AppearanceChoice("编辑壁纸", false, !busy && !imageLoading) { openEditor() }
                        if (entries.any { it.id != "builtin-sea" }) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(entries.filter { it.id != "builtin-sea" }, key = { it.id }) { entry -> WallpaperThumbnail(library, entry, selected?.id == entry.id, !busy,
                                modifier = Modifier.wallpaperMotionBounds { wallpaperSources[entry.id] = it }) { chooseWallpaper(entry) } }
                        }
                        Text("图片保留在本机。取景、旋转、模糊、明暗和柔化统一在壁纸编辑器中调整。", fontSize = 13.sp, lineHeight = 19.sp)
                        AppearanceToggle("显示首页壁纸", "只影响首页顶部，容器背景单独选择", current.container != "bare", !busy) {
                            current = current.copy(container = if (current.container == "bare") "designed" else "bare")
                        }
                    }

                }
            }
            item(key = "home-layout") {
                AppearanceSection("首页布局") {
                    Text("独立调整首页模块、标题和密度，隐藏模块后自动补位。", fontSize = 13.sp)
                    AppearanceChoice("打开首页布局", false, !busy) { layoutEditing = true }
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
                        AppearanceChoice("从首页壁纸取色", false, !busy && loaded != null) {
                            loaded?.let { image ->
                                val hex = "#" + (image.stats.overall.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')
                                current = current.copy(customization = current.customization.copy(accentHex = hex, dynamicColor = false))
                            }
                        }
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
                        AppearanceChoice("系统动画与无障碍设置", false, !busy) {
                            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                                .onFailure { note = "系统设置暂时无法打开" }
                        }
                    }

                }
            }
            item(key = "restore-default") {
                AppearanceChoice("恢复默认主题", false, !busy) { confirmReset = true }
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
            onCancel = { editing = false; editorEntry = null; editorImage = null },
            onDone = { edited ->
                editing = false
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
    sample: @Composable BoxScope.() -> Unit, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    // 未选中时原来是 colors.background 实底：卡片本身已经是 surface 底，于是每个
    // 选项都变成一块深浅不一的方贴片，一排看过去像没对齐的色块。未选中留透明，
    // 只让选中的那一项浮起来。
    Column(modifier.clip(RoundedCornerShape(Dims.cardCorner)).background(if (selected) colors.primary.copy(alpha = .10f) else Color.Transparent)
        .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }
        .padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(Dims.wallpaperTileCorner)), content = sample)
        Text(label + if (selected) " ✓" else "", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Text(detail, fontSize = Type.caption, lineHeight = 15.sp, color = colors.onSurfaceVariantSummary)
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
    val canMove = rememberMotion().canMove
    // 选中底原来用 primary 的 10% 透明度压在卡面上，可下面还有一层 graphicsLayer 阴影
    // （selectionDepth）——半透明的底会让阴影透出来，选中格因此显成一坨**脏灰**，
    // 真机像素采样是 #B5B7BF 的灰带 + #E3E5ED 的心，看着像按下去没弹起来。
    // 改成与卡面 surface 先混合成实色再画，阴影就透不过来；配一圈同色描边表达选中。
    // 混色比例按卡面亮度分档：深色卡面 #222324 上叠 14% 主色只差 Δ1.27（肉眼读不出），
    // 选中信息就全压在一条 1dp 描边上；深色档提到 26% 才有「这块被选中了」的观感。
    val darkSurface = colors.surface.luminance() < 0.5f
    val selectionMix = if (darkSurface) .26f else .14f
    val selectionFill: Color = if (selected)
        androidx.compose.ui.graphics.lerp(colors.surface, colors.primary, selectionMix) else Color.Transparent
    val selectionTint by androidx.compose.animation.animateColorAsState(
        selectionFill,
        animationSpec = if (canMove) androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow) else androidx.compose.animation.core.snap(),
        label = "appearance-selection",
    )
    val selectionDepth by animateFloatAsState(if (selected && enabled) 1f else 0f,
        animationSpec = if (canMove) androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow) else androidx.compose.animation.core.snap(),
        label = "appearance-depth")
    // 圆角只走统一 token（CornerTierTest 盯着外观页不许写死 dp）：选中感由阴影/底色抬升。
    val sampleShape = RoundedCornerShape(Dims.cardCorner)
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .graphicsLayer { shadowElevation = 1.5.dp.toPx() * selectionDepth; shape = sampleShape; clip = true }
        .background(selectionTint, sampleShape)
        // 描边是选中态的第二重信号：深色档下 1dp/45% 的对比只有 2.68:1（低于非文本 3:1），
        // 半像素还会被上面的 clip 削掉，所以深色档加粗到 1.5dp、提到 65%。
        .then(if (selected) Modifier.border(
            if (darkSurface) 1.5.dp else 1.dp,
            colors.primary.copy(alpha = if (darkSurface) .65f else .45f), sampleShape) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { this.selected = selected }
        // 三格并排时（跟随系统/浅色/深色）每格实际只有 ~99dp，原来左右各 12dp 内边距加
        // 紧贴文字的 ✓，把「跟随系统」挤成「跟随/系统」两行、还把格撑得比别人高。
        // 内边距收到 8dp、✓ 隔开 6dp，四字标签就能单行放下（不许用 maxLines 截断：
        // SettingsPlanTest 明确禁止省略号式截断）。
        .padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 14.sp, color = if (!enabled) colors.onSurfaceVariantSummary else if (selected) colors.primary else colors.onSurface)
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
        }
        // ✓ 是可选的：并排的分段控件（跟随系统/浅色/深色、柔和/鲜明/中性、六个主题色）
        // 每格只有 ~97dp，塞下 ✓ 就会把四字标签挤成两行、还把该格撑得比别人高；
        // 这类格子靠「淡底 + 描边」已经足够表达选中，不需要再加一个勾。
        if (selected && showCheck) Text("✓", color = colors.primary, modifier = Modifier.padding(start = 6.dp))
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
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
        .padding(vertical = 6.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                val colors = MiuixTheme.colorScheme
                Text(title, fontSize = 15.sp, color = colors.onSurface)
                Text(summary, fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariantSummary)
            }
            // 开关样式统一（WanzaiSwitch / Glasmmio 药丸）属于液态玻璃接口统一那一批，
            // m18833 要求等批准后实施，这里保持 Miuix 原生 Switch 不改。
            top.yukonga.miuix.kmp.basic.Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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

@Composable
private fun WallpaperThumbnail(library: WallpaperLibrary, entry: WallpaperEntry, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
    Box(modifier.width(84.dp).height(110.dp).clip(RoundedCornerShape(Dims.wallpaperTileCorner)).clickable(enabled = enabled, onClick = onClick)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(Dims.cardCorner))) {
            image?.let { Image(it.bitmap, "壁纸", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (selected) Text("✓", color = colors.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(colors.surface, RoundedCornerShape(Dims.chipCorner)).padding(4.dp))
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
