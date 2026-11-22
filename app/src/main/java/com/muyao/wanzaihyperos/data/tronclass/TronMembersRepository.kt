package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.*
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.TronclassClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*

interface TronMembersRepository {
    val state: StateFlow<ListState<TronMember>>
    val course: StateFlow<TronCourse?>
    suspend fun open(course: TronCourse)
    suspend fun refresh()
}

class LiveTronMembersRepository(
    private val client: TronclassClient,
    private val epoch: AccountEpoch = AccountEpoch(),
    private val clock: () -> Long = System::currentTimeMillis,
) : TronMembersRepository {
    private val guard = StaleGuard(epoch)
    private val snapshots = linkedMapOf<Pair<Long, Int>, ListState<TronMember>>()
    private val _state = MutableStateFlow<ListState<TronMember>>(ListState.FirstLoad)
    override val state = _state.asStateFlow()
    private val _course = MutableStateFlow<TronCourse?>(null)
    override val course = _course.asStateFlow()
    private var active: Long? = null

    init {
        epoch.registerReset {
            guard.invalidate(); snapshots.clear(); active = null
            _course.value = null; _state.value = ListState.FirstLoad
        }
    }

    override suspend fun open(course: TronCourse) {
        require(course.id > 0)
        val owner = epoch.token()
        var token: Long? = null
        epoch.publishData(owner) {
            if (_course.value?.id == course.id && active != null) return@publishData
            _course.value?.let { snapshots[owner to it.id] = tronReadSettled(_state.value) }
            if (_course.value?.id != course.id) _state.value = snapshots[owner to course.id] ?: ListState.FirstLoad
            _course.value = course
            token = guard.begin(); active = token
            _state.value = _state.value.startRefresh()
        }
        val read = token ?: return
        val job = currentCoroutineContext()[Job]
        try {
            if (job != null && !epoch.track(owner, job)) return
            val result = catchingActive { client.courseMembers(course.id, forceRefresh = true) }
            guard.publish(read) {
                _state.value = result.fold(
                    onSuccess = { rows ->
                        val meta = ListMeta(hasMore = false, updatedAtMillis = clock())
                        if (rows.isEmpty()) ListState.Empty(meta) else ListState.Content(rows, meta)
                    },
                    onFailure = { tronReadFailure(_state.value, it) },
                )
            }
        } finally {
            guard.publish(read) {
                active = null; _state.value = tronReadSettled(_state.value)
                snapshots[owner to course.id] = _state.value
                while (snapshots.size > 8) snapshots.remove(snapshots.keys.first())
            }
            if (job != null) epoch.untrack(job)
        }
    }

    override suspend fun refresh() { _course.value?.let { open(it) } }
}

internal fun <T> tronReadSettled(state: ListState<T>): ListState<T> = when (state) {
    is ListState.Content -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
    is ListState.Empty -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
    else -> state
}

internal fun <T> tronReadFailure(state: ListState<T>, error: Throwable): ListState<T> {
    val failure = when (error) {
        is HzcuException -> error
        is java.io.IOException -> HzcuException.Transport(cause = error)
        else -> HzcuException.Parse("学在城院数据读取失败", cause = error)
    }
    fun ListMeta.failed() = copy(refreshing = false, loadingMore = false, stale = true, lastRefreshError = failure)
    return when (state) {
        is ListState.Content -> state.copy(meta = state.meta.failed())
        is ListState.Empty -> state.copy(meta = state.meta.failed())
        else -> if (failure is HzcuException.LoginExpired) ListState.SessionExpired else ListState.Failed(failure)
    }
}
