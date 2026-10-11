package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * B3 剩餘三項的倉庫：**日常通知 / 我的考試 / 我的成長**。
 *
 * ### 三個共同點（所以放在一起）
 *
 * 1. **都要先拿 `userSid`**（`get_user_by_token` 的 `data.user.sid`）——
 *    這一步是共用的，抽成 [currentUser] 免得寫三遍。
 * 2. 都走 [Cjh52Client]（→ 限流閘門），都不繞過 QPS 紅線。
 * 3. 都繼承 A2 的紀律：`StaleGuard` 丟過時回應、失敗保留舊內容並標 stale。
 *
 * ### 為什麼 `userSid` 不快取到磁碟
 *
 * 它是 CJH52 側的使用者識別碼（個資）。每次取數重取一次，
 * 代價是一次請求，換來的是不多留一份個資。
 */

// ---------------------------------------------------------------------------
// 共用：當前使用者
// ---------------------------------------------------------------------------

/** 從 `get_user_by_token` 取到的、後續接口需要的那幾個值。 */
data class Cjh52UserContext(
    /** `data.user.sid`。多數接口要它當 `userId` / `userSid`。 */
    val userSid: String,
    /** `data.school.sid`。通知的 `scope.school` 用它。 */
    val schoolSid: String,
    /** `data.college.sid`。通知的 `scope.college[]` 用它。 */
    val collegeSid: String,
    /** `data.grade.value`。通知的 `scope.grade[]` 用它。 */
    val gradeValue: String,
    /** `data.specialty.sid`。通知的 `scope.specialty[]` 用它。 */
    val specialtySid: String,
)

/** 取當前使用者上下文。**每次取數都重取**（見類註釋的隱私理由）。 */
suspend fun Cjh52Client.currentUser(): Cjh52UserContext {
    val data = postForObject(HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH)
    fun field(key: String, sub: String): String =
        data[key].asObjectOrNull()?.get(sub).asStringOrNull().orEmpty()
    val userSid = field("user", "sid")
    if (userSid.isEmpty()) {
        throw com.muyao.wanzaihyperos.net.HzcuException.Parse("CJH52 沒有回傳 user.sid")
    }
    return Cjh52UserContext(
        userSid = userSid,
        schoolSid = field("school", "sid"),
        collegeSid = field("college", "sid"),
        gradeValue = field("grade", "value"),
        specialtySid = field("specialty", "sid"),
    )
}

// ---------------------------------------------------------------------------
// 日常通知
// ---------------------------------------------------------------------------

interface NoticeRepository {
    val state: StateFlow<ListState<Cjh52Notice>>
    suspend fun refresh()
}

class LiveNoticeRepository(
    private val client: Cjh52Client,
    private val clock: () -> Long = System::currentTimeMillis,
) : NoticeRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<Cjh52Notice>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<Cjh52Notice>> = _state.asStateFlow()
    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()
            val result = runCatching {
                val me = client.currentUser()
                // ★ `scope` 的四個值從用戶資訊推出來（真機實測的請求形態就是這樣）。
                //   硬編碼院系/年級會讓換了專業的人看不到通知。
                val body = buildJsonObject {
                    put("scope", buildJsonObject {
                        put("school", me.schoolSid)
                        put("college", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(me.collegeSid)) })
                        put("grade", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(me.gradeValue)) })
                        put("specialty", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(me.specialtySid)) })
                        put("role", "stu")
                    })
                    put("pageSize", PAGE_SIZE)
                    // ⚠️ 通知這批的 pageNum 實測是 0（與我的事務從 1 起不同）。
                    put("pageNum", 0)
                }
                val data = client.postForObject(HzcuEndpoints.CJH52_NOTICE_READER_PATH, body)
                val rows = data["data"].asArrayOrNull().orEmpty()
                    .mapNotNull { it.asObjectOrNull() }
                Cjh52NoticeParser.parse(rows)
            }
            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { _state.value.settleFirstPage(it, clock()) { n -> n.id } },
                onFailure = { _state.value.failWith(it.asCjh52Failure()) },
            )
        }
    }

    private companion object {
        /** 門戶實測的 pageSize。 */
        const val PAGE_SIZE = 10
    }
}

// ---------------------------------------------------------------------------
// 我的考試
// ---------------------------------------------------------------------------

interface ExamRepository {
    val state: StateFlow<ListState<Cjh52Exam>>
    suspend fun refresh()
}

class LiveExamRepository(
    private val client: Cjh52Client,
    private val clock: () -> Long = System::currentTimeMillis,
) : ExamRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<Cjh52Exam>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<Cjh52Exam>> = _state.asStateFlow()
    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()
            val result = runCatching {
                val body = buildJsonObject {
                    // ⚠️ 考試這批 pageNum 實測從 0 起。
                    put("pageNum", 0)
                    put("pageSize", PAGE_SIZE)
                    put("modelCode", HzcuEndpoints.CJH52_EXAM_MODEL_CODE)
                }
                val data = client.postForObject(HzcuEndpoints.CJH52_EXAM_LIST_PATH, body)
                val rows = data["data"].asArrayOrNull().orEmpty().mapNotNull { it.asObjectOrNull() }
                Cjh52ExamParser.parse(rows)
            }
            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                // 考試的條目**沒有穩定 id**（欄位名未取證），所以用整條 JSON 去重 ——
                // 用它當 key 是安全的：同一條重複出現時 JSON 也相同。
                onSuccess = { _state.value.settleFirstPage(it, clock()) { e -> e.raw.toString() } },
                onFailure = { _state.value.failWith(it.asCjh52Failure()) },
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}

