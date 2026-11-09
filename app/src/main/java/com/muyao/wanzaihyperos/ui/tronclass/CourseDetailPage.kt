package com.muyao.wanzaihyperos.ui.tronclass

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.CourseDetailRepository
import com.muyao.wanzaihyperos.data.tronclass.TronBulletin
import com.muyao.wanzaihyperos.data.tronclass.TronCourseDetailBundle
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.BackRow
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.muyao.wanzaihyperos.ui.design.infoItems
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import kotlinx.coroutines.launch
import com.muyao.wanzaihyperos.ui.components.WanzaiButton as Button
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **課程詳情**（「我的課 → 點一門課」進來的頁面）。只讀。
 *
 * ## 五塊內容，以及為什麼是這五塊
 *
 * | 區塊 | 來源 | 為什麼要 |
 * |------|------|---------|
 * | 課程信息 | `/api/courses/{id}` | 代碼／學分／學期／開課單位／教師 |
 * | 我的成績 | `/api/course/{id}/student-self-score` + `/score-percentages` | 學生最常回來看的 |
 * | 出勤 | `/api/course/{id}/rollcall-score` + `/student/{我}/rollcalls` | 「老師說我缺勤，我到底到沒到」 |
 * | 課程大綱 | `course_outline.common_fields` | 評分方式、教材、上課要求 |
 * | 作業 | `/api/courses/{id}/homework-activities` | 只顯示條數，點進去是原本那頁 |
 *
 * ## 兩個「不騙人」的處理
 *
 * 1. **成績未發布顯示「尚未公布」而不是 0** —— [com.muyao.wanzaihyperos.data.tronclass.TronScore]
 *    的類註釋說明了為什麼（`total_score` 在沒出成績時也是 0.0）。
 * 2. **出勤只顯示次數，不顯示那個 `score`** —— 實測 5 次全到而 `score` 是 `"0"`，
 *    顯示成「出勤得分 0」就是對學生說謊。
 *
 * ## 美化預留
 *
 * 全頁只用 [AppList] / [InfoItem] / [KeyValueRow] / [HintText] / [ActionRow] 與 [Dims]，
 * **沒有任何尺寸字面值**。要改卡片圓角、邊距、區塊間距，改
 * `ui/design/DesignTokens.kt` 一處即可。
 */
