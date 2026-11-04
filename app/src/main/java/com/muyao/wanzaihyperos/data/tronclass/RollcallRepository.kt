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
        // ① 先查**進行中的清單** —— 這是唯一確定包含「正在進行的點名」的端點。
        //
        //    實憑據（2026-11-04 15:16 那次真實提交，機器上 `files/http_cache/` 的原文）：
        //    提交前 10 秒那份快照裡就躺著這條點名
        //      rollcall_id:252446, rollcall_status:"in_progress", status:"absent"
        //    —— 同一份 JSON 裡，`rollcall_status` 是**整場點名**的狀態，
        //    頂層 `status` 才是**我**在這場點名的狀態（未簽時是 `absent`）。
        //    而課程考勤記錄那份（`files/http_cache/f169e2ea…json`）**只有兩條已結束的**
        //    （231614 / 242417，都是 finished），根本不包含進行中的那一條 ——
        //    所以只靠考勤記錄核對，在「剛簽完」那一刻永遠查不到，等於白核。
        //
        //    ⚠️ 必須繞過緩存：不清掉就會讀到**提交之前**的快照，永遠是 `absent`，
        //    那會把一次成功的簽到核成失敗 —— 比不核還糟。
        client.bypassCacheOnce()
        // ⚠️ 傳**已緩存**的學號就好：`/api/radar/rollcalls` 的每條記錄頂層 `status`
        //    本來就是「我」的狀態，不需要拿學號去 `student_rollcalls[]` 裡挑；
        //    這裡若走 `myStudentId()` 會白多打一次 `/api/profile`。
        val ongoing = runCatching {
            client.ongoingRollcalls(if (myIdResolved) cachedMyId else null)
        }.getOrNull()
        ongoing?.firstOrNull { it.id == rollcallId }?.let { row ->
            // 解析器現在把 `status`（生命週期）與 `myStatus`（我的考勤）分開了；
            // 核對要的是**我的考勤**。`row.status` 只在它的值確實是考勤值時才兜底
            // （少數端點把兩者擺在同一個位置，靠值的形狀分辨）。
            row.myStatus.takeIf { it.isNotBlank() }?.let { return it }
            row.status.takeIf { TronAttendanceStates.isAttendance(it) }?.let { return it }
        }

        // ② 再查課程考勤記錄，補「老師提交後很快結束了點名」那種情形：
        //    這時清單裡已經沒有它，但考勤記錄裡會有。
        //    `courseId == 0` 是掃描器合成的占位記錄常用的值（沒有課程就無處可查），
        //    所以要在發任何請求**之前**就返回 —— 別白打一次 `/api/profile`。
        val course = courseId.takeIf { it > 0 } ?: return null
        val me = myStudentId() ?: return null
        val rows = runCatching { client.courseAttendance(course, me) }.getOrNull() ?: return null
        return rows.firstOrNull { it.rollcallId == rollcallId }?.myStatus?.takeIf { it.isNotBlank() }
    }
}
