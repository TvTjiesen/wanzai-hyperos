package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import com.muyao.wanzaihyperos.ui.design.PressMotion
import com.muyao.wanzaihyperos.ui.design.RevealMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三条从抖音那条「8 个动效」里挑出来的东西的守卫。
 *
 * ## 为什么这三条值得钉
 *
 * 1. **松手超调**：差别只在「1.000 → 0.960 → 1.009」这 0.9% 上。以前的写法是
 *    弹簧回正、不过冲 —— 按下去扎实、松开却像一摊水。这个差别肉眼几乎说不清，
 *    但把算术抽成纯函数之后，它就是「过冲到底有没有发生」这一条断言。
 * 2. **交错入场的闸门**：延迟必须**有上限**（第 20 项不能等 2 秒），而且只在页面
 *    打开后头一小段时间里生效；否则 Lazy 每次把滚出视野的项重新组合，整排会每滑一下重弹一遍。
 * 3. **接线**：pressSpring 的 source 必须与 clickable 是**同一枚**，并且写在
 *    background 之前；漏了任一条都会静默失效（按钮照旧能点，只是不会缩）。
 */
class PressRevealTest {

    private fun source(path: String): String {
        val hit = SourceScan.sources().firstOrNull { it.first == path }
        assertTrue("扫不到 " + path + " —— 扫描器坏了，这条判据形同虚设", hit != null)
        return hit!!.second
    }

    @Test
    fun `按下缩到零点九六，松手能过冲到一倍以上`() {
        assertEquals("没按下时必须正好是原尺寸", 1f, PressMotion.scaleAt(0f), 0f)
        assertEquals(
            "按下到位时正好是 SCALE（视频读数 0.960）",
            PressMotion.SCALE,
            PressMotion.scaleAt(1f),
            1e-6f,
        )
        // 松手是弹簧回正，中途 press 会短暂为负 —— 那一段正是「弹」的本体。
        // 所以这里**不许** coerce 到 1f，必须真的能超过 1。
        assertTrue(
            "弹簧回弹时 press 为负，scale 必须超过 1 才有过冲；现在被夹住了",
            PressMotion.scaleAt(-0.5f) > 1.001f,
        )
        var prev = PressMotion.scaleAt(0f)
        for (i in 1..40) {
            val cur = PressMotion.scaleAt(i / 40f)
            assertTrue("按下过程必须单调变小，第 $i 步退回去了：$prev -> $cur", cur <= prev + 1e-6f)
            prev = cur
        }
    }

    @Test
    fun `过冲量由阻尼比算出来，不是手填的一个数字`() {
        // ζ = 0.42 的二阶欠阻尼系统，第一个峰值的超调约 23%：
        // exp(-πζ/√(1-ζ²)) = exp(-1.454) ≈ 0.2336
        assertEquals(
            "ζ=0.42 的理论超调量对不上，说明公式写错了（不是弹簧，是随便填的常数）",
            0.2336f,
            PressMotion.overshootFraction(PressMotion.OVERSHOOT_DAMPING),
            5e-4f,
        )
        assertEquals("过阻尼不该有过冲", 0f, PressMotion.overshootFraction(1f), 1e-6f)
        assertEquals("过阻尼不该有过冲", 0f, PressMotion.overshootFraction(2f), 1e-6f)
        assertTrue(
            "阻尼越小超调越大 —— 这条关系反了就等于公式写反了",
            PressMotion.overshootFraction(0.2f) > PressMotion.overshootFraction(0.6f),
        )
        // 实际峰值 = 1 + 超调量 × 压缩幅度。0.2336 × 0.04 ≈ 0.9%
        val peak = 1f + PressMotion.overshootFraction(PressMotion.OVERSHOOT_DAMPING) *
            (1f - PressMotion.SCALE)
        assertTrue("峰值要在 1.005~1.02 之间：小了看不见，大了像弹簧门", peak in 1.005f..1.02f)
    }

    @Test
    fun `交错延迟有上限，第二十项不许等两秒`() {
        assertEquals("第一项不等", 0L, RevealMotion.delayOf(0))
        assertEquals("相邻两项差 0.1s（视频读数）", RevealMotion.STEP_MS, RevealMotion.delayOf(1))
        assertEquals(
            RevealMotion.STEP_MS * RevealMotion.MAX_STEPS,
            RevealMotion.delayOf(RevealMotion.MAX_STEPS),
        )
        assertEquals(
            "超过上限之后不再加，否则长列表末尾要等好几秒才出现",
            RevealMotion.delayOf(RevealMotion.MAX_STEPS),
            RevealMotion.delayOf(40),
        )
        assertEquals("负数不能算出负延迟（delay 会抛）", 0L, RevealMotion.delayOf(-3))
        assertTrue(
            "上限 × 步长要压在单项时长的量级里，不能比入场本身还长一个数量级",
            RevealMotion.delayOf(RevealMotion.MAX_STEPS) <= RevealMotion.DURATION_MS * 3L,
        )
    }

