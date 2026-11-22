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

    /**
     * 账号密码被拒**必须自己说出来**，不能压成和网络失败同一句。
     *
     * 2026-11-22 真机：教务服务器明确回「用户名或密码不正确」，而这一行只写
     * 「更新未成功 · 暂用已有内容 · 11-22 12:59」—— 用户看不到原因，
     * 只能反复点刷新，最后把学校 CAS 的验证码敲了出来。
     */
    @Test fun aRejectedCredentialSaysWhatToFix() {
        val rejected = com.muyao.wanzaihyperos.net.HzcuException.LoginExpired("账号或密码不对")
        assertEquals(
            "暂未更新成功 · 账号或密码不对，去教务那页重新登录",
            dataFreshnessLabel(ListMeta(lastRefreshError = rejected), utc),
        )
        assertEquals(
            "更新未成功 · 账号或密码不对，去教务那页重新登录 · 暂用已有内容 · 01-01 00:00",
            dataFreshnessLabel(ListMeta(updatedAtMillis = 0L, lastRefreshError = rejected), utc),
        )
        // 认证被拒（统一的 CAS 语义）也要走同一条：两者对用户是同一件事。
        val cas = com.muyao.wanzaihyperos.net.HzcuException.AuthenticationRejected("密码错误")
        assertEquals(
            "暂未更新成功 · 账号或密码不对，去教务那页重新登录",
            dataFreshnessLabel(ListMeta(lastRefreshError = cas), utc),
        )
    }

    /** 网络类失败**不许**冒充成账号问题 —— 那会让用户白跑一趟去改密码。 */
    @Test fun aNetworkFailureDoesNotBlameTheCredential() {
        val text = dataFreshnessLabel(
            ListMeta(updatedAtMillis = 0L, lastRefreshError = com.muyao.wanzaihyperos.net.HzcuException.RequestTimeout()),
            utc,
        )
        assertFalse(text, text.contains("账号或密码"))
    }
}
