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
import com.muyao.wanzaihyperos.data.UiPrefs

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

        // ── 建议二：提醒隐私与本 App 静音时段（业主 2026-11-07 批准五项全做）──
        //
        // 隐私：关掉「显示课程与教室」时，**通知里一个字都不许出现课名/教室**。
        //   这不是"少显示一点"，是泄露面 —— 锁屏挂在桌上，旁边的人看得见。
        //   所以标题也一起换掉，而不是只把正文打码。
        // 静音：落在静音时段就**不发响**，但仍然发一条无声的摘要
        //   （用户要的是"别吵我"，不是"别提醒我"；吞掉通知会让他错过课）。
        // 广播接收器是**冷进程**：UiPrefs 那份内存状态可能是空的（进程刚被闹钟拉起来），
        // 所以先 load 一次再读偏好 —— 否则读到的全是默认值，隐私开关等于没生效。
        runCatching { UiPrefs.load(context) }
        val private = UiPrefs.reminderPrivate
        val quiet = UiPrefs.inQuietHours(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))

        val title = if (private) "即将上课" else name
        val body = if (private) {
            buildString {
                append(if (start.isNotBlank()) "$start 有一节课" else "有一节课")
                append(" · 打开「柒合院」看是哪一门")
            }
        } else {
            buildString {
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
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // 静音时段：走 SILENT 优先级 + 不带声音。渠道本身不动（系统/用户管着渠道声音，
            // 反复重建渠道去"恢复响铃"是明令禁止的做法）。
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setSilent(quiet)
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
