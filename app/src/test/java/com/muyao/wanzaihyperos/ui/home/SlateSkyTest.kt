package com.muyao.wanzaihyperos.ui.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 青灰夜档（[SkyPreset.SLATE]）的守卫（业主 2026-11-01「夜档天空两个都留」）。
 *
 * ## 它为什么不能塞进 AUTO
 *
 * AUTO 在 23 点那一支是青绿 #1B2A31→#2D3634，业主 2026-10-24 说它「同一块暗面被换成青灰色」，
 * 于是那一支被改回蓝（`NightSkyHueTest` 的 MAX_HUE_GAP = 8° 就是拦这个的）。现在业主要的是
 * **两个都留**：蓝那支继续当默认的夜，青灰单独成一个可选的档。
 * 两个都留在代码里的前提是它们**确实是两种颜色**——如果哪天有人把青灰调成蓝的近邻，
 * 外观页上就会并排摆着两张看不出差别的格子，那就是骗人。所以这条判「差得够远」。
 *
 * ## 判据
 *
 * ① 三站色相与 NIGHT 同名站相差 ≥ 20°（远大于 AUTO 内部的 8° 容差：跨档本就该换色相，
 *   同档内才不许换）；② 亮面白字压三站都 ≥ 7:1（夜档必须是暗的，不能悄悄提亮）；
 * ③ `lightIcons = true`（夜景要白字白图标，这条错了整块标题会变黑）；
 * ④ 枚举里 id 唯一（`SkyPreset.of` 靠 id 认档）。
 */
class SlateSkyTest {

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

    private fun hueGap(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return minOf(d, 360f - d)
    }

    private fun stops(look: SkyLook) = listOf(
        "顶" to look.colorAt(0f),
        "中(46%)" to look.colorAt(SKY_MID_STOP),
        "底" to look.colorAt(1f),
    )

    @Test
    fun `青灰与夜航是两种颜色，不是同一块的两次取样`() {
        val slate = stops(skyLookOf(SkyPreset.SLATE, 22))
        val night = stops(skyLookOf(SkyPreset.NIGHT, 22))
        val bad = mutableListOf<String>()
        slate.zip(night).forEach { (s, n) ->
            val gap = hueGap(hue(s.second), hue(n.second))
            if (gap < 20f) bad += "${s.first} 只差 $gap°"
        }
        assertTrue(
            "青灰档与夜航档的色相挨得太近，外观页上会像两张一样的格子：$bad",
            bad.isEmpty(),
        )
    }

    @Test
    fun `青灰档是暗面，白字压得住`() {
        val look = skyLookOf(SkyPreset.SLATE, 22)
        val white = Color(0xFFFFFFFF)
        stops(look).forEach { (name, c) ->
            val cr = wcagContrast(c, white)
            assertTrue("青灰$name 的白字对比度只有 $cr，夜档不该这么亮", cr >= 7f)
        }
        assertTrue("青灰是夜景，图标与标题必须是浅色的", look.lightIcons)
        assertEquals("青灰是夜景，墨色应当就是白", white, look.ink)
    }

    @Test
    fun `每一档的 id 都唯一，of 认得回来`() {
        val ids = SkyPreset.entries.map { it.id }
        assertEquals("有重复的档 id：$ids", ids.size, ids.toSet().size)
        SkyPreset.entries.forEach { preset ->
            assertEquals("SkyPreset.of 把 ${preset.id} 认错了", preset, SkyPreset.of(preset.id))
        }
        assertNotEquals("青灰不能和自动档同 id", SkyPreset.AUTO, SkyPreset.SLATE)
    }
}
