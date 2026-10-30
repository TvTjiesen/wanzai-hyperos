package com.muyao.wanzaihyperos.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.muyao.wanzaihyperos.MainActivity

/**
 * 闹钟响了 → 弹一条通知。
 *
 * 内容全部来自 Intent 里带的那几个字符串（排闹钟时就抄好了），这里**不查课表**：
 * 到点时可能是冷进程、可能没网，查不到就什么都弹不出来。
 */
class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        if (name.isBlank()) return
        ClassReminderNotifier.show(
            context = context,
            name = name,
            start = intent.getStringExtra(EXTRA_START).orEmpty(),
            period = intent.getStringExtra(EXTRA_PERIOD).orEmpty(),
            room = intent.getStringExtra(EXTRA_ROOM).orEmpty(),
        )
    }

    companion object {
        const val ACTION_FIRE = "com.muyao.wanzaihyperos.action.CLASS_REMINDER"
        const val EXTRA_NAME = "course_name"
        const val EXTRA_ROOM = "course_room"
        const val EXTRA_START = "course_start"
        const val EXTRA_PERIOD = "course_period"
    }
}

/** 开机 / 装包后把闹钟挂回去（AlarmManager 的闹钟重启就没了）。 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ReminderScheduler.onBoot(context)
    }
}

/** 通知长什么样。文案规矩同 strings.xml：陈述句、不指责、未知的那一段就不写。 */
internal object ClassReminderNotifier {
    private const val CHANNEL_ID = "class_reminder"

    fun show(context: Context, name: String, start: String, period: String, room: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(manager)

        val body = buildString {
            if (start.isNotBlank()) append(start).append(" 上课")
            if (period.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(period)
            }
            if (room.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(room)
            }
        }

        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.muyao.wanzaihyperos.R.drawable.ic_board_clock)
            .setContentTitle(name)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        runCatching { manager.notify(name.hashCode(), notification) }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "上课前提醒你哪门课、几点、哪间教室"
        }
        manager.createNotificationChannel(channel)
    }
}
