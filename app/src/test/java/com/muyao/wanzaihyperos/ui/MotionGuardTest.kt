package com.muyao.wanzaihyperos.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 动效与玻璃材质的**机械化守卫**：把 `docs/POLISH-AUDIT.md` 里能静态判出来的那几条，变成跑一次就红的测试。
 *
 * ## 为什么是测试而不是文档
 *
 * 判据写在文档里，只有"我下次记得去读"这一种执行力。2026-10-20 一夜里连出三次就是这种失败：
 * 顶栏纱没过采样层存活闸门 ⇒ 整条黑带（T67）；高对比那条开关的"读不到时"默认值写反 ⇒ 整片玻璃变实心（T69）；
 * 设置页重复 `item(key = …)` ⇒ 一点就崩（T68）。**前三条里只有 T68 会崩**，另两条不报错也不崩溃，
 * 只有看界面才发现，而看界面要占用业主的主力机。所以能静态判出来的一律不许靠"记得看"。
 *
 * ## 两类规则，纪律不一样
 *
 * - **硬规则（第 1、2、3、6 条）**：违规 = 一定错，数量必须是 0，不设基线、不留豁免额度。
 * - **棘轮规则（第 4、5 条）**：历史遗留一次改不完（全部收敛要同时动五六个不相干界面，违反"一次只改一个变量"），
 *   所以**只许变好、不许变差**。基线写在常量里，旁边注明它现在由哪几处构成；
 *   把基线往下调就是"又收敛了几处"的证据，往上调必须先有理由。
 *
 * 第 6 条里的白名单**不是豁免额度**，是一处有记录的设计决定（那块面本来就要压暗），
 * 数量不许增加。
 *
 * ## 判据的边界（别把它当成它做不到的那件事）
 *
 * - 只扫 `src/main/java` 下的 `.kt`。测试与 debug 源集里的数字是给验证工具用的，不是产品动效。
 * - 第 1 条只认 `tween(整数)` 这一种写法；弹簧的稳下来要多久**不在射程内**，那要真机逐帧量（审计 §6 的 S3）。
 * - 第 6 条是**文件级**文本判据：同一个文件里出现判据符号就算接了闸门，注释里提一句也算。
 *   要判"每一个调用点各自都过了闸门"得改用 AST，现在不划算 —— 所以新增玻璃面时**不许**把这条测试
 *   当成"闸门已经接好"的证明，仍然要自己在调用点确认。
 */
class MotionGuardTest {

    /** 工程根（测试的工作目录是 `app/`，往上找到 `app/build.gradle.kts`）。 */
    private val repoRoot: File
        get() {
            var d: File? = File(System.getProperty("user.dir"))
            while (d != null) {
                if (File(d, "app/build.gradle.kts").isFile) return d
                d = d.parentFile
            }
            error("找不到工程根目录")
        }


    private val uiSources: List<File>
        get() = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    /** 命中处统一报成 `文件名:行号  原文`，几条硬规则的报错才长得一样、能直接当修改位置用。 */
    private fun List<File>.hits(pattern: Regex): List<String> =
        flatMap { file ->
            file.readLines().mapIndexedNotNull { idx, line ->
                if (pattern.containsMatchIn(line)) "${file.name}:${idx + 1}  ${line.trim()}" else null
            }
        }

    // ── 1. 时长预算：UI 动效一律 ≤ 300ms（硬规则）───────────────────────
    //
    // 能例外的只有"环境类循环"：它不是对任何用户操作的响应，慢下来不伤害直接感。
    // 豁免精确到**行** —— 那一行被搬走或改掉，就要重新确认一次，不许悄悄多占整个文件的空间。
    /**
     * 环境类循环的白名单：**按"文件 + 时长"认，不按行号认**。
     *
     * ⚠️ 原来这里存的是行号（`"HomeStudio.kt" to 174`）。给那个文件加**一行 import** 就会把
     * 下面所有行推后一位 ⇒ 白名单失效、测试红，而红的原因跟动效毫无关系
     *（2026-10-22 我自己撞的一次：`HomeStudio.kt` 加了一行 `toPixelMap` 的 import）。
     * 时长是这段动画的**身份**（14 秒的线性漂移只有一个），行号不是。
     */
    private val ambientLoops = mapOf(
        // 首页视窗那幅画的缓慢漂移：业主自己要求的"会动的壁纸感"。
        // `tween(14_000)` + 线性 = 0.07Hz，在 Apple《Designing Fluid Interfaces》点名的"约 0.2Hz 摆动最难受"之下。
        "HomeStudio.kt" to 14_000,
    )

