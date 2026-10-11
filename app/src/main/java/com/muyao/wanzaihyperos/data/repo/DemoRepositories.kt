package com.muyao.wanzaihyperos.data.repo

import com.muyao.wanzaihyperos.data.AppGroup
import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.DemoCatalog
import com.muyao.wanzaihyperos.data.DemoMessage
import com.muyao.wanzaihyperos.data.DemoMessageGroup
import com.muyao.wanzaihyperos.data.DemoNews
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.TodoCount
import com.muyao.wanzaihyperos.data.appendPage
import com.muyao.wanzaihyperos.data.failLoadMore
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.items
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startLoadMore
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.withHasMore
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.isAuthFailure
import com.muyao.wanzaihyperos.session.PortalSession
import com.muyao.wanzaihyperos.session.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 把任意异常收敛成 [HzcuException]。
 *
 * 仓库层只对 UI 暴露一种错误类型，UI 的 `when` 才能穷尽。
 * 未知异常一律当「网络/传输失败」—— **绝不会**被误判成登录失效，
 * 因为那会把还登录着的用户踢到登录页（A2 明文禁止）。
 */
internal fun Throwable.asHzcuException(): HzcuException =
    this as? HzcuException ?: HzcuException.Transport(
        details = "异常类型：${this.javaClass.simpleName}",
        cause = this,
    )

/**
 * 示例数据版仓库集合。
 *
 * ### 为什么 A2 就要有「实现」而不是只有接口
 * 接口没有实现就无法证明它真的能用 —— 尤其是「切分组后旧响应不得覆盖新状态」
 * 这条要求，光写注释是证明不了的。这一套实现把 [StaleGuard] 真正接进每条异步路径，
 * 于是**单测可以直接驱动它**（注入可控的挂起来源，制造乱序返回）。
 *
 * A3 会把这里的 `load` 参数换成真正的门户调用；**类不变、接口不变、
 * UI 一行都不用改** —— 这正是「共用介面與資料底座」的意义。
 */
class DemoAppCatalogRepository(
    private val load: suspend () -> List<AppGroup> = { delay(160); DemoCatalog.groups },
    private val clock: () -> Long = System::currentTimeMillis,
) : AppCatalogRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<AppGroup>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<AppGroup>> = _state.asStateFlow()

    override suspend fun refresh() {
        val token = guard.begin()
        _state.value = _state.value.startRefresh()
        val result = runCatching { load() }
        // 过时响应：连状态都不碰。见 StaleGuard 的说明。
        if (!guard.isCurrent(token)) return
        _state.value = result.fold(
            onSuccess = { _state.value.settleFirstPage(it, clock()) { group -> group.title } },
            onFailure = { _state.value.failWith(it.asHzcuException()) },
        )
    }

    override fun seed(groups: List<AppGroup>) {
        guard.invalidate()
        _state.value = ListState.FirstLoad.settleFirstPage(groups, clock()) { it.title }
    }
}

/** 首页宫格（示例数据版）。 */
class DemoFrequentAppsRepository(
    private val load: suspend () -> List<PortalApp> = { delay(140); DemoCatalog.frequent },
    private val clock: () -> Long = System::currentTimeMillis,
) : FrequentAppsRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<PortalApp>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<PortalApp>> = _state.asStateFlow()

    override suspend fun refresh() {
        val token = guard.begin()
        _state.value = _state.value.startRefresh()
        val result = runCatching { load() }
        if (!guard.isCurrent(token)) return
        _state.value = result.fold(
            // 目录历史上有跨分组重复 id，不去重首页会出现两个一模一样的图标。
            onSuccess = { _state.value.settleFirstPage(it, clock()) { app -> app.id } },
            onFailure = { _state.value.failWith(it.asHzcuException()) },
        )
    }
}

/**
 * 消息仓库（示例数据版），**带分页与切分组**。
 *
 * 这里刻意不引入「同一时刻只有一个请求」的互斥锁：那样虽然也能避免覆盖，
 * 但会把用户的第二次点击**变成等待**。用 [StaleGuard] 则是让第一次的响应
 * 直接作废，第二次立刻生效 —— 用户看到的是「点哪个就是哪个」。
 */
