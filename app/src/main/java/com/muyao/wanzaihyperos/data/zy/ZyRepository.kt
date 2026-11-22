package com.muyao.wanzaihyperos.data.zy

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 智雲課堂的倉庫：**維持會話 + 取週課表**。
 *
 * ## 職責邊界
 * - [ZyEngine] 只管「在 WebView 裡做什麼」；
 * - 這裡管「什麼時候建會話、取回來怎麼解析」。
 *
 * ## 為什麼沒有 `login()`
 *
 * 它的認證**沿用既有的統一身份認證會話**（與門戶同一套，
 * 見 `docs/B19-WALKTHROUGH.md` §2）。使用者已經登入過的話，
 * WebView 裡直接有 cookie，不必再問帳密。
 * 沒登入時，界面給他一個「用網頁登入」的出口（見 UI 層）。
 */
interface ZyRepository {
    /** 這一週的課表。 */
    val week: StateFlow<ListState<ZyScheduleItem>>
    suspend fun load(force: Boolean = false)
    /** 點某一節課時，取它的回放信息。 */
    suspend fun liveInfo(item: ZyScheduleItem): ZyLiveInfo?
}

class LiveZyRepository internal constructor(
    private val reader: ZyReader,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : ZyRepository {
    constructor(
        engine: ZyEngine,
        clock: () -> Long = System::currentTimeMillis,
        epoch: AccountEpoch = AccountEpoch(),
    ) : this(engine.reader, clock, epoch)

    private val guard = StaleGuard(epoch)
    private val inFlight = Mutex()
    private var verifiedUid: String? = null
    private var verifiedWeek: Pair<String, String>? = null
    private val _week = MutableStateFlow<ListState<ZyScheduleItem>>(ListState.FirstLoad)
    override val week: StateFlow<ListState<ZyScheduleItem>> = _week.asStateFlow()

    init {
        epoch.registerReset {
            guard.invalidate()
            verifiedUid = null
            verifiedWeek = null
            _week.value = ListState.FirstLoad
        }
    }

    override suspend fun load(force: Boolean) {
        val owner = epoch.token()
        if (!inFlight.tryLock()) return
        try {
            if (!epoch.isDataCurrent(owner)) return
            val token = guard.begin()
            val range = currentWeekRange(clock())
            var previous = if (verifiedWeek == range) _week.value else ListState.FirstLoad
            var currentUid: String? = null
            if (!guard.publish(token) { _week.value = previous.startRefresh() }) return

            val result = try {
                catchingActive {
                    if (!reader.awaitAttached()) throw ZyDomainFailure(ZyFailure.EngineNotReady)
                    val uid = reader.ensureSession(force)
                    if (!guard.isCurrent(token)) throw CancellationException("Account changed")
                    currentUid = uid
                    if (verifiedUid != null && verifiedUid != uid) {
                        previous = ListState.FirstLoad
                        if (!guard.publish(token) { _week.value = ListState.FirstLoad }) {
                            throw CancellationException("Account changed")
                        }
                    }
                    val (start, end) = range
                    val path = "$WEEK_PATH?user_id=${encodeQuery(uid)}&tenant_id=${ZyEngine.TENANT}" +
                        "&start_at=$start&end_at=$end"
                    parseZyWeek(reader.fetchText(path))
                }.also { if (it.isFailure && guard.isCurrent(token)) reader.invalidateSession() }
            } catch (e: CancellationException) {
                guard.publish(token) { _week.value = previous }
                throw e
            }

            guard.publish(token) {
                _week.value = result.fold(
                    onSuccess = { items ->
                        verifiedUid = currentUid
                        verifiedWeek = range
                        _week.value.settleFirstPage(items, clock()) { it.id }
                    },
                    onFailure = { e ->
                        val failure = ((e as? ZyDomainFailure)?.failure ?: ZyFailure.NoResponse).toHzcuException()
                        when (val state = _week.value) {
                            is ListState.Content -> state.copy(meta = state.meta.copy(
                                refreshing = false, stale = true, lastRefreshError = failure))
                            is ListState.Empty -> state.copy(meta = state.meta.copy(
                                refreshing = false, stale = true, lastRefreshError = failure))
                            else -> retainCachedOnFailure(state, failure)
                        }
                    },
                )
            }
        } finally {
            inFlight.unlock()
        }
    }

    override suspend fun liveInfo(item: ZyScheduleItem): ZyLiveInfo? {
        val owner = epoch.token()
        return inFlight.withLock {
            if (!epoch.isDataCurrent(owner)) return@withLock null
            val result = catchingActive {
                if (!reader.awaitAttached()) return@catchingActive null
                reader.ensureSession(force = false)
                if (!epoch.isDataCurrent(owner)) return@catchingActive null
                val path = "$LIVE_PATH?all=1&show_all=1&show_delete=2&with_sub_data=1" +
                    "&course_id=${encodeQuery(item.courseId)}&sub_id=${encodeQuery(item.id)}&tenant=${ZyEngine.TENANT}"
                ZyLiveParser.parseFirst(Json.parseToJsonElement(reader.fetchText(path)) as? JsonObject)
            }
            if (result.isFailure && epoch.isDataCurrent(owner)) reader.invalidateSession()
            result.getOrNull().takeIf { epoch.isDataCurrent(owner) }
        }
    }

    private companion object {
        /** 週課表（真機實測路徑）。 */
        const val WEEK_PATH = "/courseapi/v2/schedule/get-week-schedules"

        /** 某一節課的直播／回放（真機實測路徑）。 */
        const val LIVE_PATH = "/courseapi/v2/schedule/search-live-course-list"

        /**
         * 本週的（週一, 週日）。
         *
         * ⚠️ 刻意**用本地時間自己算**，不問服務端要「今天是第幾週」——
         * 那個介面（`get-term-years`）回的學期起訖要另外解讀，
         * 而週一~週日是本機就能確定的事實。少一個介面就少一處會壞的地方。
         */
        fun currentWeekRange(nowMillis: Long): Pair<String, String> {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = nowMillis
            // ISO：週一=1 … 週日=7
            cal.firstDayOfWeek = java.util.Calendar.MONDAY
            cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val start = fmt.format(cal.time)
            cal.add(java.util.Calendar.DAY_OF_MONTH, 6)
            val end = fmt.format(cal.time)
            return start to end
        }
    }
}

private fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")

internal fun parseZyWeek(text: String): List<ZyScheduleItem> {
    val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        ?: throw ZyDomainFailure(ZyFailure.UnexpectedResponse)
    val result = root["result"] as? JsonObject
    val outerCode = (root["code"] as? JsonPrimitive)?.content?.toIntOrNull()
    val code = (result?.get("code") as? JsonPrimitive)?.content?.toIntOrNull()
    if (outerCode == 401 || code == 401) throw ZyDomainFailure(ZyFailure.NotLoggedIn)
    if ((root["success"] as? JsonPrimitive)?.content != "true" || code != 200) {
        throw ZyDomainFailure(ZyFailure.UnexpectedResponse)
    }
    val days = result?.get("list") as? JsonArray ?: throw ZyDomainFailure(ZyFailure.UnexpectedResponse)
    val courseCount = days.sumOf { day ->
        ((day as? JsonObject)?.get("course") as? JsonArray)?.size
            ?: throw ZyDomainFailure(ZyFailure.UnexpectedResponse)
    }
    val items = ZyScheduleParser.parse(root).items
    if (items.size != courseCount) throw ZyDomainFailure(ZyFailure.UnexpectedResponse)
    return items
}

/**
 * 把 [ZyFailure] 當例外往上拋的載體。
 *
 * 只在倉庫內部流動（`load` 立刻翻譯成全專案的
 * [HzcuException]，因為 `ListState` 的失敗機制綁在那個 sealed class 上）。
 */
class ZyDomainFailure(val failure: ZyFailure) : Exception(failure.message)

/**
 * 智雲課堂的**失敗原因**。
 *
 * ### 為什麼要結構化
 *
 * 與 B18 同一個理由：不同原因要給使用者**不同的下一步**。
 * 「還沒登入」要給他登入的出口；「WebView 沒就緒」要他重試；
 * 「格式看不懂」是我們的問題（要更新），不是他的。
 * 全部混成一句「載入失敗」等於什麼都沒說。
 */
sealed interface ZyFailure {
    /** 隱藏 WebView 還沒就緒（組合與 layout 的競態）。 */
    data object EngineNotReady : ZyFailure

    /** 沒有登入態（cookie 不在）→ 要給登入出口。 */
    data object NotLoggedIn : ZyFailure

    /** 請求沒有回內容。 */
    data object NoResponse : ZyFailure

    data object Timeout : ZyFailure
    data object IdentityUnavailable : ZyFailure
    data object UnexpectedResponse : ZyFailure
    data class Http(val status: Int) : ZyFailure
    data class Other(val detail: String) : ZyFailure

    val message: String
        get() = when (this) {
            EngineNotReady -> "页面还没准备好，请重新加载"
            NotLoggedIn -> "智云课堂会话已失效，请到官网重新连接"
            NoResponse -> "智云课堂未能响应，请稍后重试"
            Timeout -> "智云课堂加载超时，请重试"
            IdentityUnavailable -> "智云课堂页面未能确认身份，可以打开官网重新连接"
            UnexpectedResponse -> "智云课堂返回的数据格式异常"
            is Http -> "智云课堂请求失败（HTTP $status）"
            is Other -> detail
        }

    /** 是不是「需要使用者去登入」。 */
    val needsLogin: Boolean get() = this is NotLoggedIn

    /**
     * 翻成全專案的錯誤模型。
     *
     * `LoginExpired` 在既有機制裡會被映射成 `ListState.SessionExpired`，
     * 正好對應「要引導登入」。
     */
    fun toHzcuException(): HzcuException = when (this) {
        NotLoggedIn -> HzcuException.LoginExpired(message)
        Timeout -> HzcuException.RequestTimeout(message)
        NoResponse, is Http -> HzcuException.Transport(message)
        else -> HzcuException.Parse(message)
    }
}
