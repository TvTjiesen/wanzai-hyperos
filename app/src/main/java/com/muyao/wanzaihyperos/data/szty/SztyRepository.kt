package com.muyao.wanzaihyperos.data.szty

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.SztyClient
import com.muyao.wanzaihyperos.session.SztySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 数智体育的三块只读数据。
 *
 * ## 为什么是一个「快照」而不是三个独立的清单状态
 *
 * 三块数据的**取数顺序是有依赖的**：签到要用「当前学期号」和「体育课班级 id」，
 * 而这两样要靠前两个请求问出来。拆成三个互不知情的仓库，
 * 就要在界面层把它们串起来 —— 那个串联逻辑（先问你、再问他、然后才问他）
 * 写在 Composable 里是没法单独测的。
 *
 * ## 一块失败不拖垮另外两块
 *
 * 真机上这三块各自都可能失败（token 半路过期、体育课那学期没开、服务端某个接口抽风）。
 * 所以 [SztySnapshot.notes] 逐条记下**哪一块没取到、为什么**，
 * 而 ListState 只有在**一块都没取到**时才进 ListState.Failed。
 */
interface SztyRepository {
    val state: StateFlow<ListState<SztySnapshot>>
    suspend fun refresh()
}

class LiveSztyRepository(
    private val client: SztyClient,
    private val session: SztySession,
    private val clock: () -> Long = System::currentTimeMillis,
) : SztyRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<SztySnapshot>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<SztySnapshot>> = _state.asStateFlow()

    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = session.token()
            val userId = session.userId()
            if (token.isNullOrBlank() || userId == null) {
                // 没凭据不是「失败」，是「还没填」。页面上那一栏会自己换成输入框。
                _state.value = ListState.Empty()
                return
            }

            val ticket = guard.begin()
            _state.value = _state.value.startRefresh()

            val result = runCatching { fetch(token, userId) }
            if (!guard.isCurrent(ticket)) return

            result.fold(
                onSuccess = { snapshot ->
                    session.markSync(clock())
                    _state.value = if (snapshot.hasAnyContent) {
                        ListState.Content(
                            items = listOf(snapshot),
                            meta = ListMeta(updatedAtMillis = clock()),
                        )
                    } else {
                        // 一块都没取到：这里刻意**不装作成功**。
                        ListState.Empty(meta = ListMeta(updatedAtMillis = clock()))
                    }
                },
                onFailure = { error ->
                    _state.value = _state.value.failWith(
                        error as? HzcuException
                            ?: HzcuException.Parse("数智体育取数失败", cause = error),
                    )
                },
            )
        }
    }

    /**
     * 依次问四个接口（体测、校园跑、学期、体育课）再问签到两条。
     *
     * **只有 token 失效会让整次刷新失败** —— 那说明后面几条也没戏了。
     * 其它任何一条失败都只是记一句备注，继续问下一条。
     */
    private suspend fun fetch(token: String, userId: Int): SztySnapshot {
        val notes = mutableListOf<String>()

        var fitness = emptyList<SztyFitnessYear>()
        runCatching { client.fitness(token, userId) }
            .onSuccess { fitness = it }
            .onFailure { notes += it.note("体测成绩") }

        var run: SztyRun? = null
        runCatching { client.runSummary(token) }
            .onSuccess { run = it }
            .onFailure { notes += it.note("校园跑") }

        var termId: Int? = null
        runCatching { client.currentTermId(token, userId) }
            .onSuccess { termId = it }
            .onFailure { notes += it.note("学期") }

        var peClass: SztyPeClass? = null
        var attendance = emptyList<SztyAttendance>()
        var summary: SztyAttendanceSummary? = null

        val term = termId
        if (term == null) {
            if (notes.none { it.startsWith("学期") }) {
                notes += "学期的接口没回当前学期，体育课签到这次没查"
            }
        } else {
            runCatching { client.peClass(token, userId, term) }
                .onSuccess { peClass = it }
                .onFailure { notes += it.note("体育课") }

            val classId = peClass?.classId
            if (classId == null) {
                if (notes.none { it.startsWith("体育课") }) {
                    notes += "这学期没有体育课（服务端没给班级），所以没有签到要看"
                }
            } else {
                runCatching { client.attendance(token, userId, classId) }
                    .onSuccess { attendance = it }
                    .onFailure { notes += it.note("签到记录") }
                runCatching { client.attendanceSummary(token, userId, classId) }
                    .onSuccess { summary = it }
                    .onFailure { notes += it.note("签到汇总") }
            }
        }

        // 任何一块报 token 失效，整次就是失效 —— 那说明 token 真不能用了。
        if (notes.any { it.contains(EXPIRED_MARK) }) {
            throw HzcuException.LoginExpired("数智体育的 token 已经不能用了")
        }

        return SztySnapshot(
            fitness = fitness,
            run = run,
            peClass = peClass,
            attendance = attendance,
            attendanceSummary = summary,
            notes = notes,
        )
    }

    /** 把一条失败翻成**人话**，并带上失败原因（原因里没有凭据，见各异常的注释）。 */
    private fun Throwable.note(what: String): String = what + "没取到：" + shortReason

    private val Throwable.shortReason: String
        get() = when (this) {
            is HzcuException.LoginExpired -> EXPIRED_MARK
            is HzcuException.Api -> message ?: ("服务端说不行（code=" + code + "）")
            is HzcuException.Transport -> "网络不通"
            is HzcuException.RequestTimeout -> "超时"
            is HzcuException.HttpStatus -> "HTTP " + statusCode
            is HzcuException.Parse -> "服务端回的内容看不懂"
            else -> "未知原因"
        }

    private companion object {
        /**
         * token 失效的固定措辞。
         *
         * 用它当标记而不是立刻抛异常：一条查询失败不该中断后面几条
         *（另外两块的数据是好的，用户还是想看到），但**最后**必须整体判成失效。
         */
        const val EXPIRED_MARK = "token 已经不能用了"
    }
}

/** 三块里至少有一块真拿到了东西。 */
val SztySnapshot.hasAnyContent: Boolean
    get() = fitness.isNotEmpty() || run != null || attendance.isNotEmpty() ||
        attendanceSummary != null || peClass != null
