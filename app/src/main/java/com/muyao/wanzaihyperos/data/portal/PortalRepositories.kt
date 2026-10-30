package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.appendPage
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startLoadMore
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.withHasMore
import com.muyao.wanzaihyperos.data.repo.CountState
import com.muyao.wanzaihyperos.data.repo.RefreshPolicy
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.PortalApi
import com.muyao.wanzaihyperos.net.isAuthFailure
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
) : PortalCatalogRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<PortalAppGroup>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<PortalAppGroup>> = _state.asStateFlow()

    override suspend fun refresh() {
        val token = guard.begin()
        _state.value = _state.value.startRefresh()
        val result = runCatching { api.fetchCatalog() }
        if (!guard.isCurrent(token)) return
        _state.value = result.fold(
            onSuccess = { response ->
                val catalog = PortalParsers.parseCatalog(response.raw)
                // 分组按**名字**去重：服务端理论上不该重复，但重复一次就会在界面上
                // 出现两个同名分组，用户会以为加载错了。
                _state.value.settleFirstPage(catalog.groups, clock()) { it.name }
            },
            onFailure = { _state.value.failWith(it.asPortalException()) },
        )
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
) : PortalFrequentAppsRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<PortalAppEntry>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<PortalAppEntry>> = _state.asStateFlow()

    override suspend fun refresh() {
        val token = guard.begin()
        _state.value = _state.value.startRefresh()
        val result = runCatching { api.fetchFrequentApps() }
        if (!guard.isCurrent(token)) return
        _state.value = result.fold(
            // 门户历史上出现过同一条应用重复下发，按 id 去重。
            onSuccess = { _state.value.settleFirstPage(PortalParsers.parseFrequentApps(it.raw), clock()) { app -> app.id } },
            onFailure = { _state.value.failWith(it.asPortalException()) },
        )
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
) : PortalUnreadRepository {

    private val _state = MutableStateFlow(CountState())
    override val state: StateFlow<CountState> = _state.asStateFlow()

    private var lastSuccessMs: Long? = null

    override suspend fun refresh(force: Boolean): Boolean {
        val last = lastSuccessMs
        if (!force && last != null && clock() - last < minIntervalMs) return false
        return try {
            val response = api.fetchUnreadCount()
            val value = PortalParsers.parseUnreadCount(response.raw)
            val now = clock()
            _state.value = if (value == null) {
                // ★ 服务端没给数（或形状变了）→ **保持「不知道」**。
                _state.value.copy(stale = _state.value.value is CountValue.Known)
            } else {
                lastSuccessMs = now
                CountState(CountValue.Known(value), stale = false, updatedAtMillis = now)
            }
            true
        } catch (error: Throwable) {
            val failure = error.asPortalException()
            _state.value = if (failure.isAuthFailure || failure is HzcuException.LoginExpired) {
                CountState()
            } else if (_state.value.value is CountValue.Known) {
                _state.value.copy(stale = true)
            } else {
                CountState()
            }
            true
        }
    }

    override fun reset() {
        lastSuccessMs = null
        _state.value = CountState()
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
) : PortalTodoRepository {

    private val _counts = MutableStateFlow<PortalTodoCounts?>(null)
    private val _stale = MutableStateFlow(false)
    override val counts: StateFlow<PortalTodoCounts?> = _counts.asStateFlow()
    override val stale: StateFlow<Boolean> = _stale.asStateFlow()

    private var lastSuccessMs: Long? = null

    override suspend fun refresh(force: Boolean): Boolean {
        val last = lastSuccessMs
        if (!force && last != null && clock() - last < minIntervalMs) return false
        return try {
            val parsed = PortalParsers.parseTodoCounts(api.fetchTodoCounts().raw)
            _counts.value = if (parsed.isAllUnknown) null else parsed
            _stale.value = false
            lastSuccessMs = clock()
            true
        } catch (error: Throwable) {
            val failure = error.asPortalException()
            if (failure is HzcuException.LoginExpired) {
                _counts.value = null
                _stale.value = false
            } else {
                _stale.value = _counts.value != null
            }
            true
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
) : PortalNewsRepository {

    private val guard = StaleGuard()
    private val ioMutex = Mutex()

    private val _groups = MutableStateFlow<ListState<PortalNewsGroup>>(ListState.FirstLoad)
    private val _news = MutableStateFlow<ListState<PortalNewsItem>>(ListState.FirstLoad)
    private val _selected = MutableStateFlow<PortalNewsGroup?>(null)

    override val groups: StateFlow<ListState<PortalNewsGroup>> = _groups.asStateFlow()
    override val news: StateFlow<ListState<PortalNewsItem>> = _news.asStateFlow()
    override val selectedGroup: StateFlow<PortalNewsGroup?> = _selected.asStateFlow()

    /** 是否已经拉到过最后一页（服务端没有总页数，只能靠短页判定）。 */
    private var reachedEnd = false

    override suspend fun refreshGroups() {
        val token = guard.begin()
        _groups.value = _groups.value.startRefresh()
        val result = runCatching { api.fetchNewsGroups() }
        if (!guard.isCurrent(token)) return
        _groups.value = result.fold(
            onSuccess = { _groups.value.settleFirstPage(PortalParsers.parseNewsGroups(it.raw), clock()) { g -> g.id } },
            onFailure = { _groups.value.failWith(it.asPortalException()) },
        )
    }

    override suspend fun selectGroup(group: PortalNewsGroup) {
        val cataId = group.dataId
        if (cataId == null) {
            // 没有 dataid 就取不了列表 —— 如实说，别去请求一个瞎猜的 cataid。
            _selected.value = group
            _news.value = ListState.Empty()
            return
        }
        val token = guard.begin()
        _selected.value = group
        _news.value = ListState.FirstLoad
        reachedEnd = false

        val result = runCatching { api.fetchNewsList(cataId = cataId, page = 1, pageSize = pageSize) }
        if (!guard.isCurrent(token)) return
        _news.value = result.fold(
            onSuccess = { response ->
                val items = PortalParsers.parseNewsList(response.raw)
                reachedEnd = !PortalParsers.hasMoreByReturnedCount(items.size, pageSize)
                _news.value.settleFirstPage(items, clock()) { it.id }.withHasMore(!reachedEnd)
            },
            onFailure = { _news.value.failWith(it.asPortalException()) },
        )
    }

    override suspend fun loadMore() {
        ioMutex.withLock {
            val content = _news.value
            if (content !is ListState.Content) return
            val meta = content.meta
            if (!meta.hasMore || meta.loadingMore || reachedEnd) return
            val group = _selected.value ?: return
            val cataId = group.dataId ?: return

            _news.value = content.startLoadMore()
            val nextPage = meta.page + 1
            val result = runCatching {
                api.fetchNewsList(cataId = cataId, page = nextPage, pageSize = pageSize)
            }
            _news.value = result.fold(
                onSuccess = { response ->
                    val items = PortalParsers.parseNewsList(response.raw)
                    reachedEnd = !PortalParsers.hasMoreByReturnedCount(items.size, pageSize)
                    content.appendPage(items, nextPage, !reachedEnd, clock()) { it.id }
                },
                onFailure = { content.failWith(it.asPortalException()) },
            )
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
) : PortalMessageRepository {

    private val guard = StaleGuard()
    private val ioMutex = Mutex()

    private val _groups = MutableStateFlow<ListState<PortalMessageGroup>>(ListState.FirstLoad)
    private val _messages = MutableStateFlow<ListState<PortalMessageItem>>(ListState.FirstLoad)
    private val _selected = MutableStateFlow<PortalMessageGroup?>(null)

    override val groups: StateFlow<ListState<PortalMessageGroup>> = _groups.asStateFlow()
    override val messages: StateFlow<ListState<PortalMessageItem>> = _messages.asStateFlow()
    override val selectedGroup: StateFlow<PortalMessageGroup?> = _selected.asStateFlow()

    private var reachedEnd = false

    override suspend fun refreshGroups() {
        val token = guard.begin()
        _groups.value = _groups.value.startRefresh()
        val result = runCatching { api.fetchMessageGroups() }
        if (!guard.isCurrent(token)) return
        _groups.value = result.fold(
            onSuccess = {
                _groups.value.settleFirstPage(
                    PortalParsers.parseMessageGroups(it.raw),
                    clock(),
                    // ⚠️ 分组标识是**名字**，不是数字 id。
                ) { group -> group.name }
            },
            onFailure = { _groups.value.failWith(it.asPortalException()) },
        )
    }

    override suspend fun selectGroup(group: PortalMessageGroup) {
        // ★ 先作废旧请求再记下新选择：顺序反了的话，旧响应可能在新选择写入之后落地，
        //   把列表覆盖成分组不符的内容。
        val token = guard.begin()
        _selected.value = group
        _messages.value = ListState.FirstLoad
        reachedEnd = false

        val result = runCatching { api.fetchMessages(group = group.name, page = 1, pageSize = pageSize) }
        if (!guard.isCurrent(token)) return
        _messages.value = result.fold(
            onSuccess = { response ->
                val items = PortalParsers.parseMessageRows(response.raw)
                reachedEnd = !PortalParsers.hasMoreByReturnedCount(items.size, pageSize)
                _messages.value.settleFirstPage(items, clock()) { it.id }.withHasMore(!reachedEnd)
            },
            onFailure = { _messages.value.failWith(it.asPortalException()) },
        )
    }

    override suspend fun refreshMessages() {
        val group = _selected.value ?: return
        selectGroup(group)
    }

    override suspend fun loadMore() {
        ioMutex.withLock {
            val content = _messages.value
            if (content !is ListState.Content) return
            val meta = content.meta
            if (!meta.hasMore || meta.loadingMore || reachedEnd) return
            val group = _selected.value ?: return

            _messages.value = content.startLoadMore()
            val nextPage = meta.page + 1
            val result = runCatching {
                api.fetchMessages(group = group.name, page = nextPage, pageSize = pageSize)
            }
            _messages.value = result.fold(
                onSuccess = { response ->
                    val items = PortalParsers.parseMessageRows(response.raw)
                    reachedEnd = !PortalParsers.hasMoreByReturnedCount(items.size, pageSize)
                    content.appendPage(items, nextPage, !reachedEnd, clock()) { it.id }
                },
                onFailure = { content.failWith(it.asPortalException()) },
            )
        }
    }

    override suspend fun markRead(id: String): Boolean {
        val content = _messages.value as? ListState.Content ?: return false
        val row = content.items.firstOrNull { it.id == id }
        // 已经是已读（重复点、或服务端本来就标过）：不再发请求，也不算失败。
        if (row == null || row.isRead) return true

        // 先本地去点：未读点必须比网络先动，否则点下去像没反应。
        _messages.value = content.copy(items = MessageRead.markOne(content.items, id))

        val ok = runCatching { api.markMessageRead(id) }.getOrDefault(false)
        if (ok) return true

        // 只退回这一条：期间若已刷新/切组换了整份列表，就不拿旧数据盖回去。
        val now = _messages.value
        if (now is ListState.Content) {
            _messages.value = now.copy(items = MessageRead.revertOne(now.items, content.items, id))
        }
        return false
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
) : PortalSearchRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<PortalAppEntry>>(ListState.Empty())
    override val state: StateFlow<ListState<PortalAppEntry>> = _state.asStateFlow()

    override suspend fun search(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val token = guard.begin()
        _state.value = ListState.FirstLoad
        val result = runCatching { api.searchApps(trimmed) }
        if (!guard.isCurrent(token)) return
        _state.value = result.fold(
            onSuccess = {
                // 搜索结果没有分组，parseSearchRows 统一打「搜索结果」。
                _state.value.settleFirstPage(PortalParsers.parseSearchRows(it.raw), clock()) { app -> app.id }
            },
            onFailure = { _state.value.failWith(it.asPortalException()) },
        )
    }

    override fun clear() {
        guard.invalidate()
        _state.value = ListState.Empty()
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
