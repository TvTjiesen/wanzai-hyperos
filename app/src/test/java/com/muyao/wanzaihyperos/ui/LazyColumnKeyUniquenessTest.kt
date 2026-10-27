package com.muyao.wanzaihyperos.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 同一个 `LazyColumn` 里不许有两个相同的 `item(key = "…")`。
 *
 * ## 为什么用扫源码的方式测（而不是跑 UI）
 *
 * 2026-10-20 我把「减弱玻璃透明度」那一行加进设置页时，另起了一个
 * `item(key = "appearance-label")`，而那一页**已经有一个同名的** ⇒
 * 运行时 `IllegalArgumentException: Key "appearance-label" was already used`，
 * **设置页一点就崩**。业主原话：「设置都点不开了」。
 *
 * 这个错有三个特点，决定了它只能用这种测法：
 * - 编译期**完全不报**（key 是字符串字面量，不是类型）；
 * - 只在**滚到那一项**时才炸，不是启动就炸；
 * - 崩溃信息里带着 key，但**没有文件行号**，人得先复现再翻代码。
 * 而它 100% 可以从源码静态判出来 —— 一个正则就够，成本比"下次再崩一遍"低得多。
 *
 * ⚠️ 判据是"**同一个文件内**不许重名"。跨文件重名是安全的（每页各自一个 LazyColumn），
 *   所以这里按文件分组，不按全工程分组，否则会误报。
 */
class LazyColumnKeyUniquenessTest {

    @Test
    fun `每个界面文件里的 item key 都不重复`() {
        val srcRoot = File("src/main/java")
        assertTrue("找不到源码目录：$srcRoot.absolutePath", srcRoot.isDirectory)

        val offenders = mutableListOf<String>()
        srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val keys = Regex("""item\(\s*key\s*=\s*"([^"]+)"""")
                .findAll(file.readText())
                .map { it.groupValues[1] }
                .toList()
            val dupes = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (dupes.isNotEmpty()) {
                offenders += "${file.path} → ${dupes.joinToString()}"
            }
        }
        assertTrue(
            "这些文件里有重复的 item key，运行时会在滚到那一项时崩：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