    @Test
    fun `UI 动效时长不超过 300ms`() {
        val budgetMs = 300
        // 允许 `tween(280)`、`tween(Int, …)`、`tween(durationMillis = 280)`，并**按整数读完整**：
        // 旧写法只取 `(\d+)`，把 `tween(14_000)` 读成 14ms 直接放过 —— 带下划线的时长全在漏，比误报危险。
        val tween = Regex("""tween(?:<[A-Za-z]+>)?\(\s*(?:durationMillis\s*=\s*)?(\d[\d_]*)""")
        val violations = uiSources.flatMap { file ->
            file.readLines().mapIndexedNotNull { idx, line ->
                val ms = tween.find(line)?.groupValues?.get(1)?.replace("_", "")?.toIntOrNull()
                    ?: return@mapIndexedNotNull null
                if (ms <= budgetMs) return@mapIndexedNotNull null
                if (ambientLoops[file.name] == ms) return@mapIndexedNotNull null
                "${file.name}:${idx + 1}  时长 ${ms}ms > ${budgetMs}ms"
            }
        }
        assertTrue(
            "这些动效超出 UI 预算（对用户操作的响应一律 ≤ ${budgetMs}ms）。确实属于环境类循环的，" +
                "才写进 ambientLoops 并注明理由： " + violations.joinToString(" | "),
            violations.isEmpty(),
        )
    }

    /**
     * **元素级**尺寸棘轮：搜索钮不许缩回（v3 #3 第 9 轮审计）。
     *
     * 为什么令牌级那条不够：`DesignTokensTest` 断的是 `Dims.minTouch >= 44dp` 这个**令牌**，
     * 管不住"调用点把 `52.dp` 改成 `40.dp`" —— 那正是这类回退的真实形状（审计原话）。
     * 判据抄清单自己写的口径：**命中区 ≥44dp、盘径 ≥38dp**。
     */
    @Test
    fun `搜索钮的命中区与盘径不许缩回`() {
        val file = File(repoRoot, "app/src/main/java/com/muyao/wanzaihyperos/ui/design/liquid/GlassControls.kt")
        assertTrue("找不到 ${file.absolutePath}", file.isFile)
        val text = file.readText(Charsets.UTF_8)
        val body = text.substringAfter("fun GlassSearchButton(").substringBefore("@Composable")
        // 盘径现在写成 `val discSize = 46.dp` + `.size(discSize)`（B4-01/14 轮：
        // 尺寸只有一份，折射高度要按它算）。所以先把 `discSize` 的声明解析出来顶替。
        val declaredDisc = Regex("""val discSize = (\d+(?:\.\d+)?)\.dp""")
            .find(body)?.groupValues?.get(1)?.toFloat()
        val sizes = buildList {
            Regex("""size\((\d+(?:\.\d+)?)\.dp\)""").findAll(body).forEach { add(it.groupValues[1].toFloat()) }
            if (declaredDisc != null) add(declaredDisc)
        }
        assertTrue("搜索钮里找不到 `size(N.dp)` ⇒ 判据失效了，别放它过", sizes.isNotEmpty())
        assertTrue(
            "命中区最大只有 ${sizes.maxOrNull()}dp，低于 44dp 底线（#3：他抱怨过点不中）⇒ 实得 $sizes",
            sizes.maxOrNull()!! >= 44f,
        )
        // ⚠️ 只看**最大的两个**：第三个是最里面那个图标（26dp），它本来就比盘小 ——
        //   第一版我写成 `min >= 38`，于是被图标判红了（判据写宽/写错都会逼人绕过它）。
        val sorted = sizes.sortedDescending()
        assertTrue(
            "盘径（第二大的那个 size）小于 38dp（清单口径）：$sizes —— 以前就是 40dp，改回去等于这轮白做",
            sorted.size >= 2 && sorted[1] >= 38f,
        )
        // B4-01（业主 2026-10-23 第 1 轮审计的 must-fix）：**上界**。
        //   只钉下界的话这条棘轮是半根：把它从 46 改回 52/56 它照样绿，
        //   而"再小一点"正是这一条的原始诉求 ⇒ 盘径必须落进 38..48 这条带里。
        assertTrue(
            "盘径（第二大的那个 size）是 ${sorted[1]}dp，超出 48dp 上界（B4-01 要的是「再小一点」）：$sizes",
            sorted[1] <= 48f,
        )
    }

    // ── 2. 不许"从无到有"：进场不能从 scale 0 起（硬规则）───────────────
    @Test
    fun `进场不许从 scale 0 开始`() {
        val fromNowhere = Regex("""(initialScale|scale)\s*=\s*0f\b""")
        val found = uiSources.hits(fromNowhere)
        assertTrue("真实世界没有东西会凭空出现：进场从 0.9~0.97 + 透明度 0 起。\n" + found.joinToString("\n"), found.isEmpty())
    }

