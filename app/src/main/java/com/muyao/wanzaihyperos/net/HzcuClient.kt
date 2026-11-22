package com.muyao.wanzaihyperos.net

import androidx.annotation.VisibleForTesting
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 城院网络层**唯一出口**。
 *
 * 三件事全部在这里做完，别处不许各写一份：
 * 1. **限流**：所有请求都必须穿过 [HzcuRateLimiter]（QPS ≤ 1，同接口 ≥2s）；
 * 2. **重试**：网络类错误指数退避，最多 [DEFAULT_MAX_ATTEMPTS] 次；
 *    认证失败**不重试**（重试只会锁号），且可由调用方收紧到 1 次；
 * 3. **凭据**：ACCESS_TOKEN 从 [PortalCredentials] 取，只写进请求头与请求体，
 *    **绝不写日志**。
 *
 * 传输层用 OkHttp。构造函数私有，外部只能通过 [create] 拿到实例，
 * 而 [create] 默认注入全项目唯一的 [HzcuRateLimiter.instance] ——
 * 就算不小心建了两个 client，它们也共用同一个全局闸门，
 * QPS≤1 这条红线不会因为多一个 client 而失效。
 *
 * ### 两个必须保持的传输层设置
 * - **关掉自动跳转**（`followRedirects(false)`）：CAS 链路要逐跳读 `Location`
 *   （ticket 就在里面），交给 OkHttp 自动跟会丢掉中间响应，也没法在某一跳上做判断。
 * - **关掉 `retryOnConnectionFailure`**：连接级重试绕不过我们自己的闸门，
 *   等于在限流器背后偷偷多发请求。重试统一由 [send] 的退避循环负责，
 *   它**每次都要重新排队**，QPS 红线不会被重试打穿。
 */