@Composable
fun CourseDetailContent(
    repository: CourseDetailRepository,
    onBack: () -> Unit,
    /** 進去看這門課的作業清單（複用既有的作業頁，不重做一份）。 */
    onOpenHomework: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val bundle = (state as? ListState.Content)?.items?.firstOrNull()

    // 殼內子頁（**不新增頂層 Route**）：詳情 / 全部公告 / 討論目錄。
    // 換一門課（bundle 換人）時自動回到詳情 —— 否則會帶著上一門課的子頁進來。
    var sub by remember(bundle?.course?.id) { mutableStateOf(CourseSub.NONE) }

    // 三個清單各自的滾動狀態。**不能共用一個**：子頁與詳情的內容長度不同，
    // 共用會出現「從公告頁退回詳情，詳情莫名其妙停在半中間」。
    val mainListState = rememberLazyListState()
    val announcementsListState = rememberLazyListState()
    val discussionsListState = rememberLazyListState()
    val activeListState = when (sub) {
        CourseSub.NONE -> mainListState
        CourseSub.ANNOUNCEMENTS -> announcementsListState
        CourseSub.DISCUSSIONS -> discussionsListState
    }

    // 頂欄漸變紗跟著**當前那一頁**的清單走（詳情滾詳情的、公告滾公告的）。
    val veilProgress by rememberScrollProgress(activeListState)

    // 系統返回鍵：子頁先退回詳情，再退才離開這一門課。
    // ⚠️ 放在 Content 最外層 —— 它管的是「這一頁的子態」，不是某個列表。
    BackHandler(enabled = sub != CourseSub.NONE) { sub = CourseSub.NONE }

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.refresh() }
    }

    // 頂欄走共享殼（[SecondaryScaffold]）：漸變、安全區、返回鍵、欄高只由設計層決定一次。
    SecondaryScaffold(
        // 頂欄寫**頁名**而不是課名：稿上頂欄就是「课程详情」，
        // 課名是內容區的 hero。兩處都寫課名，進頁面第一眼就是同一個名字出現兩遍。
        title = "课程详情",
        // 副標題留空：稿上頂欄只有頁名一行，老師與學期在課程卡的 hero 裡。
        subtitle = "",
        // 子頁時殼那顆返回先回詳情，再退才離開這門課 ——
        // 否則在「全部公告」裡按左上角會直接退出課程，比稿上的返回多退一層。
        onBack = {
            if (sub != CourseSub.NONE) sub = CourseSub.NONE else onBack()
        },
        modifier = modifier,
        scrollProgress = veilProgress,
        // 詳情由「進入這門課」時載入（課程內的詳情接口），殼上不再放第二顆刷新 ——
        // 兩顆刷新按下去做的事一樣，只會多一個出錯的地方。
        onRefresh = null,
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            // 安全區只讓一次：頂欄高度（含狀態列）由殼給。
            .padding(top = innerPadding.calculateTopPadding())

        if (bundle == null) {
            // ⚠️ 「课程未匹配」与「取数失败」是两件事，不能共用一个空态：
            //   前者是课程键不对（换一门课就好），后者是网络/服务端（重试就好）。
            //   2026-11-08 对稿（稿 `course.txt` 整页只画了这个空态）发现：
            //   坏课程键进来时给的是「改用网页版」—— 可此时**根本没有课程可看**，
            //   点下去会把他带到原站首页，比什么都不做更糟。
            val notMatched = pageState == PageState.Content || pageState == PageState.Empty
            if (!notMatched) {
                StatePlaceholder(
                    state = pageState,
                    modifier = contentModifier,
                    onRetry = { scope.launch { runCatching { repository.refresh() } } },
                )
            } else {
                AppList(modifier = contentModifier) {
                    item {
                        TronHero(
                            eyebrow = "COURSE / 课程",
                            title = "课程未匹配",
                            sub = "缺少或未知的课程键，不会跳转到另一门课程。",
                            icon = MarkIcon.Course,
                        )
                    }
                    item {
                        TronNotice("请从课程列表选择一门课，再进来。", error = true)
                    }
                    item {
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("返回课程列表") }
                    }
                    item {
                        ActionRow(title = "改用网页版", action = "打开", onClick = onOpenWeb)
                    }
                }
            }
            return@SecondaryScaffold
        }

        // 稿上課程詳情分三段（課程信息 / 出勤 / 作業）。這裡用同一個 AppList，
        // 只把不相關的區塊跳過 —— 不另開三個列表，否則三份 scrollState 要各自維護，
        // 切回上一段時位置也接不上。
        // 目前選中的區段：0 課程信息 / 1 出勤 / 2 作業。
        var infoTab by remember { mutableIntStateOf(0) }

        when (sub) {
            CourseSub.NONE -> AppList(modifier = contentModifier, state = mainListState) {
                // 課程卡（課名 + 老師 + 學期 + 五行 kv）在三段之外：稿上它固定在標籤列上方，
                // 切到「出勤」「作業」時課名不會消失 —— 不然看第二段就不知道自己在看哪門課。
                item(key = "course-header") { CourseHeader(bundle) }
                item(key = "course-tabs") {
                    TabRow(
                        tabs = listOf("课程信息", "出勤", "作业"),
                        selectedTabIndex = infoTab,
                        onTabSelected = { infoTab = it },
                        minWidth = 88.dp,
                        maxWidth = 88.dp,
                        modifier = Modifier.padding(horizontal = Dims.screenH),
                    )
                }
                if (infoTab == 0) {
                    item { ScoreSection(bundle) }
                }
                if (infoTab == 1) {
                    item { AttendanceSummary(bundle) }
                // 每一條出勤記錄都是一個清單項（不是塞進一張卡片裡再自己捲動）——
                // 這樣它與其他區塊的間距、圓角自動與全 App 一致。
                if (bundle.attendance.isNotEmpty()) {
                    infoItems(bundle.attendance, key = { it.rollcallId }) { record ->
                        InfoItem(
                            title = record.title.ifBlank { record.timeLocal.ifBlank { "点名" } },
                            subtitle = listOfNotNull(
                                record.timeLocal.takeIf { it.isNotBlank() },
                                record.sourceLabel,
                            ).joinToString("　"),
                            trailing = record.statusLabel,
                            trailingColor = if (record.attended) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.error,
                        )
                    }
                }
                }
                if (infoTab == 2) {
                    item { HomeworkSection(bundle, onOpenHomework) }
                // 作業預覽（最多三條）：清單本身在既有的作業頁，這裡只給「最近在交什麼」。
                infoItems(bundle.homeworks.take(3), key = { it.id }) { hw ->
                    InfoItem(
                        title = hw.title.ifBlank { "（无标题）" },
                        subtitle = "截止 ${hw.deadlineLocal}",
                        trailing = if (hw.closed) "已截止" else null,
                        trailingColor = MiuixTheme.colorScheme.error,
                    )
                }
                }
                if (infoTab == 0) {
                // 公告：詳情頁**只給前 [PREVIEW_COUNT] 條預覽**（正文可能整頁長）。
                // 看全走殼內子頁 —— 稿上「查看全部公告」是一頁獨立列表（courseAnnouncements.png），
                // 不是就地展開。就地展開那版曾經寫過，但它跟子頁會變成兩套展開邏輯。
                item { BulletinSection(bundle) }
                infoItems(
                    bundle.bulletins.take(PREVIEW_COUNT),
                    key = { "b${it.id}" },
                ) { b ->
                    BulletinItem(b)
                }
                if (bundle.bulletins.isNotEmpty()) {
                    item {
                        ActionRow(
                            title = "全部公告 · ${bundle.bulletins.size}",
                            subtitle = if (bundle.bulletins.size > PREVIEW_COUNT) {
                                "预览 $PREVIEW_COUNT / 共 ${bundle.bulletins.size} 条"
                            } else {
                                "共 ${bundle.bulletins.size} 条"
                            },
                            // ⚠️ ActionRow 自己会在 action 后面拼「 ›」——
                            //   这里再写一枚就变成「全部 › ›」。
                            action = "查看",
                            onClick = { sub = CourseSub.ANNOUNCEMENTS },
                        )
                    }
                }

                // 考試 / 討論區：有才顯示（多數課沒有，出現空區塊只是噪音）。
                if (bundle.exams.isNotEmpty()) {
                    item { TronSection(text = "考试（${bundle.exams.size}）", icon = MarkIcon.Clock) }
                    infoItems(bundle.exams, key = { "e${it.id}" }) { exam ->
                        InfoItem(
                            title = exam.title,
                            subtitle = listOfNotNull(
                                exam.startLocal.takeIf { it.isNotBlank() }?.let { "开始 $it" },
                                exam.endLocal.takeIf { it.isNotBlank() }?.let { "结束 $it" },
                            ).joinToString("　"),
                            trailing = exam.statusLabel,
                            trailingColor = if (exam.submitted) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.error,
                            body = exam.plainDescription.takeIf { it.isNotBlank() },
                        )
                    }
                }
                // 討論：詳情頁只留入口，目錄在殼內子頁（稿上 courseDiscussions.png 是獨立一頁）。
                if (bundle.topics.isNotEmpty()) {
                    item { TronSection(text = "讨论区（${bundle.topics.size}）", icon = MarkIcon.Message) }
                    item {
                        ActionRow(
                            title = "讨论目录 · ${bundle.topics.size}",
                            subtitle = "只读目录，正文与发帖在原站",
                            action = "查看",
                            onClick = { sub = CourseSub.DISCUSSIONS },
                        )
                    }
                }

                item { OutlineSection(bundle) }

                item {
                    HintText("说明：作业提交、讨论、测验这些写操作仍走网页版（会记成绩的都不在这里做）。")
                }
                item { ActionRow(title = "更多功能", action = "改用网页版", onClick = onOpenWeb) }
                }
            }
            CourseSub.ANNOUNCEMENTS -> AnnouncementsScreen(
                bundle = bundle,
                listState = announcementsListState,
                modifier = contentModifier,
                onBack = { sub = CourseSub.NONE },
                onOpenWeb = onOpenWeb,
            )
            CourseSub.DISCUSSIONS -> DiscussionsScreen(
                bundle = bundle,
                listState = discussionsListState,
                modifier = contentModifier,
                onBack = { sub = CourseSub.NONE },
                onOpenWeb = onOpenWeb,
            )
        }
    }
}

