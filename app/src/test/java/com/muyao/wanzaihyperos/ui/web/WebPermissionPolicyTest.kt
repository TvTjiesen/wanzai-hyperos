package com.muyao.wanzaihyperos.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebPermissionPolicyTest {
    private val origin = "https://mobile.hzcu.edu.cn"
    private val pageUrl = "$origin/home?synthetic=1"
    private val camera = setOf(WebCaptureResource.Camera)
    private val both = setOf(WebCaptureResource.Camera, WebCaptureResource.Microphone)

    @Test
    fun `trusted HTTPS same origin includes exact port and case normalized domain`() {
        assertEquals(WebPermissionOrigin("mobile.hzcu.edu.cn", 443),
            WebPermissionPolicy.eligibleOrigin("HTTPS://MOBILE.HZCU.EDU.CN:443/", pageUrl))
        assertNotNull(WebPermissionPolicy.eligibleOrigin("$origin:8443", "$origin:8443/home"))
        assertNull(WebPermissionPolicy.eligibleOrigin("$origin:8443", pageUrl))
        assertNull(WebPermissionPolicy.eligibleOrigin(origin, "$origin:8443/home"))
        assertEquals("https://mobile.hzcu.edu.cn:8443",
            WebPermissionPolicy.eligibleOrigin("$origin:8443", "$origin:8443/home")?.display)
    }

    @Test
    fun `cross origin iframe and arbitrary or school suffix domains are not capture trusted`() {
        for (request in listOf("https://evil.example", "https://mobile.hzcu.edu.cn.evil.example",
            "https://evil-mobile.hzcu.edu.cn", "https://unlisted.hzcu.edu.cn",
            "https://hzcu.edu.cn", "https://ca.hzcu.edu.cn", "https://libres.hzcu.edu.cn")) {
            assertNull(request, WebPermissionPolicy.eligibleOrigin(request, request + "/page"))
            assertNull(request, WebPermissionPolicy.eligibleOrigin(request, pageUrl))
        }
        assertNull(WebPermissionPolicy.eligibleOrigin("https://cjh52.hzcu.edu.cn", pageUrl))
        assertNull(WebPermissionPolicy.eligibleOrigin(origin, "https://cjh52.hzcu.edu.cn/home"))
    }

    @Test
    fun `only explicit configured business hosts can ask and neither HTTP nor malformed origins can`() {
        for (host in listOf("mobile", "courses", "cjh52", "dingding", "zhhq", "zhgy", "safety", "iappdd", "iapp")) {
            val request = "https://$host.hzcu.edu.cn"
            assertNotNull(host, WebPermissionPolicy.eligibleOrigin(request, "$request/page"))
            assertNull(host, WebPermissionPolicy.eligibleOrigin(request.replace("https:", "http:"),
                "$request/page".replace("https:", "http:")))
        }
        for (request in listOf("", "null", "about:blank", "data:text/plain,test", "file:///tmp/test",
            "http://mobile.hzcu.edu.cn", "$origin/path", "$origin?query=1", "$origin#fragment",
            "https://user@mobile.hzcu.edu.cn", "https://mobile.hzcu.edu.cn.",
            "https://mobile.hzcu.edu.cn:0", "https://mobile.hzcu.edu.cn:65536",
            "https://mobile.hzcu.edu.cn:bad", "https://mobile.hzcu.edu.cn\\@evil.example")) {
            assertNull(request, WebPermissionPolicy.eligibleOrigin(request, pageUrl))
        }
        assertNull(WebPermissionPolicy.eligibleOrigin(origin, "http://mobile.hzcu.edu.cn/home"))
    }

    @Test
    fun `already granted Android permission is not consent and each request needs new confirmation`() {
        val gate = WebPermissionGate()
        val first = gate.begin(origin, pageUrl, camera, active = true)!!
        assertTrue(gate.finish(first.id, pageUrl, true, both).isEmpty())
        assertTrue(gate.confirm(first.id, pageUrl, true))
        assertFalse(gate.confirm(first.id, pageUrl, true))
        assertEquals(camera, gate.finish(first.id, pageUrl, true, both))
        assertTrue(gate.finish(first.id, pageUrl, true, both).isEmpty())
        val next = gate.begin(origin, pageUrl, camera, true)!!
        assertTrue(next.id > first.id)
        assertTrue(gate.finish(next.id, pageUrl, true, both).isEmpty())
        assertTrue(gate.confirm(next.id, pageUrl, true))
        assertEquals(camera, gate.finish(next.id, pageUrl, true, both))
    }

    @Test
    fun `partial system grant remains limited to requested capture resources`() {
        val gate = WebPermissionGate()
        val ticket = gate.begin(origin, pageUrl, both, true)!!
        assertTrue(gate.confirm(ticket.id, pageUrl, true))
        assertEquals(camera, gate.finish(ticket.id, pageUrl, true, camera))
        val denied = gate.begin(origin, pageUrl, camera, true)!!
        assertTrue(gate.confirm(denied.id, pageUrl, true))
        assertTrue(gate.finish(denied.id, pageUrl, true, emptySet()).isEmpty())
        assertNull(gate.begin(origin, pageUrl, emptySet(), true))
    }

    @Test
    fun `navigation even same origin invalidates confirmation and system result`() {
        for (waitingSystem in listOf(false, true)) {
            val gate = WebPermissionGate()
            val old = gate.begin(origin, pageUrl, camera, true)!!
            if (waitingSystem) assertTrue(gate.confirm(old.id, pageUrl, true))
            gate.invalidate()
            assertFalse(gate.confirm(old.id, "$origin/other", true))
            assertTrue(gate.finish(old.id, "$origin/other", true, both).isEmpty())
            val current = gate.begin(origin, "$origin/other", camera, true)!!
            assertFalse(gate.confirm(old.id, "$origin/other", true))
            gate.cancel(old.id)
            assertTrue(gate.confirm(current.id, "$origin/other", true))
            assertTrue(gate.finish(old.id, "$origin/other", true, both).isEmpty())
            assertEquals(camera, gate.finish(current.id, "$origin/other", true, both))
        }
    }

    @Test
    fun `duplicate concurrent request is denied without replacing pending consent`() {
        val gate = WebPermissionGate()
        val first = gate.begin(origin, pageUrl, camera, true)!!
        assertNull(gate.begin(origin, pageUrl, both, true))
        assertTrue(gate.confirm(first.id, pageUrl, true))
        assertNull(gate.begin(origin, pageUrl, both, true))
        assertEquals(camera, gate.finish(first.id, pageUrl, true, both))
    }

    @Test
    fun `cancelled Android request must drain its original callback before accepting a new request`() {
        val gate = WebPermissionGate()
        val old = gate.begin(origin, pageUrl, camera, true)!!
        assertTrue(gate.confirm(old.id, pageUrl, true))
        assertTrue(gate.startSystemRequest(old.id))
        assertFalse(gate.startSystemRequest(old.id))
        gate.cancel(old.id)
        gate.invalidate()
        assertNull(gate.begin(origin, pageUrl, camera, true))
        assertFalse(gate.endSystemRequest(old.id + 1))
        assertNull(gate.begin(origin, pageUrl, camera, true))
        assertTrue(gate.endSystemRequest(old.id))
        assertTrue(gate.finish(old.id, pageUrl, true, both).isEmpty())
        val current = gate.begin(origin, pageUrl, camera, true)!!
        assertTrue(gate.confirm(current.id, pageUrl, true))
        assertTrue(gate.startSystemRequest(current.id))
        assertFalse(gate.endSystemRequest(old.id))
        assertTrue(gate.finish(current.id, pageUrl, true, both).isEmpty())
        assertTrue(gate.endSystemRequest(current.id))
        assertEquals(camera, gate.finish(current.id, pageUrl, true, both))
    }

    @Test
    fun `stop origin mismatch cancellation and dispose deny old callbacks`() {
        val gate = WebPermissionGate()
        assertNull(gate.begin(origin, pageUrl, camera, active = false))
        val stopped = gate.begin(origin, pageUrl, camera, true)!!
        assertFalse(gate.confirm(stopped.id, pageUrl, false))
        val stoppedAfterConfirm = gate.begin(origin, pageUrl, camera, true)!!
        assertTrue(gate.confirm(stoppedAfterConfirm.id, pageUrl, true))
        assertTrue(gate.finish(stoppedAfterConfirm.id, pageUrl, false, both).isEmpty())
        val changed = gate.begin(origin, pageUrl, camera, true)!!
        assertTrue(gate.confirm(changed.id, pageUrl, true))
        assertTrue(gate.finish(changed.id, "https://cjh52.hzcu.edu.cn", true, both).isEmpty())
        val cancelled = gate.begin(origin, pageUrl, camera, true)!!
        gate.cancel(cancelled.id)
        assertFalse(gate.confirm(cancelled.id, pageUrl, true))
        val destroyed = gate.begin(origin, pageUrl, camera, true)!!
        assertTrue(gate.confirm(destroyed.id, pageUrl, true))
        gate.dispose()
        assertTrue(gate.finish(destroyed.id, pageUrl, true, both).isEmpty())
        assertNull(gate.begin(origin, pageUrl, camera, true))
    }

    @Test
    fun `page wires confirmation real origin cancellation lifecycle and preserves file upload without insecure bypass`() {
        val run = File(System.getProperty("user.dir"))
        val root = if (File(run.parentFile, "app/src/main/java").isDirectory) run.parentFile else run
        val source = File(root, "app/src/main/java/com/muyao/wanzaihyperos/ui/web/WebShellPage.kt").readText()
        val handler = source.substringAfter("override fun onPermissionRequest(request:")
            .substringBefore("override fun onPermissionRequestCanceled")
        assertTrue(handler.contains("request.origin.toString(), webView?.url.orEmpty()"))
        assertTrue(handler.contains("permissionGate.begin("))
        assertTrue(handler.contains("permissionConfirmation = pending"))
        assertFalse(handler.contains(".grant("))
        assertFalse(handler.contains("webPermissionLauncher.launch("))
        assertTrue(source.contains("实际请求网站：\${confirmation.ticket.origin.display}"))
        assertTrue(source.contains("onClick = { confirmCapture(confirmation) }"))
        // A capture cancelled by navigation while the system dialog was open must be answered,
        // not left hanging: it is denied instead of silently dropped.
        assertTrue(source.contains("if (pendingWebPermission !== launched) { launched.request.deny(); return }"))
        assertTrue(source.contains("if (boundaryStopped) { denyPendingCapture(); launched.request.deny(); return }"))
        assertTrue(source.contains("else launched.request.deny()"))
        assertTrue(source.contains("denyPendingCapture(); onClearCredential()"))
        assertTrue(source.contains("permissionGate.endSystemRequest(launched.ticket.id)"))
        assertTrue(source.contains("override fun onPermissionRequestCanceled"))
        assertTrue(source.contains("if (pendingWebPermission?.request === request) denyPendingCapture()"))
        assertTrue(source.contains("if (request?.isForMainFrame == true) denyPendingCapture()"))
        val navigation = source.substringAfter("override fun onPageStarted(")
            .substringBefore("override fun onPageFinished(")
        assertTrue(navigation.contains("denyPendingCapture()"))
        assertTrue(source.contains("if (isReload || (url != null && url != currentUrl)) denyPendingCapture()"))
        assertTrue(source.contains("onDismissRequest = { if (pendingWebPermission === confirmation) denyPendingCapture() }"))
        assertTrue(source.contains("Lifecycle.State.STARTED"))
        assertTrue(source.contains("LifecycleEventObserver"))
        assertTrue(source.contains("view?.onResume()"))
        assertTrue(source.contains("view?.onPause()"))
        assertTrue(source.contains("lifecycle.removeObserver(observer)"))
        assertTrue(source.contains("permissionGate.dispose()"))
        assertTrue(source.contains("destroy()"))
        assertTrue(source.contains("override fun onShowFileChooser("))
        assertTrue(source.contains("FileChooserParams.parseResult("))
        assertTrue(source.contains("pendingFileCallback?.onReceiveValue(null)"))
        assertFalse(source.contains(".proceed()"))
        assertFalse(source.contains("MIXED_CONTENT_ALWAYS_ALLOW"))
        assertFalse(source.contains("setGeolocationEnabled(true)"))
        assertFalse(source.contains("CookieManager.getInstance().setCookie("))
    }
}
