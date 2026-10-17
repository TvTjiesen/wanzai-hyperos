package com.muyao.wanzaihyperos.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 設計令牌的**關係約束**。
 *
 * ## 為什麼要測「數值之間的關係」而不是具體數值
 *
 * 業主後面要整體美化，**數值一定會改**。如果測試寫死 `assertEquals(14.dp, Dims.cardPadding)`，
 * 那美化時第一件事就是被迫改測試 —— 於是測試變成障礙而不是保護。
 *
 * 所以這裡釘的是**關係**（誰比誰大、倍數是否正確）。
 * 那些關係是「視覺層級」的體現，**美化時不該被破壞**：
 *
 * - 如果某次美化讓 `tight > sectionGap`，那「同一群組內」的間距比「群組之間」還大，
 *   視覺上群組就不成立了；
 * - 如果 `screenH` 變成 0，內容會貼著螢幕邊緣。
 *
 * 換句話說：**數值可以變，層級不能亂**。這正是自動化測試該守住的部分。
 */
class DesignTokensTest {

    @Test
    fun `间距呈层级关系`() {
        // tight < normal < sectionGap < cardGap 且都 > 0
        assertTrue("tight 必须为正", Dims.tight.value > 0f)
        assertTrue(
            "tight(${Dims.tight}) 应小于 normal(${Dims.normal})",
            Dims.tight.value < Dims.normal.value,
        )
        assertTrue(
            "normal(${Dims.normal}) 应小于或等于 sectionGap(${Dims.sectionGap})",
            Dims.normal.value <= Dims.sectionGap.value,
        )
        assertTrue(
            "sectionGap(${Dims.sectionGap}) 应小于 cardGap(${Dims.cardGap})",
            Dims.sectionGap.value < Dims.cardGap.value,
        )
    }

    @Test
    fun `同组内间距不大于组间距`() {
        // ★ 這條是「視覺群組」能不能成立的關鍵：
        //   同一個視覺群組內的分隔（tight/normal）不該大於群組之間（sectionGap）。
        //   美化時若把 tight 調大超過 sectionGap，卡片內部就會鬆散、
        //   而卡片之間反而擠在一起 —— 群組感消失。
        assertTrue(
            "tight(${Dims.tight}) 不能大于 sectionGap(${Dims.sectionGap})",
            Dims.tight.value <= Dims.sectionGap.value,
        )
        assertTrue(
            "normal(${Dims.normal}) 不能大于 sectionGap(${Dims.sectionGap})",
            Dims.normal.value <= Dims.sectionGap.value,
        )
    }

    @Test
    fun `边距与内距都是正数`() {
        // 0 或负数会让内容贴边/重叠，视觉上直接是坏的。
        for ((name, v) in listOf(
            "screenH" to Dims.screenH,
            "screenV" to Dims.screenV,
            "cardPadding" to Dims.cardPadding,
            "cardGap" to Dims.cardGap,
        )) {
            assertTrue("$name 必须为正，实际 ${v.value}", v.value > 0f)
        }
    }

    @Test
    fun `圆角不超过卡片内距的两倍`() {
        // 圓角太大而內距太小，內容會壓到圓角弧線上（看起來像被切掉）。
        // 這條給美化時一個護欄：要放大圓角就得同時放大內距。
        assertTrue(
            "cardCorner(${Dims.cardCorner}) 相对 cardPadding(${Dims.cardPadding}) 过大",
            Dims.cardCorner.value <= Dims.cardPadding.value * 2f,
        )
        assertTrue("chipCorner 不能大于 cardCorner", Dims.chipCorner.value <= Dims.cardCorner.value)
    }

    @Test
    fun `标签列宽能容纳两到三个字`() {
        // 標籤是「學校/院系/年級/班級」這類 2 字詞。太窄會換行、太寬會把值推遠。
        assertTrue("labelColumn 过窄", Dims.labelColumn.value >= 48f)
        assertTrue("labelColumn 过宽", Dims.labelColumn.value <= 96f)
        assertTrue("labelColumnWide 应不小于 labelColumn", Dims.labelColumnWide.value >= Dims.labelColumn.value)
    }

    @Test
    fun `动效时长为正且快慢有序`() {
        assertTrue(Motion.PRESS_MS > 0)
        assertTrue("FAST 必须短于 NORMAL", Motion.FAST_MS < Motion.NORMAL_MS)
    }

    // ── 第 1 步新增的令牌（規範 §3 / §4 / §9）─────────────────────────────

