package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.net.HzcuException

/**
 * 列表数据的**状态机**。全项目唯一一份。
 *
 * 方案要求「列表状态必须把 首次加载 / 内容 / 空 / 错误 / 刷新 / 翻页 分别表示」。
 * 把它们压成两个布尔量（`isLoading` + `isError`）会在三个地方骗人：
 *
 * 1. **首次加载 vs 空**：`isLoading=false` 且列表为空，界面上是「暂无内容」，
 *    但真实情况可能是**还没发请求**。用户看到「暂无」会以为学校没数据。
 * 2. **空 vs 错误**：请求失败时列表也是空的，如果只判空就会显示「暂无内容」——
 *    这是**替服务端撒谎**（业主在 Flutter 版提过的原始问题）。
 * 3. **刷新失败 vs 没有内容**：已经有数据、这次刷新失败时，
 *    正确做法是继续显示旧数据并标注「可能不是最新的」，
 *    而不是把整页清空成一个错误页。所以「刷新失败」记在 [ListMeta.lastRefreshError] 里，
 *    内容本身留在 [Content]。
 *
 * ★ 另一条红线：**未知不得当成 0**。数量类字段（未读数、待办数）取不到时
 * 必须是 `null` 而不是 0 —— 界面上显示「—」，而不是「0 条未读」。
 * 参见 [CountValue]。
 */
sealed interface ListState<out T> {

    /** 首次加载中，**还没有任何内容可显示**。不要与 [Empty] 混淆。 */
    data object FirstLoad : ListState<Nothing>

    /** 有内容。刷新/翻页只是 [meta] 上的叠加标志，不改变「有内容」这个事实。 */
    data class Content<out T>(
        val items: List<T>,
        val meta: ListMeta = ListMeta(),
    ) : ListState<T>

    /** 服务端**明确**返回了空结果。这是事实，不是失败。 */
    data class Empty(val meta: ListMeta = ListMeta()) : ListState<Nothing>

    /**
     * 取数失败，且**没有任何可显示的内容**。
     * 注意与「刷新失败但有旧数据」区分：后者仍然是 [Content]，错误记在 meta 里。
     */
    data class Failed(
        val error: HzcuException,
        val meta: ListMeta = ListMeta(),
    ) : ListState<Nothing>

    /**
     * 登录失效。**必须与 [Failed] 分开**：这个要引导去登录，
     * 而网络失败时把用户判成「没登录」是最伤人的误判（他还登录着）。
     */
    data object SessionExpired : ListState<Nothing>
}

/**
 * 与内容正交的元信息。刷新、翻页、新鲜度都在这里。
 *
 * @param refreshing 用户主动下拉刷新中（内容仍在，界面不该闪成加载态）
 * @param loadingMore 正在加载下一页
 * @param page 已加载到第几页（从 1 开始）
 * @param hasMore 还有没有下一页。**不要用服务端的 `total` 算**：
 *   实测见过 `total=9` 而实际返回 10 行，用 total 翻页会少一页。
 *   正确做法是「本次返回条数 < 请求页大小 → 没有下一页」。
 * @param updatedAtMillis 上一次**成功**取数的时间
 * @param stale 展示的是旧数据（本次刷新失败或超出新鲜期）
 * @param lastRefreshError 本次刷新失败的原因。内容还在，所以不整体降级成 [ListState.Failed]。
 */
data class ListMeta(
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val updatedAtMillis: Long? = null,
    val stale: Boolean = false,
    val lastRefreshError: HzcuException? = null,
) {
    /** 有没有值得提示用户的问题（用于顶部横幅）。 */
    val hasRefreshProblem: Boolean get() = stale || lastRefreshError != null
}

/**
 * 「可能取不到的数量」。**未知与 0 严格分开**。
 *
 * 未读数 / 待办数取不到时，界面必须显示「—」。
 * 显示 0 等于告诉用户「你没有未读消息」，而我们其实**不知道**。
 * 这是业主在 Flutter 版明确提过的问题，属于验收项。
 */
sealed interface CountValue {
    /** 确实取到了。 */
    data class Known(val value: Int) : CountValue

    /** **不知道**（还没取、或取失败）。界面显示「—」。 */
    data object Unknown : CountValue

    /** 用于显示：Unknown → null，由界面决定画「—」。 */
    val orNull: Int? get() = (this as? Known)?.value
}

// ---------------------------------------------------------------------------
// 读取辅助
// ---------------------------------------------------------------------------

