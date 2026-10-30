
package com.muyao.wanzaihyperos.ui

import com.muyao.wanzaihyperos.ui.design.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #41「tnum 令牌用到会变的数字上」的守卫。
 *
 * 要证明的事只有一件：**同一屏里会变的计数（节数、门课、条数、场次、周数、项数）必须走等宽数字**。
 * 数字一变宽，整行字就往左右跳一格；DesignTokens.tabular = "tnum" 给了令牌，
 * 但令牌本身不会自己生效，必须每个会变的数字都真的把它挂上去。
 *
 * 判据从被验对象里读：扫描 src/main + src/debug 的每个 Text( 调用体，
 * 用「插值表达式 + 量词」认出计数短语，再要求同一个调用体里出现 fontFeatureSettings = Type.tabular。
 * 扫描器自己也要被验：见 扫描器必须真的扫到货 —— 否则扫到 0 处也会「全过」。
 */
class TnumOnChangingNumbersTest {

    /** 量词表：出现在插值表达式后面、能把那个数字变成「会变的计数」的汉字量词。 */
    private val units = listOf(
        "个网页服务", "个上课时段", "个分组", "个应用", "个图标", "个时段", "个课程",
        "门课", "节", "条", "场", "次", "周", "项",
    )

    private val token = "fontFeatureSettings = Type.tabular"

    /** 一次 Text( 调用：起始行号 + 括号内的整段源码。 */
    private data class Call(val line: Int, val body: String)

    /** 括号配平地切出 Text( 的调用体；跳过 MyText( / obj.Text( 这类更长的标识符。 */
    private fun calls(text: String): List<Call> {
        val out = ArrayList<Call>()
        var i = 0
        while (true) {
            val j = text.indexOf("Text(", i)
            if (j < 0) return out
            val prev = if (j > 0) text[j - 1] else '\n'
            if (prev.isLetterOrDigit() || prev == '.' || prev == '_' || prev == '$') {
                i = j + 5
                continue
            }
            var p = j + 5
            var depth = 1
            var inString = false
            while (p < text.length) {
                val c = text[p]
                if (inString) {
                    if (c == '\\') { p += 2; continue }
                    if (c == '"') inString = false
                } else {
                    when (c) {
                        '"' -> inString = true
                        '(' -> depth += 1
                        ')' -> {
                            depth -= 1
                            if (depth == 0) break
                        }
                    }
                }
                p += 1
            }
            out.add(Call(text.substring(0, j).count { it == '\n' } + 1, text.substring(j + 5, p)))
            i = p + 1
        }
    }

    /** 计数短语：$xxx / ${...} 紧跟一个量词。 */
    private fun countingPhrase(body: String): String? {
        for (u in units) {
            val re = Regex("""(\$\{[^}\n]*\}|\$[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*""" + Regex.escape(u))
            val m = re.find(body)
            if (m != null) return m.value
        }
        return null
    }

    private val scanned by lazy { SourceScan.sources() }

    @Test
    fun 会变的计数必须走等宽数字() {
        val bad = ArrayList<String>()
        for ((path, code) in scanned) {
            if (!code.contains("Text(")) continue
            for (c in calls(code)) {
                val phrase = countingPhrase(c.body) ?: continue
                if (c.body.contains(token)) continue
                bad.add(path + ":" + c.line + " 计数短语 " + phrase + " 所在的 Text 没挂 " + token)
            }
        }
        assertTrue(
            "这些会变的数字没走等宽数字，数字变宽时整行会左右跳（#41）：\n" + bad.joinToString("\n"),
            bad.isEmpty(),
        )
    }

    @Test
    fun 扫描器必须真的扫到货() {
        var counted = 0
        for ((_, code) in scanned) {
            for (c in calls(code)) if (countingPhrase(c.body) != null) counted += 1
        }
        // 下限 = 现在的实测条数。跌破说明扫描器或量词表被改坏了，
        // 那时「上面那条全过」是空过，不是真的都合规。
        // 首轮 22 处；2026-10-30 删掉死页 AppsPage.kt（里面 3 处「共 $appCount 个应用」之类）
        // 之后是 19 处 —— 下限跟着实测降，不是放宽判据：扫描器一旦坏掉会掉到个位数。
        assertTrue("扫描器只认出 " + counted + " 处计数短语，低于 19 ⇒ 这条守卫在空转", counted >= 19)
    }

    @Test
    fun 令牌本身还是_tnum() {
        assertEquals("tnum", Type.tabular)
    }
}
