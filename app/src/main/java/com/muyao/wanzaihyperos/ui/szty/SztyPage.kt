package com.muyao.wanzaihyperos.ui.szty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.agoLabel
import com.muyao.wanzaihyperos.data.items
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.szty.SztyAttendance
import com.muyao.wanzaihyperos.data.szty.SztyAttendanceSummary
import com.muyao.wanzaihyperos.data.szty.SztyFitnessYear
import com.muyao.wanzaihyperos.data.szty.SztyPeClass
import com.muyao.wanzaihyperos.data.szty.SztyRepository
import com.muyao.wanzaihyperos.data.szty.SztyRun
import com.muyao.wanzaihyperos.data.szty.SztySnapshot
import com.muyao.wanzaihyperos.session.SztySession
import com.muyao.wanzaihyperos.ui.common.FreshnessNote
import com.muyao.wanzaihyperos.ui.common.kmText
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **数智体育**原生页：体测结果 / 校园跑公里数 / 体育课签到（全部只读）。
 *
 * ## 为什么要有「粘贴 token」这一层
 *
 * 这座系统没有可用的账密登录：真密码与乱写密码返回**一字不差**的
 * 「登录失败，使用统一身份认证账号密码登录！」，假学号才是「登录名不存在」。
 * 它把账密丢给学校统一身份认证，而那一跳现在一律拒（实测见 [SztySession] 的档头）。
 *
 * 所以这里只做一件事：让使用者在数智体育官方 App 里登录一次，
 * 把它的 token 与数字 userId 贴过来。**不做代登** —— 做了也只是把一个
 * 必然失败的密码再交一次给学校。
 *
 * ## 为什么 token 与 userId 是两个框
 *
 * 服务端**不按 token 认人，按请求体里的数字 userId 认人**：
 * `{"userId":80000}` 成功、`{"userId":32500000}`（学号）失败。
 * 而 userId 在 token 里解不出来（32 字节随机数）。缺 userId 时服务端回 HTTP 500，
 * 不是业务码 —— 所以这两样必须都问，且界面上要说清「userId 是哪个数字」。
 *
 * ## 一块失败不拖垮另外两块
 *
 * 三块数据各自可能失败（token 半路过期、这学期没开体育课、某个接口抽风）。
 * 失败的那块在 [SztySnapshot.notes] 里逐条说明，另外两块照常显示。
 */
