package com.muyao.wanzaihyperos.net

import kotlinx.coroutines.test.runTest
import org.junit.Assume
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 學在城院原生化的**鏈路驗證**（一次性探測，驗證完就刪）。
 *
 * 假設（讀 SPA 源碼得知的鏈路，見 HzcuEndpoints 的註釋）：
 * ```
 * POST https://courses.hzcu.edu.cn/api/login?login=access_token
 *      body 帶 Keycloak accessToken
 *   → 回應裡給 X-SESSION-ID
 * GET /api/todos 帶 X-SESSION-ID 頭 → JSON（實測 19 條待辦）
 * ```
 *
 * 本測試回答：這條鏈**不靠 WebView** 能不能走通。
 * ★ 只輸出狀態碼與結構，不輸出任何 token 值。
 */
class TronclassNativeChainProbeTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        return File(System.getProperty("user.dir") ?: ".")
    }

    @Test
    fun `token对换sessionID再取todos`() = runTest {
        val f = File(projectRoot(), ".secrets/login.txt")
        Assume.assumeTrue("無憑據，跳過", f.exists())
        LiveNetworkGate.requirePortal()
        LiveNetworkGate.requireTronclass()
        val lines = f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val (account, password) = lines[0] to lines[1]

        val session = com.muyao.wanzaihyperos.session.PortalSession(
            com.muyao.wanzaihyperos.session.InMemorySecureStore(),
        )
        val client = HzcuClient.create(credentials = session, rateLimiter = HzcuRateLimiter.instance)
        val cas = HzcuCasService.create(client = client, session = session)
        cas.login(account, password)
        val tokens = cas.loginForTronclassTokens(account, password)
        val access = tokens.accessToken

        val report = StringBuilder()
        report.appendLine("=== 學在城院原生鏈路探測 ===")

        // ⚠️ 用**裸 OkHttp**：門戶那個 client 會給每個請求塞 ACCESS_TOKEN 表單欄位
        //   與門戶域邏輯（它只為 iappdd 設計），拿去打 courses 域會出亂子。
        val ok = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        fun rawPost(url: String, fields: Map<String, String>, headers: Map<String, String>): Pair<Int, String>? =
            runCatching {
                val form = okhttp3.FormBody.Builder()
                fields.forEach { (k, v) -> form.add(k, v) }
                val req = okhttp3.Request.Builder().url(url).post(form.build())
                headers.forEach { (k, v) -> req.header(k, v) }
                ok.newCall(req.build()).execute().use { r -> r.code to (r.body?.string().orEmpty()) }
            }.getOrElse { e ->
                report.appendLine("  異常: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
                null
            }

        fun rawGet(url: String, headers: Map<String, String>): Pair<Int, String>? =
            runCatching {
                val req = okhttp3.Request.Builder().url(url)
                headers.forEach { (k, v) -> req.header(k, v) }
                ok.newCall(req.build()).execute().use { r -> r.code to (r.body?.string().orEmpty()) }
            }.getOrElse { e ->
                report.appendLine("  異常: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
                null
            }

        // ① access_token → sessionID
        //
        // 探測實測（2026-10-12 00:3x）：JSON 形態回
        // `400 {"errors":{"code":"_NO_USER_NAME_","user_name":["User Name not provided"]}}`
        // —— 鏈路是通的，只是**還缺 user_name**。補上再試。
        val loginUrl = "https://courses.hzcu.edu.cn/api/login?login=access_token"
        val jsonAttempts = listOf(
            "user_name+access_token" to """{"user_name":"$account","access_token":"$access"}""",
            "user_name+accessToken" to """{"user_name":"$account","accessToken":"$access"}""",
            "user_name+token" to """{"user_name":"$account","token":"$access"}""",
        )
        var sessionId: String? = null
        for ((label, json) in jsonAttempts) {
            val r = runCatching {
                val body = json.toRequestBody("application/json".toMediaType())
                val req = okhttp3.Request.Builder().url(loginUrl)
                    .header("Accept", "application/json").post(body).build()
                ok.newCall(req).execute().use { rr ->
                    // ⚠️ sessionID 在**響應頭**裡（瀏覽器實測請求帶 X-SESSION-ID，91 字元），
                    //    不在 body。第一版探測只讀 body，所以 200 了還以為失敗。
                    Triple(
                        rr.code,
                        rr.body?.string().orEmpty(),
                        rr.headers["X-SESSION-ID"]
                            ?: rr.headers["x-session-id"]
                            ?: rr.headers["Set-Cookie"]?.takeIf { it.contains("session", true) },
                    )
                }
            }.getOrNull()
            if (r == null) { report.appendLine("$label -> 請求失敗"); continue }
            val (status, respBody, headerSid) = r
            val sid = headerSid
                ?: Regex(""""?\s*"?X-SESSION-ID"?\s*[:=]\s*"([A-Za-z0-9._-]{10,})"""", RegexOption.IGNORE_CASE)
                    .find(respBody)?.groupValues?.get(1)
            report.appendLine("$label -> HTTP $status len=${respBody.length} sid=${sid != null}")
            if (sid == null && respBody.length < 400) {
                report.appendLine("    body: ${respBody.take(380).replace("\n", " ")}")
            }
            if (sid != null) { sessionId = sid; break }
            kotlinx.coroutines.delay(1500)
        }

        // ② 有 sessionID 就試 todos
        if (sessionId != null) {
            val t = rawGet(
                "https://courses.hzcu.edu.cn/api/todos",
                mapOf(
                    "X-SESSION-ID" to sessionId,
                    "Accept" to "application/json, text/plain, */*",
                    "X-Requested-With" to "XMLHttpRequest",
                ),
            )
            if (t != null) {
                val (code, body) = t
                val isJson = body.trimStart().startsWith("{")
                val n = Regex(""""course_name"""").findAll(body).count()
                report.appendLine("todos -> HTTP $code isJson=$isJson 條目約=$n")
                if (!isJson) report.appendLine("    head: ${body.take(200).replace("\n", " ")}")
            } else report.appendLine("todos -> 請求失敗")
        } else {
            report.appendLine("todos -> 跳過（沒拿到 sessionID）")
        }

        File(projectRoot(), "app/build").mkdirs()
        File(projectRoot(), "app/build/tron-chain.txt").writeText(report.toString())
        println(report)
    }
}
