package com.muyao.wanzaihyperos

import com.muyao.wanzaihyperos.ui.cjh52.GrowthPage
import com.muyao.wanzaihyperos.ui.cjh52.ExamPage
import com.muyao.wanzaihyperos.ui.cjh52.NoticePage
import com.muyao.wanzaihyperos.ui.cjh52.ClazzPage
import com.muyao.wanzaihyperos.ui.cjh52.LecturePage
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.nav.Route
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.TronclassBoot
import com.muyao.wanzaihyperos.net.shortErrorText
import com.muyao.wanzaihyperos.session.SessionState
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.MainShell
import com.muyao.wanzaihyperos.ui.WanzaiTheme
import com.muyao.wanzaihyperos.ui.busyness.BusynessPage
import com.muyao.wanzaihyperos.ui.courses.CoursesPage
import com.muyao.wanzaihyperos.ui.login.LoginPage
import com.muyao.wanzaihyperos.ui.search.SearchPage
import com.muyao.wanzaihyperos.ui.web.WebShellPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
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
fun WanzaiApp() {
    val uiState = remember { AppUiState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    "「${target.name}」仅校园网内可打开。注意：一般超时不一定都是校园网的问题。",
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

                // B17：智慧公寓 → 閘門頁（沒憑據就請使用者貼連結）。
                is AppOpenPlan.ZhgyGate -> backStack.add(Route.Zhgy)

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

        NavDisplay(
            backStack = backStack,
            transition = NavTransitions.MiuixDefault,
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
                MainShell(
                    uiState = uiState,
                    graph = app,
                    unreadCount = unreadCount,
                    onOpenApp = openApp,
                    onOpenSearch = { backStack.add(Route.Search) },
                    onOpenMessages = { uiState.selectedTab = 2 },
                    // ★ B3：首頁「待辦 / 申請 / 已辦」三格 → 我的事務對應清單。
                    onOpenBusyness = { bucketName ->
                        backStack.add(Route.Busyness(bucket = bucketName))
                    },
                    // ★ B6：首頁「全部待办」→ 多來源待辦看板。
                    onOpenTodoBoard = { backStack.add(Route.TodoBoard) },
                    onRelogin = {
                        uiState.loggedIn = false
                        uiState.displayAccount = ""
                        backStack.resetTo(Route.Login)
                    },
                    onLogout = {
                        // 登出要清凭据与 cookie 罐；在后台做，别卡界面。
                        // 用 composition 自己的 scope，别 new 一个游离的 —— 页面销毁后
                        // 游离 scope 仍会跑完，写回已失效的状态。
                        scope.launch { runCatching { app.logout() } }
                        uiState.loggedIn = false
                        uiState.displayAccount = ""
                        uiState.selectedTab = 0
                        backStack.resetTo(Route.Login)
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
                                    backStack.add(Route.Web(CJH52_COURSE_APP_ID, "我的課程", url))
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
                    when (route.feature) {
                        // B16：愛講座。**重用同一個預熱閘門** ——
                        // cjh52 的 token 必須先由 WebView 載入一次才生效，
                        // 這一支也一樣（探測時少了 Authorization 全回 code 401）。
                        "LECTURE" -> LecturePage(
                            repository = app.lectures,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenWeb = fallback,
                            modifier = Modifier,
                        )
                        "EXAM" -> ExamPage(
                            repository = app.exams,
                            onExit = { backStack.removeLastOrNull() },
                            ready = ready,
                            onFallbackToWeb = fallback,
                        )
                        "GROWTH" -> GrowthPage(
                            repository = app.growth,
                            onExit = { backStack.removeLastOrNull() },
                            ready = ready,
                            onFallbackToWeb = fallback,
                        )
                        "CLAZZ" -> ClazzPage(
                            repository = app.clazz,
                            onExit = { backStack.removeLastOrNull() },
                            ready = ready,
                            onFallbackToWeb = fallback,
                        )
                        // 認不出來的名字 → 回退到通知頁（而不是崩）。
                        // 導航狀態會被系統保存/恢復，用名字就是為了讓「枚舉改名」
                        // 只表現成「回到默認頁」。
                        else -> NoticePage(
                            repository = app.notices,
                            onExit = { backStack.removeLastOrNull() },
                            ready = ready,
                            onFallbackToWeb = fallback,
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
                    rollcallRepository = app.rollcall,
                    courseDetailRepository = app.courseDetail,
                    scheduleRepository = app.schedule,
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
