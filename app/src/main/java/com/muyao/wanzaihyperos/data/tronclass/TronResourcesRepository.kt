package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.*
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.TronclassClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*

interface TronResourcesRepository {
    val state: StateFlow<ListState<TronResource>>
    val query: StateFlow<TronResourcesQuery>
    suspend fun open(query: TronResourcesQuery = TronResourcesQuery())
    suspend fun refresh()
    suspend fun loadMore()
}

class LiveTronResourcesRepository(
    private val client: TronclassClient,
    private val epoch: AccountEpoch = AccountEpoch(),
    private val clock: () -> Long = System::currentTimeMillis,
) : TronResourcesRepository {
    private val guard = StaleGuard(epoch)
    private val snapshots = linkedMapOf<Pair<Long, TronResourcesQuery>, ListState<TronResource>>()
    private val _state = MutableStateFlow<ListState<TronResource>>(ListState.FirstLoad)
    override val state = _state.asStateFlow()
    private val _query = MutableStateFlow(TronResourcesQuery())
    override val query = _query.asStateFlow()
    private var active: Long? = null

    init {
        epoch.registerReset {
            guard.invalidate(); snapshots.clear(); active = null
            _query.value = TronResourcesQuery(); _state.value = ListState.FirstLoad
        }
    }

    override suspend fun open(query: TronResourcesQuery) = read(query, more = false)
    override suspend fun refresh() = read(_query.value, more = false)
    override suspend fun loadMore() = read(_query.value, more = true)

    private suspend fun fetch(query: TronResourcesQuery, page: Int, current: () -> Boolean): TronResourcesPageData {
        val folders = client.userResources(query, folders = true, page = page, forceRefresh = true)
        currentCoroutineContext().ensureActive()
        if (!current()) throw kotlinx.coroutines.CancellationException("Resource selection changed")
        val files = client.userResources(query, folders = false, page = page, forceRefresh = true)
        if (folders.page != page || files.page != page) throw HzcuException.Parse("资源返回了其他分页")
        return TronResourcesParser.merge(folders, files)
    }

    private suspend fun read(query: TronResourcesQuery, more: Boolean) {
        val owner = epoch.token()
        var token: Long? = null
        var before: ListState<TronResource> = ListState.FirstLoad
        epoch.publishData(owner) {
            if (_query.value == query && active != null) return@publishData
            if (more && (_query.value != query || _state.value !is ListState.Content || _state.value.metaOrNull?.hasMore != true)) return@publishData
            snapshots[owner to _query.value] = tronReadSettled(_state.value)
            if (_query.value != query) _state.value = snapshots[owner to query] ?: ListState.FirstLoad
            _query.value = query
            before = tronReadSettled(_state.value)
            token = guard.begin(); active = token
            _state.value = if (more) before.startLoadMore() else before.startRefresh()
        }
        val read = token ?: return
        val job = currentCoroutineContext()[Job]
        try {
            if (job != null && !epoch.track(owner, job)) return
            val result = catchingActive {
                val last = before.metaOrNull?.page ?: 1
                val first = if (more) last + 1 else 1
                val end = if (more) first else last
                if (end !in 1..1000) throw HzcuException.Parse("资源分页超出可读取范围")
                val rows = if (more) before.items.toMutableList() else mutableListOf()
                var page = first
                var hasMore = false
                for (requested in first..end) {
                    currentCoroutineContext().ensureActive()
                    if (!guard.isCurrent(read)) throw kotlinx.coroutines.CancellationException("Resource selection changed")
                    val data = fetch(query, requested) { guard.isCurrent(read) }
                    rows.addAll(data.uploads)
                    page = requested; hasMore = data.hasMore
                    if (!hasMore) break
                }
                val meta = ListMeta(page = page, hasMore = hasMore, updatedAtMillis = clock())
                val distinct = rows.distinctBy { it.id }
                if (distinct.isEmpty()) ListState.Empty(meta) else ListState.Content(distinct, meta)
            }
            guard.publish(read) { _state.value = result.fold(onSuccess = { it }, onFailure = { tronReadFailure(before, it) }) }
        } finally {
            guard.publish(read) {
                active = null; _state.value = tronReadSettled(_state.value)
                snapshots[owner to query] = _state.value
                while (snapshots.size > 32) snapshots.remove(snapshots.keys.first())
            }
            if (job != null) epoch.untrack(job)
        }
    }
}
