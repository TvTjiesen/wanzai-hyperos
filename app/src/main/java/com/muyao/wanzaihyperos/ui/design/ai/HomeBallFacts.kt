package com.muyao.wanzaihyperos.ui.design.ai

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.schedule.SlotSource

internal data class HomeBallObservation(
    val reading: Boolean,
    val failed: Boolean,
    val unknown: Boolean,
    val waiting: Boolean,
    val updates: List<Long>,
) {
    val phase: AiBallWorkPhase get() = when {
        waiting -> AiBallWorkPhase.Waiting
        reading -> AiBallWorkPhase.Reading
        failed -> AiBallWorkPhase.Error
        unknown -> AiBallWorkPhase.Unknown
        else -> AiBallWorkPhase.Ready
    }
}

internal object HomeBallFacts {
    fun observe(
        source: SlotSource,
        schedule: ListState<*>,
        jw: ListState<*>,
        todos: ListState<*>,
        rollcalls: ListState<*>,
        todoFacts: HomeTaskFacts,
        rollcallFacts: HomeTaskFacts,
        jwCrawling: Boolean,
        localRequests: Int,
        waiting: Boolean,
    ): HomeBallObservation {
        val selected = when (source) {
            SlotSource.JIAOWU, SlotSource.WAIT -> jw
            SlotSource.TRONCLASS -> schedule
            SlotSource.NONE -> null
        }
        val relevant = listOfNotNull(selected, todos, rollcalls)
        val reading = localRequests > 0 || relevant.any { it.metaOrNull?.refreshing == true || it.metaOrNull?.loadingMore == true } ||
            (source in setOf(SlotSource.JIAOWU, SlotSource.WAIT) && jwCrawling)
        val failed = relevant.any { it is ListState.Failed || it == ListState.SessionExpired || it.metaOrNull?.lastRefreshError != null } ||
            todoFacts.status == HomeTaskFacts.Status.Failed || rollcallFacts.status == HomeTaskFacts.Status.Failed ||
            (source == SlotSource.NONE && scheduleFailed(schedule) && scheduleFailed(jw))
        val unknown = selected == null || relevant.any { it == ListState.FirstLoad || it.metaOrNull?.stale == true } ||
            todoFacts.status == HomeTaskFacts.Status.Unknown || rollcallFacts.status == HomeTaskFacts.Status.Unknown
        return HomeBallObservation(reading, failed, unknown, waiting,
            listOf(selected?.metaOrNull?.updatedAtMillis ?: 0L, todos.metaOrNull?.updatedAtMillis ?: 0L, rollcalls.metaOrNull?.updatedAtMillis ?: 0L))
    }

    private fun scheduleFailed(state: ListState<*>): Boolean =
        state is ListState.Failed || state == ListState.SessionExpired
}

/** Counters are page-local events, not timestamps added together or global network activity. */
internal data class HomeBallSnapshot(
    val previous: HomeBallObservation? = null,
    val readUpdates: List<Long>? = null,
    val resultRevision: Long = 0L,
    val errorRevision: Long = 0L,
) {
    fun observe(next: HomeBallObservation): HomeBallSnapshot {
        val baseline = readUpdates ?: previous?.updates?.takeIf { next.reading }
        val finished = previous?.reading == true && !next.reading
        val changed = baseline != null && next.updates.zip(baseline).any { (now, before) -> now > before }
        return copy(
            previous = next,
            readUpdates = if (next.reading) baseline ?: next.updates else null,
            resultRevision = resultRevision + if (finished && next.phase == AiBallWorkPhase.Ready && changed) 1 else 0,
            errorRevision = errorRevision + if (next.phase == AiBallWorkPhase.Error &&
                (previous?.phase != AiBallWorkPhase.Error || finished)) 1 else 0,
        )
    }
}
