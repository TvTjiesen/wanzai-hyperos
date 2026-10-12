package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.portal.PortalParsers
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * 门户六组接口的**活体契约验证**。
 *
 * ### 它要回答的问题
 * `docs/API-PORTAL.md` 里的字段名是历史实测记下来的，但**是否仍然成立**只有真机能答。
 * 这个类把每个接口**各打一次**，记录：状态码、信封顶层键、`data` 的类型、
 * `rows` 的条数、以及**第一个元素的字段名**。字段名不是凭据，所以可以安全落盘。
 *
 * ### 三条纪律
 * 1. **一次运行只登录一次**（companion 里缓存），不给学校服务器添无谓的请求；
 * 2. **每个接口只打一次**，且全部经由 [HzcuRateLimiter] 的全局闸门（≥1s / 同接口 ≥2s）；
 * 3. **只记字段名与条数，绝不记值** —— 消息正文、新闻标题、应用名都不落盘。
 *
 * 没配凭据时整类跳过，所以常规 `testDebugUnitTest` 依然不需要账号。
 */
class PortalLiveContractTest {

    companion object {
        private const val EVIDENCE_NAME = "a3-live-portal-evidence.txt"

        private var client: HzcuClient? = null
        private var api: PortalApi? = null

        private val evidence = StringBuilder()

        /** 登录一次，之后所有用例共用同一个会话。 */
        fun ensureLoggedIn(): PortalApi? {
            api?.let { return it }
            val credentials = HzcuLiveLoginTest.readCredentials() ?: return null
            val session = PortalSession(InMemorySecureStore())
            val newClient = HzcuClient.create(credentials = session)
            val service = HzcuCasService.create(client = newClient, session = session)
            val result = runBlocking { service.login(credentials.first, credentials.second) }

            client = newClient
            evidence.setLength(0)
            evidence.appendLine("# A3 门户接口活体契约证据（只有状态码、键名、条数；无任何值）")
            evidence.appendLine("登录：成功（token 长度 ${result.accessToken.length}，探针 ${result.probeName}）")
            evidence.appendLine()

            return PortalApi(newClient).also { api = it }
        }

        fun record(section: String, lines: List<String>) {
            evidence.appendLine("## $section")
            lines.forEach { evidence.appendLine("  $it") }
            evidence.appendLine()
            HzcuLiveLoginTest.evidenceFile(EVIDENCE_NAME).writeText(evidence.toString())
        }

        /** 顶层键（排序）。**键名不是值**，可以落盘。 */
        fun topKeys(envelope: JsonObject): String = envelope.keys.sorted().joinToString(",")

        fun describe(element: JsonElement?): String = when (element) {
            null -> "null"
            is JsonObject -> "object(${element.size})"
            is kotlinx.serialization.json.JsonArray -> "array(${element.size})"
            else -> "scalar"
        }

        /** 第一个元素的字段名 —— 这是验证解析器的关键信息。 */
        fun firstElementKeys(element: JsonElement?): String {
            val array = (element as? kotlinx.serialization.json.JsonArray) ?: return "非数组"
            val first = array.firstOrNull() as? JsonObject ?: return "空数组"
            return first.keys.sorted().joinToString(",")
        }
    }

    @Before
    fun requireCredentials() {
        // ★ 網絡預檢要在**登入之前**：登入本身就會超時，
        //   等它失敗再跳過等於白白等一輪超時（9 條測試就是 9 輪）。
        LiveNetworkGate.requirePortal()
        val loggedIn = ensureLoggedIn()
        Assume.assumeTrue("未配置凭据，跳过门户接口活体契约测试", loggedIn != null)
    }

    private fun requireApi(): PortalApi = api!!

    // ---------------------------------------------------------------------------
    // 1. 全量应用分组目录
    // ---------------------------------------------------------------------------