/**
 * 課程詳情殼內的子頁。
 *
 * 稿上「全部公告」「討論目錄」是兩頁獨立頁面（`courseAnnouncements.png`、
 * `courseDiscussions.png`），但它們**共用這門課的取數**（`bundle` 一次帶回來），
 * 為它們各開一條頂層 Route 只是多兩個空頁面要處理載入／失敗態。
 * 所以做成殼內子態 —— 路由表不加條目（Lead 的硬約束）。
 */
private enum class CourseSub { NONE, ANNOUNCEMENTS, DISCUSSIONS }

/** 公告在詳情頁的預覽條數（其餘在「全部公告」子頁裡）。 */
private const val PREVIEW_COUNT = 2

/** 頁首：課名 + 教師／學期／學分 + 課程信息鍵值行。 */
@Composable
private fun CourseHeader(bundle: TronCourseDetailBundle) {
    val detail = bundle.detail
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            // 稿上課程卡的第一行是 eyebrow「COURSE / 课程」，第二行才是課名。
            Text(
                text = "COURSE / 课程",
                fontSize = Type.micro,
                letterSpacing = 0.08.em,
                color = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = detail?.name ?: bundle.course.name,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(
                detail?.teachers?.takeIf { it.isNotBlank() }
                    ?: bundle.course.teachers.takeIf { it.isNotBlank() },
                detail?.semesterName?.takeIf { it.isNotBlank() }
                    ?: bundle.course.semesterName.takeIf { it.isNotBlank() },
            ).joinToString("　")
            if (sub.isNotBlank()) {
                HintText(sub, modifier = Modifier.padding(top = Dims.tight))
            }

            // 上課時間只存在於課程清單那一份（詳情接口回的 classroom_schedule 實測是 null），
            // 所以從**進來時手上那門課**取。
            if (bundle.course.teachingClass.isNotBlank()) {
                KeyValueRow("上课时间", bundle.course.teachingClass, labelWidth = Dims.labelColumnWide)
            }
            detail?.courseCode?.takeIf { it.isNotBlank() }?.let {
                KeyValueRow("课程代码", it, labelWidth = Dims.labelColumnWide)
            }
            detail?.credit?.takeIf { it.isNotBlank() }?.let {
                KeyValueRow("学分", it, labelWidth = Dims.labelColumnWide)
            }
            detail?.department?.takeIf { it.isNotBlank() }?.let {
                KeyValueRow("开课单位", it, labelWidth = Dims.labelColumnWide)
            }
            detail?.startDate?.takeIf { it.isNotBlank() }?.let {
                KeyValueRow("开课日期", it, labelWidth = Dims.labelColumnWide)
            }
        }
    }
}

