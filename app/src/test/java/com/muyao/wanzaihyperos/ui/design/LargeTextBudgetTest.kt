package com.muyao.wanzaihyperos.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeTextBudgetTest {

    @Test
    fun `网格列数只随宽度与字号收窄，不回到单行截断`() {
        assertEquals(4, appGridColumns(411, 1.0f))
        assertEquals(3, appGridColumns(390, 1.15f))
        assertEquals(3, appGridColumns(360, 1.2f))
        assertEquals(2, appGridColumns(320, 1.15f))
        assertEquals(2, appGridColumns(411, 1.5f))
    }

    @Test
    fun `设置附件只在大字或窄屏时退到副标题下方`() {
        assertTrue(settingAccessoryBelow(390, 1.3f))
        assertTrue(settingAccessoryBelow(340, 1.0f))
        assertFalse(settingAccessoryBelow(411, 1.15f))
    }
}