    /**
     * 時長階梯必須**嚴格遞增**。
     *
     * 這條比「每個值都 > 0」有用：美化時如果把 `PAGE_MS` 調到比 `MORPH_MS` 還長，
     * 那「頁面切換比大形變還慢」—— 觀感上會覺得切頁在卡。
     * 階梯的**順序**是觀感的一部分，數值本身可以改。
     */
    @Test
    fun `动效时长呈递增长`() {
        val ladder = listOf(
            "PRESS" to Motion.PRESS_MS,
            "FAST" to Motion.FAST_MS,
            "NORMAL" to Motion.NORMAL_MS,
            "EXPAND" to Motion.EXPAND_MS,
            "PAGE" to Motion.PAGE_MS,
            "MORPH" to Motion.MORPH_MS,
        )
        for ((name, v) in ladder) assertTrue("$name 必须为正，实际 $v", v > 0)
        for (i in 1 until ladder.size) {
            assertTrue(
                "${ladder[i - 1].first}(${ladder[i - 1].second}) 应短于 ${ladder[i].first}(${ladder[i].second})",
                ladder[i - 1].second < ladder[i].second,
            )
        }
    }

    /**
     * 字號階梯必須**嚴格遞增且無重複**。
     *
     * 「無重複」是關鍵：規範明令「不許插中間值」，
     * 而兩個檔位剛好相等等於有一個是白定義的 —— 那種情況通常是
     * 「想改但沒刪舊的」，最後頁面各自挑一個，就不一致了。
     */
    @Test
    fun `字号阶梯递增且不重复`() {
        val ladder = listOf(
            "micro" to Type.micro,
            "caption" to Type.caption,
            "summary" to Type.summary,
            "secondary" to Type.secondary,
            "bodyEmphasis" to Type.bodyEmphasis,
            "body" to Type.body,
            "title" to Type.title,
            "displayTitle" to Type.displayTitle,
            "largeTitle" to Type.largeTitle,
        )
        for (i in 1 until ladder.size) {
            assertTrue(
                "${ladder[i - 1].first}(${ladder[i - 1].second}) 应小于 ${ladder[i].first}(${ladder[i].second})",
                ladder[i - 1].second.value < ladder[i].second.value,
            )
        }
        // 最小可用檔：規範明令「再小不許」
        assertTrue("micro 不应小于 11sp", Type.micro.value >= 11f)
    }

    /**
     * 中文小字行高必須 ≥1.5（規範 §3 硬規則 3）。
     *
     * 繁體筆畫比簡體密，行高不足時上下兩行的筆畫會黏在一起 ——
     * 這是「看得見的錯」，不是偏好問題。
     */
    @Test
    fun `中文小字行高不低于 1_5`() {
        assertTrue("LINE_HEIGHT_SMALL 应 ≥1.5，实际 ${Type.LINE_HEIGHT_SMALL}",
            Type.LINE_HEIGHT_SMALL >= 1.5f)
        assertTrue("正文字高应 ≥1.4", Type.LINE_HEIGHT_BODY >= 1.4f)
        assertTrue("标题字高应 ≥1.15", Type.LINE_HEIGHT_TITLE >= 1.15f)
        assertTrue("标题字高不应超过正文（否则标题比正文还松）",
            Type.LINE_HEIGHT_TITLE <= Type.LINE_HEIGHT_BODY)
    }

    /**
     * 觸控目標 ≥44，且**不小於**任何視覺圖示尺寸。
     *
     * 這是「按得到」與「看得見」的區別：圖示畫 20dp 沒問題，
     * 但命中區若也只有 20dp，手指會點空。44 是 iOS HIG 與 Material 的共同下限。
     */
    @Test
    fun `触控目标不小于视觉尺寸`() {
        assertTrue("minTouch 应 ≥44dp，实际 ${Dims.minTouch}", Dims.minTouch.value >= 44f)
        assertTrue("minTouch 不应小于 iconNav", Dims.minTouch.value >= Dims.iconNav.value)
        assertTrue("minTouch 不应小于 iconAction", Dims.minTouch.value >= Dims.iconAction.value)
    }

    /**
     * 頂欄欄身是 52（**不是 Material 的 56**），且比清單單行矮。
     *
     * 52 是「像 iOS」與「像安卓」的分界（規範 §4/§7）；
     * 而欄身若比單行清單項還高，頂欄會顯得笨重。
     */
    @Test
    fun `顶栏栏身是 52 而不是 56`() {
        assertEquals("規範定稿值：52（Material 是 56）", 52f, Dims.barHeight.value, 0.01f)
        assertTrue("栏身不应高于单行清單項", Dims.barHeight.value <= Dims.itemTwoLine.value)
    }

    /** 清單項三檔高度必須遞增（單行 < 兩行 < 三行）。 */
    @Test
    fun `清单项高度递增`() {
        assertTrue("单行应小于两行", Dims.itemOneLine.value < Dims.itemTwoLine.value)
        assertTrue("两行应小于三行", Dims.itemTwoLine.value < Dims.itemThreeLine.value)
        assertTrue("单行不应小于触控目标", Dims.itemOneLine.value >= Dims.minTouch.value)
    }
}
