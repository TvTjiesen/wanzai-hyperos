package com.muyao.wanzaihyperos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「感谢」页与真实构建清单不许漂移（v3 #9 的验收判据：
 * 依赖名与许可证从 `build.gradle` + Gradle 依赖报告取，不凭记忆写）。
 *
 * 这条测试不联网、不跑 Gradle，只做一件事：
 * **把 `app/build.gradle.kts` 里声明的每一个 implementation 依赖，与 [Credits.dependencies] 对一遍**。
 * 以后谁加了一支依赖却忘了登记到感谢页，这里就会红 ——
 * 因为"许可证不凭记忆写"这句话，靠注释是守不住的。
 */
class CreditsTest {

    private val repoRoot: File
        get() {
            var d: File? = File(System.getProperty("user.dir"))
            while (d != null) {
                if (File(d, "app/build.gradle.kts").isFile &&
                    File(d, "gradle/libs.versions.toml").isFile
                ) {
                    return d
                }
                d = d.parentFile
            }
            error("找不到工程根目录（user.dir=${System.getProperty("user.dir")}）")
        }

    /**
     * 解析 version catalog 的 `[libraries]`。两种写法都要认：
     * `x = { module = "g:a", version.ref = "v" }` 与本仓库实际用的
     * `x = { group = "g", name = "a", version.ref = "v" }`。
     */
    private fun catalogModules(): Map<String, String> {
        val toml = File(repoRoot, "gradle/libs.versions.toml").readText()
        val out = mutableMapOf<String, String>()
        Regex("""^\s*([A-Za-z0-9_-]+)\s*=\s*\{([^}]*)""", RegexOption.MULTILINE)
            .findAll(toml).forEach { m ->
                val key = m.groupValues[1]
                val body = m.groupValues[2]
                val module = Regex("""module\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                val g = Regex("""group\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                val n = Regex("""name\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                when {
                    module != null -> out[key] = module
                    g != null && n != null -> out[key] = "$g:$n"
                }
            }
        return out
    }

    /** 解析 `build.gradle.kts` 里 implementation 的坐标（`libs.xxx` 或直接写字符串）。 */
    private fun declaredCoordinates(): Set<String> {
        val gradle = File(repoRoot, "app/build.gradle.kts").readText()
        val modules = catalogModules()
        val out = mutableSetOf<String>()
        Regex("""implementation\(\s*(?:libs\.([A-Za-z0-9_.\-]+)|"([^"]+)")""")
            .findAll(gradle).forEach { m ->
                val alias = m.groupValues[1]
                val literal = m.groupValues[2]
                if (alias.isNotBlank()) {
                    // `libs.androidx.camera.core` → 目录里的键是 `androidx-camera-core`
                    modules[alias.replace('.', '-')]?.let { out += it }
                } else if (literal.isNotBlank() && literal.count { it == ':' } >= 1) {
                    out += literal.substringBeforeLast(':')
                }
            }
        return out
    }

    @Test
    fun `每一条 gradle 里声明的 implementation 依赖都能在感谢页找到`() {
        val declared = declaredCoordinates()
        assertTrue("没解析到任何依赖，八成是解析器跟不上 gradle 写法了", declared.isNotEmpty())
        val credited = Credits.dependencies.map { it.coordinate }.toSet()
        val missing = declared.filter { d -> credited.none { it.startsWith(d) } }.sorted()
        assertEquals("这些依赖没登记到感谢页（加依赖要顺手补 Credits）", emptyList<String>(), missing)
    }

    @Test
    fun `感谢页每条都有版本号与许可证，且坐标不重复`() {
        val coords = Credits.dependencies.map { it.coordinate }
        assertEquals("坐标重复", coords.size, coords.distinct().size)
        Credits.dependencies.forEach {
            assertTrue("${it.coordinate} 缺版本", it.version.isNotBlank())
            assertTrue("${it.coordinate} 缺许可证", it.license.isNotBlank())
        }
    }

    /**
     * 明细页必须**从 `Credits` 渲染**，不许在界面里手写任何一条库名/许可证
     * （手写就会出现"界面上列了一个根本没用的库"）。另钉住路由真的注册了。
     *
     * 2026-11-08 改指向：稿（`screens/licenses.png`）把明细整段放在「开源与字体」页，
     * 关于页只剩四行摘要。断言本身没变，只是跟着搬家 —— 仍有且只有一处渲染明细。
     */
    @Test
    fun `关于页渲染依赖清单而不是手写`() {
        var d: java.io.File? = java.io.File(System.getProperty("user.dir"))
        while (d != null && !java.io.File(d, "app/build.gradle.kts").isFile) d = d.parentFile
        val root = requireNotNull(d) { "找不到工程根" }
        val page = java.io.File(
            root,
            "app/src/main/java/com/muyao/wanzaihyperos/ui/about/LicensesPage.kt",
        ).readText(Charsets.UTF_8)
        assertTrue("开源与字体页没引用 Credits.dependencies", "Credits.dependencies" in page)
        assertTrue("开源与字体页没把改写与字体一起列出来", "Credits.derived" in page && "Credits.fonts" in page)
        val about = java.io.File(
            root,
            "app/src/main/java/com/muyao/wanzaihyperos/ui/about/AboutPage.kt",
        ).readText(Charsets.UTF_8)
        // 2026-11-08 改判：业主把两个外链收进了新的「作者」页（要连 GitHub 头像一起展示），
        // 关于页只留一个「作者」入口。所以「链接真的被渲染」这条改指 AuthorPage，
        // 关于页反向钉住它不再自己摆外链（免得出现两处入口、改一处忘一处）。
        val author = java.io.File(
            root,
            "app/src/main/java/com/muyao/wanzaihyperos/ui/about/AuthorPage.kt",
        ).readText(Charsets.UTF_8)
        assertTrue("作者页没引用 AboutLinks", "AboutLinks.github" in author && "AboutLinks.coolapk" in author)
        // 2026-11-09 业主改口：关于页保留**原项目仓库**这一个外链，作者主页仍在作者页。
        assertTrue("关于页没留原项目仓库", "AboutLinks.repo" in about)
        assertTrue(
            "关于页又摆回了作者主页外链（那只该在作者页）",
            "AboutLinks.github" !in about,
        )
        // 许可证名只允许从数据层来：**模式匹配**（上一版只黑名单三个完整串，
        // 写成 `Apache 2.0` / `GPL-3.0` 照样过）
        val licensePattern = Regex("""(?i)\b(apache|mit|bsd|gpl|lgpl|mpl|zlib)\b[\s-]*[\d.\-]*(license)?""")
        val hand = licensePattern.findAll(page).map { it.value }.toList()
        assertTrue("开源与字体页里手写了许可证名：$hand —— 必须从 Credits 来", hand.isEmpty())
        val nav = java.io.File(
            root,
            "app/src/main/java/com/muyao/wanzaihyperos/WanzaiRoot.kt",
        ).readText(Charsets.UTF_8)
        assertTrue("Route.About 没在导航里注册", "entry<Route.About>" in nav)
    }

    @Test
    fun `关于页的外链必须是他给过的真地址`() {
        // 业主 2026-10-23 给了身份：GitHub 主人 TvTjiesen、酷安 22174009
        // （来源 hzr/.../util/Links.kt:13-15）。判据：非空 + https + 主机名对得上。
        assertTrue("GitHub 链接没填", AboutLinks.github.isNotBlank())
        assertTrue("GitHub 链接必须是 https", AboutLinks.github.startsWith("https://"))
        assertTrue(
            "GitHub 链接必须指到他的主页（实得 ${AboutLinks.github}）",
            AboutLinks.github.startsWith("https://github.com/TvTjiesen"),
        )
        assertTrue("酷安链接没填", AboutLinks.coolapk.isNotBlank())
        assertTrue("酷安链接必须是 https", AboutLinks.coolapk.startsWith("https://"))
        assertTrue(
            "酷安链接必须是他本人的主页（实得 ${AboutLinks.coolapk}）",
            AboutLinks.coolapk.startsWith("https://www.coolapk.com/u/22174009"),
        )
    }

    /**
     * 反向守卫（工单第 14 轮）：`Credits.dependencies` 的**每一条**坐标都必须能在
     * `build.gradle.kts` / `gradle/libs.versions.toml` 里找到出处 —— 凭空项查不出，
     * 上一版只做了 declared→credited 一个方向。
     */
    @Test
    fun `感谢页每条坐标都能在构建文件里找到出处`() {
        var d: java.io.File? = java.io.File(System.getProperty("user.dir"))
        while (d != null && !java.io.File(d, "app/build.gradle.kts").isFile) d = d.parentFile
        val root = requireNotNull(d) { "找不到工程根" }
        val build = java.io.File(root, "app/build.gradle.kts").readText(Charsets.UTF_8)
        val catalogFile = java.io.File(root, "gradle/libs.versions.toml")
        val catalog = if (catalogFile.isFile) catalogFile.readText(Charsets.UTF_8) else ""
        val haystack = build + "\n" + catalog
        // 白名单只放"工具链自动带进来、build 文件里不会出现"的坐标，且上限 2 条 ——
        // 再大就会变成吃掉凭空项的垃圾桶。
        val toolchain = setOf("org.jetbrains.kotlin:kotlin-stdlib")
        assertTrue("白名单超长（会掩盖凭空项）", toolchain.size <= 2)
        val phantom = Credits.dependencies.filter { c ->
            if (c.coordinate in toolchain) return@filter false
            val group = c.coordinate.substringBefore(':')
            val artifact = c.coordinate.substringAfter(':', "")
            group !in haystack || artifact !in haystack
        }.map { it.coordinate }
        assertTrue("这些坐标在构建文件里找不到（凭空写的？）：$phantom", phantom.isEmpty())
    }

    /**
     * 衍生作品与字体也必须渲染（Apache-2.0 §4 的署名义务 + 随包字体的披露）。
     * 上一版 `Credits.derived` / `Credits.fonts` **全仓零引用**。
     */
    @Test
    fun `衍生作品与随包字体都渲染了`() {
        var d: java.io.File? = java.io.File(System.getProperty("user.dir"))
        while (d != null && !java.io.File(d, "app/build.gradle.kts").isFile) d = d.parentFile
        val root = requireNotNull(d) { "找不到工程根" }
        // 2026-11-08（A9 拆页）：三组清单从关于页搬到 LicensesPage，关于页只留摘要。
        // 断言不删、只把搜索范围扩到两页 —— 「衍生作品与字体必须渲染」这条义务本身没变。
        val about = java.io.File(root, "app/src/main/java/com/muyao/wanzaihyperos/ui/about/AboutPage.kt")
            .readText(Charsets.UTF_8)
        val licenses = java.io.File(root, "app/src/main/java/com/muyao/wanzaihyperos/ui/about/LicensesPage.kt")
            .readText(Charsets.UTF_8)
        val page = about + "\n" + licenses
        assertTrue("关于页/许可页都没渲染 Credits.derived（衍生作品署名没履行）", "Credits.derived" in page)
        assertTrue("关于页/许可页都没渲染 Credits.fonts（随包字体没披露）", "Credits.fonts" in page)
        // 2026-11-09 业主让把关于页底下的注释去掉，版本号改由设置页那行副标题承担。
        val settings = java.io.File(
            root,
            "app/src/main/java/com/muyao/wanzaihyperos/ui/main/pages/SettingsSectionPage.kt",
        ).readText(Charsets.UTF_8)
        assertTrue("关于页/设置页都没有版本号（BuildConfig.VERSION_NAME）", "BuildConfig.VERSION_NAME" in settings || "BuildConfig.VERSION_NAME" in about)
    }

    /**
     * 逐条期望值（工单第 17 轮）：上一版只判"像不像个许可证名"，而且那条正则里
     * 两个 `` 落成了**字面退格字节**（0x08）⇒ 恒不匹配、恒通过，**从没检查过任何东西**。
     * 现在钉死几条关键项的**确切值**（都是评审逐条核过 POM 的结论，别改）。
     *
     * 自证：把 `Credits.derived` 的许可证改成 `MIT License` ⇒ 本条必红。
     */
    @Test
    fun `关键项的许可证逐条对得上`() {
        assertEquals(
            "衍生作品（AndroidLiquidGlass）必须是 Apache License 2.0",
            "Apache License 2.0",
            // 2026-11-08：`derived` 从 1 条变成 2 条（补了 grok-ball 的 MIT 署名，
            // MIT 要求随分发保留）。所以这里按 coordinate 取，不能用 `single()`。
            Credits.derived.single { it.coordinate == "github.com/Kyant0/AndroidLiquidGlass" }.license,
        )
        assertTrue(
            "随包字体的授权文本未随包存放，这一条必须照实写（不许代答）",
            "未随包存放" in Credits.fonts.single().license,
        )
        val byCoord = Credits.dependencies.associateBy { it.coordinate }
        listOf(
            "top.yukonga.miuix.kmp:miuix-ui-android",
            "io.github.kyant0:backdrop",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android",
        ).forEach { coord ->
            val c = byCoord[coord] ?: error("Credits 里缺 $coord")
            assertTrue("$coord 的许可证应含 Apache License 2.0，实际：${c.license}", "Apache License 2.0" in c.license)
        }
    }
}
