package com.muyao.wanzaihyperos.ui.tronclass

import com.muyao.wanzaihyperos.data.tronclass.RollcallQrCode
import com.muyao.wanzaihyperos.data.tronclass.TronRollcall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B4-07 的**阶段 1 判据**（业主 2026-10-23 01:2x 的两阶段闸门：真机只做终审，行为先在这里钉）。
 *
 * 事故一：签到页扫到码 → 打 `POST /api/qr-code/scan`（那是 **roomis 校园码**）→ 学校回
 * `404 {"message":"未找到资源"}`。而真正的提交端点在官方 H5 产物里写着：
 * `answerQRRollcall: function(e){ return "/api/rollcall/".concat(e,"/answer_qr_rollcall") }`
 * ⇒ **必须带那条签到自己的 id**，且包里没有"码 → id"的接口。
 *
 * 事故二（第 1 轮阶段 1 审计的**红线**，「签到是写操作，永不重试、不许替他点提交」）：
 * 上一版在"唯一候选"时直接 `answer(id, QR, code)` —— 把我们猜的那一次当成他的选择。
 *
 * 所以判据现在是两条：① [scanPlan] 只**选出**候选、不写；② 全页唯一的写入口 `submitTo`
 * 只能从**他按下的那颗按钮**里被调用。
 */
class ScanPlanTest {

    /** 造一条最小可用的签到（只填判据要用的字段）。 */
    private fun rollcall(id: Int, needsCamera: Boolean = true, unanswered: Boolean = true): TronRollcall =
        TronRollcall(
            id = id,
            courseId = 1,
            title = "第N次点名",
            source = if (needsCamera) "qr" else "number",
            status = "in_progress",
            expired = false,
            rollcallTime = "",
            endTime = "",
            numberCode = "1234",
            myStatus = if (unanswered) "unanswered" else "on_call",
        )

    private fun pageSource(): List<String> {
        var d: File? = File(System.getProperty("user.dir") ?: ".")
        while (d != null && !File(d, "app/build.gradle.kts").isFile) d = d.parentFile
        val root = requireNotNull(d) { "找不到工程根" }
        return File(
            root,
            "app/src/main/java/com/muyao/wanzaihyperos/ui/tronclass/RollcallStationPage.kt",
        ).readLines()
    }

    /** 注释必须剥掉：说明里天生会引用这些名字，判据写错会逼人不敢写注释。 */
    private fun isComment(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
    }

    /** 取一个顶层函数的函数体（从签名行到下一个顶格 `}`）。 */
    private fun bodyOf(lines: List<String>, signature: String): List<String> {
        val start = lines.indexOfFirst { it.startsWith(signature) }
        require(start >= 0) { "找不到 $signature" }
        val end = (start + 1 until lines.size).first { lines[it] == "}" }
        return lines.subList(start, end + 1)
    }

    @Test
    fun `没有候选就不许发写请求`() {
        assertEquals(ScanPlan.NoCandidate, scanPlan(emptyList()))
    }

    @Test
    fun `唯一候选时只选出那条 id`() {
        assertEquals(ScanPlan.Single(4242), scanPlan(listOf(rollcall(4242))))
    }

    @Test
    fun `多条扫码签到时分不清 不许挑一条提交`() {
        val p = scanPlan(listOf(rollcall(1), rollcall(2), rollcall(3)))
        assertTrue("多条必须走 Ambiguous（不许替他猜一条提交）", p is ScanPlan.Ambiguous)
        assertEquals(3, (p as ScanPlan.Ambiguous).count)
    }

    /** 复核候选的筛选口径：只有「需扫码 + 未签」才算（UI 那边就是这么过滤的）。 */
    @Test
    fun `候选口径是需扫码且未签`() {
        val all = listOf(
            rollcall(11, needsCamera = true, unanswered = true),
            rollcall(12, needsCamera = false, unanswered = true),
            rollcall(13, needsCamera = true, unanswered = false),
        )
        val candidates = all.filter { it.needsCamera && it.unanswered }
        assertEquals(listOf(11), candidates.map { it.id })
        assertEquals(ScanPlan.Single(11), scanPlan(candidates))
    }

    /**
     * **静态守卫（红线）**：`scanPlan` 与 `pickScanTarget` 里不许出现任何写调用。
     *
     * 变异验收：在 `pickScanTarget` 的 `ScanPlan.Single ->` 分支里加一句
     * `submitTo(...)` 或 `repository.answer(...)` ⇒ 这条红。
     *
     * 注：`pickScanTarget` 的参数表里已经没有 `RollcallRepository`（只拿
     * "刷新 + 读候选"的 lambda），所以"顺手提交"在编译期就写不出来；这条测试是第二道。
     */
    @Test
    fun `挑候选的两处代码不许出现写入口`() {
        val lines = pageSource()
        val bodies = bodyOf(lines, "internal fun scanPlan(") + bodyOf(lines, "private fun pickScanTarget(")
        val code = bodies.filterNot(::isComment).joinToString("\n")
        for (bad in listOf("submitTo(", ".answer(", "repository", "RollcallRepository")) {
            assertTrue("挑候选的代码里出现了 `$bad` —— 扫到码只许选中，不许提交", bad !in code)
        }
    }

