package com.muyao.wanzaihyperos.net

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 城院网络层的**唯一脱敏出口**。所有要进日志、异常、界面的文本都必须先过这里。
 *
 * 红线（计划 §12.1）：密码、ACCESS_TOKEN、tempcode、CAS 会话 Cookie
 * **只允许**存在于内存与加密存储里；日志、异常详情、诊断输出一律不得出现值本身。
 *
 * 本文件移植自 Flutter 蓝本 `lib/zjuServices/exceptions.dart` 的
 * `redactSensitive` / `sanitizedRequestUri`，并修掉了蓝本里的一处泄漏（见下）。
 *
 * ### 相对蓝本的**有意改进**：URL 里的 `;jsessionid=…`
 * 蓝本的 `sanitizedRequestUri` 原样保留 path，而 CAS 走 URL 重写时会产出
 * `/cas/login;jsessionid=AAA…`。`redactSensitive()` 确实会先把它替换掉，但
 * `HzcuRequest.logShape` 是**直接**调 `sanitizedRequestUri` 的，没过 `redact` ——
 * 于是每次 CAS 请求的日志里都躺着一个可复用的会话 ID。
 * 这里把 `;jsessionid=…` 的剥离放进 [sanitizedRequestUri] 本身，
 * 让它成为**任何调用路径**都逃不掉的默认行为。
 */
object Redaction {

    const val HIDDEN = "<已隐藏>"
    const val HIDDEN_URL = "<已隐藏 URL>"
    const val UNKNOWN_URI = "<未知 URI>"

    /** 请求头类凭据：整行吃掉（值可能带空格，所以匹配到行尾）。 */
    private val headerPattern = Regex(
        """(authorization|proxy-authorization|cookie|set-cookie)\s*[:=]\s*[^\r\n]+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 字段类凭据：吃到分隔符为止，避免把后面的正常内容一起吞掉。
     *
     * ★ 排除集里必须有 `&`：form body 形如 `password=x&username=y`，
     * 少一个 `&` 就会把**后面整个字段**一起抹掉，诊断日志因此失去定位能力
     * （Flutter 蓝本的 `[^;,\s]+` 就有这个毛病，这里修掉了）。
     */
    private val credentialPattern = Regex(
        """(password|passwd|token|ticket|synjones-auth|iplanetdirectorypro|jsessionid)\s*[:=]\s*[^;,\s&]+""",
        RegexOption.IGNORE_CASE,
    )

    private val urlPattern = Regex("""https?://[^\s]+""")

    /**
     * URL 重写形式的路径参数 `;jsessionid=…`。
     * 它不是查询参数，`URI.rawPath` 会把它算进 path，所以必须单独剥。
     */
    private val jsessionIdPathParam = Regex(""";jsessionid=[^/?#]*""", RegexOption.IGNORE_CASE)

    /**
     * 把一段任意文本里的凭据与 URL 全部抹掉。**这是唯一可以打印的自由文本入口。**
     *
     * ★ 顺序：**先收 URL，再收字段**。
     *
     * Flutter 蓝本是反过来的（先字段、后 URL），结果是字段规则先把
     * `?ticket=ST-9` 换成 `?ticket=<已隐藏>`，而那串带 `<` 的文本**已经不是合法 URI**，
     * 于是 URL 规则拿到一个解析不了的串，只能整段丢掉 —— 诊断信息没了，
     * 而且丢不丢还取决于正则的实现细节。
     * 先收 URL 就没这个问题：query 反正整段不要，一步到位得到干净地址。
     */
    fun redact(value: String): String {
        var result = urlPattern.replace(value) { match -> sanitizedRequestUri(match.value) }
        result = headerPattern.replace(result) { match -> "${match.groupValues[1]}=$HIDDEN" }
        result = credentialPattern.replace(result) { match -> "${match.groupValues[1]}=$HIDDEN" }
        return result
    }

    /**
     * 只保留 `scheme://host[:port]/path`，**丢弃整个 query**。
     *
     * query 里可能有 ticket / code / service，path 里可能有 `;jsessionid`，
     * 三者都是会话凭据。保留 host 与 path 已经足够定位「打的是哪个接口」。
     *
     * ⚠️ 必须有**退化路径**：真实日志里的「URL」常常带中文、尖括号等非法字符
     * （例如某个参数值已经是 `<已隐藏>`），`java.net.URI` 会直接抛。
     * 那种情况下不能整段丢掉 —— 那等于把 host 和 path 也一起丢了，
     * 而这恰恰是排障时唯一有用的信息。
     */
    fun sanitizedRequestUri(uri: String?): String {
        if (uri == null) return UNKNOWN_URI
        val parsed = runCatching { URI(uri) }.getOrNull()
        if (parsed != null && parsed.scheme != null && parsed.host != null) {
            return buildString {
                append(parsed.scheme).append("://").append(parsed.host)
                if (parsed.port != -1) append(':').append(parsed.port)
                append(jsessionIdPathParam.replace(parsed.rawPath ?: "", ""))
            }
        }
        return tolerantSanitize(uri)
    }

    /**
     * URI 解析失败时的兜底：手工切出 `scheme://authority[/path]`，丢掉 query 与 fragment。
     *
     * authority 里 `@` 之前的部分（`user:password@host` 形式）一律丢掉 ——
     * 那是 URL 内嵌凭据，属于最典型的泄漏点。
     */
    private fun tolerantSanitize(uri: String): String {
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd <= 0) return HIDDEN_URL
        val scheme = uri.substring(0, schemeEnd)
        val rest = uri.substring(schemeEnd + 3)

        var authorityEnd = rest.length
        for (index in rest.indices) {
            val ch = rest[index]
            if (ch == '/' || ch == '?' || ch == '#') {
                authorityEnd = index
                break
            }
        }
        var authority = rest.substring(0, authorityEnd)
        val at = authority.lastIndexOf('@')
        if (at >= 0) authority = authority.substring(at + 1)
        if (authority.isEmpty()) return HIDDEN_URL

        var path = rest.substring(authorityEnd)
        val cut = path.indexOfFirst { it == '?' || it == '#' }
        if (cut >= 0) path = path.substring(0, cut)
        path = jsessionIdPathParam.replace(path, "")

        return "$scheme://$authority$path"
    }

