package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.data.tronclass.CourseDetailRepository
import com.muyao.wanzaihyperos.data.tronclass.TronBulletin
import com.muyao.wanzaihyperos.data.tronclass.TronCourseDetailBundle
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
import com.muyao.wanzaihyperos.ui.design.infoItems
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
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
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val bundle = (state as? ListState.Content)?.items?.firstOrNull()

    // 公告是否全部展開。切換課程（bundle 換人）時自動收合 ——
    // 否則會帶著上一門課的展開狀態進來。
    var showAllBulletins by remember(bundle?.course?.id) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.refresh() }
    }
    if (bundle == null) {
        StatePlaceholder(
            state = pageState,
            modifier = modifier,
            onAction = onOpenWeb,
            actionLabel = "改用网页版",
        )
        return
    }

    AppList(modifier = modifier) {
        item { BackRow("返回", onBack) }
        item { CourseHeader(bundle) }
        item { ScoreSection(bundle) }
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
        // 公告：**預設收起**，點標題展開全文（公告正文可能很長）。
        item { BulletinSection(bundle) }
        infoItems(
            bundle.bulletins.take(if (showAllBulletins) bundle.bulletins.size else PREVIEW_COUNT),
            key = { "b${it.id}" },
        ) { b ->
            BulletinItem(b)
        }

        // 考試 / 討論區：有才顯示（多數課沒有，出現空區塊只是噪音）。
        if (bundle.exams.isNotEmpty()) {
            item { SmallTitle(text = "考试（${bundle.exams.size}）") }
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
        if (bundle.topics.isNotEmpty()) {
            item { SmallTitle(text = "讨论区（${bundle.topics.size}）") }
            infoItems(bundle.topics, key = { "t${it.id}" }) { topic ->
                InfoItem(
                    title = topic.title.ifBlank { "（无标题讨论）" },
                    subtitle = listOfNotNull(
                        "${topic.topicCount} 条发言",
                        "回复 ${topic.replyCount}",
                        topic.endLocal.takeIf { it.isNotBlank() }?.let { "结束 $it" },
                    ).joinToString("　"),
                    trailing = topic.statusLabel,
                )
            }
            item {
                HintText("讨论区只能看“有哪些讨论”，看不了正文 —— 贴文内容要逐条单独取，这里不假装能读。")
            }
        }

        item { OutlineSection(bundle) }

        item {
            HintText("说明：作业提交、讨论、测验这些写操作仍走网页版（会记成绩的都不在这里做）。")
        }
        item { ActionRow(title = "更多功能", action = "改用网页版", onClick = onOpenWeb) }
    }
}

/** 公告在列表裡的預覽條數（其餘靠 `showAllBulletins` 展開）。 */
private const val PREVIEW_COUNT = 2

/** 頁首：課名 + 教師／學期／學分 + 課程信息鍵值行。 */
@Composable
private fun CourseHeader(bundle: TronCourseDetailBundle) {
    val detail = bundle.detail
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
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
        SmallTitle(text = "我的成绩")
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
        SmallTitle(text = "出勤（${bundle.attendance.size} 次记录）")
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
        SmallTitle(text = "作业（${bundle.homeworks.size}）")
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
        SmallTitle(text = "课程公告（${bundle.bulletins.size}）")
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
        SmallTitle(text = "课程大纲")
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
