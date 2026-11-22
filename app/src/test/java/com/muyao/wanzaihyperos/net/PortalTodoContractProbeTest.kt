package com.muyao.wanzaihyperos.net

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.put
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * B3 活體契約探測：門戶「我的待辦」三個接口的真實回應結構。
 *
 * ### 為什麼是活體測試
 *
 * 這三個接口（`listmybill` / `listmyprebill` / `listmyfinishbill`）在靜態提取的
 * 契約清單裡只有**路徑**，沒有包體欄位與回應結構 —— 而那正是 B3 要建模的東西。
 * 靜態看不出來，只能實測。
 *
 * ### 隱私紀律（本測試的核心約束）
 *
 * 回應可能含真實的事項標題、時間、金額。所以本測試**只斷言結構**：
 * 有沒有 `rows`、`data` 是物件還是陣列、頂層有哪些鍵。
 * **不打印任何值**，也不把回應寫進文件。
 *
 * ### 憑據來源
 *
 * 與 `HzcuLiveLoginTest` 同一套：優先讀環境變數 `WANZAI_LIVE_CREDENTIALS`，
 * 否則讀 `<projectRoot>/.secrets/login.txt`。**兩者都沒有就跳過**（`Assume`），
 * 這樣 CI 或沒憑據的機器上不會紅。
 */
class PortalTodoContractProbeTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        return File(System.getProperty("user.dir") ?: ".")
    }

    private fun readCredentials(): Pair<String, String>? {
        LiveNetworkGate.requireOptIn()
        val fromEnv = System.getenv("WANZAI_LIVE_CREDENTIALS")
        val raw = when {
            !fromEnv.isNullOrBlank() -> fromEnv
            else -> {
                val f = File(projectRoot(), ".secrets/login.txt")
                if (!f.exists()) return null
                f.readText()
            }
        }
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        return lines[0] to lines[1]
    }

    /** 只描述結構：型別 + 陣列長度 + 字串長度。**不含任何值。** */
    private fun shape(element: kotlinx.serialization.json.JsonElement?, depth: Int = 0): String {
        if (element == null) return "null"
        when (element) {
            is kotlinx.serialization.json.JsonNull -> return "null"
            is kotlinx.serialization.json.JsonArray -> {
                if (element.isEmpty()) return "[]"
                return "[${shape(element[0], depth + 1)}] (len=${element.size})"
            }
            is kotlinx.serialization.json.JsonObject -> {
                if (depth > 3) return "{...}"
                val keys = element.keys.take(24)
                return "{ " + keys.joinToString(", ") { "$it: ${shape(element[it], depth + 1)}" } +
                    if (element.keys.size > keys.size) ", …(+${element.keys.size - keys.size})" else "" + " }"
            }
            is kotlinx.serialization.json.JsonPrimitive -> {
                if (element.isString) {
                    val v = element.content
                    val tags = buildList {
                        if (Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(v)) add("date")
                        if (Regex("^\\d{2}:\\d{2}:\\d{2}").containsMatchIn(v)) add("time")
                        if (v.toLongOrNull() != null) add("numstr")
                    }
                    return "string(len=${v.length}${if (tags.isEmpty()) "" else "," + tags.joinToString(",")})"
                }
                return if (element.content == "true" || element.content == "false") "boolean" else "number"
            }
        }
    }

    @Test
    fun `探测我的待办三个接口的结构`() = runTest {
        val credentials = readCredentials()
        Assume.assumeTrue("没有联调凭据（.secrets/login.txt 或 WANZAI_LIVE_CREDENTIALS），跳过活体探测", credentials != null)
        LiveNetworkGate.requirePortal()
        val (account, password) = credentials!!

        val session = com.muyao.wanzaihyperos.session.PortalSession(
            com.muyao.wanzaihyperos.session.InMemorySecureStore(),
        )
        val client = HzcuClient.create(credentials = session, rateLimiter = HzcuRateLimiter.instance)

        // 先登录（与 A3 相同的链路）。
        val cas = HzcuCasService.create(client = client, session = session)
        cas.login(account, password)

        // 三个待办接口，逐个探测（限流器保证间隔）。
        //
        // ★ 參數已從**門戶自己的源碼**逐字讀出（不再靠猜）：
        //   `recon/portal-web/asset12_..._MyBusynessPage.js` 裡：
        //   ```js
        //   { appid: i, page: t, rows: 10 }        // 三個接口參數完全相同
        //   success: function(t){ ... t.rows ... } // 讀**頂層 rows**
        //   ```
        //   而 `appid` 來自 `listmybillGroup[selectIndex].id`，
        //   那個清單由 `listappbybilltype/<type>` 取得，且它會 unshift 一條
        //   `{ id: "", app_name: "全部" }` —— **所以 appid 可以傳空串**。
        //
        //   之前傳空 map 得到 400，是因為**缺了 page/rows**，不是缺 appid。
        val paths = listOf(
            "/api/app/billlist/listmybill",
            "/api/app/billlist/listmyprebill",
            "/api/app/billlist/listmyfinishbill",
        )

        // 门戶用的就是這一組（rows=10）；先試它，再試 appid 非空以防萬一。
        //
        // ⚠️ 兩種編碼都要試：門戶的 `MyBusynessPage` 對這三個接口**沒寫 contenttype**
        //（別的接口都顯式寫了 `x-www-form-urlencoded`），
        // 所以預設可能是 **JSON**。A3 已有先例：`listmessage`/`listnewslist`
        // 不吃 form、只吃 JSON（見 API-PORTAL §1.3），同一批接口兩種編碼並存。
        val candidates = listOf(
            "form: appid=\"\"+page+rows" to (mapOf("appid" to "", "page" to "1", "rows" to "10") to false),
            "json: appid=\"\"+page+rows" to (mapOf("appid" to "", "page" to "1", "rows" to "10") to true),
            "form: page+rows（不给 appid）" to (mapOf("page" to "1", "rows" to "10") to false),
            "json: page+rows（不给 appid）" to (mapOf("page" to "1", "rows" to "10") to true),
            "form: pageindex+pagesize" to (mapOf("appid" to "", "pageindex" to "1", "pagesize" to "10") to false),
        )

        val report = buildString {
            appendLine("=== 门户待办三接口结构（只记结构，不记值）===")
            for (path in paths) {
                appendLine()
                appendLine("## $path")
                var done = false
                for ((label, candidate) in candidates) {
                    if (done) break
                    val (fields, useJson) = candidate
                    val response = runCatching {
                        if (useJson) {
                            // JSON 包體：與 A3 的 listmessage 同款（那條實測不吃 form）。
                            // portalPost 本來就支援 jsonBody，不必繞。
                            client.portalPost(
                                path = path,
                                jsonBody = kotlinx.serialization.json.buildJsonObject {
                                    fields.forEach { (k, v) -> put(k, v) }
                                },
                                context = "待辦探測(JSON)",
                            )
                        } else {
                            client.portalPost(path = path, fields = fields, context = "待辦探測(form)")
                        }
                    }
                    response.fold(
                        onSuccess = { r ->
                            appendLine("  [$label] code=${r.code} success=${r.success}")
                            // ★ 失败时把服务端原话打出来 —— 它是唯一能指出
                            //   「到底哪里不对」的线索。**不脱敏**：
                            //   服务端的参数校验消息不含个人数据（实测都是
                            //   「参数xxx不能为空」这类），且这是活体探针，
                            //   输出只落在已 gitignore 的 build/ 下。
                            if (r.code != 200 || !r.success) {
                                appendLine("    服务端原话 = ${r.message}")
                            }
                            if (r.code == 200 && r.success) {
                                appendLine("  ★ 通了的参数组：$label -> $fields")
                                appendLine("    顶层键 = ${r.raw.keys.joinToString(", ")}")
                                appendLine("    data  = ${shape(r.data)}")
                                appendLine("    rows  = ${shape(r.raw["rows"])}")
                                appendLine("    listPayload 条数 = ${r.listPayload?.size}")
                                appendLine("    total = ${r.total} / allrecords = ${r.raw["allrecords"]}")
                                // 列出条目字段名（**不列值**）
                                r.listPayload?.firstOrNull()?.let { first ->
                                    (first as? kotlinx.serialization.json.JsonObject)?.let { o ->
                                        appendLine("    条目字段 = ${o.keys.joinToString(", ")}")
                                        appendLine("    条目形状 = ${shape(o)}")
                                    }
                                }
                                done = true
                            }
                        },
                        onFailure = { e ->
                            appendLine("  [$label] 失败：${e.javaClass.simpleName} ${(e as? HzcuException)?.details ?: ""}")
                        },
                    )
                    // 限流器已保证全局间隔；这里不再额外 sleep，避免拖长测试。
                }
                if (!done) appendLine("  ⛔ 所有候选参数都没通")
            }
        }

        // 证据写到模块目录下的 build（已 gitignore），**不含任何值**。
        val out = File(projectRoot(), "app/build").apply { mkdirs() }
        File(out, "b3-live-todo-probe.txt").writeText(report)
        println(report)
    }
}