    @Test
    fun `首页焦点操作接原生按下响应并保减少动态退路`() {
        val body = source("main/ui/main/pages/HomeSectionPage.kt")
        val fn = body.substringAfter("private fun FocusButton(").substringBefore("private fun TodayTimelineCard(")
        assertTrue("焦点操作没有委托 Glasmio 适配器", fn.contains("WanzaiButton(onClick = onClick, prominent = filled, modifier = modifier)"))
        assertTrue("签到和课表回调必须分别保留", body.contains("onClick = onOpenRollcall") && body.contains("onClick = onClick"))
        val adapter = source("main/ui/components/WanzaiGlasmioControls.kt")
            .substringAfter("fun WanzaiButton(").substringBefore("fun WanzaiTextButton(")
        assertTrue("动态闸门未消费", adapter.contains("rememberMotion().canMove"))
        assertTrue("原生与静态两支都必须收回调", Regex("onClick = onClick").findAll(adapter).count() == 2)
        assertTrue("减少动态必须仍有可用静态按钮", adapter.contains("GlasmioButton(") && adapter.contains("ButtonDefaults.buttonColors("))
        val nativeButton = java.io.File("../third_party/glasmio/glasmio-ui/src/main/java/io/github/neoruaa/glasmio/ui/GlassButtons.kt").readText()
        assertTrue("原生按钮须把回调交给玻璃表面", nativeButton.contains("GlassSurface(") && nativeButton.contains("onClick = onClick"))
        val surface = java.io.File("../third_party/glasmio/glasmio-glass/src/main/java/io/github/neoruaa/glasmio/glass/GlassSurface.kt").readText()
        assertTrue("按下变形与触摸必须是同一个状态", surface.contains("interactiveHighlight.pressProgress") && surface.contains("interactiveHighlight.gestureModifier"))
        assertTrue("变形必须覆盖表面而非只缩文字", surface.contains("Modifier.graphicsLayer(dragScaleLayerBlock).clip(shape)"))
        assertTrue("禁用按钮不能仍发按下动作", surface.contains("if (onClick != null && enabled) interactiveHighlight.modifier"))
    }

    @Test
    fun `码表与交错入场真的接在了会变的数字和会重排的列表上`() {
        // 首页校园跑读数：刷新后会变的那个数
        val home = source("main/ui/main/pages/HomeSectionPage.kt")
        assertTrue("首页校园跑读数没有走码表，数字还是直接跳变", home.contains("OdometerText("))
        // 数智体育页：同一件事的读数
        val szty = source("main/ui/szty/SztyPage.kt")
        assertTrue("数智体育页的公里数没有走码表", szty.contains("OdometerText("))
        // 两处交错入场：都必须建闸门并且真的把闸门传给了行
        val pages = listOf(
            Triple("main/ui/main/pages/NotifySectionPage.kt", "消息页", ".revealIn(gate,"),
            Triple("main/ui/main/pages/AppsSectionPage.kt", "应用页", ".revealIn(revealGate,"),
        )
        for ((path, page, usage) in pages) {
            val body = source(path)
            assertTrue("$page 没有建闸门，交错入场等于没开", body.contains("rememberRevealGate()"))
            assertTrue("$page 的闸门没交给任何一项", body.contains(usage))
        }
        // 闸门必须真的传进了消息行：只建不用是最容易发生的静默失效
        val notify = source("main/ui/main/pages/NotifySectionPage.kt")
        assertTrue("MessageGroup 没接闸门形参", notify.contains("gate: RevealGate"))
        assertTrue(
            "行上的 revealIn 必须带着闸门，不能改用别的参数名假装接了",
            notify.contains("Modifier.revealIn(gate,"),
        )
        // 应用页的闸门同理：交出去了就必须真的用在格子上
        val apps = source("main/ui/main/pages/AppsSectionPage.kt")
        assertTrue("应用页建了闸门却没用它画格子", apps.contains(".revealIn(revealGate,"))
    }
}
