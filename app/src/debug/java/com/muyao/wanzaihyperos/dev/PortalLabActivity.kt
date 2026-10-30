package com.muyao.wanzaihyperos.dev

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.muyao.wanzaihyperos.app.AppGraph
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * 只屬於 debug 構建的门户联调台。
 *
 * 為什麼要它：消息「標已讀」的接口回了 200 success，可殺進程重開後那條又變回未讀。
 * 到底是「服務端沒記住」還是「我們查的分組/id 不是同一條」，只有把
 * 標已讀 → 再取同一個分組 這兩步放在**同一個進程、同一個 token** 下跑一次才知道。
 *
 * 指令文件 `files/lab_portal.txt`，一行一條：
 * ```
 * read <消息id> <分组名>
 * ```
 * 結果寫 `files/portal_lab.txt`。只讀本地文件、只打門戶、不寫憑據、不進 release。
 */
class PortalLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            textSize = 13f
            setPadding(40, 120, 40, 40)
            text = "读取中…"
        }
        setContentView(view)

        val app = AppGraph.get(applicationContext)
        val cmdFile = File(filesDir, "lab_portal.txt")
        val outFile = File(filesDir, "portal_lab.txt")

        lifecycleScope.launch {
            val line = runCatching { cmdFile.readText().trim() }.getOrNull()
            if (line.isNullOrBlank()) {
                view.text = "files/lab_portal.txt 不存在或为空"
                return@launch
            }
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 3 || parts[0] != "read") {
                view.text = "指令不成立：$line（应为 read <id> <分组名>）"
                return@launch
            }
            val id = parts[1]
            val group = parts.drop(2).joinToString(" ")
            val report = buildString {
                appendLine("id = $id")
                appendLine("group = $group")
                val before = rowOf(app, group, id)
                appendLine("--- 标之前 ---")
                appendLine(before)
                val marked = runCatching { app.api.markMessageRead(id) }
                appendLine("--- 标已读 ---")
                appendLine("returned = ${marked.getOrNull()}  exception = ${marked.exceptionOrNull()}")
                val after = rowOf(app, group, id)
                appendLine("--- 标之后再取同分组 ---")
                appendLine(after)
            }
            outFile.writeText(report)
            view.text = report
        }
    }

    private suspend fun rowOf(app: AppGraph, group: String, id: String): String {
        val resp = runCatching { app.api.fetchMessages(group = group, page = 1) }
            .getOrElse { return "取列表失败：${it}" }
        val root = JSONObject(resp.raw.toString())
        val rows = root.optJSONArray("rows") ?: return "响应里没有 rows：${resp.raw.toString().take(200)}"
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (row.optString("id") == id) {
                return "找到这条：isread=${row.opt("isread")} title=${row.opt("title")} 共 ${rows.length()} 行"
            }
        }
        return "这个分组第一页里没有它（共 ${rows.length()} 行）"
    }
}
