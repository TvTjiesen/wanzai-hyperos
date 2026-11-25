package com.muyao.wanzaihyperos.ui.main.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.HomeLayoutOrder
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.design.*
import com.muyao.wanzaihyperos.ui.home.HomeLayoutEditing
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HomeLayoutPage(graph: AppGraph, onClose: () -> Unit) {
    var draft by remember { mutableStateOf(UiPrefs.appearanceSnapshot().validated()) }
    val latest by rememberUpdatedState(draft)
    val list = rememberLazyListState()
    val progress by rememberScrollProgress(list)
    // Keep edits local during a gesture. Save the latest complete draft while leaving,
    // including BackHandler, top-bar back, and disposal during the page swipe transition.
    LaunchedEffect(draft) { delay(200); UiPrefs.applyAppearance(draft) }
    DisposableEffect(Unit) { onDispose { UiPrefs.applyAppearance(latest) } }
    fun close() { UiPrefs.applyAppearance(draft); onClose() }
    BackHandler(onBack = ::close)

    val editor = remember(draft) {
        HomeLayoutEditing(
            settings = draft,
            onMove = { from, to -> draft = draft.copy(homeOrder = HomeLayoutOrder.move(draft.homeOrder, from, to)) },
            onToggle = { id ->
                draft = draft.copy(hiddenHomeSections = if (id in draft.hiddenHomeSections) draft.hiddenHomeSections - id else draft.hiddenHomeSections + id)
            },
        )
    }

    SecondaryScaffold(title = "首页布局", onBack = ::close, scrollProgress = progress) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding), state = list,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Column(Modifier.fillMaxWidth().themeSegment(last = false).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LayoutSectionTitle("首页画布", "这里使用首页真实卡片。长按卡片拖动排序；隐藏后仍保留原位置。预览中的按钮不会跳转或刷新。")
                    Text("长按 ⠿ 标题或卡片可排序 · 隐藏与恢复可在每块右侧操作", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            item(key = "live-home") {
                Box(Modifier.fillMaxWidth().heightIn(min = 560.dp, max = 820.dp).themeSegment(last = false).padding(8.dp)) {
                    HomeSectionPage(
                        graph = graph,
                        onOpenJw = {}, onOpenTimetable = {}, onOpenTodos = {}, onOpenMessages = {},
                        onOpenRollcall = {}, onOpenAppearance = {}, onOpenSzty = {},
                        modifier = Modifier.fillMaxSize(), layoutEditing = editor,
                    )
                }
            }
            item(key = "modules") {
                Column(Modifier.fillMaxWidth().themeSegment(last = false).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LayoutSectionTitle("首页模块", "课程与功能卡片可单独关闭；隐藏的区块也可以在上方画布中恢复。")
                    listOf("courses" to "课程与时间线", "todos" to "待办", "rollcall" to "签到", "szty" to "校园跑")
                        .chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (id, label) ->
                                    AppearanceChoice("$label · ${if (id in draft.modules) "显示" else "隐藏"}", id in draft.modules, modifier = Modifier.weight(1f)) {
                                        draft = draft.copy(modules = if (id in draft.modules) draft.modules - id else draft.modules + id)
                                    }
                                }
                            }
                        }
                    AppearanceChoice(if (draft.appsCollapsed) "网页服务 · 已收起" else "网页服务 · 展开", draft.appsCollapsed,
                        onClick = { draft = draft.copy(appsCollapsed = !draft.appsCollapsed) })
                }
            }
            item(key = "spacing") {
                Column(Modifier.fillMaxWidth().themeSegment(last = true).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LayoutSectionTitle("标题与密度", "长标题和大字号会按屏幕宽度自动换行；首页卡片保留自身高度。")
                    androidx.compose.material3.OutlinedTextField(
                        value = draft.customization.homeTitle,
                        onValueChange = { if (it.length <= 24 && it.none(Char::isISOControl)) draft = draft.copy(customization = draft.customization.copy(homeTitle = it)) },
                        label = { androidx.compose.material3.Text("首页标题 · 留空恢复默认") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("内容密度", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("comfortable" to "舒适", "compact" to "紧凑").forEach { (id, label) ->
                                AppearanceChoice(label, draft.density == id, modifier = Modifier.weight(1f)) { draft = draft.copy(density = id) }
                            }
                        }
                    }
                    LayoutSlider("正文卡片不透明度", draft.customization.cardOpacity - 30, 70) { draft = draft.copy(customization = draft.customization.copy(cardOpacity = it + 30)) }
                    LayoutSlider("界面缩放 · ${draft.customization.displayScale}%", draft.customization.displayScale - 85, 30) { draft = draft.copy(customization = draft.customization.copy(displayScale = it + 85)) }
                    LayoutSlider("界面字号 · ${draft.customization.textScale}%", draft.customization.textScale - 85, 45) { draft = draft.copy(customization = draft.customization.copy(textScale = it + 85)) }
                }
            }
        }
    }
}

@Composable
private fun LayoutSectionTitle(title: String, summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 17.sp, color = MiuixTheme.colorScheme.onSurface)
        Text(summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun LayoutSlider(label: String, value: Int, maximum: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
        ValueSlider(value, maximum, onChange = onChange, onPreview = onChange)
    }
}
