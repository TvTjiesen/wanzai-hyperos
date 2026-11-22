package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Official CourseEnrollments page: /api/course/{id}/enrollments, not /students. */
data class TronMember(
    val enrollmentId: Int,
    val userId: Int,
    val name: String,
    val userNo: String,
    val roles: List<String>,
    val aliases: List<String>,
    val department: String,
    val grade: String,
    val klass: String,
    val auditor: Boolean,
)

object TronMembersParser {
    fun parse(root: JsonObject): List<TronMember> {
        val rows = root["enrollments"].asArrayOrNull()
            ?: throw HzcuException.Parse("成员响应缺少 enrollments 列表")
        return rows.map { row ->
            val obj = row.asObjectOrNull() ?: throw HzcuException.Parse("成员条目结构异常")
            val user = obj["user"].asObjectOrNull() ?: throw HzcuException.Parse("成员条目缺少用户资料")
            fun strings(key: String): List<String> {
                val element = obj[key]
                // 缺字段与明确 null 都算「这栏没有」；其它不是数组的形态才是结构异常。
                if (element == null || element is JsonNull) return emptyList()
                val array = element.asArrayOrNull() ?: throw HzcuException.Parse("成员角色结构异常")
                return array.mapNotNull { item: JsonElement ->
                    when (item) {
                        is JsonNull -> null
                        // 服务端会把昵称/角色写成空串：空串是「没有这一项」，不是坏结构。
                        is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }
                        else -> throw HzcuException.Parse("成员角色结构异常")
                    }
                }
            }
            fun label(key: String): String = user[key].asObjectOrNull()?.get("name").asStringOrNull()
                ?: user[key].asStringOrNull().orEmpty()
            TronMember(
                enrollmentId = obj["id"].asIntOrNull()?.takeIf { it > 0 } ?: throw HzcuException.Parse("成员缺少选课标识"),
                userId = user["id"].asIntOrNull()?.takeIf { it > 0 } ?: throw HzcuException.Parse("成员缺少用户标识"),
                name = user["name"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: throw HzcuException.Parse("成员缺少姓名"),
                userNo = user["user_no"].asStringOrNull().orEmpty(),
                roles = strings("roles"), aliases = strings("aliases"),
                department = label("department"), grade = label("grade"), klass = label("klass"),
                auditor = obj["is_auditor"].asBoolOrNull() == true,
            )
        }.distinctBy { it.enrollmentId }
    }
}
