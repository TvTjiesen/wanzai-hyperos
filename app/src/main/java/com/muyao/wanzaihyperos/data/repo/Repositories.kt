package com.muyao.wanzaihyperos.data.repo

import com.muyao.wanzaihyperos.data.AppGroup
import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.DemoMessage
import com.muyao.wanzaihyperos.data.DemoMessageGroup
import com.muyao.wanzaihyperos.data.DemoNews
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.TodoCount
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.StateFlow

/**
 * 刷新节奏的硬性下限。
 *
 * 计划 §9.2：未读类数据**至少 15 分钟**才允许再刷一次；**自动轮询默认关闭**。
 * 也就是说 A2 不提供任何定时器 —— 只有明确的用户动作（下拉、点进页面）
 * 才会触发刷新，且自动触发时会被这里的下限挡住。
 */
object RefreshPolicy {
    /** 未读数 / 待办数的自动刷新下限：15 分钟。 */
    const val UNREAD_MIN_INTERVAL_MS = 15 * 60 * 1000L
}

/**
 * 过时响应的闸门。
 *
 * ★ 计划 A2 的明文要求：**「切分組、搜索及登出後，過時響應不得覆蓋新狀態。」**
 *
 * 这条要求很容易被低估。具体会怎么坏：
 * - 用户在消息页点了「教学通知」，请求 A 还在飞，他又点了「校务通知」发出请求 B；
 *   B 先回来渲染了，A 后回来**把列表覆盖成教学通知** —— 用户看到的分类和内容对不上。
 * - 用户在搜索框打了「课」，又改成「课程」；前一次的结果后到，覆盖掉正确结果。
 * - 用户点了登出，登出前发出的请求后到，**把已清空的界面又填满数据**，
 *   看起来像「登出失败」。
 *
 * 做法是给每次请求发一个**代次号**：切分组 / 改搜索词 / 登出都会让代次号 +1，
 * 于是所有在飞请求的号都变旧；响应回来时对不上号就**直接丢弃**，
 * 连状态都不碰。这比「判断一下参数是否相同」可靠 —— 后者在
 * 「切走又切回来」时会误判成同一次请求。
 *
 * 线程安全：全部走 [AtomicLong]。UI 线程、IO 线程、多个协程都会碰它。
 */
class StaleGuard(private val epoch: AccountEpoch? = null) {

    private val generation = AtomicLong(0)
    @Volatile private var ownerGeneration = 0L

    /** 开始一次新请求，返回它的代次号。**同时让此前所有在飞请求作废。** */
    fun begin(): Long {
        val ownerToken = epoch?.token() ?: 0L
        return synchronized(this) {
            ownerGeneration = ownerToken
            generation.incrementAndGet()
        }
    }

    /**
     * 让所有在飞请求作废，但**不**开始新请求。
     * 用于登出：登出后不该再有任何响应能落回状态。
     */
    fun invalidate() = synchronized(this) {
        generation.incrementAndGet()
        Unit
    }

    /** 这个代次号还是最新的吗？不是就说明该响应已过时。 */
    fun isCurrent(token: Long): Boolean = generation.get() == token &&
        (epoch?.isDataCurrent(ownerGeneration) != false)

    fun publish(token: Long, action: () -> Unit): Boolean {
        val ownerToken = ownerGeneration
        val publish = {
            synchronized(this) {
                if (generation.get() != token) false else { action(); true }
            }
        }
        return epoch?.let { owner ->
            var committed = false
            owner.publishData(ownerToken) { committed = publish() } && committed
        } ?: publish()
    }

    /** 当前代次号（测试与诊断用）。 */
    val current: Long get() = generation.get()
}

/**
 * 数量类数据的完整状态。
 *
 * [value] 用 [CountValue]（而非 `Int`）是硬要求：
 * **取不到时必须能表达「不知道」**，界面显示「—」而不是「0」。
 * [stale] 说明这个数是上次成功拿到的，本次刷新失败了。
 */
data class CountState(
    val value: CountValue = CountValue.Unknown,
    val stale: Boolean = false,
    val updatedAtMillis: Long? = null,
)

