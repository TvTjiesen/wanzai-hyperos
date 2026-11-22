package com.muyao.wanzaihyperos.app

import com.muyao.wanzaihyperos.data.cjh52.currentUser
import com.muyao.wanzaihyperos.data.cjh52.GrowthRepository
import kotlinx.coroutines.launch
import com.muyao.wanzaihyperos.data.cjh52.LiveGrowthRepository
import com.muyao.wanzaihyperos.data.cjh52.ClazzRepository
import com.muyao.wanzaihyperos.data.cjh52.LiveClazzRepository
import com.muyao.wanzaihyperos.data.cjh52.LiveExamRepository
import com.muyao.wanzaihyperos.data.cjh52.ExamRepository
import com.muyao.wanzaihyperos.data.cjh52.LiveNoticeRepository
import com.muyao.wanzaihyperos.data.cjh52.NoticeRepository
import android.content.Context
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.net.DiagnosticPolicy
import com.muyao.wanzaihyperos.net.PortalCredentials
import com.muyao.wanzaihyperos.session.SessionTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import com.muyao.wanzaihyperos.data.cjh52.CourseRepository
import com.muyao.wanzaihyperos.data.cjh52.LiveCourseRepository
import com.muyao.wanzaihyperos.data.portal.LivePortalCatalogRepository
import com.muyao.wanzaihyperos.data.portal.LivePortalBillRepository
import com.muyao.wanzaihyperos.data.portal.PortalBillRepository
import com.muyao.wanzaihyperos.data.portal.LivePortalFrequentAppsRepository
import com.muyao.wanzaihyperos.data.portal.LivePortalMessageRepository
import com.muyao.wanzaihyperos.data.portal.LivePortalNewsRepository
import com.muyao.wanzaihyperos.data.portal.LivePortalSearchRepository
import com.muyao.wanzaihyperos.data.portal.LivePortalTodoRepository
import com.muyao.wanzaihyperos.data.portal.LivePortalUnreadRepository
import com.muyao.wanzaihyperos.data.portal.PortalCatalogRepository
import com.muyao.wanzaihyperos.data.portal.PortalFrequentAppsRepository
import com.muyao.wanzaihyperos.data.portal.PortalMessageRepository
import com.muyao.wanzaihyperos.data.portal.PortalNewsRepository
import com.muyao.wanzaihyperos.data.portal.PortalSearchRepository
import com.muyao.wanzaihyperos.data.portal.PortalTodoRepository
import com.muyao.wanzaihyperos.data.portal.PortalUnreadRepository
import com.muyao.wanzaihyperos.data.todo.LiveTodoRepository
import com.muyao.wanzaihyperos.data.todo.TodoRepository
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.TronclassClient
import com.muyao.wanzaihyperos.net.HzcuCasService
import com.muyao.wanzaihyperos.net.HzcuClient
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.session.Cjh52Session
import com.muyao.wanzaihyperos.session.EncryptedSecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import com.muyao.wanzaihyperos.session.SessionState
import com.muyao.wanzaihyperos.session.TronclassSession
import com.muyao.wanzaihyperos.ui.web.WebViewCleanup

/**
 * 组合根：**全 App 唯一**组装网络层与会话层的地方。
 *
 * 为什么要有它：A2 把每一层的依赖都做成了构造注入（为了可测），
 * 那么「谁把谁 new 出来」就必须有一个明确的答案，否则依赖会散落在各页面里，
 * 变成「同一个会话被两个地方各建一份」这类最难查的 bug。
 *
 * ### 三条纪律
 *
 * 1. **[get] 必须在后台线程首次调用** —— 首次会生成 Android Keystore 主密钥，
 *    低端机上可能上百毫秒。在首帧里同步调会卡启动。
 * 2. **启动时必须 [HzcuClient.restoreCookieJar]** —— 门户的 `ACCESS_TOKEN` 与
 *    CAS 会话 Cookie **绑定**：只恢复 token 不恢复 cookie，重啟后每个业务接口都 401
 *    （这是真机抓包定位过的 bug）。所以「装 cookie 罐」和「读 token」必须一起做。
 * 3. **[HzcuClient] 只能有一个** —— 它持有 Cookie 罐，两份客户端就会有两份罐，
 *    Set-Cookie 落进哪一份取决于哪份在发请求，症状是「有时能登有时不能」。
 */
