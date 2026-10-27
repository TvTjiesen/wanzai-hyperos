package com.muyao.wanzaihyperos.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3 #1 第 9 轮审计那三条行为缺口的守卫（都是**源码级**判据，因为它们各自只有一处落点）。
 *
 * 为什么放在这里而不是 `MotionGuardTest`：那份是**棘轮**（阈值只许降），
 * 这一份是**接线存在性**（"这个闸到底接没接上"）。两类判据混在一起会让棘轮数字的含义变糊。
 */
class MotionGateWiringTest {

    private val repoRoot: File
        get() {
            var d: File? = File(System.getProperty("user.dir"))
            while (d != null) {
                if (File(d, "app/build.gradle.kts").isFile) return d
                d = d.parentFile
            }
            error("找不到工程根目录")
        }

    private fun source(name: String): String =
        File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos/$name").readText(Charsets.UTF_8)

    /**
     * #1 ①：减弱动效**不许**变成"Miuix 自带的默认曲线"。
     *
     * 事故原文：`wanzaiRouteTransitions(0)` ⇒ `NavMotion()`（库默认）⇒ 关掉动效后页面照旧位移，
     * 而且时长不由我们定。现在减弱动效走 `reducedMotion = true`（140ms **纯 alpha**）。
     */
    @Test
    fun `路由转场不许把减弱动效交给库默认曲线`() {
        val nav = source("nav/RouteTransitions.kt")
        assertTrue(
            "`NavMotion()`（无参 = Miuix 默认曲线）又回来了 ⇒ 关掉动效的用户照样看到整页位移",
            !nav.contains("NavMotion()"),
        )
        assertTrue(
            "减弱动效那一档必须有自己的时长（`routeMotionMs`）",
            nav.contains("fun routeMotionMs(") && nav.contains("REDUCED_FADE_MS"),
        )
        assertTrue(
            "位移必须能被整条抹掉（`slide = 0`）—— 只留 alpha 才是标准 §6 要的减弱动效",
            nav.contains("slide = if (reducedMotion || motionMs <= 0) 0f else 1f"),
        )
        val root = source("WanzaiRoot.kt")
        assertTrue(
            "调用点必须传 `reducedMotion`，不许再把 `if (…) navMs else 0` 那种写法带回来",
            root.contains("reducedMotion = !motion.animationsEnabled"),
        )
    }

    /**
     * #1 ②③：底栏必须与顶栏读**同一道降级闸**，并且两处弹簧都要过 `canMove`。
     *
     * 事故原文：底栏是唯一没读 `glassIsTranslucent` 的那一层（减弱透明度时只有它还在半透明 + 采样），
     * 也是唯一没判 `canMove` 的那一层（关掉动效后它还在回弹位移）。
     */
    @Test
    fun `底栏必须接降级闸与 canMove`() {
        val bar = source("ui/design/liquid/LiquidGlassNavigationBar.kt")
        assertTrue(
            "底栏没读 `motion.glassIsTranslucent` ⇒ 减弱透明度时只有它还在半透明（半套降级）",
            bar.contains("motion.glassIsTranslucent"),
        )
        assertTrue(
            "降级要收口到一个量（`barBlurActive`），下面所有分支都读它 —— 否则又会漏掉某一处采样",
            bar.contains("val barBlurActive = isBlurActive && motion.glassIsTranslucent") &&
                bar.split("barBlurActive").size >= 5,
        )
        assertTrue(
            "两处弹簧/阻尼都要过 `canMove`（关掉动效 ⇒ 位移停掉、状态照变）",
            Regex("motion\\.canMove").findAll(bar).count() >= 2,
        )
    }

    /**
     * #1 ④：**滚动期降采样**必须真的接上（审计之前全工程 `scroll` 命中 0 次）。
     *
     * 收口方式：`PageChrome.scrolling` 一个位 + 玻璃层读它（滚动期关折射、模糊减半）
     * + 页面把 `isScrollInProgress` 报进来。
     */
    @Test
    fun `滚动期降采样必须接上`() {
        val chrome = source("ui/design/liquid/IosChrome.kt")
        assertTrue(
            "`PageChrome` 上没有 `scrolling` 这个位 ⇒ 玻璃层无从知道此刻在滚",
            chrome.contains("var scrolling by mutableStateOf(false)") && chrome.contains("fun onScroll("),
        )
        assertTrue(
            "顶栏纱必须读它（滚动期不折射 + 模糊减半）",
            chrome.contains("val scrolling = LocalPageChrome.current.scrolling"),
        )
        val bar = source("ui/design/liquid/LiquidGlassNavigationBar.kt")
        assertTrue("底栏也必须读它", bar.contains("LocalPageChrome.current.scrolling"))
        assertTrue(
            "底栏的折射要能被 `!scrolling` 关掉（最贵的那一步）",
            bar.contains("if (refract > 0f && !scrolling)"),
        )
        // 页面侧：至少两个带列表的板块页要把滚动状态报进来
        val pages = File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos/ui/main/pages")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }
            .count {
                val t = it.readText(Charsets.UTF_8)
                // 两种写法都算接上：`pilot.onScroll(listState.isScrollInProgress)`（课表页）与
                // 先把状态取进局部量再报（首页）—— 判的是"这个页面报了没有"，不是"怎么写"。
                t.contains("isScrollInProgress") && t.contains("onScroll(")
            }
        assertTrue("报滚动的页面只有 $pages 个（要 ≥2）⇒ 大部分页面的滚动根本没降采样", pages >= 2)
    }
}