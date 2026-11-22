package com.muyao.wanzaihyperos.data

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class DataFreshnessTest {
    private val utc = ZoneId.of("UTC")
    @Test fun successfulFetchIsNotCalledPublicationTime() {
        assertEquals("上次检查成功 · 01-01 00:00", dataFreshnessLabel(ListMeta(updatedAtMillis = 0L), utc))
    }
    @Test fun initialAndCachedFailuresRemainDistinct() {
        assertEquals("暂未更新成功", dataFreshnessLabel(ListMeta(lastRefreshError = com.muyao.wanzaihyperos.net.HzcuException.Transport()), utc))
        assertEquals("更新未成功 · 暂用已有内容 · 01-01 00:00", dataFreshnessLabel(ListMeta(updatedAtMillis = 0L, lastRefreshError = com.muyao.wanzaihyperos.net.HzcuException.Transport()), utc))
    }
    @Test fun onlyAnActualRequestClaimsToBeUpdating() {
        assertEquals("本地内容 · 更新时间未知", dataFreshnessLabel(ListMeta(stale = true), utc))
        assertEquals("正在检查更新", dataFreshnessLabel(ListMeta(refreshing = true), utc))
    }
}
