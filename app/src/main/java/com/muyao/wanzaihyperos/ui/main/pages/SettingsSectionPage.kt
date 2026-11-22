package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.BuildConfig
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.rememberPageChromePilot
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import com.muyao.wanzaihyperos.ui.design.ai.AiBallPresentation
import com.muyao.wanzaihyperos.ui.design.ai.AiBallWork
import com.muyao.wanzaihyperos.ui.design.ai.AiBallWorkPhase
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import com.muyao.wanzaihyperos.data.reminder.ClassReminderPlan
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.muyao.wanzaihyperos.net.CacheDomain
import com.muyao.wanzaihyperos.ui.components.WanzaiOverlayDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.data.account.AccountSource
import com.muyao.wanzaihyperos.data.account.AccountStatus
import com.muyao.wanzaihyperos.data.account.snapshotAccountSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.SettingExpand
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import com.muyao.wanzaihyperos.ui.design.SettingIcon
import com.muyao.wanzaihyperos.ui.main.pages.redesign.SettingsOverviewRow
import com.muyao.wanzaihyperos.ui.main.pages.redesign.SettingsGroupTitle
import com.muyao.wanzaihyperos.ui.main.pages.redesign.SettingsConnectionCard
import com.muyao.wanzaihyperos.ui.main.pages.redesign.settingsTitleItem
import com.muyao.wanzaihyperos.ui.main.pages.redesign.SettingsCacheClearPlan
import com.muyao.wanzaihyperos.ui.main.pages.redesign.settingsCacheSizeLabel
import com.muyao.wanzaihyperos.ui.design.PillSwitch
import com.muyao.wanzaihyperos.ui.design.PeriodTimeEditor
import androidx.compose.ui.platform.LocalContext
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.springListEntrance
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 读安装包自己的 versionName。
 *
 * ⚠️ 讀不到就回「—」，**不寫死一個版本號**：寫死的數字遲早和真實包不一致，
 * 那時「關於」頁就是在骗人（也害他按錯的版本去找更新）。
 */
@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "—"
    }
}

/**
 * 设置总览：Glasmio 大标题、独立连接摘要与自增长分组列表。
 * 所有业务回调、偏好写入及提醒 helper 仍由原宿主持有。
 *
 * ## ★ 只放**真的会做事**的行
 *
 * 参照站有「检查更新 / 导出日志 / 申请适配学校」，那是因为它有 GitHub Releases 和一个
 * 适配申请服务端。我们没有：装的是自建 release，没有更新渠道，也没有日志导出。
 * **摆三行点了没反应的东西就是骗人**，所以这里一行都不放。
 *
 * 同理：旧版那两颗「重新登入／退出登入」按钮，两个都调 `onOpenSettings()`
 * （而它只是把 tab 切到设置自己）—— 等于两颗假按钮。v3 #8 把「帐号」整组重做成逐源
 * 清单之后，那颗「去重新登录」也并进了门户那一行（点它就去登录页，不再单列一行）。
 *
 * ★ 2026-10-23：上面的「全项目没有一个界面调用 logout」**已经过时** ——
 *   现在「账号」二级页（`AccountsPage`）底部就有「退出登录」，接 `AppGraph.logout()`。
 *   这条缺口已闭，不要再照旧注释办事。
 *
 * ## 落盘
 *
 * 主题、課表默認排版、節次時間表都经 `UiPrefs` 写进 SharedPreferences（明文，只放界面偏好）。
 * 凭据一律不碰这里 —— 那是 `session/SecureStore` 的事。
 */
