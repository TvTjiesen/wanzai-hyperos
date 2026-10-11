package com.muyao.wanzaihyperos.net

import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 请求描述。**只描述「做什么」，不碰「怎么发」**，所以没有绕过限流的口子。
 *
 * ★ 注意这里**没有** `attachToken` 这种东西：token 由 [HzcuClient.portalPost]
 * 在发请求前从会话里取出、塞进 fields/body。
 * 把「要不要带 token」做成请求对象上的开关，会误导后来人以为「客户端会自动加」，
 * 于是忘记自己传，线上就变成一片 401。**加 token 只有一条路**，就是 portalPost 那条。
 *
 * 移植自 Flutter 蓝本 `lib/http/hzcu/hzcu_client.dart` 的 `HzcuRequest`。
 */
class HzcuRequest private constructor(
    val url: String,
    val method: String,
    val fields: Map<String, String>?,
    val jsonBody: JsonObject?,
    val headers: Map<String, String>,
) {

    /** URL 里的纯路径部分，用于限流与日志。解析失败时退化成整串（宁可多限一点）。 */
    private val path: String = runCatching { URI(url).rawPath ?: url }.getOrDefault(url)

    /**
     * 限流器用的接口身份。**去掉 `;jsessionid=…`**：
     * CAS 的 URL 重写会让同一个接口每次换一个新 path
     * （`/cas/login;jsessionid=AAA` vs `/cas/login;jsessionid=BBB`），
     * 直接拿 path 当 key，「同接口 ≥2s」就被 jsessionid 绕开了。
     * QPS≤1 的全局闸门始终兜底，但 per-path 这层也必须算对。
     */
    val rateLimitKey: String
        get() {
            val semicolon = path.indexOf(';')
            return if (semicolon < 0) path else path.substring(0, semicolon)
        }

    /**
     * 日志里能出现的形态：只有方法和脱敏后的地址。
     * 凭据在 form body / JSON body 里，不进 URI，所以脱敏地址不含敏感信息。
     */
    val logShape: String get() = "$method ${Redaction.sanitizedRequestUri(url)}"

    /** 调试用；**不含 body**。 */
    override fun toString(): String = "HzcuRequest($logShape)"

    companion object {
        /**
         * 门户接口：POST form。
         *
         * 默认包体类型为 `application/x-www-form-urlencoded`：
         * `listappinfogroup`、`getmessagenoreadcount`、`listnewsgroup` 都吃这种。
         */
        fun portalForm(
            base: String,
            path: String,
            fields: Map<String, String>? = null,
            headers: Map<String, String> = emptyMap(),
        ): HzcuRequest = HzcuRequest(
            url = base.trimEnd('/') + path,
            method = "POST",
            fields = fields?.toMap() ?: emptyMap(),
            jsonBody = null,
            headers = headers,
        )

        /**
         * 门户接口：POST JSON。
         *
         * 实测 `listnewslist` 与 `listmessage` **不吃 form**，用 form 打过去服务端回
         * `code=400 message="Content type 'application/x-www-form-urlencoded;charset=UTF-8' not supported"`。
         * 同一批接口里两种类型并存，所以**必须由调用方按接口选**。
         */
        fun portalJson(
            base: String,
            path: String,
            body: JsonObject,
            headers: Map<String, String> = emptyMap(),
        ): HzcuRequest = HzcuRequest(
            url = base.trimEnd('/') + path,
            method = "POST",
            fields = null,
            jsonBody = body,
            headers = headers,
        )

        /**
         * 任意 URL（CAS 走的是 `ca.hzcu.edu.cn`，不在门户域）。
         *
         * URI **原样使用**：CAS 页面 action 里的 `;jsessionid=...` 是 URL 重写的会话凭据，
         * 必须照抄提交，所以这里绝不做任何「清理」。
         */
        fun url(
            url: String,
            method: String = "GET",
            fields: Map<String, String>? = null,
            jsonBody: JsonObject? = null,
            headers: Map<String, String> = emptyMap(),
        ): HzcuRequest = HzcuRequest(
            url = url,
            method = method,
            fields = fields,
            jsonBody = jsonBody,
            headers = headers,
        )
    }
}

