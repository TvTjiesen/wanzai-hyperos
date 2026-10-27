package com.muyao.wanzaihyperos.ui.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B4-02 的**机制守卫**（工单第 15 轮点名：`clipToBounds` 删掉 667 条照样全绿；
 * 第 22 轮再点名：三条全是「整串字面量包含」，一改措辞就假红，且**根本不测接缝数值**）。
 *
 * 这条守的是**四件事**，前两件是接线（源码事实），后两件是**数值**（问生产函数要答案）：
 *  ① 天空画布必须 `clipToBounds()` —— 相册档 `drawCoverBitmap` 会平移放大，
 *     不裁剪就把照片溢到画布外（实测溢出 79px，就是业主说的那条割裂线）；
 *  ② 画布底边那一段必须淡进**页面同一支渐变**（`pageGradient`）——
 *     实色 `fadeTo` 与页面对角渐变在 y=875 差 2.71 灰阶（30 行均值差 2.27 = 持续位移）；
 *  ③ 分界取整 + 羽化带 0.24h，且天空在画布底边**残留 alpha = 0**
 *     （这就是「画布内最后一行 == 画布外第一行」的充要条件）；
 *  ④ 山棱必须**自己**在画布底边之前溶完（`HILL_FEATHER` 羽化到 0），
 *     底边那条"实色收口"不许回来 —— 它就是 y=704 那条台阶（STEP 2.10 / PEAK 2.38）。
 *
 * 变异验收：删 `clipToBounds()` ⇒ ① 红；把实色收口加回来 ⇒ ④ 红。
 */
class SkySeamGuardTest {

    /**
     * 天空画布 256dp，560dpi（3.5x）⇒ **896px**。
     *
     * 256dp 是业主 2026-10-25「首页壁纸的范围可以稍微靠下一点点，大概是卡片的上三分之一吧」
     * 定下来的：天空 alpha 收 0 的行 = `0.78h`，真机卡顶 y=505、卡高 586 ⇒ 卡的上三分之一 = y≈700，
     * 解 `0.78h = 700` ⇒ h ≈ 897 ⇒ 取整到 256dp（896px）⇒ 收 0 落 698px。
     */
    private val canvasPx = 896f

    /** 560dpi ⇒ 1dp = 3.5px。 */
    private val density = 3.5f