/**
 * 全量应用目录（应用页的 7 个分组）。
 *
 * 真实实现负责把门户信封的差异藏起来：
 * - **包体类型因接口而异**（有的吃 form、有的只吃 JSON）；
 * - **数组位置因接口而异**（有的在 `data`、有的在信封顶层的 `rows`）。
 * UI 只看到 [ListState] 与领域对象，永远看不到 `code` / `success` / `rows`。
 */
interface AppCatalogRepository {
    val state: StateFlow<ListState<AppGroup>>

    /** 重新拉取整表。切分组等操作**不需要**调它。 */
    suspend fun refresh()

    /** 测试/预览用：直接置入一份数据，不发请求。 */
    fun seed(groups: List<AppGroup>)
}

/** 首页宫格（门户的「常用应用」）。 */
interface FrequentAppsRepository {
    val state: StateFlow<ListState<PortalApp>>
    suspend fun refresh()
}

/** 消息：分组 + 某分组下的消息列表（可分页）。 */
interface MessageRepository {
    val groups: StateFlow<ListState<DemoMessageGroup>>
    val messages: StateFlow<ListState<DemoMessage>>

    /** 当前选中的分组名；null 表示「全部」。 */
    val selectedGroup: StateFlow<String?>

    suspend fun refreshGroups()

    /**
     * 切换分组并加载。**会作废此前所有在飞请求**（见 [StaleGuard]），
     * 所以快速连点分组不会出现「内容与分类对不上」。
     */
    suspend fun selectGroup(group: String?)

    /** 下拉刷新当前分组。 */
    suspend fun refreshMessages()

    /** 翻页。没有下一页时不做任何事。 */
    suspend fun loadMore()
}

/** 新闻：分类 + 列表。 */
interface NewsRepository {
    val groups: StateFlow<List<String>>
    val news: StateFlow<ListState<DemoNews>>
    val selectedGroup: StateFlow<String?>

    suspend fun refreshGroups()
    suspend fun selectGroup(group: String?)
    suspend fun refreshNews()
    suspend fun loadMore()
}

/** 未读总数（底部导航的角标）。15 分钟下限见 [RefreshPolicy]。 */
interface UnreadRepository {
    val state: StateFlow<CountState>

    /**
     * 刷新未读数。
     *
     * @param force 用户**明确**要求刷新（点角标、下拉）时传 true，
     *   可越过 15 分钟下限；自动/顺带刷新传 false。
     * @return 真的发了请求就 true；被下限挡下则 false。
     */
    suspend fun refresh(force: Boolean = false): Boolean

    /** 登出：清空并恢复到「不知道」，**绝不显示 0**。 */
    fun reset()
}

/** 首页「待办事项」三个计数。与未读同一套 15 分钟下限与「未知不为 0」纪律。 */
interface TodoRepository {
    /**
     * 三个待办计数。
     *
     * `null` = **还不知道**（从未成功取到，或凭据失效被清）。
     * 界面必须显示「—」而不是「0 个待办」—— 这是业主明确提过的验收项。
     */
    val counts: StateFlow<TodoCount?>

    /** 展示的是上次成功的数据（本次刷新失败）。 */
    val stale: StateFlow<Boolean>

    suspend fun refresh(force: Boolean = false): Boolean

    /** 登出：回到「不知道」，**绝不显示 0**。 */
    fun reset()
}

/** 应用搜索。 */
interface AppSearchRepository {
    val state: StateFlow<ListState<PortalApp>>

    /**
     * 按关键字搜索。**每次调用都会作废上一次**（[StaleGuard]），
     * 所以「快速改关键字」时先返回的旧结果不会盖掉新结果。
     */
    suspend fun search(keyword: String)

    /** 清空（离开搜索页时调）。 */
    fun clear()
}

/** 登录态。UI 通过它决定显示宫格还是「去登录」。 */
interface SessionRepository {
    /** 门户登录态。**Unknown 不是 LoggedOut**，界面据此显示「检查中」而不是「未登录」。 */
    val portalState: StateFlow<com.muyao.wanzaihyperos.session.SessionState>

    /** 登录成功后调用：落盘凭据、装 cookie 罐、通知监听者。 */
    suspend fun onLoggedIn(accessToken: String, userCode: String?)

    /** 登出：清凭据 + 作废所有在飞请求 + 清空各仓库状态。 */
    suspend fun logout()
}
