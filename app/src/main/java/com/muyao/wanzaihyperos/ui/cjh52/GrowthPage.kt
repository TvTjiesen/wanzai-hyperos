package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.cjh52.Cjh52ActivityMonth
import com.muyao.wanzaihyperos.data.cjh52.Cjh52CreditProgress
import com.muyao.wanzaihyperos.data.cjh52.Cjh52KqaScore
import com.muyao.wanzaihyperos.data.cjh52.Cjh52PhysiqueScore
import com.muyao.wanzaihyperos.data.cjh52.GrowthRepository
import com.muyao.wanzaihyperos.data.cjh52.GrowthSnapshot
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 我的成長（B3）。
 *
 * ### 契約（真機實測，8 個接口）
 *
 * 門戶那一頁其實同時打了 8 個接口。原生頁只取**四個真正構成內容的**：
 *
 * | 區塊 | 接口 | 請求 |
 * |---|---|---|
 * | KQA 三維分 | `model/query_service/query_one_kqa_score` | `{userId}` |
 * | 體測成績 | `physique_service/find_my_physique_scores` | `{}` |
 * | 學分達成 | `quality_service/get_my_quality_course_score` | `{}` |
 * | 行為記錄（按月） | `kqa_statistical_service/get_events_…_group_by_month` | `{userId, startYear, endYear}` |
 *
 * 另外四個（`assist/constant_service/get`、`user_service/get_user_info_by_id`、
 * `quality_service/get_my_quality_other_score`、`courier/alarm_service/find_all_by_user`）
 * 是**下拉選項/預警**用的，原生頁不需要，故不取 —— 少 4 次請求，對 QPS 紅線友好。
 *
 * ### 為什麼不翻譯 Q/GY/K
 *
 * 門戶用 `Q` / `GY` / `K` 三個代號。**我們不知道它們的確切含義就照原樣顯示** ——
 * 編一個「知識/體育/…」的中文標籤等於造詞，而造錯會誤導使用者。
 *
 * ### 為什麼四塊分開而不是一張大卡
 *
 * 四個接口**各自獨立**（倉庫裡就是各自 `runCatching`），
 * 任何一個掛了其餘照樣顯示。分成四塊，缺哪塊一眼看得出來，
 * 而不是「一整頁空白，不知道是網路壞了還是本來就沒資料」。
 *
 * 稿 `growth.png` 給的是**四行鍵值 + 一枚進度條 + 三格分數**的骨架，
 * 這裡照那個骨架排，但每一格填的是真值：拿不到就寫「—」或「尚未获取」，
 * 絕不落成 0 —— 0 分和「沒讀到」在這門課上是兩件完全不同的事。
 */
@Composable
fun GrowthPage(
    repository: GrowthRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
    ready: Boolean = true,
    /** 稿 `growth.png` 那枚「六项服务目录」的落点。为 null 时不画。 */
    onOpenDirectory: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val snapshot = (state as? ListState.Content)?.items?.firstOrNull()

    LaunchedEffect(ready) {
        if (ready && (state is ListState.FirstLoad || state is ListState.SessionExpired ||
                (state as? ListState.Content)?.meta?.stale == true)) {
            repository.refresh()
        }
    }

    val listState = rememberLazyListState()
    val veilProgress by rememberScrollProgress(listState)
    val refresh: () -> Unit = { scope.launch { runCatching { repository.refresh() } } }

    SecondaryScaffold(
        scrollProgress = veilProgress,
        title = "我的成长",
        onBack = onExit,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "growth-hero") {
                    CjhHero(
                        eyebrow = "CJH52 / 学生服务",
                        title = "我的成长",
                        sub = "独立于教务与学在城院的原生阅读入口。",
                    )
                }
                item(key = "growth-stale") { CjhStaleNotice(pageState) }

                if (snapshot == null) {
                    item(key = "growth-state") {
                        StatePlaceholder(
                            state = pageState,
                            onAction = onFallbackToWeb,
                            actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
                        )
                    }
                } else {
                    item(key = "growth-kqa") { KqaSection(snapshot.kqa) }
                    item(key = "growth-credit") { CreditSection(snapshot.credits) }
                    item(key = "growth-physique") { PhysiqueSection(snapshot.physique) }
                    item(key = "growth-activity") { ActivitySection(snapshot.activityMonths) }
                    item(key = "growth-note") {
                        CjhNotice(
                            text = "维度和进度仅演示信息组织，不冒充学校评价规则。",
                        )
                    }
                }

                item(key = "growth-origin") {
                    CjhOriginSection(
                        webLabel = "我的成长完整网页版",
                        onOpenWeb = onFallbackToWeb,
                        onRefresh = refresh,
                        onOpenDirectory = onOpenDirectory,
                    )
                }
                item(key = "growth-source") { CjhSourceCaption("CJH52 接口") }
            }
        }
    }
}

