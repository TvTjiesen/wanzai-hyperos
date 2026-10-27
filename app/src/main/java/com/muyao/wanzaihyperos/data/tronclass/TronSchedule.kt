package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/**
 * 課程的**上課時間**（課表的一格）。
 *
 * ## 契約來源
 *
 * 學在城院**沒有**「給學生的一週課表」接口：
 *
 * - `/api/courses/{id}/timetables` 實測 **HTTP 500**（裸調、帶 `conditions={}`、
 *   帶分頁三種都試過，全是伺服器端錯誤頁）—— 那是老師端排課用的，學生調不動；
 * - 手機版 SPA 自己也只是把 `course_attributes.teaching_class_name`
 *   這個**字串原樣顯示**（在 bundle 裡搜到它只出現在欄位清單中，沒有任何解析邏輯）。
 *
 * 所以課表要**自己解析那個字串**。好在它的語法很整齊 ——
 * 撈出我 30 門課的全部 25 種寫法（存 `docs/B13-TRONCLASS-SCHEDULE.txt`）
 * 之後確認只有一種句式：
 *
 * ```
 * 周{D}第{S}-{E}节第{W1},{W2}周
 *  └ 星期幾  └ 第幾節到第幾節   └ 第幾週到第幾週
 * ```
 *
 * 多段用 `;` 分隔。實例：
 *
 * ```
 * 周2第3-4节第1,3周;周2第3-4节第5,17周
 * → 週二第 3-4 節，第 1~3 週；以及第 5~17 週
 * ```
 *
 * ### ★ 那個逗號是**範圍**，不是清單
 *
 * 一開始很容易讀成「第 1 週和第 3 週」，但這樣解釋不通三類實例：
 * `第1,1周`（清單會是「第1週和第1週」）、`第18,18周`、
 * `第8,15周`（若真是列舉，中間 9~14 週就沒課了，不合理）。
 * 當成範圍則全部自洽，且 `第{單一數字},{同一數字}周` 恰好表示「只上一週」。
 * 所以 [weekStart]..[weekEnd] 是**閉區間**。
 *
 * ### 認不出來的段落**不丟**
 *
 * [TronScheduleParser.parse] 回傳 [TronScheduleParseResult]，
 * 裡面除了解析出的 [slots] 還有 [unparsed] 原文。
 * 界面會在最後把沒認出的段落**原樣列出來** —— 寧可顯示一句看不懂的字串，
 * 也不要讓某一門課的上課時間**靜默消失**（學生會以為那門課不用上）。
 *
 * ## 為什麼不自己算「第 3 節是幾點」
 *
 * **兩個來源都沒有「節次→幾點」這張表**，是真機查過的，不是推測：
 *
 * - 學在城院：`/api/org/{id}/teaching-weeks` 只有週次與日期，沒有節次時間；
 * - 教務（正方）：課表裡每一門課的欄位是學校自己標好的
 *   `节/周`、`上课地点`、`教师`、`教学班名称`、`教学班组成`、`考核方式`、
 *   `选课备注`、`课程学时组成`、`周学时`、`总学时`、`学分`
 *   （2026-10-19 真機原文，見 `docs/B22-JW-CELL-HTML.txt`）—— **裡面就是沒有「幾點」**。
 *   左側那一列只有「上午 / 下午 / 晚上」這種時段分組（有，所以存進 [slotLabel]）。
 *
 * **編一個時間就是造數據**，而學生按編的時間去上課是要遲到的。
 * 所以他沒填節次時間之前，界面只顯示「第 3-4 節 · 上午」，
 * 要精確到點必須他自己填一次（設定 → 節次時間，課表頁也有一個入口）。
 */
/**
 * 課表資料的**來源**。
 *
 * ★ 為什麼要標這個（业主 2026-10-19：「課程不準確，你自己去教務系統重新準確的爬」）：
 * 學在城院的課表是**老師在 TronClass 裡填的那一份**，教務（正方）的課表是**排課系統那一份**。
 * 兩邊會不一致（調課、代課、換教室都只改教務 —— 真機選單裡就有
 * 「有一条调课信息，请查看课表」這種通知，見 `docs/B18-JW-MENU.txt`）。
 *
 * 所以界面上**必須寫清楚這一屏是哪一份**，不能讓他在兩個來源之間
 * 看到不一樣的課而以為其中一個壞了。
 */
