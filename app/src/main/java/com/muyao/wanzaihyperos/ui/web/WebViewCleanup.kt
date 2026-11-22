package com.muyao.wanzaihyperos.ui.web

import android.content.Context
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Pure registry: identity replacement, scoped close, and rejection throughout cookie cleanup. */
internal class CleanupRegistry {
    private data class Entry(val stop: () -> Unit)
    private val entries = IdentityHashMap<Any, Entry>()
    private var clearing = false

    @Synchronized
    fun register(owner: Any, stop: () -> Unit): AutoCloseable {
        val entry = Entry(stop)
        if (clearing) stop() else entries[owner] = entry
        return AutoCloseable {
            synchronized(this) { if (entries[owner] === entry) entries.remove(owner) }
        }
    }

    @Synchronized
    fun beginClear(): List<Throwable> {
        check(!clearing)
        clearing = true
        val snapshot = entries.values.toList()
        entries.clear()
        return snapshot.mapNotNull { entry ->
            try { entry.stop(); null } catch (error: Throwable) { error }
        }
    }

    @Synchronized
    fun endClear() { clearing = false }

    suspend fun clear(action: suspend () -> Unit): Unit = withContext(NonCancellable) {
        val errors = beginClear().toMutableList()
        try {
            try { action() } catch (error: Throwable) { errors += error }
        } finally {
            endClear()
        }
        errors.firstOrNull()?.let { first ->
            errors.drop(1).filter { it !== first }.forEach(first::addSuppressed)
            throw first
        }
        Unit
    }
}

/** Cancels complete engine chains, including delays and nested eval calls after a reattach. */
internal class CleanupWorkSet {
    private class Ticket(val owner: CleanupWorkSet, val generation: Long) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Ticket>
    }
    private val jobs = mutableSetOf<Job>()
    private var generation = 0L

    fun cancelAll() {
        val pending = synchronized(this) {
            generation++
            jobs.toList().also { jobs.clear() }
        }
        pending.forEach { it.cancel(CancellationException("WebView account boundary")) }
    }

    suspend fun <T> run(action: suspend () -> T): T = coroutineScope {
        val context = currentCoroutineContext()
        val inherited = context[Ticket]?.takeIf { it.owner === this@CleanupWorkSet }
        val job = context[Job]!!
        val ticket = synchronized(this@CleanupWorkSet) {
            if (inherited != null && inherited.generation != generation)
                throw CancellationException("WebView account boundary")
            jobs += job
            inherited ?: Ticket(this@CleanupWorkSet, generation)
        }
        try {
            withContext(ticket) {
                currentCoroutineContext().ensureActive()
                action().also { currentCoroutineContext().ensureActive() }
            }
        } finally {
            synchronized(this@CleanupWorkSet) { jobs.remove(job) }
        }
    }
}

/** Stops live views before deleting global WebView storage; disposal alone owns destroy(). */
object WebViewCleanup {
    private val registry = CleanupRegistry()
    private val clearing = Mutex()
    private val stoppedViews = java.util.WeakHashMap<WebView, Boolean>()

    fun isStopped(view: WebView): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        return stoppedViews.containsKey(view)
    }

    fun register(owner: Any, stop: () -> Unit): AutoCloseable {
        check(Looper.myLooper() == Looper.getMainLooper())
        return registry.register(owner, stop)
    }

    fun stop(view: WebView, permanent: Boolean = true) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (stoppedViews[view] == true) return
        if (permanent) stoppedViews[view] = false
        val actions: List<() -> Unit> = listOf(
            { view.stopLoading() },
            { view.onPause() },
            { view.settings.javaScriptEnabled = false },
            { view.settings.blockNetworkLoads = true },
            { view.webChromeClient = null },
            { view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                @Deprecated("Legacy WebView callback")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                    WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            } },
            { view.loadUrl("about:blank") },
            { view.clearHistory() },
            { view.clearFormData() },
        )
        val errors = actions.mapNotNull { action ->
            try { action(); null } catch (error: Throwable) { error }
        }
        errors.firstOrNull()?.let { first ->
            errors.drop(1).filter { it !== first }.forEach(first::addSuppressed)
            throw first
        }
        if (permanent) stoppedViews[view] = true
    }

    /** Bounded, non-cancellable security cleanup, even when the caller is being disposed. */
    suspend fun clearAll(context: Context): Unit = withContext(NonCancellable + Dispatchers.Main) {
        clearing.withLock {
            registry.clear {
                val errors = mutableListOf<Throwable>()
                fun attempt(action: () -> Unit) {
                    try { action() } catch (error: Throwable) { errors += error }
                }
                attempt { WebStorage.getInstance().deleteAllData() }
                val cookies = CookieManager.getInstance()
                try {
                    kotlinx.coroutines.withTimeout(10_000L) {
                        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                            cookies.removeAllCookies {
                                if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                            }
                        }
                    }
                } catch (error: Throwable) { errors += error }
                attempt { cookies.flush() }
                attempt {
                    val probe = WebView(context.applicationContext)
                    try { probe.clearCache(true) } finally { probe.destroy() }
                }
                errors.firstOrNull()?.let { first ->
                    errors.drop(1).filter { it !== first }.forEach(first::addSuppressed)
                    throw first
                }
            }
        }
    }
}