/**
 * 綜合評分：稿 `growth.png` 第一行「評分維度」+ 三格分數。
 *
 * 三格用 `.stat-grid.three` 的節奏（三列等寬、窄屏也不換行）——
 * Q/GY/K 是三個同級代號，豎排會讓人以為有先後順序。
 */
@Composable
private fun KqaSection(kqa: Cjh52KqaScore?) {
    CjhKvRow(
        label = "评分维度",
        value = if (kqa == null || kqa.isEmpty) "—" else "品德 / 学业 / 身心 / 实践",
    )
    if (kqa != null && !kqa.isEmpty) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Dims.loose),
            horizontalArrangement = Arrangement.spacedBy(Dims.cardGap),
        ) {
            StatCell(label = "Q", value = formatScore(kqa.q), modifier = Modifier.weight(1f))
            StatCell(label = "GY", value = formatScore(kqa.gy), modifier = Modifier.weight(1f))
            StatCell(label = "K", value = formatScore(kqa.k), modifier = Modifier.weight(1f))
        }
    }
    // ★ 這句必須留著：Q/GY/K 是學校系統裡的代號，我們沒有可靠釋義，
    //   所以原樣顯示。寫清楚「原樣」比讓使用者猜「Q 是不是期中考」要好。
    CjhNotice(text = "Q / GY / K 是学校系统里的维度代号，原样显示以免译错。")
}

/**
 * 學分達成：稿 `growth.png` 第二行「學分進度 / 已獲取 — / 要求 —」+ 進度條。
 *
 * 進度條只在 [Cjh52CreditProgress.compulsoryRatio] 非空時畫 ——
 * 分母缺失時畫一條 0% 的空條，等於宣稱「必修課一門都沒修」。
 */
@Composable
private fun CreditSection(credits: Cjh52CreditProgress?) {
    val colors = MiuixTheme.colorScheme
    val ratio = credits?.compulsoryRatio
    CjhKvRow(
        label = "学分进度",
        value = if (credits == null) {
            "—"
        } else {
            "已获取 " + (credits.compulsoryCredits?.let(::formatScore) ?: "—") +
                " / 要求 " + (credits.compulsoryRequirements?.let(::formatScore) ?: "—")
        },
    )
    if (ratio != null) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dims.loose)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dims.progressBarHeight)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                    .background(colors.dividerLine),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.toFloat().coerceIn(0f, 1f))
                        .height(Dims.progressBarHeight)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                        .background(colors.primary),
                )
            }
            Text(
                text = "必修课完成 ${(ratio * 100).toInt()}%" +
                    (
                        credits?.let { c ->
                            val done = c.compulsoryCourseNumber
                            val total = c.compulsoryCourseTotal
                            if (done != null && total != null) "（$done / $total 门）" else ""
                        }.orEmpty()
                        ),
                fontSize = Type.caption,
                lineHeight = Type.lineCaption,
                color = colors.onSurfaceVariantSummary,
                style = TextStyle(fontFeatureSettings = Type.tabular),
                modifier = Modifier.padding(top = Dims.tight),
            )
        }
    } else if (credits != null && credits.totalCredits == null && credits.avgScore == null) {
        CjhNotice(text = "服务端没有回传学分数字。")
    }
}

