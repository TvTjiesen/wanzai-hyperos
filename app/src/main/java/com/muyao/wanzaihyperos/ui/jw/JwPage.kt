package com.muyao.wanzaihyperos.ui.jw

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.jw.JwEngine
import com.muyao.wanzaihyperos.data.jw.JwExamParser
import com.muyao.wanzaihyperos.data.jw.JwFeature
import com.muyao.wanzaihyperos.data.jw.JwPageParser
import com.muyao.wanzaihyperos.data.jw.JwRepository
import com.muyao.wanzaihyperos.data.jw.JwScoreParser
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.infoItemsIndexed
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
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
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val state by repository.state(feature).collectAsState()

    LaunchedEffect(feature, hasCredential) {
        if (hasCredential && state is ListState.FirstLoad) runCatching { repository.load(feature) }
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

    SecondaryScaffold(
        title = "教务系统",
        subtitle = "正方教学管理信息服务平台",
        onBack = onExit,
        onRefresh = if (hasCredential) {
            { scope.launch { runCatching { repository.load(feature, force = true) } } }
        } else null,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize()) {
                if (!hasCredential) {
                    // ── 還沒設過憑據 ──────────────────────────────────
                    item {
                        HintText(
                            "教务系统没有免登，也没有续期，会话过期后只能用账号密码重新登录。\n" +
                                "所以这里需要保存一次账号密码 —— 它只存在手机的加密存储里，" +
                                "退出登录时会被清除。",
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
                        modifier = Modifier.padding(horizontal = Dims.screenH),
                    )
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
                        ResultBanner(message = failed.error.message.orEmpty(), ok = false)                    }

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
                        item { SmallTitle(text = "考试（${allItems.size}）") }
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
                                    e.time.takeIf { it.isNotBlank() },
                                    e.room.takeIf { it.isNotBlank() },
                                ).joinToString("　"),
                                trailing = e.seat.takeIf { it.isNotBlank() && !e.restricted },
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
                        item { SmallTitle(text = "成绩（${scoreItems.size}）") }
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

                item {
                    HintText(
                        "说明：考试时间与考场由学校决定是否开放查询；" +
                            "若显示「未放开不可查」，那是学校的答复，不是取数失败。",
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
            )
            DisposableEffect(Unit) {
                onDispose { engine.detach() }
            }
        }
    }
}

private const val TAB_EXAM = 0
