package com.muyao.wanzaihyperos.net

import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 城院接口的全局限流器。**全项目唯一一份实现。**
 *
 * 为什么必须是单例：学校服务器很脆，打挂了业主会被信息化办找上门（计划 §T3.2、§12.1）。
 * 只要限流状态是「每个实例自己一份」，就总会有第二条代码路径 new 一个客户端绕过去。
 * 所以生产入口只有 [instance]（构造函数私有），
 * 唯一另一个入口 [forTesting] 标了 `@VisibleForTesting`。
 * **生产代码里再造一个限流器 = 绕过全局限流，review 时应当直接否掉。**
 *
 * 语义（移植自 Flutter 蓝本 `lib/http/hzcu/hzcu_rate_limiter.dart`）：
 * - **全局 QPS ≤ 1**：任意两次「请求开始」之间至少间隔 [MIN_INTERVAL_MS]；
 * - **同一路径最小间隔 2s**：同一个接口打得太密，即使全局没超也要再等；
 * - **排队而非丢弃**：并发调用按到达顺序串行穿过闸门。调用方看到的效果是
 *   「请求变慢」，绝不会是「请求变多」，也绝不会静默丢请求。
 *
 * 这里刻意**不用**令牌桶：容量 1 的令牌桶在空闲后会攒出突发额度，
 * 而「严格串行 + 最小间隔」不允许任何突发，是对 QPS≤1 更强的保证。
 *
 * ### 与蓝本的一处等价改写
 * 蓝本用一条 `Future` 链（`_tail`）串行化，这里用 [Mutex]。
 * 语义相同（严格串行、异常不卡队伍、`finally` 一定放行），
 * 但 Mutex 在取消（协程被取消 / 页面销毁）时能正确释放锁，
 * 而 Future 链在 Dart 里靠手动 `complete()` 兜底。Android 上页面频繁销毁重建，
 * 用 Mutex 更不容易留下「永久卡死」的状态。
 */
class HzcuRateLimiter private constructor(
    private val minIntervalMs: Long,
    private val perPathMinIntervalMs: Long,
    private val clock: () -> Long,
    private val sleeper: suspend (Long) -> Unit,
) {

    companion object {
        /** 硬红线，**不许调小**。轮询类任务另有 ≥15min 的间隔约束，不在此处。 */
        const val MIN_INTERVAL_MS = 1_000L

        /** 同接口最小间隔。 */
        const val PER_PATH_MIN_INTERVAL_MS = 2_000L

        /**
         * 补齐间隔最多重试的轮数。
         *
         * 为什么不是 sleep 一次就完事：`delay` 只保证「不早于」、不保证「足量」，
         * 也可能被系统合并计时器提前唤醒。单次 sleep 完全可能让实际间隔停在 999ms
         * —— 对红线来说那就是违规。所以按「还差多少」继续补等：
         * **宁可多等一毫秒，不可少等一毫秒**。
         * 但最多补这么多轮，防止某种时钟异常把请求永远卡住
         * （卡死比偶发差 1ms 严重得多）。
         */
        private const val MAX_SPACING_ROUNDS = 8

        @Volatile
        private var shared: HzcuRateLimiter? = null

        /** 全项目唯一的限流闸门。 */
        val instance: HzcuRateLimiter
            get() = shared ?: synchronized(this) {
                shared ?: HzcuRateLimiter(
                    minIntervalMs = MIN_INTERVAL_MS,
                    perPathMinIntervalMs = PER_PATH_MIN_INTERVAL_MS,
                    clock = { monotonicMillis() },
                    sleeper = { delay(it) },
                ).also { shared = it }
            }

        /**
         * 仅供测试注入替身时钟 / 更短间隔，免得单测真等一秒。
         *
         * 默认时钟是**单调钟**（[System.nanoTime]）而不是 `System.currentTimeMillis()`：
         * 墙上时钟会被 NTP 校准往回跳，一旦回跳，间隔算出来是负数，
         * 闸门会误判「已经等够了」而放行 —— 正好在红线最该守住的时刻失效。
         */
        @VisibleForTesting
        fun forTesting(
            minIntervalMs: Long = MIN_INTERVAL_MS,
            perPathMinIntervalMs: Long = PER_PATH_MIN_INTERVAL_MS,
            clock: () -> Long = { monotonicMillis() },
            sleeper: suspend (Long) -> Unit = { delay(it) },
        ): HzcuRateLimiter = HzcuRateLimiter(minIntervalMs, perPathMinIntervalMs, clock, sleeper)

        private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
    }

    private val mutex = Mutex()
    private var lastStartMs: Long? = null
    private val lastStartByPath = mutableMapOf<String, Long>()
    private val pending = AtomicInteger(0)

    /** 当前排队中的请求数（含正在飞的那个）。用于测试与诊断。 */
    val pendingCount: Int get() = pending.get()

    /**
     * 穿过闸门执行 [action]。
     *
     * [path] 用于「同接口最小间隔」判定，传请求路径即可
     * （见 [HzcuRequest.rateLimitKey]，它已经剥掉了 `;jsessionid`）。
     *
     * 异常原样透传给调用方（限流器**不吞错误**），但一定会放行后面的请求，
     * 不会因为某次失败把队伍卡死 —— 靠的是 `Mutex.withLock` 的 `finally`。
     *
     * 注意 [action] 在锁**内**执行：这正是「严格串行」的含义 ——
     * 同一时刻最多只有一个城院请求在飞，而不是「间隔够就行」。
     */
    suspend fun <T> gate(path: String, action: suspend () -> T): T {
        pending.incrementAndGet()
        try {
            return mutex.withLock {
                awaitSpacing(path)
                val start = clock()
                lastStartMs = start
                lastStartByPath[path] = start
                action()
            }
        } finally {
            pending.decrementAndGet()
        }
    }

    /** 还需要等多久（毫秒）。返回 0 表示不用等（本进程第一次请求，或已等够）。 */
    private fun requiredWaitMs(path: String): Long {
        val now = clock()
        var longest = 0L
        lastStartMs?.let { longest = maxOf(longest, it + minIntervalMs - now) }
        lastStartByPath[path]?.let { longest = maxOf(longest, it + perPathMinIntervalMs - now) }
        return longest
    }

    /** 补齐间隔，直到「真的够了」为止。见 [MAX_SPACING_ROUNDS] 的说明。 */
    private suspend fun awaitSpacing(path: String) {
        repeat(MAX_SPACING_ROUNDS) {
            val wait = requiredWaitMs(path)
            if (wait <= 0L) return
            sleeper(wait)
        }
    }

    /** 测试用：清掉历史，回到「本进程第一次请求」的状态。 */
    @VisibleForTesting
    fun reset() {
        lastStartMs = null
        lastStartByPath.clear()
        pending.set(0)
    }
}
