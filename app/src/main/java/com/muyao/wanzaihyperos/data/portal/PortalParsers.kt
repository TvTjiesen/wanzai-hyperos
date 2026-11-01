package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asBoolOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 门户响应的**解析器**。全部是纯函数：输入拆过壳的信封，输出领域模型。
 *
 * ### 为什么集中在一个文件里
 * 服务端字段名是**实测得来**的，而且极易猜错 ——
 * `docs/API-PORTAL.md` 记着「11 个猜错的字段名让整条链路卡了好几轮」。
 * 把字段名收在一处，将来对不上时只需要看这一个文件。
 *
 * ### 三条贯穿全文件的纪律
 *
 * 1. **包体类型按接口选，不信 JS 默认值。** 这里只解析，包体类型由 `PortalApi` 决定；
 *    实测：`listmessage` / `listnewslist` / `appinfolist` 必须 JSON，
 *    而 `listappinfogroup` / `listnewsgroup` / `getbillboad` / `gettemporarycode` 吃 form。
 * 2. **数组位置分两族。** `listmessage` / `appinfolist` 的数据在**信封顶层的 `rows`**，
 *    `data` 是 `null`；目录 / 新闻 / 待办在 `data` 里。
 *    读错的那一族会**静默拿到 null**，只表现为「没有内容」。
 * 3. **取不到就 null，绝不补 0。** 计数类字段可空是刻意的：
 *    界面显示「—」比显示「0 条」诚实。
 */
object PortalParsers {

    // ---------------------------------------------------------------------------
    // 1. 全量应用分组目录
    //    POST /api/app/exclude/listappinfogroup/1 (form) → data 是数组
    // ---------------------------------------------------------------------------

    /**
     * 解析目录。
     *
     * 顶层不是数组时返回 [PortalCatalog.EMPTY] 而不是抛异常 ——
     * 页面据此走离线兜底（种子数据），比整页崩掉合理。
     */
    fun parseCatalog(envelope: JsonObject): PortalCatalog =
        parseCatalogData(envelope["data"])

    fun parseCatalogData(data: JsonElement?): PortalCatalog {
        val rawGroups = data.asArrayOrNull() ?: return PortalCatalog.EMPTY
        val groups = mutableListOf<PortalAppGroup>()
        val flat = mutableListOf<PortalAppEntry>()
        val seenIds = mutableSetOf<Int>()
        var skipped = 0

        for (rawGroup in rawGroups) {
            // 分组本身不是对象就跳过；注意这里**不算** skipped ——
            // skipped 的语义是「本该是一条应用却用不了」，不是「结构不对」。
            val group = rawGroup.asObjectOrNull() ?: continue
            val groupName = group["groupname"].text() ?: ""
            val groupId = group["id"].asIntOrNull() ?: -1

            val apps = mutableListOf<PortalAppEntry>()
            val rawApps = group["appinfolist"].asArrayOrNull()
            if (rawApps != null) {
                for (rawApp in rawApps) {
                    val appObject = rawApp.asObjectOrNull()
                    if (appObject == null) {
                        skipped++
                        continue
                    }
                    val entry = parseApp(appObject, groupName)
                    if (entry == null) {
                        skipped++
                        continue
                    }
                    apps += entry
                    // 跨分组去重：门户历史上出现过同一条应用落在两个分组里。
                    if (seenIds.add(entry.id)) flat += entry
                }
            }

            // ★ 空分组也保留：分组正确性是验收判据之一，
            //   服务端给了分组却没给应用时，界面应该显示「空分组」而不是悄悄少一组。
            groups += PortalAppGroup(id = groupId, name = groupName, apps = apps)
        }

        return PortalCatalog(groups = groups, allApps = flat, skippedEntries = skipped)
    }

