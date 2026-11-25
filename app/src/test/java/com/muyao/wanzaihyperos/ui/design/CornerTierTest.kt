package com.muyao.wanzaihyperos.ui.design

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B4-14 ③：圆角**分档**的守卫。
 *
 * ## 为什么要分档（工单第 22 轮那条）
 *
 * `Glyph.tileCorner`（11dp）原本是 **38dp 列表行磁贴**那一枚彩色底的圆角 ——
 * `SettingsChrome.SettingGlyph` 在用它。而外观页把它当"通用圆角"用了 **10 处**：
 * `:1245` 是 36dp 的胶囊控件、`:1466` 是**整屏宽**的取景框 —— 同一档位服务三种尺寸，
 * 观感必然不一致；而且画布那边 `.wp{border-radius:13px}` 的换算值（13 × 0.886 = 11.5dp）
 * 跟 11dp 也对不上（同一格两块半径）。
 *
 * 处置**不是**抬 `tileCorner` 的值 —— 那是回归面（38dp 磁贴的观感会跟着变）——
 * 而是**加一档令牌并把调用点分流**：
 *
 * | 令牌 | 值 | 给谁用 |
 * | --- | --- | --- |
 * | `Dims.chipCorner` | 8dp | 小元件 / 胶囊 |
 * | `Glyph.tileCorner` | 11dp | **只**给 38dp 列表行磁贴（**2026-11-07 退役，零调用**） |
 * | `Dims.wallpaperTileCorner` | 11.5dp | 牆紙格（设计板 `.wp`） |
 * | `Dims.containerCorner` | 14dp | 小托盘（提示条、搜索列） |
 * | `Dims.cardCorner` | 18dp | 卡片与大面 |
 *
 * ## 变异验收
 *  - 外观页某处改回 `Glyph.tileCorner` ⇒ 「外墙纸格不许用列表行那档」红
 *  - `Glyph.tileCorner` 从 11 抬到 12 ⇒ 「令牌值钉死」红
 *  - 外观页写 `RoundedCornerShape(11.5.dp)` ⇒ 「外观页圆角只走令牌」红
 */
class CornerTierTest {

    private val mainRoot = "src/main/java/com/muyao/wanzaihyperos"

    private fun bodyAfter(code: String, marker: String): String {
        val i = code.indexOf(marker)
        if (i < 0) return ""
        val rest = code.substring(i)
        val next = Regex("""\n\s*(@\w+\s+)*(private |internal |public )?fun\s""").find(rest, 1)
        return if (next == null) rest else rest.substring(0, next.range.first)
    }

    @Test
    fun `圆角分档的令牌值钉死`() {
        assertEquals("Glyph.tileCorner 应为 11dp（38dp 列表行磁贴专用，不许抬）", 11f, Glyph.tileCorner.value, 0.001f)
        assertEquals("Dims.wallpaperTileCorner 应为 11.5dp（设计板 .wp 13px × 0.886）", 11.5f, Dims.wallpaperTileCorner.value, 0.001f)
        assertEquals("Dims.chipCorner 应为 8dp", 8f, Dims.chipCorner.value, 0.001f)
        assertEquals("Dims.containerCorner 应为 14dp", 14f, Dims.containerCorner.value, 0.001f)
        assertEquals("Dims.cardCorner 应为 18dp", 18f, Dims.cardCorner.value, 0.001f)
    }

    @Test
    fun `档位是有序的`() {
        val ladder = listOf(
            "chipCorner" to Dims.chipCorner.value,
            "wallpaperTileCorner" to Dims.wallpaperTileCorner.value,
            "containerCorner" to Dims.containerCorner.value,
            "cardCorner" to Dims.cardCorner.value,
        )
        ladder.zipWithNext { (aName, a), (bName, b) ->
            assertTrue("档位反了：$aName(${a}dp) 不该大于 $bName(${b}dp)", a <= b)
        }
    }

