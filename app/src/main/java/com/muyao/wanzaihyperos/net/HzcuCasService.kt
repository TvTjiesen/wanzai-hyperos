package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 一跳的诊断信息。**已脱敏**：只有 host、去掉会话值的 path、query 的**键名**，
 * 以及状态码/类型/长度。没有任何凭据，所以可以安全地展示给业主、写进日志。
 *
 * 形状与 Flutter 蓝本一致，这样两边排障笔记能逐行对照。
 */
class HzcuHopDiagnosis(
    val step: Int,
    val method: String,
    val host: String,
    val path: String,
    val queryKeys: List<String>,
    val statusCode: Int,
    val contentType: String,
    val bodyLength: Int,
    val locationKeys: List<String>,
) {
    override fun toString(): String = buildString {
        append("  ").append(step).append(". ").append(method).append(' ')
        append(host).append(path)
        if (queryKeys.isNotEmpty()) append(" [").append(queryKeys.joinToString(",")).append(']')
        append(" → ").append(statusCode).append(' ').append(contentType)
        append(' ').append(bodyLength).append('B')
        if (locationKeys.isNotEmpty()) {
            append(" 跳转?").append(locationKeys.joinToString(","))
        }
    }
}

/** 门户登录成功的结果。`accessToken` 是凭据，**禁止打印**。 */
class HzcuCasLoginResult(
    val accessToken: String,
    val userCode: String,
    /** 命中的是哪个探针（诊断用）。 */
    val probeName: String,
    val chain: List<HzcuHopDiagnosis>,
) {
    /** 只输出长度，绝不输出值。 */
    override fun toString(): String =
        "HzcuCasLoginResult(token=<长度 ${accessToken.length}>, probe=$probeName, hops=${chain.size})"
}

/** 手机版学在城院的 identity token 对。两个字段都是 Keycloak 签发的 JWT。 */
class HzcuTronclassTokens(
    /** 登录硬通货：交给 SPA 的 `login-by-identity-token` 路由。这是**凭据**。 */
    val accessToken: String,
    /** 刷新用。可能为空（服务端没给 refresh 时只能重新走整条链）。 */
    val refreshToken: String?,
    val chain: List<HzcuHopDiagnosis>,
) {
    /** 只输出长度，绝不输出值。 */
    override fun toString(): String =
        "HzcuTronclassTokens(access=<长度 ${accessToken.length}>" +
            (if (refreshToken == null) ", 无refresh" else ", 有refresh") +
            ", hops=${chain.size})"
}

/** 探针的输入。**刻意不含 token 字段** —— 探针是纯函数，从响应里"读"出 token。 */
class HzcuProbeInput(
    val host: String,
    val query: Map<String, String>,
    val body: String,
    val statusCode: Int,
    /** 是不是已经回到门户域（还在 CAS 域就没得探）。 */
    val isPortalHost: Boolean,
)

/**
 * 门户 ACCESS_TOKEN 的候选提取方式。
 *
 * 为什么是「候选」而不是一条确定的路：门户是 uni-app SPA，
 * **token 从哪落到前端**这一环从来没有被证实过（docs/BLOCKED.md 的 T3 开口）。
 * 与其猜一条，不如把三条已知形态都试一遍，并报告**命中的是哪条** ——
 * 这样真机第一次跑通就能知道答案，不用反复改代码。
 *
 * 每条都是纯函数：输入一份响应，输出 token 或 null。可单独单测。
 */
object HzcuTokenProbes {

    /**
     * 候选 1：token 拼在门户落地 URL 的 query 上。
     * uni-app SPA 正是「从 URL 捡参数 → 存 localStorage.login_user」的套路。
     * 键名容错：`token` / `ACCESS_TOKEN` / `access_token`（大小写均试）。
     */
    fun tokenInQuery(input: HzcuProbeInput): String? {
        if (!input.isPortalHost) return null
        for ((key, value) in input.query) {
            val normalized = key.lowercase()
            if ((normalized == "token" || normalized == "access_token" || normalized == "accesstoken") &&
                looksLikeToken(value)
            ) {
                return value
            }
        }
        return null
    }

    /**
     * 候选 2：门户直接回 `{code:200,success:true,data:"<32位>"}` 包。
     * 门户统一响应包，`data` 可能直接是裸字符串（`gettemporarycode` 实测就是这个形态）。
     */
    fun tokenInJsonEnvelope(input: HzcuProbeInput): String? {
        if (!input.isPortalHost) return null
        val trimmed = input.body.trim()
        if (!trimmed.startsWith("{")) return null
        val root = try {
            Json.parseToJsonElement(trimmed) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null

        // 先确认这次调用本身是成功的，否则 `data` 里的东西没有意义。
        val success = root["success"].asBoolOrNull()
        val code = root["code"].asIntOrNull()
        if (success == false) return null
        if (code != null && code != HzcuEndpoints.CODE_OK) return null

        root["data"].asStringOrNull()?.let { if (looksLikeToken(it)) return it }

        // data 也可能是对象：{"ACCESS_TOKEN":"…"} 之类。
        val dataObject = root["data"].asObjectOrNull()
        if (dataObject != null) {
            for (key in ENVELOPE_TOKEN_KEYS) {
                val value = dataObject[key].asStringOrNull()
                if (value != null && looksLikeToken(value)) return value
            }
        }
        return null
    }

    /**
     * 候选 3：落地 HTML 的内联脚本把 login_user 写进 localStorage。
     * 门户 localStorage 形态：`login_user = {ACCESS_TOKEN, USER_TYPE, USER_CODE}`。
     */
    fun tokenInEmbeddedLoginUser(input: HzcuProbeInput): String? {
        if (!input.isPortalHost) return null
        // group(1) 是键名，group(2) 才是值 —— 别拿错。
        val match = LOGIN_USER_FIELD.find(input.body) ?: return null
        val value = match.groupValues[2]
        return if (looksLikeToken(value)) value else null
    }

    /** 默认探测顺序：按「门户是 uni-app、login_user 来自前端」这个事实排优先级。 */
    val default: List<Pair<String, (HzcuProbeInput) -> String?>> = listOf(
        "probeTokenInQuery" to ::tokenInQuery,
        "probeTokenInJsonEnvelope" to ::tokenInJsonEnvelope,
        "probeTokenInEmbeddedLoginUser" to ::tokenInEmbeddedLoginUser,
    )

    private val ENVELOPE_TOKEN_KEYS = listOf(
        "ACCESS_TOKEN", "access_token", "accessToken", "token",
    )

    /**
     * 键名组(group 1) + 值组(group 2)。
     *
     * Kotlin 的原始字符串用三重引号，里面同时写 `"` 与 `'` 都不需要转义
     * （Dart 的 `r'…'` 反而做不到，蓝本为此专门留了注释）。
     */
    private val LOGIN_USER_FIELD = Regex(
        """["']?(ACCESS_TOKEN|access_token|accessToken)["']?\s*[=:]\s*["']([A-Za-z0-9_\-]{16,64})["']""",
    )

    /**
     * token 形态判定：长度 16~64 的凭据字符集。
     *
     * ★ **不死盯 32**（文档记录的是 32）：门户同时存在 36 位 UUID 形态的 tempcode，
     * 若校方可续期凭据换了形态，死盯长度会把成功判成失败。
     */
    fun looksLikeToken(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 16 || trimmed.length > 64) return false
        return TOKEN_CHARSET.matches(trimmed)
    }

