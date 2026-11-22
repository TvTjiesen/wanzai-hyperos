package com.muyao.wanzaihyperos.nav

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
@kotlinx.serialization.SerialName("com.muyao.wanzaihyperos.nav.Route.Web")
private data class SavedWebRoute(
    val appId: Int,
    val title: String,
    val target: WebTarget = WebTarget.blocked(),
    // Decode the former field for schema compatibility, but never reuse it or write it back.
    val url: String? = null,
)

object WebRouteSerializer : KSerializer<Route.Web> {
    override val descriptor: SerialDescriptor = SavedWebRoute.serializer().descriptor
    override fun serialize(encoder: Encoder, value: Route.Web) {
        encoder.encodeSerializableValue(SavedWebRoute.serializer(), SavedWebRoute(value.appId, value.title, value.target))
    }
    override fun deserialize(decoder: Decoder): Route.Web {
        val saved = decoder.decodeSerializableValue(SavedWebRoute.serializer())
        return Route.Web(saved.appId, saved.title, if (saved.url != null) WebTarget.blocked() else saved.target)
    }
}
