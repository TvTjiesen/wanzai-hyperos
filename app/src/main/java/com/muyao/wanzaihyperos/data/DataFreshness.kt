package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.net.isCredentialRejected
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * This timestamp describes a successful fetch, never the publication date of its records.
 *
 * ★ **账号密码被拒要单独说出来**（2026-11-22 真机事故）：
 *   以前所有失败都压成同一句「更新未成功 · 暂用已有内容 · 11-22 12:59」。
 *   真机上教务服务器明确回「用户名或密码不正确」，界面却一句原因都没有 ——
 *   用户只能看到「更新未成功」，于是不停点刷新（而客户端当时还每 15 分钟自动重试一次），
 *   最后把学校 CAS 的验证码敲了出来。**失败原因里有一类要用户去做事的（改密码），
 *   把它和「网络抖了一下」说成同一句话，就是把该他处理的信号吞掉了。**
 *
 *   其余错误仍然不在这里展开：网络类失败下面就有「刷新」按钮，
 *   而把「HTTP 500」这类实现细节糊在时间行上只会让人更糊涂。
 *   界面不许说数据证明不了的话，也不该说用户处理不了的话。
 */
fun dataFreshnessLabel(meta: ListMeta, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = meta.updatedAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }
    val rejected = meta.lastRefreshError.isCredentialRejected
    return when {
        meta.refreshing -> if (time == null) "正在检查更新" else "正在更新 · 保留已有内容"
        meta.lastRefreshError != null -> {
            val reason = if (rejected) " · 账号或密码不对，去教务那页重新登录" else ""
            if (time == null) "暂未更新成功$reason" else "更新未成功$reason · 暂用已有内容 · $time"
        }
        meta.stale -> if (time == null) "本地内容 · 更新时间未知" else "本地内容 · 上次更新 $time"
        time != null -> "上次检查成功 · $time"
        else -> "更新时间未知"
    }
}
