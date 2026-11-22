package com.muyao.wanzaihyperos.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** This timestamp describes a successful fetch, never the publication date of its records. */
fun dataFreshnessLabel(meta: ListMeta, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = meta.updatedAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }
    return when {
        meta.refreshing -> if (time == null) "正在检查更新" else "正在更新 · 保留已有内容"
        meta.lastRefreshError != null -> if (time == null) "暂未更新成功" else "更新未成功 · 暂用已有内容 · $time"
        meta.stale -> if (time == null) "本地内容 · 更新时间未知" else "本地内容 · 上次更新 $time"
        time != null -> "上次检查成功 · $time"
        else -> "更新时间未知"
    }
}
