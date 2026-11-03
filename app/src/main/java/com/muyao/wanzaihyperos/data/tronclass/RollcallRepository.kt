package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.TronclassClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 學在城院**簽到**倉庫。
 *
 * ### 只做兩件事
 *
 * 1. **看**：有哪些簽到、我簽了沒（唯讀，無風險）
 * 2. **簽**：數字 / 自助 / 掃碼（寫操作，**只在使用者點擊時**）
 *
 * ### ★ 為什麼倉庫不自動重試簽到
 *
 * 读取失败重试很自然；但**签到重试等于可能送出两笔记录**。
 * 所以 [answer] 只发一次，失败就把服务端的原话回报 —— 由使用者决定要不要再按一次。
 *
 * 这台 client 的底座在 `net/TronHttp.kt`：`retryOnConnectionFailure(false)`
 * （OkHttp 默认会在连接失败时重试一次，写操作不能吃那个默认）。
 * ⚠️ 上一版这里写的是 `TronclassClient.answerRollcall` 的 `maxAttempts = 1` ——
 *    **那东西不存在**（`maxAttempts` 在 `TronclassClient` 里 0 命中，真身在 `HzcuCasService` 的
 *    另一条链路）。引用不存在的开关 = 给后来的人造假前提，已改成引用真存在的这一行。
 */
interface RollcallRepository {
    val state: StateFlow<ListState<TronRollcall>>
    suspend fun refresh()

    /**
     * 提交一次簽到。**由 UI 在使用者點擊時呼叫**。
     *
     * 回傳服務端的原話（成功或失敗），呼叫方負責顯示。
     * 本方法**不吞錯、不重試**。
     */
    suspend fun answer(
        rollcallId: Int,
        kind: RollcallAnswerKind,
        code: String?,
    ): RollcallAnswerResult

    /**
     * 提交之后回服務端考勤記錄核對一次（**只讀**，不重發寫請求）。
     *
     * 詳見 [LiveRollcallRepository.verifyAttendance] 的註釋：
     * `answer` 的響應形狀我們只有一份樣本，說不清的時候要以**服務端記在冊上的記錄**為準。
     *
     * @return 核對到的出席狀態原文；查不到回 null（= 核對不了，**不是**「沒簽上」）。
     */
    suspend fun verifyAttendance(rollcallId: Int, courseId: Int): String?
}

class LiveRollcallRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : RollcallRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<TronRollcall>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronRollcall>> = _state.asStateFlow()
    private val inFlight = Mutex()

    /**
     * 自己的 user id（來自 `/api/profile`），**帶記憶**。
     *
     * 為什麼要記憶：它在一輪刷新裡會被用到，而 `/api/profile` 是額外一次請求；
     * 每次刷新都重取等於白白多打一次。但**不落盤** —— 它是識別碼，
     * 留在記憶體裡夠了，下次啟動重取。
     */
    @Volatile
    private var cachedMyId: Int? = null
    @Volatile
    private var myIdResolved = false

    private suspend fun myStudentId(): Int? {
        if (myIdResolved) return cachedMyId
        val id = runCatching { client.profileUserId() }.getOrNull()
        cachedMyId = id
        // ★ 只有「真的取到了」才標記已解析：取不到時下次仍會再試，
        //   否則一次網絡抖動會讓「我簽了沒」永遠顯示「—」。
        myIdResolved = id != null
        return id
    }

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()
            // 先確保知道自己是誰（簽到狀態要按 student_id 挑）。
            val me = myStudentId()
            val result = runCatching { client.ongoingRollcalls(me) }
            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { _state.value.settleFirstPage(it, clock()) { r -> r.id } },
                onFailure = {
                    _state.value.failWith(
                        if (it is HzcuException) it
                        else HzcuException.Parse("签到数据解析失败", cause = it),
                    )
                },
            )
        }
    }

    override suspend fun answer(
        rollcallId: Int,
        kind: RollcallAnswerKind,
        code: String?,
    ): RollcallAnswerResult = client.answerRollcall(rollcallId, kind, code)

    /**
     * 提交之后，**回服务端的出勤記錄裡核對一次**（只讀）。
     *
     * ### 為什麼要有這一步
     *
     * 簽到成功與否，最終只有一個權威來源：**教務/學在城院記在冊上的那條記錄**。
     * 之前我們完全靠 `answer` 響應裡猜一個 `status` 欄位 —— 2026-11-03 那次就是
     * 「欄位認錯 → 服務端明明簽上了，App 說沒簽上」。
     * 判據修好了，但**判據本身仍然是在猜響應形狀**：這個站的響應外層不統一
     * （`{rollcalls:[...]}` / `{data:[...]}` / 裸數組都出現過），`answer` 的返回體
     * 更是從來沒有第二份樣本。
     *
     * 所以改成：`answer` 說不清的時候，去查 `GET /api/course/{courseId}/student/{me}/rollcalls`，
     * 看**這次 rollcallId 在不在、我在那條上的 `student_status` 是不是已到**。
     * 這是服務端自己的記賬，比任何欄位猜測都硬。
     *
     * ### 邊界（為什麼不會變成騷擾服務端）
     *
     * - **只在 `answer` 的結果「說不清」時調用**（HTTP 200 但判不出已到/未到）。
     *   明確 4xx/5xx 有服務端原話，不需要核對；明確成功也不需要。
     * - 只讀，不重發寫請求（紅線不動）。
     * - 失敗一律回 null（= 核對不了），由界面照舊顯示 `answer` 的原話，
     *   **絕不因為核對失敗就改口說「沒簽上」**。
     *
     * @return 核對到的出席狀態原文（如 `on_call` / `on_call_fine` / `absent`）；
     *   查不到、解析不出、請求失敗都回 null。
     */
    override suspend fun verifyAttendance(rollcallId: Int, courseId: Int): String? {
        // ⚠️ 先查参数、再取 `myStudentId()`：后者会多打一次 `/api/profile`。
        //    没有 courseId 时这次请求毫无意义（`courseId == 0` 是扫描器合成出来的
        //    占位记录常用的值），所以要在发任何请求**之前**就返回。
        val course = courseId.takeIf { it > 0 } ?: return null
        val me = myStudentId() ?: return null
        val rows = runCatching { client.courseAttendance(course, me) }.getOrNull() ?: return null
        return rows.firstOrNull { it.rollcallId == rollcallId }?.myStatus?.takeIf { it.isNotBlank() }
    }
}
