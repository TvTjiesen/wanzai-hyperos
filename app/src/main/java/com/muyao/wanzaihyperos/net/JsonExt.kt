package com.muyao.wanzaihyperos.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
// contentOrNull 是**扩展属性**，必须显式 import，不会跟着 JsonPrimitive 一起进来。
import kotlinx.serialization.json.contentOrNull

/**
 * 信封字段的宽松读取。
 *
 * 学校这套接口的字段类型**不稳定**：`code` 有时是数字 `401`、有时是字符串 `"401"`；
 * `success` 有时是布尔 `false`、有时是字符串 `"false"`。严格反序列化（data class +
 * `@Serializable`）在这种服务端上会直接抛，而且抛在很难定位的地方。
 * 上课时的心态是「能读到就读，读不到走默认」，而不是「类型不完美就失败」。
 *
 * 这些 helper 的意义是把「宽松」集中在一处，**只能宽在这里**：
 * 一旦离开这一层，业务代码拿到的就是强类型值了。
 */

/** 数字或数字字符串 → Int。其它一律 null（不抛异常）。 */
fun JsonElement?.asIntOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    val text = primitive.content.trim()
    return text.toIntOrNull() ?: text.toDoubleOrNull()?.toInt()
}

/** 布尔、数字或数字字符串 → Boolean。其它一律 null。 */
fun JsonElement?.asBoolOrNull(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    return when (primitive.content.trim().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}

/**
 * 文本。**刻意不要求 `isString`**：
 * 服务端有时把 message 写成数字或对象，我们宁可用它的字面量也不让解析失败。
 * `JsonNull` 返回 null（不是字符串 "null"）。
 */
fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

/** 数组；不是数组时 null。 */
fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

/** 对象；不是对象时 null。 */
fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
