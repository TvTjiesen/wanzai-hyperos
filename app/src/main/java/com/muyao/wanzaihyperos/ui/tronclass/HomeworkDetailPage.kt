package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.HomeworkDetail
import com.muyao.wanzaihyperos.data.tronclass.HomeworkDetailRepository
import com.muyao.wanzaihyperos.data.tronclass.HomeworkSubmitResult
import com.muyao.wanzaihyperos.data.tronclass.TronSubmission
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.Type
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一項作業的**詳情 + 提交**（业主 2026-10-19：「我要在待办里实现查看作业详情并且提交作业」）。
 *
 * ## 为什么「看」和「交」在同一页，但提交要走一次确认
 *
 * 看清楚要求之后才谈得上交 —— 分开两页会让人在没读要求的情况下按下提交。
 * 但提交是**会记成绩的写操作**，所以按下去不直接发：
 * 先弹一次确认（把「交几次、什么时候截止、这是第几版」在那一屏再讲一遍）。
 *
 * ## ★ 这一页刻意没有的东西
 *
 * 1. **没有传文件**。上传那条链是 `POST /api/uploads` → 直传存储 →
 *    `PUT /api/uploads/{id}/uploaded` 三跳，中间那跳的域名与参数
 *    我们**没有验证过**（见 [com.muyao.wanzaihyperos.data.tronclass.TronSubmission] 的類註釋）。
 *    在这一页放一颗「选文件提交」的按钮，最坏情况是他以为交了、其实没交。
 *    所以界面上明说这类作业走网页版，并给出口。
 * 2. **没有撤回**。撤回的请求要把**原来那一版的全部内容**（含附件）回传给服务端
 *    （源碼 `toPlain(t, xh)`），我们只读得到正文与附件的**引用**，
 *    用空附件去撤回有把记录改坏的风险。成绩相关的东西不试。
 * 3. **不显示编的节次时间／不猜要求**。要求正文取不到时直说取不到。
 */
@Composable
fun HomeworkDetailPage(
    repository: HomeworkDetailRepository,
    courseId: Int,
    homeworkId: Int,
    courseName: String,
    title: String,
    onExit: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    // 清單的滾動狀態提到這裡：頂欄漸變紗要靠它算進度（見 SecondaryScaffold 的
    // scrollProgress 參數說明）。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val veilProgress by com.muyao.wanzaihyperos.ui.design.rememberScrollProgress(listState)

    com.muyao.wanzaihyperos.ui.design.SecondaryScaffold(
        // 兜底文案對稿：稿的 top-title 就是「作業詳情與提交」。
        // （有作業名時仍優先用作業名 —— 那一行比「作業詳情」有用得多。）
        title = title.ifBlank { "作业详情与提交" },
        subtitle = courseName.ifBlank { "学在城院 · 作业" },
        onBack = onExit,
        modifier = modifier,
        scrollProgress = veilProgress,
        onRefresh = {
            scope.launch {
                if (courseId > 0) {
                    runCatching { repository.load(courseId, homeworkId, courseName, title) }
                }
            }
        },
    ) { innerPadding ->
        HomeworkDetailBody(
            repository = repository,
            onOpenWeb = onOpenWeb,
            listState = listState,
            modifier = Modifier
                .fillMaxSize()
                // 安全區只讓一次：頂欄高度（含狀態列）由殼給。
                .padding(top = innerPadding.calculateTopPadding()),
        )
    }
}

/**
 * 詳情的**內容**（不含頂欄）。
 *
 * 拆出來與 [RollcallBody] 同一個理由：外殼（學在城院內部的二級頁）與
 * 從待辦直接進來的獨立頁共用一份，不出現「一處修了另一處還錯」。
 */
