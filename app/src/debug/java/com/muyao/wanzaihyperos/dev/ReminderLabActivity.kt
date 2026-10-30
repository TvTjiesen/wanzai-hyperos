package com.muyao.wanzaihyperos.dev

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.muyao.wanzaihyperos.data.reminder.ClassReminderReceiver
import java.io.File

/**
 * 闹钟口径联调台（仅测试包）。
 *
 * 为什么要有它：2026-10-30 真机实测发现 setExactAndAllowWhileIdle 排出来的闹钟
 * 在 dumpsys alarm 里带 window=+1h0m0s0ms（「一小时内随便什么时候响」），
 * 而系统时钟 app 的闹钟是 window=0。到底是 ROM 对所有 allow-while-idle 这样，
 * 还是只对我们这个包这样，必须在这台机器上量出来 —— 猜不出来。
 *
 * 它一次挂三种写法（setExact / setExactAndAllowWhileIdle / setAlarmClock），
 * 之后用 dumpsys alarm 逐条看 window 与 flags，再决定生产代码用哪一种。
 */
class ReminderLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = StringBuilder()
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val base = System.currentTimeMillis() + 40 * 60 * 1000L
        fun pending(tag: String): PendingIntent = PendingIntent.getBroadcast(
            this,
            9000 + tag.hashCode().mod(100),
            Intent(this, ClassReminderReceiver::class.java)
                .setAction(ClassReminderReceiver.ACTION_FIRE)
                .putExtra(ClassReminderReceiver.EXTRA_NAME, "lab-$tag"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        out.appendLine("canBeExact=" + runCatching { am.canScheduleExactAlarms() }.getOrNull())
        out.appendLine("exactAlarmAppop=" + runCatching {
            android.app.AppOpsManager::class.java
        }.getOrNull())
        runCatching { am.setExact(AlarmManager.RTC_WAKEUP, base + 1 * 60_000L, pending("exact")) }
            .onSuccess { out.appendLine("setExact ok") }
            .onFailure { out.appendLine("setExact failed: " + it) }
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, base + 2 * 60_000L, pending("exactidle"))
        }.onSuccess { out.appendLine("setExactAndAllowWhileIdle ok") }
            .onFailure { out.appendLine("setExactAndAllowWhileIdle failed: " + it) }
        runCatching {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(base + 3 * 60_000L, null),
                pending("clock"),
            )
        }.onSuccess { out.appendLine("setAlarmClock ok") }
            .onFailure { out.appendLine("setAlarmClock failed: " + it) }
        File(filesDir, "reminder_lab.txt").writeText(out.toString())
        finish()
    }
}
