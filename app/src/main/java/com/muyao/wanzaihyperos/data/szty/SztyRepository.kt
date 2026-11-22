package com.muyao.wanzaihyperos.data.szty

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.jw.OwnerSnapshotStore
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.SecondaryGuard
import com.muyao.wanzaihyperos.data.repo.finishRefresh
import com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.SztyClient
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.session.SztySession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    /**
     * 快照写在哪（App 私有目录）。
     *
     * 收**目录**而不是 Context：数据层到这一层为止没有任何 android 依赖，
     * 单测就能拿 java.nio.file.Files.createTempDirectory 真跑一遍「写盘 → 冷启动读回」，
     * 而不是把它当不可测的部分绕过去。默认 null ⇒ 不落盘（纯内存），单测不传即此。
     */
    private val snapshotDir: java.io.File? = null,
    private val epoch: AccountEpoch = AccountEpoch(),
) : SztyRepository {

    private val _state = MutableStateFlow<ListState<SztySnapshot>>(ListState.FirstLoad)
    private var refreshingTicket: Long? = null
    private var cachedRaw: SztyRaw? = null
    private val guard = SecondaryGuard(epoch, session) {
        _state.value = ListState.FirstLoad
        refreshingTicket = null
        cachedRaw = null
    }
    override val state: StateFlow<ListState<SztySnapshot>> = _state.asStateFlow()

    private val inFlight = Mutex()

    private val snapshots = OwnerSnapshotStore(snapshotFile(), "szty", epoch) { session.userId()?.toString() }

    init {
        epoch.registerReset {
            guard.invalidate()
            _state.value = ListState.FirstLoad
            refreshingTicket = null
            cachedRaw = null
        }
        seedFromSnapshot(epoch.token())
    }

    override suspend fun refresh() {
        val ownerToken = epoch.token()
        val ticket = guard.begin()
        inFlight.withLock {
            if (!epoch.isCurrent(ownerToken) || !guard.isCurrent(ticket)) return
            var token: String? = null
            var userId: Int? = null
            if (!guard.publish(ticket) { token = session.token(); userId = session.userId() }) return
            if (token.isNullOrBlank() || userId == null) {
                // 没凭据不是「失败」，是「还没填」。页面上那一栏会自己换成输入框。
                guard.publish(ticket) { _state.value = ListState.Empty() }
                return
            }

            // 冷启动时 init 已经播过；这里再兜一次（例如那次读盘刚好失败）。
            if (_state.value is ListState.FirstLoad) seedFromSnapshot(ownerToken, ticket)

            // ★ 部分分区失败时，失败那几块要沿用上一份快照，不能把整包换成「只剩成功的两块」。
            //   以前这里直接拿这一次的 raw 覆盖：体测接口抽风一次，体测年表就从屏幕上消失，
            //   而磁盘上那份好快照也被覆盖掉（下次冷启动还是缺）。旧内容不是「这次的事实」，
            //   但它是**上次的事实**——丢掉它比标注「沿用上次」更不诚实。
            var previousRaw: SztyRaw? = null
            guard.publish(ticket) {
                previousRaw = cachedRaw ?: snapshots.read(ownerToken)?.takeIf { it.isNotBlank() }?.let {
                    catchingActive { SztyRaw.parse(it) }.getOrNull()
                }
            }
            val previousAt = previousRaw?.let {
                it.savedAtMillis ?: snapshotFile()?.lastModified()?.takeIf { at -> at > 0L }
            }

            var revision = -1L
            if (!guard.publish(ticket) {
                    revision = session.credentialRevision()
                    refreshingTicket = ticket
                    _state.value = _state.value.startRefresh()
                }) return

            val result = try {
                catchingActive {
                    client.withReadGuard({ guard.isCurrent(ticket) }) {
                        fetch(token!!, userId!!, ticket, ownerToken, previousRaw, previousAt)
                    }
                }
            } finally {
                epoch.publishData(ownerToken) {
                    session.publishCredential(revision) {
                        if (refreshingTicket == ticket) {
                            _state.value = _state.value.finishRefresh()
                            refreshingTicket = null
                        }
                    }
                }
            }
            guard.publish(ticket) {
                result.fold(
                    onSuccess = { fetched ->
                        val snapshot = fetched.snapshot
                        cachedRaw = fetched.raw
                        if (!fetched.usedFallback && fetched.failure == null) fetched.atMillis?.let(session::markSync)
                        _state.value = if (snapshot.hasAnyContent) {
                            ListState.Content(
                                items = listOf(snapshot),
                                // 有分区沿用上次时，把「这份数据多新」退回上一份的时间：
                                // 整体说「刚刚更新」会把其中两块旧数据也说成新的。
                                meta = ListMeta(
                                    updatedAtMillis = fetched.atMillis,
                                    stale = fetched.usedFallback || fetched.failure != null,
                                    // 六块全挂、只是靠上一份快照顶住时，错误原因仍要留在屏幕上：
                                    // 只有「沿用上次」而没有「为什么沿用」，等于把失败说成了成功。
                                    lastRefreshError = fetched.failure,
                                ),
                            )
                        } else {
                            // 一块都没取到：这里刻意**不装作成功**。
                            ListState.Empty(meta = ListMeta(updatedAtMillis = clock()))
                        }
                    },
                    onFailure = { error ->
                        _state.value = retainCachedOnFailure(
                            _state.value, error as? HzcuException
                                ?: HzcuException.Parse("数智体育取数失败", cause = error),
                        )
                    },
                )
            }
        }
    }

    /**
     * 依次问四个接口（体测、校园跑、学期、体育课）再问签到两条。
     *
     * **只有 token 失效会让整次刷新失败** —— 那说明后面几条也没戏了。
     * 其它任何一条失败都只是记一句备注，继续问下一条。
     */
    private suspend fun fetch(
        token: String,
        userId: Int,
        ticket: Long,
        ownerToken: Long,
        previousRaw: SztyRaw?,
        previousAt: Long?,
    ): SztyFetchResult {
        val failureNotes = mutableListOf<String>()
        val failedLabels = mutableSetOf<String>()
        // 「一块都没取到」时要把第一条真实错误抛出去，不能变成空列表冒充成功。
        var firstError: Throwable? = null
        fun remember(what: String, error: Throwable) {
            failureNotes += error.note(what)
            failedLabels += what
            if (firstError == null) firstError = error
        }

        suspend fun query(action: suspend () -> JsonElement?): Result<JsonElement?> {
            currentCoroutineContext().ensureActive()
            if (!guard.isCurrent(ticket)) throw CancellationException("Sports credentials changed")
            val result = catchingActive { action() }
            currentCoroutineContext().ensureActive()
            if (!guard.isCurrent(ticket)) throw CancellationException("Sports credentials changed")
            return result
        }

        var raw = SztyRaw()
        query { client.fitness(token, userId) }
            .onSuccess { raw = raw.copy(fitness = it) }
            .onFailure { remember("体测成绩", it) }

        query { client.runSummary(token) }
            .onSuccess { raw = raw.copy(run = it) }
            .onFailure { remember("校园跑", it) }

        query { client.currentTermId(token, userId) }
            .onSuccess { raw = raw.copy(term = it) }
            .onFailure { remember("学期", it) }

        // 后两条要靠前面的返回值决定要不要问，所以这里先解析一次（纯函数、无副作用）。
        // 与 [compose] 里的解析是**同一组** SztyParsers，不会分叉。
        val term = SztyParsers.currentTermId(raw.term)
        if (term != null) {
            query { client.peClass(token, userId, term) }
                .onSuccess { raw = raw.copy(peClass = it) }
                .onFailure { remember("体育课", it) }

            val classId = SztyParsers.peClass(raw.peClass)?.classId
            if (classId != null) {
                query { client.attendance(token, userId, classId) }
                    .onSuccess { raw = raw.copy(attendance = it) }
                    .onFailure { remember("签到记录", it) }
                query { client.attendanceSummary(token, userId, classId) }
                    .onSuccess { raw = raw.copy(attendanceSummary = it) }
                    .onFailure { remember("签到汇总", it) }
            }
        }

        if ("学期" in failedLabels) {
            failedLabels += listOf("体育课", "签到记录", "签到汇总")
        } else if ("体育课" in failedLabels) {
            failedLabels += listOf("签到记录", "签到汇总")
        }

        // 任何一块报 token 失效，整次就是失效 —— 那说明 token 真不能用了。
        if (failureNotes.any { it.contains(EXPIRED_MARK) }) {
            throw HzcuException.LoginExpired("数智体育的 token 已经不能用了")
        }

        // ★ 失败的分区沿用上一份快照，而不是把整包换成「只剩成功的那几块」。
        //   以前这里用这一次的 raw 直接盖磁盘：体测接口抽风一次，体测年表就从屏幕上消失，
        //   而且磁盘上那份好快照也被覆盖（下次冷启动还是缺）。旧内容不是「这次的事实」，
        //   但它是**上次的事实**——丢掉它比标注「沿用上次」更不诚实。
        val merged = mutableListOf<String>()
        fun <T> keep(label: String, current: T?, previous: T?): T? =
            if (current == null && previous != null && label in failedLabels) {
                merged += label
                previous
            } else current

        val previousTerm = SztyParsers.currentTermId(previousRaw?.term)
        val currentTerm = SztyParsers.currentTermId(raw.term)
        // 学期没取到（这次没问下面两块）或学期没变时，才允许沿用上一份的体育课与签到；
        // 学期真的换了，旧学期那几块就不能再顶上来。
        val termStillUsable = previousRaw != null && previousTerm != null &&
            (currentTerm == previousTerm || "学期" in failedLabels)

        val keptClass = if (termStillUsable) keep("体育课", raw.peClass, previousRaw?.peClass) else raw.peClass
        val previousClassId = SztyParsers.peClass(previousRaw?.peClass)?.classId
        val keptClassId = SztyParsers.peClass(keptClass)?.classId
        val classStillUsable = termStillUsable && previousClassId != null && keptClassId == previousClassId
        val kept = raw.copy(
            fitness = keep("体测成绩", raw.fitness, previousRaw?.fitness),
            run = keep("校园跑", raw.run, previousRaw?.run),
            term = keep("学期", raw.term, previousRaw?.term),
            peClass = keptClass,
            attendance = if (classStillUsable) keep("签到记录", raw.attendance, previousRaw?.attendance) else raw.attendance,
            attendanceSummary = if (classStillUsable) keep("签到汇总", raw.attendanceSummary, previousRaw?.attendanceSummary) else raw.attendanceSummary,
        )
        val usedFallback = merged.isNotEmpty()
        if (usedFallback) {
            failureNotes += merged.joinToString("、") + "这次没取到，显示的是上次取到的"
        }

        val snapshot = compose(kept, failureNotes)

        // 一块都没取到、而且确实有请求失败 ⇒ 这是**失败**，不是「服务端说没有」。
        // 少了这一条，页面上那句「正在取…」会永远停在屏幕里（Empty 与 FirstLoad 长得一样）。
        val problem = firstError
        if (!snapshot.hasAnyContent && problem != null) throw problem

        // 只有真拿到东西才盖快照：照 Jw 那条纪律，一次失败不许把上一份好数据冲掉
        //（2026-10-21 真机事故：失败的那次把快照写成了失败页，重启后还是失败页）。
        //
        // ★ 沿用上次时，`savedAtMillis` 跟着退回上一次成功取数的时间：快照文件 mtime 是
        //   「这次写盘的时间」，用它当更新时间会把沿用来的那几块也说成新的。
        // 只有「这次真拿到了东西」才盖快照：照 Jw 那条纪律，一次失败不许把上一份好数据冲掉
        //（2026-10-21 真机事故：失败的那次把快照写成了失败页，重启后还是失败页）。
        // 沿用上次分区的结果与磁盘上那份是同一件事，也不重写 —— 重写只会改 mtime，
        // 让界面把「上次取到」误报成「刚刚更新」。
        val atMillis = if (firstError != null) previousAt else clock()
        if (firstError == null) guard.publish(ticket) {
            snapshots.write(ownerToken, kept.copy(savedAtMillis = atMillis).toJson())
        }
        val failure = firstError as? HzcuException ?: firstError?.let {
            HzcuException.Parse("数智体育取数失败", cause = it)
        }
        return SztyFetchResult(
            snapshot = snapshot,
            usedFallback = usedFallback,
            atMillis = atMillis,
            failure = failure,
            raw = kept.copy(savedAtMillis = atMillis),
        )
    }

    /**
     * 把六条接口的原文拼成界面上要的那一坨。
     *
     * 线上取数与冷启动读快照**走的是同一个函数** —— 两套拼装迟早分叉，
     * 分叉的表现是「快照里和线上看到的不是一回事」。
     *
     * [extraNotes] 只有线上那条路会传（这次哪几条请求失败了）；读快照时不传，
     * 因为「上次某条没取到」不是**现在**的事实，不该继续挂在界面上。
     */
    private fun compose(raw: SztyRaw, extraNotes: List<String>): SztySnapshot {
        val notes = extraNotes.toMutableList()

        var peClass: SztyPeClass? = null
        var attendance = emptyList<SztyAttendance>()
        var summary: SztyAttendanceSummary? = null

        val term = SztyParsers.currentTermId(raw.term)
        if (term == null) {
            if (notes.none { it.startsWith("学期") }) {
                notes += "学期的接口没回当前学期，体育课签到这次没查"
            }
        } else {
            peClass = SztyParsers.peClass(raw.peClass)
            val classId = peClass?.classId
            if (classId == null) {
                if (notes.none { it.startsWith("体育课") }) {
                    notes += "这学期没有体育课（服务端没给班级），所以没有签到要看"
                }
            } else {
                attendance = SztyParsers.attendance(raw.attendance)
                summary = SztyParsers.attendanceSummary(raw.attendanceSummary)
            }
        }

        return SztySnapshot(
            fitness = SztyParsers.fitnessYears(raw.fitness),
            run = SztyParsers.run(raw.run),
            peClass = peClass,
            attendance = attendance,
            attendanceSummary = summary,
            notes = notes,
        )
    }

    private fun snapshotFile(): java.io.File? =
        snapshotDir?.let { java.io.File(it, SNAPSHOT) }

    private fun seedFromSnapshot(ownerToken: Long, ticket: Long = guard.begin()) {
        guard.publish(ticket) {
            val text = snapshots.read(ownerToken)?.takeIf { it.isNotBlank() } ?: return@publish
            val raw = catchingActive { SztyRaw.parse(text) }.getOrNull() ?: return@publish
            if (raw.savedAtMillis == null && listOf(raw.fitness, raw.run, raw.term, raw.peClass, raw.attendance, raw.attendanceSummary).all { it == null }) return@publish
            val snapshot = compose(raw, emptyList())
            cachedRaw = raw
            val meta = ListMeta(updatedAtMillis = raw.savedAtMillis ?: snapshotFile()?.lastModified(), stale = true)
            _state.value = if (snapshot.hasAnyContent) ListState.Content(listOf(snapshot), meta) else ListState.Empty(meta)
        }
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

        /** 上次取到的六条原文（首屏拿它立刻画出来）。 */
        const val SNAPSHOT = "szty_snapshot.json"
    }
}

