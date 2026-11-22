package com.muyao.wanzaihyperos.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「状态要真的发出去」—— 扫源码钉住仓库层那条 `_state.value = result.fold(` 的写法。
 *
 * ## 为什么要有这条（v3 清单 #4，独立审计第 2 轮抓到的致命一条）
 *
 * `settleFirstPage(…)` / `failWith(…)` / `appendPage(…)` 都是 `ListState<T>` 上的**纯函数**
 * （见 [ListState]：它们返回一份新状态，不改自己）。工程里正确写法是
 * `_state.value = result.fold(…)`，让 fold 的返回值被接回去。
 * `JwTimetableRepository` 曾经写成光一句 `result.fold(` —— 编译过、590 条测试全绿、
 * 日志里没有一句错，而**教务课表在一次会话里从来没上过屏**：
 * 没快照时永远停在「正在取」，有快照时永远停在「上次存的 + 正在重新取」。
 *
 * 这类"返回值被丢掉"的 bug 编译器不管（Kotlin 允许丢弃返回值），而这些仓库都要真网络、
 * 真登录，单测没法直接调 `refresh()` 断言状态变化；但它可以静态判出来。
 * 手法与 `LazyColumnKeyUniquenessTest` 一致。
 */
class StatePublishingTest {

    /** fold 当语句用（本行与上一行都没有 `=`）时，它的 lambda 体里必须有人把状态接回去。 */
    @Test
    fun `数据层不许出现把 fold 返回值丢在原地的写法`() {
        val dataRoot = File("src/main/java/com/muyao/wanzaihyperos/data")
        assertTrue("找不到数据层目录：$dataRoot.absolutePath", dataRoot.isDirectory)

        val assignInside = Regex("""(?:_state|_counts|_stale|flow)\.value\s*=""")
        val offenders = mutableListOf<String>()
        dataRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { i, line ->
                val bareFold = line.trim().startsWith("result.fold(") &&
                    !line.contains("=") &&
                    (i == 0 || !lines[i - 1].trim().endsWith("="))
                if (bareFold) {
                    val body = lines.drop(i + 1).take(40).joinToString("\n")
                    if (!assignInside.containsMatchIn(body)) offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertTrue(
            "这些 fold 把算出来的状态丢在原地（界面会永远停在「正在取」）：$offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * 教务那条链**必须把 fold 的返回值接回 state**（第 2 轮审计的致命一条，单独钉死）。
     *
     * 通用扫描判不了上下文：`_state.value.settleFirstPage(…)` 写在
     * `_state.value = result.fold(…)` 的 lambda 里是**正确**写法（返回值由外层接走）。
     * 而"教务那份永不发布"的真实代价是他每次开机都看不到新课表 —— 值得钉一条具体的。
     */
    @Test
    fun `教务课表仓库必须把 fold 的返回值接回 state`() {
        val file = File("src/main/java/com/muyao/wanzaihyperos/data/jw/JwTimetableRepository.kt")
        assertTrue("找不到 ${file.absolutePath}", file.isFile)
        val text = file.readText()
        assertTrue(
            "JwTimetableRepository 里的 `result.fold(` 前面没有 `_state.value =` ⇒ " +
                "爬到的课表永远发不出去（界面上就是一句说不完的「正在取」）",
            Regex("""_state\.value\s*=\s*result\.fold\(""").containsMatchIn(text),
        )
        // `isCrawling` 是界面上那句「正在取教务课表」的唯一正面证据，不许被删掉。
        assertTrue(
            "isCrawling 必须在接口上：首页与外观预览靠它判 WAIT，不拿 FirstLoad 猜",
            "val isCrawling" in text,
        )
    }
}