    /**
     * 一条应用。**`id` + `app_name` + `app_url` 三者缺一不可**，缺了返回 null。
     *
     * 不补默认值的理由写在 [PortalAppEntry] 上：造出一个假入口比少一条更糟。
     */
    private fun parseApp(raw: JsonObject, groupName: String): PortalAppEntry? {
        val id = raw["id"].asIntOrNull() ?: return null
        val name = raw["app_name"].text() ?: return null
        val url = raw["app_url"].text() ?: return null
        return PortalAppEntry(
            id = id,
            name = name,
            url = url,
            groupName = groupName,
            introduction = raw["app_introduction"].text(),
            colorArgb = parseHexColor(raw["app_color"].text()),
            iconUrl = raw["app_img"].text(),
        )
    }

    // ---------------------------------------------------------------------------
    // 1b. 常用应用（首页宫格）
    //     POST /api/app/listappinfoex (form) → data 是数组
    // ---------------------------------------------------------------------------

    /**
     * 常用应用。**实测（2026-10-11）**：`data` 是 14 条数组，
     * 字段比目录更精简 —— `app_color, app_img, app_name, app_url, apptype, id`
     * （**没有** `app_introduction` / `app_proxy_url`）。
     *
     * 因为字段是目录那套的子集，直接复用 [parseApp] 的「三者缺一不可」校验，
     * 免得两条路径各写一份、日后只改一处。
     */
    fun parseFrequentApps(envelope: JsonObject): List<PortalAppEntry> {
        val raw = envelope["data"].asArrayOrNull() ?: return emptyList()
        val out = mutableListOf<PortalAppEntry>()
        for (element in raw) {
            val item = element.asObjectOrNull() ?: continue
            parseApp(item, FREQUENT_GROUP_NAME)?.let { out += it }
        }
        return out
    }

    /** 常用应用在界面上的来源标签。 */
    const val FREQUENT_GROUP_NAME = "常用应用"

    // ---------------------------------------------------------------------------
    // 2. 应用搜索
    //    POST /api/app/exclude/appinfolist (JSON) → **顶层 rows**
    // ---------------------------------------------------------------------------

    /** 搜索结果**没有分组**，但磁贴与详情页都要一个来源标签，所以给一个固定的。 */
    const val SEARCH_GROUP_NAME = "搜索结果"

    fun parseSearchRows(envelope: JsonObject): List<PortalAppEntry> {
        val rows = envelope["rows"].asArrayOrNull() ?: return emptyList()
        val out = mutableListOf<PortalAppEntry>()
        for (raw in rows) {
            val object0 = raw.asObjectOrNull() ?: continue
            parseApp(object0, SEARCH_GROUP_NAME)?.let { out += it }
        }
        return out
    }

    // ---------------------------------------------------------------------------
    // 3. 未读数
    //    POST /api/app/ummessage/getmessagenoreadcount (form)
    // ---------------------------------------------------------------------------

    /**
     * 未读消息数。**取不到返回 null**（界面显示「—」，绝不显示 0）。
     *
     * ⚠️ 这个接口的**响应形状尚未实测**（`docs/API-PORTAL.md` 只记了它是未读核心，
     * 没记包体结构）。所以这里按几种最可能的形态宽松地找，找不到就 null ——
     * 与「猜一个字段名然后静默拿到 null」相比，这样至少是**显式**的未知。
     * A3 真机联调的第一件事就是把这个形状记下来并收窄这里。
     */
    fun parseUnreadCount(envelope: JsonObject): Int? {
        // 形态 A：data 直接是数字。
        envelope["data"].asIntOrNull()?.let { return it }
        // 形态 B：data 是对象，里面有计数字段。
        envelope["data"].asObjectOrNull()?.let { data ->
            for (key in UNREAD_COUNT_KEYS) {
                data[key].asIntOrNull()?.let { return it }
            }
        }
        // 形态 C：计数在信封顶层。
        for (key in UNREAD_COUNT_KEYS) {
            envelope[key].asIntOrNull()?.let { return it }
        }
        return null
    }

    private val UNREAD_COUNT_KEYS = listOf("noread", "noRead", "count", "total", "messagecount")

    // ---------------------------------------------------------------------------
    // 4. 待办三计数
    //    POST /api/app/ydmhbilllist/getbillboad (form) → **data** 里有三个字段
    // ---------------------------------------------------------------------------

