package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val veil by rememberScrollProgress(listState)
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    // 稿上是「先改、再保存」：编辑只改草稿，点保存才写进偏好。
    // 原来一边输一边落盘，没有回退余地，也对不上稿上的两颗按钮。
    var draft by remember { mutableStateOf(UiPrefs.periodTimes) }
    val rows = draft
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
        onBack = onClose,
        onSearch = onSearch,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                    // 稿 `.notice`：左边一条 2dp 竖线，浅底色，12.42px→11sp。
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.surface)
                            .height(IntrinsicSize.Min),
                    ) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(colors.onSurfaceVariantSummary.copy(alpha = 0.45f)),
                        )
                        Text(
                            text = "这里只编辑演示值，不更改学校课表。",
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        )
                    }
                }
            }

            // 不合法行逐条点名（稿上没有这一块，但这是总单要求保留的红线：
            // 结束早于开始不许静默存进去，否则课表会少一节）。
            if (brokenRows.isNotEmpty()) {
                item(key = "broken") {
                    Text(
                        text = brokenRows.joinToString("；") { it.second },
                        fontSize = Type.caption,
                        color = colors.error,
                        lineHeight = Type.caption * 1.6f,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // 11 行输入。灰格是学校日历已有的时间，格子里的 --:-- 是双方都未知。
            item(key = "editor") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                    PeriodTimeEditor(rows = rows) { next ->
                        draft = next
                    }
                }
            }

            // 稿 `.bottom-action`：一颗主按钮 + 一颗描边按钮，竖排、间距 10dp。
            item(key = "actions") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FillButton(
                        text = if (saved) "已保存" else "保存演示时间",
                        primary = true,
                        enabled = brokenRows.isEmpty(),
                    ) {
                        // 只有全表合法才落盘：不合法时按钮变灰，行内已经点名是哪几节。
                        UiPrefs.savePeriodTimes(draft)
                        saved = true
                    }
                    FitButton(text = "清除个人覆盖", enabled = filled > 0) {
                        confirmClear = true
                    }
                }
            }
        }
    }

    if (confirmClear) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { confirmClear = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
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
                            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
                            .wrapContentSize(Alignment.Center),
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
                            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
                            .wrapContentSize(Alignment.Center),
                    )
                }
            }
        }
    }
}

/** 稿 `.btn.primary`：primary 底、白字、999px 圆角、min-height 50px→44dp。 */
@Composable
private fun FillButton(
    text: String,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val bg = if (primary) colors.primary else colors.surfaceContainer
    val fg = if (primary) colors.onPrimary else colors.primary
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClickLabel = text, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = Type.body,
            fontWeight = Type.weightTitle,
            color = if (enabled) fg else fg.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
        )
    }
}

/** 稿 `.btn.outline`：透明底 + divider 描边 + ink 字。 */
@Composable
private fun FitButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, colors.dividerLine, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClickLabel = text, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = Type.body,
            color = if (enabled) colors.onBackground else colors.onSurfaceVariantSummary.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
        )
    }
}
