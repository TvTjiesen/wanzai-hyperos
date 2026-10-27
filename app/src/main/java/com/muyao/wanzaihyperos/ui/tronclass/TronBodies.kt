package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.data.tronclass.HomeworkRepository
import com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind
import com.muyao.wanzaihyperos.data.tronclass.RollcallRepository
import com.muyao.wanzaihyperos.data.tronclass.TronHomeRepository
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.BackRow
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.infoItems
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「我的課」板塊的內容（原 [TronHomePage] 的內臟，去掉頂欄）。
 *
 * 內容只用 [AppList] 與設計層的元件組成 —— 沒有 `12.dp`／`14.dp` 這種字面值，
 * 所以後面整體美化時不需要動這裡。
 */
@Composable
fun TronHomeBody(
    repository: TronHomeRepository,
    onOpenRollcall: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenCourse: (com.muyao.wanzaihyperos.data.tronclass.TronCourse) -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val home = (state as? ListState.Content)?.items?.firstOrNull()

    if (home == null) {
        StatePlaceholder(
            state = pageState,
            modifier = modifier,
            onAction = onOpenWeb,
            actionLabel = "改用网页版",
        )
        return
    }

    AppList(modifier = modifier) {
        home.nextDue?.let { due ->
            item {
                HintText("最近要交：${due.title}（${due.localTimeText.ifBlank { "—" }}）")
            }
        }

        item { SmallTitle(text = "常用") }
        item {
            ActionRow(
                title = "签到",
                subtitle = "查看进行中的签到并签到",
                action = "查看与签到",
                onClick = onOpenRollcall,
            )
        }
        item {
            ActionRow(
                title = "课表",
                subtitle = "本周与整学期的上课时间",
                action = "查看",
                onClick = onOpenSchedule,
            )
        }

        item { SmallTitle(text = "我的课（${home.courses.size}）") }
        if (home.courses.isEmpty()) {
            item { HintText("没有取到课程") }
        } else {
            infoItems(home.courses, key = { it.id }) { c ->
                InfoItem(
                    title = c.name,
                    subtitle = c.subtitle.ifBlank {
                        listOfNotNull(
                            c.teachingClass.takeIf { it.isNotBlank() },
                            c.department.takeIf { it.isNotBlank() },
                        ).joinToString("　")
                    },
                    onClick = { onOpenCourse(c) },
                )
            }
        }

        item { SmallTitle(text = "待交作业（${home.todos.size}）") }
        if (home.todos.isEmpty()) {
            item { HintText("没有待交作业") }
        } else {
            infoItems(home.todos, key = { it.id }) { t ->
                InfoItem(
                    title = t.title,
                    subtitle = listOfNotNull(
                        t.courseName.takeIf { it.isNotBlank() },
                        t.localTimeText.takeIf { it.isNotBlank() },
                    ).joinToString("　"),
                )
            }
        }

        item {
            HintText("需要讨论、测验、点名、成绩等功能？可以用网页版。")
        }
        item {
            ActionRow(title = "更多功能", action = "改用网页版", onClick = onOpenWeb)
        }
    }
}

/**
 * 簽到板塊的內容。
 *
 * 現在只是 [RollcallBody] 的一層皮：**外殼在這裡塞「返回」條目**，
 * 其餘全部走同一份實作（首頁那顆磁貼進的 [RollcallStationPage] 用的也是它）。
 *
 * ⚠️ 為什麼堅持一份實作（而不是外殼裡留一份、獨立頁再寫一份）：
 * 本專案裡「一個能力兩條入口、其中一條忘了修」出現過至少三次
 *（顶栏纱、待办时间差 8 小时、作业说明的 HTML）。
 */
@Composable
fun RollcallContent(
    repository: RollcallRepository,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RollcallBody(
        repository = repository,
        onOpenWeb = onOpenWeb,
        modifier = modifier,
        header = { item { BackRow("返回", onBack) } },
    )
}

/**
 * 作業板塊的內容（某門課的清單；點一行進詳情與提交）。
 *
 * 這裡只做「看清單 + 送進詳情」；**提交在 [HomeworkDetailContent]**，
 * 因為提交是寫操作，必須是「看清楚要求之後再按」的那一頁，
 * 不能在清單上順手按到。
 */
@Composable
fun HomeworkContent(
    repository: HomeworkRepository,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenHomework: (courseId: Int, homeworkId: Int, courseName: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val courseName by repository.courseName.collectAsState()
    val pageState = state.toPageState()
    val items = (state as? ListState.Content)?.items.orEmpty()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.refresh() }
    }

    AppList(modifier = modifier) {
        item { BackRow("返回", onBack) }
        if (courseName.isNotBlank()) {
            item { KeyValueRow("课程", courseName, labelWidth = Dims.labelColumnWide) }
        }

        if (items.isEmpty()) {
            item {
                HintText(
                    if (pageState == PageState.Empty) "这门课没有作业"
                    else "作业没有取到，请稍后重试。",
                )
            }
            item { ActionRow(title = "改用网页版", action = "打开", onClick = onOpenWeb) }
            return@AppList
        }

        infoItems(items, key = { it.homework.id }) { row ->
            val hw = row.homework
            val st = row.myStatus
            InfoItem(
                title = hw.title.ifBlank { "（无标题）" },
                subtitle = listOfNotNull(
                    "截止 ${hw.deadlineLocal}",
                    hw.submitLimitLabel,
                    hw.scorePercentage.toDoubleOrNull()?.takeIf { it > 0.0 }
                        ?.let { "占比 ${hw.scorePercentage}%" },
                    hw.homeworkType.takeIf { it.isNotBlank() },
                ).joinToString("　"),
                trailing = when {
                    // ★ 三種講法必須分開：
                    //   已交 / 未交 / **不知道**（狀態那支取不到）。
                    //   把「不知道」顯示成「未交」會讓人以為自己漏交了。
                    st == null -> null
                    else -> st.label
                },
                trailingColor = if (st?.submitted == true) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                },
                body = hw.plainDescription.takeIf { it.isNotBlank() },
                bodyMaxLines = 4,
                // ★ 點一行進詳情（业主 2026-10-19：「我要在待办里实现查看作业详情并且提交作业」）。
                onClick = {
                    onOpenHomework(repository.currentCourseId, hw.id, courseName, hw.title)
                },
            )
        }

        item {
            HintText(
                "点一行能看完整要求并直接交（正文提交）。" +
                    "需要传文件的作业还在用网页版 —— 那条上传链我们没验证过，不敢拿成绩试。",
            )
        }
        item { ActionRow(title = "网页版（交文件）", action = "打开", onClick = onOpenWeb) }
    }
}

/**
 * 某門課作業清單的**独立页**（顶栏 + [HomeworkContent]）。
 *
 * 存在的理由：`Route.Homework` 是一条真实的路由（首页/搜索都可能落到它），
 * 而路由页需要自己的顶栏。外殼裡那個二級頁仍然走 [HomeworkContent]
 * —— 一份实作，两个容器（同 [RollcallStationPage] 的做法）。
 */
@Composable
fun HomeworkListPage(
    repository: HomeworkRepository,
    courseName: String,
    onExit: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenHomework: (courseId: Int, homeworkId: Int, courseName: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "作业",
                color = Color.Transparent,
                subtitle = courseName.ifBlank { "学在城院" },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.size(Dims.iconNav),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        HomeworkContent(
            repository = repository,
            onBack = onExit,
            onOpenWeb = onOpenWeb,
            onOpenHomework = onOpenHomework,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        )
    }
}
