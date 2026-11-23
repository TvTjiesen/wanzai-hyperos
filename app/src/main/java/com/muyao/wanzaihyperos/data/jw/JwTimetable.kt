package com.muyao.wanzaihyperos.data.jw

import com.muyao.wanzaihyperos.data.tronclass.ScheduleSource
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 從**教務系統（正方 ZFSoft）**爬下來的課表。
 *
 * ## 為什麼要這一份（业主 2026-10-19：「課程不準確，你自己去教務系統重新準確的爬」）
 *
 * 現有的課表是把學在城院那串 `周2第3-4节第1,3周` 解析出來的，
 * 那個字串是**老師在 TronClass 裡自己填的**；排課的权威在正方。
 * 調課、代課、換教室通常只改正方那一份 —— 他學校教務首頁選單裡就有
 * 「有一条调课信息，请查看课表」這種通知（`docs/B18-JW-MENU.txt`），
 * 說明「兩邊不一致」是學校自己的常態，不是我們解析錯了。
 *
 * ## ★ 兩條取數路徑（逐條回退，不假裝只有一條）
 *
 * ```
 * ① JSON：POST /jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508
 *          body = xnm/xqm（從課表頁 #xnm/#xqm 讀，不寫死）
 *          → { xsxx:{XNMC,XQMMC,…}, kbList:[ {xqj, jc, zcd, kcmc, kch, xm, cdmc, …} ] }
 *          （這支是頁面 xskbcx.js 自己用的「个人课表」介面；
 *            這站**沒有** doType=query，見 docs/B18-WALKTHROUGH.md §11）
 * ② DOM ：①拿不到 JSON 時，直接讀**已經渲染好的課表表格**
 *          （行=節次、列=星期，每格 innerText 逐行拆）。
 * ```
 *
 * ⚠️ ① 的星期與節次**分在不同欄位**（`xqj`/`xqjmc` 與 `jc`），
 * [parseItem] 那套「同一字串裡星期+節次」認不出來 ⇒ 走 [parseGrkb]。
 *
 * 兩條都可能失敗，而且失敗長得很像（都是「0 門課」）。
 * 所以結果一定帶著**用了哪條路**與**哪些格沒認出來（原樣列出）**。
 *
 * ⚠️ 紀律：**認不出來的不能靜默丟**。課表少一格 = 他不去上課，
 * 那個後果比界面難看大得多（學在城院那條路就是靠 `unparsed` 才摸清學校的寫法邊界）。
 */
@kotlinx.serialization.Serializable
data class JwTimetableResult(
    val slots: List<TronScheduleSlot>,
    /** 沒認出來的原文（每條自帶是哪一格／哪個欄位）。 */
    val unparsed: List<String>,
    /** 實際用了哪條路（顯示在界面上；出問題時一眼知道該查哪條）。 */
    val strategy: String,
    /** 原始內容長度（排查用，**不含內容**，避免個資進日誌）。 */
    val rawLength: Int,
    /**
     * 學校自己寫的學期名，例如「2026-2027学年第1学期」。空 = 沒取到。
     *
     * ★ 為什麼要它（业主 2026-10-20：「上面的本周我哪知道是几周，改成第几周，第几学期」）：
     *   他要的是**絕對**的座標（第幾週、哪一學期），不是「本週」這種要他自己換算的詞。
     *   學期名不我們寫死（那到明年就是謊），從教务课表页标题里抓 ——
     *   真機逐字：「2026-2027学年第1学期某某某的课表学号：…」，
     *   正則只取「學年+學期」那一段，**姓名與學號不進介面**。
     */
    val termLabel: String = "",
) {
    /** 什麼都沒拿到（連一格都沒認出來）—— 界面據此**不顯示成「本週沒課」**。 */
    val nothingAtAll: Boolean get() = slots.isEmpty() && unparsed.isEmpty()
}

/** 正方課表的解析（**純函式**，可單測；抓取在 [JwTimetableRepository]）。 */
object JwTimetableParser {

