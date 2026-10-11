package com.muyao.wanzaihyperos.net

/**
 * 城院各系统的地址与路径常量。**全项目唯一一份**，业务代码不许再写死域名字符串。
 *
 * 移植自 Flutter 蓝本 `lib/http/hzcu/hzcu_endpoints.dart`，常量值逐字保留。
 *
 * 实测（2026-10-08 / 10-10，业主 PC 直连，无 VPN）：
 * - `ca.hzcu.edu.cn`     只开 HTTP(80)，443 拒连 → 必须 http
 * - `iappdd.hzcu.edu.cn` 门户后端，仅 HTTP
 * - `courses.hzcu.edu.cn` 超星 TronClass，仅 HTTPS
 *
 * 因为存在 http 明文域，Android 侧必须有 `network_security_config` 放行
 * （`res/xml/network_security_config.xml`），否则 targetSdk 28+ 会直接拒掉请求。
 * **不要**退回 `android:usesCleartextTraffic="true"`：那是对全网放行明文，
 * 与「只给两个学校域开口子」的安全意图不符。
 */
object HzcuEndpoints {

    // ---------------------------------------------------------------------------
    // 域
    // ---------------------------------------------------------------------------

    /** 学校 CAS 统一身份认证。**只有 80 端口。** */
    const val CAS_HOST = "ca.hzcu.edu.cn"
    const val CAS_SCHEME = "http"

    /** 数字校园门户（移动校园 / 钉钉工作台）。**只有 80 端口。** */
    const val PORTAL_HOST = "iappdd.hzcu.edu.cn"
    const val PORTAL_SCHEME = "http"

    /** 门户 SPA 入口（浏览器用的地址，App 里只作 CAS service 回跳目标）。 */
    const val PORTAL_ENTRY_PATH = "/caimsmvc/hzcc.html"

    /** 门户回调 = ACCESS_TOKEN 的落地处。 */
    const val PORTAL_BASE_URL = "$PORTAL_SCHEME://$PORTAL_HOST/"

    /** 学在城院（TronClass）桌面版域。**只有 HTTPS。** */
    const val CHAOXING_HOST = "courses.hzcu.edu.cn"

    /**
     * 学在城院的 CAS 回调 —— 拿它的会话就靠这个 `service`。
     *
     * 实测链路（2026-10-10）：
     * ```
     * GET https://courses.hzcu.edu.cn/api/cas-login
     *  → 302 identity.hzcu.edu.cn/auth/realms/zju-xzzj/protocol/cas/login?service=<本常量>
     *  → ca.hzcu.edu.cn/cas/login        （与门户**同一个** CAS 表单）
     * 登录后 → courses.hzcu.edu.cn/session 可访问 /api/profile 与 /api/my-courses
     * ```
     * **关键结论**：学在城院和门户是**同一套统一身份认证**，只差一个 `service`。
     *
     * 注意：`const val` 不允许模板引用另一个 `const val` 以外的表达式，
     * 所以这里用 `+` 拼接（编译期常量，等价且不牺牲内联）。
     */
    const val CHAOXING_CAS_SERVICE = "https://$CHAOXING_HOST/api/cas-login"

    /**
     * ★ **Keycloak 的 CAS 代理入口** —— 学在城院免登**必须**从这个门进。
     *
     * 两条入口的实测对比（同一账号、同一时刻、只差入口）：
     *
     * | 入口 | 结果 | `/api/profile` |
     * |---|---|---|
     * | **本常量（Keycloak 代理）** | 成功，拿到 `courses` 的 `session`(91 字节) | **200**（3270 字节真实资料） |
     * | `ca.hzcu.edu.cn/cas/login`（直连） | **HTTP 400**，只有旧 session(84) | **401** |
     *
     * ⚠️ 这正是 2026-10-09 那次「学在城院走 CAS」失败的原因 ——
     * 当时只试了**直连**，看到「未认证授权的服务」就判定「学校没给学在城院注册 service」。
     * 真相是学校注册在 Keycloak 里。教训：**换一条入口再试，别因为一个门进不去就断定整栋楼关了。**
     */
    const val KEYCLOAK_CAS_LOGIN_BASE =
        "https://identity.hzcu.edu.cn/auth/realms/zju-xzzj/protocol/cas/login"

    // ---------------------------------------------------------------------------
    // 学在城院**手机版**（钉钉里那个能签到/扫码的 H5）
    //
    // 与 desktop 域是同一家 TronClass 的两个前端：手机版 SPA 自己的域是
    // `mobile.hzcu.edu.cn`，但接口仍打到 `courses.hzcu.edu.cn`
    // （org 配置里的 `apiUrl`，实测逐字解出）。
    // 钉钉工作台里那个磁贴的地址就是 `https://mobile.hzcu.edu.cn/?appid=156`。
    // ---------------------------------------------------------------------------

