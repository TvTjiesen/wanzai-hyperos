package com.muyao.wanzaihyperos.data.reminder

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.jw.JwTimetableResult
import com.muyao.wanzaihyperos.data.schedule.JwVerdict
import com.muyao.wanzaihyperos.data.schedule.jwVerdictOf
import com.muyao.wanzaihyperos.data.schedule.pickSlots
import com.muyao.wanzaihyperos.data.tronclass.TronSchedule
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 决定「这次该排哪些提醒」，以及**该不该动已经排好的那些**。
 *
 * 这一层存在的理由只有一个：课表有两个来源（教务 / 学在城院），而它们在「还没取到」和
 * 「确实没课」这两件事上长得一模一样 —— 都是空列表。
 *
 * **返回 null = 不知道（调用方原样保留已排的闹钟）；返回空列表 = 确实没课（可以全撤）。**
 */
internal object ReminderBrain {

    fun plan(
        jwState: ListState<JwTimetableResult>,
        jwCrawling: Boolean,
        tronState: ListState<TronSchedule>,
        times: List<Pair<String, String>>,
        now: LocalDateTime,
        leadMinutes: Int,
        weekOf: (LocalDate) -> Int?,
    ): List<ClassReminder>? {
        val jwResult = (jwState as? ListState.Content)?.items?.firstOrNull()
        val jwSlots = jwResult?.slots.orEmpty()
        val tronResult = (tronState as? ListState.Content)?.items?.firstOrNull()
        val tronSlots = tronResult?.slots.orEmpty()

        val verdict = jwVerdictOf(
            state = jwState,
            crawling = jwCrawling,
            hasSlots = jwSlots.isNotEmpty(),
        )
        // 还在路上：既不是有课也不是没课，什么都不改。
        if (verdict == JwVerdict.IN_FLIGHT || verdict == JwVerdict.NOT_STARTED) return null

        val pick = pickSlots(jwSlots = jwSlots, jwVerdict = verdict, tronSlots = tronSlots)
        // 两边都没有格子：只有「教务确实取完且是空的」才叫知道没课；
        // 否则（学在城院也还没结果）就是不知道，不动已排的闹钟。
        if (pick.slots.isEmpty() && verdict != JwVerdict.SETTLED_EMPTY) return null

        return ClassReminderPlan.plan(
            slots = pick.slots,
            times = times,
            now = now,
            leadMinutes = leadMinutes,
            weekOf = weekOf,
        )
    }
}
