package com.muyao.wanzaihyperos.ui.design.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「此刻」卡那条 1px 暗线的守卫（业主 2026-10-25：「卡片时间下面有一条线」）。
 *
 * 缺陷外观（真机 SYNTHETIC0000001，album + liquid 档）：
 * - 单行 `y=675`，颜色恰好 `(76,75,73)`，x 范围 138..1077，跨滚动帧逐像素不动；
 * - 反解 `0.30×251 + 0.70×bg = 76 ⇒ bg ≈ 1`，即那一像素被写成了近纯黑；
 * - 三变体二分证明它由 `bloomStrokeHighlight` 单独画出：把 `base + hl` 改成 `base + hl*0`
 *   线消失，把 hl 的 alpha 改 0 线仍在（说明不是 alpha 混合，是 rgb 本身坏了）。
 *
 * 根因：`getNormal()` 里两处 `normalize(0)`。`coord.xy` 与 `corner.xy` 相等的那一行上
 * 长度为 0，GLSL 的 `normalize` 给 NaN，NaN 顺着 `corner → n → l1/l2 → rgb → hl` 一路传下去，
 * 最后 `base + hl` 把整行写成异常值。模拟器 560dpi 上同一 shader 因为几何不同而没复现，
 * 所以**只能靠源码守卫**锁住零长度分支，像素测试抓不到。
 *
 * 改法：两处 `normalize` 换成「先算长度、长度过小给兜底方向」的手写归一化。
 */
class BloomStrokeNaNTest {

    private val shader = File(
        "src/main/java/com/muyao/wanzaihyperos/ui/design/glass/BloomStrokeShader.kt",
    ).readText(Charsets.UTF_8)

    /** `getNormal` 的函数体（从签名到下一个顶层 `}`），避免把注释里的示例串也算进来。 */
    private fun getNormalBody(): String {
        val start = shader.indexOf("float3 getNormal(")
        assertTrue("找不到 getNormal() —— 函数改名了就同步改这条守卫", start >= 0)
        val end = shader.indexOf("half4 main(float2 fragCoord)", start)
        assertTrue("找不到 main(float2) —— shader 结构变了就同步改这条守卫", end > start)
        return shader.substring(start, end)
    }

    @Test
    fun `getNormal 里不许再直接 normalize 零长度向量`() {
        val body = getNormalBody()
        val bare = Regex("""normalize\s*\(\s*coord""").findAll(body).map { it.value }.toList()
        assertTrue(
            "getNormal 里还有裸的 normalize(coord…)：$bare。" +
                "coord 与 corner 重合时长度为 0，normalize 给 NaN，会顺着 hl 把整整一行写成黑色。" +
                "改成「先 length()、小于 1e-5 时给兜底方向」的写法。",
            bare.isEmpty(),
        )
    }

    @Test
    fun `两处归一化都带零长度兜底`() {
        val body = getNormalBody()
        val guards = Regex("""length\s*\(\s*\w+\s*\)""").findAll(body).count()
        assertEquals(
            "getNormal 里应当有两条 length() 兜底（dir 与 n 各一条），实际 $guards 条",
            2,
            guards,
        )
        val eps = Regex("""1e-5""").findAll(body).count()
        assertEquals("两处兜底都要带 1e-5 的阈值，实际 $eps 处", 2, eps)
    }

    @Test
    fun `兜底方向不是零向量`() {
        val body = getNormalBody()
        // 兜底给 float2(0.0, 0.0) 只对 dir 合法（它随后被 corner += dir * … 使用）；
        // n 的兜底必须是朝内的单位法线，否则高光算出来还是错的。
        assertTrue(
            "n 的零长度兜底必须给 float3(0.0, 0.0, -1.0)（朝内的法线），否则 hl 仍会算错",
            "float3(0.0, 0.0, -1.0)" in body,
        )
    }
}