    @Test
    fun catalogContract() {
        val response = runBlocking { requireApi().fetchCatalog() }
        val catalog = PortalParsers.parseCatalog(response.raw)
        val dataArray = response.raw["data"]

        val firstGroup = (dataArray as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject

        record(
            "1. 应用目录 listappinfogroup/1",
            listOf(
                "code=${response.code} success=${response.success}",
                "信封顶层键=${topKeys(response.raw)}",
                "data=${describe(response.raw["data"])}",
                "分组数=${catalog.groups.size}",
                "应用总数（跨分组去重后）=${catalog.allApps.size}",
                "跳过条数=${catalog.skippedEntries}",
                "分组字段名=${firstGroup?.keys?.sorted()?.joinToString(",") ?: "无"}",
                "首分组应用字段名=" +
                    firstElementKeys(firstGroup?.get("appinfolist")),
                "分组名样例长度=${catalog.groups.firstOrNull()?.name?.length ?: 0}",
            ),
        )

        assertTrue("目录应当至少有一个分组", catalog.groups.isNotEmpty())
        assertTrue("目录应当至少有一条应用", catalog.allApps.isNotEmpty())
    }

    // ---------------------------------------------------------------------------
    // 2. 未读数
    // ---------------------------------------------------------------------------

    @Test
    fun unreadCountContract() {
        val response = runBlocking { requireApi().fetchUnreadCount() }
        val unread = PortalParsers.parseUnreadCount(response.raw)

        record(
            "2. 未读数 getmessagenoreadcount",
            listOf(
                "code=${response.code} success=${response.success}",
                "信封顶层键=${topKeys(response.raw)}",
                "data=${describe(response.raw["data"])}",
                "解析出的未读数=${unread ?: "null（未知）"}",
            ),
        )

        // 未读数是 0 还是 null 都可能；这里只要求「调用成功」，
        // 因为「未知不得当 0」正是我们的红线，不拿它当断言。
        assertTrue(response.success)
    }

    // ---------------------------------------------------------------------------
    // 3. 待办三计数
    // ---------------------------------------------------------------------------

    @Test
    fun todoCountsContract() {
        val response = runBlocking { requireApi().fetchTodoCounts() }
        val counts = PortalParsers.parseTodoCounts(response.raw)
        val dataObject = response.raw["data"] as? JsonObject

        record(
            "3. 待办三计数 getbillboad",
            listOf(
                "code=${response.code} success=${response.success}",
                "信封顶层键=${topKeys(response.raw)}",
                "data=${describe(response.raw["data"])}",
                "data 字段名=${dataObject?.keys?.sorted()?.joinToString(",") ?: "无"}",
                "pending=${counts.pending ?: "null"} applied=${counts.applied ?: "null"} " +
                    "finished=${counts.finished ?: "null"}",
            ),
        )

        assertTrue(response.success)
    }

    // ---------------------------------------------------------------------------
    // 4. 新闻
    // ---------------------------------------------------------------------------

    @Test
    fun newsContract() {
        val api = requireApi()
        val groupsResponse = runBlocking { api.fetchNewsGroups() }
        val groups = PortalParsers.parseNewsGroups(groupsResponse.raw)
        val groupArray = groupsResponse.raw["data"] as? kotlinx.serialization.json.JsonArray
        val firstGroup = groupArray?.firstOrNull() as? JsonObject

        record(
            "4a. 新闻分组 listnewsgroup",
            listOf(
                "code=${groupsResponse.code} success=${groupsResponse.success}",
                "信封顶层键=${topKeys(groupsResponse.raw)}",
                "data=${describe(groupsResponse.raw["data"])}",
                "分组数=${groups.size}",
                "分组字段名=${firstGroup?.keys?.sorted()?.joinToString(",") ?: "无"}",
                "解析出的 dataid 值=${groups.mapNotNull { it.dataId }}",
            ),
        )

        assertTrue("新闻分组不该为空", groups.isNotEmpty())

        // 只对**第一个有 dataid 的分组**取一页，避免每个分组打一次（QPS 红线）。
        val target = groups.firstOrNull { it.dataId != null }
        Assume.assumeTrue("没有任何分组带 dataid，无法取新闻列表", target != null)
        val chosen = target!!
        val cataId = chosen.dataId!!

        val listResponse = runBlocking { api.fetchNewsList(cataId = cataId, page = 1) }
        val news = PortalParsers.parseNewsList(listResponse.raw)
        val listArray = listResponse.raw["data"] as? kotlinx.serialization.json.JsonArray
        val firstItem = listArray?.firstOrNull() as? JsonObject

        record(
            "4b. 新闻列表 listnewslist（cataid=$cataId）",
            listOf(
                "code=${listResponse.code} success=${listResponse.success}",
                "信封顶层键=${topKeys(listResponse.raw)}",
                "data=${describe(listResponse.raw["data"])}",
                "解析出的条数=${news.size}",
                "条目字段名=${firstItem?.keys?.sorted()?.joinToString(",") ?: "无"}",
                "用 start/end 分页请求成功=${listResponse.success}",
            ),
        )

        assertTrue(listResponse.success)
    }

    // ---------------------------------------------------------------------------
    // 5. 应用搜索
    // ---------------------------------------------------------------------------

    @Test
    fun searchContract() {
        val response = runBlocking { requireApi().searchApps(keyword = "课程") }
        val rows = PortalParsers.parseSearchRows(response.raw)
        val rowArray = response.raw["rows"] as? kotlinx.serialization.json.JsonArray
        val firstRow = rowArray?.firstOrNull() as? JsonObject

        record(
            "5. 应用搜索 appinfolist（JSON 包体）",
            listOf(
                "code=${response.code} success=${response.success}",
                "信封顶层键=${topKeys(response.raw)}",
                "data=${describe(response.raw["data"])}",
                "rows=${describe(response.raw["rows"])}",
                "解析出的条数=${rows.size}",
                "条目前 8 个字段名（部分）=${firstRow?.keys?.sorted()?.take(8)?.joinToString(",") ?: "无"}",
            ),
        )

        assertTrue("搜索接口应当返回成功", response.success)
    }

    // ---------------------------------------------------------------------------
    // 6. 消息
    // ---------------------------------------------------------------------------

    @Test
    fun messageContract() {
        val api = requireApi()
        val groupsResponse = runBlocking { api.fetchMessageGroups() }
        val groups = PortalParsers.parseMessageGroups(groupsResponse.raw)
        val groupArray = groupsResponse.raw["data"] as? kotlinx.serialization.json.JsonArray
        val firstGroup = groupArray?.firstOrNull() as? JsonObject

        record(
            "6a. 消息分组 listmessagegroup（form 包体）",
            listOf(
                "code=${groupsResponse.code} success=${groupsResponse.success}",
                "信封顶层键=${topKeys(groupsResponse.raw)}",
                "data=${describe(groupsResponse.raw["data"])}",
                "分组数=${groups.size}",
                "分组字段名=${firstGroup?.keys?.sorted()?.joinToString(",") ?: "无"}",
                "分组名长度=${groups.map { it.name.length }}",
                "未读数=${groups.map { it.unread }}",
            ),
        )

        assertTrue("消息分组不该为空", groups.isNotEmpty())

        // 只取第一个分组的一页。
        val targetGroup = groups.first()
        val listResponse = runBlocking { api.fetchMessages(group = targetGroup.name, page = 1) }
        val items = PortalParsers.parseMessageRows(listResponse.raw)
        val paging = PortalParsers.parseMessagePaging(listResponse.raw)
        val rowArray = listResponse.raw["rows"] as? kotlinx.serialization.json.JsonArray
        val firstItem = rowArray?.firstOrNull() as? JsonObject

        record(
            "6b. 消息列表 listmessage（JSON 包体，分组名长度 ${targetGroup.name.length}）",
            listOf(
                "code=${listResponse.code} success=${listResponse.success}",
                "信封顶层键=${topKeys(listResponse.raw)}",
                "data=${describe(listResponse.raw["data"])}（预期为 null）",
                "rows=${describe(listResponse.raw["rows"])}",
                "解析出的条数=${items.size}",
                "条目字段名=${firstItem?.keys?.sorted()?.joinToString(",") ?: "无"}",
                "page=${paging.page ?: "null"} total=${paging.total ?: "null"} " +
                    "allrecords=${paging.allRecords ?: "null"} records=${paging.records ?: "null"}",
                "按返回条数判断还有下一页=" +
                    PortalParsers.hasMoreByReturnedCount(items.size, PortalParsers.MESSAGE_PAGE_SIZE),
                "openurl 为空（应不画箭头）的条数=${items.count { it.openUrl == null }}",
            ),
        )

        assertTrue("消息列表应当返回成功", listResponse.success)
    }

    // ---------------------------------------------------------------------------
    // 7. 一次性 tempcode（A3 第 7 项：子系统 SSO 的钥匙）
    // ---------------------------------------------------------------------------

    @Test
    fun tempCodeContract() {
        val code = runBlocking { requireApi().issueTempCode() }

        // 只记长度与形态，**不记值**：tempcode 是一次性凭据。
        val looksUuid = code != null && code.matches(
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
        )

        record(
            "7. 一次性 tempcode gettemporarycode（form 包体）",
            listOf(
                "取到临时码=${code != null}",
                "长度=${code?.length ?: 0}",
                "是 UUID 形态=$looksUuid",
                "注意：一次性，不可缓存复用（每次进子系统都要重新换）",
            ),
        )

        assertTrue("tempcode 是子系统 SSO 的前提，不该取不到", code != null)
        assertTrue("tempcode 应当是 UUID 形态（实测如此）", looksUuid)
    }

    /**
     * 再取一次，确认它**确实是一次性的**（两次的值不同）。
     *
     * 这条很重要：如果服务端每次都回同一个值，那「一次性」这个前提就是错的，
     * 缓存复用会变成一个看似安全的优化 —— 而它会在子系统侧静默失效。
     */
    @Test
    fun tempCodeIsActuallyOneTime() {
        val api = requireApi()
        val first = runBlocking { api.issueTempCode() }
        val second = runBlocking { api.issueTempCode() }

        record(
            "7b. tempcode 一次性验证",
            listOf(
                "两次都取到=${first != null && second != null}",
                "两次长度=${first?.length ?: 0} / ${second?.length ?: 0}",
                "两次值不同=${first != second}",
            ),
        )

        assertTrue(first != null && second != null)
        assertTrue("两次取到同一个码，说明「一次性」的前提不成立", first != second)
    }

    // ---------------------------------------------------------------------------
    // 8. 常用应用（首页宫格）—— 形状此前从未验证过
    // ---------------------------------------------------------------------------

    @Test
    fun frequentAppsContract() {
        val response = runBlocking { requireApi().fetchFrequentApps() }

        record(
            "8. 常用应用 listappinfoex（form 包体）",
            listOf(
                "code=${response.code} success=${response.success}",
                "信封顶层键=${topKeys(response.raw)}",
                "data=${describe(response.raw["data"])}",
                "data 首元素字段名=${firstElementKeys(response.raw["data"])}",
                "data 元素个数=${(response.raw["data"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0}",
                "rows=${describe(response.raw["rows"])}",
            ),
        )

        // 「常用应用」可能是空的（用户没设过常用），所以只断言调用成功。
        assertTrue(response.success)
    }
}
