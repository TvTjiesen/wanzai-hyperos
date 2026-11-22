package com.muyao.wanzaihyperos.data.jw

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class JwOriginPolicyTest {
    private val exam = "/jwglxt/kwgl/kscx_cxXsksxxIndex.html?x=a%2Fb&x=2&empty=&q=%E8%80%83"
    private val host = "ijw.hzcu.edu.cn"

    @Test fun historicalAbsoluteRequestUsesTheCurrentDocumentScheme() {
        for (currentScheme in listOf("http", "https")) {
            for (requestScheme in listOf("http", "https")) {
                for (ready in listOf("interactive", "complete")) {
                    assertEquals("$currentScheme://$host$exam", JwOriginPolicy.resolveRequest(
                        "$currentScheme://$host/jwglxt/xtgl/index_initMenu.html", ready,
                        "$requestScheme://$host$exam",
                    ))
                }
            }
        }
        assertEquals("https://$host$exam", JwOriginPolicy.resolveRequest(
            "https://$host/jwglxt/xtgl/index_initMenu.html", "complete", exam,
        ))
    }

    @Test fun rawPathAndQueryAreNotDecodedDroppedOrReordered() {
        assertEquals(exam, JwOriginPolicy.requestPath("http://$host$exam"))
        assertEquals(exam, JwOriginPolicy.requestPath(exam))
        assertEquals("/xtgl/index_initMenu.html?", JwOriginPolicy.requestPath("/xtgl/index_initMenu.html?"))
        assertEquals("/", JwOriginPolicy.requestPath("/"))
    }

    @Test fun untrustedRequestsNeverProduceATargetForAuthenticatedBody() {
        listOf(
            "https://evil.example$exam", "https://$host.evil.example$exam",
            "https://user:password@$host$exam", "http://$host:443$exam",
            "https://$host:80$exam", "https://$host:8443$exam",
            "javascript:fetch('x')", "data:text/plain,x", "file:///tmp/x",
            "//evil.example/path", "//$host/path", "relative/path", "?only=query",
            "https://$host$exam#fragment", "/path#fragment", "/bad\\path", "/bad path",
            "https://$host//evil.example/path",
        ).forEach { assertNull(it, JwOriginPolicy.requestPath(it)) }
    }

    @Test fun wrongDocumentAndNotReadyDocumentCannotStartRequest() {
        listOf(
            "", "about:blank", "file:///tmp/x", "data:text/html,x",
            "https://evil.example/", "https://$host.evil.example/",
            "https://user@$host/", "http://$host:443/", "https://$host:80/",
            "https://$host:8443/", "https://$host//path",
        ).forEach { assertNull(it, JwOriginPolicy.resolveRequest(it, "complete", exam)) }
        listOf("", "loading", "not-ready").forEach {
            assertNull(it, JwOriginPolicy.resolveRequest("https://$host/", it, exam))
        }
    }

    @Test fun defaultPortsAndCaseAreAcceptedWithoutOpeningArbitraryPorts() {
        assertTrue(JwOriginPolicy.isTrustedUrl("http://$host:80/"))
        assertTrue(JwOriginPolicy.isTrustedUrl("https://$host:443/"))
        assertTrue(JwOriginPolicy.isTrustedUrl("HTTPS://IJW.HZCU.EDU.CN/"))
        assertFalse(JwOriginPolicy.isTrustedUrl("https://$host:8118/"))
    }

    @Test fun bothLoginContextsRequireExactTrustedSuccessPath() {
        for (prefix in listOf("", "/jwglxt")) {
            val login = "$prefix/xtgl/login_slogin.html"
            val success = "$prefix/xtgl/index_initMenu.html"
            assertTrue(JwOriginPolicy.isLoginPath(login))
            for (scheme in listOf("http", "https")) {
                assertTrue(JwOriginPolicy.isLoginSuccess("$scheme://$host$success?x=1", login))
            }
            listOf(
                "https://evil.example$success", "https://user@$host$success",
                "https://$host:8443$success", "https://$host$success.evil",
                "https://$host/other?next=$success", "https://$host/other#$success",
            ).forEach { assertFalse(it, JwOriginPolicy.isLoginSuccess(it, login)) }
        }
        assertFalse(JwOriginPolicy.isLoginSuccess("https://$host/jwglxt/xtgl/index_initMenu.html", "/xtgl/login_slogin.html"))
        assertFalse(JwOriginPolicy.isLoginSuccess("https://$host/xtgl/index_initMenu.html", "/jwglxt/xtgl/login_slogin.html"))
        assertFalse(JwOriginPolicy.isLoginPath("//evil.example/login_slogin.html"))
        assertFalse(JwOriginPolicy.isLoginSuccess("https://$host/xtgl/index_initMenu.html", "/other/login_slogin.html"))
    }

    @Test fun sourceKeepsSynchronousOriginGateBeforeFetchAndCredentialFill() {
        val source = engineSource()
        val fetch = source.substringAfter("private suspend fun fetchTextActive(").substringBefore("suspend fun evalJs(")
        assertTrue(fetch.contains("JwOriginPolicy.requestPath(path) ?: return null"))
        assertTrue(fetch.indexOf("JwOriginPolicy.documentGuardJs") < fetch.indexOf("var requestUrl = new URL"))
        assertTrue(fetch.indexOf("requestUrl.origin !== window.location.origin") < fetch.indexOf("var controller = new AbortController()"))
        assertEquals(2, Regex("mode: 'same-origin'").findAll(fetch).count())
        assertEquals(2, Regex("redirect: 'error'").findAll(fetch).count())
        assertFalse(fetch.contains("fetch(\${jsString(path)}"))
        val login = source.substringAfter("private suspend fun loginActive(").substringBefore("suspend fun fetchText(")
        assertTrue(login.contains("JwOriginPolicy.isLoginPath(loginPath)"))
        assertTrue(login.indexOf("JwOriginPolicy.documentGuardJs") < login.indexOf("y.value ="))
        assertTrue(login.contains("window.location.pathname !=="))
        assertEquals(3, Regex("JwOriginPolicy.isLoginSuccess").findAll(login).count())
        assertFalse(login.contains("contains(\"index_initMenu\")"))
        assertTrue(source.contains("cont.resume(JwOriginPolicy.isTrustedUrl(u.orEmpty()))"))
        assertFalse(source.contains("MIXED_CONTENT_ALWAYS_ALLOW"))
    }

    @Test fun sourcePreservesWorkBoundaryUniqueFetchAndAbortCleanup() {
        val source = engineSource()
        assertTrue(source.contains("work.run { fetchTextActive(path, formBody) }"))
        assertTrue(source.contains("nextFetchId++"))
        assertTrue(source.contains("window.\$key === result"))
        assertTrue(source.contains("NonCancellable + Dispatchers.Main"))
        assertTrue(source.contains("if (webView === view)"))
        assertTrue(source.contains("r.controller.abort(); delete window.\$key"))
        val guard = JwOriginPolicy.documentGuardJs
        listOf("document.readyState", "u.protocol", "u.hostname", "!u.username", "!u.password", "u.port").forEach {
            assertTrue(it, guard.contains(it))
        }
    }

    private fun engineSource(): String {
        val relative = "src/main/java/com/muyao/wanzaihyperos/data/jw/JwEngine.kt"
        return listOf(File(relative), File("app/$relative")).first { it.isFile }.readText(Charsets.UTF_8)
    }
}
