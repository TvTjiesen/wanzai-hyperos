package com.muyao.wanzaihyperos.ui.jw

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.agoLabel
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.jw.JwEngine
import com.muyao.wanzaihyperos.data.jw.JwExamParser
import com.muyao.wanzaihyperos.data.jw.JwExamSchedule
import com.muyao.wanzaihyperos.data.jw.NextExam
import com.muyao.wanzaihyperos.data.jw.JwFeature
import com.muyao.wanzaihyperos.data.jw.JwPageParser
import com.muyao.wanzaihyperos.data.jw.JwRepository
import com.muyao.wanzaihyperos.data.jw.JwScoreParser
import com.muyao.wanzaihyperos.data.jw.JwScoreSummarizer
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.ui.common.FreshnessNote
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Mark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.infoItemsIndexed
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import kotlinx.coroutines.launch
import com.muyao.wanzaihyperos.ui.components.WanzaiButton as Button
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import com.muyao.wanzaihyperos.ui.components.WanzaiTextButton as TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **教務系統**原生頁：考試信息 / 學生成績（都是只讀）。
 *
 * ## ★ 這個頁面為什麼藏了一個 WebView
 *
 * 不是偷懶，是**實測的必然**（`docs/B18-JW-PROBE.txt`）：
 * 純 HTTP 打不進教務系統（回應沒有 `JSESSIONID`），
 * **只有它自己的網頁登入處理器才能產生那個 session**。
 * 所以我們讓它自己的網頁去登入，再用那個 session 取資料。
 *
 * WebView 只給 **1 像素高**且放在畫面外 —— 它是「看不見的登入器」，
 * 使用者看到的是下面的原生界面。
 *
 * ## 為什麼用 1px 而不是 `View.GONE`
 *
 * `GONE` 的 WebView 有些機型**不會執行 JS**（不觸發 layout），
 * 因此登入與 fetch 會靜靜地失敗。用極小尺寸讓它活著，但看不到。
 *
 * ## 兩個資料來源的欄位都在服務端
 *
 * 考試時間／考場若回「未放开不可查」，界面**原樣顯示**並補一句說明
 * —— 那是學校還沒開放查詢，不是我們沒取到。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JwPage(
    repository: JwRepository,
    engine: JwEngine,
    session: com.muyao.wanzaihyperos.session.JwSession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0 = 考試信息，1 = 學生成績。用 Int：只有兩項，不會再長。
    var tab by remember { mutableIntStateOf(TAB_EXAM) }
    val feature = if (tab == TAB_EXAM) JwFeature.EXAM else JwFeature.SCORE

    // 憑據：沒設過就先請使用者輸入（教務系統沒有免登，也沒有 refresh）。
    var hasCredential by remember { mutableStateOf(session.hasCredentialPair) }
    var credentialRevision by remember { mutableStateOf(session.credentialRevision()) }
    DisposableEffect(session) {
        val listener = {
            hasCredential = session.hasCredentialPair
            credentialRevision = session.credentialRevision()
        }
        session.addListener(listener)
        onDispose { session.removeListener(listener) }
    }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val state by repository.state(feature).collectAsState()

    LaunchedEffect(feature, hasCredential, credentialRevision) {
        if (hasCredential && state is ListState.FirstLoad) com.muyao.wanzaihyperos.data.repo.catchingActive { repository.load(feature) }
    }

    val page = (state as? ListState.Content)?.items?.firstOrNull()
    val allItems = page?.root?.let {
        if (feature == JwFeature.EXAM) {
            JwExamParser.parse(com.muyao.wanzaihyperos.data.jw.JwPageParser.parse(it))
        } else {
            emptyList()
        }
    }.orEmpty()
    val scoreItems = page?.root?.let {
        if (feature == JwFeature.SCORE) {
            JwScoreParser.parse(com.muyao.wanzaihyperos.data.jw.JwPageParser.parse(it))
        } else {
            emptyList()
        }
    }.orEmpty()

    // 學期匯總（已修學分 / 加權平均分 / 加權績點）：純函式算，算不出來就是空。
    val scoreSummary = remember(scoreItems) { JwScoreSummarizer.summarize(scoreItems) }

    // 最近一场考试：只认服务端真给了日期的。学校没开放的那些（未放开不可查）
    // 不進排序，但要在卡片下面如实数出来，否则看着像没有考试。
    val nextExam = remember(allItems) { JwExamSchedule.next(allItems, java.time.LocalDate.now()) }
    val examUnknown = remember(allItems) { JwExamSchedule.unknownCount(allItems) }

    // 清單的滾動狀態提到外面：頂欄漸變紗要靠它算進度
    //（見 SecondaryScaffold 的 scrollProgress 參數說明）。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val veilProgress by com.muyao.wanzaihyperos.ui.design.rememberScrollProgress(listState)

    SecondaryScaffold(
        title = "教务查询",
        subtitle = "正方教学管理信息服务平台",
        onBack = onExit,
        scrollProgress = veilProgress,
        onRefresh = if (hasCredential) {
            { scope.launch { com.muyao.wanzaihyperos.data.repo.catchingActive { repository.load(feature, force = true) } } }
        } else null,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "jw-hero") {
                    SecHero(
                        eyebrow = "ACADEMIC / 教务查询",
                        title = "成绩与考试",
                        sub = "教务账号独立认证，不冒用门户登录状态。",
                    )
                }
                if (!hasCredential) {
                    // ── 還沒設過憑據 ──────────────────────────────────
                    item(key = "jw-gate-intro") {
                        SecNotice(
                            "教务系统没有免登，也没有续期，会话过期后只能用账号密码重新登录。" +
                                "这里需要保存一次账号密码 —— 它只存在手机的加密存储里，" +
                                "只有你自己在这台手机上清掉它才会消失。",
                        )
                    }
                    inputError?.let { msg -> item { ResultBanner(message = msg, ok = false) } }
                    item {
                        TextField(
                            value = account,
                            onValueChange = { account = it; inputError = null },
                            label = "学号 / 工号",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        TextField(
                            value = password,
                            onValueChange = { password = it; inputError = null },
                            label = "密码",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                if (account.isBlank() || password.isBlank()) {
                                    inputError = "账号与密码都要填。"
                                } else {
                                    session.save(account, password, System.currentTimeMillis())
                                    // 輸入框不留憑據
                                    account = ""
                                    password = ""
                                    inputError = null
                                    hasCredential = true
                                }
                            },
                            enabled = account.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("登录并使用") }
                    }
                    item {
                        HintText("说明：账号密码不会写进任何日志，也不会出现在界面上。")
                    }
                    return@AppList
                }

                item {
                    TabRow(
                        tabs = listOf("考试信息", "学生成绩"),
                        selectedTabIndex = tab,
                        onTabSelected = { tab = it },
                        minWidth = 96.dp,
                        maxWidth = 96.dp,
                    )
                }

                // ★ 刷新时**保留旧值**并说明它旧了（矩阵 30 的验收项）。
                //   失败不整页清空：内容还在 [ListState.Content]，错误记在 meta.lastRefreshError。
                val meta = state.metaOrNull
                meta?.let { m ->
                    item(key = "jw-freshness") {
                        FreshnessNote(
                            agoLabel(m.updatedAtMillis, System.currentTimeMillis()) +
                                when {
                                    m.refreshing -> " · 正在重新取"
                                    m.stale -> " · 这是上次存的"
                                    else -> ""
                                },
                        )
                    }
                    m.lastRefreshError?.let { problem ->
                        val why = problem.message?.takeIf { it.isNotBlank() } ?: problem.userText
                        item(key = "jw-refresh-problem") {
                            Text(
                                text = "这次没取到：$why（上面这些是上次取到的，下拉可以重试）",
                                fontSize = Type.caption,
                                lineHeight = Type.lineCaption,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // ★ 失敗要**照實說**，不能落到「沒有查到」那一支 ——
                //   那等於告訴使用者「學校沒有安排考試」，而事實是我們沒登入成功。
                //   （真機第一版就是這樣：沒憑據時顯示「沒有查到考試安排」。）
                val failed = state as? ListState.Failed
                // 憑據問題與其他失敗要分開：只有憑據問題才該請使用者重輸帳密。
                // 「登录已过期」與「页面还没准备好」的**處理方式完全不同**，
                // 混在一起會讓使用者為網路問題白打一次密碼。
                val credentialProblem = failed?.error as? HzcuException.LoginExpired
                when {
                    credentialProblem != null -> {
                        item {
                            ResultBanner(
                                message = (credentialProblem.message ?: "登录已过期") +
                                    " 请重新输入账号密码。",
                                ok = false,
                            )
                        }
                        item {
                            Button(
                                onClick = {
                                    session.clear()
                                    hasCredential = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("重新输入账号密码") }
                        }
                    }

                    failed != null -> item {
                        // 其餘失敗（引擎未就緒、表單變了、網路…）**原樣**顯示原因 ——
                        // 這台機器上 App 的 Log 拿不到，界面就是唯一的排查窗口。
                        ResultBanner(message = failed.error.userText, ok = false)                    }

                    state is ListState.FirstLoad -> item {
                        HintText("正在登录教务系统并取数…（首次约需 10 秒）")
                    }

                    page?.looksLikeHtml == true -> item {
                        ResultBanner(message = "登录状态已失效，请重新输入账号密码。", ok = false)
                    }

                    feature == JwFeature.EXAM && allItems.isEmpty() -> item {
                        HintText("没有查到考试安排。")
                    }

                    feature == JwFeature.SCORE && scoreItems.isEmpty() -> item {
                        HintText("没有查到成绩记录。")
                    }

                    feature == JwFeature.EXAM -> {
                        if (nextExam != null) {
                            item(key = "exam-next") { NextExamCard(nextExam) }
                        }
                        // ★ 整批都「沒放開」時，稿上的說法是這一句（`jw.png` restricted 分支，逐字）：
                        //   「沒放開」是學校的答覆，不是取數失敗，更不是「沒有考試」。
                        val allRestricted = allItems.isNotEmpty() && allItems.all { it.restricted }
                        if (allRestricted) {
                            item(key = "exam-restricted") {
                                SecNotice("学校暂未放开查询；这是学校限制，不是网络故障。")
                            }
                        } else if (examUnknown > 0) {
                            // 部分放開：說清楚有幾場排不進來，別讓人以為考試只有這些。
                            item(key = "exam-unknown") {
                                HintText("另有 " + examUnknown + " 场学校没开放时间，排不进来。")
                            }
                        }
                        item(key = "exam-title") {
                            SecSectionHead(title = "考试安排", meta = "共 ${allItems.size} 场", icon = MarkIcon.Clock)
                        }
                        // ★ key 必須**真的唯一**：同一門課會有多場考試
                        //   （補考、期末考…），實測「大学物理(Ⅰ)」就出現兩次。
                        //   第一版用 `courseName + examName` → Compose 直接崩
                        //   （`Key "大学物理(Ⅰ)" was already used`）。
                        //   用 [infoItemsIndexed] 以下標當 key。
                        infoItemsIndexed(allItems) { e ->
                            InfoItem(
                                title = e.courseName,
                                subtitle = listOfNotNull(
                                    e.examName.takeIf { it.isNotBlank() },
                                    // ★ 「學校還沒放開」與「欄位是空的」都不能畫成空白：
                                    //   空白在列表裡讀起來像「這場考試沒有時間/考場」。
                                    e.time.takeIf { it.isNotBlank() } ?: "时间未公布",
                                    e.room.takeIf { it.isNotBlank() } ?: "考场未公布",
                                ).joinToString("　"),
                                // 座位同理：沒放開就寫「—」，不是留白、更不是 0。
                                trailing = e.seat.takeIf { it.isNotBlank() && !e.restricted } ?: "—",
                                trailingColor = MiuixTheme.colorScheme.primary,
                                body = listOfNotNull(
                                    e.teacherName.takeIf { it.isNotBlank() }?.let { "教师 $it" },
                                    e.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
                                    e.college.takeIf { it.isNotBlank() },
                                    e.restrictionNote,
                                ).joinToString("　"),
                                bodyMaxLines = 3,
                            )
                        }
                    }

                    else -> {
                        if (!scoreSummary.isEmpty) {
                            item(key = "score-summary") { ScoreSummaryCard(scoreSummary) }
                        }
                        item(key = "score-title") {
                            SecSectionHead(title = "各科成绩", meta = "共 ${scoreItems.size} 门", icon = MarkIcon.Data)
                        }
                        // ★ 同上：**重修**會讓同一個課名出現多次，所以用下標當 key。
                        infoItemsIndexed(scoreItems) { s ->
                            InfoItem(
                                title = s.courseName,
                                subtitle = listOfNotNull(
                                    s.teacherLabel.takeIf { it.isNotBlank() },
                                    s.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
                                    s.gradePoint.takeIf { it.isNotBlank() }?.let { "绩点 $it" },
                                ).joinToString("　"),
                                trailing = s.displayScore,
                                trailingColor = MiuixTheme.colorScheme.primary,
                                body = s.publishedAt.takeIf { it.isNotBlank() }?.let { "公布于 $it" },
                            )
                        }
                    }
                }

                item(key = "jw-account-title") {
                    SecSectionHead(title = "教务账号", meta = null, icon = MarkIcon.Account)
                }
                item(key = "jw-account-actions") {
                    SecChipRow(
                        chips = listOf(
                            "刷新" to {
                                scope.launch { com.muyao.wanzaihyperos.data.repo.catchingActive { repository.load(feature, force = true) } }
                            },
                            "重新输入账号密码" to {
                                session.clear()
                                hasCredential = false
                            },
                        ),
                    )
                }
                item(key = "jw-exam-tail") {
                    HintText(
                        "说明：考试时间与考场由学校决定是否开放查询；" +
                            "若显示“未放开不可查”，那是学校的答复，不是取数失败。",
                    )
                }
                item(key = "jw-other") {
                    // ★ 教材缴费不在这里做：它是**独立的 SSO 原站业务**，不属于教务查询页。
                    //   本页没有「打开原站」的出口（`JwPage` 的签名里没有 onOpenWeb，
                    //   加它要动 `WanzaiRoot`，那不在本轮可写范围）—— 所以只如实说明边界，
                    //   不伪造一个点了没反应的按钮。
                    SecNotice(
                        "教材缴费属于独立的 SSO 原站业务，不是这个查询页能代办的；" +
                            "也不在本页伪造它的表单。",
                    )
                }
            }

            // ---- 看不見的登入器 ------------------------------------------------
            // 它負責「用網頁做登入」與「在同源環境下 fetch 資料」。
            // 尺寸 1x1 且透明：不能 GONE（那不執行 JS），也不能佔版面。
            AndroidView(
                modifier = Modifier.size(1.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        layoutParams = ViewGroup.LayoutParams(1, 1)
                        alpha = 0f
                        engine.attach(this)
                    }
                },
                onRelease = { engine.release(it) },
            )
        }
    }
}

private const val TAB_EXAM = 0

/**
 * 這頁的標題區：eyebrow + 大標題 + 一句說明（照板 `jw.png` 的 `title()` 三行）。
 *
 * 尺寸都由令牌算出來，不新造字號檔：
 * `.eyebrow{11.29px}`×0.886 ⇒ [Type.caption]；`.hero-title{29.35px}`×0.886 ⇒ [Type.displayTitle]；
 * `.muted` ⇒ [Type.caption] 灰。
 *
 * 各頁各自私有這一份（Zw/Szty/Zhgy/Busyness 同名同形）：它只有二十行，
 * 而共用位置在 `ui/design`、那不在本輪可寫範圍 —— 先重複，等 Lead 決定要不要上提。
 */
@Composable
private fun SecHero(eyebrow: String, title: String, sub: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(bottom = Dims.tight)) {
        Text(
            text = eyebrow,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            letterSpacing = 0.10.em,
            color = colors.onSurfaceVariantSummary,
            style = TextStyle(fontFeatureSettings = Type.tabular),
        )
        Text(
            text = title,
            fontSize = Type.displayTitle,
            fontWeight = Type.weightTitle,
            letterSpacing = (-0.02).em,
            lineHeight = Type.lineDisplayTitle,
            color = colors.onBackground,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = sub,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 板的 `.notice`：**左側一道細規則 + 極淡底色**，不是普通卡片。
 *
 * 照 `style.css`：
 * `padding:12px 14px; border-left:2px solid; border-radius:0 12px 12px 0; color:var(--quiet)`，
 * `.notice.error` 時整段換成 `--error` 色。
 *
 * 為什麼不直接用 [ResultBanner]：那是「一條結論橫幅」（有卡片底、居中語感），
 * 而這裡是「一句需要被看見、但不該搶走標題的說明」。兩者在板裡也是兩種東西。
 */
@Composable
private fun SecNotice(text: String, error: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val tint = if (error) colors.error else colors.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(tint),
        )
        Text(
            text = text,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = tint,
            modifier = Modifier.padding(
                start = Dims.loose,
                end = Dims.cardPadding,
                top = Dims.normal,
                bottom = Dims.normal,
            ),
        )
    }
}

/**
 * 一排「芯片式」動作（板的 `.chip-list`）。
 *
 * 板是 `flex-wrap`，但一列擠不下時 Compose 沒有現成的換行容器
 *（全工程也沒用過 `FlowRow`），所以這裡用可橫向滾動的 [androidx.compose.foundation.lazy.LazyRow]
 * ——比硬塞兩列更不會在窄屏上把字壓扁，也保住了每個控件 ≥[Dims.minTouch] 的命中區。
 */
@Composable
private fun SecChipRow(chips: List<Pair<String, () -> Unit>>) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Dims.tight),
    ) {
        items(chips.size) { i ->
            val (label, action) = chips[i]
            TextButton(text = label, onClick = action)
        }
    }
}

