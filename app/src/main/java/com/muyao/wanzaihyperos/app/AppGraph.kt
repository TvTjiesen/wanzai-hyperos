package com.muyao.wanzaihyperos.app

import com.muyao.wanzaihyperos.data.cjh52.GrowthRepository
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
) {

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
            // 學在城院的原生接口層（B6 待辦看板的第二個來源）。
            // 用**裸 OkHttp**：門戶那個 client 會塞 ACCESS_TOKEN，拿去打
            // courses 域會出亂子（實測過）。限流仍走全域那一份閘門。
            val tronHttp = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
            val tron = TronclassClient(
                http = tronHttp,
                accountProvider = { portalSession.userCode() },
                accessTokenProvider = { tronSession.accessToken() },
                onSessionId = { sid -> tronSession.saveXSessionId(sid) },
                cachedSessionId = { tronSession.xSessionId() },
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
            // B6 的待辦看板要複用同一個門戶事務倉庫（它帶單飛與快取），
            // 所以提出區域變數讓兩處指向同一實例。
            val billsRepo = LivePortalBillRepository(api)
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
            )
        }
    }
}
