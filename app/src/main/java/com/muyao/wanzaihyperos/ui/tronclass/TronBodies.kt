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
import androidx.compose.ui.text.input.KeyboardType
import com.muyao.wanzaihyperos.data.ListState
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
 * ⚠️ 這裡刻意**保留**「結果訊息」在頂部顯示（[ResultBanner]）——
 * 簽到是寫操作，成沒成必須一眼看到。詳見 `docs/B10` 記的那個 bug：
 * 訊息曾經只寫在列表分支裡，空態時看不到。
 */
@Composable
fun RollcallContent(
    repository: RollcallRepository,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val items = (state as? ListState.Content)?.items.orEmpty()

    var answerMessage by remember { mutableStateOf<String?>(null) }
    var answerOk by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf<Int?>(null) }
    var scanningFor by remember { mutableStateOf<Int?>(null) }

    if (scanningFor != null) {
        val targetId = scanningFor!!
        QrScannerPage(
            onExit = { scanningFor = null },
            onScanned = { raw ->
                scanningFor = null
                val payload = com.muyao.wanzaihyperos.data.tronclass.QrPayloadParser.extract(raw)
                if (payload == null) {
                    answerOk = false
                    answerMessage = "这不是签到二维码（内容里没有签到信息）"
                } else {
                    submitting = targetId
                    scope.launch {
                        val result = runCatching {
                            repository.answer(targetId, RollcallAnswerKind.QR, payload)
                        }.getOrElse { e ->
                            com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerResult(
                                ok = false, httpStatus = 0,
                                message = "提交失败：${e.javaClass.simpleName}",
                            )
                        }
                        answerOk = result.ok
                        answerMessage = if (result.ok) "扫码签到成功" else result.message
                        submitting = null
                        if (result.ok) runCatching { repository.refresh() }
                    }
                }
            },
        )
        return
    }

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    AppList(modifier = modifier) {
        item { BackRow("返回", onBack) }

        answerMessage?.let { msg ->
            item { ResultBanner(message = msg, ok = answerOk) }
        }

        if (items.isEmpty()) {
            item {
                HintText(
                    if (pageState == PageState.Empty) "目前没有进行中的签到"
                    else "签到没有取到，请稍后重试。",
                )
            }
            item { ActionRow(title = "改用网页版", action = "打开", onClick = onOpenWeb) }
            return@AppList
        }

        infoItems(items, key = { it.id }) { r ->
            RollcallItem(
                rollcall = r,
                submitting = submitting == r.id,
                onScan = { scanningFor = r.id },
                onSubmit = { kind, code ->
                    submitting = r.id
                    scope.launch {
                        val result = runCatching { repository.answer(r.id, kind, code) }
                            .getOrElse { e ->
                                com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerResult(
                                    ok = false, httpStatus = 0,
                                    message = "提交失败：${e.javaClass.simpleName}",
                                )
                            }
                        answerOk = result.ok
                        answerMessage = if (result.ok) "签到成功" else result.message
                        submitting = null
                        if (result.ok) runCatching { repository.refresh() }
                    }
                },
            )
        }

        item {
            HintText(
                "说明：签到是否成功由学校服务器判定（位置、IP、二维码时效都在那边校验）。" +
                    "失败原因会原样显示服务端的话，便于判断该怎么做。",
            )
        }
    }
}

@Composable
private fun RollcallItem(
    rollcall: com.muyao.wanzaihyperos.data.tronclass.TronRollcall,
    submitting: Boolean,
    onScan: () -> Unit,
    onSubmit: (RollcallAnswerKind, String?) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var input by remember(rollcall.id) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            InfoItem(
                title = rollcall.title.ifBlank { "签到" },
                subtitle = listOfNotNull(
                    rollcall.sourceLabel,
                    rollcall.rollcallTime.takeIf { it.isNotBlank() },
                    if (!rollcall.isOpen) "已结束" else null,
                ).joinToString("　"),
                trailing = rollcall.myStatusLabel,
                trailingColor = if (rollcall.myStatus == "on_call") colors.primary else colors.error,
            )

            // 只有「進行中 + 我還沒簽」才給動作 —— 避免重複提交（那是寫操作）。
            if (!rollcall.isOpen || rollcall.myStatus == "on_call") return@Column

            Spacer(Modifier.height(Dims.loose))
            when {
                rollcall.source.contains("number", true) -> {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        label = "老师报的数字",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Dims.sectionGap))
                    Button(
                        onClick = { onSubmit(RollcallAnswerKind.NUMBER, input) },
                        enabled = input.isNotBlank() && !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (submitting) "提交中…" else "签到") }
                }

                rollcall.source.contains("self", true) -> {
                    Button(
                        onClick = { onSubmit(RollcallAnswerKind.SELF_REGISTRATION, null) },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (submitting) "提交中…" else "自助签到") }
                }

                rollcall.source.contains("qr", true) || rollcall.source.contains("roomis", true) -> {
                    Button(
                        onClick = onScan,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("打开相机扫码") }
                    Spacer(Modifier.height(Dims.loose))
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        label = "或粘贴扫码结果",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Dims.sectionGap))
                    Button(
                        onClick = { onSubmit(RollcallAnswerKind.QR, input) },
                        enabled = input.isNotBlank() && !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (submitting) "提交中…" else "提交粘贴的内容") }
                }
            }
        }
    }
}

/** 作業板塊的內容（只讀）。 */
@Composable
fun HomeworkContent(
    repository: HomeworkRepository,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
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
                    hw.submitTimes?.let { "可交 $it 次" },
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
                bodyMaxLines = 6,
            )
        }

        item {
            HintText("要交作业？提交是写操作（会记成绩），这里只做只读查看。")
        }
        item { ActionRow(title = "去网页版提交", action = "打开", onClick = onOpenWeb) }
    }
}