    /**
     * ★ 2026-11-07 改判：`Glyph.tileCorner` 从「只许出现在磁贴那一处」改成「**全工程零调用**」。
     *
     * 依据：v2.1 稿的设置行没有彩色磁贴（`screens/settings.png` 四行图标底恒为卡面
     * `(255,253,248)`），`SettingGlyph` 已改裸线稿、行内换成 `Glyph.rowSlot`（26dp 留白格）。
     * 磁贴退役后这一档圆角**不该再被任何地方请求**；令牌值本身（11dp）仍由
     * [圆角分档的令牌值钉死] 钉住 —— 档位尺不删，只是不再有调用点。
     *
     * 判据方向反过来：以前是「必须有且只在这里」，现在是「一个都不许有」。
     */
    @Test
    fun `列表行那档圆角已经退役，全工程零调用`() {
        val offenders = SourceScan.sources()
            .filter { "Glyph.tileCorner" in it.second }
            .map { it.first }
        assertTrue(
            "Glyph.tileCorner 已随彩色磁贴退役（2026-11-07），不许再被任何源码请求：$offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `外观页的圆角只走令牌`() {
        val page = File("$mainRoot/ui/main/pages/AppearancePage.kt")
        assertTrue("找不到外观页", page.isFile)
        val code = SourceScan.stripComments(page.readText(Charsets.UTF_8))

        assertTrue(
            "外观页不许再用 Glyph.tileCorner（那是 38dp 列表行磁贴的档位）",
            "Glyph.tileCorner" !in code,
        )
        val literals = Regex("""RoundedCornerShape\(\s*\d+(\.\d+)?\.dp""").findAll(code).map { it.value }.toList()
        val radius = Regex("""cornerRadius\s*=\s*\d+(\.\d+)?\.dp""").findAll(code).map { it.value }.toList()
        assertTrue("外观页的圆角不许写死 dp 字面量：$literals$radius", literals.isEmpty() && radius.isEmpty())

        assertTrue("牆紙格必须走 Dims.wallpaperTileCorner", "Dims.wallpaperTileCorner" in code)
        assertTrue("胶囊控件必须走 Dims.chipCorner", "Dims.chipCorner" in code)
        assertTrue("卡片与大面必须走 Dims.cardCorner", "Dims.cardCorner" in code)
    }

    @Test
    fun `三处不同尺寸的格子各走各的档`() {
        val page = File("$mainRoot/ui/main/pages/AppearancePage.kt")
        val code = SourceScan.stripComments(page.readText(Charsets.UTF_8))

        val tile = bodyAfter(code, "fun WallpaperThumbnail(")
        assertTrue("牆紙格（WallpaperThumbnail）必须走 Dims.wallpaperTileCorner", "Dims.wallpaperTileCorner" in tile)

        val choice = bodyAfter(code, "fun VisualChoice(")
        assertTrue("壁纸选择卡外框必须复用玻璃按钮，不能自画第二套壳", "WanzaiButton(" in choice)
        val adapter = SourceScan.stripComments(File("$mainRoot/ui/components/WanzaiGlasmioControls.kt").readText(Charsets.UTF_8))
        val button = bodyAfter(adapter, "fun WanzaiButton(")
        assertTrue("按钮须保留厂商玻璃与减弱动态的同形后备", "GlasmioButton(" in button && "shape = CircleShape" in button)
        assertTrue("壁纸选择卡（VisualChoice）预览图必须走 Dims.wallpaperTileCorner", "Dims.wallpaperTileCorner" in choice)

        // A10：取景框搬去独立页（`WallpaperCropPage.kt` 的 `CropEditor`）。
        // 断言不删，改指向新文件；外观页原本那一条由 HomeSkyTest 的守卫兜住。
        val cropPage = SourceScan.stripComments(
            File("$mainRoot/ui/main/pages/WallpaperCropPage.kt").readText(Charsets.UTF_8),
        )
        val crop = bodyAfter(cropPage, "fun CropEditor(")
        assertTrue("整屏取景框（CropEditor）必须走 Dims.cardCorner", "Dims.cardCorner" in crop)
    }
}
