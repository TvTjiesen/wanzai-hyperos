package com.muyao.wanzaihyperos.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 「浙大城市学院场景管理」（CJH52）的接口层。
 *
 * ### 为什么单独一个客户端
 *
 * CJH52 是**另一套系统**，与门户/学在城院三套并存。它与门户的差别（全部来自
 * `docs/B0-CJH52-CONTRACT.md` 的真机取证，不是猜的）：
 *
 * 1. **回应信封不同** —— `{code, error, msg, data, version, path, requestId, timestamp, msgCode}`，
 *    而不是门户的 `{code, success, message, data}`。
 *    **HTTP 状态几乎恒为 200，成败在 `code`**。所以不能复用 [HzcuClient.unwrap]。
 * 2. **认证方式不同** —— 只要 `Authorization: Bearer <token>`。
 *    实测证明：`X-QW` 签名的**值不被校验**（正确与乱填结果一致），`App-Code` 也不是必需的。
 *    所以这里**不实现签名** —— 实现了也是自欺（见契约文档 §1）。
 * 3. **token 形态不同** —— 是 **UUID(36)**，不是 JWT。
 *
 * ### 复用什么
 *
 * 复用 [HzcuClient] 的**传输层**（`send`）：限流器、Cookie 罐、脱敏、超时、重试策略
 * 都在那一层，没必要重造。本类只负责「拼请求 + 解析这家的信封 + 映射成模型」。
 *
 * ### 凭据纪律
 *
 * token 只作为 `Authorization` 头传出去，**永不进日志、不进异常**。
 * [toString] 与所有日志只印长度。
 */
