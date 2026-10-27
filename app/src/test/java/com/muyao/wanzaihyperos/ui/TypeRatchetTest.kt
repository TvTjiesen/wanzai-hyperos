package com.muyao.wanzaihyperos.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 字号棘轮：钉住「**不许绕过 `Type.*` 硬编码字号**」，以及"任何屏幕文字都不得低于 `Type.micro`"。
 *
 * ## 为什么光有 `DesignTokensTest` 不够（清单 #11，独立审计静态证死）
 *
 * 业主报的是「**屏幕上的字**太小」。`DesignTokensTest` 断的是**令牌值**（micro ≥ 10sp、secondary ≥ 13sp），
 * 而调用点里还有一批直接写 `fontSize = 8.5.sp` 的 —— 令牌改了它们不动，
 * 于是出现最坏的一种"绿"：**598 条测试全绿，业主看到的还是 8.5sp 的课表格子**。
 * 这正是本工程记过的那个坑（代理指标会绿着变差）。
 *
 * ## 两条断言的分工
 *
 *  - **绝对那条**：`fontSize` 字面量一律不许低于 10sp —— 这是地板，没有"历史包袱"可讲。
 *  - **棘轮那条**：硬编码总数只许往下、不许往上。基线是当前实测值；
 *    每把一处搬回 `Type.*` 就把数字往下调一格。**新增一律判负。**
 *    思路照抄 `MotionGuardTest` 里"裸写弹簧棘轮"那一条（同一类"存量先认、增量不许"的写法）。
 */
class TypeRatchetTest {

    /**
     * 扫描根：**`src/debug` 也算**（第 9 轮审计必修）。
     *
     * 以前只扫 `src/main/java`，于是业主走查用的那套 `debug_section`（冷启动才生效的那批界面）
     * **完全不在字号规则的覆盖内** —— 他看到的"小字"有一半在守卫之外。
     */
    private val srcRoots = listOf(
        File("src/main/java/com/muyao/wanzaihyperos"),
        File("src/debug/java/com/muyao/wanzaihyperos"),
    )

    private fun hits(pattern: Regex): List<String> {
        val out = mutableListOf<String>()
        srcRoots.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "DesignTokens.kt" }
                .forEach { f ->
                    f.readLines().forEachIndexed { i, line ->
                        if (pattern.containsMatchIn(line)) out += "${f.name}:${i + 1} ${line.trim()}"
                    }
                }
        }
        return out
    }

    /**
     * 地板（第 9 轮审计把这条的洞堵上了）。
     *
     * ⚠️ 原来正则只写 `fontSize\s*=\s*(?:[0-9]|9(?:\.\d+)?)\.sp` —— **一个字都没管 `lineHeight`**，
     *   于是三处 `lineHeight = 9.sp`（`AppsSectionPage.kt` / `HomeSectionPage.kt` ×2）全部绕过这条规则。
     *   9sp 的行高在中文里连字都放不下（10sp 的字挤在 9sp 的行框里必然被裁），
     *   而"行高被裁"正是业主看到"字糊/挤"的最直接来源。现在两个属性都判。
     */
    @Test
    fun `任何屏幕文字的 fontSize 与 lineHeight 都不许低于 10sp`() {
        val small = hits(Regex("""(fontSize|lineHeight)\s*=\s*(?:[0-9]|9(?:\.\d+)?)\.sp"""))
        assertTrue(
            "这些字号/行高低于 10sp（业主 #11 抱怨的就是这一档）。改走 `Type.*`，不许放宽这条：" +
                "\n" + small.joinToString("\n"),
            small.isEmpty(),
        )
    }

    @Test
    fun `硬编码字号总数只许降不许升`() {
        val all = hits(Regex("""(fontSize|lineHeight)\s*=\s*[0-9]+(?:\.[0-9]+)?\.sp"""))
        assertTrue(
            "找不到源码目录：${srcRoots.map { it.absolutePath }}",
            srcRoots.any { it.isDirectory },
        )
        assertTrue(
            "硬编码字号/行高从基线 " + BASELINE + " 涨到 " + all.size + " 处。\n" +
                "屏幕上的字号只有走 `Type.*` 才会随 #11 那次整体上调一起动；" +
                "新增一处就会多一个改不动的地方。\n" +
                "确实搬回令牌了就把 BASELINE 往下调，并把这一条的注释改成新的实测值：\n" +
                all.joinToString("\n"),
            all.size <= BASELINE,
        )
    }

    @Test
    fun `底栏标签走令牌而不是写死 12sp`() {
        val f = File("src/main/java/com/muyao/wanzaihyperos/ui/design/liquid/LiquidGlassNavigationBar.kt")
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        val text = f.readLines().filter { "item.label" in it || "fontSize = Type.summary" in it }
        assertTrue(
            "底栏那五个标签必须读 `Type.summary`：#12/#11 以后调字号阶梯时它要跟着动，" +
                "以前是硬编码 12sp（独立审计点名过）",
            text.any { it.contains("fontSize = Type.summary") },
        )
    }

    /**
     * 小字（`Type.micro` 那一档）必须有**等效的无障碍标签**（第 9 轮审计必修）。
     *
     * 为什么单开一条：「看得见但读不出来」与「读得出来但看不清」是两个不同的问题，
     * 各要各的闸门 —— 字号那条管的是后者，这条管前者。
     * 判据是**文件级**的（同一份文件里必须出现 `contentDescription` / `onClickLabel` /
     * `semantics` 之一）：真要判到"这一个 Text 有没有标签"需要解析组合树，静态扫描做不到；
     * 文件级至少能拦住"整份新界面全是小字、一条标签都没有"这种最坏情况，而且不会假绿到骗人。
     */
    @Test
    fun `用了最小字号的文件必须有等效的无障碍标签`() {
        val root = srcRoots.first()
        val offenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                val text = f.readText(Charsets.UTF_8)
                if (!text.contains("Type.micro")) return@forEach
                val labelled = text.contains("contentDescription") ||
                    text.contains("onClickLabel") ||
                    text.contains("semantics")
                if (!labelled) offenders += f.name
            }
        assertTrue(
            "这些文件用了最小字号却一条无障碍标签都没有（读屏读不出它们）：$offenders",
            offenders.isEmpty(),
        )
    }

    /** 当前实测存量（`fontSize`/`lineHeight` 字面量，`DesignTokens.kt` 自身除外）。只许往下调。 */
    private companion object {
        const val BASELINE = 27
    }
}
