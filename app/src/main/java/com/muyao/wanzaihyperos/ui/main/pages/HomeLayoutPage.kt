package com.muyao.wanzaihyperos.ui.main.pages

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.data.HomeSticker
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.design.*
import com.muyao.wanzaihyperos.ui.home.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun HomeLayoutPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val library = remember(context) { WallpaperLibrary(context) }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(UiPrefs.appearanceSnapshot()) }
    val latest by rememberUpdatedState(draft)
    var entries by remember { mutableStateOf(emptyList<WallpaperEntry>()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val colors = MiuixTheme.colorScheme
    val list = rememberLazyListState()
    val progress by rememberScrollProgress(list)
    LaunchedEffect(library) {
        try { entries = library.entries() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { note = "图库暂时无法读取，已有贴纸保留" }
    }
    // Gesture frames stay local; persist after the last update or when leaving.
    LaunchedEffect(draft) { delay(200); UiPrefs.applyAppearance(draft) }
    DisposableEffect(Unit) { onDispose { UiPrefs.applyAppearance(latest) } }
    fun close() { UiPrefs.applyAppearance(draft); onClose() }
    BackHandler(onBack = ::close)
    fun addSticker(entry: WallpaperEntry) {
        if (draft.customization.stickers.size >= 5) { note = "最多添加五张贴纸"; return }
        val slot = draft.customization.stickers.size
        val sticker = HomeSticker(UUID.randomUUID().toString(), entry.id,
            x = listOf(.78f, .5f, .2f, .68f, .35f)[slot], y = .1f + slot * .14f)
        draft = draft.copy(customization = draft.customization.copy(stickers = draft.customization.stickers + sticker))
        note = "已添加，拖动预览中的贴纸可调整位置，双指缩放与旋转"
    }
    fun updateSticker(sticker: HomeSticker) {
        draft = draft.copy(customization = draft.customization.copy(stickers =
            draft.customization.stickers.map { if (it.id == sticker.id) sticker else it }))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val before = entries.map { it.id }.toSet()
                    val (next, rejected) = library.addImages(listOf(uri))
                    entries = next
                    val added = next.firstOrNull { it.id !in before }
                    if (added != null) addSticker(added)
                    else note = if (rejected > 0) "图片未能导入，请选择32MB以内图片或使用已有图片" else "未添加新图片"
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { note = "图片未能导入，已有布局保留" }
                finally { busy = false }
            }
        }
    }
    SecondaryScaffold(title = "首页布局", onBack = ::close, scrollProgress = progress) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = list,
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item(key = "stickers") {
                Text("贴纸", fontSize = 22.sp)
                Text("只作装饰，不覆盖课程或签到按钮。图片留在本机，支持五张。", fontSize = 13.sp)
                CompositionLocalProvider(LocalThemeCustomization provides draft.customization) {
                    Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer)) {
                        HomeStickers(Modifier.fillMaxSize(), onChange = ::updateSticker)
                    }
                }
                AppearanceChoice(if (busy) "正在导入…" else "从相册添加贴纸 · ${draft.customization.stickers.size}/5",
                    false, !busy && draft.customization.stickers.size < 5) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (entries.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Column(Modifier.width(104.dp)) {
                            val image = decorationImage(entry.id)
                            if (image != null) Image(image, "本机贴纸图片", Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                            AppearanceChoice("添加此图", false, !busy && draft.customization.stickers.size < 5) { addSticker(entry) }
                        }
                    }
                }
                note?.let { Text(it, fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
            }
            items(draft.customization.stickers, key = { it.id }) { sticker ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("贴纸 ${draft.customization.stickers.indexOfFirst { it.id == sticker.id } + 1}", fontSize = 16.sp)
                    LayoutSlider("大小", ((sticker.scale - .4f) * 100).roundToInt(), 160) { updateSticker(sticker.copy(scale = .4f + it / 100f)) }
                    LayoutSlider("旋转 · ${sticker.rotation.roundToInt()}°", sticker.rotation.roundToInt() + 180, 360) { updateSticker(sticker.copy(rotation = it - 180f)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppearanceChoice("居中", false, modifier = Modifier.weight(1f)) { updateSticker(sticker.copy(x = .5f, y = .5f)) }
                        AppearanceChoice("移除", false, modifier = Modifier.weight(1f)) {
                            draft = draft.copy(customization = draft.customization.copy(stickers = draft.customization.stickers.filterNot { it.id == sticker.id }))
                        }
                    }
                }
            }
            item(key = "modules") {
                Text("首页模块", fontSize = 22.sp)
                Text("隐藏的模块不占位置，其余内容按顺序自动补位。", fontSize = 13.sp)
                listOf("courses" to "课程", "todos" to "待办", "rollcall" to "签到", "szty" to "校园跑").chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (id, label) ->
                            AppearanceChoice("$label · ${if (id in draft.modules) "显示" else "隐藏"}", id in draft.modules, modifier = Modifier.weight(1f)) {
                                draft = draft.copy(modules = if (id in draft.modules) draft.modules - id else draft.modules + id)
                            }
                        }
                    }
                }
                AppearanceChoice(if (draft.appsCollapsed) "网页服务 · 已收起" else "网页服务 · 展开", draft.appsCollapsed) { draft = draft.copy(appsCollapsed = !draft.appsCollapsed) }
            }
            item(key = "spacing") {
                Text("标题与密度", fontSize = 22.sp)
                androidx.compose.material3.OutlinedTextField(value = draft.customization.homeTitle, onValueChange = {
                    if (it.length <= 24 && it.none(Char::isISOControl)) draft = draft.copy(customization = draft.customization.copy(homeTitle = it))
                }, label = { androidx.compose.material3.Text("首页标题 · 留空恢复默认") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("comfortable" to "舒适", "compact" to "紧凑").forEach { (id, label) ->
                        AppearanceChoice(label, draft.density == id, modifier = Modifier.weight(1f)) { draft = draft.copy(density = id) }
                    }
                }
                LayoutSlider("正文卡片不透明度", draft.customization.cardOpacity - 30, 70) { draft = draft.copy(customization = draft.customization.copy(cardOpacity = it + 30)) }
                LayoutSlider("界面缩放 · ${draft.customization.displayScale}%", draft.customization.displayScale - 85, 30) { draft = draft.copy(customization = draft.customization.copy(displayScale = it + 85)) }
                LayoutSlider("界面字号 · ${draft.customization.textScale}%", draft.customization.textScale - 85, 45) { draft = draft.copy(customization = draft.customization.copy(textScale = it + 85)) }
            }
        }
    }
}

@Composable
private fun LayoutSlider(label: String, value: Int, maximum: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp)
        ValueSlider(value, maximum, onChange = onChange, onPreview = onChange)
    }
}
