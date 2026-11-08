package com.muyao.wanzaihyperos

import com.muyao.wanzaihyperos.ui.cjh52.GrowthPage
import com.muyao.wanzaihyperos.ui.cjh52.ExamPage
import com.muyao.wanzaihyperos.ui.cjh52.NoticePage
import com.muyao.wanzaihyperos.ui.cjh52.ClazzPage
import com.muyao.wanzaihyperos.ui.cjh52.LecturePage
import com.muyao.wanzaihyperos.ui.cjh52.CjhHomePage
import com.muyao.wanzaihyperos.data.DemoCatalog
import com.muyao.wanzaihyperos.ui.tronclass.TronShellPage
import com.muyao.wanzaihyperos.ui.todo.TodoBoardPage
import com.muyao.wanzaihyperos.ui.web.Cjh52WarmUpGate
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.AppOpenPlan
import com.muyao.wanzaihyperos.data.AppOpenStrategy
import com.muyao.wanzaihyperos.data.HzcuHosts
import com.muyao.wanzaihyperos.data.tronclass.ScanHandoff
import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.nav.Route
import com.muyao.wanzaihyperos.nav.wanzaiRouteTransitions
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.TronclassBoot
import com.muyao.wanzaihyperos.net.shortErrorText
import com.muyao.wanzaihyperos.session.SessionState
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning
import com.muyao.wanzaihyperos.ui.WanzaiTheme
import com.muyao.wanzaihyperos.ui.busyness.BusynessPage
import com.muyao.wanzaihyperos.ui.courses.CoursesPage
import com.muyao.wanzaihyperos.ui.login.LoginPage
import com.muyao.wanzaihyperos.ui.search.SearchPage
import com.muyao.wanzaihyperos.ui.web.WebShellPage
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 把返回栈重置为「只有一个 [route] 作栈底」。
 *
 * 为什么不用 `clear()` + `add()`：`NavDisplay` 内部有
 * `require(backStack.isNotEmpty())`，而 `NavBackStack` 就是 `SnapshotStateList`；
 * 先清空会留下一个「空栈」的中间状态。这里改成**先替换栈底、再删其余**，
 * 列表全程非空，也就不会撞上那条断言。
 */
private fun NavBackStack.resetTo(route: NavKey) {
    if (isEmpty()) {
        add(route)
        return
    }
    this[0] = route
    while (size > 1) removeAt(lastIndex)
}

/**
 * 「我的課程」在門戶上的應用 id。
 *
 * 實測值 60（A3 真機走查：`打開策略=TempCode appId=60`）。
 * 抽成常量是因為它有**兩個**使用點（原生頁預取碼、回退網頁版），
 * 寫兩遍就會有改一處漏一處的風險。
 */
private const val CJH52_COURSE_APP_ID = 60

/**
 * cjh52 原生功能名 → 它在 cjh52 上的頁面路徑。
 *
 * 用於「改用網頁版」時退回**同一個功能**的網頁，而不是 cjh52 首頁。
 * 與 `AppOpenStrategy.nativeFeatureOf` 是**反向映射**，兩處要保持一致：
 * 那边按路徑決定走不走原生，这边按原生名退回對應網頁。
 */
private fun cjh52FeaturePath(feature: String): String = when (feature) {
    "NOTICE" -> "/notice"
    "EXAM" -> "/examination"
    "GROWTH" -> "/development/home"
    "CLAZZ" -> "/clazz/student"
    // ★ 以前缺这一条：爱讲座点「改用网页版」会落到 cjh52 的根路径（首页），
    //   而不是讲座页。与 AppOpenStrategy.nativeFeatureOf 的 "/lecture" 前缀对应。
    "LECTURE" -> "/lecture"
    else -> "/"
}

/**
 * 打开加密存储时的过渡页。
 *
 * 为什么需要它：`AppGraph` 首次构建会**生成 Android Keystore 主密钥**
 * （低端机上可能上百毫秒），所以它必须在后台线程做（见 [AppGraph] 的注释）。
 * 后台做就意味着首帧拿不到它，于是需要一个诚实的「正在准备」画面，
 * 而不是先渲染一个空壳再突然跳变。
 */
@Composable
private fun StartupScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = "正在打开加密存储…",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * 应用根：**唯一**的主题提供者 + 组合根持有者 + 路由栈。
 *
 * 路由只有 4 条（见 [Route]）：登录 / 主壳 / 搜索 / WebView 外壳。
 * 四个 tab **不是**路由 —— 它们是主壳内部的一个选中下标，
 * 这样「底部导航」与「路由栈」不会互相打架。
 */