@Composable
fun SettingsSectionPage(
    graph: AppGraph,
    uiState: AppUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLogin: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    /** 「教务系统」原生页（要在那里自己填学号+密码）。 */
    onOpenJw: () -> Unit = {},
    /** 「智慧公寓」闸门页（只能他自己粘贴入口链接）。 */
    onOpenZhgy: () -> Unit = {},
    onOpenSzty: () -> Unit = {},
    /** 「应用」板块：CJH52 的临时码要在那里打开一次网页才会激活。 */
    onOpenApps: () -> Unit = {},
    /** 关于 + 开源许可（B4-10）。 */
    onOpenAbout: () -> Unit = {},
    onOpenPreferences: () -> Unit = {},
    /** 「账号」二级页（B4-06：六源逐条挪进去，这一屏只留聚合入口）。 */
    onOpenAccounts: () -> Unit = {},
    /** 「动画与辅助显示」二级页（v2.1 稿 `screens/accessibility.png`）。 */
    /** 节次时间表搬到独立页后，首屏这一行只负责跳转（返工总单 A4）。 */
    onOpenPeriods: () -> Unit = {},
    /** 上课提醒搬到独立页（返工总单 A5）。 */
    onOpenReminders: () -> Unit = {},
    /**
     * 刷新键（返回栈深度）。**光靠 `uiState.loggedIn` 不够**：它只会 false→true，
     * 已登录门户之后再登教务 / CJH52 / 智慧公寓，那个布尔不再变化 ⇒ 聚合行的「已登录 n 项」不会重算。
     */
    refreshKey: Int = 0,
) {
    val colors = MiuixTheme.colorScheme
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val context = LocalContext.current
    var ballFacts by remember(graph) { mutableStateOf(SettingsBallSnapshot()) }
    val accountSources = ballFacts.sources
    val pageActive = com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive.current
    val entryOwner = com.muyao.wanzaihyperos.ui.main.LocalPageEntryOwner.current
    LaunchedEffect(graph, uiState.loggedIn, refreshKey, pageActive, entryOwner) {
        if (!pageActive) return@LaunchedEffect
        ballFacts = ballFacts.beginRead()
        val ticket = ballFacts.ticket
        try {
            val sources = withContext(Dispatchers.IO) {
                snapshotAccountSources(portal = graph.portalSession, tronclass = graph.tronclassSession,
                    jw = graph.jwSession, cjh52 = graph.cjh52Session, zhgy = graph.zhgySession, szty = graph.sztySession)
            }
            ballFacts = ballFacts.acceptRead(ticket, sources)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ballFacts = ballFacts.failRead(ticket) }
        finally { ballFacts = ballFacts.finishRead(ticket) }
    }
    val pilot = rememberPageChromePilot()
    val entrance = com.muyao.wanzaihyperos.ui.design.rememberSpringListEntrance(pilot.listState)
    val ballState = AiBallStates.ofAccounts(accountSources, fetching = ballFacts.loading)
    val ballWork = AiBallWork(ballFacts.phase, ballFacts.resultRevision, ballFacts.errorRevision)
    LazyColumn(state = pilot.listState, modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dims.screenH, end = Dims.screenH,
            bottom = pageBottomInsetDp(LocalNavBarVisible.current)),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        settingsTitleItem(pilot = pilot)
        item(key = "account-entry") {
            SettingsConnectionCard(
                configuration = com.muyao.wanzaihyperos.data.account.accountConfigurationSummary(accountSources),
                onClick = onOpenAccounts,
                leading = { AiBall(state = ballState, diameter = 64.dp, hitSlop = 4.dp,
                    announceArrival = false, castShadow = true,
                    presentation = AiBallPresentation.Home, work = ballWork,
                    stateDescription = com.muyao.wanzaihyperos.data.account.accountConfigurationSummary(accountSources).let {
                        it.countText() + "\n" + it.boundaryText()
                    }) },
            )
        }
        item(key = "personal-preferences") {
            Column(Modifier.springListEntrance(entrance, "personal-preferences"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsGroupTitle("让它更像你")
                GlasmioGroupedList {
                    SettingsOverviewRow(icon = SettingIcon.Glass, tint = Tone.quiet, title = "主题设置",
                        subtitle = "背景、颜色、底栏、手势与动画显示", onClick = onOpenAppearance, trailingChevron = true)
                }
            }
        }
        item(key = "learning") {
            Column(Modifier.springListEntrance(entrance, "learning"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsGroupTitle("学习与提醒")
                GlasmioGroupedList {
                    SettingsOverviewRow(icon = SettingIcon.Timetable, tint = Tone.ok, title = "默认整周课表",
                        trailing = { PillSwitch(checked = uiState.timetableGrid, label = "默认整周课表") {
                            uiState.timetableGrid = !uiState.timetableGrid
                            UiPrefs.saveTimetableGrid(uiState.timetableGrid)
                        } })
                    SettingsOverviewRow(icon = SettingIcon.Clock, tint = Tone.quiet, title = "节次时间",
                        onClick = onOpenPeriods, trailingChevron = true)
                    SettingsOverviewRow(icon = SettingIcon.Alarm, tint = Tone.quiet, title = "上课提醒",
                        subtitle = reminderSubtitle(context), onClick = onOpenReminders, trailingChevron = true)
                }
            }
        }
        item(key = "settings-more") {
            Column(Modifier.springListEntrance(entrance, "settings-more"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceChoice("偏好与备份", false, onClick = onOpenPreferences)
                AppearanceChoice("关于柒合院", false, subtitle = "版本 ${BuildConfig.VERSION_NAME}", onClick = onOpenAbout)
            }
        }
    }
}

internal data class SettingsBallSnapshot(
    val sources: List<AccountSource> = emptyList(),
    val loading: Boolean = false,
    val readFailed: Boolean = false,
    val resultRevision: Long = 0L,
    val errorRevision: Long = 0L,
    val ticket: Long = 0L,
) {
    private val known: Boolean get() = sources.isNotEmpty() && sources.none { it.status == AccountStatus.Unknown }

    val phase: AiBallWorkPhase get() = when {
        loading -> AiBallWorkPhase.Reading
        readFailed -> AiBallWorkPhase.Error
        known -> AiBallWorkPhase.Ready
        else -> AiBallWorkPhase.Unknown
    }

    fun beginRead(): SettingsBallSnapshot = copy(loading = true, readFailed = false, ticket = ticket + 1)

    fun acceptRead(readTicket: Long, next: List<AccountSource>): SettingsBallSnapshot {
        if (readTicket != ticket || !loading) return this
        val confirmed = next.isNotEmpty() && next.none { it.status == AccountStatus.Unknown }
        return copy(sources = next, readFailed = false, resultRevision = resultRevision + if (confirmed) 1 else 0)
    }

    fun failRead(readTicket: Long): SettingsBallSnapshot = if (readTicket != ticket || !loading) this else
        copy(sources = emptyList(), readFailed = true, errorRevision = errorRevision + 1)

    fun finishRead(readTicket: Long): SettingsBallSnapshot = if (readTicket != ticket) this else copy(loading = false)
}

/**
 * 「上课提醒」那行的副标题：说清**排上了几条**，而不是只说「已开启」。
 *
 * 为什么要说条数：开关打开但一条也没排上（没课表、没周次锚点、提前量比离上课还久）
 * 在这个开关上看不出任何区别 —— 他会以为开着就会响，然后不响。
 * `-1` = 还没排过（冷启动后还没走到调度那一步），这时候**不编条数**。
 */
internal fun reminderSubtitle(context: android.content.Context): String {
    // 稿 `settings`：未开启时这一行写「未开启，先确认通知权限」——先说要紧的那道闸门，
    // 而不是先描述功能（描述功能会让「关着」读起来像「已经能用，只是没开」）。
    if (!UiPrefs.classReminderEnabled) return "未开启，先确认通知权限"
    // 开了之后才谈提前量与排程条数 —— 这两行是那一支的输入。
    val lead = UiPrefs.classReminderLeadMinutes
    val count = UiPrefs.reminderPlannedCount
    val head = if (lead == 0) "上课时提醒" else "提前 $lead 分钟提醒"
    // 系统关掉精确闹钟时，闹钟变成「一小时内随便什么时候响」。这件事必须写在
    // 这一行上 —— 只写「已排 N 条」等于向他保证一件系统没答应的事。
    val late = !com.muyao.wanzaihyperos.data.reminder.ReminderScheduler.canBeExact(context)
    return when {
        count < 0 -> "$head · 还没排上（开一次课表页）"
        count == 0 -> "$head · 这七天没有要提醒的课"
        late -> "$head · 已排 $count 条（系统没给精确闹钟，可能晚到）"
        else -> "$head · 已排 $count 条"
    }
}

/**
 * 提醒页那条 notice 的正文。
 *
 * 稿上是静态一句「尚未排程。开启时先请求通知权限，拒绝后开关回到关闭。」——
 * 这里按**真实状态**分叉，因为静态文案在「已经排上 7 条」时是在骗人。
 */
internal fun reminderNotice(context: android.content.Context): String {
    if (!UiPrefs.classReminderEnabled) {
        return "尚未开启。开启时会先请求通知权限，拒绝后开关会回到关闭 —— 不假装开着。"
    }
    val count = UiPrefs.reminderPlannedCount
    val late = !com.muyao.wanzaihyperos.data.reminder.ReminderScheduler.canBeExact(context)
    return when {
        count < 0 -> "还没排上。进一次课表页就会重新排。"
        count == 0 -> "这七天没有要提醒的课。"
        late -> "已在本机排上 $count 条；系统没给精确闹钟，可能晚到几分钟。"
        else -> "已在本机排上 $count 条，只存这台手机，不改学校任何数据。"
    }
}

/**
 * 提前量三选一（5 / 10 / 15 分钟）。只在开关打开时才出现。
 *
 * 为什么不做滑杆：滑杆能拖到 1 分钟（来不及）或 60 分钟（上一节课还在上就响），
 * 这两头都是坏选择，而滑杆的每一格都得由他自己判断对不对 —— 给三个能用的就够。
 */
@Composable
internal fun ReminderLeadPicker(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    // 稿 screens/reminders.png 是**三颗各自描边的胶囊**（选中那颗描 primary、字也转 primary），
    // 不是一条灰底轨道里塞三段——轨道那种写法在浅色底上几乎看不出边界。
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClassReminderPlan.LEAD_CHOICES.forEach { minutes ->
            val on = minutes == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(
                        1.dp,
                        if (on) colors.primary else colors.dividerLine,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(onClickLabel = "提前 $minutes 分钟") { onSelect(minutes) }
                    .padding(horizontal = 17.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$minutes 分钟",
                    fontSize = Type.bodyEmphasis,
                    fontWeight = Type.weightBody,
                    color = if (on) colors.primary else colors.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 建议二的静音时段选择器（业主 2026-11-07「五项新增设置全做」）。
 *
 * 为什么是「从 / 到 各一个加减器」而不是 24 个抽签格子：
 *   静音时段是**粗粒度**设置（早半小时、晚半小时没有意义），而 24×24 两排芯片
 *   在 347dp 宽的屏上必然要横向滚动 —— 一滚动就看不见另一端，用户没法确认
 *   "我到底选了 22 到 7 还是 7 到 22"。两个数字各占一行，一眼看完。
 *
 * 小时是**循环**的：23 点再 +1 回到 0 点，0 点再 −1 回到 23 点。
 * 这样跨午夜（22:00–07:00）不需要任何特殊分支，`UiPrefs.inQuietHours` 也已经
 * 按"start > end 就跨天"处理，两边同一套语义。
 */
@Composable
internal fun QuietHoursPicker(start: Int, end: Int, onSelect: (Int, Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
    ) {
        QuietHourRow(label = "从", hour = start) { onSelect(it, end) }
        Spacer(Modifier.height(6.dp))
        QuietHourRow(label = "到", hour = end) { onSelect(start, it) }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (start == end) {
                // start == end 在实现里是「一整天」，但这个意思绝大多数人不会想到 ——
                // 与其在界面上留一个歧义，不如直说。
                "两端相同 = 全天静音。不想全天静音，把其中一端挪开一小时。"
            } else {
                val span = if (end > start) end - start else 24 - start + end
                "共 $span 小时。这段时间里提醒不响铃，但通知仍在，解锁就看得到。"
            },
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun QuietHourRow(label: String, hour: Int, onPick: (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
        Spacer(Modifier.width(10.dp))
        QuietHourStep(symbol = "−", label = "往前一小时") { onPick((hour + 23) % 24) }
        Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "%02d:00".format(hour),
                fontSize = Type.title,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                maxLines = 1,
            )
        }
        QuietHourStep(symbol = "+", label = "往后一小时") { onPick((hour + 1) % 24) }
    }
}

/**
 * 加减按钮。
 *
 * 触区 44dp 是硬规矩（见 `docs/HANDOVER-TO-GPT-DESIGN.md` §2），但**看得见的圆
 * 只有 30dp** —— 44dp 的实心圆在设置行里会显得笨重。
 */
@Composable
private fun QuietHourStep(symbol: String, label: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val isDark = com.muyao.wanzaihyperos.ui.design.liquid.isInDarkTheme()
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(44.dp)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .width(30.dp)
                .clip(CircleShape)
                .background(
                    if (isDark) Color.White.copy(alpha = 0.08f) else colors.onBackground.copy(alpha = 0.05f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = symbol, fontSize = Type.title, color = colors.onBackground)
        }
    }
}

/**
 * 建议四：提醒权限诊断（业主 2026-11-07「五项新增设置全做」）。
 *
 * ## 只读，不申请
 *
 * 这个面板**不会**弹任何系统权限框，也不会自动跳设置页。理由：他打开的是「为什么
 * 不响」，多半只是想知道原因；自动弹框会把「查一下」变成「被推销权限」。
 * 想改的，每一行右侧给一个明确的「去设置」，点了才跳。
 *
 * ## 四种原因必须分开说
 *
 * 「提醒没响」在系统里有四个彼此独立的闸门，任何一个关着都表现为同一句「没收到」：
 *   1. 应用通知总开关（`areNotificationsEnabled`）
 *   2. 本应用那条提醒渠道（Android 8+ 渠道可以被单独关掉，`importance == NONE`）
 *   3. 精确闹钟能力（Android 12+ `canScheduleExactAlarms`，关着只能"一小时内某时"）
 *   4. 根本没排上（课表没读到 / 这七天没课 / 开关刚打开还没排）
 * 合成一句「请检查系统设置」就是把四件事糊成一件，所以他下次还是不知道为什么。
 */
@Composable
internal fun ReminderDiagnostics(context: android.content.Context) {
    val colors = MiuixTheme.colorScheme
    val notif = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    val channel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)
            ?.getNotificationChannel("class_reminder")
    } else {
        null
    }
    // 渠道不存在 = 还没发过通知（渠道是第一次发时建的），不算"被关掉"。
    val channelOff = channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE
    val exact = com.muyao.wanzaihyperos.data.reminder.ReminderScheduler.canBeExact(context)
    val planned = UiPrefs.reminderPlannedCount

    fun openNotificationSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
        DiagLine(
            ok = notif,
            title = "应用通知",
            detail = if (notif) "系统允许本应用发通知" else "系统把本应用的通知整个关掉了 —— 提醒发不出来",
            action = if (notif) null else "去设置",
            onAction = ::openNotificationSettings,
        )
        DiagLine(
            ok = !channelOff,
            title = "课程提醒渠道",
            detail = when {
                channel == null -> "还没发过提醒，渠道会在第一次提醒时自动建"
                channelOff -> "「上课提醒」这条渠道被单独关掉了"
                else -> "渠道正常（重要度：${channel.importance}）"
            },
            action = if (channelOff) "去设置" else null,
            onAction = ::openNotificationSettings,
        )
        DiagLine(
            ok = exact,
            title = "精确闹钟",
            detail = if (exact) {
                "系统允许准点提醒"
            } else {
                "系统只允许模糊闹钟，提醒可能晚到一小时以内 · 设置 → 应用 → 特殊权限 → 闹钟和提醒"
            },
            action = null,
            onAction = null,
        )
        DiagLine(
            ok = planned > 0,
            title = "已排提醒",
            detail = when {
                planned < 0 -> "还没排上。开一次课表页就会重新排（提醒开关刚打开时也会）"
                planned == 0 -> "这七天没有要提醒的课"
                else -> "$planned 条已排进系统闹钟"
            },
            action = null,
            onAction = null,
        )
        Text(
            text = "这一屏只读：上面四条都是系统此刻的真实状态，本应用不会替你改动。",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

/** 诊断面板的一行：结论 + 一句原因 + 可选出口。 */
@Composable
private fun DiagLine(
    ok: Boolean,
    title: String,
    detail: String,
    action: String?,
    onAction: (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (ok) "✓" else "!",
            fontSize = Type.body,
            fontWeight = Type.weightTitle,
            color = if (ok) Tone.ok else colors.error,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = Type.summary, fontWeight = Type.weightBody, color = colors.onBackground)
            Text(text = detail, fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
        }
        if (action != null && onAction != null) {
            Text(
                text = action,
                fontSize = Type.caption,
                color = colors.primary,
                modifier = Modifier
                    .height(Dims.minTouch)
                    .clickable(onClickLabel = action) { onAction() }
                    .padding(start = 10.dp),
            )
        }
    }
}
