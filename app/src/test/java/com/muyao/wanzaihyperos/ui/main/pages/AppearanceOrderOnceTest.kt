package com.muyao.wanzaihyperos.ui.main.pages

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A2：外观与壁纸的 **12 段顺序**。
 *
 * 返工总单 A2 的验收第一条是「12 段顺序可逐条对照」。这句话原来只能靠人眼数，
 * 改一次顺序就得再数一次 —— 所以钉成测试：按源码里出现的先后位置逐段比对。
 *
 * 为什么读源码而不是跑 Compose：这一页是 `LazyColumn`，段与段的先后在源码里就是
 * `item(key = …)` 的先后，没有别的地方能改变它（除了 diff 用的 key，那个不改顺序）。
 */
class AppearanceOrderOnceTest {

    private val page = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AppearancePage.kt")
    private val text: String by lazy {
        assertTrue("找不到 ${page.absolutePath}", page.isFile)
        page.readText(Charsets.UTF_8)
    }

    private fun at(needle: String): Int {
        val i = text.indexOf(needle)
        assertTrue("外观页里找不到「$needle」", i >= 0)
        return i
    }

    @Test
    fun `十二段按稿的顺序出现`() {
        // 1 预览卡 → 2 背景色 → 3 主题 → 4 玻璃材质 → 5 通透度 → 6 柔化 → 7 暗度
        // → 8 壁纸 → 9 界面密度 → 10 首页模块 → 11 卡片(开关 + ›) → 12 恢复默认
        val segments = listOf(
            "item(key = \"preview\")" to "1 实时预览卡",
            "item(key = \"background-label\")" to "2 背景色",
            "CtlRow(label = \"主题\"" to "3 主题",
            "CtlRow(label = \"玻璃材质\")" to "4 玻璃材质",
            "CtlRow(label = \"通透度\")" to "5 通透度",
            "CtlRow(label = \"柔化\")" to "6 壁纸柔化",
            "CtlRow(label = \"暗度\")" to "7 壁纸暗度",
            "item(key = \"wallpaper-label\")" to "8 壁纸",
            "CtlRow(label = \"界面密度\"" to "9 界面密度",
            "item(key = \"home-modules\")" to "10 首页模块",
            "item(key = \"apps\")" to "11 卡片：默认折叠网页服务 + 减少动态",
            "item(key = \"reset\")" to "12 恢复默认外观",
        )
        var last = -1
        var lastLabel = ""
        for ((needle, label) in segments) {
            val i = at(needle)
            assertTrue("第 $label 段跑到了「$lastLabel」前面（第 12 段必须是稿里的顺序）", i > last)
            last = i
            lastLabel = label
        }
    }

    @Test
    fun `壁纸不再排在最后`() {
        // 返工总单 A2：「壁纸现在被放到最后（:670-834）→ 挪回第 8 位」。
        // 业主 2026-11-01 说过「壁纸的选项移到最下面」，那条只在**没有稿**的时候成立；
        // 现在稿 v2.1 把它定在第 8 位，以稿为准。
        assertTrue(
            "壁纸段必须排在首页段之前（第 8 位），不能又在页尾",
            at("item(key = \"wallpaper-label\")") < at("item(key = \"home-label\")"),
        )
    }

    @Test
    fun `容器背景不占十二段的位置，但功能还在`() {
        // A2：「多出「容器背景」，稿里没有 → 收进「实验」或隐藏，**不许删功能**」。
        // 它若夹在 12 段中间，逐条对照就会在第 3 段卡住。
        val container = at("item(key = \"container-style\")")
        assertTrue("容器背景跑到首页模块之前了，12 段顺序会被它打断",
            container > at("item(key = \"home-modules\")"))
        assertTrue("容器背景必须在恢复默认之前（页尾实验区）", container < at("item(key = \"reset\")"))
        assertTrue("「实验」这个分组头没了，容器背景就成了凭空多出来的一段",
            "SubSection(\"实验\"" in text)
        // 功能本体不许删：三段仍在
        assertTrue("容器背景的两版设置被删掉了（A2 只许收起来，不许删功能）",
            "ContainerStyle.entries.map" in text && "UiPrefs.saveContainerStyle" in text)
    }

    @Test
    fun `第 11 段的一张卡里有两行`() {
        // 稿里这一段是「开关 + ›」两行同处一卡；两行各自描边会看成两张卡。
        val card = text.substringAfter("item(key = \"apps\")").substringBefore("note?.let")
        assertTrue("第 11 段少了「默认折叠网页服务」这一行", "默认折叠网页服务" in card)
        assertTrue("第 11 段少了「减少动态与高对比」这一行", "减少动态与高对比" in card)
        assertTrue("两行必须是同一张 BorderedCard（否则看起来是两张卡）", "BorderedCard" in card)
        assertTrue("两行之间要有分隔线", "RowDivider()" in card)
        // 旧的「应用」分组头是稿里没有的层级，会把一张卡拆成两段
        assertTrue("「应用」分组头又回来了，第 11 段会被拆开", "SubSection(\"应用\")" !in text)
    }
}
