package com.muyao.wanzaihyperos.net

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun diagnosticsDefaultOffAndReleaseCannotOptIn() {
        val directory = File(temporary.root, "diagnostics")
        var now = 1_000_000L
        val debug = DiagnosticPolicy(directory, true, { now })
        assertFalse(debug.isEnabled())
        assertFalse(debug.record("portal", "synthetic", "synthetic-sensitive-body"))
        assertFalse(directory.exists())
        assertTrue(debug.enableFor(Long.MAX_VALUE))
        now += 15 * 60 * 1000L
        assertFalse(debug.isEnabled())
        assertFalse(debug.record("portal", "expired", "{}"))
        assertTrue(debug.enableFor())
        debug.disable()
        assertFalse(debug.isEnabled())
        assertFalse(DiagnosticPolicy(directory, false, { now }).enableFor())
        assertEquals("", DiagnosticPolicy(directory, false, { now }).exportSummary())
        assertFalse(debug.enableFor(0))
    }

    @Test fun rawBodiesAndCallerLabelsNeverReachFilesOrExport() {
        val directory = temporary.newFolder()
        val policy = DiagnosticPolicy(directory, true, { 1_000_000L })
        assertTrue(policy.enableFor())
        val secrets = listOf("synthetic-name", "synthetic-password", "synthetic-token", "synthetic-cookie", "synthetic-id")
        val body = """{"name":"${secrets[0]}","password":"${secrets[1]}","access_token":"${secrets[2]}","Cookie":"${secrets[3]}","student_id":"${secrets[4]}","data":[]}"""
        assertTrue(policy.record("portal", "../${secrets[0]}?ticket=${secrets[2]}", body))
        assertTrue(policy.record(secrets[1], secrets[2], "<html>${secrets[3]}</html>"))
        val retained = directory.listFiles()!!.joinToString { it.name + it.readText() }
        secrets.forEach { assertFalse(it, retained.contains(it)) }
        assertTrue(retained.contains("JSON对象"))
        assertTrue(retained.contains("HTML响应"))
        val exported = policy.exportSummary()
        assertTrue(exported.length <= 4096)
        assertTrue(exported.contains("source=portal"))
        assertTrue(exported.contains("JSON对象"))
        assertTrue(exported.contains("HTML响应"))
        secrets.forEach { assertFalse(it, exported.contains(it)) }
        assertTrue(policy.record("jw", "long", "synthetic-private".repeat(100_000)))
        assertFalse(directory.listFiles()!!.joinToString { it.readText() }.contains("synthetic-private"))
    }

    @Test fun sanitizerRemovesCompleteSecretsBeforeTheLengthCut() {
        val raw = """{"access_token":"synthetic-token-with-long-tail","password":"synthetic-password","tempcode":"synthetic-temp","X-SESSION-ID":"synthetic-session"}
Cookie: synthetic-cookie
https://example.test/api?ticket=synthetic-ticket
"""
        val safe = DiagnosticPolicy.sanitize(raw, 200)
        listOf("synthetic-token", "synthetic-password", "synthetic-temp", "synthetic-session", "synthetic-cookie", "synthetic-ticket")
            .forEach { assertFalse(it, safe.contains(it)) }
        assertTrue(safe.length <= 200)
        assertTrue(safe.contains(Redaction.HIDDEN))
        val prefix = DiagnosticPolicy.sanitize("password=synthetic-tail-with-very-long-value", 20)
        assertFalse(prefix.contains("synthetic"))
        assertEquals("", DiagnosticPolicy.sanitize(raw, 0))
    }

    @Test fun retentionAndCountAndByteLimitsAreEnforced() {
        val directory = temporary.newFolder()
        var now = 1_000_000L
        val policy = DiagnosticPolicy(directory, true, { now }, maxFiles = 2,
            maxTotalBytes = 240, maxFileBytes = 256, retentionMs = 100)
        assertTrue(policy.enableFor())
        repeat(6) {
            assertTrue(policy.record("portal", "event", "{}"))
            now++
            assertTrue(directory.listFiles()!!.size <= 2)
            assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 240)
        }
        val tooBig = File(directory, "diagnostic-oversized.txt").apply { writeText("x".repeat(257)); setLastModified(now) }
        val future = File(directory, "diagnostic-future.txt").apply { writeText("safe"); setLastModified(now + 1_000) }
        policy.prune()
        assertFalse(tooBig.exists())
        assertFalse(future.exists())
        now += 101
        assertTrue(policy.prune() > 0)
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test fun exportTreatsExternallyChangedFilesAsUntrustedAndClearStaysScoped() {
        val directory = temporary.newFolder()
        val policy = DiagnosticPolicy(directory, true, { 1_000_000L })
        val tampered = File(directory, "diagnostic-tampered.txt").apply {
            writeText("synthetic-name synthetic-student-number password=synthetic-password")
            setLastModified(1_000_000L)
        }
        val unrelated = File(directory, "home_wallpaper.img").apply { writeText("synthetic-private-photo") }
        val exported = policy.exportSummary()
        assertFalse(exported.contains("synthetic"))
        assertEquals(1, policy.clear())
        assertFalse(tampered.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun legacyCleanupNeverReadsOrDeletesUnrelatedFilesOrNestedPrivateData() {
        val directory = temporary.newFolder()
        listOf("tron_raw_a.txt", "portal_raw_b.txt", "jw_raw_c.txt").forEach {
            File(directory, it).writeText("synthetic-secret")
        }
        val photo = File(directory, "home_wallpaper.img").apply { writeText("synthetic-private-photo") }
        val other = File(directory, "portal_raw_other.json").apply { writeText("synthetic-private") }
        val nested = File(directory, "nested").apply { mkdir() }
        val nestedRaw = File(nested, "portal_raw_nested.txt").apply { writeText("synthetic-private") }
        assertEquals(3, DiagnosticPolicy.clearLegacyRawFiles(directory))
        assertTrue(photo.exists())
        assertTrue(other.exists())
        assertTrue(nestedRaw.exists())
        assertEquals(0, DiagnosticPolicy.clearLegacyRawFiles(File(directory, "absent")))
    }
}
