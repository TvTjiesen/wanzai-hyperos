package com.muyao.wanzaihyperos.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 门户业务接口。**所有门户调用的唯一入口**（除 CAS 登录外）。
 *
 * 每个方法只做一件事：**按该接口实测的包体类型与参数名**发出请求，返回拆过壳的
 * [HzcuResponse]。解析交给 `data.portal.PortalParsers`，这样「怎么发」与
 * 「怎么读」各自可测。
 *
 * ### ★ 包体类型是**逐接口**定的，不能统一
 *
 * 这是实测踩出来的坑（`docs/API-PORTAL.md`）：同一批接口里两种类型并存。
 *
 * | 接口 | 包体 | 依据 |
 * |---|---|---|
 * | `listappinfogroup/1` | **form** | 实测通过 |
 * | `listappinfoex` | **form** | 实测通过 |
 * | `ummessage/getmessagenoreadcount` | **form** | 实测通过 |
 * | `ydmhbilllist/getbillboad` | **form** | 门户 JS **显式**写了 contenttype |
 * | `newsapi/listnewsgroup` | **form** | 实测通过 |
 * | `ummessage/listmessagegroup` | **form** | 实测通过 |
 * | `outh/gettemporarycode` | **form** | 实测通过 |
 * | `app/exclude/appinfolist` | **JSON** | 实测：form → `success=false` |
 * | `newsapi/listnewslist` | **JSON** | 实测：form → `400 Content type … not supported` |
 * | `ummessage/listmessage` | **JSON** | 实测：form → 报错 |
 *
 * **为什么不能只看门户 JS**：`listnewslist` 与 `listmessage` 在 JS 里都**没写**
 * contenttype，但它们的默认**不是** form。所以判据只能是**按接口实测**。
 */
class PortalApi(private val client: HzcuClient) {

    // ---------------------------------------------------------------------------
    // 目录
    // ---------------------------------------------------------------------------

    /** 全量应用分组目录。**form**。 */
    suspend fun fetchCatalog(): HzcuResponse = client.portalPost(
        path = HzcuEndpoints.PORTAL_APP_GROUP_PATH,
        fields = emptyMap(),
        context = "应用目录",
    )

    /** 我的常用应用（首页宫格）。**form**。 */
    suspend fun fetchFrequentApps(): HzcuResponse = client.portalPost(
        path = HzcuEndpoints.PORTAL_FREQUENT_APPS_PATH,
        fields = emptyMap(),
        context = "常用应用",
    )

    /**
     * 应用搜索。**JSON**。
     *
     * 参数名逐字来自门户 `pages-search-SearchPage.js`：
     * `{ appName, devicetype: 1, pageindex: 1, pagesize: 20 }`。
     *
     * ⚠️ 服务端的 `appName` 是**未转义的 LIKE 匹配** —— 搜 `%` 会命中全部。
     * 这不是我们的 bug，但界面上不该让用户以为那是「模糊搜索」。
     */
    suspend fun searchApps(
        keyword: String,
        pageIndex: Int = 1,
        pageSize: Int = 20,
    ): HzcuResponse = client.portalPost(
        path = HzcuEndpoints.PORTAL_APP_SEARCH_PATH,
        jsonBody = buildJsonObject {
            put("appName", keyword)
            put("devicetype", 1)
            put("pageindex", pageIndex)
            put("pagesize", pageSize)
        },
        context = "应用搜索",
    )

    // ---------------------------------------------------------------------------
    // 计数
    // ---------------------------------------------------------------------------

    /** 未读消息数。**form**。 */
    suspend fun fetchUnreadCount(): HzcuResponse = client.portalPost(
        path = HzcuEndpoints.PORTAL_UNREAD_COUNT_PATH,
        fields = emptyMap(),
        context = "未读数",
    )

    /**
     * 首页待办三计数。**form**。
     *
     * 门户自己把结果缓存在 `bill_boad`，我们对应 15 分钟 TTL（见 `RefreshPolicy`）。
     */
    suspend fun fetchTodoCounts(): HzcuResponse = client.portalPost(
        path = HzcuEndpoints.PORTAL_TODO_COUNT_PATH,
        fields = emptyMap(),
        context = "待办计数",
    )

    // ---------------------------------------------------------------------------
    // 新闻
    // ---------------------------------------------------------------------------

    /** 新闻分组。**form**。 */
    suspend fun fetchNewsGroups(): HzcuResponse = client.portalPost(
        path = HzcuEndpoints.PORTAL_NEWS_GROUP_PATH,
        fields = emptyMap(),
        context = "新闻分组",
    )

    /**
     * 新闻列表。**JSON**。
     *
     * ★ 参数是 **`start` / `end`**（1 基闭区间行号）+ **`cataid`**，
     * **不是** page/pageSize，也**不是** `dataid`/`groupid`/`columnId`
     * （这几个实测都无效）。`cataid` 取自分组条目的 **`dataid`**。
     */
    suspend fun fetchNewsList(
        cataId: Int,
        page: Int,
        pageSize: Int = NEWS_PAGE_SIZE,
    ): HzcuResponse {
        val range = com.muyao.wanzaihyperos.data.portal.PortalParsers
            .newsPageRange(page, pageSize)
        return client.portalPost(
            path = HzcuEndpoints.PORTAL_NEWS_LIST_PATH,
            jsonBody = buildJsonObject {
                put("start", range.first)
                put("end", range.last)
                put("cataid", cataId)
            },
            context = "新闻列表",
        )
    }

    // ---------------------------------------------------------------------------
    // 消息
    // ---------------------------------------------------------------------------

