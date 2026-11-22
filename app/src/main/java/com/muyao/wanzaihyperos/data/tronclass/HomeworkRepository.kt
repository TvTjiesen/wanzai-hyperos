package com.muyao.wanzaihyperos.data.tronclass

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

/**
 * 某門課的作業清單 **＋ 我的提交狀態**。
 *
 * ### 為什麼把兩者合成一包
 *
 * 作業清單（`homework-activities`）不含「我交了沒」，
 * 那信息在另一支（`homework/submission-status`）。
 * 但**分開顯示沒有意義** —— 學生看的是「這份作業、截止什麼時候、**我交了沒**」。
 * 兩個各自獨立的 `ListState` 只會讓界面出現「清單有了、狀態還在轉圈」的中間態。
 *
 * ### 狀態取不到時**不假裝**
 *
 * 狀態那支失敗就給空映射，界面退回只顯示截止時間。
 * 「不知道交了沒」與「沒交」在界面上必須長得不一樣。
 */
data class ClassHomework(
    val homework: TronHomework,
    /** 我的提交狀態。null ＝ **不知道**（不是「沒交」）。 */
    val myStatus: TronHomeworkStatus?,
)

interface HomeworkRepository {
    val state: StateFlow<ListState<ClassHomework>>
    /** 目前看的是哪門課（用於界面標題）。 */
    val courseName: StateFlow<String>

    /**
     * 目前看的是哪門課的 **id**（0 = 還沒 load 過）。
     *
     * ★ 為什麼要暴露它（2026-10-19）：清單上每一行現在可以點進「作業詳情」，
     * 而詳情/提交都需要 course id（`/api/courses/{id}/homework-activities`
     * 與狀態那支都是課程內的接口）。让界面自己记一份等於把同一個事實
     * 放在兩個地方 —— 換課時只要有一處忘了改，就會把 A 課的作業 id
     * 拿到 B 課去提交。倉庫已經記了，界面就問它要。
     */
    val currentCourseId: Int

    suspend fun load(courseId: Int, courseName: String)
    suspend fun refresh()
}

class LiveHomeworkRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : HomeworkRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<ClassHomework>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<ClassHomework>> = _state.asStateFlow()

    private val _courseName = MutableStateFlow("")
    override val courseName: StateFlow<String> = _courseName.asStateFlow()

    private val inFlight = Mutex()

    private data class CacheKey(val owner: Long, val courseId: Int)
    private val snapshots = linkedMapOf<CacheKey, ListState<ClassHomework>>()
    @Volatile private var activeReadToken: Long? = null

    @Volatile
    private var courseIdValue: Int = 0

    // Called only within the owner publication boundary.
    private fun rememberSelected() {
        if (courseIdValue <= 0) return
        val key = CacheKey(epoch.token(), courseIdValue)
        val settled = when (val state = _state.value) {
            is ListState.Content -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
            is ListState.Empty -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
            else -> null
        }
        snapshots.remove(key)
        if (settled != null) snapshots[key] = settled
        while (snapshots.size > 8) snapshots.remove(snapshots.keys.first())
    }

    override val currentCourseId: Int get() = courseIdValue

    init {
        epoch.registerReset {
            guard.invalidate()
            snapshots.clear()
            activeReadToken = null
            courseIdValue = 0
            _courseName.value = ""
            _state.value = ListState.FirstLoad
        }
    }

    override suspend fun load(courseId: Int, courseName: String) {
        if (courseId <= 0) return
        var token: Long? = null
        epoch.publishData(epoch.token()) {
            _courseName.value = courseName
            if (courseIdValue == courseId && activeReadToken == guard.current) return@publishData
            if (courseIdValue != courseId) {
                rememberSelected()
                courseIdValue = courseId
                _state.value = snapshots[CacheKey(epoch.token(), courseId)] ?: ListState.FirstLoad
            }
            token = guard.begin()
            activeReadToken = token
            _state.value = _state.value.startRefresh()
        }
        token?.let { refresh(it, forceRefresh = false) }
    }

    override suspend fun refresh() {
        var token: Long? = null
        epoch.publishData(epoch.token()) {
            if (courseIdValue <= 0 || activeReadToken == guard.current) return@publishData
            token = guard.begin()
            activeReadToken = token
            _state.value = _state.value.startRefresh()
        }
        token?.let { refresh(it, forceRefresh = true) }
    }

    private fun failRead(error: HzcuException) {
        val state = _state.value
        _state.value = if (error is HzcuException.LoginExpired) when (state) {
            is ListState.Content -> state.copy(meta = state.meta.copy(
                refreshing = false, loadingMore = false, stale = true, lastRefreshError = error,
            ))
            is ListState.Empty -> state.copy(meta = state.meta.copy(
                refreshing = false, loadingMore = false, stale = true, lastRefreshError = error,
            ))
            else -> state.failWith(error)
        } else state.failWith(error)
    }

    private suspend fun refresh(token: Long, forceRefresh: Boolean) {
        try {
            inFlight.withLock {
                if (!guard.isCurrent(token)) return
                val course = courseIdValue
                val result = catchingActive {
                    val homeworks = client.homeworks(course, forceRefresh = forceRefresh)
                    if (!guard.isCurrent(token)) return
                    // A failed status lookup still means unknown, never unsubmitted.
                    val statusResult = catchingActive { client.homeworkStatus(course, forceRefresh) }
                    statusResult.exceptionOrNull()?.let { if (it is HzcuException.LoginExpired) throw it }
                    val statuses = statusResult.getOrDefault(emptyMap())
                    homeworks.map { ClassHomework(it, statuses[it.id]) }
                }
                guard.publish(token) {
                    result.fold(
                        onSuccess = { list ->
                            _state.value = _state.value.settleFirstPage(list, clock()) { h -> h.homework.id }
                        },
                        onFailure = {
                            failRead(if (it is HzcuException) it else HzcuException.Parse("作业数据解析失败", cause = it))
                        },
                    )
                    rememberSelected()
                }
            }
        } finally {
            guard.publish(token) {
                _state.value = when (val state = _state.value) {
                    is ListState.Content -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
                    is ListState.Empty -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
                    else -> state
                }
                activeReadToken = null
                rememberSelected()
            }
        }
    }
}