    /**
     * **静态守卫（红线）**：`submitTo(` 的调用点只能紧挨着按钮的动作参数。
     *
     * 两处，且只有两处：
     *  - 选择卡的 `onClick = { … submitTo(…) }`（他按"确认签到 / 就是这次"）；
     *  - 行内卡的 `onSubmit = { kind, code -> submitTo(…) }`，
     *    而 `RollcallCard` 里 `onSubmit(` 的每一处都必须写在同一行的 `onClick = {` 上。
     *
     * 变异验收：把选择卡的提交挪进 `LaunchedEffect`/`scope.launch`（自动提交）⇒ 这条红。
     */
    @Test
    fun `写入口只能挂在按钮上`() {
        val lines = pageSource()
        val code = lines.filterNot(::isComment)

        val callSites = code.indices.filter { i ->
            code[i].contains("submitTo(") && !code[i].contains("fun submitTo(")
        }
        assertEquals(
            "写入口 `submitTo(` 应只有 2 个调用点（选择卡 + 行内卡），实际 ${callSites.size} 处：" +
                callSites.joinToString { "行${it + 1}" },
            2,
            callSites.size,
        )

        for (i in callSites) {
            val near = code.subList(maxOf(0, i - 6), i + 1)
            assertTrue(
                "第 ${i + 1} 行的 `submitTo(` 不在按钮的动作参数里（前 6 行找不到 `onClick = {` / `onSubmit = {`）",
                near.any { it.contains("onClick = {") || it.contains("onSubmit = {") },
            )
        }

        // 行内卡：`onSubmit(` 的每一处都在同一行的 onClick 上
        val card = bodyOf(code, "private fun RollcallCard(")
        val inner = card.filter { "onSubmit(" in it && !it.trim().startsWith("onSubmit:") }
        assertTrue("RollcallCard 里找不到 onSubmit 调用点", inner.isNotEmpty())
        inner.forEach { line ->
            assertTrue(
                "RollcallCard 里的 `onSubmit(` 必须写在 `onClick = {` 那一行：${line.trim()}",
                line.contains("onClick = {"),
            )
        }
    }

    /**
     * **静态守卫**：那条打错的端点（roomis 校园码）不许回到签到路径上。
     * 变异验收：把 `repository.scan(code)` 加回签到路径 ⇒ 这条红。
     */
    @Test
    fun `签到路径不许再调校园码端点`() {
        val page = pageSource().filterNot(::isComment).joinToString("\n")
        assertTrue("签到页又在调 `repository.scan(`（那就是校园码端点）", "repository.scan(" !in page)
        assertTrue("签到页必须用 scanPlan 决定动作", "scanPlan(" in page)

        var d: File? = File(System.getProperty("user.dir") ?: ".")
        while (d != null && !File(d, "app/build.gradle.kts").isFile) d = d.parentFile
        val main = File(requireNotNull(d) { "找不到工程根" }, "app/src/main/java/com/muyao/wanzaihyperos")
        val hits = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .count { it.readText(Charsets.UTF_8).contains("scanQrCode") }
        assertEquals("`scanQrCode`（roomis 端点）不该再有任何调用点", 0, hits)
    }

    @Test
    fun `码里带签到编号时 清单里没有也能签`() {
        // 学校签到码 /j?p= 的第 4 段就是 rollcallId（36 进制）。
        // 扫到码不必等它出现在「进行中」清单里 —— 这是 2026-10-30 更正的口径。
        val code = RollcallQrCode.Payload(rollcallId = 249656, courseId = 67619, data = "abc")
        val picked = pendingCandidates(emptyList(), targetId = null, code = code)
        assertEquals("必须凭码里的编号合成一条", 1, picked.size)
        assertEquals(249656, picked[0].id)
        assertEquals(67619, picked[0].courseId)
        assertTrue("标题要写明这是扫到的编号，别让他以为是清单里的", "249656" in picked[0].title)
    }

    @Test
    fun `清单里有这条时用清单里的 别另造一条`() {
        val real = rollcall(249656)
        val code = RollcallQrCode.Payload(rollcallId = 249656, courseId = 67619, data = "abc")
        val picked = pendingCandidates(listOf(real), targetId = null, code = code)
        assertEquals(1, picked.size)
        assertEquals("必须是清单里那条（标题、时间都是真的）", real.title, picked[0].title)
    }

    @Test
    fun `行内扫码优先于码里的编号`() {
        val a = rollcall(1)
        val b = rollcall(2)
        val code = RollcallQrCode.Payload(rollcallId = 2, courseId = 67619, data = "abc")
        val picked = pendingCandidates(listOf(a, b), targetId = 1, code = code)
        assertEquals("他点了第 1 行，就只认第 1 行", listOf(1), picked.map { it.id })
    }

    @Test
    fun `没有码也没有行内目标时退回需扫码且未签的`() {
        val open = rollcall(1)
        val signed = rollcall(2, unanswered = false)
        val picked = pendingCandidates(listOf(open, signed), targetId = null, code = null)
        assertEquals("只挑需扫码且还没签的", listOf(1), picked.map { it.id })
    }
}