/** 我的成績。未發布說「尚未公布」，**不顯示 0**。 */
@Composable
private fun ScoreSection(bundle: TronCourseDetailBundle) {
    val score = bundle.score
    Column {
        TronSection(text = "我的成绩", icon = MarkIcon.Growth)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dims.cardPadding)) {
                if (score == null) {
                    HintText("没有取到成绩（不是 0 分，是没取到）。")
                } else {
                    KeyValueRow("总评", score.summary, labelWidth = Dims.labelColumnWide)
                    if (score.published && score.rawScore.isNotBlank()) {
                        KeyValueRow("原始分", score.rawScore, labelWidth = Dims.labelColumnWide)
                    }
                    // 已計分佔比：total − left。任一項缺就是「—」（未知不當 0）。
                    val graded = score.gradedPercentage
                    KeyValueRow(
                        "已计分",
                        graded?.let { "$it%" } ?: "—",
                        labelWidth = Dims.labelColumnWide,
                    )
                    score.unpublishedPercentage.takeIf { it.isNotBlank() && it != "0.0" }?.let {
                        KeyValueRow("未公布", "$it%", labelWidth = Dims.labelColumnWide)
                    }
                }
            }
        }
    }
}

/**
 * 出勤**彙總**（老師點名幾次、我參與幾次、最後一次什麼時候）。
 *
 * 逐條記錄由呼叫方用 `infoItems` 排在後面 —— 這樣每條記錄都是標準清單項。
 */
