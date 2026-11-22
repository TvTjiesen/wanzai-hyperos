package com.muyao.wanzaihyperos.net

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * **数智体育**（`ty.hzcu.edu.cn`）的只读客户端。
 *
 * ## 只读红线
 *
 * 这个类**只允许调用查询接口**。服务端那侧有一整套写接口
 *（`run/endRun`、`run/updateRecord`、`exerciseRecord/startExercise`、
 * `health/attend`、`association/sign*` 等），它们逐秒存轨迹、服务端重算有效里程、
 * 违规还闭环 —— 伪造一定会留痕。业主 2026-10-31 的要求也明确是「只实现查看」。
 * 所以这里刻意**没有任何**「发一个任意 path」的公开口子：
 * 六个查询各自一个方法，写死在类里。
 *
 * ## 认人机制（实测，别改）
 *
 * 服务端**不按 token 认人，按请求体里的数字 `userId` 认人**：
 * `{'userId':80000}` 成功、`{'userId':32500000}`（学号）失败、
 * `{'id':…}` / `{'stuNum':…}` 全失败。token 只作准入。
 * 漏 `userId` 时服务端回的**不是**业务码而是 **HTTP 500** ——
 * 这个和「登录失效」必须分开，所以下面把它们分别映射到不同的异常。
 *
 * ## 节流
 *
 * 交接包（`dsh_handoff/README.md`）给的红线是**两次请求至少间隔 1.6 秒**，
 * 单次会话个位数到低双位数请求。这里照抄，并且**不与门户共用闸门**
 * （那是另一套学校系统，共用会把门户的 1s 红线拖慢）。
 */
