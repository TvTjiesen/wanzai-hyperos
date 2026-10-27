package com.muyao.wanzaihyperos.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.glass.BarMaterial
import com.muyao.wanzaihyperos.ui.design.glass.cardFaceAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 天空画布的**几何守卫**（v3 清单 #2，独立审计第 6 轮必修 7）。
 *
 * 第 5/6 轮连抓三次同一类错：`Brush.verticalGradient(…)` 配 `size = Size(w, skyBottom)` 时
 * **不写 `startY/endY`**，那个 brush 就按整块 DrawScope（656/875px）解算 ——
 * 于是 `look.bottom` 永远走不到、中段变成一大段死平（真机 482 行 / 模拟器 359 行），
 * 而板上是三站一路变亮。业主报的「糊」到第 5 轮才被定位：前四轮都在改颜色，没人问几何。
 *
 * 这类错编译期不报、跑起来也不像 bug（画面只是"淡了一点"），肉眼在缩放截图上量不出来，
 * 但可以从源码静态判：**配了 skyBottom 的尺寸就必须配 startY**。
 */
class HomeSkyTest {

    private val src = File("src/main/java/com/muyao/wanzaihyperos/ui/home/HomeSky.kt")

    @Test
    fun `画到 skyBottom 的竖向渐变必须自己写明 startY`() {
        assertTrue("找不到 ${src.absolutePath}", src.isFile)
        val lines = src.readLines()
        val offenders = mutableListOf<Int>()
        lines.forEachIndexed { i, line ->
            if (!line.contains("Brush.verticalGradient(")) return@forEachIndexed
            // 只看这个调用的体：到它自己的收尾 `),` 之前（最多 12 行），
            // 再确认它是不是配了 `size = Size(w, skyBottom)`。
            val window = lines.drop(i).take(12)
            val drawsSkyBottom = window.take(10).any { it.contains("size = Size(w, skyBottom)") }
            val hasStartY = window.takeWhile { !it.contains("size = Size(w, skyBottom)") }
                .any { it.contains("startY") }
            if (drawsSkyBottom && !hasStartY) offenders += i + 1
        }
        assertTrue(
            "这些渐变画到 skyBottom 却没写 startY/endY ⇒ 按整块画布解算，底色走不到、" +
                "中段死平（第 5/6 轮同一类错，已第三次）：行 $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `AUTO 白天那两支必须带板上 46% 的中间站`() {
        val text = src.readText()
        // 板 `.atmos`：#A9CBD8 0% → #C9DEE5 46% → #F1E9DC 100%（wz-v2.html:166）。
        val windows = listOf("in 8..11 -> SkyLook(", "in 12..16 -> SkyLook(").map { key ->
            val i = text.indexOf(key)
            text.substring(i, i + 400)
        }
        windows.forEach {
            assertTrue(
                "白天档缺 0xFFC9DEE5 那一站 ⇒ 0~55% 会画成一条水平色带（业主的「糊」）",
                "0xFFC9DEE5" in it,
            )
        }
    }
    @Test
    fun `壁纸降采样闸门必须按 或 判，不许退回与`() {
        val src = java.io.File("src/main/java/com/muyao/wanzaihyperos/ui/home/HomeStudio.kt")
        assertTrue("找不到 ${src.absolutePath}", src.isFile)
        // 认那行闸门本身（`/ (sample * 2)`），不是 `inSampleSize = sample` 那行赋值
        val line = src.readLines().firstOrNull { it.contains("/ (sample * 2)") }
        assertTrue("找不到降采样闸门那一行", line != null)
        assertTrue(
            "闸门必须是 `||`：`&&` 要求两边都还超上限才继续缩，而 4:3 照片的高永远到不了 3200 ⇒ " +
                "4032×3024 卡在 inSampleSize=1（46.5MiB）、8000×6000 同理（183MiB），" +
                "外层 runCatching 还会把 OOM 吞成 null、界面一个字都不说。行：$line",
            line!!.contains("||") && !line.contains("&&"),
        )
        // 按闸门算一遍落带（与实现同一条式子），钉住"缩到不超上限"这件事。
        fun sampleOf(w: Int, h: Int, maxWidth: Int = 1440, maxHeight: Int = 3200): Int {
            var s = 1
            while (w / (s * 2) >= maxWidth || h / (s * 2) >= maxHeight) s *= 2
            return s
        }
        // 闸门语义是"再缩一半仍不小于上限才继续"⇒ 4032×3024 落 sample=2（2016×1512 ≈ 12.2MiB）。
        // 要紧的不是那 4 还是 2，而是**不许停在 1**（46.5MiB，runCatching 还会把 OOM 吞成 null）。
        assertEquals("4032×3024 手机原图", 2, sampleOf(4032, 3024))
        // 8000×6000：同样按"再缩一半仍不小于上限"⇒ sample=4（2000×1500 ≈ 12.2MiB，
        // 而不是卡死在 1 的 **183MiB**）。这条才是台账那条 OOM 雷的量级。
        assertEquals("8000×6000", 4, sampleOf(8000, 6000))
        assertTrue("小图不许被无谓缩糊", sampleOf(1200, 900) == 1)
        // cover 裁切的 slack 必须 >= 0（负数意味着"缩完还盖不满框"，会露白边）
        assertTrue(
            "降采样后（2016×1512）铺满 1:0.62 的取景框时 slack 必须非负，否则露白边",
            slackNonNegative(imgW = 2016, imgH = 1512, boxW = 1080f, boxH = 671f),
        )
    }

    private fun slackNonNegative(imgW: Int, imgH: Int, boxW: Float, boxH: Float): Boolean {
        val scale = maxOf(boxW / imgW, boxH / imgH)
        return imgW * scale >= boxW - 0.5f && imgH * scale >= boxH - 0.5f
    }

    @Test
    fun `手势检测器的 key 里不许出现取景三元组`() {
        // 「拖到一半变黏、要重新按一次才动」的标准成因：把**回调自己写入的状态**放进了
        // `pointerInput(...)` 的 key ⇒ 每个指针事件都取消并重启检测器、累积锚点被清零。
        // 这条能静态判（第 1 轮台账原话），不该留给录屏。
        val f = java.io.File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppearancePage.kt")
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        val keys = f.readLines().filter { it.contains(".pointerInput(") }
        assertTrue("外观页找不到 pointerInput", keys.isNotEmpty())
        val bad = keys.filter { k ->
            val arg = k.substringAfter("pointerInput(").substringBefore(")")
            listOf("panX", "panY", "zoom").any { arg.contains(it) }
        }
        assertTrue(
            "这些 pointerInput 的 key 带了取景三元组（会每帧重启检测器）：$bad。" +
                "key 只该留 wallpaper，其余走 rememberUpdatedState 读最新值。",
            bad.isEmpty(),
        )
        assertTrue(
            "取景量必须由 rememberUpdatedState 取（否则手势读到的是旧值）",
            f.readText().contains("rememberUpdatedState(zoom)"),
        )
    }

    /**
     * 数值钉（第 6 轮 P0#3b）：审计跑过四个变异体，旧版"写法扫描"**全部放绿** ——
     * `endY` 改回 `h` 绿、删掉 `mid` 那一支的 `startY/endY` 绿、
     * **删掉 `0.46f to look.mid` 整站（死平复活）绿**、把 `mid` 挪到 0.90 也绿。
     * 所以这一组不判"写没写过"，判"算出来对不对"。
     */
    @Test
    fun `天空几何三个停靠点的算式必须钉死`() {
        // 板上：`linear-gradient(180deg,#A9CBD8 0%,#C9DEE5 46%,#F1E9DC 100%)`（wz-v2.html:166），
        // `.atmos{height:220px}`、天空画布 256dp（业主 2026-10-25 要求壁纸再往下）、非紧凑档 `skyBottom = 0.80h`。
        val boxH = 256f * 3.5f                      // 真机 560dpi ⇒ 896px
        // 问生产函数要底边，不再自己乘一遍：`skyBottomY` 会取整，自己乘会得到 716.8 这种不存在的像素。
        val skyBottom = skyBottomY(boxH, compact = false)   // = 716
        assertEquals("天空底边必须取整", 716f, skyBottom, 0f)
        val midStop = SKY_MID_STOP * skyBottom
        assertEquals("mid 那一站的像素位置 = 0.46 × 716", 329.36, midStop.toDouble(), 0.5)
        // 第 5 轮那条"0.55 段"必须彻底不存在：0.55 站 = 385px 会造出一条 385 行的水平带
        assertTrue(0.55f * skyBottom > midStop)
        // cover 的 slack（第 6 轮 P0#2 修的是被除数）：四组输入都必须 ≥ 0 且等于解析式。
        // ★ 第 7 轮：改成**直接调 `coverSlackPx`** —— 上一版这里把算式在测试里重抄了一遍，
        //   所以函数被改回 `(boxW * scale - boxW)/2` 那个错版时，测试照样绿（虚假绿灯）。
        val cases = listOf(
            Triple(4032, 3024, 0), Triple(8000, 6000, 0), Triple(2016, 1512, 0), Triple(1080, 720, 0),
        )
        cases.forEach { (w, h, _) ->
            val boxW = 1080f
            val boxHt = 671f                          // 1 : 0.62 的取景框
            val scale = maxOf(boxW / w, boxHt / h) * 1.06f
            val (sx, sy) = coverSlackPx(w, h, boxW, boxHt, 1f)
            assertTrue("$w×$h 的 slackX 为负（会露页面底色）：$sx", sx >= 0f)
            assertTrue("$w×$h 的 slackY 为负：$sy", sy >= 0f)
            // 而且必须是「**放大后的图**比框多出来的一半」，不是拿框去乘缩放比
            assertEquals("$w×$h 的 slackX 算式", (w * scale - boxW) / 2f, sx, 0.5f)
            assertEquals("$w×$h 的 slackY 算式", (h * scale - boxHt) / 2f, sy, 0.5f)
        }
    }

    /**
     * 卡面字色的数学（v3 #12 第 7 轮：三档在卡带上量到 0.000 / 0px 之后补的）。
     *
     * 这条链以前**没有一处能测**：`glassAlpha` 是个与档位无关的常数，
     * 字色拿它和 `0.8f` 比大小 —— 于是"浅磨砂上压白字"那种只有眼睛能发现的错
     * 连红一次的机会都没有。现在数学抽成纯函数，逐档逐个时刻钉。
     */
    @Test
    fun `天空分数取色与卡面混合的数学`() {
        val look = SkyLook(
            top = Color(0xFFA9CBD8),      // 板 0%
            mid = Color(0xFFC9DEE5),      // 板 46%
            bottom = Color(0xFFF1E9DC),   // 板 100%
            ridgeFar = Color.Gray,
            ridgeNear = Color.Gray,
            ink = Color(0xFF1A1917),
            lightIcons = false,
        )
        assertCloseColor("0% 就是 top", look.top, look.colorAt(0f))
        assertCloseColor("46% 就是 mid", Color(0xFFC9DEE5), look.colorAt(SKY_MID_STOP))
        assertCloseColor("100% 就是 bottom", look.bottom, look.colorAt(1f))
        // 越界必须钳住（卡会滚出天空带，不能算出一个反着的颜色）
        assertCloseColor("负数钳到 top", look.top, look.colorAt(-2f))
        assertCloseColor("大于 1 钳到 bottom", look.bottom, look.colorAt(9f))

        val white = Color(0xFFFFFFFF)
        assertCloseColor("alpha=0 ⇒ 就是天空", Color(0xFF808080), faceOverSky(Color(0xFF808080), white, 0f))
        assertCloseColor("alpha=1 ⇒ 只剩垫色", white, faceOverSky(Color(0xFF808080), white, 1f))
        // 中点：不钉"算出来等于半灰"那个数 —— Compose 的 `lerp` 走的是它自己的色彩空间，
        // 手算 sRGB 中点期望 0.5、实得 0.388，钉它就是钉库的内部实现。
        // 字色判据真正依赖的是**单调性**：垫色越厚，卡面越亮，且必须落在两端之间。
        val thin = faceOverSky(Color(0xFF000000), white, 0.30f)
        val thick = faceOverSky(Color(0xFF000000), white, 0.90f)
        val skyLum = Color(0xFF000000).luminance()
        val tintLum = white.luminance()
        assertTrue("卡面亮度必须落在天空与垫色之间：$skyLum < ${thin.luminance()} < $tintLum",
            thin.luminance() > skyLum && thin.luminance() < tintLum)
        assertTrue(
            "垫色越厚卡面越亮（判字色就靠这条单调性）：${thin.luminance()} 应 < ${thick.luminance()}",
            thin.luminance() < thick.luminance(),
        )

        // 字色只看卡面：白面 ⇒ 深墨，黑面 ⇒ 浅墨，两套都是板上那对 token。
        // ★ 第 7 轮审计判死"近似值"之后：tint / 页面底色一律读 `AppColors` 里那两枚**主题真在用**的数
        //   （以前测试自己写 0xFFF4F7FF / 0xFF1C1F20，扫出来的 4.5:1 不是真机上那个数）。
        val lightTint = AppColors.surfaceContainerLight
        val darkTint = AppColors.surfaceContainerDark
        val lightPage = AppColors.pageBackgroundLight
        val darkPage = AppColors.pageBackgroundDark
        assertTrue(
            "亮面用板 --ink",
            cardInkWorstCase(listOf(faceOverSky(Color(0xFFFFFFFF), lightTint, 0.30f))).onLightFace,
        )
        assertTrue(
            "暗面用板 body.dark --ink",
            !cardInkWorstCase(listOf(faceOverSky(Color(0xFF0B1015), darkTint, 0.30f))).onLightFace,
        )

        // ★★★ 全档扫描（第 7 轮改写）：每一版真天空 × **卡身后那几层底** × 三档 × 深浅两主题，
        //   判出来的字色压在**每一层底上真正铺出来的那张卡面**都必须 ≥ 4.5:1。
        //   第 1 版只扫"卡背后一个颜色"，被审计抓住两处：① 那个颜色采错了带（卡八成高度压在
        //   淡成纸色那一段以下），② 强调色（胶囊字 + 倒计时环）根本没纳进判据。现在两条都在。
        val bad = mutableListOf<String>()
        SkyPreset.entries.forEach { preset ->
            if (preset == SkyPreset.ALBUM) return@forEach   // 照片档走下面那条"量出来的亮度"判据
            (0..23 step 3).forEach { hour ->
                val look = skyLookOf(preset, hour)
                listOf(false, true).forEach { isDark ->
                    val tint = if (isDark) darkTint else lightTint
                    val backdrops = look.cardBackdrops(if (isDark) darkPage else lightPage)
                    BarMaterial.entries.forEach { material ->
                        // ★ B4/#12 新口径（业主 2026-10-23 拍板）：**厚度不抬**，字压在"卡面 + 衬底"上
                        val a = cardFaceAlpha(material, isDark)
                        val faces = backdrops.map { textBackingOver(it, tint, a) }
                        val ink = cardInkWorstCase(faces)
                        faces.forEach { f ->
                            val c = wcagContrast(f, ink.main)
                            val cq = wcagContrast(f, ink.quiet)
                            val cw = wcagContrast(f, ink.warm)
                            if (c < 4.5f || cq < 3.0f || cw < 3.0f) bad +=
                                "$preset/${hour}h ${if (isDark) "深" else "浅"}$material " +
                                "厚度$a 主$c 次$cq 警$cw"
                        }
                        val accent = readableAccent(
                            faces,
                            if (isDark) Color(0xFF83C9BC) else Color(0xFF2E6B64),
                            ink.main,
                        )
                        faces.forEach { f ->
                            val c = wcagContrast(f, accent)
                            if (c < 4.5f) bad += "$preset/${hour}h ${if (isDark) "深" else "浅"}$material 强调色 $c"
                        }
                    }
                }
            }
        }
        assertTrue("这些档位下的卡面字色/强调色不合格（< 4.5:1）：$bad", bad.isEmpty())

        // 业主报的那一帧（17:03 黄昏「白压白」）：黄昏天空 + 纸色那一段，判出来必须是深墨。
        val dusk = skyLookOf(SkyPreset.AUTO, 17)
        val duskBands = dusk.cardBackdrops(lightPage)
        val duskFaces = duskBands.map { textBackingOver(it, lightTint, cardFaceAlpha(BarMaterial.LIQUID, false)) }
        assertTrue(
            "黄昏液态档必须判成深墨（白压白那一帧）：${duskFaces.map { it.luminance().toString().take(4) }}",
            cardInkWorstCase(duskFaces).onLightFace,
        )
        // 实心纸面那一档也必须同一个方向（以前它是**碰巧**对的）
        assertTrue(
            "黄昏实心档还得是深墨",
            cardInkWorstCase(duskBands.map { faceOverSky(it, lightTint, 0.94f) }).onLightFace,
        )
        // ★ 深色主题 × 白天 × 液态：第 7 轮审计算出**黑压黑 1.02:1** 的那一格，必须被新模型拦住。
        val dayDark = skyLookOf(SkyPreset.AUTO, 13).cardBackdrops(darkPage)
        val dayDarkFaces = dayDark.map { textBackingOver(it, darkTint, cardFaceAlpha(BarMaterial.LIQUID, true)) }
        val dayDarkInk = cardInkWorstCase(dayDarkFaces)
        dayDarkFaces.forEach { f ->
            assertTrue(
                "深色×白天×液态那一格还是 ${wcagContrast(f, dayDarkInk.main)}:1（审计给的是 1.02）",
                wcagContrast(f, dayDarkInk.main) >= 4.5f,
            )
        }
    }

    /**
     * 相册档：**亮照片上两档厚度是两个数；中/暗照片上它们必然收成同一个数** —— 后者写明原因。
     *
     * 第 8 轮审计必修 7 预报"相册档会把 #12 压回近似假开关"，我在模拟器上量到了 `MAD 0.000`。
     * 排查下来有两层：
     *  ① 真错的那层：当时传 `{黑, 白}` 两端兜"未知" ⇒ 已经改成量出来的照片亮度（`wallpaperStats`）；
     *  ② **不是错的那层**：次/警两档要 3:1 时，中段明度的卡面本来就谁都不合格，
     *     于是"抬厚度"这条唯一出路会把液态与磨砂一起抬到 ≈0.74 ⇒ 两档同厚。
     *     这是可读性的代价，不是档位失效 —— 那一档的差别只剩折射（真机上量得到，模拟器量不到）。
     */
    /**
     * 相册档（B4/#12 新口径）：**两档厚度永远是两个数**。
     *
     * 旧口径下"中段明度的照片会把两档抬成同一个厚度"（那是为了字的可读性抬卡面），
     * 于是真机上液态与磨砂逐像素相同；现在可读性交给字底衬底（`textBackingOver`），
     * 卡面照档位透 ⇒ 这一条就是把"档位轴回来了"钉死。
     */
    @Test
    fun `相册档两档厚度必须始终是两个数`() {
        listOf(false, true).forEach { isDark ->
            val liquid = cardFaceAlpha(BarMaterial.LIQUID, isDark)
            val frost = cardFaceAlpha(BarMaterial.FROSTED, isDark)
            assertTrue("相册档（深=$isDark）液态 $liquid 与磨砂 $frost 又同厚了", frost - liquid >= 0.1f)
        }
        // 而且"字压在档位厚度 + 衬底"上仍然合格（拿一张中段照片当例子）
        val stats = WallpaperStats(topBand = Color(0.45f, 0.45f, 0.45f), overall = Color(0.5f, 0.5f, 0.5f))
        val bands = photoBackdrops(stats, AppColors.pageBackgroundLight)
        val tint = AppColors.surfaceContainerLight
        BarMaterial.entries.forEach { m ->
            val a = cardFaceAlpha(m, false)
            val faces = bands.map { textBackingOver(it, tint, a) }
            val ink = cardInkWorstCase(faces)
            faces.forEach { f ->
                assertTrue(
                    "相册档 $m（厚度 $a）字色不合格：主${wcagContrast(f, ink.main)} " +
                        "次${wcagContrast(f, ink.quiet)} 警${wcagContrast(f, ink.warm)}",
                    wcagContrast(f, ink.main) >= 4.5f &&
                        wcagContrast(f, ink.quiet) >= 3.0f &&
                        wcagContrast(f, ink.warm) >= 3.0f,
                )
            }
        }
    }

    /**
     * `cardBackdrops` 必须**含页面纸色**（第 7 轮审计判死「取带内最亮档」那条的正面守卫）：
     * 卡顶 ≈167dp、天空只画到 `0.80 × 250dp = 200dp` ⇒ 这张卡八成高度压的是
     * **已经淡成页面底色**的那一段。以前那组采样点 [0.10,0.58] 采的是天空上半段，
     * 于是"深色×白天×液态"按亮面判了深墨，压在真实那块面上只有 1.02:1。
     */
    @Test
    fun `卡身后那组底必须含天空靠底一站与页面纸色`() {
        val look = SkyLook(
            top = Color(0xFF101820), mid = Color(0xFF3A4A58), bottom = Color(0xFFC8D8E0),
            ridgeFar = Color.Gray, ridgeNear = Color.Gray,
            ink = Color(0xFFF2EFE7), lightIcons = true,
        )
        val page = AppColors.pageBackgroundLight
        val bands = look.cardBackdrops(page)
        assertTrue(
            "底色必须覆盖**卡滚过的全程**（第 8 轮审计：天空是静止层、卡在 LazyColumn 里 ⇒ " +
                "它从 f≈0.84 一路压到 f≈0，只算静止位等于只兑了一半现）",
            bands.size >= 6,
        )
        assertCloseColor("第一层必须是天空顶端 f=0", look.colorAt(0f), bands.first())
        assertCloseColor("最后一层必须是页面纸色（天空淡完之后的底）", page, bands.last())
        assertTrue(
            "静止位那一层 f=0.84 必须在集合里",
            bands.any { kotlin.math.abs(it.luminance() - look.colorAt(0.84f).luminance()) < 1e-5f },
        )
        // 0.84 与 1.0 必须不是同一个数（一样 = 那一段是平的，采带等于没采）
        assertTrue(
            "天空最后那 16% 是平的 ⇒ 渐变没走到底，cardBackdrops 采不出真实底",
            look.colorAt(0.84f) != look.colorAt(1f),
        )
    }

    /**
     * 颜色比较按通道给 1/255 的容差：`lerp` 是 `a + (b-a)*f`，
     * 拿 `==` 判"这一站就是那个色"会在浮点尾数上假红（假红的测试等于没有测试）。
     */
    private fun assertCloseColor(message: String, expected: Color, actual: Color) {
        val d = maxOf(
            kotlin.math.abs(expected.red - actual.red),
            kotlin.math.abs(expected.green - actual.green),
            kotlin.math.abs(expected.blue - actual.blue),
        )
        assertTrue(
            "$message：期望 (${expected.red}, ${expected.green}, ${expected.blue}) " +
                "实得 (${actual.red}, ${actual.green}, ${actual.blue})，通道最大偏差 $d",
            d <= 1f / 255f,
        )
    }

    @Test
    fun `天空渐变不许再出现两站水平段，也不许用裸 h 当终点`() {
        val text = src.readText()
        assertTrue(
            "`0.55f to look.top` 是第 5/6 轮那条死平水平段的唯一来源，任何一档都不许再用它",
            !text.contains("0.55f to look.top"),
        )
        assertTrue(
            "必须真的有三站：删掉中间那一站（哪怕留着 if/else）就等于把死平放回来。" +
                "停靠点现在只有 `SKY_MID_STOP` 这一个数 —— 画的和算字色的必须共用它",
            text.contains("SKY_MID_STOP to (look.mid ?: lerp("),
        )
        assertTrue(
            "`SKY_MID_STOP` 必须是板上的 46%（改成别的数 = 画面与 `SkyLook.colorAt` 的字色解算分家）",
            text.contains("const val SKY_MID_STOP = 0.46f"),
        )
        // 按 `drawRect(` 的**整个调用体**扫（旧版只 take(10) 行，漏判了后面那几行）
        val bodies = text.substringAfter("private fun DrawScope.drawSkyScene")
            .split("drawRect(").drop(1)
        val badEndY = bodies.map { it.substringBefore("\n        )") }
            .filter { it.contains("verticalGradient") && it.contains("skyBottom") }
            .filter { it.contains("endY = h") || it.contains("size.height") || !it.contains("endY") }
        assertTrue(
            "这些竖向渐变拿裸 h / size.height 当终点（= 按整块画布解算，天空底边与 slack 全会偏）：$badEndY",
            badEndY.isEmpty(),
        )
    }

    /**
     * 第 58 轮业主报「天空没有过渡」，追到根因：羽化那一笔的**矩形起点写错**。
     *
     * `featherStops(…, transparent = true)` 在 t = 0 处的 alpha 是 **1**（不是 0）——
     * 所以矩形一旦从 (0, 0) 起、高 `fadeEnd` 铺下去，y < fadeStart 那一段就落在 t = 0 上，
     * 整片天空被 `colorAt(fadeStart / skyBottom)` 这一种颜色**不透明地平涂**，
     * 三站渐变、两层 atmos 光晕、日轮/星点全部被盖掉。
     *
     * 实测坐实（verify/58-unknown-count/analyze_flat_sky.py，exit 0）：
     * 日档 y=0..470 众数 (221,231,232) == colorAt(490/700) = (221.00,230.89,231.67) 偏差 0；
     * 夜档 (29,40,69) == (29.60,40.40,68.80) 偏差 1。两档都精确落在同一个 t 上，
     * 不可能是巧合 ⇒ 判据就该钉「这一笔的矩形必须从 fadeStart 起跳」。
     */
    @Test
    fun `羽化那一笔的矩形必须从 fadeStart 起跳`() {
        val text = src.readText()
        val body = text.split("drawRect(").firstOrNull {
            it.contains("featherStops(skyAtFadeStart") && it.contains("endY = fadeEnd")
        }
        assertTrue("找不到画 skyAtFadeStart 羽化的那一笔 drawRect", body != null)
        assertTrue(
            "矩形没写 topLeft ⇒ 从 (0,0) 起，y<fadeStart 落在 t=0（alpha=1）上，整片天空被平涂。" +
                "body: " + body,
            body!!.contains("topLeft = Offset(0f, fadeStart)"),
        )
        assertTrue(
            "矩形高度必须是 fadeEnd - fadeStart；写成 fadeEnd 会多铺 fadeStart 那么高的一段平色。" +
                "body: " + body,
            body.contains("size = Size(w, (fadeEnd - fadeStart).coerceAtLeast(0f))"),
        )
    }
}