/**
 * 成績匯總：**兩格大字統計 + 一段口徑說明**（照板 `jw.png` 的 `.stat-grid` + `box`）。
 *
 * 三個數字都是 [JwScoreSummarizer] 算的；算不出來就**不畫那一格**，
 * 而不是畫一個 `0` —— 「未知不是 0」是本工程第一條紅線。
 */
@Composable
private fun ScoreSummaryCard(summary: com.muyao.wanzaihyperos.data.jw.JwScoreSummary) {
    val colors = MiuixTheme.colorScheme
    Column {
        SecSectionHead(
            title = "学业统计",
            icon = MarkIcon.Growth,
            meta = if (summary.creditCount > 0) "已公布 ${summary.creditCount} 门" else null,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dims.tight),
            horizontalArrangement = Arrangement.spacedBy(Dims.cardGap),
        ) {
            summary.credits?.let { c ->
                StatCell(
                    label = "已修学分",
                    value = formatOne(c),
                    modifier = Modifier.weight(1f),
                )
            }
            summary.weightedScore?.let { v ->
                StatCell(
                    label = "加权平均分",
                    value = formatOne(v),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AppCard(modifier = Modifier.padding(top = Dims.cardGap)) {
            Column(modifier = Modifier.padding(horizontal = Dims.cardPadding, vertical = Dims.tight)) {
                KeyValueRow(
                    label = "加权绩点",
                    // 讀不出來就寫「—」，不寫 0.00：「績點 0」是「掛科」，不是「沒取到」。
                    value = summary.weightedGradePoint?.let { String.format("%.2f", it) } ?: "—",
                    labelWidth = Dims.labelColumnLong,
                )
                KeyValueRow(
                    label = "统计口径",
                    value = "仅计已公布且有学分科目",
                    labelWidth = Dims.labelColumnLong,
                )
            }
        }
        if (summary.excludedFromAverage > 0) {
            Text(
                text = "另有 " + summary.excludedFromAverage + " 门是等第制或学分缺失，没进加权。",
                fontSize = Type.caption,
                lineHeight = Type.lineCaption,
                color = Tone.quiet,
                modifier = Modifier.padding(top = Dims.tight),
            )
        }
    }
}

/**
 * 一格統計（板的 `.stat`）：小標 + 大字 + 沒有第三行。
 *
 * `.stat strong{26px}`×0.886 ⇒ `Type.displayTitle`（不新造字號檔）；
 * `.stat small` 走 `Type.caption` 灰。用 `AppCard` 拿邊框與圓角，
 * 不在這裡自繪卡片（`ui/design` 之外自繪會和別頁的觀感對不上）。
 */
@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = Dims.cardPadding, vertical = Dims.loose)) {
            Text(
                text = label,
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
            Text(
                text = value,
                fontSize = Type.displayTitle,
                lineHeight = Type.lineDisplayTitle,
                fontWeight = Type.weightBody,
                letterSpacing = (-0.03).em,
                color = colors.onBackground,
                style = TextStyle(fontFeatureSettings = Type.tabular),
                modifier = Modifier.padding(top = Dims.tight),
            )
        }
    }
}

