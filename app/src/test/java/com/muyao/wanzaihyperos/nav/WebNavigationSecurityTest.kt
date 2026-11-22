package com.muyao.wanzaihyperos.nav

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.ui.web.TronWebCredentials
import com.muyao.wanzaihyperos.ui.web.WebDisplayLease
import com.muyao.wanzaihyperos.ui.web.WebTargetResolver
import com.muyao.wanzaihyperos.ui.web.completeLogin
import com.muyao.wanzaihyperos.data.repo.catchingActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebNavigationSecurityTest {
    private val json = Json { encodeDefaults = true }
    private fun route(raw: String) = Route.Web(60, "网页", WebTarget.fromUrl(raw))
    private fun active() = AccountEpoch().apply { activate("synthetic-account-A") }
    private fun resolver(
        code: suspend () -> String? = { "synthetic-new-code" },
        renew: suspend (String, Long) -> TronWebCredentials? = { _, _ -> TronWebCredentials("synthetic-new-access", "synthetic-new-refresh") },
        save: (TronWebCredentials) -> Unit = {},
        revision: () -> Long = { 0L },
    ) = WebTargetResolver(code, { TronWebCredentials("synthetic-old-access", "synthetic-old-refresh") }, renew, save, revision)

    @Test fun `serialized Web and native fallback state never contains synthetic credentials`() {
        val rawTron = "https://mobile.hzcu.edu.cn/login-by-identity-token?accessToken=synthetic-access-secret&refreshToken=synthetic-refresh-secret"
        val rawCjh = "https://cjh52.hzcu.edu.cn/notice?id=42&tempcode=synthetic-code-secret&token=synthetic-token-secret&trackid=123"
        val routes: List<Route> = listOf(route(rawTron), route(rawCjh), Route.TronHome(rawTron), Route.Lectures(rawCjh))
        val encoded = json.encodeToString(ListSerializer(Route.serializer()), routes)
        listOf("synthetic-access-secret", "synthetic-refresh-secret", "synthetic-code-secret", "synthetic-token-secret", "login-by-identity-token").forEach {
            assertFalse("Persisted state must not contain credential marker", encoded.contains(it))
        }
        val restored = json.decodeFromString(ListSerializer(Route.serializer()), encoded)
        assertEquals(WebTarget.Kind.TRONCLASS, (restored[0] as Route.Web).target.kind)
        assertEquals("https://cjh52.hzcu.edu.cn/notice?id=42", (restored[1] as Route.Web).target.address)
        assertEquals("", (restored[2] as Route.TronHome).tileUrl)
    }

    @Test fun `legacy credential url blocks only its entry and never replays`() {
        val legacy = """[{"type":"com.muyao.wanzaihyperos.nav.Route.Main"},{"type":"com.muyao.wanzaihyperos.nav.Route.Web","appId":156,"title":"网页","url":"https://mobile.hzcu.edu.cn/login-by-identity-token?accessToken=synthetic-legacy-secret"}]"""
        val restored = json.decodeFromString(ListSerializer(Route.serializer()), legacy)
        assertEquals(Route.Main, restored[0])
        assertEquals(WebTarget.Kind.BLOCKED, (restored[1] as Route.Web).target.kind)
        assertFalse(json.encodeToString(ListSerializer(Route.serializer()), restored).contains("synthetic-legacy-secret"))
    }

    @Test fun `forged new saved target is validated again`() {
        val forged = """{"appId":0,"title":"网页","target":{"kind":"DIRECT","address":"https://example.org/path?token=synthetic-forged-secret"}}"""
        assertEquals(WebTarget.Kind.BLOCKED, json.decodeFromString(Route.Web.serializer(), forged).target.kind)
    }

    @Test fun `business query and SPA routes survive while credentials are rejected everywhere`() {
        listOf(
            "https://cjh52.hzcu.edu.cn/course/student?appid=60&mode=prod",
            "https://example.org/page?q=hello%20world&view=compact&page=2",
            "https://cjh52.hzcu.edu.cn/#/notice?id=42",
            "https://cjh52.hzcu.edu.cn/#/live/detail?lectureId=123",
            "https://cjh52.hzcu.edu.cn/lost/lostList",
            "https://cjh52.hzcu.edu.cn/studentCadre/StudentPostList",
            "https://cjh52.hzcu.edu.cn/workstudy/student/recruit",
            "https://cjh52.hzcu.edu.cn/lecture/lectureList",
            "http://10.61.132.121/thirdlogin.html",
        ).forEach { assertNotEquals(WebTarget.Kind.BLOCKED, WebTarget.fromUrl(it).kind) }
        listOf(
            "https://example.org/page?%74oken=synthetic-secret",
            "https://example.org/page?code=synthetic-secret",
            "https://example.org/page?redirect=https%3A%2F%2Fexample.org%2F%3Ftoken%3Dsecret",
            "https://example.org/ticket/synthetic-secret",
            "https://example.org/page#token=synthetic-secret",
            "https://example.org/#/page?accessToken=synthetic-secret",
            "https://example.org/page?x=Ab9Cd8Ef7Gh6Ij5Kl4Mn3Op2",
            "https://example.org/Ab9Cd8Ef7Gh6Ij5Kl4Mn3Op2",
            "https://example.org/page?%2574oken=secret",
            "https://user:secret@example.org/page",
            "javascript:alert(1)",
        ).forEach { assertEquals("Unsafe target must be blocked", WebTarget.Kind.BLOCKED, WebTarget.fromUrl(it).kind) }
    }

    @Test fun `school activity and lecture object IDs survive without admitting credential URLs`() {
        val id = "abcdef0123456789abcdef01"
        mapOf(
            "/activity/ActivityDetail" to "/activity/activityDetail",
            "/lecture/LectureDetail" to "/lecture/lectureDetail",
        ).forEach { (legacyPath, currentPath) ->
            val currentUrl = "https://cjh52.hzcu.edu.cn$currentPath?id=$id"
            val target = WebTarget.fromUrl("https://cjh52.hzcu.edu.cn$legacyPath?id=$id")
            assertEquals(WebTarget.Kind.CJH52, target.kind)
            assertEquals(currentUrl, target.addressForDisplay())
            val currentTarget = WebTarget.fromUrl(currentUrl)
            assertEquals(WebTarget.Kind.CJH52, currentTarget.kind)
            assertEquals(currentUrl, currentTarget.addressForDisplay())
            assertEquals(target, json.decodeFromString(WebTarget.serializer(), json.encodeToString(WebTarget.serializer(), target)))
            val credentialInput = WebTarget.fromUrl("https://cjh52.hzcu.edu.cn$legacyPath?id=$id&token=synthetic-secret")
            assertEquals(target, credentialInput)
            assertFalse(json.encodeToString(WebTarget.serializer(), credentialInput).contains("synthetic-secret"))
        }
        listOf(
            "https://example.org/activity/ActivityDetail?id=$id",
            "https://cjh52.hzcu.edu.cn/other?id=$id",
            "https://cjh52.hzcu.edu.cn/activity/ActivityDetail?id=Ab9Cd8Ef7Gh6Ij5Kl4Mn3Op2",
            "https://cjh52.hzcu.edu.cn/activity/ActivityDetail?id=$id%2Fsecret",
        ).forEach { assertEquals(WebTarget.Kind.BLOCKED, WebTarget.fromUrl(it).kind) }
    }

    @Test fun `whole catalog survives validation and forged business callback credentials do not`() {
        com.muyao.wanzaihyperos.data.DemoCatalog.all.forEach { app ->
            val target = if (app.system == com.muyao.wanzaihyperos.data.PortalSystem.JSP)
                WebTarget.fromTempCodeUrl(app.url) else WebTarget.fromUrl(app.url)
            assertNotEquals("Catalog entry ${app.id} must remain usable", WebTarget.Kind.BLOCKED, target.kind)
            assertEquals(target, json.decodeFromString(WebTarget.serializer(), json.encodeToString(WebTarget.serializer(), target)))
        }
        listOf(
            "https://ca.hzcu.edu.cn/cas/login?service=https://scsystem.hzcu.edu.cn:83/auth/private-auth-login?token=synthetic-secret",
            "https://ca.hzcu.edu.cn/cas/login?service=https://example.org/login",
            "https://ijw.hzcu.edu.cn/sso/ddszLogin?url=jcjsgl%2FjcjfMobile%3Ftoken%3Dsynthetic-secret",
            "https://example.org:8088/",
            "https://safety.hzcu.edu.cn:9000/",
        ).forEach { assertEquals(WebTarget.Kind.BLOCKED, WebTarget.fromUrl(it).kind) }
        assertEquals(WebTarget.Kind.BLOCKED, WebTarget.fromTempCodeUrl("https://example.org/page").kind)
    }

    @Test fun `JSP code is issued per display and never serialized`() = runBlocking {
        var issued = 0
        val target = WebTarget.fromTempCodeUrl(com.muyao.wanzaihyperos.data.DemoCatalog.all.first { it.id == 49 }.url)
        val route = Route.Web(49, "请假", target)
        assertEquals(WebTarget.Kind.PORTAL_TEMP, target.kind)
        val resolver = resolver(code = { "synthetic-jsp-${++issued}" })
        assertTrue(resolver.resolve(route, WebDisplayLease(active()))!!.contains("synthetic-jsp-1"))
        assertTrue(resolver.resolve(route, WebDisplayLease(active()))!!.contains("synthetic-jsp-2"))
        assertFalse(json.encodeToString(Route.Web.serializer(), route).contains("synthetic-jsp"))
    }

    @Test fun `CJH is issued at every display and never saved as usable session`() = runBlocking {
        var issued = 0
        var saved = 0
        val resolver = resolver(code = { "synthetic-code-${++issued}" }, save = { saved++ })
        val epoch = active()
        val route = route("https://cjh52.hzcu.edu.cn/notice?id=42")
        val first = resolver.resolve(route, WebDisplayLease(epoch))
        val second = resolver.resolve(route, WebDisplayLease(epoch))
        assertNotEquals(first, second)
        assertEquals(2, issued)
        assertEquals(0, saved)
        assertFalse(json.encodeToString(Route.Web.serializer(), route).contains("synthetic-code"))
    }

    @Test fun `portal detail display preserves the object ID and replaces the code on retry`() = runBlocking {
        val id = "abcdef0123456789abcdef01"
        val epoch = active()
        var issued = 0
        val resolver = resolver(code = { "synthetic-detail-${++issued}" })
        val paths = mapOf(
            "/activity/ActivityDetail" to "/activity/activityDetail",
            "/lecture/LectureDetail" to "/lecture/lectureDetail",
        )
        for ((legacyPath, currentPath) in paths) {
            val target = WebTarget.fromTempCodeUrl(
                "https://cjh52.hzcu.edu.cn$legacyPath?id=$id&tempcode=synthetic-old&token=synthetic-old",
            )
            assertEquals(WebTarget.Kind.CJH52, target.kind)
            val route = Route.Web(0, "消息详情", target)
            val lease = WebDisplayLease(epoch)
            repeat(2) {
                val uri = java.net.URI(requireNotNull(resolver.resolve(route, lease)))
                val query = uri.rawQuery.split('&').groupBy({ it.substringBefore('=') }, { it.substringAfter('=') })
                assertEquals("https", uri.scheme)
                assertEquals("cjh52.hzcu.edu.cn", uri.host)
                assertEquals(currentPath, uri.path)
                assertEquals(listOf(id), query["id"])
                assertEquals(listOf("0"), query["appid"])
                assertEquals(listOf("synthetic-detail-$issued"), query["tempcode"])
                assertEquals(query["tempcode"], query["token"])
            }
            val saved = json.encodeToString(Route.Web.serializer(), route)
            assertFalse(saved.contains("synthetic-old"))
            assertFalse(saved.contains("synthetic-detail"))
        }
        assertEquals(4, issued)
    }

    @Test fun `Tron refresh is display time only and cancelled work never falls back`() = runBlocking {
        var saved = 0
        val epoch = active()
        val route = Route.Web(156, "网页", WebTarget.tronclass())
        val fresh = resolver(save = { saved++ }).resolve(route, WebDisplayLease(epoch))
        assertTrue(fresh!!.contains("synthetic-new-access"))
        assertFalse(fresh.contains("synthetic-old-access"))
        assertEquals(1, saved)
        val cancelled = resolver(renew = { _, _ -> throw CancellationException("Synthetic cancellation") })
        var propagated = false
        try { cancelled.resolve(route, WebDisplayLease(epoch)) } catch (_: CancellationException) { propagated = true }
        assertTrue(propagated)
    }

    @Test fun `switch logout and guest reject old leases and blocked entries issue no request`() = runBlocking {
        var requests = 0
        val resolver = resolver(code = { requests++; "synthetic-code" })
        val epoch = active()
        val leaseA = WebDisplayLease(epoch)
        epoch.suspendOwner()
        epoch.activate("synthetic-account-B")
        assertNull(resolver.resolve(route("https://cjh52.hzcu.edu.cn/notice"), leaseA))
        var loaded = false
        assertFalse(leaseA.publish { loaded = true })
        assertFalse(loaded)
        val leaseB = WebDisplayLease(epoch)
        epoch.suspendOwner()
        assertNull(resolver.resolve(route("https://cjh52.hzcu.edu.cn/notice"), leaseB))
        epoch.activate(null)
        assertNull(resolver.resolve(route("https://cjh52.hzcu.edu.cn/notice"), WebDisplayLease(epoch)))
        assertNull(resolver.resolve(Route.Web(0, "旧网页", WebTarget.blocked()), WebDisplayLease(active())))
        assertEquals(0, requests)
    }

    @Test fun `switch while code or refresh response is delayed cannot publish or save`() = runBlocking {
        for (tron in listOf(false, true)) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var saved = 0
            val epoch = active()
            val lease = WebDisplayLease(epoch)
            val resolver = resolver(
                code = { entered.complete(Unit); release.await(); "synthetic-delayed-code" },
                renew = { _, _ -> entered.complete(Unit); release.await(); TronWebCredentials("synthetic-delayed-access", null) },
                save = { saved++ },
            )
            val route = if (tron) Route.Web(156, "网页", WebTarget.tronclass()) else route("https://cjh52.hzcu.edu.cn/notice")
            val pending = async { resolver.resolve(route, lease) }
            entered.await()
            epoch.suspendOwner()
            epoch.activate("synthetic-account-B")
            release.complete(Unit)
            assertNull(pending.await())
            assertEquals(0, saved)
        }
    }

    @Test fun `resolved Tron lease rejects same owner replacement or clear and old view reload is gated`() = runBlocking {
        listOf(false, true).forEach { clear ->
            val session = com.muyao.wanzaihyperos.session.TronclassSession(com.muyao.wanzaihyperos.session.InMemorySecureStore())
            session.saveTokens("synthetic-A", null)
            val lease = WebDisplayLease(active())
            val resolver = WebTargetResolver(
                { null }, { TronWebCredentials(session.accessToken(), null) }, { _, _ -> null }, {},
                session::credentialRevision, session::publishCredential,
            )
            assertTrue(resolver.resolve(Route.Web(156, "网页", WebTarget.tronclass()), lease)!!.contains("synthetic-A"))
            assertTrue(lease.isCurrent())
            if (clear) session.clear() else session.saveTokens("synthetic-B", null)
            assertFalse(lease.isCurrent())
            var reloaded = false
            assertFalse(lease.publish { reloaded = true })
            assertFalse(reloaded)
        }
        val page = java.io.File("src/main/java/com/muyao/wanzaihyperos/ui/web/WebTargetPage.kt").readText()
        assertTrue(page.contains("app.tronclassSession.addListener(listener)"))
        assertTrue(page.contains("LaunchedEffect(lease, retry, revision, credentialRevision)"))
        assertTrue(page.contains("if (current && url != null)"))
        assertTrue(page.contains("publishWebAction = lease::publish"))
        val shell = java.io.File("src/main/java/com/muyao/wanzaihyperos/ui/web/WebShellPage.kt").readText()
        assertEquals(2, Regex("publishWebAction \\{\\s*if \\(onReload == null\\) webView\\?\\.reload\\(\\) else onReload\\(\\)\\s*\\}").findAll(shell).count())
        assertTrue(page.contains("onReload = if (route.target.kind in setOf(WebTarget.Kind.CJH52, WebTarget.Kind.PORTAL_TEMP))"))
        assertTrue(page.contains("resolvedUrl = null; loading = true; retry++"))
        assertTrue(shell.contains("stopAtBoundary(it)"))
    }

    @Test fun `login exchange captures owner and credential before suspension and publishes tokens with cookies atomically`() {
        val root = java.io.File("src/main/java/com/muyao/wanzaihyperos/WanzaiRoot.kt").readText()
        val login = root.substringAfter("onAuthenticate = { account, password ->").substringBefore("entry<Route.Main>")
        val exchange = login.indexOf("val tokens = app.casService.loginForTronclassTokens")
        assertTrue(login.indexOf("val lease =") in 0 until exchange)
        assertTrue(login.indexOf("credentialRevision = app.tronclassSession.credentialRevision()") in 0 until exchange)
        assertTrue(login.contains("saved = app.tronclassSession.publishCredential(credentialRevision)"))
        assertTrue(login.contains("ssoCookiesJson ="))
        assertTrue(login.contains("lease.completeLogin {"))
        assertTrue(login.contains(") throw kotlinx.coroutines.CancellationException(\"Account changed\")"))
        assertTrue(login.contains("if (!saved) return@catchingActive"))
        assertFalse(login.contains("saveSsoCookies("))
        assertTrue(login.contains("catchingActive { app.unread.refresh"))
        assertTrue(login.contains("catch (cancelled: kotlinx.coroutines.CancellationException)"))
        assertTrue(login.substringAfter("val tokens =").contains("currentCoroutineContext().ensureActive()"))
    }

    @Test fun `delayed login exchange after primary switch cannot save or invoke success callback`() = runBlocking {
        for (failure in listOf(false, true)) {
            val epoch = active()
            val lease = WebDisplayLease(epoch)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var saved = 0
            var success = 0
            val pending = async {
                val result = lease.completeLogin {
                    catchingActive {
                        entered.complete(Unit)
                        release.await()
                        if (failure) throw IllegalStateException("Synthetic optional exchange failure")
                        if (!lease.publish { saved++ }) throw CancellationException("Account changed")
                    }
                    null
                }
                if (result == null) success++
            }
            entered.await()
            epoch.suspendOwner()
            epoch.activate("synthetic-account-B")
            release.complete(Unit)
            var cancelled = false
            try { pending.await() } catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertEquals(0, saved)
            assertEquals(0, success)
        }
    }

    @Test fun `same primary secondary rejection or optional exchange failure keeps portal login success`() = runBlocking {
        for (failure in listOf(false, true)) {
            val lease = WebDisplayLease(active())
            val session = com.muyao.wanzaihyperos.session.TronclassSession(com.muyao.wanzaihyperos.session.InMemorySecureStore())
            session.saveTokens("synthetic-A", null)
            val revision = session.credentialRevision()
            var saved = 0
            var success = 0
            val result = lease.completeLogin {
                catchingActive {
                    if (failure) throw IllegalStateException("Synthetic optional exchange failure")
                    session.saveTokens("synthetic-B", null)
                    assertFalse(session.publishCredential(revision) { saved++ })
                }
                null
            }
            if (result == null) success++
            assertEquals(0, saved)
            assertEquals(1, success)
        }
    }

    @Test fun `same owner secondary replacement rejects delayed Tron refresh`() = runBlocking {
        val epoch = active()
        var revision = 0L
        var saved = 0
        val resolver = resolver(renew = { _, _ -> revision++; TronWebCredentials("synthetic-delayed-access", null) }, save = { saved++ }, revision = { revision })
        assertNull(resolver.resolve(Route.Web(156, "网页", WebTarget.tronclass()), WebDisplayLease(epoch)))
        assertEquals(0, saved)
    }
}
