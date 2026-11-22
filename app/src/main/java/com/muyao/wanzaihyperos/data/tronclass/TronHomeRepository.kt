package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.TronclassClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TronHome(
    val courses: List<TronCourse>,
    val todos: List<TronTodo>,
    val coursesError: HzcuException? = null,
    val todosError: HzcuException? = null,
) {
    val nextDue: TronTodo? get() = todos.filter { it.endTimeMillis != null }.minByOrNull { it.endTimeMillis!! }
}

interface TronHomeRepository {
    val state: StateFlow<ListState<TronHome>>
    suspend fun refresh()
}

internal fun Throwable.tronPartFailure(): HzcuException =
    this as? HzcuException ?: HzcuException.Parse("学在城院部分数据未返回")

class LiveTronHomeRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : TronHomeRepository {
    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<TronHome>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronHome>> = _state.asStateFlow()
    private val inFlight = Mutex()

    init { epoch.registerReset { guard.invalidate(); _state.value = ListState.FirstLoad } }

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            val previousState = _state.value as? ListState.Content
            val previous = previousState?.items?.firstOrNull()
            guard.publish(token) { _state.value = _state.value.startRefresh() }
            val courses = catchingActive { client.myCourses() }
            val todos = catchingActive { client.todos() }
            guard.publish(token) {
                if (courses.isFailure && todos.isFailure) {
                    _state.value = _state.value.failWith(courses.exceptionOrNull()!!.tronPartFailure())
                } else {
                    val result = TronHome(
                        courses.getOrElse { previous?.courses.orEmpty() },
                        todos.getOrElse { previous?.todos.orEmpty() },
                        courses.exceptionOrNull()?.tronPartFailure(),
                        todos.exceptionOrNull()?.tronPartFailure(),
                    )
                    val partial = result.coursesError ?: result.todosError
                    val settled = _state.value.settleFirstPage(listOf(result), clock()) { it.hashCode() }
                    _state.value = if (settled is ListState.Content && partial != null) {
                        settled.copy(meta = settled.meta.copy(stale = true, lastRefreshError = partial,
                            updatedAtMillis = previousState?.meta?.updatedAtMillis))
                    } else settled
                }
            }
        }
    }
}
