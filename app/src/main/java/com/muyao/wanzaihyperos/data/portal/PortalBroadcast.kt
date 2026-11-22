package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.HzcuException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

// 字段来自保存的学校前端；不把结构未知的响应解释成“没有公告”。
data class PortalBroadcastItem(
    val id: String,
    val title: String,
    val description: String,
    val sendTime: String,
)

data class PortalBroadcastDetail(val item: PortalBroadcastItem, val content: String)

object PortalBroadcastParser {
    fun parseList(envelope: JsonObject): List<PortalBroadcastItem> {
        val rows = envelope["rows"] as? JsonArray
            ?: throw HzcuException.Parse("公告列表格式尚未识别")
        return rows.map { element ->
            val row = element as? JsonObject ?: throw HzcuException.Parse("公告条目格式尚未识别")
            val id = (row["id"] as? JsonPrimitive)
                ?.takeIf { it.isString || it.longOrNull != null }?.contentOrNull.orEmpty()
            val title = text(row, "title")
            if (HzcuEndpoints.portalBroadcastDetailPath(id) == null || title.isBlank())
                throw HzcuException.Parse("公告缺少有效标识或标题")
            PortalBroadcastItem(id, title, text(row, "describe"), text(row, "sendtime"))
        }.distinctBy { it.id }
    }

    fun parseDetail(envelope: JsonObject, item: PortalBroadcastItem): PortalBroadcastDetail {
        val data = envelope["data"] as? JsonObject
            ?: throw HzcuException.Parse("公告正文格式尚未识别")
        val content = data["content"] as? JsonPrimitive
        if (content == null || !content.isString) throw HzcuException.Parse("公告正文尚未提供")
        return PortalBroadcastDetail(item, content.content)
    }

    fun plainText(content: String): String {
        val withoutActiveBlocks = content.replace(
            Regex("<\\s*(script|style)\\b[^>]*>[\\s\\S]*?<\\s*/\\s*\\1\\s*>", RegexOption.IGNORE_CASE), "",
        )
        return com.muyao.wanzaihyperos.data.tronclass.htmlToPlainText(withoutActiveBlocks)
    }

    private fun text(row: JsonObject, key: String): String =
        (row[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
}