enum class ScheduleSource(val label: String) {
    /** 學在城院（TronClass）—— 解析 `teaching_class_name` 字串得來。 */
    TRONCLASS("学在城院"),

    /** 教務系統（正方 ZFSoft）—— 排課系統那一份，**學校的权威来源**。 */
    JIAOWU("教务系统"),
}

data class TronScheduleSlot(
    val courseId: Int,
    val courseName: String,
    /** 星期幾：1=週一 … 7=週日（與來源的 `周D` 同義）。 */
    val dayOfWeek: Int,
    /** 起始節次。 */
    val periodStart: Int,
    /** 結束節次（與 [periodStart] 相同表示單節）。 */
    val periodEnd: Int,
    /** 起始週次（含）。 */
    val weekStart: Int,
    /** 結束週次（含）。 */
    val weekEnd: Int,
    /** 原始段落（顯示用，也方便排查解析問題）。 */
    val raw: String,
    /** 任課教師。空 = 這個來源沒給（**不是「沒有老師」**）。 */
    val teacher: String = "",
    /** 上課地點。空 = 沒給。 */
    val room: String = "",
    /** 這一格是哪裡來的（見 [ScheduleSource]）。 */
    val source: ScheduleSource = ScheduleSource.TRONCLASS,
    /**
     * 學校自己標的時段：「上午 / 下午 / 晚上」。空 = 這個來源沒給。
     *
     * ★ 為什麼要存（业主 2026-10-19：「你课表和时间对不上」）：
     *   教务那张表**给不了几点**（上面那条纪律），但它给了「上午/下午/晚上」。
     *   在他还没填节次时间的时候，这是唯一能把课对上时间的信息 ——
     *   一块写着「第6-9节 · 下午」的课，和写着「第6-9节」的课，
     *   后者要他自己去推几点，这就是「对不上」的来源。
     */
    val slotLabel: String = "",
    /**
     * 學校標的選課狀態，只存**需要提醒的那一种**：「待筛选」（红字斜体）。
     *
     * 正方课表表头自己写着「红色斜体为待筛选，蓝色为已选上」——
     * 这是**学校给的语义**，不是我们排错了。蓝色（已选上）是常态，不占字。
     */
    val statusLabel: String = "",
) {
    /** 「週二」這種顯示名。 */
    val dayLabel: String
        get() = "周" + DAY_LABELS.getOrElse(dayOfWeek - 1) { dayOfWeek.toString() }

    /** 「第 3-4 節」。 */
    val periodLabel: String
        get() = if (periodStart == periodEnd) "第${periodStart}节" else "第${periodStart}-${periodEnd}节"

    /** 「第 5-17 週」。 */
    val weekLabel: String
        get() = if (weekStart == weekEnd) "第${weekStart}周" else "第${weekStart}-${weekEnd}周"

    /** 這一格在第 [week] 週有沒有課（閉區間）。 */
    fun activeIn(week: Int): Boolean = week in weekStart..weekEnd

    private companion object {
        val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
    }
}

/** 解析結果：[slots] 是認出來的、[unparsed] 是**認不出但保留**的原文段落。 */
data class TronScheduleParseResult(
    val slots: List<TronScheduleSlot>,
    val unparsed: List<String>,
)

object TronScheduleParser {

    /**
     * 段落句式：`周{D}第{S}-{E}节第{W1},{W2}周`。
     *
     * 寬鬆處理兩處：節次允許單節（`第3节`，雖然 25 種實例裡沒出現）、
     * 週次之間允許用 `-` 或 `~`（同一份資料萬一改寫法也不會整段失效）。
     */
    private val SEGMENT = Regex(
        """^周([1-7])第(\d+)(?:\s*-\s*(\d+))?节第(\d+)\s*[,\-~]\s*(\d+)周$""",
    )