    /** 手机版 SPA 的域（磁贴真身）。 */
    const val TRONCLASS_MOBILE_HOST = "mobile.hzcu.edu.cn"

    /** 钉钉工作台里「学在城院」磁贴的原始地址。 */
    const val TRONCLASS_MOBILE_TILE = "https://$TRONCLASS_MOBILE_HOST/?appid=156"

    /**
     * 手机版 OIDC 登录用的 Keycloak 客户端。
     *
     * 来源：钉钉自己的 localStorage `_cap_global` 里那份 org 配置
     * （`identityConfigH5Dingtalk`），逐字为
     * `{"auth-server-url":"https://identity.hzcu.edu.cn/auth","realm":"zju-xzzj",
     *   "resource":"tronclassH5","public-client":true}`。
     * `public-client: true` = **不需要 client secret**，这是它能被复现的前提。
     */
    const val TRONCLASS_OIDC_CLIENT_ID = "tronclassH5"

    /**
     * OIDC 回跳地址。**必须逐字**与注册值一致（多一个参数都会被 Keycloak 拒）。
     *
     * 它同时是 SPA 的回调路由：`IdentityWebLoginCallback` 组件只读 `?code=`，
     * **不校验 state**（读源码确认），所以 state 按 OIDC 惯例带上即可。
     */
    const val TRONCLASS_MOBILE_CALLBACK =
        "https://$TRONCLASS_MOBILE_HOST/identity-web-login-callback?_h5=true"

    private const val TRONCLASS_OIDC_AUTH_BASE =
        "https://identity.hzcu.edu.cn/auth/realms/zju-xzzj/protocol/openid-connect/auth"

    /** OIDC token 端点（授权码换 token 用）。 */
    const val TRONCLASS_OIDC_TOKEN_URL =
        "https://identity.hzcu.edu.cn/auth/realms/zju-xzzj/protocol/openid-connect/token"

    /**
     * 手机版免登的**最终入口**（真机实测通过）。
     *
     * ```
     * https://mobile.hzcu.edu.cn/login-by-identity-token
     *     ?accessToken=<Keycloak JWT>&refreshToken=<JWT>&orgName=学在城院&area=cn
     * ```
     * 这是 SPA 自己的懒加载路由 `login-by-identity-token`（chunk 489）：
     * 组件读 `accessToken`/`refreshToken`/`orgName`/`area` 后调
     * `loginByAccessToken` → `POST /api/login?login=access_token` →
     * `X-SESSION-ID` 回填 localStorage.global → **完整登录态**。
     *
     * 实测（2026-10-10 20:21，真机 WebView）：
     * 落地 `https://mobile.hzcu.edu.cn/home` ——「晚上好！李同学同学」+
     * 扫码/签到 + 真实课程列表。
     */
    const val TRONCLASS_MOBILE_IDENTITY_TOKEN_PATH = "/login-by-identity-token"

    /** 手机版免登的**种子 URL**：注入 SSO cookie 后打开它，后续交给 SPA 自己走。 */
    fun tronclassMobileAuthUrl(state: String): String =
        "$TRONCLASS_OIDC_AUTH_BASE?client_id=$TRONCLASS_OIDC_CLIENT_ID" +
            "&redirect_uri=${encodeQuery(TRONCLASS_MOBILE_CALLBACK)}" +
            "&response_type=code&scope=openid&state=$state"

    // ---------------------------------------------------------------------------
    // CJH52（浙大城市学院场景管理）—— 第二期
    // 契约来自真机取证，全文见 docs/B0-CJH52-CONTRACT.md
    // ---------------------------------------------------------------------------

    /** 移动端 H5 主機。 */
    const val CJH52_HOST = "cjh52.hzcu.edu.cn"

    const val CJH52_BASE_URL = "https://$CJH52_HOST"

    /**
     * 用门户 tempcode 换 CJH52 会话。**body 是 `{}`**（实测），
     * 凭据走 `Authorization: Bearer`。
     */
    const val CJH52_GET_USER_BY_TOKEN_PATH =
        "/dapi/v2/relationship/relationship_service/get_user_by_token"

    /**
     * 課表（B1 用）。
     *
     * 实测：body `{uid, state:"open"}` → `data[]` 每项含
     * `classTimeInfoList[]`（多時段/多周次/多教室）與 `teacherInfoList[]`。
     */
    const val CJH52_CLASS_INFO_BY_UID_PATH =
        "/dapi/v2/school_manager/class_service/get_class_info_by_uid"

