package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B14：考試 / 課程公告 / 討論區的解析測試。
 *
 * 這一輪的每一條輸入都對應 `docs/B14-TRONCLASS-SCAN.txt` 裡的**真機原文**。
 * 特別重要的是它推翻了一個**錯的結論**：B12 掃 8 門課就說「全空」，
 * 掃全部 37 門後發現 6 門有考試、4 門有討論區、1 門有公告。
 */
class TronExamParserTest {

    private fun el(json: String): JsonElement = Json.parseToJsonElement(json)

    // 真機原文（c=46122 的兩條考試，欄位已確認）。
    private val real = """
        {"exams":[
         {"announce_answer_status":"immediate_announce","check_submit_ip_consistency":false,
          "created_at":"2025-10-13T02:31:06Z","description":"",
          "end_time":"2025-10-14T07:00:00Z","exam_submissions":[63300],"id":63300,
          "is_closed":true,"is_started":true,"publish_time":null,
          "score_percentage":"0.00","start_time":"2025-10-14T01:50:00Z",
          "title":"unit1 words and expressions test"},
         {"end_time":"2025-10-16T07:00:00Z","exam_submissions":[63669],"id":63669,
          "is_closed":true,"is_started":true,"score_percentage":"0.00",
          "start_time":"2025-10-16T05:30:00Z","title":"book2unit1 Mind the gap"}
        ]}
    """.trimIndent()

    @Test
    fun `解析考試清單並轉成本地時間`() {
        val exams = TronExamParser.parseList(el(real))
        assertEquals(2, exams.size)
        val a = exams[0]
        assertEquals(63300, a.id)
        assertEquals("unit1 words and expressions test", a.title)
        assertTrue(a.started)
        assertTrue(a.closed)
        // UTC 01:50 → 本地（+08）09:50。照原樣顯示會早 8 小時。
        assertTrue("本地開始時間應為 09:50，實際 ${a.startLocal}", a.startLocal.endsWith("09:50"))
        assertTrue(a.endLocal.endsWith("15:00"))
    }

    @Test
    fun `用 exam_submissions 判斷考了沒而不是從 is_closed 推`() {
        // 兩條都是「已結束」，但一條有作答、一條沒有。
        val submitted = TronExamParser.parseOne(
            el("""{"id":1,"title":"t","is_started":true,"is_closed":true,"exam_submissions":[63300]}""")
                .let { it as kotlinx.serialization.json.JsonObject },
        )!!
        val notSubmitted = TronExamParser.parseOne(
            el("""{"id":2,"title":"t","is_started":true,"is_closed":true,"exam_submissions":[]}""")
                .let { it as kotlinx.serialization.json.JsonObject },
        )!!
        assertTrue(submitted.submitted)
        assertFalse(notSubmitted.submitted)
        assertEquals("已作答", submitted.statusLabel)
        // 結束了但沒作答 —— 講「已结束（未作答）」，不是「已作答」。
        assertEquals("已结束（未作答）", notSubmitted.statusLabel)
        // 兩者都不可再操作（都結束了）。
        assertFalse(submitted.actionable)
        assertFalse(notSubmitted.actionable)
    }

    @Test
    fun `狀態講法的優先順序是先看我考了沒`() {
        // 沒開始、沒結束、沒作答 → 未开始。
        val notStarted = TronExamParser.parseOne(
            el("""{"id":1,"title":"t","is_started":false,"is_closed":false,"exam_submissions":[]}""")
                .let { it as kotlinx.serialization.json.JsonObject },
        )!!
        assertEquals("未开始", notStarted.statusLabel)
        // 進行中 → 可以去做（但本 App 不做寫操作，只標狀態）。
        val ongoing = TronExamParser.parseOne(
            el("""{"id":2,"title":"t","is_started":true,"is_closed":false,"exam_submissions":[]}""")
                .let { it as kotlinx.serialization.json.JsonObject },
        )!!
        assertEquals("进行中", ongoing.statusLabel)
        assertTrue(ongoing.actionable)
    }

    @Test
    fun `缺 id 或外層不對時回空清單`() {
        assertEquals(0, TronExamParser.parseList(el("""{"exams":[{"title":"没有 id"}]}""")).size)
        assertEquals(0, TronExamParser.parseList(el("""{"message":"未找到资源"}""")).size)
        assertEquals(0, TronExamParser.parseList(null).size)
    }
}

/** 公告的 HTML → 純文字（這一輪新加，直接影響顯示）。 */
class TronBulletinHtmlTest {

