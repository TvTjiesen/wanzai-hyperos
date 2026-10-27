package com.muyao.wanzaihyperos.data.tronclass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 跨文件静态守卫：**提交链上不许有任何"再来一次"的写法**。
 *
 * 上一版的守卫只扫 `scanPlan` / `pickScanTarget` 两个函数体、且钉死单文件 ⇒
 * 把写调用放进调用点的 lambda、或改名成 `::submitTo` 就直接溜过。
 * 这里改成扫**整页**（剥注释后），并覆盖扫码页。
 */
class RollcallWriteGuardTest {

    private val files = listOf(
        "src/main/java/com/muyao/wanzaihyperos/ui/tronclass/RollcallStationPage.kt",
        "src/main/java/com/muyao/wanzaihyperos/ui/tronclass/QrScannerPage.kt",
        "src/main/java/com/muyao/wanzaihyperos/data/tronclass/RollcallRepository.kt",
    )

    private fun code(path: String): String {
        val f = File(path)
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        return f.readText(Charsets.UTF_8)
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun `提交链上不许出现重试原语`() {
        val banned = listOf("retryWhen", ".retry(", "repeat(", "repeat {", "while (true)")
        files.forEach { path ->
            val text = code(path)
            banned.forEach { b ->
                assertFalse("$path 里出现了 `$b` —— 签到是写操作，绝不重试", b in text)
            }
        }
    }

    @Test
    fun `写接口只在一处被调用且不在循环里`() {
        val page = code(files[0])
        // 全页只有 submitTo 一处调 repository.answer
        val calls = Regex("""repository\.answer\(""").findAll(page).count()
        assertEqualsInt("RollcallStationPage 里 `repository.answer(` 必须只有 1 处（submitTo 内）", 1, calls)
        // ⚠️ 切片前必须断言标记在：`substringAfter` 缺分隔符会**返回整串**，
        //    函数一改名，这里比的就是 import 区 ⇒ 三条守卫全绿（空转）。
        assertTrue("找不到 `private fun submitTo(` —— 改名了就把守卫一起改，不许空转", "private fun submitTo(" in page)
        // submitTo 的函数体里不许出现循环
        val body = page.substringAfter("private fun submitTo(").substringBefore("\n}")
        assertFalse("submitTo 里有循环 —— 会把写请求发多次", "for (" in body || "while (" in body || "repeat(" in body)
    }

    @Test
    fun `扫码分支不许直达写接口`() {
        val page = code(files[0])
        assertTrue(
            "找不到 `private fun pickScanTarget(` —— 改名了就把守卫一起改，不许空转",
            "private fun pickScanTarget(" in page,
        )
        val pick = page.substringAfter("private fun pickScanTarget(").substringBefore("\n}")
        listOf("submitTo(", ".answer(", "repository.answerTracked").forEach {
            assertFalse("pickScanTarget 里出现了 `$it`（扫到码只许选中，不许提交）", it in pick)
        }
    }

    private fun assertEqualsInt(message: String, expected: Int, actual: Int) =
        org.junit.Assert.assertEquals(message, expected.toLong(), actual.toLong())

    /**
     * **生产那台 client 必须关掉 OkHttp 的连接重试**（工单第 19 轮 ①）。
     *
     * 上一版单测自己起一台 `OkHttpClient.Builder()`（默认 `retryOnConnectionFailure = true`）
     * ⇒ 生产把 `false` 翻成 `true`、或加一条统一重试拦截器，679 条照样全绿、业主被重复提交签到。
     * 现在生产与测试共用 `net/TronHttp.kt` 的 `tronHttpClient()`，这条守它。
     *
     * 变异：把 factory 里的 `false` 改成 `true` ⇒ 红（`verify/b4-07/mutation_retry_true.log`）。
     */
    @Test
    fun `生产 client 必须关闭连接重试`() {
        val factory = File("src/main/java/com/muyao/wanzaihyperos/net/TronHttp.kt")
        assertTrue("找不到 ${factory.absolutePath}", factory.isFile)
        val f = factory.readText(Charsets.UTF_8)
        assertTrue("TronHttp 里没有 `retryOnConnectionFailure(false)`", "retryOnConnectionFailure(false)" in f)

        val graph = File("src/main/java/com/muyao/wanzaihyperos/app/AppGraph.kt").readText(Charsets.UTF_8)
        assertTrue("AppGraph 没有用 `tronHttpClient()` 这支工厂", "tronHttpClient()" in graph)
        assertFalse("AppGraph 又内联了一台 OkHttpClient.Builder()（配置会重新分叉）", "OkHttpClient.Builder()" in graph)

        val test = File("src/test/java/com/muyao/wanzaihyperos/data/tronclass/RollcallWriteOnceTest.kt")
            .readText(Charsets.UTF_8)
        assertTrue(
            "测试没有用生产那支 factory（自己另起一台 = 守的不是同一个东西）",
            "tronHttpClient().newBuilder()" in test,
        )
    }
}
