package com.muyao.wanzaihyperos.nav

import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Only validated, stable business addresses may be persisted. Credentials are resolved on display. */
@Serializable(with = WebTargetSerializer::class)
class WebTarget private constructor(
    val kind: Kind,
    val address: String = "",
    val colorArgb: Int? = null,
) {
    enum class Kind { DIRECT, CJH52, PORTAL_TEMP, TRONCLASS, BLOCKED }

    override fun equals(other: Any?): Boolean = other is WebTarget &&
        kind == other.kind && address == other.address && colorArgb == other.colorArgb
    override fun hashCode(): Int = 31 * (31 * kind.hashCode() + address.hashCode()) + (colorArgb ?: 0)
    override fun toString(): String = "WebTarget(kind=$kind)"

    internal fun addressForDisplay(): String {
        if (kind != Kind.CJH52) return address
        val uri = URI(address)
        val path = uri.rawPath.orEmpty()
        val currentPath = cjh52DetailPaths[path] ?: return address
        return "${uri.scheme}://${uri.rawAuthority}$currentPath" +
            uri.rawQuery?.let { "?$it" }.orEmpty() + uri.rawFragment?.let { "#$it" }.orEmpty()
    }

    companion object {
        private const val CJH52 = "cjh52.hzcu.edu.cn"
        private const val TRON = "mobile.hzcu.edu.cn"
        private val businessPorts = mapOf(
            "etcard.hzcu.edu.cn" to 8088, "safety.hzcu.edu.cn" to 8087,
            "eoa.hzcu.edu.cn" to 9000, "scsystem.hzcu.edu.cn" to 83,
            "sjfx-hzcu-edu-cn-s.webvpn1.hzcu.edu.cn" to 8118,
            "lab-hzcu-edu-cn.webvpn1.hzcu.edu.cn" to 8118,
        )
        private val catalogPaths by lazy {
            com.muyao.wanzaihyperos.data.DemoCatalog.all.mapNotNull { app ->
                runCatching { URI(app.url) }.getOrNull()?.let { it.host?.lowercase()?.let { host -> host to it.rawPath } }
            }.toSet()
        }
        private val businessKeys = setOf(
            "id", "appid", "courseid", "course_id", "sub_id", "tenant_code", "noticeid",
            "messageid", "lectureid", "examid", "classid", "clazzid", "billid", "formid",
            "taskid", "homeworkid", "type", "page", "pagesize", "tab", "mode", "area",
            "userid", "code", "devicetype", "dd_nav_bgcolor",
        )
        private val credentials = Regex("(?i)(token|tempcode|ticket|password|passwd|secret|session|cookie|authorization|authcode|credential|jwt|bearer)")
        private val transientKeys = setOf("tempcode", "token", "trackid")
        private val cjh52DetailPaths = mapOf(
            "/activity/ActivityDetail" to "/activity/activityDetail",
            "/lecture/LectureDetail" to "/lecture/lectureDetail",
        )
        private val authenticationKeys = setOf("code", "sid", "state", "nonce", "sign", "signature", "key", "auth", "access", "refresh", "sso", "samlresponse", "samlrequest")
        private fun opaque(value: String): Boolean = value.length >= 20 && value.any(Char::isLetter) &&
            (value.any(Char::isDigit) || value.any(Char::isUpperCase) || value.toSet().size >= 12)

        fun blocked(): WebTarget = WebTarget(Kind.BLOCKED)
        fun tronclass(): WebTarget = WebTarget(Kind.TRONCLASS)

        fun fromTempCodeUrl(raw: String, colorArgb: Int? = null): WebTarget {
            val safe = fromUrl(raw, colorArgb)
            val uri = runCatching { URI(safe.address) }.getOrNull()
            return when {
                safe.kind == Kind.CJH52 -> safe
                safe.kind == Kind.DIRECT && uri?.host == "dingding.hzcu.edu.cn" &&
                    uri.port in setOf(-1, 80, 443) && uri.path.startsWith("/default/dingding/") ->
                    WebTarget(Kind.PORTAL_TEMP, safe.address, colorArgb)
                else -> blocked()
            }
        }

        fun fromUrl(raw: String, colorArgb: Int? = null): WebTarget {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return blocked()
            val host = uri.host?.lowercase() ?: return blocked()
            if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.rawUserInfo != null ||
                (uri.port !in setOf(-1, 80, 443) && businessPorts[host] != uri.port)) return blocked()
            // Identity-token routes are never copied, even temporarily, into the persisted target.
            if (host == TRON && uri.scheme.equals("https", true)) return tronclass()
            val kind = if (host == CJH52) Kind.CJH52 else Kind.DIRECT
            val path = safePath(uri.rawPath.orEmpty(), host) ?: return blocked()
            val query = safeQuery(uri.rawQuery, kind, host, path) ?: return blocked()
            val fragment = uri.rawFragment?.let { rawFragment ->
                val split = rawFragment.split('?', limit = 2)
                val fragmentPath = safePath(split[0]) ?: return blocked()
                val fragmentQuery = safeQuery(split.getOrNull(1), kind) ?: return blocked()
                "#$fragmentPath$fragmentQuery"
            }.orEmpty()
            val authority = host + if (uri.port == -1) "" else ":${uri.port}"
            return WebTarget(kind, "${uri.scheme.lowercase()}://$authority$path$query$fragment", colorArgb)
        }

        internal fun restore(kind: String, address: String, colorArgb: Int?): WebTarget = when (kind) {
            Kind.TRONCLASS.name -> if (address.isEmpty()) tronclass() else blocked()
            Kind.PORTAL_TEMP.name -> fromTempCodeUrl(address, colorArgb).takeIf {
                it.kind == Kind.PORTAL_TEMP && it.address == address
            } ?: blocked()
            Kind.DIRECT.name, Kind.CJH52.name -> fromUrl(address, colorArgb).takeIf {
                it.kind.name == kind && it.address == address
            } ?: blocked()
            else -> blocked()
        }

        private fun decoded(raw: String): String? = runCatching {
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        }.getOrNull()?.takeIf { it.none { ch -> ch.isISOControl() } && '%' !in it }

        private fun safePath(raw: String, host: String = ""): String? {
            val text = decoded(raw) ?: return null
            val catalogPath = (host to raw) in catalogPaths ||
                (host == "scsystem.hzcu.edu.cn" && raw == "/auth/private-auth-login")
            val publicArticle = host == "mp.weixin.qq.com" &&
                raw.matches(Regex("/s/[A-Za-z0-9_-]{22}"))
            if (text.length > 512 || credentials.containsMatchIn(text) || ';' in text ||
                '\\' in text || text.split('/').any { it.length > 100 || (!catalogPath && ((opaque(it) && !publicArticle) || it.lowercase() in authenticationKeys)) } ||
                !text.matches(Regex("[\\p{L}\\p{N}/_.~-]*"))) return null
            return raw
        }

        private fun safeQuery(raw: String?, kind: Kind, host: String = "", path: String = ""): String? {
            if (raw.isNullOrEmpty()) return ""
            if (raw.length > 2048) return null
            val kept = mutableListOf<String>()
            for (part in raw.split('&')) {
                val pair = part.split('=', limit = 2)
                val key = decoded(pair[0])?.lowercase() ?: return null
                if (kind == Kind.CJH52 && key in transientKeys) continue
                val value = decoded(pair.getOrElse(1) { "" }) ?: return null
                if (key in setOf("service", "url")) {
                    val allowed = when {
                        key == "service" && host == "ca.hzcu.edu.cn" && path == "/cas/login" -> {
                            val nested = fromUrl(value)
                            nested.kind == Kind.DIRECT && nested.address ==
                                "https://scsystem.hzcu.edu.cn:83/auth/private-auth-login"
                        }
                        key == "url" && host == "ijw.hzcu.edu.cn" && path == "/sso/ddszLogin" ->
                            safePath(value) != null && value == "jcjsgl/jcjfMobile"
                        else -> false
                    }
                    if (!allowed) return null
                    kept += part
                    continue
                }
                if (host == CJH52 && (path in cjh52DetailPaths || path in cjh52DetailPaths.values) &&
                    key == "id" && value.matches(Regex("[0-9a-fA-F]{24}"))) {
                    kept += part
                    continue
                }
                if (!key.matches(Regex("[a-z][a-z0-9_.-]{0,39}")) || credentials.containsMatchIn(key) ||
                    credentials.containsMatchIn(value) || value.length > 80 || opaque(value) ||
                    !value.matches(Regex("[\\p{L}\\p{N}_.~ -]*"))) return null
                // Unknown ordinary low-entropy parameters remain usable; authentication hints do not.
                if ((key in authenticationKeys || key == "trackid") && value.isNotEmpty()) return null
                if (key !in businessKeys && value.length > 32) return null
                kept += part
            }
            return if (kept.isEmpty()) "" else "?${kept.joinToString("&")}"
        }
    }
}

@Serializable
private data class SavedWebTarget(val kind: String, val address: String = "", val colorArgb: Int? = null)

object WebTargetSerializer : KSerializer<WebTarget> {
    override val descriptor: SerialDescriptor = SavedWebTarget.serializer().descriptor
    override fun serialize(encoder: Encoder, value: WebTarget) {
        val safe = WebTarget.restore(value.kind.name, value.address, value.colorArgb)
        encoder.encodeSerializableValue(SavedWebTarget.serializer(), SavedWebTarget(safe.kind.name, safe.address, safe.colorArgb))
    }
    override fun deserialize(decoder: Decoder): WebTarget {
        val saved = decoder.decodeSerializableValue(SavedWebTarget.serializer())
        return WebTarget.restore(saved.kind, saved.address, saved.colorArgb)
    }
}
