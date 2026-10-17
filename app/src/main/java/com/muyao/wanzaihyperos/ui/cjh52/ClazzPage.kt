package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import com.muyao.wanzaihyperos.data.cjh52.ClazzInfo
import com.muyao.wanzaihyperos.data.cjh52.ClazzRepository
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
 * 我的班級（原生化）。
 *
 * 一張資訊卡：班級名、專業、年級、學院、人數、班主任、輔導員。
 * 這是「看一次就夠」的頁面，不需要列表也不需要下拉刷新按鈕以外的東西。
 */
@Composable
fun ClazzPage(
    repository: ClazzRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
    ready: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val clazz = (state as? ListState.Content)?.items?.firstOrNull()

    LaunchedEffect(ready) {
        if (ready && state is ListState.FirstLoad) repository.refresh()
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "我的班级",
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
        if (clazz == null) {
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
                        )
                    }
                }
                item { ClazzCard(clazz) }
            }
        }
    }
}

@Composable
private fun ClazzCard(clazz: ClazzInfo) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = clazz.name,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            val rows = buildList {
                if (clazz.collegeName.isNotBlank()) add("学院" to clazz.collegeName)
                if (clazz.specialty.isNotBlank()) add("专业" to clazz.specialty)
                if (clazz.grade.isNotBlank()) add("年级" to clazz.grade)
                clazz.headcount?.let { add("人数" to "$it 人") }
                if (clazz.teacherName.isNotBlank()) add("班主任" to clazz.teacherName)
                if (clazz.counselorName.isNotBlank()) add("辅导员" to clazz.counselorName)
            }
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary,
                        // 固定標籤列寬，讓右邊的值對齊成一欄。
                        modifier = Modifier.padding(end = 12.dp).width(72.dp),
                    )
                    Text(
                        text = value,
                        style = MiuixTheme.textStyles.body1,
                        color = colors.onSurface,
                    )
                }
            }
        }
    }
}
