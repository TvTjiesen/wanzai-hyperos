package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.data.schedule.SchoolPeriods
import com.muyao.wanzaihyperos.ui.design.PeriodTimeEditor
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 节次时间表（返工总单 A4）。
 *
 * ### 为什么从首屏内联搬出来
 *
 * 稿 `screens/periods.png` 是一张**独立页**：有 hero、有说明、有底部动作。
 * 而我们把 12 行编辑器塞在设置项下面就地展开 —— 一展开就把「存储」「关于」两组
 * 顶到屏幕外，滚动位置还会跳。设置首屏现在只留一行摘要（右值「11 节」），
 * 点进来才是编辑。
 *
 * ### 这一页做三件稿上写了、原来没做的事
 *
 * 1. **说明说的是机制**，不是安慰：「你填写的优先，留空即回退学校时间」——
 *    这一句同时解释了为什么没有「删除」按钮：清空一格就是删除。
 * 2. **不合法的行要被标出来**。`PeriodTimeEditor` 只负责画格子，它不知道
 *    「结束早于开始」是错的；原来这种行会静静存进去，课表上就少一节课。
 * 3. **清除要二次确认**。整张表退回学校时间是不可撤销的（我们已经不存旧值），
 *    一颗无确认的按钮放在手指正下方，迟早会被误触。
 */
@Composable
fun PeriodsPage(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val veil by rememberScrollProgress(listState)
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val rows = UiPrefs.periodTimes
    // 只有一格填了时间是半张表：开始有、结束没有，同样算不合法。
    // 判定顺序按学校节次表的下标走，错误信息才和用户在编辑器里数到的行对得上。
    val brokenRows = rows.mapIndexedNotNull { index, (start, end) ->
        when {
            start.isBlank() && end.isBlank() -> null
            start.isBlank() || end.isBlank() -> index + 1 to "第 ${index + 1} 节只填了一半"
            end <= start -> index + 1 to "第 ${index + 1} 节结束早于开始"
            else -> null
        }
    }
    val filled = rows.count { it.first.isNotBlank() || it.second.isNotBlank() }

    SecondaryScaffold(
        scrollProgress = veil,
        title = "节次时间表",
        subtitle = "学校时间为底，你的设置优先",
        onBack = onClose,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
            ),
        ) {
            item(key = "hero") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
                    Text(
                        text = "把时间对齐。",
                        fontSize = Type.displayTitle,
                        fontWeight = Type.weightTitle,
                        letterSpacing = (-0.02).sp,
                        color = colors.onBackground,
                    )
                    Text(
                        text = "学校日历中的已知时间作为占位。你填写的优先，留空即回退学校时间；" +
                            "双方都未知时只显示节次。",
                        fontSize = Type.secondary,
                        color = colors.onSurfaceVariantSummary,
                        lineHeight = Type.secondary * 1.65f,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item(key = "summary") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(
                            colors.surface,
                            RoundedCornerShape(14.dp),
                        )
                        .padding(14.dp),
                ) {
                    Text(
                        text = "学校给了 %d 节，你填了 %d 节".format(SchoolPeriods.table.size, filled),
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                    )
                    if (brokenRows.isNotEmpty()) {
                        // 红线：不合法不等于「当作没填」—— 那会让课表静默少一节。
                        // 逐条列出是哪几节，用户才能回去改。
                        Text(
                            text = brokenRows.joinToString("；") { it.second },
                            fontSize = Type.caption,
                            color = colors.error,
                            lineHeight = Type.caption * 1.6f,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            item(key = "editor") {
                Column(Modifier.padding(top = 8.dp)) {
                    PeriodTimeEditor(rows = rows) { next ->
                        UiPrefs.savePeriodTimes(next)
                    }
                }
            }

            item(key = "clear") {
                if (filled > 0) {
                    Text(
                        text = "清除个人覆盖",
                        fontSize = Type.caption,
                        fontWeight = Type.weightTitle,
                        color = colors.primary,
                        modifier = Modifier
                            .padding(start = 22.dp, top = 4.dp, bottom = 18.dp)
                            .clickable(onClickLabel = "清除我填过的节次时间") {
                                confirmClear = true
                            },
                    )
                }
            }

            item(key = "footnote") {
                Text(
                    text = "这一页只改本机的节次时间，不动学校课表。清空某一格就是把它退回学校时间。",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                    lineHeight = Type.micro * 1.6f,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                )
            }
        }
    }

    if (confirmClear) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmClear = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Text(
                    text = "清除个人覆盖？",
                    fontSize = Type.title,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                )
                Text(
                    text = "你填过的 $filled 节会全部回到学校时间。这一步不能撤销。",
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Type.tabular),
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    lineHeight = Type.secondary * 1.6f,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 14.dp)) {
                    Text(
                        text = "取消",
                        fontSize = Type.secondary,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier
                            .clickable(onClickLabel = "不清除") { confirmClear = false }
                            .padding(6.dp),
                    )
                    Text(
                        text = "清除",
                        fontSize = Type.secondary,
                        fontWeight = Type.weightTitle,
                        color = colors.primary,
                        modifier = Modifier
                            .padding(start = 20.dp)
                            .clickable(onClickLabel = "确认清除个人节次时间") {
                                UiPrefs.savePeriodTimes(emptyList())
                                confirmClear = false
                            }
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}