class DemoMessageRepository(
    private val pageSize: Int = 6,
    private val loadGroups: suspend () -> List<DemoMessageGroup> = {
        delay(120); DemoCatalog.messageGroups
    },
    private val loadMessages: suspend (group: String?) -> List<DemoMessage> = { group ->
        delay(150)
        if (group == null) DemoCatalog.messages else DemoCatalog.messages.filter { it.group == group }
    },
    private val clock: () -> Long = System::currentTimeMillis,
) : MessageRepository {

    private val guard = StaleGuard()
    private val ioMutex = Mutex()

    private val _groups = MutableStateFlow<ListState<DemoMessageGroup>>(ListState.FirstLoad)
    private val _messages = MutableStateFlow<ListState<DemoMessage>>(ListState.FirstLoad)
    private val _selected = MutableStateFlow<String?>(null)

    override val groups: StateFlow<ListState<DemoMessageGroup>> = _groups.asStateFlow()
    override val messages: StateFlow<ListState<DemoMessage>> = _messages.asStateFlow()
    override val selectedGroup: StateFlow<String?> = _selected.asStateFlow()

    /** 当前分组下的**全量**结果，翻页只是从这里切片。 */
    private var loadedAll: List<DemoMessage> = emptyList()

    override suspend fun refreshGroups() {
        val token = guard.begin()
        _groups.value = _groups.value.startRefresh()
        val result = runCatching { loadGroups() }
        if (!guard.isCurrent(token)) return
        _groups.value = result.fold(
            onSuccess = { _groups.value.settleFirstPage(it, clock()) { item -> item.name } },
            onFailure = { _groups.value.failWith(it.asHzcuException()) },
        )
    }

    override suspend fun selectGroup(group: String?) {
        // ★ 关键：先作废旧请求，再记下新选择。顺序反过来的话，
        //   旧响应有可能在新选择写入之后才落地，把列表覆盖成分组不符的内容。
        val token = guard.begin()
        _selected.value = group
        _messages.value = ListState.FirstLoad
        loadedAll = emptyList()

        val result = runCatching { loadMessages(group) }
        if (!guard.isCurrent(token)) return
        _messages.value = result.fold(
            onSuccess = { all ->
                loadedAll = all
                _messages.value.settleFirstPage(all.take(pageSize), clock()) { it.id }
                    .withHasMore(all.size > pageSize)
            },
            onFailure = { _messages.value.failWith(it.asHzcuException()) },
        )
    }

    override suspend fun refreshMessages() {
        val token = guard.begin()
        _messages.value = _messages.value.startRefresh()
        val group = _selected.value
        val result = runCatching { loadMessages(group) }
        if (!guard.isCurrent(token)) return
        _messages.value = result.fold(
            onSuccess = { all ->
                loadedAll = all
                _messages.value.settleFirstPage(all.take(pageSize), clock()) { it.id }
                    .withHasMore(all.size > pageSize)
            },
            onFailure = { _messages.value.failWith(it.asHzcuException()) },
        )
    }

    /**
     * 翻页。
     *
     * ★ 分页**不使用服务端的 `total`**（实测见过 `total=9` 而返回 10 行）。
     * 判据是「全量结果里还有没有下一段」，翻到底就把 `hasMore` 置 false。
     */
    override suspend fun loadMore() {
        ioMutex.withLock {
            val content = _messages.value
            // 只有「已经有内容」才谈得上翻页；加载态/错误态下翻页没有意义。
            if (content !is ListState.Content) return
            val meta = content.meta
            if (!meta.hasMore || meta.loadingMore) return

            // 翻页不动 guard：它属于同一次「分组会话」，不该把同组的其它请求作废。
            _messages.value = content.startLoadMore()
            val already = content.items.size
            val nextChunk = loadedAll.drop(already).take(pageSize)
            if (nextChunk.isEmpty()) {
                _messages.value = content.copy(
                    meta = meta.copy(loadingMore = false, hasMore = false),
                )
                return
            }
            _messages.value = content.appendPage(
                newItems = nextChunk,
                page = meta.page + 1,
                hasMore = already + nextChunk.size < loadedAll.size,
                nowMillis = clock(),
            ) { it.id }
        }
    }
}

