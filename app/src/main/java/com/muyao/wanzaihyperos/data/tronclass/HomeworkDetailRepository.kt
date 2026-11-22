package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
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
 * **一項作業的詳情**：要求 + 我的提交狀態 + 我交過的每一版 + 提交入口。
 *
 * ## 為什麼要單獨一個倉庫（而不是複用 [HomeworkRepository]）
 *
 * `HomeworkRepository` 是「一門課的清單」，它的狀態型別是
 * `ListState<ClassHomework>`（一份清單）。詳情頁要的是**一項**東西，
 * 而且多兩塊：我的提交記錄、提交這個寫操作。
 * 把雨者塞進同一個倉庫會出現「進詳情頁就把清單狀態清空」的互相踩 ——
 * 分開之後，從待辦直接進詳情也**不需要先知道是哪門課的清單**。
 *
 * ## ★ 三塊資料各自獨立成敗（這是本專案的一貫紀律）
 *
 * | 塊 | 取不到時 |
 * |---|---|
 * | 要求（作業清單裡那一項） | `homework = null` → **不給提交**（不知道規則就發寫請求） |
 * | 我交了沒（status） | `myStatus = null` → 顯示「不知道」而不是「未交」 |
 * | 我的提交記錄 | `submissionsKnown = false` → 不拿它算次數，界面上講明 |
 *
 * 把任何一塊的失敗折疊成「整頁錯誤」都會撒謊：
 * 使用者會以為**全部**都沒取到，於是轉去網頁版重交一次。
 */
data class HomeworkDetail(
    val courseId: Int,
    val courseName: String,
    /** 從待辦/清單帶過來的標題（要求沒取到時至少看得到這是哪項）。 */
    val title: String,
    /**
     * 服務端那一份**作業記錄**（要求、截止、可交次數）。
     *
     * null ＝ 在這門課的作業清單裡**沒找到這一項**。
     * 這與「找到了但沒有說明」是兩件事，界面必須分開講。
     */
    val homework: TronHomework?,
    /** 作業清單那一支**失敗**了（有別於「取到了、但清單裡沒有這一項」）。 */
    val listFailed: Boolean,
    /** 我的提交狀態。null ＝ **不知道**（不是「未交」）。 */
    val myStatus: TronHomeworkStatus?,
    val submissions: List<TronSubmission>,
    /** 提交記錄那一支是否取到了。false ＝ 不知道交過幾版。 */
    val submissionsKnown: Boolean,
    /** 我的 user id（null = `/api/profile` 沒取到 → 拿不到提交記錄）。 */
    val userId: Int?,
) {
    /** 已**正式**提交的次數（草稿不計 —— 草稿不算交过）。 */
    val submittedCount: Int get() = submissions.count { !it.draft }

    /** 草稿數。 */
    val draftCount: Int get() = submissions.count { it.draft }

    /**
     * 現在**能不能**提交。
     *
     * 注意這是「規則上能不能」；服務端仍可能因為我們看不見的條件拒絕。
     * 拒絕時原話會顯示（[com.muyao.wanzaihyperos.data.tronclass.HomeworkSubmitResult.message]）。
     */
    val submittable: Boolean get() = submitBlockReason == null

    /**
     * 不能提交時的**理由**（給使用者看的那一句）。能提交時 null。
     *
     * ★ 為什麼一定要把理由顯示出來：一個變灰的按鈕不帶理由，
     * 使用者只能猜（「是不是壞了」「是不是我沒登入」）。
     * 而這裡最壞的一種是**要求沒取到** —— 那種情況連截止時間都不知道，
     * 更不該把寫請求發出去。
     */
    val submitBlockReason: String?
        get() {
            val hw = homework
                ?: return if (listFailed) "作业要求没取到，先下拉重新加载"
                else "在这门课的作业清单里没找到这项作业，没敢提交"
            return when {
                hw.closed -> "这项作业已经关闭，不能再提交"
                !hw.acceptsAnotherSubmission(submittedCount) ->
                    "已经交了 $submittedCount 次，达到上限 ${hw.submitTimes} 次"
                else -> null
            }
        }
}

interface HomeworkDetailRepository {
    val state: StateFlow<ListState<HomeworkDetail>>

    /**
     * 進入某一項作業的詳情。
     *
     * @param homeworkId 帶過來的 id —— **可能是待辦的 id，不一定等於作業 id**
     *   （見實作裡的解析規則）。
     * @param title 帶過來的標題，用作 id 對不上時的輔助匹配。
     */
    suspend fun load(courseId: Int, homeworkId: Int, courseName: String, title: String)
    suspend fun refresh()

    /**
     * 提交。**由 UI 在使用者點擊時呼叫**，本方法**不重試**。
     *
     * 回傳服務端的原話；呼叫方負責顯示並決定要不要 [refresh]。
     */
    suspend fun submit(comment: String, draft: Boolean): HomeworkSubmitResult
}

class LiveHomeworkDetailRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : HomeworkDetailRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<HomeworkDetail>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<HomeworkDetail>> = _state.asStateFlow()

    private val inFlight = Mutex()
    private data class CacheKey(val owner: Long, val courseId: Int, val homeworkId: Int)
    private val snapshots = linkedMapOf<CacheKey, ListState<HomeworkDetail>>()
    @Volatile private var activeReadToken: Long? = null

    // Called only within the owner publication boundary.
    private fun rememberSelected() {
        if (courseId <= 0) return
        val key = CacheKey(epoch.token(), courseId, wantedHomeworkId)
        val settled = when (val state = _state.value) {
            is ListState.Content -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
            is ListState.Empty -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
            else -> null
        }
        snapshots.remove(key)
        if (settled != null) snapshots[key] = settled
        while (snapshots.size > 12) snapshots.remove(snapshots.keys.first())
    }

    @Volatile
    private var courseId: Int = 0

    @Volatile
    private var wantedHomeworkId: Int = 0

    @Volatile
    private var courseName: String = ""

    @Volatile
    private var wantedTitle: String = ""

    /** 解析出來的**真實** activity id（提交時只用它，不用 [wantedHomeworkId]）。 */
    @Volatile
    private var resolvedHomeworkId: Int = 0

    /** 我的 user id；**只記「取到过」**，取不到時下次仍會再試（同 [LiveRollcallRepository] 的教訓）。 */
    @Volatile
    private var userId: Int? = null

    init {
        epoch.registerReset {
            guard.invalidate()
            snapshots.clear()
            activeReadToken = null
            courseId = 0
            wantedHomeworkId = 0
            courseName = ""
            wantedTitle = ""
            resolvedHomeworkId = 0
            userId = null
            _state.value = ListState.FirstLoad
        }
    }

    override suspend fun load(courseId: Int, homeworkId: Int, courseName: String, title: String) {
        if (courseId <= 0) return
        var token: Long? = null
        epoch.publishData(epoch.token()) {
            val sameKey = this.courseId == courseId && wantedHomeworkId == homeworkId
            this.courseName = courseName
            wantedTitle = title
            if (sameKey && activeReadToken == guard.current) return@publishData
            if (!sameKey) {
                rememberSelected()
                this.courseId = courseId
                wantedHomeworkId = homeworkId
                resolvedHomeworkId = 0
                _state.value = snapshots[CacheKey(epoch.token(), courseId, homeworkId)] ?: ListState.FirstLoad
            }
            token = guard.begin()
            activeReadToken = token
            _state.value = _state.value.startRefresh()
        }
        token?.let { refresh(it, forceRefresh = false) }
    }

    override suspend fun refresh() {
        var token: Long? = null
        epoch.publishData(epoch.token()) {
            if (courseId <= 0 || activeReadToken == guard.current) return@publishData
            token = guard.begin()
            activeReadToken = token
            _state.value = _state.value.startRefresh()
        }
        token?.let { refresh(it, forceRefresh = true) }
    }

    private fun failRead(error: HzcuException.LoginExpired) {
        val state = _state.value
        _state.value = when (state) {
            is ListState.Content -> state.copy(meta = state.meta.copy(
                refreshing = false, loadingMore = false, stale = true, lastRefreshError = error,
            ))
            is ListState.Empty -> state.copy(meta = state.meta.copy(
                refreshing = false, loadingMore = false, stale = true, lastRefreshError = error,
            ))
            else -> state.failWith(error)
        }
    }

    private suspend fun refresh(token: Long, forceRefresh: Boolean) {
        try {
            inFlight.withLock {
                if (!guard.isCurrent(token)) return
                val course = courseId
                if (course <= 0) return
                val wanted = wantedHomeworkId
                val name = courseName
                val title = wantedTitle

                // ── 1) 要求（課程作業清單裡的那一項）──────────────────────────
                val listResult = catchingActive { client.homeworks(course, page = 1, pageSize = 50, forceRefresh = forceRefresh) }
                if (!guard.isCurrent(token)) return
                listResult.exceptionOrNull()?.let { if (it is HzcuException.LoginExpired) throw it }
                val found = listResult.getOrNull()?.let { list -> resolveIn(list, wanted, title) }

                // ── 2) 我的提交記錄（需要 user id）────────────────────────────
                val profileResult = userId?.let { Result.success(it) }
                    ?: catchingActive { client.profileUserId(forceRefresh) }
                if (!guard.isCurrent(token)) return
                profileResult.exceptionOrNull()?.let { if (it is HzcuException.LoginExpired) throw it }
                val me = profileResult.getOrNull()
                val activity = found?.id ?: 0
                val submissionsResult = if (me != null && activity > 0) {
                    catchingActive { client.submissions(activity, me, forceRefresh) }
                } else {
                    Result.failure(HzcuException.Parse("还不知道我的编号或这项作业，没去取提交记录"))
                }

                // ── 3) 我交了沒（狀態那支；獨立成敗）──────────────────────────
                if (!guard.isCurrent(token)) return
                submissionsResult.exceptionOrNull()?.let { if (it is HzcuException.LoginExpired) throw it }
                val statusResult = catchingActive { client.homeworkStatus(course, forceRefresh) }

                if (!guard.isCurrent(token)) return
                statusResult.exceptionOrNull()?.let { if (it is HzcuException.LoginExpired) throw it }
                val submissions = submissionsResult.getOrNull()
                val detail = HomeworkDetail(
                    courseId = course,
                    courseName = name,
                    title = title.ifBlank { found?.title.orEmpty() },
                    homework = found,
                    listFailed = listResult.isFailure,
                    // ★ 狀態按**解析後的 id** 挑：按帶過來的 id 挑可能是待辦 id，
                    //   那樣會把「另一項作業的已交」顯示成「這一項已交」。
                    myStatus = found?.id?.let { statusResult.getOrNull()?.get(it) },
                    submissions = submissions.orEmpty(),
                    submissionsKnown = submissions != null,
                    userId = me,
                )
                guard.publish(token) {
                    resolvedHomeworkId = activity
                    userId = me
                    _state.value = if (detail.homework == null && detail.listFailed) {
                        // 首载失败可重试；已有要求则保留内容和上次成功时间。
                        _state.value.failWith(
                            HzcuException.Parse(
                                "作业要求没取到",
                                cause = listResult.exceptionOrNull(),
                            ),
                        )
                    } else {
                        _state.value.settleFirstPage(listOf(detail), clock()) { it.homework?.id ?: 0 }
                    }
                    rememberSelected()
                }
            }
        } catch (error: HzcuException.LoginExpired) {
            guard.publish(token) {
                resolvedHomeworkId = 0
                failRead(error)
                rememberSelected()
            }
        } finally {
            guard.publish(token) {
                _state.value = when (val state = _state.value) {
                    is ListState.Content -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
                    is ListState.Empty -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
                    else -> state
                }
                activeReadToken = null
                rememberSelected()
            }
        }
    }

    /**
     * 在清單裡找出「我要看的那一項」。
     *
     * ## ★ 為什麼要兩級匹配（先 id、再標題）
     *
     * 從待辦進來的 id 是 `/api/todos` 的 `id`。它**是否等於作業 id，我沒有證據**
     * （`/api/todos` 的契約只說到 `todo_list[].id`）。
     * 而這個 id 會被拿去當**提交目標**（`POST /api/course/activities/{id}/submissions`）——
     * 打錯 id 等於把作業交到別門課去。
     *
     * 所以規則寫成：
     * 1. id 命中 → 用那一項（最常見，也最可信）；
     * 2. id 沒命中 → **只在同一門課內**按標題完全相同匹配；
     * 3. 都沒命中 → 回 null，界面上說「没找到」，**提交被擋住**。
     *
     * 標題匹配刻意要求**完全相等**而不是包含關係：包含會撞名
     * （「第一次作业」同時包含在「第一次作业(补)」裡），
     * 那種撞名在只讀頁只是難看，在寫操作頁就是交錯東西。
     */
    private fun resolveIn(list: List<TronHomework>, wantedId: Int, title: String): TronHomework? =
        list.firstOrNull { it.id == wantedId }
            ?: list.firstOrNull { it.title.isNotBlank() && it.title == title }

    override suspend fun submit(comment: String, draft: Boolean): HomeworkSubmitResult {
        val token = epoch.token()
        // 閘門放在**這一層**（而不是只在界面擋）：界面可能因為重组或狀態落後
        // 而放行一次點擊，倉庫是唯一能保证「不發」的地方。
        val current = (_state.value as? ListState.Content)?.items?.firstOrNull()
        val target = resolvedHomeworkId
        if (target <= 0 || current == null) {
            return HomeworkSubmitResult(
                ok = false, httpStatus = 0,
                message = "还没确认这是哪项作业，没敢提交",
            )
        }
        val homework = current.homework
        if (homework == null) {
            return HomeworkSubmitResult(
                ok = false, httpStatus = 0,
                message = "作业要求没取到，没敢提交",
            )
        }
        if (homework.closed) {
            return HomeworkSubmitResult(
                ok = false, httpStatus = 0,
                message = "这项作业已经关闭，没提交",
            )
        }
        // 草稿**不佔可交次數**（源碼 `is_draft` 就是這個語義），
        // 所以只有「正式提交」才受上限約束。
        if (!draft && !homework.acceptsAnotherSubmission(current.submittedCount)) {
            return HomeworkSubmitResult(
                ok = false, httpStatus = 0,
                message = "已经交过 ${homework.submitTimes} 次的上限，没再提交",
            )
        }
        if (!epoch.isCurrent(token)) throw kotlinx.coroutines.CancellationException("Account changed")
        val result = client.submitHomework(target, comment, draft)
        if (!epoch.isCurrent(token)) throw kotlinx.coroutines.CancellationException("Account changed")
        return result
    }
}