/** 当前可显示的内容；非 [ListState.Content] 时为空列表。 */
val <T> ListState<T>.items: List<T>
    get() = (this as? ListState.Content<T>)?.items ?: emptyList()

/** 元信息；[ListState.FirstLoad] / [ListState.SessionExpired] 没有 meta，给默认值。 */
val <T> ListState<T>.metaOrNull: ListMeta?
    get() = when (this) {
        is ListState.Content -> meta
        is ListState.Empty -> meta
        is ListState.Failed -> meta
        else -> null
    }

/** 是否已经有过一次明确的结论（有内容 或 确认空）。**首次加载中与失败都不算。** */
val <T> ListState<T>.hasSettled: Boolean
    get() = this is ListState.Content || this is ListState.Empty

// ---------------------------------------------------------------------------
// 状态迁移
// ---------------------------------------------------------------------------

/**
 * 开始刷新。**内容不会被清掉** —— 这是它与 [ListState.FirstLoad] 的关键差别。
 */
fun <T> ListState<T>.startRefresh(): ListState<T> = when (this) {
    is ListState.Content -> copy(meta = meta.copy(refreshing = true, lastRefreshError = null))
    is ListState.Empty -> copy(meta = meta.copy(refreshing = true, lastRefreshError = null))
    // 还没有内容（或上次是失败/登录失效）：这次就是首次加载，老老实实显示加载态。
    else -> ListState.FirstLoad
}

/** 开始加载下一页。只有已经有内容时才叫「翻页」。 */
fun <T> ListState<T>.startLoadMore(): ListState<T> = when (this) {
    is ListState.Content -> copy(meta = meta.copy(loadingMore = true))
    else -> this
}

/**
 * 首次页（或整表重载）成功。
 *
 * [deDupe] 是必须的：门户目录历史上出现过**跨分组重复 id**
 * （同一份历史实测里 87 条含重复）。不去重的话首页宫格会出现两次同一个图标，
 * 而且点击后两条的 `id` 相同，灰掉一条另一条也会跟着变。
 */
fun <T> ListState<T>.settleFirstPage(
    newItems: List<T>,
    nowMillis: Long,
    deDupe: (T) -> Any? = { it },
): ListState<T> {
    val distinct = newItems.distinctBy(deDupe)
    return if (distinct.isEmpty()) {
        // 「服务端明确说没有」= Empty；这与「取数失败」是两件事。
        ListState.Empty(
            ListMeta(
                refreshing = false,
                loadingMore = false,
                page = 1,
                hasMore = false,
                updatedAtMillis = nowMillis,
                stale = false,
                lastRefreshError = null,
            ),
        )
    } else {
        ListState.Content(
            items = distinct,
            meta = ListMeta(
                refreshing = false,
                loadingMore = false,
                page = 1,
                // 只有「取满一页」才认为可能还有下一页，见 ListMeta.hasMore 的说明。
                hasMore = true,
                updatedAtMillis = nowMillis,
                stale = false,
                lastRefreshError = null,
            ),
        )
    }
}

/**
 * 追加一页。用于翻页成功。
 *
 * 注意 [hasMore] 由调用方按「本次返回条数 < 页大小」算出来传进来，
 * **不要**用服务端 `total`（实测不可靠）。
 */
fun <T> ListState<T>.appendPage(
    newItems: List<T>,
    page: Int,
    hasMore: Boolean,
    nowMillis: Long,
    deDupe: (T) -> Any? = { it },
): ListState<T> {
    val existing = items
    val merged = if (existing.isEmpty()) newItems
    else (existing + newItems).distinctBy(deDupe)
    if (merged.isEmpty()) {
        return ListState.Empty(
            ListMeta(
                page = page,
                hasMore = hasMore,
                updatedAtMillis = nowMillis,
            ),
        )
    }
    return ListState.Content(
        items = merged,
        meta = ListMeta(
            refreshing = false,
            loadingMore = false,
            page = page,
            hasMore = hasMore,
            updatedAtMillis = nowMillis,
            stale = false,
            lastRefreshError = null,
        ),
    )
}