class HzcuClient private constructor(
    private val httpClient: OkHttpClient,
    /**
     * 门户 Cookie 罐。**按 host 分桶**（见 [HzcuCookieJar]）。
     * 暴露出来是为了让登录流程（A3）能在登录跳转链结束后把罐子落盘。
     */
    val cookieJar: HzcuCookieJar,
    private val rateLimiter: HzcuRateLimiter,
    private val credentials: PortalCredentials,
    private val portalBaseUrl: String,
    private val maxAttempts: Int,
    private val backoffBaseMs: Long,
    private val epoch: AccountEpoch?,
) {

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_BACKOFF_BASE_MS = 1_000L

        const val CONNECT_TIMEOUT_MS = 15_000L
        const val READ_TIMEOUT_MS = 20_000L

        /**
         * 学校服务器对默认的客户端 UA 不友好（曾见过直接回网关错误页），
         * 沿用浏览器 UA（计划「保留原爬虫，改 UA/域名」）。
         *
         * 这不是伪装成浏览器去绕反爬 —— 我们打的是学校自己的公开门户接口，
         * 业主有账号；用浏览器 UA 只是避免被 WAF 按「陌生客户端」拦掉，
         * 拦掉之后表现为 HTML 错误页，非常难排查。
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
        private val FORM_MEDIA_TYPE: MediaType = "application/x-www-form-urlencoded".toMediaType()

        /**
         * 建一个客户端。
         *
         * 生产只允许在组合根（`AppGraph`）里调一次；
         * 参数全部有默认值，所以调用点应当只有一行。
         */
        fun create(
            credentials: PortalCredentials,
            portalBaseUrl: String = HzcuEndpoints.portal(""),
            cookieJar: HzcuCookieJar = HzcuCookieJar(),
            rateLimiter: HzcuRateLimiter = HzcuRateLimiter.instance,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
            backoffBaseMs: Long = DEFAULT_BACKOFF_BASE_MS,
            httpClient: OkHttpClient? = null,
            epoch: AccountEpoch? = null,
        ): HzcuClient {
            val jar = cookieJar
            jar.bindEpoch(epoch)
            val client = httpClient ?: OkHttpClient.Builder()
                .cookieJar(jar)
                // 见类注释：CAS 要逐跳读 Location，自动跳转会把这个能力吃掉。
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                // 不设 callTimeout：CAS 跳转链可能有多跳，总超时会把正常的慢登录腰斩。
                .retryOnConnectionFailure(false)
                .build()
            return HzcuClient(
                httpClient = client,
                cookieJar = jar,
                rateLimiter = rateLimiter,
                credentials = credentials,
                portalBaseUrl = portalBaseUrl.trimEnd('/'),
                maxAttempts = maxAttempts.coerceAtLeast(1),
                backoffBaseMs = backoffBaseMs,
                epoch = epoch,
            )
        }

        /** 仅供测试断言「注入的限流器就是全局那一份」。 */
        @VisibleForTesting
        fun isGlobalRateLimiter(rateLimiter: HzcuRateLimiter): Boolean =
            rateLimiter === HzcuRateLimiter.instance
    }

    // ---------------------------------------------------------------------------
    // Cookie 罐
    // ---------------------------------------------------------------------------

    /**
     * 从会话层把 cookie 罐读进客户端。**App 启动预热时调一次**。
     *
     * 就地替换（[HzcuCookieJar.loadFrom]）而不是换对象：
     * OkHttpClient 持有的是这个罐子的引用。
     */
    fun restoreCookieJar() {
        publish(epoch?.token()) { cookieJar.loadFrom(credentials.portalCookiesJson()) }
    }

    /**
     * 把 cookie 罐落回会话层（加密存储）。
     *
     * 由登录流程在跳转链结束后显式调用。**没有做成每次请求自动落盘**：
     * 一次加密写入比一次网络请求还贵，而 `Set-Cookie` 在业务请求里极少变化。
     */
    fun persistCookieJar() {
        publish(epoch?.token()) {
            val json = cookieJar.toJson()
            if (json.isNotEmpty() && json != "{}") credentials.savePortalCookies(json)
        }
    }

    private fun requireCurrent(token: Long?) {
        if (epoch != null && (token == null || !epoch.isCurrent(token))) {
            throw CancellationException("Account changed")
        }
    }

    private fun publish(token: Long?, action: () -> Unit) {
        if (epoch == null) action()
        else if (token == null || !epoch.publish(token, action)) throw CancellationException("Account changed")
    }

    // ---------------------------------------------------------------------------
    // 门户业务调用
    // ---------------------------------------------------------------------------

    /**
     * 门户 GET，**返回原始正文（不拆壳）**。
     *
     * 为什么要有它：门户 SPA 的静态资源（`/caimsmvc/` 下的 html 与 js）**不是 JSON 包体**，
     * 走 [portalPost] 的拆壳会直接抛解析异常。这条路只为「取门户自己的前端代码」用
     * —— 那批接口当初就是从 6 个 JS bundle 静态提取的。
     *
     * 仍然走**同一套限流闸门与门户 Cookie**，绝不另开网络路径
     * （否则 QPS≤1 的单一闸门就漏了）。
     */
    suspend fun portalGetText(
        path: String,
        context: String = "hzcu",
        maxAttempts: Int? = null,
    ): HzcuRawResponse {
        val ownerToken = epoch?.token()
        requireCurrent(ownerToken)
        val token = credentials.accessToken()
        val headers = if (!token.isNullOrEmpty()) mapOf("token" to token) else emptyMap()
        return send(
            HzcuRequest.url(portalBaseUrl + path, method = "GET", headers = headers),
            context = context,
            maxAttempts = maxAttempts,
            ownerToken = ownerToken,
        )
    }

    /**
     * 调门户接口并拆壳。**这是所有门户功能唯一该用的方法。**
     *
     * - 无 token（未登录）→ 直接抛 [HzcuException.LoginExpired]，**不发请求**（省一次无谓命中）；
     * - HTTP 401 / 包体 code=401 / 被 302 到认证页 → [HzcuException.LoginExpired]（不重试）；
     * - 其它业务失败 → [HzcuException.Api]，消息用服务端给的中文原文。
     *
     * ★ **两种包体类型并存**（真机抓包定位，别改成统一一种）：
     * - 默认（[fields]）→ `application/x-www-form-urlencoded`；
     * - [jsonBody] → `application/json`：`listnewslist`、`listmessage` **不吃 form**。
     *   同一批接口里两种类型并存，所以**必须由调用方按接口选**。
     * 两条路共用同一套限流闸门 / Cookie / token 头 / 拆壳 —— 不许为 JSON 另开网络路径。
     *
     * [maxAttempts] 只允许**收紧**（见 [send]）。
     */
    suspend fun portalPost(
        path: String,
        fields: Map<String, String>? = null,
        jsonBody: JsonObject? = null,
        context: String = "hzcu",
        attachToken: Boolean = true,
        maxAttempts: Int? = null,
    ): HzcuResponse {
        require(fields == null || jsonBody == null) {
            "fields 与 jsonBody 只能给一个：同一请求不可能既是 form 又是 JSON"
        }
        val ownerToken = epoch?.token()
        val readOnly = isPortalRead(path)
        var renewed = false
        while (true) {
            currentCoroutineContext().ensureActive()
            requireCurrent(ownerToken)
            val rejectedToken = credentials.accessToken()
            val outcome = catchingActive {
                portalPostOnce(path, fields, jsonBody, context, attachToken, maxAttempts, ownerToken, readOnly)
            }
            requireCurrent(ownerToken)
            val error = outcome.exceptionOrNull() ?: return outcome.getOrThrow()
            val ownedRenewer = renewSessionForOwner
            val renewer = renewSession
            if (!readOnly || !attachToken || rejectedToken.isNullOrEmpty() || error !is HzcuException.LoginExpired || renewed ||
                (ownedRenewer == null && renewer == null)) throw error
            renewed = true
            renewLog(safeRequestText("检测到门户失效（$context），尝试免密续期", 160))
            val ok = try {
                withContext(renewContext.minusKey(kotlinx.coroutines.Job)) {
                    currentCoroutineContext().ensureActive()
                    requireCurrent(ownerToken)
                    renewalFlight.run(ownerToken) {
                        requireCurrent(ownerToken)
                        if (credentials.accessToken() != rejectedToken) true
                        else if (ownedRenewer != null) ownedRenewer(ownerToken) else renewer!!.invoke()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (storage: HzcuException.Storage) {
                requireCurrent(ownerToken)
                throw storage
            } catch (failure: Throwable) {
                requireCurrent(ownerToken)
                renewLog(safeRequestText("免密续期抛异常（${failure.javaClass.simpleName}: ${failure.message}）", 160))
                false
            }
            currentCoroutineContext().ensureActive()
            requireCurrent(ownerToken)
            renewLog(if (ok) "续期成功，重试一次" else "续期未成功，按原样抛出")
            if (!ok) throw error
        }
    }

    private fun isPortalRead(path: String): Boolean = (
        path.startsWith(HzcuEndpoints.PORTAL_BROADCAST_DETAIL_PATH + "/") &&
            HzcuEndpoints.portalBroadcastDetailPath(path.substringAfterLast('/')) == path
        ) || path.substringBefore('?') in setOf(
        HzcuEndpoints.PORTAL_APP_GROUP_PATH, HzcuEndpoints.PORTAL_FREQUENT_APPS_PATH,
        HzcuEndpoints.PORTAL_APP_SEARCH_PATH, HzcuEndpoints.PORTAL_UNREAD_COUNT_PATH,
        HzcuEndpoints.PORTAL_TODO_COUNT_PATH, HzcuEndpoints.PORTAL_NEWS_GROUP_PATH,
        HzcuEndpoints.PORTAL_NEWS_LIST_PATH, HzcuEndpoints.PORTAL_MESSAGE_GROUP_PATH,
        HzcuEndpoints.PORTAL_MESSAGE_LIST_PATH, HzcuEndpoints.PORTAL_BROADCAST_LIST_PATH,
        "/api/app/billlist/listmybill", "/api/app/billlist/listmyprebill", "/api/app/billlist/listmyfinishbill",
    )

    /**
     * 门户 token 失效时的**免密续期**钩子，由组合根（`AppGraph`）注入
     * `HzcuCasService.renewWithExistingSession`。
     *
     * 做成可注入而不是让 client 直接依赖 CAS 服务：CAS 服务本身要用这个 client，
     * 互相持有就是构造期循环依赖（这个坑在别的模块踩过）。
     * 没注入时行为退回改动前 —— 失效就直接抛，不会更糟。
     */
    var renewSession: (suspend () -> Boolean)? = null
    var renewSessionForOwner: (suspend (Long?) -> Boolean)? = null
    private val renewalFlight = RequestSingleFlight<Long?, Boolean>()

    /** Optional dispatcher for renewal; the caller's cancellation is always retained. */
    var renewContext: kotlin.coroutines.CoroutineContext = Dispatchers.IO

    /**
     * 诊断出口。**数据/网络层不能直接调 `android.util.Log`** —— JVM 单测里它是
     * "not mocked"，会直接把测试打炸（这一条本项目早有纪律，是我上一轮加日志时破的）。
     * 默认打 logcat；单测注入收集器，顺便还能断言日志内容。
     */
    var renewLog: (String) -> Unit = { line -> android.util.Log.i("WanzaiRenew", line) }

    /**
     * 调试探针：**假装这次调用的 token 已失效**，用来在真机上验证「免密续期」这条新路径
     * 到底走不走得通（不这么做就得等学校真的让 token 过期，无法验收）。
     *
     * 由组合根注入（读 `Settings.Global`），**默认 null = 完全不影响生产路径**。
     * 返回 true 表示"这一次要假装失效"，实现方负责保证它**只生效一次**，
     * 否则会变成"永远续不上"的死循环假象。
     */
    var debugForceExpire: (() -> Boolean)? = null

    /**
     * 调试出口：把服务端**原样**回的正文落盘。
     *
     * ★ 它是为什么来的（2026-10-30）：「标已读」点完，界面上的未读点立刻消失，杀进程重开
     *   又全回来，看起来像服务端没记住。于是加这个钩子看服务端原话，而不是靠猜。
     *   钩子给出的答案是 **服务端记住了** —— `setmessageread/{id}` 回
     *   `{"code":200,"success":true,"message":"","data":"ok"}`，随后同一进程再取同一分组，
     *   那条的 `isread` 已经是 1（dev/PortalLabActivity 一次跑完「标 → 再取」，排除了两处
     *   读法不同的可能）。真正让界面看起来回退的是**分组顺序会变**：门户前后两次把
     *   「讲座通知」和「活动通知」排在第一位，同一个 y 坐标上已经是另一条消息。
     *   默认 null = 生产路径完全不变。
     */
    var debugDump: ((String, String) -> Unit)? = null

    private suspend fun portalPostOnce(
        path: String,
        fields: Map<String, String>?,
        jsonBody: JsonObject?,
        context: String,
        attachToken: Boolean,
        maxAttempts: Int?,
        ownerToken: Long?,
        readOnly: Boolean,
    ): HzcuResponse {
        requireCurrent(ownerToken)
        val token = if (attachToken) credentials.accessToken() else null
        // 探针放在取 token 之前：它要模拟的是「带着一个已经失效的 token 去打，服务端回 401」，
        // 而这条路径的下游（renewSession → 重试一次）和真实失效完全同一条。
        if (attachToken && catchingActive { debugForceExpire?.invoke() == true }.getOrDefault(false)) {
            throw HzcuException.LoginExpired(
                "调试：模拟门户 token 失效",
                details = "接口：$path（探针 wz_expire_probe）",
            )
        }
        if (attachToken && token.isNullOrEmpty()) {
            throw HzcuException.LoginExpired(
                "还没登录城院门户",
                details = "接口：$path\n缺少 ACCESS_TOKEN，未发出请求",
            )
        }

        // token **同时**放 header：门户实测「两种都过」，所以两条路一起给，
        // 哪条被服务端采用都能过。这不算多开泄漏面：请求头本来就只能出现在抓包里
        // （那同样能看到 body），而本项目所有日志出口只打「方法 + 脱敏 URI」。
        val headers = if (attachToken && token != null) mapOf("token" to token) else emptyMap()

        val request = if (jsonBody != null) {
            val body = buildJsonObject {
                for ((key, value) in jsonBody) put(key, value)
                if (attachToken && token != null) put("token", token)
            }
            HzcuRequest.portalJson(portalBaseUrl, path, body, headers)
        } else {
            val form = LinkedHashMap<String, String>()
            fields?.let { form.putAll(it) }
            if (attachToken && token != null) form["token"] = token
            HzcuRequest.portalForm(portalBaseUrl, path, form, headers)
        }

        val raw = send(request, context = context, maxAttempts = maxAttempts, ownerToken = ownerToken, retryRead = readOnly)
        // 调试版才装：服务端原样回了什么（含写操作，例如「标已读」到底成没成）。
        publish(ownerToken) { debugDump?.invoke(
            path.trim('/').replace('/', '_').ifEmpty { "root" },
            "HTTP " + raw.statusCode + "\n" + raw.body,
        )
        }
        requireCurrent(ownerToken)
        return unwrap(raw, context = context, path = path)
    }

    // ---------------------------------------------------------------------------
    // 拆壳
    // ---------------------------------------------------------------------------

    private fun unwrap(raw: HzcuRawResponse, context: String, path: String): HzcuResponse {
        // 跳转：我们把 followRedirects 关了，所以要自己判断这次 302 是什么意思。
        if (raw.isRedirect) {
            if (Redaction.locationIndicatesAuthenticationFailure(raw.location)) {
                throw HzcuException.LoginExpired(
                    "登录状态已失效，请重新登录",
                    details = "接口：$path\nHTTP：${raw.statusCode}（被重定向到统一认证页）",
                )
            }
            throw HzcuException.Parse(
                "接口被重定向（HTTP ${raw.statusCode}）",
                details = "接口：$path",
            )
        }

        val json = try {
            Json.parseToJsonElement(raw.body) as? JsonObject
        } catch (_: Exception) {
            null
        }

        if (json == null) {
            // 网关挂了 / 被 WAF 拦时返回的是 HTML。先分清「其实是没登录」，
            // 再报「响应不是 JSON」—— 前者要引导重新登录，后者只要重试。
            if (TronRequestPolicy.authenticationFailure(raw.statusCode, raw.body, raw.location)) {
                throw HzcuException.LoginExpired(
                    "登录状态已失效，请重新登录",
                    details = "接口：$path\nHTTP：${raw.statusCode}\n响应摘要：${Redaction.responseSummary(raw.body)}",
                )
            }
            throw HzcuException.Parse(
                "响应不是 JSON（HTTP ${raw.statusCode}）",
                details = "接口：$path\n响应摘要：${Redaction.responseSummary(raw.body)}",
            )
        }

        val code = json["code"].asIntOrNull() ?: raw.statusCode
        val success = json["success"].asBoolOrNull() ?: (code == HzcuEndpoints.CODE_OK)
        val message = safeRequestText(json["message"].asStringOrNull() ?: json["msg"].asStringOrNull() ?: "", 160)

        // 门户自己有一套 kickout 约定（被别处登录挤下线）。
        if (json["kickout"].asIntOrNull() == 1) {
            throw HzcuException.LoginExpired(
                message.ifEmpty { "账号已在其它设备登录，请重新登录" },
                details = "接口：$path\ncode：$code",
            )
        }

        if (raw.statusCode == HzcuEndpoints.CODE_UNAUTHORIZED || code == HzcuEndpoints.CODE_UNAUTHORIZED) {
            throw HzcuException.LoginExpired(
                message.ifEmpty { "登录状态已失效，请重新登录" },
                details = "接口：$path\nHTTP：${raw.statusCode}\ncode：$code",
            )
        }
        if (!success) {
            throw HzcuException.Api(
                code = code,
                message = message.ifEmpty { "接口返回失败（code=$code）" },
                details = "接口：$path\ncode：$code",
            )
        }

        return HzcuResponse(
            code = code,
            success = true,
            message = message,
            data = json["data"],
            raw = json,
        )
    }

    // ---------------------------------------------------------------------------
    // 底层发送（限流 + 重试）
    // ---------------------------------------------------------------------------

    /**
     * 唯一真正发出网络请求的地方。**必须在限流闸门之内执行。**
     *
     * [maxAttempts] 是单次调用的尝试上限覆盖，**只允许收紧**。
     * CAS 表单提交**必须传 1**：POST 带凭据时「响应丢了」不等于「请求没到」，
     * 自动重试会把同一份密码再发一次。若密码是错的，账号失败次数就翻倍 ——
     * 校方可按失败次数锁号（docs/BLOCKED.md 明文风险）。宁可让用户手动重试，
     * 也不由程序替他多打一次凭据。
     *
     * 供 CAS 登录等外部流程复用同一个闸门与重试策略。
     * 外部拿不到 OkHttpClient，只能表达「发这个请求」。
     */
    suspend fun send(
        request: HzcuRequest,
        context: String = "hzcu",
        maxAttempts: Int? = null,
        ownerToken: Long? = epoch?.token(),
        retryRead: Boolean = false,
    ): HzcuRawResponse {
        val readOnly = request.method == "GET" || request.method == "HEAD" || retryRead
        val attempts = if (readOnly) (maxAttempts ?: this.maxAttempts).coerceIn(1, this.maxAttempts) else 1
        var lastError: HzcuException? = null

        for (attempt in 1..attempts) {
            currentCoroutineContext().ensureActive()
            requireCurrent(ownerToken)
            if (attempt > 1) {
                // 退避在闸门**之外**、限流在闸门**之内**：等待时间不占用别人的名额，
                // 而重试的那一次仍然要重新排队，QPS 红线不被重试打穿。
                delay(backoffFor(attempt))
            }
            try {
                return rateLimiter.gate(request.rateLimitKey) {
                    currentCoroutineContext().ensureActive()
                    requireCurrent(ownerToken)
                    open(request, ownerToken).also { requireCurrent(ownerToken) }
                }
            } catch (error: HzcuException) {
                requireCurrent(ownerToken)
                // 认证类失败重试没有意义（还可能触发风控），直接上抛。
                if (error.isAuthFailure || !error.isRetryable) throw error
                lastError = error
            }
        }

        throw lastError ?: HzcuException.Transport("未知失败")
    }

    private fun backoffFor(attempt: Int): Long {
        val factor = 1L shl (attempt - 2).coerceIn(0, 4)
        return backoffBaseMs * factor
    }

    private suspend fun open(request: HzcuRequest, ownerToken: Long?): HzcuRawResponse {
        val builder = Request.Builder()
            .url(request.url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Charset", "UTF-8")
        for ((name, value) in request.headers) builder.header(name, value)
        builder.method(request.method, encodeBody(request))

        val scopedClient = httpClient.newBuilder()
            .cookieJar(cookieJar.forRequest(epoch, ownerToken))
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val call = scopedClient.newCall(builder.build())
        return try {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                requireCurrent(ownerToken)
                execute(call).use { response ->
                    // 先取元数据再读正文：`string()` 会把 body 关掉。
                    // OkHttp 5 起 `Response.body` 是非空的，所以这里不用也无从做空判断。
                    val body = response.body
                    val contentType = body.contentType()?.toString()
                    val text = body.string()
                    currentCoroutineContext().ensureActive()
                    requireCurrent(ownerToken)

                    val raw = HzcuRawResponse(
                        statusCode = response.code,
                        body = text,
                        requestUri = request.url,
                        location = response.header("Location"),
                        contentType = contentType,
                    )
                    // 401 刻意不在这里抛：它要么是「没登录」要么是门户的
                    // `token 不能为空`，两种都由 unwrap 归类成 LoginExpired，
                    // 顺便把服务端的中文原话带上去。
                    if (response.code >= 400 && response.code != HzcuEndpoints.CODE_UNAUTHORIZED) {
                        throw HzcuException.HttpStatus(
                            statusCode = response.code,
                            requestUri = request.url,
                            summary = Redaction.responseSummary(text),
                        )
                    }
                    raw
                }
            }
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        } catch (error: HzcuException) {
            requireCurrent(ownerToken)
            throw error
        } catch (error: SocketTimeoutException) {
            currentCoroutineContext().ensureActive()
            requireCurrent(ownerToken)
            throw HzcuException.RequestTimeout(cause = error)
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            requireCurrent(ownerToken)
            // 消息里可能带完整 URL（含 query），所以只保留固定文案 + 异常类型。
            throw HzcuException.Transport(
                details = "异常类型：${error.javaClass.simpleName}",
                cause = error,
            )
        }
    }

    private suspend fun execute(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    /**
     * 表单/JSON 编码。token 只在返回的字节里，不进任何日志。
     *
     * 空 body 的 POST 显式给 0 长度而不是走 chunked：
     * 某些 servlet 容器会拒掉 chunked 的空 POST（CAS 的某些跳转就是空 body POST）。
     */
    private fun encodeBody(request: HzcuRequest): RequestBody? = when {
        request.jsonBody != null -> request.jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
        request.fields != null -> {
            val form = FormBody.Builder()
            for ((key, value) in request.fields) form.add(key, value)
            form.build()
        }
        request.method == "POST" -> ByteArray(0).toRequestBody(FORM_MEDIA_TYPE)
        else -> null
    }
}
