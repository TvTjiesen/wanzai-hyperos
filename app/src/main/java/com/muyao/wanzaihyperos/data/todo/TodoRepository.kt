package com.muyao.wanzaihyperos.data.todo

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.portal.PortalBillBucket
import com.muyao.wanzaihyperos.data.portal.PortalBillRepository
import com.muyao.wanzaihyperos.net.HzcuException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 多來源待辦倉庫（B6）。
 *
 * ### 它做什麼
 *
 * 把**幾個互不相通**的系統裡的「要辦的事」聚到一個看板：
 *
 * | 來源 | 內容 | 取數方式 |
 * |------|------|---------|
 * | 門戶 | 流程審批（請假、申請…） | [PortalBillRepository]（已可用） |
 * | 學在城院 | 作業 | [com.muyao.wanzaihyperos.net.TronclassClient.todos]（已可用） |
 *
 * ### ★ 學在城院那一路的鑑權是怎麼通的（2026-10-12 真機取證）
 *
 * 契約與鑑權方式都查清了：
 *
 * ```jsonc
 * // 域：courses.hzcu.edu.cn（不是 mobile！）
 * GET /api/todos
 * 頭：X-SESSION-ID: <91 字元>、X-Requested-With: XMLHttpRequest
 * → { todo_list: [{ id, title, course_name, course_code, type, end_time, is_locked, … }] }
 * ```
 *
 * `X-SESSION-ID` 不在 identity 的 token 對裡（`TronclassSession` 存的是
 * accessToken/refreshToken），它是 SPA 用 token 對換來的、只存在 WebView 的
 * `localStorage.global` 裡。所以這條路由自己走了一次
 * `POST /api/login?login=access_token` 把 token 對換成 sessionID ——
 * 實作在 [com.muyao.wanzaihyperos.net.TronclassClient.sessionId]，失敗後單飛續期
 * 也在那裡。**這曾經是「待接入」，現在不是了。**
 *
 * ### 為什麼失敗時給 null 而不是空清單
 *
 * 空清單在界面上是「0 條作業」，那等於斷言「你沒作業」；而失敗時我們**不知道**。
 * 顯示「這個來源還沒接通」比顯示「0 條待辦」誠實得多。
 *
 * ### 為什麼要「來源內去重、不跨來源」
 *
 * 兩邊的 id 體系完全不同（門戶是字串 billid，學在城院是數字 id）。
 * 跨來源「去重」會把兩邊碰巧相同的 key 誤刪。
 */
interface TodoRepository {
    val state: StateFlow<ListState<TodoBoard>>
    suspend fun refresh()
}