class SztyClient(
    private val baseUrl: String = BASE_URL,
    private val httpClient: OkHttpClient = tronHttpClient(),
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        /** 与交接包 `config.json` 的 `schools.hzcu` 一致。 */
        const val BASE_URL = "https://ty.hzcu.edu.cn/app"

        /**
         * 浏览器式 UA。**必须带** —— 服务端 WAF 按 UA 放行，
         * 默认 curl/OkHttp 的 UA 直接 403（交接包实测）。
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; M2012K11AC Build/UKQ1; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/89.0.4389.90 Mobile Safari/537.36 uni-app"

        /** 两次请求之间的最小间隔。**不许调小**（交接包红线 1.6s）。 */
        const val MIN_INTERVAL_MS = 1_800L

        /** 服务端业务码：token 过期 / 被别的设备顶下线。 */
        const val CODE_TOKEN_EXPIRED = -3
        const val CODE_KICKED = -4

        /** 服务端业务码：成功。 */
        const val CODE_OK = 1

        private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
    }

    private val gate = Mutex()
    private var lastCallAt: Long = 0

    private class ReadGuard(val current: () -> Boolean) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<ReadGuard>
    }

    internal suspend fun <T> withReadGuard(current: () -> Boolean, action: suspend () -> T): T =
        withContext(ReadGuard(current)) { action() }

    private suspend fun ensureReadCurrent() {
        val context = currentCoroutineContext()
        context.ensureActive()
        if (context[ReadGuard]?.current?.invoke() == false) throw CancellationException("Sports credentials changed")
    }

    /**
     * 发一个**只读**查询，返回信封里的 `obj`。
     *
     * `obj` 可能是 null 且**不是错误**：例如 `sign/getSignClassStage`
     * 在「当前没有签到期次」时就是 `rel="没有签到期次"` + `obj=null`。
     */
    private suspend fun query(
        token: String,
        path: String,
        body: JsonObject,
        context: String,
    ): JsonElement? {
        val raw = send(token, path, body, context)

        // 先判 HTTP：漏参数时服务端回的是 500 —— 这与「网络不通」「token 过期」都不同，
        // 必须让用户看到「少东西」，而不是让界面显示一句 HTTP 500。
        if (raw.statusCode >= 400) {
            throw HzcuException.HttpStatus(
                statusCode = raw.statusCode,
                requestUri = raw.requestUri,
                summary = if (raw.statusCode == 500) {
                    "$context：服务端把这当成服务器错误回了 500（实测=这次请求少参数）"
                } else {
                    Redaction.responseSummary(raw.body)
                },
            )
        }

        val root = runCatching { Json.parseToJsonElement(raw.body) }.getOrNull() as? JsonObject
            ?: throw HzcuException.Parse(
                "$context：服务端回的不是 JSON",
                Redaction.responseSummary(raw.body),
            )

        val code = (root["statusCode"] as? JsonPrimitive)?.content?.toIntOrNull()
        val message = (root["message"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

        if (code == CODE_TOKEN_EXPIRED || code == CODE_KICKED) {
            throw HzcuException.LoginExpired(
                "$context：token 已经不能用了" + if (message == null) "" else "（$message）",
            )
        }
        if (code != CODE_OK) {
            throw HzcuException.Api(
                code = code ?: raw.statusCode,
                message = message ?: "$context：服务端没说话",
            )
        }
        return root["obj"]
    }

    private suspend fun send(
        token: String,
        path: String,
        body: JsonObject,
        context: String,
    ): HzcuRawResponse = gate.withLock {
        // 节流放在锁内：并发调用会排队，而不是各自算各自的间隔。
        val wait = lastCallAt + MIN_INTERVAL_MS - clock()
        if (wait > 0) sleeper(wait)
        ensureReadCurrent()
        lastCallAt = clock()

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Encrypt", "FALSE")
            .header("token", token)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        withContext(Dispatchers.IO) {
            ensureReadCurrent()
            try {
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    HzcuRawResponse(
                        statusCode = response.code,
                        body = text,
                        requestUri = request.url.toString(),
                        location = response.header("Location"),
                        contentType = response.body.contentType()?.toString(),
                    )
                }
            } catch (error: HzcuException) {
                throw error
            } catch (error: java.io.IOException) {
                throw HzcuException.Transport("$context：连不上数智体育", cause = error)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 六个只读查询。**没有第七个。**
    //
    // 它们返回的是服务端 obj 的**原文**，不是解析后的模型 —— 解析统一在仓库层做
    //（com.muyao.wanzaihyperos.data.szty.SztyParsers）。这样仓库才能把原文存成磁盘快照，
    // 冷启动读回来走同一个解析，不会出现「快照里和线上看到的不是一回事」。
    // ---------------------------------------------------------------------

    /** 体测成绩（按学年倒序）。 */
    suspend fun fitness(token: String, userId: Int): JsonElement? =
        query(token, "/tyapi/mobile/health/getMyGrade", buildJsonObject { put("userId", userId) }, "体测")

    /** 校园跑 + 课外锻炼的学期汇总（服务端这一条不认 userId，靠 token 就行）。 */
    suspend fun runSummary(token: String): JsonElement? =
        query(token, "/tyapi/mobile/exerciseRecord/getSemesterExerciseList", buildJsonObject { }, "校园跑")

    /** 学期清单，取 termId 最大的那个当当前学期。 */
    suspend fun currentTermId(token: String, userId: Int): JsonElement? =
        query(token, "/tyapi/mobile/exerciseRecord/getAssessmentYears", buildJsonObject { put("userId", userId) }, "学期")

    /** 体育课班级。 */
    suspend fun peClass(token: String, userId: Int, termId: Int): JsonElement? =
        query(
            token,
            "/tyapi/mobile/teaching/semesterStuEduClass",
            buildJsonObject {
                put("termId", termId)
                put("userId", userId)
            },
            "体育课",
        )

    /** 逐次课的签到记录。 */
    suspend fun attendance(token: String, userId: Int, eduClassId: Int): JsonElement? =
        query(
            token,
            "/tyapi/mobile/sign/stuAttendanceList",
            buildJsonObject {
                put("userId", userId)
                put("eduClassId", eduClassId)
            },
            "签到记录",
        )

    /** 签到汇总（已签到 / 迟到 / 请假 / 旷课）。 */
    suspend fun attendanceSummary(token: String, userId: Int, eduClassId: Int): JsonElement? =
        query(
            token,
            "/tyapi/mobile/sign/getSignClassHistory",
            buildJsonObject {
                put("userId", userId)
                put("eduClassId", eduClassId)
            },
            "签到汇总",
        )
}