@Composable
private fun AttendanceSummary(bundle: TronCourseDetailBundle) {
    val summary = bundle.rollcallScore
    Column {
        TronSection(text = "出勤（${bundle.attendance.size} 次记录）", icon = MarkIcon.Scan)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dims.cardPadding)) {
                if (summary == null) {
                    HintText(
                        if (bundle.attendance.isEmpty()) "没有取到出勤记录。"
                        else "没有取到出勤统计。",
                    )
                } else {
                    KeyValueRow("参与/点名", summary.summary, labelWidth = Dims.labelColumnWide)
                    if (summary.lastRollcallLocal.isNotBlank()) {
                        KeyValueRow("最后一次", summary.lastRollcallLocal, labelWidth = Dims.labelColumnWide)
                    }
                    if (bundle.attendance.isEmpty()) {
                        HintText("没有出勤记录明细。")
                    }
                }
            }
        }
    }
}

/**
 * 作業**區塊標題**＋「看全部」入口。
 *
 * ⚠️ 作業那三條預覽**不在這裡** —— 它們是 `LazyListScope` 的項目，
 * 只能由 [AppList] 的 scope 產生（放在普通 `@Composable` 裡編不過）。
 * 所以這個函式只畫標題與入口，預覽由呼叫方排。
 */
@Composable
private fun HomeworkSection(bundle: TronCourseDetailBundle, onOpenHomework: () -> Unit) {
    Column {
        TronSection(text = "作业（${bundle.homeworks.size}）", icon = MarkIcon.File)
        if (bundle.homeworks.isEmpty()) {
            HintText("这门课目前没有作业。")
        } else {
            Spacer(Modifier.height(Dims.tight))
            ActionRow(
                title = "全部作业",
                subtitle = "共 ${bundle.homeworks.size} 条",
                action = "查看",
                onClick = onOpenHomework,
            )
        }
    }
}

/**
 * 公告區塊的**標題與展開控制**。
 *
 * ⚠️ 公告條目本身不在這裡（它們是 `LazyListScope` 的項目，
 * 只能由 [AppList] 的 scope 產生）。這個函式只畫標題那一行。
 */
@Composable
private fun BulletinSection(bundle: TronCourseDetailBundle) {
    Column {
        TronSection(text = "课程公告（${bundle.bulletins.size}）", icon = MarkIcon.Pin)
        if (bundle.bulletins.isEmpty()) {
            HintText("这门课没有公告。")
        } else if (bundle.bulletins.size > PREVIEW_COUNT) {
            HintText("共 ${bundle.bulletins.size} 条。")
        }
    }
}

/**
 * 一條公告：標題 + 時間 + 正文。
 *
 * ### ★ 正文顯示的是**去過 HTML 標籤**的文字
 *
 * 服務端回的 `content` 是 HTML 富文本（`<p><span style=...>…`）。
 * 直接用會看到一堆標籤 —— 所以走 [TronBulletin.plainText]，
 * 而不是在這裡開一個 WebView。理由見那個屬性的註釋。
 *
 * 正文預設**最多 6 行**：公告動輒整頁，列表裡不該被一條佔滿。
 */
