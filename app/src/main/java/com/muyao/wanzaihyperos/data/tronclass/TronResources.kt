package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.*
import kotlinx.serialization.json.*

/** Only the current user's uploads domain; shared resources use a different contract. */
data class TronResource(
    val id: Int,
    val name: String,
    val type: String,
    val folder: Boolean,
    val size: Long?,
    val createdAt: String,
)

data class TronResourcesQuery(val folderId: Int = 0, val keyword: String = "") {
    init { require(folderId >= 0) }
    fun conditions(folders: Boolean): JsonObject = buildJsonObject {
        put("keyword", keyword)
        put("includeSlides", false)
        put("limitTypes", JsonArray((if (folders) listOf("folder") else listOf("file", "video", "document", "image", "audio", "scorm", "swf", "wmpkg", "link")).map(::JsonPrimitive)))
        put("fileType", "all")
        put("parentId", folderId)
        put("sourceType", if (folders) "MyResourcesFolder" else "MyResourcesFile")
        put("sortPredicate", "created_at")
        put("reverse", true)
        put("no-intercept", true)
    }
}

data class TronResourcesPageData(val uploads: List<TronResource>, val page: Int, val pages: Int, val pageSize: Int?) {
    val hasMore: Boolean get() = page < pages
}

object TronResourcesParser {
    fun parse(root: JsonObject): TronResourcesPageData {
        val rows = root["uploads"].asArrayOrNull() ?: throw HzcuException.Parse("资源响应缺少 uploads 列表")
        val page = root["page"].asIntOrNull()?.takeIf { it > 0 } ?: throw HzcuException.Parse("资源分页结构异常")
        val pages = root["pages"].asIntOrNull()?.takeIf { it >= 0 } ?: throw HzcuException.Parse("资源分页结构异常")
        // 空响应实测不带 page_size（2026-11-22 真机捕获 `{"page":1,"pages":1,"uploads":[]}`）；缺省即未知，不判结构异常。
        val pageSize = root["page_size"]?.takeIf { it !is JsonNull }?.let { raw ->
            raw.asIntOrNull()?.takeIf { it > 0 } ?: throw HzcuException.Parse("资源分页结构异常")
        }
        val uploads = rows.map { row ->
            val obj = row.asObjectOrNull() ?: throw HzcuException.Parse("资源条目结构异常")
            val type = obj["type"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: throw HzcuException.Parse("资源缺少类型")
            val size = (obj["size"] as? JsonPrimitive)?.longOrNull
            if (size != null && size < 0) throw HzcuException.Parse("资源大小异常")
            TronResource(
                id = obj["id"].asIntOrNull()?.takeIf { it > 0 } ?: throw HzcuException.Parse("资源缺少标识"),
                name = obj["name"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: throw HzcuException.Parse("资源缺少名称"),
                type = type, folder = type == "folder" || obj["is_folder"].asBoolOrNull() == true,
                size = size, createdAt = obj["created_at"].asStringOrNull().orEmpty(),
            )
        }
        if (uploads.isNotEmpty() && (pages == 0 || page > pages)) throw HzcuException.Parse("资源分页与条目不一致")
        return TronResourcesPageData(uploads, page, pages, pageSize)
    }

    /** Mirrors the official folder-first service, including its per-page limit. */
    fun merge(folders: TronResourcesPageData, files: TronResourcesPageData): TronResourcesPageData {
        val size = listOfNotNull(folders.pageSize, files.pageSize).maxOrNull()
        val rows = (folders.uploads.filter { it.folder } + files.uploads).distinctBy { it.id }
        return TronResourcesPageData(
            if (size != null) rows.take(size) else rows,
            maxOf(folders.page, files.page), maxOf(folders.pages, files.pages), size,
        )
    }
}
