package com.muyao.wanzaihyperos.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B18：教務系統（正方 ZFSoft）的解析測試。
 *
 * 輸入逐字抄自真機探測原文（`docs/B18-JW-PROBE.txt`）。
 */
class JwParserTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // ── 分頁信封 ─────────────────────────────────────────────────────────
    @Test
    fun `解析分頁信封與 items`() {
        val page = JwPageParser.parse(
            obj(
                """{"currentPage":1,"currentResult":2,"items":[{"a":1},{"b":2}],
                    "limit":50,"offset":0,"pageNo":1,"pageSize":50,"showCount":50,
                    "totalCount":22,"totalPage":1,"totalResult":22}""",
            ),
        )
        assertEquals(2, page.items.size)
        assertEquals(22, page.total)
        assertFalse(page.isEmpty)
    }

    @Test
    fun `totalCount 與 totalResult 不一致時取較大值`() {
        // 這個站的同類系統出現過兩者不一致；取較大值當參考，
        // 而翻頁仍以「本次回傳數 < 頁大小」為準（全專案一致）。
        val page = JwPageParser.parse(
            obj("""{"items":[],"totalCount":9,"totalResult":10}"""),
        )
        assertEquals(10, page.total)
    }

    @Test
    fun `筆數取不到時是 null 而不是 0`() {
        val page = JwPageParser.parse(obj("""{"items":[]}"""))
        assertTrue(page.isEmpty)
        assertNull("未知不當成 0", page.total)
    }

    @Test
    fun `外層不對時回空頁而不是崩掉`() {
        assertTrue(JwPageParser.parse(null).isEmpty)
        assertTrue(JwPageParser.parse(obj("""{"message":"未登录"}""")).isEmpty)
    }

    // ── 考試信息 ─────────────────────────────────────────────────────────
    /** 真機原文（`gnmkdm=N358105` 的第一筆）。 */
    private val examReal = """
        {"xh_id":"12345678","bj":"软件工程9901","cdbh":"未放开不可查","zwh":"未放开不可查",
         "ksmc":"2026-2027学年第1学期开学补考","kssj":"未放开不可查","kch":"C01113",
         "kkxy":"计算机与计算科学学院","xqm":"3","cdmc":"未放开不可查","cdxqmc":"未放开不可查",
         "kcmc":"数据挖掘技术与应用","pycc":"本科","totalresult":22,"njmc":"2025",
         "jgmc":"计算机与计算科学学院","jxbmc":"数据挖掘技术与应用C01113",
         "sjbh":"统一-2026-2027学年第1学期开学补考-C01113","xb":"男","zymc":"软件工程",
         "xf":"3.0","xh":"12345678","xnmc":"2026-2027","cdjc":"未放开不可查","xm":"某某某",
         "xnm":"2026","xqmc":"浙大城市学院","jsxx":"J05011/王贵","xqmmc":"1","row_id":1}
    """.trimIndent()

    @Test
    fun `解析考試並取出要顯示的欄位`() {
        val e = JwExamParser.parseOne(obj(examReal))!!
        assertEquals("数据挖掘技术与应用", e.courseName)
        assertEquals("2026-2027学年第1学期开学补考", e.examName)
        assertEquals("C01113", e.courseCode)
        assertEquals("3.0", e.credit)
        assertEquals("计算机与计算科学学院", e.college)
        assertEquals("2026-2027", e.academicYear)
        // 教師原始是 `工號/姓名` → 顯示名取後半
        assertEquals("王贵", e.teacherName)
    }

    @Test
    fun `未放开不可查是服務端的答覆而不是解析失敗`() {
        // ★ 這台部署把考試時間與考場全部回成「未放开不可查」——
        //   那是**學校還沒開放查詢**，界面必須原樣轉達並說明，
        //   不可以自己編一個時間，也不可以顯示成空白讓人以為「沒安排」。
        val e = JwExamParser.parseOne(obj(examReal))!!
        assertTrue("應判定為受限", e.restricted)
        assertEquals("学校尚未开放考试时间与考场查询", e.restrictionNote)
        // 原文要保留（原樣顯示給使用者看）
        assertEquals("未放开不可查", e.time)
        assertEquals("未放开不可查", e.room)
    }

    @Test
    fun `沒有受限標記時 restricted 為 false`() {
        val e = JwExamParser.parseOne(
            obj("""{"kcmc":"课","kssj":"2026-10-20 09:00","cdmc":"教六205","zwh":"12"}"""),
        )!!
        assertFalse(e.restricted)
        assertNull(e.restrictionNote)
    }

    @Test
    fun `缺課程名的考試條目丟掉`() {
        assertEquals(0, JwExamParser.parse(JwPageParser.parse(obj("""{"items":[{"ksmc":"x"}]}"""))).size)
    }

    // ── 學生成績 ─────────────────────────────────────────────────────────
    /** 真機原文（`gnmkdm=N305005` 的第一筆）。 */
    private val scoreReal = """
        {"bfzcj":"60","bh":"01312501","bj":"软件工程9901","cj":"60","cjbdczr":"王波",
         "cjbdsj":"2026-01-20 20:15:53","cjsfzf":"否","czr":"王波","jd":"1.50",
         "jgmc":"计算机与计算科学学院","jsxm":"董婷;王波",
         "jxbmc":"(2025-2026-1)数据挖掘技术与应用","kcmc":"数据结构基础","xf":"3.0"}
    """.trimIndent()

    @Test
    fun `解析成績並取出績點學分教師`() {
        val s = JwScoreParser.parseOne(obj(scoreReal))!!
        assertEquals("数据结构基础", s.courseName)
        assertEquals("60", s.score)
        assertEquals("1.50", s.gradePoint)
        assertEquals("3.0", s.credit)
        assertEquals("2026-01-20 20:15:53", s.publishedAt)
        // 原始是 `;` 分隔 → 顯示用「、」
        assertEquals("董婷、王波", s.teacherLabel)
    }

    @Test
    fun `成績是等第時不當成數字處理`() {
        // 同一套系統的其它部署會回 `cj:"优秀"`。收成字串才不會丟資訊。
        val s = JwScoreParser.parseOne(obj("""{"kcmc":"课","cj":"优秀","jd":"4.0"}"""))!!
        assertEquals("优秀", s.displayScore)
    }

    @Test
    fun `成績兩個欄位都空時顯示破折號而不是 0`() {
        val s = JwScoreParser.parseOne(obj("""{"kcmc":"课"}"""))!!
        assertEquals("—", s.displayScore)
    }

    @Test
    fun `cj 空但有 bfzcj 時退回百分制`() {
        val s = JwScoreParser.parseOne(obj("""{"kcmc":"课","cj":"","bfzcj":"88"}"""))!!
        assertEquals("88", s.displayScore)
    }

    @Test
    fun `缺課程名的成績條目丟掉`() {
        assertEquals(0, JwScoreParser.parse(JwPageParser.parse(obj("""{"items":[{"cj":"60"}]}"""))).size)
    }

    // ── 功能定義 ─────────────────────────────────────────────────────────
    @Test
    fun `功能碼與路徑逐字對應真機選單`() {
        // 這三個值是從它自己的選單 onclick 抄出來的，錯了就打不通。
        assertEquals("N358105", JwFeature.EXAM.code)
        assertEquals("/kwgl/kscx_cxXsksxxIndex.html", JwFeature.EXAM.path)
        assertEquals("N305005", JwFeature.SCORE.code)
        assertEquals("/cjcx/cjcx_cxDgXscj.html", JwFeature.SCORE.path)
        assertEquals("N253508", JwFeature.TIMETABLE.code)
    }

    @Test
    fun `課表沒有 doType 資料介面`() {
        // 實測課表的內容直接畫在 HTML 裡，沒有 JSON 介面。
        assertFalse(JwFeature.TIMETABLE.hasDataApi)
        assertTrue(JwFeature.EXAM.hasDataApi)
    }

    @Test
    fun `頁面網址照 openWin 的規則帶 layout 與 gnmkdm`() {
        val url = JwFeature.EXAM.pageUrl("http://ijw.hzcu.edu.cn")
        assertTrue(url.contains("/jwglxt/kwgl/kscx_cxXsksxxIndex.html"))
        assertTrue(url.contains("gnmkdm=N358105"))
        assertTrue(url.contains("layout=default"))
    }

    @Test
    fun `資料介面網址帶 doType 與 gnmkdm`() {
        val url = JwFeature.SCORE.dataUrl("http://ijw.hzcu.edu.cn")
        assertTrue(url.contains("/jwglxt/cjcx/cjcx_cxDgXscj.html"))
        assertTrue(url.contains("doType=query"))
        assertTrue(url.contains("gnmkdm=N305005"))
    }
}
