package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.appendPage
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startLoadMore
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.withHasMore
import com.muyao.wanzaihyperos.data.repo.CountState
import com.muyao.wanzaihyperos.data.repo.RefreshPolicy
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.net.isAuthFailure
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 门户数据的**真实仓库**。每个方法走 [PortalApi]，解析走 [PortalParsers]。
 *
 * ### 为什么不改 A1/A2 的仓库与接口
 * A2 建的是「接口 + 示例实作」，它同时充当**离线兜底**（服务端不通时界面还能给点东西）。
 * 真实实作放在这里、用自己的接口，好处是：
 * - **零回归风险** —— 不碰任何已被真机走查验证过的 A1 代码；
 * - 迁移是**显式**的：将来某页要接真实数据，就把注入的仓替换掉，一行的事。
 *
 * ### 全部继承 A2 已定的纪律
 * - 过时响应由 [StaleGuard] 丢弃（切分组 / 改搜索词 / 登出后不得覆盖新状态）；
 * - 计数取不到就是 **null**（界面显示「—」），**绝不写 0**；
 * - 刷新失败**保留已有内容**并标 `stale`，不清空；
 * - 分页**按返回条数**判，不用服务端 `total`（实测 `total=20` 而 `allrecords=192`）。
 */

// ---------------------------------------------------------------------------
// 目录
// ---------------------------------------------------------------------------

interface PortalCatalogRepository {
    val state: StateFlow<ListState<PortalAppGroup>>
    suspend fun refresh()
}

