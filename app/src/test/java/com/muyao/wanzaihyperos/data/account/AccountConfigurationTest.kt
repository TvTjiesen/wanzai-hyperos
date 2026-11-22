package com.muyao.wanzaihyperos.data.account

import com.muyao.wanzaihyperos.session.Cjh52Session
import com.muyao.wanzaihyperos.session.FailingSecureStore
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.JwSession
import com.muyao.wanzaihyperos.session.PortalSession
import com.muyao.wanzaihyperos.session.SztySession
import com.muyao.wanzaihyperos.session.TronclassSession
import com.muyao.wanzaihyperos.session.ZhgySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountConfigurationTest {
    private fun source(id: String, status: AccountStatus) = AccountSource(
        id, id, "合成来源", status, null, null, "查看入口", AccountTarget.AppsTab,
    )

    @Test fun configuredCountsCredentialsAndObtainedCodeButNotUnobservableWebSession() {
        val states = listOf(
            AccountStatus.LoggedIn, AccountStatus.LoggedIn, AccountStatus.LoggedIn,
            AccountStatus.ObtainedNotActivated, AccountStatus.LoggedIn,
            AccountStatus.Unknown, AccountStatus.InWebOnly,
        )
        val summary = accountConfigurationSummary(states.mapIndexed { i, status -> source("$i", status) })
        assertEquals(7, summary.sourceCount)
        assertEquals(5, summary.configuredCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(1, summary.webOnlyCount)
        assertFalse(summary.reading)
        assertEquals("7 来源 · 5 项已确认配置 · 1 项未知", summary.countText())
        assertTrue(summary.boundaryText().contains("配置不等于在线"))
        assertTrue(summary.boundaryText().contains("不向学校验证"))
        assertTrue(summary.boundaryText().contains("智云在网页内确认，不计入已配置"))
    }

    @Test fun loadingDoesNotInventZeroAndKnownEmptyDoesNotCountWebOnly() {
        val loading = accountConfigurationSummary(emptyList())
        assertTrue(loading.reading)
        assertEquals("7 来源 · 配置读取中", loading.countText())
        assertFalse(loading.countText().contains("0 项"))
        val known = accountConfigurationSummary(
            (0..5).map { source("$it", AccountStatus.LoggedOut) } + source("zy", AccountStatus.InWebOnly),
        )
        assertEquals("7 来源 · 0 项已确认配置", known.countText())
        assertFalse(known.reading)
    }

    @Test fun mixedUnknownRemainsSeparateWithoutHidingConfirmedConfiguration() {
        val summary = accountConfigurationSummary(listOf(
            source("known", AccountStatus.LoggedIn), source("failed", AccountStatus.Unknown),
            source("empty", AccountStatus.LoggedOut), source("zy", AccountStatus.InWebOnly),
        ))
        assertEquals(1, summary.configuredCount)
        assertEquals(1, summary.unknownCount)
        assertTrue(summary.countText().contains("1 项已确认配置 · 1 项未知"))
        assertTrue(summary.boundaryText().contains("未知单独列出"))
    }

    @Test fun realSnapshotUsesOnlySyntheticLocalStoresAndKeepsStoredTime() {
        val storedTime = 1760000000000L
        val snapshots = snapshotAccountSources(
            PortalSession(InMemorySecureStore()), TronclassSession(InMemorySecureStore()),
            JwSession(FailingSecureStore()),
            Cjh52Session(InMemorySecureStore(mapOf(
                Cjh52Session.KEY_TOKEN to "0f0f0f0f-1111-2222-3333-444444444444",
                Cjh52Session.KEY_OBTAINED_AT to storedTime.toString(),
            ))),
            ZhgySession(InMemorySecureStore()), SztySession(InMemorySecureStore()),
        )
        assertEquals(listOf("portal", "tronclass", "jw", "cjh52", "zhgy", "szty", "zy"), snapshots.map { it.id })
        assertEquals(AccountStatus.Unknown, snapshots.first { it.id == "jw" }.status)
        assertEquals(storedTime, snapshots.first { it.id == "cjh52" }.credentialAtMillis)
        assertEquals(null, snapshots.first { it.id == "zy" }.credentialAtMillis)
        val summary = accountConfigurationSummary(snapshots)
        assertEquals(1, summary.configuredCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(1, summary.webOnlyCount)
    }
}