    private val TOKEN_CHARSET = Regex("^[A-Za-z0-9_\\-]+$")
}

/**
 * 城院统一身份认证（CAS）登录。
 *
 * 链路（真机实测 + 蓝本对照）：
 * ```
 * 1. GET  ca.hzcu.edu.cn/cas/login?service=<门户>      → 读 fm1 隐藏域
 * 2. POST <页面 action 原样，含 ;jsessionid>            → 密码用 AES 密文
 * 3. 跟跳转链，每一跳先跑探针再决定要不要真去 GET
 * ```
 *
 * ### 两条必须守住的纪律
 *
 * 1. **提交只发一次**（`maxAttempts = 1`）：带凭据的 POST「响应丢了」不等于
 *    「请求没到」，自动重试等于把同一份密码再交一次；密码错时失败次数翻倍，
 *    正中「按失败次数锁号」。
 * 2. **token 与 cookie 必须一起落**：[HzcuClient.persistCookieJar] + 会话保存
 *    是**同一步**。门户的 `ACCESS_TOKEN` 与 CAS 会话 Cookie 绑定，只落 token
 *    不落 cookie，业务接口会全部 401（真机抓包定位过的 bug）。
 *    所以这里只有一个出口 [_finish]，不许在三处各写一遍。
 *
 * ### 与蓝本的一处实现差异
 * 蓝本每一步都用**新建的局部 cookie 罐**；Kotlin 侧 OkHttp 的 CookieJar 挂在
 * client 上，所以登录开始时先把罐子**清空**（`cookieJar.clear()`），
 * 之后每一跳的 `Set-Cookie` 由 OkHttp 按 host 自动入桶。
 * 语义相同（一次登录 = 一个全新的会话），而且少一份手工传罐的出错面。
 */