@Composable
fun WanzaiApp(
    /**
     * 從外部 intent 帶進來的智慧公寓連結（B17，見 `MainActivity`）。
     *
     * 有值時：**存下憑據並直接跳進智慧公寓** ——
     * 使用者在別處點連結／分享進來，期望的就是「直接看到那個頁面」，
     * 而不是先看到一個要他再貼一次的輸入框。
     */
    incomingZhgyLink: String? = null,
    onIncomingZhgyLinkConsumed: () -> Unit = {},
    /** 调试包专用：`am start --ei debug_section N` 直接落到某板块（见 `MainActivity`）。 */
    debugSection: DebugSection? = null,
) {
    val uiState = remember { AppUiState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 键是整次投递（含序号）而不是那个 Int：同值重放要能再落一次（#42）。
    LaunchedEffect(debugSection) {
        debugSection?.let { uiState.selectedTab = it.section }
    }

    // ── 路由动效的热调通道（和底栏那套读的是同一个 `GlassTuning`）──────
    //
    // 必须经 `navTuning` 走一层再 `remember` 它：直接在 `NavDisplay` 的参数里读
    // `GlassTuning.navMs` 不会触发重组，改完 Settings 界面不动，量出来的数是假的
    //（`liquid/GlassTuning.kt` 档头那条警告对我们同样成立）。
    var navTuning by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        if (GlassTuning.refresh(context)) navTuning++
        val closeable = GlassTuning.startObserving(context) { navTuning++ }
        onDispose { closeable.close() }
    }
    // ── 无障碍三条：动画时长缩放 / 高对比文字 / 我们自己的"减弱透明度"──────
    //
    // ★ 为什么必须在这里读（业主 2026-10-20 批准的清单第 2 条）：
    //   **Compose 不会自动尊重系统的"动画时长缩放"**。开发者选项拉到 0，
    //   系统动画全没了，我们的弹簧和位移照跑 —— 用户明确说了"别动"，界面还在动。
    //   通道形状照 `GlassTuning`（同一件事两套监听迟早一套是死的）。
    DisposableEffect(Unit) {
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.refresh(context)
        val close = com.muyao.wanzaihyperos.ui.design.MotionPrefs.startObserving(context)
        onDispose { close() }
    }
    // 消费端一律走 `rememberMotion()`：它读 `MotionPrefs.version`，所以系统里一改就重组，
    // 不需要再往下传一个 CompositionLocal（同一件事留一条路）。
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val routeTransition = remember(navTuning, motion.animationsEnabled) {
        // ★ 第 9 轮审计头条：**别再传 0**。传 0 走的是 `NavMotion()`（Miuix 自带默认曲线），
        //   于是"关掉动效"= 位移照旧、时长还换成了库默认值 —— 用户想省掉的正是"整页推一遍"。
        //   现在减弱动效那一档传 `reducedMotion = true` ⇒ 140ms **纯 alpha** 淡入淡出
        //   （标准 §6：减弱动效 ≠ 零反馈；位移与弹簧全停，淡入淡出要留）。
        wanzaiRouteTransitions(
            motionMs = GlassTuning.navMs.roundToInt(),
            reducedMotion = !motion.animationsEnabled,
        )
    }

    var graph by remember { mutableStateOf<AppGraph?>(null) }
    LaunchedEffect(Unit) {
        // ★ 必须离开主线程：首次会生成 Keystore 主密钥。
        graph = withContext(Dispatchers.IO) { AppGraph.get(context) }
    }

    WanzaiTheme(themeMode = uiState.themeMode) {
        val app = graph
        if (app == null) {
            StartupScreen()
            return@WanzaiTheme
        }

        // 上次已经登录过：直接反映到界面状态上（凭据在加密存储里，不是这里读的）。
        LaunchedEffect(app) {
            // ★ 偏好落盘在这一条上：冷启动读一次，把主题/課表排版恢复到界面状态。
            //   读盘放在已登录判断之前 —— 未登录也要记得他选过浅色。
            com.muyao.wanzaihyperos.data.UiPrefs.load(context)
            uiState.themeMode = AppThemeMode.entries.firstOrNull {
                it.name == com.muyao.wanzaihyperos.data.UiPrefs.themeModeName
            } ?: AppThemeMode.System
            uiState.timetableGrid = com.muyao.wanzaihyperos.data.UiPrefs.timetableGrid
            // 减弱透明度是我们自己存的那一条（Android 没有系统级开关），所以要把盘里的值
            // 推给运行期那份；推漏的后果是"设置里开了、重启又没了" —— 这类我们栽过。
            com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceTransparency(
                com.muyao.wanzaihyperos.data.UiPrefs.reduceTransparency,
            )
            // 「磨砂」那条也一样：盘里存的是 glassMode，运行期要推给 MotionPrefs，
            // 否则重启后设置页显示磨砂、屏幕上却还是带折射的液态。
            com.muyao.wanzaihyperos.ui.design.MotionPrefs.setRefractsGlass(
                com.muyao.wanzaihyperos.data.UiPrefs.glassMode == "liquid",
            )
            // ★ 返工总单 C4：「减少动态」「高对比」现在是应用内偏好，冷启动同样要推给运行期，
            //   否则表现为「设置里开着、重启就没了」—— 这类我们栽过（见上面两条注释）。
            com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceMotion(
                com.muyao.wanzaihyperos.data.UiPrefs.reduceMotion,
            )
            com.muyao.wanzaihyperos.ui.design.MotionPrefs.setHighContrastOverride(
                com.muyao.wanzaihyperos.data.UiPrefs.highContrastOverride,
            )
            if (app.portalSession.state == SessionState.LoggedIn) {
                uiState.loggedIn = true
                uiState.displayAccount = app.displayAccount()
            }
        }

        // 启动时落到哪一页：已登录直奔主壳，否则先登录。
        val backStack = rememberNavBackStack<Route>(
            when (app.startRoute()) {
                AppGraph.StartRoute.Main -> Route.Main
                AppGraph.StartRoute.Login -> Route.Login
            },
        )

        // ★ B17：收到外部帶進來的智慧公寓連結（使用者在別處點連結／分享進來）。
        //
        //   這裡直接**存下憑據並跳進智慧公寓**，而不是落到閘門頁要他再貼一次 ——
        //   他點連結的意圖就是「看那個頁面」。
        //
        //   ⚠️ 只在已登入時處理，否則會出現「沒登入卻直接開了公寓頁」這種半狀態。
        //     未處理的連結會在登入後再次觸發（MainActivity 的 state 還在）。
        LaunchedEffect(incomingZhgyLink, uiState.loggedIn) {
            val link = incomingZhgyLink ?: return@LaunchedEffect
            if (!uiState.loggedIn) return@LaunchedEffect
            val code = com.muyao.wanzaihyperos.session.ZhgySession.extractCode(link)
            if (code.isNullOrBlank()) {
                // 抽不出碼就別假裝成功 —— 直接無視，使用者仍可從閘門頁手動設定。
                onIncomingZhgyLinkConsumed()
                return@LaunchedEffect
            }
            app.zhgySession.save(code, System.currentTimeMillis())
            // 避免重複堆疊：已經在這一頁就不再放一次。
            if (backStack.lastOrNull() != Route.Zhgy) {
                backStack.add(Route.Zhgy)
            }
            onIncomingZhgyLinkConsumed()
        }

        /**
         * 点应用的**统一入口**：按 [AppOpenStrategy] 决定走哪条路。
         *
         * 三条路各自的理由（见 `AppOpenStrategy` 的可信度分級表）：
         * - **普通外鏈**：第三方系統各自有登入，塞門戶憑據反而多一處泄漏面；
         * - **tempcode**：門戶自建系統要一次性換碼。**每次點擊都現取**，
         *   因為 tempcode 是**一次性**的（實測兩次取值不同），緩存復用會在子系統側靜默失效；
         * - **僅校園網**：先告訴使用者，而不是讓他對著白屏等超時。
         *
         * ⚠️ 取碼失敗時**不打開**子系統 —— 拼一個沒有 `tempcode` 的地址，
         * 使用者只會落到子系統的登入頁，看起來像「App 壞了」。
         */
        val openApp: (PortalApp) -> Unit = { target ->
            val plan = AppOpenStrategy.decide(target)
            // 只记「走了哪条路 + 应用 id」，不记地址与任何凭据（地址里之后会带 tempcode）。
            android.util.Log.i("WanzaiOpen", "打开策略=${plan.javaClass.simpleName} appId=${target.id}")
            when (plan) {
                is AppOpenPlan.TempCode -> scope.launch {
                    val tempCode = runCatching { app.api.issueTempCode() }.getOrNull()
                    if (tempCode == null) {
                        Toast.makeText(
                            context,
                            "没能取到一次性临时码，请稍后重试（不会用旧码凑数）",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        // ★ B1：把 tempcode 存进 CJH52 会话。
                        //
                        // 实测定理（docs/B0-CJH52-CONTRACT.md §2.1）：
                        // 门户签发的 tempcode **本身就是 CJH52 的 Bearer token**，
                        // 不需要任何换码步骤。所以原生直连只要在手里有它就够了。
                        //
                        // 这条同时**不影响**下面照旧打开 WebView ——
                        // 两条路并存：WebView 是已验证可用的路径，
                        // 原生页是新增的；原生页失败时用户仍可走 WebView。
                        runCatching {
                            app.cjh52Session.save(tempCode, System.currentTimeMillis())
                        }
                        val url = AppOpenStrategy.buildTempCodeUrl(
                            appUrl = plan.url,
                            appId = plan.appId,
                            colorArgb = target.tint?.toArgb(),
                            tempCode = tempCode,
                            nowMillis = System.currentTimeMillis(),
                        )
                        backStack.add(Route.Web(target.id, target.name, url))
                    }
                }

                is AppOpenPlan.CampusOnly -> Toast.makeText(
                    context,
                    "“${target.name}”仅校园网内可打开。注意：一般超时不一定都是校园网的问题。",
                    Toast.LENGTH_LONG,
                ).show()

                // ★ B1：「我的課程」原生頁。
                //
                // 必須先取一次 tempcode 存進 CJH52 會話 —— B0 實測
                // （契約文檔 §2.1）證明**門戶簽發的 tempcode 本身就是 CJH52 的 Bearer token**，
                // 沒有它原生頁發出的請求會是 401。
                //
                // 取不到碼**不進原生頁**（進去只會看到「需要先登入」），
                // 而是老實提示 —— 不拿舊碼湊數（tempcode 一次性，A3 已驗證）。
                // ★ B1：「我的課程」原生頁。
                //
                // ⚠️ 這裡**不取碼、不存會話** —— 那是閘門（[Cjh52WarmUpGate]）的職責。
                //
                // 先前版本在這裡取碼並存進會話，結果是**壞的**：
                // 那枚碼從未被瀏覽器載入過，直接當 Bearer 用會被拒（401）；
                // 而閘門看到「會話已有 token」就放行了，於是原生頁必定失敗。
                //
                // 正確的分工：
                // - 閘門：負責「讓會話可用」（取碼 → 真實 WebView 載入 → 讀回 token）；
                // - 這裡：只負責導航。
                is AppOpenPlan.NativeCourses -> backStack.add(Route.Courses)

                // ★ B3 剩餘三項：日常通知 / 我的考試 / 我的成長。
                //
                // 與 NativeCourses 一樣，這裡**不取碼、不存會話** ——
                // 那是預熱閘門（Cjh52WarmUpGate）的職責。
                // 先前在別處存「未激活的碼」導致閘門誤判，那次教訓記在
                // docs/B1-WALKTHROUGH.md §3。
                is AppOpenPlan.NativeFeature -> backStack.add(Route.Cjh52Feature(plan.feature))

                // ★ 稿 cjh.png：cjh52 的**根地址**（門戶「場景管理」那一格）→ 學生服務目錄頁。
                //
                // 之前它落到 TempCode 那一支，直接開了個網頁；而我們已經把六個子頁都做成原生了，
                // 根地址還回到網頁等於把使用者送出去。
                is AppOpenPlan.CjhHome -> backStack.add(Route.CjhHome)
                // B17：智慧公寓 → 閘門頁（沒憑據就請使用者貼連結）。
                is AppOpenPlan.ZhgyGate -> backStack.add(Route.Zhgy)

                // B18：教務查詢類入口 → 原生頁（考試／成績）。
                is AppOpenPlan.JwNative -> backStack.add(Route.Jw)

                // B19：智雲課堂 → 原生週課表頁。
                is AppOpenPlan.ZyNative -> backStack.add(Route.Zy)

                // ★ A4：学在城院**手机版免登**（带自动续期）。
                //
                // 旧实现只在输入密码那一刻换一次 token 对，之后直接用存的旧值。
                // Keycloak 的 access_token 寿命约 1 小时 —— 真机实测 20:53 登录、
                // 22:44 再打开就**卡在换票入口进不去**，只能重新输密码。
                //
                // 现在每次打开先试 refresh_token 续期（标准 OIDC，不需要密码）：
                // - 续期成功 → 用新 token；
                // - 续期失败 → **回退用旧 token 试一次**（它可能还在寿命内），
                //   并打日志 —— 旧 token 也失效时 SPA 会落到它自己的登录页，
                //   那是最后兜底，不是我们的错，但日志要说清楚走到哪一步。
                // ★ 學在城院：走**原生頁**（我的課 + 待交作業）。
                //
                // 只負責導航；取數失敗時由原生頁底部的「改用網頁版」回退，
                // 回退那條複用下面的 identity-token 邏輯（已驗證的免登路）。
                is AppOpenPlan.NativeTronclass -> backStack.add(Route.TronHome(tileUrl = plan.tileUrl))

                is AppOpenPlan.TronclassFreeLogin -> scope.launch {
                    val access = app.tronclassSession.accessToken()
                    val refresh = app.tronclassSession.refreshToken()
                    val fresh = if (refresh.isNullOrBlank()) null else runCatching {
                        app.casService.refreshTronclassTokens(refresh)
                    }.getOrNull()
                    val url = when {
                        fresh != null -> {
                            // 续期成功：落盘（SSO cookie 原样带上，别丢）。
                            app.tronclassSession.saveTokens(
                                fresh.accessToken,
                                fresh.refreshToken,
                                ssoCookiesJson = app.tronclassSession.ssoCookiesJson(),
                            )
                            android.util.Log.i("WanzaiLogin", "学在城院 token 已续期")
                            TronclassBoot.identityTokenUrl(fresh.accessToken, fresh.refreshToken)
                        }
                        !access.isNullOrEmpty() -> {
                            if (fresh == null && !refresh.isNullOrBlank()) {
                                android.util.Log.w(
                                    "WanzaiLogin",
                                    "学在城院续期失败，回退用旧 token（可能已过期，SPA 会落到自己的登录页）",
                                )
                            }
                            TronclassBoot.identityTokenUrl(access, refresh)
                        }
                        else -> plan.tileUrl   // 从没登录过：进 SPA 自己的登录页
                    }
                    backStack.add(Route.Web(target.id, target.name, url))
                }

                // 普通外鏈 / 無地址：開外鏈即可。
                else -> backStack.add(Route.Web(target.id, target.name, target.url))
            }
        }

        // 未读数角标。**未知（CountValue.Unknown）时不画角标**，
        // 但也不显示 0 —— 具体「未读 —」由消息页用文字承担。
        val unreadState by app.unread.state.collectAsState()
        val unreadCount = (unreadState.value as? CountValue.Known)?.value


        // ── 上课提醒 ────────────────────────────────────────────────────
        //
        // 三件事：读开关 → 用 [ReminderBrain] 把课表算成一串提醒 → 交给
        // [ReminderScheduler] 挂成系统闹钟。
        //
        // ★ 为什么不在「设置页打开开关」那一刻就排：那一刻 likely 两个课表来源都还是
        //   FirstLoad，plan 会返回 null（不知道），排出来是空的。真正的排程点是
        //   **课表状态变了**，所以这里盯的是两个仓库的 state，而不是开关本身。
        val reminderOn = com.muyao.wanzaihyperos.data.UiPrefs.classReminderEnabled
        val reminderLead = com.muyao.wanzaihyperos.data.UiPrefs.classReminderLeadMinutes
        val reminderJwState by app.jwTimetable.state.collectAsState()
        val reminderTronState by app.schedule.state.collectAsState()
        val reminderJwCrawling = app.jwTimetable.isCrawling.value
        LaunchedEffect(app, reminderOn, reminderLead, reminderJwState, reminderTronState, reminderJwCrawling) {
            if (!reminderOn) {
                // 关掉就该真的不响：不清的话，之前排上的那些还会在今天继续弹。
                withContext(Dispatchers.IO) {
                    runCatching { com.muyao.wanzaihyperos.data.reminder.ReminderScheduler.clear(context) }
                }
                return@LaunchedEffect
            }
            val planned = withContext(Dispatchers.IO) {
                runCatching {
                    com.muyao.wanzaihyperos.data.reminder.ReminderBrain.plan(
                        jwState = reminderJwState,
                        jwCrawling = reminderJwCrawling,
                        tronState = reminderTronState,
                        times = com.muyao.wanzaihyperos.data.UiPrefs.periodTimes,
                        now = java.time.LocalDateTime.now(),
                        leadMinutes = reminderLead,
                        // 第几周只认锚点推算：算不出就 null，整段不排（不猜单双周）。
                        weekOf = { date -> com.muyao.wanzaihyperos.data.UiPrefs.weekFromAnchor(date.toEpochDay()) },
                    )
                }.getOrNull()
            }
            // null = 这次还不知道（还在取 / 取失败）：**保留已排的那些**，一条都不动。
            if (planned == null) return@LaunchedEffect
            withContext(Dispatchers.IO) {
                runCatching { com.muyao.wanzaihyperos.data.reminder.ReminderScheduler.sync(context, planned) }
            }
            com.muyao.wanzaihyperos.data.UiPrefs.saveReminderCount(planned.size)
        }

        LaunchedEffect(app, uiState.loggedIn) {
            if (uiState.loggedIn) {
                // force = false：自动刷新受 15 分钟下限约束；全 App 没有轮询定时器。
                runCatching { app.unread.refresh(force = false) }
            }
        }

        // ★ 學在城院「改用網頁版」的**唯一**出口。
        //
        // 抽成函式而不是在每個原生頁各寫一遍：這段涉及
        // 「先續期 token → 失敗回退舊 token → 再失敗才用原始地址」三步，
        // 抄三遍就會有一處漏掉續期（那條路徑的表現是「使用者突然要重新登入」）。
        fun openTronWeb(tileUrl: String) {
            scope.launch {
                val access = app.tronclassSession.accessToken()
                val refresh = app.tronclassSession.refreshToken()
                val fresh = if (refresh.isNullOrBlank()) null else runCatching {
                    app.casService.refreshTronclassTokens(refresh)
                }.getOrNull()
                val url = when {
                    fresh != null -> {
                        // 續期成功：落盤（SSO cookie 原樣帶上）。
                        app.tronclassSession.saveTokens(
                            fresh.accessToken,
                            fresh.refreshToken,
                            ssoCookiesJson = app.tronclassSession.ssoCookiesJson(),
                        )
                        TronclassBoot.identityTokenUrl(fresh.accessToken, fresh.refreshToken)
                    }
                    !access.isNullOrEmpty() -> TronclassBoot.identityTokenUrl(access, refresh)
                    tileUrl.isNotBlank() -> tileUrl
                    else -> HzcuEndpoints.TRONCLASS_MOBILE_TILE
                }
                backStack.add(Route.Web(156, "学在城院", url))
            }
        }

        // 打开一条门户给的地址（消息 openurl 与扫到的网址都走这里）。
        //
        // 原樣交過去對 cjh52 是不行的（真機 2026-10-30 實測）：
        // 它的 SPA 只認 URL 上的臨時碼，沒帶碼就 replace 到登錄頁、
        // 一幀內容都不渲染 —— 使用者看到一片灰，還以為 App 壞了。
        // cjh52 的地址先換一枚一次性碼再打開（與「我的課程」網頁版同路）；
        // 其餘域維持原樣：門戶自己也是丟給 WebView。
        //
        // 標題由呼叫方給：消息裡是「消息详情」，掃一掃裡是「扫到的网页」。
        fun openUrlWithTempCode(raw: String, title: String) {
            HzcuEndpoints.portalOpenUrl(raw)?.let { url ->
                if (AppOpenStrategy.needsTempCode(url)) {
                    scope.launch {
                        val tempCode = runCatching { app.api.issueTempCode() }.getOrNull()
                        if (tempCode == null) {
                            Toast.makeText(
                                context,
                                "没能取到一次性临时码，这条消息打不开，请稍后重试",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            backStack.add(
                                Route.Web(
                                    0,
                                    title,
                                    AppOpenStrategy.buildTempCodeUrl(
                                        appUrl = url,
                                        appId = 0,
                                        colorArgb = null,
                                        tempCode = tempCode,
                                        nowMillis = System.currentTimeMillis(),
                                    ),
                                ),
                            )
                        }
                    }
                } else {
                    backStack.add(Route.Web(0, title, url))
                }
            }
        }

        // ── 容器背景风格：**根上读一次**，往下用 CompositionLocal 传 ─────────────
        //
        // 为什么不在每个页面各读一次 `UiPrefs.containerStyle`：那样开关切过去时，
        // 每一层各自订阅、各自重组，会出现「先变的页已经换了、后变的还没换」的错位。
        // 在这里读一次，同一帧里所有下游一起变。
        val containerStyle = com.muyao.wanzaihyperos.ui.design.ContainerStyle.of(
            com.muyao.wanzaihyperos.data.UiPrefs.containerStyle,
        )

        androidx.compose.runtime.CompositionLocalProvider(
            com.muyao.wanzaihyperos.ui.design.LocalContainerStyle provides containerStyle,
        ) {
            NavDisplay(
                backStack = backStack,
                // 参考（变焦环）的推/退动效，见 `nav/RouteTransitions.kt` 的换算说明。
                transition = routeTransition,
                onBack = { backStack.removeLastOrNull() },
            ) {
            entry<Route.Login> {
                LoginPage(
                    onLoggedIn = { account ->
                        uiState.loggedIn = true
                        uiState.displayAccount = account
                        uiState.selectedTab = 0
                        backStack.resetTo(Route.Main)
                    },
                    onSkip = {
                        // 允许不登录先看界面（走查用），登录态保持 false。
                        backStack.resetTo(Route.Main)
                    },
                    onAuthenticate = { account, password ->
                        // 真实登录。密码只作为参数穿过，绝不落盘/进日志。
                        // 返回 null = 成功；返回一句中文 = 给用户看的错。
                        try {
                            val result = app.casService.login(account, password)
                            // 登录成功顺手拉一次未读（此时才有 token）。
                            runCatching { app.unread.refresh(force = true) }
                            // ★ 学在城院的 identity token 对**必须在这一刻换**：
                            //   密码不持久化，登录页一过就再没有机会拿它换 token 了。
                            //   失败**不影响门户登录**（用 runCatching 兜住）——
                            //   学在城院打不开只是降级到它自己的登录页，不该把门户也拖崩。
                            runCatching {
                                val tokens = app.casService.loginForTronclassTokens(account, password)
                                app.tronclassSession.saveTokens(
                                    accessToken = tokens.accessToken,
                                    refreshToken = tokens.refreshToken,
                                )
                                // 顺手把 SSO cookie 收下来备用（token 失效时走免登链重新拿码）。
                                val sso = TronclassBoot.pickSsoCookies(app.client.cookieJar)
                                if (sso.isNotEmpty()) {
                                    app.tronclassSession.saveSsoCookies(TronclassBoot.ssoCookiesToJson(sso))
                                }
                                android.util.Log.i(
                                    "WanzaiLogin",
                                    "学在城院 token 对已换取（access 长度=${tokens.accessToken.length}，" +
                                        "${if (tokens.refreshToken == null) "无 refresh" else "有 refresh"}）",
                                )
                            }.onFailure { error ->
                                // 换 token 对失败**不该让门户登录看起来失败**，
                                // 但也不能静默 —— 否则用户点学在城院只会看到它自己的登录页，
                                // 而我们无从知道是「没换到」还是「换了没用」。
                                // 只记异常类型与脱敏 details（host/键名/状态码），不记凭据。
                                android.util.Log.w(
                                    "WanzaiLogin",
                                    "学在城院 token 换取失败：${error.javaClass.simpleName} " +
                                        (error as? HzcuException)?.details,
                                )
                            }
                            // ★ 只记长度与探针名，绝不记 token 本身。
                            android.util.Log.i(
                                "WanzaiLogin",
                                "登录成功：token 长度=${result.accessToken.length} " +
                                    "探针=${result.probeName} 跳数=${result.chain.size}",
                            )
                            null
                        } catch (error: HzcuException) {
                            // details 已脱敏（只有 host/键名/状态码），可以进日志帮排查。
                            android.util.Log.w(
                                "WanzaiLogin",
                                "登录失败：${error.javaClass.simpleName} ${error.details}",
                            )
                            error.message ?: error.shortErrorText()
                        } catch (error: Throwable) {
                            android.util.Log.w("WanzaiLogin", "登录异常：${error.javaClass.simpleName}")
                            error.shortErrorText()
                        }
                    },
                )
            }

            entry<Route.Main> {
                // ★ 新主殼：五個板塊 + 液態玻璃底欄。
                //
                // 舊的 `MainShell`（四 tab，Scaffold + NavigationBar）已被取代：
                // 業主要求「把已重構和未重構分開、按功能分類」，
                // 而舊版把原生頁與網頁入口混在一個宮格裡。
                com.muyao.wanzaihyperos.ui.main.WanzaiMainShell(
                    graph = app,
                    uiState = uiState,
                    onOpenApp = openApp,
                    onOpenSearch = { backStack.add(Route.Search) },
                    onOpenSection = { /* 板塊切換由主殼內部處理 */ },
                    onOpenJw = { backStack.add(Route.Jw) },
                    // 課表頁頁尾「學在城院 · 本週 / 全部」：進原生 TronClass。
                    onOpenTron = { backStack.add(Route.TronHome(tileUrl = "https://" + HzcuHosts.TRONCLASS_MOBILE)) },
                    onOpenTimetable = { uiState.selectedTab = 1 },
                    onOpenMessages = { uiState.selectedTab = 2 },
                    onOpenTodos = { backStack.add(Route.TodoBoard) },
                    onOpenAbout = { backStack.add(Route.About) },
                    onOpenBrand = { backStack.add(Route.Brand) },
                    onOpenCrop = { backStack.add(Route.WallpaperCrop) },
                    onOpenAccounts = { backStack.add(Route.AccountSources) },
                    // 返回栈深度：设置页账号聚合行按它重算（B4-06 第 1 处真缺陷）
                    navDepth = backStack.size,
                    // 首頁「簽到」磁貼：兩步到掃碼（业主 2026-10-19）。
                    onOpenRollcall = { backStack.add(Route.Rollcall()) },
                    // 應用頁的掃一掃。進來先清一次中轉槽：
                    // 上一次掃到一半退出的碼不該在這次冒出來。
                    onOpenScan = {
                        ScanHandoff.clear()
                        backStack.add(Route.Scanner)
                    },
                    onOpenSettings = { uiState.selectedTab = 4 },
                    // 登入頁出口。v3 #8 之後只剩一處走它：「賬號」組的門戶那一行
                    // （舊的「去重新登錄」按鈕已並進去）。學在城院那一行只用文字指回這裡、
                    // 本身不可點 —— 它沒有自己的登入口，跳過去也是門戶登入頁。
                    onOpenLogin = { backStack.add(Route.Login) },
                    // 设置页「帐号」那一组：智慧公寓只能他自己粘入口链接（App 取不到码）。
                    onOpenZhgy = { backStack.add(Route.Zhgy) },
                    onOpenSzty = { backStack.add(Route.Szty) },
                    // 消息裡點一條：門戶的 openurl 交給 WebView。
                    //
                    // ⚠️ 原樣交過去對 cjh52 是不行的（真機 2026-10-30 實測）：
                    //   它的 SPA 只認 URL 上的臨時碼，沒帶碼就 replace 到登錄頁、
                    //   一幀內容都不渲染 —— 使用者看到一片灰，還以為 App 壞了。
                    //   cjh52 的地址先換一枚一次性碼再打開（與「我的課程」網頁版同路）；
                    //   其餘域維持原樣：門戶自己也是丟給 WebView。
                    onOpenUrl = { raw ->
                        HzcuEndpoints.portalOpenUrl(raw)?.let { url ->
                            if (AppOpenStrategy.needsTempCode(url)) {
                                scope.launch {
                                    val tempCode = runCatching { app.api.issueTempCode() }.getOrNull()
                                    if (tempCode == null) {
                                        Toast.makeText(
                                            context,
                                            "没能取到一次性临时码，这条消息打不开，请稍后重试",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        backStack.add(
                                            Route.Web(
                                                0,
                                                "消息详情",
                                                AppOpenStrategy.buildTempCodeUrl(
                                                    appUrl = url,
                                                    appId = 0,
                                                    colorArgb = null,
                                                    tempCode = tempCode,
                                                    nowMillis = System.currentTimeMillis(),
                                                ),
                                            ),
                                        )
                                    }
                                }
                            } else {
                                backStack.add(Route.Web(0, "消息详情", url))
                            }
                        }
                    },
                    // 點開一條消息之後標已讀（docs/B7-WALKTHROUGH.md 第 2 節的順序）：
                    // ① 頁面已先把未讀點去掉、並發了 setmessageread；
                    // ② 這裡只負責「寫成功就重取未讀總數」，③ 寫失敗就說一句並重取。
                    //
                    // 為什麼重取而不是本地減一：服務端 getmessagenoreadcount 的口徑未知
                    // （只知它是標量），本地算術會長期越偏 —— 越用越不準比不更新更糟。
                    // 底欄「消息」那顆 tab 的未讀角標。未知（CountValue.Unknown）時傳 0
                    // —— 不畫 0 也不畫問號，與「此刻」卡對未知數的處置同一條口徑。
                    unreadCount = unreadCount ?: 0,
                    onReadMessage = { id ->
                        scope.launch {
                            val ok = runCatching { app.messages.markRead(id) }.getOrDefault(false)
                            app.unread.refresh(force = true)
                            if (!ok) {
                                Toast.makeText(context, "这条没标上已读，联网后会自动重标", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onReadAllMessage = { group ->
                        scope.launch {
                            val ok = runCatching { app.messages.markAllRead(group) }.getOrDefault(false)
                            app.unread.refresh(force = true)
                            Toast.makeText(
                                context,
                                if (ok) "这一组都标已读了" else "没标上，联网后再试一次",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }

            entry<Route.Search> {
                SearchPage(
                    graph = app,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenApp = openApp,
                )
            }

            // B4-06：账号二级页（设置首页只留一行聚合入口）。
            entry<Route.AccountSources> {
                com.muyao.wanzaihyperos.ui.main.pages.AccountsPage(
                    graph = app,
                    uiState = uiState,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenLogin = { backStack.add(Route.Login) },
                    onOpenJw = { backStack.add(Route.Jw) },
                    onOpenZhgy = { backStack.add(Route.Zhgy) },
                    onOpenSzty = { backStack.add(Route.Szty) },
                    onOpenApps = { uiState.selectedTab = 3; backStack.removeLastOrNull() },
                    // 回到这一页时要重新取六源快照（见 AccountsPage 的 refreshKey 注释）
                    refreshKey = backStack.size,
                )
            }

            // B4-10：关于 + 开源许可（外链与依赖清单都是真的，见 `AboutLinks` / `Credits`）。
            entry<Route.About> {
                com.muyao.wanzaihyperos.ui.about.AboutPage(
                    onBack = { backStack.removeLastOrNull() },
                    onSearch = { backStack.add(Route.Search) },
                    onOpenLicenses = { backStack.add(Route.Licenses) },
                    onOpenBrand = { backStack.add(Route.Brand) },
                )
            }

            // A9：开源许可与字体（三级页）。
            entry<Route.Licenses> {
                com.muyao.wanzaihyperos.ui.about.LicensesPage(
                    onBack = { backStack.removeLastOrNull() },
                    onSearch = { backStack.add(Route.Search) },
                )
            }

            // A6：品牌与图标（二级页）。
            entry<Route.Brand> {
                com.muyao.wanzaihyperos.ui.about.BrandPage(
                    onBack = { backStack.removeLastOrNull() },
                    onSearch = { backStack.add(Route.Search) },
                )
            }

            // A10：本地照片取景（三级页）。点应用才落盘，点取消原样退出。
            entry<Route.WallpaperCrop> {
                com.muyao.wanzaihyperos.ui.main.pages.WallpaperCropPage(
                    onBack = { backStack.removeLastOrNull() },
                    onSearch = { backStack.add(Route.Search) },
                )
            }

            // ★ 稿 cjh.png：CJH52 學生服務**目錄頁**。
            //
            // 六個原生子頁各有各的預熱閘門，所以這一頁**自己不需要閘門**：
            // 它不取數、只導航。真正的取數（與可能失敗的預熱）發生在子頁上，
            // 那也是使用者能看見「哪一頁沒進來」的地方 —— 目錄頁先失敗反而更難查。
            entry<Route.CjhHome> {
                CjhHomePage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenCourses = { backStack.add(Route.Courses) },
                    onOpenClazz = { backStack.add(Route.Cjh52Feature("CLAZZ")) },
                    onOpenNotices = { backStack.add(Route.Cjh52Feature("NOTICE")) },
                    onOpenExams = { backStack.add(Route.Cjh52Feature("EXAM")) },
                    onOpenGrowth = { backStack.add(Route.Cjh52Feature("GROWTH")) },
                    onOpenLectures = { backStack.add(Route.Lectures()) },
                )
            }
            // ★ B1：原生課表頁。
            //
            // 語義上的返回 = 回上一頁（可能是首頁，也可能是搜尋頁）。
            entry<Route.Courses> {
                // ★ 會話閘門：先確保 CJH52 會話可用，再放行頁面取數。
                //
                //   為什麼需要它：實測（見 Cjh52WarmUp 的類註釋）——
                //   門戶新發的 tempcode 直接當 Bearer 用會被拒（401），
                //   但**被真實瀏覽器載入過的同一枚碼**立刻可用。
                //   閘門會開一個 0 尺寸 WebView 把碼「捂熱」，只做一次。
                //
                //   已驗證：預熱一次之後，token 對多個 cjh52 接口都有效
                //   （get_user_by_token 與 get_class_info_by_uid 都 200），
                //   所以**所有 cjh52 原生頁共用同一個閘門**即可。
                Cjh52WarmUpGate(
                    session = app.cjh52Session,
                    issueUrl = {
                        runCatching { app.api.issueTempCode() }.getOrNull()?.let { code ->
                            AppOpenStrategy.buildTempCodeUrl(
                                appUrl = HzcuEndpoints.CJH52_BASE_URL + "/course/student",
                                appId = CJH52_COURSE_APP_ID,
                                colorArgb = null,
                                tempCode = code,
                                nowMillis = System.currentTimeMillis(),
                            )
                        }
                    },
                ) { ready ->
                    CoursesPage(
                        repository = app.courses,
                        onExit = { backStack.removeLastOrNull() },
                        ready = ready,
                        // 稿 `cjhCourses.png`：那顆「CJH52 查课表」通向門戶的查課表
                        // （ID 153），**不是**本頁的資料源 —— 兩條路各自獨立保留。
                        onOpenTimetable = { openApp(DemoCatalog.frequent.first { it.id == 153 }) },
                        onOpenDirectory = { backStack.add(Route.CjhHome) },
                        // 原生拿不到資料時的出口：換回 WebView（那條是已驗證的路）。
                        onFallbackToWeb = {
                            scope.launch {
                                val tempCode = runCatching { app.api.issueTempCode() }.getOrNull()
                                if (tempCode == null) {
                                    Toast.makeText(
                                        context,
                                        "没能取到一次性临时码，请稍后重试",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    val url = AppOpenStrategy.buildTempCodeUrl(
                                        appUrl = HzcuEndpoints.CJH52_BASE_URL + "/course/student",
                                        appId = CJH52_COURSE_APP_ID,
                                        colorArgb = null,
                                        tempCode = tempCode,
                                        nowMillis = System.currentTimeMillis(),
                                    )
                                    backStack.add(Route.Web(CJH52_COURSE_APP_ID, "我的课程", url))
                                }
                            }
                        },
                    )
                }
            }

            // ★ B3 剩餘三項：日常通知 / 我的考試 / 我的成長。
            //
            // 三頁共用**同一個**預熱閘門 —— 一次預熱服務所有 cjh52 功能
            // （實測同一枚 token 對多個接口都有效），所以這裡不必各開一次。
            entry<Route.Cjh52Feature> { route ->
                Cjh52WarmUpGate(
                    session = app.cjh52Session,
                    issueUrl = {
                        runCatching { app.api.issueTempCode() }.getOrNull()?.let { code ->
                            AppOpenStrategy.buildTempCodeUrl(
                                appUrl = HzcuEndpoints.CJH52_BASE_URL,
                                appId = 0,
                                colorArgb = null,
                                tempCode = code,
                                nowMillis = System.currentTimeMillis(),
                            )
                        }
                    },
                ) { ready ->
                    // 網頁版出口：各頁共用同一個「回退」動作，但**回退到各自的頁面**
                    //（之前統一退回 cjh52 根，使用者從「班級」退回網頁卻落在首頁，
                    // 還得自己再點一次 —— 回退要回到同一個功能才說得過去）。
                    val fallback: () -> Unit = {
                        scope.launch {
                            val tempCode = runCatching { app.api.issueTempCode() }.getOrNull()
                            if (tempCode == null) {
                                Toast.makeText(
                                    context,
                                    "没能取到一次性临时码，请稍后重试",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                val url = AppOpenStrategy.buildTempCodeUrl(
                                    appUrl = HzcuEndpoints.CJH52_BASE_URL + cjh52FeaturePath(route.feature),
                                    appId = 0,
                                    colorArgb = null,
                                    tempCode = tempCode,
                                    nowMillis = System.currentTimeMillis(),
                                )
                                backStack.add(Route.Web(0, route.feature, url))
                            }
                        }
                    }
                    // 目錄頁出口：稿上每頁都有那枚「六项服务目录」，
                    // 從子頁一鍵回到 CJH52 的六項服務清單。
                    val toDirectory: () -> Unit = { backStack.add(Route.CjhHome) }
                    when (route.feature) {
                        // B16：愛講座。**重用同一個預熱閘門** ——
                        // cjh52 的 token 必須先由 WebView 載入一次才生效，
                        // 這一支也一樣（探測時少了 Authorization 全回 code 401）。
                        "LECTURE" -> LecturePage(
                            repository = app.lectures,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenWeb = fallback,
                            modifier = Modifier,
                            onOpenDirectory = toDirectory,
                        )
                        "EXAM" -> ExamPage(
                            repository = app.exams,
                            onExit = { backStack.removeLastOrNull() },
                            ready = ready,
                            onFallbackToWeb = fallback,
                            onOpenDirectory = toDirectory,
                        )
                        "GROWTH" -> GrowthPage(
                            repository = app.growth,
                            onExit = { backStack.removeLastOrNull() },
                            ready = ready,
                            onFallbackToWeb = fallback,
                            onOpenDirectory = toDirectory,
                        )
                        "CLAZZ" -> ClazzPage(
                            repository = app.clazz,
                            onExit = { backStack.removeLastOrNull() },
                            ready = ready,
                            onFallbackToWeb = fallback,
                            onOpenDirectory = toDirectory,
                        )
                        // 認不出來的名字 → 回退到通知頁（而不是崩）。
                        // 導航狀態會被系統保存/恢復，用名字就是為了讓「枚舉改名」
                        // 只表現成「回到默認頁」。
                        else -> NoticePage(
                            repository = app.notices,
                            onExit = { backStack.removeLastOrNull() },
                            ready = ready,
                            onFallbackToWeb = fallback,
                            onOpenDirectory = toDirectory,
                        )
                    }
                }
            }

            // ★ B3：我的事務（待辦 / 我的申請 / 我的已辦）。
            //
            // `bucket` 是**清單名**（`"TODO"` 等）。名字認不出來時**回退到待辦**
            // 而不是崩 —— 導航狀態會被系統保存/恢復，用名字就是為了讓
            // 「枚舉改名」這種事只表現成「回到默認 tab」。
            entry<Route.Busyness> { route ->
                val initial = com.muyao.wanzaihyperos.data.portal.PortalBillBucket.entries
                    .firstOrNull { it.name == route.bucket }
                    ?: com.muyao.wanzaihyperos.data.portal.PortalBillBucket.TODO
                BusynessPage(
                    repository = app.bills,
                    onExit = { backStack.removeLastOrNull() },
                    initialBucket = initial,
                )
            }

            // ★ B6：多來源待辦看板。
            //
            // 它**不需要 CJH52 預熱閘門**：目前接通的來源只有門戶事務
            // （A3 的會話就夠）。等學在城院那一路接上時再評估要不要包閘門。
            entry<Route.TodoBoard> {
                TodoBoardPage(
                    repository = app.todoBoard,
                    onExit = { backStack.removeLastOrNull() },
                    // ★ 作業那一類待辦點下去 → 詳情 + 提交
                    //（业主 2026-10-19：「我要在待办里实现查看作业详情并且提交作业」）。
                    // `key` 是 `/api/todos` 的 id（**不保證等於作業 id**），
                    // 所以詳情倉庫還會拿課名+標題再核對一次；核對不上就**不提交**。
                    onOpenHomework = { item ->
                        backStack.add(
                            Route.HomeworkDetail(
                                courseId = item.courseId,
                                homeworkId = item.key.toIntOrNull() ?: 0,
                                courseName = item.subtitle,
                                title = item.title,
                            ),
                        )
                    },
                    // ★ 門戶「我的事務」（`Route.Busyness`）以前在整個工程裡**零入口**：
                    //   頁面寫好了、路由註冊了，卻沒有任何一處 `backStack.add` —— 用戶永遠到不了。
                    //   待辦看板裡那一組「門戶 · 審批」就是以它為數據源的，所以入口掛在這裡。
                    onOpenBills = { backStack.add(Route.Busyness()) },
                )
            }

            // ★ 學在城院（**一個外殼，三個板塊**）。
            //
            // 先前是三個獨立路由（我的課／簽到／作業），各自有返回箭頭 ——
            // 在「學在城院」內部切換時看起來像反覆離開再進入這個應用。
            // 現在由 TronShellPage 統一提供頂欄與標籤，
            // 簽到／作業降為**板塊內部的二級頁**。
            entry<Route.TronHome> { route ->
                TronShellPage(
                    homeRepository = app.tronHome,
                    feedRepository = app.tronFeed,
                    meRepository = app.tronMe,
                    homeworkRepository = app.homework,
                    homeworkDetailRepository = app.homeworkDetail,
                    rollcallRepository = app.rollcall,
                    courseDetailRepository = app.courseDetail,
                    scheduleRepository = app.schedule,
                    onExit = { backStack.removeLastOrNull() },
                    onOpenWeb = { openTronWeb(route.tileUrl) },
                )
            }

            // ★ 首頁那顆「簽到」磁貼的第二條入口（业主 2026-10-19）。
            //
            // 為什麼讓它**獨立成一路由**而不是「先進學在城院外殼再點進去」：
            // 上課時老师的码就在屏幕上，他要的是「点开签到 → 扫码」两步，
            // 不是「点开应用 → 找标签 → 找签到」四步。
            // 内容仍是同一份 `RollcallBody`（不会两处行为不一致）。
            entry<Route.Rollcall> { route ->
                com.muyao.wanzaihyperos.ui.tronclass.RollcallStationPage(
                    repository = app.rollcall,
                    onExit = { backStack.removeLastOrNull() },
                    onOpenWeb = { openTronWeb(route.tileUrl) },
                )
            }

            // 全局扫一掃：應用頁那顆方形鈕進來。
            //
            // 掃到的原文不進路由參數（憑據永不進導航狀態，見 nav/Routes.kt）：
            // 簽到碼走 ScanHandoff 一次性中轉，簽到頁 take() 取走即清空。
            entry<Route.Scanner> {
                com.muyao.wanzaihyperos.ui.tronclass.ScannerPage(
                    onExit = { backStack.removeLastOrNull() },
                    onWeb = { url ->
                        backStack.removeLastOrNull()
                        openUrlWithTempCode(url, "扫到的网页")
                    },
                    onRollcall = { raw ->
                        ScanHandoff.put(raw)
                        backStack.removeLastOrNull()
                        backStack.add(Route.Rollcall())
                    },
                )
            }

            // 某門課的作業清單（獨立入口；外殼裡那個二級頁用的是同一份 HomeworkContent）。
            entry<Route.Homework> { route ->
                LaunchedEffect(route.courseId) {
                    // 取數交給 `graph.fetch`（應用級作用域）：組合被拆掉時不該把
                    // 在飞的请求一起带走（T54/T56 那条根因）。
                    if (route.courseId > 0) {
                        app.fetch { app.homework.load(route.courseId, route.courseName) }
                    }
                }
                com.muyao.wanzaihyperos.ui.tronclass.HomeworkListPage(
                    repository = app.homework,
                    courseName = route.courseName,
                    onExit = { backStack.removeLastOrNull() },
                    onOpenWeb = { openTronWeb(route.tileUrl) },
                    onOpenHomework = { courseId, homeworkId, courseName, title ->
                        backStack.add(Route.HomeworkDetail(courseId, homeworkId, courseName, title))
                    },
                )
            }

            // ★ 一項作業的詳情 + 提交（业主 2026-10-19：「实现查看作业详情并且提交作业」）。
            entry<Route.HomeworkDetail> { route ->
                LaunchedEffect(route.homeworkId) {
                    if (route.courseId > 0) {
                        app.fetch {
                            app.homeworkDetail.load(
                                route.courseId, route.homeworkId, route.courseName, route.title,
                            )
                        }
                    }
                }
                com.muyao.wanzaihyperos.ui.tronclass.HomeworkDetailPage(
                    repository = app.homeworkDetail,
                    courseId = route.courseId,
                    homeworkId = route.homeworkId,
                    courseName = route.courseName,
                    title = route.title,
                    onExit = { backStack.removeLastOrNull() },
                    onOpenWeb = { openTronWeb(route.tileUrl) },
                )
            }

            entry<Route.Lectures> { route ->
                // 愛講座（B16）：自帶頂欄與清單，不需要預熱閘門
                //（它用的是已激活的 cjh52 會話，由前面的 NativeFeature 那條激活）。
                com.muyao.wanzaihyperos.ui.cjh52.LecturePage(
                    repository = app.lectures,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenWeb = { backStack.add(Route.Web(54, "爱讲座", route.tileUrl)) },
                )
            }

            // B17：智慧公寓 —— 沒憑據時問使用者要連結，有就直接開。
            entry<Route.Zhgy> {
                com.muyao.wanzaihyperos.ui.web.ZhgyGatePage(
                    session = app.zhgySession,
                    onExit = { backStack.removeLastOrNull() },
                )
            }

            // B18：教務系統 —— 考試信息 / 學生成績（只讀）。
            entry<Route.Jw> {
                com.muyao.wanzaihyperos.ui.jw.JwPage(
                    repository = app.jw,
                    engine = app.jwEngine,
                    session = app.jwSession,
                    onExit = { backStack.removeLastOrNull() },
                )
            }

            // B19：智雲課堂 —— 本週課表 + 回放入口。
            entry<Route.Zy> {
                com.muyao.wanzaihyperos.ui.zy.ZyPage(
                    repository = app.zy,
                    engine = app.zyEngine,
                    onOpenReplay = { item ->
                        backStack.add(
                            Route.ZyReplay(
                                url = com.muyao.wanzaihyperos.data.zy.ZyReplayUrls.detail(
                                    courseId = item.courseId,
                                    subId = item.id,
                                ),
                                title = item.title,
                            ),
                        )
                    },
                    onExit = { backStack.removeLastOrNull() },
                )
            }

            // B19：智雲課堂回放播放（用它的頁面 —— 見 ZyReplayPage 的註釋）。
            entry<Route.ZyReplay> { route ->
                com.muyao.wanzaihyperos.ui.zy.ZyReplayPage(
                    url = route.url,
                    title = route.title,
                    onExit = { backStack.removeLastOrNull() },
                    // 稿 `zyReplay.png`：未匹配時那顆「选择智云课程」=
                    // 退回上一頁（也就是課程清單），別把人困在死路。
                    onPickCourse = { backStack.removeLastOrNull() },
                )
            }

            // B20：數智體育（體測 / 校園跑 / 體育課簽到，只讀）。
            // token 與數字 userId 在加密會話裡，不進導航狀態。
            entry<Route.Szty> {
                com.muyao.wanzaihyperos.ui.szty.SztyPage(
                    repository = app.szty,
                    session = app.sztySession,
                    onExit = { backStack.removeLastOrNull() },
                )
            }
            entry<Route.Web> { route ->
                WebShellPage(
                    title = route.title,
                    url = route.url,
                    onExit = { backStack.removeLastOrNull() },
                    // ⚠️ 這裡曾經傳入 `cookieJar` 讓 WebView 注入 CAS 會話（B4），
                    //   B5 真機對照實驗證明它把圖書館弄壞了（注入後打開就是廠商
                    //   端點 500，且清掉注入 cookie 仍壞 —— WebView 自己累積的
                    //   `.ca.hzcu.edu.cn` 會話刪不掉）。已撤銷，理由寫在
                    //   WebShellPage 的對應註釋與 docs/B5-WALKTHROUGH.md。
                )
            }
            }
        }
    }
}
