package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.appendPage
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.StaleGuard
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
) : LectureRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<Lecture>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<Lecture>> = _state.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    override val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val inFlight = Mutex()

    @Volatile
    private var page = 1

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()
            val result = runCatching { fetch(1) }
            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { (items, more) ->
                    page = 1
                    _hasMore.value = more
                    // ★ 這個站的 total 是可信的（與門戶不同），但**翻頁仍用
                    //   「本次回傳數 < 頁大小」判斷**（與全專案一致）——
                    //   寧可多要一次也不要少顯示。
                    _state.value.settleFirstPage(items, clock()) { it.id }
                },
                onFailure = { _state.value.failWith(it.asCjh52Exception()) },
            )
        }
    }

    override suspend fun loadMore() {
        inFlight.withLock {
            if (!_hasMore.value) return
            val next = page + 1
            val token = guard.begin()
            val result = runCatching { fetch(next) }
            if (!guard.isCurrent(token)) return
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
                onFailure = { _state.value = _state.value.failWith(it.asCjh52Exception()) },
            )
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

    private fun Throwable.asCjh52Exception(): HzcuException =
        this as? HzcuException ?: HzcuException.Parse("讲座数据解析失败", cause = this)

    private companion object {
        const val LECTURE_PATH = "/dapi/v2/activity/lecture_service/find_lectures"
        const val PAGE_SIZE = 20
    }
}
