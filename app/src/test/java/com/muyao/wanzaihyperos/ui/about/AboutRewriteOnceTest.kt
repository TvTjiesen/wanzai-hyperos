package com.muyao.wanzaihyperos.ui.about

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「柒合院」这一版关于族的可复跑验收（2026-11-08）。
 *
 * 业主这一轮给的是**口述**，不是稿：
 * 「关于页写：柒合院。浙大（city college 不发音）非官方校园聚合客户端……再加个
 * 单开一个容器的作者的页面，点进去就是我的 github 主页，要显示我的 github 头像。
 * 底下的品牌与图标方案可以去掉……你还可以写一份用户协议和隐私政策在最底下。」
 *
 * 口述最容易在后续迭代里被"顺手改回去"（比如又塞一条外链、又把品牌页加回来、
 * 又让协议页去拉远程文档）。所以这里把七条形状钉成断言：
 *
 * 1. 应用名就是「柒合院」，debug 版带「（新版·测试）」后缀；
 * 2. 关于页的名称与副标题按口述原文（含 city college 那句梗）；
 * 3. 关于页用的是**他自己的图标**（`ic_brand`），不是虚线占位框；
 * 4. 作者页有头像资源 + GitHub 主页 + 作者名；
 * 5. 用户协议与隐私政策各自成页，正文来自 `LegalText`（本地常量，不联网）；
 * 6. 两份文本的关键事实必须写出来（「只留在这台手机上」「不上传」）；
 * 7. 旧名「鲸集」在源码里只剩一条历史注释，不许回到界面。
 */
class AboutRewriteOnceTest {

    private val mainRoot = "src/main/java/com/muyao/wanzaihyperos"
    private val resRoot = "src/main/res"

    private fun code(path: String): String =
        SourceScan.stripComments(File("$mainRoot/$path").readText(Charsets.UTF_8))

    private fun res(path: String): String = File("$resRoot/$path").readText(Charsets.UTF_8)

    // ── 1. 应用名 ─────────────────────────────────────────────────────
    @Test
    fun `应用名是柒合院，debug 版仍带测试后缀`() {
        val main = res("values/strings.xml")
        assertTrue("app_name 没改成柒合院", "<string name=\"app_name\">柒合院</string>" in main)

        val debug = File("src/debug/res/values/strings.xml").readText(Charsets.UTF_8)
        assertTrue("debug 版没跟着改名", "柒合院" in debug)
        assertTrue("debug 版丢了「（新版·测试）」后缀，装机会分不清新旧版", "（新版·测试）" in debug)
    }

    // ── 2. 关于页的名称与副标题 ───────────────────────────────────────
    @Test
    fun `关于页写柒合院，副标题按口述原文`() {
        val about = code("ui/about/AboutPage.kt")
        assertTrue("关于页没有「柒合院」", "\"柒合院\"" in about)
        assertTrue(
            "副标题没写 city college 那句（业主原话，别改写成「城市学院」）",
            "浙大（city college 不发音）非官方校园聚合客户端" in about,
        )
        assertTrue("关于页还留着「名称待定」", "名称待定" !in about)
    }

    // ── 3. 头像直接显示在关于页（业主 2026-11-09）────────────────────
    @Test
    fun `关于页直接显示作者头像，不再是图标或虚线占位框`() {
        val about = code("ui/about/AboutPage.kt")
        assertTrue("关于页没画头像", "R.drawable.author_avatar" in about)
        assertTrue("关于页还在画虚线占位框", "dashPathEffect" !in about)
        assertTrue("关于页还写着「你的图标」占位文案", "你的图标" !in about)
        assertTrue("头像资源不存在", File("$resRoot/drawable-nodpi/author_avatar.png").exists())
    }

    // ── 4. 作者页 ─────────────────────────────────────────────────────
    @Test
    fun `作者页有头像、GitHub 主页与作者名`() {
        val author = code("ui/about/AuthorPage.kt")
        assertTrue("作者页没画头像", "R.drawable.author_avatar" in author)
        assertTrue("作者页没接 GitHub 主页", "AboutLinks.github" in author)
        assertTrue("作者页没写作者名", "TvTjiesen" in author)
        assertTrue("头像资源不存在", File("$resRoot/drawable-nodpi/author_avatar.png").exists())
        // 头像必须是圆形裁切，不然 320×320 的方图会露白角。
        assertTrue("头像没做圆形裁切", "CircleShape" in author)
        // 关于页只留原项目仓库一个外链，作者主页与酷安都收进作者页。
        val about = code("ui/about/AboutPage.kt")
        assertTrue("关于页没留原项目仓库", "AboutLinks.repo" in about)
        assertTrue("关于页又摆回了作者主页外链", "AboutLinks.github" !in about)
    }

    // ── 5. 权限与隐私口径 ─────────────────────────────────────────────
    @Test
    fun `协议与隐私各自成页，正文来自本地常量`() {
        val terms = code("ui/about/LegalText.kt")
        assertTrue("没有用户协议正文", "val terms: List<LegalSection>" in terms)
        assertTrue("没有隐私政策正文", "val privacy: List<LegalSection>" in terms)

        val page = code("ui/about/LegalPage.kt")
        assertTrue("法律文本页不接受外部正文", "sections: List<LegalSection>" in page)
        // 断网也要读得到 —— 不许出现 http 拉取的痕迹。
        assertTrue("法律文本页在联网拉正文", "http" !in page.lowercase())

        val root = code("WanzaiRoot.kt")
        assertTrue("没接用户协议路由", "Route.Terms" in root)
        assertTrue("没接隐私政策路由", "Route.Privacy" in root)
    }

    @Test
    fun `两份文本把「只在本机」和「不上传」写明`() {
        val terms = LegalText.terms
        val privacy = LegalText.privacy
        assertTrue("用户协议节数太少（应 ≥ 6），实际 ${terms.size}", terms.size >= 6)
        assertTrue("隐私政策节数太少（应 ≥ 8），实际 ${privacy.size}", privacy.size >= 8)

        val all = (terms + privacy).joinToString("\n") { it.title + "\n" + it.body }
        for (needle in listOf("不会上传", "只留在这台手机上", "Keystore", "hzcu.edu.cn", "没有统计")) {
            assertTrue("两份文本都没写「$needle」", needle in all)
        }
        // 不许出现「已通过法律审查」这类做不到的背书。
        assertTrue("文本里出现了不该有的背书", "法律审查" !in all)
    }

    // ── 6. 旧名清场 ───────────────────────────────────────────────────
    @Test
    fun `旧名鲸集只留在历史注释里，界面上一处都不许剩`() {
        val hits = mutableListOf<String>()
        val base = File(".")
        base.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { it.path.replace('\\', '/').contains("/src/main/") || it.path.replace('\\', '/').contains("/src/debug/") }
            .forEach { f ->
                val text = f.readText(Charsets.UTF_8)
                if (!text.contains("鲸集")) return@forEach
                // 只允许 strings.xml 里那条「历史名」注释提到旧名 —— 注释要连 XML 的
                // `<!-- -->` 一起剥掉，因为 `SourceScan.stripComments` 只认 Kotlin 的 // 与 /* */。
                val noComments = text
                    .replace(Regex("<!--[\\s\\S]*?-->"), "")
                    .let { SourceScan.stripComments(it) }
                if (noComments.contains("鲸集")) hits += f.path
            }
        assertEquals("这些文件的代码里还残留旧名：$hits", 0, hits.size)
    }
}