    @Test
    fun `真機公告的 HTML 被去成可讀文字`() {
        // 逐字抄自 docs/B14-TRONCLASS-SCAN.txt（c=46092 的開課公告）。
        val html =
            """<p><span style="font-size: 13.5pt;">各位同学好，本课程将采用线下为主，</span>""" +
                """<span style="font-size: 13.5pt; color: black; background: white;">备用</span>""" +
                """<span style="font-size: 13.5pt;">线上以钉钉直播的方式进行授课。</span></p>""" +
                """<p style="line-height: 125%;"><b><span style="font-family: 黑体;">一、对学生在线学习的要求 </span></b></p>"""
        val text = htmlToPlainText(html)
        assertFalse("不該留下任何標籤：$text", text.contains("<"))
        assertFalse("不該留下 style 屬性：$text", text.contains("style"))
        assertTrue(text.contains("各位同学好，本课程将采用线下为主"))
        assertTrue(text.contains("备用"))
        // 兩個 <p> 之間要有換行，否則會擠成一行。
        assertTrue("段落應該分行，實際：$text", text.lines().size >= 2)
        assertTrue(text.lines()[0].startsWith("各位同学好"))
    }

    @Test
    fun `br 標籤變成換行`() {
        val text = htmlToPlainText("第一行<br/>第二行<br>第三行")
        assertEquals(listOf("第一行", "第二行", "第三行"), text.lines())
    }

    @Test
    fun `常見實體被還原`() {
        val text = htmlToPlainText("A&amp;B &lt;tag&gt; &quot;q&quot; &#39;s&#39;&nbsp;尾")
        assertTrue(text.contains("A&B"))
        assertTrue(text.contains("<tag>"))
        assertTrue(text.contains("\"q\""))
        assertTrue(text.contains("'s'"))
        assertTrue(text.contains("尾"))
    }

    @Test
    fun `amp 最後還原避免雙重解碼`() {
        // `&amp;lt;` 的原意是「顯示 &lt; 這四個字」，不是「顯示 <」。
        val text = htmlToPlainText("请使用 &amp;lt;br&amp;gt; 换行")
        assertTrue("應該是 &lt;br&gt; 而不是 <br>：$text", text.contains("&lt;br&gt;"))
    }

    @Test
    fun `純文字原樣回傳且不亂動`() {
        assertEquals("就是一段普通文字", htmlToPlainText("  就是一段普通文字  "))
        assertEquals("", htmlToPlainText(""))
        assertEquals("", htmlToPlainText("   "))
    }

    @Test
    fun `空段落與多餘空白被收掉`() {
        val text = htmlToPlainText("<p>甲</p><p></p><p>   </p><p>乙</p>")
        assertEquals(listOf("甲", "乙"), text.lines())
    }

    @Test
    fun `公告模型的 plainText 走同一條轉換`() {
        val b = TronBulletin(
            id = 1,
            title = "開課公告",
            content = "<p>各位同学好</p>",
            authorName = "",
            createdAt = "2025-09-13T13:02:58Z",
            isRead = null,
            courseName = "",
        )
        assertEquals("各位同学好", b.plainText)
        // 時間也要轉本地（UTC 13:02 → 本地 21:02）。
        assertTrue(b.createdAtLocal.endsWith("21:02"))
    }
}

/** 討論區主題（只讀；只給「有哪些討論」）。 */
class TronTopicCategoryParserTest {

    private fun el(json: String): JsonElement = Json.parseToJsonElement(json)

    // 真機原文（c=60830，欄位已確認）。
    private val real = """
        {"topic_categories":[
         {"activity":{"can_show_score":true,"end_time":"2026-10-30T01:05:00Z",
            "data":{"description":"","score_percentage":"1.0","enable":true},
            "id":492740,"is_closed":false,"is_started":true,
            "title":"你对凌老师的课程资料满意吗","type":"forum"},
          "activity_id":492740,"created_at":"2026-09-18T01:06:22Z","id":88724,
          "title":"你对凌老师的课程资料满意吗",
          "topics":[{"id":579968,"reply_count":3},{"id":579971,"reply_count":2}]},
         {"activity":{"id":88856,"title":"请给出例题解答过程","is_closed":true},
          "activity_id":493232,"id":88856,"title":"请给出例题解答过程",
          "topics":[{"id":582218,"reply_count":0}]}
        ]}
    """.trimIndent()

