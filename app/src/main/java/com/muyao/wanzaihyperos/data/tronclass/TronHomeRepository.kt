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
 * 學在城院**首頁**要展示的一包資料。
 *
 * 只放「一眼要看」的兩塊：最近課程 + 待辦作業。
 * 學在城院本身功能極多（討論、測驗、點名、互評…），
 * **不追求全覆蓋** —— 做最常用的兩塊，其餘走「改用網頁版」。
 */
data class TronHome(
    val courses: List<TronCourse>,
    val todos: List<TronTodo>,
) {
    /** 待辦裡最早到期的那條（首頁給一句「最近要交什麼」）。 */
    val nextDue: TronTodo? get() = todos.filter { it.endTimeMillis != null }.minByOrNull { it.endTimeMillis!! }
}

interface TronHomeRepository {
    val state: StateFlow<ListState<TronHome>>
    suspend fun refresh()
}

class LiveTronHomeRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : TronHomeRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<TronHome>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronHome>> = _state.asStateFlow()
    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()

            // 兩塊**各自 runCatching**：學在城院的接口彼此獨立，
            // 課程掛了不該讓待辦也消失（反之亦然）。全掛才當整頁失敗。
            val result = runCatching {
                val courses = runCatching { client.recentCourses() }
                val todos = runCatching { client.todos() }
                if (courses.isFailure && todos.isFailure) {
                    // 兩個都失敗：把第一個的異常上拋（它最能說明原因）。
                    throw courses.exceptionOrNull() ?: todos.exceptionOrNull()
                        ?: HzcuException.Parse("学在城院没有取到数据")
                }
                TronHome(
                    courses = courses.getOrDefault(emptyList()),
                    todos = todos.getOrDefault(emptyList()),
                )
            }

            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { _state.value.settleFirstPage(listOf(it), clock()) { h -> h.courses.size + h.todos.size } },
                onFailure = {
                    _state.value.failWith(
                        if (it is HzcuException) it
                        else HzcuException.Parse("学在城院数据解析失败", cause = it),
                    )
                },
            )
        }
    }
}
