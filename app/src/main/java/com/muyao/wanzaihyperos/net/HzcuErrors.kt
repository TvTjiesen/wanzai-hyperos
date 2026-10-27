package com.muyao.wanzaihyperos.net

/**
 * 城院网络层的错误模型。**唯一一份** —— 别处不许各写一套 try/catch 文案。
 *
 * 移植自 Flutter 蓝本 `lib/zjuServices/exceptions.dart` 的异常层级，
 * 但改成了 sealed class：蓝本靠继承关系 + `is` 判断分散在多个地方，
 * 漏掉一个分支就变成「网络错误被当成登录失效」这种最伤用户的误判
 * （会把用户从已登录状态踢到登录页）。Kotlin 的 `when` 穷尽检查
 * 让编译器替我们盯住这件事。
 *
 * 三条纪律：
 * 1. **只带摘要，不带正文**（[HttpStatus] 只存 [HttpStatus.summary]）；
 * 2. **只带错误码，不带消息**（[Storage]，存储层异常消息可能回显存储值）；
 * 3. [isRetryable] 必须显式列出每个分支 —— 认证失败重试只会把账号打锁。
 */
sealed class HzcuException(
    message: String,
    val details: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * **诊断用人话**：把「被压成一句文案」的那层底下真正的异常带出来。
     *
     * 为什么需要它：`asPortalException()` / `asCjh52Failure()` / 各仓库的
     * `HzcuException.Parse("签到数据解析失败", cause = it)` 都会把原始异常收进 [cause]，
     * 而界面只渲染 `message` —— 真机上就只能看到「解析失败」四个字，
     * 到底是 401、超时、还是 JSON 结构变了完全看不出来；数据层要能跑 JVM 单测，
     * 也不能随便 `android.util.Log`。本轮排查消息／新聞／签到三处报错就是卡在这一步。
     *
     * 做成成员而不是顶层扩展函数：八个渲染点各加一行 import 太容易漏，
     * 漏一处就变成「一半的错误诚实、一半不诚实」，比全不诚实更难查。
     *
     * 纪律：只带**异常类名 + message + 已有的 details**，绝不带请求正文、URL、凭据；
     * 长度截断，避免把整段 HTML 错误页糊到卡片上。
     */
    val userText: String
        get() {
            var deepest: Throwable = this
            var guard = 0
            while (deepest.cause != null && deepest.cause !== deepest && guard < 8) {
                deepest = deepest.cause!!
                guard++
            }
            val inner = deepest.message?.take(160)
            val head = message?.takeIf { it.isNotBlank() } ?: "请求失败"
            val tail = if (deepest === this && inner.isNullOrBlank()) ""
            else " · ${deepest.javaClass.simpleName}${inner?.let { ": $it" } ?: ""}"
            return head + tail + (details?.take(120).let { if (it.isNullOrBlank()) "" else " · $it" })
        }

    /**
     * 服务端明确要求重新认证（HTTP 401 / 信封 code=401 / 正文说自己没登录）。
     *
     * ⚠️ 与「网络不通」严格区分：这个要引导重新登录，
     * 而网络不通**绝不能**把用户判成未登录（计划 §T3.2 的验收项之一）。
     */
    class LoginExpired(
        message: String,
        details: String? = null,
        cause: Throwable? = null,
    ) : HzcuException(message, details, cause)

    /**
     * 业务失败：信封解出来了，但 `success=false` 且不是 401。
     * `message` 用服务端给的中文原文 —— 学校写的「该生无此权限」比我们翻译的准。
     */
    class Api(
        val code: Int,
        message: String,
        details: String? = null,
        cause: Throwable? = null,
    ) : HzcuException(message, details, cause)

    /**
     * HTTP 4xx / 5xx。**刻意不持有正文**，正文可能含姓名、学号，
     * 异常一旦被日志或崩溃上报抓走就是泄漏。只留状态码 + 脱敏 URI + 结构摘要。
     */
    class HttpStatus(
        val statusCode: Int,
        requestUri: String,
        val summary: String,
    ) : HzcuException(
        "HTTP $statusCode ${Redaction.sanitizedRequestUri(requestUri)}；响应摘要 $summary",
    )

    /** 连不上 / DNS 失败 / TLS 失败 / 连接被重置。**不是**登录失效。 */
    class Transport(
        message: String = "网络连接失败",
        details: String? = null,
        cause: Throwable? = null,
    ) : HzcuException(message, details, cause)

    /** 超时。与 [Transport] 分开，因为「重试」对超时的收益更高。 */
    class RequestTimeout(
        message: String = "请求超时",
        details: String? = null,
        cause: Throwable? = null,
    ) : HzcuException(message, details, cause)

    /** 响应不是期望的 JSON，或结构对不上。 */
    class Parse(
        message: String,
        details: String? = null,
        cause: Throwable? = null,
    ) : HzcuException(message, details, cause)

    /**
     * 统一身份认证**明确**说这次没通过（密码错 / 账号被锁）。
     *
     * ★ 与 [LoginExpired] 严格分开：
     * - [LoginExpired] 是「你之前登录过，现在过期了」→ 引导重新登录即可；
     * - 本类是「这次认证本身被拒」→ 必须让用户重敲，**且绝不能自动重试**
     *   （重试等于拿同一个错密码反复敲门，校方可按失败次数锁号）。
     */
    class AuthenticationRejected(
        message: String,
        details: String? = null,
        cause: Throwable? = null,
    ) : HzcuException(message, details, cause)

    /**
     * CAS 链路走完了，但所有探针都没能取出 `ACCESS_TOKEN`。
     *
     * **这不代表密码错** —— CAS 那边很可能已经认证成功（302 都给了），
     * 只是「token 从哪取」这一环还没被证实。登录页必须把它和「密码错误」分开显示：
     * 前者要按链条改代码，后者只需要重敲。
     *
     * [chain] 是**已脱敏**的跳转链，可以安全地展示与记录。
     */
    class TokenSourceUnverified(
        val chain: List<String>,
        cause: Throwable? = null,
    ) : HzcuException(
        message = "统一身份认证已通过，但没能取到门户令牌",
        details = buildString {
            append("这不是密码错误，是“token 从哪取”这一环尚未证实。\n跳转链（已脱敏，无凭据）：")
            chain.forEach { append('\n').append(it) }
        },
        cause = cause,
    )

    /**
     * 加密存储读写失败（Keystore 密钥失效、ROM 异常……）。
     * **只带 op 与错误码**：`KeyStoreException.message` 这类文本可能回显存储值。
     */
    class Storage(val op: String, val code: String) :
        HzcuException("secure storage $op 失败：$code")
}