    @Test
    fun `解析討論區主題並統計發言與回覆`() {
        val topics = TronTopicCategoryParser.parseList(el(real))
        assertEquals(2, topics.size)
        val a = topics[0]
        assertEquals(88724, a.id)
        assertEquals("你对凌老师的课程资料满意吗", a.title)
        assertEquals(2, a.topicCount)
        assertEquals(5, a.replyCount)
        assertFalse(a.closed)
        assertEquals("进行中", a.statusLabel)
        // 結束時間要轉本地（UTC 01:05 → 本地 09:05）。
        assertTrue(a.endLocal.endsWith("09:05"))
        assertEquals("已结束", topics[1].statusLabel)
    }

    @Test
    fun `activity 為 null 的條目不會讓整門課消失`() {
        // 真機實情：c=51420 有一條 {"activity":null,"activity_id":0,...}。
        val topics = TronTopicCategoryParser.parseList(
            el(
                """{"topic_categories":[
                     {"activity":null,"activity_id":0,"id":1,"title":"没有活动的讨论","topics":[]},
                     {"activity":{"id":9,"title":"有活动"},"id":2,"title":"有活动","topics":[{"id":1,"reply_count":1}]}
                   ]}""",
            ),
        )
        assertEquals("壞的一條不該拖垮好的那條", 2, topics.size)
        assertEquals("没有活动的讨论", topics[0].title)
        assertEquals(0, topics[0].topicCount)
        assertFalse(topics[0].closed)
        assertEquals(1, topics[1].replyCount)
    }

    @Test
    fun `標題退回 activity 的 title`() {
        val topics = TronTopicCategoryParser.parseList(
            el("""{"topic_categories":[{"id":1,"activity":{"title":"只有活动有标题"},"topics":[]}]}"""),
        )
        assertEquals("只有活动有标题", topics[0].title)
    }

    @Test
    fun `外層不對或空回空清單`() {
        assertEquals(0, TronTopicCategoryParser.parseList(el("""{"message":"未找到资源"}""")).size)
        assertEquals(0, TronTopicCategoryParser.parseList(el("""{"topic_categories":[]}""")).size)
        assertEquals(0, TronTopicCategoryParser.parseList(null).size)
    }
}

/** 課程公告複用全校公告的解析器 —— 確認兩者外層都吃。 */
class TronCourseBulletinParserTest {

    @Test
    fun `課程公告的 bulletins 外層可解析`() {
        val list = TronCourseBulletinParser.parseList(
            Json.parseToJsonElement(
                """{"bulletins":[{"id":9587,"title":"開課公告","content":"<p>各位</p>",
                     "created_at":"2025-09-13T13:02:58Z"}]}""",
            ),
        )
        assertEquals(1, list.size)
        assertEquals("開課公告", list[0].title)
        assertEquals("各位", list[0].plainText)
    }
}

/**
 * 課程大綱的 `description` **也是 HTML** —— 真機上抓到的 bug。
 *
 * B12 探測時那門課的大綱是空的（`description` 全是 `""`），
 * 所以「內容是純文字」這個假設一直沒被驗證。
 * 換一門有填大綱的課（毛澤東思想）才看到 `<div>平时成绩满分为50分…</div>`。
 */
class TronOutlineHtmlTest {

    @Test
    fun `真機大綱的 HTML 被去成可讀文字`() {
        // 逐字抄自真機（毛澤東思想概論，「平時成績」欄位）。
        val html = "<div>平时成绩满分为50分，包含线上考核（20分）、线下考核（30分）</div>" +
            "<div>考核与活跃度正相关。不参与者不得分。</div><div><br></div>"
        val field = TronOutlineField(title = "平时成绩", description = html)
        val text = field.plainDescription
        assertFalse("大綱不該留下標籤：$text", text.contains("<"))
        assertTrue(text.contains("平时成绩满分为50分"))
        assertTrue(text.contains("不参与者不得分"))
        // 兩個 <div> 要分行，否則近百字會擠成一坨。
        assertTrue("應該分行，實際：$text", text.lines().size >= 2)
    }

    @Test
    fun `沒填的大綱回空字串`() {
        assertEquals("", TronOutlineField("教学目标", "").plainDescription)
        // 純空白也算沒填（`outlineFilled` 用的是原始 description，兩者要一致）。
        assertEquals("", TronOutlineField("教学目标", "   ").plainDescription)
    }

    @Test
    fun `純文字大綱原樣顯示`() {
        val field = TronOutlineField("教学目标", "掌握链表与树")
        assertEquals("掌握链表与树", field.plainDescription)
    }
}