    private val DAY = Regex("""(?:周|星期|礼拜)\s*([1-7一二三四五六日天])""")
    private val DAY_CHARS = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7,
    )

    /**
     * 「二 第3-4节」→ (2, 3, 4)。解析不出星期或節次時回 null。
     *
     * ★ 節次只在**星期之後**的那段裡找：正方一格裡常寫
     * 「1-16周 第1-2节」，兩個範圍長得一模一樣，
     * 從頭找會把「1-16周」讀成第 1-16 節（那是會讓人上一整天課的錯）。
     */
    fun dayPeriod(text: String?): Triple<Int, Int, Int>? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        val dm = DAY.find(t) ?: return null
        val day = dm.groupValues[1].toIntOrNull() ?: DAY_CHARS[dm.groupValues[1].firstOrNull() ?: ' ']
        if (day == null || day !in 1..7) return null
        val tail = t.substring(dm.range.last + 1)
        val pm = periodMatch(tail) ?: return null
        return Triple(day, pm.first, pm.second)
    }

    /**
     * 「第3-4节」/「3,4节」/「5节」→ (3, 4)。回傳 (起, 迄)。
     *
     * ★ 另外吃**光桿數字**（「1」「1-2」「3,4」）：真機實測正方課表左側那一列
     *   常常只寫「1-2」不帶「节」字 —— 只認「N节」會一行都解析不出來，
     *   而在界面上的表現就是「教务那份是空的」。
     *   限制在 6 個字元以內才這樣兜，避免把「2026-2027学年第1学期…」那種標題行
     *   也讀成節次（那個錯我上一版剛踩過）。
     */
    fun periodOnly(text: String?): Pair<Int, Int>? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        periodMatch(t)?.let { return it }
        if (t.length > 6) return null
        val bare = Regex("^第?\\s*(\\d{1,2})\\s*(?:[-~－,]\\s*(\\d{1,2}))?\\s*节?$").find(t) ?: return null
        val a = bare.groupValues[1].toIntOrNull() ?: return null
        val b = bare.groupValues.getOrNull(2)?.toIntOrNull() ?: a
        if (a !in 1..14 || b !in 1..14) return null
        return minOf(a, b) to maxOf(a, b)
    }

    private fun periodMatch(t: String): Pair<Int, Int>? {
        val m = Regex("""第\s*(\d{1,2})\s*[-~－,]\s*(\d{1,2})\s*节""").find(t)
            ?: Regex("""第\s*(\d{1,2})\s*节""").find(t)
            ?: Regex("""(\d{1,2})\s*[-~－]\s*(\d{1,2})\s*节""").find(t)
            ?: Regex("""(\d{1,2})\s*节""").find(t)
            ?: return null
        val g = m.groupValues
        val start = g[1].toIntOrNull() ?: return null
        val end = g.getOrNull(2)?.toIntOrNull() ?: start
        return minOf(start, end) to maxOf(start, end)
    }

    /** 「1-16周」/「1,16周」→ 閉區間。認不出回 null（呼叫方決定怎麼兜底）。 */
    fun weekRange(text: String?): IntRange? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        Regex("""(\d{1,2})\s*[-~－]\s*(\d{1,2})\s*周""").find(t)?.let {
            val a = it.groupValues[1].toIntOrNull() ?: return@let
            val b = it.groupValues[2].toIntOrNull() ?: return@let
            return minOf(a, b)..maxOf(a, b)
        }
        Regex("""(\d{1,2})\s*周""").find(t)?.let {
            val a = it.groupValues[1].toIntOrNull() ?: return@let
            return a..a
        }
        return null
    }

    /**
     * 解析一條 `kbList[]`。
     *
     * @return null = **沒認出星期或節次**（呼叫方放進 unparsed，不許丟）
     */
    fun parseItem(obj: JsonObject): TronScheduleSlot? {
        val name = obj["kcmc"].asStringOrNull() ?: obj["courseName"].asStringOrNull() ?: ""
        val teacher = obj["jsxm"].asStringOrNull() ?: obj["jszgh"].asStringOrNull() ?: ""
        val room = obj["cdmc"].asStringOrNull()
            ?: obj["kcdz"].asStringOrNull()
            ?: obj["jxdd"].asStringOrNull()
            ?: ""
        val courseCode = obj["kch"].asStringOrNull() ?: obj["kchid"].asStringOrNull() ?: ""

        // 1) 先掃正方常見的那幾個欄位名；2) 都不行就把這個條目所有字串值掃一遍。
        //    為什麼要掃多個：這個站的欄位名在不同部署裡不一致（連外層鍵都單複數混用）。
        val dayAndPeriod = listOf("xbjmsz", "jcname", "jxcd", "xqjmc", "skxq", "week")
            .firstNotNullOfOrNull { key -> dayPeriod(obj[key].asStringOrNull()) }
            ?: obj.values.filterIsInstance<JsonPrimitive>()
                .mapNotNull { dayPeriod(it.content) }
                .firstOrNull()
            ?: return null

        val weeks = weekRange(obj["zcd"].asStringOrNull() ?: obj["zc"].asStringOrNull())
        val raw = obj["zcd"].asStringOrNull().orEmpty()
        return TronScheduleSlot(
            // id 用課程號的穩定哈希（同一門課恆同色）。**不是學校的 id**，
            // 只給配色與 key 用，所以取一個不會撞的窄域正數即可。
            courseId = stableId(courseCode.ifBlank { name }),
            courseName = name,
            dayOfWeek = dayAndPeriod.first,
            periodStart = dayAndPeriod.second,
            periodEnd = dayAndPeriod.third,
            weekStart = weeks?.first ?: 1,
            weekEnd = weeks?.last ?: DEFAULT_LAST_WEEK,
            // 週次沒給時在原文上留一句：詳情裡看得見「這是我們兜的底」，
            // 而不是以為學校說了整學期都有課。
            raw = if (weeks == null) (if (raw.isBlank()) "周次未给出" else "$raw（周次未给出，按整学期显示）")
                  else raw,
            teacher = teacher,
            room = room,
            source = ScheduleSource.JIAOWU,
        )
    }

    /** 解析 JSON 回應（路徑 ①）。外層鍵 `kbList` / `data.kbList` / 直接陣列都接。 */
    fun parseJson(root: JsonElement?): JwTimetableResult {
        val array: JsonArray? = when (root) {
            is JsonArray -> root
            is JsonObject -> root["kbList"].asArrayOrNull()
                ?: root["data"].asObjectOrNull()?.get("kbList").asArrayOrNull()
                ?: root["data"].asArrayOrNull()
            else -> null
        }
        if (array == null) {
            return JwTimetableResult(
                emptyList(),
                listOf("教务系统回的不是课表数据（没有 kbList 这一项）"),
                "教务JSON",
                root?.toString()?.length ?: 0,
            )
        }
        val slots = mutableListOf<TronScheduleSlot>()
        val unparsed = mutableListOf<String>()
        array.mapNotNull { it.asObjectOrNull() }.forEachIndexed { i, obj ->
            val slot = parseItem(obj)
            if (slot == null) {
                // 原樣留證：把這個條目所有非空字串欄位拼一行。
                // 他截圖給我時，我看得見學校到底給的是什麼形態 —— 否則只能猜。
                val dump = obj.entries
                    .mapNotNull { (k, v) -> v.asStringOrNull()?.takeIf { it.isNotBlank() }?.let { "$k=$it" } }
                    .joinToString(" ")
                    .take(160)
                unparsed.add("第${i + 1}条没认出星期或节次：$dump")
            } else {
                slots.add(slot)
            }
        }
        return JwTimetableResult(slots, unparsed, "教务JSON", root.toString().length)
    }

    /**
     * 解析這個部署**真正**用的「個人課表」介面（[JwTimetableRepository] 路徑 ①）。
     *
     * 形態（curl 兩步登入後 POST `xskbcx_cxXsgrkb.html?gnmkdm=N253508` 逐字實測）：
     * ```
     * { xsxx:{XNMC:"2026-2027", XQMMC:"1", XNM, XQM, …},
     *   kbList:[ {xqj:"1", xqjmc:"星期一", jc:"1-2节", jcs:"1-2", jcor:"1-2",
     *             zcd:"1-3周,5-17周", kcmc:"…", kch:"A01014", xm:"陈明", cdmc:"文1-103"}, … ] }
     * ```
     *
     * ★ 為什麼不能复用 [parseItem]：那套要求「同一個字串裡既有星期又有節次」，
     *   這裡**星期（xqj/xqjmc）與節次（jc/jcs/jcor）分在不同欄位**，
     *   逐條都認不出來 ⇒ 22 條全進 unparsed ⇒ 課表永遠是空的。
     *   教師欄位是 `xm`（不是 `jsxm`）；週次是**多段清單**（`zcd`），
     *   一段一個槽位（與 DOM 路徑同一條既定規則）。
     */
    fun parseGrkb(root: JsonElement?): JwTimetableResult {
        val obj = root.asObjectOrNull()
        val array = obj?.get("kbList").asArrayOrNull()
        if (array == null) {
            return JwTimetableResult(
                emptyList(),
                listOf("教务个人课表接口回的不是课表数据（没有 kbList 这一项）"),
                "教务个人课表",
                root?.toString()?.length ?: 0,
            )
        }
        val termLabel = grkbTermLabel(obj)
        val slots = LinkedHashMap<String, TronScheduleSlot>()
        val unparsed = mutableListOf<String>()
        array.forEachIndexed { i, item ->
            val o = item.asObjectOrNull() ?: return@forEachIndexed
            val day = dayFromGrkb(o)
            val period = listOf("jc", "jcs", "jcor")
                .firstNotNullOfOrNull { key -> periodOnly(o[key].asStringOrNull()) }
            if (day == null || period == null) {
                // 認不出來**不許靜默丟**：原樣留證（一格丟一門課 = 他不去上）。
                val dump = o.entries
                    .mapNotNull { (k, v) -> v.asStringOrNull()?.takeIf { it.isNotBlank() }?.let { "$k=$it" } }
                    .joinToString(" ")
                    .take(160)
                unparsed.add("第${i + 1}条没认出星期或节次：$dump")
                return@forEachIndexed
            }
            val name = (o["kcmc"].asStringOrNull() ?: o["courseName"].asStringOrNull() ?: "")
                .ifBlank { "（未命名）" }
            val courseCode = o["kch"].asStringOrNull().orEmpty()
            // `jsxm` 是別的部署的欄位名，這個站用 `xm`；兩個都留著當相容。
            val teacher = o["xm"].asStringOrNull() ?: o["jsxm"].asStringOrNull() ?: ""
            val room = o["cdmc"].asStringOrNull().orEmpty()
            val zcd = o["zcd"].asStringOrNull().orEmpty()
            // 多段週次逐段還原；學校真沒給就按整學期兜底並在 raw 留痕。
            val weeks = weekList(zcd).ifEmpty { listOf(1..DEFAULT_LAST_WEEK) }
            weeks.forEach { w ->
                val slot = TronScheduleSlot(
                    courseId = stableId(courseCode.ifBlank { name }),
                    courseName = name.take(40),
                    dayOfWeek = day,
                    periodStart = period.first,
                    periodEnd = period.second,
                    weekStart = w.first,
                    weekEnd = w.last,
                    raw = if (zcd.isBlank()) "周次未给出" else zcd,
                    teacher = teacher,
                    room = room,
                    source = ScheduleSource.JIAOWU,
                )
                // dedupeKey 不含教師 ⇒「同課同節次同週、不同老師」會被壓掉，
                // 這裡補上教師，避免把換老師的那一位靜默吞掉（與「不靜默丟」一致）。
                slots.putIfAbsent("${slot.dedupeKey()}|$teacher", slot)
            }
        }
        return JwTimetableResult(
            slots.values.sortedWith(compareBy({ it.dayOfWeek }, { it.periodStart }, { it.courseName })),
            unparsed, "教务个人课表", root.toString().length, termLabel,
        )
    }

    /** `xqj`(1..7) 優先，`xqjmc`(星期一…日) 兜底。 */
    private fun dayFromGrkb(o: JsonObject): Int? {
        o["xqj"].asStringOrNull()?.trim()?.toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
        val m = DAY.find(o["xqjmc"].asStringOrNull().orEmpty()) ?: return null
        val g = m.groupValues[1]
        val d = g.toIntOrNull() ?: DAY_CHARS[g.firstOrNull() ?: ' '] ?: return null
        return d.takeIf { it in 1..7 }
    }

    /** 學期名從 `xsxx` 拼（不拿日期猜）：`XNMC`="2026-2027"、`XQMMC`="1"。 */
    private fun grkbTermLabel(obj: JsonObject?): String {
        val xsxx = obj?.get("xsxx").asObjectOrNull() ?: return ""
        val xn = xsxx["XNMC"].asStringOrNull().orEmpty()
        val xq = xsxx["XQMMC"].asStringOrNull().orEmpty()
        return if (xn.isNotBlank() && xq.isNotBlank()) "${xn}学年第${xq}学期" else ""
    }

    /**
     * 解析 DOM 抽出來的表格（路徑 ②）。
     *
     * 形態由 [JwTimetableDom.SCRIPT] 產生：
     * `{"rows":[{"period":"1-2节","cells":["高等数学\n张三\n理1-216\n1-16周", …]}]}`
     *
     * ## ★ 真機實測：他們學校的格子是**一整串**，而且跨節靠 rowspan 重複
     *
     * 實際拿到的一格長這樣（逐字，只是把個人信息換掉了）：
     * ```
     * 大学物理(Ⅱ)(1-2节)1周,4-6周,8-15周,17周××学院文1-103陈明(2026-2027-1)-A01014-J05018-5…
     * ```
     * 三個後果：
     * 1. **節次寫在格子裡**（`(1-2节)`），行首那一列只是「第1节」這種單節標號，
     *    而且同一門課因為 rowspan 會在第 1、2 行**各出現一次** ——
     *    照行首建槽位會把一門課拆成兩塊。
     *    ⇒ **優先按格子裡的 `(N-M节)` 建槽位**，行首只在格子沒寫時當兜底；
     *    再用去重把 rowspan 的重複壓掉。
     * 2. **週次是一張清單不是一個範圍**（`1周,4-6周,8-15周,17周`）——
     *    一個槽位只有一個 `weekStart..weekEnd`，所以**一個區間出一個槽位**。
     *    只取第一段會讓他以為「第 1 週以外都沒課」。
     * 3. 老師與教室擠在同一串裡（`…学院文1-103陈明(2026-…`）。
     *    只抓**有把握的兩種形態**：教室 = 「漢字+數字-數字」（文1-103、理4-527）；
     *    教師 = 紧跟在教室後面、`(學年-學年` 之前的那 2~4 個漢字。
     *    抓不到就**留空**（界面上那一行不佔位），不猜。
     */
    fun parseDom(root: JsonElement?): JwTimetableResult {
        val obj = root.asObjectOrNull()
            ?: return JwTimetableResult(emptyList(), listOf("教务课表页面没读到内容"), "教务网页", 0)
        obj["error"].asStringOrNull()?.let {
            return JwTimetableResult(emptyList(), listOf("教务课表页面：$it"), "教务网页", 0)
        }
        val rows = obj["rows"].asArrayOrNull()?.mapNotNull { it.asObjectOrNull() }.orEmpty()
        if (rows.isEmpty()) {
            val heads = obj["heads"].asArrayOrNull()?.mapNotNull { it.asStringOrNull() }.orEmpty()
            val hint = listOfNotNull(
                obj["gridRows"].asStringOrNull()?.let { "页面表格 $it 行" },
                obj["headerRow"].asStringOrNull()?.let { "表头在第 $it 行" },
                obj["periodCol"].asStringOrNull()?.let { "节次列第 $it 列" },
                heads.takeIf { it.isNotEmpty() }?.let { "行首依次是“${it.joinToString(" / ")}”" },
            ).joinToString("，")
            return JwTimetableResult(
                emptyList(),
                listOf(
                    "教务课表页面里没挑出课表行" +
                        if (hint.isBlank()) "（可能学校改版了，或这一学期还没排课）" else "（$hint）",
                ),
                "教务网页", 0,
            )
        }
        val slots = LinkedHashMap<String, TronScheduleSlot>()
        val unparsed = mutableListOf<String>()
        var seen = 0
        rows.forEach { row ->
            val rowPeriod = periodOnly(row["period"].asStringOrNull())
            val rowSlot = row["slot"].asStringOrNull()?.trim().orEmpty()
            val cells = row["cells"].asArrayOrNull() ?: return@forEach
            cells.forEachIndexed { idx, cell ->
                val day = idx + 1
                if (day !in 1..7) return@forEachIndexed
                // ── 新形態（2026-10-19 真機）：一個格子 = **一门课一个对象**的数组 ──
                val blocks = cell.asArrayOrNull()
                if (blocks != null) {
                    blocks.mapNotNull { it.asObjectOrNull() }.forEach { b ->
                        seen++
                        val fromBlock = slotsFromBlock(b, day, rowPeriod, rowSlot)
                        if (fromBlock != null) {
                            fromBlock.forEach { s -> slots.putIfAbsent(s.dedupeKey(), s) }
                        } else {
                            // 认不出来**不许静默丢**：原样列在页面上（一格丢一门课 = 他不去上）
                            unparsed.add(
                                "周${DAY_NAMES.getOrElse(day - 1) { day }}${row["period"].asStringOrNull().orEmpty()}节" +
                                    "有一块没认出节次：${(b["raw"].asStringOrNull() ?: b.toString()).take(120)}",
                            )
                        }
                    }
                    return@forEachIndexed
                }
                val text = cell.asStringOrNull()?.trim().orEmpty()
                if (text.isEmpty()) return@forEachIndexed   // 空格子 = 那一格沒課，不是沒認出來
                seen++
                val fromCell = slotFromCell(text, day)
                if (fromCell != null) {
                    fromCell.forEach { s -> slots.putIfAbsent(s.dedupeKey(), s) }
                    return@forEachIndexed
                }
                // 格子裡沒寫 `(N-M节)` ⇒ 退回「行首那一列就是節次」的舊形態
                if (rowPeriod == null) {
                    unparsed.add("行“${row["period"].asStringOrNull().orEmpty()}”没认出节次：${text.take(120)}")
                    return@forEachIndexed
                }
                val weeks = weekList(text)
                // 一格裡可能有**兩門課**（調課會並列在同一格，中間空行隔開）——
                // 不拆就會只看到第一門，第二門靜默消失（那正是要避免的那種錯）。
                text.split(Regex("\n{2,}")).forEach { piece ->
                val lines = piece.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) return@forEach
                val slot = TronScheduleSlot(
                    courseId = stableId(lines.firstOrNull().orEmpty().ifBlank { piece.take(24) }),
                    courseName = lines.firstOrNull()?.takeIf { it.isNotBlank() }?.take(40)
                        ?: piece.take(40),
                    dayOfWeek = day,
                    periodStart = rowPeriod.first,
                    periodEnd = rowPeriod.second,
                    weekStart = weeks.firstOrNull()?.first ?: 1,
                    weekEnd = weeks.firstOrNull()?.last ?: DEFAULT_LAST_WEEK,
                    raw = piece.replace('\n', ' ').take(120),
                    // 老師有兩種真實形態：正方那種一整串擠在一起（`(學年-學年` 前面那幾個字），
                    // 以及分行寫的「課名 / 老師 / 教室」。兩種都試，都抓不到就留空。
                    teacher = teacherOf(piece).ifBlank { lines.getOrNull(1).orEmpty() },
                    room = roomOf(piece).ifBlank { lines.getOrNull(2).orEmpty() },
                    source = ScheduleSource.JIAOWU,
                )
                slots.putIfAbsent(slot.dedupeKey(), slot)
                }
            }
        }
        if (slots.isEmpty() && unparsed.isEmpty() && seen > 0) {
            unparsed.add("教务课表读到 $seen 个格子，但每一格都没认出节次形态")
        }
        return JwTimetableResult(
            slots.values.sortedWith(compareBy({ it.dayOfWeek }, { it.periodStart }, { it.courseName })),
            unparsed, "教务网页", root.toString().length,
            termLabel = termOf(obj["title"].asStringOrNull()),
        )
    }

    /**
     * 從教务课表页的標題裡只取「學年+學期」。
     *
     * 真機逐字：`2026-2027学年第1学期某某某的课表学号：12345678注：红色斜体为待筛选…`
     * —— 介面只要前面那一段，**姓名與學號絕不帶到界面上**（這一頁會截圖、也會被人看到）。
     *
     * 認不出來就回空字串（界面就不顯示學期），**不拿今天的日期猜一個**。
     */
    fun termOf(title: String?): String {
        val t = title?.trim().orEmpty()
        if (t.isEmpty()) return ""
        val m = Regex("""(\d{4})\s*[-~－]\s*(\d{4})\s*学年\s*第?\s*(\d+)\s*学期""").find(t)
            ?: Regex("""第\s*(\d+)\s*学期""").find(t)
            ?: return ""
        val g = m.groupValues
        return if (g.size >= 4 && g[1].isNotBlank()) "${g[1]}-${g[2]}学年第${g[3]}学期"
        else "第${m.groupValues[1]}学期"
    }

    /** 一格裡寫了 `(N-M节)` 時，按它自己的節次與週次清單出槽位（可能多條）。 */
    private fun slotFromCell(text: String, day: Int): List<TronScheduleSlot>? {
        val marker = CELL_PERIOD.find(text) ?: return null
        val name = text.substringBefore(marker.value).trim().ifBlank { "（未命名）" }
        val rest = text.substringAfter(marker.value)
        val start = marker.groupValues[1].toIntOrNull() ?: return null
        val end = marker.groupValues[2].toIntOrNull() ?: start
        val weeks = weekList(rest).ifEmpty { listOf(1..DEFAULT_LAST_WEEK) }
        return weeks.map { w ->
            TronScheduleSlot(
                courseId = stableId(name),
                courseName = name.take(40),
                dayOfWeek = day,
                periodStart = minOf(start, end),
                periodEnd = maxOf(start, end),
                weekStart = w.first,
                weekEnd = w.last,
                raw = text.replace('\n', ' ').take(120),
                teacher = teacherOf(rest),
                room = roomOf(rest),
                source = ScheduleSource.JIAOWU,
            )
        }
    }

    /** 一格可能列**多個週次區間**（`1周,4-6周,8-15周,17周`），逐個還原。 */
    fun weekList(text: String): List<IntRange> =
        CELL_WEEKS.findAll(text).map { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@map null
            val b = m.groupValues.getOrNull(2)?.toIntOrNull() ?: a
            minOf(a, b)..maxOf(a, b)
        }.mapNotNull { it }.toList()

    /**
     * 學校切成的一块（一个 `div.timetable_con`）→ 槽位。
     *
     * 欄位名是**学校自己标的**（`docs/B22-JW-CELL-HTML.txt` 逐字）：
     * `节/周`、`上课地点`、`教师`、`教学班名称`、`教学班组成`、`考核方式`、
     * `选课备注`、`课程学时组成`、`周学时`、`总学时`、`学分`。
     *
     * @param rowPeriod 行首那一列的節次（学校没在块里写节次时的兜底）
     * @param slotLabel 行首「上午/下午/晚上」—— 学校给的唯一时段信息
     * @return null = 这一塊**認不出節次也認不出週次**（呼叫方放 unparsed，不许丢）
     */
    private fun slotsFromBlock(
        obj: JsonObject,
        day: Int,
        rowPeriod: Pair<Int, Int>?,
        slotLabel: String,
    ): List<TronScheduleSlot>? {
        val fields = obj["fields"].asObjectOrNull()
        val rawText = obj["raw"].asStringOrNull().orEmpty().trim()
        val name = obj["name"].asStringOrNull()?.trim().orEmpty()

        // 学校改版、格子里没有 timetable_con 时，脚本交的是 `{raw: 整格文字}`：
        // 退回上一版那条「从文字里找 (N-M节)」的路 —— **结构变了不能就没课**。
        if (fields == null && name.isBlank()) {
            return rawText.takeIf { it.isNotBlank() }
                ?.let { slotFromCell(it, day) }
                ?.map { it.copy(slotLabel = slotLabel) }
        }

        val jp = fieldOf(fields, "节/周").ifBlank {
            // 栏位名换个部署可能写成「节次/周次」——按「既有节又有周」找，找不到就空
            fields?.entries?.firstOrNull { it.key.contains('节') && it.key.contains('周') }
                ?.value?.asStringOrNull().orEmpty().trim()
        }
        val marker = BLOCK_PERIOD.find(jp)
        val start = marker?.groupValues?.get(1)?.toIntOrNull() ?: rowPeriod?.first
        val end = marker?.groupValues?.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 } ?: start ?: return null
        if (start == null) return null

        val weeks = weekList(jp).ifEmpty {
            // `节/周` 里只有节（`(6-9节)`）时，周次写在同格的文字里；都没有 = 学校真没给
            weekList(rawText)
        }
        val room = roomFromLabeled(fieldOf(fields, "上课地点"))
        val teacher = fieldOf(fields, "教师")
        val code = fieldOf(fields, "教学班名称")
        val exam = fieldOf(fields, "考核方式")
        val credit = fieldOf(fields, "学分")
        // 学校表头：红色斜体=待筛选、蓝色=已选上。只把**需要提醒的**那种带出去。
        val color = obj["color"].asStringOrNull().orEmpty()
        val status = if (color.equals("red", true) || obj["italic"].asStringOrNull() == "italic") "待筛选" else ""
        val courseName = name.ifBlank { rawText.substringBefore('(').trim().ifBlank { "（未命名）" } }

        val detail = listOfNotNull(
            jp.takeIf { it.isNotBlank() },
            exam.takeIf { it.isNotBlank() }?.let { "考核：$it" },
            credit.takeIf { it.isNotBlank() }?.let { "学分：$it" },
        ).joinToString(" · ").ifBlank { rawText.take(120) }

        return (weeks.ifEmpty { listOf(1..DEFAULT_LAST_WEEK) }).map { w ->
            TronScheduleSlot(
                // 有教学班号就用它（同一门课不同班次会分开，配色也稳定）；没有退回课名。
                courseId = stableId(code.ifBlank { courseName }),
                courseName = courseName.take(40),
                dayOfWeek = day,
                periodStart = minOf(start, end),
                periodEnd = maxOf(start, end),
                weekStart = w.first,
                weekEnd = w.last,
                raw = detail.take(160),
                teacher = teacher,
                room = room,
                source = ScheduleSource.JIAOWU,
                slotLabel = slotLabel,
                statusLabel = status,
            )
        }
    }

    /** 按学校标的栏位名取值（名子上有空格、尾部对不齐都在这层吸收）。 */
    private fun fieldOf(fields: JsonObject?, key: String): String {
        if (fields == null) return ""
        fields[key].asStringOrNull()?.trim()?.let { return it }
        return fields.entries.firstOrNull { it.key.trim() == key }
            ?.value?.asStringOrNull()?.trim().orEmpty()
    }

    /**
     * 「上课地点」这一栏的实战形态（真机逐字，两种都有）：
     * `浙大城市学院  文1-103`（有空格）、`浙大城市学院文1-103`（挤成一串）、
     * `浙大城市学院线上教学`、`浙大城市学院西网球场`、`浙大城市学院校内场地`。
     *
     * 学校名整屏都一样 ⇒ 没有信息量，去掉；剩下那截就是学校自己写的地点。
     *
     * ★ 兩種都要處理（真機第一版只遇到有空格那種，第二次採集發現是**挤在一起**的）：
     *   `innerText` 會不會留那個空格，取決於 `<font>` 里的空白被誰吞了 ——
     *   只按空格切會得到「浙大城市学院文1-103」這種帶校名的地址（屏上每一行都長一半，
     *   而他要看的是「文1-103」）。
     *
     * 為什麼不裁到只剩「數字-數字」：`线上教学`、`西网球场`、`校内场地` 這種**没有编号**的
     * 真实地点会被整条丢掉（旧版就是这样，那些格子一个地点都不显示）。
     * 抓不到就原樣留 [t]，**不裁**。
     */
    private fun roomFromLabeled(v: String): String {
        val t = v.trim()
        if (t.isEmpty()) return ""
        val parts = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size >= 2 && parts.first().let { p ->
                p.length >= 3 && (p.endsWith("学院") || p.endsWith("大学") || p.endsWith("学校"))
            }
        ) return parts.drop(1).joinToString(" ")
        // 挤成一串的形态：前缀必须**至少 3 个字**才当校名裁 ——
        // 「学院楼201」这种以「学院」开头的真实地点不能被裁成「楼201」。
        val m = ROOM_PREFIX.find(t) ?: return t
        val rest = t.substring(m.range.last + 1)
        return if (PLACE_START.matches(rest)) rest else t
    }

    /**
     * 教室：正方寫成「(教學樓)編號-房號」（文1-103、理4-527）。
     *
     * ★ 只取**紧贴数字的那一個漢字**：贪婪匹配會把「××学院文1-103」整段吃進來，
     *   那是學院名不是教室。也不要 0 個漢字 —— 那樣會把週次清單裡的
     *   「4-6周」當成教室（真機上踩到過這類誤抓）。
     *   抓不到就留空（界面上那一行不佔位），**不拿别的字湊**。
     */
    private fun roomOf(text: String): String =
        Regex("""(\p{IsHan})(\d{1,2}-\d{2,4})""").find(text)?.let { it.groupValues[1] + it.groupValues[2] }.orEmpty()

    /**
     * 教師：紧跟在 `(學年-學年` 前面的那 2~3 個漢字。抓不到留空，**不猜**。
     *
     * ★ 為什麼是 2~3 而不是 2~4（真機踩到）：寫成 {2,4} 時，
     *   「…西网球场赵得龙(2026-…」會從更左的位置起匹配，抓成「场赵得龙」——
     *   把教室名的最後一個字併進老師名。長度收緊到 3 之後，
     *   正則只能從「赵得龙」這個起點才凑得出「後面紧跟 (學年」，就對了。
     *   四字姓名（複姓）會被截成三字：**寧可少一字，也不要多吃一個教室的字**。
     */
    private fun teacherOf(text: String): String =
        Regex("""([\p{IsHan}]{2,3})\(\d{4}-\d{4}""").find(text)?.groupValues?.get(1).orEmpty()

    /** rowspan 會讓同一門課在相鄰幾行各出現一次 —— 用它自己當鍵壓掉。 */
    private fun TronScheduleSlot.dedupeKey(): String =
        "$courseName|$dayOfWeek|$periodStart-$periodEnd|$weekStart-$weekEnd|$room"

    private val CELL_PERIOD = Regex("""\((\d{1,2})\s*[-~－]\s*(\d{1,2})\s*节\)""")
    /**
     * 學校 `节/周` 栏里的节次（真機：`(1-2节)`、`(6-9节)`；单节写成 `(8节)`）。
     * 与 [CELL_PERIOD] 分开：那个要求必须有横杠，这个不要求。
     */
    private val BLOCK_PERIOD = Regex("""\(\s*(\d{1,2})\s*(?:[-~－]\s*(\d{1,2}))?\s*节\s*\)""")
    private val CELL_WEEKS = Regex("""(\d{1,2})(?:-(\d{1,2}))?周""")
    private val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")
    /** 校名前缀：至少 3 个字再接「学院/大学/学校」（非贪婪，取最短的那一段）。 */
    private val ROOM_PREFIX = Regex("""^.{3,}?(?:学院|大学|学校)""")
    /** 裁掉校名后，剩下的必须以汉字或数字开头（`文1-103`、`线上教学`）才算一个地点。 */
    private val PLACE_START = Regex("""^[\p{IsHan}\d].+""")
    /** 字串 → 穩定的窄域正整數（同一門課恆同值；只給配色與 key 用）。 */
    private fun stableId(text: String): Int {
        if (text.isBlank()) return 0
        var h = 0
        for (ch in text) h = (31 * h + ch.code) and 0x7fffffff
        return h % 100000 + 1
    }

    /** 學校沒給週次時的兜底末週（18 是這學期實測的教學週上界，見 teaching-weeks）。 */
    private const val DEFAULT_LAST_WEEK = 18
}