class AppGraph private constructor(
    /**
     * 只用于「清 WebView 残留」这类需要 Context 的系统调用。
     * **永远是 applicationContext**（见 [get]），不会泄漏 Activity。
     */
    private val appContext: Context,
    val client: HzcuClient,
    val portalSession: PortalSession,
    val tronclassSession: TronclassSession,
    val cjh52Session: Cjh52Session,
    val casService: HzcuCasService,
    val api: PortalApi,
    val cjh52: Cjh52Client,
    internal val cjh52Recovery: com.muyao.wanzaihyperos.ui.web.Cjh52Recovery,
    /** 學在城院原生接口層（B6 待辦的第二個來源）。 */
    val tronclass: TronclassClient,
    /**
     * 只讀接口的本地緩存（設定頁要拿它顯示「佔了多少 + 一鍵清除」）。
     *
     * ★ 為什麼要把這個物件暴露出來：裡面存的是他本人的課表/賬單/成績。
     *   悄悄往他手機裡寫一堆個人數據、又不給看得見的大小和清除入口，
     *   這件事本身就不該做（业主 2026-10-20 同意加緩存，但這一條是我自己加的條件）。
     */
    val httpCache: com.muyao.wanzaihyperos.net.ResponseCache,
    /** 學在城院原生首頁（我的課 + 待交作業）。 */
    val tronHome: com.muyao.wanzaihyperos.data.tronclass.TronHomeRepository,
    /** 學在城院簽到（看 + 簽）。 */
    val rollcall: com.muyao.wanzaihyperos.data.tronclass.RollcallRepository,
    /** 學在城院某門課的作業（只看清單；點一行进 [homeworkDetail]）。 */
    val homework: com.muyao.wanzaihyperos.data.tronclass.HomeworkRepository,
    /**
     * 學在城院**一項作業的詳情 + 提交**（2026-10-19）。
     *
     * 单独一个仓库而不是塞进 [homework]：提交是写操作，
     * 它的闸门（不知道要求就不发）必须绑在**这一项**的状态上，
     * 而不是「上一次看的是哪门课」的全局清单状态上。
     */
    val homeworkDetail: com.muyao.wanzaihyperos.data.tronclass.HomeworkDetailRepository,
    /** 學在城院「課程動態」（公告 + 待辦 + 我的課的合成視圖）。 */
    val tronFeed: com.muyao.wanzaihyperos.data.tronclass.TronFeedRepository,
    /** 學在城院「我的」（個人資料 + 概覽）。 */
    val tronMe: com.muyao.wanzaihyperos.data.tronclass.TronMeRepository,
    /** 學在城院某門課的**詳情**（信息 / 成績 / 出勤 / 大綱 / 作業）。 */
    val courseDetail: com.muyao.wanzaihyperos.data.tronclass.CourseDetailRepository,
    val tronMembers: com.muyao.wanzaihyperos.data.tronclass.TronMembersRepository,
    val tronResources: com.muyao.wanzaihyperos.data.tronclass.TronResourcesRepository,
    /** 學在城院**課表**（把各門課的上課時間字串攤成一週的格子）。 */
    val schedule: com.muyao.wanzaihyperos.data.tronclass.ScheduleRepository,
    val catalog: PortalCatalogRepository,
    val frequent: PortalFrequentAppsRepository,
    val unread: PortalUnreadRepository,
    val todo: PortalTodoRepository,
    val news: PortalNewsRepository,
    val messages: PortalMessageRepository,
    val broadcasts: com.muyao.wanzaihyperos.data.portal.PortalBroadcastRepository,
    val search: PortalSearchRepository,
    val courses: CourseRepository,
    val bills: PortalBillRepository,
    val notices: NoticeRepository,
    val exams: ExamRepository,
    val growth: GrowthRepository,
    val clazz: ClazzRepository,
    /** B6：多來源待辦看板（聚合門戶審批 + 學在城院作業）。 */
    val todoBoard: TodoRepository,
    /** B16：CJH52 愛講座（原生清單，只讀）。 */
    val lectures: com.muyao.wanzaihyperos.data.cjh52.LectureRepository,
    /** B17：智慧公寓的會話（一段由使用者提供的 `code`，只進加密存儲）。 */
    val zhgySession: com.muyao.wanzaihyperos.session.ZhgySession,
    /** B18：教務系統會話（**明文密碼**，全專案唯一一個 —— 見該類的註釋）。 */
    val jwSession: com.muyao.wanzaihyperos.session.JwSession,
    /** B18：教務系統引擎（用不可見 WebView 維持 session 與取數）。 */
    val jwEngine: com.muyao.wanzaihyperos.data.jw.JwEngine,
    /** B18：教務系統倉庫（兩段登入 + 取資料）。 */
    val jw: com.muyao.wanzaihyperos.data.jw.JwRepository,
    /**
     * 教務系統的**課表**（排課的权威来源）。
     *
     * 單獨一個倉庫而不是塞進 [jw]：`jw` 的狀態是「一個功能一份原始回應」，
     * 而課表要**兩條取數路徑回退**（JSON → 讀渲染好的表格）並產出可直接渲染的
     * 槽位清單。混進去會讓那個 when 分支長出第二套流程。
     */
    val jwTimetable: com.muyao.wanzaihyperos.data.jw.JwTimetableRepository,
    /** B19：智雲課堂引擎（沿用既有統一身份認證會話，用不可見 WebView 取數）。 */
    val zyEngine: com.muyao.wanzaihyperos.data.zy.ZyEngine,
    /** B19：智雲課堂倉庫（週課表 + 回放）。 */
    val zy: com.muyao.wanzaihyperos.data.zy.ZyRepository,
    /** B20：数智体育的会话（用户粘贴的 token + 服务端认人用的数字 userId）。 */
    val sztySession: com.muyao.wanzaihyperos.session.SztySession,
    /** B20：数智体育仓库（体测 / 校园跑 / 体育课签到，全只读）。 */
    val szty: com.muyao.wanzaihyperos.data.szty.SztyRepository,
    val accountEpoch: AccountEpoch,
    val diagnostics: DiagnosticPolicy,
    private val sessions: SessionTransaction,
) {

    /**
     * 网络取数的**生命周期归属**：应用级，不是页面级。
     *
     * ★ 本轮真机根因（消息／新聞「取不到」＋待办「正在整理…」永远转圈）：
     *   页面以前在自己的 `LaunchedEffect(Unit)` 里直接 `runCatching { repo.refresh() }`，
     *   于是请求**寄生在页面的组合生命周期上** —— 切个 tab、左右滑一下，组合离开，
     *   协程被取消，OkHttp/Compose 抛 `LeftCompositionCancellationException`；
     *   仓库的 `runCatching` 把它当普通失败，写进**仓库级**的 StateFlow，
     *   假错误就一直挂在那，下次进同一页还是「取不到」。
     *   另一半症状同理：唯一在飞的那次请求被取消后**没人补发**，状态停在 `FirstLoad`，
     *   界面就永远「正在取得…」（connect 15s / read 20s 的超时也救不了，因为根本没在等）。
     *
     * 取数是**应用的事实**，不该因为某一帧没人看就被掐掉。页面只负责订阅结果。
     *
     * ⚠️ `SupervisorJob`：一个取数失败/取消不能连带停掉其它取数。
     */
    val ioScope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )

    /**
     * 把一次取数挂到 [ioScope] 上：页面只负责订阅结果，**不 owns 请求的生命**。
     *
     * 做成成员函数而不是顶层扩展，是为了让五个板块页一行 import 都不用加 ——
     * 漏 import 只会编译失败，但「漏了包一层、又挂回组合生命周期」是**能编译过的回归**，
     * 那种才难查。
     */
    private val busy = BusyTracker()
    val inFlight = busy.state
    private val boundaryMutex = Mutex()
    private val _accountRevision = MutableStateFlow(0L)
    val accountRevision: kotlinx.coroutines.flow.StateFlow<Long> = _accountRevision
    @Volatile private var boundaryWrite = false
    @Volatile private var primaryAllowed = portalSession.state == SessionState.LoggedIn

    init {
        accountEpoch.registerReset { busy.reset() }
        portalSession.addListener {
            if (!boundaryWrite && portalSession.state != SessionState.LoggedIn) {
                primaryAllowed = false
                accountEpoch.suspendOwner()
                _accountRevision.value = _accountRevision.value + 1
            }
        }
        listOf(tronclassSession, cjh52Session, zhgySession, jwSession, sztySession).forEach { session ->
            watchSecondary(session) { "${session.state}:${session.replacementRevision}" }
        }
    }

    private fun watchSecondary(session: com.muyao.wanzaihyperos.session.StoredSession, signature: () -> String) {
        val observer = SessionChangeObserver(httpCache, signature) {
            if (!boundaryWrite) {
                // 同一主账号内换次级凭据（取码、SID、教务账密）：缓存要清，
                // 但**不能**推进 generation —— 否则每点一次应用都会取消全部在途请求、
                // 把所有仓库打回 FirstLoad（真机表现为首页与待办整页重载）。
                httpCache.clearAll()
                client.restoreCookieJar()
            }
        }
        session.addListener(observer::observe)
    }

    fun fetch(block: suspend () -> Unit): Job {
        val token = accountEpoch.token()
        return ioScope.launch {
            try { runTracked(token, block) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { diagnostics.record("general", "fetch", error.javaClass.simpleName) }
        }
    }

    suspend fun runTracked(token: Long = accountEpoch.token(), block: suspend () -> Unit) {
        val job = currentCoroutineContext()[Job] ?: error("Request has no Job")
        if (!accountEpoch.track(token, job)) throw CancellationException("Account changed")
        var ticket: Long? = null
        try {
            if (!accountEpoch.publishData(token) { ticket = busy.begin() })
                throw CancellationException("Account changed")
            block()
        } finally {
            ticket?.let(busy::end)
            accountEpoch.untrack(job)
        }
    }

    fun startRoute(): StartRoute =
        if (primaryAllowed && portalSession.state == SessionState.LoggedIn) StartRoute.Main else StartRoute.Login

    fun displayAccount(): String = portalSession.userCode().orEmpty()

    private suspend fun beforeLogin(account: String): Long = boundaryMutex.withLock {
        synchronized(httpCache) {
            primaryAllowed = false
            accountEpoch.suspendOwner()
            httpCache.clearAll()
            _accountRevision.value = _accountRevision.value + 1
        }
        WebViewCleanup.clearAll(appContext)
        synchronized(httpCache) {
            accountEpoch.activate(account.trim(), allowData = false)
            accountEpoch.token()
        }
    }

    private fun confirmLogin(account: String, token: Long?) {
        if (token == null || !accountEpoch.publish(token) {
            check(portalSession.userCode() == account) { "Account confirmation mismatch" }
            primaryAllowed = true
            check(accountEpoch.allowData(token))
            _accountRevision.value = _accountRevision.value + 1
        }) throw CancellationException("Account changed")
    }

    suspend fun logout() = boundaryMutex.withLock {
        synchronized(httpCache) {
            primaryAllowed = false
            accountEpoch.suspendOwner()
            httpCache.clearAll()
            _accountRevision.value = _accountRevision.value + 1
        }
        cleanupOnExit(
            action = {
                withContext(Dispatchers.IO) {
                    boundaryWrite = true
                    try { sessions.clearAll() } finally { boundaryWrite = false }
                }
            },
            {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    com.muyao.wanzaihyperos.data.reminder.ReminderScheduler.clear(appContext)
                }
            },
            { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { diagnostics.clear() } },
            { WebViewCleanup.clearAll(appContext) },
        )
    }

    enum class StartRoute { Login, Main }

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        /**
         * 取全 App 唯一的组合根。**首次调用必须在后台线程**（见类注释）。
         *
         * 用 `applicationContext`：Activity 会被重建，抓着 Activity 的 Context
         * 建长期对象是典型的内存泄漏。
         */
        fun get(context: Context): AppGraph {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(appContext: Context): AppGraph {
            val store = EncryptedSecureStore.open(appContext)
            val httpCache = com.muyao.wanzaihyperos.net.ResponseCache(appContext.filesDir)
            val epoch = AccountEpoch(httpCache)
            val diagnostics = DiagnosticPolicy(java.io.File(appContext.filesDir, "diagnostics"),
                (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            DiagnosticPolicy.clearLegacyRawFiles(appContext.filesDir)
            diagnostics.prune()
            val portalSession = PortalSession(store).apply { ensureLoaded() }
            val tronSession = TronclassSession(store).apply { ensureLoaded() }
            val cjh52Session = Cjh52Session(store).apply { ensureLoaded() }
            val zhgySessionInstance = com.muyao.wanzaihyperos.session.ZhgySession(store).apply { ensureLoaded() }
            val jwSessionInstance = com.muyao.wanzaihyperos.session.JwSession(store).apply { ensureLoaded() }
            val sztySessionInstance = com.muyao.wanzaihyperos.session.SztySession(store).apply { ensureLoaded() }
            epoch.activate(portalSession.userCode(), allowData = portalSession.state == SessionState.LoggedIn)
            val transaction = SessionTransaction(store, portalSession, tronSession, cjh52Session,
                zhgySessionInstance, jwSessionInstance, sztySessionInstance)
            val credentials = object : PortalCredentials by portalSession {
                var ownerGraph: AppGraph? = null
                override fun savePortalSession(accessToken: String, userCode: String?, portalCookiesJson: String) {
                    val graph = ownerGraph
                    synchronized(httpCache) {
                        if (graph != null) graph.boundaryWrite = true
                        try { transaction.savePortal(accessToken, userCode, portalCookiesJson) }
                        finally { if (graph != null) graph.boundaryWrite = false }
                    }
                }
            }
            val client = HzcuClient.create(credentials = credentials, epoch = epoch)
            client.restoreCookieJar()
            val api = PortalApi(client)
            fun cjh52Write(action: () -> Unit) {
                synchronized(httpCache) {
                    val graph = credentials.ownerGraph
                    val previous = graph?.boundaryWrite
                    if (graph != null) graph.boundaryWrite = true
                    try { action() }
                    finally { if (graph != null) graph.boundaryWrite = previous ?: false }
                }
            }
            val cjh52Recovery = com.muyao.wanzaihyperos.ui.web.Cjh52Recovery(
                epoch = epoch,
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO),
                readCredential = {
                    synchronized(store) {
                        if (cjh52Session.isUsable) com.muyao.wanzaihyperos.ui.web.Cjh52Credential(
                            cjh52Session.token()!!, cjh52Session.credentialRevision(),
                        ) else null
                    }
                },
                clearCredential = { cjh52Write { cjh52Session.clear() } },
                saveCredential = { code ->
                    cjh52Write {
                        cjh52Session.save(code, System.currentTimeMillis())
                        cjh52Session.markActivated()
                    }
                },
                acquire = { owner ->
                    val code = api.issueTempCode()
                    val lease = com.muyao.wanzaihyperos.ui.web.WebDisplayLease(epoch)
                    if (code == null || lease.generation != owner || !lease.isCurrent()) null
                    else {
                        val address = com.muyao.wanzaihyperos.data.AppOpenStrategy.buildTempCodeUrl(
                            appUrl = com.muyao.wanzaihyperos.net.HzcuEndpoints.CJH52_BASE_URL + "/course/student",
                            appId = 60, colorArgb = null, tempCode = code, nowMillis = System.currentTimeMillis(),
                        )
                        if (!com.muyao.wanzaihyperos.ui.web.activateCjh52InWebView(appContext, address, code, lease)) null
                        else {
                            // Validate with the existing read endpoint, without recursive recovery or caching a new credential.
                            val probe = Cjh52Client(client, { code }, epoch = epoch)
                            probe.currentUser()
                            if (lease.isCurrent()) code else null
                        }
                    }
                },
            )
            val cjh52 = Cjh52Client(transport = client,
                tokenProvider = { cjh52Session.token().takeIf { cjh52Session.isUsable } }, epoch = epoch,
            ).apply { recovery = cjh52Recovery }
            val casService = HzcuCasService.create(client = client, session = credentials, epoch = epoch)
            client.renewSessionForOwner = { token -> casService.renewWithExistingSession(ownerToken = token) != null }
            // 验收探针：`adb shell settings put global wz_expire_probe 1` 后**第一次**门户调用
            // 会被当成失效，从而真的走一遍"免密续期 → 重试一次"。
            // ⚠️ 只在内存里自锁（App 不能写 Settings.Global，清了也没意义）：
            //    一个进程只演一次，避免"永远失效"这种假象把结论带偏。
            var probeFired = false
            client.debugForceExpire = {
                val armed = runCatching {
                    android.provider.Settings.Global.getInt(
                        appContext.contentResolver, "wz_expire_probe", 0,
                    ) == 1
                }.getOrDefault(false)
                if (armed && !probeFired) {
                    probeFired = true
                    android.util.Log.i("WanzaiRenew", "探针生效：本次门户调用按失效处理")
                    true
                } else {
                    false
                }
            }
            // 學在城院的原生接口層（B6 待辦看板的第二個來源）。
            // 用**裸 OkHttp**：門戶那個 client 會塞 ACCESS_TOKEN，拿去打
            // courses 域會出亂子（實測過）。限流仍走全域那一份閘門。
            // ★ 配置抽到 `net/TronHttp.kt`：单测必须用**同一支**（否则生产改坏了测试也绿）。
            val tronHttp = com.muyao.wanzaihyperos.net.tronHttpClient()
            val tron = TronclassClient(
                http = tronHttp,
                accountProvider = { portalSession.userCode() },
                accessTokenProvider = { tronSession.accessToken() },
                onSessionIdForOwner = { sid, identity ->
                    val original = identity ?: throw CancellationException("Account changed")
                    if (!epoch.publishData(original.generation) { tronSession.saveXSessionId(sid) })
                        throw CancellationException("Account changed")
                },
                cachedSessionId = { tronSession.xSessionId() },
            // 簽到包體要 `deviceId`（官方 1154.js 的 n={data,deviceId}）。同一台機器永遠同一個值。
            deviceIdProvider = { tronSession.deviceId() },
                // ★ 續期：待辦看板不經過「打開學在城院磁貼」那條會續期的路，
                //   token 過期時必須在這裡自己補救（否則界面只顯示「取數失敗」）。
                identityProvider = epoch::identity,
                refreshAccessTokenForOwner = { identity ->
                    val ownerToken = identity?.generation ?: throw CancellationException("Account changed")
                    var refreshToken: String? = null
                    if (!epoch.publishData(ownerToken) { refreshToken = tronSession.refreshToken() })
                        throw CancellationException("Account changed")
                    if (refreshToken.isNullOrBlank()) {
                        null
                    } else {
                        try {
                            val fresh = casService.refreshTronclassTokens(refreshToken!!, ownerToken = ownerToken)
                            if (!epoch.publishData(ownerToken) {
                                tronSession.saveTokens(fresh.accessToken, fresh.refreshToken,
                                    ssoCookiesJson = tronSession.ssoCookiesJson(), renewal = true)
                            }) throw CancellationException("Account changed")
                            fresh.accessToken
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (storage: com.muyao.wanzaihyperos.net.HzcuException.Storage) { throw storage }
                        catch (_: Exception) { null }
                    }
                },
            )
            tron.cache = httpCache
            tron.debugDump = { name, content -> diagnostics.record("tronclass", name, content); Unit }
            client.debugDump = { name, content -> diagnostics.record("portal", name, content); Unit }
            // 所以提出區域變數讓兩處指向同一實例。
            val billsRepo = LivePortalBillRepository(api, epoch = epoch)
            // B18：教務系統的兩個長命物件。**必須在這裡建一次**（見下面命名實參的註釋）。
            val jwEngineInstance = com.muyao.wanzaihyperos.data.jw.JwEngine()
            // B19：智雲課堂。同樣**只能建一份** —— WebView 裡那份會話就在引擎裡。
            val zyEngineInstance = com.muyao.wanzaihyperos.data.zy.ZyEngine()
            // B20：數智體育的會話。**只建一份** —— 倉庫每次取數都問它拿 token 與 userId。
            val graph = AppGraph(
                appContext = appContext,
                client = client,
                portalSession = portalSession,
                tronclassSession = tronSession,
                cjh52Session = cjh52Session,
                casService = casService,
                api = api,
                cjh52 = cjh52,
                cjh52Recovery = cjh52Recovery,
                tronclass = tron,
                httpCache = httpCache,
                tronHome = com.muyao.wanzaihyperos.data.tronclass.LiveTronHomeRepository(tron, epoch = epoch),
                // 簽到需要「我的 student_id」來判斷簽了沒 —— 由倉庫自己向
                // `/api/profile` 取（帶快取），不在此處傳變數：
                // 那個 id 是**介面來源**的信息，讓 AppGraph 記住它等於把
                // 「怎麼取得」洩漏到組裝層。
                rollcall = com.muyao.wanzaihyperos.data.tronclass.LiveRollcallRepository(tron, epoch = epoch),
                homework = com.muyao.wanzaihyperos.data.tronclass.LiveHomeworkRepository(tron, epoch = epoch),
                homeworkDetail = com.muyao.wanzaihyperos.data.tronclass.LiveHomeworkDetailRepository(tron, epoch = epoch),
                tronFeed = com.muyao.wanzaihyperos.data.tronclass.LiveTronFeedRepository(tron, epoch = epoch),
                tronMe = com.muyao.wanzaihyperos.data.tronclass.LiveTronMeRepository(tron, epoch = epoch),
                courseDetail = com.muyao.wanzaihyperos.data.tronclass.LiveCourseDetailRepository(tron, epoch = epoch),
                tronMembers = com.muyao.wanzaihyperos.data.tronclass.LiveTronMembersRepository(tron, epoch = epoch),
                tronResources = com.muyao.wanzaihyperos.data.tronclass.LiveTronResourcesRepository(tron, epoch = epoch),
                schedule = com.muyao.wanzaihyperos.data.tronclass.LiveScheduleRepository(tron, epoch = epoch, appContext = appContext, session = tronSession),
                catalog = LivePortalCatalogRepository(api, epoch = epoch),
                frequent = LivePortalFrequentAppsRepository(api, epoch = epoch),
                unread = LivePortalUnreadRepository(api, epoch = epoch),
                todo = LivePortalTodoRepository(api, epoch = epoch),
                news = LivePortalNewsRepository(api, epoch = epoch,
                    snapshotFile = java.io.File(appContext.filesDir, "content-snapshots/portal-news.json")),
                messages = LivePortalMessageRepository(api, epoch = epoch,
                    snapshotFile = java.io.File(appContext.filesDir, "content-snapshots/portal-messages.json")),
                broadcasts = com.muyao.wanzaihyperos.data.portal.LivePortalBroadcastRepository(api, epoch),
                search = LivePortalSearchRepository(api, epoch = epoch),
                courses = LiveCourseRepository(cjh52, epoch = epoch),
                // ★ 提出區域變數而不是直接寫 `bills = LivePortalBillRepository(api, epoch = epoch)`：
                //   B6 的待辦看板要複用**同一個**門戶事務倉庫（它自己帶單飛與快取），
                //   而命名實參在實參列表裡不能互相引用，所以必须先建實例。
                //   共用同一實例還有個好處：從「我的事務」回來時資料是热的。
                bills = billsRepo,
                notices = LiveNoticeRepository(cjh52, epoch = epoch),
                exams = LiveExamRepository(cjh52, epoch = epoch),
                growth = LiveGrowthRepository(cjh52, epoch = epoch),
                clazz = LiveClazzRepository(cjh52, epoch = epoch),
                todoBoard = LiveTodoRepository(billsRepo, tron, epoch = epoch),
                lectures = com.muyao.wanzaihyperos.data.cjh52.LiveLectureRepository(cjh52, epoch = epoch),
                zhgySession = zhgySessionInstance,
                // B18：教務系統。引擎與倉庫**共用同一個實例** ——
                // WebView 裡那份 session 就在引擎裡，分成兩份就等於兩份登入態。
                jwSession = jwSessionInstance,
                jwEngine = jwEngineInstance,
                jw = com.muyao.wanzaihyperos.data.jw.LiveJwRepository(jwEngineInstance, jwSessionInstance, epoch = epoch),
                jwTimetable = com.muyao.wanzaihyperos.data.jw.LiveJwTimetableRepository(
                    jwEngineInstance, jwSessionInstance,
                    // 調試版會把教务那张表的原文落盘（业主「课表和时间对不上」→
                    // 我要照学校给的真实字节写解析器，不是猜）。release 里不生效。
                    appContext = appContext, epoch = epoch, diagnostics = diagnostics,
                ),
                zyEngine = zyEngineInstance,
                zy = com.muyao.wanzaihyperos.data.zy.LiveZyRepository(zyEngineInstance, epoch = epoch),
                // B20：數智體育。會話與倉庫共用同一份 —— 倉庫每次取數都要問它拿 token 與 userId。
                sztySession = sztySessionInstance,
                szty = com.muyao.wanzaihyperos.data.szty.LiveSztyRepository(
                    com.muyao.wanzaihyperos.net.SztyClient(),
                    sztySessionInstance,
                    // 上次取到的六条原文放这儿：冷启动先用它画，别让首页「校园跑」空等十几秒。
                    snapshotDir = appContext.filesDir, epoch = epoch,
                ),
                accountEpoch = epoch, diagnostics = diagnostics, sessions = transaction,
            )
            credentials.ownerGraph = graph
            casService.beforeLoginForOwner = graph::beforeLogin
            casService.afterLoginForOwner = { account, token -> graph.confirmLogin(account, token) }
            return graph
        }
    }
}
