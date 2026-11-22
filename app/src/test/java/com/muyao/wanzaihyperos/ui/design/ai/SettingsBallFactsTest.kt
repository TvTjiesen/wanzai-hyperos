package com.muyao.wanzaihyperos.ui.design.ai

import com.muyao.wanzaihyperos.data.account.AccountSource
import com.muyao.wanzaihyperos.data.account.AccountStatus
import com.muyao.wanzaihyperos.data.account.AccountTarget
import com.muyao.wanzaihyperos.data.account.accountConfigurationSummary
import com.muyao.wanzaihyperos.ui.main.pages.SettingsBallSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBallFactsTest {
    private fun source(status: AccountStatus) = AccountSource(
        id = "synthetic", name = "Synthetic", provides = "Local configuration", status = status,
        credentialAtMillis = null, timeLabel = null, whereToLogin = "", target = AccountTarget.None,
    )

    private fun page(): String = sequenceOf(
        File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/SettingsSectionPage.kt"),
        File("app/src/main/java/com/muyao/wanzaihyperos/ui/main/pages/SettingsSectionPage.kt"),
    ).first { it.isFile }.readText(Charsets.UTF_8)

    @Test fun readySnapshotRequiresActualReadAndKeepsConfigurationDistinctFromOnline() {
        val initial = SettingsBallSnapshot()
        assertEquals(AiBallWorkPhase.Unknown, initial.phase)
        assertEquals(0L, initial.resultRevision)
        val reading = initial.beginRead()
        assertEquals(AiBallWorkPhase.Reading, reading.phase)
        val result = reading.acceptRead(reading.ticket, listOf(source(AccountStatus.LoggedOut)))
        assertTrue(result.loading)
        val finished = result.finishRead(reading.ticket)
        assertEquals(AiBallWorkPhase.Ready, finished.phase)
        assertEquals(1L, finished.resultRevision)
        assertEquals(AiBallState.Idle, AiBallStates.ofAccounts(finished.sources))
        val summary = accountConfigurationSummary(finished.sources)
        assertEquals(0, summary.configuredCount)
        assertTrue(summary.boundaryText().contains("配置不等于在线"))
        assertEquals(finished, finished.acceptRead(reading.ticket, listOf(source(AccountStatus.LoggedIn))))
        assertEquals(1L, finished.copy().resultRevision)
    }

    @Test fun emptyOrUnknownReadFinishesWithoutSuccessOrFakeOnline() {
        for (sources in listOf(emptyList(), listOf(source(AccountStatus.Unknown)))) {
            val reading = SettingsBallSnapshot().beginRead()
            val finished = reading.acceptRead(reading.ticket, sources).finishRead(reading.ticket)
            assertFalse(finished.loading)
            assertEquals(AiBallWorkPhase.Unknown, finished.phase)
            assertEquals(0L, finished.resultRevision)
            assertEquals(AiBallState.Loading, AiBallStates.ofAccounts(finished.sources))
            assertEquals(0, accountConfigurationSummary(finished.sources).configuredCount)
        }
    }

    @Test fun cancelAndFailureDoNotInventSuccessOrPinLoading() {
        val reading = SettingsBallSnapshot().beginRead()
        val cancelled = reading.finishRead(reading.ticket)
        assertFalse(cancelled.loading)
        assertEquals(AiBallWorkPhase.Unknown, cancelled.phase)
        assertEquals(0L, cancelled.resultRevision)
        assertEquals(0L, cancelled.errorRevision)
        val failed = reading.failRead(reading.ticket).finishRead(reading.ticket)
        assertFalse(failed.loading)
        assertEquals(AiBallWorkPhase.Error, failed.phase)
        assertEquals(0L, failed.resultRevision)
        assertEquals(1L, failed.errorRevision)
        val retry = failed.beginRead()
        assertEquals(AiBallWorkPhase.Reading, retry.phase)
        assertEquals(1L, retry.errorRevision)
    }

    @Test fun staleResponseAndOldFinallyCannotTouchNewPageRead() {
        val first = SettingsBallSnapshot().beginRead()
        val second = first.beginRead()
        assertEquals(second, second.acceptRead(first.ticket, listOf(source(AccountStatus.LoggedIn))))
        assertEquals(second, second.failRead(first.ticket))
        assertEquals(second, second.finishRead(first.ticket))
        assertTrue(second.loading)
        val finished = second.acceptRead(second.ticket, listOf(source(AccountStatus.InWebOnly))).finishRead(second.ticket)
        assertEquals(AiBallWorkPhase.Ready, finished.phase)
        assertEquals(1L, finished.resultRevision)
        assertEquals(0, accountConfigurationSummary(finished.sources).configuredCount)
    }

    @Test fun revisionsAdvanceOnlyForNewConfirmedResultsOrActualFailures() {
        var facts = SettingsBallSnapshot()
        repeat(3) { index ->
            val reading = facts.beginRead()
            facts = reading.acceptRead(reading.ticket, listOf(source(AccountStatus.LoggedOut))).finishRead(reading.ticket)
            assertEquals(index + 1L, facts.resultRevision)
            assertEquals(0L, facts.errorRevision)
        }
        val unknown = facts.beginRead()
        facts = unknown.acceptRead(unknown.ticket, listOf(source(AccountStatus.Unknown))).finishRead(unknown.ticket)
        assertEquals(3L, facts.resultRevision)
        assertEquals(AiBallWorkPhase.Unknown, facts.phase)
    }

    @Test fun settingsUsesLocalIoFactsAndOptInWithoutChangingSizeOrOriginalSummary() {
        val text = page()
        assertFalse(text.contains("graph.inFlight"))
        assertFalse(text.contains("collectAsVisibleStateWithLifecycle"))
        assertTrue(text.contains("LaunchedEffect(graph, uiState.loggedIn, refreshKey, pageActive, entryOwner)"))
        val effect = text.substringAfter("LaunchedEffect(graph, uiState.loggedIn, refreshKey, pageActive, entryOwner)").substringBefore("val pilot =")
        assertTrue(effect.indexOf("if (!pageActive)") < effect.indexOf("withContext(Dispatchers.IO)"))
        assertTrue(effect.indexOf("ballFacts.beginRead()") < effect.indexOf("withContext(Dispatchers.IO)"))
        assertTrue(effect.indexOf("snapshotAccountSources(") < effect.indexOf("ballFacts.acceptRead(ticket, sources)"))
        assertTrue(Regex("catch \\(cancelled: CancellationException\\)\\s*\\{\\s*throw cancelled").containsMatchIn(effect))
        assertTrue(Regex("finally\\s*\\{\\s*ballFacts = ballFacts\\.finishRead\\(ticket\\)").containsMatchIn(effect))
        assertTrue(text.contains("AiBallStates.ofAccounts(accountSources, fetching = ballFacts.loading)"))
        assertEquals(1, Regex("AiBall\\(").findAll(text).count())
        val entry = text.substringAfter("item(key = \"account-entry\")").substringBefore("item(key = \"personal-preferences\")")
        assertTrue(entry.contains("diameter = 64.dp, hitSlop = 4.dp"))
        assertTrue(entry.contains("presentation = AiBallPresentation.Home, work = ballWork"))
        assertTrue(entry.contains("announceArrival = false"))
        assertTrue(entry.contains("configuration = com.muyao.wanzaihyperos.data.account.accountConfigurationSummary(accountSources)"))
        assertTrue(entry.contains("it.countText() + \"\\n\" + it.boundaryText()"))
        assertFalse(entry.contains("online"))
    }
}
