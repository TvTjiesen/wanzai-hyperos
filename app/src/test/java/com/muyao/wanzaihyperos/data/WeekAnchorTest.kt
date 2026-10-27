package com.muyao.wanzaihyperos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 教学周锚点的推算（[weekFrom]）。
 *
 * ## 为什么这一组测试重要
 *
 * 有了锚点，課表才能在「学在城院那支 teachingWeeks() 还没回来」时**立刻**画出来
 * （业主 2026-10-20：「我不想每次进来都要看到他重新加载一遍」）。
 * 但推算错一周，他会拿着错的周次去上课 —— 所以这里的每一条负数/越界路径都要钉死：
 * **宁可回 null 说"还不知道"，也不报一个看起来很像真的号。**
 */
class WeekAnchorTest {

    @Test
    fun `锚点当天就是那一周`() {
        assertEquals(6, weekFrom(anchorEpochDay = 100L, anchorWeek = 6, todayEpochDay = 100L))
    }

    @Test
    fun `同一周内过几天还是同一周`() {
        // 周次按教学周走：锚点 +6 天仍在同一周
        assertEquals(6, weekFrom(100L, 6, 106L))
    }

    @Test
    fun `满一周才进一号`() {
        assertEquals(7, weekFrom(100L, 6, 107L))
        assertEquals(8, weekFrom(100L, 6, 120L))
    }

    @Test
    fun `锚点在未来不算`() {
        // 设备时钟被改到过未来，或锚点是别的学期留下的：不能倒推出一个号
        assertNull(weekFrom(100L, 6, 99L))
    }

    @Test
    fun `锚点太旧就不信`() {
        assertEquals(6 + 17, weekFrom(100L, 6, 100L + 120L))   // 边界：正好 120 天仍信
        assertNull(weekFrom(100L, 6, 100L + 121L))
        assertNull(weekFrom(100L, 6, 100L + 400L))
    }

    @Test
    fun `算出非正数不算`() {
        assertNull(weekFrom(100L, 0, 100L))
        assertNull(weekFrom(100L, -3, 100L))
    }
}
