package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.appendPage
import com.muyao.wanzaihyperos.data.startLoadMore
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.PortalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * 「我的事務」倉庫（B3）—— 待辦 / 我的申請 / 我的已辦 三個清單。
 *
 * ### 契約（真機實測，見 `docs/B3-WALKTHROUGH.md`）
 *
 * ```
 * POST /api/app/billlist/{listmybill|listmyprebill|listmyfinishbill}
 *   **JSON 包體**（form 一律 400）
 *   { appid: "", page: 1, rows: 10 }
 *   → 讀**頂層 rows[]**
 * ```
 *
 * ### 繼承 A2 的紀律
 *
 * - 切清單時用 [StaleGuard] 丟棄過時回應（否則「待辦」的結果會覆蓋到「已辦」上）；
 * - 刷新失敗**保留已有內容**並標 stale，不清空；
 * - 分頁按**返回條數**判「還有沒有下一頁」，不依賴 `total`；
 * - 全走 [PortalApi] → [HzcuClient] 的限流閘門（不繞過 QPS 紅線）。
 */
interface PortalBillRepository {
    /** 當前選中的清單。 */
    val bucket: StateFlow<PortalBillBucket>
    /** 當前清單的列表狀態。 */
    val state: StateFlow<ListState<PortalBill>>

    /** 重新拉當前清單。 */
    suspend fun refresh()

    /** 切到另一個清單（立刻拉資料）。 */
    suspend fun selectBucket(bucket: PortalBillBucket)

    /** 載入下一頁。 */
    suspend fun loadMore()
}

class LivePortalBillRepository(
    private val api: PortalApi,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : PortalBillRepository {

    private val guard = StaleGuard(epoch)

    private val _bucket = MutableStateFlow(PortalBillBucket.TODO)
    override val bucket: StateFlow<PortalBillBucket> = _bucket.asStateFlow()

    private val _state = MutableStateFlow<ListState<PortalBill>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<PortalBill>> = _state.asStateFlow()

    /** Reject duplicate page loads; activeReadToken coalesces the selected bucket's reads. */
    private val inFlight = Mutex()

    private var activeReadToken: Long? = null
    private data class BucketKey(val owner: Long, val bucket: PortalBillBucket)
    private val snapshots = mutableMapOf<BucketKey, ListState<PortalBill>>()

    private fun settled(state: ListState<PortalBill>): ListState<PortalBill> = when (state) {
        is ListState.Content -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
        is ListState.Empty -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
        else -> state
    }

    private fun rememberBucket() {
        snapshots[BucketKey(epoch.token(), _bucket.value)] = settled(_state.value)
    }

    private fun failed(error: Throwable): ListState<PortalBill> {
        val failure = error.asPortalException()
        val current = settled(_state.value)
        return if (failure is HzcuException.LoginExpired) when (current) {
            is ListState.Content -> current.copy(meta = current.meta.copy(stale = true, lastRefreshError = failure))
            is ListState.Empty -> current.copy(meta = current.meta.copy(stale = true, lastRefreshError = failure))
            else -> retainCachedOnFailure(current, failure)
        } else retainCachedOnFailure(current, failure)
    }

    init {
        epoch.registerReset {
            guard.invalidate()
            snapshots.clear()
            activeReadToken = null
            _bucket.value = PortalBillBucket.TODO
            _state.value = ListState.FirstLoad
        }
    }

    override suspend fun refresh() {
        var request: Long? = null
        if (!epoch.publishData(epoch.token()) {
            if (activeReadToken == guard.current) return@publishData
            request = guard.begin()
            activeReadToken = request
        }) return
        refreshWindow(request ?: return)
    }

    override suspend fun selectBucket(bucket: PortalBillBucket) {
        var request: Long? = null
        if (!epoch.publishData(epoch.token()) {
            if (_bucket.value == bucket && activeReadToken == guard.current) return@publishData
            rememberBucket()
            request = guard.begin()
            activeReadToken = request
            _bucket.value = bucket
            _state.value = snapshots[BucketKey(epoch.token(), bucket)] ?: ListState.FirstLoad
        }) return
        refreshWindow(request ?: return)
    }

    private suspend fun refreshWindow(token: Long) {
        var bucket: PortalBillBucket? = null
        var pages = 1
        if (!guard.publish(token) {
            bucket = _bucket.value
            pages = (_state.value.metaOrNull?.page ?: 1).coerceAtLeast(1)
            _state.value = settled(_state.value).startRefresh()
        }) return
        val selected = bucket ?: return
        try {
            val result = catchingActive {
                val items = mutableListOf<PortalBill>()
                var lastPage = 1
                var hasMore = false
                for (page in 1..pages) {
                    if (!guard.isCurrent(token)) return
                    val response = fetchPage(selected, page)
                    if (!guard.isCurrent(token)) return
                    items += response.items
                    lastPage = page
                    hasMore = response.hasMore
                    if (!hasMore) break
                }
                val unique = items.distinctBy { it.id }
                val meta = ListMeta(page = lastPage, hasMore = hasMore, updatedAtMillis = clock())
                if (unique.isEmpty()) ListState.Empty(meta) else ListState.Content(unique, meta)
            }
            guard.publish(token) {
                _state.value = result.fold(onSuccess = { it }, onFailure = { failed(it) })
            }
        } finally {
            guard.publish(token) {
                activeReadToken = null
                _state.value = settled(_state.value)
                rememberBucket()
            }
        }
    }

    override suspend fun loadMore() {
        if (!inFlight.tryLock()) return
        try {
            var request: Long? = null
            var bucket: PortalBillBucket? = null
            var previous: ListState.Content<PortalBill>? = null
            if (!epoch.publishData(epoch.token()) {
                val current = _state.value as? ListState.Content ?: return@publishData
                if (!current.meta.hasMore || activeReadToken == guard.current) return@publishData
                previous = current
                bucket = _bucket.value
                request = guard.begin()
                activeReadToken = request
                _state.value = current.startLoadMore()
            }) return
            val token = request ?: return
            val content = previous ?: return
            val selected = bucket ?: return
            try {
                val nextPage = content.meta.page + 1
                val result = catchingActive { fetchPage(selected, nextPage) }
                guard.publish(token) {
                    _state.value = result.fold(
                        onSuccess = { content.appendPage(it.items, nextPage, it.hasMore, clock()) { bill -> bill.id } },
                        onFailure = { failed(it) },
                    )
                }
            } finally {
                guard.publish(token) {
                    activeReadToken = null
                    _state.value = settled(_state.value)
                    rememberBucket()
                }
            }
        } finally { inFlight.unlock() }
    }

    private class Page(val items: List<PortalBill>, val hasMore: Boolean)

    private suspend fun fetchPage(bucket: PortalBillBucket, page: Int): Page {
        val response = api.fetchBills(bucket = bucket, page = page)
        val returned = response.listPayload.orEmpty()
        val rows = returned.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        val items = PortalBillParser.parse(rows)
        // ★ 判「還有下一頁」用**返回條數 == 每頁條數**（門戶自己的判據），
        //   不用 `total` —— A2 已實測 total 不可靠（total=20 而 allrecords=192）。
        val hasMore = returned.size >= PortalApi.BILL_PAGE_SIZE
        return Page(items, hasMore)
    }
}

// 註：`Throwable.asPortalException()` 已由 `PortalRepositories.kt` 以 `internal`
// 定義在同一個 package 裡，本文件**直接用它**。
// 先前我在這裡又寫了一份，Kotlin 報 `Conflicting overloads` ——
// 同 package 內的同名擴展函式會衝突，不要重複定義。
