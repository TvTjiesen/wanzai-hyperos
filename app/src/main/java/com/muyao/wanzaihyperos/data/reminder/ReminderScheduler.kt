package com.muyao.wanzaihyperos.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 上课提醒的排程：算好的清单 → 一串系统闹钟。
 *
 * 三条设计取舍：
 *  1. **只用 AlarmManager，不引 WorkManager**。WorkManager 的最小粒度是分钟级且会被
 *     打批，「提前 10 分钟」这种要求会被拖到十几分钟后，等于没用。
 *  2. 最多排 MAX_ALARMS 条。一条提醒一个 PendingIntent 是 Android 的老规矩，
 *     排几十条既没必要（清单本来就只往后看七天）也难取消干净。
 *  3. 排之前**先全取消**。课表一刷新，之前那批就作废了；不取消会留下
 *     「已经改了教室、通知还写旧教室」这种鬼故事。
 */
internal object ReminderScheduler {
    /** 最多挂这么多条闹钟。七天里最多十几节课，16 条够用。 */
    const val MAX_ALARMS = 16

    fun sync(context: Context, reminders: List<ClassReminder>) {
        cancelAll(context)
        ReminderStore.save(context, reminders)
        arm(context, ReminderStore.load(context))
    }

    /**
     * 系统现在愿不愿意给精确闹钟（Android 12 起 `SCHEDULE_EXACT_ALARM` 是可被用户关掉的）。
     * 关掉时排出来的是窗口闹钟，可能晚一小时 —— 界面要**如实**说，不能只写「已排 N 条」。
     */
    fun canBeExact(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** 开机 / 装包后照着磁盘上的清单把闹钟挂回去。 */
    fun onBoot(context: Context) {
        arm(context, ReminderStore.load(context))
    }

    fun clear(context: Context) {
        try { cancelAll(context) } finally { ReminderStore.clear(context) }
    }

    private fun arm(context: Context, reminders: List<ClassReminder>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        reminders.sortedBy { it.atMillis }
            .filter { it.atMillis > now }
            .take(MAX_ALARMS)
            .forEachIndexed { index, reminder ->
                val pending = fireIntent(context, index, reminder)
                // → 2026-10-30 真机实测（ReminderLabActivity 一次挂三种写法后看 dumpsys alarm）：
                //   setExact                     → window=+30m45s
                //   setExactAndAllowWhileIdle    → window=+31m29s
                //   setAlarmClock                → **window=0**
                // 这台 ROM 把前两种都塞进「半小时到一小时内随便什么时候响」的窗口，
                // 提前 10 分钟提醒会变成提前 -20 分钟（响在下课后）。只有 setAlarmClock
                // 不受窗口策略管。代价是系统会把它当成「下一个闹钟」画一个闹钟图标 ——
                // 但课要按时提醒，这个代价必须付。
                try {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.atMillis, null), pending)
                } catch (denied: SecurityException) {
                    // 老系统 / 别的 ROM 上 setAlarmClock 不可用时，退回最接近的写法。
                    runCatching {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, pending)
                    }.onFailure {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, pending)
                    }
                }
            }
    }

    private fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (index in 0 until MAX_ALARMS) {
            val pending = PendingIntent.getBroadcast(
                context,
                index,
                fireIntentBody(context),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                am.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun fireIntentBody(context: Context): Intent =
        Intent(context, ClassReminderReceiver::class.java)
            .setAction(ClassReminderReceiver.ACTION_FIRE)

    private fun fireIntent(context: Context, index: Int, reminder: ClassReminder): PendingIntent {
        val intent = fireIntentBody(context)
            .putExtra(ClassReminderReceiver.EXTRA_NAME, reminder.courseName)
            .putExtra(ClassReminderReceiver.EXTRA_ROOM, reminder.room)
            .putExtra(ClassReminderReceiver.EXTRA_START, reminder.startLabel)
            .putExtra(ClassReminderReceiver.EXTRA_PERIOD, reminder.periodLabel)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, index, intent, flags)
    }
}