@Composable
fun HomeworkDetailBody(
    repository: HomeworkDetailRepository,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    header: LazyListScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val detail = (state as? ListState.Content)?.items?.firstOrNull()

    var draft by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<HomeworkNotice?>(null) }
    var submitting by remember { mutableStateOf(false) }
    /** 按了提交先确认；这里存的是**确认要发的那一种**（正式/草稿）。 */
    var confirmDraft by remember { mutableStateOf<Boolean?>(null) }

    // 触觉：只有"把请求发出去 / 服务端认了 / 服务端拒了"这三刻（同签到页那条纪律）。
    // 交作业是会进成绩的动作，反馈必须**和画面同一帧**，不能等下一次重组才补。
    val haptics = com.muyao.wanzaihyperos.ui.design.rememberHaptics()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.refresh() }
    }

    if (detail == null) {
        val error = (state as? ListState.Failed)?.error?.userText
        AppList(modifier = modifier, state = listState) {
            header()
            item {
                StatePlaceholder(
                    state = pageState,
                    detail = error,
                    onRetry = { scope.launch { runCatching { repository.refresh() } } },
                )
            }
            item { ActionRow(title = "改用网页版", action = "打开", onClick = onOpenWeb) }
        }
        return
    }

    AppList(modifier = modifier, state = listState) {
        header()

        item {
            notice?.let { n ->
                ResultBanner(message = n.text, ok = n.ok)
                Spacer(Modifier.height(Dims.cardGap))
            }
        }

        // ── 頁面級的三行：eyebrow / 作業名 / 課程名 ─────────────────
        // 稿上這三行在頂欄下面、卡片上面。原本作業名畫在卡片裡，
        // 補 hero 之後若還留著，同一句話會出現兩次 —— 所以標題提到這裡。
        item {
            TronHero(
                eyebrow = "ASSIGNMENT / 作业",
                title = detail.title.ifBlank { "作业详情与提交" },
                sub = detail.courseName.ifBlank { "学在城院 · 作业" },
                icon = MarkIcon.File,
            )
        }

        // ── 一眼要看到的三件事：交没交、截止、还能交几次 ───────────
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Dims.cardPadding)) {
                    // 稿上標題下面那顆膠囊：「已有 N 次正式提交 · 可查看版本」。
                    // 它回答的是學生最常問的一句 ——「我到底交沒交成、還能不能回看」。
                    if (detail.submittedCount > 0) {
                        SubmittedPill(detail.submittedCount)
                        Spacer(Modifier.height(Dims.tight))
                    }
                    StatusLine(detail)
                    Spacer(Modifier.height(Dims.loose))
                    val hw = detail.homework
                    if (hw == null) {
                        HintText(
                            if (detail.listFailed) "作业要求没取到（下面那一支接口失败），" +
                                "所以截止时间与可交次数都不知道。"
                            else "在这门课的作业清单里没找到这一项，" +
                                "所以不看要求、也不提交（怕提交到错的地方）。",
                        )
                    } else {
                        KeyValueRow("截止时间", hw.deadlineLocal, labelWidth = Dims.labelColumnWide)
                        KeyValueRow("可交次数", hw.submitLimitLabel, labelWidth = Dims.labelColumnWide)
                        // 「形式 / 占比」稿上是一條：這兩項都是「這份作業長什麼樣」，
                        // 拆兩行反而看不出「個人正文與附件 / 10%」的對應關係。
                        val pct = hw.scorePercentage.toDoubleOrNull()?.takeIf { it > 0.0 }
                        KeyValueRow(
                            label = "形式 / 占比",
                            value = listOfNotNull(
                                hw.homeworkType.takeIf { it.isNotBlank() },
                                pct?.let { "${hw.scorePercentage}%" },
                            ).joinToString(" / ").ifBlank { "—" },
                            labelWidth = Dims.labelColumnWide,
                        )
                        // 稿上第四條 kv。原本「小組作業」只在**是小組作業時**
                        // 冒出一行 HintText，於是「不是小組作業」的人看不到這個欄位。
                        KeyValueRow(
                            label = "小组作业",
                            value = if (hw.submitByGroup) "是" else "否",
                            labelWidth = Dims.labelColumnWide,
                        )
                        if (hw.submitByGroup) {
                            Spacer(Modifier.height(Dims.tight))
                            HintText("这是小组作业：以小组为单位交，个人提交可能被服务器拒绝。")
                        }
                    }
                }
            }
        }

        // ── 要求正文 ──────────────────────────────────────────────
        detail.homework?.plainDescription?.takeIf { it.isNotBlank() }?.let { req ->
            item { TronSection(text = "作业要求", icon = MarkIcon.Pin) }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = req,
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurface,
                        modifier = Modifier.padding(Dims.cardPadding),
                    )
                }
            }
        } ?: detail.homework?.let {
            item { HintText("这项作业没有填写要求说明。") }
        }

        // ── 我交过的每一版 ────────────────────────────────────────
        item {
            TronSection(
                text = "我的提交（${detail.submissions.size}）",
                icon = MarkIcon.File,
                trailing = {
                    // 稿上這一行右邊那句 —— 「我交了 3 次」到底算不算草稿，
                    // 是學生判斷「交夠了沒有」的唯一依據。
                    Text(
                        text = "草稿不计正式次数",
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                    )
                },
            )
        }
        if (!detail.submissionsKnown) {
            item {
                HintText(
                    if (detail.userId == null) "没取到我的编号，拿不到提交记录 —— " +
                        "所以“交过几版”不知道，**不等于没交过**。"
                    else "提交记录没取到，稍后下拉重试。这一项不影响要求与截止时间。",
                )
            }
        } else if (detail.submissions.isEmpty()) {
            item { HintText("还没有提交记录。") }
        } else {
            detail.submissions.forEach { s ->
                item { SubmissionRow(s) }
            }
        }

        // ── 提交区 ────────────────────────────────────────────────
        item { TronSection(text = "提交正文", icon = MarkIcon.File) }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Dims.cardPadding)) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = "我的正文",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Miuix 0.9.4 的 TextField **没有** isError/supportingText 参数，
                    // 所以提示与失败原因一律放在它下面一行（见 miuix-skill 的
                    // component-selection.md「TextField validation」）。
                    val reason = detail.submitBlockReason
                    if (reason != null) {
                        Spacer(Modifier.height(Dims.tight))
                        Text(
                            text = reason,
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.error,
                        )
                    }
                    Spacer(Modifier.height(Dims.sectionGap))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            text = "保存草稿",
                            onClick = { confirmDraft = true },
                            enabled = !submitting && reason == null && draft.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { confirmDraft = false },
                            enabled = !submitting && reason == null && draft.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            // 「正式」二字是稿子特意拿來和「草稿」對立的：
                            // 「提交作業」讀不出這層對立，學生會以為和存草稿同一量級。
                            Text(if (submitting) "提交中…" else "正式提交")
                        }
                    }
                    Spacer(Modifier.height(Dims.loose))
                    HintText(
                        "只能交正文。要交文件的作业请用网页版 —— " +
                            "那条上传链我们没验证过，不敢拿成绩试。",
                    )
                }
            }
        }

        // ── 需要文件或撤回？──────────────────────────────────────
        // 稿上這是一整塊（標題 + 兩顆按鈕 + 一句「原站入口完整保留」）。
        // 原本只有一顆藏在頁尾的 ActionRow —— 學生會把它當次要設置，
        // 而它其實是「交附件 / 撤回」的唯一入口。
        // 兩顆都跳網頁版：上傳（FileProvider 授權）與撤回（學校側窗口）
        // 兩條鏈本專案都沒驗證過，不拿成績試。
        item { TronSection(text = "需要文件或撤回？", icon = MarkIcon.Warn) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "上传作业附件",
                    onClick = onOpenWeb,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "撤回正式提交",
                    onClick = onOpenWeb,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            TronNotice("原站上传和撤回入口完整保留；本 App 不改变任何学校作业。")
        }

        // ── 來源標註 ─────────────────────────────────────────────
        // 這一頁有「提交」這種不可逆動作。稿上寫的是演示時間；
        // 我們讀的是真接口，所以改成真的來源（寫「本地模擬樣例」才是造假）。
        item {
            Text(
                text = "来源：学在城院接口 · 以原站为准",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
        }
        item { Spacer(Modifier.height(Dims.sectionGap)) }
    }

    // 写操作确认：把「这是第几版、截止什么时候、还能不能交」再讲一遍。
    //
    // ★ 「没带附件」这一句是**照抄网页版的行为**：官方 H5 的写作业页
    //   在只有正文、没有附件时也会先弹一次确认（源码 `preSubmit` →
    //   `showConfirm(submission.operationAttachment)`），确认后才发请求。
    //   我们抄它，是因为这一句正好是他做决定需要的信息 ——
    //   学校 33/33 项作业的形式都是 `file_upload`（docs/B15-WALKTHROUGH.md），
    //   「只交正文」在这所学校必须让他自己知道并同意。
    OverlayDialog(
        show = confirmDraft != null,
        onDismissRequest = { confirmDraft = null },
        title = if (confirmDraft == true) "保存草稿？" else "正式提交？",
        summary = buildString {
            val asDraft = confirmDraft == true
            append(if (asDraft) "草稿不会交上去，也不占可交次数。" else "提交后由老师评分。")
            detail.homework?.takeIf { it.homeworkType.contains("file", true) }?.let {
                append("\n注意：这项作业要求上传文件（形式 ${it.homeworkType}），" +
                    "现在只交正文，老师那边看不到文件。")
            }
            append("\n课程：${detail.courseName.ifBlank { "—" }}")
            append("\n作业：${detail.title.ifBlank { "（无标题）" }}")
            append("\n已经正式交过 ${detail.submittedCount} 次")
            detail.homework?.let { append(" · ${it.submitLimitLabel}") }
            append("\n正文 ${draft.length} 个字，附件 0 个")
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "取消",
                onClick = { confirmDraft = null },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val asDraft = confirmDraft == true
                    confirmDraft = null
                    submitting = true
                    haptics.commit()
                    scope.launch {
                        val result: HomeworkSubmitResult =
                            runCatching { repository.submit(draft, asDraft) }
                                .getOrElse { e ->
                                    HomeworkSubmitResult(
                                        ok = false, httpStatus = 0,
                                        message = "没发出去：${e.javaClass.simpleName}",
                                    )
                                }
                        notice = HomeworkNotice(
                            ok = result.ok,
                            text = when {
                                result.ok && asDraft -> "草稿已保存"
                                result.ok -> "提交成功"
                                else -> result.message
                            },
                        )
                        submitting = false
                        if (result.ok) haptics.success() else haptics.failure()
                        // ★ 只有成功才刷新：失败时刷新会把错误横幅下面那一屏改写掉，
                        //   他来不及看清是哪一条规则没过。
                        if (result.ok) {
                            draft = ""
                            runCatching { repository.refresh() }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (confirmDraft == true) "保存草稿" else "确定提交") }
        }
    }
}

