package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.*
import org.junit.Test

class TimetableRefreshPolicyTest {
    private val now = 50_000_000L

    @Test fun freshCachedContentAndConfirmedEmptyAvoidNetwork() {
        val meta = ListMeta(updatedAtMillis = now - 1000, stale = true)
        assertFalse(shouldRefreshTimetable(ListState.Content(listOf("cached"), meta), now))
        assertFalse(shouldRefreshTimetable(ListState.Empty(meta), now))
    }

    @Test fun expiredCacheRefreshesAtTheBoundary() {
        val meta = ListMeta(updatedAtMillis = now - TIMETABLE_REFRESH_INTERVAL_MS)
        assertTrue(shouldRefreshTimetable(ListState.Content(listOf("cached"), meta), now))
        assertFalse(shouldRefreshTimetable(ListState.Content(listOf("cached"), meta), now - 1))
    }

    @Test fun missingTimeAndClockRollbackRequestAnUpdate() {
        assertTrue(shouldRefreshTimetable(ListState.FirstLoad, now))
        assertTrue(shouldRefreshTimetable(ListState.Empty(), now))
        assertTrue(shouldRefreshTimetable(ListState.Empty(ListMeta(updatedAtMillis = now + 1)), now))
    }

    @Test fun anExistingRefreshIsNotDuplicated() {
        assertFalse(shouldRefreshTimetable(ListState.Empty(ListMeta(refreshing = true)), now))
    }

    /**
     * 账号密码被明确拒绝后，**自动刷新必须停**。
     *
     * 2026-11-22 真机事故：教务那份的 15 分钟循环一直拿同一对被服务器拒绝的账号密码
     * 反复登录，把学校 CAS 的验证码敲了出来（`For security reasons, please input the captcha`）。
     * 自动循环再发一次不会变对，只会继续累积失败次数。
     */
    @Test fun aRejectedCredentialStopsTheAutomaticLoop() {
        val rejected = ListMeta(
            // 缓存已经过期很久了 —— 单看时间这一条，旧逻辑会说「该刷了」。
            updatedAtMillis = now - TIMETABLE_REFRESH_INTERVAL_MS * 10,
            stale = true,
            lastRefreshError = HzcuException.LoginExpired("账号或密码不对"),
        )
        assertFalse(
            "凭据被拒后不该被自动重发",
            shouldRefreshTimetable(ListState.Content(listOf("cached"), rejected), now),
        )
        assertFalse(
            shouldRefreshTimetable(ListState.Empty(rejected), now),
        )
        // 没有旧数据时凭据被拒会降级成 SessionExpired（没有 meta），这一支也要挡住。
        assertFalse(shouldRefreshTimetable(ListState.SessionExpired, now))
        // CAS 语义的认证被拒同理。
        assertFalse(
            shouldRefreshTimetable(
                ListState.Content(listOf("cached"), rejected.copy(
                    lastRefreshError = HzcuException.AuthenticationRejected("密码错误"),
                )),
                now,
            ),
        )
    }

    /** 网络类失败**不该**停掉自动刷新：它下一次可能就好了。 */
    @Test fun aNetworkFailureStillAllowsTheNextAutomaticAttempt() {
        val timedOut = ListMeta(
            updatedAtMillis = now - TIMETABLE_REFRESH_INTERVAL_MS,
            stale = true,
            lastRefreshError = HzcuException.RequestTimeout(),
        )
        assertTrue(shouldRefreshTimetable(ListState.Content(listOf("cached"), timedOut), now))
    }
}
