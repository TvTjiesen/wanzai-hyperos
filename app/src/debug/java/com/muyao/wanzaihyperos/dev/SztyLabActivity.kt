package com.muyao.wanzaihyperos.dev

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.szty.SztySnapshot
import kotlinx.coroutines.launch
import java.io.File

/**
 * 只屬於 debug 構建的數智體育聯調台。
 *
 * 為什麼要它：數智體育的憑據是 54 個字符的 token，含 `+` `/` `=` `_`，
 * `adb shell input text` 打進去會被中文輸入法吃掉（實測 `A_B` 變成 `——`），
 * 而 `adb shell cmd clipboard` 在這台機器上根本沒有實現。
 * 於是真機端到端驗證只差「把這串字送進去」這一步 —— 這個入口把它變成一次文件讀取。
 *
 * 用法：把 token 與 userId 分兩行寫進 `files/lab_szty.txt`，啟動本頁，
 * 它會存入會話（與手輸走同一個 [com.muyao.wanzaihyperos.session.SztySession.save]）
 * 再拉一次，把結果落到 `files/szty_lab.txt`。
 *
 * 只讀一個本地文件、只發一輪只讀查詢、不進 release。
 */
class SztyLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            textSize = 14f
            setPadding(32, 110, 32, 32)
            text = "读取中…"
        }
        setContentView(view)

        val app = AppGraph.get(applicationContext)
        val inFile = File(filesDir, "lab_szty.txt")
        val outFile = File(filesDir, "szty_lab.txt")

        lifecycleScope.launch {
            val lines = runCatching {
                inFile.readText().lines().map { it.trim() }.filter { it.isNotEmpty() }
            }.getOrNull()
            if (lines == null || lines.size < 2) {
                view.text = "files/lab_szty.txt 需要两行：第一行 token，第二行 userId"
                return@launch
            }
            val token = lines[0]
            val userId = lines[1].toIntOrNull()
            if (userId == null || userId <= 0) {
                view.text = "第二行不是正整数 userId：${lines[1]}"
                return@launch
            }

            app.sztySession.save(token, userId, System.currentTimeMillis())
            val error = runCatching { app.szty.refresh() }.exceptionOrNull()

            val state = app.szty.state.value
            val report = buildString {
                appendLine("tokenLen = ${token.length}")
                appendLine("userId = $userId")
                appendLine("refreshError = $error")
                appendLine("state = ${state::class.simpleName}")
                when (state) {
                    is ListState.Content -> {
                        val s: SztySnapshot = state.items.first()
                        appendLine("fitnessYears = ${s.fitness.size}")
                        s.fitness.forEach { y ->
                            appendLine("  · ${y.year} grade=${y.gradeText} score=${y.score} items=${y.items.size}")
                        }
                        appendLine("run = ${s.run}")
                        appendLine("peClass = ${s.peClass}")
                        appendLine("attendance = ${s.attendance.size} 条")
                        appendLine("attendanceSummary = ${s.attendanceSummary}")
                        appendLine("notes = ${s.notes}")
                    }
                    is ListState.Failed -> appendLine("error = ${state.error}")
                    is ListState.Empty -> appendLine("Empty（一块都没取到）")
                    is ListState.SessionExpired -> appendLine("SessionExpired（token 失效）")
                    else -> Unit
                }
            }
            outFile.writeText(report)
            view.text = report
        }
    }
}
