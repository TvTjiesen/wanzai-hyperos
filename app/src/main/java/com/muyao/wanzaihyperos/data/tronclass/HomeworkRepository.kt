package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
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
) : HomeworkRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<ClassHomework>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<ClassHomework>> = _state.asStateFlow()

    private val _courseName = MutableStateFlow("")
    override val courseName: StateFlow<String> = _courseName.asStateFlow()

    private val inFlight = Mutex()

    @Volatile
    private var courseIdValue: Int = 0

    override val currentCourseId: Int get() = courseIdValue

    override suspend fun load(courseId: Int, courseName: String) {
        courseIdValue = courseId
        _courseName.value = courseName
        // 換課就**清空重載**：留著上一門課的作業比空白更糟
        //（使用者會以為這門課有那些作業）。
        _state.value = ListState.FirstLoad
        refresh()
    }

    override suspend fun refresh() {
        val courseId = courseIdValue
        if (courseId <= 0) return
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()

            val result = runCatching {
                val homeworks = client.homeworks(courseId)
                // ★ 狀態那支**獨立 runCatching**：它掛了不該讓整份作業清單消失。
                //   取不到就是「不知道交了沒」，界面照那個講。
                val statuses = runCatching { client.homeworkStatus(courseId) }
                    .getOrDefault(emptyMap())
                homeworks.map { ClassHomework(it, statuses[it.id]) }
            }

            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { list ->
                    _state.value.settleFirstPage(list, clock()) { h -> h.homework.id }
                },
                onFailure = {
                    _state.value.failWith(
                        if (it is HzcuException) it
                        else HzcuException.Parse("作业数据解析失败", cause = it),
                    )
                },
            )
        }
    }
}