    /**
     * 解析一門課的上課時間字串。
     *
     * @param text `course_attributes.teaching_class_name` 原文（可能為空）
     */
    fun parse(text: String, courseId: Int, courseName: String): TronScheduleParseResult {
        if (text.isBlank()) return TronScheduleParseResult(emptyList(), emptyList())
        val slots = mutableListOf<TronScheduleSlot>()
        val unparsed = mutableListOf<String>()
        // 分隔符實測是 `;`（半角）。全角 `；` 也接，避免格式微調就整門課消失。
        text.split(';', '；').map { it.trim() }.filter { it.isNotEmpty() }.forEach { segment ->
            val m = SEGMENT.matchEntire(segment)
            if (m == null) {
                unparsed.add(segment)
                return@forEach
            }
            val day = m.groupValues[1].toIntOrNull()
            val start = m.groupValues[2].toIntOrNull()
            val end = (m.groupValues[3].takeIf { it.isNotEmpty() } ?: m.groupValues[2]).toIntOrNull()
            val w1 = m.groupValues[4].toIntOrNull()
            val w2 = m.groupValues[5].toIntOrNull()
            if (day == null || start == null || end == null || w1 == null || w2 == null) {
                unparsed.add(segment)
                return@forEach
            }
            // 來源若把範圍寫反（end < start），照實際大小排好再顯示 ——
            // 顯示「第 9-3 節」是我們自己造的錯。
            slots.add(
                TronScheduleSlot(
                    courseId = courseId,
                    courseName = courseName,
                    dayOfWeek = day,
                    periodStart = minOf(start, end),
                    periodEnd = maxOf(start, end),
                    weekStart = minOf(w1, w2),
                    weekEnd = maxOf(w1, w2),
                    raw = segment,
                ),
            )
        }
        return TronScheduleParseResult(
            // 排序：週一→週日，同一天按節次。穩定的順序讓界面不必再排。
            slots = slots.sortedWith(compareBy({ it.dayOfWeek }, { it.periodStart }, { it.weekStart })),
            unparsed = unparsed,
        )
    }
}

// ---------------------------------------------------------------------------
// 教學週
// ---------------------------------------------------------------------------

/**
 * 一個教學週（`/api/org/{orgId}/teaching-weeks`）。
 *
 * ```
 * GET /api/org/1/teaching-weeks
 * → { items: [ { id, name:"第18周", sequence:18,
 *                start_date:"2027-01-11", end_date:"2027-01-17", comment } ] }
 * ```
 *
 * ### ★ 為什麼要這支
 *
 * 沒有它就不知道「現在是第幾週」，也就無法把課表收斂成「本週要上什麼」——
 * 而「本週」正是課表唯一的用途。實測 `sequence` 與日期對得上
 *（第 1 週 = 2026-09-14，與課程的 `start_date` 一致）。
 *
 * 日期是**純日期**（沒有時區），所以用 [LocalDate] 比較，不經過 `Instant`
 * —— 那會在多數時區把 00:00 的日子算偏一天。
 */
data class TronTeachingWeek(
    val sequence: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)
}

object TronTeachingWeekParser {

    fun parse(root: JsonObject?): List<TronTeachingWeek> {
        val items = root?.get("items").asArrayOrNull() ?: return emptyList()
        return items.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val seq = obj["sequence"].asIntOrNull() ?: return@mapNotNull null
            val start = obj["start_date"].asStringOrNull()?.let(::parseDate) ?: return@mapNotNull null
            val end = obj["end_date"].asStringOrNull()?.let(::parseDate) ?: return@mapNotNull null
            TronTeachingWeek(seq, start, end)
        }.sortedBy { it.sequence }
    }

    /** `yyyy-MM-dd` → [LocalDate]。解析不出來回 null（呼叫方據此丟掉那一條）。 */
    internal fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.trim().take(10)) }.getOrNull()
}

/**
 * [date] 落在第幾教學週。不在任何教學週內（寒暑假、開學前）回 null。
 *
 * 回 null **不是錯誤** —— 是「現在沒課」這個事實，界面照這個講。
 */
fun currentTeachingWeek(weeks: List<TronTeachingWeek>, date: LocalDate): Int? =
    weeks.firstOrNull { it.contains(date) }?.sequence
