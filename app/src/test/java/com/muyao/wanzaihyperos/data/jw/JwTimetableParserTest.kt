package com.muyao.wanzaihyperos.data.jw

import com.muyao.wanzaihyperos.data.tronclass.ScheduleSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教務（正方）課表的解析。
 *
 * 分兩組，**證據等級不同**，別混著看：
 *
 * - 上面那組（`kbList` JSON、舊 DOM 形態）是按正方常见欄位**手寫**的 ——
 *   證的是「解析器對這種形態是對的」，不是「學校回的就是這個形態」；
 * - 下面「按 div.timetable_con 切块」那一組的形態**逐字取自真機**
 *   （`docs/B22-JW-CELL-HTML.txt`：他账号上第一格的原樣 innerHTML）。
 *
 * ★ 為什麼要留手寫那組：正方各部署不一樣，回退路徑（舊 DOM、raw-only）
 *   必須仍然能用 —— 學校哪天改版，課表不能就變空白。
 */
class JwTimetableParserTest {

    private fun json(s: String) = Json.parseToJsonElement(s)

    // ── 字串 → 星期/節次/週次 ─────────────────────────────────────────────

    @Test
    fun `星期与节次的常见写法`() {
        assertEquals(Triple(1, 1, 2), JwTimetableParser.dayPeriod("星期一第1-2节"))
        assertEquals(Triple(2, 3, 4), JwTimetableParser.dayPeriod("周2第3-4节"))
        assertEquals(Triple(5, 7, 7), JwTimetableParser.dayPeriod("周五 第7节"))
        assertEquals(Triple(7, 1, 2), JwTimetableParser.dayPeriod("星期日 1-2节"))
        // 認不出星期就回 null（呼叫方要留證，不能丟）
        assertNull(JwTimetableParser.dayPeriod("第1-2节"))
        assertNull(JwTimetableParser.dayPeriod(""))
    }

    @Test
    fun `★ 周次那段不能被读成节次`() {
        // 「1-16周 第1-2节」裡兩個範圍長得一樣。
        // 若從頭找，會讀成第 1-16 節 —— 那是會讓人從第一節坐到最后一節的錯。
        assertEquals(Triple(2, 1, 2), JwTimetableParser.dayPeriod("周二 1-16周 第1-2节"))
        assertEquals(1..16, JwTimetableParser.weekRange("1-16周"))
    }

    @Test
    fun `周次的三种写法`() {
        assertEquals(1..16, JwTimetableParser.weekRange("1-16周"))
        assertEquals(3..3, JwTimetableParser.weekRange("3周"))
        assertEquals(10..16, JwTimetableParser.weekRange("第10~16周"))
        assertNull(JwTimetableParser.weekRange("双周"))   // 認不出 ⇒ 由呼叫方兜底並留痕
    }

    // ── 路徑 ①：JSON ─────────────────────────────────────────────────────
    @Test
    fun `kbList 一条能变成可渲染的槽位`() {
        val r = JwTimetableParser.parseJson(
            json(
                """{"kbList":[
                  {"kch":"20112201","kcmc":"高等数学","jsxm":"张三","cdmc":"理1-216",
                   "zcd":"1-16周","xbjmsz":"星期一第1-2节"}
                ]}""",
            ),
        )
        val s = r.slots.single()
        assertEquals("高等数学", s.courseName)
        assertEquals("张三", s.teacher)
        assertEquals("理1-216", s.room)
        assertEquals(1, s.dayOfWeek)
        assertEquals(1, s.periodStart)
        assertEquals(2, s.periodEnd)
        assertEquals(1, s.weekStart)
        assertEquals(16, s.weekEnd)
        assertEquals(ScheduleSource.JIAOWU, s.source)
        assertTrue(r.unparsed.isEmpty())
        assertEquals("教务JSON", r.strategy)
    }

