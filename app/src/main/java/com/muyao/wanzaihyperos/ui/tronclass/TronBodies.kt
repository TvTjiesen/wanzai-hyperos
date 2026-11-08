package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.HomeworkRepository
import com.muyao.wanzaihyperos.data.tronclass.RollcallRepository
import com.muyao.wanzaihyperos.data.tronclass.TronHomeRepository
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.cjh52.CjhChipFlow
import com.muyao.wanzaihyperos.ui.cjh52.CjhChipSpec
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.BackRow
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.infoItems
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
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

    val scope = rememberCoroutineScope()

    AppList(modifier = modifier) {
        item {
            TronHero(
                eyebrow = "LEARNING / 学在城院",
                title = "学习，有条不紊",
                sub = "课程、待交与课堂动态，各有归处。",
                icon = MarkIcon.Course,
            )
        }

        // 稿上的那一排 chip：课表 / 签到 / 刷新。
        // 「刷新」是真的刷 —— 這一頁的資料只有它自己那一份，
        // 按下就重新打一次接口；外殼那顆下拉刷新刷的是整頁（含這個板塊）。
        item {
            CjhChipFlow(
                modifier = Modifier.padding(horizontal = Dims.screenH),
                chips = listOf(
                    CjhChipSpec("学在课表", onOpenSchedule),
                    CjhChipSpec("签到站", onOpenRollcall),
                    CjhChipSpec(
                        "刷新",
                        { scope.launch { runCatching { repository.refresh() } } },
                    ),
                ),
            )
        }

        item {
            TronSection(
                text = "我的课程",
                icon = MarkIcon.Course,
                trailing = { SectionMeta("本学期 · ${home.courses.size} 门") },
            )
        }
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
                        ).joinToString(" · ")
                    },
                    onClick = { onOpenCourse(c) },
                )
            }
        }

        item {
            TronSection(
                text = "最近要交",
                icon = MarkIcon.Todo,
                trailing = { SectionMeta("截止优先") },
            )
        }
        if (home.todos.isEmpty()) {
            item { HintText("没有待交作业") }
        } else {
            infoItems(home.todos, key = { it.id }) { t ->
                InfoItem(
                    title = t.title,
                    subtitle = listOfNotNull(
                        t.courseName.takeIf { it.isNotBlank() },
                        t.localTimeText.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    // 右側掛截止時間：稿上這裡是「已有 N 次提交 / 尚未正式提交」，
                    // 但 `/api/todos` 不給提交次數（TronTodo 沒有這個欄位），
                    // 不知道就不能編 —— 只放能從這條資料算出來的截止時間。
                    trailing = t.localTimeText.takeIf { it.isNotBlank() },
                )
            }
        }

        item { TronSection(text = "完整学习入口", icon = MarkIcon.App) }
        item {
            CjhChipFlow(
                modifier = Modifier.padding(horizontal = Dims.screenH),
                chips = listOf(
                    CjhChipSpec("讨论 / 测验 / 学习记录网页版", onOpenWeb),
                ),
            )
        }

        item { HintText("来源：学在城院接口 · 以原站为准") }
    }
}

/** 區塊標題右邊那行小字（稿上的 `span.meta`）。 */
@Composable
private fun SectionMeta(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
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
    com.muyao.wanzaihyperos.ui.design.SecondaryScaffold(
        title = "作业",
        subtitle = courseName.ifBlank { "学在城院" },
        onBack = onExit,
        modifier = modifier,
        // 這一層沒有自己的資料：刷新由裡面的 [HomeworkContent] 自己管
        //（它已經有 LaunchedEffect 首刷 + 「改用網頁版」出口）。
        onRefresh = null,
    ) { innerPadding ->
        HomeworkContent(
            repository = repository,
            onBack = onExit,
            onOpenWeb = onOpenWeb,
            onOpenHomework = onOpenHomework,
            modifier = Modifier
                .fillMaxSize()
                // 安全區只讓一次：頂欄高度（含狀態列）由殼給。
                .padding(top = innerPadding.calculateTopPadding()),
        )
    }
}