class LivePortalCatalogRepository(
    private val api: PortalApi,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : PortalCatalogRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<PortalAppGroup>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<PortalAppGroup>> = _state.asStateFlow()

    init {
        epoch.registerReset {
            guard.invalidate()
            _state.value = ListState.FirstLoad
        }
    }

    override suspend fun refresh() {
        val token = guard.begin()
        if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return
        val result = catchingActive { api.fetchCatalog() }
        guard.publish(token) {
            _state.value = result.fold(
                onSuccess = { response ->
                    val catalog = PortalParsers.parseCatalog(response.raw)
                    // 分组按**名字**去重：服务端理论上不该重复，但重复一次就会在界面上
                    // 出现两个同名分组，用户会以为加载错了。
                    _state.value.settleFirstPage(catalog.groups, clock()) { it.name }
                },
                onFailure = { retainCachedOnFailure(_state.value, it.asPortalException()) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 常用应用（首页宫格）
// ---------------------------------------------------------------------------

interface PortalFrequentAppsRepository {
    val state: StateFlow<ListState<PortalAppEntry>>
    suspend fun refresh()
}

/**
 * 常用应用。实测 `data` 是 14 条数组。
 *
 * ★ **不做图标下载**：`app_img` 有 14 个地址，一次全下就是 14 次请求 ——
 * 直接踩 QPS≤1 的红线。图标按名字本地推断（见 `data.portal.toUiApp`）。
 */
class LivePortalFrequentAppsRepository(
    private val api: PortalApi,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : PortalFrequentAppsRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<PortalAppEntry>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<PortalAppEntry>> = _state.asStateFlow()

    init {
        epoch.registerReset {
            guard.invalidate()
            _state.value = ListState.FirstLoad
        }
    }

    override suspend fun refresh() {
        val token = guard.begin()
        if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return
        val result = catchingActive { api.fetchFrequentApps() }
        guard.publish(token) {
            _state.value = result.fold(
                // 门户历史上出现过同一条应用重复下发，按 id 去重。
                onSuccess = { _state.value.settleFirstPage(PortalParsers.parseFrequentApps(it.raw), clock()) { app -> app.id } },
                onFailure = { retainCachedOnFailure(_state.value, it.asPortalException()) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 未读数
// ---------------------------------------------------------------------------

interface PortalUnreadRepository {
    val state: StateFlow<CountState>
    suspend fun refresh(force: Boolean = false): Boolean
    fun reset()
}

/**
 * 未读数。**15 分钟下限 + 未知不为 0**（与 A2 的 [RefreshPolicy] 同一套）。
 *
 * 实测该接口的 `data` 是**标量**（真机取到 307），且与 9 个分组的未读之和完全吻合 ——
 * 所以这个数可以放心当角标用。
 */
class LivePortalUnreadRepository(
    private val api: PortalApi,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = RefreshPolicy.UNREAD_MIN_INTERVAL_MS,
    private val epoch: AccountEpoch = AccountEpoch(),
) : PortalUnreadRepository {

    private val _state = MutableStateFlow(CountState())
    override val state: StateFlow<CountState> = _state.asStateFlow()

    private val guard = StaleGuard(epoch)
    private var lastSuccessMs: Long? = null

    init {
        epoch.registerReset {
            guard.invalidate()
            lastSuccessMs = null
            _state.value = CountState()
        }
    }

    override suspend fun refresh(force: Boolean): Boolean {
        val token = guard.begin()
        val last = lastSuccessMs
        if (!force && last != null && clock() - last < minIntervalMs) return false
        val result = catchingActive { PortalParsers.parseUnreadCount(api.fetchUnreadCount().raw) }
        return guard.publish(token) {
            result.fold(
                onSuccess = { value ->
                    val now = clock()
                    _state.value = if (value == null) {
                        _state.value.copy(stale = _state.value.value is CountValue.Known)
                    } else {
                        lastSuccessMs = now
                        CountState(CountValue.Known(value), stale = false, updatedAtMillis = now)
                    }
                },
                onFailure = { error ->
                    val failure = error.asPortalException()
                    _state.value = if (failure.isAuthFailure || failure is HzcuException.LoginExpired) {
                        CountState()
                    } else if (_state.value.value is CountValue.Known) {
                        _state.value.copy(stale = true)
                    } else CountState()
                },
            )
        }
    }

    override fun reset() {
        epoch.publish(epoch.token()) {
            guard.invalidate()
            lastSuccessMs = null
            _state.value = CountState()
        }
    }
}

// ---------------------------------------------------------------------------
// 待办三计数
// ---------------------------------------------------------------------------

interface PortalTodoRepository {
    /** `null` = 不知道。**界面显示「—」而不是 0。** */
    val counts: StateFlow<PortalTodoCounts?>
    val stale: StateFlow<Boolean>
    suspend fun refresh(force: Boolean = false): Boolean
}

class LivePortalTodoRepository(
    private val api: PortalApi,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = RefreshPolicy.UNREAD_MIN_INTERVAL_MS,
    private val epoch: AccountEpoch = AccountEpoch(),
) : PortalTodoRepository {

    private val _counts = MutableStateFlow<PortalTodoCounts?>(null)
    private val _stale = MutableStateFlow(false)
    override val counts: StateFlow<PortalTodoCounts?> = _counts.asStateFlow()
    override val stale: StateFlow<Boolean> = _stale.asStateFlow()

    private val guard = StaleGuard(epoch)
    private var lastSuccessMs: Long? = null

    init {
        epoch.registerReset {
            guard.invalidate()
            lastSuccessMs = null
            _counts.value = null
            _stale.value = false
        }
    }

    override suspend fun refresh(force: Boolean): Boolean {
        val token = guard.begin()
        val last = lastSuccessMs
        if (!force && last != null && clock() - last < minIntervalMs) return false
        val result = catchingActive { PortalParsers.parseTodoCounts(api.fetchTodoCounts().raw) }
        return guard.publish(token) {
            result.fold(
                onSuccess = { parsed ->
                    _counts.value = if (parsed.isAllUnknown) null else parsed
                    _stale.value = false
                    lastSuccessMs = clock()
                },
                onFailure = { error ->
                    if (error.asPortalException() is HzcuException.LoginExpired) {
                        _counts.value = null
                        _stale.value = false
                    } else _stale.value = _counts.value != null
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 新闻
// ---------------------------------------------------------------------------

interface PortalNewsRepository {
    val groups: StateFlow<ListState<PortalNewsGroup>>
    val news: StateFlow<ListState<PortalNewsItem>>
    val selectedGroup: StateFlow<PortalNewsGroup?>
    suspend fun refreshGroups()
    suspend fun selectGroup(group: PortalNewsGroup)
    suspend fun loadMore()

    /**
     * 下拉刷新：重取当前这一组的第一页，不清空已有内容。
     *
     * 与 selectGroup 只差一件事，但用户看得见：它走 startRefresh，
     * 已经有内容时列表原地不动、只把 refreshing 置起来；
     * selectGroup 会先把列表清成 FirstLoad，界面一闪成「正在取得新闻…」再冒出来。
     */
    suspend fun refreshKeepingContent()
}

/**
 * 新闻。
 *
 * ★ 分页用 **`start`/`end` 1 基闭区间行号**（页大小 8），
 * 参数是 **`cataid`**（取自分组的 `dataid`）——**不是** page/pageSize，
 * 也**不是** `dataid`/`groupid`/`columnId`（那几个实测无效）。
 */
class LivePortalNewsRepository(
    private val api: PortalApi,
    private val pageSize: Int = PortalParsers.NEWS_PAGE_SIZE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
    snapshotFile: java.io.File? = null,
) : PortalNewsRepository {

    private val guard = StaleGuard(epoch)
    private val groupsGuard = StaleGuard(epoch)
    private val ioMutex = Mutex()

    private val _groups = MutableStateFlow<ListState<PortalNewsGroup>>(ListState.FirstLoad)
    private val _news = MutableStateFlow<ListState<PortalNewsItem>>(ListState.FirstLoad)
    private val _selected = MutableStateFlow<PortalNewsGroup?>(null)

    override val groups: StateFlow<ListState<PortalNewsGroup>> = _groups.asStateFlow()
    override val news: StateFlow<ListState<PortalNewsItem>> = _news.asStateFlow()
    override val selectedGroup: StateFlow<PortalNewsGroup?> = _selected.asStateFlow()

    private var reachedEnd = false
    private var activeReadToken: Long? = null
    private data class GroupKey(val ownerGeneration: Long, val id: String, val cataId: Int?)
    private data class GroupSnapshot(
        val state: ListState<PortalNewsItem>, val reachedEnd: Boolean, val group: PortalNewsGroup,
    )
    private val groupSnapshots = mutableMapOf<GroupKey, GroupSnapshot>()
    private val diskSnapshot = snapshotFile?.let { PortalNewsSnapshot(it, epoch) }
    private var diskSeeded = false

    private fun groupKey(group: PortalNewsGroup) = GroupKey(epoch.token(), group.id, group.dataId)

    private fun seedSnapshot() {
        val owner = epoch.token()
        epoch.publishData(owner) {
            if (diskSeeded || epoch.identity() == null) return@publishData
            diskSeeded = true
            val restored = diskSnapshot?.read(owner) ?: return@publishData
            guard.begin()
            restored.entries.forEach { entry ->
                groupSnapshots[groupKey(entry.group)] = GroupSnapshot(
                    entry.state, entry.state.metaOrNull?.hasMore != true, entry.group,
                )
            }
            _groups.value = ListState.Content(restored.entries.map { it.group }, ListMeta(stale = true))
            _selected.value = restored.selected
            restored.selected?.let { group ->
                groupSnapshots[groupKey(group)]?.let { snapshot ->
                    _news.value = snapshot.state
                    reachedEnd = snapshot.reachedEnd
                }
            }
        }
    }

    private fun rememberSelected() {
        val group = _selected.value ?: return
        val state = settledFlags(_news.value)
        groupSnapshots[groupKey(group)] = GroupSnapshot(state, reachedEnd, group)
    }

    private fun persistSnapshot() {
        val owner = epoch.token()
        val entries = groupSnapshots.filterKeys { it.ownerGeneration == owner }.values.mapNotNull { snapshot ->
            when (snapshot.state) {
                is ListState.Content, is ListState.Empty -> if (snapshot.state.metaOrNull?.updatedAtMillis != null) {
                    PortalNewsSnapshot.Entry(snapshot.group, snapshot.state)
                } else null
                else -> null
            }
        }
        diskSnapshot?.write(owner, _selected.value, entries)
    }

    private fun settledFlags(state: ListState<PortalNewsItem>): ListState<PortalNewsItem> = when (state) {
        is ListState.Content -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
        is ListState.Empty -> state.copy(meta = state.meta.copy(refreshing = false, loadingMore = false))
        else -> state
    }

    private fun failed(state: ListState<PortalNewsItem>, error: Throwable): ListState<PortalNewsItem> {
        val failure = error.asPortalException()
        return if (failure is HzcuException.LoginExpired) when (state) {
            is ListState.Content -> state.copy(meta = state.meta.copy(
                refreshing = false, loadingMore = false, stale = true, lastRefreshError = failure,
            ))
            is ListState.Empty -> state.copy(meta = state.meta.copy(
                refreshing = false, loadingMore = false, stale = true, lastRefreshError = failure,
            ))
            else -> state.failWith(failure)
        } else state.failWith(failure)
    }

    private fun hasNextPage(raw: JsonObject): Boolean =
        PortalParsers.hasMoreByReturnedCount((raw["data"] as? JsonArray)?.size ?: 0, pageSize)

    init {
        epoch.registerReset {
            guard.invalidate()
            groupsGuard.invalidate()
            groupSnapshots.clear()
            diskSeeded = false
            activeReadToken = null
            _groups.value = ListState.FirstLoad
            _news.value = ListState.FirstLoad
            _selected.value = null
            reachedEnd = false
            snapshotFile?.let { check(!it.exists() || it.delete()) { "Could not clear portal news snapshot" } }
        }
        seedSnapshot()
    }

    override suspend fun refreshGroups() {
        seedSnapshot()
        val token = groupsGuard.begin()
        if (!groupsGuard.publish(token) { _groups.value = _groups.value.startRefresh() }) return
        val result = catchingActive { api.fetchNewsGroups() }
        groupsGuard.publish(token) {
            _groups.value = result.fold(
                onSuccess = { _groups.value.settleFirstPage(PortalParsers.parseNewsGroups(it.raw), clock()) { g -> g.id } },
                onFailure = { retainCachedOnFailure(_groups.value, it.asPortalException()) },
            )
        }
    }

    override suspend fun selectGroup(group: PortalNewsGroup) {
        seedSnapshot()
        var requestToken: Long? = null
        if (!epoch.publishData(epoch.token()) {
            if (_selected.value?.let(::groupKey) == groupKey(group) && activeReadToken == guard.current) {
                _selected.value = group
                return@publishData
            }
            rememberSelected()
            requestToken = guard.begin()
            activeReadToken = null
            _selected.value = group
            val cached = groupSnapshots[groupKey(group)]
            _news.value = cached?.state ?: ListState.FirstLoad
            reachedEnd = cached?.reachedEnd ?: false
            if (group.dataId == null) {
                _news.value = ListState.Empty(ListMeta(hasMore = false))
                reachedEnd = true
                rememberSelected()
                requestToken = null
            }
        }) return
        refreshWindow(requestToken ?: return)
    }

    override suspend fun refreshKeepingContent() {
        seedSnapshot()
        var requestToken: Long? = null
        if (!guard.publish(guard.current) {
            if (_selected.value?.dataId == null || activeReadToken == guard.current) return@publish
            requestToken = guard.begin()
        }) return
        refreshWindow(requestToken ?: return)
    }

    private suspend fun refreshWindow(token: Long) {
        var cataId: Int? = null
        var pages = 1
        if (!guard.publish(token) {
            cataId = _selected.value?.dataId ?: return@publish
            pages = (_news.value.metaOrNull?.page ?: 1).coerceAtLeast(1)
            activeReadToken = token
            _news.value = settledFlags(_news.value)
            _news.value = _news.value.startRefresh()
        }) return
        val category = cataId ?: return
        try {
            val result = catchingActive {
                val rows = mutableListOf<PortalNewsItem>()
                var lastPage = 1
                var hasMore = false
                for (page in 1..pages) {
                    if (!guard.isCurrent(token)) return
                    val response = api.fetchNewsList(cataId = category, page = page, pageSize = pageSize)
                    if (!guard.isCurrent(token)) return
                    rows += PortalParsers.parseNewsList(response.raw)
                    lastPage = page
                    hasMore = hasNextPage(response.raw)
                    if (!hasMore) break
                }
                val items = rows.distinctBy { it.id }
                val meta = ListMeta(page = lastPage, hasMore = hasMore, updatedAtMillis = clock())
                if (items.isEmpty()) ListState.Empty(meta) else ListState.Content(items, meta)
            }
            guard.publish(token) {
                val state = result.fold(
                    onSuccess = { it },
                    onFailure = { failed(_news.value, it) },
                )
                reachedEnd = state.metaOrNull?.hasMore != true
                _news.value = state
                rememberSelected()
                if (result.isSuccess) persistSnapshot()
            }
        } catch (cancelled: CancellationException) {
            guard.publish(token) {
                _news.value = settledFlags(_news.value)
                rememberSelected()
            }
            throw cancelled
        } finally {
            guard.publish(token) { activeReadToken = null }
        }
    }

    override suspend fun loadMore() {
        if (!ioMutex.tryLock()) return
        try {
            var snapshot: ListState.Content<PortalNewsItem>? = null
            var cataId: Int? = null
            var requestToken: Long? = null
            if (!guard.publish(guard.current) {
                val content = _news.value as? ListState.Content ?: return@publish
                if (!content.meta.hasMore || content.meta.refreshing || content.meta.loadingMore || reachedEnd) return@publish
                cataId = _selected.value?.dataId ?: return@publish
                snapshot = content
                requestToken = guard.begin()
                activeReadToken = requestToken
                _news.value = content.startLoadMore()
            }) return
            val token = requestToken ?: return
            val content = snapshot ?: return
            val category = cataId ?: return
            val nextPage = content.meta.page + 1
            try {
                val result = catchingActive { api.fetchNewsList(cataId = category, page = nextPage, pageSize = pageSize) }
                guard.publish(token) {
                    _news.value = result.fold(
                        onSuccess = { response ->
                            val items = PortalParsers.parseNewsList(response.raw)
                            reachedEnd = !hasNextPage(response.raw)
                            content.appendPage(items, nextPage, !reachedEnd, clock()) { it.id }
                        },
                        onFailure = { failed(content, it) },
                    )
                    rememberSelected()
                    if (result.isSuccess) persistSnapshot()
                }
            } catch (cancelled: CancellationException) {
                guard.publish(token) {
                    _news.value = settledFlags(content)
                    rememberSelected()
                }
                throw cancelled
            } finally {
                guard.publish(token) { activeReadToken = null }
            }
        } finally {
            ioMutex.unlock()
        }
    }
}

// ---------------------------------------------------------------------------
// 消息
// ---------------------------------------------------------------------------

interface PortalMessageRepository {
    val groups: StateFlow<ListState<PortalMessageGroup>>
    val messages: StateFlow<ListState<PortalMessageItem>>
    val selectedGroup: StateFlow<PortalMessageGroup?>
    suspend fun refreshGroups()
    suspend fun selectGroup(group: PortalMessageGroup)
    suspend fun refreshMessages()
    suspend fun loadMore()

    /**
     * 下拉刷新：重取当前这一组的第一页，不清空已有内容（走 startRefresh）。
     *
     * 与 refreshMessages 的区别就是刷新时列表会不会闪一下：
     * refreshMessages 转手给 selectGroup，会先把列表清成 FirstLoad。
     */
    suspend fun refreshKeepingContent()

    /**
     * 把一条标成已读。
     *
     * 顺序是固定的（docs/B7-WALKTHROUGH.md 第 2 节）：**先本地去点**（乐观），再发写请求；
     * 写失败就把那一行退回未读并返回 false —— 界面说已读而服务端说未读，
     * 会让人把没看过的通知漏掉。
     *
     * 这里**不碰未读总数**：服务端的计数口径未知（只知它是标量），
     * 本地减一会长期越偏。调用方成功后重取 getmessagenoreadcount。
     *
     * @return true = 服务端已接受（或它本来就是已读，没发请求）；false = 写失败、已回滚。
     */
    suspend fun markRead(id: String): Boolean

    /**
     * 把**当前这一组**整个标成已读。
     *
     * 契约是 POST setallmessageread/{分组名字}，门户没有「所有分组一起标」的接口。
     *
     * 同一个红线：这里**不碰任何计数**。服务端总未读 304 而各组 noread 之和 302，
     * 本地减一会长期越偏；调用方成功后重取分组与 getmessagenoreadcount。
     *
     * @return true = 服务端已接受（列表已重取）；false = 名字为空或写失败。
     */
    suspend fun markAllRead(group: String): Boolean
}

/**
 * 消息。
 *
 * ★ 三个实测坑都在这里对齐：
 * 1. `listmessage` **必须 JSON 包体**（[PortalApi.fetchMessages] 已经这么做）；
 * 2. 数组在**信封顶层的 `rows`**，`data` 是 `null`；
 * 3. 分页**按返回条数**判，**不用 `total`** —— 实测 `total=20` 而 `allrecords=192`。
 */
class LivePortalMessageRepository(
    private val api: PortalApi,
    private val pageSize: Int = PortalParsers.MESSAGE_PAGE_SIZE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
    snapshotFile: java.io.File? = null,
) : PortalMessageRepository {

    private val guard = StaleGuard(epoch)
    private val groupsGuard = StaleGuard(epoch)
    private val ioMutex = Mutex()

    private val _groups = MutableStateFlow<ListState<PortalMessageGroup>>(ListState.FirstLoad)
    private val _messages = MutableStateFlow<ListState<PortalMessageItem>>(ListState.FirstLoad)
    private val _selected = MutableStateFlow<PortalMessageGroup?>(null)

    override val groups: StateFlow<ListState<PortalMessageGroup>> = _groups.asStateFlow()
    override val messages: StateFlow<ListState<PortalMessageItem>> = _messages.asStateFlow()
    override val selectedGroup: StateFlow<PortalMessageGroup?> = _selected.asStateFlow()

    private var reachedEnd = false
    private var activeReadToken: Long? = null
    private data class GroupKey(val ownerGeneration: Long, val name: String)
    private data class GroupSnapshot(val state: ListState<PortalMessageItem>, val reachedEnd: Boolean)
    private val groupSnapshots = mutableMapOf<GroupKey, GroupSnapshot>()
    private val diskSnapshot = snapshotFile?.let { PortalMessageSnapshot(it, epoch) }
    private var diskSeeded = false

    private fun <T> failed(state: ListState<T>, error: Throwable): ListState<T> {
        val failure = error.asPortalException()
        return if (failure is HzcuException.LoginExpired) when (state) {
            is ListState.Content -> state.copy(meta = state.meta.copy(
                refreshing = false, loadingMore = false, stale = true, lastRefreshError = failure))
            is ListState.Empty -> state.copy(meta = state.meta.copy(
                refreshing = false, loadingMore = false, stale = true, lastRefreshError = failure))
            else -> state.failWith(failure)
        } else state.failWith(failure)
    }

    private fun seedSnapshot() {
        val owner = epoch.token()
        epoch.publishData(owner) {
            if (diskSeeded) return@publishData
            diskSeeded = true
            val restored = diskSnapshot?.read(owner) ?: return@publishData
            restored.states.forEach { (name, state) ->
                groupSnapshots[GroupKey(owner, name)] = GroupSnapshot(state, state.metaOrNull?.hasMore != true)
            }
            _groups.value = ListState.Content(restored.groups, ListMeta(stale = true))
            _selected.value = restored.groups.firstOrNull { it.name == restored.selected }
            _selected.value?.let { group ->
                groupSnapshots[GroupKey(owner, group.name)]?.let { snapshot ->
                    _messages.value = snapshot.state
                    reachedEnd = snapshot.reachedEnd
                }
            }
        }
    }

    private fun rememberSelected() {
        val group = _selected.value ?: return
        val state = when (val current = _messages.value) {
            is ListState.Content -> current.copy(meta = current.meta.copy(refreshing = false, loadingMore = false))
            is ListState.Empty -> current.copy(meta = current.meta.copy(refreshing = false, loadingMore = false))
            else -> current
        }
        val owner = epoch.token()
        groupSnapshots[GroupKey(owner, group.name)] = GroupSnapshot(state, reachedEnd)
        val groups = (_groups.value as? ListState.Content)?.items.orEmpty()
        if (groups.isNotEmpty()) diskSnapshot?.write(owner, group.name, groups,
            groupSnapshots.filterKeys { it.ownerGeneration == owner }.mapKeys { it.key.name }.mapValues { it.value.state })
    }

    init {
        epoch.registerReset {
            guard.invalidate()
            groupsGuard.invalidate()
            groupSnapshots.clear()
            diskSeeded = false
            snapshotFile?.let { check(!it.exists() || it.delete()) { "Could not clear portal message snapshot" } }
            activeReadToken = null
            _groups.value = ListState.FirstLoad
            _messages.value = ListState.FirstLoad
            _selected.value = null
            reachedEnd = false
        }
    }

    override suspend fun refreshGroups() {
        seedSnapshot()
        refreshGroups(groupsGuard.begin())
    }

    private suspend fun refreshGroups(token: Long) {
        if (!groupsGuard.publish(token) { _groups.value = _groups.value.startRefresh() }) return
        val result = catchingActive { api.fetchMessageGroups() }
        var replacement: PortalMessageGroup? = null
        val selectionAtStart = _selected.value
        groupsGuard.publish(token) {
            _groups.value = result.fold(
                onSuccess = {
                    val groups = PortalParsers.parseMessageGroups(it.raw)
                    val current = _selected.value
                    val next = reconcileMessageGroup(groups, current)
                    if (current?.name == next?.name) _selected.value = next
                    else replacement = next
                    if (next == null) {
                        rememberSelected()
                        guard.invalidate()
                        activeReadToken = null
                        _selected.value = null
                        _messages.value = ListState.FirstLoad
                        reachedEnd = false
                    }
                    _groups.value.settleFirstPage(groups, clock()) { group -> group.name }
                },
                onFailure = { failed(_groups.value, it) },
            )
        }
        replacement?.let { next ->
            if (groupsGuard.isCurrent(token) && _selected.value == selectionAtStart) selectGroup(next)
        }
    }

    private fun hasNextMessagePage(raw: JsonObject): Boolean =
        PortalParsers.hasMoreByReturnedCount((raw["rows"] as? JsonArray)?.size ?: 0, pageSize)

    override suspend fun selectGroup(group: PortalMessageGroup) {
        var requestToken: Long? = null
        if (!epoch.publishData(epoch.token()) {
            if (_selected.value?.name == group.name) {
                _selected.value = group
                return@publishData
            }
            rememberSelected()
            requestToken = guard.begin()
            activeReadToken = null
            _selected.value = group
            val cached = groupSnapshots[GroupKey(epoch.token(), group.name)]
            _messages.value = cached?.state ?: ListState.FirstLoad
            reachedEnd = cached?.reachedEnd ?: false
        }) return
        refreshMessages(requestToken ?: return)
    }

    override suspend fun refreshMessages() = refreshKeepingContent()

    override suspend fun refreshKeepingContent() {
        var requestToken: Long? = null
        if (!guard.publish(guard.current) {
            if (_selected.value == null || activeReadToken == guard.current) return@publish
            requestToken = guard.begin()
        }) return
        refreshMessages(requestToken ?: return)
    }

    private suspend fun refreshMessages(token: Long) {
        var selected: PortalMessageGroup? = null
        var pages = 1
        if (!guard.publish(token) {
            val group = _selected.value ?: return@publish
            val current = _messages.value
            selected = group
            pages = (current.metaOrNull?.page ?: 1).coerceAtLeast(1)
            activeReadToken = token
            _messages.value = when (current) {
                is ListState.Content -> current.copy(meta = current.meta.copy(loadingMore = false)).startRefresh()
                else -> current.startRefresh()
            }
        }) return
        val group = selected ?: return

        try {
            val result = catchingActive {
                val rows = mutableListOf<PortalMessageItem>()
                var lastPage = 1
                var hasMore = false
                for (page in 1..pages) {
                    if (!guard.isCurrent(token)) return
                    val response = api.fetchMessages(group = group.name, page = page, pageSize = pageSize)
                    if (!guard.isCurrent(token)) return
                    rows += PortalParsers.parseMessageRows(response.raw)
                    lastPage = page
                    hasMore = hasNextMessagePage(response.raw)
                    if (!hasMore) break
                }
                val items = newestMessageRows(rows.distinctBy { it.id })
                val meta = ListMeta(page = lastPage, hasMore = hasMore, updatedAtMillis = clock())
                if (items.isEmpty()) ListState.Empty(meta) else ListState.Content(items, meta)
            }
            guard.publish(token) {
                val state = result.fold(
                    onSuccess = { it },
                    onFailure = { failed(_messages.value, it) },
                )
                reachedEnd = state.metaOrNull?.hasMore != true
                _messages.value = state
                rememberSelected()
            }
        } catch (cancelled: CancellationException) {
            guard.publish(token) {
                _messages.value = retainCachedOnFailure(_messages.value, cancelled.asPortalException())
                rememberSelected()
            }
            throw cancelled
        } finally {
            guard.publish(token) { activeReadToken = null }
        }
    }

    override suspend fun loadMore() {
        if (!ioMutex.tryLock()) return
        try {
            var snapshot: ListState.Content<PortalMessageItem>? = null
            var selected: PortalMessageGroup? = null
            var requestToken: Long? = null
            if (!guard.publish(guard.current) {
                val content = _messages.value as? ListState.Content ?: return@publish
                val meta = content.meta
                if (!meta.hasMore || meta.refreshing || meta.loadingMore || reachedEnd) return@publish
                val group = _selected.value ?: return@publish
                snapshot = content
                selected = group
                requestToken = guard.begin()
                _messages.value = content.startLoadMore()
            }) return
            val token = requestToken ?: return
            val content = snapshot ?: return
            val group = selected ?: return
            val nextPage = content.meta.page + 1
            try {
                val result = catchingActive {
                    api.fetchMessages(group = group.name, page = nextPage, pageSize = pageSize)
                }
                guard.publish(token) {
                    _messages.value = result.fold(
                        onSuccess = { response ->
                            val items = PortalParsers.parseMessageRows(response.raw)
                            reachedEnd = !hasNextMessagePage(response.raw)
                            val appended = content.appendPage(items, nextPage, !reachedEnd, clock()) { it.id }
                            if (appended is ListState.Content) appended.copy(items = newestMessageRows(appended.items)) else appended
                        },
                        onFailure = { failed(content, it) },
                    )
                    rememberSelected()
                }
            } catch (cancelled: CancellationException) {
                guard.publish(token) {
                    _messages.value = retainCachedOnFailure(content, cancelled.asPortalException())
                    rememberSelected()
                }
                throw cancelled
            }
        } finally {
            ioMutex.unlock()
        }
    }

    override suspend fun markRead(id: String): Boolean {
        val token = guard.begin()
        val content = _messages.value as? ListState.Content ?: return false
        val row = content.items.firstOrNull { it.id == id }
        if (row == null || row.isRead) return guard.isCurrent(token)
        if (!guard.publish(token) {
            _messages.value = content.copy(items = MessageRead.markOne(content.items, id))
            rememberSelected()
        }) return false
        val ok = catchingActive { api.markMessageRead(id) }.getOrDefault(false)
        val published = guard.publish(token) {
            if (!ok) {
                val now = _messages.value
                if (now is ListState.Content) {
                    _messages.value = now.copy(items = MessageRead.revertOne(now.items, content.items, id))
                }
            }
            rememberSelected()
        }
        return ok && published
    }

    /**
     * 分组全部已读。成功后就重取一遍列表 —— 界面上的「已读」必须是服务端说已读，
     * 本地先涂一遍再等服务端，一旦写失败就留下「看着读过、其实没读」的假象。
     */
    override suspend fun markAllRead(group: String): Boolean {
        val ownerToken = epoch.token()
        val token = guard.begin()
        val name = group.trim()
        if (name.isEmpty() || !epoch.isCurrent(ownerToken) || !guard.publish(token) {}) return false
        val ok = catchingActive { api.markAllMessagesRead(name) }.getOrDefault(false)
        if (!ok || !guard.isCurrent(token)) return false
        refreshMessages(token)
        var groupToken: Long? = null
        if (!epoch.publish(ownerToken) { groupToken = groupsGuard.begin() }) return false
        refreshGroups(groupToken ?: return false)
        return epoch.isCurrent(ownerToken)
    }
}

// ---------------------------------------------------------------------------
// 搜索
// ---------------------------------------------------------------------------

interface PortalSearchRepository {
    val state: StateFlow<ListState<PortalAppEntry>>
    suspend fun search(keyword: String)
    fun clear()
}

class LivePortalSearchRepository(
    private val api: PortalApi,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : PortalSearchRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<PortalAppEntry>>(ListState.Empty())
    override val state: StateFlow<ListState<PortalAppEntry>> = _state.asStateFlow()

    /** 上一次**成功取回**的关键词；同词再搜只是后台刷新，不清空已有结果。 */
    private var activeKeyword: String? = null

    init {
        epoch.registerReset {
            guard.invalidate()
            activeKeyword = null
            _state.value = ListState.Empty()
        }
    }

    override suspend fun search(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val token = guard.begin()
        val keepCurrent = trimmed == activeKeyword && _state.value.metaOrNull != null
        if (!guard.publish(token) {
                _state.value = if (keepCurrent) _state.value.startRefresh() else ListState.FirstLoad
            }
        ) {
            return
        }
        try {
            val result = catchingActive { api.searchApps(trimmed) }
            guard.publish(token) {
                _state.value = result.fold(
                    onSuccess = {
                        activeKeyword = trimmed
                        // 搜索结果没有分组，parseSearchRows 统一打「搜索结果」。
                        _state.value.settleFirstPage(PortalParsers.parseSearchRows(it.raw), clock()) { app -> app.id }
                    },
                    onFailure = { com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure(_state.value, it.asPortalException()) },
                )
            }
        } finally {
            guard.publish(token) {
                val current = _state.value
                _state.value = when (current) {
                    is ListState.Content -> current.copy(meta = current.meta.copy(refreshing = false, loadingMore = false))
                    is ListState.Empty -> current.copy(meta = current.meta.copy(refreshing = false, loadingMore = false))
                    else -> current
                }
            }
        }
    }

    override fun clear() {
        epoch.publish(epoch.token()) {
            guard.invalidate()
            activeKeyword = null
            _state.value = ListState.Empty()
        }
    }
}

// ---------------------------------------------------------------------------
// 共用
// ---------------------------------------------------------------------------

/** 把任意异常收敛成 [HzcuException]。**未知异常绝不被当成登录失效。** */
internal fun Throwable.asPortalException(): HzcuException =
    this as? HzcuException ?: HzcuException.Transport(
        details = "异常类型：${this.javaClass.simpleName}",
        cause = this,
    )
