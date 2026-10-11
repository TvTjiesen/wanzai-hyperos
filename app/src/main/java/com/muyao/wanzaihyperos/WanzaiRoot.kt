package com.muyao.wanzaihyperos

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
import com.muyao.wanzaihyperos.net.TronclassBoot
import com.muyao.wanzaihyperos.net.shortErrorText
import com.muyao.wanzaihyperos.session.SessionState
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.MainShell
import com.muyao.wanzaihyperos.ui.WanzaiTheme
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

                // ★ A4：学在城院**手机版免登**。
                //
                // token 对必须在**登录那一刻**换好（密码不持久化，之后没机会再换），
                // 这里只是把它读出来拼 URL。读不到就老实进它自己的登录页 ——
                // 绝不拿一个空/旧 token 去撞，那只会落到 SPA 的错误分支。
                is AppOpenPlan.TronclassFreeLogin -> {
                    val access = app.tronclassSession.accessToken()
                    val refresh = app.tronclassSession.refreshToken()
                    val url = if (access.isNullOrEmpty()) plan.tileUrl
                    else TronclassBoot.identityTokenUrl(access, refresh)
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

            entry<Route.Web> { route ->
                WebShellPage(
                    title = route.title,
                    url = route.url,
                    onExit = { backStack.removeLastOrNull() },
                )
            }
        }
    }
}
