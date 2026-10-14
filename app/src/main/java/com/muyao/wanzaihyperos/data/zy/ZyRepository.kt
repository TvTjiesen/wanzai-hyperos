package com.muyao.wanzaihyperos.data.zy

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
    /** 點某一節課時，取它的回放資訊。 */
    suspend fun liveInfo(item: ZyScheduleItem): ZyLiveInfo?
}

class LiveZyRepository(
    private val engine: ZyEngine,
    private val clock: () -> Long = System::currentTimeMillis,
) : ZyRepository {

    private val guard = StaleGuard()
    private val inFlight = Mutex()
    private val _week = MutableStateFlow<ListState<ZyScheduleItem>>(ListState.FirstLoad)
    override val week: StateFlow<ListState<ZyScheduleItem>> = _week.asStateFlow()

    /** 這次執行取到的 `user_id`（取數要用）。 */
    @Volatile
    private var userId: String? = null

    override suspend fun load(force: Boolean) {
        inFlight.withLock {
            val token = guard.begin()
            _week.value = _week.value.startRefresh()

            val result = runCatching {
                if (!engine.awaitAttached()) {
                    throw ZyDomainFailure(ZyFailure.EngineNotReady)
                }
                val uid = userId ?: engine.ensureSession()
                if (uid == null) throw ZyDomainFailure(ZyFailure.NotLoggedIn)
                userId = uid

                val (start, end) = currentWeekRange(clock())
                val path = "$WEEK_PATH?user_id=$uid&tenant_id=${ZyEngine.TENANT}" +
                    "&start_at=$start&end_at=$end"
                val text = engine.fetchText(ZyEngine.BASE + path)
                    ?: throw ZyDomainFailure(ZyFailure.NoResponse)
                if (text.startsWith("__ERR__")) {
                    throw ZyDomainFailure(
                        ZyFailure.Other("請求失敗：${text.removePrefix("__ERR__").take(60)}"),
                    )
                }
                if (text.trimStart().startsWith("<")) throw ZyDomainFailure(ZyFailure.NotLoggedIn)
                val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                    ?: throw ZyDomainFailure(
                        ZyFailure.Other("智雲課堂回的格式看不懂（${text.length} 字元），可能需要更新"),
                    )
                val week = ZyScheduleParser.parse(root)
                // 信封裡明確失敗（success=false）→ 當成登入失效，不要畫成「這週沒課」
                val success = (root["success"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (success == "false") throw ZyDomainFailure(ZyFailure.NotLoggedIn)
                week.items
            }

            if (!guard.isCurrent(token)) return
            _week.value = result.fold(
                onSuccess = { items ->
                    if (items.isEmpty()) {
                        // 明確的空（服務端說這週沒課）
                        _week.value.settleFirstPage(emptyList(), clock()) { it.id }
                    } else {
                        _week.value.settleFirstPage(items, clock()) { it.id }
                    }
                },
                onFailure = { e ->
                    val failure = (e as? ZyDomainFailure)?.failure
                        ?: ZyFailure.Other(e.message ?: "取數失敗")
                    _week.value.failWith(failure.toHzcuException())
                },
            )
        }
    }

    override suspend fun liveInfo(item: ZyScheduleItem): ZyLiveInfo? = runCatching {
        val uid = userId ?: return@runCatching null
        if (uid.isBlank()) return@runCatching null
        val path = "$LIVE_PATH?all=1&show_all=1&show_delete=2&with_sub_data=1" +
            "&course_id=${item.courseId}&sub_id=${item.id}&tenant=${ZyEngine.TENANT}"
        val text = engine.fetchText(ZyEngine.BASE + path) ?: return@runCatching null
        if (text.startsWith("__ERR__") || text.trimStart().startsWith("<")) return@runCatching null
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        ZyLiveParser.parseFirst(root)
    }.getOrNull()

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

    /** 其它。 */
    data class Other(val detail: String) : ZyFailure

    val message: String
        get() = when (this) {
            EngineNotReady -> "页面还没准备好，请点右上角重新加载"
            NotLoggedIn -> "还没有登录智云课堂"
            NoResponse -> "没有取到数据（页面可能还在加载），请点右上角重试"
            is Other -> detail
        }

    /** 是不是「需要使用者去登入」。 */
    val needsLogin: Boolean get() = this is NotLoggedIn

    /**
     * 翻成全專案的錯誤模型。
     *
     * `LoginExpired` 在既有機制裡會被映射成 `ListState.SessionExpired`，
     * 正好對應「要引導登入」——所以需要登入的走它，其餘走 `Parse`。
     */
    fun toHzcuException(): HzcuException =
        if (needsLogin) HzcuException.LoginExpired(message) else HzcuException.Parse(message)
}
