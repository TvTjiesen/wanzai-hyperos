package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.appendPage
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startLoadMore
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.withHasMore
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.net.isAuthFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
) : PortalBillRepository {

    private val guard = StaleGuard()

    private val _bucket = MutableStateFlow(PortalBillBucket.TODO)
    override val bucket: StateFlow<PortalBillBucket> = _bucket.asStateFlow()

    private val _state = MutableStateFlow<ListState<PortalBill>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<PortalBill>> = _state.asStateFlow()

    /**
     * 單飛。**必須串行**：`loadMore` 與 `refresh` 併發時，
     * 兩個回應會按到達順序覆蓋，出現「列表裡同一頁出現兩次」這種難查的症狀。
     */
    private val inFlight = Mutex()

    /** 當前已載入到第幾頁（下一頁 = 這個 + 1）。 */
    private var loadedPage = 0

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()
            val result = runCatching { fetchPage(_bucket.value, page = 1) }
            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { page ->
                    loadedPage = 1
                    // settleFirstPage 自己會把 hasMore 設成 true（「取滿一頁才可能還有」），
                    // 所以取不滿時要**顯式覆蓋**成 false。
                    _state.value
                        .settleFirstPage(page.items, clock()) { it.id }
                        .withHasMore(page.hasMore)
                },
                onFailure = { _state.value.failWith(it.asPortalException()) },
            )
        }
    }

    override suspend fun selectBucket(bucket: PortalBillBucket) {
        if (_bucket.value == bucket && _state.value is ListState.Content) return
        inFlight.withLock {
            // 切清單 = 換資料源，所以**先作廢在途回應**，再把列表清成「首次載入」。
            // 不清空的話，切過去的一瞬間會顯示**上一個清單的內容** ——
            // 那比空白更糟：使用者會以為「已辦裡有這些待辦」。
            guard.begin()
            _bucket.value = bucket
            loadedPage = 0
            _state.value = ListState.FirstLoad
        }
        refresh()
    }

    override suspend fun loadMore() {
        inFlight.withLock {
            val current = _state.value
            if (current !is ListState.Content) return
            if (current.meta.hasMore == false) return
            val token = guard.begin()
            _state.value = current.startLoadMore()
            val nextPage = loadedPage + 1
            val result = runCatching { fetchPage(_bucket.value, page = nextPage) }
            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { page ->
                    loadedPage = nextPage
                    _state.value.appendPage(
                        newItems = page.items,
                        page = nextPage,
                        hasMore = page.hasMore,
                        nowMillis = clock(),
                    ) { it.id }
                },
                onFailure = { _state.value.failWith(it.asPortalException()) },
            )
        }
    }

    private class Page(val items: List<PortalBill>, val hasMore: Boolean)

    private suspend fun fetchPage(bucket: PortalBillBucket, page: Int): Page {
        val response = api.fetchBills(bucket = bucket, page = page)
        val rows = response.listPayload.orEmpty().mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        val items = PortalBillParser.parse(rows)
        // ★ 判「還有下一頁」用**返回條數 == 每頁條數**（門戶自己的判據），
        //   不用 `total` —— A2 已實測 total 不可靠（total=20 而 allrecords=192）。
        val hasMore = rows.size >= PortalApi.BILL_PAGE_SIZE
        return Page(items, hasMore)
    }
}

// 註：`Throwable.asPortalException()` 已由 `PortalRepositories.kt` 以 `internal`
// 定義在同一個 package 裡，本文件**直接用它**。
// 先前我在這裡又寫了一份，Kotlin 報 `Conflicting overloads` ——
// 同 package 內的同名擴展函式會衝突，不要重複定義。