/** 新闻仓库（示例数据版）。与消息同样的切分组/翻页纪律。 */
class DemoNewsRepository(
    private val pageSize: Int = 6,
    private val loadGroups: suspend () -> List<String> = { delay(110); DemoCatalog.newsGroups },
    private val loadNews: suspend (group: String?) -> List<DemoNews> = { group ->
        delay(140)
        if (group == null) DemoCatalog.news else DemoCatalog.news.filter { it.group == group }
    },
    private val clock: () -> Long = System::currentTimeMillis,
) : NewsRepository {

    private val guard = StaleGuard()
    private val _groups = MutableStateFlow<List<String>>(emptyList())
    private val _news = MutableStateFlow<ListState<DemoNews>>(ListState.FirstLoad)
    private val _selected = MutableStateFlow<String?>(null)

    override val groups: StateFlow<List<String>> = _groups.asStateFlow()
    override val news: StateFlow<ListState<DemoNews>> = _news.asStateFlow()
    override val selectedGroup: StateFlow<String?> = _selected.asStateFlow()

    private var loadedAll: List<DemoNews> = emptyList()

    override suspend fun refreshGroups() {
        val token = guard.begin()
        val result = runCatching { loadGroups() }
        if (!guard.isCurrent(token)) return
        result.onSuccess { _groups.value = it }
    }

    override suspend fun selectGroup(group: String?) {
        val token = guard.begin()
        _selected.value = group
        _news.value = ListState.FirstLoad
        loadedAll = emptyList()
        val result = runCatching { loadNews(group) }
        if (!guard.isCurrent(token)) return
        _news.value = result.fold(
            onSuccess = { all ->
                loadedAll = all
                _news.value.settleFirstPage(all.take(pageSize), clock()) { it.id }
                    .withHasMore(all.size > pageSize)
            },
            onFailure = { _news.value.failWith(it.asHzcuException()) },
        )
    }

    override suspend fun refreshNews() = selectGroup(_selected.value)

    override suspend fun loadMore() {
        val content = _news.value
        if (content !is ListState.Content) return
        val meta = content.meta
        if (!meta.hasMore || meta.loadingMore) return
        _news.value = content.startLoadMore()
        val already = content.items.size
        val nextChunk = loadedAll.drop(already).take(pageSize)
        if (nextChunk.isEmpty()) {
            _news.value = content.copy(meta = meta.copy(loadingMore = false, hasMore = false))
            return
        }
        _news.value = content.appendPage(
            newItems = nextChunk,
            page = meta.page + 1,
            hasMore = already + nextChunk.size < loadedAll.size,
            nowMillis = clock(),
        ) { it.id }
    }
}

/**
 * 未读数（示例数据版）。**15 分钟下限 + 未知不为 0** 都在这条实现里。
 */
class DemoUnreadRepository(
    private val load: suspend () -> Int = { delay(120); DemoCatalog.unreadTotal },
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = RefreshPolicy.UNREAD_MIN_INTERVAL_MS,
) : UnreadRepository {

    private val _state = MutableStateFlow(CountState())
    override val state: StateFlow<CountState> = _state.asStateFlow()

    private var lastSuccessMs: Long? = null

    override suspend fun refresh(force: Boolean): Boolean {
        val last = lastSuccessMs
        // 自动刷新（force=false）一律受 15 分钟下限约束；**默认关闭的自动轮询**
        // 就是靠这道闸门兜住的 —— 将来谁加了定时器，也刷不动。
        if (!force && last != null && clock() - last < minIntervalMs) return false

        return try {
            val value = load()
            val now = clock()
            lastSuccessMs = now
            _state.value = CountState(CountValue.Known(value), stale = false, updatedAtMillis = now)
            true
        } catch (error: Throwable) {
            val failure = error.asHzcuException()
            // ★ 失败**绝不写 0**：
            //   - 有上次成功取到的值 → 保留它并标 stale（界面显示「12 条（可能不是最新）」）；
            //   - 从未成功过 → 保持 Unknown（界面显示「—」，而不是「0 条未读」）；
            //   - 凭据失效 → 清成 Unknown：那个数已经不可信了，留着比没有更糟。
            _state.value = when {
                failure is HzcuException.LoginExpired -> CountState()
                _state.value.value is CountValue.Known -> _state.value.copy(stale = true)
                else -> CountState()
            }
            true
        }
    }

    override fun reset() {
        lastSuccessMs = null
        _state.value = CountState()
    }
}

