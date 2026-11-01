package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * 外观页壁纸格的守卫（2026-11-01 真机两处缺陷）。
 *
 * ## 缺陷一：相册格明明存过图却显示「+」
 *
 * `val wallpaper = remember(rev, preset) { if (preset == SkyPreset.ALBUM) decodeWallpaper(…) else null }`
 * —— 只有**当前选中的就是相册**才去解码。它本来没事（那格只在选中时才有内容），
 * 但改成 3 列网格里一格常驻缩略图之后，它变成「存过图也显示 +」，
 * 与「从没存过」看不出区别。缩略图必须与选中态无关。
 *
 * ## 缺陷二：格内白字压不住（实测 2.31~2.42:1，合格线 4.5）
 *
 * 小样的底边是**真的淡到纸色**了（`SkyPreview` 走 `fadeTo = pageBg`），
 * 而旧压暗条只有 21dp 高、末端 0.38 且起点全透明 ⇒ 文字带落在几乎没被压到的位置。
 * 这条判据把压暗条的**几何与 alpha 从源码里读出来**、按 WCAG 算一遍，
 * 而不是去数「height(26.dp)」这种字面量 —— 后者改了高度就会假绿。
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

    // ---- 1. 缩略图与选中态解耦 --------------------------------

    @Test
    fun `相册缩略图只在 rev 变了才重解，不看当前选中哪一档`() {
        val src = code()
        val line = src.lines().firstOrNull { "val wallpaper = remember(" in it }
        assertTrue("找不到 wallpaper 的 remember：解码口径不明", line != null)
        assertTrue(
            "相册图必须与选中档无关，现在仍然带着 preset 条件：$line",
            "preset" !in line!!,
        )
        assertTrue("解码没有跟着 rev 走，换图后不会刷新：$line", "rev" in line)
    }

    @Test
    fun `相册格画出存过的那张，而不是只在选中时画`() {
        val body = bodyOf(code(), "private fun AlbumTile(")
        assertTrue(
            "AlbumTile 里画缩略图仍然要求「并且被选中」，非相册档下这格又会退化成「+」",
            Regex("""wallpaper\s*!=\s*null\s*&&\s*on""").containsMatchIn(body).not(),
        )
        assertTrue("AlbumTile 必须能把图字节画出来", "bitmap = wallpaper" in body)
    }

    // ---- 2. 压暗条的覆盖量够不够 ------------------------------

    /** 真机量出来的最坏底：小样淡到纸色那一段 (209,215,220)（[local]/ap4.png x=100,y=1360）。 */
    private val worstBackdrop = Triple(209, 215, 220)

    /**
     * 名字带：距格底 3.5dp（`padding(bottom = 3.5.dp)`）往上一个字高。
     * 字高是**量出来的**：真机 dump 里六格标签的 bounds 高度都是 53px，53 / 3.5 = 15.1dp。
     */
    private val labelNearDp = 3.5f
    private val labelFarDp = 3.5f + 15.1f

    private fun luminance(r: Int, g: Int, b: Int): Double {
        fun f(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
    }

    /** 从源码里抠出压暗条：高度 dp + 各站 (位置, alpha)。 */
    private fun scrim(body: String, who: String): Pair<Float, List<Pair<Float, Float>>> {
        val h = Regex("""\.height\(([\d.]+)\.dp\)""").find(body)
        assertTrue("$who 里找不到压暗条的高度", h != null)
        val height = h!!.groupValues[1].toFloat()
        val gradStart = body.indexOf("Brush.verticalGradient(")
        assertTrue("$who 里找不到压暗条的渐变", gradStart >= 0)
        // 按括号配对取出整个 verticalGradient(...) 调用 —— 用「下一个换行后的第一个右括号」
        // 会截在 `Color.Black.copy(alpha = 0.30f` 中间，后面的站一个都读不到（第一版就这么假绿）。
        var depth = 0
        var end = gradStart
        while (end < body.length) {
            if (body[end] == '(') depth++
            if (body[end] == ')') {
                depth--
                if (depth == 0) break
            }
            end++
        }
        val grad = body.substring(gradStart, end + 1)
        val stops = mutableListOf<Pair<Float, Float>>()
        Regex("""([\d.]+)f\s+to\s+Color\.Black\.copy\(alpha\s*=\s*([\d.]+)f\)""")
            .findAll(grad)
            .forEach { stops += it.groupValues[1].toFloat() to it.groupValues[2].toFloat() }
        Regex("""([\d.]+)f\s+to\s+Color\.Transparent""")
            .findAll(grad)
            .forEach { stops += it.groupValues[1].toFloat() to 0f }
        val sorted = stops.sortedBy { it.first }
        val full = mutableListOf<Pair<Float, Float>>()
        if (sorted.firstOrNull()?.first != 0f) full += 0f to 0f
        full += sorted
        if (sorted.lastOrNull()?.first != 1f) full += 1f to (sorted.lastOrNull()?.second ?: 0f)
        return height to full
    }

    /** 渐变上 f 处的 alpha（站间线性）。 */
    private fun alphaAt(stops: List<Pair<Float, Float>>, f: Float): Float {
        stops.zipWithNext().forEach { (a, b) ->
            if (f >= a.first && f <= b.first) {
                val span = b.first - a.first
                if (span <= 0f) return b.second
                return a.second + (b.second - a.second) * ((f - a.first) / span)
            }
        }
        return stops.last().second
    }

    @Test
    fun `格内白字压在压暗条上够 4_5 比 1`() {
        val src = code()
        val worst = mutableListOf<String>()
        listOf(
            "private fun WallpaperTile(" to "壁纸格",
            "private fun AlbumTile(" to "相册格",
        ).forEach { (header, who) ->
            val (height, stops) = scrim(bodyOf(src, header), who)
            // 名字带距格底 3.5~19.5dp ⇒ 渐变自顶向下的位置 f = 1 - 距底 / 条高
            val bandNear = 1f - labelFarDp / height
            val bandFar = 1f - labelNearDp / height
            val probes = mutableListOf(bandNear, bandFar)
            (0..20).forEach { probes += bandNear + (bandFar - bandNear) * it / 20f }
            val minAlpha = probes.minOf { alphaAt(stops, it) }
            val (r, g, b) = worstBackdrop
            val bg = luminance(
                (r * (1 - minAlpha)).toInt().coerceIn(0, 255),
                (g * (1 - minAlpha)).toInt().coerceIn(0, 255),
                (b * (1 - minAlpha)).toInt().coerceIn(0, 255),
            )
            val cr = 1.05 / (bg + 0.05)
            if (cr < 4.5) worst += "$who 条高 ${height}dp、覆盖下限 $minAlpha ⇒ 白字只有 $cr"
        }
        assertTrue("格内名字在白字上压不住（< 4.5:1）：$worst", worst.isEmpty())
    }

    @Test
    fun `压暗条不能是从透明一路拉到底，名字那一带要够深`() {
        val body = bodyOf(code(), "private fun WallpaperTile(")
        val (height, stops) = scrim(body, "壁纸格")
        assertTrue("压暗条只有 ${height}dp，装不下一整条字带", height >= 26f)
        val deepest = stops.maxOf { it.second }
        assertTrue("压暗条最深一站只有 $deepest，白字要 4.5:1 至少要 0.50", deepest >= 0.50f)
    }
}
