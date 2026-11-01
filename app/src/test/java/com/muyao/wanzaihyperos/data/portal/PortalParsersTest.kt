package com.muyao.wanzaihyperos.data.portal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门户响应的解析。
 *
 * 这些用例里的 JSON **逐字使用实测出来的字段名**（`app_name` / `app_url` /
 * `app_color` / `messagegroup` / `noread` / `countmyprehandle` / `createdate` …）。
 * 字段名写错时这里会红 —— 那正是它的价值：`docs/API-PORTAL.md` 记着
 * 「11 个猜错的字段名让整条链路卡了好几轮」。
 */
class PortalParsersTest {

    private fun envelope(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    // ---------------------------------------------------------------------------
    // 目录
    // ---------------------------------------------------------------------------

    private val catalogJson = """
    {"code":200,"success":true,"message":"","data":[
      {"id":1,"groupname":"学工与课程","appinfolist":[
        {"id":156,"app_name":"学在城院","app_url":"https://mobile.hzcu.edu.cn/","app_color":"#108ee9",
         "app_img":"http://iappdd.hzcu.edu.cn/img/156.png","app_introduction":"数智一课堂"},
        {"id":60,"app_name":"我的课程","app_url":"https://cjh52.hzcu.edu.cn/course","app_color":"108ee9"}
      ]},
      {"id":2,"groupname":"生活服务","appinfolist":[
        {"id":156,"app_name":"学在城院（重复）","app_url":"https://mobile.hzcu.edu.cn/"}
      ]},
      {"id":3,"groupname":"空分组","appinfolist":[]}
    ]}
    """

    @Test
    fun `目录解析出分组与应用`() {
        val catalog = PortalParsers.parseCatalog(envelope(catalogJson))

        assertEquals(3, catalog.groups.size)
        assertEquals("学工与课程", catalog.groups[0].name)
        assertEquals(1, catalog.groups[0].id)
        assertEquals(2, catalog.groups[0].apps.size)
        assertEquals(156, catalog.groups[0].apps[0].id)
        assertEquals("学在城院", catalog.groups[0].apps[0].name)
        assertEquals("数智一课堂", catalog.groups[0].apps[0].introduction)
        assertEquals(0xFF108EE9.toInt(), catalog.groups[0].apps[0].colorArgb)
    }

    @Test
    fun `跨分组重复的应用只保留一条`() {
        val catalog = PortalParsers.parseCatalog(envelope(catalogJson))
        // 156 在两个分组里都出现，扁平表里只该有一条。
        assertEquals(2, catalog.allApps.size)
        assertEquals(listOf(156, 60), catalog.allApps.map { it.id })
        assertNull(catalog.byId(999))
        assertEquals("我的课程", catalog.byId(60)?.name)
    }

    /** ★ 分组正确性是验收判据之一：服务端给了分组却没给应用时，界面要显示「空分组」。 */
    @Test
    fun `空分组被保留而不是被丢掉`() {
        val catalog = PortalParsers.parseCatalog(envelope(catalogJson))
        val empty = catalog.groups.first { it.name == "空分组" }
        assertTrue(empty.apps.isEmpty())
    }

    /** ★ 缺 id / app_name / app_url 的记录整条跳过并计数，**绝不补默认值**。 */
    @Test
    fun `缺关键字段的记录被跳过并计数`() {
        val json = """
        {"code":200,"success":true,"data":[
          {"id":1,"groupname":"G","appinfolist":[
            {"app_name":"没有 id","app_url":"https://a/"},
            {"id":2,"app_url":"https://b/"},
            {"id":3,"app_name":"没有 url"},
            {"id":4,"app_name":"好的","app_url":"https://d/"}
          ]}
        ]}
        """
        val catalog = PortalParsers.parseCatalog(envelope(json))

        assertEquals("只有第 4 条能用", 1, catalog.allApps.size)
        assertEquals(3, catalog.skippedEntries)
        assertEquals(4, catalog.allApps[0].id)
    }

    @Test
    fun `只有空白的关键字段也算缺失`() {
        val json = """
        {"code":200,"data":[{"id":1,"groupname":"G","appinfolist":[
          {"id":7,"app_name":"   ","app_url":"https://a/"},
          {"id":8,"app_name":"好","app_url":"  "}
        ]}]}
        """
        val catalog = PortalParsers.parseCatalog(envelope(json))
        assertEquals(0, catalog.allApps.size)
        assertEquals(2, catalog.skippedEntries)
    }

    @Test
    fun `顶层不是数组时返回空目录而不是抛异常`() {
        val catalog = PortalParsers.parseCatalog(envelope("""{"code":200,"data":null}"""))
        assertEquals(PortalCatalog.EMPTY, catalog)
        assertTrue(catalog.groups.isEmpty())
    }

    @Test
    fun `ARGB 颜色解析`() {
        assertEquals(0xFF108EE9.toInt(), PortalParsers.parseHexColor("#108ee9"))
        assertEquals(0xFF108EE9.toInt(), PortalParsers.parseHexColor("108ee9"))
        assertEquals(0xFF108EE9.toInt(), PortalParsers.parseHexColor("#FF108ee9"))
        assertEquals(0x80108EE9.toInt(), PortalParsers.parseHexColor("#80108ee9"))
        assertNull(PortalParsers.parseHexColor(null))
        assertNull(PortalParsers.parseHexColor(""))
        assertNull(PortalParsers.parseHexColor("#12345"))
        assertNull(PortalParsers.parseHexColor("不是颜色"))
    }

    // ---------------------------------------------------------------------------
    // 搜索：★ 数据在顶层 rows
    // ---------------------------------------------------------------------------

    @Test
    fun `搜索结果读顶层 rows 而不是 data`() {
        // 实测：appinfolist 的 data 是 null，数组在顶层 rows。
        val json = """
        {"code":200,"success":true,"message":"","data":null,
         "rows":[{"id":60,"app_name":"我的课程","app_url":"https://cjh52.hzcu.edu.cn/course"}],
         "total":1,"page":1}
        """
        val rows = PortalParsers.parseSearchRows(envelope(json))

        assertEquals(1, rows.size)
        assertEquals(60, rows[0].id)
        assertEquals(PortalParsers.SEARCH_GROUP_NAME, rows[0].groupName)
    }

    @Test
    fun `搜索没有 rows 时返回空列表而不是崩`() {
        assertTrue(PortalParsers.parseSearchRows(envelope("""{"code":200,"data":null}""")).isEmpty())
    }

    // ---------------------------------------------------------------------------
    // 消息：★ 数据在顶层 rows；分组标识是名字
    // ---------------------------------------------------------------------------

    @Test
    fun `消息条目读顶层 rows`() {
        val json = """
        {"code":200,"success":true,"message":"","data":null,
         "rows":[
           {"id":"9001","ticker":"t","title":"讲座通知一","message":"正文一","isread":false,
            "openurl":"https://x.hzcu.edu.cn/m/1","userid":"2021001","appid":"60",
            "messagegroup":"讲座通知","createdate":"2026-10-10 09:00","lastupdatedate":"2026-10-10 09:30",
            "messageid":"m1"}
         ],
         "page":1,"total":9,"allrecords":10,"records":10}
        """
        val rows = PortalParsers.parseMessageRows(envelope(json))

        assertEquals(1, rows.size)
        assertEquals("9001", rows[0].id)
        assertEquals("讲座通知一", rows[0].title)
        assertEquals("讲座通知", rows[0].group)
        assertFalse(rows[0].isRead)
        assertEquals("2026-10-10 09:00", rows[0].createdDate)
        assertEquals("https://x.hzcu.edu.cn/m/1", rows[0].openUrl)
    }

    /** openurl 为空时保持 null：界面据此不画箭头也不给点击（与门户行为一致）。 */
    @Test
    fun `openurl 为空时是 null`() {
        val json = """
        {"code":200,"rows":[{"id":"1","title":"无地址","message":"","isread":true,
          "messagegroup":"G","createdate":"","lastupdatedate":"","openurl":""}]}
        """
        val rows = PortalParsers.parseMessageRows(envelope(json))
        assertNull(rows[0].openUrl)
    }

    /** ★ 实测：total 比返回条数还小，所以**不许**用它判分页。 */
    @Test
    fun `分页按返回条数判断而不是 total`() {
        // 实测原文：第 1 页返回 10 条，信封却报 total=9。
        val paging = PortalParsers.parseMessagePaging(
            envelope("""{"code":200,"page":1,"total":9,"allrecords":10,"records":10}"""),
        )
        assertEquals(9, paging.total)
        assertEquals(10, paging.allRecords)

        val pageSize = PortalParsers.MESSAGE_PAGE_SIZE
        assertTrue(
            "返回满一页 ⇒ 可能还有下一页；照抄 total=9 会误判成没有",
            PortalParsers.hasMoreByReturnedCount(returned = 10, pageSize = pageSize),
        )
        assertFalse(PortalParsers.hasMoreByReturnedCount(returned = 3, pageSize = pageSize))
    }

    @Test
    fun `消息分组解析且标识是分组名`() {
        val json = """
        {"code":200,"success":true,"data":[
          {"messagegroup":"讲座通知","noread":3,"isread":false},
          {"messagegroup":"系统通知","noread":0,"isread":true}
        ]}
        """
        val groups = PortalParsers.parseMessageGroups(envelope(json))

        assertEquals(2, groups.size)
        assertEquals("讲座通知", groups[0].name)
        assertEquals(3, groups[0].unread)
        assertEquals("系统通知", groups[1].name)
    }

    @Test
    fun `没有 messagegroup 名字的分组被跳过`() {
        val json = """{"code":200,"data":[{"noread":3},{"messagegroup":"好的","noread":1}]}"""
        val groups = PortalParsers.parseMessageGroups(envelope(json))
        assertEquals(1, groups.size)
        assertEquals("好的", groups[0].name)
    }

    // ---------------------------------------------------------------------------
    // 新闻
    // ---------------------------------------------------------------------------

    @Test
    fun `新闻分组解析`() {
        val json = """
        {"code":200,"data":[
          {"id":"11","newsgroup":"教育教学","dataid":3},
          {"id":"12","newsgroup":"科学研究","dataid":7}
        ]}
        """
        val groups = PortalParsers.parseNewsGroups(envelope(json))

        assertEquals(2, groups.size)
        assertEquals("教育教学", groups[0].name)
        assertEquals(3, groups[0].dataId)
        assertEquals("11", groups[0].id)
    }

    @Test
    fun `新闻条目用的是 createdate`() {
        val json = """
        {"code":200,"data":[
          {"id":"501","title":"关于开展…的通知","createdate":"2026-10-09"}
        ]}
        """
        val news = PortalParsers.parseNewsList(envelope(json))

        assertEquals(1, news.size)
        assertEquals("关于开展…的通知", news[0].title)
        assertEquals("2026-10-09", news[0].createdDate)
    }

    /**
     * 新闻正文地址：`blink` 优先，`linktitle` 兜底，都没有就是 null。
     *
     * ★ 2026-11-01 从真机落盘的 `listnewslist` 原文核出这两个字段。以前解析只取了
     *   title/createdate/id ⇒ 新闻行整行不可点，一条只能读标题、读不到正文的新闻
     *   等于一条标题。字段名写错这里会红（本文件的职责就是钉字段名）。
     */
    @Test
    fun `新闻有正文地址，blink 优先 linktitle 兜底`() {
        val json = """
        {"code":200,"data":[
          {"id":"338558","title":"射箭队","createdate":"2026-10-28 16:54:10",
           "blink":"http://ccpo.hzcu.edu.cn/art/2026/10/28/art_101_338558.html",
           "linktitle":"https://mp.weixin.qq.com/s/zUWxtTFge0gDZn3fZ-bTuQ"},
          {"id":"338539","title":"学宪法","createdate":"2026-10-28 15:17:52",
           "blink":"","linktitle":"https://mp.weixin.qq.com/s/6qslVCMMbtc6sTWemOg__A"},
          {"id":"338518","title":"毕业大会","createdate":"2026-10-28 10:31:21",
           "blink":"","linktitle":""}
        ]}
        """
        val news = PortalParsers.parseNewsList(envelope(json))

        assertEquals("blink 在的时候用 blink", "http://ccpo.hzcu.edu.cn/art/2026/10/28/art_101_338558.html", news[0].openUrl)
        assertEquals("blink 是空串时用 linktitle", "https://mp.weixin.qq.com/s/6qslVCMMbtc6sTWemOg__A", news[1].openUrl)
        assertNull("两个都没有就是没有，不许编一个地址", news[2].openUrl)
    }

    /** 逐字来自门户 JS：`{ start: 8 * (e - 1) + 1, end: 8 * e }` —— 1 基闭区间。 */
    @Test
    fun `新闻分页是 1 基闭区间且页大小 8`() {
        assertEquals(1..8, PortalParsers.newsPageRange(1))
        assertEquals(9..16, PortalParsers.newsPageRange(2))
        assertEquals(17..24, PortalParsers.newsPageRange(3))
        // 页码非法时按第 1 页处理，不要算出负的行号。
        assertEquals(1..8, PortalParsers.newsPageRange(0))
        assertEquals(1..8, PortalParsers.newsPageRange(-5))
    }

    // ---------------------------------------------------------------------------
    // 待办三计数：★ 数据在 data（与消息族相反）
    // ---------------------------------------------------------------------------

    @Test
    fun `待办三计数从 data 读`() {
        val json = """
        {"code":200,"success":true,"message":"","data":
          {"countmyprehandle":1,"countmyrequestbill":2,"countmyhandled":3,"oagetoadbsl":"x"}}
        """
        val counts = PortalParsers.parseTodoCounts(envelope(json))

        assertEquals(1, counts.pending)
        assertEquals(2, counts.applied)
        assertEquals(3, counts.finished)
        assertFalse(counts.isAllUnknown)
    }

    /** ★ 取不到就是 null（界面显示「—」），**绝不补 0**。 */
    @Test
    fun `待办字段缺失时是 null 而不是 0`() {
        val counts = PortalParsers.parseTodoCounts(
            envelope("""{"code":200,"data":{"countmyprehandle":5}}"""),
        )

        assertEquals(5, counts.pending)
        assertNull("缺的字段绝不能变成 0", counts.applied)
        assertNull(counts.finished)
        assertFalse(counts.isAllUnknown)
    }

    @Test
    fun `待办整个 data 缺失时三个都是 null`() {
        val counts = PortalParsers.parseTodoCounts(envelope("""{"code":200,"data":null}"""))
        assertTrue(counts.isAllUnknown)
        assertNull(counts.pending)
    }

    // ---------------------------------------------------------------------------
    // 未读数：★ 取不到就是 null
    // ---------------------------------------------------------------------------

    @Test
    fun `未读数支持 data 是数字`() {
        assertEquals(12, PortalParsers.parseUnreadCount(envelope("""{"code":200,"data":12}""")))
    }

    @Test
    fun `未读数支持 data 是对象`() {
        assertEquals(
            7,
            PortalParsers.parseUnreadCount(envelope("""{"code":200,"data":{"noread":7}}""")),
        )
    }

    @Test
    fun `未读数支持字段在信封顶层`() {
        assertEquals(
            5,
            PortalParsers.parseUnreadCount(envelope("""{"code":200,"data":null,"total":5}""")),
        )
    }

    /** ★ 认不出来就 null：界面显示「—」而不是「0 条未读」。 */
    @Test
    fun `未读数认不出来时是 null 而不是 0`() {
        assertNull(PortalParsers.parseUnreadCount(envelope("""{"code":200,"data":null}""")))
        assertNull(
            PortalParsers.parseUnreadCount(
                envelope("""{"code":200,"data":{"未知字段":"x"}}"""),
            ),
        )
    }

    // ---------------------------------------------------------------------------
    // tempcode
    // ---------------------------------------------------------------------------

    @Test
    fun `tempcode 是 data 里的裸字符串`() {
        val json = """
        {"code":200,"success":true,"message":"",
         "data":"9a362a1d-42f1-4173-b189-c96f2bfa4fd9"}
        """
        assertEquals(
            "9a362a1d-42f1-4173-b189-c96f2bfa4fd9",
            PortalParsers.parseTempCode(envelope(json)),
        )
    }

    @Test
    fun `tempcode 缺失时是 null`() {
        assertNull(PortalParsers.parseTempCode(envelope("""{"code":200,"data":null}""")))
        assertNull(PortalParsers.parseTempCode(envelope("""{"code":200,"data":""}""")))
    }
}
