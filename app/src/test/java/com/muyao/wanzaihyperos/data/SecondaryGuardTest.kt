package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.SecondaryGuard
import com.muyao.wanzaihyperos.data.repo.finishRefresh
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.JwSession
import com.muyao.wanzaihyperos.session.SztySession
import org.junit.Assert.*
import org.junit.Test

class SecondaryGuardTest {
    @Test fun replacementAndClearSynchronouslyInvalidateOnlyTheirSource() {
        val epoch = AccountEpoch().apply { activate("synthetic-main") }
        val jw = JwSession(InMemorySecureStore()).apply { save("synthetic-jw", "synthetic-password", 1L) }
        val sports = SztySession(InMemorySecureStore()).apply { save("synthetic-token", 7, 1L) }
        var jwState: ListState<String> = ListState.Content(listOf("synthetic-old"))
        var sportsResets = 0
        val jwGuard = SecondaryGuard(epoch, jw) { jwState = ListState.FirstLoad }
        val sportsGuard = SecondaryGuard(epoch, sports) { sportsResets++ }
        val old = jwGuard.begin()
        val sportsTicket = sportsGuard.begin()
        val main = epoch.token()
        jw.markLogin(2L)
        assertTrue(jwGuard.isCurrent(old))
        jw.save("synthetic-next", "synthetic-password-next", 3L)
        assertTrue(jwState is ListState.FirstLoad)
        assertFalse(jwGuard.publish(old) { jwState = ListState.Content(listOf("late-old")) })
        assertTrue(sportsGuard.isCurrent(sportsTicket))
        assertEquals(0, sportsResets)
        assertEquals(main, epoch.token())
        val beforeClear = jwGuard.begin()
        jw.clear()
        assertFalse(jwGuard.isCurrent(beforeClear))
        assertEquals(main, epoch.token())
    }

    @Test fun requestStartedBetweenCommitAndNotificationIsNotInvalidatedByTheSameReplacement() {
        val session = SztySession(InMemorySecureStore()).apply { save("synthetic", 7, 1L) }
        var guard: SecondaryGuard? = null
        var restarted: Long? = null
        session.addListener { restarted = guard?.begin() }
        var resets = 0
        guard = SecondaryGuard(AccountEpoch(), session) { resets++ }
        session.save("synthetic-next", 8, 2L)
        assertEquals(1, resets)
        assertTrue(requireNotNull(guard).isCurrent(requireNotNull(restarted)))
    }

    @Test fun oldFinallyCannotClearNewRequestLoadingAndFirstLoadCancellationIsNotFailure() {
        val session = SztySession(InMemorySecureStore()).apply { save("synthetic", 7, 1L) }
        val guard = SecondaryGuard(AccountEpoch(), session) {}
        var state: ListState<String> = ListState.FirstLoad
        val first = guard.begin()
        guard.publish(first) { state = state.finishRefresh() }
        assertTrue(state is ListState.FirstLoad)
        state = ListState.Content(listOf("synthetic"))
        val old = guard.begin()
        guard.publish(old) { state = state.startRefresh() }
        val next = guard.begin()
        guard.publish(next) { state = state.startRefresh() }
        assertFalse(guard.publish(old) { state = state.finishRefresh() })
        assertTrue((state as ListState.Content).meta.refreshing)
        guard.publish(next) { state = state.finishRefresh() }
        assertFalse((state as ListState.Content).meta.refreshing)
    }
}