    /** 當前考勤（B2 用）。body `{userId}`；空態是 `[]` 而非 null。 */
    const val CJH52_CURRENT_ATTENDANCE_PATH =
        "/dapi/v2/attendance/course_attendance_service/query_student_current_attendance"

    // ---- B3 剩餘三項（真機取證：build/three.json、build/codes.json）----------

    /**
     * 日常通知（讀者視角）。
     *
     * 實測請求：`{scope: {school, college[], grade[], specialty[], role}, pageSize, pageNum}`
     * —— `scope` 的幾個值**從 `get_user_by_token` 的用戶資訊推出來**，不是硬編碼。
     */
    const val CJH52_NOTICE_READER_PATH =
        "/dapi/v2/courier/notice_service/find_all_for_reader"

    /**
     * 我的考試。
     *
     * 實測請求：`{pageNum: 0, pageSize: 10, modelCode: "M0017"}`
     * ⚠️ **`pageNum` 從 0 起**（與「我的事務」那批從 1 起的不同）。
     */
    const val CJH52_EXAM_LIST_PATH =
        "/dapi/v2/school_manager/exam_service/get_user_exam_list"

    /** 我的考試的 `modelCode`。實測值，非猜測。 */
    const val CJH52_EXAM_MODEL_CODE = "M0017"

    // ---- 我的成長（8 個接口，真機實測）---------------------------------------

    /** KQA 三維分數。body `{userId}` → `{Q, GY, K}`。 */
    const val CJH52_KQA_SCORE_PATH = "/dapi/v2/model/query_service/query_one_kqa_score"

    /** 體測成績。body `{}` → `data[]`。 */
    const val CJH52_PHYSIQUE_PATH =
        "/dapi/v2/school_manager/physique_service/find_my_physique_scores"

    /** 學分達成。body `{}` → `data{}`。 */
    const val CJH52_CREDIT_PROGRESS_PATH =
        "/dapi/v2/studyguide/quality_service/get_my_quality_course_score"

    /** 行為記錄（按月）。body `{userId, startYear, endYear}` → `data.results[]`。 */
    const val CJH52_ACTIVITY_MONTHS_PATH =
        "/dapi/v2/model/kqa_statistical_service/get_events_by_user_id_and_years_group_by_month"

    /** 用戶資訊（成長頁要拿 userSid）。body `{userSid}`。 */
    const val CJH52_USER_INFO_PATH =
        "/dapi/v2/school_manager/user_service/get_user_info_by_id"

    /**
     * 班級詳情（B：我的班級原生化）。body `{clazzSid}`。
     *
     * 實測回應 `data = { sid, clazz, specialty, grade, number, collegeName,
     * teacherInfoList[], counselorInfoList[] }` —— 班級 sid 從
     * `get_user_by_token` 的 `data.clazz.sid` 拿。
     */
    const val CJH52_CLAZZ_INFO_PATH =
        "/dapi/v2/school_manager/clazz_service/get_clazz_info_plus_by_id"

    // ---------------------------------------------------------------------------
    // CAS
    // ---------------------------------------------------------------------------

    fun casLogin(service: String? = null): String =
        if (service == null) "http://$CAS_HOST/cas/login"
        else "http://$CAS_HOST/cas/login?service=${encodeQuery(service)}"

    // ---------------------------------------------------------------------------
    // 门户接口（全部 POST，鉴权 = form 字段 token=<ACCESS_TOKEN>）
    // ---------------------------------------------------------------------------

    /** 全量应用分组目录：7 分组。 */
    const val PORTAL_APP_GROUP_PATH = "/api/app/exclude/listappinfogroup/1"

    /** 未读消息数（通知角标核心）。 */
    const val PORTAL_UNREAD_COUNT_PATH = "/api/app/ummessage/getmessagenoreadcount"

    /** 我的常用应用（首页宫格 + 更多）。 */
    const val PORTAL_FREQUENT_APPS_PATH = "/api/app/listappinfoex"

    /** 签发一次性 tempcode（跨子系统 SSO 的钥匙，一次性，不可缓存复用）。 */
    const val PORTAL_TEMPORARY_CODE_PATH = "/api/outh/gettemporarycode"

    /**
     * 门户登录。[**只接受 JSON**]，form 会被 Spring 顶回
     * `Content type 'application/x-www-form-urlencoded;charset=UTF-8' not supported`。
     */
    const val PORTAL_LOGIN_PATH = "/api/outh/login"

    /** 首页公告栏。 */
    const val PORTAL_BULLETIN_PATH = "/api/app/ydmhbilllist/getbillboad"

