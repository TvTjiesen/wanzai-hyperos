package com.muyao.wanzaihyperos.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 夜档天空的**观感守卫**（业主 2026-10-24「天空你自己选一个我觉得好看的」）。
 *
 * 缺陷长什么样（2026-10-24 在模拟器上量出来的，两帧各 1216×2640）：
 * `auto` 的 20..22 那支是 #1B2545→#3D4A72，色相 225.7/226.0/225.3°；
 * 到了 23 点整切成照板的 #1B2A31→#22373C→#2D3634，色相掉到 199.1/191.5/166.7°（青绿）。
 * 而**亮度几乎没动**（top 0.0199 → 0.0211）。于是业主看到的是「同一块暗面被换成青灰色」，
 * 而不是「夜深了更暗」—— 这就是当时那支要不得的原因，不是好不好看的问题。
 *
 * 为什么用源码级数值而不是像素测试：天空是一段竖向渐变加山脊，像素测试要先钉窗口、
 * 钉密度、钉设备代次，一换代就失效（B4-03 那条 F_淡出带就是这么废掉的）。
 * 而这一条判的正是**色板本身**，直接读 `skyLookOf` 的输出即可，跨设备成立。
 */
class NightSkyHueTest {

    /** sRGB → 色相角（度）。Compose 的 Color 只给线性分量，色相得自己算。 */
    private fun hue(c: Color): Float {
        val r = c.red
        val g = c.green
        val b = c.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d == 0f) return 0f
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return (h * 60f + 360f) % 360f
    }

    /** 色相环上两点的最短夹角（度）。 */
    private fun hueGap(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return minOf(d, 360f - d)
    }

    /** 0% / 46% / 100% 三个停靠站，两档各取一份。 */
    private fun stops(look: SkyLook) = listOf(
        "顶" to look.colorAt(0f),
        "中(46%)" to look.colorAt(SKY_MID_STOP),
        "底" to look.colorAt(1f),
    )

    // 判据阈值：现状那支跨档只差 0.2~1.3°，照板那支差 26.6~58.6°，取 8° 两边都留余量。
    private val MAX_HUE_GAP = 8f

    @Test
    fun `凌晨那一档必须与傍晚那支同色相，不许整块换色`() {
        val evening = stops(skyLookOf(SkyPreset.AUTO, 21))
        val lateNight = stops(skyLookOf(SkyPreset.AUTO, 2))
        val bad = evening.zip(lateNight).mapNotNull { (e, l) ->
            val (eName, eColor) = e
            val (_, lColor) = l
            val gap = hueGap(hue(eColor), hue(lColor))
            if (gap > MAX_HUE_GAP) {
                "${l.first}: 20..22 ${eName} ${hue(eColor)}° 与凌晨 ${hue(lColor)}° 差 ${gap}°"
            } else null
        }
        assertTrue(
            "凌晨那支把色相换掉了（> ${MAX_HUE_GAP}°）：$bad —— " +
                "换色相而亮度不动，看起来就是「同一块暗面镀了一层别的颜色」，不是夜深了",
            bad.isEmpty(),
        )
    }

    @Test
    fun `夜色必须一路变暗，不是只换色相`() {
        // 18 点黄昏 → 20..22 → 凌晨 2 点。天空顶这一路必须单调变暗。
        val topLums = listOf(18, 21, 2).map { skyLookOf(SkyPreset.AUTO, it).colorAt(0f).luminance() }
        assertTrue(
            "天空顶的亮度没有一路下降：18h=${topLums[0]} 21h=${topLums[1]} 02h=${topLums[2]}",
            topLums[0] > topLums[1] && topLums[1] > topLums[2],
        )
        // 天空底同理（黄昏那站是 #F0A97A，亮得起来；之后两档都是暗面）
        val botLums = listOf(18, 21, 2).map { skyLookOf(SkyPreset.AUTO, it).colorAt(1f).luminance() }
        assertTrue(
            "天空底的亮度没有一路下降：18h=${botLums[0]} 21h=${botLums[1]} 02h=${botLums[2]}",
            botLums[0] > botLums[1] && botLums[1] > botLums[2],
        )
    }

    @Test
    fun `夜档白字的对比度必须仍然达标`() {
        val look = skyLookOf(SkyPreset.AUTO, 2)
        assertTrue("凌晨档的字色该是白的", look.ink == Color.White)
        stops(look).forEach { (name, c) ->
            val cr = wcagContrast(c, look.ink)
            assertTrue("凌晨档 $name 站上白字只有 $cr:1（要 ≥ 10）", cr >= 10f)
        }
    }
}
