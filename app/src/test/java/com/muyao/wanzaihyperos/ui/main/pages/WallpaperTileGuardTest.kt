package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 外观页壁纸格的守卫（2026-11-01 真机两处缺陷）。
 *
 * 壁纸格已统一成 [WallpaperThumbnail]（相册、导入图与自带图同一套格子）；
 * 这条守的还是两件事：
 *  ① 缩略图解码与「当前选中的是不是这一档」无关 —— 存过图就必须画出来；
 *  ② 格子上的勾选徽标必须落在不透明底色上，不能直接压照片（对比度）。
 */
class WallpaperTileGuardTest {

    private val pageFile =
        "src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppearancePage.kt"

    private fun code(): String {
        val f = File(pageFile)
        assertTrue("找不到外观页源码：$pageFile", f.isFile)
        return SourceScan.stripComments(f.readText(Charsets.UTF_8))
    }

    /** 取某个 composable 的函数体（从声明花括号配对到收尾）。 */
    private fun bodyOf(src: String, header: String): String {
        val i = src.indexOf(header)
        assertTrue("源码里找不到 $header", i >= 0)
        var depth = 0
        var j = i
        while (j < src.length) {
            if (src[j] == '{') depth++
            if (src[j] == '}') {
                depth--
                if (depth == 0) return src.substring(i, j + 1)
            }
            j++
        }
        return src.substring(i)
    }

    @Test
    fun `相册缩略图只跟随图库和图片身份，不看当前选中哪一档`() {
        val body = bodyOf(code(), "private fun WallpaperThumbnail(")
        val loading = body.substringAfter("var image by").substringBefore("val colors = MiuixTheme.colorScheme")
        assertTrue("解码须按 entry.id 缓存", "remember(library, entry.id) { mutableStateOf<LoadedWallpaper?>(null) }" in body)
        assertTrue("解码须由 entry 文件发起", "loadWallpaper(library.file(entry)" in loading)
        assertFalse("缩略图解码不得看当前选中哪一档", "selected" in loading)
        assertFalse("缩略图解码不得看 preset", "preset" in loading)
        assertTrue("取消读取不得发布到已离开或复用的图格", loading.indexOf("ensureActive()") in 0 until loading.indexOf("image = decoded"))
        assertTrue("取消必须继续传播", loading.contains("throw cancel"))
        assertFalse("解码失败不得删除私人图片", loading.contains(".delete("))
    }

    @Test
    fun `相册格画出存过的那张，而不是只在选中时画`() {
        val body = bodyOf(code(), "private fun WallpaperThumbnail(")
        assertTrue("缩略图必须把图字节画出来", "image?.let { Image(it.bitmap" in body)
        assertFalse(
            "缩略图不能要求「并且被选中」才画，非选中档会退化成空框",
            Regex("""image\s*!=\s*null\s*&&\s*selected""").containsMatchIn(body),
        )
        assertTrue("勾选徽标仍按选中态显示", "if (selected) Text(\"✓\"" in body)
    }

    @Test
    fun `格内勾选徽标压在非透明底色上`() {
        val body = bodyOf(code(), "private fun WallpaperThumbnail(")
        val badge = body.substringAfter("if (selected) Text(\"✓\"")
        assertTrue("勾选徽标没有不透明底色，压在照片上对比度不可控", ".background(colors.surface" in badge)
        assertTrue("勾选徽标的 ✓ 必须用主色", "color = colors.primary" in badge)
    }
}