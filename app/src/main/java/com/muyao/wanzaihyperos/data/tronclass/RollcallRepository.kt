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
 * 讀取失敗重試很自然；但**簽到重試等於可能送出兩筆記錄**。
 * 所以 [answer] 只發一次，失敗就把服務端的原話回報 ——
 * 由使用者決定要不要再按一次。這與 `TronclassClient.answerRollcall`
 * 的 `maxAttempts = 1` 是同一個決定。
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
}
