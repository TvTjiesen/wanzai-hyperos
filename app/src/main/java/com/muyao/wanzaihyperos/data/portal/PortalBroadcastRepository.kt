package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.withHasMore
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure
import com.muyao.wanzaihyperos.net.HzcuResponse
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.PortalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

interface PortalBroadcastRepository {
    val state: StateFlow<ListState<PortalBroadcastItem>>
    val detail: StateFlow<ListState<PortalBroadcastDetail>>
    suspend fun refresh()
    suspend fun open(item: PortalBroadcastItem)
    fun closeDetail()
}

class LivePortalBroadcastRepository(
    private val readList: suspend () -> HzcuResponse,
    private val readDetail: suspend (String) -> HzcuResponse,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : PortalBroadcastRepository {
    constructor(api: PortalApi, epoch: AccountEpoch) : this(api::fetchBroadcasts, api::fetchBroadcastDetail, epoch = epoch)

    private val guard = StaleGuard(epoch)
    private val detailGuard = StaleGuard(epoch)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ListState<PortalBroadcastItem>>(ListState.FirstLoad)
    private val _detail = MutableStateFlow<ListState<PortalBroadcastDetail>>(ListState.FirstLoad)
    override val state = _state.asStateFlow()
    override val detail = _detail.asStateFlow()
    private data class DetailKey(val owner: Long, val id: String)
    private val detailSnapshots = mutableMapOf<DetailKey, ListState<PortalBroadcastDetail>>()
    private var selectedId: String? = null
    private var activeDetailToken: Long? = null

    private fun <T> settled(state: ListState<T>): ListState<T> = when (state) {
        is ListState.Content -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
        is ListState.Empty -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
        else -> state
    }

    private fun rememberDetail() {
        val id = selectedId ?: return
        val current = settled(_detail.value)
        if (current is ListState.Content || current is ListState.Empty) {
            detailSnapshots[DetailKey(epoch.token(), id)] = current
        }
    }

    private fun <T> failed(state: ListState<T>, error: Throwable): ListState<T> {
        val failure = error.asPortalException()
        val current = settled(state)
        return if (failure is HzcuException.LoginExpired) when (current) {
            is ListState.Content -> current.copy(meta = current.meta.copy(stale = true, lastRefreshError = failure))
            is ListState.Empty -> current.copy(meta = current.meta.copy(stale = true, lastRefreshError = failure))
            else -> retainCachedOnFailure(current, failure)
        } else retainCachedOnFailure(current, failure)
    }

    init {
        epoch.registerReset {
            guard.invalidate()
            detailGuard.invalidate()
            detailSnapshots.clear()
            selectedId = null
            activeDetailToken = null
            _state.value = ListState.FirstLoad
            _detail.value = ListState.FirstLoad
        }
    }

    override suspend fun refresh() {
        if (!mutex.tryLock()) return
        val token = guard.begin()
        try {
            if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return
            val result = catchingActive { PortalBroadcastParser.parseList(readList().raw) }
            guard.publish(token) {
                _state.value = result.fold(
                    onSuccess = { _state.value.settleFirstPage(it, clock()) { item -> item.id }.withHasMore(false) },
                    onFailure = { failed(_state.value, it) },
                )
            }
        } finally {
            guard.publish(token) {
                _state.value = when (val current = _state.value) {
                    is ListState.Content -> current.copy(meta = current.meta.copy(refreshing = false))
                    is ListState.Empty -> current.copy(meta = current.meta.copy(refreshing = false))
                    else -> current
                }
            }
            mutex.unlock()
        }
    }

    override suspend fun open(item: PortalBroadcastItem) {
        var request: Long? = null
        if (!epoch.publishData(epoch.token()) {
            if (selectedId == item.id && activeDetailToken == detailGuard.current) return@publishData
            rememberDetail()
            request = detailGuard.begin()
            activeDetailToken = request
            selectedId = item.id
            val cached = detailSnapshots[DetailKey(epoch.token(), item.id)]
            val restored = if (cached is ListState.Content) {
                cached.copy(items = cached.items.map { it.copy(item = item) })
            } else cached
            _detail.value = (restored ?: ListState.FirstLoad).startRefresh()
        }) return
        val token = request ?: return
        try {
            val result = catchingActive { PortalBroadcastParser.parseDetail(readDetail(item.id).raw, item) }
            detailGuard.publish(token) {
                _detail.value = result.fold(
                    onSuccess = { _detail.value.settleFirstPage(listOf(it), clock()).withHasMore(false) },
                    onFailure = { failed(_detail.value, it) },
                )
            }
        } finally {
            detailGuard.publish(token) {
                activeDetailToken = null
                _detail.value = settled(_detail.value)
                rememberDetail()
            }
        }
    }

    override fun closeDetail() {
        epoch.publishData(epoch.token()) {
            rememberDetail()
            detailGuard.invalidate()
            activeDetailToken = null
            selectedId = null
            _detail.value = ListState.FirstLoad
        }
    }
}
