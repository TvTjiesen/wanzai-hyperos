package com.muyao.wanzaihyperos.ui.design.ai

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.todo.TodoBoard

/** Empty is a confirmed result, never a fallback for an absent source. */
data class HomeTaskFacts(
    val status: Status,
    val observedCount: Int? = null,
    val updatedAtMillis: Long? = null,
) {
    enum class Status { Unknown, Failed, Empty, Items }

    companion object {
        val Unknown = HomeTaskFacts(Status.Unknown)
        val Empty = HomeTaskFacts(Status.Empty, 0)

        fun ofTodos(state: ListState<TodoBoard>): HomeTaskFacts = when (state) {
            is ListState.Content -> {
                val boards = state.items
                val groups = boards.flatMap { it.groups }
                val count = groups.sumOf { it.items.size }
                val status = when {
                    groups.any { it.failed } || state.meta.lastRefreshError != null -> Status.Failed
                    state.meta.hasRefreshProblem -> Status.Unknown
                    groups.map { it.source }.toSet() != com.muyao.wanzaihyperos.data.todo.TodoSource.entries.toSet() ||
                        groups.any { it.total == null || it.hasMore || it.total != it.items.size } -> Status.Unknown
                    count > 0 -> Status.Items
                    else -> Status.Empty
                }
                HomeTaskFacts(status, count.takeIf { it > 0 || status == Status.Empty },
                    state.meta.updatedAtMillis)
            }
            is ListState.Empty -> HomeTaskFacts(
                if (state.meta.lastRefreshError != null) Status.Failed else if (state.meta.hasRefreshProblem) Status.Unknown else Status.Empty,
                if (state.meta.hasRefreshProblem) null else 0, state.meta.updatedAtMillis)
            is ListState.Failed, ListState.SessionExpired -> HomeTaskFacts(Status.Failed)
            ListState.FirstLoad -> Unknown
        }

        fun ofRollcalls(state: ListState<*>): HomeTaskFacts = when (state) {
            is ListState.Content -> HomeTaskFacts(
                if (state.meta.lastRefreshError != null) Status.Failed else if (state.meta.hasRefreshProblem) Status.Unknown else if (state.items.isEmpty()) Status.Empty else Status.Items,
                state.items.size, state.meta.updatedAtMillis)
            is ListState.Empty -> HomeTaskFacts(
                if (state.meta.lastRefreshError != null) Status.Failed else if (state.meta.hasRefreshProblem) Status.Unknown else Status.Empty,
                if (state.meta.hasRefreshProblem) null else 0, state.meta.updatedAtMillis)
            is ListState.Failed, ListState.SessionExpired -> HomeTaskFacts(Status.Failed)
            ListState.FirstLoad -> Unknown
        }
    }
}