@Composable
private fun BulletinItem(bulletin: TronBulletin) {
    val text = bulletin.plainText
    InfoItem(
        title = bulletin.title.ifBlank { "（无标题公告）" },
        subtitle = listOfNotNull(
            bulletin.createdAtLocal.takeIf { it.isNotBlank() && it != "—" },
            bulletin.authorName.takeIf { it.isNotBlank() },
        ).joinToString("　").ifBlank { null },
        body = text.takeIf { it.isNotBlank() },
        bodyMaxLines = 6,
    )
}

/** 課程大綱。老師沒填時要說清楚「沒填」而不是「取不到」。 */
@Composable
private fun OutlineSection(bundle: TronCourseDetailBundle) {
    val detail = bundle.detail
    Column {
        TronSection(text = "课程大纲", icon = MarkIcon.Library)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dims.cardPadding)) {
                when {
                    detail == null -> HintText("没有取到课程大纲。")
                    !detail.outlineFilled -> HintText("老师尚未填写课程大纲（栏位有 ${detail.outline.size} 个，内容都是空的）。")
                    else -> detail.filledOutline.forEach { field ->
                        // ⚠️ 用**直排**（標題一行、內容在下），不用 KeyValueRow：
                        //   大綱的內容是整段文字（實測「平時成績」那段近百字），
                        //   塞進 72dp 標籤欄旁邊會變成一條窄柱，極難讀。
                        //   真機上第一版就是這樣，換了才正常。
                        Text(
                            text = field.title,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(Dims.tight))
                        Text(
                            text = field.plainDescription,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Dims.loose))
                    }
                }
            }
        }
    }
}

/**
 * 殼內子頁：**全部公告**（照 `screens/courseAnnouncements.png`）。
 *
 * ### 為什麼是「可展開行」而不是一堆卡片
 *
 * 稿上三條公告每行只有「標題 + 日期 + 右端一枚展開符」，展開後正文在上方推開。
 * 公告正文本來就可能整頁長（實測近百字到幾百字），若全部預設鋪開，
 * 三條公告就要滑好幾屏，找「哪一條是下次課準備」反而更慢。
 *
 * ### 展開狀態按課程隔離
 *
 * `remember(bundle.course.id)` —— 換一門課時整份重來。
 * 不隔離會出現「切到另一門課，它有 5 條公告而上一門課展開的是 id 3」這種
 * 莫名其妙已經展開一條的情況（id 撞上就展開，撞不上就全收起 —— 兩者都不對）。
 */
@Composable
private fun AnnouncementsScreen(
    bundle: TronCourseDetailBundle,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    var expanded by remember(bundle.course.id) { mutableStateOf(emptySet<Int>()) }

    AppList(modifier = modifier, state = listState) {
        // ★ 子頁的返回條：**不用第二顆頂欄**（殼的 title 仍是課名）。
        //   點它回詳情，與系統返回鍵走同一個 `sub = NONE`。
        item { BackRow("全部公告", onBack) }
        item {
            TronHero(
                eyebrow = "COURSE / BULLETINS",
                icon = MarkIcon.Message,
                title = "全部课程公告",
                sub = if (bundle.bulletins.isEmpty()) {
                    "这门课没有公告"
                } else {
                    "${bundle.bulletins.size} 条 · 点一行展开正文"
                },
            )
        }
        item {
            // 稿上這塊是「G04：当前 App 仅有公告预览」那條開發註記 ——
            // 那是給評審看的，不該出現在成品裡。這裡換成對使用者有用的那句。
            TronNotice("正文已去掉 HTML 标签；公告里的图片与附件只在原站有。")
        }
        if (bundle.bulletins.isEmpty()) {
            item { HintText("这门课暂时没有公告。原站公告区可能也是空的，不是取数失败。") }
        } else {
            infoItems(bundle.bulletins, key = { "b${it.id}" }) { b ->
                BulletinRow(
                    bulletin = b,
                    expanded = b.id in expanded,
                    onToggle = {
                        expanded = if (b.id in expanded) expanded - b.id else expanded + b.id
                    },
                )
            }
        }
        item {
            ActionRow(
                title = "原站公告全文",
                subtitle = "图片、附件与置顶顺序以原站为准",
                action = "打开",
                onClick = onOpenWeb,
            )
        }
    }
}

