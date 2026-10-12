package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.tronclass.TronTodo
import com.muyao.wanzaihyperos.data.tronclass.TronTodoParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 學在城院（TronClass）的**原生**接口層。
 *
 * ### 鑑權鏈（真機探測 2026-10-12 00:3x，逐步驗證）
 *
 * ```
 * POST https://courses.hzcu.edu.cn/api/login?login=access_token
 *      Content-Type: application/json
 *      body {"user_name":"<學號>","access_token":"<Keycloak JWT>"}
 *   → HTTP 200，body {"user_id":36519}
 *            **X-SESSION-ID 在響應頭裡**（不在 body —— 第一版探測只讀 body，
 *              200 了還以為失敗，這是踩過的坑）
 *
 * GET https://courses.hzcu.edu.cn/api/todos
 *      頭 X-SESSION-ID: <91 字元>
 *          X-Requested-With: XMLHttpRequest
 *          Accept: application/json（外加 text/plain 與通配，見下方 getTodos 的實際寫法）
 *   → HTTP 200 JSON，實測 19 條
 * ```
 *
 * ⚠️ 上面這段註釋裡**不能**照抄那個 Accept 值的原文 ——
 * 它的結尾是「斜線星號」，寫進 KDoc 會提前把註釋塊閉合掉（真的編譯報過）。
 *
 * ### 三個容易搞錯的點（都實測過）
 *
 * 1. **接口域是 `courses.hzcu.edu.cn`，不是 `mobile.hzcu.edu.cn`** ——
 *    後者是 SPA 頁面，在它上面 fetch `/api/todos` 拿到的是 HTML（SPA 空殼）。
 * 2. **JSON 包體的欄位名是 `user_name` + `access_token`（蛇形）** ——
 *    用 `accessToken` 會回 `_NO_PASSWORD_`（服務端把沒認出的欄位當缺失處理，
 *    報錯訊息還誤導人）。
 * 3. **不帶 `user_name` 會回 `_NO_USER_NAME_`** —— 所以學號是必需項。
 *
 * ### 為什麼不复用 [HzcuClient] 的 transport
 *
 * `HzcuClient` 是**為門戶域設計**的：它會給每個請求塞 `ACCESS_TOKEN` 表單欄位、
 * 按門戶的信封解碼。拿去打 courses 域會出亂子（實測三個變體全部異常）。
 * 所以這裡用裸 OkHttp，只借用全域的 [HzcuRateLimiter] 守 QPS 紅線。
 *
 * ### 憑據紀律
 *
 * - `access_token`（Keycloak JWT）與 `X-SESSION-ID` **都是憑據**：
 *   只放進包體/請求頭，**永不進日誌、異常、toString**；
 * - 日誌只記「拿到了/拿不到」與長度。
 */
