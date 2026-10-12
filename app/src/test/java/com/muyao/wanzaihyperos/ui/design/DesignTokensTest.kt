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
        assertTrue(Motion.FAST_MS > 0)
        assertTrue("FAST 必须短于 NORMAL", Motion.FAST_MS < Motion.NORMAL_MS)
    }
}
