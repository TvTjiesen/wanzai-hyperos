package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
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
 * 學在城院「**課程動態**」——最近發生了什麼。
 *
 * ### ★ 為什麼這是一個「合成視圖」而不是一個接口
 *
 * 摸底時發現：學在城院首頁的「課程動態」在資料層**不是獨立接口**，
 * 它是各課程活動（作業／公告／討論）的彙總。而我們手上已經有三塊來源。
 *
 * 所以這一頁**不猜一個可能不存在的接口**，而是把已有的資料
 * 按時間排成「最近發生了什麼」。代價是時間軸不如原站完整，
 * 但**準確**比完整重要（原站隨時可開，看到錯的卻會誤導）。
 *
 * ### 三塊來源各自的失敗互不影響
 *
 * 每一塊獨立 `runCatching`：公告掛了不該讓作業也消失。
 * 全掛才當整頁失敗。
 */
data class TronFeed(
    val bulletins: List<TronBulletin>,
    val todos: List<TronTodo>,
    val courses: List<TronCourse>,
    val bulletinsError: HzcuException? = null,
    val todosError: HzcuException? = null,
    val coursesError: HzcuException? = null,
) {
    /** 有沒有任何內容可顯示（用來區分「空」與「有東西」）。 */
    val isEmpty: Boolean get() = bulletins.isEmpty() && todos.isEmpty() && courses.isEmpty()
}

interface TronFeedRepository {
    val state: StateFlow<ListState<TronFeed>>
    suspend fun refresh()
}

class LiveTronFeedRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : TronFeedRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<TronFeed>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronFeed>> = _state.asStateFlow()
    private val inFlight = Mutex()

    init { epoch.registerReset { guard.invalidate(); _state.value = ListState.FirstLoad } }

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            val previousState = _state.value as? ListState.Content
            val previous = previousState?.items?.firstOrNull()
            guard.publish(token) { _state.value = _state.value.startRefresh() }
            val bulletins = catchingActive { client.orgBulletins() }
            val todos = catchingActive { client.todos() }
            val courses = catchingActive { client.myCourses() }
            guard.publish(token) {
                if (bulletins.isFailure && todos.isFailure && courses.isFailure) {
                    _state.value = _state.value.failWith(bulletins.exceptionOrNull()!!.tronPartFailure())
                } else {
                    val feed = TronFeed(
                        bulletins.getOrElse { previous?.bulletins.orEmpty() },
                        todos.getOrElse { previous?.todos.orEmpty() },
                        courses.getOrElse { previous?.courses.orEmpty() },
                        bulletins.exceptionOrNull()?.tronPartFailure(),
                        todos.exceptionOrNull()?.tronPartFailure(),
                        courses.exceptionOrNull()?.tronPartFailure(),
                    )
                    val partial = feed.bulletinsError ?: feed.todosError ?: feed.coursesError
                    val settled = _state.value.settleFirstPage(listOf(feed), clock()) { it.hashCode() }
                    _state.value = if (settled is ListState.Content && partial != null) {
                        settled.copy(meta = settled.meta.copy(stale = true, lastRefreshError = partial,
                            updatedAtMillis = previousState?.meta?.updatedAtMillis))
                    } else settled
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 「我的」
// ---------------------------------------------------------------------------

/** 「我的」頁的一包資料：個人信息 + 統計。 */
data class TronMe(
    val profile: TronProfile,
    val stats: TronMyStats,
    val statsErrors: List<HzcuException> = emptyList(),
    val ownerGeneration: Long = 0L,
)

interface TronMeRepository {
    val state: StateFlow<ListState<TronMe>>
    suspend fun refresh()
}

/**
 * 「我的」頁倉庫。
 *
 * ### ★ 統計數字**不另外打接口**
 *
 * 待辦數／課程數／進行中簽到數，全部從**已經取過的資料**算出來。
 * 多打三個請求只為了顯示三個數字，不划算 —— 而限流紅線（全局 QPS ≤ 1）
 * 要求我們省著用。
 *
 * 代價是這一頁刷新時要順帶取三份資料（那三份在首頁也取）。
 * 但那是**已有快取/單飛**的倉庫在做，實際請求數不重複。
 */
class LiveTronMeRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : TronMeRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<TronMe>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronMe>> = _state.asStateFlow()
    private val inFlight = Mutex()

    init { epoch.registerReset { guard.invalidate(); _state.value = ListState.FirstLoad } }

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            val previousTime = (_state.value as? ListState.Content)?.meta?.updatedAtMillis
            guard.publish(token) { _state.value = _state.value.startRefresh() }
            val result = catchingActive {
                val profile = client.profile() ?: throw HzcuException.Parse("没有取到个人资料")
                val todos = catchingActive { client.todos() }
                val courses = catchingActive { client.myCourses() }
                val rollcalls = catchingActive { client.ongoingRollcalls(null) }
                TronMe(
                    profile,
                    TronMyStats(todos.getOrNull()?.size, courses.getOrNull()?.size,
                        rollcalls.getOrNull()?.count { it.isOpen }),
                    listOfNotNull(todos.exceptionOrNull(), courses.exceptionOrNull(),
                        rollcalls.exceptionOrNull()).map { it.tronPartFailure() },
                    ownerGeneration = epoch.token(),
                )
            }
            guard.publish(token) {
                _state.value = result.fold(
                    onSuccess = { me ->
                        val settled = _state.value.settleFirstPage(listOf(me), clock()) { it.profile.userNo }
                        if (settled is ListState.Content && me.statsErrors.isNotEmpty()) {
                            settled.copy(meta = settled.meta.copy(stale = true,
                                lastRefreshError = me.statsErrors.first(), updatedAtMillis = previousTime))
                        } else settled
                    },
                    onFailure = { _state.value.failWith(it.tronPartFailure()) },
                )
            }
        }
    }
}
