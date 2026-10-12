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

    /** 待辦（作業）列表。sessionID 失效時**自動重換一次**再試。 */
    suspend fun todos(): List<TronTodo> {
        val first = runCatching { getTodos(sessionId()) }
        val err = first.exceptionOrNull()
        if (err == null) return first.getOrThrow()
        // 只在「會話類失敗」時重換 —— 網絡錯誤重換沒意義，直接上拋。
        if (err !is HzcuException.LoginExpired && !isUnauthorized(err)) throw err
        // ★ 只記「走了重換這條路」與異常類型，**不記任何憑據**。
        //   沒有這條日誌時，真機上只看到界面「取數失敗」，
        //   分不清是「sessionID 過期」還是「換取本身壞了」。
        android.util.Log.i("WanzaiTron", "todos 首次失敗（${err.javaClass.simpleName}），重換 sessionID 後重試")
        val sid = exchangeSessionId()
        return getTodos(sid)
    }

    /**
     * 首頁要用的「最近訪問的課程」。
     *
     * 與 [todos] 同一套會話與重試策略 —— 抽成共用是為了**只有一處**
     * 決定「會話失效時怎麼辦」，避免兩個接口各自演化出不同的補救方式。
     */
    suspend fun recentCourses(): List<com.muyao.wanzaihyperos.data.tronclass.TronCourse> {
        val first = runCatching { getCourses(sessionId()) }
        val err = first.exceptionOrNull()
        if (err == null) return first.getOrThrow()
        if (err !is HzcuException.LoginExpired && !isUnauthorized(err)) throw err
        android.util.Log.i("WanzaiTron", "课程首次失败（${err.javaClass.simpleName}），重換 sessionID 後重試")
        return getCourses(exchangeSessionId())
    }

    private fun isUnauthorized(t: Throwable): Boolean =
        t is HzcuException.HttpStatus && t.statusCode.let { it == 401 || it == 403 }

    /** 取最近訪問的課程。錯誤處理與 [getTodos] 同一套判據。 */
    private suspend fun getCourses(sid: String): List<com.muyao.wanzaihyperos.data.tronclass.TronCourse> =
        limiter.gate("/api/user/recently-visited-courses") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val request = Request.Builder()
                    .url(TRON_BASE + "/api/user/recently-visited-courses")
                    .header("X-SESSION-ID", sid)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "application/json, text/plain, */*")
                    .get()
                    .build()
                http.newCall(request).execute().use { resp ->
                    when {
                        resp.code == 401 || resp.code == 403 ->
                            throw HzcuException.LoginExpired("学在城院会话已失效")
                        resp.code != 200 -> throw HzcuException.HttpStatus(
                            statusCode = resp.code,
                            requestUri = TRON_BASE + "/api/user/recently-visited-courses",
                            summary = "课程取数失败",
                        )
                        else -> {
                            val text = resp.body?.string().orEmpty()
                            // 與 todos 同樣：回 HTML 說明會話被踢，要報錯而不是給空列表。
                            if (text.trimStart().startsWith("<")) {
                                throw HzcuException.LoginExpired("学在城院会话已失效（返回了页面而非数据）")
                            }
                            val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                                ?: throw HzcuException.Parse("课程响应不是 JSON 对象")
                            com.muyao.wanzaihyperos.data.tronclass.TronCourseParser.parse(root)
                        }
                    }
                }
            }
        }

    private suspend fun getTodos(sid: String): List<TronTodo> = limiter.gate("/api/todos") {
        // 同上：同步 OkHttp 必須在 IO 線程。
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val request = Request.Builder()
                .url(TRON_BASE + "/api/todos")
                .header("X-SESSION-ID", sid)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/plain, */*")
                .get()
                .build()
            http.newCall(request).execute().use { resp ->
                when {
                    resp.code == 401 || resp.code == 403 ->
                        throw HzcuException.LoginExpired("学在城院会话已失效")
                    resp.code != 200 -> throw HzcuException.HttpStatus(
                        statusCode = resp.code,
                        requestUri = TRON_BASE + "/api/todos",
                        summary = "待办取数失败",
                    )
                    else -> {
                        val text = resp.body?.string().orEmpty()
                        // ⚠️ 服務端可能回 HTML（會話被踢時會落到 SPA 空殼）——
                        //   那種情況要報錯而不是靜默給空列表，
                        //   否則界面會顯示「0 條作業」，那是謊報。
                        if (text.trimStart().startsWith("<")) {
                            throw HzcuException.LoginExpired("学在城院会话已失效（返回了页面而非数据）")
                        }
                        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                            ?: throw HzcuException.Parse("待办响应不是 JSON 对象")
                        TronTodoParser.parse(root)
                    }
                }
            }
        }
    }

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
