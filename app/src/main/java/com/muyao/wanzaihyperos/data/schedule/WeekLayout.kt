package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot

/**
 * 課表版面算的**純邏輯**（不碰 Compose，所以可單測）。
 *
 * ## 為什麼要抽出來（业主 2026-10-19：「不要顯示那什么一周三節課，
 * 我要你把五節課全部顯示了，把雙層的課改成單層的，自己設計排版，
 * 這週只顯示本週的課程」）
 *
 * 那四條聽起來是視覺要求，但**每一條都是規則**：
 * 哪些日子該出現在版面上、兩門課重疊時怎麼表達、某節課本週到底有沒有。
 * 上一版把它們寫死在 `WeekGrid` 裡面，於是每次改版面都要重寫一次邏輯，
 * 而且**沒有測試盯著** —— 而課表顯示錯的代價是他走到錯的教室、
 * 在錯的時間出現。抽出來之後界面只消費結果。
 *
 * ## ★「單層」是怎麼實作的（2026-10-20 改成**對齊左側時間軸**）
 *
 * 上一版是**時間網格**：行=節次、欄=星期，撞課時再把那一欄**橫向切成兩道**
 *（雙層）。5 欄 × 2 道 ⇒ 每道只剩 ~25dp，課名豎成一串認不出的波浪 ——
 * 業主原話「我都看不見課了」。所以「雙層改單層」= **不切道**。
 *
 * T58 那版我把行高等於節次這件事一起刪了（一天一欄往下排），結果時間沒地方放，
 * 只能寫在每一塊課上。業主 2026-10-20：「**你别标时间啊，跟其他课表软件一样
 * 侧边写个时间课程对齐时间不行吗**」—— 對，課表的時間本來就該在**軸**上，
 * 不該每塊課自己背一行字。
 *
 * 現在：**左側一條時間軸（第幾節 + 幾點），行高等於一節，課塊按節次落在行上**。
 * 「單層」保住了（一欄一道，不橫向切），「對齊時間」也保住了。
 *
 * 代價與對策：撞課的兩門課**放不下同一個位置**（真機數據本週 0 對撞，
 * 但學在城院那份有）。[lane] 不切道、不藏課 —— 把後面那門**往下擠**並標
 * `misaligned`，界面上寫「與上一門同時」。宁可看着错位，也不能让一门课消失。
 */

/**
 * 節次 → 幾點幾分。
 *
 * ## 為什麼單獨一個物件（业主 2026-10-19：「你课表和时间对不上」）
 *
 * 上一版改排版时把**節次轴整条删掉了**，而那条轴上是唯一显示时间的地方
 *（时间是他在設定頁一格一格填的）。删掉之后课表上就只剩「第 3-4 节」——
 * 他填过的东西凭空消失，这就是"对不上"。
 *
 * 现在时间挂在**每一块课自己**身上（不再依赖某一列），網格和列表兩種排版都看得到。
 *
 * ## 兩層來源，逐格合併（2026-10-20）
 *
 * 1. **他自己填的**（`UiPrefs.periodTimes`）—— 填過的那一頭以他為準。
 *    他改過的東西要是被內置表蓋掉，就是「我明明改了沒生效」那種投訴；
 * 2. **學校日曆給的**（[SchoolPeriods]，從他 2026-10-20 發來的 `日历-浙大城市学院.ics` 推出來）
 *    —— 他沒填的那一格用這張表補上。
 *
 * 兩頭湊齊才畫（只寫「10:00」會被讀成「10:00 開始，那幾點結束？」）。
 * 任何一頭都沒有 ⇒ 回 null，界面那一行不佔位，**不編一個時間出來**。
 */
object PeriodClock {

    /** 一格一個頭：空白 = 沒填（不当成「填了個空字符串」）。 */
    private fun head(v: String?): String? = v?.trim()?.takeIf { it.isNotEmpty() }

