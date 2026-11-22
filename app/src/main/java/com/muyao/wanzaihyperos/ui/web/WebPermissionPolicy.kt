package com.muyao.wanzaihyperos.ui.web

import java.net.URI
import java.util.Locale

internal data class WebPermissionOrigin(val host: String, val port: Int) {
    val display: String get() = "https://$host" + if (port == 443) "" else ":$port"
}

internal enum class WebCaptureResource { Camera, Microphone }

internal object WebPermissionPolicy {
    // Only known school business pages; navigation/reading itself is not restricted by this list.
    private val captureHosts = setOf(
        "mobile.hzcu.edu.cn", "courses.hzcu.edu.cn", "cjh52.hzcu.edu.cn",
        "dingding.hzcu.edu.cn", "zhhq.hzcu.edu.cn", "zhgy.hzcu.edu.cn",
        "safety.hzcu.edu.cn", "iappdd.hzcu.edu.cn", "iapp.hzcu.edu.cn",
    )

    fun httpsOrigin(url: String, originOnly: Boolean = false): WebPermissionOrigin? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.isOpaque || uri.rawUserInfo != null) {
            return null
        }
        if (originOnly && (uri.rawPath !in listOf(null, "", "/") ||
                uri.rawQuery != null || uri.rawFragment != null)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host.endsWith('.') || host.any { it.code > 127 }) return null
        val port = if (uri.port == -1) 443 else uri.port
        if (port !in 1..65535) return null
        WebPermissionOrigin(host, port)
    }.getOrNull()

    fun eligibleOrigin(requestOrigin: String, mainFrameUrl: String): WebPermissionOrigin? {
        val origin = httpsOrigin(requestOrigin, originOnly = true) ?: return null
        if (origin.host !in captureHosts) return null
        return origin.takeIf { it == httpsOrigin(mainFrameUrl) }
    }
}

internal class WebPermissionGate {
    internal data class Ticket(
        val id: Long,
        val origin: WebPermissionOrigin,
        val resources: Set<WebCaptureResource>,
    )

    private enum class Stage { Confirmation, SystemPermissions }
    private var sequence = 0L
    private var ticket: Ticket? = null
    private var stage = Stage.Confirmation
    private var disposed = false
    private var systemRequestId: Long? = null

    fun begin(requestOrigin: String, mainFrameUrl: String, resources: Set<WebCaptureResource>, active: Boolean): Ticket? {
        if (disposed || !active || ticket != null || systemRequestId != null || resources.isEmpty()) return null
        val origin = WebPermissionPolicy.eligibleOrigin(requestOrigin, mainFrameUrl) ?: return null
        return Ticket(++sequence, origin, resources.toSet()).also {
            ticket = it
            stage = Stage.Confirmation
        }
    }

    fun confirm(id: Long, mainFrameUrl: String, active: Boolean): Boolean {
        val current = ticket ?: return false
        if (current.id != id || stage != Stage.Confirmation) return false
        if (!valid(current, mainFrameUrl, active)) {
            cancel(id)
            return false
        }
        stage = Stage.SystemPermissions
        return true
    }

    fun finish(id: Long, mainFrameUrl: String, active: Boolean, granted: Set<WebCaptureResource>): Set<WebCaptureResource> {
        val current = ticket ?: return emptySet()
        if (current.id != id || stage != Stage.SystemPermissions || systemRequestId != null) return emptySet()
        ticket = null
        return if (valid(current, mainFrameUrl, active)) current.resources.intersect(granted) else emptySet()
    }

    fun startSystemRequest(id: Long): Boolean {
        if (ticket?.id != id || stage != Stage.SystemPermissions || systemRequestId != null) return false
        systemRequestId = id
        return true
    }

    fun endSystemRequest(id: Long): Boolean {
        if (systemRequestId != id) return false
        systemRequestId = null
        return true
    }

    fun cancel(id: Long) {
        if (ticket?.id == id) ticket = null
    }

    fun invalidate() { ticket = null }

    fun dispose() {
        invalidate()
        disposed = true
    }

    private fun valid(current: Ticket, mainFrameUrl: String, active: Boolean): Boolean =
        !disposed && active && current.origin == WebPermissionPolicy.httpsOrigin(mainFrameUrl)
}