/**
 * 底层收发用的原始响应。给 CAS 登录这类需要自己读跳转链的场景用。
 *
 * **刻意不持有 OkHttp 的 `Response` / `Headers` / cookie 列表**：
 * `Set-Cookie` 里是 `CASTGC` / `JSESSIONID` / `route` 这类会话凭据，
 * 一旦被塞进响应对象，后面谁写日志、谁把它拼进异常 details，都是随手就能泄漏。
 * 所以这里只在此刻抽出「安全且够用」的字段。
 */
class HzcuRawResponse(
    val statusCode: Int,
    val body: String,
    val requestUri: String,
    /** 跳转目标。只有 CAS 链路读它。**query 里可能有 ticket，禁止直接打印。** */
    val location: String?,
    /** 只存 MIME 类型文本（`application/json;charset=UTF-8`），不含凭据。 */
    val contentType: String?,
) {
    val isRedirect: Boolean get() = statusCode in REDIRECT_CODES

    /** 日志里能出现的形态。URI 的 query 可能带 ticket，必须脱敏。 */
    val logShape: String get() = "$statusCode ${Redaction.sanitizedRequestUri(requestUri)} $contentType"

    /**
     * ⚠️ **刻意不输出 body**。
     *
     * 门户返回里可能带姓名、学号，CAS 页面里可能带表单，任何一处字符串插值
     * 都会把它们带进日志。要读正文请显式访问 [body]，让意图在代码里可见。
     */
    override fun toString(): String = "HzcuRawResponse($logShape)"

    companion object {
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * 一次门户调用的结果：**已经拆过** `{code, success, message, data}` 外壳。
 *
 * 移植自蓝本 `HzcuResponse`，并补上了 [rows] / [listPayload] 两个成员 ——
 * 蓝本里这两个字段的差异散落在各个 repository 的 `json['rows']` 里，
 * 每接一个新接口都要重新踩一次「读了 data，静默拿到 null」的坑。
 */
class HzcuResponse(
    val code: Int,
    val success: Boolean,
    val message: String,
    /** 信封的 `data` 字段。可能是对象、数组，也可能是 null（消息族就是 null）。 */
    val data: JsonElement?,
    /** 整个信封原样保留，供读取 `rows`、`total` 这类顶层字段。 */
    val raw: JsonObject,
) {
    val asList: JsonArray? get() = data.asArrayOrNull()
    val asMap: JsonObject? get() = data.asObjectOrNull()

    /**
     * 信封**顶层**的 `rows`。
     *
     * 实测（2026-10-10）：消息族 `listmessage` 与搜索族 `appinfolist`
     * 把数组放在顶层 `rows`，而 `data` 是 `null`。
     * 用 `data` 读会**静默**拿到 null —— 不报错，只表现为「没有消息」。
     */
    val rows: JsonArray? get() = raw["rows"].asArrayOrNull()

    /**
     * 「我要列表」的统一入口：优先顶层 `rows`，其次 `data`。
     *
     * 把两族接口的差异**收在这一处**，业务代码不必知道自己在跟哪一族打交道。
     */
    val listPayload: JsonArray? get() = rows ?: asList

    /** 信封顶层的 `total`。⚠️ 实测不可靠（见过 `total=9` 而实际 10 行），**翻页别信它**。 */
    val total: Int? get() = raw["total"].asIntOrNull()

    /** 只输出形状，不输出内容。 */
    override fun toString(): String =
        "HzcuResponse(code=$code, success=$success, message=${Redaction.redact(message)}, " +
            "data=${describeShape(data)})"

    companion object {
        private fun describeShape(element: JsonElement?): String = when (element) {
            null -> "null"
            is JsonArray -> "array(${element.size})"
            is JsonObject -> "object(${element.size})"
            else -> "scalar"
        }
    }
}
