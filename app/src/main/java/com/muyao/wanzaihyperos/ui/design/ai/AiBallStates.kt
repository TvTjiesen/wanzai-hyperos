package com.muyao.wanzaihyperos.ui.design.ai

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.account.AccountSource
import com.muyao.wanzaihyperos.data.account.AccountStatus
import com.muyao.wanzaihyperos.data.schedule.TodayPlan
import com.muyao.wanzaihyperos.data.todo.TodoItem

/**
 * 把每一页**已经算好的事实**翻译成小球的一张脸。
 *
 * 这个对象只有纯函数，没有 Compose、没有 IO、没有时间读取 —— 目的是让取值口径
 * 能被单元测试逐条钉住（`AiBallStatesTest`），而不是散在五个页面里各写一套 when。
 * 时间由调用方传进来（`hour`），所以这条函数在任何钟点下都可测。
 *
 * ## 五条不许松的口径
 *
 * 1. **不知道 != 0**。`ListState.FirstLoad` 与 `CountValue.Unknown` 一律 [AiBallState.Loading]，
 *    不许落到 [AiBallState.Idle]（`data/ListState.kt` 的红线，消息页角标显示「—」是同一件事）。
 * 2. **失败才叫 Error**。只有 `Failed` / `SessionExpired` 给 [AiBallState.Error]；
 *    设置页里「未登录」是待办不是故障，所以不进 Error。
 * 3. **数据到手且为空** 才是 [AiBallState.Idle]（「真的没有」）。
 * 4. **有活干才叫 Thinking**。`fetching` 必须来自真实的在途计数（`AppGraph.isFetching()`），
 *    不许拿「定时器在跑」冒充。
 * 5. **睡觉要有理由**。[AiBallState.Sleep] 只在深夜**且真的没事**时给；
 *    深夜但课没上完时给的是 [AiBallState.Busy] / [AiBallState.Idle]，不是睡脸。
 *
 * 规则与坐标见 `docs/B4-12-BALL-AS-CORE.md` §1。
 */
object AiBallStates {

    /**
     * 通用列表：失败 -> Error；首次加载 -> Loading；拿到（有内容或确实为空）-> Idle。
     *
     * 用在应用页（`graph.catalog`）。当这一页已经知道结果、而系统里还有取数在飞时，
     * 给的是 [AiBallState.Thinking] —— 「我知道现在是什么，但我正在更新它」。
     */
    fun ofList(state: ListState<*>?, fetching: Boolean = false): AiBallState {
        val base = when (state) {
            is ListState.Failed -> AiBallState.Error
            ListState.SessionExpired -> AiBallState.Error
            ListState.FirstLoad -> AiBallState.Loading
            is ListState.Content, is ListState.Empty -> AiBallState.Idle
            // null = 还没有第一帧状态（仓库刚建）。这与 FirstLoad 同义：还不知道。
            null -> AiBallState.Loading
        }
        return working(base, fetching)
    }

    /**
     * 数量类：null = 未知 -> Loading；拿到的数字（含 0）-> Idle。
     *
     * 用在消息页：`pendingCount` 为 null 时是「没取到待处理」，不是「没有待处理」。
     */
    fun ofCount(value: Int?, fetching: Boolean = false): AiBallState =
        working(if (value == null) AiBallState.Loading else AiBallState.Idle, fetching)

    /**
     * 首页「此刻」卡与「球场」。
     *
     * 优先级是刻意的，而且按「用户现在该做什么」排，不按「技术上的严重程度」排：
     *
     * 1. 真失败了就别画「今天没有课」（那会把故障说成好消息）。
     * 2. **有一张签到在等你**，比「正在读」更该被看见 —— 它有时限，读完了也补不回来。
     * 3. 还不知道就别画「没有」（见 `data/ListState.kt` 三条红线）。
     * 4. 正在上课是当下最硬的事实。
     * 5. 24 小时内有待办到期，比「待机」值得说。
     * 6. 有取数在飞 = 正在处理。
     * 7. 课上完了才是 Done。
     * 8. 深夜且真的没事才睡。
     * 9. 早上第一次打开 = 刚醒。
     */
    fun ofHome(
        plan: TodayPlan?,
        loading: Boolean,
        failure: String?,
        ongoing: Boolean,
        hour: Int = -1,
        waitingRollcall: Boolean = false,
        urgentTodo: Boolean = false,
        fetching: Boolean = false,
        /**
         * 距下一节课开始还有多少分钟（返工总单 B9）。
         *
         * null = 算不出来（今天没课、或这一节的上下课时间学校没给）。
         * **算不出来就不触发** —— 认不出时间的课不许猜（见 `data/schedule/TodayPlan.kt` 档头）。
         */
        minutesToNextClass: Int? = null,
    ): AiBallState = when {
        failure != null -> AiBallState.Error
        waitingRollcall -> AiBallState.Waiting
        loading -> AiBallState.Loading
        ongoing -> AiBallState.Busy
        // ★ B9：马上要上课，比「24 小时内有个待办」更急。放在 ongoing 之后：
        //   正在上课是更硬的事实。60 分钟是「现在就该动身」的口径。
        minutesToNextClass != null && minutesToNextClass in 0..59 -> AiBallState.Focus
        urgentTodo -> AiBallState.Focus
        plan == null -> AiBallState.Loading
        fetching -> AiBallState.Thinking
        plan.totalToday > 0 && plan.remaining.isEmpty() -> AiBallState.Done
        isDeepNight(hour) -> AiBallState.Sleep
        isEarlyMorning(hour) -> AiBallState.WakeUp
        else -> AiBallState.Idle
    }

