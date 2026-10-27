package com.muyao.wanzaihyperos.data.apps

import com.muyao.wanzaihyperos.data.PortalApp

/**
 * 应用页那一排「常用」到底放哪几格。
 *
 * ## 为什么要单独有这个文件
 *
 * 以前这一排是 `native.take(4) + web.take(2)` —— **六格**，而且标题右边写着
 * 「最近使用」（`AppsSectionPage.kt` 178 行原来那句 `note = "最近使用"`）。
 * 可这一页从来没有记过任何一次打开，那个"最近使用"是在替业主撒谎。
 * v3 清单 #7 把它改成：恒 ≤4 格、有一个说得清的默认算法、并且可以手动钉/撤。
 *
 * ## 排序依据（业主验收判据要求"排序依据写在代码注释里（真数据，不编）"）
 *
 * 分档取先，**不使用权重**（权重就是猜数）。每一档都用一个真实存在的数据源：
 *
 * 1. [CommonReason.PINNED] —— 他自己钉过的，按钉的顺序。这是"可自定义"那一半。
 * 2. [CommonReason.LIVE] —— **此刻**真的有货：有一条还没截止的签到，或待板里某个来源
 *    真的给了至少一条待办（不是"待板落过一次库" —— 那份 `items` 永远是单元素包装，
 *    判 `isNotEmpty()` 会恒真，独立审计 v3 #7 第 2 轮抓到过）。
 *    喂进来的是 `graph.rollcall` 与 `graph.todoBoard` 已经落下来的那份状态
 *    （**不是** `graph.homework` —— 待办里含作业，走的是待办板那一支），不是猜的。
 *    ★ 界面措辞是「现在有事」，不是「本周有事」：信号就是"此刻屏上有一条没截止的"，
 *      说"本周"比证据宽（独立审计 v3 #7 第 1 轮 B-2）。
 *    它落在哪一格由 `AppOpenStrategy` 说了算（原生学在城院那一格装着签到与作业），
 *    这里不另写一份名单 —— 但这一档点亮的是**所有**走原生学在城院那格的入口，
 *    因为签到与作业都在同一个原生容器里，按格子分不出"哪一格有"。
 * 3. [CommonReason.SCHOOL] —— 学校门户自己那份「常用应用」，走 **`graph.frequent`**
 *    （接口 `listappinfoex`，实测 14 条），按**学校给的顺序**。这是学校统计出来的他的常用，
 *    不是我编的。
 *    ★ 以前这一档找的是**目录分组**里名叫「常用应用」的那组 —— 目录的组名其实是
 *    「热门推荐」「校园生活」，`PortalParsers.FREQUENT_GROUP_NAME` 只是那条接口的来源标签，
 *    所以这一档一直空转（独立审计 v3 #7 第 1 轮抓到）。
 * 4. [CommonReason.RECENT] —— 本 App 自己记的打开次数（[recordAppOpen] 那条链）。
 *    没记过就是 0，这一档就会自然空掉 —— 空掉不等于前几档可以拿来凑。
 * 5. [CommonReason.FALLBACK] —— 以上全都没有时，按门户目录原序补满，
 *    界面上会照实写「目录顺序」，不冒充"最近使用"。
 *
 * 同一格只会归进它命中的**第一档**（先到先得，不重复计）。
 */

/** 「常用」最多几格（业主 2026-10-22：「应用『常用』最多 4 格」）。 */
const val COMMON_LIMIT = 4

/** 这一格为什么在常用里 —— 界面上要能把它讲清楚。 */
enum class CommonReason(val label: String) {
    PINNED("你钉住的"),
    LIVE("现在有事"),
    SCHOOL("学校常用名单"),
    RECENT("在本应用打开得多"),
    FALLBACK("目录顺序"),
}

data class CommonPick(val app: PortalApp, val reason: CommonReason)

/** 各档的输入。缺哪一档就给空集合，算法不会替它编。 */
data class CommonSignals(
    val pinnedIds: List<Int> = emptyList(),
    val liveIds: Set<Int> = emptySet(),
    val schoolFrequentIds: List<Int> = emptyList(),
    val openCounts: Map<Int, Int> = emptyMap(),
)

/**
 * 从 [candidates] 里挑至多 [limit] 格常用。
 *
 * 只在**候选里存在的 id** 上生效：钉过的应用如果从目录里消失了（学校下架），
 * 它不会凭空占一格 —— 那会比"少一格"更糟，因为他点了打不开。
 */
fun pickCommon(
    candidates: List<PortalApp>,
    signals: CommonSignals,
    limit: Int = COMMON_LIMIT,
): List<CommonPick> {
    val byId = candidates.associateBy { it.id }
    val out = ArrayList<CommonPick>(limit)
    val taken = HashSet<Int>()

    fun add(id: Int, reason: CommonReason): Boolean {
        if (out.size >= limit) return false
        val app = byId[id] ?: return false
        if (!taken.add(id)) return false
        out += CommonPick(app, reason)
        return true
    }

    signals.pinnedIds.forEach { add(it, CommonReason.PINNED) }
    signals.liveIds.forEach { add(it, CommonReason.LIVE) }
    signals.schoolFrequentIds.forEach { add(it, CommonReason.SCHOOL) }
    signals.openCounts.entries
        .filter { it.value > 0 }
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .forEach { add(it.key, CommonReason.RECENT) }
    candidates.forEach { add(it.id, CommonReason.FALLBACK) }

    return out
}

/** 标题右边那句说明：把这一排**实际**是按什么凑起来的讲出来，一条理由都不藏。 */
fun commonCaption(picks: List<CommonPick>): String {
    if (picks.isEmpty()) return ""
    val counts = picks.groupingBy { it.reason }.eachCount()
    val order = CommonReason.entries.filter { counts.containsKey(it) }
    // 整排都是同一个理由时不必报数（「目录顺序×4」这种话在界面上像日志）。
    if (order.size == 1) return order.first().label
    return order.joinToString("·") {
        if (counts.getValue(it) == 1) it.label else "${it.label}×${counts.getValue(it)}"
    }
}
