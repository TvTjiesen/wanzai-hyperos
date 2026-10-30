package com.muyao.wanzaihyperos.dev

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind
import com.muyao.wanzaihyperos.data.tronclass.RollcallQrCode
import kotlinx.coroutines.launch
import java.io.File

/**
 * 只屬於 debug 構建的簽到聯調台。
 *
 * 為什麼要它：真機上那張碼帶 0x10 控制字符，`adb shell input text` 打不進去
 * （InputShellCommand 會 NPE），而掃碼必須本人舉著手機對著老師的屏幕。
 * 有了這個入口，把碼原文寫進 `files/lab_code.txt` 就能在這裡原樣走一次
 * `answerRollcall`，把**服務端原話**落到 `files/rollcall_lab.txt`。
 *
 * 只讀一個本地文件、只發一次請求、不寫憑據、不進 release。
 */
class RollcallLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            textSize = 15f
            setPadding(40, 120, 40, 40)
            text = "读取中…"
        }
        setContentView(view)

        val app = AppGraph.get(applicationContext)
        val codeFile = File(filesDir, "lab_code.txt")
        val outFile = File(filesDir, "rollcall_lab.txt")

        lifecycleScope.launch {
            val code = runCatching { codeFile.readText().trim() }.getOrNull()
            if (code.isNullOrBlank()) {
                view.text = "files/lab_code.txt 不存在或为空"
                return@launch
            }
            val parsed = RollcallQrCode.parse(code)
            val id = parsed?.rollcallId
            if (id == null) {
                view.text = "码里没有签到编号，解不出来：$parsed"
                return@launch
            }
            val result = runCatching {
                app.tronclass.answerRollcall(id, RollcallAnswerKind.QR, code)
            }
            val body = runCatching { File(filesDir, "tron_raw_answer_qr_resp.txt").readText() }
                .getOrDefault("(没有响应落盘)")
            val report = buildString {
                appendLine("code = $code")
                appendLine("parsed = $parsed")
                appendLine("rollcallId = $id")
                appendLine("result.ok = ${result.getOrNull()?.ok}")
                appendLine("result.httpStatus = ${result.getOrNull()?.httpStatus}")
                appendLine("result.message = ${result.getOrNull()?.message}")
                appendLine("exception = ${result.exceptionOrNull()}")
                appendLine("--- resp ---")
                appendLine(body)
            }
            outFile.writeText(report)
            view.text = report
        }
    }
}