@Composable
fun SztyPage(
    repository: SztyRepository,
    session: SztySession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 与 JwPage / ZhgyGatePage 同一种写法：remember 住「现在有没有凭据」，
    // 存进去之后就地切换成内容（不必导航到另一个路由）。
    var hasCredential by remember { mutableStateOf(session.hasCredentialPair) }
    var tokenInput by remember { mutableStateOf("") }
    var userIdInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    /**
     * 一键把剪贴板里的 token 与 userId 填进来。
     *
     * ### ★ 为什么非得有这个按钮
     *
     * token 是 54 个字符、里面带 `+` `/` `=` `_`。让用户在手机上照着敲，
     * 敲错一个字符服务端只会回一条「token 失效」，谁也不知道错在哪。
     * 而这两个值在抓包里本来就挨在一起（请求头那一行 + 请求体里的 `userId`），
     * 所以这里不要求「只复制 token」—— 整段粘进来，用正则把两样捞出来。
     *
     * ⚠️ 只在用户主动点按这一刻读剪贴板，读到的东西除了这两样之外不落任何地方。
     */
    fun pasteFromClipboard() {
        val clip = runCatching {
            context.getSystemService(android.content.ClipboardManager::class.java)
                ?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        if (clip.isNullOrBlank()) {
            inputError = "剪贴板是空的。请先在抓包里复制带 token 的那一段。"
            return
        }
        val pasted = com.muyao.wanzaihyperos.data.szty.parseSztyPaste(clip)
        if (pasted.isEmpty) {
            inputError = "没认出 token。数智体育的 token 以 SPORT_API_ 开头，请连前缀一起复制。"
            return
        }
        pasted.token?.let { tokenInput = it }
        pasted.userId?.let { userIdInput = it.toString() }
        inputError = when {
            pasted.token == null -> "认出了 userId = ${pasted.userId}，但没看到 token（要以 SPORT_API_ 开头）。"
            pasted.userId == null -> "认出了 token，但没看到 userId（'userId' 后面那一串短数字），请单独填一下。"
            else -> null
        }
    }

    val state by repository.state.collectAsState()

    LaunchedEffect(hasCredential) {
        if (hasCredential && state is ListState.FirstLoad) runCatching { repository.refresh() }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val veilProgress by rememberScrollProgress(listState)

    SecondaryScaffold(
        title = "数智体育",
        subtitle = "体测、校园跑、体育课签到（只读）",
        onBack = onExit,
        scrollProgress = veilProgress,
        onRefresh = if (hasCredential) {
            { scope.launch { runCatching { repository.refresh() } } }
        } else null,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                if (!hasCredential) {
                    item(key = "gate-intro") {
                        HintText(
                            "数智体育没有免登，也没有可用的账号密码登录" +
                                "（真密码与乱写密码回的是同一条错误）。\n" +
                                "所以这里要你在数智体育官方 App 里登录一次，把它发的 token 与数字 userId 贴过来 —— " +
                                "两样都只存在这台手机的加密存储里。",
                        )
                    }
                    inputError?.let { msg ->
                        item(key = "gate-error") { ResultBanner(message = msg, ok = false) }
                    }
                    item(key = "gate-help") {
                        HintText(
                            "token 怎么拿：官方 App 里登录后，用抓包工具看请求头里以 SPORT_API_ 开头的那一串。\n" +
                                "userId 怎么拿：同一个请求体里的 userId，是一串短数字（不是学号）。",
                        )
                    }
                    item(key = "gate-token") {
                        TextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it; inputError = null },
                            label = "token（SPORT_API_…）",
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "gate-userid") {
                        TextField(
                            value = userIdInput,
                            onValueChange = { userIdInput = it.filter { c -> c.isDigit() } },
                            label = "userId（数字，不是学号）",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // ★ 一键粘贴：token 54 个字符带 + / = _，让用户在手机上照着敲
                    // 等于让他把一个必然打错的东西打一遍，错了还只会回「token 失效」。
                    item(key = "gate-paste") {
                        Button(
                            onClick = { pasteFromClipboard() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("从剪贴板粘贴 token 和 userId") }
                    }
                    item(key = "gate-save") {
                        Button(
                            onClick = {
                                val token = tokenInput.trim()
                                val userId = userIdInput.trim().toIntOrNull()
                                when {
                                    token.isBlank() ->
                                        inputError = "token 是空的。请把 SPORT_API_ 开头那一串完整贴进来。"
                                    !token.startsWith(com.muyao.wanzaihyperos.data.szty.SPORT_TOKEN_PREFIX) ->
                                        inputError = "这一串不像 token。数智体育的 token 以 SPORT_API_ 开头。"
                                    userId == null || userId <= 0 ->
                                        inputError = "userId 得是正整数。学号（8 位）不是 userId，服务端会拒。"
                                    else -> {
                                        session.save(token, userId, System.currentTimeMillis())
                                        // 输入框清掉 —— 画面上不该继续留着凭据。
                                        tokenInput = ""
                                        userIdInput = ""
                                        inputError = null
                                        hasCredential = true
                                        scope.launch { runCatching { repository.refresh() } }
                                    }
                                }
                            },
                            enabled = tokenInput.isNotBlank() && userIdInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("用这两样去取数") }
                    }
                    item(key = "gate-scope") {
                        HintText(
                            "说明：本页只读，一条写接口都不调（不开始/结束跑步、不上报记录）。" +
                                "取数按服务端要求节流，1.8 秒一次，不并发轰炸。",
                        )
                    }
                } else {
                    sztyContent(
                        state = state,
                        onClear = {
                            session.clear()
                            hasCredential = false
                        },
                    )
                }
            }
        }
    }
}

/** 已填凭据之后的三块内容 + 各种失败形态。抽出来只为让上面那层 if 读得清。 */
private fun androidx.compose.foundation.lazy.LazyListScope.sztyContent(
    state: ListState<SztySnapshot>,
    onClear: () -> Unit,
) {
    val meta = state.metaOrNull
    val snapshot = state.items.firstOrNull()
    val failed = state as? ListState.Failed

    if (state is ListState.SessionExpired) {
        item(key = "expired") {
            ResultBanner(
                message = "数智体育那边不认这枚 token 了（-3 过期 / -4 被别的设备顶下线）。" +
                    "回数智体育 App 重新登录一次，再把新的 token 与 userId 贴过来。",
                ok = false,
            )
        }
        item(key = "expired-reset") {
            Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("重新填 token") }
        }
        return
    }

    if (failed != null) {
        item(key = "failed") { ResultBanner(message = failed.error.userText, ok = false) }
        item(key = "failed-hint") { HintText("下拉可以重试。如果确实是 token 不对，点下面的按钮重填。") }
        item(key = "failed-reset") {
            Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("重新填 token") }
        }
        return
    }

    if (snapshot == null) {
        // FirstLoad 与 Empty 在这里长得一样，但话不一样：
        // Empty = 服务端每条都应了、却一条内容都没给（那是「没有」，不是「还在取」）。
        if (state is ListState.Empty) {
            item(key = "nothing") { HintText("服务端每条都应了，但一条内容都没给：体测还没录、没有校园跑记录、这学期也没有体育课。") }
            item(key = "nothing-refresh") { HintText("下拉可以再取一次。") }
        } else {
            item(key = "loading") { HintText("正在取体测、校园跑与签到记录…（服务端要求两次请求隔 1.8 秒，首次要等十秒上下）") }
        }
        return
    }

    item(key = "freshness") {
        FreshnessNote(agoLabel(meta?.updatedAtMillis, System.currentTimeMillis()))
    }

    // ★ 哪一块没取到都要写出来，不能拿空白冒充「没有」。
    snapshot.notes.forEachIndexed { index, note ->
        item(key = "note-" + index) { HintText(note) }
    }

    // ── 校园跑 ──────────────────────────────────────────────
    item(key = "run-title") { SmallTitle(text = "校园跑") }
    item(key = "run-card") { RunCard(snapshot.run) }

    // ── 体测 ────────────────────────────────────────────────
    item(key = "fitness-title") { SmallTitle(text = "体测") }
    if (snapshot.fitness.isEmpty()) {
        item(key = "fitness-empty") { HintText("服务端没有回体测成绩（不是「没测」，是这次没取到）。") }
    } else {
        snapshot.fitness.forEachIndexed { index, year ->
            item(key = "fitness-" + index) { FitnessCard(year) }
        }
    }

    // ── 体育课签到 ──────────────────────────────────────────
    item(key = "pe-title") { SmallTitle(text = "体育课签到") }
    item(key = "pe-card") {
        AttendanceCard(snapshot.peClass, snapshot.attendanceSummary, snapshot.attendance)
    }

    item(key = "tail") {
        HintText(
            "说明：数据来自数智体育（ty.hzcu.edu.cn），只读。它按请求里的数字 userId 认人，" +
                "所以换了账号要连 userId 一起换。",
        )
    }
}

