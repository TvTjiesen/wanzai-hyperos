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
 * 一門課的**詳情**要顯示的一包資料。
 *
 * ### 為什麼是「一包」而不是五個倉庫
 *
 * 這一頁的五塊（詳情／成績／出勤彙總／出勤記錄／作業）**只在這一頁用**，
 * 而且是同時被需要的。做成五個倉庫會得到五個 `ListState`、五個 `FirstLoad`、
 * 五處各自判斷「這一塊該顯示載入中還是空」—— 那是介面上「一塊轉圈、一塊空白、
 * 一塊顯示錯誤」的來源。
 *
 * ### 每一塊可為 null / 空，且**互不影響**
 *
 * 學在城院這幾支端點彼此獨立（實測：公告與考試對八門課全空，成績與出勤有資料）。
 * 一塊掛掉不該讓整頁變成錯誤頁 —— 所以各自 `runCatching`，
 * 全部失敗才當整頁失敗（照 [LiveTronHomeRepository] 的先例）。
 *
 * ### ★ [course] 是「進來時手上有的那門課」
 *
 * 詳情接口回來之前，頁首就已經要顯示課名與上課時間（那些在課程清單裡就有）。
 * 所以把 [TronCourse] 一起帶進來 —— 這也是為什麼 [load] 收的是
 * [TronCourse] 而不是一個 id。
 */
data class TronCourseDetailBundle(
    val course: TronCourse,
    val detail: TronCourseDetail?,
    val score: TronScore?,
    val rollcallScore: TronRollcallScore?,
    val attendance: List<TronAttendance>,
    val homeworks: List<TronHomework>,
) {
    /**
     * 有沒有任何一塊取到了。
     *
     * 只用來區分「真的沒東西」與「全部取數失敗」——
     * 沒有它就會出現「這門課什麼都沒有」這種**替服務端撒謊**的畫面。
     */
    val hasAnyData: Boolean
        get() = detail != null || score != null || rollcallScore != null ||
            attendance.isNotEmpty() || homeworks.isNotEmpty()
}

interface CourseDetailRepository {
    val state: StateFlow<ListState<TronCourseDetailBundle>>
    /** 進入某一門課（會清掉上一門課的資料）。 */
    suspend fun load(course: TronCourse)
    suspend fun refresh()
}

class LiveCourseDetailRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : CourseDetailRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<TronCourseDetailBundle>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronCourseDetailBundle>> = _state.asStateFlow()

    private val inFlight = Mutex()

    @Volatile
    private var current: TronCourse? = null

    /**
     * 自己的 user id（`/api/profile` 的 `id`），**帶記憶**。
     *
     * 出勤記錄那支端點必須帶學號。與 [LiveRollcallRepository] 同一套做法：
     * 只有真的取到了才記住，取不到下次再試（一次網絡抖動不該讓出勤永遠是空的）。
     */
    @Volatile
    private var cachedMyId: Int? = null
    @Volatile
    private var myIdResolved = false

    private suspend fun myStudentId(): Int? {
        if (myIdResolved) return cachedMyId
        val id = runCatching { client.profileUserId() }.getOrNull()
        cachedMyId = id
        myIdResolved = id != null
        return id
    }

    override suspend fun load(course: TronCourse) {
        current = course
        // 換課就清空：留著上一門課的成績比空白更糟（會被當成這門課的成績）。
        _state.value = ListState.FirstLoad
        refresh()
    }

    override suspend fun refresh() {
        val course = current ?: return
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()

            val result = runCatching {
                val id = course.id
                // 學號取不到時出勤那塊就是空的（**不是「沒缺勤」**）——
                // 這裡不丟錯，讓其餘四塊照常顯示。
                val me = myStudentId()

                // 每一塊各自 runCatching，**並留住失敗原因**：
                // 全空時要靠它說明「是取數失敗」而不是「這門課什麼都沒有」。
                val detailR = runCatching { client.courseDetail(id) }
                val scoreR = runCatching { client.courseScore(id) }
                val rollcallR = runCatching { client.courseRollcallScore(id) }
                val attendanceR = if (me == null) {
                    Result.success(emptyList())
                } else {
                    runCatching { client.courseAttendance(id, me) }
                }
                val homeworksR = runCatching { client.homeworks(id) }

                val bundle = TronCourseDetailBundle(
                    course = course,
                    detail = detailR.getOrNull(),
                    score = scoreR.getOrNull(),
                    rollcallScore = rollcallR.getOrNull(),
                    attendance = attendanceR.getOrDefault(emptyList()),
                    homeworks = homeworksR.getOrDefault(emptyList()),
                )
                if (!bundle.hasAnyData) {
                    // 有異常 → 整頁失敗（把最能說明原因的那個上拋）。
                    // 全部成功卻都空 → 那是**事實**，照樣顯示（頁首仍有課名）。
                    val err = listOf(detailR, scoreR, rollcallR, attendanceR, homeworksR)
                        .firstNotNullOfOrNull { it.exceptionOrNull() }
                    if (err != null) throw err
                }
                bundle
            }

            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = {
                    _state.value.settleFirstPage(listOf(it), clock()) { b -> b.course.id }
                },
                onFailure = {
                    _state.value.failWith(
                        if (it is HzcuException) it
                        else HzcuException.Parse("课程详情解析失败", cause = it),
                    )
                },
            )
        }
    }
}
