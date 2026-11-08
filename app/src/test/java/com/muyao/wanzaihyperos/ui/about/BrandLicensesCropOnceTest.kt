package com.muyao.wanzaihyperos.ui.about

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 返工总单 A6 / A9 / A10 的可复跑验收（2026-11-08）。
 *
 * 三条都是「页搬了、语义改了」这一类改动，靠肉眼看截图判不准：
 * - A9：关于页内嵌三组清单搬进独立页，**条数必须仍等于数据层**，不能搬丢也不能手写补。
 * - A6：品牌页/设置行不许出现「已定稿」（业主 2026-11-07 明确说现用名不满意）。
 * - A10：取景从「拖动即落盘」改成「点应用才落盘」，即 `saveWallpaperCrop` 只许出现在应用那一段。
 */
class BrandLicensesCropOnceTest {

    private val mainRoot = "src/main/java/com/muyao/wanzaihyperos"

    private fun code(path: String): String =
        SourceScan.stripComments(File("$mainRoot/$path").readText(Charsets.UTF_8))

    private fun bodyAfter(code: String, needle: String): String {
        val at = code.indexOf(needle)
        assertTrue("找不到 $needle", at >= 0)
        return code.substring(at)
    }

    // ── A9 ────────────────────────────────────────────────────────────
    @Test
    fun `开源许可页三组都渲染数据层，条数一致`() {
        val page = code("ui/about/LicensesPage.kt")
        assertTrue("依赖组没搬过去", "Credits.dependencies.forEachIndexed" in page)
        assertTrue("改写自组没搬过去", "Credits.derived.forEachIndexed" in page)
        assertTrue("字体组没搬过去", "Credits.fonts.forEachIndexed" in page)

        // 数据层本身不能是空表 —— 否则「条数一致」是 0 == 0 的假绿。
        val deps = com.muyao.wanzaihyperos.data.Credits.dependencies.size
        val derived = com.muyao.wanzaihyperos.data.Credits.derived.size
        val fonts = com.muyao.wanzaihyperos.data.Credits.fonts.size
        assertTrue("依赖条数应当 > 0，实际 $deps", deps > 0)
        assertTrue("改写自条数应当 > 0，实际 $derived", derived > 0)
        assertTrue("字体条数应当 > 0，实际 $fonts", fonts > 0)

        // 关于页只留摘要：清单不许再内嵌。
        val about = code("ui/about/AboutPage.kt")
        assertTrue("关于页还在渲染清单条目（应已搬去 LicensesPage）", "CreditRow(c)" !in about)
        // 但摘要行要写条数，且读的是同一份数据。
        assertTrue("摘要行没读 Credits 条数", "Credits.dependencies.size" in about)
        assertTrue("摘要行没读字体条数", "Credits.fonts.size" in about)
    }

    @Test
    fun `关于页两行都能点进`() {
        val about = code("ui/about/AboutPage.kt")
        assertTrue("开源许可行没接 onClick", "onClick = onOpenLicenses" in about)
        assertTrue("品牌与图标行没接 onClick", "onClick = onOpenBrand" in about)
    }

    // ── A6 ────────────────────────────────────────────────────────────
    @Test
    fun `品牌与图标页不许出现已定稿，状态如实写待定`() {
        val brand = code("ui/about/BrandPage.kt")
        assertTrue("品牌页出现了「已定稿」", "已定稿" !in brand)
        assertTrue("品牌页没写名称状态", "待审核" in brand || "待定" in brand)

        val settings = code("ui/main/pages/SettingsSectionPage.kt")
        assertTrue("设置行右值还在写「已定稿」", "已定稿" !in settings)
        assertTrue("设置行右值没改成「待定」", "\"待定" in settings)
        assertTrue("设置行没接 onClick（还是死行）", "onClick = onOpenBrand" in settings)
    }

    @Test
    fun `品牌页写明图标由业主提供，不把生成图当最终资产`() {
        val brand = code("ui/about/BrandPage.kt")
        assertTrue("没写图标由业主提供", "由业主提供" in brand)
        assertTrue("没声明不把生成图当最终资产", "最终资产" in brand)
        assertTrue("没写未做商标检索", "商标" in brand)
        // 四个尺寸预览 32 / 48 / 64 / 96（源码用一处 listOf 驱动，别拆成四份手写）。
        assertTrue("缺四档尺寸预览", "listOf(32, 48, 64, 96)" in brand)
    }

    // ── A10 ───────────────────────────────────────────────────────────
    @Test
    fun `取景页只有点应用才落盘，取景框里不许写盘`() {
        val page = code("ui/main/pages/WallpaperCropPage.kt")
        val saves = page.split("UiPrefs.saveWallpaperCrop").size - 1
        assertEquals("saveWallpaperCrop 应当只在「应用」那一段出现一次，实际 $saves 次", 1, saves)

        // 取景框（CropEditor）读到的是草稿值，不许自己写盘。
        val editor = bodyAfter(page, "fun CropEditor(")
        assertTrue("取景框里仍在写盘（会变回「拖动即落盘」）", "saveWallpaperCrop" !in editor)

        // 取消必须原样退出：不写盘、只 onBack。
        assertTrue("没有「取消」", "取消" in page)
        assertTrue("没有「应用」", "应用" in page)
        assertTrue("没有 onBack", "onBack()" in page)
    }

    @Test
    fun `外观页不再内嵌取景框，改成一行跳转`() {
        val page = code("ui/main/pages/AppearancePage.kt")
        assertTrue("外观页还在内嵌取景框", "WallpaperCropFrame" !in page)
        assertTrue("外观页没接 onOpenCrop", "onOpenCrop" in page)
    }
}
