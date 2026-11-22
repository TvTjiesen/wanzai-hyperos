package com.muyao.wanzaihyperos.ui.main.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.PrefsBackup
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.net.CacheDomain
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.components.WanzaiOverlayDialog
import com.muyao.wanzaihyperos.ui.design.*
import com.muyao.wanzaihyperos.ui.main.pages.redesign.SettingsCacheClearPlan
import com.muyao.wanzaihyperos.ui.main.pages.redesign.settingsCacheSizeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferencesPage(graph: AppGraph, uiState: AppUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val motion = rememberMotion()
    val list = rememberLazyListState()
    val entrance = rememberSpringListEntrance(list)
    var preview by remember { mutableStateOf<PrefsBackup.Preview?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var domains by remember { mutableStateOf<List<CacheDomain>?>(null) }
    var cacheExpanded by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf<SettingsCacheClearPlan?>(null) }
    LaunchedEffect(graph.httpCache) {
        try { domains = withContext(Dispatchers.IO) { graph.httpCache.domains() } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { result = "缓存大小暂时无法读取" }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        val text = PrefsBackup.export()
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("系统没有给出可写的数据流")
                    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                result = "偏好已导出"
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                result = if (failure is IllegalStateException && failure.message == "系统没有给出可写的数据流") {
                    "导出未完成：系统没有给出可写的数据流，请重新选择保存位置"
                } else "导出未完成，请重试"
            }
            finally { busy = false }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: error("No readable stream")
                    input.use {
                        val bytes = it.readNBytes(PrefsBackup.MAX_BYTES + 1)
                        require(bytes.size <= PrefsBackup.MAX_BYTES)
                        bytes.toString(Charsets.UTF_8)
                    }
                }
                preview = PrefsBackup.parse(text)
                if (preview == null) result = PrefsBackup.lastError ?: "无法读取这份备份"
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { result = "备份无法读取或超过64KB" }
            finally { busy = false }
        }
    }
    SecondaryScaffold(title = "偏好与备份", onBack = onBack) { padding ->
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(com.muyao.wanzaihyperos.ui.design.Dims.screenH, 12.dp, com.muyao.wanzaihyperos.ui.design.Dims.screenH, com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp(com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible.current)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "backup") {
                Column(Modifier.springListEntrance(entrance, "backup"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("把熟悉的界面带回来", fontSize = Type.title, color = colors.onSurface)
                    Text("备份外观、布局和辅助显示。不含账号、学校记录、图片或提醒计划。", fontSize = Type.summary, lineHeight = Type.summary * 1.45f, color = if (motion.highContrast) colors.onSurface else colors.onSurfaceVariantSummary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppearanceChoice("导出偏好", false, !busy, Modifier.weight(1f)) { export.launch(PrefsBackup.suggestedFileName()) }
                        AppearanceChoice("从备份恢复", false, !busy, Modifier.weight(1f)) { importer.launch(arrayOf("application/json", "text/plain")) }
                    }
                }
            }
            result?.let { message -> item(key = "result") { Text(message, fontSize = Type.summary, lineHeight = Type.summary * 1.45f, color = if (motion.highContrast) colors.onSurface else colors.onSurfaceVariantSummary) } }
            item(key = "local-data") {
                Column(Modifier.springListEntrance(entrance, "local-data"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("本机存储", fontSize = Type.title, color = colors.onSurface)
                    val totalBytes = domains?.sumOf { it.bytes }
                    AppearanceChoice("读取缓存 · ${totalBytes?.let(::settingsCacheSizeLabel) ?: "读取中"}", cacheExpanded) { cacheExpanded = !cacheExpanded }
                    if (cacheExpanded) {
                        Text("只清理读取缓存，不退出账号、不删除壁纸。", fontSize = Type.summary, lineHeight = Type.summary * 1.45f, color = if (motion.highContrast) colors.onSurface else colors.onSurfaceVariantSummary)
                        domains.orEmpty().forEach { domain ->
                            AppearanceChoice("${domain.name} · ${settingsCacheSizeLabel(domain.bytes)}", false, !busy) {
                                pendingClear = SettingsCacheClearPlan(domain.name, domain.entries, domain.bytes)
                            }
                        }
                        AppearanceChoice("清理全部读取缓存", false, !busy && domains != null && domains!!.isNotEmpty()) {
                            pendingClear = SettingsCacheClearPlan(null, domains!!.sumOf { it.entries }, domains!!.sumOf { it.bytes })
                        }
                    }
                }
            }
            if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                item(key = "developer-tools") {
                    AppearanceChoice("小球实验室", false) {
                        runCatching { context.startActivity(android.content.Intent().setClassName(context, "com.muyao.wanzaihyperos.dev.AiBallLabActivity")) }
                            .onFailure { result = "实验室暂时无法打开" }
                    }
                }
            }
        }
    }
    preview?.let { pending ->
        WanzaiOverlayDialog(show = true, onDismissRequest = { preview = null }, title = "恢复这些偏好？") {
            Column(Modifier.heightIn(max = 360.dp).then(Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(pending.lines.size) { index -> val (label, value) = pending.lines[index]; Text("$label：$value", fontSize = Type.summary, lineHeight = Type.summary * 1.45f, color = if (motion.highContrast) colors.onSurface else colors.onSurfaceVariantSummary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceChoice("取消恢复", false, modifier = Modifier.weight(1f)) { preview = null }
                    AppearanceChoice("确认恢复这份偏好备份", true, !busy, Modifier.weight(1f)) {
                        if (busy || preview != pending) return@AppearanceChoice
                        preview = null
                        busy = true
                        scope.launch {
                            try {
                                val count = withContext(Dispatchers.IO) { PrefsBackup.apply(pending) }
                                if (count >= 0) {
                                    com.muyao.wanzaihyperos.ui.syncUiStateFromPrefs(uiState)
                                    MotionPrefs.setReduceTransparency(UiPrefs.reduceTransparency)
                                    MotionPrefs.setRefractsGlass(UiPrefs.glassMode == "liquid")
                                    MotionPrefs.setReduceMotion(UiPrefs.reduceMotion)
                                    MotionPrefs.setHighContrastOverride(UiPrefs.highContrastOverride)
                                }
                                result = if (count >= 0) "已恢复 $count 项偏好" else PrefsBackup.lastError
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { result = "恢复未完成，请重试" }
                            finally { busy = false }
                        }
                    }
                }
            }
        }
    }
    pendingClear?.let { plan ->
        WanzaiOverlayDialog(show = true, onDismissRequest = { pendingClear = null }, title = plan.title, summary = plan.impactText()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceChoice("取消，不删除缓存", false, modifier = Modifier.weight(1f)) { pendingClear = null }
                AppearanceChoice("确认清理本地读取缓存", false, !busy, Modifier.weight(1f)) {
                    if (busy || pendingClear != plan) return@AppearanceChoice
                    pendingClear = null; busy = true
                    scope.launch {
                        try {
                            val next = withContext(Dispatchers.IO) {
                                if (plan.domain == null) graph.httpCache.clearAll() else graph.httpCache.clearDomain(plan.domain)
                                graph.httpCache.domains()
                            }
                            domains = next; result = plan.observedResult(next)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { result = "清理未确认完成，请重试" }
                        finally { busy = false }
                    }
                }
            }
        }
    }
}