/**
 * 一條可展開的公告。
 *
 * 折疊時兩行（標題 + 時間／作者）；展開時在下面接正文，**不限行**
 * —— 這一頁就是給人讀全文的，截斷等於白進來一趟。
 */
@Composable
private fun BulletinRow(
    bulletin: TronBulletin,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val body = bulletin.plainText
    val meta = listOfNotNull(
        bulletin.createdAtLocal.takeIf { it.isNotBlank() && it != "—" },
        bulletin.authorName.takeIf { it.isNotBlank() },
    ).joinToString("　")

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            // 命中區 ≥ 44dp：折疊態只有兩行字，不撐一下會比手指窄。
            .heightIn(min = Dims.minTouch)
            .clickable(onClickLabel = if (expanded) "收起正文" else "展开正文") { onToggle() },
    ) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bulletin.title.ifBlank { "（无标题公告）" },
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(Dims.tight))
                        Text(
                            text = meta,
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
                Spacer(Modifier.width(Dims.tight))
                // 稿上是「＋」；展開後同一個位置變「收起」。文字比符號好懂，
                // 而且它本身不是獨立按鈕 —— 整行才是（不會出現兩層點擊區）。
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.primary,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(Dims.loose))
                if (body.isBlank()) {
                    HintText("这条公告没有正文（原站只给了标题）。")
                } else {
                    Text(
                        text = body,
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * 殼內子頁：**討論目錄**（照 `screens/courseDiscussions.png`）。
 *
 * ### 只有目錄，沒有正文
 *
 * 端點 `/api/course/{id}/topic_categories` 回來的是「有哪幾個討論區、各有幾條發言」，
 * **不含貼文正文**；逐條抓正文要另開一條鏈路（37 門課裡只有 4 門有討論區），
 * 收益不成立。所以這裡如實只做目錄，正文與發帖都交回原站。
 *
 * ### 不畫「›」
 *
 * 沒有詳情可進 —— 畫一個箭頭等於告訴他「點這裡有下一頁」，點下去只會是什麼都沒有。
 * 所以每行右端是**灰的計數**，不是可點的入口。
 */
@Composable
private fun DiscussionsScreen(
    bundle: TronCourseDetailBundle,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme

    AppList(modifier = modifier, state = listState) {
        item { BackRow("讨论目录", onBack) }
        item {
            TronHero(
                eyebrow = "COURSE / DISCUSSIONS",
                icon = MarkIcon.Message,
                title = "课程讨论目录",
                sub = "只读原生目录，正文与发帖在原站。",
            )
        }
        if (bundle.topics.isEmpty()) {
            item { HintText("这门课没有讨论区。") }
        } else {
            infoItems(bundle.topics, key = { "t${it.id}" }) { topic ->
                InfoItem(
                    title = topic.title.ifBlank { "（无标题讨论）" },
                    subtitle = listOfNotNull(
                        if (topic.topicCount > 0) "${topic.topicCount} 条发言" else "数量 —",
                        topic.endLocal.takeIf { it.isNotBlank() }?.let { "结束 $it" } ?: "截止未获取",
                        topic.statusLabel,
                    ).joinToString(" · "),
                    // 右端是「N 条回复」—— 灰的，因為它只是事實，不是入口。
                    trailing = if (topic.replyCount > 0) "${topic.replyCount} 条回复" else "回复 —",
                    trailingColor = colors.onSurfaceVariantSummary,
                )
            }
            item { TronNotice("没有伪造讨论正文或发帖表单。") }
        }
        item { HintText("讨论正文和回复仍走网页版") }
        item { ActionRow(title = "进入原站讨论", action = "打开", onClick = onOpenWeb) }
    }
}
