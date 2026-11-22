package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.finishRefresh
import com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure
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
    /** `data.clazz.sid`。班級詳情接口要它（`clazzSid`）。 */
    val clazzSid: String,
)

/** 取當前使用者上下文。**每次取數都重取**（見類註釋的隱私理由）。 */
suspend fun Cjh52Client.currentUser(): Cjh52UserContext {
    val data = postForObject(HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH)
    fun field(key: String, sub: String): String =
        data[key].asObjectOrNull()?.get(sub).asStringOrNull().orEmpty()
    val userSid = field("user", "sid")
    if (userSid.isEmpty()) {
        throw com.muyao.wanzaihyperos.net.HzcuException.Parse("CJH52 没有回传 user.sid")
    }
    return Cjh52UserContext(
        userSid = userSid,
        schoolSid = field("school", "sid"),
        collegeSid = field("college", "sid"),
        gradeValue = field("grade", "value"),
        specialtySid = field("specialty", "sid"),
        clazzSid = field("clazz", "sid"),
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
    private val epoch: AccountEpoch = AccountEpoch(),
) : NoticeRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<Cjh52Notice>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<Cjh52Notice>> = _state.asStateFlow()
    private val inFlight = Mutex()

    init { epoch.registerReset { guard.invalidate(); _state.value = ListState.FirstLoad } }

    override suspend fun refresh() {
        val token = guard.begin()
        try {
        inFlight.withLock {
            if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return
            val result = catchingActive {
                val me = client.currentUser()
                // ★ `scope` 的四個值從用戶信息推出來（真機實測的請求形態就是這樣）。
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
            guard.publish(token) {
                _state.value = result.fold(
                    onSuccess = { _state.value.settleFirstPage(it, clock()) { n -> n.id } },
                    onFailure = { retainCachedOnFailure(_state.value, it.asCjh52Failure()) },
                )
            }
        }
        } finally {
            // ★ 取消（切页/换账号/退出）不会走到上面的 fold：转圈标志必须在这里收掉。
            guard.publish(token) { _state.value = _state.value.finishRefresh() }
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
    private val epoch: AccountEpoch = AccountEpoch(),
) : ExamRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<Cjh52Exam>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<Cjh52Exam>> = _state.asStateFlow()
    private val inFlight = Mutex()

    init { epoch.registerReset { guard.invalidate(); _state.value = ListState.FirstLoad } }

    override suspend fun refresh() {
        val token = guard.begin()
        try {
        inFlight.withLock {
            if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return
            val result = catchingActive {
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
            guard.publish(token) {
                _state.value = result.fold(
                    // 考試的條目沒有穩定 id，沿用整條 JSON 去重。
                    onSuccess = { _state.value.settleFirstPage(it, clock()) { e -> e.raw.toString() } },
                    onFailure = { retainCachedOnFailure(_state.value, it.asCjh52Failure()) },
                )
            }
        }
        } finally {
            // ★ 取消（切页/换账号/退出）不会走到上面的 fold：转圈标志必须在这里收掉。
            guard.publish(token) { _state.value = _state.value.finishRefresh() }
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
    val successfulAtMillis: Map<String, Long> = emptyMap(),
    val completeUpdatedAtMillis: Long? = null,
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
 * 普通子请求失败时保留该区旧内容并标记过期；鉴权失败停止后续请求，
 * 有旧内容时保留并提示重连，没有旧内容时显示登录失效。
 */
class LiveGrowthRepository(
    private val client: Cjh52Client,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : GrowthRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<GrowthSnapshot>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<GrowthSnapshot>> = _state.asStateFlow()
    private val inFlight = Mutex()

    init { epoch.registerReset { guard.invalidate(); _state.value = ListState.FirstLoad } }

    override suspend fun refresh() {
        val token = guard.begin()
        try {
        inFlight.withLock {
            if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return

            var partialFailure: Throwable? = null
            val previousAt = _state.value.metaOrNull?.updatedAtMillis
            try {
                val result = catchingActive {
                // 先拿 userId（成長頁的接口都要它）。
                val me = client.currentUser()
                val userId = me.userSid.toIntOrNull()
                    ?: throw com.muyao.wanzaihyperos.net.HzcuException.Parse(
                        "CJH52 的 user.sid 不是数字，成长页接口无法使用",
                    )

                val previous = (_state.value as? ListState.Content)?.items?.firstOrNull()
                suspend fun <T> part(block: suspend () -> T): Result<T> = growthPart(block).onFailure {
                    if (partialFailure == null) partialFailure = it
                }
                val kqa = part {
                    Cjh52GrowthParser.parseKqaScore(
                        client.postForObject(
                            HzcuEndpoints.CJH52_KQA_SCORE_PATH,
                            buildJsonObject { put("userId", userId) },
                        ),
                    )
                }.getOrElse { previous?.kqa }

                val physique = part {
                    Cjh52GrowthParser.parsePhysique(
                        client.postForArray(HzcuEndpoints.CJH52_PHYSIQUE_PATH),
                    )
                }.getOrElse { previous?.physique.orEmpty() }

                val credits = part {
                    Cjh52GrowthParser.parseCreditProgress(
                        client.postForObject(HzcuEndpoints.CJH52_CREDIT_PROGRESS_PATH),
                    )
                }.getOrElse { previous?.credits }

                val months = part {
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
                }.getOrElse { previous?.activityMonths.orEmpty() }

                // 「全掛」才算失敗：四個都拿不到東西時，回報錯誤比顯示一片空白誠實。
                if (kqa == null && physique.isEmpty() && credits == null && months.isEmpty()) {
                    throw com.muyao.wanzaihyperos.net.HzcuException.Parse(
                        "成长页的四个接口都没有回传资料",
                    )
                }

                GrowthSnapshot(kqa = kqa, physique = physique, credits = credits, activityMonths = months)
            }

            guard.publish(token) {
                    _state.value = result.fold(
                        onSuccess = {
                            val updated = _state.value.settleFirstPage(listOf(it), clock()) { s -> s.hashCode() }
                            partialFailure?.let { error ->
                                // 部分分区沿用了上次的资料 ⇒ 整页「更新时间」不能前进到刚刚，
                                // 否则界面会把旧资料标成刚取的。
                                updated.keepGrowthOnFailure(error.asCjh52Failure(), previousAt)
                            } ?: updated
                        },
                        onFailure = { _state.value.keepGrowthOnFailure(it.asCjh52Failure()) },
                    )
                }
            } finally {
                // 取消（切页/换账号/退出）不会走到上面的 fold：转圈标志必须在这里收掉，
                // 否则 Content(refreshing = true) 会一直转，下次进页也一直转。
                guard.publish(token) { _state.value = _state.value.finishRefresh() }
            }
        }
        } finally {
            // ★ 外层兜底（同 LectureRepository 的放置位置）：若下一次刷新在锁上等待时被取消，
            //   它从不进入 withLock lambda，内层 finally 不会执行，只有这里收得了转圈标志。
            guard.publish(token) { _state.value = _state.value.finishRefresh() }
        }
    }
}

private fun ListState<GrowthSnapshot>.keepGrowthOnFailure(
    error: com.muyao.wanzaihyperos.net.HzcuException,
    previousAt: Long? = null,
): ListState<GrowthSnapshot> = when (this) {
    is ListState.Content -> copy(meta = meta.copy(
        refreshing = false, loadingMore = false, stale = true, lastRefreshError = error,
        updatedAtMillis = previousAt ?: meta.updatedAtMillis,
    ))
    else -> retainCachedOnFailure(this, error)
}

private suspend fun <T> growthPart(block: suspend () -> T): Result<T> =
    catchingActive { block() }.onFailure {
        if (it is com.muyao.wanzaihyperos.net.HzcuException.LoginExpired) throw it
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
    else com.muyao.wanzaihyperos.net.HzcuException.Parse("CJH52 资料解析失败", cause = this)