/**
 * 「被取消」**不是**「取不到」。
 *
 * 本轮真机根因（訊息／新聞两处「取不到」）：页面在 `LaunchedEffect(Unit)` 里直接
 * `runCatching { repo.refreshX() }`，仓库内部又是 `runCatching { api.fetch() }`。
 * 切走页面（点底栏、左右滑动、动画期间离场）会取消这个协程，
 * OkHttp/Compose 抛的是 `LeftCompositionCancellationException`（**不是** CancellationException 的常见子类），
 * 被 `runCatching` 当成普通失败吞掉 → `failWith(Transport("网络连接失败"))`
 * → 写进**仓库级**的 StateFlow。仓库活得比页面久，于是这条假错误会一直挂在那，
 * 下次进来还是「取不到訊息」，而真实原因只是"上一次没等完"。
 *
 * 判据放在这个**共用漏斗**而不是二十个 `runCatching` 现场：
 * 漏一处就还有一处会骗人（业主反复强调过"闸门要在共用段"）。
 */
private fun HzcuException.isCancellation(): Boolean {
    var t: Throwable? = this
    var guard = 0
    while (t != null && guard < 8) {
        if (t is kotlinx.coroutines.CancellationException) return true
        if (t is java.util.concurrent.CancellationException) return true
        if (t.javaClass.name.endsWith("LeftCompositionCancellationException")) return true
        if (t.message?.contains("left the composition") == true) return true
        if (t.message?.contains("Job was cancelled", ignoreCase = true) == true) return true
        t = t.cause
        guard++
    }
    return false
}

/**
 * 取数失败。
 *
 * ★ 关键判断：**已经显示的内容不会被清掉**。
 * 有数据时降级为「旧数据 + 标注刷新失败」，没数据时才是整页 [ListState.Failed]。
 * 认证失效走 [sessionExpired]，与网络失败分开。
 *
 * ★ 但**取消**不进这个分支：见 [isCancellation]，取消只清掉转圈标志，
 *   不写错误、不标 `stale`（否则一次切页会永久污染仓库状态）。
 */
fun <T> ListState<T>.failWith(error: HzcuException): ListState<T> {
    if (error.isCancellation()) return when (this) {
        is ListState.Content -> copy(
            meta = meta.copy(refreshing = false, loadingMore = false),
        )
        is ListState.Empty -> copy(
            meta = meta.copy(refreshing = false, loadingMore = false),
        )
        // 首屏没取完就被切走：留在 FirstLoad，下次进来会重新发请求（不是错误）
        else -> this
    }
    return when (error) {
    is HzcuException.LoginExpired -> ListState.SessionExpired
    else -> when (this) {
        is ListState.Content -> copy(
            meta = meta.copy(
                refreshing = false,
                loadingMore = false,
                stale = true,
                lastRefreshError = error,
            ),
        )
        is ListState.Empty -> copy(
            meta = meta.copy(
                refreshing = false,
                loadingMore = false,
                stale = true,
                lastRefreshError = error,
            ),
        )
        else -> ListState.Failed(error)
    }
    }
}

/**
 * 翻页失败：**保留已有内容**，只把翻页标志关掉并记下原因。
 * 同样地，**取消不算失败**（见 [failWith] 里那条根因说明）。
 */
fun <T> ListState<T>.failLoadMore(error: HzcuException): ListState<T> =
    if (error.isCancellation()) {
        when (this) {
            is ListState.Content -> copy(meta = meta.copy(loadingMore = false))
            else -> this
        }
    } else if (error is HzcuException.LoginExpired) {
        ListState.SessionExpired
    } else when (this) {
        is ListState.Content -> copy(
            meta = meta.copy(
                loadingMore = false,
                // 翻页失败不代表已有内容变旧，所以**不动 stale**。
                lastRefreshError = error,
            ),
        )
        else -> this
    }

/** 登录失效。任何状态都直接降级成 [ListState.SessionExpired]。 */
fun <T> ListState<T>.sessionExpired(): ListState<T> = ListState.SessionExpired

/**
 * 修正 [ListMeta.hasMore]。
 *
 * 首次页加载时用它可以表达「这一页之后还有没有」。
 * ★ 判据是**本次返回条数 < 请求页大小**（说明服务端已经没有更多了），
 * **不要**用服务端的 `total`：实测见过 `total=9` 而实际返回 10 行，
 * 用 total 算会少显示一页。
 */
fun <T> ListState<T>.withHasMore(hasMore: Boolean): ListState<T> = when (this) {
    is ListState.Content -> copy(meta = meta.copy(hasMore = hasMore))
    is ListState.Empty -> copy(meta = meta.copy(hasMore = hasMore))
    else -> this
}
