package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.*
import com.muyao.wanzaihyperos.data.tronclass.*
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.ui.design.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TronMembersPage(
    repository: TronMembersRepository,
    course: TronCourse,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repositoryState by repository.state.collectAsState()
    val currentCourse by repository.course.collectAsState()
    val state = if (currentCourse?.id == course.id) repositoryState else ListState.FirstLoad
    val scope = rememberCoroutineScope()
    var search by remember(course.id) { mutableStateOf("") }
    val list = rememberLazyListState()
    val progress by rememberScrollProgress(list)
    LaunchedEffect(repository, course.id) { repository.open(course) }
    val rows = state.items.filter { member ->
        search.isBlank() || listOf(member.name, member.userNo, member.department, member.klass).any { it.contains(search.trim(), ignoreCase = true) }
    }
    SecondaryScaffold(
        title = "课程成员", subtitle = course.name, onBack = onBack, modifier = modifier,
        onRefresh = { scope.launch { repository.refresh() } }, scrollProgress = progress,
    ) { padding ->
        AppList(modifier = Modifier.padding(padding), state = list) {
            item {
                TextField(value = search, onValueChange = { search = it }, label = "搜索姓名、学号或班级", modifier = Modifier.fillMaxWidth())
            }
            item { TronReadStatus(state, "课程暂未提供成员") }
            if (state is ListState.Failed || state is ListState.SessionExpired) {
                item { ActionRow("重新读取成员", "重试", onClick = { scope.launch { repository.refresh() } }) }
            }
            if (state is ListState.Content && rows.isEmpty()) item { HintText("没有匹配的成员") }
            items(rows, key = { it.enrollmentId }) { member ->
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text(member.name, style = MiuixTheme.textStyles.body1)
                        val roles = member.roles.map { role ->
                            when (role) {
                                "student" -> "学生"
                                "instructor", "teacher" -> "教师"
                                "assistant", "teaching_assistant" -> "助教"
                                "auditor" -> "旁听"
                                else -> role
                            }
                        }
                        val roleText = (roles + member.aliases + if (member.auditor) listOf("旁听") else emptyList()).distinct().joinToString(" · ")
                        if (roleText.isNotBlank()) HintText(roleText)
                        if (member.userNo.isNotBlank()) HintText(member.userNo)
                        val affiliation = listOf(member.department, member.grade, member.klass).filter { it.isNotBlank() }.joinToString(" · ")
                        if (affiliation.isNotBlank()) HintText(affiliation)
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T> TronReadStatus(state: ListState<T>, emptyText: String) {
    val meta = state.metaOrNull
    when (state) {
        ListState.FirstLoad -> HintText("正在读取…")
        ListState.SessionExpired -> HintText("登录已失效，请返回学在城院重新连接后重试")
        is ListState.Failed -> HintText(tronReadMessage(state.error))
        is ListState.Empty -> HintText(emptyText)
        is ListState.Content -> if (meta?.refreshing == true) HintText("正在更新，当前内容仍可查看")
    }
    meta?.lastRefreshError?.let { HintText("更新失败，保留上次内容：${tronReadMessage(it)}") }
}

private fun tronReadMessage(error: HzcuException): String = when {
    error is HzcuException.HttpStatus && error.statusCode == 403 -> "当前账号无权查看此内容（403）"
    error is HzcuException.Api && error.code == 403 -> "当前账号无权查看此内容（403）"
    else -> error.userText
}