// ---------------------------------------------------------------------------
// 我的成長
// ---------------------------------------------------------------------------

/** 成長頁的資料包（一次刷新把幾個接口的結果湊齊）。 */
data class GrowthSnapshot(
    val kqa: Cjh52KqaScore?,
    val physique: List<Cjh52PhysiqueScore>,
    val credits: Cjh52CreditProgress?,
    val activityMonths: List<Cjh52ActivityMonth>,
)

interface GrowthRepository {
    val state: StateFlow<ListState<GrowthSnapshot>>
    suspend fun refresh()
}

/**
 * 成長頁倉庫。
 *
 * ⚠️ **它與另兩個不同：資料是「一包」而不是「一列」**，
 * 所以包在 `ListState` 裡只有 0 或 1 項 ——
 * 這樣做是為了**沿用既有的載入/空/錯誤/stale 狀態機**（不必再造一套），
 * 代價是「一項」這個語義有點繞，故在此註明。
 *
 * ⚠️ **單個子接口失敗不讓整頁失敗**：四個接口裡任何一個掛了，
 * 其餘的照樣顯示（各自為 null / 空）。整頁失敗只在「全掛」時才報。
 */
class LiveGrowthRepository(
    private val client: Cjh52Client,
    private val clock: () -> Long = System::currentTimeMillis,
) : GrowthRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<GrowthSnapshot>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<GrowthSnapshot>> = _state.asStateFlow()
    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()

            val result = runCatching {
                // 先拿 userId（成長頁的接口都要它）。
                val me = client.currentUser()
                val userId = me.userSid.toIntOrNull()
                    ?: throw com.muyao.wanzaihyperos.net.HzcuException.Parse(
                        "CJH52 的 user.sid 不是數字，成長頁接口無法使用",
                    )

                // 四個子請求各自 runCatching：**一個掛不拖累其它**。
                val kqa = runCatching {
                    Cjh52GrowthParser.parseKqaScore(
                        client.postForObject(
                            HzcuEndpoints.CJH52_KQA_SCORE_PATH,
                            buildJsonObject { put("userId", userId) },
                        ),
                    )
                }.getOrNull()

                val physique = runCatching {
                    Cjh52GrowthParser.parsePhysique(
                        client.postForArray(HzcuEndpoints.CJH52_PHYSIQUE_PATH),
                    )
                }.getOrDefault(emptyList())

                val credits = runCatching {
                    Cjh52GrowthParser.parseCreditProgress(
                        client.postForObject(HzcuEndpoints.CJH52_CREDIT_PROGRESS_PATH),
                    )
                }.getOrNull()

                val months = runCatching {
                    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val data = client.postForObject(
                        HzcuEndpoints.CJH52_ACTIVITY_MONTHS_PATH,
                        buildJsonObject {
                            put("userId", userId)
                            put("startYear", year - 1)
                            put("endYear", year)
                        },
                    )
                    Cjh52GrowthParser.parseActivityMonths(data)
                }.getOrDefault(emptyList())

                // 「全掛」才算失敗：四個都拿不到東西時，回報錯誤比顯示一片空白誠實。
                if (kqa == null && physique.isEmpty() && credits == null && months.isEmpty()) {
                    throw com.muyao.wanzaihyperos.net.HzcuException.Parse(
                        "成長頁的四個接口都沒有回傳資料",
                    )
                }

                GrowthSnapshot(kqa = kqa, physique = physique, credits = credits, activityMonths = months)
            }

            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                // 只有一項：`settleFirstPage` 拿到空清單會變 Empty，
                // 所以成功時一定是「一項的 Content」。
                onSuccess = { _state.value.settleFirstPage(listOf(it), clock()) { s -> s.hashCode() } },
                onFailure = { _state.value.failWith(it.asCjh52Failure()) },
            )
        }
    }
}

/**
 * 把任意異常歸一到 [com.muyao.wanzaihyperos.net.HzcuException]。
 *
 * ⚠️ 名稱刻意**不叫** `asCjh52Exception` / `asPortalException`：
 * `PortalRepositories.kt` 已有一個 `internal fun Throwable.asPortalException()`，
 * 同 package 內同名擴展會**衝突**（實際踩過一次 `Conflicting overloads`）。
 */
internal fun Throwable.asCjh52Failure(): com.muyao.wanzaihyperos.net.HzcuException =
    if (this is com.muyao.wanzaihyperos.net.HzcuException) this
    else com.muyao.wanzaihyperos.net.HzcuException.Parse("CJH52 資料解析失敗", cause = this)
