package com.muyao.wanzaihyperos.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Non-secret owner partition; generation must change on logout/account replacement. */
class RequestIdentity(val ownerKey: String, val generation: Long) {
    init { require(ownerKey.isNotBlank()); require(generation >= 0) }
    override fun equals(other: Any?): Boolean =
        other is RequestIdentity && ownerKey == other.ownerKey && generation == other.generation
    override fun hashCode(): Int = 31 * ownerKey.hashCode() + generation.hashCode()
    override fun toString(): String = "RequestIdentity(generation=$generation)"
}

internal data class RequestKey(
    val identity: RequestIdentity?,
    val method: String,
    val path: String,
    val forceRefresh: Boolean,
)

/** Followers share one result; cancelling a follower never cancels the leader. */
internal class RequestSingleFlight<K, V> {
    private val lock = Any()
    private val pending = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun run(key: K, action: suspend () -> V): V {
        val (result, leader) = synchronized(lock) {
            pending[key]?.let { it to false }
                ?: CompletableDeferred<V>().also { pending[key] = it }.let { it to true }
        }
        if (!leader) return result.await()
        try {
            return action().also { result.complete(it) }
        } catch (error: Throwable) {
            result.completeExceptionally(error)
            throw error
        } finally {
            synchronized(lock) { if (pending[key] === result) pending.remove(key) }
        }
    }
}

