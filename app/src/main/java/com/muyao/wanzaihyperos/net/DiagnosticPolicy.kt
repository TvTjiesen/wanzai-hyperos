package com.muyao.wanzaihyperos.net

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

/** In-memory debug opt-in; persists structural summaries only, never raw school responses. */
class DiagnosticPolicy(
    private val directory: File,
    private val debuggable: Boolean,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxFiles: Int = 8,
    private val maxTotalBytes: Long = 128 * 1024L,
    private val maxFileBytes: Int = 16 * 1024,
    private val retentionMs: Long = 24 * 60 * 60 * 1000L,
) {
    init {
        require(maxFiles > 0 && maxTotalBytes > 0 && maxFileBytes > 0 && retentionMs > 0)
    }

    private var enabledUntil = 0L
    private val sequence = AtomicLong()

    @Synchronized
    fun enableFor(durationMs: Long = 15 * 60 * 1000L): Boolean {
        if (!debuggable || durationMs <= 0) return false
        val now = nowMs()
        enabledUntil = now + durationMs.coerceAtMost(15 * 60 * 1000L)
        return true
    }

    @Synchronized
    fun disable() { enabledUntil = 0L }

    @Synchronized
    fun isEnabled(): Boolean = debuggable && enabledUntil > 0L && nowMs() < enabledUntil

    /** Caller runs file work on IO; name/body cannot become file names or raw retained text. */
    @Synchronized
    fun record(source: String, name: String, body: String): Boolean {
        if (!isEnabled()) return false
        val safeSource = source.takeIf { it in setOf("tronclass", "portal", "jw", "general") } ?: "general"
        val shape = if (body.length <= 64 * 1024) Redaction.responseSummary(body)
            else "<响应过长；长度=${body.length}>"
        val labelShape = if (name.isBlank()) "unnamed" else "event"
        val content = sanitize("source=$safeSource; $labelShape; $shape", maxFileBytes)
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxFileBytes || bytes.size > maxTotalBytes) return false
        return runCatching {
            check(directory.isDirectory || directory.mkdirs())
            prune()
            val file = File.createTempFile("diagnostic-${sequence.incrementAndGet()}-", ".txt", directory)
            try {
                file.writeBytes(bytes)
                file.setLastModified(nowMs())
                prune()
                file.isFile
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }.getOrDefault(false)
    }

    @Synchronized
    fun clear(): Int {
        val files = retainedFiles()
        return files.count { runCatching { it.delete() }.getOrDefault(false) }
    }

    /** Export is summary-only and bounded, even when files are stale or externally modified. */
    @Synchronized
    fun exportSummary(): String {
        if (!debuggable) return ""
        prune()
        return retainedFiles().sortedBy { it.lastModified() }.joinToString("\n") { file ->
            val text = runCatching {
                file.inputStream().use { input ->
                    val buffer = ByteArray(maxFileBytes)
                    val count = input.read(buffer)
                    if (count <= 0) "" else String(buffer, 0, count, Charsets.UTF_8)
                }
            }.getOrDefault("")
            if (STRUCTURAL_LINE.matches(text)) sanitize(text, 256)
            else sanitize(Redaction.responseSummary(text), 256)
        }.take(4096)
    }

    @Synchronized
    fun prune(): Int {
        var removed = 0
        val now = nowMs()
        retainedFiles().forEach { file ->
            val age = now - file.lastModified()
            if (age < 0 || age > retentionMs || file.length() > maxFileBytes) {
                if (runCatching { file.delete() }.getOrDefault(false)) removed++
            }
        }
        val oldestFirst = retainedFiles().sortedBy { it.lastModified() }
        var bytes = oldestFirst.sumOf { it.length() }
        var count = oldestFirst.size
        oldestFirst.forEach { file ->
            if (count > maxFiles || bytes > maxTotalBytes) {
                val size = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    bytes -= size
                    count--
                    removed++
                }
            }
        }
        return removed
    }

    private fun retainedFiles(): List<File> = directory.listFiles().orEmpty().filter {
        it.name.startsWith("diagnostic-") && it.name.endsWith(".txt") && it.isFile && !Files.isSymbolicLink(it.toPath())
    }

    companion object {
        private val STRUCTURAL_LINE = Regex(
            """source=(?:tronclass|portal|jw|general); (?:unnamed|event); <(?:空响应|(?:HTML响应|文本响应|JSON字符串|响应过长)；长度=\d+|JSON数组；条目数=\d+|JSON标量|JSON对象；字段数=\d+；常见字段=(?:无|(?:code|status|success|message|msg|data|result|items|rows)(?:,(?:code|status|success|message|msg|data|result|items|rows))*))>""",
        )

        fun sanitize(value: String, maxLength: Int = 1024): String {
            require(maxLength >= 0)
            return safeRequestText(value, maxLength)
        }

        /** Deletes only direct legacy diagnostics; never opens their bodies or touches private photos. */
        fun clearLegacyRawFiles(filesDir: File): Int = filesDir.listFiles().orEmpty().count { file ->
            val legacy = listOf("tron_raw_", "portal_raw_", "jw_raw_").any { file.name.startsWith(it) }
            legacy && file.name.endsWith(".txt") && file.isFile && !Files.isSymbolicLink(file.toPath()) &&
                runCatching { file.delete() }.getOrDefault(false)
        }
    }
}
