package com.muyao.wanzaihyperos.ui

import java.io.File

/**
 * 源码文本扫描的小工具（只给守卫测试用）。
 *
 * 为什么要「去注释」再扫：守卫要判的是**代码里有没有这个写法**，
 * 而不是**文档里有没有提到它**。KDoc 里写「以前用的是 `Glyph.tileCorner`」是正常的取证记录，
 * 不该把守卫刷红；反过来，注释里写「`MinePage(` 除本文件外 0 命中」也不该被当成调用点。
 *
 * 去 `//` 注释时要避开**字符串里的 `//`**（URL 很常见）：
 * 用「`//` 前面引号个数是奇数 ⇒ 它在字符串里」这个判据，保守地不删那一行。
 */
object SourceScan {

    /** 扫描面：`src/main` 与 `src/debug`，路径前缀分别是 `main/` 与 `debug/`。 */
    private val ROOTS = listOf(
        "src/main/java/com/muyao/wanzaihyperos" to "main",
        "src/debug/java/com/muyao/wanzaihyperos" to "debug",
    )

    /** 返回 (相对路径, 去注释后的代码)。路径形如 `main/ui/main/pages/AppearancePage.kt`。 */
    fun sources(): List<Pair<String, String>> =
        ROOTS.flatMap { (path, label) ->
            val root = File(path)
            if (!root.isDirectory) return@flatMap emptyList()
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { file ->
                    "$label/" + file.relativeTo(root).path.replace(File.separatorChar, '/') to
                        stripComments(file.readText(Charsets.UTF_8))
                }
                .sortedBy { it.first }
                .toList()
        }

    fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        return noBlock.lines().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            if (idx < 0) {
                line
            } else {
                val before = line.substring(0, idx)
                if (before.count { it == '"' } % 2 == 1) line else line.substring(0, idx)
            }
        }
    }
}
