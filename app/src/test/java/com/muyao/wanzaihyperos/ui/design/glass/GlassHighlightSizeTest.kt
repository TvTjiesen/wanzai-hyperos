package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 玻璃高光的**尺寸闸门**守卫（#58 第 4 条）。
 *
 * ## 这条测试在防什么
 *
 * 业主原话：「此刻卡偏灰、缺高光」。追下去发现：卡不是「灰」，是整块被涂成了一片
 * 不透明实色板 —— BloomStrokeShader 里 halfView.y = ∞ 让 roundedBoxSDF 退化成一维，
 * outMask 全 1，于是**整块卡面都被当成高光带**，而 hl.a 写死的 1.0 把层 alpha
 * 从 0.30 抬成 1，玻璃语义整个消失（边缘那道亮边也被盖没）。
 *
 * 而 ∞ 之所以能溜进来，是因为旧检查写成 `!(w > 0f) || !(h > 0f)`：
 * 它挡得住 NaN，**挡不住 ∞**（∞ > 0 为真）。注释里写着「NaN 也要被拦住」，
 * 结果漏掉了另一半。
 *
 * 所以这里三件事一起钉住：判定函数本身、两个调用点、以及「旧写法不许回来」。
 */
class GlassHighlightSizeTest {

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
        File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos/" + name).readText(Charsets.UTF_8)

    @Test
    fun `无穷大的尺寸必须被判成不可用`() {
        // 这一条就是本轮的真正原因：∞ 能骗过 > 0f。
        assertNull("宽 ∞ 必须拒", finiteSizeOrNull(Size(Float.POSITIVE_INFINITY, 875f)))
        assertNull("高 ∞ 必须拒", finiteSizeOrNull(Size(1216f, Float.POSITIVE_INFINITY)))
        assertNull(
            "两轴都 ∞（Constraints.Infinity 两个方向）必须拒",
            finiteSizeOrNull(Size(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)),
        )
    }

    @Test
    fun `NaN 零 负数也都不算数`() {
        assertNull("Size.Unspecified 是 NaN", finiteSizeOrNull(Size.Unspecified))
        assertNull("宽 0", finiteSizeOrNull(Size(0f, 875f)))
        assertNull("高 0", finiteSizeOrNull(Size(1216f, 0f)))
        assertNull("负", finiteSizeOrNull(Size(-1f, 875f)))
        assertNull("负 ∞ 同样不算", finiteSizeOrNull(Size(Float.NEGATIVE_INFINITY, 875f)))
    }

    @Test
    fun `正常尺寸必须原样放行`() {
        val ok = Size(1216f, 875f)
        assertEquals("有限正尺寸不许被吞掉", ok, finiteSizeOrNull(ok))
    }

    @Test
    fun `高光入口自己必须再筛一遍`() {
        val shader = source("ui/design/glass/BloomStrokeShader.kt")
        assertTrue(
            "bloomStrokeHighlight 里找不到 finiteSizeOrNull(explicitSize ?: size) —— 尺寸闸门又断了",
            shader.contains("finiteSizeOrNull(explicitSize ?: size)"),
        )
        assertTrue(
            "旧写法 !(w > 0f) || !(h > 0f) 拦不住 ∞，不许回来",
            !shader.contains("if (!(w > 0f) || !(h > 0f)) return"),
        )
    }

    /**
     * 单靠入口自己筛是不够的：explicitSize 为 ∞ 时入口会**整层跳过高光**（回 null 即 return），
     * 那等于把高光静默关掉。正确做法是调用方先判，∞ 就不传，让它退回引擎给的真尺寸。
     */
    @Test
    fun `调用点不许把未判定的尺寸传进高光`() {
        val surface = source("ui/design/glass/GlassSurface.kt")
        assertTrue(
            "调用点又直接传 explicitSize = sizePx 了 —— maxHeight 可能是 Constraints.Infinity",
            !surface.contains("explicitSize = sizePx,"),
        )
        assertTrue(
            "调用点应传 finiteSizeOrNull(sizePx)",
            surface.contains("explicitSize = finiteSizeOrNull(sizePx)"),
        )
    }
}
