package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import com.muyao.wanzaihyperos.data.cjh52.Cjh52ActivityMonth
import com.muyao.wanzaihyperos.data.cjh52.Cjh52CreditProgress
import com.muyao.wanzaihyperos.data.cjh52.Cjh52KqaScore
import com.muyao.wanzaihyperos.data.cjh52.Cjh52PhysiqueScore
import com.muyao.wanzaihyperos.data.cjh52.GrowthRepository
import com.muyao.wanzaihyperos.data.cjh52.GrowthSnapshot
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
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
 * ### 為什麼分成四張卡而不是一張大卡
 *
 * 四個接口**各自獨立**（倉庫裡就是各自 `runCatching`），
 * 任何一個掛了其餘照樣顯示。分成四張卡，缺哪塊一眼看得出來，
 * 而不是「一整頁空白，不知道是網路壞了還是本來就沒資料」。
 */
@Composable
fun GrowthPage(
    repository: GrowthRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
    ready: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val snapshot = (state as? ListState.Content)?.items?.firstOrNull()

    LaunchedEffect(ready) {
        if (ready && state is ListState.FirstLoad) repository.refresh()
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "我的成长",
color = Color.Transparent,
                subtitle = "來自場景管理（cjh52）",
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { runCatching { repository.refresh() } } }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "重新加载",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (snapshot == null) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier.padding(innerPadding),
                onAction = onFallbackToWeb,
                actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (pageState == PageState.Stale) {
                    item {
                        Text(
                            text = "这次没刷新成功，显示的是上一次的数据",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                item { KqaCard(snapshot.kqa) }
                item { CreditCard(snapshot.credits) }
                if (snapshot.physique.isNotEmpty()) {
                    item { PhysiqueCard(snapshot.physique) }
                }
                if (snapshot.activityMonths.isNotEmpty()) {
                    item { ActivityCard(snapshot.activityMonths) }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun KqaCard(kqa: Cjh52KqaScore?) {
    SectionCard("综合评分") {
        if (kqa == null || kqa.isEmpty) {
            Text(
                text = "没有取到评分",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // 代號原樣顯示（見類註釋：不造中文標籤）。
                ScoreChip("Q", kqa.q)
                ScoreChip("GY", kqa.gy)
                ScoreChip("K", kqa.k)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                // 誠實說明：我們不知道這三個代號的中文含義。
                text = "Q / GY / K 是學校系統裡的維度代號，原樣顯示以免譯錯。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ScoreChip(label: String, value: Double?) {
    val colors = MiuixTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.let { formatScore(it) } ?: "—",
            style = MiuixTheme.textStyles.title3,
            color = if (value == null) colors.onSurfaceVariantSummary else colors.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun CreditCard(credits: Cjh52CreditProgress?) {
    SectionCard("学分达成") {
        if (credits == null) {
            Text(
                text = "没有取到学分信息",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            return@SectionCard
        }
        val colors = MiuixTheme.colorScheme
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            credits.compulsoryRatio?.let { ratio ->
                Text(
                    text = "必修课完成 ${(ratio * 100).toInt()}%（${credits.compulsoryCourseNumber}/${credits.compulsoryCourseTotal}）",
                    style = MiuixTheme.textStyles.body1,
                    color = colors.onSurface,
                )
                // 進度條：用 Box 畫（Miuix 沒有現成的 LinearProgress 帶值版本）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.dividerLine),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio.toFloat())
                            .height(6.dp)
                            .background(colors.primary),
                    )
                }
            }
            // 分母缺失時**不顯示百分比**（見 Cjh52CreditProgress.compulsoryRatio 的註釋）。
            val lines = buildList {
                credits.totalCredits?.let { add("已修学分：${formatScore(it)}") }
                credits.compulsoryCredits?.let { add("必修学分：${formatScore(it)}") }
                credits.compulsoryRequirements?.let { add("必修要求：${formatScore(it)}") }
                credits.avgScore?.let { add("平均分：${formatScore(it)}") }
            }
            if (lines.isEmpty()) {
                Text(
                    text = "服务端没有回传学分数字",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                )
            } else {
                Text(
                    text = lines.joinToString("　"),
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun PhysiqueCard(scores: List<Cjh52PhysiqueScore>) {
    SectionCard("体测成绩") {
        val colors = MiuixTheme.colorScheme
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in scores) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        // ★ 只顯示 `schoolYear`，**不拼 `name`** ——
                        //   實測兩者內容重疊（`schoolYear="2025-2026学年"`、
                        //   `name="2025"`），拼起來會變成
                        //   「2025-2026学年 2025」這種重複又難讀的結果。
                        //   真機上就是這麼顯示的，所以這裡刻意只取一個。
                        text = item.schoolYear.ifBlank { item.name },
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.score?.let { formatScore(it) } ?: "—",
                        style = MiuixTheme.textStyles.body1,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(months: List<Cjh52ActivityMonth>) {
    SectionCard("行为记录（按月）") {
        val colors = MiuixTheme.colorScheme
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 只顯示有記錄的月份（count > 0）—— 全列出來會是一大片 0。
            val active = months.filter { (it.count ?: 0) > 0 }.sortedByDescending { it.count }
            if (active.isEmpty()) {
                Text(
                    text = "这段时间没有行为记录",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                )
            } else {
                for (m in active.take(12)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${m.year}-${m.month}",
                            style = MiuixTheme.textStyles.body2,
                            color = colors.onSurface,
                            modifier = Modifier.width(90.dp),
                        )
                        Text(
                            text = "${m.count} 次",
                            style = MiuixTheme.textStyles.body2,
                            color = colors.primary,
                            modifier = Modifier.width(70.dp),
                        )
                        m.totalScore?.let {
                            Text(
                                text = "得分 ${formatScore(it)}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = colors.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                if (active.size > 12) {
                    Text(
                        text = "（只显示记录最多的 12 个月）",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/**
 * 分數顯示：整數不帶小數點，有小數才保留。
 *
 * 為什麼要處理：服務端回的是 Double，`7.0` 直接顯示會很難看，
 * 而 `toInt()` 硬轉又會把 `2.44` 變成 `2`（丟資訊）。
 */
private fun formatScore(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.CHINA, "%.2f", value)
