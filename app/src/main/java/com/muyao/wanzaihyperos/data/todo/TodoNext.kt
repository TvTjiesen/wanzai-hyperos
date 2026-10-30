package com.muyao.wanzaihyperos.data.todo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 「最近要交的那一件」。
 *
 * ### 為什麼要它
 *
 * 待辦頁是**按來源分組**的（作業一組、審批一組），每組內按截止時間排。
 * 分組是對的（兩邊「辦」法不同），但代價是：**「今天最该先动哪一件」看不见**。
 * 真机实测 19 条作业散在两组里，要自己滚动比对日期。
 *
 * 所以这里从所有分组里挑出**一件**最急的，单独摆一行。
 *
 * ### 三条不许编
 *
 * 1. **没有截止時間的条目不排**。學在城院的 `end_time` 实测常常是 null，
 *    門戶那条给的是**创建时间**而不是截止时间。拿它当截止时间排出来的顺序
 *    看起来很确定，其实是编的 —— 那些条目只计数（[unknownTimeCount]），不参与挑选。
 * 2. **已经过期的不跳过**。过期的作业仍然在待办里（还没交），
 *    它比任何未来的都急；跳过它去显示下周那份，等于帮人把迟交的事藏起来。
 * 3. 时间相同或都过期时按 key 稳定排序，保证**两次刷新看到的是同一件**
 *    （顺序每次都在跳会让人以为是新任务）。
 */
data class UrgentTodo(
    val item: TodoItem,
    /** 截止时刻的毫秒值（一定有值 —— 没值的条目根本不会进来）。 */
    val dueAtMillis: Long,
    /** 已经过了截止时刻。 */
    val overdue: Boolean,
    /** 距截止还有几天（**负数**表示已经过期几天）。按日历天算，不是按 24 小时整除。 */
    val daysAway: Long,
)

object TodoNext {

    /**
     * 从所有分组里挑出最急的一件；一条有截止时间的都没有时返回 null。
     *
     * @param now 用来判断「过期」与算天数。传进来而不是内部取 `now()` 是为了可单测。
     */
    fun pick(groups: List<TodoGroup>, now: LocalDateTime): UrgentTodo? =
        candidates(groups, now).minWithOrNull(ordering())

    /**
     * 把条目按「急迫程度」排好：过期的在前（越早过期越前），再按截止时刻升序，
     * 没截止时间的垫底（保持原有相对顺序）。
     *
     * 首页「要处理」用它取代「按分组顺序取前三条」—— 那样子组顺序一变，
     * 摆在前面的条目跟着变，而真正明天要交的可能一次都没露过面。
     */
    fun ordered(items: List<TodoItem>, now: LocalDateTime): List<TodoItem> {
        val nowMillis = nowMillis(now)
        // 只有**真有截止时间**的条目才按时间排。
        // 門戶那条给的是创建时间，混进来排会把 2025 年创建的老审批顶到最上面。
        val dated = items.filter { it.hasDeadline && it.sortTimeMillis != null }
        val rest = items.filter { !(it.hasDeadline && it.sortTimeMillis != null) }
        val overdue = dated.filter { (it.sortTimeMillis ?: 0L) < nowMillis }
            .sortedWith(compareBy({ it.sortTimeMillis }, { it.source.name }, { it.key }))
        val upcoming = dated.filter { (it.sortTimeMillis ?: 0L) >= nowMillis }
            .sortedWith(compareBy({ it.sortTimeMillis }, { it.source.name }, { it.key }))
        return overdue + upcoming + rest
    }

    /** 有多少条**有截止时间**。用来如实说「还有 N 条不知道什么时候到期」。 */
    fun datedCount(groups: List<TodoGroup>): Int =
        groups.sumOf { g -> g.items.count { it.hasDeadline && it.sortTimeMillis != null } }

    /** 有多少条**没有**截止时间（所以排不进「最近要交」）。 */
    fun unknownTimeCount(groups: List<TodoGroup>): Int =
        groups.sumOf { g -> g.items.count { !(it.hasDeadline && it.sortTimeMillis != null) } }

    /** 天数 → 人话。0 = 今天，1 = 明天，负数 = 已经过期几天。 */
    fun dueText(daysAway: Long): String = when {
        daysAway < 0 -> "已过期 " + (-daysAway) + " 天"
        daysAway == 0L -> "今天到期"
        daysAway == 1L -> "明天到期"
        else -> daysAway.toString() + " 天后到期"
    }

    private fun candidates(groups: List<TodoGroup>, now: LocalDateTime): List<UrgentTodo> {
        val nowMillis = nowMillis(now)
        val today = now.toLocalDate()
        return groups.flatMap { g -> g.items }.mapNotNull { item ->
            // 没有截止时间的（含門戶那条只有创建时间的）一条都不参与挑选。
            if (!item.hasDeadline) return@mapNotNull null
            val due = item.sortTimeMillis ?: return@mapNotNull null
            val dueDate = runCatching {
                Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrNull() ?: return@mapNotNull null
            UrgentTodo(
                item = item,
                dueAtMillis = due,
                overdue = due < nowMillis,
                daysAway = ChronoUnit.DAYS.between(today, dueDate),
            )
        }
    }

    private fun ordering(): Comparator<UrgentTodo> = compareBy(
        { if (it.overdue) 0 else 1 },
        { it.dueAtMillis },
        { it.item.source.name },
        { it.item.key },
    )

    private fun nowMillis(now: LocalDateTime): Long =
        now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 只给测试与「今天」这类展示用：把毫秒换成当地日期。 */
    internal fun dateOf(millis: Long): LocalDate? = runCatching {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull()
}