    /**
     * 响应正文的**结构摘要**。绝不复制正文内容 ——
     * 门户返回里可能带姓名、学号、手机号，CAS 页面里可能带表单。
     *
     * 只回答三个问题：是 JSON 还是 HTML、有几个字段、有没有我们关心的键。
     */
    fun responseSummary(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "<空响应>"

        // ★ HTML 必须**先判**。实测 `Json.parseToJsonElement("<html>")` 不会抛异常，
        // 而是返回一个 JsonPrimitive（树解析器把裸词当字符串收下了），
        // 于是网关的登录页会被摘要成「JSON标量」—— 既看不出是 HTML，
        // 也让 `bodyIndicatesAuthenticationFailure` 那条线索被埋掉。
        // 真正的 JSON 一定以 `{` `[` `"` 数字或 true/false/null 开头，不会以 `<` 开头。
        if (looksLikeHtml(trimmed)) return "<HTML响应；长度=${trimmed.length}>"

        return try {
            when (val element = Json.parseToJsonElement(trimmed)) {
                is JsonObject -> {
                    val common = COMMON_KEYS.filter { element.containsKey(it) }
                    "<JSON对象；字段数=${element.size}；" +
                        "常见字段=${if (common.isEmpty()) "无" else common.joinToString(",")}>"
                }
                is JsonArray -> "<JSON数组；条目数=${element.size}>"
                is JsonPrimitive ->
                    when {
                        element.isString -> "<JSON字符串；长度=${element.content.length}>"
                        isJsonLiteral(element.content) -> "<JSON标量>"
                        // 既不是字符串也不是 JSON 字面量 → 其实是纯文本被树解析器收下了。
                        else -> "<文本响应；长度=${trimmed.length}>"
                    }
            }
        } catch (_: Exception) {
            "<文本响应；长度=${trimmed.length}>"
        }
    }

    /** `true` / `false` / `null` / 数字，才算真的 JSON 标量。 */
    private fun isJsonLiteral(content: String): Boolean {
        val text = content.trim()
        return text == "true" || text == "false" || text == "null" ||
            text.toDoubleOrNull() != null
    }

    private val COMMON_KEYS = listOf(
        "code", "status", "success", "message", "msg", "data", "result", "items", "rows",
    )

    /**
     * 正文长得像登录页吗。用于把「其实是没登录」和「服务端真的挂了」分开 ——
     * 前者要引导用户重新登录，后者要提示重试，混起来会让用户白等。
     */
    fun looksLikeHtml(body: String): Boolean {
        val normalized = body.trimStart().lowercase()
        return normalized.startsWith("<!doctype html") ||
            normalized.startsWith("<html") ||
            normalized.contains("<body") ||
            normalized.contains("<form")
    }

    /**
     * 正文是不是在说「你没登录」。
     *
     * 移植自蓝本 `bodyIndicatesAuthenticationFailure`。注意这里包含
     * `410 Gone` 之类网关页面的典型文案（`authserver`、`统一身份认证`），
     * 因为学校网关在会话过期时正是回这些。
     */
    fun bodyIndicatesAuthenticationFailure(body: String): Boolean {
        val normalized = body.lowercase()
        return AUTH_FAILURE_MARKERS.any { normalized.contains(it) }
    }

    /**
     * 跳转目标是不是在把我们往认证页赶。
     *
     * 我们把 OkHttp 的自动跳转关掉了（CAS 要逐跳读 `Location`，ticket 就在里面），
     * 所以「门户把业务请求 302 到登录页」这件事必须由我们自己识别，
     * 否则会退化成「响应不是 JSON」，把「你没登录」误报成「服务端坏了」。
     *
     * ⚠️ 只看 host 与路径关键词，**绝不读 query**（`service=` / `ticket=` 都是凭据）。
     */
    fun locationIndicatesAuthenticationFailure(location: String?): Boolean {
        if (location == null) return false
        val normalized = location.lowercase()
        return normalized.contains("cas/login") ||
            normalized.contains("identity.hzcu.edu.cn") ||
            normalized.contains("auth/realms/zju") ||
            normalized.contains("authserver")
    }

    private val AUTH_FAILURE_MARKERS = listOf(
        "cas/login",
        "login_ssologin",
        "统一身份认证",
        "未登录",
        "请先登录",
        "登录已失效",
        "认证失败",
        "unauthorized",
        "authserver",
        "\"kickout\":1",
        "\"kickout\":\"1\"",
        "kickout=1",
        "token 不能为空",
    )
}
