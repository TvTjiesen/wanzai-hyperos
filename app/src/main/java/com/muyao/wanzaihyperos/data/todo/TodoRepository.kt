package com.muyao.wanzaihyperos.data.todo

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.hasSettled
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.portal.PortalBillBucket
import com.muyao.wanzaihyperos.data.portal.PortalBillRepository
import com.muyao.wanzaihyperos.net.HzcuException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * | 學在城院 | 作業 | 🟡 **待接入**（見下） |
 *
 * ### ★ 為什麼學在城院那一路**先留空**
 *
 * 它的契約與鑑權方式**都已經查清**了（真機取證）：
 *
 * ```jsonc
 * // 域：courses.hzcu.edu.cn（不是 mobile！）
 * GET /api/todos
 * 頭：X-SESSION-ID: <91 字元>、X-Requested-With: XMLHttpRequest
 * → { todo_list: [{ id, title, course_name, course_code, type, end_time, is_locked, … }] }
 * ```
 *
 * **但 `X-SESSION-ID` 我們手上沒有**：`TronclassSession` 存的是 identity **token 對**
 * （accessToken/refreshToken），而 sessionID 是 SPA 拿它換來的、只存在
 * WebView 的 `localStorage.global` 裡。
 *
 * 要接上它，得自己走一次 `POST /api/login?login=access_token` 把 token 對換成
 * sessionID —— 那是一個**新的鑑權流程**，需要單獨取證與驗證
 * （A4 只是把它交給 SPA 做，我們從沒自己調過）。
 *
 * **所以這一輪的處理是：框架做好、門戶接通、學在城院標為「待接入」並在界面上
 * 明確顯示「尚未接入」** —— 而不是編一個假的空清單。
 * 顯示「這個來源還沒接」比顯示「0 條待辦」誠實得多。
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
    private val clock: () -> Long = System::currentTimeMillis,
) : TodoRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<TodoBoard>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TodoBoard>> = _state.asStateFlow()
    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()

            // ---- 來源 1：門戶的待辦 ----------------------------------------
            //
            // 回一個 SourceResult 而不是裸清單：要把「還有下一頁」一起帶出來。
            val portalResult = runCatching {
                bills.selectBucket(PortalBillBucket.TODO)
                // 等倉庫真的落定（它自己是單飛 + StaleGuard 的）。
                // 用 hasSettled 而不是「!= Loading」：ListState 裡根本沒有 Loading，
                // 未落定的態是 FirstLoad。
                val s = bills.state.first { it.hasSettled }
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
                        )
                    }
                    is ListState.Empty ->
                        SourceResult(source = TodoSource.PORTAL, items = emptyList(), total = 0)
                    is ListState.SessionExpired -> throw HzcuException.LoginExpired("门户登录状态已失效")
                    is ListState.Failed -> throw s.error
                    else -> throw HzcuException.Transport("门户待办未返回结果")
                }
            }

            // ---- 來源 2：學在城院的作業（**待接入**）------------------------
            //
            // 刻意 items=null + 一句明確的原因，而不是空清單：
            // 空清單在界面上表現成「0 條」，那等於告訴使用者「你沒有作業」——
            // 而事實是**我們還沒接**。這是兩種完全不同的意思。
            //
            // ⚠️ 這裡的文字是**給使用者看的**：只說「還沒接通」這個事實，
            //   不提 sessionID、代碼註釋這類內部細節（第一版寫了「見代碼註釋」，
            //   真機截圖裡就露出來了 —— 那是把開發筆記印在產品上）。
            //   技術原因記在本類註釋與 docs/B6-WALKTHROUGH.md，那才是它該在的地方。
            val tronResult = SourceResult(
                source = TodoSource.TRONCLASS,
                items = null,
                error = "这个来源还没接通，暂时看不到作业",
            )

            val board = TodoBoardBuilder.build(
                listOf(
                    tronResult,
                    portalResult.getOrElse { e ->
                        SourceResult(
                            source = TodoSource.PORTAL,
                            items = null,
                            error = shortReason(e),
                        )
                    },
                ),
            )

            if (!guard.isCurrent(token)) return
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
                        updatedAtMillis = clock(),
                        stale = _state.value.hasSettled,
                    ),
                )
            }
        }
    }

    /**
     * 把異常收成一句**給使用者看**的短話。
     *
     * 為什麼不直接把 `message` 顯示出來：那些訊息裡可能有 URL、參數名、
     * 甚至（別處的）憑據片段。只給一句人能懂的。
     */
    private fun shortReason(t: Throwable): String = when (t) {
        is HzcuException.LoginExpired -> "登录状态已失效"
        is HzcuException.Transport, is HzcuException.RequestTimeout -> "网络不可用"
        is HzcuException.Parse -> "服务端返回了无法解析的内容"
        else -> "取数失败"
    }
}