/** 提交区的一次提示（null = 没有）。 */
private data class HomeworkNotice(val ok: Boolean, val text: String)

/** 「交了没」那一行：已交 / 未交 / **不知道** 三种说法分开。 */
@Composable
private fun StatusLine(detail: HomeworkDetail) {
    val colors = MiuixTheme.colorScheme
    val text = when {
        // 版數交給上面那顆膠囊講，這裡只講「交沒交」——
        // 兩行都寫「第 N 版」等於同一件事說兩遍。
        detail.myStatus?.submitted == true -> "已交"
        detail.myStatus?.submitted == false -> "未交"
        else -> "交了没：不知道（状态那支没取到）"
    } + if (detail.draftCount > 0) "　有 ${detail.draftCount} 份草稿" else ""
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = when (detail.myStatus?.submitted) {
            true -> colors.primary
            false -> colors.error
            null -> colors.onSurfaceVariantSummary
        },
    )
}

/** 我交过的某一版。 */
@Composable
private fun SubmissionRow(submission: TronSubmission) {
    val colors = MiuixTheme.colorScheme
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Text(
                text = buildString {
                    append(if (submission.draft) "草稿" else "第 ${submission.id} 版")
                    if (submission.latest) append(" · 最新")
                    append(" · ${submission.createdAtLocal.ifBlank { "时间未知" }}")
                },
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(Dims.tight))
            // 稿上這一塊是四條 kv：正式版本／正文／教師評語／附件數。
            // 「正式版本」上面那行已經講了（第 N 版 · 時間），這裡補另外三項。
            // 長正文截到 120 字並留省略號：不截的話一版能撐滿兩屏，
            // 而這一頁要的是「我上次交的是什麼」，不是重讀全文。
            KeyValueRow(
                label = "正文",
                value = submission.plainComment.ifBlank { "—" }.clip120(),
                labelWidth = Dims.labelColumnWide,
            )
            KeyValueRow(
                label = "教师评语",
                value = submission.instructorComment.ifBlank { "—" }.clip120(),
                labelWidth = Dims.labelColumnWide,
            )
            // 附件數：`TronSubmission` 沒有解析接口的 `uploads[]`（那一層不在本次
            // 可改範圍內），所以這一格**不能**寫 0 —— 寫 0 是在說「你沒傳附件」，
            // 而我們其實不知道。如實寫「只在原站顯示」。
            KeyValueRow(
                label = "附件",
                value = "只在原站显示",
                labelWidth = Dims.labelColumnWide,
            )
            if (!submission.draft) {
                Spacer(Modifier.height(Dims.tight))
                Text(
                    text = "评分：${submission.scoreLabel}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (submission.instructorScore.isNullOrBlank()) {
                        colors.onSurfaceVariantSummary
                    } else {
                        colors.primary
                    },
                )
            }
        }
    }
}

/** 长文本截断：留省略号，读者能看出「后面还有」。 */
private fun String.clip120(): String = if (length > 120) take(120) + "…" else this

/**
 * 「已有 N 次正式提交 · 可查看版本」胶囊。
 *
 * 稿上是 `<span class="pill">`：底色比卡片深一档、圆角全满、字号小一号。
 * 为什么不复用 `SettingsChrome.kt` 的 `PillSwitch` —— 那是开关，不是状态标签。
 */
@Composable
private fun SubmittedPill(count: Int) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = "已有 $count 次正式提交 · 可查看版本",
        fontSize = Type.caption,
        color = colors.primary,
        // 次數會變（1→2→3），走等寬數字，否則膠囊寬度會跟著跳。
        style = TextStyle(fontFeatureSettings = Type.tabular),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(horizontal = Dims.loose, vertical = Dims.tight),
    )
}
