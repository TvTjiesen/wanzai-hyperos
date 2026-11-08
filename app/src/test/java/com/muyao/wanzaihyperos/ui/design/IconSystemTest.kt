package com.muyao.wanzaihyperos.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B4-13 的静态守卫：设置页图标的**线宽 / 圆角 / 颜色**只能走令牌。
 *
 * 为什么要守卫：这一项的全部价值就是「以后重绘时只改一处」。只要还有一处在调用点写
 * `Color(0xFF2E6B64)` 或 `size(20.dp)`，下次统一重绘就会漏掉它 —— 而漏掉的表现是
 * 「这一枚和别的不一样」，肉眼很难发现（本工程已经因为「同一屏两行一个图标」被审计点过）。
 */
class IconSystemTest {

    private val design = "src/main/java/com/muyao/wanzaihyperos/ui/design"
    private val pages = "src/main/java/com/muyao/wanzaihyperos/ui/main/pages"

    private fun text(path: String) = File(path).readText(Charsets.UTF_8)

    @Test
    fun `调用点里不许再写十六进制磁贴色`() {
        listOf(
            "$pages/SettingsSectionPage.kt",
            "$pages/AccountsPage.kt",
            "$pages/AppearancePage.kt",
        ).forEach { path ->
            val hex = Regex("""Color\(0x[0-9A-Fa-f]{8}\)""").findAll(text(path)).map { it.value }.toList()
            assertTrue("$path 里还有硬编码颜色：$hex（改走 Tone.ok / Tone.warn / Tone.quiet）", hex.isEmpty())
        }
    }

    @Test
    fun `图形规则读令牌而不是字面量`() {
        val chrome = text("$design/SettingsChrome.kt")
        assertTrue("图标画布不是 Glyph.size", "Modifier.size(Glyph.size)" in chrome)
        assertTrue("线宽不是 Glyph.STROKE_RATIO", "Glyph.STROKE_RATIO" in chrome)
        assertTrue("圆角不是 Glyph.CORNER_RATIO", "Glyph.CORNER_RATIO" in chrome)
        // ★ 上一版只禁了 `s * 0.11f` / `s * 0.08f` 两个字符串，抓不到真实违规 `* 0.2f`。
        //   但也不能禁"所有 `s * 0.5f`"—— 那是图形自己在 20dp 画布里的**坐标**，不是规则。
        //   真正要收口的是三条**规则量**：线宽、圆角、以及 stroke 的赋值。
        val badCorner = chrome.lines().filter { "cornerRadius" in it && Regex("""\*\s*0\.\d+f""").containsMatchIn(it) }
        assertTrue("圆角又写成裸比例了（改走 Glyph.CORNER_RATIO / INNER_RECT_CORNER_RATIO）：$badCorner", badCorner.isEmpty())
        val strokeLine = chrome.lines().firstOrNull { it.trim().startsWith("val stroke =") }
        assertTrue("找不到线宽那一行", strokeLine != null)
        assertTrue("线宽不是从 Glyph.STROKE_RATIO 算的：$strokeLine", "Glyph.STROKE_RATIO" in strokeLine!!)
    }

    /** 18 枚图标（`Theme` 第 19 轮删掉，`Alarm` 2026-10-31 随上课提醒加进来，
     *  `Dumbbell` 2026-11-01 随数智体育加进来，`Moon` / `Broom` 2026-11-08 随静音时段与
     *  分域清缓存加进来，`Eye` / `Sun` 同日随「动画与辅助显示」页加进来，`Contrast` / `Glass` 稍后随外观页加进来）：
     *  枚举条目数变了就要同步改清单。 */
    @Test
    fun `图标清单是 18 枚`() {
        assertEquals(
            "SettingIcon 条目数变了 —— 清单在 docs/B4-13-ICON-INVENTORY.md，改了枚举就同步改表",
            18,
            SettingIcon.entries.size,
        )
    }

    /**
     * 图形**留白格**与开关钮**必须真的被用上**（工单第 14 轮）：上一版只禁 `cornerRadius` 行里的裸比例，
     * 把 `RoundedCornerShape(Glyph.tileCorner)` 改回 `11.dp`、`size(Glyph.tile)` 改回 `38.dp`、
     * `size(Glyph.knob)` 改回 `22.dp` —— **三条全绿**。现在逐个点名 + 钉值。
     *
     * ## ★ 2026-11-07 改判：彩色磁贴退役，`RoundedCornerShape(Glyph.tileCorner)` 那条必需项删掉
     *
     * 依据：v2.1 稿的 `screens/settings.png` / `screens/accounts.png` 里，设置行左侧
     * **只有一枚灰色裸线图标**（像素采样：`settings.png` 四行图标 bbox x=41..79、底恒为卡面
     * `(255,253,248)`、笔画色 `(96,103,117)`；`accounts.png` 六行 x=43..59、宽 17px）——
     * 根本没有彩色方块。用户 2026-11-07 的口径是「其余页面一比一复刻」。
     * 于是 `SettingGlyph` 去掉 `.clip(...).background(tint)`，图形色从 `Color.White` 改 `tint`；
     * 行内的 38dp 磁贴换成 `Glyph.rowSlot`（26dp 留白格）。
     *
     * **没有放宽的部分**：`Glyph.tile` / `Glyph.tileCorner` / `Glyph.knob` /
     * `INNER_RECT_CORNER_RATIO` 四个值仍然钉死（档位尺不能悄悄变），
     * `Modifier.size(Glyph.rowSlot)`、`Modifier.size(Glyph.size)`、`.size(Glyph.knob)` 仍然点名，
     * 三条「改回字面量」的禁令一条不少。
     */
    @Test
    fun `留白格与开关钮必须读令牌且值不变`() {
        val chrome = text("$design/SettingsChrome.kt")
        listOf(
            "Modifier.size(Glyph.rowSlot)",
            "Modifier.size(Glyph.size)",
            ".size(Glyph.knob)",
        ).forEach { need ->
            assertTrue("SettingsChrome 里没有 `$need`（又被改回字面量了？）", need in chrome)
        }
        // 把三条"改回字面量"的具体写法也禁掉
        listOf("size(26.dp)", "size(38.dp)", "size(22.dp)").forEach { bad ->
            assertTrue("SettingsChrome 里还有字面量 `$bad`", bad !in chrome)
        }
        // ★ 2026-11-07 起 `SettingGlyph` 不再画彩色底 ⇒ 磁贴圆角那一档不该再被请求；
        //   写回来等于把退役的方块又铺回去（半改不改比不改更糟）。
        assertTrue(
            "SettingGlyph 已经改裸线稿了，这里不该再有磁贴圆角",
            "RoundedCornerShape(Glyph.tileCorner)" !in chrome,
        )
        // 四个值钉死：改常量等于改观感，必须连带改这条测试和清单
        assertEquals("Glyph.rowSlot 应为 26dp", 26f, Glyph.rowSlot.value, 0.001f)
        assertEquals("Glyph.tile 应为 38dp（退役档位尺，不许变）", 38f, Glyph.tile.value, 0.001f)
        assertEquals("Glyph.tileCorner 应为 11dp（退役档位尺，不许变）", 11f, Glyph.tileCorner.value, 0.001f)
        assertEquals("Glyph.knob 应为 22dp", 22f, Glyph.knob.value, 0.001f)
        assertEquals("INNER_RECT_CORNER_RATIO 应为 0.2", 0.2f, Glyph.INNER_RECT_CORNER_RATIO, 0.0001f)
    }
}
