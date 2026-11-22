package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.TronclassClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.repo.finishRefresh
import com.muyao.wanzaihyperos.data.jw.OwnerSnapshotStore
import com.muyao.wanzaihyperos.session.TronclassSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class SavedSchedule(val slots: List<TronScheduleSlot>, val unparsed: List<Pair<String, String>>)

/**
 * **課表**：把各門課的上課時間字串攤成一週的格子。
 *
 * ## 資料怎麼來（以及為什麼要自己算）
 *
 * 學在城院**沒有**給學生的一週課表接口（`/api/courses/{id}/timetables` 實測 500，
 * 那是老師端的；手機版 SPA 也只是把 `teaching_class_name` 原樣顯示）。
 * 所以課表 = **課程清單的上課時間字串 + 教學週**，兩份都已經有了：
 *
 * ```
 * GET /api/my-courses?conditions=%7B%7D   → 每門課的 teaching_class_name
 * GET /api/org/1/teaching-weeks           → 第 N 週 = 哪段日期
 * ```
 *
 * 好處是**不多打任何請求**（那兩支首頁本來就在打）；
 * 代價是節次只能顯示「第 3-4 節」，沒有「幾點幾分」
 * —— 學校沒給節次時間表，**猜一個就是編造**（見 [TronScheduleSlot] 的類註釋）。
 */
data class TronSchedule(
    /** 全部格子（依週一→週日、節次排序）。 */
    val slots: List<TronScheduleSlot>,
    /** 目前是第幾教學週。**未知時是 `CountValue.Unknown`**（顯示「—」而不是 0）。 */
    val currentWeek: CountValue,
    /** 認不出格式而**原樣保留**的段落（連同它屬於哪門課）。 */
    val unparsed: List<Pair<String, String>>,
) {
    /** 只有這幾門課有上課時間。 */
    val courseCount: Int get() = slots.map { it.courseId }.distinct().size

    /** 某一週、某一天的格子（課表畫面用）。 */
    fun slotsAt(week: Int, dayOfWeek: Int): List<TronScheduleSlot> =
        slots.filter { it.dayOfWeek == dayOfWeek && it.activeIn(week) }

    /** 這一週有課的日子（畫「本週」用）。 */
    val activeDays: List<Int> get() = slots.map { it.dayOfWeek }.distinct().sorted()
}

interface ScheduleRepository {
    val state: StateFlow<ListState<TronSchedule>>
    suspend fun refresh()
    suspend fun refreshIfNeeded() {
        if (com.muyao.wanzaihyperos.data.schedule.shouldRefreshTimetable(state.value, System.currentTimeMillis())) refresh()
    }
}

class LiveScheduleRepository(
    private val client: TronclassClient,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
    appContext: android.content.Context? = null,
    private val session: TronclassSession? = null,
) : ScheduleRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<TronSchedule>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronSchedule>> = _state.asStateFlow()
    private val inFlight = Mutex()
    private val snapshotFile = appContext?.let { java.io.File(it.filesDir, "tron_timetable_snapshot.json") }
    private val snapshots = OwnerSnapshotStore(snapshotFile, "tron-timetable", epoch)
    private var credentialRevision = session?.credentialRevision()

    private fun seedSnapshot() {
        val owner = epoch.token()
        epoch.publishData(owner) {
            val read: () -> Unit = {
                val saved = snapshots.read(owner)?.let {
                    catchingActive { Json.decodeFromString<SavedSchedule>(it) }.getOrNull()
                }
                if (saved != null) {
                    val meta = ListMeta(updatedAtMillis = snapshotFile?.lastModified(), stale = true, hasMore = false)
                    _state.value = if (saved.slots.isEmpty() && saved.unparsed.isEmpty()) ListState.Empty(meta)
                    else ListState.Content(listOf(TronSchedule(saved.slots, CountValue.Unknown, saved.unparsed)), meta)
                }
            }
            session?.let { it.publishCredential(it.credentialRevision(), read) } ?: read()
        }
    }

    /**
     * 教學週快取。
     *
     * 它一學期只變一次（學校放假才會改），而課表每次刷新都要用 ——
     * 不快取等於每次刷新都多打一支。**取不到不記住**：下次仍會再試。
     */
    @Volatile
    private var cachedWeeks: List<TronTeachingWeek>? = null

    init {
        epoch.registerReset {
            guard.invalidate()
            cachedWeeks = null
            _state.value = ListState.FirstLoad
        }
        session?.addListener {
            epoch.publishData(epoch.token()) {
                val revision = session.credentialRevision()
                session.publishCredential(revision) {
                    if (credentialRevision != revision) {
                        credentialRevision = revision
                        guard.invalidate()
                        cachedWeeks = null
                        snapshotFile?.delete()
                        _state.value = ListState.FirstLoad
                    }
                }
            }
        }
        if (session == null || session.accessToken() != null) seedSnapshot()
    }

    private suspend fun teachingWeeks(token: Long): List<TronTeachingWeek>? {
        cachedWeeks?.let { return it }
        val weeks = catchingActive { client.teachingWeeks() }.getOrNull()
        if (!weeks.isNullOrEmpty()) guard.publish(token) { cachedWeeks = weeks }
        return weeks
    }

    override suspend fun refresh() {
        val token = guard.begin()
        val owner = epoch.token()
        val revision = session?.credentialRevision()
        try {
        inFlight.withLock {
            if (!guard.isCurrent(token)) return
            if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return

            val result = catchingActive {
                // 課程清單是**必須**的（沒有它整頁沒意義）。
                val courses = client.myCourses()

                val slots = mutableListOf<TronScheduleSlot>()
                val unparsed = mutableListOf<Pair<String, String>>()
                courses.forEach { c ->
                    val parsed = TronScheduleParser.parse(c.teachingClass, c.id, c.name)
                    slots.addAll(parsed.slots)
                    parsed.unparsed.forEach { unparsed.add(c.name to it) }
                }

                // 教學週取不到 → currentWeek 是 Unknown（顯示「—」），
                // **不是 0**：0 會被讀成「還沒開學」。
                val weeks = teachingWeeks(token)
                val currentWeek = weeks
                    ?.let { currentTeachingWeek(it, today()) }
                    ?.let { CountValue.Known(it) }
                    ?: CountValue.Unknown

                TronSchedule(
                    slots = slots.sortedWith(
                        compareBy({ it.dayOfWeek }, { it.periodStart }, { it.weekStart }),
                    ),
                    currentWeek = currentWeek,
                    unparsed = unparsed,
                )
            }

            val publish: () -> Unit = {
                _state.value = result.fold(
                    onSuccess = { schedule ->
                        val save: () -> Unit = {
                            snapshots.write(owner, Json.encodeToString(SavedSchedule(schedule.slots, schedule.unparsed)))
                        }
                        if (revision != null) session?.publishCredential(revision, save) else save()
                        _state.value.settleFirstPage(listOf(schedule), clock()) { s -> s.slots.size }
                    },
                    onFailure = {
                        _state.value.failWith(
                            if (it is HzcuException) it
                            else HzcuException.Parse("课表解析失败", cause = it),
                        )
                    },
                )
            }
            guard.publish(token) {
                if (revision != null) session?.publishCredential(revision, publish) else publish()
            }
        }
        } finally {
            guard.publish(token) {
                val finish: () -> Unit = { _state.value = _state.value.finishRefresh() }
                if (revision != null) session?.publishCredential(revision, finish) else finish()
            }
        }
    }
}
