package com.muyao.wanzaihyperos.session

import com.muyao.wanzaihyperos.net.HzcuException

/**
 * 会话的三态。
 *
 * ★ **为什么不只是 `Boolean`**（这是从 Flutter 蓝本继承来的教训）：
 * 「还没读出来」与「读出来了，确实没登录」是两件完全不同的事。
 * 用 `Boolean` 表示时二者都是 `false`，界面上就变成**把「未知」显示成「未登录」**
 * —— 用户明明登录着，却因为一次存储读取没完成被踢到登录页；
 * 或者反过来，网络不通时未读数显示成 `0`，替服务端撒了谎。
 * 方案对此有明确红线：**未知不得当成 0，网络失败不得判成登出。**
 */
enum class SessionState {
    /** 还没读存储（或存储读取失败）。**既不是已登录，也不是未登录。** */
    Unknown,

    /** 读到了，确实没有凭据。 */
    LoggedOut,

    /** 读到了，有凭据。 */
    LoggedIn,
}

/**
 * 「存在加密存储里的一段会话」的公共骨架。
 *
 * 两个子类（[PortalSession] / [TronclassSession]）共用：
 * - **懒加载**：首次访问才读存储，之后走内存；并发首次访问由 [lock] 串行化，
 *   不会读到「还没加载完」的 null 而误判成未登录；
 * - **读失败降级**：密钥失效 / root ROM 异常时**当作 [SessionState.Unknown]**，
 *   而不是 LoggedOut（见 [SessionState] 的说明），异常本身不抛到界面上；
 * - **监听通知**：登录态变化时通知首页宫格与未读角标一起刷。
 *
 * 子类只负责三件事：怎么从存储读（[loadFrom]）、怎么清内存（[clearMemory]）、
 * 以及「什么算有凭据」（[hasCredential]）。
 */
abstract class StoredSession(protected val store: SecureStore) {

    protected val lock = Any()

    @Volatile
    protected var loaded: Boolean = false

    @Volatile
    private var loadFailed: Boolean = false

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /**
     * 当前登录态。**不会触发加载**（见 [SessionState] 的说明）——
     * 界面拿它渲染前先确保已经 [ensureLoaded] 过一次（App 启动时预热）。
     */
    val state: SessionState
        get() = when {
            !loaded || loadFailed -> SessionState.Unknown
            hasCredential() -> SessionState.LoggedIn
            else -> SessionState.LoggedOut
        }

    /** 子类：当前内存里算不算「有凭据」。 */
    protected abstract fun hasCredential(): Boolean

    /** 子类：从存储把字段读进内存。可能抛 [HzcuException.Storage]。 */
    protected abstract fun loadFrom(store: SecureStore)

    /** 子类：只清内存，不动存储。 */
    protected abstract fun clearMemory()

    /**
     * 确保存储已读过一次。**幂等**；首次调用会同步碰加密存储，
     * 所以请在后台线程/协程里预热（见 `SessionWarmup`），别在首帧调。
     */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            try {
                loadFrom(store)
                loadFailed = false
            } catch (error: HzcuException.Storage) {
                // 只留错误码，绝不回显存储值；降级成 Unknown 由上层引导重新登录。
                clearMemory()
                loadFailed = true
                lastStorageError = error
            }
            loaded = true
        }
    }

    /** 最近一次存储读取失败的原因（只含 op + 错误码）。成功时为 null。 */
    @Volatile
    var lastStorageError: HzcuException.Storage? = null
        protected set

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** 通知所有监听者。单个监听者抛异常不影响其它监听者（UI 崩一个不该拖累全局）。 */
    protected fun notifyListeners() {
        for (listener in listeners) {
            runCatching { listener() }
        }
    }

    /** 标记「已加载且成功」，供子类的 save/clear 复用。 */
    protected fun markLoaded() {
        loaded = true
        loadFailed = false
        lastStorageError = null
    }

    /** 不落盘的凭据一律走这里描述，**禁止把值拼进任何字符串**。 */
    protected fun describe(value: String?): String =
        if (value == null) "<无>" else "<长度 ${value.length}>"
}
