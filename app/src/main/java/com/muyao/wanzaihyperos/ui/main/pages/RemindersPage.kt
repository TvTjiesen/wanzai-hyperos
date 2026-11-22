package com.muyao.wanzaihyperos.ui.main.pages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.PillSwitch
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.PaperCard
import com.muyao.wanzaihyperos.ui.design.SettingIcon
import com.muyao.wanzaihyperos.ui.design.SettingRow
import com.muyao.wanzaihyperos.ui.design.SettingSectionLabel
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 上课提醒（返工总单 A5）。
 *
 * ### 三级结构
 *
 * - **一级（设置首屏）**：一行「上课提醒」+ 当前值摘要，点进来。
 * - **二级（本页）**：开关、提前多久、隐私、静音。
 * - **三级（本页内的折叠）**：「提醒为什么不响」的只读诊断。
 *
 * 原来这四件东西全部内联在设置首屏、还带一个 12 行的静音时段选择器：
 * 一打开开关，「存储」「关于」两组就被顶到屏幕外，用户以为设置不见了。
 *
 * ### 为什么权限失败要把开关落回关
 *
 * 稿 `reminders.png` 写「开启时先请求通知权限，拒绝后开关回到关闭」。
 * 先落成「开」是为了让权限框弹出的那一瞬界面不闪回关；但权限回调必须
 * 把**真实结果**写回来 —— 一个开着却永远不会响的提醒，比关着更糟。
 */
@Composable
fun RemindersPage(
    onClose: () -> Unit,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val veil by rememberScrollProgress(listState)
    var showDiag by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        UiPrefs.saveClassReminderEnabled(granted)
    }

    SecondaryScaffold(
        scrollProgress = veil,
        title = "上课提醒",
        onBack = onClose,
        onSearch = onSearch,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            // 左右 16dp 放在 contentPadding 上：与设置页同一条正文列。
            // 不放在每个 item 上 —— 组标题与卡片若各自加，图标会被顶到屏幕边缘（2026-11-08 修）。
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = pageBottomInsetDp(LocalNavBarVisible.current),
            ),
        ) {
            item(key = "reminder-rows") {
                // 稿是 .paper.flush：表面色 + 1px 描边 + 圆角，无阴影。
                // 设置页的 SettingGroup 是玻璃卡，比稿重一档。
                PaperCard {
                    SettingRow(
                        icon = SettingIcon.Alarm,
                        tint = if (UiPrefs.classReminderEnabled) Tone.ok else Tone.quiet,
                        title = "上课提醒",
                        subtitle = reminderSubtitle(context),
                        trailing = {
                            PillSwitch(
                                checked = UiPrefs.classReminderEnabled,
                                label = "上课提醒",
                            ) {
                                val on = !UiPrefs.classReminderEnabled
                                val already = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (on && !already) {
                                    UiPrefs.saveClassReminderEnabled(true)
                                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    UiPrefs.saveClassReminderEnabled(on)
                                }
                            }
                        },
                    )
                }
            }

            if (UiPrefs.classReminderEnabled) {
                item(key = "lead-heading") {
                    Text(
                        // 稿 h3 = 20.3px × 0.886 ≈ 18sp，正好是 Type.title；
                        // 它在这页是**小节标题**，所以带上 heading 语义给读屏。
                        text = "提前多久",
                        modifier = Modifier
                            .padding(top = 18.dp, bottom = 10.dp)
                            .semantics { heading() },
                        fontSize = Type.title,
                        fontWeight = Type.weightTitle,
                        color = colors.onBackground,
                    )
                }

                item(key = "lead-chips") {
                    ReminderLeadPicker(
                        selected = UiPrefs.classReminderLeadMinutes,
                        onSelect = { UiPrefs.saveReminderLead(it) },
                    )
                }

                item(key = "lead-notice") {
                    // 稿 `.notice`：左侧 2dp 竖线 + surface 底 + 11dp 圆角，正文 quiet。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
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
                            text = reminderNotice(context),
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                            lineHeight = Type.caption * 1.6f,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        )
                    }
                }
            }

            if (UiPrefs.classReminderEnabled) {
                item(key = "privacy-label") {
                    SettingSectionLabel("隐私", icon = MarkIcon.Account)
                }
                item(key = "privacy-rows") {
                    PaperCard {
                        SettingRow(
                            icon = SettingIcon.Shield,
                            tint = Tone.ok,
                            title = "通知里不显示课名与教室",
                            subtitle = if (UiPrefs.reminderPrivate) {
                                "锁屏只写「即将上课」，具体课程要进应用才看得到"
                            } else {
                                "锁屏直接显示课程、时间与教室"
                            },
                            trailing = {
                                PillSwitch(checked = UiPrefs.reminderPrivate, label = "提醒隐私") {
                                    UiPrefs.saveReminderPrivate(!UiPrefs.reminderPrivate)
                                }
                            },
                        )
                        SettingRow(
                            icon = SettingIcon.Moon,
                            tint = if (UiPrefs.quietHoursEnabled) Tone.ok else Tone.quiet,
                            title = "静音时段",
                            subtitle = if (UiPrefs.quietHoursEnabled) {
                                val s = "%02d:00".format(UiPrefs.quietHoursStart)
                                val e = "%02d:00".format(UiPrefs.quietHoursEnd)
                                "$s – $e 不发响，仍留一条无声摘要 · 只约束本应用"
                            } else {
                                "默认关。开启后这段时间的提醒不响铃，但不会吞掉"
                            },
                            trailing = {
                                PillSwitch(checked = UiPrefs.quietHoursEnabled, label = "静音时段") {
                                    UiPrefs.saveQuietHoursEnabled(!UiPrefs.quietHoursEnabled)
                                }
                            },
                        )
                        if (UiPrefs.quietHoursEnabled) {
                            QuietHoursPicker(
                                start = UiPrefs.quietHoursStart,
                                end = UiPrefs.quietHoursEnd,
                                onSelect = { s, e -> UiPrefs.saveQuietHours(s, e) },
                            )
                        }
                    }
                }

                // 三级：只读诊断。**不在这里申请权限**（那是系统设置的事），
                // 只报告「此刻到底允许了什么」——四种原因必须逐条给结论。
                item(key = "diag-label") {
                    Text(
                        text = "排程诊断",
                        fontSize = Type.title,
                        color = colors.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                item(key = "diag") {
                    PaperCard {
                        SettingRow(
                            icon = SettingIcon.Info,
                            tint = if (showDiag) Tone.ok else Tone.quiet,
                            title = "提醒为什么不响",
                            subtitle = "只读检查：应用通知、课程渠道、精确闹钟、已排条数",
                            onClick = { showDiag = !showDiag },
                            trailing = {
                                Text(
                                    text = if (showDiag) "▾" else "›",
                                    fontSize = Type.title,
                                    color = colors.onSurfaceVariantSummary,
                                )
                            },
                        )
                        if (showDiag) {
                            ReminderDiagnostics(context)
                        }
                    }
                }
            }

            item(key = "footnote") {
                Text(
                    text = "提醒由本机闹钟触发，不经过服务器。关掉开关会立刻取消已排的提醒。",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                    lineHeight = Type.micro * 1.6f,
                    modifier = Modifier.padding(top = 14.dp, bottom = 24.dp),
                )
            }
        }
    }
}
