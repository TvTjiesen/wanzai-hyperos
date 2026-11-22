package com.muyao.wanzaihyperos.data.jw

import java.net.URI

internal object JwOriginPolicy {
    private const val HOST = "ijw.hzcu.edu.cn"
    private val loginPaths = setOf("/xtgl/login_slogin.html", "/jwglxt/xtgl/login_slogin.html")

    fun isTrustedUrl(raw: String): Boolean = trustedUri(raw) != null

    fun isLoginPath(path: String): Boolean = path in loginPaths

    fun isLoginSuccess(currentUrl: String, loginPath: String): Boolean {
        if (!isLoginPath(loginPath)) return false
        val current = trustedUri(currentUrl) ?: return false
        return current.rawPath == loginPath.substringBeforeLast('/') + "/index_initMenu.html"
    }

    fun requestPath(raw: String): String? {
        val uri = parse(raw) ?: return null
        if (uri.rawFragment != null || uri.rawUserInfo != null) return null
        if (uri.isAbsolute) {
            if (trustedUri(raw) == null) return null
        } else if (uri.rawAuthority != null) return null
        val path = uri.rawPath.orEmpty()
        if (!path.startsWith('/') || path.startsWith("//")) return null
        return path + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun resolveRequest(currentUrl: String, readyState: String, raw: String): String? {
        if (readyState != "interactive" && readyState != "complete") return null
        val current = trustedUri(currentUrl) ?: return null
        val path = requestPath(raw) ?: return null
        return "${current.scheme.lowercase()}://$HOST$path"
    }

    // Rechecked inside the same synchronous JS turn that writes credentials or starts fetch.
    val documentGuardJs = """
        (function(){
          try {
            var u = new URL(window.location.href);
            return (document.readyState === 'interactive' || document.readyState === 'complete') &&
              (u.protocol === 'http:' || u.protocol === 'https:') &&
              u.hostname === '$HOST' && !u.username && !u.password &&
              (!u.port || (u.protocol === 'http:' && u.port === '80') ||
                          (u.protocol === 'https:' && u.port === '443'));
          } catch (_) { return false; }
        })()
    """.trimIndent()

    private fun trustedUri(raw: String): URI? {
        val uri = parse(raw) ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!uri.host.equals(HOST, ignoreCase = true) || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != if (scheme == "http") 80 else 443) return null
        if (uri.rawPath.orEmpty().startsWith("//")) return null
        return uri
    }

    private fun parse(raw: String): URI? {
        if (raw.any { it <= ' ' || it == '\\' }) return null
        return runCatching { URI(raw) }.getOrNull()
    }
}
