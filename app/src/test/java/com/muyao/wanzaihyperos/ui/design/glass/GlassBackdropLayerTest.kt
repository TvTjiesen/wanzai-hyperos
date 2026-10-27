package com.muyao.wanzaihyperos.ui.design.glass

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「此刻」卡黑带（2026-10-24 真机复现）的守卫：**采样层里那层不透明底必须排在
 * `layerBackdrop` 的里面**。
 *
 * ## 缺陷长什么样
 *
 * 真机 [local]/now2.png 的「此刻」卡内部是一块 **U 形暗带**，色 (76,75,73)。
 * 反解：卡面色 = SURFACE_CONTAINER (251,248,241) 按 [com.muyao.wanzaihyperos.ui.design.glass.BarOptics.cardFaceAlpha]
 * 的液态厚度 0.30 压在某物上 —— 0.30×251 + 0.70×bg = 76 ⇒ bg ≈ 0（纯黑）。
 * 同一位置换 frost（0.66）得 (165,163,159) ⇒ bg ≈ −2，同样指向黑。
 * ⇒ 不是卡面 alpha 错，是**它底下采到的像素是黑的**。
 *
 * ## 根因
 *
 * [GlassBackdropHost] 的采样层原本写成
 * ```
 * Modifier.fillMaxSize().background(pageBrush).layerBackdrop(backdrop)
 * ```
 * Modifier 链是**外先内后**：`background` 排在 `layerBackdrop` 外面，
 * 那层纸色在录制开始**之前**就画完了，**没进录制**。
 * 于是采到的只有 `content()` —— 而首页的 content 只有 250dp 高的天空盒
 * （`ui/main/pages/HomeSectionPage.kt:281-337`），天空盒以下整片是透明，
 * kyant 的折射对透明区域不是"没效果"，是**铺黑**。
 *
 * 换序之后模拟器逐像素对照：暗带 57 行 → 0 行，其余像素不动（只有状态栏、
 * 天空与卡交界两处因时间与滚动位置不同而差）。
 *
 * ## 为什么要写成源码守卫而不是像素测试
 *
 * 这条缺陷**只在真机的玻璃档上肉眼可见**，单测拿不到 GPU 采样结果；
 * 但"链序写反"是一段纯文本事实，读得到、钉得住，而且改回去立刻红 ——
 * 这就是这条守卫存在的全部理由。
 */
class GlassBackdropLayerTest {

    private val hostFile =
        "src/main/java/com/muyao/wanzaihyperos/ui/design/glass/GlassSurface.kt"

    /** 取 [marker] 之后、下一个顶层 `fun`/`@Composable` 之前的那段函数体。 */
    private fun bodyAfter(code: String, marker: String): String {
        val i = code.indexOf(marker)
        if (i < 0) return ""
        val rest = code.substring(i)
        val next = Regex("""\n\s*(@\w+\s+)*(private |internal |public )?fun\s""").find(rest, 1)
        return if (next == null) rest else rest.substring(0, next.range.first)
    }

    private fun hostBody(): String {
        val file = File(hostFile)
        assertTrue("找不到采样层宿主源文件：$hostFile", file.isFile)
        val code = SourceScan.stripComments(file.readText(Charsets.UTF_8))
        val body = bodyAfter(code, "fun GlassBackdropHost(")
        assertTrue("没在 $hostFile 里找到 GlassBackdropHost 函数体", body.isNotBlank())
        return body
    }

    @Test
    fun `采样层的不透明底必须排在 layerBackdrop 里面`() {
        val body = hostBody()
        val layer = body.indexOf(".layerBackdrop(backdrop)")
        val bg = body.indexOf(".background(")
        assertTrue(
            "GlassBackdropHost 里没找到 .layerBackdrop(backdrop)；采样层被改写过，先确认判据还成立",
            layer >= 0,
        )
        assertTrue(
            "GlassBackdropHost 里没找到 .background(...)；整屏不透明底被删了，" +
                "天空盒以外的区域会重新变成透明 ⇒ 玻璃卡内铺黑",
            bg >= 0,
        )
        assertTrue(
            "采样层的 Modifier 链写反了：.background(...) 排在第 " + bg +
                " 个字符处、.layerBackdrop(backdrop) 排在 " + layer +
                " 个字符处。Modifier 链是外先内后 —— background 在外侧时那层纸色在录制开始前就画完了，" +
                "采到的只有 content()（250dp 天空盒），天空盒以下透明 ⇒ kyant 铺黑" +
                "（真机「此刻」卡 U 形暗带 (76,75,73) = 0.30×251 压纯黑）。" +
                "把 .background(...) 挪到 .layerBackdrop(backdrop) 之后。",
            layer < bg,
        )
    }

    @Test
    fun `被采样的那层铺的是与外壳同一支 pageBrush`() {
        val body = hostBody()
        val bg = body.indexOf(".background(")
        assertTrue(bg >= 0)
        // 只看到链尾那一小段，避免匹配到函数别处的同名调用。
        val window = body.substring(bg, minOf(body.length, bg + 400))
        assertTrue(
            "采样层那层底不是 AppColors.pageBrush(...)。它必须与外壳 " +
                "WanzaiMainShell.kt 画的是同一支刷子，否则坐标对得上时采到的也不是真内容，" +
                "正常路径上会变成可见的色差。实测片段：\n" + window,
            window.contains("AppColors.pageBrush(") || window.contains("pageBrush("),
        )
    }
}