/**
 * 六条查询的原文，一起进快照。
 *
 * 每条都可能为 null：null 既可能是「这次没问」（前一环没给出 termId / classId），
 * 也可能是「问了但失败」——后者由 notes 说实话。
 * 读快照时只有前者仍然成立，后者不该继续挂着（见 [LiveSztyRepository.compose] 的注释）。
 */
internal data class SztyRaw(
    val fitness: JsonElement? = null,
    val run: JsonElement? = null,
    val term: JsonElement? = null,
    val peClass: JsonElement? = null,
    val attendance: JsonElement? = null,
    val attendanceSummary: JsonElement? = null,
    /**
     * 这份快照代表「什么时候的事实」。
     *
     * 沿用上次的分区时，写盘时间会晚于那几块数据的时间；文件 mtime 不能当更新时间用。
     * 老快照没有这个字段 ⇒ 回退到文件 mtime（那时整包都是同一次取的，mtime 是准的）。
     */
    val savedAtMillis: Long? = null,
) {
    fun toJson(): String {
        val fields = LinkedHashMap<String, JsonElement>()
        fitness?.let { fields["fitness"] = it }
        run?.let { fields["run"] = it }
        term?.let { fields["term"] = it }
        peClass?.let { fields["peClass"] = it }
        attendance?.let { fields["attendance"] = it }
        attendanceSummary?.let { fields["attendanceSummary"] = it }
        savedAtMillis?.let { fields["savedAt"] = JsonPrimitive(it) }
        return JsonObject(fields).toString()
    }

    companion object {
        /** 读不懂就当空快照（宁可当作没有缓存，也不要让一次坏文件把启动搞崩）。 */
        fun parse(text: String): SztyRaw {
            val root = catchingActive { Json.parseToJsonElement(text).asObjectOrNull() }.getOrNull()
                ?: return SztyRaw()
            return SztyRaw(
                fitness = root["fitness"],
                run = root["run"],
                term = root["term"],
                peClass = root["peClass"],
                attendance = root["attendance"],
                attendanceSummary = root["attendanceSummary"],
                savedAtMillis = (root["savedAt"] as? JsonPrimitive)?.content?.toLongOrNull(),
            )
        }
    }
}

/** 一次取数的结果：快照 + 是否沿用了上次的分区 + 这份数据的时间 + 全挂时的真实错误。 */
internal data class SztyFetchResult(
    val snapshot: SztySnapshot,
    val usedFallback: Boolean,
    val atMillis: Long?,
    val failure: HzcuException? = null,
    val raw: SztyRaw,
)

/** 三块里至少有一块真拿到了东西。 */
val SztySnapshot.hasAnyContent: Boolean
    get() = fitness.isNotEmpty() || run != null || attendance.isNotEmpty() ||
        attendanceSummary != null || peClass != null
