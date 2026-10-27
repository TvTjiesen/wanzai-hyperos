package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.largeTitleItem
import com.muyao.wanzaihyperos.ui.design.liquid.rememberPageChromePilot
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import androidx.compose.foundation.layout.Arrangement
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
    /** 「应用」板块：CJH52 的临时码要在那里打开一次网页才会激活。 */
    onOpenApps: () -> Unit = {},
    /** 关于 + 开源许可（B4-10）。 */
    onOpenAbout: () -> Unit = {},
    /** 「账号」二级页（B4-06：六源逐条挪进去，这一屏只留聚合入口）。 */
    onOpenAccounts: () -> Unit = {},
    /**
     * 刷新键（返回栈深度）。**光靠 `uiState.loggedIn` 不够**：它只会 false→true，
     * 已登录门户之后再登教务 / CJH52 / 智慧公寓，那个布尔不再变化 ⇒ 聚合行的「已登录 n 项」不会重算。
     */
    refreshKey: Int = 0,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    var showPeriods by remember { mutableStateOf(false) }
    // 订阅 MotionPrefs.version：`reduceTransparency` 是普通 @Volatile 字段，
    // 直接读不会触发重组 —— 开关会「动了但不变」（Motion.kt 档头写过的坑）。
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()

    // 六源快照。`ensureLoaded()` 会**同步碰加密存储**（Keystore 解密），所以整份快照
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
        largeTitleItem(pilot, leading = { AiBall(state = ballState, hitSlop = AiBall.INLINE_SLOP) })

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
        item(key = "account-entry") {
            SettingSectionLabel("账号")
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Shield,
                    tint = Tone.quiet,
                    title = "登录与账号",
                    subtitle = if (accountSources.isEmpty()) {
                        "正在读取各系统的登录状态"
                    } else {
                        val n = accountSources.count { it.status == AccountStatus.LoggedIn }
                        "已登录 $n 项 / 共 ${accountSources.size} 项 · 逐项查看与重新登录"
                    },
                    onClick = onOpenAccounts,
                    trailingChevron = true,
                )
            }
        }

        // ---- 外觀 ----------------------------------------------------------
        item(key = "appearance-label") { SettingSectionLabel("外观") }
        item(key = "appearance") {
            SettingGroup {
                // ★ 主题三行**撤掉了**（工单第 15 轮）：它和外观页第一行是同一个功能两个入口。
                //   现在主题只在外观页出现（`AppearanceOneStopTest` 守"唯一入口"）。
                SettingRow(
                    icon = SettingIcon.Glass,
                    tint = Tone.quiet,
                    title = "外观与壁纸",
                    subtitle = "主题 · 壁纸 · 柔化 · 暗度 · 液态玻璃 · 首页模块",
                    onClick = onOpenAppearance,
                )
                SettingRow(
                    // 不再共用 `Glass`：两行同一个图形 = 看起来是同一件事写了两遍。
                    icon = SettingIcon.Contrast,
                    // 磁贴底色跟着开关走：OFF 时还给强调绿，等于用颜色替一项
                    // 没生效的功能背书（复评 2026-10-22）。
                    tint = if (UiPrefs.reduceTransparency) Tone.quiet else Tone.ok,
                    title = "减弱玻璃透明度",
                    subtitle = "底栏、顶栏和浮层改成近实底（高对比文字时自动）",
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
            }
        }

        // ---- 課表 ----------------------------------------------------------
        item(key = "timetable-label") { SettingSectionLabel("课表") }
        item(key = "timetable-default-view") {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Timetable,
                    tint = Tone.ok,
                    title = "打开时先看整周课表",
                    // ★ 這行字原本寫「默認用三天窗口網格」—— 三天窗口在 T58
                    //   已經按他的要求拆掉了（「我要你把五節課全部顯示」）。
                    //   設定裡留一個不存在的選項名，比沒有這個選項更糟。
                    subtitle = "关掉则先看今天议程（板子默认）",
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
                    subtitle = "学校日历认得 ${com.muyao.wanzaihyperos.data.schedule.PeriodClock.knownCount(UiPrefs.periodTimes)} 节" +
                        " · 你另填 ${com.muyao.wanzaihyperos.data.schedule.PeriodClock.filledCount(UiPrefs.periodTimes)} 节，填过的以你为准",
                    onClick = { showPeriods = !showPeriods },
                    trailing = { Text(text = if (showPeriods) "▾" else "›", fontSize = Type.title, color = colors.onSurfaceVariantSummary) },
                )
            }
        }
        item(key = "period-editor") {
            SettingExpand(visible = showPeriods) {
                SettingGroup {
                    PeriodTimeEditor(rows = UiPrefs.periodTimes) { rows ->
                        UiPrefs.savePeriodTimes(rows)
                    }
                }
            }
        }

        // ---- 本地缓存（业主 2026-10-20 同意加缓存，這一條是我加的條件）------------
        //
        // 緩存里存的是他本人的課表/賬單/成績原文。悄悄往他手機裡寫這些、
        // 又不給一個看得見的大小和一鍵清除，這件事本身就不該做。
        item(key = "http-cache") {
            SettingGroup {
                var cacheBytes by remember { mutableStateOf(graph.httpCache.usedBytes()) }
                var cacheCount by remember { mutableStateOf(graph.httpCache.entries()) }
                SettingRow(
                    icon = SettingIcon.Info,
                    tint = Tone.ok,
                    title = "本地缓存",
                    // 副標題兩行就截，所以話要短：先說大小條數，再說一句最要緊的邊界。
                    subtitle = if (cacheCount == 0)
                        "空 · 读来的数据存 5 分钟，来回切板块不再向学校要一次"
                    else "${cacheBytes / 1024} KB · $cacheCount 条 · 只存读来的，签到交作业不经过这里",
                    onClick = {
                        cacheBytes = graph.httpCache.clearAll()
                        cacheCount = 0
                    },
                    trailing = {
                        Text(
                            text = if (cacheCount == 0) "—" else "清除",
                            fontSize = Type.summary,
                            color = if (cacheCount == 0) colors.onSurfaceVariantSummary else colors.primary,
                        )
                    },
                )
            }
        }

        // ---- 關於 ----------------------------------------------------------
        item(key = "about-label") { SettingSectionLabel("关于") }
        item(key = "about") {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Info,
                    tint = Tone.quiet,
                    title = "玩在城院",
                    subtitle = "版本 ${rememberAppVersion()} · 非官方客户端",
                )
                SettingRow(
                    icon = SettingIcon.School,
                    tint = Tone.ok,
                    title = "资料来源",
                    subtitle = "浙大城市学院门户与各业务系统，资料所有权归学校",
                )
                // B4-10：关于页（GitHub / 酷安 / 逐条开源许可）收在二级页里
                SettingRow(
                    icon = SettingIcon.Info,
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
                        title = "AI 小球调试台",
                        subtitle = "仅调试包：32 个表情、全部动画与参数逐项调",
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
                    )
                }
            }
        }
    }
}