class LiveTodoRepository(
    private val bills: PortalBillRepository,
    private val tron: com.muyao.wanzaihyperos.net.TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : TodoRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<TodoBoard>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TodoBoard>> = _state.asStateFlow()
    private val inFlight = Mutex()

    init { epoch.registerReset { guard.invalidate(); _state.value = ListState.FirstLoad } }

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            val previousState = _state.value as? ListState.Content
            val previous = previousState?.items?.firstOrNull()
            guard.publish(token) { _state.value = _state.value.startRefresh() }

            // ---- 來源 1：門戶的待辦 ----------------------------------------
            //
            // 回一個 SourceResult 而不是裸清單：要把「還有下一頁」一起帶出來。
            val portalResult = catchingActive {
                bills.selectBucket(PortalBillBucket.TODO)
                // selectBucket completes its refresh, including failed/expired outcomes.
                val s = bills.state.value
                when (s) {
                    is ListState.Content -> {
                        val mapped = s.items.map { it.toTodoItem() }
                        // ★ 只取了第一頁：`hasMore` 為真時總數只是**下限**，
                        //   不能當「共 N 條」顯示（見 TodoBoard.knownTotal 的說明）；
                        //   取不滿一頁才說明這**就是全部**，那個數是準確的。
                        SourceResult(
                            source = TodoSource.PORTAL,
                            items = mapped,
                            total = if (s.meta.hasMore) null else mapped.size,
                            hasMore = s.meta.hasMore,
                            error = s.meta.lastRefreshError?.let(::shortReason)
                                ?: if (s.meta.stale) "这是上次取得的待办，当前结果未确认" else null,
                        )
                    }
                    is ListState.Empty -> SourceResult(
                        source = TodoSource.PORTAL, items = emptyList(),
                        total = if (s.meta.stale || s.meta.lastRefreshError != null) null else 0,
                        error = s.meta.lastRefreshError?.let(::shortReason)
                            ?: if (s.meta.stale) "这是上次取得的待办，当前结果未确认" else null,
                    )
                    is ListState.SessionExpired -> throw HzcuException.LoginExpired("门户登录状态已失效")
                    is ListState.Failed -> throw s.error
                    else -> throw HzcuException.Transport("门户待办未返回结果")
                }
            }

            // ---- 來源 2：學在城院的作業 ------------------------------------
            //
            // 鏈路（真機探測 2026-10-12）：Keycloak accessToken
            //   → POST /api/login?login=access_token → 響應頭 X-SESSION-ID
            //   → GET /api/todos → 19 條。實現在 TronclassClient。
            //
            // 失敗時仍給 null 而不是空清單：空清單在界面上是「0 條作業」，
            // 那等於斷言「你沒作業」；而失敗時我們**不知道**。
            val tronResult = catchingActive {
                val list = tron.todos()
                SourceResult(
                    source = TodoSource.TRONCLASS,
                    items = list.map { it.toTodoItem() },
                    // /api/todos 不分頁（實測一次給全部），所以條數就是總數。
                    total = list.size,
                )
            }

            val board = TodoBoardBuilder.build(
                listOf(
                    tronResult.getOrElse { e ->
                        SourceResult(
                            source = TodoSource.TRONCLASS,
                            items = previous?.groups?.firstOrNull { it.source == TodoSource.TRONCLASS }?.items,
                            error = shortReason(e),
                        )
                    },
                    portalResult.getOrElse { e ->
                        SourceResult(
                            source = TodoSource.PORTAL,
                            items = previous?.groups?.firstOrNull { it.source == TodoSource.PORTAL }?.items,
                            error = shortReason(e),
                        )
                    },
                ),
            )

            guard.publish(token) {
            _state.value = when {
                board.allFailed -> _state.value.failWith(
                    portalResult.exceptionOrNull()?.let {
                        if (it is HzcuException) it else HzcuException.Transport("待办取数失败")
                    } ?: HzcuException.Transport("待办取数失败"),
                )
                // 只有一個「項目」：整個看板。包在 ListState 裡是為了**沿用既有的
                // 載入/空/錯誤/stale 狀態機**，不必為這一頁再造一套。
                // 代價是「一項」這個語義有點繞，故在此註明。
                else -> ListState.Content(
                    items = listOf(board),
                    meta = com.muyao.wanzaihyperos.data.ListMeta(
                        updatedAtMillis = if (board.groups.any { it.failed }) previousState?.meta?.updatedAtMillis else clock(),
                        stale = board.groups.any { it.failed },
                        lastRefreshError = if (board.groups.any { it.failed })
                            (portalResult.exceptionOrNull() ?: tronResult.exceptionOrNull())?.tronTodoFailure()
                                ?: HzcuException.Transport("部分待办来源尚未确认") else null,
                    ),
                )
            }
            }
        }
    }

    private fun Throwable.tronTodoFailure(): HzcuException =
        this as? HzcuException ?: HzcuException.Transport("部分待办取数失败")

    /**
     * 把異常收成一句**給使用者看**的短話。
     *
     * 為什麼不直接把 `message` 顯示出來：那些消息裡可能有 URL、參數名、
     * 甚至（別處的）憑據片段。只給一句人能懂的。
     */
    private fun shortReason(t: Throwable): String = when (t) {
        is HzcuException.LoginExpired -> "登录状态已失效，请重新登录"
        is HzcuException.Transport, is HzcuException.RequestTimeout -> "网络不可用"
        is HzcuException.Parse -> "服务端返回了无法解析的内容"
        // ⚠️ 不要把所有別的異常都說成「取數失敗」——
        //   那句話讓真機排查時完全看不出方向（分不清 HTTP 碼、超時、還是解析）。
        //   帶上異常類型名（**不含任何消息文本**，避免服務端文案裡的憑據片段）。
        is HzcuException.HttpStatus -> "服务端返回 HTTP ${t.statusCode}"
        else -> "取数失败（${t.javaClass.simpleName}）"
    }
}
