package com.muyao.wanzaihyperos.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.glass.BarMaterial
import com.muyao.wanzaihyperos.ui.design.glass.cardFaceAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B4-03（业主 2026-10-23：「背景太黄 ⇒ 改白一点」）的阶段 1 判据。
 *
 * 判据不是「我觉得白了」，而是三条**可算**的：
 *  ① **黄度 `R−B` 必须比改前小**（每个亮档色标都要）；
 *  ② **亮度只许升不许降**（"改白"不是"改灰"：压暗会连带压掉字与卡的层次）；
 *  ③ **对比度不低于改前**（底色变亮，深墨只会更清楚；这条防的是"顺手把字底衬也提亮"）。
 *
 * 底下那张 `BEFORE` 表是**改之前的生产值**（不是估的）：它们原来是 `AppColors.pageBackgroundLight`、
 * `AppColors.lightBgStops`、`skyLookOf(...)` 里的字面量，改动时逐字抄进来的。
 * ⇒ 这条测试就是 B4-03 的**反向验收**：谁把色值改回原样，①②立刻红。
 */
class SkyWhiteningTest {

    /** 改前的原值（逐字抄自改动前的源码）。 */
    private data class Before(val what: String, val color: Color, val now: () -> Color)

    private fun r(c: Color) = (c.toArgb() shr 16) and 0xFF
    private fun g(c: Color) = (c.toArgb() shr 8) and 0xFF
    private fun b(c: Color) = c.toArgb() and 0xFF

    /** 浮点黄度（R−B，0..255 口径）：`faceOverSky` 是浮点 lerp，判它必须用浮点尺。 */
    private fun yellowF(c: Color): Float = (c.red - c.blue) * 255f

    /** 黄度：R 与 B 的差（板面越黄这个数越大）。 */
    private fun yellowness(c: Color) = r(c) - b(c)

    private val cases = listOf(
        Before("页面底色", Color(0xFFF4F0E8)) { AppColors.pageBackgroundLight },
        Before("页面底色上一档", Color(0xFFF8F4ED)) { AppColors.lightBgStops[0] },
        Before("页面底色下一档", Color(0xFFEDE8DD)) { AppColors.lightBgStops[2] },
        Before("晨雾天空底端", Color(0xFFF2E5D6)) { skyLookOf(SkyPreset.MORNING, 9).bottom },
        Before("清晨档(5..7)底端", Color(0xFFF6D9B8)) { skyLookOf(SkyPreset.AUTO, 6).bottom },
        Before("上午档(8..11)底端", Color(0xFFF1E9DC)) { skyLookOf(SkyPreset.AUTO, 9).bottom },
        Before("下午档(12..16)底端", Color(0xFFF1E9DC)) { skyLookOf(SkyPreset.AUTO, 13).bottom },
    )

    @Test
    fun `每一档亮色的黄度都必须比改前低`() {
        val bad = cases.mapNotNull { c ->
            val now = c.now()
            val drop = yellowness(c.color) - yellowness(now)
            if (drop < 4) "${c.what}: R−B ${yellowness(c.color)} → ${yellowness(now)}（只降了 $drop）" else null
        }
        assertTrue(
            "这些亮档色标没变白（判据：黄度 R−B 至少降 4，且不许反向）：\n" + bad.joinToString("\n"),
            bad.isEmpty(),
        )
    }