    private fun src(): String {
        val f = File("src/main/java/com/muyao/wanzaihyperos/ui/home/HomeSky.kt")
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    /**
     * **去掉注释**的源码：注释里提一句"这里以前画过 xxx"不该让守卫变红，
     * 反过来也不能靠注释把守卫骗绿 —— 只看真代码。
     */
    private fun code(): String = src()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `天空画布必须裁剪`() {
        val text = code()
        assertTrue(
            "HomeSky 的 Canvas 没 `clipToBounds()`（相册照片会溢出画布 79px）",
            "Canvas(modifier = modifier.fillMaxSize().clipToBounds())" in text,
        )
    }

    @Test
    fun `画布底边要淡进页面同一支渐变`() {
        val text = code()
        assertTrue("HomeSky 没有把页面渐变传下去", "pageGradient = pageGradient" in text)
        assertTrue("没有把整屏高度传下去（几何对不上）", "pageHeightPx = pageHeightPx" in text)
        assertTrue(
            "底边那一段没画渐变（又退回实色 fadeTo 了？）",
            "drawRect(brush = pageGradient" in text,
        )
        assertTrue("没有 `pageGradient != null && pageHeightPx > 0f` 的分支", "pageGradient != null && pageHeightPx > 0f" in text)
    }

    @Test
    fun `分界必须取整且羽化带是 0_24h`() {
        // 问生产函数要答案，不再扫字符串：改措辞不会假红，改数值必红。
        val skyBottom = skyBottomY(canvasPx, compact = false)
        assertEquals(
            "天空底边必须落在整像素（半像素会合成 1px 暗线）",
            skyBottom, skyBottom.toInt().toFloat(), 0f,
        )
        assertEquals("普通档天空底边 = 0.80h ⇒ int(716.8) = 716", 716f, skyBottom, 0f)
        assertEquals("小样档天空底边 = 0.86h ⇒ int(770.56) = 770", 770f, skyBottomY(canvasPx, compact = true), 0f)

        assertEquals("羽化带比例必须是 0.24h", 0.24f, SKY_FADE_SPAN, 0f)

        val fadeStart = skyFadeStartY(canvasPx, skyBottom)
        assertEquals("羽化带起点必须落在整像素", fadeStart, fadeStart.toInt().toFloat(), 0f)
        // int(716 − 896×0.24) = int(500.96) = 500 ⇒ 带长 716 − 500 = 216
        assertEquals("羽化带长度必须是 0.24h ⇒ 216px", 216f, skyBottom - fadeStart, 0f)
        assertTrue("羽化带必须结束在天空底边上", fadeStart < skyBottom)
    }

    @Test
    fun `画布内最后一行必须等于画布外第一行`() {
        val skyBottom = skyBottomY(canvasPx, compact = false)
        val zeroRow = skyAlphaZeroRow(canvasPx, compact = false)

        // 天空在画布底边**一点残留都不许有**：这一行往下只剩页面那支渐变，
        // 与画布外第一行同色 ⇒ 画布内最后一行 == 画布外第一行。
        assertTrue(
            "天空在画布底边还有残留（$zeroRow > ${skyBottom.toInt()}）：画布内最后一行会 ≠ 画布外第一行",
            zeroRow <= skyBottom.toInt(),
        )
        assertTrue(
            "天空淡尽的位置离画布底边太近（余量 ${skyBottom.toInt() - zeroRow}px）",
            skyBottom.toInt() - zeroRow >= 8,
        )
        assertTrue("画布底边必须在画布内（否则这条判据没意义）", skyBottom < canvasPx)

        // 渐变末端 alpha 必须是 0 且保持平 —— 平的那一段是「上 30 行均值 == 下 30 行均值」的来源。
        val stops = featherStops(Color.White, transparent = true)
        assertEquals("羽化带末端的天空 alpha 必须是 0", 0f, stops.last().second.alpha, 0f)
        assertEquals("alpha 收 0 之后必须保持平（否则接缝 30 行被斜坡拉出 ~1.8 灰阶）", 0f, stops[stops.size - 2].second.alpha, 0f)
    }

    /**
     * 相册档淡入带的**底色**不许是平涂的页面底色。
     *
     * 2026-10-25 第 40 轮实测（verify/b4-02/r5_album_4034d05f_a.png，模拟器 560dpi）：
     * 上一版这一笔是 `featherStops(fadeTo)` —— `fadeTo` 是 `colorScheme.background`
     * 这个**单一颜色**，而带下第一行铺的是 `pageGradient`（对角渐变）。两者只在某些 x 上
     * 恰好相等，于是 y=699→700 出现一条**方向随 x 翻转**的台阶：左条 x[2,18) 亮 1.38、
     * 右条 x[1200,1214) 暗 1.78 灰阶（判据 STEP_MAX 0.5）；两帧各自内部水平极平
     * （max|dq| 0.22/0.25）⇒ 是边界，不是照片也没有噪声。
     *
     * 做法必须是「把同一支 pageGradient 铺进离屏层，再用纵向 alpha DstIn 削出斜坡」：
     * 带底 alpha=1 ⇒ 恰好等于带下第一行。改成平涂单色就红。
     */
    @Test
    fun `相册淡入带的底色必须是页面那支渐变_不是平涂单色`() {
        val text = code()
        // 只截相册那一支：外观小样那条"淡到实色"的老路（pageGradient == null）本来就用平涂，
        // 不能因为它在就把守卫变红 —— 守的是**有页面渐变时**那一支。
        val from = text.indexOf("&& albumPhoto) {")
        assertTrue("找不到相册支的开头（分支被改名了？）", from >= 0)
        val to = text.indexOf("} else", from)
        assertTrue("找不到相册支的结尾", to > from)
        val album = text.substring(from, to)
        assertTrue(
            "相册淡入带又用平涂 fadeTo 收尾了 —— 那是 y=700 那条左右方向相反的台阶（0.74/1.80 灰阶）",
            "featherStops(fadeTo)" !in album,
        )
        assertTrue(
            "淡入带没把 pageGradient 铺进离屏层（底色会与带下第一行不等）",
            "drawRect(brush = pageGradient" in text,
        )
        assertTrue(
            "淡入斜坡必须用 DstIn 的纵向 alpha 蒙版削出来",
            "featherStops(Color.White)" in text && "blendMode = BlendMode.DstIn" in text,
        )
        assertTrue(
            "削斜坡前必须开离屏层（saveLayer），否则 DstIn 会啃掉整块画布",
            "canvas.saveLayer(Rect(0f, fadeStart, w, fadeEnd), Paint())" in text,
        )
    }

    @Test
    fun `山棱必须自己在画布底边之前溶完`() {
        val band = HILL_BAND_DP * density
        val skyBottom = skyBottomY(canvasPx, compact = false)
        val zeroY = hillAlphaZeroY(skyBottom, band)

        assertEquals("山棱带高度 = 99dp = 346.5px", 346.5f, band, 0f)
        assertTrue(
            "山棱 alpha 必须在画布底边之前收到 0（实测 $zeroY vs $skyBottom）",
            zeroY <= skyBottom - 8f,
        )

        val stops = hillFeatherStops()
        assertEquals("山棱羽化末端 alpha 必须是 0", 0f, stops.last().second, 0f)
        assertEquals(
            "山棱羽化末端必须保持平",
            0f, stops[stops.size - 2].second, 0f,
        )
    }

    /**
     * **画布高度必须与调用点那个盒子同源**（业主 2026-10-25 第 1 条）。
     *
     * 上面几条用的 `canvasPx = 896f` 是**测试自己写的常量**，生产里那个盒子
     * 是 `HomeSectionPage.kt` 的 `height(256.dp)`。两边只要不一致，
     * 上面全绿而真机上是另一回事 —— 那正是「自报完成」。所以这里把 dp 值读出来对一遍：
     * 改生产数字不改这条，立刻红。
     */
    @Test
    fun `天空盒高度必须与测试常量同源`() {
        val page = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/HomeSectionPage.kt")
        assertTrue("找不到 ${page.absolutePath}", page.isFile)
        val text = page.readText(Charsets.UTF_8)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")
        val m = Regex("""height\(if \("courses" in UiPrefs\.homeModules\) ([\d.]+)\.dp else ([\d.]+)\.dp\)""")
            .find(text)
        assertTrue("HomeSectionPage 里找不到那句 height(if (\"courses\" …) N.dp else M.dp) —— 结构变了就同步改这条守卫", m != null)
        val withCourses = m!!.groupValues[1].toFloat()
        assertEquals(
            "天空盒高度 ${withCourses}dp × 3.5 ≠ 测试里的 canvasPx ${canvasPx}px ⇒ 判据与生产脱节",
            withCourses * density,
            canvasPx,
            0f,
        )
        assertEquals(
            "「今日课程」关着那一档仍是 150dp（没有「此刻」卡，切线不能穿过「要处理」那行）",
            150f,
            m.groupValues[2].toFloat(),
            0f,
        )
    }

    @Test
    fun `画布底边不许再补实色收口`() {
        val text = code()
        assertTrue(
            "又补回一条实色收口了 —— 那就是 y=704 那条台阶（STEP 2.10 / PEAK 2.38，判据 ❌）",
            "skyBottom + 4f" !in text,
        )
        assertTrue(
            "不许把页面底色当不透明刷画过天空底边（实色 vs 页面对角渐变 ⇒ 右边缘差一档）",
            "1f to fadeTo," !in text,
        )
    }
}
