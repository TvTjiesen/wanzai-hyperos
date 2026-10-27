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
) : TronFeedRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<TronFeed>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronFeed>> = _state.asStateFlow()
    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()

            val result = runCatching {
                // 公告：**必須帶 `conditions={}`**（實測：不帶會 500）。
                val bulletins = runCatching { client.orgBulletins() }
                val todos = runCatching { client.todos() }
                val courses = runCatching { client.myCourses() }

                if (bulletins.isFailure && todos.isFailure && courses.isFailure) {
                    throw bulletins.exceptionOrNull() ?: todos.exceptionOrNull()
                        ?: courses.exceptionOrNull() ?: HzcuException.Parse("学在城院没有取到数据")
                }
                TronFeed(
                    bulletins = bulletins.getOrDefault(emptyList()),
                    todos = todos.getOrDefault(emptyList()),
                    courses = courses.getOrDefault(emptyList()),
                )
            }

            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { _state.value.settleFirstPage(listOf(it), clock()) { f -> f.hashCode() } },
                onFailure = {
                    _state.value.failWith(
                        if (it is HzcuException) it
                        else HzcuException.Parse("课程动态解析失败", cause = it),
                    )
                },
            )
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
) : TronMeRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<TronMe>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronMe>> = _state.asStateFlow()
    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()

            val result = runCatching {
                // 個人信息是**必須**的（沒有它整頁沒意義）。
                val profile = client.profile()
                    ?: throw HzcuException.Parse("没有取到个人资料")

                // 統計各自獨立：取不到就是 null（**不是 0**）。
                val todos = runCatching { client.todos() }.getOrNull()
                val courses = runCatching { client.myCourses() }.getOrNull()
                val rollcalls = runCatching { client.ongoingRollcalls(null) }.getOrNull()

                TronMe(
                    profile = profile,
                    stats = TronMyStats(
                        todoCount = todos?.size,
                        courseCount = courses?.size,
                        openRollcallCount = rollcalls?.count { it.isOpen },
                    ),
                )
            }

            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { _state.value.settleFirstPage(listOf(it), clock()) { m -> m.profile.userNo } },
                onFailure = {
                    _state.value.failWith(
                        if (it is HzcuException) it
                        else HzcuException.Parse("个人资料解析失败", cause = it),
                    )
                },
            )
        }
    }
}