    @Test
    fun `同一门课的 id 稳定，不同课不同`() {
        val one = JwTimetableParser.parseJson(
            json("""{"kbList":[{"kcmc":"A","kch":"K1","xbjmsz":"周一第1节","zcd":"1-2周"}]}"""),
        ).slots.single().courseId
        val same = JwTimetableParser.parseJson(
            json("""{"kbList":[{"kcmc":"A","kch":"K1","xbjmsz":"周三第5节","zcd":"1-2周"}]}"""),
        ).slots.single().courseId
        val other = JwTimetableParser.parseJson(
            json("""{"kbList":[{"kcmc":"B","kch":"K2","xbjmsz":"周一第1节","zcd":"1-2周"}]}"""),
        ).slots.single().courseId
        assertEquals(one, same)          // 配色要恆定
        assertTrue(other > 0 && one != other)
    }

    @Test
    fun `认不出的条目进 unparsed 并带上原文`() {
        val r = JwTimetableParser.parseJson(
            json("""{"kbList":[{"kcmc":"奇怪课","someOtherField":"周一"}]}"""),
        )
        assertTrue(r.slots.isEmpty())
        assertEquals(1, r.unparsed.size)
        // 原樣留證：他截圖給我時我看得見學校到底給的是什麼形態。
        assertTrue(r.unparsed.single().contains("奇怪课"))
        assertTrue(r.unparsed.single().contains("someOtherField"))
    }

    @Test
    fun `没有 kbList 时说清是形态不对，而不是没课`() {
        val r = JwTimetableParser.parseJson(json("""{"success":true}"""))
        assertTrue(r.slots.isEmpty() && r.unparsed.isNotEmpty())
        assertTrue(r.unparsed.single().contains("没有 kbList"))
        // 「一条证据都没有」才是真空白。這裡有話可說（形態不對），
        // 界面據此顯示「形态不对」而不是「本周没课」—— 兩者差很多。
        assertFalse(r.nothingAtAll)
    }

    // ── 路徑 ②：DOM 表格 ──────────────────────────────────────────────────

    @Test
    fun `光桿數字的節次列也要認（正方真機就是這樣寫的）`() {
        assertEquals(1 to 2, JwTimetableParser.periodOnly("1-2"))
        assertEquals(5 to 5, JwTimetableParser.periodOnly("5"))
        assertEquals(3 to 4, JwTimetableParser.periodOnly("第3-4节"))
        // 標題行不能被抓成節次（真機踩過：「2026-2027学年第1学期…」被當成一行課）
        assertNull(JwTimetableParser.periodOnly("2026-2027学年第1学期某某某的课表"))
        assertNull(JwTimetableParser.periodOnly("学号：12345678"))
    }