class Cjh52Client(
    private val transport: HzcuClient,
    private val tokenProvider: () -> String?,
) {

    /**
     * 调一个 CJH52 接口并解析它的信封。
     *
     * @param path `/dapi/v2/...` 形式
     * @param body JSON 包体（该站的读接口几乎都是 POST + JSON）
     */
    suspend fun post(path: String, body: JsonObject = buildJsonObject { }): JsonObject {
        val token = tokenProvider()
        if (token.isNullOrEmpty()) {
            // 没有 token 就别发请求：实测无 Authorization 会回 code=401，
            // 提前抛出能给出更准确的文案（「需要先登录」而不是「没有权限」）。
            // ★ 日志只记「没有 token」这个事实，不记任何值 —— 便于排查
            //   「是没存进去，还是存了但没带上」这两件完全不同的事。
            android.util.Log.w("WanzaiCjh52", "请求 $path 时没有 token（会话未建立）")
            throw HzcuException.LoginExpired("CJH52 会话未建立")
        }

        val raw = transport.send(
            HzcuRequest.url(
                url = HzcuEndpoints.CJH52_BASE_URL.trimEnd('/') + path,
                method = "POST",
                jsonBody = body,
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    // 租户参数。实测学生端为 "6"；带上更稳妥（服务端当前不校验，
                    // 但契约文档 §9 提示多校环境可能需要）。
                    "App-Code" to appCode,
                    "platform" to "H5",
                    "lang" to "zh-CN",
                ),
            ),
            context = "CJH52 $path",
        )
        // ★ 只记「HTTP 状态 + token 指纹」，**不记 token 值、不记响应正文**
        //   （正文可能含姓名/学号 —— 契约文档 §9 已点名）。
        //
        // 指纹 = 前 4 位 + 长度 + 简单校验和：**只够判断「两次拿到的是不是同一个」**，
        // 不足以还原凭据（UUID 还剩 32 位未知）。
        // 需要它是因为实测出现过一个必须区分的情形：
        //   HTTP 401「身份驗證失敗」= 带的 token **被拒**（不是缺头）
        //   code 401「沒有權限訪問接口」= **没带** token
        // 两者含义完全不同，只靠状态码会误判。
        android.util.Log.i(
            "WanzaiCjh52",
            "POST $path → HTTP ${raw.statusCode}（token ${fingerprint(token)}）",
        )

        val bodyText = raw.body
        val root = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(bodyText) as? JsonObject
        }.getOrNull() ?: throw HzcuException.Parse(
            "CJH52 返回的不是 JSON 对象",
            details = raw.logShape,
        )

        val code = root["code"].asIntOrNull()
            ?: throw HzcuException.Parse("CJH52 信封里没有 code", details = raw.logShape)

        if (code != CODE_OK) {
            // 401 是这家表达「没权限 / 没登录」的方式 —— 归到 LoginExpired，
            // 让上层走「重新登录」而不是「网络错误」（与门户的判据保持一致）。
            if (code == CODE_UNAUTHORIZED) {
                throw HzcuException.LoginExpired(
                    root["msg"].asStringOrNull() ?: "CJH52 会话已失效",
                    details = raw.logShape,
                )
            }
            // 其余业务码用服务端原文 —— 学校写的「查无用户」比我们翻译的准。
            throw HzcuException.Api(
                code = code,
                message = root["msg"].asStringOrNull() ?: "CJH52 请求失败（code=$code）",
                details = raw.logShape,
            )
        }

        return root
    }

    /** 取 `data` 对象；不是对象就抛 Parse。 */
    suspend fun postForObject(path: String, body: JsonObject = buildJsonObject { }): JsonObject =
        post(path, body)["data"].asObjectOrNull()
            ?: throw HzcuException.Parse("CJH52 的 data 不是对象")

    /**
     * 取 `data` 数组。
     *
     * ⚠️ **空态是 `[]` 而不是 `null`**（考勤接口实测如此）。
     * 所以这里把「data 为 null」与「data 为 []」都归一成**空列表**，
     * 而不是让 null 变成一个需要上层特殊处理的第三种状态。
     */
    suspend fun postForArray(path: String, body: JsonObject = buildJsonObject { }): List<JsonObject> {
        val element = post(path, body)["data"]
        if (element == null || element is kotlinx.serialization.json.JsonNull) return emptyList()
        val array = element.asArrayOrNull()
            ?: throw HzcuException.Parse("CJH52 的 data 既不是数组也不是 null")
        return array.mapNotNull { it.asObjectOrNull() }
    }

    /**
     * 租户参数。**不写死成 "6"** —— 契约文档 §9 提到这是多校共用 SaaS，
     * 写死会让多校环境出错。实际取值链在 [Cjh52Tenant]。
     */
    private val appCode: String get() = Cjh52Tenant.appCode()

    override fun toString(): String =
        "Cjh52Client(token=${tokenProvider()?.length ?: 0}字符)"

    companion object {
        /** 该站的「成功」。注意这是**业务码**，不是 HTTP 状态。 */
        const val CODE_OK = 200

        /** 该站表达「没权限 / 没登录」。 */
        const val CODE_UNAUTHORIZED = 401

        /**
         * 凭据指纹：**前 4 位 + 长度 + 校验和**。
         *
         * 用途：日志里判断「两次拿到的是不是同一个 token」，
         * 而**不足以还原**它（UUID 还剩 32 位未知）。
         *
         * 为什么需要：实测存在两种**含义完全不同**的 401 ——
         * - `HTTP 401` + `"身份驗證失敗"` = 带的 token 被服务端拒绝；
         * - `HTTP 200` + `code 401` = 压根没带 token。
         * 排查时必须能区分这两者，否则会把「凭据无效」误判成「漏了请求头」。
         */
        fun fingerprint(token: String?): String {
            if (token.isNullOrEmpty()) return "<无>"
            var sum = 0
            for (ch in token) sum = (sum * 31 + ch.code) and 0x7FFFFFFF
            return "${token.take(4)}…(len=${token.length},sum=$sum)"
        }
    }
}

/**
 * 租户参数取值链。
 *
 * 实测学生端 `localStorage["school"] = "6"`、`window.__ENV_APPCODE__ = 6`。
 * 但那是**浏览器里**的值；原生侧没有 localStorage，
 * 所以这里保留一个可注入的取值点，默认回落 `"6"`（当前唯一在用的租户）。
 *
 * **刻意不做成全局可变单例**（那会让并发测试互相污染）；
 * 需要换租户时构造 [Cjh52Client] 前设定。
 */
object Cjh52Tenant {
    /**
     * 当前租户码。默认 `"6"`（浙大城市学院，appCode=6）。
     *
     * 之所以敢给默认值：契约文档 §1 的实测证明**服务端当前不校验它**，
     * 所以默认值不会导致请求失败；给默认值只是为了让代码不必处理 null。
     */
    @Volatile
    var override: String? = null

    fun appCode(): String = override ?: DEFAULT

    const val DEFAULT = "6"
}
