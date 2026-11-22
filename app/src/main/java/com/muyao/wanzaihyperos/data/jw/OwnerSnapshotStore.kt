package com.muyao.wanzaihyperos.data.jw

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.net.asObjectOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Private snapshots must prove their owner; legacy unbound payloads are never seeded. */
internal class OwnerSnapshotStore(
    private val file: File?,
    private val source: String,
    private val epoch: AccountEpoch,
    private val secondaryOwner: (() -> String?)? = null,
) {
    private fun secondaryKey(): String? = secondaryOwner?.invoke()?.takeIf { it.isNotBlank() }?.let {
        java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun read(token: Long): String? {
        var payload: String? = null
        epoch.publish(token) {
            val identity = epoch.identity() ?: return@publish
            val target = file?.takeIf { it.isFile && it.length() in 1..MAX_BYTES } ?: return@publish
            payload = catchingActive {
                val root = Json.parseToJsonElement(target.readText()).asObjectOrNull() ?: return@catchingActive null
                fun field(key: String) = (root[key] as? JsonPrimitive)?.content
                if (field("version") != "1" || field("source") != source || field("owner") != identity.ownerKey) {
                    return@catchingActive null
                }
                if (secondaryOwner != null && (secondaryKey() == null || field("secondaryOwner") != secondaryKey())) {
                    return@catchingActive null
                }
                if (field("generation")?.toLongOrNull()?.let { it >= 0L } != true) return@catchingActive null
                (root["payload"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            }.getOrNull()
        }
        return payload
    }

    fun write(token: Long, payload: String): Boolean {
        var written = false
        epoch.publish(token) {
            val identity = epoch.identity() ?: return@publish
            val target = file ?: return@publish
            val secondary = secondaryKey()
            if (secondaryOwner != null && secondary == null) return@publish
            if (payload.isBlank() || payload.toByteArray(Charsets.UTF_8).size > MAX_BYTES / 2) return@publish
            written = catchingActive {
                target.parentFile?.mkdirs()
                val text = buildJsonObject {
                    put("version", JsonPrimitive(1))
                    put("source", JsonPrimitive(source))
                    put("owner", JsonPrimitive(identity.ownerKey))
                    put("generation", JsonPrimitive(identity.generation))
                    secondary?.let { put("secondaryOwner", JsonPrimitive(it)) }
                    put("payload", JsonPrimitive(payload))
                }.toString()
                if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return@catchingActive false
                val temp = File.createTempFile("snapshot-", ".tmp", target.parentFile)
                try {
                    temp.writeText(text)
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    true
                } finally { temp.delete() }
            }.getOrDefault(false)
        }
        return written
    }

    private companion object { const val MAX_BYTES = 2_097_152L }
}
