package com.muyao.wanzaihyperos.ui.design.glass

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「静止还在出帧」的守卫：**跟重力转的高光不许直接读传感器状态**。
 *
 * ## 缺陷长什么样（2026-10-25 真机实测）
 *
 * 首页/消息页什么都不动，`dumpsys gfxinfo <pkg> reset` 之后 15 s 里仍然渲染 **695 / 696 帧**
 * （90 Hz 满帧 1350，也就是稳定在 45 fps 上下反复重绘）。同一台机器换掉右上角搜索钮的
 * `liveBlur`、或把高光的传感器依赖摘掉，读数立刻掉到 0~4 帧 —— 换包 A/B 双向可逆。
 *
 * ## 根因
 *
 * `liquid/LiquidGlassNavigationBar.kt` 的 [rememberGravityRotatedHighlight] 原本写
 * `val tilt by rememberDeviceTilt()`：加速度计上报节奏**比帧还快**，
 * 每次上报都换出一份新的 [Highlight] ⇒ `Modifier.drawBackdrop(highlight = { … })`
 * 的 lambda 每次都是新的 ⇒ 玻璃面每帧重画并重新采样。
 *
 * 正确的写法在 `ui/design/glass/BarOptics.kt`：`rememberGravityLightDir` 把方向收进
 * 一个 remember 住的 [GravityLightHolder]，外面再包 `derivedStateOf`，
 * 只有**量化后真的跨格**（`BarOptics.QUANT_STEPS`）才往下游失效 —— 平放静置时一次重组都不产生。
 *
 * ## 为什么是源码守卫
 *
 * 单测拿不到 GPU 帧数；但「有没有直接读传感器 state」是一段纯文本事实，
 * 读得到、钉得住，改回去立刻红。像素证据在 `verify/perf-idle/`，这条只负责不让它悄悄返潮。
 */
class GravityHighlightSettleTest {

    private val navFile =
        "src/main/java/com/muyao/wanzaihyperos/ui/design/liquid/LiquidGlassNavigationBar.kt"

    private val opticsFile =
        "src/main/java/com/muyao/wanzaihyperos/ui/design/glass/BarOptics.kt"

    /** 取 [marker] 之后、下一个顶层 `fun`/`@Composable` 之前的那段函数体。 */
    private fun bodyAfter(code: String, marker: String): String {
        val i = code.indexOf(marker)
        if (i < 0) return ""
        val rest = code.substring(i)
        val next = Regex("""\n\s*(@\w+\s+)*(private |internal |public )?fun\s""").find(rest, 1)
        return if (next == null) rest else rest.substring(0, next.range.first)
    }

    private fun bodyOf(path: String, marker: String): String {
        val file = File(path)
        assertTrue("找不到源文件：$path", file.isFile)
        val body = bodyAfter(SourceScan.stripComments(file.readText(Charsets.UTF_8)), marker)
        assertTrue("没在 $path 里找到 $marker 的函数体", body.isNotBlank())
        return body
    }

    @Test
    fun `跟重力转的高光必须走量化持有器，不许直接读传感器状态`() {
        val body = bodyOf(navFile, "fun rememberGravityRotatedHighlight(")
        assertTrue(
            "rememberGravityRotatedHighlight 里又出现 rememberDeviceTilt —— 加速度计上报比帧还快，" +
                "直接读它会让每次上报都换一份 Highlight ⇒ drawBackdrop 的 highlight lambda 失效 " +
                "⇒ 玻璃面每帧重画重采样。真机读数：首页/消息页静止 15 s 渲染 695/696 帧（应接近 0）。" +
                "改用 glass/BarOptics.kt 的 rememberGravityLightDir(extraDegrees = …)。实测片段：\n" + body,
            !body.contains("rememberDeviceTilt"),
        )
        assertTrue(
            "rememberGravityRotatedHighlight 没有走 rememberGravityLightDir：光就不会再跟重力转，" +
                "或者又回到了自己手写的一套未量化实现。实测片段：\n" + body,
            body.contains("rememberGravityLightDir("),
        )
    }

    @Test
    fun `量化持有器的输出必须包在 derivedStateOf 里`() {
        val body = bodyOf(opticsFile, "fun rememberGravityLightDir(")
        assertTrue(
            "rememberGravityLightDir 里没有 derivedStateOf —— 少了这一层，" +
                "holder.advance() 的返回值会被每次重组重新读一遍，量化就白做了。" +
                "注释里写得很清楚：加速度计上报节奏比帧还快。实测片段：\n" + body,
            body.contains("derivedStateOf"),
        )
        assertTrue(
            "rememberGravityLightDir 没有把方向交给 GravityLightHolder.advance —— " +
                "死区 / 低通 / 量化都在那个持有器里，绕开它等于三种保护全丢。实测片段：\n" + body,
            body.contains(".advance("),
        )
    }
}