/** 校园跑。**「跑步」与「总目标」是两条不能相加的数**，分开画。 */
@Composable
private fun RunCard(run: SztyRun?) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        Column(
            modifier = Modifier.padding(Dims.cardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (run == null) {
                Text(
                    text = "这次没取到校园跑数据",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                )
                return@Column
            }
            val km = run.runKm
            val target = run.runTargetKm
            Text(
                text = if (km == null) "跑步公里数未取到" else kmText(km) + " 公里",
                style = MiuixTheme.textStyles.title2.copy(fontFeatureSettings = Type.tabular),
                color = colors.primary,
            )
            if (km != null && target != null && target > 0) {
                // 进度条：项目里没有现成的线性进度条，用两层 Box 画
                //（范本见 ui/cjh52/GrowthPage.kt 的「学分达成」）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.dividerLine),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((km / target).toFloat().coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(colors.primary),
                    )
                }
            }
            Text(
                text = runText(run),
                style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = Type.tabular),
                color = colors.onSurfaceVariantSummary,
            )
            if (run.totalKm != null) {
                Text(
                    text = "其它运动累计 " + kmText(run.totalKm) + " 公里" +
                        (run.totalCount?.let { "（" + it + " 次）" } ?: "") +
                        " —— 这是「总目标」那条，含跑步以外的项目，不要和上面相加。",
                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = Type.tabular),
                    color = Tone.quiet,
                )
            }
        }
    }
}

