package com.muyao.wanzaihyperos.data.reminder

import android.content.Context
import java.io.File

/**
 * 已排好的提醒清单，落在 App 私有目录。
 *
 * 为什么要落盘：AlarmManager 的闹钟**重启就全没了**，而重启时进程是新的、
 * 课表也还没刷下来。开机广播只能照着这份清单把闹钟重新挂一遍，
 * 所以清单必须在磁盘上，而且要带够到点弹通知用的全部文字。
 *
 * 为什么不用 SharedPreferences：这不是「用户偏好」，是**派生数据的快照**，
 * 用户改了课表就该整份被覆盖；混进偏好文件会被「恢复默认」一起清掉或漏清。
 */
internal object ReminderStore {
    private const val FILE = "class_reminders.txt"
    private const val CELLS = 5

    fun save(context: Context, reminders: List<ClassReminder>) {
        runCatching {
            val file = File(context.filesDir, FILE)
            if (reminders.isEmpty()) {
                file.delete()
                return@runCatching
            }
            file.writeText(reminders.joinToString("\n") { line(it) })
        }
    }

    fun load(context: Context): List<ClassReminder> = runCatching {
        val file = File(context.filesDir, FILE)
        if (!file.isFile) return@runCatching emptyList()
        file.readLines().mapNotNull { parse(it) }
    }.getOrDefault(emptyList())

    fun clear(context: Context) {
        val file = File(context.filesDir, FILE)
        check(!file.exists() || file.delete()) { "Reminder cleanup was not confirmed" }
    }

    /** 制表符分列；字段里的制表符与换行先抹平，否则一行会裂成两行。 */
    private fun line(r: ClassReminder): String = listOf(
        r.atMillis.toString(),
        clean(r.courseName),
        clean(r.room),
        clean(r.startLabel),
        clean(r.periodLabel),
    ).joinToString("\t")

    private fun parse(raw: String): ClassReminder? {
        val cells = raw.split("\t")
        if (cells.size != CELLS) return null
        val at = cells[0].toLongOrNull() ?: return null
        return ClassReminder(
            atMillis = at,
            courseName = cells[1],
            room = cells[2],
            startLabel = cells[3],
            periodLabel = cells[4],
        )
    }

    private fun clean(v: String): String =
        v.replace("\t", " ").replace("\n", " ").replace("\r", " ")
}
