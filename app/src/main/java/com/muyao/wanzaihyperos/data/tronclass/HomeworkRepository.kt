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
 * 某門課的作業清單（含要求）。
 *
 * ### 為什麼要按課程載入
 *
 * 作業接口是**課程內**的（`/api/courses/{id}/homework-activities`）——
 * 沒有「全校所有作業」這個端點。所以頁面先給課程清單，選一門課再看它的作業。
 *
 * 這也是為什麼「待辦」與「作業」是兩個不同的視圖：
 * - 待辦 = **跨課程**的彙總（哪些要交），只有標題；
 * - 作業 = **單門課內**的完整清單（要求、截止、能交幾次）。
 */
interface HomeworkRepository {
    val state: StateFlow<ListState<TronHomework>>
    /** 目前看的是哪門課（用於界面標題）。 */
    val courseName: StateFlow<String>
    suspend fun load(courseId: Int, courseName: String)
    suspend fun refresh()
}

class LiveHomeworkRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : HomeworkRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<TronHomework>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronHomework>> = _state.asStateFlow()

    private val _courseName = MutableStateFlow("")
    override val courseName: StateFlow<String> = _courseName.asStateFlow()

    private val inFlight = Mutex()

    @Volatile
    private var currentCourseId: Int? = null

    override suspend fun load(courseId: Int, courseName: String) {
        currentCourseId = courseId
        _courseName.value = courseName
        // 換課就**清空重載**：留著上一門課的作業比空白更糟
        //（使用者會以為這門課有那些作業）。
        _state.value = ListState.FirstLoad
        refresh()
    }

    override suspend fun refresh() {
        val courseId = currentCourseId ?: return
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()
            val result = runCatching { client.homeworks(courseId) }
            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { _state.value.settleFirstPage(it, clock()) { h -> h.id } },
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