/**
 * 體測成績：每學年一行。稿 `growth.png` 第三行是「體測 / 尚未獲取，不是 0 分」。
 *
 * 只顯示 `schoolYear`，不拼 `name` —— 真機上兩者重疊，拼起來會變成
 * 「2025-2026学年 2025」這種話。`score` 為空時整行寫「—」，不寫 0。
 */
@Composable
private fun PhysiqueSection(scores: List<Cjh52PhysiqueScore>) {
    if (scores.isEmpty()) {
        CjhKvRow(label = "体测", value = "尚未获取，不是 0 分")
        return
    }
    scores.forEach { item ->
        CjhKvRow(
            label = item.schoolYear.ifBlank { item.name.ifBlank { "体测" } },
            value = item.score?.let(::formatScore) ?: "—",
        )
    }
}

/**
 * 行為記錄（按月，≤12 個月）。
 *
 * 稿 `growth.png` 第四行是「活動月份 / 11月 · 活動 1 項樣例」。
 * 這裡按真值列出每個有記錄的月份，最多 12 行 —— 超過一屏就沒人往下看了，
 * 而完整的常年記錄在原站。
 */
@Composable
private fun ActivitySection(months: List<Cjh52ActivityMonth>) {
    // ★ 只把「確實有記錄」的月份算進來，但 `count == null` 的月份**不能**當成 0 丟掉：
    //   那是「讀不到」，不是「沒有」。它們照樣列出來、次數寫「—」。
    val active = months
        .filter { month -> month.count?.let { it > 0 } ?: true }
        .sortedByDescending { month -> month.count ?: -1 }
    if (active.isEmpty()) {
        CjhKvRow(label = "活动月份", value = "这段时间没有行为记录")
        return
    }
    active.take(12).forEach { month ->
        CjhKvRow(
            label = "${month.year} 年 ${month.month} 月",
            value = buildString {
                append(month.count?.let { "$it 次" } ?: "— 次")
                month.totalScore?.let { append(" · 得分 ${formatScore(it)}") }
            },
        )
    }
    if (active.size > 12) {
        CjhNotice(text = "只显示记录最多的 12 个月，完整记录请进入原站。")
    }
}

/**
 * 一格分數（稿 `.stat`）：標籤在上、數字在下，數字走等寬。
 *
 * 數字用 [Type.largeTitle] 而不是 [Type.displayTitle]：三格並排時
 * displayTitle 的 26sp 在 320dp 窄屏上會把「—」和「100」擠到折行。
 */
@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Dims.cardCorner))
            .background(colors.surface)
            .padding(vertical = Dims.loose, horizontal = Dims.normal),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            letterSpacing = 0.06.em,
            color = colors.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            fontSize = Type.largeTitle,
            lineHeight = Type.lineDisplayLoose,
            fontWeight = Type.weightBody,
            letterSpacing = (-0.03).em,
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFeatureSettings = Type.tabular),
            modifier = Modifier.padding(top = Dims.tight),
        )
    }
}

/**
 * 分數顯示：整數不帶小數點，其餘兩位；null 寫「—」。
 *
 * 介面回的是 `Double?`，直接 `toString()` 會出現 `92.0` 這種「精度假象」；
 * 而 `%.2f` 又會把 92 寫成 92.00。這裡按值本身決定。
 * null 是「讀不到」，**不是 0 分** —— 所以寫「—」，絕不落在 "0"。
 */
private fun formatScore(value: Double?): String {
    if (value == null) return "—"
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.CHINA, "%.2f", value)
    }
}