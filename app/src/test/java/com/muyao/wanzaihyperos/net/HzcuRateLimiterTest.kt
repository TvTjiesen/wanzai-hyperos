package com.muyao.wanzaihyperos.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 限流器是**对学校服务器的承诺**，必须用测试钉死。
 *
 * 「全局 ≥1 秒、同接口 ≥2 秒、严格串行」这三条如果只写在注释里，
 * 将来任何人加一条新的网络路径都不会注意到它。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HzcuRateLimiterTest {

    /**
     * 用虚拟时钟的限流器：`runTest` 里的 `delay` 是虚拟时间，
     * 所以这些测试**不会真的等上几秒**，但断言的是真实的间隔逻辑。
     */
    private fun TestLimiter(scope: kotlinx.coroutines.test.TestScope) = HzcuRateLimiter.forTesting(
        clock = { scope.testScheduler.currentTime },
        sleeper = { delay(it) },
    )

    @Test
    fun `不同接口之间至少间隔 1 秒`() = runTest {
        val limiter = TestLimiter(this)
        val starts = mutableListOf<Long>()
        listOf("/a", "/b", "/c").forEach { path ->
            limiter.gate(path) { starts += testScheduler.currentTime }
        }
        assertEquals(listOf(0L, 1_000L, 2_000L), starts)
    }

    @Test
    fun `同一接口至少间隔 2 秒`() = runTest {
        val limiter = TestLimiter(this)
        val starts = mutableListOf<Long>()
        repeat(3) {
            limiter.gate("/api/app/x") { starts += testScheduler.currentTime }
        }
        // 同路径的 2 秒比全局的 1 秒更严，所以取 2 秒。
        assertEquals(listOf(0L, 2_000L, 4_000L), starts)
    }

    @Test
    fun `并发调用被串行化而不是并行`() = runTest {
        val limiter = TestLimiter(this)
        var inFlight = 0
        var maxInFlight = 0
        val jobs = (1..4).map { index ->
            launch {
                limiter.gate("/api/app/$index") {
                    inFlight++
                    maxInFlight = maxOf(maxInFlight, inFlight)
                    delay(50)
                    inFlight--
                }
            }
        }
        jobs.forEach { it.join() }
        assertEquals("同一时刻最多只能有一个请求在飞", 1, maxInFlight)
    }

    @Test
    fun `某次请求抛异常不会把队伍卡死`() = runTest {
        val limiter = TestLimiter(this)
        val ran = mutableListOf<String>()

        runCatching { limiter.gate("/a") { throw IllegalStateException("boom") } }
        // 后面这个必须还能跑起来；如果 finally 漏了 complete，这里会永久挂住。
        limiter.gate("/b") { ran += "b" }

        assertEquals(listOf("b"), ran)
        assertEquals(0, limiter.pendingCount)
    }

    @Test
    fun `排队中的请求数会被正确统计`() = runTest {
        val limiter = TestLimiter(this)
        val seen = mutableListOf<Int>()
        val jobs = (1..3).map { index ->
            launch {
                limiter.gate("/api/app/$index") {
                    // 记下「此刻（含自己）有多少个请求在排队/在飞」。
                    seen += limiter.pendingCount
                }
            }
        }
        jobs.forEach { it.join() }
        // 第一个进来时后面还没启动，所以第一个看到的可能是 1。
        assertTrue("至少能看到有请求排队", seen.any { it >= 2 })
        assertEquals(0, limiter.pendingCount)
    }

    @Test
    fun `reset 之后回到第一次请求的状态`() = runTest {
        val limiter = TestLimiter(this)
        limiter.gate("/a") { }
        limiter.reset()
        val starts = mutableListOf<Long>()
        limiter.gate("/a") { starts += testScheduler.currentTime }
        assertEquals(listOf(0L), starts)
    }

    @Test
    fun `生产红线常量就是 1 秒与 2 秒`() {
        // 这两条是对学校服务器的承诺，改小就是违规，所以钉死在这里。
        assertEquals(1_000L, HzcuRateLimiter.MIN_INTERVAL_MS)
        assertEquals(2_000L, HzcuRateLimiter.PER_PATH_MIN_INTERVAL_MS)
    }
}