    /** 第 [period] 節幾點上課：他填的那一格優先，沒填才用學校日曆。 */
    fun startOf(times: List<Pair<String, String>>, period: Int): String? =
        head(times.getOrNull(period - 1)?.first) ?: SchoolPeriods.startOf(period)

    /** 第 [period] 節幾點下課。 */
    fun endOf(times: List<Pair<String, String>>, period: Int): String? =
        head(times.getOrNull(period - 1)?.second) ?: SchoolPeriods.endOf(period)

    /**
     * @param times 第 N 個元素對應第 N 節（`UiPrefs.periodTimes` 的形態），可以整條為空
     * @return 「10:00-11:40」；任何一頭缺就回 null（**不顯示半截時間**）
     */
    fun label(times: List<Pair<String, String>>, start: Int, end: Int): String? {
        val from = startOf(times, start) ?: return null
        val to = endOf(times, end) ?: return null
        return "$from-$to"
    }

    /** 他自己一共填了幾節（設定頁那句「你填了 N 节」用）。 */
    fun filledCount(times: List<Pair<String, String>>): Int = times.count { head(it.first) != null }

    /** 合併之後一共認得幾節（這一節的上課或下課，任一頭有值就算認得）。 */
    fun knownCount(times: List<Pair<String, String>>): Int {
        val last = maxOf(times.size, SchoolPeriods.table.size)
        return (1..last).count {
            head(times.getOrNull(it - 1)?.first) != null ||
                head(times.getOrNull(it - 1)?.second) != null ||
                SchoolPeriods.startOf(it) != null || SchoolPeriods.endOf(it) != null
        }
    }
}

/** 一欄（一天）。 */
data class DayColumn(
    val dayOfWeek: Int,
    /** 這一天的課，按起始節次排序（**單層**：不再切成多道）。 */
    val slots: List<TronScheduleSlot>,
) {
    val dayLabel: String get() = "周" + DAY_LABELS.getOrElse(dayOfWeek - 1) { dayOfWeek.toString() }
    val isEmpty: Boolean get() = slots.isEmpty()

    /** 這一天的第一节课（欄頭顯示「第1节起」用；空欄為 null）。 */
    val firstPeriod: Int? get() = slots.minOfOrNull { it.periodStart }

    /**
     * 這一格有沒有「和同一時間的另一門課撞了」。
     *
     * ★ 為什麼要單獨標出來：單層排版下撞課的兩門課會**上下排開**，
     * 如果他不知道它們其實是同一時段，會以為是「連堂」而錯過第一門。
     * 撞課是**來源的事實**（真機樣本裡週二第 1-2 節確實有兩門），
     * 不是我們排錯了 —— 所以界面要說「同一时间还有 1 门」。
     */
    fun conflictsOf(slot: TronScheduleSlot): List<TronScheduleSlot> =
        slots.filter { it !== slot && WeekLayout.overlaps(it, slot) }

    private companion object {
        val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
    }
}

object WeekLayout {

    /** 週一到週五：**恆常顯示**（哪怕整天沒課）。 */
    val WEEKDAYS = (1..5).toList()

    /** 兩門課時間有沒有重疊（閉區間相交；不同天一律不算重疊）。 */
    fun overlaps(a: TronScheduleSlot, b: TronScheduleSlot): Boolean =
        a.dayOfWeek == b.dayOfWeek &&
            a.periodStart <= b.periodEnd && b.periodStart <= a.periodEnd

    /**
     * 本週要顯示哪幾欄。
     *
     * 1. 週一～五**一定在**。空的一天也是一條信息（「週三沒課」不能因為
     *    沒課就從版面上消失 —— 那會讓後面幾天的位置看起來往左挪了一格）；
     * 2. 週六日**只有本週真的有課才加進來**（這台機 442dp 寬，
     *    七欄時每欄只剩 60dp，課名又會豎成波浪）。
     *
     * @param slots 已經按目標週筛過的課（見 [forWeek]）
     */
    fun columns(slots: List<TronScheduleSlot>): List<DayColumn> {
        val days = WEEKDAYS + (6..7).filter { d -> slots.any { it.dayOfWeek == d } }
        return days.map { d ->
            DayColumn(d, slots.filter { it.dayOfWeek == d }.sortedBy { it.periodStart })
        }
    }

