package com.muyao.wanzaihyperos.ui.design.ai

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.todo.TodoBoard
import com.muyao.wanzaihyperos.data.todo.TodoBoardBuilder
import com.muyao.wanzaihyperos.data.todo.SourceResult
import com.muyao.wanzaihyperos.data.todo.TodoSource
import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.*
import org.junit.Test

class HomeTaskFactsTest {
    private fun state(vararg results: SourceResult): ListState<TodoBoard> =
        ListState.Content(listOf(TodoBoardBuilder.build(results.toList())))

    @Test fun absentAndFailedSourcesNeverBecomeConfirmedEmpty() {
        assertEquals(HomeTaskFacts.Status.Unknown, HomeTaskFacts.ofTodos(ListState.FirstLoad).status)
        assertEquals(HomeTaskFacts.Status.Failed, HomeTaskFacts.ofTodos(ListState.SessionExpired).status)
        assertEquals(HomeTaskFacts.Status.Failed,
            HomeTaskFacts.ofTodos(ListState.Failed(HzcuException.Transport("offline"))).status)
        assertEquals(HomeTaskFacts.Status.Unknown,
            HomeTaskFacts.ofTodos(state(SourceResult(TodoSource.PORTAL, emptyList(), total = 0))).status)
    }

    @Test fun emptyRequiresBothSourcesWithNoMissingPage() {
        val sources = TodoSource.entries.map { SourceResult(it, emptyList(), total = 0) }
        assertEquals(HomeTaskFacts.Status.Empty, HomeTaskFacts.ofTodos(state(*sources.toTypedArray())).status)
        val paged = sources.map { if (it.source == TodoSource.PORTAL) it.copy(hasMore = true) else it }
        assertEquals(HomeTaskFacts.Status.Unknown, HomeTaskFacts.ofTodos(state(*paged.toTypedArray())).status)
        val failed = sources.map { if (it.source == TodoSource.PORTAL) it.copy(items = null, error = "offline") else it }
        assertEquals(HomeTaskFacts.Status.Failed, HomeTaskFacts.ofTodos(state(*failed.toTypedArray())).status)
    }

    @Test fun staleEmptyIsNotACompletionPromise() {
        assertEquals(HomeTaskFacts.Status.Unknown,
            HomeTaskFacts.ofTodos(ListState.Empty(ListMeta(stale = true))).status)
        assertEquals(HomeTaskFacts.Status.Failed,
            HomeTaskFacts.ofRollcalls(ListState.Empty(ListMeta(lastRefreshError = HzcuException.Transport("offline")))).status)
        assertEquals(HomeTaskFacts.Status.Empty, HomeTaskFacts.ofRollcalls(ListState.Empty()).status)
    }

    @Test fun taskFactsAreSpokenButNeverOverrideActiveClassOrCamera() {
        assertEquals("待办还未确认，请查看待办",
            AiBallStates.homeFactText(AiBallState.Loading, HomeTaskFacts.Unknown, HomeTaskFacts.Empty))
        assertNull(AiBallStates.homeFactText(AiBallState.Busy, HomeTaskFacts.Unknown, HomeTaskFacts.Unknown))
        assertNull(AiBallStates.homeFactText(AiBallState.Waiting, HomeTaskFacts.Unknown, HomeTaskFacts.Unknown))
        assertNull(AiBallStates.homeFactText(AiBallState.Done, HomeTaskFacts.Empty, HomeTaskFacts.Empty))
    }
}