class HzcuCasService private constructor(
    private val client: HzcuClient,
    private val session: PortalCredentials,
    private val probes: List<Pair<String, (HzcuProbeInput) -> String?>>,
    private val maxHops: Int,
    /**
     * CAS 登录页地址的构造方式。生产永远走 [HzcuEndpoints.casLogin]；
     * 单独抽出来只为一件事：**让单测能把整条登录链指向本机假服务器**
     * —— 那样全链路（取表单 → 加密提交 → 跟跳 → 探针命中 → 落盘）都能在
     * 不碰学校服务器、不用真实账号的前提下验证。
     */
    private val pageUriBuilder: (String) -> String,
    /**
     * TronClass OIDC 授权端点的构造方式。生产永远走
     * [HzcuEndpoints.tronclassMobileAuthUrl]；抽出来同样只为单测能指向假服务器。
     */
    private val tronclassAuthUrlBuilder: (String) -> String,
    /** TronClass token 端点。生产是 [HzcuEndpoints.TRONCLASS_OIDC_TOKEN_URL]。 */
    private val tronclassTokenUrl: String,
    private val epoch: AccountEpoch?,
) {

    var beforeLogin: (suspend (String) -> Unit)? = null
    var beforeLoginForOwner: (suspend (String) -> Long?)? = null
    var afterLogin: (suspend (String) -> Unit)? = null
    var afterLoginForOwner: (suspend (String, Long?) -> Unit)? = null

    private fun requireCurrent(token: Long?) {
        if (epoch != null && (token == null || !epoch.isCurrent(token))) throw CancellationException("Account changed")
    }

    private suspend fun sendForOwner(
        token: Long?, request: HzcuRequest, context: String, maxAttempts: Int? = null,
    ): HzcuRawResponse {
        currentCoroutineContext().ensureActive()
        requireCurrent(token)
        return client.send(request, context, maxAttempts, ownerToken = token).also { requireCurrent(token) }
    }

    private suspend fun publish(token: Long?, action: () -> Unit) {
        currentCoroutineContext().ensureActive()
        if (epoch == null) action()
        else if (token == null || !epoch.publish(token, action)) throw CancellationException("Account changed")
        requireCurrent(token)
    }

    companion object {
        const val DEFAULT_MAX_HOPS = 5

        fun create(
            client: HzcuClient,
            session: PortalCredentials,
            probes: List<Pair<String, (HzcuProbeInput) -> String?>> = HzcuTokenProbes.default,
            maxHops: Int = DEFAULT_MAX_HOPS,
            pageUriBuilder: (String) -> String = HzcuEndpoints::casLogin,
            tronclassAuthUrlBuilder: (String) -> String = HzcuEndpoints::tronclassMobileAuthUrl,
            tronclassTokenUrl: String = HzcuEndpoints.TRONCLASS_OIDC_TOKEN_URL,
            epoch: AccountEpoch? = null,
        ): HzcuCasService = HzcuCasService(
            client,
            session,
            probes,
            maxHops,
            pageUriBuilder,
            tronclassAuthUrlBuilder,
            tronclassTokenUrl,
            epoch,
        )

        // ---------------------------------------------------------------------
        // 「这次认证没过」的**强**判据
        // ---------------------------------------------------------------------

        /**
         * 只会出现在错误提示里、**绝不可能出现在正常页面**的词。
         *
         * 中英双语都要有：2026-10-09 实测发现学校有**两套模板**，
         * **移动模板（App 真机拿到的那个）的报错是英文的**：
         * `The credentials you provided cannot be determined to be authentic.`
         * 只列中文的话，一旦错误框结构变化（取不到那句话），英文报错就会漏判成
         * 「token 来源未证实」，把排查引向完全错误的方向。
         */
        private val STRONG_FAILURE_MARKERS = listOf(
            // 中文（桌面模板）
            "密码不正确", "密码错误", "用户名或密码", "账号或密码",
            "账号不存在", "用户不存在", "帐号", "帐密", "认证失败", "锁定",
            // 英文（移动模板，实测原文）
            "cannot be determined to be authentic", "credentials you provided",
            "Authentication failed", "Invalid credentials",
            "Account is locked", "locked out",
        )

        /**
         * 是「认证没过」就返回给人看的原因；不是就返回 null。
         *
         * ⚠️ 判据只用**强**词表 + 非空错误框。「验证码 / captcha」这类词**不能**当判据：
         * 实测的正常登录页里本来就有（fm2 的 tab 叫「手机验证码登录」、
         * fm2 带 captcha 输入框），拿它们定罪会把一次正常登录判成失败。
         */
        fun authFailureMessage(body: String): String? {
            if (body.isEmpty()) return null
            // 只有回到登录页形态，错误文案才有意义（否则页面是门户业务页）。
            val isLoginPage = body.contains("id=\"fm1\"") || body.contains("统一身份认证")
            if (!isLoginPage) return null

            errorMsgSentence(body)?.let { return localizeAuthFailure(it) }

            // 错误框为空/取不到：只有强判据词能定罪。
            for (marker in STRONG_FAILURE_MARKERS) {
                if (body.contains(marker)) {
                    return "统一身份认证未通过（页面提示含“$marker”）"
                }
            }
            return null
        }

        /**
         * 移动模板的英文报错 → 业主能直接读懂的中文（原文保留在后面）。
         *
         * 只翻译**已实测确认**的那一句，不发明别的：见到没见过的英文句子时原样透传 ——
         * 宁可让业主看到英文原文，也不要瞎翻译成错误的诊断方向。
         */
        private fun localizeAuthFailure(sentence: String): String {
            val sentence = safeRequestText(sentence, 160)
            val lower = sentence.lowercase()
            if (lower.contains("cannot be determined to be authentic") ||
                lower.contains("credentials you provided")
            ) {
                return "学号或密码不正确（学校返回原文：$sentence）"
            }
            if (lower.contains("locked")) {
                return "账号已被锁定，请联系学校信息化办（学校返回原文：$sentence）"
            }
            return safeRequestText(sentence, 160)
        }

        /**
         * 从 `div_errorMsgBox` 里取那句人话。找不到返回 null。
         *
         * 注意 marker 命中的位置在 `class="div_errorMsgBox">` 里面，
         * 所以切出来的片段开头一定带着 `">`，**必须一起剥掉** ——
         * 否则这句会被原样显示到登录页上，用户看到 `"> 密码不正确`。
         */
        internal fun errorMsgSentence(body: String): String? {
            val marker = "div_errorMsgBox"
            val index = body.indexOf(marker)
            if (index < 0) return null
            val from = index + marker.length
            val close = body.indexOf("</div>", from)
            if (close < 0) return null
            val text = body.substring(from, close)
                .replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ")
                // 去掉开头的 `"> 之类
                .replace(Regex("^[^\\u4e00-\\u9fa5A-Za-z0-9]+"), "")
                .trim()
            if (text.isEmpty()) return null
            return safeRequestText(text, 120)
        }
    }

    /**
     * 执行登录。**成功后 token 与会话 Cookie 都已进加密存储**，调用方只需刷新 UI。
     *
     * [account] [password] 来自登录页输入框；本方法**绝不**返回或记录明文密码。
     *
     * 失败时抛：
     * - [HzcuException.AuthenticationRejected] = 账号/密码问题 → UI 提示重来；
     * - [HzcuException.TokenSourceUnverified] = CAS 可能已认证成功，但没人知道 token 从哪取
     *   → 异常里带**脱敏**链条供改代码。
     */
    suspend fun login(
        account: String,
        password: String,
        service: String = HzcuEndpoints.PORTAL_BASE_URL,
    ): HzcuCasLoginResult {
        val trimmedAccount = account.trim()
        if (trimmedAccount.isEmpty()) {
            throw HzcuException.AuthenticationRejected("用户名不能为空")
        }
        if (password.isEmpty()) {
            throw HzcuException.AuthenticationRejected("密码不能为空")
        }

        currentCoroutineContext().ensureActive()
        val ownedBefore = beforeLoginForOwner
        val ownerToken = if (ownedBefore != null) ownedBefore(trimmedAccount) else {
            beforeLogin?.invoke(trimmedAccount)
            epoch?.token()
        }
        currentCoroutineContext().ensureActive()
        requireCurrent(ownerToken)
        val chain = mutableListOf<HzcuHopDiagnosis>()

        // 一次登录 = 一个全新的会话。清掉上一次可能残留的 cookie，
        // 避免「登录失败但旧会话还在」这种最难查的中间态。
        publish(ownerToken) { client.cookieJar.clear() }

        // 门户域 = 常量那个 iappdd + 本次 service 的 host。
        // 带上 service 是因为「CAS 认证成功后该回到的地方」按定义就是这次流程的门户；
        // 不带的话，测试里指回环 fake 时探针全部失效，等于没法单测。
        val serviceHost = HzcuEndpoints.hostOf(service)
        val portalHosts = buildSet {
            add(HzcuEndpoints.PORTAL_HOST.lowercase())
            if (!serviceHost.isNullOrEmpty()) add(serviceHost)
        }

        // ---- 第 1 步：取登录页，读 fm1 隐藏域 --------------------------------
        val pageUri = pageUriBuilder(service)
        val page = sendForOwner(ownerToken, HzcuRequest.url(pageUri), context = "CAS 登录页")
        chain += diagnose(1, "GET", page, pageUri)

        val form = try {
            HzcuCasForm.parse(page.body)
        } catch (error: HzcuException.Parse) {
            throw HzcuException.Parse(
                "统一身份认证登录页结构变了，读不到账号表单",
                details = "${error.message}\n${chainText(chain)}",
                cause = error,
            )
        }

        // ---- 第 2 步：提交表单 -----------------------------------------------
        // 提交地址以页面 action 为准（;jsessionid 必须原样带上，见 HzcuCasForm）。
        val submitUri = form.submitUri(pageUri)
        val fields = form.fields(
            account = trimmedAccount,
            // 浏览器提交的就是 AES 密文，必须一致；明文既过不了认证也违反红线。
            encryptedPassword = HzcuCasCrypto.encryptPassword(password),
        )

        var current = sendForOwner(ownerToken,
            HzcuRequest.url(submitUri, method = "POST", fields = fields),
            context = "CAS 表单提交",
            // ★ 带凭据的 POST **只发一次**，理由见类注释与 [HzcuClient.send]。
            maxAttempts = 1,
        )
        var currentUri = submitUri
        chain += diagnose(2, "POST", current, currentUri)

        // ---- 第 3 步：跑探针 → 跟跳 → 再跑探针 -------------------------------
        for (hop in 3..maxHops) {
            runProbes(current, currentUri, portalHosts)?.let { hit ->
                return finish(chain, hit.token, hit.probeName, trimmedAccount, ownerToken)
            }

            // 认证失败的判定必须在「跟下一跳」之前：
            // CAS 失败回的是 200 + 同一个登录页（带错误提示），不是 302。
            authFailureMessage(current.body)?.let { reason ->
                throw HzcuException.AuthenticationRejected(reason, details = chainText(chain))
            }

            if (!current.isRedirect) break // 到底了：非跳转、非登录页、无 token
            val location = current.location?.trim()
            if (location.isNullOrEmpty()) {
                throw HzcuException.Parse(
                    "统一身份认证返回跳转但没有 Location",
                    details = chainText(chain),
                )
            }
            val nextUri = resolveLocation(currentUri, location)

            // Location 本身就是一条候选链：token 若拼在回调 URL 上，现在就在这里，
            // 不跟着跳去取落地页。命中就省掉一次对学校服务器的请求（QPS 红线），
            // 落地页是 SPA 壳、请求失败也不影响已经拿到手的 token。
            probeInput(
                HzcuProbeInput(
                    host = HzcuEndpoints.hostOf(nextUri) ?: "",
                    query = parseQuery(nextUri),
                    body = "",
                    statusCode = current.statusCode,
                    isPortalHost = portalHosts.contains(HzcuEndpoints.hostOf(nextUri)?.lowercase() ?: ""),
                ),
            )?.let { hit ->
                return finish(chain, hit.token, hit.probeName, trimmedAccount, ownerToken)
            }

            current = sendForOwner(ownerToken, HzcuRequest.url(nextUri), context = "CAS 跳转链 第 $hop 跳")
            currentUri = nextUri
            chain += diagnose(hop, "GET", current, nextUri)
        }

        // 最后一条响应也要跑一遍探针（循环是「先探后跟」，尾巴没探）。
        runProbes(current, currentUri, portalHosts)?.let { hit ->
            return finish(chain, hit.token, hit.probeName, trimmedAccount, ownerToken)
        }
        // 尾巴也要做失败判定：落地页若带回错误文案，报「认证失败」比报
        // 「token 来源未证实」准确得多。
        authFailureMessage(current.body)?.let { reason ->
            throw HzcuException.AuthenticationRejected(reason, details = chainText(chain))
        }

        // ---- 全探针落空 -------------------------------------------------------
        // 如实报告链条，**绝不假装登录成功**。
        throw HzcuException.TokenSourceUnverified(chain.map { it.toString() })
    }

    /**
     * 登录成功的**唯一出口**：落 token + 落 cookie 罐。
     *
     * 抽成一个方法而不是在三处各写一遍，是因为这两步**必须一起做**：
     * 门户的 `ACCESS_TOKEN` 与 CAS 会话 Cookie 绑定，只落 token 不落 cookie，
     * 业务接口会全部 401。三处各写一遍时漏掉一处，症状就是「有时能登有时不能」，极难查。
     */
    private suspend fun finish(
        chain: List<HzcuHopDiagnosis>,
        token: String,
        probeName: String,
        account: String,
        ownerToken: Long?,
    ): HzcuCasLoginResult {
        publish(ownerToken) { session.savePortalSession(token, account, client.cookieJar.toJson()) }
        val ownedHook = afterLoginForOwner
        if (ownedHook != null) ownedHook(account, ownerToken) else afterLogin?.invoke(account)
        currentCoroutineContext().ensureActive()
        requireCurrent(ownerToken)
        return HzcuCasLoginResult(
            accessToken = token,
            userCode = account,
            probeName = probeName,
            chain = chain,
        )
    }

    /**
     * **用已存的 CAS 会话换一个新 ACCESS_TOKEN，不带密码**（业主 2026-10-19 选的「先 A」）。
     *
     * ## 为什么需要它
     *
     * 门户 token 一过期，原来**没有任何**恢复路径：`PortalCredentials` 不存密码，
     * 全项目也搜不到重登入口，于是所有门户功能（消息/新聞/待办/应用列表）红到底，
     * 只能等使用者自己重敲密码。
     *
     * ## 为什么这样做是安全的
     *
     * 浏览器登录过一次之后隔天再开"直接进去了"，靠的就是 CAS 的 TGT cookie。
     * 本项目已经把那次登录拿到的 cookie 存进 `SecureStore`
     * （[HzcuClient.persistCookieJar]，且 `finish()` 的注释写明「token 与 cookie 绑定」），
     * 所以**不需要把密码落在磁盘上**也能重放这条跳转链。
     *
     * ## 三条实现纪律
     *
     * 1. **绝不清 cookie**（和 [login] 最大的区别 —— login 开头就 `cookieJar.clear()`，
     *    复用它会亲手把要重放的东西删掉）。
     * 2. **换不到就回 null，不抛异常**。"cookie 也过期了"是**预期结果**，
     *    不是异常；调用方据此才决定要不要让使用者重登。
     *    原来 [login] 末尾抛的 `TokenSourceUnverified` 在这里不适用。
     * 3. **不提交任何表单、不碰密码字段**。整条链只有 GET 跟跳。
     *
     * @return 新的 ACCESS_TOKEN；重放失败（cookie 死了 / 需要重新输密码）返回 null。
     */
    /** 同 [HzcuClient.renewLog]：JVM 单测里 `android.util.Log` 是 not mocked。 */
    private fun renewLog(line: String) {
        runCatching { android.util.Log.i("WanzaiRenew", line) }
    }

    suspend fun renewWithExistingSession(
        service: String = HzcuEndpoints.PORTAL_BASE_URL,
        ownerToken: Long? = epoch?.token(),
    ): String? {
        currentCoroutineContext().ensureActive()
        requireCurrent(ownerToken)
        val chain = mutableListOf<HzcuHopDiagnosis>()
        val serviceHost = HzcuEndpoints.hostOf(service)
        val portalHosts = buildSet {
            add(HzcuEndpoints.PORTAL_HOST.lowercase())
            if (!serviceHost.isNullOrEmpty()) add(serviceHost)
        }

        var currentUri = pageUriBuilder(service)
        var current = try {
            sendForOwner(ownerToken, HzcuRequest.url(currentUri), context = "CAS 会话重放 第 1 跳")
        } catch (error: HzcuException) {
            // 网络层失败 ≠ 会话失效：这里一律当"续不上"处理，让调用方保留原有错误。
            renewLog("重放首跳失败（${error.javaClass.simpleName}）")
            return null
        }
        chain += diagnose(1, "GET", current, currentUri)

        for (hop in 2..maxHops) {
            runProbes(current, currentUri, portalHosts)?.let { hit ->
                return acceptRenewedToken(hit.token, chain, hit.probeName, ownerToken)
            }
            if (!current.isRedirect) break
            val location = current.location?.trim()
            if (location.isNullOrEmpty()) break
            val nextUri = resolveLocation(currentUri, location)

            // Location 上可能就挂着 token（和 login 同样的省请求策略）
            probeInput(
                HzcuProbeInput(
                    host = HzcuEndpoints.hostOf(nextUri) ?: "",
                    query = parseQuery(nextUri),
                    body = "",
                    statusCode = current.statusCode,
                    isPortalHost = portalHosts.contains(HzcuEndpoints.hostOf(nextUri)?.lowercase() ?: ""),
                ),
            )?.let { hit ->
                return acceptRenewedToken(hit.token, chain, hit.probeName, ownerToken)
            }

            current = try {
                sendForOwner(ownerToken, HzcuRequest.url(nextUri), context = "CAS 会话重放 第 $hop 跳")
            } catch (error: HzcuException) {
                renewLog("第 $hop 跳失败（${error.javaClass.simpleName}）")
                return null
            }
            currentUri = nextUri
            chain += diagnose(hop, "GET", current, nextUri)
        }

        runProbes(current, currentUri, portalHosts)?.let { hit ->
            return acceptRenewedToken(hit.token, chain, hit.probeName, ownerToken)
        }
        // 走到底没探到 token ⇒ cookie 已经不能免密换票。回 null，**不假装成功**。
        renewLog("重放落空（跳数=${chain.size}），需要使用者重新登录一次")
        return null
    }

    /** 重放成功的唯一出口：落 token + 落 cookie，并保留原有账号显示。 */
    private suspend fun acceptRenewedToken(
        token: String,
        chain: List<HzcuHopDiagnosis>,
        probeName: String,
        ownerToken: Long?,
    ): String {
        publish(ownerToken) { session.savePortalSession(token, session.userCode(), client.cookieJar.toJson()) }
        renewLog("免密续期成功 探针=$probeName 跳数=${chain.size}")
        return token
    }

    /** 退出登录：清 ACCESS_TOKEN 与会话 cookie。 */
    suspend fun logout() {
        val ownerToken = epoch?.token()
        publish(ownerToken) {
            client.cookieJar.clear()
            session.clear()
        }
    }

    /**
     * 用 CAS 登录换**手机版学在城院的 identity token**。
     *
     * ## 链路（蓝本全程实测打通，见 docs/PLAN-GPT.md §A4）
     *
     * ```text
     * 1. Keycloak OIDC 授权端点（client=tronclassH5, redirect_uri=手机版回调）
     *    → 自动 302 到 CAS 表单（Keycloak 的 ding-cas-client broker）
     * 2. 提交 CAS 表单（AES 密码，与 [login] 同一套）
     *    → 302 回 redirect_uri?code=<授权码>
     * 3. code → Keycloak token 端点 → {access_token, refresh_token}
     * ```
     *
     * 返回的 access_token 交给 WebView：
     * `mobile.hzcu.edu.cn/login-by-identity-token?accessToken=…&refreshToken=…&orgName=学在城院`
     * （真机实测落地 `/home`：扫码/签到 —— 钉钉同款）。
     *
     * ## 与 [login] 的关系
     *
     * 入口同源（都是 Keycloak + CAS 表单，`;jsessionid` 照抄提交），但**不走门户探针**：
     * 手机版 SPA 不认 cookie，只认 localStorage 里的 sessionID，
     * 而那个 sessionID 正是 `/api/login?login=access_token` 用这个
     * access_token 换来的 —— 所以 token 才是这链路的硬通货。
     *
     * ## 必须守住的纪律
     *
     * 1. **只在登录流程里调用**，用完就把 SSO cookie 落盘备用，不要反复登录。
     *    换码 POST（步骤 2 的表单提交、步骤 3 的 token 兑换）**只发一次**
     *    （`maxAttempts = 1`）：带凭据的 POST 自动重试等于把同一份密码再交一次。
     * 2. 本方法**不碰门户会话**：`client.cookieJar.clear()` 只清发出请求的罐子里的
     *    本次残留？——**不**。注意这里用的是**同一个 client**，
     *    所以开始前**不**清空罐子（那会把门户的会话一起清掉）。
     *    正确做法详见调用方的「第二份 client + 独立罐」注释。
     */
    suspend fun loginForTronclassTokens(
        account: String,
        password: String,
        state: String = java.util.UUID.randomUUID().toString().replace("-", ""),
    ): HzcuTronclassTokens {
        val trimmedAccount = account.trim()
        if (trimmedAccount.isEmpty()) {
            throw HzcuException.AuthenticationRejected("用户名不能为空")
        }
        if (password.isEmpty()) {
            throw HzcuException.AuthenticationRejected("密码不能为空")
        }

        val ownerToken = epoch?.token()
        currentCoroutineContext().ensureActive()
        requireCurrent(ownerToken)
        val chain = mutableListOf<HzcuHopDiagnosis>()
        val redirectUri = HzcuEndpoints.TRONCLASS_MOBILE_CALLBACK

        // ---- 1) OIDC 授权端点（会自动 302 到 CAS 表单）----
        //
        // 注：授权 URL 的 redirect_uri 带 query（`…callback?_h5=true`），
        // tronclassMobileAuthUrl 内部已做 encodeComponent 编码；这里直接用拼好的
        // 完整字符串，避免任何二次规范化破坏与注册值的逐字一致性。
        // （Keycloak 对 redirect_uri 是逐字比对，多一个参数就拒。）
        val authUrlString = tronclassAuthUrlBuilder(state)
        val authUri = authUrlString
        val page = sendForOwner(ownerToken, HzcuRequest.url(authUri), context = "OIDC 授权页")
        var current = page
        var currentUri = authUri
        chain += diagnose(1, "GET", current, currentUri)

        // ---- 2)+3) 跟跳 →（只在需要时）提交表单 → 一路跟到回调抓授权码 ----
        //
        // ★ 这条链上**不能假定一定有登录表单**。
        //   门户登录刚建立过 CAS 会话时，同一个 cookie 罐里已经有 TGT，
        //   CAS 那一跳会**直接 302 带 ticket 回跳**，整条链没有任何表单页
        //   （真机实测：第 3 跳 ca.hzcu.edu.cn/cas/login → 302 <无>）。
        //   所以「解析并提交表单」是**可选分支**，两种情形都必须能走完。
        var code: String? = null
        var submitted = false
        var step = 1
        var formParseError: String? = null

        for (round in 0 until maxHops + 5) {
            // (a) 已经落在回调地址上 → 授权码就在 URL 里，结束。
            //     SPA 只读 code、不校验 state，但我们**仍然要传 state**
            //     （OIDC 惯例 + Keycloak 注册值逐字一致性）。
            if (currentUri.startsWith(redirectUri) ||
                currentUri.contains("identity-web-login-callback")
            ) {
                code = parseQuery(currentUri)["code"]?.takeIf { it.isNotEmpty() }
                if (code != null) break
            }

            // (b) 表单是可选分支：只在「还没提交过」时尝试解析。
            val form = if (submitted) {
                null
            } else {
                try {
                    HzcuCasForm.parse(current.body)
                } catch (error: HzcuException.Parse) {
                    // 解析失败未必是故障：已认证态下这一页根本不是表单。
                    // 先记下来，真走到「没拿到码」时再连同跳转链一起报出去。
                    formParseError = error.message
                    null
                }
            }

            // (c) 认证失败判定必须在「跟下一跳」之前：
            //     CAS 失败回的是 200 + 同一个登录页（带错误提示），不是 302。
            //     只在「已提交过」或「这一页不是表单」时判定，
            //     避免把一张正常的登录页误判成失败页。
            if (submitted || form == null) {
                authFailureMessage(current.body)?.let { reason ->
                    throw HzcuException.AuthenticationRejected(reason, details = chainText(chain))
                }
            }

            // (d) 落在登录表单上 → 提交。带凭据的 POST **只发一次**，
            //     理由见类注释与 [HzcuClient.send]。
            if (form != null) {
                // 提交地址以页面 action 为准（;jsessionid 必须原样带上，见 HzcuCasForm）。
                val submitUri = form.submitUri(currentUri)
                val fields = form.fields(
                    account = trimmedAccount,
                    // 浏览器提交的就是 AES 密文，必须一致；明文既过不了认证也违反红线。
                    encryptedPassword = HzcuCasCrypto.encryptPassword(password),
                )
                step += 1
                current = sendForOwner(ownerToken,
                    HzcuRequest.url(submitUri, method = "POST", fields = fields),
                    context = "CAS 表单提交（OIDC）",
                    maxAttempts = 1,
                )
                currentUri = submitUri
                submitted = true
                formParseError = null
                chain += diagnose(step, "POST", current, currentUri)
                continue
            }

            // (e) 既不是回调也不是表单 → 继续跟跳。
            if (!current.isRedirect) break
            val location = current.location?.trim()
            if (location.isNullOrEmpty()) {
                throw HzcuException.Parse(
                    "OIDC 授权链返回跳转但没有 Location",
                    details = chainText(chain),
                )
            }
            val next = resolveLocation(currentUri, location)
            current = sendForOwner(ownerToken, HzcuRequest.url(next), context = "OIDC 授权跳转")
            currentUri = next
            step += 1
            chain += diagnose(step, "GET", current, next)
        }

        if (code.isNullOrEmpty()) {
            // 两种失败要分开说清楚，否则排查时会误以为是同一种。
            val how = if (submitted) {
                "已提交过账号表单"
            } else {
                "全程未出现账号表单" +
                    (formParseError?.let { "（表单解析失败：$it）" } ?: "（可能已是登录态或页面改版）")
            }
            throw HzcuException.Parse(
                "授权链走完了但没拿到授权码（$how）",
                details = chainText(chain),
            )
        }

        // ---- 4) 授权码 → token ----
        val tokenResponse = sendForOwner(ownerToken,
            HzcuRequest.url(
                tronclassTokenUrl,
                method = "POST",
                fields = mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to HzcuEndpoints.TRONCLASS_OIDC_CLIENT_ID,
                    "code" to code,
                    "redirect_uri" to redirectUri,
                ),
            ),
            context = "OIDC 换 token",
            maxAttempts = 1,
        )
        chain += diagnose(step + 1, "POST", tokenResponse, tronclassTokenUrl)
        val body = tokenResponse.body.trim()
        if (tokenResponse.statusCode != 200 || !body.startsWith("{")) {
            throw HzcuException.Api(
                code = tokenResponse.statusCode,
                message = "Keycloak 换 token 失败（HTTP ${tokenResponse.statusCode}）",
                details = chainText(chain),
            )
        }
        val decoded = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: throw HzcuException.Parse(
            "Keycloak 换 token 的响应不是 JSON 对象",
            details = chainText(chain),
        )
        val accessToken = decoded["access_token"].asStringOrNull()
        if (accessToken.isNullOrEmpty()) {
            throw HzcuException.Parse(
                "Keycloak 响应里没有 access_token",
                details = chainText(chain),
            )
        }
        // refresh_token 可能缺席（服务端没给时只能重新走整条链），不算失败。
        val refreshToken = decoded["refresh_token"].asStringOrNull()
        currentCoroutineContext().ensureActive()
        requireCurrent(ownerToken)
        return HzcuTronclassTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            chain = chain,
        )
    }

    /**
     * 用 refresh_token **续期**手机版学在城院的 token 对。
     *
     * ## 為什麼需要它（真機實測 2026-10-11）
     *
     * Keycloak 的 access_token 壽命約 1 小時。舊實現在**只在輸入密碼那一刻**
     * 換一次 token 對，之後一律直接用存的舊值 —— 實測 20:53 登入、22:44 打開
     * 學在城院就**卡在換票入口進不去**，只能重新輸密碼。
     *
     * 標準 OIDC 的 refresh_token 就是為這個場景設計的：
     * 用它可以拿新的 access_token，**不需要密碼、不需要重走 CAS 全鏈**。
     *
     * ## 失敗的態度
     *
     * 任何失敗（過期、被撤、網絡）都**原樣拋出**，由調用方決定回退
     * （調用方的回退是「用舊 token 試一次」，再不行才落到 SPA 自己的登入頁）。
     * **不吞錯** —— 吞了錯就永遠不知道該重新登入。
     *
     * ## 紀律
     *
     * - `maxAttempts = 1`：帶憑據的 POST 不自動重試（與 [loginForTronclassTokens] 同理）；
     * - 響應裡的 refresh_token **可能輪換**（Keycloak 的 reuse-refresh-token 策略），
     *   所以拿到新的就存新的，拿不到就保留舊的 —— 丟了 refresh 等於把續期能力丟了。
     */
    suspend fun refreshTronclassTokens(
        refreshToken: String,
        ownerToken: Long? = epoch?.token(),
    ): HzcuTronclassTokens {
        if (refreshToken.isBlank()) {
            throw HzcuException.AuthenticationRejected("refresh_token 为空，无法续期")
        }
        currentCoroutineContext().ensureActive()
        requireCurrent(ownerToken)
        val chain = mutableListOf<HzcuHopDiagnosis>()
        val response = sendForOwner(ownerToken,
            HzcuRequest.url(
                tronclassTokenUrl,
                method = "POST",
                fields = mapOf(
                    "grant_type" to "refresh_token",
                    "client_id" to HzcuEndpoints.TRONCLASS_OIDC_CLIENT_ID,
                    "refresh_token" to refreshToken,
                ),
            ),
            context = "OIDC 续期 token",
            maxAttempts = 1,
        )
        chain += diagnose(1, "POST", response, tronclassTokenUrl)
        val body = response.body.trim()
        if (response.statusCode != 200 || !body.startsWith("{")) {
            throw HzcuException.Api(
                code = response.statusCode,
                message = "Keycloak 续期失败（HTTP ${response.statusCode}）",
                details = chainText(chain),
            )
        }
        val decoded = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: throw HzcuException.Parse("Keycloak 续期的响应不是 JSON 对象", details = chainText(chain))
        val accessToken = decoded["access_token"].asStringOrNull()
        if (accessToken.isNullOrEmpty()) {
            throw HzcuException.Parse("Keycloak 续期响应里没有 access_token", details = chainText(chain))
        }
        currentCoroutineContext().ensureActive()
        requireCurrent(ownerToken)
        return HzcuTronclassTokens(
            accessToken = accessToken,
            // 輪換就換新，沒給就沿用傳進來的那份 —— 丟了它等於丟了續期能力。
            refreshToken = decoded["refresh_token"].asStringOrNull()?.takeIf { it.isNotBlank() }
                ?: refreshToken,
            chain = chain,
        )
    }

    // ---------------------------------------------------------------------------
    // 探针
    // ---------------------------------------------------------------------------

    private class ProbeHit(val token: String, val probeName: String)

    private fun runProbes(
        response: HzcuRawResponse,
        uri: String,
        portalHosts: Set<String>,
    ): ProbeHit? {
        val host = HzcuEndpoints.hostOf(uri) ?: ""
        return probeInput(
            HzcuProbeInput(
                host = host,
                query = parseQuery(uri),
                body = response.body,
                statusCode = response.statusCode,
                isPortalHost = portalHosts.contains(host.lowercase()),
            ),
        )
    }

    /**
     * 对任意一份探针输入（响应体，或**还没请求的 Location**）跑一遍全部探针。
     *
     * 拆出来是因为 CAS 成功链里最有价值的一跳是「302 的 Location 本身」。
     */
    private fun probeInput(input: HzcuProbeInput): ProbeHit? {
        if (!input.isPortalHost) return null // 还在 CAS 域，没得探
        for ((name, probe) in probes) {
            val token = try {
                probe(input)
            } catch (_: Exception) {
                // 探针是纯解析函数，它抛异常是探针自己的 bug，不能掀掉登录流程。
                continue
            }
            if (!token.isNullOrEmpty()) return ProbeHit(token, name)
        }
        return null
    }

    // ---------------------------------------------------------------------------
    // 诊断
    // ---------------------------------------------------------------------------

    private fun diagnose(
        step: Int,
        method: String,
        response: HzcuRawResponse,
        requestUri: String,
    ): HzcuHopDiagnosis {
        val path = runCatching { java.net.URI(requestUri).rawPath ?: requestUri }
            .getOrDefault(requestUri)
        return HzcuHopDiagnosis(
            step = step,
            method = method,
            host = HzcuEndpoints.hostOf(requestUri) ?: "",
            // ;jsessionid 的值抹掉，只留键名与结构。
            // 保留 `jsessionid=` 这个键名（而不是把整段删掉）：诊断时能一眼看出
            // 这一跳走的是 URL 重写而非 Cookie，同时值仍然是 `…`，不泄漏会话 ID。
            path = path.replace(Regex(";jsessionid=[^;]*"), ";jsessionid=…"),
            queryKeys = parseQuery(requestUri).keys.sorted(),
            statusCode = response.statusCode,
            contentType = response.contentType ?: "<无>",
            bodyLength = response.body.length,
            locationKeys = sanitizeLocation(response.location),
        )
    }

    private fun chainText(chain: List<HzcuHopDiagnosis>): String =
        "跳转链（已脱敏，无凭据）：\n" + chain.joinToString("\n")
}