/** 「已跑 16.8 公里 · 要求 120 公里 · 4 次」；缺哪一段就不写那一段。 */
private fun runText(run: SztyRun): String {
    val parts = mutableListOf<String>()
    run.runKm?.let { parts += "已跑 " + kmText(it) + " 公里" }
    run.runTargetKm?.let { parts += "要求 " + kmText(it) + " 公里" }
    run.runCount?.let { parts += it.toString() + " 次" }
    return if (parts.isEmpty()) "这条汇总里没有公里数字" else parts.joinToString(" · ")
}

/** 一个学年的体测。grade 可能是「缺项」，原样显示 —— 那是学校还没录完，不是我们没取到。 */
@Composable
private fun FitnessCard(year: SztyFitnessYear) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = year.year,
                    style = MiuixTheme.textStyles.body1,
                    color = colors.onSurface,
                )
                Text(
                    text = fitnessScoreText(year),
                    style = MiuixTheme.textStyles.body1.copy(fontFeatureSettings = Type.tabular),
                    color = if (year.score == null) colors.onSurfaceVariantSummary else colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            year.items.forEach { item ->
                // ★ 空串 = 未测，**不是 0**：服务端给 50 米 / 1000 米的 data 就是空串。
                KeyValueRow(
                    label = item.name,
                    value = item.value ?: "未测",
                    labelWidth = Dims.labelColumnLong,
                )
            }
        }
    }
}

private fun fitnessScoreText(year: SztyFitnessYear): String {
    val grade = year.gradeText?.takeIf { it.isNotBlank() }
    val level = year.level?.takeIf { it.isNotBlank() }
    return when {
        grade != null && level != null -> grade + " · " + level
        grade != null -> grade
        level != null -> level
        else -> "没有总评"
    }
}

/** 体育课 + 每次签到 + 服务端自己写的那句汇总。 */
@Composable
private fun AttendanceCard(
    peClass: SztyPeClass?,
    summary: SztyAttendanceSummary?,
    records: List<SztyAttendance>,
) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            if (peClass == null) {
                Text(
                    text = "这学期没有体育课（服务端没给班级），所以没有签到要看。",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                )
                return@Column
            }
            Text(
                text = peClass.name,
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val where = listOfNotNull(
                peClass.week?.takeIf { it.isNotBlank() },
                peClass.periods?.takeIf { it.isNotBlank() }?.let { "第 " + it + " 节" },
                peClass.address?.takeIf { it.isNotBlank() },
            ).joinToString("　")
            if (where.isNotBlank()) {
                Text(
                    text = where,
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // 服务端自己写的那句话直接用，不自己拼（它的口径是学校口径）。
            summary?.headStatistics?.takeIf { it.isNotBlank() }?.let { line ->
                Text(
                    text = line,
                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = Type.tabular),
                    color = colors.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (summary == null) {
                Text(
                    text = "这次没取到签到汇总",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (records.isEmpty()) {
                Text(
                    text = "还没有任何一次课的签到记录。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                // 服务端是按 stageId 给的，不是按日期 —— 这里按标题倒序
                //（标题前 10 位就是 YYYY-MM-DD，实测「2026-10-23 第6次课 考勤」）。
                records.sortedByDescending { it.title }.forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = record.title,
                            style = MiuixTheme.textStyles.footnote2
                                .copy(fontFeatureSettings = Type.tabular),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = record.status,
                            style = MiuixTheme.textStyles.footnote2,
                            color = attendanceColor(record.status),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 签到状态的颜色：签到成功用主色，请假用静音色，其余（未签到 / 旷课）用警示色。 */
@Composable
private fun attendanceColor(status: String) = when {
    status.contains("已签到") -> MiuixTheme.colorScheme.primary
    status.contains("请假") -> Tone.quiet
    else -> Tone.warn
}