/**
 * 讀課表表格用的 JS。
 *
 * ## ★ 為什麼在頁面裡讀 DOM，而不是抓 HTML 回來自己解析
 *
 * 正方的課表是渲染完才填好的，而且格子用 `rowspan` 跨行 ——
 * 在頁面裡讀 `innerText` 等於讓它自己的排版引擎替我們展開跨行。
 * 再說這個站的登入**本來就只能靠 WebView**（`docs/B18-WALKTHROUGH.md` §4），
 * 「在頁面裡執行 JS」是現成能力，不多引一個 HTML 解析依賴。
 *
 * 找表格用的是**結構性判據**（表頭要有「星期一…星期五」+「节」），
 * 不寫死 id：這一類系統的 id 換版本就變，寫死會靜默拿到 0 筆，
 * 而 0 筆在界面上和「本週沒課」長得一模一樣。
 */
object JwTimetableDom {
    /**
     * 在頁面裡把課表表格攤成 `{"rows":[{"period":"1-2节","cells":[7 個字串]}]}`。
     *
     * ## ★ 三個都是真機上踩出來的（第一次爬就全踩到了）
     *
     * 1. **rowspan/colspan 會把後面的行「縮短」**。正方課表裡一門跨 1-2 節的課，
     *    第 2 節那一行的 `tr.cells` 就少一格 —— 直接按 `cells[i]` 對星期會**整體錯位**。
     *    所以必須先把表格按 rowspan/colspan **攤成真正的二維網格**再取值。
     * 2. **第一行常常是標題行**（「2026-2027学年第1学期 某某的课表 学号：…」），
     *    它只有一個跨列的格子。真機實測：我上一版把它當成一節課，
     *    回報「没认出节次」而**真正的課一行都沒進**。判據改成
     *    「行首那一格必須是節次形態」（見 PERIOD_HEAD 那個正則）。
     * 3. **星期是表頭給的，不是位置給的**。有的部署把「時間」放在第 0 列、
     *    星期從第 1 列開始；有的中間還有「備註」列。所以先讀表頭行，
     *    把每一列**映射到星期幾**，再按那個映射取格子。
     */
    val SCRIPT: String = """
        (function(){
          try {
            function txt(td){ return (td.innerText||'').replace(/ /g,' ').trim(); }
            function norm(s){ return (s||'').replace(/\s+/g,' ').trim(); }
            var tables = Array.prototype.slice.call(document.querySelectorAll('table'));
            if (!tables.length) return JSON.stringify({error:'这个页面里没有任何表格'});

            // 1) 找最像課表的表：表頭裡出現的「星期/周X」種類最多
            function dayKinds(tb){
              var t = norm(tb.innerText), n = 0;
              for (var d = 1; d <= 7; d++) {
                var a = '星期' + '一二三四五六日'.charAt(d-1);
                var b = '周' + '一二三四五六日'.charAt(d-1);
                if (t.indexOf(a) >= 0 || t.indexOf(b) >= 0) n++;
              }
              return n;
            }
            var best = null, bestDays = 0;
            tables.forEach(function(tb){
              var n = dayKinds(tb);
              if (n > bestDays) { bestDays = n; best = tb; }
            });
            if (!best || bestDays < 4) return JSON.stringify({error:'没找到带星期表头的课表表格'});

            // 2) 按 rowspan/colspan 攤成二維網格（空格子補 null）
            //    ⚠️ 同時記下**每一格是哪個 td**：正文（innerText）會把學校的
            //    結構丟光 —— 一格裡 3 門課擠成一串、藍色/紅色斜體的區別也沒了。
            //    要按 `div.timetable_con` 一格一格切，必须拿著元素本身。
            var grid = [], gridEl = [];
            Array.prototype.slice.call(best.rows).forEach(function(tr, r){
              while (grid.length <= r) { grid.push([]); gridEl.push([]); }
              var c = 0;
              Array.prototype.slice.call(tr.cells).forEach(function(td){
                while (grid[r][c] !== undefined) c++;          // 被上一行 rowspan 佔掉的列
                var rs = td.rowSpan || 1, cs = td.colSpan || 1;
                var v = txt(td);
                for (var i = 0; i < rs; i++) {
                  while (grid.length <= r + i) { grid.push([]); gridEl.push([]); }
                  for (var j = 0; j < cs; j++) {
                    grid[r + i][c + j] = v; gridEl[r + i][c + j] = td;
                  }
                }
                c += cs;
              });
            });

            // 3) 表頭行 → 每列是星期幾（1..7），並找出「節次」那一列
            var colOfDay = {}, periodCol = -1, headerRow = -1;
            grid.forEach(function(row, r){
              var hits = 0;
              row.forEach(function(cell, c){
                var m = norm(cell).match(/^(?:星期|周)([一二三四五六日天])$/);
                if (m) { hits++; colOfDay[c] = '一二三四五六日天'.indexOf(m[1]) + 1; }
              });
              if (hits >= 4 && headerRow < 0) headerRow = r;
            });
            if (headerRow < 0) return JSON.stringify({error:'课表表头认不出星期列'});
            // ★ 節次列**不能取「最左那個非星期列」**：真機實測這張表左邊有**兩列**
            //   —— 第 0 列是「上午/下午/晚上」這種時段分組，第 1 列才是「1-2节」。
            //   取最左會取到「上午」，然後一行都挑不出來（我上一版就是這樣）。
            //   改成**打分**：哪一列的單元格最像節次，那一列才是節次列。
            var PH = /^第?\s*(\d{1,2})\s*(?:[-~－,]\s*(\d{1,2}))?\s*节?/;
            function looksLikePeriod(v){
              var m = (v||'').match(PH);
              if (!m) return false;
              var n = parseInt(m[1], 10);
              return n >= 1 && n <= 14;
            }
            var bestCol = -1, bestHits = 0;
            for (var c2 = 0; c2 < (grid[headerRow]||[]).length; c2++) {
              if (colOfDay[c2]) continue;                 // 那一列已經是星期列
              var hits = 0;
              grid.forEach(function(row, r){ if (r > headerRow && looksLikePeriod(norm(row[c2]))) hits++; });
              if (hits > bestHits) { bestHits = hits; bestCol = c2; }
            }
            periodCol = bestCol;
            if (periodCol < 0 || bestHits < 2) {
              return JSON.stringify({error:'没找到节次那一列（非星期列里最多只有 ' + bestHits + ' 格像节次）'});
            }

            // 4) 數據行：行首必須是節次形態（「1-2节」「第3节」「5」），
            //    標題行/說明行（「2026-2027学年…学号：…」）因為不匹配會被自然跳過。
            // 行首判據用**前綴匹配**而不是整行相等：真機實測正方那一列常寫成
            // 「1-2节 08:00-09:40」（節次後面跟著時間），要求整行等於「1-2节」會
            // 一行都挑不出來。同時用兩條攔截防住標題行：
            //   · 起始數字必須在 1..14（「2026-2027学年…」會被抓成 20 → 擋掉）
            //   · 超過 20 字且不含「节」的一律不當節次（標題/說明行）
            var PERIOD_HEAD = /^第?\s*(\d{1,2})\s*(?:[-~－,]\s*(\d{1,2}))?\s*节?/;

            /**
             * 把一個格子切成**一门课一个对象**，并把学校自己标的栏位名带着。
             *
             * ★ 为什么不再交 innerText（业主 2026-10-19：「你课表和时间对不上」）：
             *   真机原文（docs/B22-JW-CELL-HTML.txt）里，一个格子是
             *   `<div class="timetable_con">` × N，每个字段是一个 `<p>`，
             *   字段名在 `<span title="上课地点">` 这种 tooltip 上。
             *   innerText 会把这三件事全丢掉：
             *     1. 一格 3 门课挤成一串 ⇒ 旧解析只认**第一个** `(N-M节)`，
             *        后面那两门课**整块静默消失**（他课表上没有、实际要去上）；
             *     2. 学校明明有具名栏位（教室/教师/节周/考核方式/学分），
             *        我却用正则从挤在一起的字符串里猜 ⇒ 教室「教六206」这类抓不到；
             *     3. 表头自己写着「红色斜体为待筛选，蓝色为已选上」⇒ 颜色是**选课状态**，
             *        innerText 里没有，于是将「待筛选」的课当成本周要上的课显示。
             *   现在这三样都从 DOM 里拿。
             */
            function blocksOf(td, text){
              var t = norm(text);
              if (!td || !t) return null;
              var cons = td.querySelectorAll('div.timetable_con');
              // 学校哪天改版、格子里没有 timetable_con 了：整格当一个 raw 块交回去，
              // 由 Kotlin 走旧的内嵌 `(N-M节)` 那条路（**不能因为结构变了就没课**）。
              if (!cons.length) return [{ raw: t.slice(0, 400) }];
              var out = [];
              Array.prototype.slice.call(cons).forEach(function(con){
                var titleEl = con.querySelector('span.title');
                var fonts = con.querySelectorAll('font[color]');
                var color = fonts.length ? (fonts[0].getAttribute('color') || '') : '';
                var styleSrc = titleEl || (fonts.length ? fonts[0] : con);
                var italic = '';
                try { italic = window.getComputedStyle(styleSrc).fontStyle; } catch (e) {}
                var f = {};
                Array.prototype.slice.call(con.querySelectorAll('p')).forEach(function(p){
                  var lab = p.querySelector('span[title]');
                  var key = norm(lab ? (lab.getAttribute('title') || '') : '');
                  var val = norm(p.innerText);            // glyphicon 是 ::before，不进气泡文字
                  if (key && val) f[key] = val.slice(0, 90);
                });
                out.push({
                  name: norm(titleEl ? titleEl.innerText : '').slice(0, 60),
                  color: color, italic: italic, fields: f,
                  raw: norm(con.innerText).slice(0, 300),
                });
              });
              return out;
            }

            var rows = [], heads = [];
            grid.forEach(function(row, r){
              if (r <= headerRow) return;
              var head = norm(row[periodCol]);
              if (heads.length < 5) heads.push(head.slice(0, 24));
              var hm = head ? head.match(PERIOD_HEAD) : null;
              if (!hm) return;
              var n1 = parseInt(hm[1], 10);
              if (!(n1 >= 1 && n1 <= 14)) return;
              if (head.length > 20 && head.indexOf('节') < 0) return;
              // 時段分組行（「上午」那種）在選對節次列之後自然不會匹配；
              // 這一條是兜底：萬一學校把時段放在**同一列**裡，也不把它當一節課。
              if (/^(上午|下午|晚上|早自习|晚自习)$/.test(head)) return;
              var cells = [null,null,null,null,null,null,null];
              row.forEach(function(v, c){
                var d = colOfDay[c];
                if (d >= 1 && d <= 7) cells[d-1] = blocksOf((gridEl[r] || [])[c], v);
              });
              rows.push({ period: head, slot: norm(row[0]), cells: cells });
            });
            // ↓ **調試用**：第一格資料格的原樣 innerHTML。
            //   學校表頭自己寫了「紅色斜體為待篩選，藍色為已選上」——
            //   一門課到底是他**選上的**還是**候選**，顏色是唯一 distinguishing 的東西，
            //   而 innerText 把它丟光了（所以週一会出现「大学物理 第18周」這種
            //   跟他實際上课對不上的塊）。我要先在真機上看见**真实的标记**，
            //   再决定按什麼切格、要不要標「待篩選」—— 不靠猜寫解析器。
            var probeHtml = '', probeLeaves = [];
            try {
              var firstCell = null;
              for (var rr = headerRow + 1; rr < best.rows.length && !firstCell; rr++) {
                for (var cc = 0; cc < best.rows[rr].cells.length; cc++) {
                  var t = best.rows[rr].cells[cc];
                  if (t && txt(t).length > 8) { firstCell = t; break; }
                }
              }
              if (firstCell) {
                probeHtml = (firstCell.innerHTML || '').slice(0, 4000);
                var walk = function(node){
                  if (!node) return;
                  if (node.nodeType === 1) {
                    var kids = Array.prototype.slice.call(node.childNodes);
                    var hasElem = kids.some(function(k){ return k.nodeType === 1; });
                    var st = window.getComputedStyle(node);
                    if (!hasElem) probeLeaves.push({
                      tag: node.tagName, text: norm(node.innerText).slice(0, 70),
                      color: st.color, fs: st.fontStyle, fw: st.fontWeight
                    });
                    else kids.forEach(walk);
                  }
                };
                Array.prototype.slice.call(firstCell.childNodes).forEach(walk);
              }
            } catch (pe) { probeHtml = 'probe failed: ' + String(pe).slice(0, 80); }
            return JSON.stringify({
              rows: rows, gridRows: grid.length, headerRow: headerRow,
              periodCol: periodCol, heads: heads,
              // 標題行（真機逐字：「2026-2027学年第1学期×××的课表学号：…」）。
              // Kotlin 那邊只用正則取「學年+學期」那一段，姓名與學號不进介面。
              title: norm((grid[0] || [])[0] || '').slice(0, 120),
              probeHtml: probeHtml, probeLeaves: probeLeaves,
              // ↓ 只給**調試版的原文落盤**用（JwTimetableRepository.dumpDebug）：
              //   我要看學校**自己**寫的行首（是不是帶「08:00-09:40」那種時間）、
              //   表頭到底哪一列是星期幾（週次對不上的另一種可能：列映射錯）。
              //   解析器不讀這三项，所以多了它不改變任何行為。
              colMap: (function(){ var o = []; for (var c3 = 0; c3 < grid[headerRow].length; c3++) o.push(colOfDay[c3] || 0); return o; })(),
              header: (grid[headerRow] || []).map(function(v){ return norm(v).slice(0, 20); }),
              gridDump: grid.slice(0, 30).map(function(r){
                return r.map(function(v){ return norm(v || '').slice(0, 90); });
              }),
            });
          } catch (e) { return JSON.stringify({error: String(e).slice(0,140)}); }
        })()
    """.trimIndent()
}