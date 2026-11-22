package com.muyao.wanzaihyperos.data

import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A portable theme contains visual choices only; importing always produces a draft. */
object AppearanceThemePackage {
    private const val LIMIT = 65536
    // 旧版主题包会带已移除的字段（如贴纸 stickers）；读入时忽略未知键，宁可少一项也不能整包作废。
    private val json = Json { ignoreUnknownKeys = true }
    @Serializable private data class Package(val format: String = "qihe-theme", val version: Int = 1, val appearance: AppearanceSettings)

    fun write(output: OutputStream, settings: AppearanceSettings) {
        val bytes = json.encodeToString(Package(appearance = settings.validated())).toByteArray(Charsets.UTF_8)
        require(bytes.size <= LIMIT)
        output.write(bytes)
    }

    fun read(input: InputStream): AppearanceSettings {
        val buffer = java.io.ByteArrayOutputStream()
        val block = ByteArray(4096)
        while (true) {
            val count = input.read(block)
            if (count < 0) break
            require(buffer.size() + count <= LIMIT) { "Theme package exceeds 64 KiB" }
            buffer.write(block, 0, count)
        }
        val bytes = buffer.toByteArray()
        val value = json.decodeFromString<Package>(bytes.toString(Charsets.UTF_8))
        require(value.format == "qihe-theme" && value.version == 1) { "Unsupported theme package" }
        return value.appearance.validated()
    }
}