    /**
     * 课表页：两份课表（教务 + 学在城院）里**至少有一份能用**就不算坏。
     *
     * 这一页的网格是 `pickSlots` 拼出来的（教务优先、学在城院兜底），所以
     * 「教务挂了但学在城院有货」时屏幕上是有课表的 —— 那种时候画 Error 是撒谎。
     * 反过来两份都拿不到才是真的这一页没数据。
     */
    fun ofTimetable(
        schedule: ListState<*>?,
        jw: ListState<*>?,
        fetching: Boolean = false,
    ): AiBallState {
        val a = ofList(schedule)
        val b = ofList(jw)
        val usable = { s: AiBallState -> s == AiBallState.Idle }
        val base = when {
            usable(a) || usable(b) -> AiBallState.Idle
            a == AiBallState.Error || b == AiBallState.Error -> AiBallState.Error
            else -> AiBallState.Loading
        }
        return working(base, fetching)
    }

    /**
     * 设置页账号面：还没读到 -> Loading；读到过（哪怕都是未登录）-> Idle。
     *
     * `AccountStatus.Unknown` = 「还没读到 / 读失败」，按 `data/account/AccountSources.kt:66-81`
     * 的规定它**不是未登录**，所以只要有一条 Unknown 就仍然算「不知道」。
     * 未登录（`LoggedOut`）在这页是正常的待办状态，不给 Error —— 与设计稿
     * 「已登录 0 项 / 共 6 项 -> 02 待机」一致。
     */
    fun ofAccounts(
        sources: List<AccountSource>?,
        fetching: Boolean = false,
    ): AiBallState {
        val base = when {
            sources == null || sources.isEmpty() -> AiBallState.Loading
            sources.any { it.status == AccountStatus.Unknown } -> AiBallState.Loading
            else -> AiBallState.Idle
        }
        return working(base, fetching)
    }

    /**
     * 「刚看见东西」的那一秒：[AiBallState.Curious]。
     *
     * 纯函数，因为「上一帧还在加载」这件事只有页面自己的 remember 知道；
     * 把这个判断留在页面里就等于又把口径散开了。
     */
    fun curiousIfJustArrived(wasLoading: Boolean, now: AiBallState): AiBallState =
        if (wasLoading && now == AiBallState.Idle) AiBallState.Curious else now

    /** 已经知道结果、但还有取数在飞 -> Thinking（30 思考中）。其它状态不受影响。 */
    private fun working(base: AiBallState, fetching: Boolean): AiBallState =
        if (fetching && base == AiBallState.Idle) AiBallState.Thinking else base

    /**
     * 有没有一件待办在 [withinMs] 内到期。
     *
     * 只认**真的解析出了截止时刻**的条目（`sortTimeMillis != null`）。日期说不清的待办
     * 不配被叫做「快到期了」—— 那是在替它编一个时间。已经过期的也算急（欠着的更该看见）。
     */
    fun hasUrgentTodo(
        items: List<TodoItem>?,
        nowMillis: Long,
        withinMs: Long = URGENT_WINDOW_MS,
    ): Boolean = items.orEmpty().any { t ->
        t.sortTimeMillis?.let { it <= nowMillis + withinMs } == true
    }

    /** 「快到期」的窗口：24 小时。 */
    const val URGENT_WINDOW_MS: Long = 24L * 60 * 60 * 1000

    /** 23:00–05:59：一天里唯一「真的没事就该睡」的时段。 */
    fun isDeepNight(hour: Int): Boolean = hour >= 23 || (hour in 0..5)

    /** 06:00–07:59：早上第一次打开时它「刚醒」。 */
    fun isEarlyMorning(hour: Int): Boolean = hour in 6..7
}
