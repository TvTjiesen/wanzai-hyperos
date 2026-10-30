package com.muyao.wanzaihyperos.app

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
    /** 學在城院**課表**（把各門課的上課時間字串攤成一週的格子）。 */
    val schedule: com.muyao.wanzaihyperos.data.tronclass.ScheduleRepository,
    val catalog: PortalCatalogRepository,
    val frequent: PortalFrequentAppsRepository,
    val unread: PortalUnreadRepository,
    val todo: PortalTodoRepository,
    val news: PortalNewsRepository,
    val messages: PortalMessageRepository,
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
    fun fetch(block: suspend () -> Unit) {
        // 在途计数：小球靠它说「正在处理」（引擎 30 思考中）。没有这条计数就只能靠猜，
        // 而猜出来的「正在忙」是假的 —— 一次取数都没有的时候它也转。
        _inFlight.value = _inFlight.value + 1
        ioScope.launch {
            try {
                runCatching { block() }
            } finally {
                _inFlight.value = (_inFlight.value - 1).coerceAtLeast(0)
            }
        }
    }

    private val _inFlight = kotlinx.coroutines.flow.MutableStateFlow(0)

    /**
     * 当前有几笔取数在路上。页面用 `graph.inFlight.collectAsState()` 读，
     * 所以「开始取数 / 取完了」会自己触发重组，不需要页面轮询。
     */
    val inFlight: kotlinx.coroutines.flow.StateFlow<Int> = _inFlight


    /** 启动时该落在哪一页：已经登录就直奔主壳，否则先登录。 */
    fun startRoute(): StartRoute =
        if (portalSession.state == SessionState.LoggedIn) StartRoute.Main else StartRoute.Login

    /** 已登录时的学号（给「我的」页显示）。不是凭据本身。 */
    fun displayAccount(): String = portalSession.userCode().orEmpty()

    /** 退出登录：清凭据 + 清 cookie 罐 + 清 WebView 残留 + 恢复未知计数。 */
    suspend fun logout() {
        casService.logout()
        // ★ 学在城院的 token 对也一起清：它与门户是两套独立的登录态，
        //   只清门户会把「学在城院已登录」这个状态留给下一个账号。
        tronclassSession.clear()
        // ★ CJH52 的 tempcode 也是凭据，且是**第三套独立登录态**，同样要清。
        cjh52Session.clear()
        // ★ B17：智慧公寓那段 code 也是凭据（能读报修记录、内务分、生活缴费），
        //   它是**第四套独立登录态**。不清的话下一个人进来会看到前一个人的公寓资料。
        zhgySession.clear()
        // ★ B18：教務系統的帳密是**第五套獨立登錄態**，而且是全專案唯一的明文密碼。
        //   退出登錄時**必須**清掉 —— 這是最不能漏的一項。
        jwSession.clear()
        // ★ 只清自己的凭据还不够：学在城院那个 SPA 的登录态**不在 Cookie 里**，
        //   在 WebView 的 localStorage 里（isLogin / sessionID / identityRefreshToken）。
        //   不清它，下一个登录的人打开学在城院会直接进上一个人的首页。
        //   详见 WebViewCleanup 的类注释。
        runCatching { WebViewCleanup.clearAll(appContext) }
        unread.reset()
        // 待办与列表没有 reset 接口，靠下次访问时被 StaleGuard 作废 + 会话失效降级。
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
            val portalSession = PortalSession(store)
            // 触发一次读取：把上次登录的 token 与 cookie 载入内存。
            // 现在仍在后台线程，所以这里同步读是安全的。
            portalSession.ensureLoaded()

            val client = HzcuClient.create(credentials = portalSession)
            // ★ 见类注释第 2 条：token 与 cookie 必须一起恢复。
            client.restoreCookieJar()

            val api = PortalApi(client)
            val cjh52Session = Cjh52Session(store)
            // CJH52 的 token 提供者：从会话读（**不打印、不缓存到别处**）。
            val cjh52 = Cjh52Client(client, tokenProvider = { cjh52Session.token() })
            val tronSession = TronclassSession(store)
            val casService = HzcuCasService.create(client = client, session = portalSession)
            // ★ 门户 token 失效时的**免密续期**接上（业主选的「先 A」：只重放已存 CAS cookie，
            //   不把密码落到磁盘）。挂在 client 上 = 消息/新聞/待办/应用列表一次全好。
            client.renewSession = { casService.renewWithExistingSession() != null }
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
            val httpCache = com.muyao.wanzaihyperos.net.ResponseCache(appContext.filesDir)
            val tron = TronclassClient(
                http = tronHttp,
                accountProvider = { portalSession.userCode() },
                accessTokenProvider = { tronSession.accessToken() },
                onSessionId = { sid -> tronSession.saveXSessionId(sid) },
                cachedSessionId = { tronSession.xSessionId() },
            // 簽到包體要 `deviceId`（官方 1154.js 的 n={data,deviceId}）。同一台機器永遠同一個值。
            deviceIdProvider = { tronSession.deviceId() },
                // ★ 續期：待辦看板不經過「打開學在城院磁貼」那條會續期的路，
                //   token 過期時必須在這裡自己補救（否則界面只顯示「取數失敗」）。
                refreshAccessToken = {
                    val refreshToken = tronSession.refreshToken()
                    if (refreshToken.isNullOrBlank()) {
                        null
                    } else {
                        runCatching {
                            val fresh = casService.refreshTronclassTokens(refreshToken)
                            // SSO cookie 原樣帶上，別在續期時弄丟。
                            tronSession.saveTokens(
                                fresh.accessToken,
                                fresh.refreshToken,
                                ssoCookiesJson = tronSession.ssoCookiesJson(),
                            )
                            fresh.accessToken
                        }.getOrNull()
                    }
                },
            )
            // 只讀接口走本地緩存（5 分鐘）：他抱怨的「每次點進去都要重新載入」
            // 多數就是這幾支重複請求疊出來的。下拉刷新會繞開它（見 bypassCacheOnce）。
            tron.cache = httpCache
            // 調試版才裝這個鉤子：把「服務端到底回了什麼」落盤。
            //
            // ★ 為什麼要它（业主 2026-10-20：「签到功能根本就没生效…更何况你根本没判断准」）：
            //   他反饋「判斷不準」時，我要看的不是**我的解析器輸出了什麼**，
            //   而是**服務端實際回了什麼**。之前我從沒有 `/api/radar/rollcalls` 的真實回應樣本，
            //   等於閉著寫解析器。取回辦法（不需要 root）：
            //   `adb -s <串號> shell run-as com.muyao.wanzaihyperos cat files/tron_raw_rollcalls.txt`
            //   —— 他自己在 App 裡打開簽到頁就會寫，我不碰他的屏幕。
            if ((appContext.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            ) {
                tron.debugDump = { name, content ->
                    runCatching {
                        java.io.File(appContext.filesDir, "tron_raw_$name.txt").writeText(content)
                    }
                }
                // 门户侧的同款钩子（2026-10-30：消息标已读要验证服务端到底回了什么）。
                // 取法：adb shell run-as com.muyao.wanzaihyperos cat files/portal_raw_<路径>.txt
                client.debugDump = { name, content ->
                    runCatching {
                        java.io.File(appContext.filesDir, "portal_raw_$name.txt").writeText(content)
                    }
                }
            }
            // 所以提出區域變數讓兩處指向同一實例。
            val billsRepo = LivePortalBillRepository(api)
            // B18：教務系統的兩個長命物件。**必須在這裡建一次**（見下面命名實參的註釋）。
            val jwSessionInstance = com.muyao.wanzaihyperos.session.JwSession(store)
            val jwEngineInstance = com.muyao.wanzaihyperos.data.jw.JwEngine()
            // B19：智雲課堂。同樣**只能建一份** —— WebView 裡那份會話就在引擎裡。
            val zyEngineInstance = com.muyao.wanzaihyperos.data.zy.ZyEngine()
            return AppGraph(
                appContext = appContext,
                client = client,
                portalSession = portalSession,
                tronclassSession = tronSession,
                cjh52Session = cjh52Session,
                casService = casService,
                api = api,
                cjh52 = cjh52,
                tronclass = tron,
                httpCache = httpCache,
                tronHome = com.muyao.wanzaihyperos.data.tronclass.LiveTronHomeRepository(tron),
                // 簽到需要「我的 student_id」來判斷簽了沒 —— 由倉庫自己向
                // `/api/profile` 取（帶快取），不在此處傳變數：
                // 那個 id 是**介面來源**的信息，讓 AppGraph 記住它等於把
                // 「怎麼取得」洩漏到組裝層。
                rollcall = com.muyao.wanzaihyperos.data.tronclass.LiveRollcallRepository(tron),
                homework = com.muyao.wanzaihyperos.data.tronclass.LiveHomeworkRepository(tron),
                homeworkDetail = com.muyao.wanzaihyperos.data.tronclass.LiveHomeworkDetailRepository(tron),
                tronFeed = com.muyao.wanzaihyperos.data.tronclass.LiveTronFeedRepository(tron),
                tronMe = com.muyao.wanzaihyperos.data.tronclass.LiveTronMeRepository(tron),
                courseDetail = com.muyao.wanzaihyperos.data.tronclass.LiveCourseDetailRepository(tron),
                schedule = com.muyao.wanzaihyperos.data.tronclass.LiveScheduleRepository(tron),
                catalog = LivePortalCatalogRepository(api),
                frequent = LivePortalFrequentAppsRepository(api),
                unread = LivePortalUnreadRepository(api),
                todo = LivePortalTodoRepository(api),
                news = LivePortalNewsRepository(api),
                messages = LivePortalMessageRepository(api),
                search = LivePortalSearchRepository(api),
                courses = LiveCourseRepository(cjh52),
                // ★ 提出區域變數而不是直接寫 `bills = LivePortalBillRepository(api)`：
                //   B6 的待辦看板要複用**同一個**門戶事務倉庫（它自己帶單飛與快取），
                //   而命名實參在實參列表裡不能互相引用，所以必须先建實例。
                //   共用同一實例還有個好處：從「我的事務」回來時資料是热的。
                bills = billsRepo,
                notices = LiveNoticeRepository(cjh52),
                exams = LiveExamRepository(cjh52),
                growth = LiveGrowthRepository(cjh52),
                clazz = LiveClazzRepository(cjh52),
                todoBoard = LiveTodoRepository(billsRepo, tron),
                lectures = com.muyao.wanzaihyperos.data.cjh52.LiveLectureRepository(cjh52),
                zhgySession = com.muyao.wanzaihyperos.session.ZhgySession(store),
                // B18：教務系統。引擎與倉庫**共用同一個實例** ——
                // WebView 裡那份 session 就在引擎裡，分成兩份就等於兩份登入態。
                jwSession = jwSessionInstance,
                jwEngine = jwEngineInstance,
                jw = com.muyao.wanzaihyperos.data.jw.LiveJwRepository(jwEngineInstance, jwSessionInstance),
                jwTimetable = com.muyao.wanzaihyperos.data.jw.LiveJwTimetableRepository(
                    jwEngineInstance, jwSessionInstance,
                    // 調試版會把教务那张表的原文落盘（业主「课表和时间对不上」→
                    // 我要照学校给的真实字节写解析器，不是猜）。release 里不生效。
                    appContext = appContext,
                ),
                zyEngine = zyEngineInstance,
                zy = com.muyao.wanzaihyperos.data.zy.LiveZyRepository(zyEngineInstance),
            )
        }
    }
}
