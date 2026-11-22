package com.muyao.wanzaihyperos.ui.tronclass

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.*
import com.muyao.wanzaihyperos.data.tronclass.*
import com.muyao.wanzaihyperos.ui.design.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TronResourcesPage(
    repository: TronResourcesRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repositoryState by repository.state.collectAsState()
    val query by repository.query.collectAsState()
    val scope = rememberCoroutineScope()
    var path by remember(repository) { mutableStateOf(listOf(0 to "我的资源")) }
    var confirmedPath by remember(repository) { mutableStateOf(listOf(0 to "我的资源")) }
    var draft by remember(repository) { mutableStateOf("") }
    var selected by remember(repository) { mutableStateOf<TronResource?>(null) }
    val state = if (query.folderId == path.last().first) repositoryState else ListState.FirstLoad
    val list = rememberLazyListState()
    val progress by rememberScrollProgress(list)
    LaunchedEffect(repository) { repository.open(TronResourcesQuery()) }
    LaunchedEffect(query.folderId) {
        if (query.folderId == 0 && path.last().first != 0) {
            path = listOf(0 to "我的资源"); confirmedPath = path; selected = null
        }
    }
    LaunchedEffect(query, state, path) {
        if (query.folderId == path.last().first && state.hasSettled) confirmedPath = path
        if (state.hasSettled && state.items.none { it.id == selected?.id }) selected = null
    }
    val directorySubtitle = if (path == confirmedPath || state.hasSettled) path.joinToString(" / ") { it.second }
    else confirmedPath.joinToString(" / ") { it.second } + when (state) {
        is ListState.Failed, ListState.SessionExpired -> " · 未能打开「${path.last().second}」"
        else -> " · 正在打开「${path.last().second}」"
    }
    fun navigate(next: List<Pair<Int, String>>) {
        path = next; draft = ""; selected = null
        scope.launch { repository.open(TronResourcesQuery(next.last().first)) }
    }
    val back: () -> Unit = {
        when {
            selected != null -> selected = null
            query.keyword.isNotBlank() -> {
                draft = ""; scope.launch { repository.open(query.copy(keyword = "")) }
            }
            path.size > 1 -> navigate(path.dropLast(1))
            else -> onBack()
        }
    }
    BackHandler(enabled = selected != null || query.keyword.isNotBlank() || path.size > 1, onBack = back)
    SecondaryScaffold(
        title = "我的资源", subtitle = directorySubtitle, onBack = back,
        modifier = modifier, onRefresh = { scope.launch { repository.refresh() } }, scrollProgress = progress,
    ) { padding ->
        AppList(modifier = Modifier.padding(padding), state = list) {
            item {
                TextField(value = draft, onValueChange = { draft = it }, label = "搜索当前目录", modifier = Modifier.fillMaxWidth())
            }
            item {
                ActionRow("搜索资源", "搜索", onClick = {
                    selected = null
                    scope.launch { repository.open(TronResourcesQuery(path.last().first, draft.trim())) }
                })
            }
            if (path.size > 1) {
                item { ActionRow("返回上级目录", "返回", onClick = { navigate(path.dropLast(1)) }) }
                item { ActionRow("回到我的资源", "根目录", onClick = { navigate(path.take(1)) }) }
            }
            item { TronReadStatus(state, if (query.keyword.isBlank()) "此目录暂无资源" else "没有匹配的资源") }
            if (state is ListState.Failed || state is ListState.SessionExpired) {
                item { ActionRow("重新读取资源", "重试", onClick = { scope.launch { repository.refresh() } }) }
            }
            selected?.let { resource ->
                item(key = "resource-metadata") {
                    AppCard {
                        Column(Modifier.padding(Dims.cardPadding)) {
                            Text(resource.name, style = MiuixTheme.textStyles.body1)
                            HintText("类型：${resource.type}")
                            resource.size?.let { HintText("大小：$it 字节") }
                            if (resource.createdAt.isNotBlank()) HintText("创建时间：${resource.createdAt}")
                        }
                    }
                }
                item { ActionRow("收起资源信息", "收起", onClick = { selected = null }) }
            }
            items(state.items, key = { it.id }) { resource ->
                ActionRow(
                    title = resource.name, action = if (resource.folder) "进入" else "信息",
                    subtitle = if (resource.folder) "文件夹" else listOf(resource.type, resource.createdAt).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = {
                        if (resource.folder) navigate(path + (resource.id to resource.name)) else selected = resource
                    },
                )
            }
            if (state is ListState.Content && state.metaOrNull?.hasMore == true) {
                item {
                    ActionRow(
                        "更多资源", if (state.metaOrNull?.loadingMore == true) "正在读取…" else "加载下一页",
                        onClick = { scope.launch { repository.loadMore() } },
                    )
                }
            }
        }
    }
}
