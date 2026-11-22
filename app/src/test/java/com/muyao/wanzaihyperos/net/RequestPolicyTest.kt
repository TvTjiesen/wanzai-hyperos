package com.muyao.wanzaihyperos.net

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RequestPolicyTest {
    @Test fun renewalRequiresAuthenticationEvidence() {
        assertTrue(TronRequestPolicy.authenticationFailure(401, "maintenance"))
        assertTrue(TronRequestPolicy.authenticationFailure(200, """{"error_code":"session_expired"}"""))
        assertTrue(TronRequestPolicy.authenticationFailure(200, """{"code":401}"""))
        assertTrue(TronRequestPolicy.authenticationFailure(200, """<html><form action='/cas/login'><input type='password'></form></html>"""))
        assertTrue(TronRequestPolicy.authenticationFailure(302, "", "https://authserver.example.test/cas/login"))
        listOf(403, 500, 503).forEach { status ->
            assertFalse(TronRequestPolicy.authenticationFailure(status, """{"error_code":"session_expired"}"""))
        }
        listOf(
            "<html>统一身份认证 authserver 维护中</html>",
            """{"error_code":"wrong_number_code"}""",
            """{"message":"please login when maintenance ends"}""",
        ).forEach { assertFalse(TronRequestPolicy.authenticationFailure(200, it)) }
    }

    @Test fun onlyEndpointContractCanBecomeCacheData() {
        fun valid(path: String, body: String) = TronRequestPolicy.validContract(path, Json.parseToJsonElement(body))
        assertTrue(valid("/api/todos", """{"todo_list":[]}"""))
        assertTrue(valid("/api/my-courses?page=1", """{"courses":[]}"""))
        assertTrue(valid("/api/courses/1", """{"id":1,"display_name":"Synthetic"}"""))
        assertTrue(valid("/api/radar/rollcalls", """{"rollcalls":[]}"""))
        assertTrue(valid("/api/courses/1/exams", """{"data":[]}"""))
        assertFalse(valid("/api/courses/1/exams", """{"data":{"exams":[]}}"""))
        assertFalse(valid("/api/my-courses", """{"courses":[{"id":1}]}"""))
        assertFalse(valid("/api/todos", """{"todo_list":[{}]}"""))
        listOf("{}", "null", "[]", """{"todo_list":[1]}""", """{"todo_list":[],"success":false}""",
            """{"todo_list":[],"error_code":"busy"}""", """{"todo_list":[],"code":500}""")
            .forEach { assertFalse(it, valid("/api/todos", it)) }
        assertFalse(valid("/api/courses/1", """{"id":1}"""))
        assertFalse(valid("/api/unknown", """{"data":[]}"""))
    }

    @Test fun sensitiveAndFastChangingEndpointsHaveNoCacheTtl() {
        listOf("/api/radar/rollcalls?api_version=1", "/api/course/1/student/2/rollcalls",
            "/api/course/1/homework/submission-status", "/api/homework/1/submission_list",
            "/api/profile", "/api/unknown").forEach { assertEquals(it, 0L, TronRequestPolicy.ttlMs(it)) }
        assertEquals(30_000L, TronRequestPolicy.ttlMs("/api/todos"))
        assertEquals(30_000L, TronRequestPolicy.ttlMs("/api/courses/1/homework-activities?page=1"))
        assertEquals(60_000L, TronRequestPolicy.ttlMs("/api/course/1/student-self-score"))
        assertEquals(300_000L, TronRequestPolicy.ttlMs("/api/my-courses?conditions=%7B%7D"))
    }

    @Test fun userErrorsAreRedactedBeforeTruncationIncludingDeepCauses() {
        val secrets = listOf("synthetic-password", "synthetic-token", "synthetic-ticket", "synthetic-temp", "synthetic-sid", "synthetic-cookie", "synthetic-bearer")
        val text = """{"password":"${secrets[0]}","access_token":"${secrets[1]}","ticket":"${secrets[2]}","tempcode":"${secrets[3]}","X-SESSION-ID":"${secrets[4]}"}
Cookie: session=${secrets[5]}
Authorization: Bearer ${secrets[6]}
https://example.test/api?token=another-secret
"""
        val error = HzcuException.Parse(text, details = text, cause = IllegalArgumentException(text + "x".repeat(1_000)))
        val safe = error.userText
        assertTrue(safe.length <= 400)
        secrets.forEach { assertFalse(it, safe.contains(it)) }
        assertFalse(safe.contains("another-secret"))
        assertTrue(safe.contains(Redaction.HIDDEN))
        assertFalse(safeRequestText("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.fakeSignature", 400).contains("fakeSignature"))
        assertFalse(IllegalStateException(text).shortErrorText().contains(secrets[0]))
        val escaped = """{"password":"synthetic-\"escaped-password","Cookie":"synthetic-json-cookie"}"""
        val escapedSafe = safeRequestText(escaped, 400)
        assertFalse(escapedSafe.contains("escaped-password"))
        assertFalse(escapedSafe.contains("synthetic-json-cookie"))
    }
}
