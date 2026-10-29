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
 *    報錯消息還誤導人）。
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
            "换 sessionID 失败（${stale?.javaClass?.simpleName}），尝试续期 accessToken",
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
     * 「我的課」：本學期與歷來的**全部**課程。
     *
     * 端點是 `/api/my-courses`（實測 20 門），比 `/api/user/recently-visited-courses`
     * 多出教師、學分、學期三樣，而且是全量而不是「最近點過的」——
     * 見 [com.muyao.wanzaihyperos.data.tronclass.TronCourse] 的類註釋。
     *
     * ⚠️ `conditions={}` **必須帶**：不帶會 500（與公告那支同一個毛病）。
     *
     * ### ★ 頁大小原本是 30，那是**截斷**（真機驗證時抓到）
     *
     * 真機上「我的课（30）」看起來很合理，但用 `page_size=40` 探測時實際有
     * **37 門** —— 也就是說有 7 門課（多為較早的學期）**在 App 裡根本看不到**，
     * 而且症狀很隱蔽：列表沒有任何「還有更多」的提示，
     * 使用者只會以為自己沒選那些課。
     *
     * 這種「安靜截斷」比報錯更糟（報錯至少知道出事了）。
     * 所以放大到 [PAGE_SIZE] 並在界面顯示實際門數，兩邊對得上。
     */
    suspend fun myCourses(pageSize: Int = PAGE_SIZE):
        List<com.muyao.wanzaihyperos.data.tronclass.TronCourse> =
        parseListOf("/api/my-courses?conditions=%7B%7D&page=1&page_size=$pageSize", "我的课") {
            com.muyao.wanzaihyperos.data.tronclass.TronCourseParser.parse(it)
        }

    /**
     * 一門課的詳情（含課程大綱欄位）。
     *
     * 取不到（解析不出必要欄位）回 null，由呼叫方決定顯示什麼 ——
     * **不丟一個空殼物件**（那會讓界面顯示一排空白鍵值）。
     */
    suspend fun courseDetail(courseId: Int):
        com.muyao.wanzaihyperos.data.tronclass.TronCourseDetail? =
        com.muyao.wanzaihyperos.data.tronclass.TronCourseDetailParser.parse(
            requestJson("/api/courses/$courseId", "课程详情", method = "GET"),
        )

    /**
     * 我在某門課的成績。
     *
     * 兩支端點（自評分 + 佔比）**各自 runCatching**：佔比那支掛了不該讓成績也不顯示。
     * 兩支都失敗才回 null。
     */
    suspend fun courseScore(courseId: Int):
        com.muyao.wanzaihyperos.data.tronclass.TronScore? {
        val self = runCatching {
            requestJson("/api/course/$courseId/student-self-score", "成绩", method = "GET")
        }
        val percent = runCatching {
            requestJson("/api/course/$courseId/score-percentages", "成绩占比", method = "GET")
        }
        return com.muyao.wanzaihyperos.data.tronclass.TronScoreParser.parse(
            self.getOrNull(),
            percent.getOrNull(),
        )
    }

    /** 某門課的出勤彙總（老師點名幾次、我參與幾次）。 */
    suspend fun courseRollcallScore(courseId: Int):
        com.muyao.wanzaihyperos.data.tronclass.TronRollcallScore? =
        com.muyao.wanzaihyperos.data.tronclass.TronRollcallScoreParser.parse(
            requestJson("/api/course/$courseId/rollcall-score", "出勤", method = "GET"),
        )

    /**
     * 某門課的**出勤記錄**（每一次點名我到了沒有）。
     *
     * ⚠️ 路徑是 `course`（**單數**）+ `/student/{我的 id}`：
     * 同站的 `/api/courses/{id}/rollcalls` 是老師端的（實測 403）。
     * 學號不能省 —— 這支問的就是「**我**出席狀況」。
     */
    suspend fun courseAttendance(courseId: Int, studentId: Int):
        List<com.muyao.wanzaihyperos.data.tronclass.TronAttendance> =
        parseElementOf("/api/course/$courseId/student/$studentId/rollcalls", "出勤记录") {
            com.muyao.wanzaihyperos.data.tronclass.TronAttendanceParser.parseList(it)
        }

    /**
     * 進行中的簽到（唯讀）。
     *
     * 端點是 `/api/radar/rollcalls` —— 名字叫 radar，但它回的是**所有**進行中的簽到
     * （實測 200 可用），不只是雷達那種。名稱是歷史遺留。
     *
     * ★ `api_version=1.1.0` 是**逐字抄源碼**的（`build/recon/tron/all3.txt`）：
     * ```js
     * getOngoingRollcalls:function(){return a().get(s.ZP.getOngoingRollcalls,{params:{api_version:"1.1.0"}})}
     * ```
     * 我之前沒帶這個參數。這正是「一直提示没有签到」最可能的原因之一 ——
     * 這個站的接口按 api_version 改回應結構（同一個客戶端裡
     * `answer?api_version=1.76`、`stop_radar?api_version=1.1.0` 都是這樣），
     * 少了參數就可能拿到一個**結構不同、我解析成空**的回應。
     */
    suspend fun ongoingRollcalls(myStudentId: Int?): List<com.muyao.wanzaihyperos.data.tronclass.TronRollcall> =
        requestJson("/api/radar/rollcalls?api_version=1.1.0", "签到", method = "GET").let { root ->
            debugDump?.invoke("rollcalls", root.toString())
            com.muyao.wanzaihyperos.data.tronclass.TronRollcallParser.parseList(root, myStudentId)
        }

    /**
     * **掃碼認領**：把掃到的原串交給服務端，讓它說這是哪一次簽到。
     *
     * ★ 為什麼要有這一支（业主 2026-10-20：「谁让你设置有签到的时候才能扫码了，
     *   更何况你根本没判断准」）：
     *   原生 App 的掃碼流程就是**掃完直接把字串送上去**
     *   （`build/recon/tron/qr_todo2.txt` 逐字：`_scan()` 拿到結果後
     *   `(0,a.Y)({params:{codeStr:t}})`，而 `a.Y` 打的正是
     *   `scanRoomisQRCode:"/api/qr-code/scan"`）。
     *   也就是說**認碼的是服務端**，不需要客戶端先在「進行中」清單里认出那一條 ——
     *   我上一版拿清單當掃碼的門票，等於把順序做反了。
     *
     * ⚠️ 這一支的回應結構我**沒有真機樣本**（要真掃一次才知道）。
     * 所以這裡**不猜欄位**：把 HTTP 狀態與服務端原話回給界面，
     * 由界面顯示。成功與否只認服務端說的話。
     */

    /**
     * 只給**可調試構建**的原文落盤鉤子（由 `AppGraph` 在 debug 時裝上）。
     *
     * 用途單一：他反饋「判斷不準」時，我要看的不是我的解析器輸出了什麼，
     * 而是**服務端到底回了什麼**。沒有這個，我只能照猜改解析器。
     * release 構建這個子為 null，一點副作用都沒有。
     */
    var debugDump: ((String, String) -> Unit)? = null


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
     * 某門課的**我的提交狀態**（每項作業交了沒）。**只讀**。
     *
     * 作業清單本身不含「我交了沒」，所以先前那頁只能講截止時間。
     * 這一支補上那個缺口。能取不到時回空映射 —— 界面就退回「只顯示截止時間」，
     * **不假裝全部未交**。
     */
    suspend fun homeworkStatus(courseId: Int): Map<Int, com.muyao.wanzaihyperos.data.tronclass.TronHomeworkStatus> =
        com.muyao.wanzaihyperos.data.tronclass.TronHomeworkStatusParser.parse(
            requestJson("/api/course/$courseId/homework/submission-status", "作业状态", method = "GET"),
        )

    /**
     * 機構的**教學週**（第 N 週 = 哪段日期）。
     *
     * ⚠️ `orgId` 實測是 `1`（我們學校那一筆）。這是**機構層級**的設定，
     * 不是課程層級 —— 所以拿它來算「本週是第幾週」是對的。
     *
     * 取不到回空清單（呼叫方把「第幾週」顯示成「—」，**不猜**）。
     */
    suspend fun teachingWeeks(orgId: Int = 1):
        List<com.muyao.wanzaihyperos.data.tronclass.TronTeachingWeek> =
        com.muyao.wanzaihyperos.data.tronclass.TronTeachingWeekParser.parse(
            requestJson("/api/org/$orgId/teaching-weeks", "教学周", method = "GET"),
        )

    /**
     * 某門課的**作業清單**（含要求說明）。
     *
     * ⚠️ 路徑是 `/api/courses/{id}/homework-activities`（**courses 複數**）。
     * 先前猜成 `/api/course/{id}/homeworks` 全 404 —— 這個站單複數混用，
     * 只能逐字照抄源碼，不能按慣例推。
     */
    suspend fun homeworks(courseId: Int, page: Int = 1, pageSize: Int = 20):
        List<com.muyao.wanzaihyperos.data.tronclass.TronHomework> =
        parseListOf("/api/courses/$courseId/homework-activities?page=$page&page_size=$pageSize", "作业") {
            com.muyao.wanzaihyperos.data.tronclass.TronHomeworkParser.parseList(it)
        }

    /**
     * 我在某項作業上的**提交記錄**（交過幾份、哪份是最新的、評了幾分）。**只讀**。
     *
     * 端點逐字取自源碼：`getStudentSubmissionList` →
     * `/api/activities/{作业 id}/students/{user id}/submission_list`。
     * 注意 user id 有默認值（源碼 `void 0===t&&(t=p.Z.userID)`），
     * 但我們**必須顯式傳** —— 我們只知道自己的 id 從 `/api/profile` 取，
     * 不傳等於依賴「頁面裡那個全域變數」，原生端沒有它。
     *
     * id 取不到時**呼叫方不該調用本方法**（它會把 null 拼成字串 "null" 打到服務端）。
     */
    suspend fun submissions(homeworkId: Int, userId: Int):
        List<com.muyao.wanzaihyperos.data.tronclass.TronSubmission> =
        parseElementOf(
            "/api/activities/$homeworkId/students/$userId/submission_list",
            "我的提交",
        ) { com.muyao.wanzaihyperos.data.tronclass.TronSubmissionParser.parseList(it) }

    /**
     * **提交作業**（寫操作）。
     *
     * ⚠️ 與 [answerRollcall] 同一套紀律，而且這裡的後果更重（**記成績**）：
     *
     * 1. **只由使用者明確點擊觸發**；本方法**一次都不重試**
     *    （重試一次 = 可能多一筆記錄，而多那筆會**吃掉可交次數**）。
     *    所以刻意繞開 `parseListOf` 那條「會話失效自動重換再試」的路 ——
     *    那條路對 GET 是對的，對 POST 是錯的。
     * 2. 規則判定（截止、鎖定、次數上限、小組）全在**服務端**；
     *    這裡不預判、不猜，老實提交，把服務端的原話回報。
     * 3. 成功判據是 **HTTP 201**（源碼 `[2,201===i]` 逐字可見），
     *    不是 200。**把 200 當成功會顯示「提交成功」而服務端其實沒建記錄** ——
     *    那種假成功是最貴的一種錯（他會以為交了）。
     *
     * @param draft true = 存草稿（`is_draft`，**不算交过**）。
     */
    suspend fun submitHomework(
        homeworkId: Int,
        comment: String,
        draft: Boolean,
    ): com.muyao.wanzaihyperos.data.tronclass.HomeworkSubmitResult {
        val body = com.muyao.wanzaihyperos.data.tronclass.homeworkSubmissionPayload(comment, draft)
        val (status, text) = rawRequest(
            "/api/course/activities/$homeworkId/submissions",
            "POST",
            body,
        )
        return com.muyao.wanzaihyperos.data.tronclass.HomeworkSubmitResult(
            ok = status == 201,
            httpStatus = status,
            message = extractMessage(
                text,
                fallback = if (draft) "草稿未保存（服务端未说明原因）" else "提交未成功（服务端未说明原因）",
            ),
        )
    }

    /**
     * 全校／機構**公告**（首頁「課程動態」的來源之一）。
     *
     * 回應外層是 `{bulletins:[...]}` 或 `{data:[...]}`，
     * 所以用 [requestJsonElement] 取原始 JSON 再交給解析器（它三種外層都接）。
     */
    suspend fun orgBulletins(page: Int = 1, pageSize: Int = 20):
        List<com.muyao.wanzaihyperos.data.tronclass.TronBulletin> =
        parseElementOf(
            "/api/org-bulletin/bulletins?conditions=%7B%7D&page=$page&page_size=$pageSize",
            "公告",
        ) { com.muyao.wanzaihyperos.data.tronclass.TronBulletinParser.parseList(it) }

    /** 某門課的公告。 */
    suspend fun courseBulletins(courseId: Int, page: Int = 1, pageSize: Int = 20):
        List<com.muyao.wanzaihyperos.data.tronclass.TronBulletin> =
        parseElementOf(
            "/api/courses/$courseId/bulletins?page=$page&page_size=$pageSize",
            "课程公告",
        ) { com.muyao.wanzaihyperos.data.tronclass.TronBulletinParser.parseList(it) }

    /**
     * 某門課的**考試**清單。
     *
     * ⚠️ 先前（B12）以為它對所有課都空 —— 那是只掃 8 門課的結論。
     * B14 掃全部 37 門後**6 門有考試**，見 [com.muyao.wanzaihyperos.data.tronclass.TronExam]。
     */
    suspend fun courseExams(courseId: Int):
        List<com.muyao.wanzaihyperos.data.tronclass.TronExam> =
        parseElementOf("/api/courses/$courseId/exams", "考试") {
            com.muyao.wanzaihyperos.data.tronclass.TronExamParser.parseList(it)
        }

    /**
     * 某門課的**討論區主題**（只讀）。
     *
     * ⚠️ 回應裡只給貼文 `id` 與回覆數，**沒有貼文內容** ——
     * 要讀內容得逐條打 `/api/topics/{id}`。所以這一層只做「有哪些討論」，
     * 見 [com.muyao.wanzaihyperos.data.tronclass.TronTopicCategory] 的類註釋。
     */
    suspend fun courseTopicCategories(courseId: Int):
        List<com.muyao.wanzaihyperos.data.tronclass.TronTopicCategory> =
        parseElementOf("/api/courses/$courseId/topic-categories", "讨论区") {
            com.muyao.wanzaihyperos.data.tronclass.TronTopicCategoryParser.parseList(it)
        }

    /** 個人信息（「我的」頁）。 */
    suspend fun profile(): com.muyao.wanzaihyperos.data.tronclass.TronProfile? =
        com.muyao.wanzaihyperos.data.tronclass.TronProfileParser.parse(
            requestJson("/api/profile", "个人资料", method = "GET"),
        )

    /**
     * **簽到動作**（寫操作）。
     *
     * ⚠️ 這是本專案**極少數的寫操作**之一，所以把紀律寫在這裡：
     *
     * 1. **只由使用者明確點擊觸發** —— 絕不自動呼叫、絕不重試。
     *    （重試一次簽到 ＝ 可能送出兩筆記錄。）
     * 2. 校驗在**服務端**（位置、二維碼時效、IP、雷達信号），
     *    所以這裡**不猜測、不預判**：老實提交，把服務端的原話回報給使用者。
     * 3. 失敗消息要用服務端說的（它比我們準），但要**去掉可能含憑據的片段**。
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
        // 只给可调试构建：把他自己按下提交那一次真正发出去的包体留下（业主 2026-10-28 第⑤条）。
        // 没有这一条，签到码只能靠猜；有了它，服务端收到什么就是一串可查的原文。
        debugDump?.invoke("answer_" + kind.name.lowercase(), bodyJson)
        val (status, text) = rawRequest(path, "PUT", bodyJson)
        return com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerResult(
            ok = status == 200,
            httpStatus = status,
            // 服務端原話（校驗失敗時它會說「位置不對」「二維碼過期」等）。
            // ⚠️ 只保留短消息，且**不帶請求內容**。
            message = extractMessage(text, fallback = "签到未成功（服务端未说明原因）"),
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
        android.util.Log.i("WanzaiTron", "$label 首次失败（${err.javaClass.simpleName}），重换 sessionID 后重试")
        return parse(requestJson(path, label, method = "GET", forceNewSession = true))
    }

    /**
     * 與 [parseListOf] 相同，但解析器吃的是**原始 JSON 元素**。
     *
     * 為什麼需要它：這個站的回應外層不統一 —— 同類資料可能是
     * `{bulletins:[...]}`、`{data:[...]}` 或直接 `[...]`。
     * 把「認外層」的責任放在**解析器**（那裡能一次處理三種），
     * 而不是在這裡硬轉成 `JsonObject`（那會讓 `[...]` 直接失敗）。
     */
    private suspend fun <T> parseElementOf(
        path: String,
        label: String,
        parse: (kotlinx.serialization.json.JsonElement?) -> List<T>,
    ): List<T> {
        val first = runCatching { requestElement(path, label) }
        val err = first.exceptionOrNull()
        if (err == null) return parse(first.getOrThrow())
        if (err !is HzcuException.LoginExpired && !isUnauthorized(err)) throw err
        android.util.Log.i("WanzaiTron", "$label 首次失败（${err.javaClass.simpleName}），重换 sessionID 后重试")
        return parse(requestElement(path, label, forceNewSession = true))
    }

    /** 取原始 JSON 元素（不要求是物件）。錯誤判據與 [requestJson] 一致。 */
    private suspend fun requestElement(
        path: String,
        label: String,
        forceNewSession: Boolean = false,
    ): kotlinx.serialization.json.JsonElement? {
        val sid = if (forceNewSession) exchangeSessionId() else sessionId()
        val (status, text) = rawRequest(path, "GET", null, sid)
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
        return runCatching { Json.parseToJsonElement(text) }.getOrNull()
            ?: throw HzcuException.Parse("$label 响应不是 JSON")
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
        // ── 本地缓存（业主 2026-10-20：「每次点进应用数据都要重新加载」）──────
        // 判據全在 [ResponseCache] 裡：只有 GET、只有非會話類端點、只有 JSON 正文會進這裡。
        val c = cache
        val usable = if (c != null && c.cacheable(path, method) && !takeBypass()) c else null
        usable?.get(path, TTL_MS)?.let { hit -> return 200 to hit }

        val out = netRequest(path, method, jsonBody, sid ?: sessionId())
        // 只存 200 且真是 JSON 的回應（被踢回登入頁時回的是 HTML，
        // 存下來等於把「會話已失效」這個狀態凍進緩存裡，下次直接假装有数据）。
        if (usable != null && out.first == 200 && !out.second.trimStart().startsWith("<")) {
            usable.put(path, out.second)
        }
        return out
    }

    /** 真正發網絡請求的那一層（緩存與後臺再取都複用它，**自己不看緩存**）。 */
    private suspend fun netRequest(
        path: String,
        method: String,
        jsonBody: String?,
        sid: String,
    ): Pair<Int, String> = limiter.gate(path) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val builder = Request.Builder()
                .url(TRON_BASE + path)
                .header("X-SESSION-ID", sid)
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

    /**
     * 只給**下拉刷新/重試**用：讓下一次讀一定走網絡。
     *
     * ★ 為什麼要有這個開關：緩存要是不給出口，他點刷新會拿到一份舊數據，
     *   然後得出「刷新是假的」—— 那比沒有緩存更糟。
     *   用「一次性」而不是布爾量：用完自動复位，不會因為某個調用點忘了關而長期失效。
     */
    fun bypassCacheOnce() { bypassOnce = true }

    @Volatile
    private var bypassOnce = false

    private fun takeBypass(): Boolean {
        val v = bypassOnce
        bypassOnce = false
        return v
    }

    /** 只讀接口的本地緩存（由 `AppGraph` 裝上；null = 不緩存，單測與 preview 就是這個）。 */
    var cache: ResponseCache? = null

    /**
     * 從回應裡取服務端的短消息（給使用者看）。取不到就給個通用說法。
     *
     * @param fallback 兜底文字。**由呼叫方給**：簽到失敗與作業提交失敗是兩回事，
     *   把「签到未成功」顯示在作業提交失敗的橫幅上，使用者會以為按錯了地方。
     * ⚠️ 回傳值來自服務端：**限長、不帶請求內容、不寫日誌**（見 `Redaction`）。
     */
    private fun extractMessage(text: String, fallback: String): String {
        val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return fallback
        val msg = obj["message"].asStringOrNull()
            ?: obj["msg"].asStringOrNull()
            ?: obj["error"].asStringOrNull()
        return msg?.take(120)?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun isUnauthorized(t: Throwable): Boolean =
        t is HzcuException.HttpStatus && t.statusCode.let { it == 401 || it == 403 }

    /** 最小 JSON 字串轉義（值可能含引號/反斜線）。**實作在 [jsonQuote]，這裡只是本類的別名。** */
    private fun jsonString(v: String): String = jsonQuote(v)

    companion object {
        /** 接口域（**不是** mobile.hzcu.edu.cn，見類註釋）。 */
        private const val TRON_BASE = "https://courses.hzcu.edu.cn"
        private val JSON_TYPE = "application/json".toMediaType()

        /**
         * 課程清單的頁大小。
         *
         * 實測我們學校是 37 門（含歷來學期）。取 60 是留一倍餘裕 ——
         * 寧可多要一點也不要有課看不到（見 [myCourses] 的註釋）。
         */
        private const val PAGE_SIZE = 60

        /**
         * 緩存多久。**5 分鐘是刻意的短，而且這版不做「先頂舊數據、背後再刷」**：
         *
         * · 他抱怨的是「每次點進去都要重新載入」—— 在幾個板塊之間來回切就發生在幾分鐘內，
         *   5 分鐘剛好把這些重複請求省掉；
         * · 主殼目前沒有下拉刷新可以校正（查過：五個板塊都沒有刷新手勢），
         *   緩存放長就會變成「他看到舊數據而屏幕上沒有一個字告訴他舊」——
         *   那是這個工程最不能接受的失敗（假綠燈的同族）。
         *   要做更長的緩存，必須同時給每一屏加「X 分鐘前更新 + 下拉刷新」，那是另一輪的活。
         */
        private const val TTL_MS = 5 * 60 * 1000L
    }
}