    /**
     * 待办三计数。字段名逐字来自门户自己的 JS：
     * `countmyprehandle` / `countmyrequestbill` / `countmyhandled`。
     *
     * ⚠️ 与消息那一族**不同**：数据在 `data`、包体是 form。别互相套用。
     */
    fun parseTodoCounts(envelope: JsonObject): PortalTodoCounts {
        val data = envelope["data"].asObjectOrNull() ?: return PortalTodoCounts()
        return PortalTodoCounts(
            pending = data["countmyprehandle"].asIntOrNull(),
            applied = data["countmyrequestbill"].asIntOrNull(),
            finished = data["countmyhandled"].asIntOrNull(),
        )
    }

    // ---------------------------------------------------------------------------
    // 5. 新闻
    //    POST /api/newsapi/listnewsgroup (form)  → data 是数组
    //    POST /api/newsapi/listnewslist  (JSON) → data 是数组（参数 start/end/cataid）
    // ---------------------------------------------------------------------------

    /** 新闻分组。服务端字段：`id`(字符串) / `newsgroup` / `dataid`。 */
    fun parseNewsGroups(envelope: JsonObject): List<PortalNewsGroup> {
        val raw = envelope["data"].asArrayOrNull() ?: return emptyList()
        val out = mutableListOf<PortalNewsGroup>()
        for (element in raw) {
            val item = element.asObjectOrNull() ?: continue
            val name = item["newsgroup"].text() ?: continue
            out += PortalNewsGroup(
                id = item["id"].text() ?: name,
                name = name,
                dataId = item["dataid"].asIntOrNull(),
            )
        }
        return out
    }

    /**
     * 新闻条目。服务端字段：`title` / `createdate` / `id`。
     *
     * 注意渲染用的是 `createdate`（不是 `lastupdatedate` 之类）。
     */
    fun parseNewsList(envelope: JsonObject): List<PortalNewsItem> {
        val raw = envelope["data"].asArrayOrNull() ?: return emptyList()
        val out = mutableListOf<PortalNewsItem>()
        for (element in raw) {
            val item = element.asObjectOrNull() ?: continue
            val title = item["title"].text() ?: continue
            out += PortalNewsItem(
                id = item["id"].text() ?: title,
                title = title,
                createdDate = item["createdate"].text() ?: "",
                // 正文页：`blink` 是校网正文，`linktitle` 是公众号版（部分为空）。
                // 两个都读不到就留 null —— 让界面去决定「这行不给点」，不是编一个地址。
                openUrl = item["blink"].text() ?: item["linktitle"].text(),
            )
        }
        return out
    }

    /**
     * 新闻分页参数：**1 基闭区间行号**，页大小 8（**不是** page/pageSize）。
     *
     * 逐字来自门户 `pages-main-HomePage.js`：
     * `{ start: 8 * (e - 1) + 1, end: 8 * e, cataid: a }`。
     */
    fun newsPageRange(page: Int, pageSize: Int = NEWS_PAGE_SIZE): IntRange {
        val safePage = page.coerceAtLeast(1)
        val start = pageSize * (safePage - 1) + 1
        return start..(pageSize * safePage)
    }

    const val NEWS_PAGE_SIZE = 8

    // ---------------------------------------------------------------------------
    // 6. 消息
    //    POST /api/app/ummessage/listmessagegroup (form, 可带 pageindex/pagesize)
    //    POST /api/app/ummessage/listmessage      (JSON) → **顶层 rows**
    // ---------------------------------------------------------------------------

    /** 消息分组。服务端字段：`messagegroup` / `noread` / `isread`。 */
    fun parseMessageGroups(envelope: JsonObject): List<PortalMessageGroup> {
        val raw = envelope["data"].asArrayOrNull() ?: return emptyList()
        val out = mutableListOf<PortalMessageGroup>()
        for (element in raw) {
            val item = element.asObjectOrNull() ?: continue
            // ⚠️ 分组的标识就是这个名字本身，不是数字 id。
            val name = item["messagegroup"].text() ?: continue
            out += PortalMessageGroup(
                name = name,
                unread = item["noread"].asIntOrNull() ?: 0,
                isRead = item["isread"].asBoolOrNull(),
            )
        }
        return out
    }

