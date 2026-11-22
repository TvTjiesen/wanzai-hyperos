package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.appendPage
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.finishRefresh
import com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * **愛講座**（CJH52）的倉庫。**只讀**。
 *
 * ### 為什麼挑這一支做原生
 *
 * B16 探測（`docs/B16-CJH52-PROBE.txt`）確認它 `total: 2541` ——
 * 是這次探到的候選裡**資料量最大**的一個，而且完全只讀
 *（報名/取消報名是寫操作，本專案不碰）。
 *
 * 學生看講座的實際需求是「最近有什麼值得聽的」，
 * 原生列表比 WebView 快得多，也不需要每次重新登入那套流程。
 *
 * ### 分頁
 *
 * 服務端**要求分頁參數**：不帶會回
 * `code 500 Cannot invoke "java.lang.Integer.intValue()" because "pageNum" is null`
 *（探測時踩過）。所以 [PAGE_SIZE] 是必要的，不是可選的最佳化。
 */
interface LectureRepository {
    val state: StateFlow<ListState<Lecture>>
    /** 目前是第幾頁（供「載入更多」判斷）。 */
    val hasMore: StateFlow<Boolean>
    suspend fun refresh()
    suspend fun loadMore()
}

class LiveLectureRepository(
    private val cjh52: Cjh52Client,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : LectureRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<Lecture>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<Lecture>> = _state.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    override val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val inFlight = Mutex()

    @Volatile
    private var page = 1

    init {
        epoch.registerReset {
            guard.invalidate()
            _state.value = ListState.FirstLoad
            _hasMore.value = true
            page = 1
        }
    }

    override suspend fun refresh() {
        val token = guard.begin()
        try {
        inFlight.withLock {
            if (!guard.isCurrent(token)) return
            if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return
            // ★ 刷新的是**已经加载出来的整段窗口**，不是只重取第 1 页。
            //   以前这里固定 fetch(1)，然后把状态换成第一页：用户翻到第 3 页再下拉，
            //   屏幕上会突然只剩 20 条（已加载的两页凭空消失），而 page 还被重置成 1。
            //   窗口要整体换、整体提交；中途失败就保持旧窗口，不提交半份。
            val loadedPages = if (_state.value is ListState.Content) page.coerceAtLeast(1) else 1
            val result = catchingActive { fetchWindow(loadedPages) }
            guard.publish(token) {
                _state.value = result.fold(
                    onSuccess = { window ->
                        page = window.pages
                        _hasMore.value = window.hasMore
                        if (window.items.isEmpty()) {
                            _state.value.settleFirstPage(emptyList(), clock()) { it.id }
                        } else {
                            ListState.Content(
                                items = window.items,
                                meta = ListMeta(
                                    page = window.pages,
                                    hasMore = window.hasMore,
                                    updatedAtMillis = clock(),
                                ),
                            )
                        }
                    },
                    onFailure = { retainCachedOnFailure(_state.value, it.asCjh52Exception()) },
                )
            }
        }
        } finally {
            guard.publish(token) { _state.value = _state.value.finishRefresh() }
        }
    }

    override suspend fun loadMore() {
        val token = guard.begin()
        try {
        inFlight.withLock {
            if (!guard.isCurrent(token)) return
            if (!_hasMore.value || _state.value !is ListState.Content) return
            val next = page + 1
            val result = catchingActive { fetch(next) }
            guard.publish(token) {
                result.fold(
                    onSuccess = { (items, more) ->
                        page = next
                        _hasMore.value = more
                        // 用全專案共用的 appendPage（它會一併處理 meta 與去重）——
                        // 自己手寫合併容易漏掉 hasMore / updatedAt 那些欄位。
                        _state.value = _state.value.appendPage(
                            newItems = items,
                            page = next,
                            hasMore = more,
                            nowMillis = clock(),
                        ) { it.id }
                    },
                    // 翻頁失敗**不清掉已有內容**，只把原因記下（照全專案的做法）。
                    onFailure = { _state.value = retainCachedOnFailure(_state.value, it.asCjh52Exception()) },
                )
            }
        }
        } finally {
            guard.publish(token) { _state.value = _state.value.finishRefresh() }
        }
    }

    private suspend fun fetch(pageNum: Int): Pair<List<Lecture>, Boolean> {
        val body: JsonObject = buildJsonObject {
            put("pageNum", pageNum)
            put("pageSize", PAGE_SIZE)
        }
        val root = cjh52.post(LECTURE_PATH, body)
        val items = LectureParser.parse(root)
        // 回傳數 < 頁大小 ⇒ 沒有下一頁（不要用 total 算）。
        return items to (items.size >= PAGE_SIZE)
    }

    private data class Window(val items: List<Lecture>, val hasMore: Boolean, val pages: Int)

    /** 依次重取 1..pages，再把整段窗口交回去；任何一页失败都让整次刷新失败（旧窗口不动）。 */
    private suspend fun fetchWindow(pages: Int): Window {
        val items = mutableListOf<Lecture>()
        var hasMore = true
        var lastPage = 0
        for (p in 1..pages) {
            val (pageItems, pageMore) = fetch(p)
            items += pageItems
            lastPage = p
            hasMore = pageMore
            if (pageItems.size < PAGE_SIZE) { hasMore = false; break }
            if (!pageMore) break
        }
        return Window(
            items = items.distinctBy { it.id },
            hasMore = hasMore,
            pages = lastPage.coerceAtLeast(1),
        )
    }

    private fun Throwable.asCjh52Exception(): HzcuException =
        this as? HzcuException ?: HzcuException.Parse("讲座数据解析失败", cause = this)

    private companion object {
        const val LECTURE_PATH = "/dapi/v2/activity/lecture_service/find_lectures"
        const val PAGE_SIZE = 20
    }
}