    /**
     * 「只顯示本週」：把全學期的段落筛成第 [week] 週真的有課的那些。
     *
     * ⚠️ 週次範圍是**閉區間**（`第1,3周` = 1~3 週，不是「第 1 和第 3 週」）——
     * 這個判讀有 30 門課的樣本撐著，見 [TronScheduleSlot] 的類註釋與
     * `docs/B13-TRONCLASS-SCHEDULE.txt`。判錯的後果是「某週少一门课」，
     * 那種錯**看不出來**（他不會知道那門課本週其實有）。
     *
     * [week] 為 null（還不知道目前是第幾週）時**原樣回傳** ——
     * 由界面去顯示「還不知道第幾週」，而不是假裝筛過了。
     */
    fun forWeek(slots: List<TronScheduleSlot>, week: Int?): List<TronScheduleSlot> =
        if (week == null) slots else slots.filter { it.activeIn(week) }

    /** 日程（列表）視圖：只留有課的那幾天，按天分組、按節次排序。 */
    fun agenda(slots: List<TronScheduleSlot>): List<Pair<Int, List<TronScheduleSlot>>> =
        slots.groupBy { it.dayOfWeek }
            .toSortedMap()
            .map { (day, list) -> day to list.sortedBy { it.periodStart } }

    /**
     * 本週一共幾門課（**同一門課拆成多段只算一門**）。
     *
     * 為什麼要 distinct：實測有課程被拆成 4 段（`周2第8-9节第10,16周;…`），
     * 直接 `slots.size` 會報「本週 12 門課」而其實只有 6 門 ——
     * 那種數字虛報沒有好處，只會讓人懷疑整頁。
     */
    fun courseCount(slots: List<TronScheduleSlot>): Int =
        slots.map { it.courseKey() }.distinct().size

    /** 同一門課的判別鍵：有 id 用 id，沒 id（教務來源可能沒給）退回課名。 */
    private fun TronScheduleSlot.courseKey(): String =
        if (courseId > 0) "id:$courseId" else "name:$courseName"

    /**
     * 一欄（一天）怎麼落在**節次行**上：把 [DayColumn] 摊成恰好 [maxPeriod] 行高的序列。
     *
     * 回來的清單**一定剛好佔滿 maxPeriod 行**（有空檔就插 [LaneCell.Free]），
     * 這樣每一欄的高度一致 ⇒ 左側時間軸的第 5 行和課塊的第 5 行**在同一條水平線上**。
     * 這是這個函式存在的全部理由：對齊一旦交給界面自己算，就會出現
     * 「週四的課比週二的低半格」那種錯，而那種錯他一眼看得出、我卻很難在程式裡發現。
     *
     * 撞課（同一欄兩門重疊）時**不切道、不藏課**：後面那門往下擠，
     * 並標 `misaligned = true` 讓界面寫「與上一門同時」。
     * 本週真機數據是 0 對撞（`docs/LOG.md` T62 用 .ics 逐個時刻核過），
     * 但學在城院那份有，所以這條路徑必須存在而且必須看得出來。
     */
    fun lane(column: DayColumn, maxPeriod: Int): List<LaneCell> {
        val cells = ArrayList<LaneCell>()
        var row = 1
        column.slots.sortedWith(compareBy({ it.periodStart }, { it.periodEnd })).forEach { slot ->
            val top = maxOf(row, slot.periodStart)
            if (top > row) cells.add(LaneCell.Free(top - row))
            if (top > maxPeriod) return@forEach          // 排到行數之外（來源給了個超界的節次）
            // **保住原時長**往下挪，而不是被压扁：一門 2 節的課擠下來還是應該佔 2 行，
            // 否則課塊高度不再等於節次，這條軸就白畫了。
            val bottom = (top + (slot.periodEnd - slot.periodStart)).coerceAtMost(maxPeriod)
            cells.add(
                LaneCell.Placed(
                    slot = slot,
                    rows = bottom - top + 1,
                    misaligned = slot.periodStart < row,  // 被上一門課壓住 ⇒ 它不在自己的行上
                ),
            )
            row = bottom + 1
        }
        if (row <= maxPeriod) cells.add(LaneCell.Free(maxPeriod - row + 1))
        return cells
    }

