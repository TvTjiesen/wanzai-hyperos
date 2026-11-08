package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.muyao.wanzaihyperos.data.cjh52.Cjh52Exam
import com.muyao.wanzaihyperos.data.cjh52.ExamRepository
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.MarkedHeading
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 我的考試（B3）。
 *
 * ### 契約（真機實測）
 *
 * ```
 * POST school_manager/exam_service/get_user_exam_list
 *   { pageNum: 0, pageSize: 10, modelCode: "M0017" }
 *   → data.data[]
 * ```
 *
 * ### ⚠️ 為什麼這一頁「看起來很樸素」
 *
 * 取證時該帳號**當下沒有考試**（`data: []`），所以**條目裡的欄位名沒取到**。
 * 而 PLAN §B3 明文要求「**未核實接口不編造資料**」。
 *
 * 所以這一頁刻意**不去猜欄位**：
 * - 有資料時，只顯示**能從常見欄位名認出來**的標題（`name`/`title`/`examName`），
 *   認不出來就顯示「（无法显示的考试条目）」而不是編一個假標題；
 * - 沒有資料時寫「尚未公布」—— 不是「沒有考試」，也不是「0 場」：
 *   接口回空在這裡**只能證明這一刻讀不到**，證明不了「沒有」；
 * - 並在頁面上**明確說明**「欄位尚未核實」——
 *   與其讓使用者以為這是完整的考試信息，不如說清楚。
 *
 * 等有考試的時候再補一次取證即可（`tools/cdp-threepages.js` 重跑）。
 */
@Composable
fun ExamPage(
    repository: ExamRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
    ready: Boolean = true,
    /** 稿 `cjhExams.png` 那枚「六项服务目录」的落点。为 null 时不画。 */
    onOpenDirectory: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val exams = (state as? ListState.Content)?.items.orEmpty()

    LaunchedEffect(ready) {
        if (ready && (state is ListState.FirstLoad || state is ListState.SessionExpired)) {
            repository.refresh()
        }
    }

    val listState = rememberLazyListState()
    val veilProgress by rememberScrollProgress(listState)
    val refresh: () -> Unit = { scope.launch { runCatching { repository.refresh() } } }

    SecondaryScaffold(
        scrollProgress = veilProgress,
        title = "我的考试",
        onBack = onExit,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "exam-hero") {
                    CjhHero(
                        eyebrow = "CJH52 / 学生服务",
                        title = "我的考试",
                        sub = "独立于教务与学在城院的原生阅读入口。",
                    )
                }
                item(key = "exam-stale") { CjhStaleNotice(pageState) }

                if (exams.isEmpty()) {
                    // 稿 `cjhExams.png` 的兩行鍵值：項目 / 狀態。
                    // 這裡的值必須是「讀不到」而不是「沒有」——
                    // 空表 + 未核實欄位，最誠實的說法就是「尚未公布」。
                    item(key = "exam-pending") {
                        CjhKvRow(label = "原始项目", value = "—")
                        CjhKvRow(label = "原始状态", value = "尚未公布")
                    }
                    item(key = "exam-state") {
                        StatePlaceholder(
                            state = pageState,
                            // 失敗/離線/過期由占位自己講，這裡不搶話；
                            // 只有真空（接口確實回了一張空表）才補一句話。
                            detail = if (pageState == PageState.Empty) {
                                "接口这次没有返回考试记录，也没有可确认的字段。"
                            } else {
                                null
                            },
                            onAction = onFallbackToWeb,
                            actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
                        )
                    }
                } else {
                    item(key = "exam-heading") {
                        MarkedHeading(
                            text = "考试安排",
                            icon = MarkIcon.Clock,
                            trailing = {
                                Text(
                                    text = "共 ${exams.size} 场",
                                    // 会变的计数走等宽数字，数字宽度一变整行会左右跳（#41）。
                                    style = TextStyle(fontFeatureSettings = Type.tabular),
                                    fontSize = Type.caption,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                        )
                    }
                    items(exams) { exam -> ExamRow(exam) }
                }

                item(key = "exam-note") {
                    // ★ 誠實標註：這一頁的欄位還沒核實過。稿 `cjhExams.png` 的說明條逐字照抄。
                    //   「指路」那句 2026-11-08 修過：原文寫「請點右上『改用網頁版』」，
                    //   而右上從來只有一顆刷新鈕 —— 指路指到一個不存在的按鈕，比不指路更糟。
                    CjhNotice(
                        text = "当前已读证据未核实全部考试字段。这里不杜撰时间、考场、座位；" +
                            "完整原始字段请进入原站。",
                    )
                }
                item(key = "exam-origin") {
                    CjhOriginSection(
                        webLabel = "我的考试完整网页版",
                        onOpenWeb = onFallbackToWeb,
                        onRefresh = refresh,
                        onOpenDirectory = onOpenDirectory,
                    )
                }
                item(key = "exam-source") { CjhSourceCaption("CJH52 接口") }
            }
        }
    }
}

/**
 * 一條考試記錄。
 *
 * 標題認不出來時寫「（无法显示的考试条目）」；下面那行「服务端栏位：…」
 * 是接口真給了什麼鍵的全盤托出 —— 它既讓使用者知道這條到底有多少信息，
 * 也是我們下次補取證的線索。
 */
@Composable
private fun ExamRow(exam: Cjh52Exam) {
    CjhRowItem(
        title = exam.title ?: "（无法显示的考试条目）",
        icon = MarkIcon.Clock,
        meta = if (exam.raw.isEmpty()) {
            "服务端没有给出任何栏位"
        } else {
            "服务端栏位：" + exam.raw.keys.joinToString(", ")
        },
        bodyMaxLines = 3,
    )
}