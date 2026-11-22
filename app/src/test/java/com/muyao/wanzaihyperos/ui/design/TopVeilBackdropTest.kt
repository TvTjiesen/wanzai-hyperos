package com.muyao.wanzaihyperos.ui.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TopVeilBackdropTest {
    private fun source(path: String): String = File("src/main/java/com/muyao/wanzaihyperos/$path")
        .readText(Charsets.UTF_8).let { text ->
            Regex("""/\*[\s\S]*?\*/""").replace(text, "").lines().joinToString("\n") { it.substringBefore("//") }
        }

    @Test fun `次级页采样未就绪不画假色带`() {
        val code = source("ui/design/TopVeil.kt")
        assertFalse("次级页不得按主题伪造采样", "colorScheme.background" in code)
        assertTrue("只采当前存活背景", "source: Backdrop?" in code && "if (source == null || !backdropIsLive(source)) return" in code)
        assertTrue("使用统一渐变模糊", "progressiveBlurBackdrop(source, alpha" in code && "LocalThemeCustomization.current.topBlur" in code)
        val scaffold = source("ui/design/AppLayout.kt").substringAfter("fun SecondaryScaffold(")
        assertTrue("次级页记录自身内容", "val pageSource = rememberLayerBackdrop()" in scaffold && "layerBackdrop(pageSource)" in scaffold && "source = pageSource" in scaffold)
        assertFalse("不得采下层页面", "LocalBackdropSink" in scaffold)
    }

    @Test fun `主顶栏只模糊真实背景而不铺主题遮罩`() {
        val code = source("ui/design/liquid/IosChrome.kt")
        val top = code.substringAfter("fun IosTopBar(").substringBefore("fun LargeTitle(")
        assertTrue("采样真实页面并统一渐变模糊", "backdrop = liveBackdrop" in top && "progressiveBackdropBlur(" in top)
        assertTrue("保留小范围软边", "IosBarHeight.toPx() * .50f" in top && "BlendMode.DstIn" in top)
        assertFalse("不可叠白色涂层", "drawRect(veilBase" in top || "background(veilBase" in top)
        assertFalse("高对比不可把普通模糊替换成实色", "solidBand" in top)
        assertTrue("文字跟随真实采样亮度", "luminanceState = backgroundLuminance" in top && "color = titleInk" in top)
    }
}