    @Test
    fun `★ 真機形態：一格是一整串、節次寫在格子裡、週次是一張清單`() {
        // 這一格是 2026-10-19 真機爬他們學校課表時**實際拿到的形態**
        //（課名/老師/學號換成假的，結構與標點逐字保留）。
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[
                  {"period":"第1节","cells":["大学物理(Ⅱ)(1-2节)1周,4-6周,8-15周,17周××学院文1-103李老师(2026-2027-1)-A01-J05-5软件工程9901;软件工程2502考试讲课:32","",  "","","","",""]},
                  {"period":"第2节","cells":["大学物理(Ⅱ)(1-2节)1周,4-6周,8-15周,17周××学院文1-103李老师(2026-2027-1)-A01-J05-5软件工程9901;软件工程2502考试讲课:32","","","","","",""]}
                ]}""",
            ),
        )
        // rowspan 讓同一門課在第 1、2 行各出現一次 —— 必須壓成一塊，
        // 否則界面上會看到兩塊一模一樣的課（真機第一版就是這樣）。
        val phys = r.slots.filter { it.courseName.startsWith("大学物理") }
        assertTrue("rowspan 沒壓住：${r.slots.map { it.raw.take(20) }}", phys.isNotEmpty())
        val one = phys.first()
        // 節次取自**格子裡的 (1-2节)**，不是行首的「第1节」
        assertEquals(1, one.periodStart)
        assertEquals(2, one.periodEnd)
        // 課名是 marker 之前那一段（連它自己的括號一起）
        assertEquals("大学物理(Ⅱ)", one.courseName)
        assertEquals("文1-103", one.room)
        assertEquals("李老师", one.teacher)
        // 週次清單 `1周,4-6周,8-15周,17周` ⇒ 一個區間出一個槽位（不是一段吞掉全部）
        val weeks = r.slots.filter { it.courseName.startsWith("大学物理") }
            .map { it.weekStart..it.weekEnd }.sortedBy { it.first }
        assertEquals(listOf(1..1, 4..6, 8..15, 17..17), weeks)
        assertEquals(1, one.dayOfWeek)
    }

    @Test
    fun `weekList 把逗号分隔的多段周次全部还原`() {
        assertEquals(
            listOf(1..1, 4..6, 8..15, 17..17),
            JwTimetableParser.weekList("1周,4-6周,8-15周,17周"),
        )
        assertEquals(listOf(1..16), JwTimetableParser.weekList("1-16周"))
        assertTrue(JwTimetableParser.weekList("没有周次信息").isEmpty())
    }

    @Test
    fun `表格的一格变成槽位，空格子跳过`() {
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[
                  {"period":"1-2节","cells":["高等数学\n张三\n理1-216\n1-16周","","","","","",""]},
                  {"period":"3-4节","cells":["","","大学物理\n李四\n理3-101\n1-8周","","","",""]}
                ]}""",
            ),
        )
        assertEquals(2, r.slots.size)
        val mon = r.slots.first()
        assertEquals(1, mon.dayOfWeek)
        assertEquals(1, mon.periodStart)
        assertEquals(2, mon.periodEnd)
        assertEquals("张三", mon.teacher)
        assertEquals("理1-216", mon.room)
        assertEquals(1..16, mon.weekStart..mon.weekEnd)
        val wed = r.slots[1]
        assertEquals(3, wed.dayOfWeek)
        assertEquals(3, wed.periodStart)
        assertEquals("教务网页", r.strategy)
    }

    @Test
    fun `一格里的两门课（空行分隔）拆成两格`() {
        val r = JwTimetableParser.parseDom(
            json("""{"rows":[{"period":"1-2节","cells":["课A\n老师A\nA101\n\n课B\n老师B\nB202","","","","","",""]}]}"""),
        )
        assertEquals(2, r.slots.size)
        assertEquals(listOf("课A", "课B"), r.slots.map { it.courseName })
    }

    @Test
    fun `节次认不出时整行留证，不静默消失`() {
        val r = JwTimetableParser.parseDom(
            json("""{"rows":[{"period":"上午","cells":["某课\n某人","","","","","",""]}]}"""),
        )
        assertTrue(r.slots.isEmpty())
        assertTrue(r.unparsed.single().contains("没认出节次"))
        assertTrue(r.unparsed.single().contains("某课"))
    }

    @Test
    fun `没找到表格时说的是改版，不是没课`() {
        val r = JwTimetableParser.parseDom(json("""{"error":"没找到带星期表头的课表表格"}"""))
        assertTrue(r.slots.isEmpty())
        assertTrue(r.unparsed.single().contains("没找到"))
    }

    @Test
    fun `DOM 里没给周次时按整学期兜底而不是丢掉`() {
        val r = JwTimetableParser.parseDom(
            json("""{"rows":[{"period":"1-2节","cells":["课X\n师X\n教室X","","","","","",""]}]}"""),
        )
        val s = r.slots.single()
        assertEquals(1, s.weekStart)
        assertEquals(18, s.weekEnd)   // 寧可多顯示一週，也不因為抓不到週次就把課藏掉
    }

    // ── 路徑 ②′：**按学校的 div.timetable_con 切块**（2026-10-19 真机原文形态）────
    //
    // 下面的形态逐字来自 `docs/B22-JW-CELL-HTML.txt`（他账号上真机落盘的第一格 innerHTML），
    // 课程/老师/教室也来自那一轮取证。这一组才是「学校实际给的字节」。

    /** 一格一块的紧凑写法：只写要验的字段，其余省略（解析器要能容忍缺字段）。 */
    private fun block(
        name: String,
        jp: String,
        place: String = "",
        teacher: String = "",
        code: String = "",
        color: String = "blue",
        italic: String = "normal",
        exam: String = "",
    ) = """{"name":"$name","color":"$color","italic":"$italic","raw":"$name $jp",
        "fields":{"节/周":"$jp","上课地点":"$place","教师":"$teacher",
        "教学班名称":"$code","考核方式":"$exam"}}"""

    /** 一行（时段 + 节次 + 七天格子）；没给的格子补 `null`（= 那一格没课）。 */
    private fun row(slot: String, period: String, vararg cells: String) = buildString {
        append("""{"period":"$period","slot":"$slot","cells":[""")
        for (i in 0 until 7) {
            if (i > 0) append(',')
            append(cells.getOrNull(i) ?: EMPTY)
        }
        append("]}")
    }

    /** 一格一块 / 一格多块（真机一格最多三块）。 */
    private fun oneCell(b: String) = "[$b]"
    private fun cellsOf(vararg b: String) = "[${b.joinToString(",")}]"
    private val EMPTY = "null"

    @Test
    fun `★ 一格两门课必须两块都在（旧版只认第一个标记，后面整门课静默消失）`() {
        // 真机形态：周二第 8-9 节挤着毛概的两段（第 1-3,5-9,17 周 与 第 10-16 周，两个老师）。
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[ ${row(
                    "下午", "8",
                    EMPTY,
                    cellsOf(
                        block("毛泽东思想和中国特色社会主义理论体系概论", "(8-9节)1-3周,5-9周,17周", "浙大城市学院 教六206", "张柏榕"),
                        block("毛泽东思想和中国特色社会主义理论体系概论", "(8-9节)10-16周", "浙大城市学院 教六206", "闫超阳"),
                    ),
                )} ] }""",
            ),
        )
        // 一块里的**每个周次区间出一个块**（这是既定规则，见 parseDom 的注释：
        // 只取第一段会让他以为第 4 周以外都没课）。所以两块 → 3 + 1 = 4 块。
        // ★ 关键在第二段（第 10-16 周、换老师）：**旧版只认格子里第一个 `(N-M节)`，
        //   那一整块会静默消失** ⇒ 第 10~16 周他以为没毛概，实际要去上。
        assertEquals(4, r.slots.size)
        assertEquals(
            setOf("1-3", "5-9", "10-16", "17-17"),
            r.slots.map { "${it.weekStart}-${it.weekEnd}" }.toSet(),
        )
        // 老师跟着各自的块走（同一门课两个老师 = 后半段换老师，不是解析串了）
        assertEquals(
            mapOf("10-16" to "闫超阳", "1-3" to "张柏榕"),
            r.slots.associate { "${it.weekStart}-${it.weekEnd}" to it.teacher }
                .filterKeys { it == "10-16" || it == "1-3" },
        )
        assertTrue(r.unparsed.isEmpty())
    }

    @Test
    fun `具名栏位抽得干净：教室不带学院前缀，单周与多段周都对`() {
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[ ${row(
                    "上午", "1",
                    oneCell(block("大学物理(Ⅱ)", "(1-2节)第18周", "浙大城市学院  文1-103", "陈明", "(2026-2027-1)-A01014-J05018-5", exam = "考试")),
                )} ] }""",
            ),
        )
        val s = r.slots.single()
        assertEquals(1, s.periodStart)
        assertEquals(2, s.periodEnd)
        // 「第18周」= **只有第 18 周**，不是 1~18 周（这一条错了会把课多显示十七周）
        assertEquals(18, s.weekStart)
        assertEquals(18, s.weekEnd)
        assertEquals("文1-103", s.room)          // 学院前缀没有信息量，裁掉
        assertEquals("陈明", s.teacher)
        assertEquals("上午", s.slotLabel)          // 学校给的唯一时段信息
        assertEquals(ScheduleSource.JIAOWU, s.source)
        // 详情里看得见学校标的考核方式（不是我们编的）
        assertTrue(s.raw.contains("考试"))
    }

    @Test
    fun `旧正则抓不到的教室形态，具名栏位能抓到`() {
        // 「教六206」「线上教学」「西网球场」都**不是**「汉字+数字-数字」，
        // 上一版从挤成一串的 innerText 里猜正则，这三类一律留空。
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[ ${row(
                    "下午", "8",
                    oneCell(block("总体国家安全观", "(8-9节)1周,5周,8-9周", "浙大城市学院 教六205", "葛杨")),
                    oneCell(block("匹克球Ⅰ", "(3-4节)1周,4-6周", "浙大城市学院 西网球场", "赵得龙")),
                    oneCell(block("形势与政策", "(8-9节)2-3周", "线上教学", "葛杨")),
                )} ] }""",
            ),
        )
        assertEquals(
            mapOf("总体国家安全观" to "教六205", "匹克球Ⅰ" to "西网球场", "形势与政策" to "线上教学"),
            r.slots.associate { it.courseName to it.room },
        )
    }

    @Test
    fun `真机第二种形态：校名和教室挤成一串也要只留教室`() {
        // 第二次真机采集到的**逐字**值（第一次是带空格的，innerText 会不会留空格
        // 取决于学校页面里那段空白被谁吞了 —— 只按空格切会把校名留在地址里）。
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[ ${row(
                    "下午", "10",
                    cellsOf(
                        block("城市和电影", "(10-11节)1-2周,4-6周,17周", "浙大城市学院教五110", "侯伟"),
                        block("马克思主义劳动观概论", "(8-9节)10-17周", "浙大城市学院校内场地", "陈婧洁"),
                        block("总体国家安全观", "(8-9节)2-3周,6-7周", "浙大城市学院线上教学", "葛杨"),
                    ),
                )} ] }""",
            ),
        )
        assertEquals(
            mapOf("城市和电影" to "教五110", "马克思主义劳动观概论" to "校内场地", "总体国家安全观" to "线上教学"),
            r.slots.associate { it.courseName to it.room },
        )
    }

    @Test
    fun `地点名自己就以「学院」开头时不裁`() {
        // 「学院楼201」这种要是被裁成「楼201」，他按屏幕去找就找不到楼。
        val r = JwTimetableParser.parseDom(
            json("""{"rows":[ ${row("上午", "1", oneCell(block("某课", "(1-2节)1-8周", "学院楼201", "老师")))} ] }"""),
        )
        assertEquals("学院楼201", r.slots.single().room)
    }

    @Test
    fun `红色斜体=待筛选要带出来，蓝色已选上不占字`() {
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[ ${row(
                    "上午", "3",
                    oneCell(block("游泳Ⅰ", "(3-4节)1-16周", "浙大城市学院 东泳馆", "李某某", color = "red", italic = "italic")),
                    oneCell(block("数据结构与算法", "(3-4节)1-3周,5-17周", "浙大城市学院 教六306", "周勇")),
                )} ] }""",
            ),
        )
        assertEquals("待筛选", r.slots.first { it.courseName == "游泳Ⅰ" }.statusLabel)
        assertEquals("", r.slots.first { it.courseName == "数据结构与算法" }.statusLabel)
    }

    @Test
    fun `rowspan 的重复块压掉，但同课不同教室的两块都留着`() {
        val cell = oneCell(block("大学英语", "(5-6节)1-8周", "浙大城市学院 教五101", "张老师"))
        val another = oneCell(block("大学英语", "(5-6节)1-8周", "浙大城市学院 教五202", "李老师"))
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[ ${row("上午", "5", cell)},
                    ${row("上午", "6", cell)},
                    ${row("上午", "5", another)},
                    ${row("上午", "6", another)} ] }""",
            ),
        )
        // 同一格跨两行（rowspan）⇒ 出现两次，必须压成一块；
        // 但**同课同节次不同教室**是分流上课，不能被当成重复压掉。
        assertEquals(2, r.slots.size)
        assertEquals(setOf("教五101", "教五202"), r.slots.map { it.room }.toSet())
    }

    @Test
    fun `节次没写在栏位里时退回行首那一列，再不行退回文字里的标记`() {
        // ① 学校某格 `节/周` 只写了周次（真机见过 `(8-9节)` 缺失的写法）⇒ 用行首节次
        val viaRow = JwTimetableParser.parseDom(
            json("""{"rows":[ ${row("上午", "7", oneCell(block("实验课", "第1-8周", "浙大城市学院 理4-527", "王老师")))} ] }"""),
        )
        assertEquals(7 to 7, viaRow.slots.single().let { it.periodStart to it.periodEnd })
        assertEquals(1, viaRow.slots.single().weekStart)
        assertEquals(8, viaRow.slots.single().weekEnd)

        // ② 学校改版、格子里没有 timetable_con（脚本只能交整格文字）⇒ 走旧的 `(N-M节)` 那条路
        val viaRaw = JwTimetableParser.parseDom(
            json("""{"rows":[ ${row("上午", "1", """[{"raw":"高等数学(1-2节)1-16周"}]""")} ] }"""),
        )
        assertEquals("高等数学", viaRaw.slots.single().courseName)
        assertEquals(1, viaRaw.slots.single().periodStart)
        assertEquals(2, viaRaw.slots.single().periodEnd)
    }

    @Test
    fun `学期名只取学校写的那一段，不带姓名和学号`() {
        // 真機逐字的標題行（`docs/B22-JW-CELL-HTML.txt` 裡第 0 行）：
        val real = "2026-2027学年第1学期某某某的课表学号：12345678注：红色斜体为待筛选，蓝色为已选上"
        assertEquals("2026-2027学年第1学期", JwTimetableParser.termOf(real))
        assertEquals("第2学期", JwTimetableParser.termOf("第2学期课表"))
        // 認不出 ⇒ 空字串（界面就不顯示學期），**不拿今天的日期猜一個**
        assertEquals("", JwTimetableParser.termOf("课表"))
        assertEquals("", JwTimetableParser.termOf(null))
    }

    @Test
    fun `DOM 读回来的学期名会带在结果里`() {
        val r = JwTimetableParser.parseDom(
            json(
                """{"title":"2026-2027学年第1学期×××的课表学号：123","rows":[ ${row(
                    "上午", "1", oneCell(block("大学物理(Ⅱ)", "(1-2节)第18周", "浙大城市学院  文1-103", "陈明")),
                )} ] }""",
            ),
        )
        assertEquals("2026-2027学年第1学期", r.termLabel)
    }

    @Test
    fun `块与行首都认不出节次时原样留证，不静默少一门课`() {
        // 学校某格 `节/周` 空着，行首那一列又不是节次形态（学校改版时会发生）。
        // 丢掉 = 他不去上，所以必须**列在页面上**。
        val r = JwTimetableParser.parseDom(
            json(
                """{"rows":[ ${row("上午", "上午", oneCell(block("某课", "", "浙大城市学院 文1-103", "陈明")))} ] }""",
            ),
        )
        assertTrue(r.slots.isEmpty())
        assertEquals(1, r.unparsed.size)
        assertTrue(r.unparsed.single().contains("没认出节次"))
        assertTrue(r.unparsed.single().contains("某课"))
    }
}
