package com.muyao.wanzaihyperos.ui.main.pages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.muyao.wanzaihyperos.ui.design.SettingGroup
import com.muyao.wanzaihyperos.ui.design.SettingIcon
import com.muyao.wanzaihyperos.ui.design.SettingRow
import com.muyao.wanzaihyperos.ui.design.SettingSectionLabel
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
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
            // 左右 16dp 放在 contentPadding 上：与设置页同一条正文列。
            // 不放在每个 item 上 —— 组标题与卡片若各自加，图标会被顶到屏幕边缘（2026-11-08 修）。
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
            ),
        ) {
            item(key = "hero") {
                Column(Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                    Text(
                        text = "提醒只在本机排。",
                        // 读屏要能把这句当标题读出来（TypeRatchet 要求最小字号文件必须有等效标签）。
                        modifier = Modifier.semantics { heading() },
                        fontSize = Type.displayTitle,
                        fontWeight = Type.weightTitle,
                        letterSpacing = (-0.02).sp,
                        color = colors.onBackground,
                    )
                    Text(
                        text = "开启时先请求通知权限，拒绝后开关会回到关闭 —— 不假装开着。" +
                            "这里的每一项都只作用于本机通知，不改学校任何数据。",
                        fontSize = Type.secondary,
                        color = colors.onSurfaceVariantSummary,
                        lineHeight = Type.secondary * 1.65f,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item(key = "reminder-label") {
                SettingSectionLabel("提醒", icon = MarkIcon.Clock)
            }

            item(key = "reminder-rows") {
                SettingGroup {
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
                    if (UiPrefs.classReminderEnabled) {
                        Text(
                            text = "提前多久",
                            fontSize = Type.title,
                            fontWeight = Type.weightTitle,
                            color = colors.onBackground,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
                        )
                        ReminderLeadPicker(
                            selected = UiPrefs.classReminderLeadMinutes,
                            onSelect = { UiPrefs.saveReminderLead(it) },
                        )
                    }
                }
            }

            if (UiPrefs.classReminderEnabled) {
                item(key = "privacy-label") {
                    SettingSectionLabel("隐私", icon = MarkIcon.Account)
                }
                item(key = "privacy-rows") {
                    SettingGroup {
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
                    SettingSectionLabel("排程诊断", icon = MarkIcon.Warn)
                }
                item(key = "diag") {
                    SettingGroup {
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