    /** 消息分页信息。服务端字段：`page` / `total` / `allrecords` / `records`。 */
    data class MessagePaging(
        val page: Int?,
        /** ⚠️ **不可靠**：实测见过 `total=9` 而实际返回 10 行。**不许用它判分页**。 */
        val total: Int?,
        val allRecords: Int?,
        val records: Int?,
    )

    fun parseMessagePaging(envelope: JsonObject): MessagePaging = MessagePaging(
        page = envelope["page"].asIntOrNull(),
        total = envelope["total"].asIntOrNull(),
        allRecords = envelope["allrecords"].asIntOrNull(),
        records = envelope["records"].asIntOrNull(),
    )

    /**
     * 消息条目。**读信封顶层的 `rows`**，不是 `data`。
     *
     * ★ 实测：`listmessage` 的 `data` 是 `null`，数组在顶层 `rows`。
     * 用 `data` 读会**静默拿到 null**，只表现为「没有消息」。
     */
    fun parseMessageRows(envelope: JsonObject): List<PortalMessageItem> {
        val rows = envelope["rows"].asArrayOrNull() ?: return emptyList()
        val out = mutableListOf<PortalMessageItem>()
        for (element in rows) {
            val item = element.asObjectOrNull() ?: continue
            val id = item["id"].text() ?: continue
            val title = item["title"].text() ?: continue
            out += PortalMessageItem(
                id = id,
                title = title,
                message = item["message"].text() ?: "",
                isRead = item["isread"].asBoolOrNull() ?: false,
                group = item["messagegroup"].text() ?: "",
                createdDate = item["createdate"].text() ?: "",
                lastUpdateDate = item["lastupdatedate"].text() ?: "",
                // 空 openurl 保持 null：界面据此**不画箭头也不给点击**（与门户一致）。
                openUrl = item["openurl"].text(),
            )
        }
        return out
    }

    const val MESSAGE_PAGE_SIZE = 10

    /**
     * 有没有下一页 —— **按本次返回条数判，不用 `total`**。
     *
     * ★ 实测（2026-10-10）：「讲座通知」第 1 页返回 **10 条**，信封里却报 `total=9`
     * —— total 比返回条数还小。照抄 total 会多打无效请求，或者反过来把还有的一页
     * 误判成没有。
     */
    fun hasMoreByReturnedCount(returned: Int, pageSize: Int): Boolean = returned >= pageSize

    // ---------------------------------------------------------------------------
    // 7. 一次性 tempcode
    //    POST /api/outh/gettemporarycode (form) → data 是 UUID 字符串
    // ---------------------------------------------------------------------------

    /**
     * 一次性临时码。
     *
     * ★ 实测 `data` 是**裸字符串 UUID**（`"9a362a1d-42f1-4173-b189-c96f2bfa4fd9"`），
     * 不是对象。**一次性的，不可缓存复用。**
     */
    fun parseTempCode(envelope: JsonObject): String? =
        envelope["data"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    // ---------------------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------------------

    /**
     * `#108ee9` / `108ee9` / `#FF108ee9` → ARGB int。解析不了返回 null。
     *
     * 门户给的是 6 位 RGB（大量 `#108ee9`），必须补不透明的 `0xFF` 前缀；
     * 若哪天给 8 位则按 AARRGGBB 原样用。
     */
    fun parseHexColor(raw: String?): Int? {
        if (raw == null) return null
        var hex = raw.trim()
        if (hex.startsWith("#")) hex = hex.substring(1)
        if (hex.isEmpty()) return null
        if (hex.length == 6) hex = "FF$hex"
        if (hex.length != 8) return null
        return hex.toLongOrNull(16)?.toInt()
    }

    /**
     * 文本读取：字符串原样（trim），数字/布尔取字面量。**空串按「没有」处理。**
     *
     * `asStringOrNull` 会过滤 null，但 `" "` 这种只有空白的值必须在这里挡掉 ——
     * 否则 `app_url = " "` 会被当成一个有效地址，点进去是白屏。
     */
    private fun JsonElement?.text(): String? =
        this.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
