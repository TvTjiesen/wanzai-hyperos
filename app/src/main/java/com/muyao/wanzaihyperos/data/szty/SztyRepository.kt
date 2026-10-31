package com.muyao.wanzaihyperos.data.szty

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.SztyClient
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.session.SztySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
) : SztyRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<SztySnapshot>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<SztySnapshot>> = _state.asStateFlow()

    private val inFlight = Mutex()

    /**
     * 构造时就播上一次的快照。
     *
     * ★ 为什么要这个：数智体育这六条请求之间有 1.8 秒硬节流，第一次取全要十几秒 ——
     *   这段时间首页那张「校园跑」卡只能写「正在取校园跑…」。而那个数字通常是**昨天同一个数**，
     *   先把它连同「这是上次存的」一起画出来，比画一行占位字诚实。
     */
    init {
        seedFromSnapshot()
    }

    override suspend fun refresh() {
        inFlight.withLock {
            val token = session.token()
            val userId = session.userId()
            if (token.isNullOrBlank() || userId == null) {
                // 没凭据不是「失败」，是「还没填」。页面上那一栏会自己换成输入框。
                _state.value = ListState.Empty()
                return
            }

            // 冷启动时 init 已经播过；这里再兜一次（例如那次读盘刚好失败）。
            if (_state.value is ListState.FirstLoad) seedFromSnapshot()

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
        val failureNotes = mutableListOf<String>()
        // 「一块都没取到」时要把第一条真实错误抛出去，不能变成空列表冒充成功。
        var firstError: Throwable? = null
        fun remember(what: String, error: Throwable) {
            failureNotes += error.note(what)
            if (firstError == null) firstError = error
        }

        var raw = SztyRaw()
        runCatching { client.fitness(token, userId) }
            .onSuccess { raw = raw.copy(fitness = it) }
            .onFailure { remember("体测成绩", it) }

        runCatching { client.runSummary(token) }
            .onSuccess { raw = raw.copy(run = it) }
            .onFailure { remember("校园跑", it) }

        runCatching { client.currentTermId(token, userId) }
            .onSuccess { raw = raw.copy(term = it) }
            .onFailure { remember("学期", it) }

        // 后两条要靠前面的返回值决定要不要问，所以这里先解析一次（纯函数、无副作用）。
        // 与 [compose] 里的解析是**同一组** SztyParsers，不会分叉。
        val term = SztyParsers.currentTermId(raw.term)
        if (term != null) {
            runCatching { client.peClass(token, userId, term) }
                .onSuccess { raw = raw.copy(peClass = it) }
                .onFailure { remember("体育课", it) }

            val classId = SztyParsers.peClass(raw.peClass)?.classId
            if (classId != null) {
                runCatching { client.attendance(token, userId, classId) }
                    .onSuccess { raw = raw.copy(attendance = it) }
                    .onFailure { remember("签到记录", it) }
                runCatching { client.attendanceSummary(token, userId, classId) }
                    .onSuccess { raw = raw.copy(attendanceSummary = it) }
                    .onFailure { remember("签到汇总", it) }
            }
        }

        // 任何一块报 token 失效，整次就是失效 —— 那说明 token 真不能用了。
        if (failureNotes.any { it.contains(EXPIRED_MARK) }) {
            throw HzcuException.LoginExpired("数智体育的 token 已经不能用了")
        }

        val snapshot = compose(raw, failureNotes)

        // 一块都没取到、而且确实有请求失败 ⇒ 这是**失败**，不是「服务端说没有」。
        // 少了这一条，页面上那句「正在取…」会永远停在屏幕里（Empty 与 FirstLoad 长得一样）。
        val problem = firstError
        if (!snapshot.hasAnyContent && problem != null) throw problem

        // 只有真拿到东西才盖快照：照 Jw 那条纪律，一次失败不许把上一份好数据冲掉
        //（2026-10-21 真机事故：失败的那次把快照写成了失败页，重启后还是失败页）。
        if (snapshot.hasAnyContent) saveSnapshot(raw)

        return snapshot
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

    /**
     * 存**六条接口的原文**，不存解析后的模型。
     *
     * 理由是「两套解析迟早分叉」：存模型就得给每个模型写一套序列化、读回来再解一遍，
     * 于是同一份数据有了两条解析路径。存原文则读回来走同一个 [compose]。
     */
    private fun saveSnapshot(raw: SztyRaw) {
        val f = snapshotFile() ?: return
        runCatching { f.writeText(raw.toJson()) }
    }

    /** 用快照立刻把界面填上（标 [ListMeta.stale]，界面会自己加「这是上次存的」）。 */
    private fun seedFromSnapshot() {
        val f = snapshotFile() ?: return
        if (!f.isFile) return
        val text = runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val raw = runCatching { SztyRaw.parse(text) }.getOrNull() ?: return
        val snapshot = compose(raw, emptyList()).takeIf { it.hasAnyContent } ?: return
        _state.value = ListState.Content(
            items = listOf(snapshot),
            meta = ListMeta(updatedAtMillis = f.lastModified(), stale = true),
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
) {
    fun toJson(): String {
        val fields = LinkedHashMap<String, JsonElement>()
        fitness?.let { fields["fitness"] = it }
        run?.let { fields["run"] = it }
        term?.let { fields["term"] = it }
        peClass?.let { fields["peClass"] = it }
        attendance?.let { fields["attendance"] = it }
        attendanceSummary?.let { fields["attendanceSummary"] = it }
        return JsonObject(fields).toString()
    }

    companion object {
        /** 读不懂就当空快照（宁可当作没有缓存，也不要让一次坏文件把启动搞崩）。 */
        fun parse(text: String): SztyRaw {
            val root = runCatching { Json.parseToJsonElement(text).asObjectOrNull() }.getOrNull()
                ?: return SztyRaw()
            return SztyRaw(
                fitness = root["fitness"],
                run = root["run"],
                term = root["term"],
                peClass = root["peClass"],
                attendance = root["attendance"],
                attendanceSummary = root["attendanceSummary"],
            )
        }
    }
}

/** 三块里至少有一块真拿到了东西。 */
val SztySnapshot.hasAnyContent: Boolean
    get() = fitness.isNotEmpty() || run != null || attendance.isNotEmpty() ||
        attendanceSummary != null || peClass != null