/** 是不是「要重新登录」这一类。 */
val HzcuException?.isAuthFailure: Boolean
    get() = this is HzcuException.LoginExpired

/**
 * 该不该自动重试。
 *
 * 注意 [HzcuException.LoginExpired] 与 [HzcuException.Api] 都返回 false：
 * - 认证失败重试 = 拿同一个坏凭据反复敲门，校方可按失败次数锁号（docs/BLOCKED.md）；
 * - 业务失败是确定性的，重试只会得到同一句话。
 */
val HzcuException?.isRetryable: Boolean
    get() = when (this) {
        null -> false
        is HzcuException.LoginExpired -> false
        is HzcuException.Api -> false
        is HzcuException.Parse -> false
        is HzcuException.Storage -> false
        // ★ 认证被拒**绝不能重试**：拿同一个错密码反复敲门，
        //   校方可按失败次数锁号（docs/BLOCKED.md 明文风险）。
        is HzcuException.AuthenticationRejected -> false
        // token 来源未证实是「代码要改」，重试只会再走一遍同样的链路。
        is HzcuException.TokenSourceUnverified -> false
        is HzcuException.HttpStatus -> statusCode >= 500 || statusCode == 429
        is HzcuException.Transport -> true
        is HzcuException.RequestTimeout -> true
    }

/**
 * 给界面看的短文案。**永远不包含凭据、URL query 或服务端正文。**
 *
 * 与 [HzcuException.details] 分工：`shortErrorText` 上界面，
 * `details` 只进诊断日志（且同样已脱敏）。
 */
fun Throwable?.shortErrorText(): String = when (this) {
    null -> "未知错误"
    is HzcuException -> message?.takeIf { it.isNotBlank() } ?: "请求失败"
    is java.net.SocketTimeoutException -> "请求超时"
    is java.net.UnknownHostException -> "网络连接失败"
    is java.io.IOException -> "网络连接失败"
    is kotlinx.serialization.SerializationException -> "返回数据格式异常"
    is IllegalStateException -> "请求状态异常"
    else -> "请求失败"
}