/** 待办三计数（示例数据版）。纪律与未读完全一致。 */
class DemoTodoRepository(
    private val load: suspend () -> TodoCount = { delay(130); DemoCatalog.todoCount },
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = RefreshPolicy.UNREAD_MIN_INTERVAL_MS,
) : TodoRepository {

    private val _counts = MutableStateFlow<TodoCount?>(null)
    private val _stale = MutableStateFlow(false)
    override val counts: StateFlow<TodoCount?> = _counts.asStateFlow()
    override val stale: StateFlow<Boolean> = _stale.asStateFlow()

    private var lastSuccessMs: Long? = null

    override suspend fun refresh(force: Boolean): Boolean {
        val last = lastSuccessMs
        if (!force && last != null && clock() - last < minIntervalMs) return false
        return try {
            _counts.value = load()
            _stale.value = false
            lastSuccessMs = clock()
            true
        } catch (error: Throwable) {
            val failure = error.asHzcuException()
            if (failure is HzcuException.LoginExpired) {
                _counts.value = null
                _stale.value = false
            } else {
                // 保留上次的三计数并标 stale；从未成功过就保持 null（界面显示「—」）。
                _stale.value = _counts.value != null
            }
            true
        }
    }

    override fun reset() {
        lastSuccessMs = null
        _counts.value = null
        _stale.value = false
    }
}

/** 应用搜索（示例数据版）。快速改关键字时旧结果不会覆盖新结果。 */
class DemoAppSearchRepository(
    private val load: suspend (String) -> List<PortalApp> = { keyword ->
        delay(150)
        DemoCatalog.all.filter { it.name.contains(keyword, ignoreCase = true) }
    },
    private val clock: () -> Long = System::currentTimeMillis,
) : AppSearchRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<PortalApp>>(ListState.Empty())
    override val state: StateFlow<ListState<PortalApp>> = _state.asStateFlow()

    override suspend fun search(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val token = guard.begin()
        _state.value = ListState.FirstLoad
        val result = runCatching { load(trimmed) }
        if (!guard.isCurrent(token)) return
        _state.value = result.fold(
            onSuccess = { _state.value.settleFirstPage(it, clock()) { app -> app.id } },
            onFailure = { _state.value.failWith(it.asHzcuException()) },
        )
    }

    override fun clear() {
        guard.invalidate()
        _state.value = ListState.Empty()
    }
}

/**
 * 登录态（示例数据版，真实凭据走 [PortalSession]）。
 *
 * 登出时**必须**作废所有在飞请求：否则登出前发出的请求会带着数据后到，
 * 把已清空的界面又填满 —— 用户会以为「登出没生效」。
 */
class PortalSessionRepository(
    private val session: PortalSession,
    private val guard: StaleGuard,
    private val onLoggedOut: suspend () -> Unit = {},
) : SessionRepository {

    private val _portalState = MutableStateFlow(session.state)
    override val portalState: StateFlow<SessionState> = _portalState.asStateFlow()

    override suspend fun onLoggedIn(accessToken: String, userCode: String?) {
        session.save(accessToken, userCode)
        refreshState()
    }

    override suspend fun logout() {
        // 顺序要紧：先让在飞请求全部作废，再清凭据与界面状态。
        guard.invalidate()
        session.clear()
        onLoggedOut()
        refreshState()
    }

    private fun refreshState() {
        _portalState.value = session.state
    }
}