    // ── 3. UI 上不许用 ease-in 类（硬规则）─────────────────────────────
    //
    // ease-in 起步慢，而起步那一刻正是用户盯着看的时候 ⇒ 同样时长下它比 ease-out 明显更"黏"。
    // `LinearInEasing` 这一支同时覆盖 `FastOutLinearInEasing`（Material 里它确实被用作"离场曲线"，
    // 本工程的判据是不许出现在界面代码里）。`EaseIn\b` 带词边界，所以合法的 `EaseInOut` 不会被误报。
    @Test
    fun `UI 缓动不许用 ease-in 类`() {
        val easeIn = Regex("""LinearInEasing|\bEaseIn\b|\beaseIn\b""")
        val found = uiSources.hits(easeIn)
        assertTrue(
            "进出场用 ease-out（或 FastOutSlowIn），屏内位移用 ease-in-out；ease-in 一律算缺陷。\n" +
                found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    // ── 4. 棘轮：弹簧参数不许继续裸写，要往令牌收敛 ──────────────────────
    //
    // 基线 19 = `liquid/GlassControls.kt` 10 + `main/WanzaiMainShell.kt` 4 + `glass/BarOptics.kt` 2
    //         + `home/HomeStudio.kt` 2 + `design/SettingsChrome.kt` 1。
    // 对照事实：`Motion` 时长令牌全工程**只被引用 2 次**（`DelayedLoading`、`MainShell`），
    // 也就是说曲线基本是各写各的 —— "五组手写且几乎相同的数"本身就是审计项。
    // 引用 `Spring.DampingRatioMediumBouncy` 这类常量的写法**不计入**裸写：那是我们要收敛成的样子。
    private val springLiteralBaseline = 19

    @Test
    fun `裸写的弹簧参数只许减少`() {
        val spring = Regex("""(stiffness|dampingRatio)\s*=\s*[0-9]""")
        val count = uiSources.sumOf { file -> spring.findAll(file.readText()).count() }
        assertTrue(
            "弹簧字面量从基线 $springLiteralBaseline 涨到 $count —— 新动效请引用 Motion/MotionPrefs 令牌，" +
                "别再造一组几乎一样的数。",
            count <= springLiteralBaseline,
        )
    }

    // ── 5. 棘轮：无障碍闸门的接线点不许减少 ──────────────────────────────
    //
    // 系统动画缩放 / 高对比 / 减弱透明度这三条一旦被某次改动绕过，界面上**只是风格变了**，
    // 不报错也不崩 —— 只能靠这条兜住（T69 就是这一类：整片玻璃变实心，日志里才看得出）。
    // 基线 10 处的构成：液态玻璃栈 3（`liquid/GlassBar`、`liquid/GlassControls`、`liquid/IosChrome`）
    //         + 另一套玻璃栈 1（`glass/GlassSurface`）+ 通道本身 3（`Motion`、`Haptics`、`data/UiPrefs`）
    //         + 界面接线 3（设置页、主壳、根）。少一个文件 = 有人把某一条支路的降级分支删了。
    private val a11yGateBaseline = 10

    @Test
    fun `无障碍闸门的接线点不许减少`() {
        val gate = Regex("""rememberMotion|MotionPrefs|canMove|glassIsTranslucent""")
        val files = uiSources.filter { gate.containsMatchIn(it.readText()) }
        assertTrue(
            "读无障碍信号的文件从基线 $a11yGateBaseline 降到 ${files.size} —— 是不是有人把降级分支删了？",
            files.size >= a11yGateBaseline,
        )
    }

    // ── 6. 每面玻璃都必须过"采样层不是空壳"这道闸（硬规则）───────────────
    //
    // T67 那两次事故（黑带、黑饼）根因是同一条：Miuix 拿**空采样层**去模糊，不是"没效果"，
    // 而是铺不透明黑。所以每个采页面层的调用点，都必须和下面三个判据之一待在同一个文件里。
    //
    // 白名单只有一处，而且是**设计决定不是漏接**：`GlassControls.kt` 里那个整屏遮罩本来就要压暗，
    // 空层最坏是"暗得实一点"，不会出现"内容上盖一块黑饼"；给它接闸门反而要让遮罩变透明。
    // 这个名单不许变长 —— 再加一项就是在多要一块"可以不接闸门"的玻璃。
    private val backdropWhitelist = setOf("GlassControls.kt")

    @Test
    fun `每面玻璃都接了采样层存活闸门`() {
        // 只认**调用点**：`Modifier.drawBackdrop(backdrop = …)` 一定带 `backdrop =`（同一行或下一行）。
        // 不带它的是定义与转发（`CombinedBackdrop` 里那个 `override fun DrawScope.drawBackdrop`）
        // 和注释 —— 第一版没区分，把这两个文件误判成违规了。
        val callSites = uiSources.filter { file ->
            val lines = file.readLines()
            lines.withIndex().any { (i, line) ->
                line.contains("drawBackdrop(") &&
                    lines.drop(i).take(2).any { it.contains("backdrop =") }
            }
        }
        val guard = Regex("""backdropIsLive|rememberSampleSettled|glassIsTranslucent""")
        val offenders = callSites
            .filterNot { guard.containsMatchIn(it.readText()) }
            .filterNot { it.name in backdropWhitelist }
            .map { it.name }
        assertTrue(
            "这些文件在采页面层却没接存活闸门 —— 空采样帧会被 Miuix 画成不透明黑" +
                "（T67 的黑带与黑饼都是这一条）： " + offenders.joinToString(" | "),
            offenders.isEmpty(),
        )
    }
}
