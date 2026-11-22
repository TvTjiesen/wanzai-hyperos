package com.muyao.wanzaihyperos.ui.home

import androidx.compose.ui.graphics.Color
import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeSkyAlbumMaskTest {
    private fun albumBranch(): String {
        val source = SourceScan.stripComments(
            File("src/main/java/com/muyao/wanzaihyperos/ui/home/HomeSky.kt").readText(),
        )
        val start = "if (pageGradient != null && pageHeightPx > 0f && albumPhoto) {"
        val end = "} else if (pageGradient != null && pageHeightPx > 0f) {"
        assertTrue("相册专用分支必须存在", start in source && end in source)
        return source.substringAfter(start).substringBefore(end).replace(Regex("\\s+"), " ")
    }

    @Test
    fun `相册页面渐变与遮罩必须覆盖同一完整天空层`() {
        val body = albumBranch()
        assertTrue(body.contains("canvas.saveLayer(Rect(0f, 0f, w, h), Paint())"))
        assertTrue(body.contains("drawRect(brush = pageGradient, size = Size(w, pageHeightPx))"))
        assertTrue(body.contains("*featherStops(Color.White), startY = fadeStart, endY = fadeEnd,"))
        assertTrue(body.contains("size = Size(w, h), blendMode = BlendMode.DstIn"))
        assertEquals(1, Regex("canvas.saveLayer\\(").findAll(body).count())
        assertEquals(1, Regex("canvas.restore\\(").findAll(body).count())
    }

    @Test
    fun `相册渐隐上下不能再拼接独立窄带矩形`() {
        val body = albumBranch()
        assertFalse("窄带裁剪在分数采样位置仍会留下横线", "clipRect(" in body)
        assertFalse("蒙版必须从天空原点覆盖全高", "topLeft = Offset(0f, fadeStart)" in body)
        assertFalse("不能恢复窄带离屏尺寸", "Rect(0f, fadeStart, w, fadeEnd)" in body)
    }

    @Test
    fun `全画布蒙版沿用透明到不透明的单调渐隐`() {
        val stops = featherStops(Color.White)
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(1f, stops.last().first, 0f)
        assertEquals(0f, stops.first().second.alpha, 0f)
        assertEquals(1f, stops.last().second.alpha, 0f)
        stops.asList().zipWithNext().forEach { (a, b) ->
            assertTrue(a.first < b.first)
            assertTrue(a.second.alpha <= b.second.alpha)
        }
    }
}