    /** 一欄裡某一塊位置上是「空的」還是「一堂課佔幾行」。 */
    sealed class LaneCell {
        /** 空 [rows] 行（這幾天這裡沒課 —— 空也是信息，不能塌掉）。 */
        data class Free(val rows: Int) : LaneCell()

        data class Placed(
            val slot: TronScheduleSlot,
            /** 佔幾行（= 幾節）。 */
            val rows: Int,
            /** true = 它**沒落在自己該在的行上**（前面有課撞了）。界面必须标出来。 */
            val misaligned: Boolean,
        ) : LaneCell()
    }

    /** 本週最多排到第幾節（來源偶爾會給個离谱的節次號，這裡封頂，不讓界面長成一屏空白）。 */
    fun rowsNeeded(columns: List<DayColumn>, cap: Int = 14): Int =
        columns.maxOfOrNull { c -> c.slots.maxOfOrNull { it.periodEnd } ?: 0 }?.coerceAtMost(cap) ?: 0

    /**
     * 週切換器上**第一行**（教學周本身），學期名另起一行小字。
     *
     * 真機上「第 6 周 · 本周 · 2026-2027学年第1学期」在 1216px 寬的卡片裡會折成兩行，
     * 而折行位置取決於學期名長度 —— 看上去像字被擠壞了。分開之後：
     * 第一行永遠短（他真正要知道的那個數），第二行才是學校原名，放不下就省略。
     */
    fun weekHeadline(baseWeek: Int?, offset: Int): String =
        // 和 switchLabel 的頭半段共用一份實作，免得兩處的「本週前/後」寫法漂開。
        switchLabel(baseWeek, offset, "")

    /**
     * 週切換那一行的文字。
     *
     * ★ 為什麼不寫「本週」（业主 2026-10-20：「上面的本周我哪知道是几周，
     *   改成第几周，第几学期」）：「本週」是相對詞，他得先自己算出今天是第幾週才看得懂，
     *   而那個數正是他打開這一頁想知道的東西。所以一律給**絕對**的「第 N 周」，
     *   偏移只在不是本週時作為補充說明寫在後面。
     *
     * @param term 學校自己寫的學期名（教务课表页标题里那段）；空 = 還沒取到，**不編**。
     */
    fun switchLabel(baseWeek: Int?, offset: Int, term: String): String {
        // 不知道第几周就**不写「本周」**：那一行是他判断"这屏是不是现在这周"的唯一依据，
        // 「第 ? 周 · 本周」看着像坏了，而且"本周"在这种状态下根本没有意义（业主 2026-10-20）。
        val head = if (baseWeek == null) "还没取到教学周" else "第 ${baseWeek + offset} 周"
        val hint = when {
            baseWeek == null -> ""
            offset == 0 -> "· 本周"
            offset == 1 -> "· 本周后 1 周"
            offset == -1 -> "· 本周前 1 周"
            offset > 0 -> "· 本周后 $offset 周"
            else -> "· 本周前 ${-offset} 周"
        }
        val t = term.trim()
        val body = if (hint.isEmpty()) head else "$head $hint"
        return if (t.isEmpty()) body else "$body · $t"
    }
}