internal fun safeRequestText(value: String, maxLength: Int): String {
    val fields = Regex(
        """(?i)["']?([\w-]*(?:password|passwd|token|ticket|tempcode|session[_-]?id|cookie|authorization|synjones-auth|iplanetdirectorypro)[\w-]*)["']?\s*[:=]\s*(?:"(?:\\.|[^"\\\r\n])*(?:"|$)|'(?:\\.|[^'\\\r\n])*(?:'|$)|[^;,\s&]+)""",
    )
    val jwt = Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b""")
    val safe = jwt.replace(fields.replace(Redaction.redact(value)) {
        "${it.groupValues[1]}=${Redaction.HIDDEN}"
    }, Redaction.HIDDEN)
    return safe.take(maxLength)
}

/** Only documented authentication evidence permits automatic renewal. */
internal object TronRequestPolicy {
    private val authCodes = setOf(
        "unauthorized", "not_logged_in", "login_required", "session_expired", "invalid_session",
        "invalid_session_id", "token_expired", "invalid_token", "_invalid_access_token_",
    )

    fun authenticationFailure(status: Int, body: String, location: String? = null): Boolean {
        if (status == 401) return true
        if (status == 403 || status >= 500) return false
        if (status in 300..399 && Redaction.locationIndicatesAuthenticationFailure(location)) return true
        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        if (root != null) {
            if (root["code"].asIntOrNull() == 401 || root["status"].asIntOrNull() == 401) return true
            if (listOf("error_code", "error", "code").any {
                    root[it].asStringOrNull()?.lowercase() in authCodes
                }) return true
            return root["kickout"].asIntOrNull() == 1
        }
        val text = body.lowercase()
        return Redaction.looksLikeHtml(text) && text.contains("<form") &&
            Regex("""type\s*=\s*["']?password""").containsMatchIn(text) &&
            (text.contains("cas/login") || text.contains("authserver") || text.contains("login_ssologin"))
    }

    fun ttlMs(path: String): Long {
        val p = path.substringBefore('?')
        return when {
            p == "/api/radar/rollcalls" || p.endsWith("/rollcalls") ||
                p.endsWith("/submission-status") || p.endsWith("/submission_list") -> 0L
            p == "/api/todos" || p.endsWith("/homework-activities") -> 30_000L
            p.contains("score") -> 60_000L
            p.endsWith("/teaching-weeks") || p == "/api/my-courses" -> 300_000L
            p.endsWith("/bulletins") || p.endsWith("/exams") || p.endsWith("/topic-categories") -> 60_000L
            p.matches(Regex("/api/courses/\\d+")) -> 300_000L
            else -> 0L
        }
    }

    fun validContract(path: String, element: JsonElement): Boolean {
        val p = path.substringBefore('?')
        val obj = element as? JsonObject
        if (obj != null && (obj["success"].asBoolOrNull() == false ||
                obj["code"].asIntOrNull()?.let { it >= 400 } == true ||
                obj["status"].asIntOrNull()?.let { it >= 400 } == true ||
                listOf("error_code", "error").any { !obj[it].asStringOrNull().isNullOrBlank() })) return false
        fun list(
            vararg keys: String,
            nestedKeys: List<String> = emptyList(),
            ids: List<String> = listOf("id"),
            extra: (JsonObject) -> Boolean = { true },
        ): Boolean {
            val array = (element as? JsonArray) ?: keys.firstNotNullOfOrNull { obj?.get(it) as? JsonArray }
                ?: nestedKeys.firstNotNullOfOrNull { (obj?.get("data") as? JsonObject)?.get(it) as? JsonArray }
                ?: return false
            return array.all { entry ->
                entry is JsonObject && ids.any { entry[it].asIntOrNull() != null } && extra(entry)
            }
        }
        return when {
            p == "/api/todos" -> obj != null && list("todo_list")
            p == "/api/my-courses" -> obj != null && list("courses", "visited_courses", extra = {
                !it["name"].asStringOrNull().isNullOrBlank()
            })
            p.matches(Regex("/api/course/\\d+/enrollments")) -> obj != null && obj["enrollments"] is JsonArray
            p == "/api/user/resources" -> obj != null && obj["uploads"] is JsonArray &&
                (obj["page"].asIntOrNull() ?: 0) > 0 &&
                (obj["pages"].asIntOrNull() ?: -1) >= 0 &&
                (obj["page_size"].asIntOrNull()?.let { it > 0 } ?: true)
            p == "/api/profile" -> obj?.get("id").asIntOrNull() != null
            p == "/api/radar/rollcalls" -> list("rollcalls", "data", nestedKeys = listOf("rollcalls"), ids = listOf("id", "rollcall_id"))
            p.endsWith("/rollcalls") -> list("rollcalls", "data", ids = listOf("rollcall_id"))
            p.endsWith("/homework-activities") || p.endsWith("/submission-status") ->
                obj != null && list("homework_activities")
            p.endsWith("/submission_list") -> list("list", "submissions", "data", nestedKeys = listOf("list"))
            p.endsWith("/teaching-weeks") -> obj != null && list("items", ids = listOf("sequence"), extra = {
                runCatching {
                    java.time.LocalDate.parse(it["start_date"].asStringOrNull()?.trim()?.take(10))
                    java.time.LocalDate.parse(it["end_date"].asStringOrNull()?.trim()?.take(10))
                }.isSuccess
            })
            p.endsWith("/bulletins") -> list("bulletins", "data", nestedKeys = listOf("bulletins"))
            p.endsWith("/exams") -> list("exams", "data")
            p.endsWith("/topic-categories") -> list("topic_categories", "data")
            p.endsWith("/student-self-score") -> obj != null &&
                (obj["self_score"] is JsonObject || obj.containsKey("total_score"))
            p.endsWith("/score-percentages") -> obj != null &&
                listOf("score_percentage_total", "score_percentage_left", "unpublished_percentage")
                    .any { obj[it] is JsonPrimitive }
            p.endsWith("/rollcall-score") -> obj != null &&
                (obj["rollcall_times"].asIntOrNull() != null || obj["rollcall_count"].asIntOrNull() != null)
            p.matches(Regex("/api/courses/\\d+")) -> obj != null && obj["id"].asIntOrNull() != null &&
                !(obj["display_name"].asStringOrNull() ?: obj["name"].asStringOrNull()).isNullOrBlank()
            else -> false
        }
    }
}
