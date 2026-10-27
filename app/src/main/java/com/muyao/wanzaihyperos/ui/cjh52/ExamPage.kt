package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import com.muyao.wanzaihyperos.data.cjh52.Cjh52Exam
import com.muyao.wanzaihyperos.data.cjh52.ExamRepository
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.Type
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
 * - 有資料時，只顯示**能從常見欄位名認出來**的（`name`/`title`/`examName`），
 *   認不出來就顯示「（無法顯示的考試條目）」而不是編一個假標題；
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
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val exams = (state as? ListState.Content)?.items.orEmpty()

    LaunchedEffect(ready) {
        if (ready && state is ListState.FirstLoad) repository.refresh()
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "我的考试",
color = Color.Transparent,
                subtitle = "来自场景管理（cjh52）",
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
        if (exams.isEmpty()) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier.padding(innerPadding),
                detail = if (pageState == PageState.Empty) "目前没有考试安排" else null,
                onAction = onFallbackToWeb,
                actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "共 ${exams.size} 场",
                        style = MiuixTheme.textStyles.body1.copy(fontFeatureSettings = Type.tabular),
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(exams) { exam -> ExamCard(exam) }
                item {
                    // ★ 誠實標註：這一頁的欄位還沒核實過。
                    //   與其讓使用者以為看到的就是全部，不如說清楚。
                    Text(
                        text = "说明：考试条目的栏位尚未核实（取证时没有进行中的考试），" +
                            "目前只显示能确认的信息。需要完整内容请点右上“改用网页版”。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamCard(exam: Cjh52Exam) {
    val colors = MiuixTheme.colorScheme
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                // 認不出標題欄位時**不編造**，明說無法顯示。
                text = exam.title ?: "（无法显示的考试条目）",
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // 把已知的鍵列出來，讓使用者（和我們）知道服務端到底給了什麼 ——
                // 這比顯示空白有用，也是下次補取證的線索。
                text = "服务端栏位：" + exam.raw.keys.joinToString(", "),
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
