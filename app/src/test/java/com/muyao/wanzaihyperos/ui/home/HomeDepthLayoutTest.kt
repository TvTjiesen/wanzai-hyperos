package com.muyao.wanzaihyperos.ui.home

import com.muyao.wanzaihyperos.ui.home.redesign.homeDepthCanvasHeight
import com.muyao.wanzaihyperos.ui.home.redesign.homeStatusDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDepthLayoutTest {
    @Test fun `半卡淡出跟随真实卡高与来源`() {
        for (top in listOf(130f, 180f, 240f)) {
            for (height in listOf(96f, 176f, 300f, 620f)) {
                for (album in listOf(false, true)) {
                    val h = homeDepthCanvasHeight(top, height, album)
                    val fadeEnd = h * if (album) 0.80f else 0.78f
                    assertEquals(top + height / 2, fadeEnd, 0.001f)
                    assertTrue(h.isFinite() && h > 0)
                }
            }
        }
    }

    @Test fun `只去重复前缀而保留来源缓存和错误`() {
        assertEquals("", homeStatusDetail("正在读", "正在读"))
        assertEquals("这是上次存的", homeStatusDetail("稍等 · 这是上次存的", "稍等"))
        assertEquals("来自学在城院（教务未连接）", homeStatusDetail("稍等 · 来自学在城院（教务未连接）", "稍等"))
        assertEquals("教务失败：网络错误", homeStatusDetail("教务失败：网络错误", "取不到课表"))
        assertEquals("稍等一下", homeStatusDetail("稍等一下", "稍等"))
        assertEquals("有一件待办", homeStatusDetail("有一件待办", "46 分钟后上课"))
    }

    @Test fun `不可用的初始测量不产生负高度`() {
        assertEquals(0f, homeDepthCanvasHeight(-1f, -1f, true), 0f)
    }
}