    /** 消息分组。**form**，可带 `pageindex` / `pagesize`。 */
    suspend fun fetchMessageGroups(
        pageIndex: Int = 1,
        pageSize: Int = 20,
    ): HzcuResponse = client.portalPost(
        path = HzcuEndpoints.PORTAL_MESSAGE_GROUP_PATH,
        fields = mapOf(
            "pageindex" to pageIndex.toString(),
            "pagesize" to pageSize.toString(),
        ),
        context = "消息分组",
    )

    /**
     * 某个分组下的消息。**JSON**。
     *
     * 参数名逐字来自门户 `pages-message-MessageAppListPage.js`：
     * `var a = { messagegroup: e, page: n, rows: 10 }`。
     *
     * ⚠️ [group] 是**分组名本身**（不是数字 id）。
     * 「全部消息」该怎么表达**尚未实测** —— 门户 JS 只传具体分组名。
     * 所以这里要求明确的分组名，不发明一个 `""` 表示全部
     * （凭空发明参数正是「猜字段名」那类错误的来源）。
     */
    suspend fun fetchMessages(
        group: String,
        page: Int,
        pageSize: Int = MESSAGE_ROWS,
    ): HzcuResponse = client.portalPost(
        path = HzcuEndpoints.PORTAL_MESSAGE_LIST_PATH,
        jsonBody = buildJsonObject {
            put("messagegroup", group)
            put("page", page)
            put("rows", pageSize)
        },
        context = "消息列表",
    )

    /**
     * 单条标已读。
     *
     * 形态照门户原文：POST、路径带 id、没有请求体 —— 走 form 分支的空 fields
     * （[HzcuClient.portalPost] 里 fields/jsonBody 二选一，空 map 就是「无体」）。
     * 失败时 unwrap 会抛 HzcuException，由调用方接。
     *
     * @return false 表示 id 不成立、**一个请求都没发**（调用方不要把这种情况当成「已读」）。
     */
    suspend fun markMessageRead(id: String): Boolean {
        val path = HzcuEndpoints.portalMessageReadPath(id) ?: return false
        client.portalPost(path = path, fields = emptyMap(), context = "消息已读")
        return true
    }

    /** 消息接口的 `rows` 参数值。门户 JS 里就写死 10。 */
    companion object {
        const val MESSAGE_ROWS = 10
        const val NEWS_PAGE_SIZE = 8

        /**
         * 「我的事務」每頁條數。
         *
         * 門戶源碼裡寫死 `rows: 10`，且用 `10 == e.length` 判斷「還有下一頁」
         * （見 `MyBusynessPage` 的 `getAppList`）。照抄它是**刻意的**：
         * 改成別的數，那個判據就跟著失效 —— 而它正是「有沒有更多」的唯一線索。
         */
        const val BILL_PAGE_SIZE = 10
    }

    /** 把一个 JSON 包体原样发出去（给需要额外参数的调用方留的口子）。 */
    internal suspend fun postJson(path: String, body: JsonObject, context: String): HzcuResponse =
        client.portalPost(path = path, jsonBody = body, context = context)

    // ---------------------------------------------------------------------------
    // 我的事務（B3）
    // ---------------------------------------------------------------------------

    /**
     * 拉「我的事務」某個清單的一頁。
     *
     * ### ★ 必須用 JSON 包體（真機實測，2026-10-11）
     *
     * 三個接口用 **form 一律回 `code 400`**，換 JSON 立刻 `code 200`。
     * 這與 A3 的 `listmessage`/`listnewslist` 是同一個坑
     * （API-PORTAL §1.3 已記過一次）。所以參數照門戶源碼給，
     * 但**包體類型不能照門戶源碼猜** —— 那裡的 `sendRequest` 封裝決定了實際編碼，
     * 只有實測才準。
     *
     * ### 參數（逐字來自門戶 `MyBusynessPage` 的源碼）
     *
     * ```js
     * { appid: i, page: t, rows: 10 }   // 三個接口完全相同
     * ```
     * `appid` 可以傳空串 —— 門戶的「全部」那一項就是 `{ id: "", app_name: "全部" }`。
     *
     * @param bucket 三個清單之一（路徑與標籤都綁在它身上）
     * @param page 1 起
     */
    suspend fun fetchBills(
        bucket: com.muyao.wanzaihyperos.data.portal.PortalBillBucket,
        page: Int = 1,
        appId: String = "",
    ): HzcuResponse = postJson(
        path = bucket.path,
        body = buildJsonObject {
            put("appid", appId)
            put("page", page)
            put("rows", BILL_PAGE_SIZE)
        },
        context = "我的事务-${bucket.label}",
    )

    // ---------------------------------------------------------------------------
    // 跨子系统 SSO
    // ---------------------------------------------------------------------------

    /**
     * 签发**一次性** tempcode。**form**。
     *
     * ★ 一次性的，**每次进子系统都要重新换，不可缓存复用**。
     * 所以这里不提供任何缓存，调用方也拿不到「上次那个」。
     *
     * 返回 null 表示服务端没给码 —— 调用方应当拒绝打开子系统，
     * 而不是拼一个没有 `tempcode` 的地址（那会落到子系统的登录页）。
     */
    suspend fun issueTempCode(): String? {
        val response = client.portalPost(
            path = HzcuEndpoints.PORTAL_TEMPORARY_CODE_PATH,
            fields = emptyMap(),
            context = "临时码",
        )
        return com.muyao.wanzaihyperos.data.portal.PortalParsers.parseTempCode(response.raw)
    }
}