// ---------------------------------------------------------------------------
// CAS 地址小工具（纯函数，放在文件作用域便于单测直接打）
// ---------------------------------------------------------------------------

/** Location 里只保留 query 的**键名**，值可能是 ticket。 */
internal fun sanitizeLocation(raw: String?): List<String> {
    val trimmed = raw?.trim() ?: return emptyList()
    if (trimmed.isEmpty()) return emptyList()
    return parseQuery(trimmed).keys.sorted()
}

/**
 * 取 query 参数（已做百分号解码，与 Dart 的 `Uri.queryParameters` 同语义：
 * 同名键**后者覆盖前者**）。
 *
 * 自己解而不交给 OkHttp：CAS 的地址里有 `;jsessionid=` 与各种编码，
 * 而且我们只需要「键名 + 值」，让 HttpUrl 去严格校验这些边缘地址反而多一处失败面。
 */
internal fun parseQuery(uri: String): Map<String, String> {
    val question = uri.indexOf('?')
    if (question < 0) return emptyMap()
    val query = uri.substring(question + 1).substringBefore('#')
    if (query.isEmpty()) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val equals = pair.indexOf('=')
        val rawKey = if (equals < 0) pair else pair.substring(0, equals)
        val rawValue = if (equals < 0) "" else pair.substring(equals + 1)
        out[urlDecode(rawKey)] = urlDecode(rawValue)
    }
    return out
}

private fun urlDecode(value: String): String =
    runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

/**
 * 按 RFC 3986 解析相对 Location。失败时退化成「用原地址的 scheme+authority 拼绝对路径」，
 * 因为 CAS 的 Location 里带 `;jsessionid`，严格解析器偶尔会拒。
 */
internal fun resolveLocation(baseUri: String, location: String): String {
    if (location.startsWith("http://") || location.startsWith("https://")) return location
    return try {
        java.net.URI(baseUri).resolve(location).toString()
    } catch (_: Exception) {
        val base = runCatching { java.net.URI(baseUri) }.getOrNull()
            ?: throw HzcuException.Parse("跳转地址无法解析", details = "base 不是合法 URI")
        val authority = buildString {
            append(base.scheme).append("://").append(base.host)
            if (base.port != -1) append(':').append(base.port)
        }
        if (location.startsWith("/")) authority + location else "$base/$location"
    }
}