/**
 * 分區標題（板的 `.section-heading`：`h3` 20.3px + 右側 `.meta` 12.42px）。
 *
 * `h3`×0.886 ⇒ `Type.title`；`.meta` ⇒ `Type.caption` 灰。
 * Miuix 的 `SmallTitle` 只有一行字，放不下右側那條元信息，所以這裡自己排一行。
 */
@Composable
private fun SecSectionHead(title: String, meta: String?, icon: MarkIcon = MarkIcon.Data) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dims.sectionGap),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // 正文前图标（业主：每个区块标题前一枚，选语义最近的，别全用 Info）。
            BodyMark(icon = icon, tint = colors.primary, size = Mark.size)
            Text(
                text = title,
                fontSize = Type.title,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
            )
        }
        if (!meta.isNullOrBlank()) {
            Text(
                text = meta,
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

/** 一位小数的展示：整数去掉 .0，免得「已修 12.0 学分」看着像精度丢失。 */
internal fun formatOne(v: Double): String {
    val rounded = Math.round(v * 10.0) / 10.0
    return if (rounded == Math.floor(rounded)) rounded.toInt().toString() else String.format("%.1f", rounded)
}

/** 最近一场考试：左边是哪门课什么时候考，右边是还有几天。 */
@Composable
private fun NextExamCard(next: NextExam) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        Row(
            modifier = Modifier.padding(Dims.cardPadding),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最近一场",
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                )
                Text(
                    text = next.courseName,
                    fontSize = Type.body,
                    fontWeight = Type.weightBody,
                    color = colors.onBackground,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Dims.tight),
                )
                // 日期与时间都是服务端原文里切出来的，只拼不换 —— 换算一次就可能编。
                Text(
                    text = listOfNotNull(
                        next.date.toString().replace("-", "/"),
                        next.startClock?.let { c -> if (next.endClock != null) c + "–" + next.endClock else c },
                        // ★ 考場沒開放時是 service 端的字串（含「未放开」），不是空串；
                        //   空串時寫「未公布」而不是留白 —— 留白看起來像「在線下考場」。
                        next.room.takeIf { it.isNotBlank() } ?: "考场未公布",
                    ).joinToString(" · "),
                    fontSize = Type.caption,
                    lineHeight = Type.lineCaption,
                    color = colors.onSurfaceVariantSummary,
                    style = TextStyle(fontFeatureSettings = Type.tabular),
                    modifier = Modifier.padding(top = Dims.tight),
                )
            }
            Spacer(Modifier.width(Dims.loose))
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = if (next.daysAway == 0L) "今天" else if (next.daysAway == 1L) "明天" else next.daysAway.toString(),
                    fontSize = Type.title,
                    fontWeight = Type.weightTitle,
                    color = colors.primary,
                    style = TextStyle(fontFeatureSettings = Type.tabular),
                )
                if (next.daysAway > 1L) {
                    Text(
                        text = "天",
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
