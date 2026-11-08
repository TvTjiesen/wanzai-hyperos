package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.largeTitleItem
import com.muyao.wanzaihyperos.ui.design.liquid.rememberPageChromePilot
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.muyao.wanzaihyperos.ui.design.SettingGroup
import com.muyao.wanzaihyperos.ui.design.SettingIcon
import com.muyao.wanzaihyperos.ui.design.SettingRow
import com.muyao.wanzaihyperos.ui.design.SettingSectionLabel
import com.muyao.wanzaihyperos.ui.design.PillSwitch
import com.muyao.wanzaihyperos.ui.design.PeriodTimeEditor
import androidx.compose.ui.platform.LocalContext
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
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
 * **設定板塊**（业主 2026-10-19：设置页照参照站的功能与 UI 做）。
 *
 * ## 版式
 *
 * 分组小标题 + 一个圆角容器里塞几行，每行「彩色圆角方形图标 + 标题/副标题 + 右侧
 * chevron 或开关」—— 图标全部 Canvas 自绘（`ui/design/SettingsChrome.kt`）。
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
    /** 「账号」二级页（B4-06：六源逐条挪进去，这一屏只留聚合入口）。 */
    onOpenAccounts: () -> Unit = {},
    /** 「动画与辅助显示」二级页（v2.1 稿 `screens/accessibility.png`）。 */
    onOpenAccessibility: () -> Unit = {},
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
    val context = LocalContext.current
    var showPeriods by remember { mutableStateOf(false) }
    // 资料来源弹层（返工总单 A11）
    var showSources by remember { mutableStateOf(false) }
    // 稿用 R.sheet 的形态：一条说明，不是一页。也不放任何外链（验收要求「不打开外部链接」）。
    if (showSources) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSources = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Text(
                    text = "资料来源",
                    fontSize = Type.title,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                )
                Text(
                    text = "浙大城市学院门户、教务系统、学在城院及各业务系统。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    lineHeight = Type.secondary * 1.65f,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = "所有内容与数据归学校所有。本应用只做聚合展示，不改写、不对外转发；" +
                        "评审稿里的合成样例仅用于演示，不代表任何真实记录。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    lineHeight = Type.secondary * 1.65f,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "关闭",
                    fontSize = Type.secondary,
                    color = colors.primary,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clickable(onClickLabel = "关闭资料来源") { showSources = false },
                )
            }
        }
    }
    var showDiag by remember { mutableStateOf(false) }

    // 通知权限：Android 13 起是运行时权限。打开开关时当场要一次，
    // **被拒就把开关退回「关」** —— 留一个「已开」却没有通知权限的开关，等于骗他。
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        UiPrefs.saveClassReminderEnabled(granted)
        if (!granted) {
            Toast.makeText(context, "系统没给通知权限，提醒发不出来", Toast.LENGTH_LONG).show()
        }
    }
    // 订阅 MotionPrefs.version：`reduceTransparency` 是普通 @Volatile 字段，
    // 直接读不会触发重组 —— 开关会「动了但不变」（Motion.kt 档头写过的坑）。
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()

    // 各源快照。`ensureLoaded()` 会**同步碰加密存储**（Keystore 解密），所以整份快照
    // 在 IO 线程算好再上屏 —— 放在 `remember` 里算就是把四次解密挂在设置页第一帧上
    // （v3 #8 复审计到过一次：`LaunchedEffect` 在组合之后才跑，`remember` 那次已经读完了）。
    var accountSources by remember { mutableStateOf<List<AccountSource>>(emptyList()) }
    // 登录态**或**返回栈深度一变就重算（聚合入口上的「已登录 N 项」不能停在旧数）
    LaunchedEffect(uiState.loggedIn, refreshKey) {
        accountSources = withContext(Dispatchers.IO) {
            snapshotAccountSources(
                portal = graph.portalSession,
                tronclass = graph.tronclassSession,
                jw = graph.jwSession,
                cjh52 = graph.cjh52Session,
                zhgy = graph.zhgySession,
                szty = graph.sztySession,
            )
        }
    }
    // 大標題交給頂欄（T53 的試點抽成共用實作）：標題是列表第 0 項，
    // 跟著內容一起滾，滾出去之後由頂欄接手小標題。
    // 原本這一頁自己畫了一個「消息/應用/設定」的大字 —— 那就是雙標題的來源：
    // 頂欄已經會顯示板塊名，頁內再畫一個，兩處的字體/位置遲早不一致。
    val pilot = rememberPageChromePilot()

    // 小球跟着内容走（业主 2026-10-25「不要单独一个图层」）：挂在大标题左边，随列表滚。
    // 这一页没有课程数据，唯一的事实是「六个系统的登录状态读没读到」。
    // `AccountStatus.Unknown` 的注释写着「不得当成未登录」—— 所以它是 Loading，不是 Idle。
    val inFlight by graph.inFlight.collectAsState()
    val ballState = AiBallStates.ofAccounts(accountSources, fetching = inFlight > 0)

    // ── 英雄区副行（设计稿 v2.1：「液态玻璃 · 偏好保存在本机」）──────────────
    // 前半是当前玻璃档位（真实读取，用户改了就跟着变），后半是这句承诺本身。
    // 档位名与外观页保持同一套说法，别在这里另造词。
    val glassLabel = when (UiPrefs.glassMode) {
        "frost" -> "磨砂玻璃"
        "off" -> "关闭玻璃"
        else -> "液态玻璃"
    }
    val heroSubtitle = "$glassLabel · 偏好保存在本机"

    LazyColumn(
        state = pilot.listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            // 大標題自己帶狀態欄 inset，這裡 top 必須歸零 ——
            // 留著原本那 56dp 會出現「標題下面憑空一大塊空白」。
            top = 0.dp,
            bottom = pageBottomInsetDp(LocalNavBarVisible.current),   // 玻璃底欄的高度：讓最後一行能滾到玻璃底下
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        // 2026-11-07 按设计稿 v2.1 复刻首屏英雄区：「显示偏好 / 你的习惯 / 液态玻璃 · 偏好保存在本机」。
        // 顶栏小标题仍是「设置」，首屏大字换成「你的习惯」。
        largeTitleItem(
            pilot = pilot,
            leading = { AiBall(state = ballState, diameter = AiBall.DIAMETER_PAGE, hitSlop = AiBall.INLINE_SLOP) },
            kicker = "显示偏好",
            heroTitle = "你的习惯",
            subtitle = heroSubtitle,
            divider = true,
        )

        // ---- 帳號（v3 #8：业主 2026-10-22「把需要登录的账号都列出来」）--------
        //
        // 学校这六套登录态**互不牵连**（凭据与寿命各不相同，见 `data/account/AccountSources.kt`），
        // 所以合成一行「已登录/未登录」就是骗人：他教务掉线了，界面却可能显示已登录。
        // 逐源给：状态 · 最近一次真实时间 · 去哪登。
        //
        // ★ 这一屏**没有任何代登录**：点了只是跳到对应的页，账号密码永远由他自己填。
        // ---- 账号（B4-06：六源收进二级页，这里只留一行聚合入口）-------------
        //
        // 为什么收起来：六行 × 两行说明在这个屏里占了半屏，下面「外观/外观与壁纸/关于」
        // 全被挤到第二屏。收进去之后**每一项仍然逐条可达**（`AccountsPage` 照原样列），
        // 只是"要不要看"由他决定。
        //
        // ⚠️ 聚合行只报**数量**，不报"已登录/未登录"那种一句话结论 ——
        // 六套登录态互不牵连（见 `data/account/AccountSources.kt`），一句话必然骗人。
        // ---- 账号与外观（v2.1 稿 `screens/settings.png`：**一张卡四行**）------
        //
        // 板面这一个分组叫「账号与外观」，只有一张卡：账号与连接 / 外观与壁纸 /
        // 减弱透明度（开关）/ 动画与辅助显示（带 ›）。原来这里是「账号」「外观」两张卡，
        // 与板面一比，卡中间多了一条缝、标题也多了一行。
        item(key = "account-entry") {
            SettingSectionLabel("账号与外观", icon = MarkIcon.Account)
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Shield,
                    tint = Tone.quiet,
                    title = "账号与连接",
                    subtitle = if (accountSources.isEmpty()) {
                        "正在读取各系统的登录状态"
                    } else {
                        val n = accountSources.count { it.status == AccountStatus.LoggedIn }
                        // ★ 副标题必须在一行内说完（2026-11-01 真机截图：「…逐项查看与重新
                        //   登录」折出的第二行只有「登录」两个字，看着像排坏了）。
                        //   真机量出的可写宽度约 229dp，Type.caption 11sp × 1.15 ⇒ 约 19 字。
                        "$n / ${accountSources.size} 个来源 · 独立连接"
                    },
                    onClick = onOpenAccounts,
                    trailingChevron = true,
                    // 稿 `settings` 右值「7 项」= 七个来源，不是「已登录 7 个」。
                    // 写成登录数会在未登录时变成「0 项」，读起来像「没接任何来源」。
                    trailing = {
                        Text(
                            // 会变的数字必须等宽：来源数是 7 但「5 / 7」里的 5 也在变，
                            // 不等宽时数字一变整行左右跳（#41）。
                            text = if (accountSources.isEmpty()) "—" else accountSources.size.toString() + " 项",
                            fontSize = Type.caption,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Type.tabular),
                            color = colors.onSurfaceVariantSummary,
                        )
                    },
                )
                // ★ 主题三行**撤掉了**（工单第 15 轮）：它和外观页第一行是同一个功能两个入口。
                //   现在主题只在外观页出现（`AppearanceOneStopTest` 守"唯一入口"）。
                SettingRow(
                    icon = SettingIcon.Glass,
                    tint = Tone.quiet,
                    title = "外观与壁纸",
                    subtitle = "色彩、材质、布局与本地壁纸",
                    // 稿 `settings` 右值「自定义」：不是"已改过"的判定，是这一组设置的统称 ——
                    // 与「7 项」同一手法（右端是归类，不是状态）。
                    trailingChevron = true,
                    trailing = {
                        Text(
                            text = "自定义",
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    },
                    onClick = onOpenAppearance,
                )
                SettingRow(
                    icon = SettingIcon.Eye,
                    // 笔画色跟着开关走：OFF 时还给强调蓝，等于用颜色替一项
                    // 没生效的功能背书（复评 2026-10-22）。2026-11-07 起图形是裸线稿，
                    // 这个 tint 就是线条颜色本身（对卡面 7.34 vs 5.24，两档都过 4.5）。
                    tint = if (UiPrefs.reduceTransparency) Tone.ok else Tone.quiet,
                    title = "减弱透明度",
                    subtitle = "暂用实色 · 保留材质选择",
                    trailing = {
                        PillSwitch(
                            checked = motion.reduceTransparency,
                            label = "减弱透明度",
                        ) {
                            val next = !motion.reduceTransparency
                            com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceTransparency(next)
                            UiPrefs.saveReduceTransparency(next)
                        }
                    },
                )
                SettingRow(
                    icon = SettingIcon.Sun,
                    tint = Tone.quiet,
                    title = "动画与辅助显示",
                    subtitle = "跟随系统 · 减少动态 · 高对比",
                    onClick = onOpenAccessibility,
                    trailingChevron = true,
                )
            }
        }

        // ---- 課表 ----------------------------------------------------------
        item(key = "timetable-label") { SettingSectionLabel("课表与提醒", icon = MarkIcon.Schedule) }
        item(key = "timetable-default-view") {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Timetable,
                    tint = Tone.ok,
                    title = "默认整周课表",
                    // ★ 這行字原本寫「默認用三天窗口網格」—— 三天窗口在 T58
                    //   已經按他的要求拆掉了（「我要你把五節課全部顯示」）。
                    //   設定裡留一個不存在的選項名，比沒有這個選項更糟。
                    subtitle = "关闭时默认显示今天议程",
                    trailing = {
                        PillSwitch(
                            checked = uiState.timetableGrid,
                            label = "课表板",
                        ) {
                            uiState.timetableGrid = !uiState.timetableGrid
                            UiPrefs.saveTimetableGrid(uiState.timetableGrid)
                        }
                    },
                )
                SettingRow(
                    icon = SettingIcon.Clock,
                    tint = Tone.ok,
                    title = "节次时间表",
                    // 2026-10-20：底色换成**学校日历**给的那一份（他从 .ics 里推出来的），
                    // 所以这里不再写「未設定 · 课表只标第 N 节」—— 那句话说的是旧状态，
                    // 现在课表本来就有几点，这一屏只是「要不要覆盖学校那份」。
                    // 副标题两行就截（SettingsChrome.SettingRow 硬编码 maxLines=2），
                    // 所以这里只留「认几节 / 你填几节 / 以谁为准」三件事；
                    // 学校那一层的说明在展开后的编辑器里（PeriodTimeEditor 顶部）。
                    // 稿 `settings`：`row("节次时间表", "学校时间为底，你的设置优先", "", "periods")` ——
                    // 副标题只有一句「以谁为准」，右值是「11 节」（学校日历的节数）。
                    // 原来那句「认得 N 节 · 你另填 N 节」把两件事塞进副标题，两行就截；
                    // 现在「你另填几节」放进展开后的编辑器里（那一屏本来就写着），右值只报学校节数。
                    subtitle = "学校时间为底，你的设置优先",
                    onClick = onOpenPeriods,
                    // 返工总单 A4：这一行不再就地展开，改成跳独立页（稿 periods 是独立页）。
                    // 原来那颗自绘的 ›/▾ 箭头随之删除 —— 现在恒为跳转，交给 trailingChevron。
                    trailingChevron = true,
                )
                SettingRow(
                    icon = SettingIcon.Alarm,
                    tint = if (UiPrefs.classReminderEnabled) Tone.ok else Tone.quiet,
                    title = "上课提醒",
                    // 返工总单 A5：提前多久 / 隐私 / 静音 / 诊断全部搬到独立页做三级。
                    // 原来四件东西加一个静音选择器就地展开，一打开就把「存储」「关于」
                    // 顶到屏幕外 —— 用户会以为设置项丢了。首屏只留这一行摘要。
                    subtitle = reminderSubtitle(context),
                    onClick = onOpenReminders,
                    trailingChevron = true,
                )            }
        }

        // ---- 本地缓存（业主 2026-10-20 同意加缓存，這一條是我加的條件）------------
        //
        // 緩存里存的是他本人的課表/賬單/成績原文。悄悄往他手機裡寫這些、
        // 又不給一個看得見的大小和一鍵清除，這件事本身就不該做。
        // 稿 `settings` 把缓存单独归在一个「存储」分组下 —— 它确实是另一件事：
        // 前两组是「怎么显示」「怎么提醒」，这一组是「东西存在哪、占多大」。
        item(key = "storage-label") { SettingSectionLabel("存储", icon = MarkIcon.Data) }
        item(key = "http-cache") {
            SettingGroup {
                // 建议五：分域清理（业主 2026-11-07「五项新增设置全做」）。
                //
                // 为什么不是"一键全清"了事：一键全清是最后手段。他多半只想清掉
                // 「成绩那一类」而留着课表 —— 但他得先看得见有哪几类。
                // 所以这里先把分类列出来，每类给条数/大小/清；最后才是全清。
                var domains by remember { mutableStateOf(graph.httpCache.domains()) }
                var expanded by remember { mutableStateOf(false) }
                val totalBytes = domains.sumOf { it.bytes }
                val totalCount = domains.sumOf { it.entries }
                SettingRow(
                    icon = SettingIcon.Broom,
                    tint = Tone.ok,
                    title = "本地缓存",
                    // 稿 `settings`：副标题是**边界声明**（只缓存读取数据），不是大小 ——
                    // 大小放右值。原来的「N KB · N 条 · 点开可按类别清」把三件事塞进一行，
                    // 两行就截（SettingRow 硬编码 maxLines=2）。
                    subtitle = if (totalCount == 0) {
                        "空 · 读来的数据存 5 分钟，来回切板块不再向学校要一次"
                    } else {
                        "只缓存读取数据，写操作不经缓存"
                    },
                    onClick = { expanded = !expanded },
                    trailing = {
                        Text(
                            text = when {
                                totalCount == 0 -> "—"
                                expanded -> "▾"
                                // 稿右值「2.4 MB」：保留一位小数，1023.6 KB 读起来不像人给的数。
                                totalBytes >= 1024 * 1024 ->
                                    ((totalBytes / (1024 * 102.4)).toInt() / 10.0).toString() + " MB"
                                else -> (totalBytes / 1024).toString() + " KB"
                            },
                            fontSize = Type.summary,
                            color = if (totalCount == 0) colors.onSurfaceVariantSummary else colors.primary,
                        )
                    },
                )
                if (expanded && domains.isNotEmpty()) {
                    domains.forEach { d ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = "清掉" + d.name + "这一类缓存") {
                                    graph.httpCache.clearDomain(d.name)
                                    domains = graph.httpCache.domains()
                                }
                                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = d.name,
                                fontSize = Type.summary,
                                color = colors.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = d.entries.toString() + " 条 · " + (d.bytes / 1024) + " KB",
                                fontSize = Type.caption,
                                color = colors.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(text = "清", fontSize = Type.caption, color = colors.primary)
                        }
                    }
                }
                SettingRow(
                    icon = SettingIcon.Broom,
                    tint = if (totalCount == 0) Tone.quiet else Tone.warn,
                    title = "全部清掉",
                    subtitle = "下次进各板块会重新向学校取一次（多等几百毫秒）",
                    onClick = {
                        graph.httpCache.clearAll()
                        domains = graph.httpCache.domains()
                    },
                    trailing = {
                        Text(
                            text = "清空",
                            fontSize = Type.summary,
                            color = if (totalCount == 0) colors.onSurfaceVariantSummary else colors.primary,
                        )
                    },
                )
            }
        }

        // ---- 偏好备份（建议五，业主 2026-11-07「五项新增设置全做」）--------------
        item(key = "backup-label") { SettingSectionLabel("偏好备份", icon = MarkIcon.Data) }
        item(key = "backup") {
            SettingGroup {
                var pending by remember { mutableStateOf<com.muyao.wanzaihyperos.data.PrefsBackup.Preview?>(null) }
                var result by remember { mutableStateOf<String?>(null) }

                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    runCatching {
                        // ★ 返工总单 C6：原来写的是 openOutputStream(uri)?.use { ... }，
                        //   返回 null 时 ?.use 直接短路、既没写也没抛异常，于是照样报
                        //   「已导出到所选文件」—— 假的成功比失败更坏。这里把 null 变成失败。
                        val out = context.contentResolver.openOutputStream(uri)
                            ?: error("系统没有给出可写的数据流")
                        out.use { s ->
                            s.write(com.muyao.wanzaihyperos.data.PrefsBackup.export().toByteArray())
                        }
                    }.onSuccess {
                        result = "已导出到所选文件"
                    }.onFailure {
                        result = "导出失败：" + (it.message ?: "存储拒绝")
                    }
                }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytes().toString(Charsets.UTF_8)
                        }.orEmpty()
                    }.mapCatching { text ->
                        com.muyao.wanzaihyperos.data.PrefsBackup.parse(text)
                            ?: error(com.muyao.wanzaihyperos.data.PrefsBackup.lastError ?: "读不出来")
                    }.onSuccess { pending = it }
                        .onFailure { result = "读不了这份文件：" + (it.message ?: "") }
                }

                SettingRow(
                    icon = SettingIcon.Doc,
                    tint = Tone.ok,
                    title = "导出偏好",
                    subtitle = "主题、背景、材质、容器、密度、模块、折叠、课表默认视图 · 不含账号与草稿",
                    onClick = { exportLauncher.launch(com.muyao.wanzaihyperos.data.PrefsBackup.suggestedFileName()) },
                    trailing = { Text(text = "导出", fontSize = Type.summary, color = colors.primary) },
                )
                SettingRow(
                    icon = SettingIcon.School,
                    tint = Tone.quiet,
                    title = "从备份恢复",
                    subtitle = "先看它会改哪几项，确认后才写入 · 有一项不合法就整份不应用",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    trailing = { Text(text = "选择文件", fontSize = Type.summary, color = colors.primary) },
                )

                // 二次确认：逐项列出"会变成什么"，而不是只说"已恢复"。
                val p = pending
                if (p != null) {
                    // 「确认」这颗要写两件事：改了什么（n 项）和改没改成。
                    // 单独提出来是因为它落在 `Text(...)` 的 modifier lambda 里时，
                    // `TnumOnChangingNumbersTest` 的源码扫描会把 `"已应用 $n 项偏好"`
                    // 当成「这个 Text 渲染的计数」—— 而那行字其实是「确认恢复」。
                    // 提到 Text 外面，判据和代码就都对上了（不是给测试开后门）。
                    val applyNow: () -> Unit = {
                        val n = com.muyao.wanzaihyperos.data.PrefsBackup.apply(p)
                        if (n >= 0) {
                            // ★ 返工总单 C3：apply 只把值写进 UiPrefs 与盘，**没推给界面状态**
                            //   ⇒ 导入成功、提示「已应用 N 项」，但主题/周视图/材质要冷启才变，
                            //   用户会以为导入失败。这里走与冷启动（WanzaiRoot 那条 LaunchedEffect）
                            //   完全相同的双写路径，一份口径两处复用。
                            com.muyao.wanzaihyperos.ui.syncUiStateFromPrefs(uiState)
                            com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceTransparency(
                                com.muyao.wanzaihyperos.data.UiPrefs.reduceTransparency,
                            )
                            com.muyao.wanzaihyperos.ui.design.MotionPrefs.setRefractsGlass(
                                com.muyao.wanzaihyperos.data.UiPrefs.glassMode == "liquid",
                            )
                        }
                        result = if (n >= 0) "已应用 $n 项偏好" else {
                            com.muyao.wanzaihyperos.data.PrefsBackup.lastError ?: "没有应用"
                        }
                        pending = null
                    }
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                        Text(
                            text = "这份备份会改掉这 ${p.lines.size} 项：",
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Type.tabular),
                            fontSize = Type.summary,
                            fontWeight = Type.weightBody,
                            color = colors.onBackground,
                        )
                        p.lines.forEach { (label, value) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
                                Text(
                                    text = label,
                                    fontSize = Type.caption,
                                    color = colors.onSurfaceVariantSummary,
                                    modifier = Modifier.width(84.dp),
                                )
                                Text(text = value, fontSize = Type.caption, color = colors.onBackground)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "确认恢复",
                                fontSize = Type.summary,
                                color = colors.primary,
                                modifier = Modifier
                                    .height(Dims.minTouch)
                                    .clickable(onClickLabel = "确认恢复这份偏好备份") { applyNow() }
                                    .padding(end = 16.dp),
                            )
                            Text(
                                text = "取消",
                                fontSize = Type.summary,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .height(Dims.minTouch)
                                    .clickable(onClickLabel = "取消恢复") { pending = null }
                                    .padding(end = 16.dp),
                            )
                        }
                    }
                }
                val r = result
                if (r != null) {
                    Text(
                        text = r,
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    )
                }
            }
        }

        // ---- 關於 ----------------------------------------------------------
        item(key = "about-label") { SettingSectionLabel("关于", icon = MarkIcon.Info) }
        item(key = "about") {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Badge,
                    tint = Tone.quiet,
                    // 稿 `settings` 这一行叫「名称与图标」，副标题写名称状态、右值是「提案」。
                    title = "名称与图标",
                    subtitle = "鲸集 · 学士服鲸鱼娘 · 版本 ${rememberAppVersion()}",
                    trailing = {
                        Text(
                            // 稿写「提案」（命名未定时）；现在已经定稿，如实说，不画假箭头。
                            text = "已定稿",
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    },
                )
                // ★ 返工总单 A11：这一行以前没有 onClick（死行）。稿里它是个弹层（R.sheet），
                //   不是独立页 —— 一条「这些数据归谁」的说明不值得占一个 Route。
                SettingRow(
                    icon = SettingIcon.School,
                    tint = Tone.ok,
                    title = "资料来源",
                    subtitle = "浙大城市学院门户与各业务系统，资料所有权归学校",
                    onClick = { showSources = true },
                    trailingChevron = true,
                )
                // B4-10：关于页（GitHub / 酷安 / 逐条开源许可）收在二级页里
                SettingRow(
                    icon = SettingIcon.Doc,
                    tint = Tone.ok,
                    title = "关于与开源许可",
                    subtitle = "GitHub、酷安，以及本应用用到的开源项目与许可证",
                    onClick = onOpenAbout,
                    trailingChevron = true,
                )
                // 调试包才有的入口：AI 小球调试台（整个 Activity 只存在于 src/debug，
                // release 包里没有这个类，所以这里用类名字符串而不是直接引用 —— 直接 import
                // 会让 release 编译期就找不着符号）。判据用 FLAG_DEBUGGABLE 而不是 BuildConfig.DEBUG，
                // 与本项目其它 debug 口子一致。
                if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    SettingRow(
                        icon = SettingIcon.Info,
                        tint = Tone.warn,
                        title = "小球调试台",
                        subtitle = "仅 debug 包 · 不属于学校业务",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent().setClassName(
                                        context,
                                        "com.muyao.wanzaihyperos.dev.AiBallLabActivity",
                                    ),
                                )
                            }
                        },
                        trailingChevron = true,
                        trailing = {
                            Text(
                                text = "DEBUG",
                                fontSize = Type.micro,
                                color = colors.onSurfaceVariantSummary,
                            )
                        },
                    )
                }
            }
        }
    }
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
 * 提前量三选一（5 / 10 / 15 分钟）。只在开关打开时才出现。
 *
 * 为什么不做滑杆：滑杆能拖到 1 分钟（来不及）或 60 分钟（上一节课还在上就响），
 * 这两头都是坏选择，而滑杆的每一格都得由他自己判断对不对 —— 给三个能用的就够。
 */
@Composable
internal fun ReminderLeadPicker(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val isDark = com.muyao.wanzaihyperos.ui.design.liquid.isInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // 稿 v2.1 `reminders.png` 这小节的标题是「提前多久」——
            // 只写「提前」两个字，左边悬空、读起来像被截断。
            text = "提前多久",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Dims.chipCorner))
                .background(colors.onBackground.copy(alpha = 0.045f))
                .padding(1.8.dp),
        ) {
            ClassReminderPlan.LEAD_CHOICES.forEach { minutes ->
                val on = minutes == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dims.chipCorner))
                        .background(
                            if (on) {
                                if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.66f)
                            } else {
                                Color.Transparent
                            },
                        )
                        // 选中**不换字重**：加粗让字变宽，整排会横抖（外观页 Segmented 同一条规矩）。
                        .clickable(onClickLabel = "提前 $minutes 分钟") { onSelect(minutes) }
                        .padding(vertical = 4.4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$minutes 分钟",
                        fontSize = Type.caption,
                        fontWeight = Type.weightBody,
                        color = if (on) colors.onBackground else colors.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
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