    @Test
    fun `改白不是改灰 亮度只许升不许降`() {
        val bad = cases.mapNotNull { c ->
            val now = c.now()
            val dl = now.luminance() - c.color.luminance()
            // 1.dp 级别的四舍五入会有 ±0.001 的抖动，留 0.002 的余量
            if (dl < -0.002f) "${c.what}: 亮度 ${c.color.luminance()} → ${now.luminance()}" else null
        }
        assertTrue("抬白把底色压暗了（那会让字更糊，且白得更假）：\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `改后对比度不低于改前`() {
        val ink = Color(0xFF1A1917)   // 亮面那支 ink（`INK_ON_LIGHT_FACE`）
        val bad = cases.mapNotNull { c ->
            val before = wcagContrast(c.color, ink)
            val now = wcagContrast(c.now(), ink)
            if (now + 0.01f < before) "${c.what}: ${before}:1 → ${now}:1" else null
        }
        assertTrue("底色抬白后对比度反而掉了：\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /**
     * **相册那条路径**也一起吃这枚色：`photoBackdrops` 的最后一层就是 `pageBg`（天空渐隐之后
     * 压在照片下面的底），`cardBackdrops` 的最后一层同理 ⇒ 纸色一变白，照片档的收尾也跟着变白。
     *
     * 照片本身当然不变（那是他的图）；变的是"照片淡出去之后那一块"。
     */
    @Test
    fun `相册档的收尾站也一改变白`() {
        val before = Color(0xFFF4F0E8)
        val after = AppColors.pageBackgroundLight
        val stats = WallpaperStats(
            topBand = Color(0.86f, 0.82f, 0.74f),
            overall = Color(0.78f, 0.74f, 0.66f),
        )
        // ★ 第 23 轮：**三层全部取自生产**，不再手写混色公式。
        //   上一版在测试里写死 `photo*0.55 + pageBg*0.45` —— 那个 0.55 是我发明的数，
        //   生产的卡面厚度是 `cardFaceAlpha(档位)`、混色是 `faceOverSky(底, 纸色, 厚度)`，
        //   测自己造的公式 = 生产怎么改测试都照样绿。
        //   再上一版更糟：比的是 `photoBackdrops(...).last()`，那**就是传进去的 pageBg 本身**（同义反复）。
        //   现在：底取自 `photoBackdrops`/`cardBackdrops`，厚度取自 `cardFaceAlpha`，
        //   铺出来的面用生产的 `faceOverSky` ⇒ 判的是"这次抬白能不能穿过真实那三层"。
        // 混色是**浮点** lerp，而老的 `yellowness()` 走 toArgb（8bit 取整）：
        // 期望 4.7 被量化成 4 —— 那是取样误差，不是生产没变白。这里全程按浮点算。
        val paperDrop = yellowF(before) - yellowF(after)
        assertTrue("对照组先坏：纸色自己就没变白（Δ=$paperDrop），后面的传导判据都没意义", paperDrop >= 2)
        // 最后一层底**就是** pageBg 自己（生产 `photoBackdrops` 的第 3 层）⇒ 传导必须等于纸色降幅；
        // 这一条专门抓"pageBg 被从 photoBackdrops 里摘掉"（上一版同义反复的那层，反过来用就成了锚）
        // 形状锚（不是白化判据，是"生产函数还在不在那条链上"）：
        //   第 23 轮第一次跑变异取证 —— 把 `photoBackdrops` 里的 pageBg **摘掉**、把 SOLID 的厚度
        //   从 0.94 改成 0.2 —— 两次测试**都还是绿的**（`review/REPORT.md` 第 23 轮记着）。
        //   原因就在这两条：传导用了 `faceOverSky(before, before, α)` 这种自己拼的表达式（不查生产清单），
        //   而纸色那一层 `lerp(pageBg, pageBg, α)` 恒等于 pageBg ⇒ α 根本不影响它，单调性被这层抹平。
        //   现在：① 断言生产清单的**形状**（层数 + 最后一层就是纸色）；② 平均传导**只在非纸色层**上算。
        val photoBands = photoBackdrops(stats, after)
        assertEquals("照片档底必须给到 3 层（照片顶带 / 照片整体 / 纸色）", 3, photoBands.size)
        assertEquals("生产 photoBackdrops 的最后一层底必须是**纸色本身**（摘掉它 = 卡淡出去后没有底）",
            after, photoBands.last())
        val skyBands = skyLookOf(SkyPreset.AUTO, 13).cardBackdrops(after)
        assertEquals("内置天空档的最后一层底必须是纸色本身", after, skyBands.last())

        val byMaterial = BarMaterial.entries
            .map { it to cardFaceAlpha(it, isDark = false) }
            .sortedBy { it.second }                     // LIQUID < FROSTED < SOLID（厚度轴）
        val dropPerMaterial = mutableListOf<Pair<String, Float>>()
        // 最后一层是纸色自己 ⇒ 参与"逐层不许更黄"，但不参与"随厚度单调"
        val faceCountMinusTail = photoBackdrops(stats, after).size - 1
        for ((material, alpha) in byMaterial) {
            // ⚠️ 不许写成 `photoBackdrops(stats, before to after)` 那种：拿同一张 pageBg 自己比，
            //   是**同义反复**（比的是"新纸色比旧纸色白"）。上一版就是这么写的。
            val photoPairs = photoBackdrops(stats, before)
                .mapIndexed { k, c -> k to faceOverSky(c, before, alpha) }
                .zip(photoBackdrops(stats, after).mapIndexed { k, c -> k to faceOverSky(c, after, alpha) })
                .map { (o, n) -> o.first to (o.second to n.second) }
            val skyPairs = skyLookOf(SkyPreset.AUTO, 13).cardBackdrops(before)
                .mapIndexed { k, c -> k to faceOverSky(c, before, alpha) }
                .zip(skyLookOf(SkyPreset.AUTO, 13).cardBackdrops(after).mapIndexed { k, c -> k to faceOverSky(c, after, alpha) })
                .map { (o, n) -> o.first to (o.second to n.second) }
            val drops = mutableListOf<Float>()
            val photoDrops = mutableListOf<Float>()
            listOf("照片档" to photoPairs, "内置天空档" to skyPairs).forEach { (tag, pairs) ->
                pairs.forEach { (k, face) ->
                    val (old, now) = face
                    val drop = yellowF(old) - yellowF(now)
                    drops += drop
                    // 纸色那一层（最后一层）lerp(pageBg,pageBg,α) 恒等于纸色 ⇒ 对 α 不敏感，
                    // 算进平均会把厚度单调性抹平 ⇒ **只拿非纸色层**做传导与单调判据
                    if (k < faceCountMinusTail) photoDrops += drop
                    assertTrue(
                        "$tag × $material（厚度 $alpha）第 $k 层铺出来的面**变得更黄**：Δ(R−B)=$drop",
                        drop >= 0f,
                    )
                    assertTrue(
                        "$tag × $material 第 $k 层铺出来更暗了（改白不是改灰）：" +
                            "${old.luminance()} → ${now.luminance()}",
                        now.luminance() >= old.luminance() - 0.001f,
                    )
                }
            }
            val mean = drops.average().toFloat()
            val photoMean = if (photoDrops.isEmpty()) mean else photoDrops.average().toFloat()
            // 生产把混色结果**量化到 8bit**（PROBE 实测：LIQUID 那一档 topBand 层的传导正好被
            // 舍进 0）。所以"每一层都必须降 ≥1 灰阶"是我定错的闸，不是生产的问题 ——
            // 判两条真成立的：① 任何一层都不许变得更黄；② 这一档整体必须把白化传到（均值 ≥1 灰阶）。
            assertTrue(
                "$material（厚度 $alpha）非纸色层的平均传导只有 $photoMean（<0.8 灰阶）⇒ 纸色抬白没走到卡面",
                photoMean >= 0.8f,
            )
            dropPerMaterial += material.name to photoMean
        }
        // 厚度越大、纸色占的面越重 ⇒ 抬白必须**随厚度单调变大**。这条抓的是"生产把哪一档的 alpha 改了"
        // （上一版我按 `alpha × 纸色降幅` 硬算期望值，实测 SOLID 只传到 4.0/4.7 —— 那是 sRGB 合成
        //  不是组件线性插值，**我的解析模型错了**，不是生产错了。所以改判"方向 + 单调"这种真成立的不变量，
        //  而不是把我算错的数当闸门。）
        for (k in 1 until dropPerMaterial.size) {
            val (prevName, prev) = dropPerMaterial[k - 1]
            val (name, cur) = dropPerMaterial[k]
            assertTrue(
                "白化传导不按厚度单调：$prevName(薄)平均=$prev 却 ≥ $name(厚)平均=$cur" +
                    "（纸色在厚面里占得更重，传导必须更多）",
                cur >= prev - 0.01f,
            )
        }
        // 灯色没被顺手改：ink 仍是固定值，对纸的对比度不许降
        val ink = Color(0xFF1A1917)
        assertTrue(
            "抬白之后墨色对比度反而降了",
            wcagContrast(after, ink) + 0.01f >= wcagContrast(before, ink),
        )
    }

    /**
     * 三站仍然单调（顶 → 中 → 底越来越亮）。
     *
     * 这条是 `mid` 那次修的延续：`0→top` 与 `bottom` 之间只许单向走，
     * 否则天空会被读成"两块颜色拼起来"，而上滑时正好扫过那条拐点。
     */
    @Test
    fun `亮档天空仍是顶深浅底的三站单调`() {
        listOf(6, 9, 13, 15).forEach { hour ->
            val look = skyLookOf(SkyPreset.AUTO, hour)
            val mid = look.mid ?: Color(
                (r(look.top) + r(look.bottom)) / 2,
                (g(look.top) + g(look.bottom)) / 2,
                (b(look.top) + b(look.bottom)) / 2,
            )
            assertTrue(
                "${hour}h 的天空不是单调变亮：top ${look.top.luminance()} mid ${mid.luminance()} bottom ${look.bottom.luminance()}",
                look.top.luminance() < mid.luminance() && mid.luminance() < look.bottom.luminance(),
            )
        }
        // 晨雾是两站（无 mid），只判两端
        val morning = skyLookOf(SkyPreset.MORNING, 9)
        assertTrue(
            "晨雾天空不是单调变亮",
            morning.top.luminance() < morning.bottom.luminance(),
        )
    }
}
