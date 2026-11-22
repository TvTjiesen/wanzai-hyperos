package com.muyao.wanzaihyperos.nav

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Legacy native fallback fields have the same boundary as WebTarget, in both directions. */
object SafeTileUrlSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SafeTileUrl", PrimitiveKind.STRING)
    private fun safe(raw: String): String {
        val target = WebTarget.fromUrl(raw)
        return when (target.kind) {
            WebTarget.Kind.DIRECT, WebTarget.Kind.CJH52 -> target.address
            else -> ""
        }
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(safe(value))
    override fun deserialize(decoder: Decoder): String = safe(decoder.decodeString())
}