    /** 新闻分组 / 列表。 */
    const val PORTAL_NEWS_GROUP_PATH = "/api/newsapi/listnewsgroup"

    /** ⚠️ `listnewslist` **不吃 form**，必须 JSON 包体。 */
    const val PORTAL_NEWS_LIST_PATH = "/api/newsapi/listnewslist"

    /**
     * 应用搜索。
     *
     * 契约逐字来自门户自己的 `pages-search-SearchPage.js`：
     * ```js
     * var e = { appName: t, devicetype: 1, pageindex: 1, pagesize: 20 };
     * sendRequest({ url: "/api/app/exclude/appinfolist", method: "POST", data: e })
     * // 成功：res.rows 就是应用数组
     * ```
     * 注意它与目录的 `listappinfogroup` 是**同族不同名**的两个接口，
     * 一个按 `usertype` 分组返回，一个按名字搜（扁平的 `rows`）。
     */
    const val PORTAL_APP_SEARCH_PATH = "/api/app/exclude/appinfolist"

    /** 消息分组 / 列表。 */
    const val PORTAL_MESSAGE_GROUP_PATH = "/api/app/ummessage/listmessagegroup"

    /**
     * ⚠️ 两条实测出来的坑，改这个接口时别再踩：
     * 1. **必须 JSON 包体**（form 会被服务端顶回错误）；
     * 2. 数据在**信封顶层的 `rows`，`data` 是 `null`** ——
     *    用 `HzcuResponse.data` 读会静默拿到 null（不报错，只表现为「没有消息」）。
     */
    const val PORTAL_MESSAGE_LIST_PATH = "/api/app/ummessage/listmessage"

    /**
     * 首页「待办事项」三个计数的接口。契约逐字读自门户自己的 JS。
     *
     * ⚠️ 三个坑（与消息那一族**不同**，别互相套用）：
     * 1. 包体是 **form**（`x-www-form-urlencoded`）；
     * 2. 数据在 **`data`**（不是信封顶层的 `rows`）；
     * 3. 门户自己把结果缓存在 `bill_boad`，我们对应 15 分钟 TTL。
     *
     * 名字里的 `ydmh` = 移动门户。当初判定「无法枚举接口」是因为没有凭据 ——
     * 任意 `/api/app/` 下的接口都回 `401 token 不能为空`，分不出 404/401。
     * 从门户前端代码里读接口名绕开了这个死结：**有源码就别猜接口。**
     */
    const val PORTAL_TODO_COUNT_PATH = "/api/app/ydmhbilllist/getbillboad"

    // ---------------------------------------------------------------------------
    // URL 拼装
    // ---------------------------------------------------------------------------

    /**
     * 门户 URL。
     *
     * ⚠️ 这里是**字符串拼接**，不是「解析成 path 再交给 URL 构造器」。
     * Flutter 蓝本踩过的坑：`Uri(path: path)` 会把 query 里的 `?` 当成
     * 路径里的非法字符编码成 `%3F`，于是 `?a=1` 变成路径的一部分、
     * 服务端收不到任何查询参数，表现为**静默失败**（很难看出来）。
     * Android 侧同理：任何「把 path 当独立组件传」的 API 都别用在带 query 的地址上。
     */
    fun portal(path: String): String = "$PORTAL_SCHEME://$PORTAL_HOST$path"

    /**
     * 从任意 URL 取**小写 host**；解析不了返回 null（**不抛异常**）。
     *
     * 放在这里而不是让网络层各自 `URI(...)`：CAS 链路里的地址带 `;jsessionid=`
     * 与各种编码，各处自己解析迟早会有一处忘了小写化，而 host 比较是大小写敏感的字符串比较 ——
     * 一次大小写不一致就会让「这算不算门户域」判错，探针随之全部失效。
     */
    fun hostOf(url: String): String? {
        if (url.isBlank()) return null
        return runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull()
    }

    /**
     * 极简百分号编码，用于拼 query。语义与 Dart 的 `Uri.encodeComponent` 一致。
     *
     * `internal` 而不是 `private`：单测要断言授权 URL 里 redirect_uri 的逐字一致性，
     * 不暴露它就只能把期望值硬编码一份 —— 两份编码逻辑迟早漂移。
     */
    internal fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    // ---------------------------------------------------------------------------
    // 门户响应信封：{code, success, message, data}
    // 实测：缺 token → HTTP 401 + {"code":401,"message":"token 不能为空","success":false}
    // 实测：伪造的 32 位 token 同样返回这句（服务端不做区分，不泄露 token 是否存在）
    // ---------------------------------------------------------------------------

    const val CODE_OK = 200
    const val CODE_UNAUTHORIZED = 401
}