class TronclassClient(
    private val http: OkHttpClient,
    private val accountProvider: () -> String?,
    private val accessTokenProvider: () -> String?,
    /** 讀到 sessionID 時回呼（讓上層快取進加密存儲）。**這是憑據**。 */
    private val onSessionId: (String) -> Unit = {},
    private val cachedSessionId: () -> String? = { null },
    private val limiter: HzcuRateLimiter = HzcuRateLimiter.instance,
    /**
     * 續期 Keycloak accessToken（可選）。
     *
     * ### 為什麼需要它
     *
     * accessToken 壽命約 1 小時。先前只有「打開學在城院磁貼」那條路會續期，
     * 而**待辦看板是另一條入口** —— 使用者直接點「全部待辦」時，
     * token 可能早就過期，於是換 sessionID 直接失敗。
     * 真機上表現成「學在城院·作業 → 取數失敗」，但看不出是過期。
     *
     * 回傳續期後的新 accessToken；續不了回 null（呼叫方據此報「請重新登入」）。
     */
    private val refreshAccessToken: suspend () -> String? = { null },
) {

    /**
     * 拿一個可用的 sessionID：優先复用快取的，沒有就去換一枚。
     *
     * 換取需要 Keycloak accessToken —— 它由登入/續期流程負責保持新鮮
     * （見 `WanzaiRoot` 的 TronclassFreeLogin 分支）。
     */
    suspend fun sessionId(): String {
        cachedSessionId()?.takeIf { it.isNotBlank() }?.let { return it }
        return exchangeSessionId()
    }

    private suspend fun exchangeSessionId(): String {
        val account = accountProvider()?.takeIf { it.isNotBlank() }
            ?: throw HzcuException.LoginExpired("需要先登录才能取学在城院数据")

        // 第一次：用現成的 accessToken。
        val first = runCatching { exchangeWith(account, requireAccessToken()) }
        first.getOrNull()?.let { return it }

        // ★ 失敗再續期一次。這是**待辦看板那條入口**能用的關鍵：
        //   它不經過「打開學在城院磁貼」那條會續期的路，
        //   token 過期時必須在這裡自己補救。
        val stale = first.exceptionOrNull()
        android.util.Log.i(
            "WanzaiTron",
            "換 sessionID 失敗（${stale?.javaClass?.simpleName}），嘗試續期 accessToken",
        )
        val fresh = refreshAccessToken()
        if (fresh.isNullOrBlank()) {
            // 續不了（沒 refresh_token、或 refresh 也被拒）→ 明確要求重新登入。
            throw HzcuException.LoginExpired("学在城院登录状态已失效，请重新登录")
        }
        // 續期成功：**再換一次**。這次失敗就照實上拋。
        return exchangeWith(account, fresh)
    }

    private fun requireAccessToken(): String =
        accessTokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw HzcuException.LoginExpired("学在城院登录状态已失效，请重新登录")

    private suspend fun exchangeWith(account: String, access: String): String {
        val body = """{"user_name":${jsonString(account)},"access_token":${jsonString(access)}}"""
            .toRequestBody(JSON_TYPE)

        return limiter.gate("/api/login") {
            // ★ 必須在 IO 線程：這些是**同步**的 OkHttp 調用，
            //   在 Main 上跑會直接 NetworkOnMainThreadException。
            //   （真機第一次跑就是這個錯 —— 界面顯示「取數失敗（NetworkOnMainThreadException）」，
            //    把那句籠統的「取數失敗」改成帶異常類型之後一眼就看出來了。）
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val request = Request.Builder()
                    .url(TRON_BASE + "/api/login?login=access_token")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                http.newCall(request).execute().use { resp ->
                    when {
                        resp.code == 200 -> {
                            val sid = resp.header("X-SESSION-ID")
                            if (sid.isNullOrBlank()) {
                                throw HzcuException.Parse("登录响应里没有 X-SESSION-ID 头")
                            }
                            onSessionId(sid)
                            sid
                        }
                        // 401/403：token 對過期 —— 讓上層去重新登入，不要在這裡重試。
                        resp.code == 401 || resp.code == 403 ->
                            throw HzcuException.LoginExpired("学在城院登录状态已失效")
                        else -> {
                            // ⚠️ 不把回應正文塞進異常：它可能含 token 片段。
                            //   只記狀態碼與長度。
                            val text = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                            throw HzcuException.HttpStatus(
                                statusCode = resp.code,
                                requestUri = TRON_BASE + "/api/login",
                                summary = "len=${text.length}",
                            )
                        }
                    }
                }
            }
        }
    }

    /** 待辦（作業）列表。 */
    suspend fun todos(): List<TronTodo> =
        parseListOf("/api/todos", "待办") { TronTodoParser.parse(it) }

    /**
     * 首頁要用的「最近訪問的課程」。
     */
    suspend fun recentCourses(): List<com.muyao.wanzaihyperos.data.tronclass.TronCourse> =
        parseListOf("/api/user/recently-visited-courses", "课程") {
            com.muyao.wanzaihyperos.data.tronclass.TronCourseParser.parse(it)
        }

    /**
     * 進行中的簽到（唯讀）。
     *
     * 端點是 `/api/radar/rollcalls` —— 名字叫 radar，但它回的是**所有**進行中的簽到
     * （實測 200 可用），不只是雷達那種。名稱是歷史遺留。
     */
    suspend fun ongoingRollcalls(myStudentId: Int?): List<com.muyao.wanzaihyperos.data.tronclass.TronRollcall> =
        requestJson("/api/radar/rollcalls", "签到", method = "GET").let { root ->
            com.muyao.wanzaihyperos.data.tronclass.TronRollcallParser.parseList(root, myStudentId)
        }

    /**
     * 取自己的 user id（`/api/profile` 的 `id`）。
     *
     * 簽到資料裡的 `student_rollcalls[].student_id` 用它來比對「哪條是我」——
     * 沒有它就分不清「我簽了」與「別人簽了」。
     *
     * 取不到時回 null 由呼叫方決定怎麼顯示（**不猜一個 id**）。
     */
    suspend fun profileUserId(): Int? =
        requestJson("/api/profile", "个人资料", method = "GET")["id"].asIntOrNull()

    /**
     * **簽到動作**（寫操作）。
     *
     * ⚠️ 這是本專案**極少數的寫操作**之一，所以把紀律寫在這裡：
     *
     * 1. **只由使用者明確點擊觸發** —— 絕不自動呼叫、絕不重試。
     *    （重試一次簽到 ＝ 可能送出兩筆記錄。）
     * 2. 校驗在**服務端**（位置、二維碼時效、IP、雷達訊號），
     *    所以這裡**不猜測、不預判**：老實提交，把服務端的原話回報給使用者。
     * 3. 失敗訊息要用服務端說的（它比我們準），但要**去掉可能含憑據的片段**。
     *
     * @param rollcallId 簽到 id
     * @param kind 簽到方式（決定端點）：`number` / `self_registration` / `qr`
     * @param code 數字簽到的數字，或掃碼得到的字串。其餘方式可為 null。
     */
    suspend fun answerRollcall(
        rollcallId: Int,
        kind: com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind,
        code: String?,
    ): com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerResult {
        val path = when (kind) {
            com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind.NUMBER ->
                "/api/rollcall/$rollcallId/answer_number_rollcall"
            com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind.SELF_REGISTRATION ->
                "/api/rollcall/$rollcallId/answer_self_registration_rollcall"
            com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind.QR ->
                "/api/rollcall/$rollcallId/answer_qr_rollcall"
            com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind.PLAIN ->
                "/api/rollcall/$rollcallId/answer?api_version=1.76"
        }
        // 數字簽到的包體欄位是 `number_code`（源碼模型欄位名），
        // 掃碼那條帶的是掃到的原始字串。
        val bodyJson = when (kind) {
            com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind.NUMBER ->
                """{"number_code":${jsonString(code.orEmpty())}}"""
            com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind.QR ->
                """{"code_str":${jsonString(code.orEmpty())}}"""
            else -> "{}"
        }
        val (status, text) = rawRequest(path, "PUT", bodyJson)
        return com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerResult(
            ok = status == 200,
            httpStatus = status,
            // 服務端原話（校驗失敗時它會說「位置不對」「二維碼過期」等）。
            // ⚠️ 只保留短訊息，且**不帶請求內容**。
            message = extractMessage(text),
        )
    }

    // -------------------------------------------------------------------------
    // 共用的請求與重試（**只有一處**決定「會話失效了怎麼辦**）
    //
    // 先前 `todos` 與 `recentCourses` 各寫了一遍「首次失敗→重換 sessionID→再試」，
    // 加第三、第四個接口就會出現四份幾乎一樣的邏輯 —— 那種重複的代價不是行數，
    // 是**它們會各自演化**（有一處忘了重試、有一處重試兩次）。
    // 所以抽在這裡，新接口只寫「路徑 + 怎麼解析」。
    // -------------------------------------------------------------------------

    /**
     * 發一個帶 sessionID 的請求，**失敗且屬於會話問題時自動重換一次**。
     *
     * @param parse 解析器；只負責把 JSON 變成模型。
     */
    private suspend fun <T> parseListOf(
        path: String,
        label: String,
        parse: (JsonObject) -> List<T>,
    ): List<T> {
        val first = runCatching { requestJson(path, label, method = "GET") }
        val err = first.exceptionOrNull()
        if (err == null) return first.getOrThrow().let { parse(it) }
        if (err !is HzcuException.LoginExpired && !isUnauthorized(err)) throw err
        android.util.Log.i("WanzaiTron", "$label 首次失败（${err.javaClass.simpleName}），重換 sessionID 後重試")
        return parse(requestJson(path, label, method = "GET", forceNewSession = true))
    }

    /**
     * 發請求並回已解析的 JSON（物件）。
     *
     * 錯誤判據**集中在此**：
     * - 401/403 → 會話失效；
     * - 非 200 → HttpStatus；
     * - 回 HTML → 會話失效（被踢時 SPA 會兜底成頁面）—— 這條很重要，
     *   若當成「空資料」會讓界面顯示「0 條」，那是謊報。
     */
    private suspend fun requestJson(
        path: String,
        label: String,
        method: String,
        forceNewSession: Boolean = false,
    ): JsonObject {
        val sid = if (forceNewSession) exchangeSessionId() else sessionId()
        val (status, text) = rawRequest(path, method, null, sid)
        when {
            status == 401 || status == 403 -> throw HzcuException.LoginExpired("学在城院会话已失效")
            status != 200 -> throw HzcuException.HttpStatus(
                statusCode = status,
                requestUri = TRON_BASE + path,
                summary = "$label 失败",
            )
        }
        if (text.trimStart().startsWith("<")) {
            throw HzcuException.LoginExpired("学在城院会话已失效（返回了页面而非数据）")
        }
        return runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw HzcuException.Parse("$label 响应不是 JSON 对象")
    }

    /**
     * 最底層：發一個請求，回 (狀態碼, 正文)。
     *
     * ⚠️ 同步 OkHttp，**必須在 IO 線程**（Main 上會 NetworkOnMainThreadException，
     * 真機踩過）。
     */
    private suspend fun rawRequest(
        path: String,
        method: String,
        jsonBody: String?,
        sid: String? = null,
    ): Pair<Int, String> {
        val effectiveSid = sid ?: sessionId()
        return limiter.gate(path) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val builder = Request.Builder()
                    .url(TRON_BASE + path)
                    .header("X-SESSION-ID", effectiveSid)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "application/json, text/plain, */*")
                when (method) {
                    "GET" -> builder.get()
                    "PUT" -> builder.put((jsonBody ?: "{}").toRequestBody(JSON_TYPE))
                    else -> builder.post((jsonBody ?: "{}").toRequestBody(JSON_TYPE))
                }
                http.newCall(builder.build()).execute().use { resp ->
                    resp.code to resp.body?.string().orEmpty()
                }
            }
        }
    }

    /** 從回應裡取服務端的短訊息（給使用者看）。取不到就給個通用說法。 */
    private fun extractMessage(text: String): String {
        val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return "签到未成功（服务端未说明原因）"
        val msg = obj["message"].asStringOrNull()
            ?: obj["msg"].asStringOrNull()
            ?: obj["error"].asStringOrNull()
        return msg?.take(120)?.takeIf { it.isNotBlank() } ?: "签到未成功（服务端未说明原因）"
    }

    private fun isUnauthorized(t: Throwable): Boolean =
        t is HzcuException.HttpStatus && t.statusCode.let { it == 401 || it == 403 }

    /** 最小 JSON 字串轉義（值可能含引號/反斜線）。 */
    private fun jsonString(v: String): String = buildString {
        append('"')
        for (ch in v) when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
        append('"')
    }

    companion object {
        /** 接口域（**不是** mobile.hzcu.edu.cn，見類註釋）。 */
        private const val TRON_BASE = "https://courses.hzcu.edu.cn"
        private val JSON_TYPE = "application/json".toMediaType()
    }
}
